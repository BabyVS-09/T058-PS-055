package com.ats.service;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.CandidateStage;
import com.ats.model.InterviewRound;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.model.RequisitionStatus;
import com.ats.model.TransitionResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates and executes application stage transitions according to the PS-055
 * state machine and business rules (BR-04, BR-05, BR-06, BR-07, BR-30, BR-31).
 */
public class StageTransitionService {

    public static final BigDecimal MIN_VALID_SCORE = new BigDecimal("0.00");
    public static final BigDecimal MAX_VALID_SCORE = new BigDecimal("5.00");
    public static final BigDecimal IMMEDIATE_REJECT_THRESHOLD = new BigDecimal("2.00");
    public static final BigDecimal L1_TO_L2_PASS_THRESHOLD = new BigDecimal("3.00");
    public static final BigDecimal L2_TO_HR_PASS_THRESHOLD = new BigDecimal("3.50");

    private final Map<CandidateStage, Set<CandidateStage>> legalTransitions;
    private final boolean enforceMandatoryScores;

    /**
     * Default Week 2 constructor: enforces the full stage transition matrix and
     * enforces score thresholds whenever round scores have been recorded, while
     * allowing pure stage-transition flows before Week 3 interview scheduling is attached.
     */
    public StageTransitionService() {
        this(false);
    }

    /**
     * Configurable constructor allowing later weeks to require recorded round scores
     * before advancing into L2 or HR.
     */
    public StageTransitionService(boolean enforceMandatoryScores) {
        this.enforceMandatoryScores = enforceMandatoryScores;
        this.legalTransitions = buildTransitionMatrix();
    }

