package com.ats.service;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.Candidate;
import com.ats.model.CandidateSource;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.model.RequisitionStatus;
import com.ats.model.TransitionResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core Week 2 backend service managing in-memory registration of requisitions,
 * candidates, applications, and stage transitions (BR-01 through BR-07).
 *
 * Designed for straightforward extension in later weeks (interview scheduling,
 * offer calculation, approval chains, and pipeline reporting).
 */
public class RecruitmentService {

    private final Map<String, Requisition> requisitions = new LinkedHashMap<>();
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();
    private final Map<String, Application> applications = new LinkedHashMap<>();
    private final StageTransitionService stageTransitionService;

    public RecruitmentService() {
        this(new StageTransitionService());
    }

    public RecruitmentService(StageTransitionService stageTransitionService) {
        if (stageTransitionService == null) {
            throw new IllegalArgumentException("stageTransitionService cannot be null");
        }
        this.stageTransitionService = stageTransitionService;
    }

    /**
     * Registers a new job requisition after validating fields and preventing duplicate IDs (BR-01).
     */
    public Requisition registerRequisition(
            String requisitionId,
            String role,
            GradeBand gradeBand,
            int headcount,
            String location,
            BigDecimal minCtc,
            BigDecimal maxCtc,
            String hiringManager,
            LocalDate openingDate) {

        Requisition requisition = new Requisition(
                requisitionId,
                role,
                gradeBand,
                headcount,
                location,
                minCtc,
                maxCtc,
                hiringManager,
                openingDate
        );

        String normalizedId = requisition.getRequisitionId();
        if (requisitions.containsKey(normalizedId)) {
            throw new AtsException(
                    ReasonCode.DUPLICATE_REQUISITION_ID,
                    "Requisition ID '" + normalizedId + "' is already registered."
            );
        }

        requisitions.put(normalizedId, requisition);
        return requisition;
    }

    /**
     * Registers a candidate with distinct ID and full name (BR-02).
     */
    public Candidate registerCandidate(
            String candidateId,
            String name,
            int noticePeriodDays,
            BigDecimal currentCtc,
            BigDecimal expectedCtc,
            BigDecimal experienceYears,
            CandidateSource source) {

        Candidate candidate = new Candidate(
                candidateId,
                name,
                noticePeriodDays,
                currentCtc,
                expectedCtc,
                experienceYears,
                source
        );

        String normalizedId = candidate.getCandidateId();
        if (candidates.containsKey(normalizedId)) {
            throw new AtsException(
                    ReasonCode.DUPLICATE_CANDIDATE_ID,
                    "Candidate ID '" + normalizedId + "' is already registered."
            );
        }

        candidates.put(normalizedId, candidate);
        return candidate;
    }

    /**
     * Registers a candidate when a single candidate ID or name identifier is provided (BR-02).
     */
    public Candidate registerCandidate(
            String candidateIdOrName,
            int noticePeriodDays,
            BigDecimal currentCtc,
            BigDecimal expectedCtc,
            BigDecimal experienceYears,
            CandidateSource source) {
        return registerCandidate(
                candidateIdOrName,
                candidateIdOrName,
                noticePeriodDays,
                currentCtc,
                expectedCtc,
                experienceYears,
                source
        );
    }

    /**
     * Applies a registered candidate to an open requisition on the current date (BR-03).
     */
    public Application applyCandidate(String applicationId, String candidateId, String requisitionId) {
        return applyCandidate(applicationId, candidateId, requisitionId, LocalDate.now());
    }

    /**
     * Applies a registered candidate to a registered requisition on a specified date (BR-03).
     */
    public Application applyCandidate(
            String applicationId,
            String candidateId,
            String requisitionId,
            LocalDate applicationDate) {

        if (applicationId == null || applicationId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application ID is required.");
        }
        if (candidateId == null || candidateId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Candidate ID is required.");
        }
        if (requisitionId == null || requisitionId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Requisition ID is required.");
        }
        if (applicationDate == null) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application date is required.");
        }

        String normalizedAppId = applicationId.trim();
        String normalizedCandidateId = candidateId.trim();
        String normalizedReqId = requisitionId.trim();

        if (applications.containsKey(normalizedAppId)) {
            throw new AtsException(
                    ReasonCode.DUPLICATE_APPLICATION_ID,
                    "Application ID '" + normalizedAppId + "' already exists."
            );
        }

        Candidate candidate = candidates.get(normalizedCandidateId);
        if (candidate == null) {
            throw new AtsException(
                    ReasonCode.CANDIDATE_NOT_FOUND,
                    "Candidate '" + normalizedCandidateId + "' is not registered."
            );
        }

