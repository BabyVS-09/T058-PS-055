package com.ats.model;

import java.time.LocalDateTime;

/**
 * Immutable audit entry recording an application stage change and timestamp.
 */
public record StageTransitionRecord(
        CandidateStage fromStage,
        CandidateStage toStage,
        LocalDateTime transitionedAt,
        ReasonCode reasonCode,
        String remarks
) {
}
