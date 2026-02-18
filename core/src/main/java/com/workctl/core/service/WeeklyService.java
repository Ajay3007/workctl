package com.workctl.core.service;

import com.workctl.core.domain.WeeklySummary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing weekly summaries.
 */
public class WeeklyService {
    private List<WeeklySummary> summaries;

    public WeeklyService() {
        this.summaries = new ArrayList<>();
    }

    public WeeklySummary createSummary(LocalDate weekStartDate, LocalDate weekEndDate) {
        WeeklySummary summary = new WeeklySummary(weekStartDate, weekEndDate);
        summaries.add(summary);
        return summary;
    }

    public Optional<WeeklySummary> getSummary(String summaryId) {
        return summaries.stream().filter(s -> s.getId().equals(summaryId)).findFirst();
    }

    public List<WeeklySummary> listSummaries() {
        return new ArrayList<>(summaries);
    }

    public boolean deleteSummary(String summaryId) {
        return summaries.removeIf(s -> s.getId().equals(summaryId));
    }
}
