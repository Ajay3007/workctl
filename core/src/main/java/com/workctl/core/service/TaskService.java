package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.Task;
import com.workctl.core.model.Task.SubTask;
import com.workctl.core.model.TaskStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class TaskService {

    private final ProjectService projectService = new ProjectService();

    public void addTask(String projectName,
                        String description,
                        List<String> tags,
                        int priority)
    {
        modifyTasks(projectName, tasksData -> {

            int nextId = tasksData.nextId;

            Task task = new Task(
                    nextId,
                    description,
                    TaskStatus.OPEN,
                    tags == null ? new ArrayList<>() : tags,
                    priority,
                    LocalDate.now()
            );

            tasksData.tasks.add(task);
            tasksData.nextId++;

            autoLog(projectName, task, "created", null);

        });
    }

    public void startTask(String projectName, int id) {
        changeStatus(projectName, id, TaskStatus.IN_PROGRESS);
    }

    public void completeTask(String projectName, int id) {

        changeStatus(projectName, id, TaskStatus.DONE);

    }

    private void changeStatus(String projectName, int id, TaskStatus newStatus) {

        modifyTasks(projectName, tasksData -> {

            Task task = tasksData.tasks.stream()
                    .filter(t -> t.getId() == id)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Task not found"));

            TaskStatus previousStatus = task.getStatus();

            task.setStatus(newStatus);
            task.setUpdatedDate(LocalDate.now());
            if (newStatus == TaskStatus.DONE) {
                task.setCompletedDate(LocalDate.now());
            } else if (previousStatus == TaskStatus.DONE) {
                task.setCompletedDate(null); // clear when reopening
            }

            if (newStatus == TaskStatus.IN_PROGRESS) {
                String action = (previousStatus == TaskStatus.DONE) ? "reopened" : "started";
                autoLog(projectName, task, action, previousStatus);
            } else if (newStatus == TaskStatus.DONE) {
                autoLog(projectName, task, "completed", previousStatus);
            } else if (newStatus == TaskStatus.OPEN && previousStatus == TaskStatus.DONE) {
                autoLog(projectName, task, "reopened", previousStatus);
            }
            // IN_PROGRESS → OPEN: no log (minor reversion, not a meaningful lifecycle event)
        });
    }



    // ========================
    // INTERNAL FILE ENGINE
    // ========================

    private void modifyTasks(String projectName, TaskModifier modifier) {

        TasksData data = loadTasks(projectName);

        modifier.apply(data);

        writeTasks(projectName, data);
    }

    private TasksData loadTasks(String projectName) {

        try {
            AppConfig config = ConfigManager.load();
            Path tasksFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("tasks.md");

            if (!Files.exists(tasksFile)) {
                initializeFile(tasksFile, projectName);
            }

            List<String> lines = Files.readAllLines(tasksFile);

            TasksData data = parseTasks(lines);
            backfillDatesFromLog(projectName, data.tasks);
            return data;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load tasks", e);
        }
    }

    private void writeTasks(String projectName, TasksData data) {

        try {
            AppConfig config = ConfigManager.load();
            Path tasksFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("tasks.md");

            StringBuilder sb = new StringBuilder();

            sb.append("# Tasks – ").append(projectName).append("\n\n");
            sb.append("<!-- NEXT_ID: ").append(data.nextId).append(" -->\n\n");

            Map<TaskStatus, List<Task>> grouped = groupByStatus(data.tasks);

            for (TaskStatus status : TaskStatus.values()) {

                sb.append("## ").append(statusLabel(status)).append("\n");

                grouped.getOrDefault(status, List.of())
                        .stream()
                        .sorted(Comparator.comparingInt(Task::getId))
                        .forEach(t -> sb.append(formatTask(t)).append("\n"));

                sb.append("\n");
            }

            Files.writeString(tasksFile, sb.toString());

        } catch (IOException e) {
            throw new RuntimeException("Failed to write tasks", e);
        }
    }

    private TasksData parseTasks(List<String> lines) {

        List<Task> tasks = new ArrayList<>();
        int nextId = 1;
        TaskStatus currentStatus = null;

        Pattern nextIdPattern    = Pattern.compile("NEXT_ID: (\\d+)");
        Pattern taskPattern      = Pattern.compile("(\\d+)\\. \\[(.)\\](?: \\(P(\\d)\\))? (.+)");
        Pattern metaPattern      = Pattern.compile("created=(\\d{4}-\\d{2}-\\d{2})");
        Pattern updatedPattern   = Pattern.compile("updated=(\\d{4}-\\d{2}-\\d{2})");
        Pattern completedPattern = Pattern.compile("completed=(\\d{4}-\\d{2}-\\d{2})");

        Integer currentId = null;
        StringBuilder descriptionBuilder = null;
        TaskStatus currentTaskStatus = null;
        int currentPriority = 2;
        LocalDate currentCreatedDate   = LocalDate.now();
        LocalDate currentUpdatedDate   = null;
        LocalDate currentCompletedDate = null;
        // NEW: accumulate subtasks per task
        List<SubTask> currentSubtasks = new ArrayList<>();

        for (String line : lines) {

            // ======================
            // NEXT_ID
            // ======================
            Matcher nextMatcher = nextIdPattern.matcher(line);
            if (nextMatcher.find()) {
                nextId = Integer.parseInt(nextMatcher.group(1));
                continue;
            }

            // ======================
            // SECTION HEADER
            // ======================
            if (line.startsWith("## ")) {
                // Save current task before switching sections
                if (currentId != null) {
                    tasks.add(buildTask(currentId, descriptionBuilder,
                            currentTaskStatus, currentPriority,
                            currentCreatedDate, currentUpdatedDate,
                            currentCompletedDate, currentSubtasks));
                    currentId = null;
                    currentSubtasks     = new ArrayList<>();
                    currentUpdatedDate   = null;
                    currentCompletedDate = null;
                }
                String section = line.substring(3).trim();
                currentStatus = switch (section) {
                    case "Open" -> TaskStatus.OPEN;
                    case "In Progress" -> TaskStatus.IN_PROGRESS;
                    case "Done" -> TaskStatus.DONE;
                    default -> currentStatus;
                };
                continue;
            }

            // ======================
            // TASK HEADER LINE
            // ======================
            Matcher taskMatcher = taskPattern.matcher(line.trim());

            if (taskMatcher.matches() && currentStatus != null) {

                // Save previous task
                if (currentId != null) {
                    tasks.add(buildTask(currentId, descriptionBuilder,
                            currentTaskStatus, currentPriority,
                            currentCreatedDate, currentUpdatedDate,
                            currentCompletedDate, currentSubtasks));
                    currentSubtasks     = new ArrayList<>();
                    currentUpdatedDate   = null;
                    currentCompletedDate = null;
                }

                currentId = Integer.parseInt(taskMatcher.group(1));

                String priorityGroup = taskMatcher.group(3);
                currentPriority = (priorityGroup != null)
                        ? Integer.parseInt(priorityGroup)
                        : 2;

                // Raw group 4 includes the inline metadata comment; strip it for title.
                String rawGroup4  = taskMatcher.group(4).trim();
                String titlePart  = rawGroup4.replaceAll("\\s*<!--.*?-->\\s*$", "").trim();
                descriptionBuilder = new StringBuilder(titlePart);

                currentTaskStatus = currentStatus;

                // Parse all three dates from the inline comment (e.g. <!-- created=... updated=... -->)
                Matcher cm = metaPattern.matcher(rawGroup4);
                currentCreatedDate   = cm.find() ? LocalDate.parse(cm.group(1)) : LocalDate.now();

                Matcher um = updatedPattern.matcher(rawGroup4);
                currentUpdatedDate   = um.find() ? LocalDate.parse(um.group(1)) : null;

                Matcher dm = completedPattern.matcher(rawGroup4);
                currentCompletedDate = dm.find() ? LocalDate.parse(dm.group(1)) : null;

                continue;
            }

            if (currentId == null) continue;

            // ======================
            // 4-SPACE INDENTED LINES
            // ======================
            if (line.startsWith("    ")) {

                // NEW: Check for subtask line first (    - [ ] or    - [x])
                SubTask st = SubTask.fromLine(line);
                if (st != null) {
                    currentSubtasks.add(st);
                    continue;
                }

                String trimmed = line.trim();

                // ---- Extract metadata ----
                if (trimmed.startsWith("<!--")) {

                    Matcher metaMatcher = metaPattern.matcher(trimmed);

                    if (metaMatcher.find()) {
                        currentCreatedDate =
                                LocalDate.parse(metaMatcher.group(1));
                    }

                    continue; // Do NOT append metadata to description
                }

                // ---- Normal multiline ----
                descriptionBuilder.append("\n")
                        .append(trimmed);
            }
        }

        // ======================
        // Add last task
        // ======================
        if (currentId != null) {
            tasks.add(buildTask(currentId, descriptionBuilder,
                    currentTaskStatus, currentPriority,
                    currentCreatedDate, currentUpdatedDate,
                    currentCompletedDate, currentSubtasks));
        }

        return new TasksData(tasks, nextId);
    }

    /** Helper to construct a Task and attach its subtask list */
    private Task buildTask(int id, StringBuilder desc, TaskStatus status,
                           int priority, LocalDate created,
                           LocalDate updated, LocalDate completed,
                           List<SubTask> subtasks) {
        Task t = new Task(
                id,
                desc != null ? desc.toString().trim() : "",
                status,
                new ArrayList<>(),
                priority,
                created
        );
        t.setUpdatedDate(updated);
        t.setCompletedDate(completed);
        t.setSubtasks(new ArrayList<>(subtasks));
        return t;
    }


    /**
     * Reads the project's work-log.md and, for any task whose logged "created"
     * date is earlier than the currently-stored createdDate, replaces the
     * createdDate with the log value.  This repairs tasks that had their date
     * overwritten by the old parse-default-to-today bug.
     *
     * Runs on every load but is a fast, side-effect-free best-effort pass —
     * it never throws and never writes to disk on its own.
     */
    private void backfillDatesFromLog(String projectName, List<Task> tasks) {
        try {
            Path logFile = getLogFilePath(projectName);
            if (!Files.exists(logFile)) return;

            List<String> logLines = Files.readAllLines(logFile);

            Pattern idPattern     = Pattern.compile("id=(\\d+)");
            Pattern actionPattern = Pattern.compile("action=(\\w+)");
            Pattern datePattern   = Pattern.compile("date=(\\d{4}-\\d{2}-\\d{2})");

            // Collect earliest "created" date per task ID from all log events
            Map<Integer, LocalDate> createdDates = new HashMap<>();

            boolean   inEvent     = false;
            Integer   eventId     = null;
            String    eventAction = null;
            LocalDate eventDate   = null;

            for (String line : logLines) {

                if (line.contains("TASK_EVENT:")) {
                    inEvent = true;
                    eventId = null; eventAction = null; eventDate = null;
                    continue;
                }

                if (!inEvent) continue;

                // End-of-block marker
                if (line.trim().startsWith("-->")) {
                    if ("created".equals(eventAction) && eventId != null && eventDate != null) {
                        createdDates.merge(eventId, eventDate,
                                (existing, candidate) -> candidate.isBefore(existing) ? candidate : existing);
                    }
                    inEvent = false;
                    continue;
                }

                Matcher idM = idPattern.matcher(line);
                if (idM.find()) eventId = Integer.parseInt(idM.group(1));

                Matcher actM = actionPattern.matcher(line);
                if (actM.find()) eventAction = actM.group(1);

                Matcher dateM = datePattern.matcher(line);
                if (dateM.find()) eventDate = LocalDate.parse(dateM.group(1));
            }

            // Apply: use log date whenever it is earlier than the stored createdDate
            for (Task task : tasks) {
                LocalDate logDate = createdDates.get(task.getId());
                if (logDate != null) {
                    LocalDate stored = task.getCreatedDate();
                    if (stored == null || logDate.isBefore(stored)) {
                        task.setCreatedDate(logDate);
                    }
                }
            }

        } catch (Exception ignored) {
            // Backfill is best-effort — never break normal task loading
        }
    }

    private void initializeFile(Path file, String projectName) throws IOException {

        Files.createDirectories(file.getParent());

        String template = """
                # Tasks – %s

                <!-- NEXT_ID: 1 -->

                ## Open

                ## In Progress

                ## Done

                """.formatted(projectName);

        Files.writeString(file, template);
    }

    private Map<TaskStatus, List<Task>> groupByStatus(List<Task> tasks) {
        return tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
    }

    private String formatTask(Task task) {

        String checkbox = switch (task.getStatus()) {
            case OPEN -> "[ ]";
            case IN_PROGRESS -> "[~]";
            case DONE -> "[x]";
        };

        String priorityLabel = "(P" + task.getPriority() + ")";

        String[] lines = task.getDescription().split("\\R");

        StringBuilder sb = new StringBuilder();

        // ===== First Line (Single Source of Truth) =====
        // Strip any pre-existing metadata comment from lines[0] (defensive de-dup)
        String firstLine = lines[0].replaceAll("\\s*<!--.*?-->\\s*$", "").trim();
        sb.append(task.getId())
                .append(". ")
                .append(checkbox)
                .append(" ")
                .append(priorityLabel)
                .append(" ")
                .append(firstLine)
                .append("  <!-- created=")
                .append(task.getCreatedDate());
        if (task.getUpdatedDate() != null)
            sb.append(" updated=").append(task.getUpdatedDate());
        if (task.getCompletedDate() != null)
            sb.append(" completed=").append(task.getCompletedDate());
        sb.append(" -->");

        // ===== Remaining lines (indented description only) =====
        for (int i = 1; i < lines.length; i++) {
            sb.append("\n    ").append(lines[i]);
        }

        // ===== Subtask lines (NEW) =====
        for (SubTask st : task.getSubtasks()) {
            sb.append("\n").append(st.toMarkdownLine());
        }

        return sb.toString();
    }


    private String statusLabel(TaskStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case DONE -> "Done";
        };
    }

    private static class TasksData {
        List<Task> tasks;
        int nextId;

        TasksData(List<Task> tasks, int nextId) {
            this.tasks = tasks;
            this.nextId = nextId;
        }
    }

    private interface TaskModifier {
        void apply(TasksData data);
    }

    public Optional<Task> getTask(String projectName, int id) {

        TasksData data = loadTasks(projectName);

        return data.tasks.stream()
                .filter(t -> t.getId() == id)
                .findFirst();
    }

    private void autoLog(String projectName,
                         Task task,
                         String action,
                         TaskStatus previousStatus) {

        try {

            ProjectService projectService = new ProjectService();
            AppConfig config = ConfigManager.load();

            String title = task.getTitle();

            String message = switch (action) {
                case "created" ->
                        "Created Task #" + task.getId() + " – " + title;
                case "started" ->
                        "Started Task #" + task.getId() + " – " + title;
                case "completed" ->
                        "Completed Task #" + task.getId() + " – " + title;
                case "reopened" ->
                        "Reopened Task #" + task.getId() + " – " + title;
                default ->
                        "Updated Task #" + task.getId();
            };

            String metadata = """
            <!-- TASK_EVENT:
                 id=%d
                 action=%s
                 previousStatus=%s
                 status=%s
                 date=%s
                 tags=%s
            -->
            """.formatted(
                    task.getId(),
                    action,
                    previousStatus == null ? "NONE" : previousStatus,
                    task.getStatus(),
                    LocalDate.now(),
                    task.getTags() == null ? "" : String.join(",", task.getTags())
            );

            String section = switch (action) {
                case "created", "started", "reopened" -> "assigned";
                case "completed" -> "done";
                default -> "done";
            };

            projectService.addLogEntry(
                    projectName,
                    message + "\n" + metadata,
                    section,
                    List.of("task", action)
            );

        } catch (Exception ignored) {
            // lifecycle must never break because of logging
        }
    }

    public List<Task> getTasks(String projectName) {
        TasksData data = loadTasks(projectName);
        return data.tasks;
    }


    public List<Task> getAllTasks(Path workspace, String projectName) {

        try {
            Path tasksFile = workspace
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("tasks.md");

            if (!Files.exists(tasksFile)) {
                return List.of();
            }

            List<String> lines = Files.readAllLines(tasksFile);
            TasksData data = parseTasks(lines);

            return data.tasks;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load tasks", e);
        }
    }


    private Map<Integer, LocalDate> getLastStatusChangeMap(String projectName) {

        Map<Integer, LocalDate> lastChangeMap = new HashMap<>();

        try {
            Path logFile = getLogFilePath(projectName);
            if (!Files.exists(logFile)) return lastChangeMap;

            List<String> lines = Files.readAllLines(logFile);

            Pattern idPattern = Pattern.compile("id=(\\d+)");
            Pattern datePattern = Pattern.compile("date=(\\d{4}-\\d{2}-\\d{2})");

            Integer currentId = null;
            LocalDate currentDate = null;

            for (String line : lines) {

                Matcher idMatcher = idPattern.matcher(line);
                Matcher dateMatcher = datePattern.matcher(line);

                if (idMatcher.find()) {
                    currentId = Integer.parseInt(idMatcher.group(1));
                }

                if (dateMatcher.find()) {
                    currentDate = LocalDate.parse(dateMatcher.group(1));
                }

                if (currentId != null && currentDate != null) {

                    lastChangeMap.merge(
                            currentId,
                            currentDate,
                            (oldDate, newDate) -> newDate.isAfter(oldDate) ? newDate : oldDate
                    );

                    currentId = null;
                    currentDate = null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return lastChangeMap;
    }

    private Path getLogFilePath(String projectName) throws IOException {

        AppConfig config = ConfigManager.load();

        return Paths.get(config.getWorkspace())
                .resolve("01_Projects")
                .resolve(projectName)
                .resolve("notes")
                .resolve("work-log.md");
    }


    private long calculateStagnantTasks(String projectName) {

        List<Task> tasks = getTasks(projectName);
        Map<Integer, LocalDate> lastChangeMap = getLastStatusChangeMap(projectName);

        LocalDate today = LocalDate.now();

        return tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .filter(t -> {
                    LocalDate lastChange = lastChangeMap.get(t.getId());
                    if (lastChange == null) return false;
                    return ChronoUnit.DAYS.between(lastChange, today) > 7;
                })
                .count();
    }

    public void updateStatus(String projectName,
                             int taskId,
                             TaskStatus newStatus) {

        modifyTasks(projectName, data -> {

            Task task = data.tasks.stream()
                    .filter(t -> t.getId() == taskId)
                    .findFirst()
                    .orElseThrow();

            if (task.getStatus() == newStatus) return;

            TaskStatus previous = task.getStatus();

            task.setStatus(newStatus);
            task.setUpdatedDate(LocalDate.now());
            if (newStatus == TaskStatus.DONE) {
                task.setCompletedDate(LocalDate.now());
            } else if (previous == TaskStatus.DONE) {
                task.setCompletedDate(null); // clear when reopening
            }

            if (newStatus == TaskStatus.IN_PROGRESS) {
                String action = (previous == TaskStatus.DONE) ? "reopened" : "started";
                autoLog(projectName, task, action, previous);
            } else if (newStatus == TaskStatus.DONE) {
                autoLog(projectName, task, "completed", previous);
            } else if (newStatus == TaskStatus.OPEN && previous == TaskStatus.DONE) {
                autoLog(projectName, task, "reopened", previous);
            }
            // IN_PROGRESS → OPEN: no log (minor reversion, not a meaningful lifecycle event)
        });
    }


    private Path getTaskFilePath(String projectName) throws IOException {

        AppConfig config = ConfigManager.load();

        return Paths.get(config.getWorkspace())
                .resolve("01_Projects")
                .resolve(projectName)
                .resolve("notes")
                .resolve("tasks.md");
    }

    public void updateDescription(String projectName,
                                  int taskId,
                                  String newDescription) {

        modifyTasks(projectName, data -> {
            data.tasks.stream()
                    .filter(t -> t.getId() == taskId)
                    .findFirst()
                    .ifPresent(t -> {
                        t.setDescription(newDescription);
                        t.setUpdatedDate(LocalDate.now());
                    });
        });
    }

    public void updatePriority(String projectName,
                               int taskId,
                               int newPriority) {

        modifyTasks(projectName, data -> {
            data.tasks.stream()
                    .filter(t -> t.getId() == taskId)
                    .findFirst()
                    .ifPresent(t -> {
                        t.setPriority(newPriority);
                        t.setUpdatedDate(LocalDate.now());
                    });
        });
    }

    public void deleteTask(String projectName, int taskId) {

        modifyTasks(projectName, data -> {

            data.tasks.removeIf(t -> t.getId() == taskId);

        });
    }

    // ========================
    // NEW — SUBTASK METHODS
    // ========================

    /**
     * Append a new open subtask to an existing task.
     */
    public void addSubtask(String projectName, int taskId, String title) {
        modifyTasks(projectName, data ->
                data.tasks.stream()
                        .filter(t -> t.getId() == taskId)
                        .findFirst()
                        .ifPresent(t -> {
                            t.getSubtasks().add(new SubTask(title, false));
                            t.setUpdatedDate(LocalDate.now());
                        })
        );
    }

    /**
     * Toggle done/not-done for a subtask by 0-based index.
     */
    public void toggleSubtask(String projectName, int taskId, int subtaskIndex) {
        modifyTasks(projectName, data ->
                data.tasks.stream()
                        .filter(t -> t.getId() == taskId)
                        .findFirst()
                        .ifPresent(t -> {
                            List<SubTask> subs = t.getSubtasks();
                            if (subtaskIndex >= 0 && subtaskIndex < subs.size()) {
                                subs.get(subtaskIndex).setDone(!subs.get(subtaskIndex).isDone());
                                t.setUpdatedDate(LocalDate.now());
                            }
                        })
        );
    }

    /**
     * Replace the entire subtask list for a task.
     * Called when the Add/Edit dialog is saved.
     */
    public void setSubtasks(String projectName, int taskId, List<SubTask> subtasks) {
        modifyTasks(projectName, data ->
                data.tasks.stream()
                        .filter(t -> t.getId() == taskId)
                        .findFirst()
                        .ifPresent(t -> {
                            t.setSubtasks(new ArrayList<>(subtasks));
                            t.setUpdatedDate(LocalDate.now());
                        })
        );
    }

    /**
     * Delete a subtask by 0-based index.
     *
     * Returns true  — subtask removed successfully.
     * Returns false — task not found, or index out of range (caller prints the error).
     */
    public boolean deleteSubtask(String projectName, int taskId, int subtaskIndex) {

        // Validate bounds BEFORE opening a write lock, so we can return false cleanly
        Optional<Task> taskOpt = getTask(projectName, taskId);
        if (taskOpt.isEmpty()) return false;
        if (subtaskIndex < 0 || subtaskIndex >= taskOpt.get().getSubtasks().size()) return false;

        modifyTasks(projectName, data ->
                data.tasks.stream()
                        .filter(t -> t.getId() == taskId)
                        .findFirst()
                        .ifPresent(t -> {
                            t.getSubtasks().remove(subtaskIndex);
                            t.setUpdatedDate(LocalDate.now());
                        })
        );
        return true;
    }

}