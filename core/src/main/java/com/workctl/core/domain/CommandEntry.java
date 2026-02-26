package com.workctl.core.domain;

import java.util.Objects;
import java.util.UUID;

public class CommandEntry {

    private String id;
    private String category;
    private String title;
    private String command;
    private String notes;
    private String projectTag;

    public CommandEntry() {
        this.id = UUID.randomUUID().toString();
        this.projectTag = "GLOBAL"; // Default
    }

    public CommandEntry(String category, String title, String command, String notes, String projectTag) {
        this.id = UUID.randomUUID().toString();
        this.category = category;
        this.title = title;
        this.command = command;
        this.notes = notes;
        this.projectTag = (projectTag == null || projectTag.isBlank()) ? "GLOBAL" : projectTag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getProjectTag() {
        return projectTag;
    }

    public void setProjectTag(String projectTag) {
        this.projectTag = (projectTag == null || projectTag.isBlank()) ? "GLOBAL" : projectTag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CommandEntry that = (CommandEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CommandEntry{" +
                "category='" + category + '\'' +
                ", title='" + title + '\'' +
                ", command='" + command + '\'' +
                ", projectTag='" + projectTag + '\'' +
                '}';
    }
}
