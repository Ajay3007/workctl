package com.workctl.cli.commands;

import com.workctl.agent.AgentService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * workctl ask <project> "your question or instruction"
 *
 * The CLI entry point for the AI agent.
 *
 * Examples:
 *   workctl ask myproject "What did I work on this week?"
 *   workctl ask myproject "Which P1 tasks are stagnant?"
 *   workctl ask myproject "Give me project health insights"
 *   workctl ask myproject --act "Break down the logging feature into tasks"
 *   workctl ask myproject --act "Mark task 52 as done"
 *
 * Flags:
 *   --act     Enables write mode: agent can add tasks and move task status.
 *             Without this flag, agent is read-only (safe by default).
 *
 *   --weekly  Generate an AI-powered weekly summary.
 *             workctl ask myproject --weekly
 *             workctl ask myproject --weekly --from 2026-02-11 --to 2026-02-17
 *
 *   --insight Generate AI-powered project insights (richer than workctl insight).
 *             workctl ask myproject --insight
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

        System.out.println("\n🤖 workctl AI Agent — Project: " + projectName);
        System.out.println("─".repeat(50));

        String response;

        if (weekly) {
            // AI-powered weekly summary mode
            String from = fromDate != null ? fromDate :
                    java.time.LocalDate.now().minusDays(6).toString();
            String to = toDate != null ? toDate :
                    java.time.LocalDate.now().toString();

            System.out.println("📅 Generating AI weekly summary (" + from + " → " + to + ")...\n");
            response = agentService.weeklyAiSummary(projectName, from, to);

        } else if (insight) {
            // AI-powered insights mode
            System.out.println("📊 Generating AI project insights...\n");
            response = agentService.aiInsights(projectName);

        } else if (question.isBlank()) {
            // No question given
            System.out.println("Please provide a question or use --weekly / --insight flags.");
            System.out.println("Example: workctl ask " + projectName + " \"What tasks are stagnant?\"");
            return;

        } else {
            // Standard ask mode
            if (act) {
                System.out.println("✏ Write mode ON — agent may add/move tasks\n");
            }
            System.out.println("You: " + question + "\n");
            response = agentService.ask(projectName, question, act);
        }

        System.out.println("Agent: " + response);
        System.out.println("─".repeat(50) + "\n");
    }
}
