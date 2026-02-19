package com.workctl.gui.agent;

import com.workctl.agent.AgentService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;

import java.util.concurrent.CompletableFuture;

/**
 * AgentPanel — Improved version
 *
 * Changes from v1:
 *   1. Agent bubbles now use selectable TextArea (read-only, styled) so text
 *      can be selected and copied with Ctrl+C / right-click.
 *   2. Right panel: WebView that renders the latest agent response as
 *      proper Markdown HTML — tables, bold, bullets, headers all render correctly.
 *   3. Write mode moved into a clearly labelled toggle button with a
 *      description label that explains what it does.
 *   4. Copy button on each agent bubble for one-click copy.
 */
public class AgentPanel extends HBox {

    private final AgentService agentService = new AgentService();

    // ── Left: Chat ───────────────────────────────────────────────────
    private VBox chatBox;
    private ScrollPane chatScroll;
    private TextField inputField;
    private Button sendButton;
    private Label statusLabel;

    // ── Right: Markdown Preview ──────────────────────────────────────
    private WebView markdownView;
    private Label previewLabel;

    // ── State ────────────────────────────────────────────────────────
    private ToggleButton writeModeBtn;
    private String currentProject;
    private String lastAgentResponse = "";

    public AgentPanel(String projectName) {
        this.currentProject = projectName;
        buildUI();
    }

    public void setProject(String projectName) {
        this.currentProject = projectName;
        chatBox.getChildren().clear();
        addAgentBubble("Project switched to **" + projectName + "**. How can I help?");
        updatePreview("Project switched to **" + projectName + "**. How can I help?");
    }

    // ════════════════════════════════════════════════════════════════
    // UI CONSTRUCTION
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        setSpacing(0);
        setStyle("-fx-background-color: #f0f2f5;");

        // ── RIGHT PANEL: Markdown Preview ────────────────────────────
        VBox rightPanel = buildPreviewPanel();
        rightPanel.setPrefWidth(420);
        rightPanel.setMinWidth(320);
        rightPanel.setMaxWidth(500);

        // ── LEFT PANEL: Chat ─────────────────────────────────────────
        VBox leftPanel = buildChatPanel();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Divider
        Region divider = new Region();
        divider.setPrefWidth(1);
        divider.setStyle("-fx-background-color: #dee2e6;");