        Requisition requisition = requisitions.get(normalizedReqId);
        if (requisition == null) {
            throw new AtsException(
                    ReasonCode.REQUISITION_NOT_FOUND,
                    "Requisition '" + normalizedReqId + "' is not registered."
            );
        }

        if (requisition.getStatus() == RequisitionStatus.CLOSED) {
            throw new AtsException(
                    ReasonCode.REQUISITION_CLOSED,
                    "Requisition '" + normalizedReqId + "' is CLOSED and cannot accept new applications."
            );
        }

        if (applicationDate.isBefore(requisition.getOpeningDate())) {
            throw new AtsException(
                    ReasonCode.INVALID_DATE,
                    "Application date (" + applicationDate + ") cannot be earlier than requisition opening date ("
                            + requisition.getOpeningDate() + ")."
            );
        }

        // Prevent the same candidate from submitting duplicate applications to the same requisition
        boolean alreadyApplied = applications.values().stream()
                .anyMatch(existing -> existing.getCandidateId().equals(normalizedCandidateId)
                        && existing.getRequisitionId().equals(normalizedReqId));
        if (alreadyApplied) {
            throw new AtsException(
                    ReasonCode.DUPLICATE_CANDIDATE_APPLICATION,
                    "Candidate '" + normalizedCandidateId + "' has already applied to requisition '" + normalizedReqId + "'."
            );
        }

        Application application = new Application(
                normalizedAppId,
                normalizedCandidateId,
                normalizedReqId,
                requisition.getGradeBand(),
                applicationDate
        );

        applications.put(normalizedAppId, application);
        return application;
    }

    /**
     * Transitions an application's stage or throws {@link AtsException} with a documented reason code (BR-04).
     */
    public TransitionResult transitionStage(String applicationId, CandidateStage targetStage) {
        Application application = getRequiredApplication(applicationId);
        Requisition requisition = requisitions.get(application.getRequisitionId());
        return stageTransitionService.transition(application, targetStage, requisition);
    }

    /**
     * Evaluates whether a stage transition would be valid without modifying state.
     */
    public TransitionResult evaluateStageTransition(String applicationId, CandidateStage targetStage) {
        Application application = getRequiredApplication(applicationId);
        Requisition requisition = requisitions.get(application.getRequisitionId());
        return stageTransitionService.evaluateTransition(application, targetStage, requisition);
    }

    /**
     * Records an interview score for L1, L2, or HR, enforcing BR-07 immediate rejection if score < 2.0.
     */
    public TransitionResult recordInterviewScore(String applicationId, InterviewRound round, BigDecimal score) {
        Application application = getRequiredApplication(applicationId);
        Requisition requisition = requisitions.get(application.getRequisitionId());
        return stageTransitionService.recordScore(application, round, score, requisition);
    }

    public Requisition getRequiredRequisition(String requisitionId) {
        if (requisitionId == null || requisitionId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Requisition ID is required.");
        }
        Requisition requisition = requisitions.get(requisitionId.trim());
        if (requisition == null) {
            throw new AtsException(
                    ReasonCode.REQUISITION_NOT_FOUND,
                    "Requisition '" + requisitionId.trim() + "' was not found."
            );
        }
        return requisition;
    }

    public Candidate getRequiredCandidate(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Candidate ID is required.");
        }
        Candidate candidate = candidates.get(candidateId.trim());
        if (candidate == null) {
            throw new AtsException(
                    ReasonCode.CANDIDATE_NOT_FOUND,
                    "Candidate '" + candidateId.trim() + "' was not found."
            );
        }
        return candidate;
    }

    public Application getRequiredApplication(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Application ID is required.");
        }
        Application application = applications.get(applicationId.trim());
        if (application == null) {
            throw new AtsException(
                    ReasonCode.APPLICATION_NOT_FOUND,
                    "Application '" + applicationId.trim() + "' was not found."
            );
        }
        return application;
    }

    public Collection<Requisition> getAllRequisitions() {
        return Collections.unmodifiableCollection(requisitions.values());
    }

    public Collection<Candidate> getAllCandidates() {
        return Collections.unmodifiableCollection(candidates.values());
    }

    public Collection<Application> getAllApplications() {
        return Collections.unmodifiableCollection(applications.values());
    }

    public List<Application> getApplicationsForRequisition(String requisitionId) {
        String normalizedId = requisitionId == null ? "" : requisitionId.trim();
        return applications.values().stream()
                .filter(app -> app.getRequisitionId().equals(normalizedId))
                .toList();
    }

    public StageTransitionService getStageTransitionService() {
        return stageTransitionService;
    }
}
