package com.workctl.cli.commands;

import com.workctl.cli.util.EditorUtil;
import com.workctl.core.model.Task;
import com.workctl.core.service.TaskService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Command(
        name = "task",
        description = "Manage project tasks",
        subcommands = {
                TaskCommand.Add.class,
                TaskCommand.ListTasks.class,
                TaskCommand.Start.class,
                TaskCommand.Done.class,
                TaskCommand.Show.class,
                TaskCommand.Delete.class
        }
)
public class TaskCommand {

    private static final TaskService taskService = new TaskService();

    // ======================
    // ADD
    // ======================

    @Command(name = "add", description = "Add a new task")
    static class Add implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Option(names = "--tag",
                description = "Add tag(s)",
                arity = "0..*")
        private List<String> tags = new ArrayList<>();

        @Option(names = "--message", description = "Task description")
        private String message;

        @Option(names = "--file", description = "Read task description from file")
        private Path file;

        @Option(names = "--edit", description = "Open editor to write task description")
        private boolean edit;

        @CommandLine.Option(
                names = {"-p", "--priority"},
                description = "Task priority (1=High, 2=Medium, 3=Low)",
                defaultValue = "2"
        )
        private int priority;


        @Override
        public void run() {
            try {

                String desc = resolveDescription();

                taskService.addTask(projectName, desc, tags, priority);

                System.out.println("Task added successfully.");

            } catch (Exception e) {
                System.out.println("Failed to add task");
                e.printStackTrace();
            }
        }


        private String resolveDescription() throws Exception {

            if (edit) {
                return EditorUtil.openEditorAndCapture();
            }

            if (file != null) {
                return Files.readString(file);
            }

            if (message != null && !message.isBlank()) {
                return message;
            }

            // Interactive fallback
            System.out.println("Enter task description. Type END on a new line to finish:");

            Scanner scanner = new Scanner(System.in);
            StringBuilder sb = new StringBuilder();

            while (true) {
                String line = scanner.nextLine();
                if ("END".equalsIgnoreCase(line.trim())) {
                    break;
                }
                sb.append(line).append("\n");
            }

            return sb.toString().trim();
        }

    }

    // ======================
    // LIST
    // ======================

    @Command(name = "list", description = "List tasks for a project")
    static class ListTasks implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Override
        public void run() {
            taskService.listTasks(projectName);
        }
    }

    // ======================
    // START
    // ======================

    @Command(name = "start", description = "Move task to In Progress")
    static class Start implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        @Override
        public void run() {
            taskService.startTask(projectName, taskId);
            System.out.println("Task moved to In Progress.");
        }
    }

    // ======================
    // DONE
    // ======================

    @Command(name = "done", description = "Mark task as Done")
    static class Done implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        @Override
        public void run() {
            taskService.completeTask(projectName, taskId);
            System.out.println("Task marked as Done.");
        }
    }

    @Command(name = "show", description = "Show full task details")
    static class Show implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int id;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            taskService.getTask(projectName, id)
                    .ifPresentOrElse(task -> {

                        System.out.println("\nTask #" + task.getId());
                        System.out.println("Status: " + task.getStatus());
                        System.out.println("\nDescription:\n");
                        System.out.println(task.getDescription());

                    }, () -> {
                        System.out.println("Task not found.");
                    });
        }
    }

    @Command(name = "delete", description = "Delete a task")
    static class Delete implements Runnable {

        @Parameters(index = "0")
        private String projectName;

        @Option(names = {"-id"}, required = true)
        private int taskId;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            taskService.deleteTask(projectName, taskId);
            System.out.println("Task deleted successfully.");
        }
    }


}