        getChildren().addAll(leftPanel, divider, rightPanel);
    }

    // ── LEFT: full chat column ───────────────────────────────────────
    private VBox buildChatPanel() {
        VBox panel = new VBox(0);

        // Header
        HBox header = new HBox(10);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #2c3e50;");
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🤖 AI Agent");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold;");

        Label subtitle = new Label("Powered by Claude");
        subtitle.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Write Mode Toggle ────────────────────────────────────────
        // Replaced plain checkbox with a visible toggle button + description
        writeModeBtn = new ToggleButton("✏ Write Mode: OFF");
        writeModeBtn.setStyle(offStyle());
        writeModeBtn.selectedProperty().addListener((obs, wasOn, isOn) -> {
            writeModeBtn.setText(isOn ? "✏ Write Mode: ON" : "✏ Write Mode: OFF");
            writeModeBtn.setStyle(isOn ? onStyle() : offStyle());
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

        // Write mode description bar — visible below header when ON
        Label writeModeInfo = new Label(
            "  ✏  Write mode is OFF — agent can read tasks/logs but cannot modify them. " +
            "Toggle ON to let the agent create tasks or change status."
        );
        writeModeInfo.setWrapText(true);
        writeModeInfo.setStyle("""
            -fx-background-color: #eaf4fb;
            -fx-text-fill: #1a5276;
            -fx-font-size: 11;
            -fx-padding: 6 16;
            -fx-border-color: #aed6f1;
            -fx-border-width: 0 0 1 0;
            """);

        writeModeBtn.selectedProperty().addListener((obs, wasOn, isOn) -> {
            if (isOn) {
                writeModeInfo.setText(
                    "  ✏  Write mode is ON — agent can create tasks and change task status. " +
                    "Turn OFF to return to read-only mode."
                );
                writeModeInfo.setStyle("""
                    -fx-background-color: #fef9e7;
                    -fx-text-fill: #7d6608;
                    -fx-font-size: 11;
                    -fx-padding: 6 16;
                    -fx-border-color: #f9e79f;
                    -fx-border-width: 0 0 1 0;
                    """);
            } else {
                writeModeInfo.setText(
                    "  ✏  Write mode is OFF — agent can read tasks/logs but cannot modify them. " +
                    "Toggle ON to let the agent create tasks or change status."
                );
                writeModeInfo.setStyle("""
                    -fx-background-color: #eaf4fb;
                    -fx-text-fill: #1a5276;
                    -fx-font-size: 11;
                    -fx-padding: 6 16;
                    -fx-border-color: #aed6f1;
                    -fx-border-width: 0 0 1 0;
                    """);
            }
        });

        // Quick Actions
        HBox quickActions = new HBox(8);
        quickActions.setPadding(new Insets(10, 16, 10, 16));
        quickActions.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

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

        // Chat message area
        chatBox = new VBox(12);
        chatBox.setPadding(new Insets(16));

        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: transparent;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Welcome
        addAgentBubble(
            "Hi! I'm your AI assistant for **" + currentProject + "**.\n\n" +
            "I can read your tasks, work logs, and project stats. Ask me anything.\n\n" +
            "The **Markdown Preview** panel on the right will render my responses " +
            "with proper formatting — tables, bold text, and bullet points."
        );

        // Status
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11; -fx-padding: 4 16;");

        // Input row
        inputField = new TextField();
        inputField.setPromptText("Ask the agent anything about your project...");
        inputField.setStyle("""
            -fx-font-size: 13;
            -fx-padding: 10 14;
            -fx-background-radius: 20;
            -fx-border-radius: 20;
            -fx-border-color: #dee2e6;
            """);
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage());

        sendButton = new Button("Send ➤");
        sendButton.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #3498db, #2980b9);
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-padding: 10 18;
            -fx-background-radius: 20;
            """);
        sendButton.setOnAction(e -> sendMessage());

        HBox inputRow = new HBox(10, inputField, sendButton);
        inputRow.setPadding(new Insets(12, 16, 16, 16));
        inputRow.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        inputRow.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(
            header, writeModeInfo, quickActions, chatScroll, statusLabel, inputRow
        );

        return panel;
    }

    // ── RIGHT: markdown preview column ──────────────────────────────
    private VBox buildPreviewPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: white;");

        // Preview header
        HBox previewHeader = new HBox();
        previewHeader.setPadding(new Insets(12, 16, 12, 16));
        previewHeader.setStyle("""
            -fx-background-color: #34495e;
            -fx-border-color: #2c3e50;
            -fx-border-width: 0 0 1 0;
            """);
        previewHeader.setAlignment(Pos.CENTER_LEFT);

        previewLabel = new Label("📄 Markdown Preview");
        previewLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 13; -fx-font-weight: bold;");

        Label previewHint = new Label("  — latest response");
        previewHint.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11;");

        previewHeader.getChildren().addAll(previewLabel, previewHint);

        // WebView for rendered Markdown
        markdownView = new WebView();
        markdownView.setContextMenuEnabled(true); // right-click → copy in WebView
        VBox.setVgrow(markdownView, Priority.ALWAYS);

        // Initial placeholder
        loadPreviewHtml("<p style='color:#999; font-style:italic;'>Agent responses will be rendered here with full Markdown formatting.</p>");

        panel.getChildren().addAll(previewHeader, markdownView);
        return panel;
    }

    private Button quickAction(String label, String message) {
        Button btn = new Button(label);
        btn.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #ced4da;
            -fx-border-radius: 14;
            -fx-background-radius: 14;
            -fx-font-size: 11;
            -fx-padding: 5 12;
            """);
        btn.setOnAction(e -> {
            inputField.setText(message);
            sendMessage();
        });
        return btn;
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
            updatePreview(response);
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
        // User bubbles: styled TextArea so text is selectable
        TextArea bubble = new TextArea(text);
        bubble.setEditable(false);
        bubble.setWrapText(true);
        bubble.setPrefRowCount(1);
        bubble.setMaxWidth(480);
        bubble.setStyle("""
            -fx-background-color: #3498db;
            -fx-text-fill: white;
            -fx-background-radius: 16 16 4 16;
            -fx-border-color: transparent;
            -fx-font-size: 13;
            -fx-padding: 4;
            -fx-control-inner-background: #3498db;
            """);

        // Auto-resize height to content
        bubble.setPrefRowCount(Math.min(text.split("\n").length + 1, 8));

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(0, 4, 0, 60));
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addAgentBubble(String rawMarkdown) {
        // ── Selectable text area ─────────────────────────────────────
        TextArea bubble = new TextArea(rawMarkdown);
        bubble.setEditable(false);
        bubble.setWrapText(true);
        bubble.setMaxWidth(520);
        bubble.setPrefRowCount(Math.min(rawMarkdown.split("\n").length + 2, 20));
        bubble.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 12;
            -fx-border-color: #e9ecef;
            -fx-border-radius: 12;
            -fx-border-width: 1;
            -fx-font-size: 13;
            -fx-font-family: 'Segoe UI', Arial, sans-serif;
            -fx-control-inner-background: white;
            -fx-padding: 4;
            """);

        // ── Copy button ──────────────────────────────────────────────
        Button copyBtn = new Button("⎘ Copy");
        copyBtn.setStyle("""
            -fx-background-color: #ecf0f1;
            -fx-text-fill: #555;
            -fx-font-size: 10;
            -fx-padding: 3 8;
            -fx-background-radius: 8;
            -fx-border-color: #dee2e6;
            -fx-border-radius: 8;
            """);
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(rawMarkdown);
            clipboard.setContent(content);

            // Visual feedback
            copyBtn.setText("✓ Copied!");
            copyBtn.setStyle("""
                -fx-background-color: #d5f5e3;
                -fx-text-fill: #1e8449;
                -fx-font-size: 10;
                -fx-padding: 3 8;
                -fx-background-radius: 8;
                -fx-border-color: #a9dfbf;
                -fx-border-radius: 8;
                """);

            // Reset after 2 seconds
            new java.util.Timer().schedule(new java.util.TimerTask() {
                public void run() {
                    Platform.runLater(() -> {
                        copyBtn.setText("⎘ Copy");
                        copyBtn.setStyle("""
                            -fx-background-color: #ecf0f1;
                            -fx-text-fill: #555;
                            -fx-font-size: 10;
                            -fx-padding: 3 8;
                            -fx-background-radius: 8;
                            -fx-border-color: #dee2e6;
                            -fx-border-radius: 8;
                            """);
                    });
                }
            }, 2000);
        });

        // "View in Preview" button
        Button previewBtn = new Button("⊞ Preview");
        previewBtn.setStyle("""
            -fx-background-color: #eaf4fb;
            -fx-text-fill: #1a5276;
            -fx-font-size: 10;
            -fx-padding: 3 8;
            -fx-background-radius: 8;
            -fx-border-color: #aed6f1;
            -fx-border-radius: 8;
            """);
        previewBtn.setOnAction(e -> updatePreview(rawMarkdown));

        HBox btnRow = new HBox(6, copyBtn, previewBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Label icon = new Label("🤖");
        icon.setStyle("-fx-font-size: 18; -fx-padding: 4 4 0 0;");

        VBox bubbleCol = new VBox(4, bubble, btnRow);

        HBox wrapper = new HBox(8, icon, bubbleCol);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setPadding(new Insets(0, 60, 0, 0));

        chatBox.getChildren().add(wrapper);
        scrollToBottom();

        // Auto-update preview with the latest response
        updatePreview(rawMarkdown);
    }

    // ════════════════════════════════════════════════════════════════
    // MARKDOWN PREVIEW
    // ════════════════════════════════════════════════════════════════

    /**
     * Convert raw markdown to HTML and load it in the WebView.
     *
     * Uses commonmark (already in your gui/build.gradle) to render.
     * Falls back to a simple regex-based renderer if commonmark isn't available.
     */
    private void updatePreview(String markdown) {
        lastAgentResponse = markdown;
        String html = renderMarkdown(markdown);
        loadPreviewHtml(html);
    }

    private String renderMarkdown(String markdown) {
        try {
            // Use commonmark — already a dependency in gui/build.gradle
            org.commonmark.parser.Parser parser =
                    org.commonmark.parser.Parser.builder().build();
            org.commonmark.renderer.html.HtmlRenderer renderer =
                    org.commonmark.renderer.html.HtmlRenderer.builder().build();
            return renderer.render(parser.parse(markdown));
        } catch (Exception e) {
            // Fallback: simple text with line breaks
            return "<pre style='white-space:pre-wrap;'>" +
                   markdown.replace("&", "&amp;").replace("<", "&lt;") +
                   "</pre>";
        }
    }

    private void loadPreviewHtml(String bodyHtml) {
        String fullHtml = """
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
                h1 { font-size: 20px; color: #1a252f; border-bottom: 2px solid #3498db; padding-bottom: 8px; }
                h2 { font-size: 17px; color: #2c3e50; border-bottom: 1px solid #dee2e6; padding-bottom: 4px; margin-top: 20px; }
                h3 { font-size: 15px; color: #34495e; margin-top: 14px; }
                table {
                    border-collapse: collapse;
                    width: 100%%;
                    margin: 14px 0;
                    font-size: 13px;
                }
                th {
                    background: #2c3e50;
                    color: white;
                    padding: 8px 12px;
                    text-align: left;
                }
                td {
                    padding: 7px 12px;
                    border-bottom: 1px solid #ecf0f1;
                }
                tr:nth-child(even) td { background: #f8f9fa; }
                code {
                    background: #f4f6f7;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: 'Consolas', monospace;
                    font-size: 12px;
                    color: #c0392b;
                }
                pre {
                    background: #f4f6f7;
                    padding: 14px;
                    border-radius: 6px;
                    overflow-x: auto;
                    font-size: 12px;
                    border-left: 3px solid #3498db;
                }
                blockquote {
                    border-left: 4px solid #3498db;
                    margin: 0;
                    padding: 8px 16px;
                    background: #eaf4fb;
                    color: #1a5276;
                }
                ul, ol { padding-left: 22px; }
                li { margin: 4px 0; }
                strong { color: #1a252f; }
                hr { border: none; border-top: 1px solid #dee2e6; margin: 16px 0; }
                p { margin: 8px 0; }
            </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """.formatted(bodyHtml);

        markdownView.getEngine().loadContent(fullHtml);
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

    private String offStyle() {
        return """
            -fx-background-color: #455a64;
            -fx-text-fill: #b0bec5;
            -fx-border-color: #607d8b;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-font-size: 11;
            -fx-padding: 5 12;
            """;
    }

    private String onStyle() {
        return """
            -fx-background-color: #f39c12;
            -fx-text-fill: white;
            -fx-border-color: #e67e22;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-font-size: 11;
            -fx-font-weight: bold;
            -fx-padding: 5 12;
            """;
    }
}
