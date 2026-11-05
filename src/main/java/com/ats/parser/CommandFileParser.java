package com.ats.parser;

import com.ats.model.Application;
import com.ats.model.AtsException;
import com.ats.model.Candidate;
import com.ats.model.CandidateSource;
import com.ats.model.CandidateStage;
import com.ats.model.GradeBand;
import com.ats.model.InterviewRound;
import com.ats.model.ReasonCode;
import com.ats.model.Requisition;
import com.ats.model.TransitionResult;
import com.ats.service.RecruitmentService;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads structured pipe-delimited ATS commands from a text file, validates arguments,
 * invokes {@link RecruitmentService}, and prints deterministic results (BR-38).
 */
public class CommandFileParser {

    private final RecruitmentService recruitmentService;
    private final PrintStream out;

    public CommandFileParser(RecruitmentService recruitmentService) {
        this(recruitmentService, System.out);
    }

    public CommandFileParser(RecruitmentService recruitmentService, PrintStream out) {
        if (recruitmentService == null) {
            throw new IllegalArgumentException("recruitmentService cannot be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("output stream cannot be null");
        }
        this.recruitmentService = recruitmentService;
        this.out = out;
    }

    /**
     * Reads and executes all non-comment, non-blank commands from the specified file path.
     */
    public List<CommandExecutionResult> processFile(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        return processLines(lines);
    }

    /**
     * Executes a list of raw command lines in order and returns structured results.
     */
    public List<CommandExecutionResult> processLines(List<String> lines) {
        List<CommandExecutionResult> results = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String rawLine = lines.get(index);
            if (rawLine == null) {
                continue;
            }

            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            CommandExecutionResult result = executeSingleLine(lineNumber, trimmed);
            results.add(result);
        }

        return results;
    }

    private CommandExecutionResult executeSingleLine(int lineNumber, String line) {
        String[] rawTokens = line.split("\\|", -1);
        String[] tokens = new String[rawTokens.length];
        for (int i = 0; i < rawTokens.length; i++) {
            tokens[i] = rawTokens[i].trim();
        }

        String commandKeyword = tokens[0].toUpperCase();

        try {
            return switch (commandKeyword) {
                case "REGISTER_REQUISITION" -> handleRegisterRequisition(lineNumber, line, tokens);
                case "REGISTER_CANDIDATE" -> handleRegisterCandidate(lineNumber, line, tokens);
                case "APPLY" -> handleApply(lineNumber, line, tokens);
                case "RECORD_SCORE" -> handleRecordScore(lineNumber, line, tokens);
                case "TRANSITION_STAGE", "ADVANCE_STAGE", "MOVE_STAGE" ->
                        handleTransitionStage(lineNumber, line, tokens);
                case "SUMMARY", "PRINT_SUMMARY" -> handleSummary(lineNumber, line, tokens);
                default -> throw new AtsException(
                        ReasonCode.UNKNOWN_COMMAND,
                        "Unknown command '" + tokens[0] + "'. Supported commands: REGISTER_REQUISITION, "
                                + "REGISTER_CANDIDATE, APPLY, RECORD_SCORE, TRANSITION_STAGE, SUMMARY."
                );
            };
        } catch (AtsException ex) {
            String formattedError = String.format(
                    "[ERROR] Line %d [%s]: %s",
                    lineNumber, ex.getReasonCode(), ex.getMessage()
            );
            out.println(formattedError);
            return new CommandExecutionResult(lineNumber, line, false, ex.getReasonCode(), ex.getMessage());
        }
    }

