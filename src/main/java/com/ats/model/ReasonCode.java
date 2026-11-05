package com.ats.model;

/**
 * Standardized reason codes used across validation errors and stage transitions.
 * Aligns with Week 1 Design Document business rules (BR-01 through BR-07, BR-31, BR-38).
 */
public enum ReasonCode {
    SUCCESS,

    // Duplicate ID errors
    DUPLICATE_REQUISITION_ID,
    DUPLICATE_CANDIDATE_ID,
    DUPLICATE_APPLICATION_ID,
    DUPLICATE_CANDIDATE_APPLICATION,

    // Field & domain validation errors (BR-01, BR-02, BR-03)
    MISSING_REQUIRED_FIELD,
    INVALID_GRADE,
    INVALID_HEADCOUNT,
    INVALID_CTC,
    INVALID_NOTICE_PERIOD,
    INVALID_EXPERIENCE,
    INVALID_SOURCE,
    INVALID_DATE,
    INVALID_STAGE,
    INVALID_INTERVIEW_ROUND,
    INVALID_SCORE,

    // Entity lookup & state errors
    REQUISITION_NOT_FOUND,
    REQUISITION_CLOSED,
    CANDIDATE_NOT_FOUND,
    APPLICATION_NOT_FOUND,

    // Stage transition & gate errors (BR-04, BR-05, BR-06, BR-07, BR-31)
    SAME_STAGE_TRANSITION,
    TERMINAL_STAGE_LOCKED,
    ILLEGAL_STAGE_TRANSITION,
    INSUFFICIENT_L1_SCORE,
    INSUFFICIENT_L2_SCORE,
    MISSING_INTERVIEW_SCORE,
    AUTO_REJECTED_LOW_SCORE,
    HEADCOUNT_EXHAUSTED,

    // Command file parser errors (BR-38)
    UNKNOWN_COMMAND,
    INVALID_COMMAND_ARGUMENTS
}
