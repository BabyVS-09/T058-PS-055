
package com.ats.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable audit record for an offer approval decision.
 */
public record ApprovalRecord(
        ApproverRole approverRole,
        ApprovalDecision decision,
        LocalDate deadline,
        LocalDateTime decidedAt,
        String remarks) {

    public ApprovalRecord {
        if (approverRole == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Approver role is required."
            );
        }

        if (decision == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Approval decision is required."
            );
        }

        if (deadline == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Approval deadline is required."
            );
        }

        if (decidedAt == null) {
            throw new AtsException(
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Decision date and time are required."
            );
        }

        remarks = remarks == null ? "" : remarks.trim();
    }
}
