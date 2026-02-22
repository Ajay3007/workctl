package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.Task;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.model.WeeklyReportData;
import com.workctl.core.model.WeeklyReportData.StagnantEntry;

import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Produces a WeeklyReportData for any arbitrary week range.
 *
 * Intelligence is entirely rule-based (no AI):
 *  - Parses TASK_EVENT HTML comment blocks from work-log.md for exact dates
 *  - Computes velocity, trend, stagnation, tag focus, and backlog balance
 *  - Generates a human-readable headline + insight bullets from those metrics
 */
public class WeeklyReportService {

    private static final Pattern EVENT_PATTERN =
            Pattern.compile("<!--\\s*TASK_EVENT:(.*?)-->", Pattern.DOTALL);
    private static final Pattern FIELD_PATTERN  =
            Pattern.compile("(\\w+)=(.+)");
    private static final Pattern DATE_HEADER    =
            Pattern.compile("^# (\\d{4}-\\d{2}-\\d{2})\\s*$");

    // ── Public API ────────────────────────────────────────────────

    public WeeklyReportData generateReport(String projectName,
                                           LocalDate weekStart,
                                           LocalDate weekEnd) {
        try {
            return buildReport(projectName, weekStart, weekEnd);
        } catch (Exception e) {
            return emptyReport(projectName, weekStart, weekEnd,
                    "Error generating report: " + e.getMessage());
        }
    }

    // ── Report construction ───────────────────────────────────────

    private WeeklyReportData buildReport(String projectName,
                                         LocalDate weekStart,
                                         LocalDate weekEnd) throws Exception {

        TaskService taskService = new TaskService();
        List<Task> allTasks = taskService.getTasks(projectName);

        // ── Read work-log.md ──────────────────────────────────────
        AppConfig config  = ConfigManager.load();
        Path logFile = Paths.get(config.getWorkspace())
                .resolve("01_Projects").resolve(projectName)
                .resolve("notes").resolve("work-log.md");

        String logContent = Files.exists(logFile) ? Files.readString(logFile) : "";
        List<Map<String, String>> events = parseEvents(logContent);

        // ── Build event maps ──────────────────────────────────────
        Map<Integer, LocalDate> createdByEvent   = new HashMap<>();
        Map<Integer, LocalDate> completedByEvent = new HashMap<>();
        Map<Integer, LocalDate> lastChangeMap    = new HashMap<>();

        Map<String, Integer> weekTagFreq = new LinkedHashMap<>();  // tags in week range
        Map<String, Integer> allTagFreq  = new LinkedHashMap<>();  // all-time tag freq

        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        LocalDate prevWeekEnd   = weekEnd.minusWeeks(1);
        int prevWeekCompleted   = 0;

        for (Map<String, String> ev : events) {
            try {
                int       id     = Integer.parseInt(ev.get("id").trim());
                String    action = ev.getOrDefault("action", "").trim();
                LocalDate date   = LocalDate.parse(ev.get("date").trim());

                lastChangeMap.merge(id, date, (a, b) -> a.isAfter(b) ? a : b);

                if ("created".equals(action))   createdByEvent.put(id, date);
                if ("completed".equals(action)) {
                    completedByEvent.put(id, date);
                    if (!date.isBefore(prevWeekStart) && !date.isAfter(prevWeekEnd))
                        prevWeekCompleted++;
                }

                String rawTags = ev.getOrDefault("tags", "");
                if (!rawTags.isBlank()) {
                    boolean inWeek = !date.isBefore(weekStart) && !date.isAfter(weekEnd);
                    for (String t : rawTags.split(",")) {
                        String tag = t.trim();
                        if (!tag.isEmpty()) {
                            allTagFreq.merge(tag, 1, Integer::sum);
                            if (inWeek) weekTagFreq.merge(tag, 1, Integer::sum);
                        }
                    }
                }
            } catch (Exception ignored) { /* skip malformed events */ }
        }

        // ── IDs for this week ─────────────────────────────────────
        Set<Integer> completedInWeek = completedByEvent.entrySet().stream()
                .filter(e -> inRange(e.getValue(), weekStart, weekEnd))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<Integer> createdInWeek = new HashSet<>(
                createdByEvent.entrySet().stream()
                        .filter(e -> inRange(e.getValue(), weekStart, weekEnd))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet())
        );

        // Also use task.getCreatedDate() as fallback for projects with sparse logs
        allTasks.stream()
                .filter(t -> t.getCreatedDate() != null)
                .filter(t -> inRange(t.getCreatedDate(), weekStart, weekEnd))
                .forEach(t -> createdInWeek.add(t.getId()));

