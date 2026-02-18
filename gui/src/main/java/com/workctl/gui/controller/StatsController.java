package com.workctl.gui.controller;

import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;
import com.workctl.gui.ProjectContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.util.Map;

public class StatsController {

    @FXML private Label totalLabel;
    @FXML private Label openLabel;
    @FXML private Label progressLabel;
    @FXML private Label doneLabel;

    @FXML private ProgressBar completionBar;
    @FXML private Label completionPercentLabel;

    @FXML private Label stagnationWarningLabel;

    @FXML private GridPane heatmapGrid;

    @FXML private Label stagnationLabel;

    @FXML private ProgressBar productivityBar;
    @FXML private Label productivityLabel;

    private final StatsService statsService = new StatsService();

    private String currentProject;

    @FXML
    public void initialize() {
        ProjectContext.addListener(this::loadStats);
    }

    private void loadStats(String projectName) {

        if (projectName == null) return;

        currentProject = projectName;

        try {
            ProjectInsights insights =
                    statsService.generateInsights(projectName);

            // ---- Basic Numbers ----
            totalLabel.setText("Total: " + insights.getTotalTasks());
            openLabel.setText("Open: " + insights.getOpenTasks());
            progressLabel.setText("In Progress: " + insights.getInProgressTasks());
            doneLabel.setText("Done: " + insights.getDoneTasks());

            // ---- Completion ----
            double completion = insights.getCompletionRate() / 100.0;

            completionBar.setProgress(completion);
            completionPercentLabel.setText(
                    String.format("Completion: %.0f%%",
                            insights.getCompletionRate())
            );

            // ---- Stagnation Warning ----
            if (insights.getStagnantTasks() > 0) {
                stagnationWarningLabel.setText(
                        "⚠ " + insights.getStagnantTasks()
                                + " tasks stagnant for >7 days"
                );
            } else {
                stagnationWarningLabel.setText("");
            }

            double productivity = insights.getProductivityScore() / 100.0;

            productivityBar.setProgress(productivity);

            productivityLabel.setText(
                    "Productivity Score: " +
                            String.format("%.1f / 100", insights.getProductivityScore())
            );


            // ---- Heatmap ----
            renderHeatmap(insights.getDailyActivity());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // HEATMAP
    // ==========================================

    private void renderHeatmap(Map<LocalDate, Integer> dailyActivity) {

        heatmapGrid.getChildren().clear();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);

        int index = 0;

        for (LocalDate date = start;
             !date.isAfter(today);
             date = date.plusDays(1)) {

            int activity = dailyActivity.getOrDefault(date, 0);

            Region cell = new Region();
            cell.setPrefSize(18, 18);

            cell.setStyle("""
                -fx-background-color: %s;
                -fx-background-radius: 3;
                """.formatted(getHeatColor(activity)));

            Tooltip.install(cell,
                    new Tooltip(date + " : " + activity + " events"));

            heatmapGrid.add(cell, index % 7, index / 7);

            index++;
        }
    }

    private String getHeatColor(int activity) {

        if (activity == 0) return "#eeeeee";
        if (activity == 1) return "#c6e48b";
        if (activity <= 3) return "#7bc96f";
        if (activity <= 5) return "#239a3b";
        return "#196127";
    }
}
