package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.ThemeManager;
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
 * LogController — Logs tab (dark HTML preview with Raw toggle)
 *
 * Renders work-log.md as a dark-themed HTML preview.
 * A toolbar "Raw" toggle switches to the raw Markdown text.
 */
public class LogController {

    @FXML private VBox logRoot;

    private WebView   markdownView;
    private Label     statusLabel;
    private Label     lastRefreshLabel;
    private Button    rawToggleBtn;

    private String  currentProject;
    private String  currentRawContent = "";
    private boolean showingRaw        = false;

    @FXML
    public void initialize() {
        buildUI();

        ProjectContext.addListener(project -> {
            currentProject = project;
            if (project == null) return;
            startWatchingProject(project);
            loadLog(project);
        });

        ProjectContext.addFileChangeListener(() -> {
            if (currentProject != null) loadLog(currentProject);
        });

        // Re-render HTML when theme switches
        ThemeManager.addListener(() -> Platform.runLater(this::rerender));
    }

    // ════════════════════════════════════════════════════════════════
    // UI BUILD
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        logRoot.setSpacing(0);

        // ── Toolbar (CSS class handles all theme-sensitive colors) ─
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8, 14, 8, 14));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("panel-toolbar");

        Label title = new Label("Work Log");
        title.getStyleClass().add("panel-toolbar-title");

        statusLabel = new Label("");
        statusLabel.getStyleClass().add("panel-status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lastRefreshLabel = new Label("");
        lastRefreshLabel.getStyleClass().add("panel-refresh");

        rawToggleBtn = new Button("<> Raw");
        rawToggleBtn.getStyleClass().add("panel-toolbar-btn");
        rawToggleBtn.setOnAction(e -> toggleRaw());

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("panel-toolbar-btn");
        refreshBtn.setOnAction(e -> { if (currentProject != null) loadLog(currentProject); });

        toolbar.getChildren().addAll(title, statusLabel, spacer, lastRefreshLabel, rawToggleBtn, refreshBtn);

        // ── WebView ───────────────────────────────────────────────
        markdownView = new WebView();
        markdownView.setContextMenuEnabled(true);
        VBox.setVgrow(markdownView, Priority.ALWAYS);
        markdownView.getEngine().loadContent(buildPlaceholderHtml());

        logRoot.getChildren().addAll(toolbar, markdownView);
    }

    // ════════════════════════════════════════════════════════════════
    // RAW TOGGLE
    // ════════════════════════════════════════════════════════════════

    private void toggleRaw() {
        showingRaw = !showingRaw;
        // CSS class handles active/inactive button color
        rawToggleBtn.getStyleClass().removeAll("panel-toolbar-btn", "panel-toolbar-btn-active");
        rawToggleBtn.getStyleClass().add(showingRaw ? "panel-toolbar-btn-active" : "panel-toolbar-btn");
        rawToggleBtn.setText(showingRaw ? "Preview" : "<> Raw");
        rerender();
    }

    private void rerender() {
        if (showingRaw) {
            markdownView.getEngine().loadContent(wrapRaw(currentRawContent));
        } else {
            renderMarkdown(currentRawContent);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD + RENDER
    // ════════════════════════════════════════════════════════════════

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
                if (!content.equals(currentRawContent)) {
                    currentRawContent = content;
                    Platform.runLater(() -> {
                        if (showingRaw) {
                            markdownView.getEngine().loadContent(wrapRaw(content));
                        } else {
                            renderMarkdown(content);
                        }
                        updateRefreshLabel();
                    });
                }
            } else {
                Platform.runLater(() -> {
                    currentRawContent = "";
                    markdownView.getEngine().loadContent(buildPlaceholderHtml(projectName));
                    updateRefreshLabel();
                });
            }

        } catch (Exception e) {
            Platform.runLater(() ->
                    markdownView.getEngine().loadContent(buildErrorHtml(e.getMessage())));
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
            markdownView.getEngine().loadContent(wrapHtmlDark(bodyHtml));
        } catch (Exception e) {
            markdownView.getEngine().loadContent(
                    "<pre style='color:#fc8181'>" + e.getMessage() + "</pre>");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HTML WRAPPERS
    // ════════════════════════════════════════════════════════════════

    private String wrapHtmlDark(String body) {
        String bg     = ThemeManager.htmlBg();
        String text   = ThemeManager.htmlText();
        String h1col  = ThemeManager.htmlHeading();
        String border = ThemeManager.htmlBorder();
        String muted  = ThemeManager.htmlMuted();
        String code   = ThemeManager.htmlCode();
        String codeT  = ThemeManager.htmlCodeText();
        String link   = ThemeManager.htmlLink();
        String quote  = ThemeManager.htmlQuote();
        String quoteT = ThemeManager.htmlQuoteText();

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
             + "*{box-sizing:border-box;}"
             + "body{font-family:'Segoe UI',system-ui,sans-serif;font-size:14px;line-height:1.75;"
             +   "color:" + text + ";padding:22px 28px;margin:0;background:" + bg + ";"
             +   "-webkit-user-select:text;user-select:text;}"
             + "h1{font-size:20px;color:" + h1col + ";border-bottom:2px solid #2563eb;padding-bottom:6px;margin:0 0 16px;}"
             + "h2{font-size:16px;color:" + text + ";border-bottom:1px solid " + border + ";padding-bottom:4px;margin:22px 0 10px;}"
             + "h3{font-size:14px;color:" + muted + ";margin:14px 0 6px;}"
             + "ul,ol{padding-left:24px;} li{margin:4px 0;}"
             + "code{background:" + code + ";padding:2px 7px;border-radius:4px;"
             +   "font-family:'Consolas','Courier New',monospace;font-size:12px;color:" + codeT + ";}"
             + "pre{background:" + code + ";color:" + text + ";padding:14px 16px;border-radius:6px;"
             +   "font-size:12px;overflow-x:auto;border-left:3px solid #2563eb;}"
             + "pre code{background:none;color:" + text + ";padding:0;}"
             + "blockquote{border-left:4px solid #2563eb;margin:0;padding:8px 16px;"
             +   "background:" + quote + ";color:" + quoteT + ";border-radius:0 4px 4px 0;}"
             + "strong{color:" + h1col + ";}"
             + "hr{border:none;border-top:1px solid " + border + ";margin:18px 0;}"
             + "p{margin:6px 0;}"
             + "a{color:" + link + ";text-decoration:none;} a:hover{text-decoration:underline;}"
             + "</style></head><body>" + body + "</body></html>";
    }

    private String wrapRaw(String content) {
        String escaped = content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String bg   = ThemeManager.htmlBg();
        String text = ThemeManager.isDark() ? "#d4d4d4" : "#334155";
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
             + "body{background:" + bg + ";color:" + text + ";"
             +   "font-family:'Consolas','Courier New',monospace;font-size:12px;"
             +   "padding:16px 20px;margin:0;-webkit-user-select:text;user-select:text;}"
             + "pre{white-space:pre-wrap;word-break:break-word;}"
             + "</style></head><body><pre>" + escaped + "</pre></body></html>";
    }

    private String buildPlaceholderHtml() { return buildPlaceholderHtml(null); }

    private String buildPlaceholderHtml(String projectName) {
        String bg    = ThemeManager.htmlBg();
        String muted = ThemeManager.htmlDim();
        String strong= ThemeManager.htmlMuted();
        String msg = projectName != null
                ? "No work-log.md found for project: <strong style='color:" + strong + "'>" + projectName + "</strong>"
                : "Select a project to view the work log.";
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
             + "body{background:" + bg + ";color:" + muted + ";font-family:'Segoe UI',sans-serif;"
             +   "display:flex;align-items:center;justify-content:center;height:100vh;margin:0;font-size:14px;}"
             + "p{text-align:center;line-height:1.8;}"
             + "</style></head><body><p>" + msg + "</p></body></html>";
    }

    private String buildErrorHtml(String msg) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
             + "<style>body{background:" + ThemeManager.htmlBg() + ";color:#fc8181;"
             +   "font-family:monospace;padding:20px;}</style>"
             + "</head><body><pre>Failed to load log:\n" + (msg == null ? "" : msg) + "</pre></body></html>";
    }

    private void updateRefreshLabel() {
        lastRefreshLabel.setText("Refreshed " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
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
            ProjectContext.watchProjectDir(notesDir);
        } catch (Exception e) {
            System.err.println("[LogController] Failed to start watcher: " + e.getMessage());
        }
    }
}
