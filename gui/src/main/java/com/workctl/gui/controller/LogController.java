package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.gui.ProjectContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LogController — Logs tab
 *
 * Features:
 *   - Split pane: raw Markdown text on left, rendered HTML preview on right
 *   - Auto-refresh via ProjectContext.WatchService — updates when work-log.md
 *     changes on disk (CLI commands, AI agent writes, manual edits)
 *   - Manual refresh button
 *   - Last-refreshed timestamp
 */
public class LogController {

    // ── FXML — the Logs tab root must be a container (AnchorPane or VBox)
    // that we populate entirely in code so we can add the split pane + toolbar
    @FXML
    private VBox logRoot;   // fx:id="logRoot" — replace logTextArea with this VBox in FXML

    // ── Internal nodes (built in code) ───────────────────────────
    private TextArea  rawTextArea;
    private WebView   markdownView;
    private Label     statusLabel;
    private Label     lastRefreshLabel;

    // ── State ─────────────────────────────────────────────────────
    private String currentProject;
    private String currentRawContent = "";

    @FXML
    public void initialize() {
        buildUI();

        // 1. Refresh when user switches project
        ProjectContext.addListener(project -> {
            currentProject = project;
            if (project == null) return;
            startWatchingProject(project);
            loadLog(project);
        });

        // 2. Refresh when tasks.md or work-log.md changes on disk
        ProjectContext.addFileChangeListener(() -> {
            if (currentProject != null) {
                loadLog(currentProject);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    // UI BUILD
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        logRoot.setSpacing(0);

        // ── Toolbar ───────────────────────────────────────────────
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 14, 8, 14));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("""
            -fx-background-color: #2c3e50;
            -fx-border-color: #1a252f;
            -fx-border-width: 0 0 1 0;
            """);

        Label title = new Label("📋 Work Log");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold;");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lastRefreshLabel = new Label("");
        lastRefreshLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10;");

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.setStyle("""
            -fx-background-color: #34495e;
            -fx-text-fill: #ecf0f1;
            -fx-border-color: #4a6278;
            -fx-border-radius: 4;
            -fx-background-radius: 4;
            -fx-font-size: 11;
            -fx-padding: 4 10;
            """);
        refreshBtn.setOnAction(e -> {
            if (currentProject != null) loadLog(currentProject);
        });

        toolbar.getChildren().addAll(title, statusLabel, spacer, lastRefreshLabel, refreshBtn);

        // ── Split Pane ────────────────────────────────────────────
        // Left: raw Markdown text (selectable, copyable)
        rawTextArea = new TextArea();
        rawTextArea.setEditable(false);
        rawTextArea.setWrapText(false);
        rawTextArea.setStyle("""
            -fx-font-family: 'Consolas', 'Courier New', monospace;
            -fx-font-size: 12;
            -fx-control-inner-background: #1e1e1e;
            -fx-text-fill: #d4d4d4;
            """);

        VBox leftPane = new VBox(rawTextArea);
        VBox.setVgrow(rawTextArea, Priority.ALWAYS);
        Label leftHeader = buildPaneHeader("📄 Raw Markdown");
        leftPane.getChildren().add(0, leftHeader);

        // Right: rendered Markdown (WebView)
        markdownView = new WebView();
        markdownView.setContextMenuEnabled(true);

        VBox rightPane = new VBox(markdownView);
        VBox.setVgrow(markdownView, Priority.ALWAYS);
        Label rightHeader = buildPaneHeader("🌐 Preview");
        rightPane.getChildren().add(0, rightHeader);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.45);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        logRoot.getChildren().addAll(toolbar, splitPane);
    }

    private Label buildPaneHeader(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(5, 12, 5, 12));
        label.setStyle("""
            -fx-background-color: #34495e;
            -fx-text-fill: #bdc3c7;
            -fx-font-size: 11;
            -fx-font-weight: bold;
            """);
        return label;
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD + RENDER
    // ════════════════════════════════════════════════════════════════

