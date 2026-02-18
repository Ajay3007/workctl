package com.workctl.core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Meeting {
    private String id;
    private String projectId;
    private String title;
    private LocalDateTime dateTime;
    private String attendees;
    private String agenda;
    private String notes;
    private LocalDateTime createdAt;

    public Meeting(String projectId, String title, LocalDateTime dateTime) {
        this.id = UUID.randomUUID().toString();
        this.projectId = projectId;
        this.title = title;
        this.dateTime = dateTime;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getAttendees() {
        return attendees;
    }

    public void setAttendees(String attendees) {
        this.attendees = attendees;
    }

    public String getAgenda() {
        return agenda;
    }

    public void setAgenda(String agenda) {
        this.agenda = agenda;
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