        // ── Task look-up map ──────────────────────────────────────
        Map<Integer, Task> byId = allTasks.stream()
                .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));

        // ── Build task lists ──────────────────────────────────────
        List<Task> completedTasks = completedInWeek.stream()
                .filter(byId::containsKey)
                .map(byId::get)
                .sorted(Comparator.comparingInt(Task::getPriority))
                .collect(Collectors.toList());

        // New tasks added this week — exclude those already shown in "Completed"
        List<Task> newTasks = createdInWeek.stream()
                .filter(byId::containsKey)
                .filter(id -> !completedInWeek.contains(id))
                .map(byId::get)
                .sorted(Comparator.comparingInt(Task::getPriority))
                .collect(Collectors.toList());

        List<Task> inProgressTasks = allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .sorted(Comparator.comparingInt(Task::getPriority))
                .collect(Collectors.toList());

        // ── Stagnant tasks ────────────────────────────────────────
        LocalDate today = LocalDate.now();
        List<StagnantEntry> stagnantTasks = allTasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .map(t -> {
                    LocalDate fallback  = t.getCreatedDate() != null ? t.getCreatedDate() : today;
                    LocalDate lastChange = lastChangeMap.getOrDefault(t.getId(), fallback);
                    long daysIdle = ChronoUnit.DAYS.between(lastChange, today);
                    return new StagnantEntry(t, daysIdle);
                })
                .filter(e -> e.daysIdle() > 7)
                .sorted(Comparator.comparingLong(StagnantEntry::daysIdle).reversed())
                .collect(Collectors.toList());

        // ── Aggregate counts ──────────────────────────────────────
        int openCount   = (int) allTasks.stream().filter(t -> t.getStatus() == TaskStatus.OPEN).count();
        int totalDone   = (int) allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        double compRate = allTasks.isEmpty() ? 0.0 : (totalDone * 100.0 / allTasks.size());

        double velScore   = Math.min(completedTasks.size() * 10.0, 100);
        double stagPenalty= stagnantTasks.size() * 5.0;
        double prodScore  = Math.max(0, Math.min(100,
                (compRate * 0.5) + (velScore * 0.4) - stagPenalty));

        // ── Sorted tag activity (this week, else fall back to all-time) ──
        Map<String, Integer> tagActivity = (weekTagFreq.isEmpty() ? allTagFreq : weekTagFreq)
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // ── Log highlights ────────────────────────────────────────
        List<String> logHighlights = extractLogHighlights(logContent, weekStart, weekEnd);

        // ── Intelligence layer (rule-based) ───────────────────────
        String velocityLabel = velocityLabel(completedTasks.size());
        String headline = buildHeadline(
                completedTasks.size(), inProgressTasks.size(),
                stagnantTasks.size(), velocityLabel);
        List<String> insights = buildInsights(
                completedTasks.size(), newTasks.size(), stagnantTasks.size(),
                topTag(weekTagFreq.isEmpty() ? allTagFreq : weekTagFreq),
                prevWeekCompleted, compRate, allTasks.size(),
                completedTasks);

        return new WeeklyReportData(
                projectName, weekStart, weekEnd, today,
                completedTasks.size(), inProgressTasks.size(), openCount, newTasks.size(),
                compRate, prodScore,
                velocityLabel, headline, insights,
                completedTasks, inProgressTasks, newTasks, stagnantTasks,
                tagActivity, logHighlights);
    }

    // ── Intelligence rules ────────────────────────────────────────

    private String velocityLabel(int completed) {
        if (completed == 0) return "Quiet";
        if (completed <= 2) return "Steady";
        if (completed <= 4) return "Strong";
        if (completed <= 7) return "High";
        return "Elite";
    }

    private String buildHeadline(int completed, int inProgress, long stagnant, String vel) {
        StringBuilder sb = new StringBuilder(vel).append(" week — ");
        if (completed == 0) {
            sb.append("no tasks completed");
        } else {
            sb.append(completed).append(" task").append(completed > 1 ? "s" : "").append(" closed");
        }
        if (inProgress > 0)
            sb.append(", ").append(inProgress).append(" in flight");
        if (stagnant > 0)
            sb.append("; ").append(stagnant).append(" stagnant");
        return sb.toString();
    }

    private List<String> buildInsights(int completed, int newThisWeek, long stagnant,
                                        String topTag, int prevWeekCompleted,
                                        double completionRate, int totalTasks,
                                        List<Task> completedTasks) {
        List<String> insights = new ArrayList<>();

        // Week-over-week velocity trend
        if (prevWeekCompleted > 0 && completed != prevWeekCompleted) {
            double pct = (completed - prevWeekCompleted) * 100.0 / prevWeekCompleted;
            if (pct >= 20)
                insights.add("Velocity up " + (int) pct + "% vs. last week — momentum building");
            else if (pct <= -20)
                insights.add("Velocity down " + (int) Math.abs(pct) + "% vs. last week");
        } else if (prevWeekCompleted == 0 && completed > 0) {
            insights.add("First completions this week — getting traction");
        }

        // Backlog balance
        if (newThisWeek > completed + 2)
            insights.add("Backlog is growing — more tasks added than completed");
        else if (completed > 0 && completed >= newThisWeek)
            insights.add("Backlog shrinking — completing faster than adding");

        // Stagnation
        if (stagnant > 3)
            insights.add(stagnant + " tasks idle for over a week — a review session may help");
        else if (stagnant > 0)
            insights.add(stagnant + " stagnant task" + (stagnant > 1 ? "s" : "") + " — may need attention");

        // Focus area
        if (!"None".equals(topTag))
            insights.add("Primary focus area this week: #" + topTag);

        // P1 completion
        long p1Done = completedTasks.stream().filter(t -> t.getPriority() == 1).count();
        if (p1Done > 0)
            insights.add("Delivered " + p1Done + " high-priority (P1) task" + (p1Done > 1 ? "s" : ""));

        // Overall health
        if (completionRate >= 75 && totalTasks > 3)
            insights.add("Strong overall completion (" + String.format("%.0f", completionRate) + "%) — project well-managed");

        if (insights.isEmpty())
            insights.add("No notable patterns this week — keep the momentum going");

        return insights;
    }

    private String topTag(Map<String, Integer> freq) {
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    // ── Log highlight extraction ──────────────────────────────────

    /**
     * Extracts plain (non-event) text lines from work-log.md sections
     * whose date header falls within [weekStart, weekEnd].
     */
    private List<String> extractLogHighlights(String content, LocalDate weekStart, LocalDate weekEnd) {
        List<String> highlights = new ArrayList<>();
        if (content.isBlank()) return highlights;

        boolean inWeekSection = false;
        boolean inEventBlock  = false;

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.stripTrailing();

            // Date header
            Matcher dm = DATE_HEADER.matcher(line);
            if (dm.matches()) {
                try {
                    LocalDate d = LocalDate.parse(dm.group(1));
                    inWeekSection = inRange(d, weekStart, weekEnd);
                } catch (Exception ignored) { inWeekSection = false; }
                continue;
            }

            // Skip TASK_EVENT HTML comment blocks
            if (line.contains("<!-- TASK_EVENT") || line.contains("<!--\nTASK_EVENT")) {
                inEventBlock = true; continue;
            }
            if (inEventBlock) {
                if (line.contains("-->")) inEventBlock = false;
                continue;
            }

            // Collect meaningful content lines
            if (inWeekSection && !line.isBlank()
                    && !line.startsWith("##")
                    && !line.startsWith("#")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty())
                    highlights.add(trimmed);
            }
        }

        // Return at most the 12 most recent lines
        int from = Math.max(0, highlights.size() - 12);
        return new ArrayList<>(highlights.subList(from, highlights.size()));
    }

    // ── Parsing helpers ───────────────────────────────────────────

    private List<Map<String, String>> parseEvents(String content) {
        List<Map<String, String>> events = new ArrayList<>();
        Matcher m = EVENT_PATTERN.matcher(content);
        while (m.find()) {
            Map<String, String> ev = new HashMap<>();
            Matcher fm = FIELD_PATTERN.matcher(m.group(1));
            while (fm.find())
                ev.put(fm.group(1).trim(), fm.group(2).trim());
            if (ev.containsKey("id") && ev.containsKey("date"))
                events.add(ev);
        }
        return events;
    }

    private boolean inRange(LocalDate d, LocalDate start, LocalDate end) {
        return !d.isBefore(start) && !d.isAfter(end);
    }

    // ── Empty / error fallback ────────────────────────────────────

    private WeeklyReportData emptyReport(String projectName,
                                          LocalDate weekStart,
                                          LocalDate weekEnd,
                                          String reason) {
        return new WeeklyReportData(
                projectName, weekStart, weekEnd, LocalDate.now(),
                0, 0, 0, 0, 0.0, 0.0,
                "Quiet", reason,
                List.of(reason),
                List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of());
    }
}