    private void loadLog(String projectName) {
        try {
            AppConfig config  = ConfigManager.load();
            Path logPath = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (Files.exists(logPath)) {
                String content = Files.readString(logPath);
                // Only re-render if content actually changed (avoid flicker)
                if (!content.equals(currentRawContent)) {
                    currentRawContent = content;
                    Platform.runLater(() -> {
                        rawTextArea.setText(content);
                        renderMarkdown(content);
                        updateRefreshLabel();
                    });
                }
            } else {
                Platform.runLater(() -> {
                    rawTextArea.setText("No work-log.md found for project: " + projectName);
                    loadPlaceholderHtml(projectName);
                    updateRefreshLabel();
                });
            }

        } catch (Exception e) {
            Platform.runLater(() -> {
                rawTextArea.setText("Failed to load log: " + e.getMessage());
            });
            e.printStackTrace();
        }
    }

    private void renderMarkdown(String markdown) {
        try {
            org.commonmark.parser.Parser parser =
                    org.commonmark.parser.Parser.builder().build();
            org.commonmark.renderer.html.HtmlRenderer renderer =
                    org.commonmark.renderer.html.HtmlRenderer.builder().build();
            String bodyHtml = renderer.render(parser.parse(markdown));
            markdownView.getEngine().loadContent(wrapHtml(bodyHtml));
        } catch (Exception e) {
            markdownView.getEngine().loadContent(
                    "<pre style='color:red'>" + e.getMessage() + "</pre>");
        }
    }

    private void loadPlaceholderHtml(String projectName) {
        markdownView.getEngine().loadContent(wrapHtml(
                "<p style='color:#999;font-style:italic;'>" +
                        "No work-log.md found for project: <strong>" + projectName + "</strong></p>"
        ));
    }

    private String wrapHtml(String body) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: 'Segoe UI', Arial, sans-serif;
                    font-size: 14px;
                    line-height: 1.7;
                    color: #2c3e50;
                    padding: 20px 24px;
                    margin: 0;
                    background: white;
                }
                h1 { font-size: 20px; border-bottom: 2px solid #3498db; padding-bottom: 6px; color: #1a252f; }
                h2 { font-size: 17px; border-bottom: 1px solid #ecf0f1; padding-bottom: 4px;
                     color: #2c3e50; margin-top: 20px; }
                h3 { font-size: 14px; color: #34495e; margin-top: 14px; }
                ul, ol { padding-left: 22px; }
                li { margin: 3px 0; }
                code {
                    background: #f4f6f7; padding: 2px 6px;
                    border-radius: 4px; font-family: 'Consolas', monospace;
                    font-size: 12px; color: #c0392b;
                }
                pre {
                    background: #1e1e1e; color: #d4d4d4;
                    padding: 14px; border-radius: 6px;
                    font-size: 12px; overflow-x: auto;
                    border-left: 3px solid #3498db;
                }
                pre code { background: none; color: #d4d4d4; padding: 0; }
                blockquote {
                    border-left: 4px solid #3498db;
                    margin: 0; padding: 8px 16px;
                    background: #eaf4fb; color: #1a5276;
                }
                strong { color: #1a252f; }
                hr { border: none; border-top: 1px solid #ecf0f1; margin: 16px 0; }
                p { margin: 6px 0; }
                /* TASK_EVENT comments are hidden in HTML */
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(body);
    }

    private void updateRefreshLabel() {
        String time = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss"));
        lastRefreshLabel.setText("Last refresh: " + time);
    }

    // ════════════════════════════════════════════════════════════════
    // FILE WATCHER
    // ════════════════════════════════════════════════════════════════

    private void startWatchingProject(String projectName) {
        try {
            AppConfig config = ConfigManager.load();
            Path notesDir = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes");

            // Tell ProjectContext to watch this directory
            // Any change to tasks.md or work-log.md fires notifyFileChanged()
            // which calls our addFileChangeListener callback above
            ProjectContext.watchProjectDir(notesDir);

        } catch (Exception e) {
            System.err.println("[LogController] Failed to start watcher: " + e.getMessage());
        }
    }
}