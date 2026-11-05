package com.ats.parser;

import com.ats.model.ReasonCode;

/**
 * Captures the outcome of executing a single command line from a command file.
 */
public record CommandExecutionResult(
        int lineNumber,
        String rawCommand,
        boolean success,
        ReasonCode reasonCode,
        String message
) {
}
