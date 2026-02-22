package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.web.WebView;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * StatsController — Stats tab (dark HTML WebView dashboard)
 *
 * Auto-refreshes via ProjectContext file watcher whenever tasks.md changes.
 */
public class StatsController {

    @FXML private WebView statsWebView;

    private final StatsService statsService = new StatsService();
    private String currentProject;

    @FXML
    public void initialize() {
        statsWebView.getEngine().loadContent(buildPlaceholderHtml());

        ProjectContext.addListener(projectName -> {
            currentProject = projectName;
            if (projectName == null) return;
            startWatchingProject(projectName);
            loadStatsAsync(projectName);
        });

        ProjectContext.addFileChangeListener(() -> {
            if (currentProject != null) loadStatsAsync(currentProject);
        });

        // Re-render HTML when theme switches
        ThemeManager.addListener(() -> {
            if (currentProject != null) {
                loadStatsAsync(currentProject);
            } else {
                Platform.runLater(() ->
                        statsWebView.getEngine().loadContent(buildPlaceholderHtml()));
            }
        });
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD STATS
    // ════════════════════════════════════════════════════════════════

    private void loadStatsAsync(String projectName) {
        Thread thread = new Thread(() -> {
            try {
                ProjectInsights insights = statsService.generateInsights(projectName);
                Platform.runLater(() -> statsWebView.getEngine()
                        .loadContent(buildHtmlDashboard(insights)));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> statsWebView.getEngine()
                        .loadContent(buildErrorHtml(e.getMessage())));
            }
        }, "workctl-stats-loader");
        thread.setDaemon(true);
        thread.start();
    }

    // ════════════════════════════════════════════════════════════════
    // HTML DASHBOARD BUILDER
    // ════════════════════════════════════════════════════════════════

    private String buildHtmlDashboard(ProjectInsights ins) {
        String lastRefresh = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        double comp = ins.getCompletionRate();
        double prod = ins.getProductivityScore();
        long   stag = ins.getStagnantTasks();

        String scoreColor = prod >= 70 ? "#68d391" : prod >= 50 ? "#f6ad55" : "#fc8181";
        String compGrad   = comp >= 75
                ? "linear-gradient(90deg,#38a169,#68d391)"
                : "linear-gradient(90deg,#4299e1,#63b3ed)";

        // ── Heatmap ────────────────────────────────────────────────
        StringBuilder heat = new StringBuilder();
        heat.append("<div style='display:grid;grid-template-columns:repeat(7,18px);gap:3px;'>");
        LocalDate today = LocalDate.now();
        LocalDate heatStart = today.minusDays(34);
        Map<LocalDate, Integer> activity = ins.getDailyActivity();
        for (LocalDate d = heatStart; !d.isAfter(today); d = d.plusDays(1)) {
            int cnt = activity.getOrDefault(d, 0);
            String col   = heatColor(cnt);
            String title = d + ": " + cnt + (cnt == 1 ? " event" : " events");
            heat.append("<div title='").append(title)
                .append("' style='width:18px;height:18px;border-radius:3px;background:")
                .append(col).append(";'></div>");
        }
        heat.append("</div>");

        // ── Stagnation badge ───────────────────────────────────────
        String stagBadge = stag > 0
                ? "<span class='badge br'>&#9888; " + stag + " task" + (stag > 1 ? "s" : "") + " idle for &gt;7 days</span>"
                : "<span class='badge bg'>&#10003; No stagnant tasks</span>";

        // ── Score label ────────────────────────────────────────────
        String scoreLabel = getScoreLabel(prod);

        // ── CSS (theme-aware) ──────────────────────────────────────
        String bg      = ThemeManager.htmlBg();
        String surface = ThemeManager.htmlSurface();
        String border  = ThemeManager.htmlBorder();
        String text    = ThemeManager.htmlText();
        String heading = ThemeManager.htmlHeading();
        String muted   = ThemeManager.htmlMuted();
        String dim     = ThemeManager.htmlDim();
        String pTrack  = ThemeManager.isDark() ? "#2d3748" : "#e2e8f0";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0;}");
        sb.append("body{font-family:'Segoe UI',system-ui,sans-serif;background:" + bg + ";color:" + text + ";padding:20px;font-size:13px;}");
        sb.append(".hdr{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px;padding-bottom:12px;border-bottom:1px solid " + border + ";}");
        sb.append(".hdr h1{font-size:18px;color:" + heading + ";font-weight:700;}");
        sb.append(".ts{font-size:11px;color:" + dim + ";}");
        sb.append(".cards{display:flex;gap:12px;margin-bottom:14px;}");
        sb.append(".card{flex:1;background:" + surface + ";border:1px solid " + border + ";border-radius:10px;padding:14px;text-align:center;}");
        sb.append(".cv{font-size:30px;font-weight:bold;margin-bottom:4px;}");
        sb.append(".cl{font-size:10px;color:" + dim + ";text-transform:uppercase;letter-spacing:.5px;}");
        sb.append(".sec{background:" + surface + ";border:1px solid " + border + ";border-radius:10px;padding:16px;margin-bottom:12px;}");
        sb.append(".st{font-size:11px;font-weight:600;color:" + muted + ";text-transform:uppercase;letter-spacing:.8px;margin-bottom:12px;}");
        sb.append(".ptrack{background:" + pTrack + ";border-radius:6px;height:10px;overflow:hidden;margin:8px 0;}");
        sb.append(".pfill{height:100%;border-radius:6px;}");
        sb.append(".plbl{display:flex;justify-content:space-between;font-size:12px;color:" + muted + ";}");
        sb.append(".score-row{display:flex;align-items:center;gap:16px;}");
        sb.append(".snum{font-size:40px;font-weight:bold;line-height:1;}");
        sb.append(".slbl{font-size:13px;color:" + muted + ";margin-top:4px;}");
        sb.append(".badge{display:inline-block;padding:5px 14px;border-radius:20px;font-size:12px;font-weight:600;}");
        sb.append(".br{background:rgba(245,101,101,.15);color:#fc8181;border:1px solid rgba(245,101,101,.3);}");
        sb.append(".bg{background:rgba(72,187,120,.15);color:#68d391;border:1px solid rgba(72,187,120,.3);}");
        sb.append(".day-hdr{display:flex;gap:3px;margin-bottom:4px;font-size:9px;color:" + dim + ";}");
        sb.append(".dh{width:18px;text-align:center;}");
        sb.append("</style></head><body>");

