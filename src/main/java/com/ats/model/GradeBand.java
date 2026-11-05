package com.ats.model;

import java.util.Arrays;

/**
 * Job grade bands supported by the organization (BR-01: G1 to G6).
 * Includes numeric levels so later weeks can compare panel grades (e.g., BR-11: grade >= target + 1).
 */
public enum GradeBand {
    G1(1),
    G2(2),
    G3(3),
    G4(4),
    G5(5),
    G6(6);

    private final int level;

    GradeBand(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /**
     * Checks whether this grade is greater than or equal to another grade.
     */
    public boolean isAtLeast(GradeBand other) {
        return this.level >= other.level;
    }

    /**
     * Parses a string into a valid GradeBand (G1-G6).
     */
    public static GradeBand fromString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Grade band is required (expected G1-G6).");
        }
        String normalized = rawValue.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(grade -> grade.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new AtsException(
                        ReasonCode.INVALID_GRADE,
                        "Invalid grade band '" + rawValue.trim() + "'. Allowed values: G1, G2, G3, G4, G5, G6."
                ));
    }
}
