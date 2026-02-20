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

                System.out.print("Type the project name to confirm deletion [" + projectName + "]: ");
                String confirmation = new java.util.Scanner(System.in).nextLine().trim();

                if (!confirmation.equals(projectName)) {
                    ConsolePrinter.warning("Deletion cancelled — name did not match.");
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
