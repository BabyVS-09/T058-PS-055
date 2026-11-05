package com.ats.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Links a registered {@link Candidate} to a {@link Requisition} and tracks its
 * independent recruitment stage, interview round scores, and transition audit trail (BR-03).
 */
public class Application {

    private final String applicationId;
    private final String candidateId;
    private final String requisitionId;
    private final GradeBand targetGrade;
    private final LocalDate applicationDate;
    private final LocalDateTime createdAt;

    private CandidateStage currentStage;
    private BigDecimal l1Score;
    private BigDecimal l2Score;
    private BigDecimal hrScore;
    private Integer waitlistPosition;
    private final List<StageTransitionRecord> stageHistory;

    public Application(
            String applicationId,
            String candidateId,
            String requisitionId,
            GradeBand targetGrade,
            LocalDate applicationDate) {

        this.applicationId = requireText(applicationId, "Application ID");
        this.candidateId = requireText(candidateId, "Candidate ID");
        this.requisitionId = requireText(requisitionId, "Requisition ID");

        if (targetGrade == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Target grade is required.");
        }
        if (applicationDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application date is required.");
        }

        this.targetGrade = targetGrade;
        this.applicationDate = applicationDate;
        this.createdAt = LocalDateTime.now();
        this.currentStage = CandidateStage.SCREENED;
        this.stageHistory = new ArrayList<>();
        this.stageHistory.add(new StageTransitionRecord(
                null,
                CandidateStage.SCREENED,
                this.createdAt,
                ReasonCode.SUCCESS,
                "Initial application created in SCREENED stage"
        ));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required and cannot be blank.");
        }
        return value.trim();
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getRequisitionId() {
        return requisitionId;
    }

    public GradeBand getTargetGrade() {
        return targetGrade;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public CandidateStage getCurrentStage() {
        return currentStage;
    }

    /**
     * Updates the current stage and appends an immutable audit record.
     */
    public void applyTransition(CandidateStage nextStage, ReasonCode reasonCode, String remarks) {
        CandidateStage previous = this.currentStage;
        this.currentStage = nextStage;
        this.stageHistory.add(new StageTransitionRecord(
                previous,
                nextStage,
                LocalDateTime.now(),
                reasonCode,
                remarks
        ));
    }

    public BigDecimal getL1Score() {
        return l1Score;
    }

    public void setL1Score(BigDecimal l1Score) {
        this.l1Score = normalizeScore(l1Score);
    }

    public BigDecimal getL2Score() {
        return l2Score;
    }

    public void setL2Score(BigDecimal l2Score) {
        this.l2Score = normalizeScore(l2Score);
    }

    public BigDecimal getHrScore() {
        return hrScore;
    }

    public void setHrScore(BigDecimal hrScore) {
        this.hrScore = normalizeScore(hrScore);
    }

    private static BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public void setWaitlistPosition(Integer waitlistPosition) {
        this.waitlistPosition = waitlistPosition;
    }

    public List<StageTransitionRecord> getStageHistory() {
        return Collections.unmodifiableList(stageHistory);
    }

    @Override
    public String toString() {
        return String.format(
                "Application{id='%s', candidateId='%s', requisitionId='%s', targetGrade=%s, stage=%s, appliedDate=%s}",
                applicationId, candidateId, requisitionId, targetGrade, currentStage, applicationDate
        );
    }
}
