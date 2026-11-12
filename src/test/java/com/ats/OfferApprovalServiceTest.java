
package com.ats;

import com.ats.model.ApprovalDecision;
import com.ats.model.ApprovalRecord;
import com.ats.model.ApproverRole;
import com.ats.model.Offer;
import com.ats.model.OfferCalculationResult;
import com.ats.model.OfferStatus;
import com.ats.service.OfferApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OfferApprovalServiceTest {

    private static final BigDecimal BAND_MAX =
            new BigDecimal("2000000");

    private static final LocalDate MONDAY =
            LocalDate.of(2026, 11, 16);

    private Offer createOffer(String id) {
        return new Offer(
                id,
                "APP-301",
                new BigDecimal("1800000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1800000"),
                false,
                false,
                MONDAY,
                MONDAY.plusDays(30),
                false
        );
    }

    private OfferCalculationResult calculation(
            String expectedCtc,
            boolean vpRequired,
            boolean cfoRequired) {

        return new OfferCalculationResult(
                new BigDecimal(expectedCtc),
                new BigDecimal("1800000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1800000"),
                vpRequired,
                vpRequired,
                cfoRequired,
                new BigDecimal("2200000"),
                new BigDecimal("2300000")
        );
    }

    private void approve(
            OfferApprovalService service,
            String offerId,
            ApproverRole role,
            LocalDate date) {

        service.recordDecision(
                offerId,
                role,
                ApprovalDecision.APPROVED,
                date.atTime(10, 0),
                "Approved"
        );
    }

    @Test
    @DisplayName("Only Hiring Manager approval is required within band")
    void shouldRequireOnlyHiringManager() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-1");

        service.startApproval(
                offer,
                calculation("1800000", false, false),
                BAND_MAX,
                MONDAY
        );

        assertEquals(
                ApproverRole.HIRING_MANAGER,
                service.getCurrentApprover("OFF-1")
        );

        approve(service, "OFF-1",
                ApproverRole.HIRING_MANAGER, MONDAY);

        assertTrue(service.isCompleted("OFF-1"));
        assertEquals(OfferStatus.APPROVED, offer.getStatus());
        assertEquals(1, service.getApprovalHistory("OFF-1").size());
    }

    @Test
    @DisplayName("HR Head approval follows Hiring Manager above band maximum")
    void shouldRequireHrHeadAboveBandMaximum() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-2");

        service.startApproval(
                offer,
                calculation("2100000", false, false),
                BAND_MAX,
                MONDAY
        );

        approve(service, "OFF-2",
                ApproverRole.HIRING_MANAGER, MONDAY);

        assertEquals(
                ApproverRole.HR_HEAD,
                service.getCurrentApprover("OFF-2")
        );

        approve(service, "OFF-2",
                ApproverRole.HR_HEAD, MONDAY);

        assertTrue(service.isCompleted("OFF-2"));
        assertEquals(OfferStatus.APPROVED, offer.getStatus());
    }

    @Test
    @DisplayName("VP approval follows Hiring Manager and HR Head")
    void shouldRequireVpApproval() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-3");

        service.startApproval(
                offer,
                calculation("2220000", true, false),
                BAND_MAX,
                MONDAY
        );

        approve(service, "OFF-3",
                ApproverRole.HIRING_MANAGER, MONDAY);

        assertEquals(
                ApproverRole.HR_HEAD,
                service.getCurrentApprover("OFF-3")
        );

        approve(service, "OFF-3",
                ApproverRole.HR_HEAD, MONDAY);

        assertEquals(
                ApproverRole.VP,
                service.getCurrentApprover("OFF-3")
        );

        approve(service, "OFF-3",
                ApproverRole.VP, MONDAY);

        assertTrue(service.isCompleted("OFF-3"));
        assertEquals(OfferStatus.APPROVED, offer.getStatus());
    }

    @Test
    @DisplayName("CFO approval is appended after the other required approvals")
    void shouldAppendCfoApproval() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-4");

        service.startApproval(
                offer,
                calculation("2300000", true, true),
                BAND_MAX,
                MONDAY
        );

        approve(service, "OFF-4",
                ApproverRole.HIRING_MANAGER, MONDAY);
        approve(service, "OFF-4",
                ApproverRole.HR_HEAD, MONDAY);
        approve(service, "OFF-4",
                ApproverRole.VP, MONDAY);

        assertEquals(
                ApproverRole.CFO,
                service.getCurrentApprover("OFF-4")
        );

        approve(service, "OFF-4",
                ApproverRole.CFO, MONDAY);

        assertTrue(service.isCompleted("OFF-4"));
        assertEquals(OfferStatus.APPROVED, offer.getStatus());
        assertEquals(4, service.getApprovalHistory("OFF-4").size());
    }

    @Test
    @DisplayName("Hiring Manager timeout is recorded and automatically skipped")
    void shouldSkipHiringManagerAfterTimeout() {
        OfferApprovalService service = new OfferApprovalService();
        service.startApproval(
                createOffer("OFF-5"),
                calculation("2100000", false, false),
                BAND_MAX,
                MONDAY
        );

        LocalDate deadline =
                service.getCurrentDeadline("OFF-5");

        assertEquals(LocalDate.of(2026, 11, 18), deadline);

        service.processTimeout("OFF-5", deadline.plusDays(1));

        assertEquals(
                ApproverRole.HR_HEAD,
                service.getCurrentApprover("OFF-5")
        );

        List<ApprovalRecord> history =
                service.getApprovalHistory("OFF-5");

        assertEquals(1, history.size());
        assertEquals(
                ApprovalDecision.TIMED_OUT,
                history.get(0).decision()
        );
        assertEquals(
                ApproverRole.HIRING_MANAGER,
                history.get(0).approverRole()
        );
    }

    @Test
    @DisplayName("VP timeout requires manual action and allows later approval")
    void shouldRequireManualVpActionAfterTimeout() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-6");

        service.startApproval(
                offer,
                calculation("2220000", true, false),
                BAND_MAX,
                MONDAY
        );

        approve(service, "OFF-6",
                ApproverRole.HIRING_MANAGER, MONDAY);
        approve(service, "OFF-6",
                ApproverRole.HR_HEAD, MONDAY);

        LocalDate deadline =
                service.getCurrentDeadline("OFF-6");

        service.processTimeout("OFF-6", deadline.plusDays(1));

        assertTrue(service.isWaitingForManualAction("OFF-6"));
        assertFalse(service.isCompleted("OFF-6"));
        assertEquals(
                ApproverRole.VP,
                service.getCurrentApprover("OFF-6")
        );

        assertEquals(
                ApprovalDecision.TIMED_OUT,
                service.getApprovalHistory("OFF-6").get(2).decision()
        );

        approve(service, "OFF-6",
                ApproverRole.VP, deadline.plusDays(1));

        assertTrue(service.isCompleted("OFF-6"));
        assertEquals(OfferStatus.APPROVED, offer.getStatus());
        assertEquals(4, service.getApprovalHistory("OFF-6").size());
    }

    @Test
    @DisplayName("Rejection stops the approval workflow")
    void shouldStopWorkflowWhenApproverRejects() {
        OfferApprovalService service = new OfferApprovalService();
        Offer offer = createOffer("OFF-7");

        service.startApproval(
                offer,
                calculation("2100000", false, false),
                BAND_MAX,
                MONDAY
        );

        service.recordDecision(
                "OFF-7",
                ApproverRole.HIRING_MANAGER,
                ApprovalDecision.REJECTED,
                MONDAY.atTime(11, 0),
                "Budget not approved"
        );

        assertTrue(service.isCompleted("OFF-7"));
        assertEquals(OfferStatus.REJECTED, offer.getStatus());
        assertEquals(1, service.getApprovalHistory("OFF-7").size());
    }

    @Test
    @DisplayName("Approval deadline excludes weekends and configured holidays")
    void shouldExcludeWeekendsAndHolidaysFromDeadline() {
        LocalDate thursday =
                LocalDate.of(2026, 11, 12);

        LocalDate fridayHoliday =
                LocalDate.of(2026, 11, 13);

        OfferApprovalService service =
                new OfferApprovalService(Set.of(fridayHoliday));

        service.startApproval(
                createOffer("OFF-8"),
                calculation("1800000", false, false),
                BAND_MAX,
                thursday
        );

        // Friday is a holiday; Saturday and Sunday are weekends.
        // Monday is working day 1 and Tuesday is working day 2.
        assertEquals(
                LocalDate.of(2026, 11, 17),
                service.getCurrentDeadline("OFF-8")
        );
    }
}
