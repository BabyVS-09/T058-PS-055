
package com.ats.service;

import com.ats.model.ApprovalDecision;
import com.ats.model.ApprovalRecord;
import com.ats.model.ApproverRole;
import com.ats.model.AtsException;
import com.ats.model.Offer;
import com.ats.model.OfferCalculationResult;
import com.ats.model.OfferStatus;
import com.ats.model.ReasonCode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages sequential offer approvals, deadlines, timeouts and audit history.
 */
public class OfferApprovalService {

    public static final int APPROVAL_DEADLINE_WORKING_DAYS = 2;

    private final Map<String, ApprovalWorkflow> workflows = new HashMap<>();
    private final Set<LocalDate> holidays;

    public OfferApprovalService() {
        this(Collections.emptySet());
    }

    public OfferApprovalService(Set<LocalDate> holidays) {
        this.holidays = holidays == null
                ? Collections.emptySet()
                : Set.copyOf(holidays);
    }

    /**
     * Starts approval using the requisition's maximum CTC.
     */
    public void startApproval(
            Offer offer,
            OfferCalculationResult calculation,
            BigDecimal bandMax,
            LocalDate startDate) {

        if (offer == null || calculation == null
                || bandMax == null || startDate == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Offer, calculation, band maximum and start date are required."
            );
        }

