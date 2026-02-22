package com.workctl.gui.agent;

import com.workctl.agent.AgentService;
import com.workctl.gui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AgentPanel — Dark-themed AI chat panel (full-width, no split preview)
 *
 * Agent responses render as themed HTML (markdown) directly in chat bubbles.
 * Responds to ThemeManager dark/light switching — re-renders all bubble WebViews.
 *
 * Preserved features: write mode toggle, quick actions, copy button, send message.
 */
public class AgentPanel extends VBox {

    private final AgentService agentService = new AgentService();

    // ── Chat components ──────────────────────────────────────────────
    private VBox       chatBox;
    private ScrollPane chatScroll;
    private TextField  inputField;
    private Button     sendButton;
    private Label      statusLabel;
    private Label      writeModeInfo;

    // ── State ────────────────────────────────────────────────────────
    private ToggleButton writeModeBtn;
    private String       currentProject;

    /** Tracks every agent-bubble WebView + its raw markdown for theme re-render */
    private final List<Map.Entry<WebView, String>> agentBubbles = new ArrayList<>();

    public AgentPanel(String projectName) {
        this.currentProject = projectName;
        buildUI();
        ThemeManager.addListener(() -> Platform.runLater(this::rerenderTheme));
    }

    public void setProject(String projectName) {
        this.currentProject = projectName;
        chatBox.getChildren().clear();
        agentBubbles.clear();
        addAgentBubble("Project switched to **" + projectName + "**. How can I help?");
    }

    // ════════════════════════════════════════════════════════════════
    // UI CONSTRUCTION
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        setSpacing(0);

        // ── Header toolbar ───────────────────────────────────────────
        HBox header = new HBox(10);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-toolbar");

        Label title = new Label("🤖 AI Agent");
        title.getStyleClass().add("panel-toolbar-title");

        Label subtitle = new Label("Powered by Claude");
        subtitle.getStyleClass().add("panel-status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        writeModeBtn = new ToggleButton("✏ Write Mode: OFF");
        writeModeBtn.getStyleClass().add("panel-toolbar-btn");
        writeModeBtn.selectedProperty().addListener((obs, wasOn, isOn) -> {
            writeModeBtn.setText(isOn ? "✏ Write Mode: ON" : "✏ Write Mode: OFF");
            writeModeBtn.getStyleClass().removeAll("panel-toolbar-btn", "panel-toolbar-btn-active");
            writeModeBtn.getStyleClass().add(isOn ? "panel-toolbar-btn-active" : "panel-toolbar-btn");
            updateWriteModeInfo(isOn);
        });
        Tooltip.install(writeModeBtn, new Tooltip(
            "OFF (default) → Agent can only READ your tasks and logs.\n" +
            "               Safe for questions and analysis.\n\n" +
            "ON  → Agent can also CREATE tasks and MOVE task status.\n" +
            "      Enable this when you say things like:\n" +
            "      'Break this goal into tasks'\n" +
            "      'Mark task #52 as done'"
        ));

        header.getChildren().addAll(title, subtitle, spacer, writeModeBtn);

        // ── Write mode info bar ──────────────────────────────────────
        writeModeInfo = new Label(
            "  ✏  Write mode is OFF — agent can read tasks/logs but cannot modify them. " +
            "Toggle ON to let the agent create tasks or change status."
        );
        writeModeInfo.setWrapText(true);
        applyWriteModeInfoStyle(false);

        // ── Quick actions toolbar ────────────────────────────────────
        HBox quickActions = new HBox(8);
        quickActions.setPadding(new Insets(9, 16, 9, 16));
        quickActions.getStyleClass().add("panel-toolbar");
        quickActions.getChildren().addAll(
            quickAction("📅 Weekly Summary",
                "Generate an AI summary of what I accomplished this week"),
            quickAction("📊 Project Insights",
                "Give me an AI analysis of this project's health and productivity"),
            quickAction("⚠ Stagnant Tasks",
                "Which tasks have been stuck for more than 7 days?"),
            quickAction("🔀 Decompose Goal",
                "I'll type a goal and you break it into tasks — ready?")
        );

        // ── Chat area ────────────────────────────────────────────────
        chatBox = new VBox(12);
        chatBox.setPadding(new Insets(16));

        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.getStyleClass().add("kanban-col-scroll");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Welcome message
        addAgentBubble(
            "Hi! I'm your AI assistant for **" + currentProject + "**.\n\n" +
            "I can read your tasks, work logs, and project stats. Ask me anything!"
        );

        // ── Status bar ───────────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("panel-status");
        statusLabel.setStyle("-fx-padding: 3 16;");

        // ── Input row ────────────────────────────────────────────────
        inputField = new TextField();
        inputField.setPromptText("Ask the agent anything about your project...");
        inputField.getStyleClass().add("search-field");
        inputField.setStyle("-fx-background-radius: 20; -fx-border-radius: 20;");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage());

