package com.workctl.cli.commands;

import com.workctl.cli.util.ConsolePrinter;
import com.workctl.cli.util.EditorUtil;
import com.workctl.core.model.Task;
import com.workctl.core.model.Task.SubTask;
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
                TaskCommand.Delete.class,
                TaskCommand.SubtaskGroup.class   // NEW
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

        // NEW: repeatable option — each --subtask value becomes one subtask
        // Example: task add myproject -m "Build API" --subtask "Design schema" --subtask "Write tests"
        @Option(
                names = "--subtask",
                description = "Add subtask(s) to the new task (repeatable)",
                arity = "0..*"
        )
        private List<String> subtaskTitles = new ArrayList<>();

        @Override
        public void run() {
            try {

                String desc = resolveDescription();

                taskService.addTask(projectName, desc, tags, priority);

                // Attach any --subtask values to the newly created task
                if (!subtaskTitles.isEmpty()) {
                    List<Task> all = taskService.getTasks(projectName);
                    all.stream()
                            .max((a, b) -> Integer.compare(a.getId(), b.getId()))
                            .ifPresent(newest -> {
                                for (String title : subtaskTitles) {
                                    if (title != null && !title.isBlank()) {
                                        taskService.addSubtask(
                                                projectName, newest.getId(), title.trim());
                                    }
                                }
                            });
                }

                ConsolePrinter.success("Task added successfully.");

                if (!subtaskTitles.isEmpty()) {
                    ConsolePrinter.info(subtaskTitles.size() + " subtask(s) added.");
                }

            } catch (Exception e) {
                ConsolePrinter.error("Failed to add task: " + e.getMessage());
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
            ConsolePrinter.success("Task moved to In Progress.");
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
            ConsolePrinter.success("Task marked as Done.");
        }
    }

    // ======================
    // SHOW
    // FIX: now displays subtasks after the description
    // ======================

    @Command(name = "show", description = "Show full task details including subtasks")
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

                        System.out.println();
                        System.out.println("Task #" + task.getId()
                                + "  [P" + task.getPriority() + "]"
                                + "  " + task.getStatus());
                        System.out.println("Created: " + task.getCreatedDate());
                        System.out.println();
                        System.out.println("Description:");
                        System.out.println();

                        // Strip inline metadata comments before display
                        String desc = task.getDescription()
                                .replaceAll("<!--.*?-->", "").trim();
                        System.out.println(desc);

                        // ── Subtasks section (NEW) ──────────────────────
                        if (task.hasSubtasks()) {

                            List<SubTask> subtasks = task.getSubtasks();
                            int done  = task.getDoneSubtaskCount();
                            int total = task.getTotalSubtaskCount();

                            System.out.println();
                            System.out.println("Subtasks (" + done + "/" + total + " done):");

                            for (int i = 0; i < subtasks.size(); i++) {
                                SubTask st = subtasks.get(i);
                                String tick = st.isDone() ? "✓" : "○";
                                System.out.println("  " + i + ".  " + tick + "  " + st.getTitle());
                            }
                        }

                        System.out.println();

                    }, () -> ConsolePrinter.error("Task #" + id + " not found."));
        }
    }

    // ======================
    // DELETE
    // ======================

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
            ConsolePrinter.success("Task deleted successfully.");
        }
    }

    // ============================================================
    // SUBTASK GROUP
    //
    // SubtaskGroup is the "subtask" named group registered on TaskCommand.
    // Its four sub-subcommands are declared as direct static inner classes
    // of TaskCommand (same level as Add, Done, etc.) so that Picocli can
    // resolve them without ambiguity at runtime.
    //
    // Usage:
    //   workctl task subtask add    <project> <task-id> "<title>"
    //   workctl task subtask done   <project> <task-id> <index>
    //   workctl task subtask list   <project> <task-id>
    //   workctl task subtask delete <project> <task-id> <index>
    // ============================================================

    @Command(
            name = "subtask",
            description = "Manage subtasks of a task",
            subcommands = {
                    TaskCommand.SubtaskAdd.class,
                    TaskCommand.SubtaskDone.class,
                    TaskCommand.SubtaskList.class,
                    TaskCommand.SubtaskDelete.class
            }
    )
    static class SubtaskGroup implements Runnable {

        @Override
        public void run() {
            ConsolePrinter.info(
                    "Usage: workctl task subtask <add|done|list|delete> ...\n" +
                            "Run 'workctl task subtask --help' for details.");
        }
    }

    // ======================
    // SUBTASK ADD
    // ======================

    @Command(name = "add", description = "Add a subtask to an existing task")
    static class SubtaskAdd implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        @Parameters(index = "2", description = "Subtask title")
        private String title;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            taskService.getTask(projectName, taskId).ifPresentOrElse(task -> {

                taskService.addSubtask(projectName, taskId, title.trim());

                ConsolePrinter.success(
                        "Subtask added to Task #" + taskId
                                + " (\"" + task.getTitle() + "\"): " + title.trim());

            }, () -> ConsolePrinter.error("Task #" + taskId + " not found."));
        }
    }

    // ======================
    // SUBTASK DONE (toggle)
    // ======================

    @Command(name = "done", description = "Toggle a subtask done/undone by its index (0-based)")
    static class SubtaskDone implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        @Parameters(index = "2", description = "Subtask index (0-based, see: task subtask list)")
        private int subtaskIndex;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            taskService.getTask(projectName, taskId).ifPresentOrElse(task -> {

                List<SubTask> subs = task.getSubtasks();

                if (subs.isEmpty()) {
                    ConsolePrinter.warning("Task #" + taskId + " has no subtasks.");
                    return;
                }

                if (subtaskIndex < 0 || subtaskIndex >= subs.size()) {
                    ConsolePrinter.error(
                            "Invalid index " + subtaskIndex
                                    + ". Valid range: 0-" + (subs.size() - 1)
                                    + ". Run 'task subtask list' to see indexes.");
                    return;
                }

                boolean wasDone = subs.get(subtaskIndex).isDone();
                taskService.toggleSubtask(projectName, taskId, subtaskIndex);

                String newState = wasDone ? "marked open" : "marked done";
                ConsolePrinter.success(
                        "Subtask " + subtaskIndex + " \""
                                + subs.get(subtaskIndex).getTitle() + "\" " + newState + ".");

            }, () -> ConsolePrinter.error("Task #" + taskId + " not found."));
        }
    }

    // ======================
    // SUBTASK LIST
    // ======================

    @Command(name = "list", description = "List subtasks for a task")
    static class SubtaskList implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            taskService.getTask(projectName, taskId).ifPresentOrElse(task -> {

                System.out.println();
                System.out.println("Subtasks for Task #" + taskId + " - " + task.getTitle());
                System.out.println();

                List<SubTask> subs = task.getSubtasks();

                if (subs.isEmpty()) {
                    ConsolePrinter.info("No subtasks yet. Add one with:");
                    ConsolePrinter.info("  task subtask add " + projectName + " " + taskId + " \"<title>\"");
                    return;
                }

                int done = task.getDoneSubtaskCount();
                System.out.println("Progress: " + done + "/" + subs.size() + " done");
                System.out.println();

                for (int i = 0; i < subs.size(); i++) {
                    SubTask st = subs.get(i);
                    String tick = st.isDone() ? "✓" : "○";
                    System.out.println("  " + i + ".  " + tick + "  " + st.getTitle());
                }

                System.out.println();

            }, () -> ConsolePrinter.error("Task #" + taskId + " not found."));
        }
    }

    // ======================
    // SUBTASK DELETE
    // ======================

    @Command(name = "delete", description = "Delete a subtask by its index (0-based)")
    static class SubtaskDelete implements Runnable {

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Parameters(index = "1", description = "Task ID")
        private int taskId;

        @Parameters(index = "2", description = "Subtask index (0-based, see: task subtask list)")
        private int subtaskIndex;

        private final TaskService taskService = new TaskService();

        @Override
        public void run() {

            // Capture the title before deletion so the confirmation message is useful
            String subtaskTitle = taskService.getTask(projectName, taskId)
                    .map(task -> {
                        List<SubTask> subs = task.getSubtasks();
                        if (subtaskIndex >= 0 && subtaskIndex < subs.size()) {
                            return subs.get(subtaskIndex).getTitle();
                        }
                        return null;
                    })
                    .orElse(null);

            boolean deleted = taskService.deleteSubtask(projectName, taskId, subtaskIndex);

            if (deleted) {
                ConsolePrinter.success(
                        "Subtask " + subtaskIndex
                                + (subtaskTitle != null ? " \"" + subtaskTitle + "\"" : "")
                                + " deleted from Task #" + taskId + ".");
            } else {
                taskService.getTask(projectName, taskId).ifPresentOrElse(task -> {
                    int total = task.getTotalSubtaskCount();
                    if (total == 0) {
                        ConsolePrinter.error("Task #" + taskId + " has no subtasks.");
                    } else {
                        ConsolePrinter.error(
                                "Invalid index " + subtaskIndex
                                        + ". Valid range: 0-" + (total - 1)
                                        + ". Run 'task subtask list' to see indexes.");
                    }
                }, () -> ConsolePrinter.error("Task #" + taskId + " not found."));
            }
        }
    }
}