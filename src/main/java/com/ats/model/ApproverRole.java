package com.ats.model;

/**
 * Roles in the sequential offer approval matrix (BR-24, BR-25, BR-26, BR-28).
 */
public enum ApproverRole {
    HIRING_MANAGER(false),
    HR_HEAD(false),
    VP(true),
    CFO(true);

    private final boolean nonSkippable;

    ApproverRole(boolean nonSkippable) {
        this.nonSkippable = nonSkippable;
    }

    /**
     * VP and CFO must never be automatically skipped on timeout (BR-28, DEC-05).
     */
    public boolean isNonSkippable() {
        return nonSkippable;
    }
}
