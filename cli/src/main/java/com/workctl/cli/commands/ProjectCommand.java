package com.workctl.cli.commands;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.service.ProjectService;
import com.workctl.core.domain.Project;
import com.workctl.cli.util.ConsolePrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.nio.file.Path;
import java.util.List;



import java.nio.file.Path;
import java.util.List;

@Command(
        name = "project",
        description = "Manage projects",
        subcommands = {
                ProjectCommand.CreateCommand.class,
                ProjectCommand.ListCommand.class
        }
)
public class ProjectCommand implements Runnable {

    @Override
    public void run() {
        ConsolePrinter.info("Use: workctl project <create|list>");
    }



    @Command(
            name = "create",
            description = "Create a new project"
    )
    public static class CreateCommand implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @CommandLine.Option(
                names = {"-d", "--description"},
                description = "Project description"
        )
        private String description = "";

        private final ProjectService projectService = new ProjectService();

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();
                Path workspace = Path.of(config.getWorkspace());

                Project project =
                        projectService.createProject(workspace, projectName, description);

                ConsolePrinter.success("Project created: " + projectName);
                ConsolePrinter.info("Project ID: " + project.getId());

            } catch (Exception e) {
                ConsolePrinter.error(e.getMessage());
            }
        }
    }




    @Command(
            name = "list",
            description = "List all projects"
    )
    public static class ListCommand implements Runnable {

        private final ProjectService projectService = new ProjectService();

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();
                Path workspace = Path.of(config.getWorkspace());

                List<Project> projects = projectService.listProjects(workspace);

                if (projects.isEmpty()) {
                    ConsolePrinter.info("No projects found.");
                    return;
                }

                ConsolePrinter.info("Projects:");

                projects.forEach(p ->
                        System.out.println("  - " + p.getName())
                );

            } catch (Exception e) {
                ConsolePrinter.error(e.getMessage());
            }
        }
    }

}
