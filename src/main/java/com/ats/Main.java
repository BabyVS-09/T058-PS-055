package com.ats;

import com.ats.parser.CommandExecutionResult;
import com.ats.parser.CommandFileParser;
import com.ats.service.RecruitmentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point for PS-055: Recruitment ATS with Interview Scheduling
 * and Offer Approval Matrix (Week 2 Core Engine).
 *
 * Team ID : T058
 * Members : Baby V S (1AT24CG012), Bhagyashree Rama Naik (1AT24CG013)
 */
public class Main {

    public static final String DEFAULT_COMMAND_FILE = "sample-data/happy-path.txt";

    public static void main(String[] args) {
        String targetFilePath = (args != null && args.length > 0 && !args[0].isBlank())
                ? args[0].trim()
                : DEFAULT_COMMAND_FILE;

        Path commandFile = Path.of(targetFilePath);

        System.out.println("================================================================================");
        System.out.println("PS-055: Recruitment ATS with Interview Scheduling and Offer Approval Matrix");
        System.out.println("Team T058 | Baby V S (1AT24CG012) & Bhagyashree Rama Naik (1AT24CG013)");
        System.out.println("Command File: " + commandFile);
        System.out.println("================================================================================");

        if (!Files.exists(commandFile) || !Files.isRegularFile(commandFile)) {
            System.err.println("[FATAL] Command file not found: " + commandFile.toAbsolutePath());
            System.err.println("Usage: java -cp target/classes com.ats.Main [path-to-command-file]");
            System.exit(1);
        }

        RecruitmentService recruitmentService = new RecruitmentService();
        CommandFileParser parser = new CommandFileParser(recruitmentService, System.out);

        try {
            List<CommandExecutionResult> results = parser.processFile(commandFile);

            long successCount = results.stream().filter(CommandExecutionResult::success).count();
            long errorCount = results.size() - successCount;

            System.out.println("================================================================================");
            System.out.printf("Execution Complete | Commands Processed: %d | Succeeded: %d | Rejected/Errors: %d%n",
                    results.size(), successCount, errorCount);
            System.out.println("================================================================================");
        } catch (IOException ex) {
            System.err.println("[FATAL] Failed to read command file '" + commandFile + "': " + ex.getMessage());
            System.exit(1);
        }
    }
}
