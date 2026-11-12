package com.ats.service;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.Candidate;
import com.ats.model.CandidateSource;
import com.ats.model.OfferCalculationResult;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Service for calculating candidate offer CTC, joining bonuses, recruitment fees,
 * total hiring costs, OUT_OF_BAND flags, and approval thresholds
 * (BR-15 through BR-23).
 */
public class OfferCalculator {

    public static final BigDecimal QUICK_JOIN_BONUS = new BigDecimal("50000.00");
    public static final BigDecimal BUYOUT_JOIN_BONUS = new BigDecimal("100000.00");
    public static final BigDecimal REFERRAL_FEE = new BigDecimal("25000.00");
    public static final BigDecimal AGENCY_FEE_RATE = new BigDecimal("0.0833"); // 8.33% of offered CTC
    public static final BigDecimal CTC_HIKE_CAP_RATE = new BigDecimal("1.40"); // 1.40x current CTC
    public static final BigDecimal OUT_OF_BAND_THRESHOLD_RATE = new BigDecimal("1.10"); // 1.10x bandMax
    public static final BigDecimal CFO_APPROVAL_THRESHOLD_RATE = new BigDecimal("1.15"); // 1.15x bandMax

    /**
     * Calculates the offer for an application, candidate, and requisition.
     */
    public OfferCalculationResult calculateOffer(
            Application application,
            Candidate candidate,
            Requisition requisition,
            boolean buyoutNotice,
            LocalDate offerDate,
            LocalDate targetJoinDate) {

        if (application == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application is required.");
        }
        if (candidate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Candidate is required.");
        }
        if (requisition == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Requisition is required.");
        }
        if (offerDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Offer date is required.");
        }

        BigDecimal expectedCtc = candidate.getExpectedCtc();
        BigDecimal currentCtc = candidate.getCurrentCtc();
        BigDecimal bandMin = requisition.getMinCtc();
        BigDecimal bandMax = requisition.getMaxCtc();

        if (expectedCtc == null || expectedCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(ReasonCode.INVALID_CTC, "Candidate expected CTC must be strictly positive.");
        }
        if (currentCtc == null || currentCtc.compareTo(BigDecimal.ZERO) < 0) {
            throw new AtsException(ReasonCode.INVALID_CTC, "Candidate current CTC cannot be negative.");
        }

        // BR-16: Flag OUT_OF_BAND when expected CTC exceeds band maximum by more than 10%
        // Exactly 10% above bandMax is NOT OUT_OF_BAND; 10.01% IS.
        BigDecimal outOfBandThreshold = bandMax.multiply(OUT_OF_BAND_THRESHOLD_RATE).setScale(2, RoundingMode.HALF_UP);
        boolean outOfBand = expectedCtc.compareTo(outOfBandThreshold) > 0;

        // BR-15: Preliminary CTC calculation
        // Maximum permitted CTC is the lower of: (Current CTC * 1.40) and Requisition band maximum
        BigDecimal cap140 = currentCtc.multiply(CTC_HIKE_CAP_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxPermittedCtc = cap140.compareTo(bandMax) < 0 ? cap140 : bandMax;

        // Apply caps to expected CTC
        BigDecimal offeredCtc = expectedCtc.compareTo(maxPermittedCtc) <= 0 ? expectedCtc : maxPermittedCtc;

        // Precedence DEC-16: Requisition band minimum floor is enforced to guarantee minimum grade equity
        if (offeredCtc.compareTo(bandMin) < 0) {
            offeredCtc = bandMin;
        }
        offeredCtc = offeredCtc.setScale(2, RoundingMode.HALF_UP);

        // BR-18, BR-19, BR-20: Joining bonus calculation
        BigDecimal joiningBonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyoutReimbursement = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Buyout reimbursement cap is one month of current CTC / 12 (BR-19)
        BigDecimal monthlyCtc = currentCtc.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);

        if (candidate.getSource() == CandidateSource.INTERNAL) {
            // BR-20: INTERNAL candidates receive zero joining bonus
            joiningBonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            // Notice period <= 30 days and joins within 45 days of offer date -> 50,000
            if (candidate.getNoticePeriodDays() <= 30 && targetJoinDate != null) {
                long daysToJoin = ChronoUnit.DAYS.between(offerDate, targetJoinDate);
                if (daysToJoin >= 0 && daysToJoin <= 45) {
                    joiningBonus = QUICK_JOIN_BONUS;
                }
            }

            // Notice period > 60 days and candidate buys out notice -> 1,00,000 joining bonus
            if (candidate.getNoticePeriodDays() > 60 && buyoutNotice) {
                joiningBonus = BUYOUT_JOIN_BONUS;
                buyoutReimbursement = monthlyCtc;
            }
        }

        // BR-21, BR-22: Applicable fees (referral / agency)
        BigDecimal applicableFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (candidate.getSource() == CandidateSource.REFERRAL) {
            applicableFee = REFERRAL_FEE;
        } else if (candidate.getSource() == CandidateSource.AGENCY) {
            applicableFee = offeredCtc.multiply(AGENCY_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        // BR-23: Total hiring cost = offeredCTC + applicableFee + joiningBonus
        BigDecimal totalCost = offeredCtc.add(applicableFee).add(joiningBonus).setScale(2, RoundingMode.HALF_UP);

        // BR-23: Total recruitment cost above band maximum x 1.15 requires CFO approval
        BigDecimal cfoThreshold = bandMax.multiply(CFO_APPROVAL_THRESHOLD_RATE).setScale(2, RoundingMode.HALF_UP);
        boolean requiresCfoApproval = totalCost.compareTo(cfoThreshold) > 0;

        // BR-17, BR-26: OUT_OF_BAND or > 1.10x bandMax requires VP approval
        boolean requiresVpApproval = outOfBand || offeredCtc.compareTo(outOfBandThreshold) > 0;

        return new OfferCalculationResult(
                expectedCtc,
                offeredCtc,
                joiningBonus,
                buyoutReimbursement,
                applicableFee,
                totalCost,
                outOfBand,
                requiresVpApproval,
                requiresCfoApproval,
                outOfBandThreshold,
                cfoThreshold
        );
    }

    /**
     * Helper to compute buyout reimbursement cap: currentCtc / 12.
     */
    public BigDecimal calculateBuyoutReimbursementCap(BigDecimal currentCtc) {
        if (currentCtc == null || currentCtc.compareTo(BigDecimal.ZERO) < 0) {
            throw new AtsException(ReasonCode.INVALID_CTC, "Current CTC cannot be negative.");
        }
        return currentCtc.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Helper to compute agency fee: 8.33% of offered CTC.
     */
    public BigDecimal calculateAgencyFee(BigDecimal offeredCtc) {
        if (offeredCtc == null || offeredCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(ReasonCode.INVALID_CTC, "Offered CTC must be positive.");
        }
        return offeredCtc.multiply(AGENCY_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