        if (bandMax.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Requisition band maximum must be positive."
            );
        }

        String offerId = offer.getOfferId();

        if (offerId == null || offerId.isBlank()) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Offer ID is required."
            );
        }

        if (workflows.containsKey(offerId)) {
            throw new AtsException(
                    ReasonCode.APPROVAL_REQUIRED,
                    "An approval workflow already exists for this offer."
            );
        }

        List<ApproverRole> chain =
                buildApprovalChain(calculation, bandMax);

        ApprovalWorkflow workflow =
                new ApprovalWorkflow(offer, chain, startDate);

        workflows.put(offerId, workflow);
        offer.setStatus(OfferStatus.PENDING_APPROVAL);
    }

    /**
     * Builds the approval chain according to CTC thresholds.
     */
    private List<ApproverRole> buildApprovalChain(
            OfferCalculationResult calculation,
            BigDecimal bandMax) {

        List<ApproverRole> chain = new ArrayList<>();

        BigDecimal expectedCtc = calculation.expectedCtc();

        if (expectedCtc == null) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Expected CTC is required for approval."
            );
        }

        chain.add(ApproverRole.HIRING_MANAGER);

        // Above band maximum requires HR Head approval.
        if (expectedCtc.compareTo(bandMax) > 0) {
            chain.add(ApproverRole.HR_HEAD);
        }

        // Above 110% of band maximum requires VP approval.
        if (calculation.requiresVpApproval()) {
            if (!chain.contains(ApproverRole.HR_HEAD)) {
                chain.add(ApproverRole.HR_HEAD);
            }
            chain.add(ApproverRole.VP);
        }

        // CFO is appended when total hiring cost exceeds 115%.
        if (calculation.requiresCfoApproval()) {
            chain.add(ApproverRole.CFO);
        }

        return chain;
    }

    /**
     * Records an approval or rejection from the current approver.
     */
    public void recordDecision(
            String offerId,
            ApproverRole approver,
            ApprovalDecision decision,
            LocalDateTime decidedAt,
            String remarks) {

        ApprovalWorkflow workflow = getWorkflow(offerId);

        if (approver == null || decision == null || decidedAt == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Approver, decision and decision date are required."
            );
        }

        if (workflow.isCompleted()) {
            throw new AtsException(
                    ReasonCode.APPROVAL_REQUIRED,
                    "This approval workflow is already completed."
            );
        }

        if (approver != workflow.getCurrentApprover()) {
            throw new AtsException(
                    ReasonCode.APPROVAL_REQUIRED,
                    "Expected approver: " + workflow.getCurrentApprover()
            );
        }

        if (decision == ApprovalDecision.TIMED_OUT) {
            throw new AtsException(
                    ReasonCode.TIMED_OUT,
                    "Use processTimeout to record an expired approval."
            );
        }

        LocalDate decisionDate = decidedAt.toLocalDate();

        // After timeout, only a non-skippable approver may act manually.
        if (workflow.isWaitingForManualAction()) {
            if (!approver.isNonSkippable()) {
                throw new AtsException(
                        ReasonCode.APPROVAL_REQUIRED,
                        "Manual action is not available for this approver."
                );
            }
        } else if (decisionDate.isAfter(workflow.getCurrentDeadline())) {
            throw new AtsException(
                    ReasonCode.TIMED_OUT,
                    "Approval deadline has passed. Process the timeout first."
            );
        }

        workflow.addRecord(new ApprovalRecord(
                approver,
                decision,
                workflow.getCurrentDeadline(),
                decidedAt,
                remarks
        ));

        if (decision == ApprovalDecision.REJECTED) {
            workflow.getOffer().setStatus(OfferStatus.REJECTED);
            workflow.complete();
            return;
        }

        workflow.moveToNextApprover(decisionDate);
    }

    /**
     * Processes an expired approval deadline.
     * Hiring Manager and HR Head are skipped automatically.
     * VP and CFO are recorded as timed out but require manual action.
     */
    public void processTimeout(String offerId, LocalDate currentDate) {

        if (currentDate == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Current date is required."
            );
        }

        ApprovalWorkflow workflow = getWorkflow(offerId);

        if (workflow.isCompleted()
                || workflow.isWaitingForManualAction()) {
            return;
        }

        if (!currentDate.isAfter(workflow.getCurrentDeadline())) {
            return;
        }

        ApproverRole currentApprover = workflow.getCurrentApprover();

        workflow.addRecord(new ApprovalRecord(
                currentApprover,
                ApprovalDecision.TIMED_OUT,
                workflow.getCurrentDeadline(),
                currentDate.atStartOfDay(),
                "Approval deadline expired."
        ));

        if (currentApprover.isNonSkippable()) {
            workflow.setWaitingForManualAction(true);
            return;
        }

        workflow.moveToNextApprover(currentDate);
    }

    public ApproverRole getCurrentApprover(String offerId) {
        return getWorkflow(offerId).getCurrentApprover();
    }

    public LocalDate getCurrentDeadline(String offerId) {
        return getWorkflow(offerId).getCurrentDeadline();
    }

    public List<ApprovalRecord> getApprovalHistory(String offerId) {
        return getWorkflow(offerId).getHistory();
    }

    public boolean isCompleted(String offerId) {
        return getWorkflow(offerId).isCompleted();
    }

    public boolean isWaitingForManualAction(String offerId) {
        return getWorkflow(offerId).isWaitingForManualAction();
    }

    private ApprovalWorkflow getWorkflow(String offerId) {

        if (offerId == null || offerId.isBlank()) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Offer ID is required."
            );
        }

        ApprovalWorkflow workflow = workflows.get(offerId.trim());

        if (workflow == null) {
            throw new AtsException(
                    ReasonCode.OFFER_NOT_FOUND,
                    "No approval workflow found for offer " + offerId + "."
            );
        }

        return workflow;
    }

    /**
     * Calculates the second working day after the start date.
     */
    private LocalDate calculateDeadline(LocalDate startDate) {

        LocalDate date = startDate;
        int workingDays = 0;

        while (workingDays < APPROVAL_DEADLINE_WORKING_DAYS) {
            date = date.plusDays(1);

            if (isWorkingDay(date)) {
                workingDays++;
            }
        }

        return date;
    }

    private boolean isWorkingDay(LocalDate date) {

        DayOfWeek day = date.getDayOfWeek();

        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }

    /**
     * Stores the state of one offer's approval workflow.
     */
    private class ApprovalWorkflow {

        private final Offer offer;
        private final List<ApproverRole> approvalChain;
        private final List<ApprovalRecord> history = new ArrayList<>();

        private int currentIndex;
        private LocalDate currentDeadline;
        private boolean completed;
        private boolean waitingForManualAction;

        private ApprovalWorkflow(
                Offer offer,
                List<ApproverRole> approvalChain,
                LocalDate startDate) {

            this.offer = offer;
            this.approvalChain = new ArrayList<>(approvalChain);
            this.currentIndex = 0;
            this.currentDeadline = calculateDeadline(startDate);
        }

        private Offer getOffer() {
            return offer;
        }

        private ApproverRole getCurrentApprover() {
            return completed ? null : approvalChain.get(currentIndex);
        }

        private LocalDate getCurrentDeadline() {
            return currentDeadline;
        }

        private List<ApprovalRecord> getHistory() {
            return Collections.unmodifiableList(
                    new ArrayList<>(history)
            );
        }

        private void addRecord(ApprovalRecord record) {
            history.add(record);
        }

        private boolean isCompleted() {
            return completed;
        }

        private boolean isWaitingForManualAction() {
            return waitingForManualAction;
        }

        private void setWaitingForManualAction(boolean value) {
            waitingForManualAction = value;
        }

        private void complete() {
            completed = true;
        }

        private void moveToNextApprover(LocalDate decisionDate) {

            waitingForManualAction = false;
            currentIndex++;

            if (currentIndex >= approvalChain.size()) {
                completed = true;
                offer.setStatus(OfferStatus.APPROVED);
            } else {
                currentDeadline = calculateDeadline(decisionDate);
            }
        }
    }
}
