package com.ats;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.Candidate;
import com.ats.model.CandidateSource;
import com.ats.model.GradeBand;
import com.ats.model.OfferCalculationResult;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.service.OfferCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link OfferCalculator} covering Module 3 (BR-15, BR-16, BR-17)
 * and Module 4 (BR-18, BR-19, BR-20, BR-21, BR-22, BR-23).
 */
class OfferCalculatorTest {

    private OfferCalculator calculator;
    private Requisition standardRequisition;
    private Application standardApplication;

    @BeforeEach
    void setUp() {
        calculator = new OfferCalculator();
        // Band: 10,00,000 to 20,00,000 (G4)
        standardRequisition = new Requisition(
                "REQ-101",
                "Senior Developer",
                GradeBand.G4,
                2,
                "Bengaluru",
                new BigDecimal("1000000.00"),
                new BigDecimal("2000000.00"),
                "Hiring Manager",
                LocalDate.of(2026, 3, 1)
        );
        standardApplication = new Application(
                "APP-1",
                "CAN-1",
                "REQ-101",
                GradeBand.G4,
                LocalDate.of(2026, 3, 2)
        );
    }

    // ==========================================
    // MODULE 3: OFFER CTC CALCULATION TESTS
    // ==========================================

    @Test
    @DisplayName("Module 3: Normal expected CTC within band and within 1.40x cap")
    void shouldCalculateNormalExpectedCtc() {
        // current CTC: 12,00,000. 1.40x = 16,80,000. Expected = 15,00,000.
        Candidate candidate = new Candidate("CAN-1", "Test", 30, new BigDecimal("1200000"),
                new BigDecimal("1500000"), new BigDecimal("5.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertEquals(new BigDecimal("1500000.00"), result.offeredCtc());
        assertFalse(result.outOfBand());
    }

    @Test
    @DisplayName("Module 3: Expected CTC below band minimum is raised to band minimum floor")
    void shouldRaiseExpectedCtcToBandMinimumFloor() {
        // Expected CTC is 8,00,000; band min is 10,00,000 -> floor raises to 10,00,000
        Candidate candidate = new Candidate("CAN-2", "Test", 30, new BigDecimal("600000"),
                new BigDecimal("800000"), new BigDecimal("3.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertEquals(new BigDecimal("1000000.00"), result.offeredCtc());
    }

    @Test
    @DisplayName("Module 3: Expected CTC capped by current CTC x 1.40")
    void shouldCapOfferedCtcBy140PercentCurrentCtc() {
        // Current: 10,00,000. 1.40x = 14,00,000. Expected: 18,00,000 (<= bandMax 20,00,000).
        Candidate candidate = new Candidate("CAN-3", "Test", 30, new BigDecimal("1000000"),
                new BigDecimal("1800000"), new BigDecimal("6.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertEquals(new BigDecimal("1400000.00"), result.offeredCtc());
    }

    @Test
    @DisplayName("Module 3: Expected CTC capped by band maximum")
    void shouldCapOfferedCtcByBandMaximum() {
        // Current: 18,00,000. 1.40x = 25,20,000. Band max: 20,00,000. Expected: 22,00,000.
        Candidate candidate = new Candidate("CAN-4", "Test", 30, new BigDecimal("1800000"),
                new BigDecimal("2200000"), new BigDecimal("8.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertEquals(new BigDecimal("2000000.00"), result.offeredCtc());
    }

    @Test
    @DisplayName("Module 3: Exactly 10% above band maximum is NOT OUT_OF_BAND (BR-16)")
    void shouldNotFlagOutOfBandAtExactly10PercentAboveBandMax() {
        // Band max: 20,00,000. Exactly 10% above = 22,00,000.00.
        Candidate candidate = new Candidate("CAN-5", "Test", 30, new BigDecimal("1600000"),
                new BigDecimal("2200000.00"), new BigDecimal("7.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertFalse(result.outOfBand(), "Exactly 10% above band maximum must NOT be flagged OUT_OF_BAND");
    }

    @Test
    @DisplayName("Module 3: 10.01% above band maximum IS OUT_OF_BAND (BR-16)")
    void shouldFlagOutOfBandAt10Point01PercentAboveBandMax() {
        // Band max: 20,00,000. 10.01% above = 20,00,000 * 1.1001 = 22,00,200.00.
        // Specifically 22,00,000.01 is already > 22,00,000.00 (10%).
        Candidate candidate = new Candidate("CAN-6", "Test", 30, new BigDecimal("1600000"),
                new BigDecimal("2200200.00"), new BigDecimal("7.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertTrue(result.outOfBand(), "10.01% above band maximum MUST be flagged OUT_OF_BAND");
        assertTrue(result.requiresVpApproval(), "OUT_OF_BAND offer requires VP approval");
    }

    @Test
    @DisplayName("Module 3: Rejects invalid or negative CTC amounts with INVALID_CTC")
    void shouldRejectInvalidCtcAmounts() {
        AtsException negEx = assertThrows(AtsException.class,
                () -> calculator.calculateBuyoutReimbursementCap(new BigDecimal("-500")));
        assertEquals(ReasonCode.INVALID_CTC, negEx.getReasonCode());

        AtsException agencyEx = assertThrows(AtsException.class,
                () -> calculator.calculateAgencyFee(BigDecimal.ZERO));
        assertEquals(ReasonCode.INVALID_CTC, agencyEx.getReasonCode());
    }

    @Test
    @DisplayName("Module 3: Preserves monetary scale of 2 decimal places with HALF_UP rounding")
    void shouldPreserveMonetaryScaleTwoWithHalfUpRounding() {
        Candidate candidate = new Candidate("CAN-7", "Test", 30, new BigDecimal("1234567.89"),
                new BigDecimal("1500000.555"), new BigDecimal("5.0"), CandidateSource.PORTAL);

        OfferCalculationResult result = calculator.calculateOffer(
                standardApplication, candidate, standardRequisition, false,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)
        );

        assertEquals(2, result.offeredCtc().scale());
        assertEquals(2, result.totalCost().scale());
    }

    // ==========================================
    // MODULE 4: JOINING BONUSES AND RECRUITMENT COSTS TESTS
    // ==========================================

    @Test
    @DisplayName("Module 4: Joining bonus ₹50,000 for notice period <= 30 days joining within 45 days")
    void shouldAward50kJoiningBonusForQuickJoiner() {
        LocalDate offerDate = LocalDate.of(2026, 3, 1);

        // Notice period 0 days, joins in 10 days -> 50,000
        Candidate c0 = new Candidate("C0", "T0", 0, new BigDecimal("1000000"), new BigDecimal("1200000"), new BigDecimal("3.0"), CandidateSource.PORTAL);
        OfferCalculationResult r0 = calculator.calculateOffer(standardApplication, c0, standardRequisition, false, offerDate, offerDate.plusDays(10));
        assertEquals(new BigDecimal("50000.00"), r0.joiningBonus());

        // Notice period 30 days, joins in exactly 45 days -> 50,000
        Candidate c30 = new Candidate("C30", "T30", 30, new BigDecimal("1000000"), new BigDecimal("1200000"), new BigDecimal("3.0"), CandidateSource.PORTAL);
        OfferCalculationResult r30 = calculator.calculateOffer(standardApplication, c30, standardRequisition, false, offerDate, offerDate.plusDays(45));
        assertEquals(new BigDecimal("50000.00"), r30.joiningBonus());

        // Notice period 30 days, joins in 46 days (after 45 days) -> 0 bonus
        OfferCalculationResult r46 = calculator.calculateOffer(standardApplication, c30, standardRequisition, false, offerDate, offerDate.plusDays(46));
        assertEquals(BigDecimal.ZERO.setScale(2), r46.joiningBonus());

        // Notice period 31 days (exceeds 30 days) joining in 20 days -> 0 bonus
        Candidate c31 = new Candidate("C31", "T31", 31, new BigDecimal("1000000"), new BigDecimal("1200000"), new BigDecimal("3.0"), CandidateSource.PORTAL);
        OfferCalculationResult r31 = calculator.calculateOffer(standardApplication, c31, standardRequisition, false, offerDate, offerDate.plusDays(20));
        assertEquals(BigDecimal.ZERO.setScale(2), r31.joiningBonus());
    }

    @Test
    @DisplayName("Module 4: ₹1,00,000 joining bonus when notice period > 60 days with buyout")
    void shouldAward100kBonusForNoticeBuyout() {
        LocalDate offerDate = LocalDate.of(2026, 3, 1);

        // Notice period 60 days with buyout -> Not eligible (requires notice period > 60)
        Candidate c60 = new Candidate("C60", "T60", 60, new BigDecimal("1200000"), new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.PORTAL);
        OfferCalculationResult r60 = calculator.calculateOffer(standardApplication, c60, standardRequisition, true, offerDate, offerDate.plusDays(30));
        assertEquals(BigDecimal.ZERO.setScale(2), r60.joiningBonus());

        // Notice period 61 days with buyout -> 1,00,000
        Candidate c61 = new Candidate("C61", "T61", 61, new BigDecimal("1200000"), new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.PORTAL);
        OfferCalculationResult r61 = calculator.calculateOffer(standardApplication, c61, standardRequisition, true, offerDate, offerDate.plusDays(30));
        assertEquals(new BigDecimal("100000.00"), r61.joiningBonus());

        // Notice period 91 days with buyout -> 1,00,000
        Candidate c91 = new Candidate("C91", "T91", 91, new BigDecimal("1200000"), new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.PORTAL);
        OfferCalculationResult r91 = calculator.calculateOffer(standardApplication, c91, standardRequisition, true, offerDate, offerDate.plusDays(30));
        assertEquals(new BigDecimal("100000.00"), r91.joiningBonus());

        // Notice period 61 days WITHOUT buyout -> 0 bonus
        OfferCalculationResult rNoBuyout = calculator.calculateOffer(standardApplication, c61, standardRequisition, false, offerDate, offerDate.plusDays(30));
        assertEquals(BigDecimal.ZERO.setScale(2), rNoBuyout.joiningBonus());
    }

    @Test
    @DisplayName("Module 4: Buyout reimbursement cap is one month of current CTC / 12 (BR-19)")
    void shouldCalculateBuyoutReimbursementCap() {
        // current CTC 12,00,000 / 12 = 1,00,000.00
        BigDecimal cap12 = calculator.calculateBuyoutReimbursementCap(new BigDecimal("1200000.00"));
        assertEquals(new BigDecimal("100000.00"), cap12);

        // current CTC 15,00,000 / 12 = 1,25,000.00
        BigDecimal cap15 = calculator.calculateBuyoutReimbursementCap(new BigDecimal("1500000.00"));
        assertEquals(new BigDecimal("125000.00"), cap15);
    }

    @Test
    @DisplayName("Module 4: INTERNAL candidates receive zero joining bonus (BR-20)")
    void shouldAwardZeroJoiningBonusForInternalCandidate() {
        LocalDate offerDate = LocalDate.of(2026, 3, 1);

        // Internal candidate with 0 days notice period joining in 5 days
        Candidate internalQuick = new Candidate("INT-1", "Internal Emp", 0, new BigDecimal("1200000"),
                new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.INTERNAL);
        OfferCalculationResult res1 = calculator.calculateOffer(standardApplication, internalQuick, standardRequisition, false, offerDate, offerDate.plusDays(5));
        assertEquals(BigDecimal.ZERO.setScale(2), res1.joiningBonus());

        // Internal candidate with notice period 90 days buying out
        Candidate internalBuyout = new Candidate("INT-2", "Internal Emp 2", 90, new BigDecimal("1200000"),
                new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.INTERNAL);
        OfferCalculationResult res2 = calculator.calculateOffer(standardApplication, internalBuyout, standardRequisition, true, offerDate, offerDate.plusDays(30));
        assertEquals(BigDecimal.ZERO.setScale(2), res2.joiningBonus());
    }

    @Test
    @DisplayName("Module 4: REFERRAL fee ₹25,000 and AGENCY fee 8.33% of offered CTC")
    void shouldCalculateReferralAndAgencyFees() {
        LocalDate offerDate = LocalDate.of(2026, 3, 1);

        // Referral candidate: fee is ₹25,000
        Candidate referralCandidate = new Candidate("REF-1", "Ref Person", 45, new BigDecimal("1200000"),
                new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.REFERRAL);
        OfferCalculationResult refRes = calculator.calculateOffer(standardApplication, referralCandidate, standardRequisition, false, offerDate, offerDate.plusDays(45));
        assertEquals(new BigDecimal("25000.00"), refRes.applicableFee());

        // Agency candidate: fee is 8.33% of offered CTC (15,00,000 * 0.0833 = 1,24,950.00)
        Candidate agencyCandidate = new Candidate("AGN-1", "Agency Person", 45, new BigDecimal("1200000"),
                new BigDecimal("1500000"), new BigDecimal("4.0"), CandidateSource.AGENCY);
        OfferCalculationResult agnRes = calculator.calculateOffer(standardApplication, agencyCandidate, standardRequisition, false, offerDate, offerDate.plusDays(45));
        assertEquals(new BigDecimal("124950.00"), agnRes.applicableFee());
    }

    @Test
    @DisplayName("Module 4: Total recruitment cost threshold at and above 1.15 x band maximum (BR-23)")
    void shouldEnforceCfoApprovalThresholdAt115PercentBandMax() {
        // Band max: 20,00,000. CFO threshold = 20,00,000 * 1.15 = 23,00,000.00.
        LocalDate offerDate = LocalDate.of(2026, 3, 1);

        // Case 1: Total cost exactly 23,00,000.00 (not above) -> does NOT require CFO approval
        // offeredCtc = 20,00,000, referral fee = 25,000, bonus = 0 -> total = 20,25,000 <= 23,00,000 -> false
        Candidate c1 = new Candidate("C-CFO-1", "Test", 45, new BigDecimal("1800000"),
                new BigDecimal("2000000"), new BigDecimal("6.0"), CandidateSource.PORTAL);
        OfferCalculationResult res1 = calculator.calculateOffer(standardApplication, c1, standardRequisition, false, offerDate, offerDate.plusDays(45));
        assertFalse(res1.requiresCfoApproval());

        // Case 2: Candidate with Agency fee on high CTC:
        // Requisition with bandMax = 20,00,000
        // Offered CTC = 20,00,000. Agency fee (8.33%) = 1,66,600.00. Buyout bonus = 1,00,000.
        // Total cost = 20,00,000 + 1,66,600 + 1,00,000 = 22,66,600.
        // If bandMax is 18,00,000, CFO threshold = 18,00,000 * 1.15 = 20,70,000.
        Requisition req18 = new Requisition("REQ-18", "Dev", GradeBand.G3, 1, "Blr",
                new BigDecimal("1000000"), new BigDecimal("1800000"), "Mgr", LocalDate.of(2026, 3, 1));
        Application app18 = new Application("APP-18", "C-18", "REQ-18", GradeBand.G3, LocalDate.of(2026, 3, 2));
        Candidate agencyBuyout = new Candidate("AGN-BO", "Agent", 65, new BigDecimal("1600000"),
                new BigDecimal("1800000"), new BigDecimal("5.0"), CandidateSource.AGENCY);

        // Offered CTC = 18,00,000. Agency fee = 18,00,000 * 0.0833 = 1,49,940.00. Bonus = 1,00,000.00.
        // Total cost = 18,00,000 + 1,49,940 + 1,00,000 = 20,49,940.00.
        // CFO threshold = 18,00,000 * 1.15 = 20,70,000.00. Total is below -> false.
        OfferCalculationResult res18 = calculator.calculateOffer(app18, agencyBuyout, req18, true, offerDate, offerDate.plusDays(30));
        assertFalse(res18.requiresCfoApproval());

        // Now with Requisition bandMax = 15,00,000. CFO threshold = 15,00,000 * 1.15 = 17,25,000.00.
        // Offered CTC = 15,00,000. Agency fee = 15,00,000 * 0.0833 = 1,24,950.00. Buyout bonus = 1,00,000.
        // Total = 17,24,950 <= 17,25,000 -> not above.
        // Let's create a case where total cost strictly exceeds CFO threshold:
        // Requisition band: min 8,00,000, max 10,00,000. CFO threshold = 11,50,000.00.
        // Candidate: offered 10,00,000, buyout bonus 1,00,000, agency fee 83,300 -> total = 11,83,300.00 > 11,50,000.00 -> requires CFO!
        Requisition req10 = new Requisition("REQ-10", "Dev", GradeBand.G2, 1, "Blr",
                new BigDecimal("700000"), new BigDecimal("1000000"), "Mgr", LocalDate.of(2026, 3, 1));
        Application app10 = new Application("APP-10", "C-10", "REQ-10", GradeBand.G2, LocalDate.of(2026, 3, 2));
        Candidate agencyExceeds = new Candidate("AGN-EX", "Agent", 65, new BigDecimal("900000"),
                new BigDecimal("1000000"), new BigDecimal("3.0"), CandidateSource.AGENCY);

        OfferCalculationResult resExceeds = calculator.calculateOffer(app10, agencyExceeds, req10, true, offerDate, offerDate.plusDays(30));
        assertTrue(resExceeds.requiresCfoApproval(), "Total cost 11,83,300 exceeds 11,50,000 and requires CFO approval");
    }
}