        sendButton = new Button("Send ➤");
        sendButton.getStyleClass().add("add-task-btn");
        sendButton.setStyle("-fx-background-radius: 20;");
        sendButton.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, inputField, sendButton);
        inputRow.setPadding(new Insets(12, 16, 16, 16));
        inputRow.setAlignment(Pos.CENTER);
        inputRow.getStyleClass().add("panel-toolbar");
        inputRow.setStyle("-fx-border-width: 1 0 0 0;"); // top border only

        getChildren().addAll(header, writeModeInfo, quickActions, chatScroll, statusLabel, inputRow);
    }

    private Button quickAction(String label, String message) {
        Button btn = new Button(label);
        btn.getStyleClass().add("panel-toolbar-btn");
        btn.setOnAction(e -> {
            inputField.setText(message);
            sendMessage();
        });
        return btn;
    }

    /** Applies theme-aware inline style to the write mode info bar */
    private void applyWriteModeInfoStyle(boolean isOn) {
        if (isOn) {
            writeModeInfo.setStyle(ThemeManager.isDark()
                ? "-fx-background-color: #2d2a1a; -fx-text-fill: #f6c347; -fx-font-size: 11;" +
                  " -fx-padding: 6 16; -fx-border-color: #5a4e1a; -fx-border-width: 0 0 1 0;"
                : "-fx-background-color: #fef9e7; -fx-text-fill: #7d6608; -fx-font-size: 11;" +
                  " -fx-padding: 6 16; -fx-border-color: #f9e79f; -fx-border-width: 0 0 1 0;"
            );
        } else {
            writeModeInfo.setStyle(ThemeManager.isDark()
                ? "-fx-background-color: #12192e; -fx-text-fill: #63b3ed; -fx-font-size: 11;" +
                  " -fx-padding: 6 16; -fx-border-color: #1e3a5f; -fx-border-width: 0 0 1 0;"
                : "-fx-background-color: #eaf4fb; -fx-text-fill: #1a5276; -fx-font-size: 11;" +
                  " -fx-padding: 6 16; -fx-border-color: #aed6f1; -fx-border-width: 0 0 1 0;"
            );
        }
    }

    private void updateWriteModeInfo(boolean isOn) {
        writeModeInfo.setText(isOn
            ? "  ✏  Write mode is ON — agent can create tasks and change task status." +
              " Turn OFF to return to read-only mode."
            : "  ✏  Write mode is OFF — agent can read tasks/logs but cannot modify them." +
              " Toggle ON to let the agent create tasks or change status."
        );
        applyWriteModeInfoStyle(isOn);
    }

    // ════════════════════════════════════════════════════════════════
    // THEME RE-RENDER
    // ════════════════════════════════════════════════════════════════

    private void rerenderTheme() {
        applyWriteModeInfoStyle(writeModeBtn.isSelected());
        for (Map.Entry<WebView, String> entry : agentBubbles) {
            entry.getKey().getEngine().loadContent(buildAgentHtml(entry.getValue()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MESSAGE HANDLING
    // ════════════════════════════════════════════════════════════════

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (message.isBlank() || currentProject == null) return;

        inputField.clear();
        addUserBubble(message);
        setInputEnabled(false);
        statusLabel.setText("🤖 Agent is thinking...");

        boolean actMode = writeModeBtn.isSelected();

        CompletableFuture.supplyAsync(() ->
            agentService.ask(currentProject, message, actMode)
        ).thenAcceptAsync(response -> {
            addAgentBubble(response);
            setInputEnabled(true);
            statusLabel.setText("");
            scrollToBottom();
        }, Platform::runLater)
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                addAgentBubble("Error: " + ex.getMessage());
                setInputEnabled(true);
                statusLabel.setText("");
            });
            return null;
        });
    }

    // ════════════════════════════════════════════════════════════════
    // CHAT BUBBLE BUILDERS
    // ════════════════════════════════════════════════════════════════

    private void addUserBubble(String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(500);
        bubble.setStyle(
            "-fx-background-color: #2563eb;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 16 16 4 16;" +
            "-fx-font-size: 13;" +
            "-fx-padding: 10 14;"
        );

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(0, 4, 0, 60));
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addAgentBubble(String rawMarkdown) {
        // Render markdown directly in a WebView for proper formatting
        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);
        webView.setMaxWidth(Double.MAX_VALUE);
        webView.setPrefHeight(100); // initial; auto-sized below

        webView.getEngine().loadContent(buildAgentHtml(rawMarkdown));

        // Auto-size height once the document is rendered
        webView.getEngine().documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                Platform.runLater(() -> {
                    try {
                        Object h = webView.getEngine().executeScript("document.body.scrollHeight");
                        if (h instanceof Integer ih) {
                            webView.setPrefHeight(ih + 20);
                        }
                    } catch (Exception ignored) {}
                });
            }
        });

        // Track for theme re-rendering on dark/light toggle
        agentBubbles.add(new AbstractMap.SimpleEntry<>(webView, rawMarkdown));

        // ── Copy button ──────────────────────────────────────────────
        Button copyBtn = new Button("⎘ Copy");
        copyBtn.getStyleClass().add("panel-toolbar-btn");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(rawMarkdown);
            clipboard.setContent(content);

            copyBtn.setText("✓ Copied!");
            copyBtn.setStyle(
                "-fx-background-color: rgba(72,187,120,0.15); -fx-text-fill: #68d391;" +
                "-fx-border-color: rgba(72,187,120,0.3); -fx-border-radius: 4;" +
                "-fx-background-radius: 4; -fx-font-size: 11; -fx-padding: 4 10;"
            );

            PauseTransition delay = new PauseTransition(Duration.seconds(2));
            delay.setOnFinished(ev -> {
                copyBtn.setText("⎘ Copy");
                copyBtn.getStyleClass().setAll("panel-toolbar-btn");
                copyBtn.setStyle("");
            });
            delay.play();
        });

        HBox btnRow = new HBox(6, copyBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);
        btnRow.setPadding(new Insets(4, 0, 0, 0));

        Label icon = new Label("🤖");
        icon.setStyle("-fx-font-size: 18; -fx-padding: 4 6 0 0;");

        VBox bubbleCol = new VBox(2, webView, btnRow);
        HBox.setHgrow(webView, Priority.ALWAYS);

        HBox wrapper = new HBox(8, icon, bubbleCol);
        HBox.setHgrow(bubbleCol, Priority.ALWAYS);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setPadding(new Insets(0, 4, 0, 0));

        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    // ════════════════════════════════════════════════════════════════
    // HTML BUILDER
    // ════════════════════════════════════════════════════════════════

    private String buildAgentHtml(String rawMarkdown) {
        String bodyHtml;
        try {
            List<org.commonmark.Extension> extensions =
                List.of(org.commonmark.ext.gfm.tables.TablesExtension.create());
            org.commonmark.parser.Parser parser =
                org.commonmark.parser.Parser.builder().extensions(extensions).build();
            org.commonmark.renderer.html.HtmlRenderer renderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().extensions(extensions).build();
            bodyHtml = renderer.render(parser.parse(rawMarkdown));
        } catch (Exception e) {
            bodyHtml = "<pre>" +
                rawMarkdown.replace("&", "&amp;").replace("<", "&lt;") +
                "</pre>";
        }

        String bg     = ThemeManager.htmlSurface();
        String text   = ThemeManager.htmlText();
        String h1col  = ThemeManager.htmlHeading();
        String border = ThemeManager.htmlBorder();
        String muted  = ThemeManager.htmlMuted();
        String code   = ThemeManager.htmlCode();
        String codeT  = ThemeManager.htmlCodeText();
        String link   = ThemeManager.htmlLink();
        String quote  = ThemeManager.htmlQuote();
        String quoteT = ThemeManager.htmlQuoteText();
        String rowAlt = ThemeManager.htmlBg();

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
            + "*{box-sizing:border-box;}"
            + "body{font-family:'Segoe UI',system-ui,sans-serif;font-size:13px;line-height:1.7;"
            +   "color:" + text + ";margin:0;padding:10px 14px;background:" + bg + ";"
            +   "border-radius:10px;-webkit-user-select:text;user-select:text;}"
            + "h1{font-size:17px;color:" + h1col + ";border-bottom:1.5px solid #2563eb;"
            +   "padding-bottom:5px;margin:0 0 12px;}"
            + "h2{font-size:14px;color:" + text + ";border-bottom:1px solid " + border + ";"
            +   "padding-bottom:3px;margin:14px 0 8px;}"
            + "h3{font-size:13px;color:" + muted + ";margin:10px 0 5px;}"
            + "ul,ol{padding-left:22px;margin:6px 0;} li{margin:3px 0;}"
            + "code{background:" + code + ";padding:2px 6px;border-radius:3px;"
            +   "font-family:'Consolas','Courier New',monospace;font-size:11px;color:" + codeT + ";}"
            + "pre{background:" + code + ";color:" + text + ";padding:10px 14px;border-radius:6px;"
            +   "font-size:11px;overflow-x:auto;border-left:2px solid #2563eb;}"
            + "pre code{background:none;color:" + text + ";padding:0;}"
            + "blockquote{border-left:3px solid #2563eb;margin:0;padding:6px 12px;"
            +   "background:" + quote + ";color:" + quoteT + ";border-radius:0 4px 4px 0;}"
            + "strong{color:" + h1col + ";}"
            + "hr{border:none;border-top:1px solid " + border + ";margin:12px 0;}"
            + "p{margin:5px 0;}"
            + "a{color:" + link + ";text-decoration:none;} a:hover{text-decoration:underline;}"
            + "table{border-collapse:collapse;width:100%;margin:10px 0;font-size:12px;}"
            + "th{background:#2563eb;color:white;padding:6px 10px;text-align:left;}"
            + "td{padding:5px 10px;border-bottom:1px solid " + border + ";}"
            + "tr:nth-child(even) td{background:" + rowAlt + ";}"
            + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private void setInputEnabled(boolean enabled) {
        inputField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }
}
