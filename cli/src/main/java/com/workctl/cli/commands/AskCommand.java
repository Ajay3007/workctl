package com.workctl.cli.commands;

import com.workctl.agent.AgentService;
import com.workctl.cli.util.CliPrompt;
import com.workctl.cli.util.CliSpinner;
import com.workctl.cli.util.ConsolePrinter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * workctl ask <project> "your question or instruction"
 *
 * The CLI entry point for the AI agent.
 *
 * Examples:
 *   workctl ask myproject "What did I work on this week?"
 *   workctl ask myproject "Which P1 tasks are stagnant?"
 *   workctl ask myproject --act "Break down the logging feature into tasks"
 *   workctl ask myproject --weekly
 *   workctl ask myproject --insight
 *   workctl ask myproject          ← launches interactive REPL
 */
@Command(
        name = "ask",
        description = "Ask the AI agent about your project or give it instructions"
)
public class AskCommand implements Runnable {

    @Parameters(index = "0", description = "Project name")
    private String projectName;

    @Parameters(index = "1",
                description = "Your question or instruction for the agent",
                defaultValue = "")
    private String question;

    @Option(names = "--act",
            description = "Enable write mode: agent can add and move tasks")
    private boolean act;

    @Option(names = "--weekly",
            description = "Generate AI-powered weekly summary")
    private boolean weekly;

    @Option(names = "--insight",
            description = "Generate AI-powered project insights")
    private boolean insight;

    @Option(names = "--from",
            description = "Start date for weekly summary (yyyy-MM-dd)")
    private String fromDate;

    @Option(names = "--to",
            description = "End date for weekly summary (yyyy-MM-dd)")
    private String toDate;

    private final AgentService agentService = new AgentService();

    @Override
    public void run() {

        System.out.println();
        ConsolePrinter.header("AI Agent — " + projectName);

        if (weekly) {
            runWeekly();
        } else if (insight) {
            runInsight();
        } else if (question.isBlank()) {
            runReplMode();
        } else {
            runSingleQuestion();
        }
    }

    private void runWeekly() {
        String from = fromDate != null ? fromDate :
                java.time.LocalDate.now().minusDays(6).toString();
        String to = toDate != null ? toDate :
                java.time.LocalDate.now().toString();

        ConsolePrinter.info("Generating AI weekly summary (" + from + " → " + to + ")...");
        System.out.println();

        CliSpinner spinner = new CliSpinner("Analyzing");
        spinner.start();
        String response;
        try {
            response = agentService.weeklyAiSummary(projectName, from, to);
        } finally {
            spinner.stop();
        }

        System.out.println("\u001B[36mAgent:\u001B[0m " + response);
        ConsolePrinter.separator();
        System.out.println();
    }

    private void runInsight() {
        ConsolePrinter.info("Generating AI project insights...");
        System.out.println();

        CliSpinner spinner = new CliSpinner("Analyzing project");
        spinner.start();
        String response;
        try {
            response = agentService.aiInsights(projectName);
        } finally {
            spinner.stop();
        }

        System.out.println("\u001B[36mAgent:\u001B[0m " + response);
        ConsolePrinter.separator();
        System.out.println();
    }

    private void runSingleQuestion() {
        if (act) {
            ConsolePrinter.info("Write mode ON — agent may add/move tasks");
            System.out.println();
        }
        System.out.println("You: " + question);
        System.out.println();

        CliSpinner spinner = new CliSpinner("Thinking");
        spinner.start();
        String response;
        try {
            response = agentService.ask(projectName, question, act);
        } finally {
            spinner.stop();
        }

        System.out.println("\u001B[36mAgent:\u001B[0m " + response);
        ConsolePrinter.separator();
        System.out.println();
    }

    private void runReplMode() {
        try {
            Path historyFile = Path.of(System.getProperty("user.home"), ".workctl", "ask_history");
            Files.createDirectories(historyFile.getParent());

            Terminal terminal = TerminalBuilder.builder().dumb(true).build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .variable(LineReader.HISTORY_FILE, historyFile)
                    .build();

            ConsolePrinter.info("REPL mode — Ctrl+D to exit, Ctrl+C to skip line");
            System.out.println();

            while (true) {
                String input;
                try {
                    input = reader.readLine("\u001B[36mYou › \u001B[0m");
                } catch (UserInterruptException e) {
                    // Ctrl+C: skip current input, continue loop
                    System.out.println();
                    continue;
                } catch (EndOfFileException e) {
                    // Ctrl+D: exit
                    break;
                }
                if (input == null || input.isBlank()) continue;

                CliSpinner spinner = new CliSpinner("Thinking");
                spinner.start();
                String response;
                try {
                    response = agentService.ask(projectName, input, act);
                } finally {
                    spinner.stop();
                }

                System.out.println("\u001B[36mAgent:\u001B[0m " + response);
                System.out.println();
            }

            terminal.close();
            ConsolePrinter.info("Goodbye.");

        } catch (IOException e) {
            ConsolePrinter.error("Failed to start REPL: " + e.getMessage());
        }
    }
}