        // ── Header ─────────────────────────────────────────────────
        sb.append("<div class='hdr'><h1>Project Statistics</h1>")
          .append("<span class='ts'>Last refresh: ").append(lastRefresh).append("</span></div>");

        // ── Metric cards ───────────────────────────────────────────
        sb.append("<div class='cards'>");
        appendCard(sb, "#63b3ed", ins.getTotalTasks(),       "Total");
        appendCard(sb, "#fc8181", ins.getOpenTasks(),        "Open");
        appendCard(sb, "#f6ad55", ins.getInProgressTasks(),  "In Progress");
        appendCard(sb, "#68d391", ins.getDoneTasks(),        "Done");
        sb.append("</div>");

        // ── Completion rate ────────────────────────────────────────
        sb.append("<div class='sec'><div class='st'>Completion Rate</div>");
        sb.append("<div class='plbl'><span>")
          .append(String.format("%.1f", comp)).append("% complete</span><span>")
          .append(ins.getDoneTasks()).append("/").append(ins.getTotalTasks())
          .append(" tasks done</span></div>");
        sb.append("<div class='ptrack'><div class='pfill' style='width:")
          .append(String.format("%.1f", comp)).append("%;background:").append(compGrad)
          .append(";'></div></div></div>");

        // ── Productivity score ─────────────────────────────────────
        sb.append("<div class='sec'><div class='st'>Productivity Score</div>");
        sb.append("<div class='score-row'>");
        sb.append("<div class='snum' style='color:").append(scoreColor).append("'>")
          .append(String.format("%.0f", prod)).append("</div>");
        sb.append("<div><div style='font-weight:bold;font-size:16px;color:white;'>")
          .append(scoreLabel).append("</div><div class='slbl'>out of 100</div></div></div>");
        sb.append("<div class='ptrack' style='margin-top:10px'>")
          .append("<div class='pfill' style='width:").append(String.format("%.1f", prod))
          .append("%;background:").append(scoreColor).append(";'></div></div></div>");

        // ── Stagnation ─────────────────────────────────────────────
        sb.append("<div class='sec'><div class='st'>Stagnation</div>")
          .append(stagBadge).append("</div>");

        // ── Activity heatmap ───────────────────────────────────────
        sb.append("<div class='sec'><div class='st'>Activity — Last 35 Days</div>");
        sb.append("<div class='day-hdr'>");
        for (String d : new String[]{"M","T","W","T","F","S","S"})
            sb.append("<div class='dh'>").append(d).append("</div>");
        sb.append("</div>");
        sb.append(heat);
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendCard(StringBuilder sb, String color, int value, String label) {
        sb.append("<div class='card'><div class='cv' style='color:").append(color)
          .append("'>").append(value)
          .append("</div><div class='cl'>").append(label).append("</div></div>");
    }

    private String heatColor(int count) {
        if (count == 0) return "#1a2535";
        if (count == 1) return "#1a4730";
        if (count <= 3) return "#276134";
        if (count <= 5) return "#2d7a40";
        return "#39d353";
    }

    private String getScoreLabel(double score) {
        if (score >= 85) return "Elite Execution";
        if (score >= 70) return "Strong Momentum";
        if (score >= 50) return "Stable";
        if (score >= 30) return "Fragmented";
        return "Stalled";
    }

    // ════════════════════════════════════════════════════════════════
    // PLACEHOLDER / ERROR HTML
    // ════════════════════════════════════════════════════════════════

    private String buildPlaceholderHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
             + "body{background:" + ThemeManager.htmlBg() + ";color:" + ThemeManager.htmlDim() + ";"
             +   "font-family:'Segoe UI',sans-serif;display:flex;align-items:center;"
             +   "justify-content:center;height:100vh;margin:0;font-size:14px;}"
             + "p{text-align:center;line-height:1.8;}"
             + "</style></head><body><p>Select a project to view statistics.</p></body></html>";
    }

    private String buildErrorHtml(String msg) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
             + "<style>body{background:" + ThemeManager.htmlBg() + ";color:#fc8181;"
             +   "font-family:monospace;padding:20px;}</style>"
             + "</head><body><pre>Failed to load stats:\n" + (msg == null ? "" : msg) + "</pre></body></html>";
    }

    // ════════════════════════════════════════════════════════════════
    // FILE WATCHER
    // ════════════════════════════════════════════════════════════════

    private void startWatchingProject(String projectName) {
        try {
            AppConfig config = ConfigManager.load();
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
