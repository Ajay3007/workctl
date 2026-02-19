package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;
import com.workctl.gui.ProjectContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * StatsController — Stats tab
 *
 * Changes from original:
 *   - Auto-refresh via ProjectContext.WatchService — stats update whenever
 *     tasks.md changes (task added, moved, deleted from Kanban or CLI)
 *   - Loads stats on background thread to avoid freezing the UI
 *   - Shows last-refreshed timestamp
 *   - Calls ProjectContext.watchProjectDir() so the single WatchService
 *     instance (shared with LogController) covers both files
 */
public class StatsController {

    @FXML private Label totalLabel;
    @FXML private Label openLabel;
    @FXML private Label progressLabel;
    @FXML private Label doneLabel;

    @FXML private ProgressBar completionBar;
    @FXML private Label completionPercentLabel;

    @FXML private Label stagnationWarningLabel;
    @FXML private Label stagnationLabel;

    @FXML private ProgressBar productivityBar;
    @FXML private Label productivityLabel;

    @FXML private GridPane heatmapGrid;

    // Optional: add fx:id="lastRefreshLabel" to stats.fxml for timestamp
    @FXML
    private Label lastRefreshLabel;

    private final StatsService statsService = new StatsService();
    private String currentProject;

    @FXML
    public void initialize() {

        // 1. Refresh when user switches project
        ProjectContext.addListener(projectName -> {
            currentProject = projectName;
            startWatchingProject(projectName);     // start/restart file watcher
            loadStatsAsync(projectName);
        });

        // 2. Auto-refresh when tasks.md or work-log.md changes on disk
        //    This fires after: task add, task done, task start (CLI or AI agent)
        //    and also after any Kanban board action (which already calls refreshBoard,
        //    but this ensures Stats stays in sync too)
        ProjectContext.addFileChangeListener(() -> {
            if (currentProject != null) {
                loadStatsAsync(currentProject);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD STATS
    // ════════════════════════════════════════════════════════════════

    /**
     * Load stats on a background thread, then update UI on JavaFX thread.
     * Prevents the UI from freezing when stats calculation takes time.
     */
    private void loadStatsAsync(String projectName) {
        Thread thread = new Thread(() -> {
            try {
                ProjectInsights insights = statsService.generateInsights(projectName);
                Platform.runLater(() -> renderStats(insights));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (totalLabel != null)
                        totalLabel.setText("Failed to load stats.");
                });
            }
        }, "workctl-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderStats(ProjectInsights insights) {

        // ── Task counts ───────────────────────────────────────────
        totalLabel.setText("Total: " + insights.getTotalTasks());
        openLabel.setText("Open: " + insights.getOpenTasks());
        progressLabel.setText("In Progress: " + insights.getInProgressTasks());
        doneLabel.setText("Done: " + insights.getDoneTasks());

        // ── Completion bar ────────────────────────────────────────
        double completion = insights.getCompletionRate() / 100.0;
        completionBar.setProgress(completion);
        completionPercentLabel.setText(
                String.format("Completion: %.1f%%", insights.getCompletionRate()));

        // ── Stagnation warning ────────────────────────────────────
        long stagnant = insights.getStagnantTasks();
        if (stagnant > 0) {
            stagnationWarningLabel.setText(
                    "⚠  " + stagnant + " task" + (stagnant == 1 ? "" : "s")
                            + " stagnant for >7 days");
            stagnationWarningLabel.setStyle(
                    "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            stagnationWarningLabel.setText("✓  No stagnant tasks");
            stagnationWarningLabel.setStyle("-fx-text-fill: #27ae60;");
        }

        // ── Productivity score ────────────────────────────────────
        double productivity = insights.getProductivityScore() / 100.0;
        productivityBar.setProgress(productivity);

        String scoreLabel = getScoreLabel(insights.getProductivityScore());
        productivityLabel.setText(String.format(
                "Productivity Score: %.1f / 100  %s",
                insights.getProductivityScore(), scoreLabel));

        // ── Heatmap ───────────────────────────────────────────────
        renderHeatmap(insights.getDailyActivity());

        // ── Timestamp ─────────────────────────────────────────────
        if (lastRefreshLabel != null) {
            lastRefreshLabel.setText("Last refresh: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
    }

    private String getScoreLabel(double score) {
        if (score >= 85) return "🔥 Elite Execution";
        if (score >= 70) return "🚀 Strong Momentum";
        if (score >= 50) return "⚖ Stable";
        if (score >= 30) return "⚠ Fragmented";
        return "🧊 Stalled";
    }

    // ════════════════════════════════════════════════════════════════
    // HEATMAP
    // ════════════════════════════════════════════════════════════════

    private void renderHeatmap(Map<LocalDate, Integer> dailyActivity) {
        heatmapGrid.getChildren().clear();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);

        int index = 0;
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            int activity = dailyActivity.getOrDefault(date, 0);

            Region cell = new Region();
            cell.setPrefSize(18, 18);
            cell.setStyle("""
                -fx-background-color: %s;
                -fx-background-radius: 3;
                """.formatted(heatColor(activity)));

            Tooltip.install(cell,
                    new Tooltip(date + " : " + activity + " event" + (activity == 1 ? "" : "s")));

            heatmapGrid.add(cell, index % 7, index / 7);
            index++;
        }
    }

    private String heatColor(int activity) {
        if (activity == 0) return "#eeeeee";
        if (activity == 1) return "#c6e48b";
        if (activity <= 3) return "#7bc96f";
        if (activity <= 5) return "#239a3b";
        return "#196127";
    }

    // ════════════════════════════════════════════════════════════════
    // FILE WATCHER
    // ════════════════════════════════════════════════════════════════

    /**
     * Tell ProjectContext to watch the current project's notes/ directory.
     * ProjectContext.watchProjectDir() is shared — only one WatchService
     * runs at a time, so calling this from both LogController and StatsController
     * is safe (the second call replaces the first, which is fine since they
     * always watch the same directory for the same project).
     */
    private void startWatchingProject(String projectName) {
        try {
            AppConfig config  = ConfigManager.load();
            var notesDir = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes");

            ProjectContext.watchProjectDir(notesDir);

        } catch (Exception e) {
            System.err.println("[StatsController] Failed to start watcher: " + e.getMessage());
        }
    }
}