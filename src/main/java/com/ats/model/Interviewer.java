package com.ats.model;

import java.time.LocalTime;

/**
 * Represents an interviewer participating in recruitment panels (BR-08, BR-09, BR-10, BR-11).
 */
public class Interviewer {

    private final String interviewerId;
    private final String name;
    private final GradeBand gradeBand;
    private final LocalTime windowStart;
    private final LocalTime windowEnd;

    public Interviewer(String interviewerId, String name, GradeBand gradeBand) {
        this(interviewerId, name, gradeBand, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }

    public Interviewer(
            String interviewerId,
            String name,
            GradeBand gradeBand,
            LocalTime windowStart,
            LocalTime windowEnd) {

        if (interviewerId == null || interviewerId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interviewer ID is required.");
        }
        if (name == null || name.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interviewer name is required.");
        }
        if (gradeBand == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interviewer grade band is required.");
        }
        if (windowStart == null || windowEnd == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Availability window start and end are required.");
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new AtsException(ReasonCode.INVALID_DATE, "Availability window end must be after start.");
        }

        this.interviewerId = interviewerId.trim();
        this.name = name.trim();
        this.gradeBand = gradeBand;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public String getInterviewerId() {
        return interviewerId;
    }

    public String getName() {
        return name;
    }

    public GradeBand getGradeBand() {
        return gradeBand;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    @Override
    public String toString() {
        return String.format("Interviewer{id='%s', name='%s', grade=%s, window=%s-%s}",
                interviewerId, name, gradeBand, windowStart, windowEnd);
    }
}
