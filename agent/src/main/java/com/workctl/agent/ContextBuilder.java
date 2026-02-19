package com.workctl.agent;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.Task;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ContextBuilder
 *
 * Reads the project's filesystem data and formats it into a rich system prompt
 * that gives Claude full awareness of the project state before it answers.
 *
 * This is sent as the SYSTEM PROMPT in every API call — Claude reads this
 * before seeing the user's question, so it already "knows" your project.
 *
 * What gets included:
 *   - Current date (so Claude can reason about "today", "this week")
 *   - Task summary: counts per status, P1 tasks listed explicitly
 *   - Stagnant tasks highlighted (> 7 days without change)
 *   - Recent log entries (last 7 days) for conversational context
 *   - Instructions on what tools are available and when to use them
 */
public class ContextBuilder {

    private final TaskService taskService = new TaskService();

    /**
     * Build the full system prompt for a given project.
     *
     * @param projectName  the workctl project name
     * @param allowWrite   true = include write tools (add_task, move_task)
     *                     false = read-only mode (list_tasks, search_logs, get_insights only)
     */
    public String buildSystemPrompt(String projectName, boolean allowWrite) {

        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are an AI assistant embedded in workctl — a developer productivity system.
                You have full context of the user's project and access to tools to read and
                (optionally) act on their task board and work logs.

                Today's date: %s
                Current project: %s

                """.formatted(LocalDate.now(), projectName));

        // ── Task Summary ──────────────────────────────────────────────────
        sb.append("=== CURRENT TASK BOARD ===\n");
        try {
            List<Task> tasks = taskService.getTasks(projectName);

            long open = tasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN).count();
            long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
            long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

            sb.append("Total: ").append(tasks.size())
              .append("  |  Open: ").append(open)
              .append("  |  In Progress: ").append(inProgress)
              .append("  |  Done: ").append(done).append("\n\n");

            // P1 tasks — always show these explicitly
            List<Task> p1Tasks = tasks.stream()
                    .filter(t -> t.getPriority() == 1 && t.getStatus() != TaskStatus.DONE)
                    .collect(Collectors.toList());

            if (!p1Tasks.isEmpty()) {
                sb.append("P1 (High Priority) Tasks:\n");
                p1Tasks.forEach(t -> sb.append("  #").append(t.getId())
                        .append(" [").append(t.getStatus()).append("] ")
                        .append(t.getTitle()).append("\n"));
                sb.append("\n");
            }

            // Stagnant tasks — older than 7 days, not done
            List<Task> stagnant = tasks.stream()
                    .filter(t -> t.getStatus() != TaskStatus.DONE)
                    .filter(t -> ChronoUnit.DAYS.between(t.getCreatedDate(), LocalDate.now()) > 7)
                    .collect(Collectors.toList());

            if (!stagnant.isEmpty()) {
                sb.append("⚠ Stagnant Tasks (7+ days old, not completed):\n");
                stagnant.forEach(t -> {
                    long days = ChronoUnit.DAYS.between(t.getCreatedDate(), LocalDate.now());
                    sb.append("  #").append(t.getId())
                      .append(" [P").append(t.getPriority()).append("] ")
                      .append(t.getTitle())
                      .append(" (").append(days).append(" days)\n");
                });
                sb.append("\n");
            }

        } catch (Exception e) {
            sb.append("(Could not load tasks: ").append(e.getMessage()).append(")\n\n");
        }

        // ── Recent Log Entries ────────────────────────────────────────────
        sb.append("=== RECENT WORK LOG (last 7 days) ===\n");
        try {
            AppConfig config = ConfigManager.load();
            Path logFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (Files.exists(logFile)) {
                String recentLogs = extractRecentLogs(logFile, 7);
                sb.append(recentLogs.isBlank() ? "(No entries in the last 7 days)\n" : recentLogs);
            } else {
                sb.append("(No work log found)\n");
            }
        } catch (Exception e) {
            sb.append("(Could not read work log)\n");
        }

        // ── Behavior Instructions ─────────────────────────────────────────
        sb.append("""

                === YOUR BEHAVIOR ===
                - Answer questions about tasks, logs, and project state using your tools.
                - Be concise but insightful. Don't just repeat raw data — interpret it.
                - When you notice stagnant P1 tasks, proactively mention them.
                - If the user asks to summarize the week, call search_logs with the date range.
                - If the user asks for insights, call get_insights then explain the score.
                """);

        if (allowWrite) {
            sb.append("""
                - Write mode is ON: you may call add_task and move_task when the user asks.
                - Before adding multiple tasks, confirm your plan in plain text first.
                - Never add duplicate tasks. Call list_tasks first if uncertain.
                """);
        } else {
            sb.append("""
                - Read-only mode: you can only list, search, and explain. You cannot add or move tasks.
                - If the user asks you to create or move tasks, tell them to add the --act flag.
                """);
        }

        return sb.toString();
    }

    /**
     * Extract log entries from the last N days from work-log.md.
     * Skips metadata comment blocks (TASK_EVENT) to keep context clean.
     */
    private String extractRecentLogs(Path logFile, int days) throws Exception {

        LocalDate cutoff = LocalDate.now().minusDays(days);
        List<String> lines = Files.readAllLines(logFile);

        StringBuilder sb = new StringBuilder();
        LocalDate currentDate = null;
        boolean inRange = false;
        boolean inMetaBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Start of metadata block
            if (trimmed.startsWith("<!-- TASK_EVENT")) {
                inMetaBlock = true;
                continue;
            }
            // End of metadata block
            if (inMetaBlock) {
                if (trimmed.endsWith("-->")) inMetaBlock = false;
                continue;
            }

            // Date header
            if (trimmed.startsWith("## ")) {
                try {
                    currentDate = LocalDate.parse(trimmed.substring(3).trim());
                    inRange = !currentDate.isBefore(cutoff);
                    if (inRange) sb.append("\n").append(trimmed).append("\n");
                } catch (Exception ignored) {}
                continue;
            }

            if (!inRange) continue;

            // Section headers and bullet entries
            if (trimmed.startsWith("### ") || trimmed.startsWith("- ")) {
                sb.append(line).append("\n");
            }
        }

        return sb.toString();
    }
}
