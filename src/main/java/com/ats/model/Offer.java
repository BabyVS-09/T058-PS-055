package com.ats.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an offer created for a candidate's application (BR-15 through BR-30).
 */
public class Offer {

    private final String offerId;
    private final String applicationId;
    private final BigDecimal offeredCtc;
    private final BigDecimal joiningBonus;
    private final BigDecimal applicableFee;
    private final BigDecimal totalCost;
    private final boolean outOfBand;
    private final boolean requiresCfoApproval;
    private final LocalDate offerDate;
    private final LocalDate validUntilDate;
    private final LocalDate targetJoinDate;
    private final boolean buyoutNotice;
    private OfferStatus status;
    private final LocalDateTime createdAt;

    public Offer(
            String offerId,
            String applicationId,
            BigDecimal offeredCtc,
            BigDecimal joiningBonus,
            BigDecimal applicableFee,
            BigDecimal totalCost,
            boolean outOfBand,
            boolean requiresCfoApproval,
            LocalDate offerDate,
            LocalDate targetJoinDate,
            boolean buyoutNotice) {

        if (offerId == null || offerId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Offer ID is required.");
        }
        if (applicationId == null || applicationId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application ID is required.");
        }
        if (offeredCtc == null || offeredCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(ReasonCode.INVALID_CTC, "Offered CTC must be strictly greater than zero.");
        }
        if (offerDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Offer date is required.");
        }

        this.offerId = offerId.trim();
        this.applicationId = applicationId.trim();
        this.offeredCtc = offeredCtc.setScale(2, RoundingMode.HALF_UP);
        this.joiningBonus = (joiningBonus != null ? joiningBonus : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.applicableFee = (applicableFee != null ? applicableFee : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.totalCost = (totalCost != null ? totalCost : offeredCtc.add(this.joiningBonus).add(this.applicableFee)).setScale(2, RoundingMode.HALF_UP);
        this.outOfBand = outOfBand;
        this.requiresCfoApproval = requiresCfoApproval;
        this.offerDate = offerDate;
        this.validUntilDate = offerDate.plusDays(7); // BR-29: 7 calendar days
        this.targetJoinDate = targetJoinDate;
        this.buyoutNotice = buyoutNotice;
        this.status = OfferStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
    }

    public String getOfferId() {
        return offerId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public BigDecimal getOfferedCtc() {
        return offeredCtc;
    }

    public BigDecimal getJoiningBonus() {
        return joiningBonus;
    }

    public BigDecimal getApplicableFee() {
        return applicableFee;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public boolean isOutOfBand() {
        return outOfBand;
    }

    public boolean isRequiresCfoApproval() {
        return requiresCfoApproval;
    }

    public LocalDate getOfferDate() {
        return offerDate;
    }

    public LocalDate getValidUntilDate() {
        return validUntilDate;
    }

    public LocalDate getTargetJoinDate() {
        return targetJoinDate;
    }

    public boolean isBuyoutNotice() {
        return buyoutNotice;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        if (status == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Offer status cannot be null.");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return String.format("Offer{id='%s', app='%s', ctc=%s, bonus=%s, fee=%s, total=%s, oob=%s, cfo=%s, status=%s}",
                offerId, applicationId, offeredCtc.toPlainString(), joiningBonus.toPlainString(),
                applicableFee.toPlainString(), totalCost.toPlainString(), outOfBand, requiresCfoApproval, status);
    }
}
