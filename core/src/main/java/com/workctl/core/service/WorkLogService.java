package com.workctl.core.service;

import com.workctl.core.domain.WorkLogEntry;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing work log entries.
 */
public class WorkLogService {
    private List<WorkLogEntry> entries;

    public WorkLogService() {
        this.entries = new ArrayList<>();
    }

    public WorkLogEntry createEntry(String projectId, LocalDate date) {
        WorkLogEntry entry = new WorkLogEntry(projectId, date);
        entries.add(entry);
        return entry;
    }

    public Optional<WorkLogEntry> getEntry(String entryId) {
        return entries.stream().filter(e -> e.getId().equals(entryId)).findFirst();
    }

    public List<WorkLogEntry> listEntries(String projectId) {
        List<WorkLogEntry> projectEntries = new ArrayList<>();
        for (WorkLogEntry entry : entries) {
            if (entry.getProjectId().equals(projectId)) {
                projectEntries.add(entry);
            }
        }
        return projectEntries;
    }

    public boolean deleteEntry(String entryId) {
        return entries.removeIf(e -> e.getId().equals(entryId));
    }
}
