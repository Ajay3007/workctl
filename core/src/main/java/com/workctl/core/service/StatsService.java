package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.ProjectInsights;
import com.workctl.core.model.Task;
import com.workctl.core.model.TaskStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class StatsService {

    private static final Pattern EVENT_PATTERN =
            Pattern.compile("TASK_EVENT:(.*?)-->", Pattern.DOTALL);

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(\\w+)=(.+)");

    public void generate(String projectName) {

        try {
            AppConfig config = ConfigManager.load();

            Path logFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                System.out.println("No logs found.");
                return;
            }

            String content = Files.readString(logFile);

            List<Map<String, String>> events = parseEvents(content);

            computeStats(events, projectName);

        } catch (Exception e) {
            System.out.println("Failed to generate stats.");
        }
    }

    private List<Map<String, String>> parseEvents(String content) {

        List<Map<String, String>> events = new ArrayList<>();

        Matcher matcher = EVENT_PATTERN.matcher(content);

        while (matcher.find()) {

            String block = matcher.group(1);

            Map<String, String> event = new HashMap<>();

            Matcher fieldMatcher = FIELD_PATTERN.matcher(block);

            while (fieldMatcher.find()) {
                event.put(
                        fieldMatcher.group(1).trim(),
                        fieldMatcher.group(2).trim()
                );
            }

            events.add(event);
        }

        return events;
    }

    public ProjectInsights generateInsights(String projectName) {

        try {
            TaskService taskService = new TaskService();
            List<Task> tasks = taskService.getTasks(projectName);

            AppConfig config = ConfigManager.load();

            Path logFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                return emptyInsights();
            }

            String content = Files.readString(logFile);
            List<Map<String, String>> events = parseEvents(content);

            // =========================================
            // BASIC COUNTS
            // =========================================

            int total = tasks.size();

            int open = (int) tasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.OPEN)
                    .count();

            int inProgress = (int) tasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                    .count();

            int done = (int) tasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .count();

            double completionRate = total == 0
                    ? 0
                    : (done * 100.0 / total);

            // =========================================
            // EVENT ANALYSIS
            // =========================================

            Map<Integer, LocalDate> createdDates = new HashMap<>();
            Map<Integer, LocalDate> completedDates = new HashMap<>();
            Map<Integer, LocalDate> lastChangeMap = new HashMap<>();
            Map<LocalDate, Integer> dailyActivity = new HashMap<>();

            Map<String, Integer> tagFrequency = new HashMap<>();

            LocalDate now = LocalDate.now();
            int completedThisWeek = 0;

            for (Map<String, String> event : events) {

                int id = Integer.parseInt(event.get("id"));
                String action = event.get("action");
                LocalDate date = LocalDate.parse(event.get("date"));

                // last status change tracking
                lastChangeMap.merge(id, date,
                        (oldDate, newDate) ->
                                newDate.isAfter(oldDate) ? newDate : oldDate);

                // heatmap activity
                dailyActivity.merge(date, 1, Integer::sum);

                if ("created".equals(action)) {
                    createdDates.put(id, date);
                }

                if ("completed".equals(action)) {
                    completedDates.put(id, date);

                    if (!date.isBefore(now.minusDays(7))) {
                        completedThisWeek++;
                    }
                }

                String tags = event.get("tags");
                if (tags != null && !tags.isBlank()) {
                    for (String tag : tags.split(",")) {
                        tagFrequency.merge(tag.trim(), 1, Integer::sum);
                    }
                }
            }

            // =========================================
            // STAGNATION
            // =========================================

            long stagnantTasks = lastChangeMap.entrySet().stream()
                    .filter(e -> !completedDates.containsKey(e.getKey()))
                    .filter(e -> ChronoUnit.DAYS.between(e.getValue(), now) > 7)
                    .count();

            // =========================================
            // PRODUCTIVITY SCORING MODEL
            // =========================================

            double weeklyVelocityScore = Math.min(completedThisWeek * 10.0, 100);
            double stagnationPenalty = stagnantTasks * 5.0;

            double productivityScore =
                    (completionRate * 0.5) +
                            (weeklyVelocityScore * 0.4) -
                            stagnationPenalty;

            productivityScore = Math.max(0, Math.min(100, productivityScore));

            // =========================================
            // TOP TAG
            // =========================================

            String mostUsedTag = tagFrequency.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("None");

            // =========================================
            // RETURN OBJECT
            // =========================================

            return new ProjectInsights(
                    total,
                    open,
                    inProgress,
                    done,
                    completedThisWeek,
                    completionRate,
                    mostUsedTag,
                    productivityScore,
                    stagnantTasks,
                    dailyActivity
            );

        } catch (Exception e) {
            return emptyInsights();
        }
    }

    private ProjectInsights emptyInsights() {
        return new ProjectInsights(
                0, 0, 0, 0,
                0,
                0.0,
                "None",
                0.0,
                0L,
                new HashMap<>()
        );
    }

    private int calculateCompletedThisWeek(String projectName) {

        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.minusDays(7);

        // Parse work-log metadata TASK_EVENT blocks
        // Count completed where date >= weekStart

        return parseWeeklyCompletedCount(projectName, weekStart);
    }

    private String calculateMostUsedTag(List<Task> tasks) {

        Map<String, Long> tagCount = tasks.stream()
                .flatMap(t -> t.getTags().stream())
                .collect(Collectors.groupingBy(
                        tag -> tag,
                        Collectors.counting()
                ));

        return tagCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    private int parseWeeklyCompletedCount(String projectName, LocalDate weekStart) {

        try {

            AppConfig config = ConfigManager.load();

            Path logFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                return 0;
            }

            List<String> lines = Files.readAllLines(logFile);

            int count = 0;

            for (int i = 0; i < lines.size(); i++) {

                String line = lines.get(i);

                // Detect metadata block
                if (line.contains("TASK_EVENT")) {

                    String action = null;
                    String dateStr = null;

                    // Read next few lines inside metadata block
                    for (int j = i; j < lines.size() && !lines.get(j).contains("-->"); j++) {

                        String metaLine = lines.get(j).trim();

                        if (metaLine.startsWith("action=")) {
                            action = metaLine.replace("action=", "").trim();
                        }

                        if (metaLine.startsWith("date=")) {
                            dateStr = metaLine.replace("date=", "").trim();
                        }
                    }

                    if ("completed".equalsIgnoreCase(action) && dateStr != null) {

                        LocalDate completedDate = LocalDate.parse(dateStr);

                        if (!completedDate.isBefore(weekStart)) {
                            count++;
                        }
                    }
                }
            }

            return count;

        } catch (Exception e) {
            return 0;
        }
    }

    private ProjectInsights computeStats(List<Map<String, String>> events,
                                         String projectName) {

        Map<Integer, LocalDate> createdDates = new HashMap<>();
        Map<Integer, LocalDate> completedDates = new HashMap<>();
        Map<Integer, LocalDate> lastChangeMap = new HashMap<>();
        Map<LocalDate, Integer> dailyActivity = new HashMap<>();

        int created = 0;
        int completed = 0;
        int completedThisWeek = 0;

        Map<String, Integer> tagFrequency = new HashMap<>();

        LocalDate now = LocalDate.now();
        LocalDate weekStart = now.minusDays(7);

        // =============================
        // Parse events
        // =============================
        for (Map<String, String> event : events) {

            int id = Integer.parseInt(event.get("id"));
            String action = event.get("action");
            LocalDate date = LocalDate.parse(event.get("date"));

            // Track last status change
            lastChangeMap.merge(id, date,
                    (oldDate, newDate) ->
                            newDate.isAfter(oldDate) ? newDate : oldDate);

            // Track daily activity (for heatmap)
            dailyActivity.merge(date, 1, Integer::sum);

            if ("created".equals(action)) {
                created++;
                createdDates.put(id, date);
            }

            if ("completed".equals(action)) {
                completed++;
                completedDates.put(id, date);

                if (!date.isBefore(weekStart)) {
                    completedThisWeek++;
                }
            }

            String tags = event.get("tags");
            if (tags != null && !tags.isBlank()) {
                for (String tag : tags.split(",")) {
                    tagFrequency.merge(tag.trim(), 1, Integer::sum);
                }
            }
        }

        // =============================
        // Duration Analysis
        // =============================
        List<Long> durations = new ArrayList<>();

        for (Integer id : completedDates.keySet()) {
            if (createdDates.containsKey(id)) {
                long days = ChronoUnit.DAYS.between(
                        createdDates.get(id),
                        completedDates.get(id)
                );
                durations.add(days);
            }
        }

        double avgDuration = durations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // =============================
        // Stagnation Detection
        // =============================
        long stagnant = 0;

        for (Map.Entry<Integer, LocalDate> entry : lastChangeMap.entrySet()) {

            Integer id = entry.getKey();
            LocalDate lastChange = entry.getValue();

            if (!completedDates.containsKey(id)) {

                long daysIdle = ChronoUnit.DAYS.between(lastChange, now);

                if (daysIdle > 7) {
                    stagnant++;
                }
            }
        }

        // =============================
        // Counts
        // =============================
        int openTasks = created - completed;
        int inProgressTasks = 0; // Optional future improvement
        int doneTasks = completed;
        int totalTasks = created;

        // =============================
        // Completion Rate
        // =============================
        double completionRate = totalTasks == 0
                ? 0
                : (doneTasks * 100.0 / totalTasks);

        // =============================
        // Intelligent Productivity Score
        // =============================
        double weeklyVelocityScore = Math.min(completedThisWeek * 10, 100);
        double stagnationPenalty = stagnant * 5;

        double productivityScore =
                (completionRate * 0.5) +
                        (weeklyVelocityScore * 0.4) -
                        (stagnationPenalty);

        productivityScore =
                Math.max(Math.min(productivityScore, 100), 0);

        // =============================
        // Top Tag
        // =============================
        String topTag = tagFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        // =============================
        // Return Insights Object
        // =============================
        return new ProjectInsights(
                totalTasks,
                openTasks,
                inProgressTasks,
                doneTasks,
                completedThisWeek,
                completionRate,
                topTag,
                productivityScore,
                stagnant,
                dailyActivity
        );
    }


}
