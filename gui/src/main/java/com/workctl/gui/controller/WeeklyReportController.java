package com.workctl.gui.controller;

import com.workctl.core.model.Task;
import com.workctl.core.model.WeeklyReportData;
import com.workctl.core.model.WeeklyReportData.StagnantEntry;
import com.workctl.core.service.WeeklyReportService;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Controls the Weekly Report tab.
 *
 * - DatePickers set the week range (default: current Mon–Sun)
 * - "Generate" calls WeeklyReportService on a background thread
 * - WebView renders an HTML preview
 * - "Export TXT" / "Export PDF" write files to a user-chosen location
 */
public class WeeklyReportController {

    // ── FXML bindings ─────────────────────────────────────────────
    @FXML private DatePicker weekStartPicker;
    @FXML private DatePicker weekEndPicker;
    @FXML private Button     generateBtn;
    @FXML private Button     exportTxtBtn;
    @FXML private Button     exportPdfBtn;
    @FXML private Label      statusLabel;
    @FXML private WebView    reportWebView;

    // ── State ─────────────────────────────────────────────────────
    private final WeeklyReportService reportService = new WeeklyReportService();
    private String          currentProject;
    private WeeklyReportData currentReport;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // ── Lifecycle ─────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Default to current Mon–Sun
        LocalDate today = LocalDate.now();
        weekStartPicker.setValue(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        weekEndPicker  .setValue(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));

        // Listen for project changes
        ProjectContext.addListener(project -> {
            currentProject = project;
            currentReport  = null;
            exportTxtBtn.setDisable(true);
            exportPdfBtn.setDisable(true);
            showPlaceholder(project);
        });

        showPlaceholder(null);

