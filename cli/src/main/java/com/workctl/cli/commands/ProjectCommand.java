package com.workctl.cli.commands;

import com.workctl.cli.util.CliPrompt;
import com.workctl.cli.util.ConsolePrinter;
import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Project;
import com.workctl.core.service.ProjectService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.nio.file.Path;
import java.util.List;

@Command(
        name = "project",
        description = "Manage projects",
        subcommands = {
                ProjectCommand.CreateCommand.class,
                ProjectCommand.ListCommand.class,
                ProjectCommand.DeleteCommand.class
        }
)
public class ProjectCommand implements Runnable {

    @Override
    public void run() {
        ConsolePrinter.info("Use: workctl project <create|list|delete>");
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




    @Command(name = "delete", description = "Delete a project and all its data")
    public static class DeleteCommand implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        private final ProjectService projectService = new ProjectService();

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();
                Path workspace = Path.of(config.getWorkspace());

                boolean confirmed = CliPrompt.confirm(
                        "Delete project \"" + projectName + "\" and ALL its data?");

                if (!confirmed) {
                    ConsolePrinter.warning("Deletion cancelled.");
                    return;
                }

                projectService.deleteProject(workspace, projectName);
                ConsolePrinter.success("Project deleted: " + projectName);

            } catch (IllegalArgumentException e) {
                ConsolePrinter.error(e.getMessage());
            } catch (Exception e) {
                ConsolePrinter.error("Failed to delete project: " + e.getMessage());
            }
        }
    }



    @Command(
            name = "list",
            description = "List all projects"
    )
    public static class ListCommand implements Runnable {

        @CommandLine.Option(
                names = {"--plain"},
                description = "Output plain project names (one per line), suitable for scripting"
        )
        private boolean plain;

        private final ProjectService projectService = new ProjectService();

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();
                Path workspace = Path.of(config.getWorkspace());

                List<Project> projects = projectService.listProjects(workspace);

                if (plain) {
                    projects.forEach(p -> System.out.println(p.getName()));
                    return;
                }

                if (projects.isEmpty()) {
                    ConsolePrinter.info("No projects found.");
                    return;
                }

                ConsolePrinter.header("Projects");
                projects.forEach(p ->
                        System.out.println("  \u001B[36m▸\u001B[0m " + p.getName())
                );

            } catch (Exception e) {
                ConsolePrinter.error(e.getMessage());
            }
        }
    }

}
