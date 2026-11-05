package com.ats.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a job requisition opened by a hiring manager (BR-01).
 */
public class Requisition {

    private final String requisitionId;
    private final String role;
    private final GradeBand gradeBand;
    private final int headcount;
    private int reservedHeadcount;
    private final String location;
    private final BigDecimal minCtc;
    private final BigDecimal maxCtc;
    private final String hiringManager;
    private final LocalDate openingDate;
    private final LocalDate targetJoinDate;
    private RequisitionStatus status;
    private final LocalDateTime createdAt;

    public Requisition(
            String requisitionId,
            String role,
            GradeBand gradeBand,
            int headcount,
            String location,
            BigDecimal minCtc,
            BigDecimal maxCtc,
            String hiringManager,
            LocalDate openingDate) {
        this(requisitionId, role, gradeBand, headcount, location, minCtc, maxCtc, hiringManager, openingDate, null);
    }

    public Requisition(
            String requisitionId,
            String role,
            GradeBand gradeBand,
            int headcount,
            String location,
            BigDecimal minCtc,
            BigDecimal maxCtc,
            String hiringManager,
            LocalDate openingDate,
            LocalDate targetJoinDate) {

        this.requisitionId = requireText(requisitionId, "Requisition ID");
        this.role = requireText(role, "Role");
        if (gradeBand == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Grade band is required.");
        }
        this.gradeBand = gradeBand;

        if (headcount <= 0) {
            throw new AtsException(
                    ReasonCode.INVALID_HEADCOUNT,
                    "Headcount must be a positive integer (received: " + headcount + ")."
            );
        }
        this.headcount = headcount;
        this.reservedHeadcount = 0;

        this.location = requireText(location, "Location");

        if (minCtc == null || maxCtc == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Minimum CTC and Maximum CTC are required.");
        }
        if (minCtc.compareTo(BigDecimal.ZERO) <= 0 || maxCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Minimum and Maximum CTC must both be strictly greater than zero."
            );
        }
        if (minCtc.compareTo(maxCtc) > 0) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Minimum CTC (" + minCtc.toPlainString() + ") cannot exceed Maximum CTC (" + maxCtc.toPlainString() + ")."
            );
        }
        this.minCtc = minCtc.setScale(2, RoundingMode.HALF_UP);
        this.maxCtc = maxCtc.setScale(2, RoundingMode.HALF_UP);

        this.hiringManager = requireText(hiringManager, "Hiring manager");

        if (openingDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Opening date is required.");
        }
        if (targetJoinDate != null && targetJoinDate.isBefore(openingDate)) {
            throw new AtsException(
                    ReasonCode.INVALID_DATE,
                    "Target join date cannot be earlier than opening date."
            );
        }
        this.openingDate = openingDate;
        this.targetJoinDate = targetJoinDate;
        this.status = RequisitionStatus.OPEN;
        this.createdAt = LocalDateTime.now();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required and cannot be blank.");
        }
        return value.trim();
    }

    public String getRequisitionId() {
        return requisitionId;
    }

    public String getRole() {
        return role;
    }

    public GradeBand getGradeBand() {
        return gradeBand;
    }

    public int getHeadcount() {
        return headcount;
    }

    public int getReservedHeadcount() {
        return reservedHeadcount;
    }

    public int getAvailableHeadcount() {
        return Math.max(0, headcount - reservedHeadcount);
    }

    public boolean hasAvailableHeadcount() {
        return reservedHeadcount < headcount;
    }

    /**
     * Reserves one headcount slot when an application enters OFFER_DRAFT (BR-31).
     */
    public void reserveHeadcount() {
        if (!hasAvailableHeadcount()) {
            throw new AtsException(
                    ReasonCode.HEADCOUNT_EXHAUSTED,
                    "No available headcount on requisition " + requisitionId
                            + " (reserved " + reservedHeadcount + "/" + headcount + ")."
            );
        }
        this.reservedHeadcount++;
    }

    /**
     * Releases one reserved headcount slot when an offer is declined, withdrawn, or rejected (BR-30).
     */
    public void releaseHeadcount() {
        if (this.reservedHeadcount > 0) {
            this.reservedHeadcount--;
        }
    }

    public String getLocation() {
        return location;
    }

    public BigDecimal getMinCtc() {
        return minCtc;
    }

    public BigDecimal getMaxCtc() {
        return maxCtc;
    }

    public String getHiringManager() {
        return hiringManager;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public LocalDate getTargetJoinDate() {
        return targetJoinDate;
    }

    public RequisitionStatus getStatus() {
        return status;
    }

    public void setStatus(RequisitionStatus status) {
        if (status == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Requisition status cannot be null.");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return String.format(
                "Requisition{id='%s', role='%s', grade=%s, headcount=%d (reserved=%d), location='%s', ctcBand=%s-%s, manager='%s', opened=%s, status=%s}",
                requisitionId, role, gradeBand, headcount, reservedHeadcount,
                location, minCtc.toPlainString(), maxCtc.toPlainString(),
                hiringManager, openingDate, status
        );
    }
}