    private static Map<CandidateStage, Set<CandidateStage>> buildTransitionMatrix() {
        Map<CandidateStage, Set<CandidateStage>> matrix = new EnumMap<>(CandidateStage.class);

        // Primary forward pipeline + controlled side/exit states (Section 5.1)
        matrix.put(CandidateStage.SCREENED, EnumSet.of(
                CandidateStage.L1,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.L1, EnumSet.of(
                CandidateStage.L2,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.L2, EnumSet.of(
                CandidateStage.HR,
                CandidateStage.WAITLIST,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.HR, EnumSet.of(
                CandidateStage.OFFER_DRAFT,
                CandidateStage.WAITLIST,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.WAITLIST, EnumSet.of(
                CandidateStage.HR,
                CandidateStage.OFFER_DRAFT,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.ON_HOLD, EnumSet.of(
                CandidateStage.SCREENED,
                CandidateStage.L1,
                CandidateStage.L2,
                CandidateStage.HR,
                CandidateStage.OFFER_DRAFT,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.OFFER_DRAFT, EnumSet.of(
                CandidateStage.OFFER_APPROVED,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.OFFER_APPROVED, EnumSet.of(
                CandidateStage.OFFERED,
                CandidateStage.ON_HOLD,
                CandidateStage.REJECTED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.OFFERED, EnumSet.of(
                CandidateStage.ACCEPTED,
                CandidateStage.DECLINED,
                CandidateStage.WITHDRAWN
        ));

        matrix.put(CandidateStage.ACCEPTED, EnumSet.of(
                CandidateStage.JOINED,
                CandidateStage.DECLINED,
                CandidateStage.WITHDRAWN
        ));

        // Terminal stages have no legal outgoing transitions
        matrix.put(CandidateStage.JOINED, Collections.emptySet());
        matrix.put(CandidateStage.REJECTED, Collections.emptySet());
        matrix.put(CandidateStage.DECLINED, Collections.emptySet());
        matrix.put(CandidateStage.WITHDRAWN, Collections.emptySet());

        return Collections.unmodifiableMap(matrix);
    }

    /**
     * Returns the immutable set of allowed next stages for a given stage.
     */
    public Set<CandidateStage> getAllowedNextStages(CandidateStage currentStage) {
        if (currentStage == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(legalTransitions.getOrDefault(currentStage, Collections.emptySet()));
    }

    /**
     * Evaluates whether a stage transition is legal without mutating application state.
     */
    public TransitionResult evaluateTransition(
            Application application,
            CandidateStage targetStage,
            Requisition requisition) {

        if (application == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application cannot be null.");
        }
        if (targetStage == null) {
            return new TransitionResult(
                    false,
                    application.getApplicationId(),
                    application.getCurrentStage(),
                    null,
                    ReasonCode.MISSING_REQUIRED_FIELD,
                    "Target stage is required.",
                    LocalDateTime.now()
            );
        }

        CandidateStage currentStage = application.getCurrentStage();

        // 1. Reject no-op transition to the identical stage
        if (currentStage == targetStage) {
            return new TransitionResult(
                    false,
                    application.getApplicationId(),
                    currentStage,
                    targetStage,
                    ReasonCode.SAME_STAGE_TRANSITION,
                    "Application " + application.getApplicationId() + " is already in stage " + currentStage + ".",
                    LocalDateTime.now()
            );
        }

        // 2. Reject transitions out of terminal stages (JOINED, REJECTED, DECLINED, WITHDRAWN)
        if (currentStage.isTerminal()) {
            return new TransitionResult(
                    false,
                    application.getApplicationId(),
                    currentStage,
                    targetStage,
                    ReasonCode.TERMINAL_STAGE_LOCKED,
                    "Cannot transition application " + application.getApplicationId()
                            + " from terminal stage " + currentStage + " to " + targetStage + ".",
                    LocalDateTime.now()
            );
        }

        // 3. Validate against the state machine transition graph (BR-04)
        Set<CandidateStage> allowedTargets = legalTransitions.getOrDefault(currentStage, Collections.emptySet());
        if (!allowedTargets.contains(targetStage)) {
            return new TransitionResult(
                    false,
                    application.getApplicationId(),
                    currentStage,
                    targetStage,
                    ReasonCode.ILLEGAL_STAGE_TRANSITION,
                    "Illegal transition for application " + application.getApplicationId()
                            + ": " + currentStage + " -> " + targetStage
                            + ". Allowed next stages from " + currentStage + ": " + allowedTargets + ".",
                    LocalDateTime.now()
            );
        }

        // 4. Enforce BR-05 gate: L1 -> L2 requires L1 score >= 3.0
        if (currentStage == CandidateStage.L1 && targetStage == CandidateStage.L2) {
            BigDecimal l1Score = application.getL1Score();
            if (l1Score == null && enforceMandatoryScores) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.MISSING_INTERVIEW_SCORE,
                        "L1 score must be recorded before advancing application "
                                + application.getApplicationId() + " to L2.",
                        LocalDateTime.now()
                );
            }
            if (l1Score != null && l1Score.compareTo(L1_TO_L2_PASS_THRESHOLD) < 0) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.INSUFFICIENT_L1_SCORE,
                        "Application " + application.getApplicationId() + " L1 score ("
                                + l1Score.toPlainString() + ") is below required minimum 3.00 for L2.",
                        LocalDateTime.now()
                );
            }
        }

        // 5. Enforce BR-06 gate: L2 -> HR requires L2 score >= 3.5
        if (currentStage == CandidateStage.L2 && targetStage == CandidateStage.HR) {
            BigDecimal l2Score = application.getL2Score();
            if (l2Score == null && enforceMandatoryScores) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.MISSING_INTERVIEW_SCORE,
                        "L2 score must be recorded before advancing application "
                                + application.getApplicationId() + " to HR.",
                        LocalDateTime.now()
                );
            }
            if (l2Score != null && l2Score.compareTo(L2_TO_HR_PASS_THRESHOLD) < 0) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.INSUFFICIENT_L2_SCORE,
                        "Application " + application.getApplicationId() + " L2 score ("
                                + l2Score.toPlainString() + ") is below required minimum 3.50 for HR.",
                        LocalDateTime.now()
                );
            }
        }

        // 6. Enforce requisition state and headcount check when entering OFFER_DRAFT (BR-31)
        if (requisition != null && !currentStage.reservesHeadcount() && targetStage.reservesHeadcount()) {
            if (requisition.getStatus() == RequisitionStatus.CLOSED) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.REQUISITION_CLOSED,
                        "Requisition " + requisition.getRequisitionId() + " is CLOSED and cannot draft new offers.",
                        LocalDateTime.now()
                );
            }
            if (!requisition.hasAvailableHeadcount()) {
                return new TransitionResult(
                        false,
                        application.getApplicationId(),
                        currentStage,
                        targetStage,
                        ReasonCode.HEADCOUNT_EXHAUSTED,
                        "No available headcount on requisition " + requisition.getRequisitionId()
                                + " (reserved " + requisition.getReservedHeadcount() + "/"
                                + requisition.getHeadcount() + "). Move candidate to WAITLIST instead.",
                        LocalDateTime.now()
                );
            }
        }