        // Re-render report HTML when theme switches
        ThemeManager.addListener(() -> Platform.runLater(() -> {
            if (currentReport != null) {
                reportWebView.getEngine().loadContent(buildHtmlReport(currentReport));
            } else {
                showPlaceholder(currentProject);
            }
        }));
    }

    // ── Actions ───────────────────────────────────────────────────

    @FXML
    public void handleGenerate() {
        if (currentProject == null || currentProject.isBlank()) {
            setStatus("No project selected — pick one from the sidebar.");
            return;
        }

        LocalDate start = weekStartPicker.getValue();
        LocalDate end   = weekEndPicker.getValue();

        if (start == null || end == null || end.isBefore(start)) {
            setStatus("Invalid date range — end must be on or after start.");
            return;
        }

        generateBtn.setDisable(true);
        exportTxtBtn.setDisable(true);
        exportPdfBtn.setDisable(true);
        setStatus("Generating report…");

        String project = currentProject;
        new Thread(() -> {
            WeeklyReportData data = reportService.generateReport(project, start, end);
            String html           = buildHtmlReport(data);
            Platform.runLater(() -> {
                currentReport = data;
                reportWebView.getEngine().loadContent(html, "text/html");
                exportTxtBtn.setDisable(false);
                exportPdfBtn.setDisable(false);
                generateBtn.setDisable(false);
                setStatus("Generated at " + LocalTime.now().format(TIME_FMT));
            });
        }, "workctl-report-gen").start();
    }

    @FXML
    public void handleExportTxt() {
        if (currentReport == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Report as Text");
        fc.setInitialFileName(defaultFilename("txt"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fc.showSaveDialog(reportWebView.getScene().getWindow());
        if (file == null) return;

        try {
            Files.writeString(file.toPath(), buildPlainTextReport(currentReport));
            setStatus("Exported TXT → " + file.getName());
        } catch (IOException e) {
            showError("Export failed", e.getMessage());
        }
    }

    @FXML
    public void handleExportPdf() {
        if (currentReport == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Report as PDF");
        fc.setInitialFileName(defaultFilename("pdf"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = fc.showSaveDialog(reportWebView.getScene().getWindow());
        if (file == null) return;

        try {
            writePdf(currentReport, file);
            setStatus("Exported PDF → " + file.getName());
        } catch (Exception e) {
            showError("PDF export failed", e.getMessage());
        }
    }

    // ── HTML report builder ───────────────────────────────────────

    private String buildHtmlReport(WeeklyReportData d) {
        StringBuilder html = new StringBuilder(buildHtmlHead());

        // Title
        html.append("<h1>").append(esc(d.getProjectName())).append("</h1>\n");
        html.append("<div class='meta'>")
            .append("Week: ").append(d.getWeekStart().format(DATE_FMT))
            .append(" – ").append(d.getWeekEnd().format(DATE_FMT))
            .append(" &nbsp;·&nbsp; Generated: ").append(d.getGeneratedDate().format(DATE_FMT))
            .append("</div>\n");

        // Headline + velocity
        String vClass = velClass(d.getVelocityLabel());
        html.append("<div class='headline'>")
            .append("<span class='vel-badge ").append(vClass).append("'>")
            .append(d.getVelocityLabel()).append("</span>  ")
            .append(esc(d.getHeadline()))
            .append("</div>\n");

        // Key metrics row
        html.append("<div class='metrics-row'>\n")
            .append(metric(d.getCompletedThisWeek(), "Completed"))
            .append(metric(d.getInProgressCount(),   "In Progress"))
            .append(metric(d.getOpenCount(),          "Open"))
            .append(metric(d.getNewTasksThisWeek(),  "Added"))
            .append(metricPct(d.getCompletionRate(), "Completion"))
            .append(metricScore(d.getProductivityScore()))
            .append("</div>\n");

        // Insights
        if (!d.getInsights().isEmpty()) {
            html.append(sectionHead("Observations"));
            html.append("<ul class='insight-list'>\n");
            d.getInsights().forEach(s ->
                html.append("<li>").append(esc(s)).append("</li>\n"));
            html.append("</ul>\n");
        }

        // Completed tasks
        html.append(sectionHead("Completed This Week (" + d.getCompletedThisWeek() + ")"));
        if (d.getCompletedTasks().isEmpty()) {
            html.append("<p class='empty'>No tasks completed in this period.</p>\n");
        } else {
            d.getCompletedTasks().forEach(t -> html.append(taskRow(t, "done")));
        }

        // In Progress
        html.append(sectionHead("In Progress (" + d.getInProgressCount() + ")"));
        if (d.getInProgressTasks().isEmpty()) {
            html.append("<p class='empty'>No tasks currently in progress.</p>\n");
        } else {
            d.getInProgressTasks().forEach(t -> html.append(taskRow(t, "wip")));
        }

        // Newly added
        if (!d.getNewTasks().isEmpty()) {
            html.append(sectionHead("Newly Added (" + d.getNewTasksThisWeek() + ")"));
            d.getNewTasks().forEach(t -> html.append(taskRow(t, "new")));
        }

        // Stagnant
        if (!d.getStagnantTasks().isEmpty()) {
            html.append(sectionHead(
                "Needs Attention — Stagnant (" + d.getStagnantTasks().size() + ")"));
            d.getStagnantTasks().forEach(e -> html.append(stagnantRow(e)));
        }

        // Tag activity
        if (!d.getTagActivity().isEmpty()) {
            html.append(sectionHead("Tag Activity"));
            int max = d.getTagActivity().values().stream().mapToInt(i -> i).max().orElse(1);
            d.getTagActivity().forEach((tag, count) -> {
                int pct = (int) (count * 100.0 / max);
                html.append("<div class='tag-row'>")
                    .append("<span class='tag-name'>#").append(esc(tag)).append("</span>")
                    .append("<div class='tag-bar-bg'>")
                    .append("<div class='tag-bar-fill' style='width:").append(pct).append("%;'></div>")
                    .append("</div>")
                    .append("<span class='tag-count'>").append(count).append("</span>")
                    .append("</div>\n");
            });
        }

        // Log highlights
        if (!d.getLogHighlights().isEmpty()) {
            html.append(sectionHead("Work Log Highlights"));
            d.getLogHighlights().forEach(line ->
                html.append("<div class='log-entry'>").append(esc(line)).append("</div>\n"));
        }

        html.append("</body></html>");
        return html.toString();
    }

    // ── HTML helper fragments ──────────────────────────────────────

    private String sectionHead(String title) {
        return "<h2>" + esc(title) + "</h2>\n";
    }

    private String metric(int value, String label) {
        return "<div class='metric'>"
                + "<div class='metric-value'>" + value + "</div>"
                + "<div class='metric-label'>" + label + "</div>"
                + "</div>\n";
    }

    private String metricPct(double value, String label) {
        return "<div class='metric'>"
                + "<div class='metric-value'>" + String.format("%.0f", value) + "%</div>"
                + "<div class='metric-label'>" + label + "</div>"
                + "</div>\n";
    }

    private String metricScore(double score) {
        String cls = score >= 85 ? "score-elite"
                   : score >= 70 ? "score-strong"
                   : score >= 50 ? "score-stable"
                   : "score-stalled";
        String label = score >= 85 ? "Elite"
                     : score >= 70 ? "Strong"
                     : score >= 50 ? "Stable"
                     : "Stalled";
        return "<div class='metric'>"
                + "<div class='metric-value " + cls + "'>"
                + String.format("%.0f", score) + "</div>"
                + "<div class='metric-label'>Score — " + label + "</div>"
                + "</div>\n";
    }

    private String taskRow(Task t, String type) {
        String icon = switch (type) {
            case "done" -> "<span class='icon-done'>✓</span>";
            case "wip"  -> "<span class='icon-wip'>◉</span>";
            default     -> "<span class='icon-new'>+</span>";
        };
        String badge = "<span class='badge p" + t.getPriority() + "'>P" + t.getPriority() + "</span>";
        String subtask = t.hasSubtasks()
                ? "<span class='task-meta'>" + t.getDoneSubtaskCount() + "/" + t.getTotalSubtaskCount() + " subtasks</span>"
                : "";
        return "<div class='task-row'>" + icon + badge
                + "<span class='task-title'>" + esc(t.getTitle()) + "</span>"
                + subtask + "</div>\n";
    }

    private String stagnantRow(StagnantEntry e) {
        String badge = "<span class='badge p" + e.task().getPriority() + "'>P" + e.task().getPriority() + "</span>";
        return "<div class='task-row'>"
                + "<span class='icon-warn'>⚠</span>"
                + badge
                + "<span class='task-title'>" + esc(e.task().getTitle()) + "</span>"
                + "<span class='task-meta stale'>" + e.daysIdle() + "d idle</span>"
                + "</div>\n";
    }

    private String velClass(String label) {
        return switch (label) {
            case "Elite"  -> "vel-elite";
            case "High"   -> "vel-high";
            case "Strong" -> "vel-strong";
            case "Steady" -> "vel-steady";
            default       -> "vel-quiet";
        };
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Plain-text report ─────────────────────────────────────────

    private String buildPlainTextReport(WeeklyReportData d) {
        StringBuilder sb = new StringBuilder();
        String divider = "═".repeat(56);
        String thin    = "─".repeat(56);

        sb.append(divider).append('\n');
        sb.append("  WEEKLY REPORT\n");
        sb.append(divider).append('\n');
        sb.append("  Project  : ").append(d.getProjectName()).append('\n');
        sb.append("  Week     : ")
          .append(d.getWeekStart().format(DATE_FMT)).append(" – ")
          .append(d.getWeekEnd().format(DATE_FMT)).append('\n');
        sb.append("  Generated: ").append(d.getGeneratedDate().format(DATE_FMT)).append('\n');
        sb.append(divider).append('\n').append('\n');

        // Summary line
        sb.append("  ").append(d.getHeadline()).append('\n').append('\n');

        // Metrics table
        sb.append("  METRICS\n").append("  ").append(thin).append('\n');
        sb.append(String.format("  %-22s %d%n", "Completed this week:", d.getCompletedThisWeek()));
        sb.append(String.format("  %-22s %d%n", "In Progress:",         d.getInProgressCount()));
        sb.append(String.format("  %-22s %d%n", "Open:",                d.getOpenCount()));
        sb.append(String.format("  %-22s %d%n", "Added this week:",     d.getNewTasksThisWeek()));
        sb.append(String.format("  %-22s %.0f%%%n","Completion rate:",    d.getCompletionRate()));
        sb.append(String.format("  %-22s %.0f / 100%n","Productivity score:", d.getProductivityScore()));
        sb.append('\n');

        // Insights
        sb.append("  OBSERVATIONS\n").append("  ").append(thin).append('\n');
        d.getInsights().forEach(i -> sb.append("  → ").append(i).append('\n'));
        sb.append('\n');

        // Completed tasks
        sb.append("  COMPLETED THIS WEEK (").append(d.getCompletedThisWeek()).append(")\n");
        sb.append("  ").append(thin).append('\n');
        if (d.getCompletedTasks().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            d.getCompletedTasks().forEach(t ->
                sb.append("  [x] [P").append(t.getPriority()).append("] ").append(t.getTitle()).append('\n'));
        }
        sb.append('\n');

        // In Progress
        sb.append("  IN PROGRESS (").append(d.getInProgressCount()).append(")\n");
        sb.append("  ").append(thin).append('\n');
        if (d.getInProgressTasks().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            d.getInProgressTasks().forEach(t -> {
                sb.append("  [~] [P").append(t.getPriority()).append("] ").append(t.getTitle());
                if (t.hasSubtasks())
                    sb.append("  (").append(t.getDoneSubtaskCount()).append('/').append(t.getTotalSubtaskCount()).append(" subtasks)");
                sb.append('\n');
            });
        }
        sb.append('\n');

        // Newly added
        if (!d.getNewTasks().isEmpty()) {
            sb.append("  NEWLY ADDED (").append(d.getNewTasksThisWeek()).append(")\n");
            sb.append("  ").append(thin).append('\n');
            d.getNewTasks().forEach(t ->
                sb.append("  [ ] [P").append(t.getPriority()).append("] ").append(t.getTitle()).append('\n'));
            sb.append('\n');
        }

        // Stagnant
        if (!d.getStagnantTasks().isEmpty()) {
            sb.append("  NEEDS ATTENTION — STAGNANT (").append(d.getStagnantTasks().size()).append(")\n");
            sb.append("  ").append(thin).append('\n');
            d.getStagnantTasks().forEach(e ->
                sb.append("  [!] [P").append(e.task().getPriority()).append("] ")
                  .append(e.task().getTitle())
                  .append("  (").append(e.daysIdle()).append("d idle)\n"));
            sb.append('\n');
        }

        // Tag activity
        if (!d.getTagActivity().isEmpty()) {
            sb.append("  TAG ACTIVITY\n").append("  ").append(thin).append('\n');
            d.getTagActivity().forEach((tag, count) ->
                sb.append(String.format("  #%-20s %d%n", tag, count)));
            sb.append('\n');
        }

        // Log highlights
        if (!d.getLogHighlights().isEmpty()) {
            sb.append("  WORK LOG HIGHLIGHTS\n").append("  ").append(thin).append('\n');
            d.getLogHighlights().forEach(line -> sb.append("  ").append(line).append('\n'));
            sb.append('\n');
        }

        sb.append(divider).append('\n');
        sb.append("  Generated by workctl\n");
        sb.append(divider).append('\n');

        return sb.toString();
    }

    // ── PDF export (Apache PDFBox) ────────────────────────────────

    private static final float A4_W  = PDRectangle.A4.getWidth();   // 595
    private static final float A4_H  = PDRectangle.A4.getHeight();  // 842
    private static final float MARGIN = 50;
    private static final float CONTENT_W = A4_W - 2 * MARGIN;

    /** Mutable context passed through PDF drawing methods. */
    private static class PdfCtx {
        PDDocument doc;
        PDPage     page;
        PDPageContentStream cs;
        float   y;
        float[] bgColor; // filled on every new page

        PdfCtx(PDDocument doc, float[] bgColor) throws IOException {
            this.doc     = doc;
            this.bgColor = bgColor;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) cs.close();
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y  = A4_H - MARGIN;
            // Fill background on every page
            if (bgColor != null) {
                cs.setNonStrokingColor(new PDColor(bgColor, PDDeviceRGB.INSTANCE));
                cs.addRect(0, 0, A4_W, A4_H);
                cs.fill();
            }
        }

        void checkPage(float needed) throws IOException {
            if (y - needed < MARGIN) newPage();
        }

        void drawText(String text, float x, PDFont font, float size, float[] rgb)
                throws IOException {
            cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(sanitizePdf(text));
            cs.endText();
        }

        void drawRect(float x, float rectY, float w, float h, float[] rgb) throws IOException {
            cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
            cs.addRect(x, rectY, w, h);
            cs.fill();
        }

        void nl(float h) { y -= h; }
    }

    private void writePdf(WeeklyReportData d, File file) throws IOException {
        PDFont bold  = PDType1Font.HELVETICA_BOLD;
        PDFont reg   = PDType1Font.HELVETICA;

        boolean isDark  = ThemeManager.isDark();
        float[] bgColor  = isDark ? new float[]{0.12f, 0.12f, 0.15f}  : new float[]{0.972f, 0.984f, 0.992f};
        float[] primary  = isDark ? new float[]{1f, 1f, 1f}            : new float[]{0.059f, 0.090f, 0.149f};
        float[] accent   = {0.13f, 0.83f, 0.67f};  // teal — same for both themes
        float[] muted    = isDark ? new float[]{0.45f, 0.53f, 0.6f}   : new float[]{0.39f, 0.45f, 0.52f};
        float[] red      = {0.87f, 0.27f, 0.27f};  // same for both themes
        float[] yellow   = isDark ? new float[]{0.95f, 0.77f, 0.3f}   : new float[]{0.78f, 0.42f, 0.04f};
        float[] gray     = isDark ? new float[]{0.3f, 0.35f, 0.4f}    : new float[]{0.75f, 0.80f, 0.85f};
        float[] bandBg   = isDark ? new float[]{0.11f, 0.19f, 0.32f}  : new float[]{0.882f, 0.914f, 0.949f};
        float[] tagColor = isDark ? new float[]{0.49f, 0.83f, 0.98f}  : new float[]{0.145f, 0.388f, 0.922f};

        try (PDDocument doc = new PDDocument()) {
            // Background is drawn automatically by PdfCtx.newPage() on every page
            PdfCtx ctx = new PdfCtx(doc, bgColor);

            // ── Title block ───────────────────────────────────────
            ctx.nl(8);
            ctx.drawText("WEEKLY REPORT", MARGIN, bold, 22, accent);
            ctx.nl(28);
            ctx.drawText(d.getProjectName(), MARGIN, bold, 15, primary);
            ctx.nl(18);
            ctx.drawText(
                "Week: " + d.getWeekStart().format(DATE_FMT)
                    + "  –  " + d.getWeekEnd().format(DATE_FMT),
                MARGIN, reg, 10, muted);
            ctx.nl(13);
            ctx.drawText("Generated: " + d.getGeneratedDate().format(DATE_FMT), MARGIN, reg, 10, muted);
            ctx.nl(20);

            // ── Metrics band ──────────────────────────────────────
            float bandY = ctx.y - 44;
            ctx.drawRect(MARGIN - 8, bandY, CONTENT_W + 16, 50, bandBg);
            float[] cols = {MARGIN, MARGIN + 90, MARGIN + 180, MARGIN + 270, MARGIN + 360};
            String[][] metrics = {
                {String.valueOf(d.getCompletedThisWeek()), "Completed"},
                {String.valueOf(d.getInProgressCount()),   "In Progress"},
                {String.valueOf(d.getOpenCount()),          "Open"},
                {String.valueOf(d.getNewTasksThisWeek()),  "Added"},
                {String.format("%.0f%%", d.getCompletionRate()), "Completion"},
            };
            for (int i = 0; i < metrics.length && i < cols.length; i++) {
                ctx.y = bandY + 32;
                ctx.drawText(metrics[i][0], cols[i], bold, 14, accent);
                ctx.y = bandY + 16;
                ctx.drawText(metrics[i][1], cols[i], reg, 8, muted);
            }
            ctx.y = bandY - 10;
            ctx.nl(8);

            // ── Headline ──────────────────────────────────────────
            ctx.checkPage(30);
            ctx.drawText(d.getHeadline(), MARGIN, bold, 11, primary);
            ctx.nl(18);

            // ── Observations ──────────────────────────────────────
            if (!d.getInsights().isEmpty()) {
                pdfSection(ctx, "OBSERVATIONS", bold, reg, accent, muted);
                for (String ins : d.getInsights()) {
                    ctx.checkPage(14);
                    for (String line : wrapPdf(ins, reg, 10, CONTENT_W - 10)) {
                        ctx.drawText((line == ins.split("\n")[0] ? "-> " : "   ") + line,
                                MARGIN + 4, reg, 10, muted);
                        ctx.nl(13);
                    }
                }
                ctx.nl(4);
            }

            // ── Completed tasks ───────────────────────────────────
            pdfSection(ctx, "COMPLETED THIS WEEK (" + d.getCompletedThisWeek() + ")",
                    bold, reg, accent, muted);
            if (d.getCompletedTasks().isEmpty()) {
                pdfItem(ctx, "(none)", reg, muted);
            } else {
                for (Task t : d.getCompletedTasks())
                    pdfTask(ctx, t, "[x]", accent, bold, reg, muted, yellow, red);
            }
            ctx.nl(4);

            // ── In Progress ───────────────────────────────────────
            pdfSection(ctx, "IN PROGRESS (" + d.getInProgressCount() + ")",
                    bold, reg, accent, muted);
            if (d.getInProgressTasks().isEmpty()) {
                pdfItem(ctx, "(none)", reg, muted);
            } else {
                for (Task t : d.getInProgressTasks())
                    pdfTask(ctx, t, "[~]", new float[]{0.38f, 0.65f, 0.98f}, bold, reg, muted, yellow, red);
            }
            ctx.nl(4);

            // ── Stagnant ──────────────────────────────────────────
            if (!d.getStagnantTasks().isEmpty()) {
                pdfSection(ctx, "NEEDS ATTENTION (" + d.getStagnantTasks().size() + ")",
                        bold, reg, red, muted);
                for (StagnantEntry e : d.getStagnantTasks()) {
                    ctx.checkPage(14);
                    String label = "[!] [P" + e.task().getPriority() + "] "
                            + truncate(e.task().getTitle(), 50)
                            + "  (" + e.daysIdle() + "d idle)";
                    ctx.drawText(sanitizePdf(label), MARGIN + 4, reg, 10, red);
                    ctx.nl(14);
                }
                ctx.nl(4);
            }

            // ── Tag activity ──────────────────────────────────────
            if (!d.getTagActivity().isEmpty()) {
                pdfSection(ctx, "TAG ACTIVITY", bold, reg, accent, muted);
                int maxCount = d.getTagActivity().values().stream().mapToInt(i -> i).max().orElse(1);
                for (Map.Entry<String, Integer> e : d.getTagActivity().entrySet()) {
                    ctx.checkPage(14);
                    float barW = (e.getValue() * 120f / maxCount);
                    // tag name
                    ctx.drawText("#" + sanitizePdf(e.getKey()), MARGIN + 4, reg, 10, tagColor);
                    // bar background
                    ctx.drawRect(MARGIN + 110, ctx.y - 2, 122, 10, gray);
                    // bar fill
                    ctx.drawRect(MARGIN + 110, ctx.y - 2, barW, 10, accent);
                    // count
                    ctx.drawText(String.valueOf(e.getValue()),
                            MARGIN + 240, reg, 10, muted);
                    ctx.nl(14);
                }
                ctx.nl(4);
            }

            // ── Log highlights ────────────────────────────────────
            if (!d.getLogHighlights().isEmpty()) {
                pdfSection(ctx, "WORK LOG HIGHLIGHTS", bold, reg, accent, muted);
                for (String line : d.getLogHighlights()) {
                    ctx.checkPage(14);
                    for (String wrapped : wrapPdf(line, reg, 9, CONTENT_W - 10)) {
                        ctx.drawText(wrapped, MARGIN + 4, reg, 9, muted);
                        ctx.nl(12);
                    }
                }
            }

            // ── Footer ────────────────────────────────────────────
            ctx.checkPage(20);
            ctx.nl(10);
            ctx.drawRect(MARGIN, ctx.y, CONTENT_W, 1, gray);
            ctx.nl(10);
            ctx.drawText("Generated by workctl  •  " + d.getGeneratedDate().format(DATE_FMT),
                    MARGIN, reg, 8, muted);

            ctx.cs.close();
            doc.save(file);
        }
    }

    private void pdfSection(PdfCtx ctx, String title,
                             PDFont bold, PDFont reg,
                             float[] accent, float[] muted) throws IOException {
        ctx.checkPage(30);
        ctx.nl(6);
        ctx.drawRect(MARGIN, ctx.y - 2, CONTENT_W, 1, muted);
        ctx.nl(12);
        ctx.drawText(title, MARGIN, bold, 9, accent);
        ctx.nl(14);
    }

    private void pdfItem(PdfCtx ctx, String text, PDFont reg, float[] color) throws IOException {
        ctx.checkPage(14);
        ctx.drawText(text, MARGIN + 4, reg, 10, color);
        ctx.nl(14);
    }

    private void pdfTask(PdfCtx ctx, Task t, String marker,
                          float[] markerColor, PDFont bold, PDFont reg,
                          float[] muted, float[] yellow, float[] red) throws IOException {
        ctx.checkPage(14);
        float[] pColor = t.getPriority() == 1 ? red : t.getPriority() == 2 ? yellow : muted;
        String label = marker + " [P" + t.getPriority() + "] " + truncate(t.getTitle(), 52);
        if (t.hasSubtasks())
            label += "  (" + t.getDoneSubtaskCount() + "/" + t.getTotalSubtaskCount() + ")";
        ctx.drawText(sanitizePdf(label), MARGIN + 4, reg, 10, pColor);
        ctx.nl(14);
    }

    /** Wrap text to fit within maxWidth points with the given font/size. */
    private List<String> wrapPdf(String text, PDFont font, float size, float maxW) {
        List<String> lines = new ArrayList<>();
        try {
            String[] words = text.split("\\s+");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                String test = cur.isEmpty() ? word : cur + " " + word;
                float w = font.getStringWidth(sanitizePdf(test)) / 1000f * size;
                if (w > maxW && !cur.isEmpty()) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        } catch (IOException e) {
            lines.add(sanitizePdf(text));
        }
        return lines.isEmpty() ? List.of(sanitizePdf(text)) : lines;
    }

    /** Replace non-Latin-1 characters so PDFBox standard fonts don't throw. */
    private static String sanitizePdf(String s) {
        if (s == null) return "";
        return s.replace("✓", "[x]")
                .replace("◉", "[~]")
                .replace("⚠", "[!]")
                .replace("→", "->")
                .replace("·", ".")
                .replace("—", "--")
                .replace("–", "-")
                .replace("\u2019", "'")
                .replace("\u201C", "\"")
                .replace("\u201D", "\"")
                .replaceAll("[^\u0000-\u00FF]", "?");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── Utilities ─────────────────────────────────────────────────

    private void showPlaceholder(String project) {
        String body = (project == null)
                ? "<p class='empty'>No project selected.<br>Pick one from the left sidebar.</p>"
                : "<p class='empty'><b>" + esc(project) + "</b> selected.<br>"
                    + "Choose a week range and click <b>Generate</b> to create your report.</p>";
        reportWebView.getEngine().loadContent(buildPlaceholderHead() + body + "</body></html>", "text/html");
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String defaultFilename(String ext) {
        String project = currentProject != null ? currentProject : "report";
        LocalDate s = weekStartPicker.getValue();
        String week = (s != null) ? s.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "week";
        return project + "-weekly-" + week + "." + ext;
    }

    // ── Theme-aware HTML assets ────────────────────────────────────

    private String buildHtmlHead() {
        boolean dark      = ThemeManager.isDark();
        String bg         = dark ? "#0f1117"  : "#f8fafc";
        String text       = dark ? "#e2e8f0"  : "#1e293b";
        String heading    = dark ? "#f8fafc"  : "#0f172a";
        String surface    = dark ? "#1e293b"  : "#f1f5f9";
        String rowBorder  = dark ? "#1a2035"  : "#e2e8f0";
        String border     = dark ? "#1e293b"  : "#e2e8f0";
        String muted      = "#64748b";
        String taskTitle  = dark ? "#cbd5e1"  : "#334155";
        String taskMeta   = dark ? "#475569"  : "#64748b";
        String dim        = dark ? "#94a3b8"  : "#475569";
        String p3bg       = dark ? "#1e293b"  : "#e2e8f0";
        String p3text     = dark ? "#94a3b8"  : "#475569";
        String tagName    = dark ? "#7dd3fc"  : "#2563eb";

        return "<!DOCTYPE html>"
            + "<html><head><meta charset=\"UTF-8\"><style>"
            + "*{box-sizing:border-box;margin:0;padding:0}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            +      "background:" + bg + ";color:" + text + ";max-width:920px;margin:0 auto;"
            +      "padding:32px 44px;font-size:14px;line-height:1.6}"
            + "h1{font-size:22px;font-weight:700;color:" + heading + ";margin-bottom:4px}"
            + ".meta{color:" + muted + ";font-size:12px;margin-bottom:24px}"
            + ".headline{background:" + surface + ";border-left:3px solid #22d3ee;"
            +           "padding:12px 16px;border-radius:4px;margin-bottom:20px;"
            +           "font-size:14px;display:flex;align-items:center;gap:10px}"
            + ".vel-badge{font-size:10px;font-weight:700;padding:3px 8px;border-radius:10px;"
            +            "white-space:nowrap;flex-shrink:0}"
            + ".vel-elite{background:#065f46;color:#6ee7b7}"
            + ".vel-high{background:#164e63;color:#67e8f9}"
            + ".vel-strong{background:#1e3a5f;color:#93c5fd}"
            + ".vel-steady{background:#3f3f46;color:#d4d4d8}"
            + ".vel-quiet{background:#27272a;color:#71717a}"
            + ".metrics-row{display:flex;gap:10px;margin-bottom:20px;flex-wrap:wrap}"
            + ".metric{background:" + surface + ";border-radius:6px;padding:12px 16px;"
            +         "flex:1;min-width:90px;text-align:center}"
            + ".metric-value{font-size:22px;font-weight:700;color:#22d3ee}"
            + ".metric-label{font-size:10px;color:" + muted + ";text-transform:uppercase;"
            +               "letter-spacing:.06em;margin-top:2px}"
            + ".score-elite{color:#6ee7b7}.score-strong{color:#93c5fd}"
            + ".score-stable{color:#fcd34d}.score-stalled{color:#fca5a5}"
            + "h2{font-size:10px;font-weight:600;text-transform:uppercase;letter-spacing:.1em;"
            +    "color:" + muted + ";border-top:1px solid " + border + ";padding-top:18px;"
            +    "margin-top:18px;margin-bottom:10px}"
            + ".task-row{display:flex;align-items:flex-start;gap:8px;"
            +           "padding:7px 0;border-bottom:1px solid " + rowBorder + "}"
            + ".task-row:last-child{border-bottom:none}"
            + ".badge{font-size:10px;font-weight:700;padding:2px 6px;border-radius:3px;"
            +        "white-space:nowrap;flex-shrink:0}"
            + ".p1{background:#7f1d1d;color:#fca5a5}"
            + ".p2{background:#78350f;color:#fcd34d}"
            + ".p3{background:" + p3bg + ";color:" + p3text + "}"
            + ".icon-done{color:#4ade80;font-weight:bold;flex-shrink:0}"
            + ".icon-wip{color:#60a5fa;flex-shrink:0}"
            + ".icon-new{color:#a78bfa;flex-shrink:0}"
            + ".icon-warn{color:#f87171;flex-shrink:0}"
            + ".task-title{flex:1;color:" + taskTitle + "}"
            + ".task-meta{font-size:11px;color:" + taskMeta + ";flex-shrink:0}"
            + ".stale{color:#f87171}"
            + ".insight-list{padding:0;list-style:none}"
            + ".insight-list li{padding:5px 0;color:" + dim + ";font-size:13px}"
            + ".insight-list li::before{content:'→ ';color:#22d3ee}"
            + ".tag-row{display:flex;align-items:center;gap:10px;padding:4px 0}"
            + ".tag-name{color:" + tagName + ";font-size:12px;width:110px;flex-shrink:0}"
            + ".tag-bar-bg{background:" + border + ";border-radius:2px;flex:1;height:13px;max-width:280px}"
            + ".tag-bar-fill{background:#3b82f6;height:13px;border-radius:2px}"
            + ".tag-count{color:" + muted + ";font-size:12px;width:26px}"
            + ".log-entry{color:" + dim + ";font-size:12px;padding:3px 0;font-family:monospace}"
            + ".empty{color:" + taskMeta + ";font-style:italic;padding:8px 0;font-size:13px}"
            + "</style></head><body>";
    }

    private String buildPlaceholderHead() {
        boolean dark = ThemeManager.isDark();
        String bg    = dark ? "#0f1117" : "#f8fafc";
        String text  = dark ? "#64748b" : "#64748b";
        String bold  = dark ? "#94a3b8" : "#475569";
        return "<!DOCTYPE html>"
            + "<html><head><meta charset=\"UTF-8\"><style>"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            +      "background:" + bg + ";color:" + text + ";display:flex;align-items:center;"
            +      "justify-content:center;height:100vh;margin:0;font-size:14px;text-align:center}"
            + ".empty{line-height:1.8}"
            + "b{color:" + bold + "}"
            + "</style></head><body><div class='empty'>";
    }
}
