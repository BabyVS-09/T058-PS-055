package com.ats;

import com.ats.model.Application;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.InterviewSlot;
import com.ats.model.Interviewer;
import com.ats.model.ReasonCode;
import com.ats.model.ScheduleResult;
import com.ats.service.InterviewScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for {@link InterviewScheduler} covering BR-08 through BR-14:
 * durations, daily limits, 15-min gap, window boundaries, L2 panel grade, weekends/holidays,
 * and stage preservation on NO_SLOT.
 */
class InterviewSchedulerTest {

    private InterviewScheduler scheduler;
    private Interviewer interviewerG4;
    private Interviewer interviewerG3;
    private Interviewer interviewerG5;
    private Application applicationG3;

    @BeforeEach
    void setUp() {
        scheduler = new InterviewScheduler();
        interviewerG3 = new Interviewer("INT-1", "Interviewer G3", GradeBand.G3);
        interviewerG4 = new Interviewer("INT-2", "Interviewer G4", GradeBand.G4);
        interviewerG5 = new Interviewer("INT-3", "Interviewer G5", GradeBand.G5);
        applicationG3 = new Application("APP-1", "CAN-1", "REQ-1", GradeBand.G3, LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("BR-08: Standard interview durations (L1=60m, L2=45m, HR=30m)")
    void shouldHaveStandardDurations() {
        assertEquals(60, InterviewRound.L1.getDurationMinutes());
        assertEquals(45, InterviewRound.L2.getDurationMinutes());
        assertEquals(30, InterviewRound.HR.getDurationMinutes());
    }

    @Test
    @DisplayName("BR-13: An interview ending exactly at the availability-window boundary is valid")
    void shouldAcceptInterviewEndingExactlyAtWindowBoundary() {
        // Window is 09:00 - 17:00. An L1 interview 16:00 - 17:00 ends exactly at 17:00
        ReasonCode result = scheduler.validateSlotForInterviewer(
                interviewerG4,
                LocalDate.of(2026, 3, 2),
                LocalTime.of(16, 0),
                LocalTime.of(17, 0)
        );
        assertEquals(ReasonCode.SUCCESS, result);

        // An interview extending past window (16:01 - 17:01) is invalid
        ReasonCode pastResult = scheduler.validateSlotForInterviewer(
                interviewerG4,
                LocalDate.of(2026, 3, 2),
                LocalTime.of(16, 1),
                LocalTime.of(17, 1)
        );
        assertEquals(ReasonCode.NO_SLOT, pastResult);
    }

    @Test
    @DisplayName("BR-10: Overlapping bookings are rejected")
    void shouldRejectOverlappingBookings() {
        LocalDate date = LocalDate.of(2026, 3, 2); // Monday
        InterviewSlot existing = new InterviewSlot(
                "APP-1",
                InterviewRound.L1,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                List.of(interviewerG4)
        );
        scheduler.bookSlot(existing);

        // Attempt overlap 10:30 - 11:30
        ReasonCode overlap = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(10, 30),
                LocalTime.of(11, 30)
        );
        assertEquals(ReasonCode.OVERLAPPING_INTERVIEW, overlap);
    }

    @Test
    @DisplayName("BR-10: 14-minute gap between interviews is rejected")
    void shouldReject14MinuteGap() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        InterviewSlot existing = new InterviewSlot(
                "APP-1",
                InterviewRound.L1,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                List.of(interviewerG4)
        );
        scheduler.bookSlot(existing);

        // Existing ends at 11:00. New interview starts at 11:14 (14-min gap) -> REJECTED
        ReasonCode gapAfter = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(11, 14),
                LocalTime.of(12, 14)
        );
        assertEquals(ReasonCode.INSUFFICIENT_INTERVIEW_GAP, gapAfter);

