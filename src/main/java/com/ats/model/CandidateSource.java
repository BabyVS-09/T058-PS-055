package com.ats.model;

import java.util.Arrays;

/**
 * Recruitment channel through which a candidate was sourced (BR-02).
 * Used in later weeks for bonus exemptions (BR-20) and referral/agency fees (BR-21, BR-22).
 */
public enum CandidateSource {
    REFERRAL,
    PORTAL,
    AGENCY,
    INTERNAL;

    /**
     * Parses a string into a valid CandidateSource.
     */
    public static CandidateSource fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Candidate source is required (REFERRAL, PORTAL, AGENCY, INTERNAL)."
            );
        }
        String normalized = rawValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(source -> source.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new AtsException(
                        ReasonCode.INVALID_SOURCE,
                        "Invalid candidate source '" + rawValue.trim()
                                + "'. Allowed values: REFERRAL, PORTAL, AGENCY, INTERNAL."
                ));
    }
}
