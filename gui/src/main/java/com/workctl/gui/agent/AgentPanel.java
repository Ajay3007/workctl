package com.workctl.gui.agent;

import com.workctl.agent.AgentService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.util.concurrent.CompletableFuture;

/**
 * AgentPanel
 *
 * A JavaFX chat UI panel that lets users talk to the AI agent from the GUI.
 * Add this as a new Tab in your MainController TabPane.
 *
 * Features:
 *   - Chat-style message bubbles (user on right, agent on left)
 *   - Non-blocking: agent calls run on a background thread so GUI stays responsive
 *   - Write mode toggle: checkbox to enable --act mode
 *   - Quick action buttons for common agent tasks
 *   - Loading indicator while waiting for Claude
 *
 * To add to your GUI:
 *   Tab agentTab = new Tab("🤖 AI Agent", new AgentPanel(currentProject));
 *   agentTab.setClosable(false);
 *   tabPane.getTabs().add(agentTab);
 *
 * Or call AgentPanel.setProject(projectName) when user selects a project.
 */
public class AgentPanel extends VBox {

    private final AgentService agentService = new AgentService();

    private VBox chatBox;
    private ScrollPane scrollPane;
    private TextField inputField;
    private Button sendButton;
    private CheckBox writeMode;
    private Label statusLabel;
    private String currentProject;

    public AgentPanel(String projectName) {
        this.currentProject = projectName;
        buildUI();
    }

    // Called when user switches project in the sidebar
    public void setProject(String projectName) {
        this.currentProject = projectName;
        chatBox.getChildren().clear();
        addAgentBubble("Project switched to **" + projectName + "**. How can I help?");
    }

    // ════════════════════════════════════════════════════════════════
    // UI CONSTRUCTION
    // ════════════════════════════════════════════════════════════════

    private void buildUI() {
        setSpacing(0);
        setStyle("-fx-background-color: #f8f9fa;");

        // ── Header ───────────────────────────────────────────────────
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

        writeMode = new CheckBox("Write mode");
        writeMode.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 11;");
        Tooltip writeTip = new Tooltip(
                "When checked, agent can create tasks and change task status.\n" +
                "Leave unchecked for read-only questions.");
        Tooltip.install(writeMode, writeTip);

        header.getChildren().addAll(title, subtitle, spacer, writeMode);

        // ── Quick Actions ────────────────────────────────────────────
        HBox quickActions = new HBox(8);
        quickActions.setPadding(new Insets(10, 16, 10, 16));
        quickActions.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        Button btnWeekly    = quickAction("📅 Weekly Summary",
                "Generate an AI summary of what I accomplished this week");
        Button btnInsights  = quickAction("📊 Project Insights",
                "Give me an AI analysis of this project's health and productivity");
        Button btnStagnant  = quickAction("⚠ Stagnant Tasks",
                "Which tasks have been stuck for more than 7 days?");
        Button btnBreakdown = quickAction("🔀 Decompose Goal",
                "I'll type a goal and you break it into tasks -- ready?");

        quickActions.getChildren().addAll(btnWeekly, btnInsights, btnStagnant, btnBreakdown);

        // ── Chat Area ────────────────────────────────────────────────
        chatBox = new VBox(12);
        chatBox.setPadding(new Insets(16));

        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Welcome message
        addAgentBubble(
            "Hi! I'm your AI assistant for **" + currentProject + "**.\n\n" +
            "I can read your tasks, work logs, and project stats. Ask me anything like:\n" +
            "• \"What did I work on this week?\"\n" +
            "• \"Which P1 tasks are stagnant?\"\n" +
            "• \"How is my productivity score?\"\n\n" +
            "Enable **Write mode** if you want me to create or move tasks."
        );

        // ── Status bar ───────────────────────────────────────────────
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11; -fx-padding: 0 16;");

        // ── Input Row ────────────────────────────────────────────────
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

        getChildren().addAll(header, quickActions, scrollPane, statusLabel, inputRow);
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
                -fx-cursor: hand;
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

        // Show user bubble
        addUserBubble(message);

        // Disable input while waiting
        setInputEnabled(false);
        statusLabel.setText("🤖 Agent is thinking...");

        boolean actMode = writeMode.isSelected();

        // Run agent call on background thread — never block JavaFX thread
        CompletableFuture.supplyAsync(() ->
                agentService.ask(currentProject, message, actMode)
        ).thenAcceptAsync(response -> {
            // Update UI back on JavaFX thread
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
        bubble.setStyle("""
                -fx-background-color: #3498db;
                -fx-text-fill: white;
                -fx-padding: 10 14;
                -fx-background-radius: 16 16 4 16;
                -fx-font-size: 13;
                """);

        HBox wrapper = new HBox(bubble);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    private void addAgentBubble(String text) {
        // Simple markdown-ish rendering: bold **text** and bullet points
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(560);
        bubble.setFont(Font.font(13));
        bubble.setStyle("""
                -fx-background-color: white;
                -fx-text-fill: #2c3e50;
                -fx-padding: 10 14;
                -fx-background-radius: 16 16 16 4;
                -fx-border-color: #e9ecef;
                -fx-border-width: 1;
                -fx-border-radius: 16 16 16 4;
                -fx-font-size: 13;
                """);

        Label icon = new Label("🤖");
        icon.setStyle("-fx-font-size: 18;");

        HBox wrapper = new HBox(8, icon, bubble);
        wrapper.setAlignment(Pos.CENTER_LEFT);
        chatBox.getChildren().add(wrapper);
        scrollToBottom();
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private void setInputEnabled(boolean enabled) {
        inputField.setDisable(!enabled);
        sendButton.setDisable(!enabled);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}