        // New interview ends at 09:46 before 10:00 (14-min gap, within window 09:00-17:00) -> REJECTED
        ReasonCode gapBefore = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(9, 46)
        );
        assertEquals(ReasonCode.INSUFFICIENT_INTERVIEW_GAP, gapBefore);
    }

    @Test
    @DisplayName("BR-10: Exactly 15-minute gap between interviews is accepted")
    void shouldAcceptExactly15MinuteGap() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        InterviewSlot existing = new InterviewSlot(
                "APP-1",
                InterviewRound.L1,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                List.of(interviewerG4)
        );
        scheduler.bookSlot(existing);

        // Existing ends at 11:00. New interview starts at 11:15 (exact 15-min gap) -> ACCEPTED
        ReasonCode gapAfter = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(11, 15),
                LocalTime.of(12, 15)
        );
        assertEquals(ReasonCode.SUCCESS, gapAfter);

        // New interview ends at 09:45 before 10:00 (exact 15-min gap, within window 09:00-17:00) -> ACCEPTED
        ReasonCode gapBefore = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(9, 0),
                LocalTime.of(9, 45)
        );
        assertEquals(ReasonCode.SUCCESS, gapBefore);
    }

    @Test
    @DisplayName("BR-09: Maximum three interviews per day accepted; fourth interview rejected")
    void shouldEnforceThreeInterviewDailyLimit() {
        LocalDate date = LocalDate.of(2026, 3, 2);

        // Book 3 non-overlapping interviews with adequate gaps
        scheduler.bookSlot(new InterviewSlot("APP-1", InterviewRound.L1, date, LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(interviewerG4)));
        scheduler.bookSlot(new InterviewSlot("APP-2", InterviewRound.L1, date, LocalTime.of(10, 30), LocalTime.of(11, 30), List.of(interviewerG4)));
        scheduler.bookSlot(new InterviewSlot("APP-3", InterviewRound.L1, date, LocalTime.of(12, 0), LocalTime.of(13, 0), List.of(interviewerG4)));

        // Verify fourth interview attempt on the same day is rejected
        ReasonCode fourthAttempt = scheduler.validateSlotForInterviewer(
                interviewerG4,
                date,
                LocalTime.of(14, 0),
                LocalTime.of(15, 0)
        );
        assertEquals(ReasonCode.DAILY_LIMIT_EXCEEDED, fourthAttempt);
    }

    @Test
    @DisplayName("BR-11: L2 panel grade must contain at least one interviewer whose grade is >= target grade + 1")
    void shouldEnforceL2PanelGradeRequirement() {
        // application targetGrade is G3. Required panel grade >= G4 (3 + 1).

        // Panel with only G3 interviewer: fails
        boolean panelFails = scheduler.validateL2PanelGrade(applicationG3.getTargetGrade(), List.of(interviewerG3));
        assertFalse(panelFails);

        ScheduleResult resultFails = scheduler.scheduleInterview(
                applicationG3,
                InterviewRound.L2,
                List.of(interviewerG3),
                LocalDate.of(2026, 3, 2),
                Collections.emptySet()
        );
        assertFalse(resultFails.success());
        assertEquals(ReasonCode.INSUFFICIENT_PANEL_GRADE, resultFails.reasonCode());

        // Panel with G3 and G4 interviewer: passes
        boolean panelPasses = scheduler.validateL2PanelGrade(applicationG3.getTargetGrade(), List.of(interviewerG3, interviewerG4));
        assertTrue(panelPasses);

        ScheduleResult resultPasses = scheduler.scheduleInterview(
                applicationG3,
                InterviewRound.L2,
                List.of(interviewerG3, interviewerG4),
                LocalDate.of(2026, 3, 2),
                Collections.emptySet()
        );
        assertTrue(resultPasses.success());
        assertEquals(ReasonCode.SUCCESS, resultPasses.reasonCode());
        assertNotNull(resultPasses.slot());
    }

    @Test
    @DisplayName("BR-12: Exclude weekends and supplied holidays when searching next 10 working days")
    void shouldExcludeWeekendsAndHolidays() {
        LocalDate searchStart = LocalDate.of(2026, 3, 6); // Friday
        LocalDate holidayMonday = LocalDate.of(2026, 3, 9); // Monday is holiday
        Set<LocalDate> holidays = Set.of(holidayMonday);

        List<LocalDate> workingDays = scheduler.getNextWorkingDays(searchStart, 5, holidays);

        // Expected 5 working days:
        // Day 1: 2026-03-06 (Friday)
        // Saturday 03-07, Sunday 03-08 excluded
        // Monday 03-09 excluded (holiday)
        // Day 2: 2026-03-10 (Tuesday)
        // Day 3: 2026-03-11 (Wednesday)
        // Day 4: 2026-03-12 (Thursday)
        // Day 5: 2026-03-13 (Friday)
        assertEquals(5, workingDays.size());
        assertEquals(LocalDate.of(2026, 3, 6), workingDays.get(0));
        assertEquals(LocalDate.of(2026, 3, 10), workingDays.get(1));
        assertEquals(LocalDate.of(2026, 3, 11), workingDays.get(2));
        assertEquals(LocalDate.of(2026, 3, 12), workingDays.get(3));
        assertEquals(LocalDate.of(2026, 3, 13), workingDays.get(4));
    }

    @Test
    @DisplayName("BR-14: Returns NO_SLOT and preserves candidate stage unchanged when no slot is available")
    void shouldReturnNoSlotAndPreserveCandidateStage() {
        // Book up all 3 daily slots on all 10 working days for interviewerG4
        LocalDate start = LocalDate.of(2026, 3, 2);
        List<LocalDate> days = scheduler.getNextWorkingDays(start, 10, Collections.emptySet());

        for (LocalDate day : days) {
            scheduler.bookSlot(new InterviewSlot("B1", InterviewRound.L1, day, LocalTime.of(9, 0), LocalTime.of(11, 0), List.of(interviewerG4)));
            scheduler.bookSlot(new InterviewSlot("B2", InterviewRound.L1, day, LocalTime.of(11, 30), LocalTime.of(13, 30), List.of(interviewerG4)));
            scheduler.bookSlot(new InterviewSlot("B3", InterviewRound.L1, day, LocalTime.of(14, 0), LocalTime.of(17, 0), List.of(interviewerG4)));
        }

        CandidateStage initialStage = applicationG3.getCurrentStage();
        ScheduleResult result = scheduler.scheduleInterview(
                applicationG3,
                InterviewRound.L1,
                List.of(interviewerG4),
                start,
                Collections.emptySet()
        );

        assertFalse(result.success());
        assertEquals(ReasonCode.NO_SLOT, result.reasonCode());
        // BR-14: Candidate's application stage remains unchanged!
        assertEquals(initialStage, applicationG3.getCurrentStage());
    }
}
