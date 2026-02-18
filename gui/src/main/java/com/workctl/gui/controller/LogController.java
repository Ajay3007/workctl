package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.gui.ProjectContext;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogController {

    @FXML
    private TextArea logTextArea;

    private String currentProject;


    @FXML
    public void initialize() {

        ProjectContext.addListener(this::loadLog);

    }

    private void loadLog(String projectName) {

        try {
            AppConfig config = ConfigManager.load();

            Path logPath = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (Files.exists(logPath)) {
                String content = Files.readString(logPath);
                logTextArea.setText(content);
            } else {
                logTextArea.setText("No log file found.");
            }

        } catch (Exception e) {
            logTextArea.setText("Failed to load log.");
            e.printStackTrace();
        }
    }

    public void setProject(String projectName) {
        this.currentProject = projectName;
        loadLogs();
    }

    private void loadLogs() {
        if (currentProject == null) return;

        try {
            AppConfig config = ConfigManager.load();
            Path logFile = Path.of(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(currentProject)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (Files.exists(logFile)) {
                String content = Files.readString(logFile);
                logTextArea.setText(content);
            } else {
                logTextArea.setText("No log file found.");
            }

        } catch (Exception e) {
            logTextArea.setText("Failed to load logs.");
            e.printStackTrace();
        }
    }

}
