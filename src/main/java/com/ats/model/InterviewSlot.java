package com.ats.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a scheduled interview booking (BR-08 through BR-14).
 */
public class InterviewSlot {

    private final String applicationId;
    private final InterviewRound round;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final List<Interviewer> panel;

    public InterviewSlot(
            String applicationId,
            InterviewRound round,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            List<Interviewer> panel) {

        if (applicationId == null || applicationId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application ID is required.");
        }
        if (round == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview round is required.");
        }
        if (date == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview date is required.");
        }
        if (startTime == null || endTime == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Start time and end time are required.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new AtsException(ReasonCode.INVALID_DATE, "Interview end time must be after start time.");
        }
        if (panel == null || panel.isEmpty()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Panel cannot be null or empty.");
        }

        this.applicationId = applicationId.trim();
        this.round = round;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.panel = new ArrayList<>(panel);
    }

    public String getApplicationId() {
        return applicationId;
    }

    public InterviewRound getRound() {
        return round;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public List<Interviewer> getPanel() {
        return Collections.unmodifiableList(panel);
    }

    /**
     * Checks if this slot overlaps with another slot.
     */
    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    @Override
    public String toString() {
        return String.format("InterviewSlot{appId='%s', round=%s, date=%s, time=%s-%s, panelSize=%d}",
                applicationId, round, date, startTime, endTime, panel.size());
    }
}
