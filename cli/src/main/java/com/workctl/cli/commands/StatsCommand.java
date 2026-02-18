package com.workctl.cli.commands;

import com.workctl.core.service.StatsService;
import picocli.CommandLine;

@CommandLine.Command(
        name = "stats",
        description = "Generate analytics from task lifecycle events"
)
public class StatsCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Project name")
    private String projectName;

    private final StatsService statsService = new StatsService();

    @Override
    public void run() {
        statsService.generate(projectName);
    }
}
