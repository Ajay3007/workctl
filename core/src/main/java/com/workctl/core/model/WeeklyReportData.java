package com.workctl.core.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated, intelligence-enriched data for one week's report.
 * Produced by WeeklyReportService — consumed by WeeklyReportController.
 */
public class WeeklyReportData {

    /** A non-done task that has had no status change for > 7 days. */
    public record StagnantEntry(Task task, long daysIdle) {}

    // ── Identity ──────────────────────────────────────────────────
    private final String    projectName;
    private final LocalDate weekStart;
    private final LocalDate weekEnd;
    private final LocalDate generatedDate;

    // ── Counts ────────────────────────────────────────────────────
    private final int    completedThisWeek;
    private final int    inProgressCount;
    private final int    openCount;
    private final int    newTasksThisWeek;
    private final double completionRate;     // overall project completion %
    private final double productivityScore;  // 0-100

    // ── Intelligent labels ────────────────────────────────────────
    private final String       velocityLabel;  // Quiet / Steady / Strong / High / Elite
    private final String       headline;       // one-line narrative sentence
    private final List<String> insights;       // 2-5 rule-derived bullet observations

    // ── Task lists ────────────────────────────────────────────────
    private final List<Task>           completedTasks;
    private final List<Task>           inProgressTasks;
    private final List<Task>           newTasks;
    private final List<StagnantEntry>  stagnantTasks;

    // ── Tag breakdown (events in week) ────────────────────────────
    private final Map<String, Integer> tagActivity;

    // ── Work-log text snippets from this week ─────────────────────
    private final List<String> logHighlights;

    public WeeklyReportData(
            String projectName, LocalDate weekStart, LocalDate weekEnd, LocalDate generatedDate,
            int completedThisWeek, int inProgressCount, int openCount, int newTasksThisWeek,
            double completionRate, double productivityScore,
            String velocityLabel, String headline, List<String> insights,
            List<Task> completedTasks, List<Task> inProgressTasks,
            List<Task> newTasks, List<StagnantEntry> stagnantTasks,
            Map<String, Integer> tagActivity, List<String> logHighlights) {

        this.projectName       = projectName;
        this.weekStart         = weekStart;
        this.weekEnd           = weekEnd;
        this.generatedDate     = generatedDate;
        this.completedThisWeek = completedThisWeek;
        this.inProgressCount   = inProgressCount;
        this.openCount         = openCount;
        this.newTasksThisWeek  = newTasksThisWeek;
        this.completionRate    = completionRate;
        this.productivityScore = productivityScore;
        this.velocityLabel     = velocityLabel;
        this.headline          = headline;
        this.insights          = insights;
        this.completedTasks    = completedTasks;
        this.inProgressTasks   = inProgressTasks;
        this.newTasks          = newTasks;
        this.stagnantTasks     = stagnantTasks;
        this.tagActivity       = tagActivity;
        this.logHighlights     = logHighlights;
    }

    // ── Getters ───────────────────────────────────────────────────

    public String    getProjectName()       { return projectName; }
    public LocalDate getWeekStart()         { return weekStart; }
    public LocalDate getWeekEnd()           { return weekEnd; }
    public LocalDate getGeneratedDate()     { return generatedDate; }
    public int       getCompletedThisWeek() { return completedThisWeek; }
    public int       getInProgressCount()   { return inProgressCount; }
    public int       getOpenCount()         { return openCount; }
    public int       getNewTasksThisWeek()  { return newTasksThisWeek; }
    public double    getCompletionRate()    { return completionRate; }
    public double    getProductivityScore() { return productivityScore; }
    public String    getVelocityLabel()     { return velocityLabel; }
    public String    getHeadline()          { return headline; }

    public List<String>         getInsights()       { return insights; }
    public List<Task>           getCompletedTasks() { return completedTasks; }
    public List<Task>           getInProgressTasks(){ return inProgressTasks; }
    public List<Task>           getNewTasks()       { return newTasks; }
    public List<StagnantEntry>  getStagnantTasks()  { return stagnantTasks; }
    public Map<String, Integer> getTagActivity()    { return tagActivity; }
    public List<String>         getLogHighlights()  { return logHighlights; }
}
