package com.workctl.cli.commands;

import com.workctl.cli.util.CliPrompt;
import com.workctl.cli.util.EditorUtil;
import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.service.ProjectService;
import com.workctl.cli.util.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Command(
        name = "log",
        description = "Add work log entry"
)
public class LogCommand implements Runnable {

    @Parameters(index = "0", description = "Project name")
    private String projectName;

    @Option(names = {"-m", "--message"}, description = "Log message")
    private String message;

    @Option(names = {"-s", "--section"}, description = "Section: assigned|done|changes|commands|notes")
    private String section;

    @Option(names = "--tag",
            description = "Add tag(s)",
            arity = "0..*")
    private List<String> tags = new ArrayList<>();

    @Option(names = "--file",
            description = "Read log message from file")
    private Path file;

    @Option(names = "--edit",
            description = "Open external editor (VSCode) to write log")
    private boolean edit;



    private final ProjectService projectService = new ProjectService();

    @Override
    public void run() {

        try {
            String finalMessage = resolveMessage();

            projectService.addLogEntry(
                    projectName,
                    finalMessage,
                    section,
                    tags
            );

            ConsolePrinter.success("Log updated successfully.");

        } catch (Exception e) {
            ConsolePrinter.error("Failed to update log: " + e.getMessage());
        }
    }

    private String resolveMessage() throws IOException, InterruptedException {

        // 1️⃣ Editor mode
        if (edit) {
            return EditorUtil.openEditorAndCapture();
        }

        // 2️⃣ File mode
        if (file != null) {
            return Files.readString(file);
        }

        // 3️⃣ Direct message
        if (message != null && !message.isBlank()) {
            return message;
        }

        // 4️⃣ Interactive mode
        return CliPrompt.promptMultiline("Enter log message");
    }


}
