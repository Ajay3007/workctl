package com.workctl.cli.commands;

import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;
import picocli.CommandLine;

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

        ProjectInsights insights =
                statsService.generateInsights(projectName);

        System.out.println("\n📊 Project Insights: " + projectName);

        System.out.println("\n--- Task Overview ---");
        System.out.println("Total Tasks: " + insights.getTotalTasks());
        System.out.println("Open: " + insights.getOpenTasks());
        System.out.println("In Progress: " + insights.getInProgressTasks());
        System.out.println("Done: " + insights.getDoneTasks());

        System.out.println("\n--- Performance ---");
        System.out.println("Completed This Week: " + insights.getCompletedThisWeek());
        System.out.println("Completion Rate: " +
                String.format("%.2f", insights.getCompletionRate()) + "%");

        System.out.println("Productivity Score: " +
                String.format("%.2f", insights.getProductivityScore()) + " / 100");

        System.out.println("Stagnant Tasks (>7 days): " +
                insights.getStagnantTasks());

        System.out.println("\n--- Intelligence ---");
        System.out.println("Most Used Tag: #" +
                insights.getMostUsedTag());

        System.out.println("Active Days (Heatmap entries): " +
                insights.getDailyActivity().size());
    }
}
