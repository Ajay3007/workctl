package com.workctl.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import com.workctl.core.service.ProjectService;

@Command(
        name = "weekly",
        description = "Generate weekly summary"
)
public class WeeklyCommand implements Runnable {

    @Parameters(index = "0", description = "Project name")
    private String projectName;

    @Option(names = "--from", description = "Start date (yyyy-MM-dd)")
    private String fromDate;

    @Option(names = "--to", description = "End date (yyyy-MM-dd)")
    private String toDate;

    @Option(names = "--section",
            description = "Filter by section: done|changes|commands")
    private String sectionFilter;

    private final ProjectService projectService = new ProjectService();

    @Override
    public void run() {
        projectService.generateWeeklySummary(
                projectName,
                fromDate,
                toDate,
                sectionFilter
        );
    }

}
