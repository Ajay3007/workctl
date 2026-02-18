package com.workctl.core.model;

import java.time.LocalDate;
import java.util.Map;

public class ProjectInsights {

    private int totalTasks;
    private int openTasks;
    private int inProgressTasks;
    private int doneTasks;
    private int completedThisWeek;
    private double completionRate;
    private String mostUsedTag;

    // 🔥 NEW FIELDS
    private double productivityScore;
    private long stagnantTasks;
    private Map<LocalDate, Integer> dailyActivity;

    public ProjectInsights(
            int totalTasks,
            int openTasks,
            int inProgressTasks,
            int doneTasks,
            int completedThisWeek,
            double completionRate,
            String mostUsedTag,
            double productivityScore,
            long stagnantTasks,
            Map<LocalDate, Integer> dailyActivity
    ) {

        this.totalTasks = totalTasks;
        this.openTasks = openTasks;
        this.inProgressTasks = inProgressTasks;
        this.doneTasks = doneTasks;
        this.completedThisWeek = completedThisWeek;
        this.completionRate = completionRate;
        this.mostUsedTag = mostUsedTag;
        this.productivityScore = productivityScore;
        this.stagnantTasks = stagnantTasks;
        this.dailyActivity = dailyActivity;
    }

    // getters...
    public int getTotalTasks() {
        return totalTasks;
    }

    public int getOpenTasks() {
        return openTasks;
    }

    public int getInProgressTasks() {
        return inProgressTasks;
    }

    public int getDoneTasks() {
        return doneTasks;
    }

    public int getCompletedThisWeek() {
        return completedThisWeek;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public String getMostUsedTag() {
        return mostUsedTag;
    }

    public double getProductivityScore() {
        return productivityScore;
    }

    public long getStagnantTasks() {
        return stagnantTasks;
    }

    public Map<LocalDate, Integer> getDailyActivity() {
        return dailyActivity;
    }

}
