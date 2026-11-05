package com.ats.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Represents a candidate registered in the recruitment system (BR-02).
 */
public class Candidate {

    private final String candidateId;
    private final String name;
    private final int noticePeriodDays;
    private final BigDecimal currentCtc;
    private final BigDecimal expectedCtc;
    private final BigDecimal experienceYears;
    private final CandidateSource source;
    private final LocalDateTime registeredAt;

    /**
     * Convenience constructor when a single identifier/name is supplied.
     */
    public Candidate(
            String candidateIdOrName,
            int noticePeriodDays,
            BigDecimal currentCtc,
            BigDecimal expectedCtc,
            BigDecimal experienceYears,
            CandidateSource source) {
        this(candidateIdOrName, candidateIdOrName, noticePeriodDays, currentCtc, expectedCtc, experienceYears, source);
    }

    /**
     * Primary constructor storing distinct candidate ID and candidate full name.
     */
    public Candidate(
            String candidateId,
            String name,
            int noticePeriodDays,
            BigDecimal currentCtc,
            BigDecimal expectedCtc,
            BigDecimal experienceYears,
            CandidateSource source) {

        this.candidateId = requireText(candidateId, "Candidate ID");
        this.name = requireText(name, "Candidate name");

        if (noticePeriodDays < 0) {
            throw new AtsException(
                    ReasonCode.INVALID_NOTICE_PERIOD,
                    "Notice period days cannot be negative (received: " + noticePeriodDays + ")."
            );
        }
        this.noticePeriodDays = noticePeriodDays;

        if (currentCtc == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Current CTC is required.");
        }
        if (currentCtc.compareTo(BigDecimal.ZERO) < 0) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Current CTC cannot be negative (received: " + currentCtc.toPlainString() + ")."
            );
        }
        this.currentCtc = currentCtc.setScale(2, RoundingMode.HALF_UP);

        if (expectedCtc == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Expected CTC is required.");
        }
        if (expectedCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AtsException(
                    ReasonCode.INVALID_CTC,
                    "Expected CTC must be strictly greater than zero (received: " + expectedCtc.toPlainString() + ")."
            );
        }
        this.expectedCtc = expectedCtc.setScale(2, RoundingMode.HALF_UP);

        if (experienceYears == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Experience years is required.");
        }
        if (experienceYears.compareTo(BigDecimal.ZERO) < 0) {
            throw new AtsException(
                    ReasonCode.INVALID_EXPERIENCE,
                    "Experience years cannot be negative (received: " + experienceYears.toPlainString() + ")."
            );
        }
        this.experienceYears = experienceYears.stripTrailingZeros().scale() < 0
                ? experienceYears.setScale(1, RoundingMode.HALF_UP)
                : experienceYears;

        if (source == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Candidate source is required.");
        }
        this.source = source;
        this.registeredAt = LocalDateTime.now();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required and cannot be blank.");
        }
        return value.trim();
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getName() {
        return name;
    }

    public int getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public BigDecimal getCurrentCtc() {
        return currentCtc;
    }

    public BigDecimal getExpectedCtc() {
        return expectedCtc;
    }

    public BigDecimal getExperienceYears() {
        return experienceYears;
    }

    public CandidateSource getSource() {
        return source;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    @Override
    public String toString() {
        return String.format(
                "Candidate{id='%s', name='%s', noticeDays=%d, currentCTC=%s, expectedCTC=%s, expYears=%s, source=%s}",
                candidateId, name, noticePeriodDays,
                currentCtc.toPlainString(), expectedCtc.toPlainString(),
                experienceYears.toPlainString(), source
        );
    }
}
