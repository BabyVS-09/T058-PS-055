package com.ats.model;

import java.util.Arrays;

/**
 * Interview rounds with fixed standard durations in minutes (BR-08).
 */
public enum InterviewRound {
    L1(60),
    L2(45),
    HR(30);

    private final int durationMinutes;

    InterviewRound(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public static InterviewRound fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview round is required (L1, L2, HR).");
        }
        String normalized = rawValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(round -> round.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new AtsException(
                        ReasonCode.INVALID_INTERVIEW_ROUND,
                        "Invalid interview round '" + rawValue.trim() + "'. Allowed values: L1, L2, HR."
                ));
    }
}