    private CommandExecutionResult handleRegisterRequisition(int lineNumber, String line, String[] tokens) {
        // Expected: REGISTER_REQUISITION | reqId | role | grade | headcount | location | minCtc | maxCtc | hiringManager | openingDate
        if (tokens.length != 10) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "REGISTER_REQUISITION expects 9 arguments (reqId | role | grade | headcount | location | minCtc | maxCtc | hiringManager | openingDate), found "
                            + (tokens.length - 1) + "."
            );
        }

        String requisitionId = requireToken(tokens, 1, "Requisition ID");
        String role = requireToken(tokens, 2, "Role");
        GradeBand grade = GradeBand.fromString(tokens[3]);
        int headcount = parseHeadcount(tokens[4]);
        String location = requireToken(tokens, 5, "Location");
        BigDecimal minCtc = parseCtc(tokens[6], "Minimum CTC");
        BigDecimal maxCtc = parseCtc(tokens[7], "Maximum CTC");
        String hiringManager = requireToken(tokens, 8, "Hiring manager");
        LocalDate openingDate = parseDate(tokens[9], "Opening date");

        Requisition registered = recruitmentService.registerRequisition(
                requisitionId,
                role,
                grade,
                headcount,
                location,
                minCtc,
                maxCtc,
                hiringManager,
                openingDate
        );

        String msg = String.format(
                "Requisition registered: ID=%s, Role=\"%s\", Grade=%s, Headcount=%d, Location=%s, CTC=%s-%s, Manager=\"%s\", Opened=%s",
                registered.getRequisitionId(),
                registered.getRole(),
                registered.getGradeBand(),
                registered.getHeadcount(),
                registered.getLocation(),
                registered.getMinCtc().toPlainString(),
                registered.getMaxCtc().toPlainString(),
                registered.getHiringManager(),
                registered.getOpeningDate()
        );
        out.println("[SUCCESS] " + msg);
        return new CommandExecutionResult(lineNumber, line, true, ReasonCode.SUCCESS, msg);
    }

    private CommandExecutionResult handleRegisterCandidate(int lineNumber, String line, String[] tokens) {
        // Primary 8-token syntax: REGISTER_CANDIDATE | candidateId | name | noticePeriodDays | currentCtc | expectedCtc | experienceYears | source
        // Also supports 7-token syntax: REGISTER_CANDIDATE | candidateIdOrName | noticePeriodDays | currentCtc | expectedCtc | experienceYears | source
        if (tokens.length != 8 && tokens.length != 7) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "REGISTER_CANDIDATE expects 7 arguments (candidateId | name | noticeDays | currentCtc | expectedCtc | experienceYears | source), found "
                            + (tokens.length - 1) + "."
            );
        }

        Candidate registered;
        if (tokens.length == 8) {
            String candidateId = requireToken(tokens, 1, "Candidate ID");
            String name = requireToken(tokens, 2, "Candidate name");
            int noticeDays = parseNoticePeriod(tokens[3]);
            BigDecimal currentCtc = parseCtc(tokens[4], "Current CTC");
            BigDecimal expectedCtc = parseCtc(tokens[5], "Expected CTC");
            BigDecimal experienceYears = parseExperience(tokens[6]);
            CandidateSource source = CandidateSource.fromString(tokens[7]);

            registered = recruitmentService.registerCandidate(
                    candidateId, name, noticeDays, currentCtc, expectedCtc, experienceYears, source
            );
        } else {
            String idOrName = requireToken(tokens, 1, "Candidate ID/Name");
            int noticeDays = parseNoticePeriod(tokens[2]);
            BigDecimal currentCtc = parseCtc(tokens[3], "Current CTC");
            BigDecimal expectedCtc = parseCtc(tokens[4], "Expected CTC");
            BigDecimal experienceYears = parseExperience(tokens[5]);
            CandidateSource source = CandidateSource.fromString(tokens[6]);

            registered = recruitmentService.registerCandidate(
                    idOrName, noticeDays, currentCtc, expectedCtc, experienceYears, source
            );
        }

        String msg = String.format(
                "Candidate registered: ID=%s, Name=\"%s\", NoticeDays=%d, CurrentCTC=%s, ExpectedCTC=%s, Experience=%s yrs, Source=%s",
                registered.getCandidateId(),
                registered.getName(),
                registered.getNoticePeriodDays(),
                registered.getCurrentCtc().toPlainString(),
                registered.getExpectedCtc().toPlainString(),
                registered.getExperienceYears().toPlainString(),
                registered.getSource()
        );
        out.println("[SUCCESS] " + msg);
        return new CommandExecutionResult(lineNumber, line, true, ReasonCode.SUCCESS, msg);
    }

    private CommandExecutionResult handleApply(int lineNumber, String line, String[] tokens) {
        // Expected: APPLY | applicationId | candidateId | requisitionId [ | applicationDate ]
        if (tokens.length != 5 && tokens.length != 4) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "APPLY expects 3 or 4 arguments (applicationId | candidateId | requisitionId [ | applicationDate ]), found "
                            + (tokens.length - 1) + "."
            );
        }

        String applicationId = requireToken(tokens, 1, "Application ID");
        String candidateId = requireToken(tokens, 2, "Candidate ID");
        String requisitionId = requireToken(tokens, 3, "Requisition ID");
        LocalDate applicationDate = tokens.length == 5
                ? parseDate(tokens[4], "Application date")
                : LocalDate.now();

        Application application = recruitmentService.applyCandidate(
                applicationId, candidateId, requisitionId, applicationDate
        );
        Candidate candidate = recruitmentService.getRequiredCandidate(application.getCandidateId());
        Requisition requisition = recruitmentService.getRequiredRequisition(application.getRequisitionId());

        String msg = String.format(
                "Application created: ID=%s, Candidate=%s (\"%s\"), Requisition=%s (\"%s\"), TargetGrade=%s, Stage=%s, AppliedOn=%s",
                application.getApplicationId(),
                candidate.getCandidateId(),
                candidate.getName(),
                requisition.getRequisitionId(),
                requisition.getRole(),
                application.getTargetGrade(),
                application.getCurrentStage(),
                application.getApplicationDate()
        );
        out.println("[SUCCESS] " + msg);
        return new CommandExecutionResult(lineNumber, line, true, ReasonCode.SUCCESS, msg);
    }

    private CommandExecutionResult handleRecordScore(int lineNumber, String line, String[] tokens) {
        // Expected: RECORD_SCORE | applicationId | round | score
        if (tokens.length != 4) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "RECORD_SCORE expects 3 arguments (applicationId | round | score), found "
                            + (tokens.length - 1) + "."
            );
        }

        String applicationId = requireToken(tokens, 1, "Application ID");
        InterviewRound round = InterviewRound.fromString(tokens[2]);
        BigDecimal score = parseScore(tokens[3]);

        TransitionResult result = recruitmentService.recordInterviewScore(applicationId, round, score);
        String prefix = result.reasonCode() == ReasonCode.AUTO_REJECTED_LOW_SCORE ? "[AUTO-REJECT]" : "[SUCCESS]";
        out.println(prefix + " " + result.message());
        return new CommandExecutionResult(lineNumber, line, true, result.reasonCode(), result.message());
    }

    private CommandExecutionResult handleTransitionStage(int lineNumber, String line, String[] tokens) {
        // Expected: TRANSITION_STAGE | applicationId | targetStage
        if (tokens.length != 3) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "TRANSITION_STAGE expects 2 arguments (applicationId | targetStage), found "
                            + (tokens.length - 1) + "."
            );
        }

        String applicationId = requireToken(tokens, 1, "Application ID");
        CandidateStage targetStage = CandidateStage.fromString(tokens[2]);

        TransitionResult result = recruitmentService.transitionStage(applicationId, targetStage);
        String msg = String.format(
                "Stage updated: Application=%s, Transition=%s -> %s [%s]",
                result.applicationId(),
                result.fromStage(),
                result.toStage(),
                result.reasonCode()
        );
        out.println("[SUCCESS] " + msg);
        return new CommandExecutionResult(lineNumber, line, true, result.reasonCode(), msg);
    }

    private CommandExecutionResult handleSummary(int lineNumber, String line, String[] tokens) {
        if (tokens.length != 1) {
            throw new AtsException(
                    ReasonCode.INVALID_COMMAND_ARGUMENTS,
                    "SUMMARY does not take arguments."
            );
        }

        out.println("--------------------------------------------------------------------------------");
        out.println("ATS PIPELINE SNAPSHOT");
        out.printf("Registered Requisitions : %d%n", recruitmentService.getAllRequisitions().size());
        for (Requisition req : recruitmentService.getAllRequisitions()) {
            out.printf("  - %s | %s (%s) | Location=%s | Headcount=%d (Reserved=%d, Free=%d) | Status=%s%n",
                    req.getRequisitionId(),
                    req.getRole(),
                    req.getGradeBand(),
                    req.getLocation(),
                    req.getHeadcount(),
                    req.getReservedHeadcount(),
                    req.getAvailableHeadcount(),
                    req.getStatus());
        }
        out.printf("Registered Candidates   : %d%n", recruitmentService.getAllCandidates().size());
        out.printf("Active/Total Applications: %d%n", recruitmentService.getAllApplications().size());
        for (Application app : recruitmentService.getAllApplications()) {
            out.printf("  - %s | Candidate=%s | Requisition=%s | Grade=%s | CurrentStage=%s | Transitions=%d%n",
                    app.getApplicationId(),
                    app.getCandidateId(),
                    app.getRequisitionId(),
                    app.getTargetGrade(),
                    app.getCurrentStage(),
                    app.getStageHistory().size() - 1);
        }
        out.println("--------------------------------------------------------------------------------");

        String msg = "Printed ATS pipeline summary.";
        return new CommandExecutionResult(lineNumber, line, true, ReasonCode.SUCCESS, msg);
    }

    private static String requireToken(String[] tokens, int index, String fieldName) {
        if (index >= tokens.length || tokens[index] == null || tokens[index].isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required and cannot be blank.");
        }
        return tokens[index];
    }

    private static int parseHeadcount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Headcount is required.");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new AtsException(ReasonCode.INVALID_HEADCOUNT, "Headcount must be a valid integer: '" + raw + "'.", ex);
        }
    }

    private static int parseNoticePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Notice period is required.");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new AtsException(
                    ReasonCode.INVALID_NOTICE_PERIOD,
                    "Notice period days must be a valid integer: '" + raw + "'.",
                    ex
            );
        }
    }

    private static BigDecimal parseCtc(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required.");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new AtsException(ReasonCode.INVALID_CTC, fieldName + " must be a valid numeric amount: '" + raw + "'.", ex);
        }
    }

    private static BigDecimal parseExperience(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Experience years is required.");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new AtsException(
                    ReasonCode.INVALID_EXPERIENCE,
                    "Experience years must be a valid number: '" + raw + "'.",
                    ex
            );
        }
    }

    private static BigDecimal parseScore(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, "Interview score is required.");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new AtsException(
                    ReasonCode.INVALID_SCORE,
                    "Interview score must be a valid decimal between 0.0 and 5.0: '" + raw + "'.",
                    ex
            );
        }
    }

    private static LocalDate parseDate(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new AtsException(ReasonCode.MISSING_REQUIRED_FIELD, fieldName + " is required (YYYY-MM-DD).");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new AtsException(
                    ReasonCode.INVALID_DATE,
                    fieldName + " must use ISO format YYYY-MM-DD (received: '" + raw + "').",
                    ex
            );
        }
    }
}
