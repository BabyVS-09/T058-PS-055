package com.ats.model;

import java.util.Arrays;

/**
 * All lifecycle stages of a candidate's application to a requisition (Section 5.1, BR-04).
 */
public enum CandidateStage {
    SCREENED(false, false),
    L1(false, false),
    L2(false, false),
    HR(false, false),
    OFFER_DRAFT(false, true),
    OFFER_APPROVED(false, true),
    OFFERED(false, true),
    ACCEPTED(false, true),
    DECLINED(true, false),
    JOINED(true, true),
    REJECTED(true, false),
    WAITLIST(false, false),
    ON_HOLD(false, false),
    WITHDRAWN(true, false);

    private final boolean terminal;
    private final boolean reservesHeadcount;

    CandidateStage(boolean terminal, boolean reservesHeadcount) {
        this.terminal = terminal;
        this.reservesHeadcount = reservesHeadcount;
    }

    /**
     * Terminal stages cannot transition to any other stage.
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Indicates whether an application in this stage occupies one requisition headcount (BR-30, BR-31).
     */
    public boolean reservesHeadcount() {
        return reservesHeadcount;
    }

    /**
     * Parses a string into a valid CandidateStage.
     */
    public static CandidateStage fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Candidate stage is required.");
        }
        String normalized = rawValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(stage -> stage.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new AtsException(
                        ReasonCode.INVALID_STAGE,
                        "Invalid candidate stage '" + rawValue.trim() + "'."
                ));
    }
}
