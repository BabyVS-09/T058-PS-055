package com.ats.model;

import java.time.LocalDateTime;

/**
 * Structured outcome of evaluating or executing a stage transition (BR-04).
 */
public record TransitionResult(
        boolean allowed,
        String applicationId,
        CandidateStage fromStage,
        CandidateStage toStage,
        ReasonCode reasonCode,
        String message,
        LocalDateTime evaluatedAt
) {
}
