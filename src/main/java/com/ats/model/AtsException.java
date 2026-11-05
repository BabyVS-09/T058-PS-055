package com.ats.model;

import java.util.Objects;

/**
 * Custom domain exception carrying a structured {@link ReasonCode} for
 * validation failures, duplicate registrations, and illegal stage transitions.
 */
public class AtsException extends RuntimeException {

    private final ReasonCode reasonCode;

    public AtsException(ReasonCode reasonCode, String message) {
        super(message);
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    }

    public AtsException(ReasonCode reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }
}
