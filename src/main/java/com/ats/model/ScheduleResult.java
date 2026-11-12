package com.ats.model;

/**
 * Result of an interview scheduling attempt (BR-12, BR-13, BR-14).
 */
public record ScheduleResult(
        boolean success,
        InterviewSlot slot,
        ReasonCode reasonCode,
        String message
) {
    public static ScheduleResult successful(InterviewSlot slot, String message) {
        return new ScheduleResult(true, slot, ReasonCode.SUCCESS, message);
    }

    public static ScheduleResult failure(ReasonCode reasonCode, String message) {
        return new ScheduleResult(false, null, reasonCode, message);
    }
}
