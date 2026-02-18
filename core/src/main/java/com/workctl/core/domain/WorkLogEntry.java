package com.workctl.core.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkLogEntry {
    private String id;
    private String projectId;
    private LocalDate date;
    private List<String> assigned;
    private List<String> done;
    private List<String> changesSuggested;
    private List<String> commandsUsed;
    private String notes;
    private LocalDateTime createdAt;

    public WorkLogEntry(String projectId, LocalDate date) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.date = date;
        this.assigned = new ArrayList<>();
        this.done = new ArrayList<>();
        this.changesSuggested = new ArrayList<>();
        this.commandsUsed = new ArrayList<>();
        this.notes = "";
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<String> getAssigned() {
        return assigned;
    }

    public List<String> getDone() {
        return done;
    }

    public List<String> getChangesSuggested() {
        return changesSuggested;
    }

    public List<String> getCommandsUsed() {
        return commandsUsed;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
