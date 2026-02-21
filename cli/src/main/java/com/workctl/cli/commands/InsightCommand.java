package com.workctl.cli.commands;

import com.workctl.cli.util.CliSpinner;
import com.workctl.cli.util.ConsolePrinter;
import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;
import picocli.CommandLine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@CommandLine.Command(
        name = "insight",
        description = "Generate intelligent project insights"
)
public class InsightCommand implements Runnable {

    @CommandLine.Parameters(index = "0")
    private String projectName;

    private final StatsService statsService = new StatsService();

    @Override
    public void run() {

        CliSpinner spinner = new CliSpinner("Analyzing project");
        spinner.start();
        ProjectInsights insights;
        try {
            insights = statsService.generateInsights(projectName);
        } finally {
            spinner.stop();
        }

        System.out.println();
        ConsolePrinter.header("Project Insights — " + projectName);
        System.out.println();

        // ── Task Overview ─────────────────────────────────────────────
        ConsolePrinter.header("Task Overview");
        System.out.println("  Total Tasks   " + insights.getTotalTasks());
        System.out.println("  Open          " + insights.getOpenTasks());
        System.out.println("  In Progress   " + insights.getInProgressTasks());
        System.out.println("  Done          " + insights.getDoneTasks());
        System.out.println();

        // ── Performance ───────────────────────────────────────────────
        ConsolePrinter.header("Performance");
        System.out.println("  Completed this week   " + insights.getCompletedThisWeek());

        int rateInt = (int) Math.round(insights.getCompletionRate());
        System.out.println("  Completion rate       "
                + ConsolePrinter.progressBar(rateInt, 100, 20)
                + String.format("  (%.1f%%)", insights.getCompletionRate()));

        int scoreInt = (int) Math.round(insights.getProductivityScore());
        System.out.println("  Productivity score    "
                + ConsolePrinter.progressBar(scoreInt, 100, 20)
                + String.format("  (%.1f / 100)", insights.getProductivityScore()));

        long stagnant = insights.getStagnantTasks();
        String stagnantStr = stagnant > 0
                ? "\u001B[31m" + stagnant + "\u001B[0m"   // red if any
                : "\u001B[32m" + stagnant + "\u001B[0m";  // green if zero
        System.out.println("  Stagnant (>7 days)    " + stagnantStr);
        System.out.println();

        // ── Intelligence ──────────────────────────────────────────────
        ConsolePrinter.header("Intelligence");
        System.out.println("  Most used tag       #" + insights.getMostUsedTag());
        System.out.println("  Active days          " + insights.getDailyActivity().size());
        System.out.println();

        // ── 14-Day Activity Chart ─────────────────────────────────────
        ConsolePrinter.header("14-Day Activity");
        printActivityChart(insights.getDailyActivity());
        System.out.println();
    }

    private void printActivityChart(Map<LocalDate, Integer> activity) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        int maxVal = activity.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (maxVal == 0) maxVal = 1;

        for (int i = 13; i >= 0; i--) {
            LocalDate date  = today.minusDays(i);
            int count       = activity.getOrDefault(date, 0);
            int barLen      = (int) Math.round((double) count / maxVal * 20);
            String filled   = "\u001B[32m" + "█".repeat(barLen) + "\u001B[0m";
            String empty    = "\u001B[2m"  + "░".repeat(20 - barLen) + "\u001B[0m";
            String countStr = count > 0 ? "  " + count : "";
            System.out.println("  " + date.format(fmt) + "  " + filled + empty + countStr);
        }
    }
}
