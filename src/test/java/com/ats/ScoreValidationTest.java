package com.ats;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.ReasonCode;
import com.ats.model.TransitionResult;
import com.ats.service.StageTransitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Interview Score Validation (Module 2, BR-05, BR-06, BR-07):
 * L1 >= 3.0, L2 >= 3.5, 0.0-5.0 range, negative scores, score > 5, score < 2.0 auto-rejection,
 * null score validation.
 */
class ScoreValidationTest {

    private StageTransitionService transitionService;
    private Application application;

    @BeforeEach
    void setUp() {
        transitionService = new StageTransitionService(true); // strict mandatory score enforcement
        application = new Application("APP-SCORE-1", "CAN-1", "REQ-1", GradeBand.G3, LocalDate.of(2026, 3, 1));
        // Advance to L1
        transitionService.transition(application, CandidateStage.L1, null);
    }

    @Test
    @DisplayName("Module 2: L1 score exactly 3.0 passes L1 -> L2 gate")
    void shouldPassL1AtExactly3Point0() {
        TransitionResult scoreRes = transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("3.0"), null);
        assertEquals(ReasonCode.SUCCESS, scoreRes.reasonCode());

        TransitionResult transRes = transitionService.transition(application, CandidateStage.L2, null);
        assertTrue(transRes.allowed());
        assertEquals(CandidateStage.L2, application.getCurrentStage());
    }

    @Test
    @DisplayName("Module 2: L1 score below 3.0 fails L1 -> L2 gate")
    void shouldRejectL1Below3Point0() {
        transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("2.99"), null);

        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.transition(application, CandidateStage.L2, null));
        assertEquals(ReasonCode.INSUFFICIENT_L1_SCORE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: L2 score exactly 3.5 passes L2 -> HR gate")
    void shouldPassL2AtExactly3Point5() {
        // Pass L1 with 3.0
        transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("3.0"), null);
        transitionService.transition(application, CandidateStage.L2, null);

        // Record L2 with exact 3.5
        TransitionResult scoreRes = transitionService.recordScore(application, InterviewRound.L2, new BigDecimal("3.5"), null);
        assertEquals(ReasonCode.SUCCESS, scoreRes.reasonCode());

        TransitionResult transRes = transitionService.transition(application, CandidateStage.HR, null);
        assertTrue(transRes.allowed());
        assertEquals(CandidateStage.HR, application.getCurrentStage());
    }

    @Test
    @DisplayName("Module 2: L2 score below 3.5 fails L2 -> HR gate")
    void shouldRejectL2Below3Point5() {
        // Pass L1 with 4.0
        transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("4.0"), null);
        transitionService.transition(application, CandidateStage.L2, null);

        // Record L2 with 3.49
        transitionService.recordScore(application, InterviewRound.L2, new BigDecimal("3.49"), null);

        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.transition(application, CandidateStage.HR, null));
        assertEquals(ReasonCode.INSUFFICIENT_L2_SCORE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: Boundary score 0.0 is valid but triggers BR-07 auto-rejection")
    void shouldAcceptBoundaryScore0AndAutoReject() {
        TransitionResult result = transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("0.0"), null);
        assertEquals(ReasonCode.AUTO_REJECTED_LOW_SCORE, result.reasonCode());
        assertEquals(CandidateStage.REJECTED, application.getCurrentStage());
    }

    @Test
    @DisplayName("Module 2: Boundary score 5.0 is valid and passes")
    void shouldAcceptBoundaryScore5Point0() {
        TransitionResult result = transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("5.0"), null);
        assertEquals(ReasonCode.SUCCESS, result.reasonCode());
        assertEquals(new BigDecimal("5.00"), application.getL1Score());
    }

    @Test
    @DisplayName("Module 2: Negative score is rejected with INVALID_SCORE")
    void shouldRejectNegativeScore() {
        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("-0.1"), null));
        assertEquals(ReasonCode.INVALID_SCORE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: Score above 5.0 is rejected with INVALID_SCORE")
    void shouldRejectScoreAbove5Point0() {
        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("5.01"), null));
        assertEquals(ReasonCode.INVALID_SCORE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: Null score is rejected with MISSING_REQUIRED_FIELD")
    void shouldRejectNullScore() {
        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.recordScore(application, InterviewRound.L1, null, null));
        assertEquals(ReasonCode.MISSING_REQUIRED_FIELD, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: Missing L1 score when required rejects transition with MISSING_INTERVIEW_SCORE")
    void shouldRejectAdvancingWhenMandatoryScoreMissing() {
        // In strict mode (enforceMandatoryScores = true), advancing without recording score fails
        AtsException ex = assertThrows(AtsException.class,
                () -> transitionService.transition(application, CandidateStage.L2, null));
        assertEquals(ReasonCode.MISSING_INTERVIEW_SCORE, ex.getReasonCode());
    }

    @Test
    @DisplayName("Module 2: Any score < 2.0 (e.g. 1.95) triggers BR-07 immediate rejection")
    void shouldAutoRejectScoreBelow2Point0() {
        TransitionResult result = transitionService.recordScore(application, InterviewRound.L1, new BigDecimal("1.95"), null);
        assertEquals(ReasonCode.AUTO_REJECTED_LOW_SCORE, result.reasonCode());
        assertEquals(CandidateStage.REJECTED, application.getCurrentStage());
    }
}