        return new TransitionResult(
                true,
                application.getApplicationId(),
                currentStage,
                targetStage,
                ReasonCode.SUCCESS,
                "Stage transitioned from " + currentStage + " to " + targetStage + ".",
                LocalDateTime.now()
        );
    }

    /**
     * Validates and applies a stage transition, throwing {@link AtsException} with the
     * exact {@link ReasonCode} if the transition is illegal.
     */
    public TransitionResult transition(Application application, CandidateStage targetStage, Requisition requisition) {
        TransitionResult evaluation = evaluateTransition(application, targetStage, requisition);
        if (!evaluation.allowed()) {
            throw new AtsException(evaluation.reasonCode(), evaluation.message());
        }

        CandidateStage previousStage = application.getCurrentStage();

        // Update headcount reservation on the linked requisition when crossing offer-reservation boundaries
        if (requisition != null) {
            if (!previousStage.reservesHeadcount() && targetStage.reservesHeadcount()) {
                requisition.reserveHeadcount();
            } else if (previousStage.reservesHeadcount() && !targetStage.reservesHeadcount()) {
                requisition.releaseHeadcount();
            }
        }

        application.applyTransition(targetStage, ReasonCode.SUCCESS, evaluation.message());
        return evaluation;
    }

    /**
     * Records an interview round score and enforces BR-07 (immediate rejection if score < 2.0).
     */
    public TransitionResult recordScore(
            Application application,
            InterviewRound round,
            BigDecimal score,
            Requisition requisition) {

        if (application == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application cannot be null.");
        }
        if (round == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview round is required.");
        }
        if (score == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview score is required.");
        }
        if (score.compareTo(MIN_VALID_SCORE) < 0 || score.compareTo(MAX_VALID_SCORE) > 0) {
            throw new AtsException(
                    ReasonCode.INVALID_SCORE,
                    "Interview score must be between 0.00 and 5.00 (received: " + score.toPlainString() + ")."
            );
        }
        if (application.getCurrentStage().isTerminal()) {
            throw new AtsException(
                    ReasonCode.TERMINAL_STAGE_LOCKED,
                    "Cannot record score for application " + application.getApplicationId()
                            + " because it is in terminal stage " + application.getCurrentStage() + "."
            );
        }

        switch (round) {
            case L1 -> application.setL1Score(score);
            case L2 -> application.setL2Score(score);
            case HR -> application.setHrScore(score);
        }

        CandidateStage previousStage = application.getCurrentStage();

        // BR-07: Any score < 2.0 in any round moves the application to REJECTED immediately
        if (score.compareTo(IMMEDIATE_REJECT_THRESHOLD) < 0) {
            if (requisition != null && previousStage.reservesHeadcount()) {
                requisition.releaseHeadcount();
            }
            String message = round + " score " + score.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " is below 2.00; application " + application.getApplicationId()
                    + " automatically moved to REJECTED.";
            application.applyTransition(CandidateStage.REJECTED, ReasonCode.AUTO_REJECTED_LOW_SCORE, message);
            return new TransitionResult(
                    true,
                    application.getApplicationId(),
                    previousStage,
                    CandidateStage.REJECTED,
                    ReasonCode.AUTO_REJECTED_LOW_SCORE,
                    message,
                    LocalDateTime.now()
            );
        }

        return new TransitionResult(
                true,
                application.getApplicationId(),
                previousStage,
                previousStage,
                ReasonCode.SUCCESS,
                round + " score " + score.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " recorded for application " + application.getApplicationId() + ".",
                LocalDateTime.now()
        );
    }
}
