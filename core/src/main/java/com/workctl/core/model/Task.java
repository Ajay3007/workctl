package com.workctl.core.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Task {

    // ===============================
    // SubTask inner class (NEW)
    //
    // Stored in tasks.md as 4-space-indented checkbox lines:
    //   1. [ ] (P2) Task title  <!-- created=... -->
    //       - [ ] Subtask one
    //       - [x] Done subtask
    // ===============================
    public static class SubTask {

        private String title;
        private boolean done;

        public SubTask(String title, boolean done) {
            this.title = title;
            this.done  = done;
        }

        public String  getTitle()          { return title; }
        public boolean isDone()            { return done; }
        public void    setTitle(String t)  { this.title = t; }
        public void    setDone(boolean d)  { this.done  = d; }

        /** Serialize to a 4-space-indented markdown checkbox line */
        public String toMarkdownLine() {
            return "    - " + (done ? "[x]" : "[ ]") + " " + title;
        }

        /**
         * Parse a raw file line of the form "    - [ ] title" or "    - [x] title".
         * Returns null if the line is not a subtask line.
         */
        public static SubTask fromLine(String rawLine) {
            Pattern p = Pattern.compile("^    - \\[([ x])\\] (.+)$");
            Matcher m = p.matcher(rawLine);
            if (!m.matches()) return null;
            return new SubTask(m.group(2).trim(), m.group(1).equals("x"));
        }
    }

    // ===============================
    // Original fields — UNCHANGED
    // ===============================
    private int id;
    private String description;
    private TaskStatus status;
    private List<String> tags;
    private int priority; // 1 = High, 2 = Medium, 3 = Low
    private LocalDate createdDate;

    // NEW field
    private List<SubTask> subtasks = new ArrayList<>();

    // ===============================
    // Original constructors — UNCHANGED
    // ===============================

    public Task(int id,
                String description,
                TaskStatus status,
                List<String> tags,
                int priority,
                LocalDate createdDate) {

        this.id = id;
        this.description = description != null ? description : "";
        this.status = status;
        this.tags = tags;
        this.priority = priority;
        this.createdDate = createdDate;
        this.subtasks = new ArrayList<>();
    }

    public Task(int id,
                String description,
                TaskStatus status,
                List<String> tags,
                int priority) {

        this(id,
                description,
                status,
                tags,
                priority,
                LocalDate.now());  // default createdDate
    }

    // Backward compatible constructor
    public Task(int id,
                String description,
                TaskStatus status,
                List<String> tags) {

        this(id,
                description,
                status,
                tags,
                2,                // default priority = Medium
                LocalDate.now()); // default createdDate
    }


    // ===============================
    // Getters — ORIGINAL UNCHANGED
    // ===============================

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public List<String> getTags() {
        return tags;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDate getCreatedDate() {
        return createdDate;
    }

    // NEW getters
    public List<SubTask> getSubtasks() {
        if (subtasks == null) subtasks = new ArrayList<>();
        return subtasks;
    }

    public boolean hasSubtasks() {
        return subtasks != null && !subtasks.isEmpty();
    }

    public int getDoneSubtaskCount() {
        if (subtasks == null) return 0;
        return (int) subtasks.stream().filter(SubTask::isDone).count();
    }

    public int getTotalSubtaskCount() {
        return subtasks == null ? 0 : subtasks.size();
    }

    // ===============================
    // Setters — ORIGINAL UNCHANGED
    // ===============================

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // NEW setter
    public void setSubtasks(List<SubTask> subtasks) {
        this.subtasks = subtasks != null ? subtasks : new ArrayList<>();
    }

    // ===============================
    // Derived Methods — ORIGINAL UNCHANGED
    // ===============================

    public String getTitle() {

        if (description == null || description.isBlank()) {
            return "";
        }

        String firstLine = description.split("\\R")[0];

        // Remove accidental metadata
        firstLine = firstLine.replaceAll("<!--.*?-->", "").trim();

        return firstLine;
    }


    // ===============================
    // Markdown Serialization — ORIGINAL UNCHANGED
    // ===============================

    public String toMarkdown() {

        String checkbox = switch (status) {
            case OPEN -> "[ ]";
            case IN_PROGRESS -> "[~]";
            case DONE -> "[x]";
        };

        StringBuilder sb = new StringBuilder();

        sb.append(id)
                .append(". ")
                .append(checkbox)
                .append(" ")
                .append(getTitle());

        // Handle multiline description
        if (description != null && description.contains("\n")) {

            String[] lines = description.split("\\R");

            for (int i = 1; i < lines.length; i++) {
                sb.append("\n    ")
                        .append(lines[i]);
            }
        }

        return sb.toString();
    }

    public static Task fromMarkdownBlock(List<String> lines) {

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Empty task block");
        }

        String firstLine = lines.get(0).trim();

        Pattern pattern = Pattern.compile("(\\d+)\\. \\[(.)] (.+)");
        Matcher matcher = pattern.matcher(firstLine);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid task markdown: " + firstLine);
        }

        int id = Integer.parseInt(matcher.group(1));
        String statusSymbol = matcher.group(2);
        String title = matcher.group(3);

        TaskStatus status = switch (statusSymbol) {
            case " " -> TaskStatus.OPEN;
            case "~" -> TaskStatus.IN_PROGRESS;
            case "x" -> TaskStatus.DONE;
            default -> throw new IllegalArgumentException("Unknown status: " + statusSymbol);
        };

        // Reconstruct full description
        StringBuilder description = new StringBuilder(title);

        for (int i = 1; i < lines.size(); i++) {

            String line = lines.get(i);

            if (line.startsWith("    ")) {
                description.append("\n")
                        .append(line.substring(4));
            } else if (!line.isBlank()) {
                // fallback (in case indentation missing)
                description.append("\n")
                        .append(line.trim());
            }
        }

        return new Task(id, description.toString(), status, new ArrayList<>());
    }

    // ===============================
    // Equality — ORIGINAL UNCHANGED
    // ===============================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return id == task.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}