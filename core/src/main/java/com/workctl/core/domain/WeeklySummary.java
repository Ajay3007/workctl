package com.workctl.core.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WeeklySummary {
    private String id;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private List<String> accomplishments;
    private List<String> challenges;
    private List<String> nextWeekPlan;
    private LocalDateTime createdAt;

    public WeeklySummary(LocalDate weekStartDate, LocalDate weekEndDate) {
        this.id = UUID.randomUUID().toString();
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.accomplishments = new ArrayList<>();
        this.challenges = new ArrayList<>();
        this.nextWeekPlan = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public List<String> getAccomplishments() {
        return accomplishments;
    }

    public List<String> getChallenges() {
        return challenges;
    }

    public List<String> getNextWeekPlan() {
        return nextWeekPlan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
