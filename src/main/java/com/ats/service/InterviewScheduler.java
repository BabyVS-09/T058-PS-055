package com.ats.service;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.InterviewSlot;
import com.ats.model.Interviewer;
import com.ats.model.ReasonCode;
import com.ats.model.ScheduleResult;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for interview scheduling, slot search, and calendar constraint validation
 * (BR-08, BR-09, BR-10, BR-11, BR-12, BR-13, BR-14).
 */
public class InterviewScheduler {

    public static final int MAX_DAILY_INTERVIEWS_PER_INTERVIEWER = 3;
    public static final int MIN_INTERVIEW_GAP_MINUTES = 15;
    public static final int WORKING_DAYS_HORIZON = 10;
    public static final int SLOT_SEARCH_INCREMENT_MINUTES = 15;

    // Track bookings per interviewer: interviewerId -> List<InterviewSlot>
    private final Map<String, List<InterviewSlot>> interviewerBookings = new HashMap<>();
    private final List<InterviewSlot> allScheduledSlots = new ArrayList<>();

    /**
     * Checks if a date is a working day (Mon-Fri) and not in the holiday set.
     */
    public boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        if (date == null) {
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return holidays == null || !holidays.contains(date);
    }

    /**
     * Computes a list of the next N working days starting on or after the given startDate.
     */
    public List<LocalDate> getNextWorkingDays(LocalDate startDate, int count, Set<LocalDate> holidays) {
        List<LocalDate> workingDays = new ArrayList<>();
        LocalDate current = startDate;
        while (workingDays.size() < count) {
            if (isWorkingDay(current, holidays)) {
                workingDays.add(current);
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    /**
     * Validates that an L2 interview panel contains at least one interviewer whose
     * grade is at least one grade higher than the target grade (BR-11).
     */
    public boolean validateL2PanelGrade(GradeBand targetGrade, List<Interviewer> panel) {
        if (targetGrade == null || panel == null || panel.isEmpty()) {
            return false;
        }
        int requiredLevel = targetGrade.getLevel() + 1;
        for (Interviewer interviewer : panel) {
            if (interviewer.getGradeBand().getLevel() >= requiredLevel) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates whether a specific interviewer can accept a proposed slot on a given date.
     * Checks:
     * 1. Window bounds (slot ending exactly at windowEnd is valid).
     * 2. Daily limit <= 3.
     * 3. No overlap with existing bookings.
     * 4. Minimum 15-minute gap from any existing booking on that date.
     */
    public ReasonCode validateSlotForInterviewer(
            Interviewer interviewer,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime) {

        // Check availability window
        if (startTime.isBefore(interviewer.getWindowStart()) || endTime.isAfter(interviewer.getWindowEnd())) {
            return ReasonCode.NO_SLOT;
        }

        List<InterviewSlot> bookings = interviewerBookings.getOrDefault(interviewer.getInterviewerId(), Collections.emptyList());
        long dailyCount = bookings.stream()
                .filter(slot -> slot.getDate().equals(date))
                .count();

        if (dailyCount >= MAX_DAILY_INTERVIEWS_PER_INTERVIEWER) {
            return ReasonCode.DAILY_LIMIT_EXCEEDED;
        }

        for (InterviewSlot existing : bookings) {
            if (!existing.getDate().equals(date)) {
                continue;
            }

            // Overlap check
            if (startTime.isBefore(existing.getEndTime()) && endTime.isAfter(existing.getStartTime())) {
                return ReasonCode.OVERLAPPING_INTERVIEW;
            }

            // Gap check: if slot is after existing, startTime must be >= existing.endTime + 15 min
            if (startTime.isAfter(existing.getEndTime()) || startTime.equals(existing.getEndTime())) {
                long minutesBetween = Duration.between(existing.getEndTime(), startTime).toMinutes();
                if (minutesBetween < MIN_INTERVIEW_GAP_MINUTES) {
                    return ReasonCode.INSUFFICIENT_INTERVIEW_GAP;
                }
            }

            // Gap check: if slot is before existing, endTime + 15 min must be <= existing.startTime
            if (endTime.isBefore(existing.getStartTime()) || endTime.equals(existing.getStartTime())) {
                long minutesBetween = Duration.between(endTime, existing.getStartTime()).toMinutes();
                if (minutesBetween < MIN_INTERVIEW_GAP_MINUTES) {
                    return ReasonCode.INSUFFICIENT_INTERVIEW_GAP;
                }
            }
        }

        return ReasonCode.SUCCESS;
    }

    /**
     * Searches for the earliest valid interview slot across the next 10 working days (BR-12).
     * Returns ScheduleResult with the slot or NO_SLOT.
     * Note: Does NOT modify the candidate's application stage (BR-14).
     */
    public ScheduleResult scheduleInterview(
            Application application,
            InterviewRound round,
            List<Interviewer> panel,
            LocalDate searchStartDate,
            Set<LocalDate> holidays) {

        if (application == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application is required.");
        }
        if (round == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview round is required.");
        }
        if (panel == null || panel.isEmpty()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview panel cannot be empty.");
        }
        if (searchStartDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Search start date is required.");
        }

        // BR-11: Validate L2 panel grade requirement upfront
        if (round == InterviewRound.L2 && !validateL2PanelGrade(application.getTargetGrade(), panel)) {
            return ScheduleResult.failure(
                    ReasonCode.INSUFFICIENT_PANEL_GRADE,
                    "L2 panel must contain at least one interviewer with grade at least "
                            + (application.getTargetGrade().getLevel() + 1) + " (target grade: "
                            + application.getTargetGrade() + ")."
            );
        }

        int durationMinutes = round.getDurationMinutes();
        List<LocalDate> workingDays = getNextWorkingDays(searchStartDate, WORKING_DAYS_HORIZON, holidays);

        for (LocalDate date : workingDays) {
            // Determine intersection of availability windows for all panel members
            LocalTime windowStart = panel.stream()
                    .map(Interviewer::getWindowStart)
                    .max(LocalTime::compareTo)
                    .orElse(LocalTime.of(9, 0));

            LocalTime windowEnd = panel.stream()
                    .map(Interviewer::getWindowEnd)
                    .min(LocalTime::compareTo)
                    .orElse(LocalTime.of(17, 0));

            if (!windowEnd.isAfter(windowStart)) {
                continue;
            }

            LocalTime candidateStart = windowStart;
            while (!candidateStart.plusMinutes(durationMinutes).isAfter(windowEnd)) {
                LocalTime candidateEnd = candidateStart.plusMinutes(durationMinutes);

                boolean allPanelAvailable = true;
                for (Interviewer interviewer : panel) {
                    ReasonCode validation = validateSlotForInterviewer(interviewer, date, candidateStart, candidateEnd);
                    if (validation != ReasonCode.SUCCESS) {
                        allPanelAvailable = false;
                        break;
                    }
                }

                if (allPanelAvailable) {
                    InterviewSlot slot = new InterviewSlot(
                            application.getApplicationId(),
                            round,
                            date,
                            candidateStart,
                            candidateEnd,
                            panel
                    );
                    bookSlot(slot);
                    return ScheduleResult.successful(
                            slot,
                            String.format("Scheduled %s interview for application %s on %s from %s to %s with %d interviewer(s).",
                                    round, application.getApplicationId(), date, candidateStart, candidateEnd, panel.size())
                    );
                }

                candidateStart = candidateStart.plusMinutes(SLOT_SEARCH_INCREMENT_MINUTES);
            }
        }

        return ScheduleResult.failure(
            ReasonCode.NO_SLOT,
            "NO_SLOT: No valid interview slot found within the next " + WORKING_DAYS_HORIZON + " working days."
        );
    }

    /**
     * Directly books an interview slot into the scheduler.
     */
    public void bookSlot(InterviewSlot slot) {
        for (Interviewer interviewer : slot.getPanel()) {
            interviewerBookings.computeIfAbsent(interviewer.getInterviewerId(), k -> new ArrayList<>()).add(slot);
        }
        allScheduledSlots.add(slot);
    }

    public List<InterviewSlot> getBookingsForInterviewer(String interviewerId) {
        return Collections.unmodifiableList(interviewerBookings.getOrDefault(interviewerId, Collections.emptyList()));
    }

    public List<InterviewSlot> getAllScheduledSlots() {
        return Collections.unmodifiableList(allScheduledSlots);
    }
}
