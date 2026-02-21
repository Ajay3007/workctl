package com.workctl.cli.commands;

import com.workctl.cli.util.CliPrompt;
import com.workctl.cli.util.ConsolePrinter;
import com.workctl.cli.util.EditorUtil;
import com.workctl.core.model.Task;
import com.workctl.core.model.Task.SubTask;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                TaskCommand.SubtaskGroup.class
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
            return CliPrompt.promptMultiline("Enter task description");
        }
    }

    // ======================
    // LIST
    // ======================

    @Command(name = "list", description = "List tasks for a project")
    static class ListTasks implements Runnable {

        // Column widths (fixed except TITLE which is dynamic)
        private static final int W_ID       = 4;
        private static final int W_STATUS   = 12;
        private static final int W_PRI      = 4;
        private static final int W_SUBTASKS = 10;
        // separators between columns: 4 gaps × 2 chars = 8
        private static final int W_FIXED    = W_ID + W_STATUS + W_PRI + W_SUBTASKS + 8;
        private static final int W_TITLE_MIN = 20;

        @Parameters(index = "0", description = "Project name")
        private String projectName;

        @Override
        public void run() {
            List<Task> tasks = taskService.getTasks(projectName);

            if (tasks.isEmpty()) {
                ConsolePrinter.info("No tasks found for project: " + projectName);
                return;
            }

            int termWidth = terminalWidth();
            int titleWidth = Math.max(W_TITLE_MIN, termWidth - W_FIXED);

            Map<TaskStatus, List<Task>> grouped = tasks.stream()
                    .collect(Collectors.groupingBy(Task::getStatus));

            System.out.println();
            ConsolePrinter.table(
                    new String[]{"ID", "STATUS", "PRI", "TITLE", "SUBTASKS"},
                    List.of(),   // no rows — just print headers once
                    new int[]{W_ID, W_STATUS, W_PRI, titleWidth, W_SUBTASKS}
            );

            boolean firstGroup = true;
            for (TaskStatus status : TaskStatus.values()) {
                List<Task> group = grouped.getOrDefault(status, List.of());
                if (group.isEmpty()) continue;

                if (!firstGroup) {
                    System.out.println(
                            "\u001B[2m" + "─".repeat(W_ID + W_STATUS + W_PRI + titleWidth + W_SUBTASKS + 8) + "\u001B[0m");
                }
                firstGroup = false;

                group.stream()
                        .sorted(Comparator.comparingInt(Task::getId))
                        .forEach(t -> printRow(t, titleWidth));
            }
            System.out.println();
        }

        private static void printRow(Task task, int titleWidth) {
            String id      = "#" + task.getId();
            String status  = ConsolePrinter.statusBadge(task.getStatus());
            String pri     = ConsolePrinter.priorityBadge(task.getPriority());
            String rawTitle = task.getTitle();
            String title   = rawTitle.length() > titleWidth
                    ? rawTitle.substring(0, titleWidth - 3) + "..."
                    : rawTitle;
            String subtasks = task.hasSubtasks()
                    ? task.getDoneSubtaskCount() + "/" + task.getTotalSubtaskCount() + " \u2713"
                    : "";

            System.out.println(
                    ConsolePrinter.padRight(id,      W_ID)      + "  " +
                    ConsolePrinter.padRight(status,  W_STATUS)  + "  " +
                    ConsolePrinter.padRight(pri,     W_PRI)     + "  " +
                    ConsolePrinter.padRight(title,   titleWidth) + "  " +
                    subtasks
            );
        }

        private static String statusLabel(TaskStatus status) {
            return switch (status) {
                case OPEN        -> "Open";
                case IN_PROGRESS -> "In Progress";
                case DONE        -> "Done";
            };
        }

        private static int terminalWidth() {
            try {
                org.jline.terminal.Terminal t =
                        org.jline.terminal.TerminalBuilder.builder().dumb(true).build();
                int w = t.getWidth();
                t.close();
                return (w > 0) ? w : 100;
            } catch (Exception e) {
                return 100;
            }
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
                        ConsolePrinter.header("Task #" + task.getId());
                        System.out.println(
                                "  " + ConsolePrinter.priorityBadge(task.getPriority())
                                + "  " + ConsolePrinter.statusBadge(task.getStatus())
                                + "  Created: " + task.getCreatedDate());
                        ConsolePrinter.separator();

                        // Strip inline metadata comments before display
                        String desc = task.getDescription()
                                .replaceAll("<!--.*?-->", "").trim();
                        System.out.println(desc);

                        // ── Subtasks section ─────────────────────────────────────
                        if (task.hasSubtasks()) {

                            List<SubTask> subtasks = task.getSubtasks();
                            int done  = task.getDoneSubtaskCount();
                            int total = task.getTotalSubtaskCount();

                            System.out.println();
                            ConsolePrinter.separator();
                            System.out.println("  Subtasks  " +
                                    ConsolePrinter.progressBar(done, total, 12));
                            System.out.println();

                            for (int i = 0; i < subtasks.size(); i++) {
                                SubTask st = subtasks.get(i);
                                String tick = st.isDone()
                                        ? "\u001B[32m✓\u001B[0m"
                                        : "\u001B[2m○\u001B[0m";
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
                ConsolePrinter.header("Subtasks — Task #" + taskId);

                List<SubTask> subs = task.getSubtasks();

                if (subs.isEmpty()) {
                    ConsolePrinter.info("No subtasks yet. Add one with:");
                    ConsolePrinter.info("  task subtask add " + projectName + " " + taskId + " \"<title>\"");
                    return;
                }

                int done = task.getDoneSubtaskCount();
                System.out.println("  " + ConsolePrinter.progressBar(done, subs.size(), 16));
                System.out.println();

                for (int i = 0; i < subs.size(); i++) {
                    SubTask st = subs.get(i);
                    String tick = st.isDone()
                            ? "\u001B[32m✓\u001B[0m"
                            : "\u001B[2m○\u001B[0m";
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
