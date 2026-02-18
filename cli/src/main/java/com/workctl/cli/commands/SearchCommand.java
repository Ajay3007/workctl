package com.workctl.cli.commands;

import com.workctl.core.service.ProjectService;
import picocli.CommandLine;

@CommandLine.Command(
        name = "search",
        description = "Search logs by keyword or tag"
)
public class SearchCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Keyword or tag")
    private String query;

    @CommandLine.Option(names = "--tag",
            description = "Search by tag")
    private boolean searchByTag;

    private final ProjectService projectService = new ProjectService();

    @Override
    public void run() {
        projectService.search(query, searchByTag);
    }
}
