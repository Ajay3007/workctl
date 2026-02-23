package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Interview;
import com.workctl.core.domain.Meeting;
import com.workctl.core.domain.Project;
import com.workctl.core.model.InterviewResult;
import com.workctl.core.model.MeetingStatus;
import com.workctl.core.service.InterviewService;
import com.workctl.core.service.MeetingService;
import com.workctl.core.service.ProjectService;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.ThemeManager;
import com.workctl.gui.agent.AgentPanel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private VBox    projectListVBox;
    @FXML private TabPane mainTabPane;
    @FXML private Label   projectCountLabel;
    @FXML private Button  deleteProjectBtn;
    @FXML private Button  themeToggleBtn;

    // Meetings sidebar
    @FXML private VBox    meetingListSidebarVBox;
    @FXML private Label   meetingCountLabel;

    // Interviews sidebar
    @FXML private VBox    interviewListSidebarVBox;
    @FXML private Label   interviewCountLabel;

    // Injected controllers from fxml includes
    @FXML private MeetingController   meetingsViewController;
    @FXML private InterviewController interviewViewController;

    private final ProjectService  projectService  = new ProjectService();
    private final MeetingService  meetingService  = new MeetingService();
    private final InterviewService interviewService = new InterviewService();
    private AgentPanel agentPanel;

    /** Currently selected project name (null = none). */
    private String selectedProject;

    private ScheduledExecutorService workspaceWatcher;
    private WatchService watchService;
    private volatile long lastProjectEventMs = 0;

    @FXML
    public void initialize() {
        try {
            // ── Weekly Report tab ─────────────────────────────────
            FXMLLoader reportLoader = new FXMLLoader(getClass().getResource(
                    "/com/workctl/gui/view/weekly-report.fxml"));
            Parent reportView = reportLoader.load();
            Tab reportTab = new Tab("Weekly Report", reportView);
            reportTab.setClosable(false);
            mainTabPane.getTabs().add(reportTab);

            // ── AI Agent tab ──────────────────────────────────────
            agentPanel = new AgentPanel(null);
            Tab agentTab = new Tab("AI Agent", agentPanel);
            agentTab.setClosable(false);
            mainTabPane.getTabs().add(agentTab);

            // ── Load projects ─────────────────────────────────────
            loadProjects();

            // ── Delete button: disabled until a card is selected ──
            deleteProjectBtn.setDisable(true);

            // ── Wire MeetingController sidebar callback ────────────
            if (meetingsViewController != null) {
                meetingsViewController.setOnMeetingChanged(this::loadSidebarMeetings);
            }
            loadSidebarMeetings();

            // ── Wire InterviewController sidebar callback ──────────
            if (interviewViewController != null) {
                interviewViewController.setOnInterviewChanged(this::loadSidebarInterviews);
            }
            loadSidebarInterviews();

            // ── Workspace watcher ─────────────────────────────────
            startWorkspaceWatcher();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // THEME TOGGLE
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void handleThemeToggle() {
        ThemeManager.toggle();           // changes state, notifies HTML-panel listeners
        // Switch scene stylesheet
        var stylesheets = themeToggleBtn.getScene().getStylesheets();
        stylesheets.setAll(
                getClass().getResource(ThemeManager.cssPath()).toExternalForm());
        // Update button icon
        themeToggleBtn.setText(ThemeManager.toggleIcon());
        // CSS classes on project cards auto-update — no rebuild needed
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD PROJECTS — build card list
    // ════════════════════════════════════════════════════════════════

    private void loadProjects() {
        try {
            AppConfig config = ConfigManager.load();
            Path workspace   = Paths.get(config.getWorkspace());
            List<Project> projects = projectService.listProjects(workspace);

            Platform.runLater(() -> {
                projectListVBox.getChildren().clear();

                for (Project p : projects) {
                    projectListVBox.getChildren().add(createProjectCard(p.getName()));
                }

                updateCountLabel(projects.size());

                // Restore selection if project still exists
                boolean stillExists = projects.stream()
                        .anyMatch(p -> p.getName().equals(selectedProject));
                if (selectedProject != null && stillExists) {
                    highlightCard(selectedProject);
                } else {
                    selectedProject = null;
                    deleteProjectBtn.setDisable(true);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCountLabel(int count) {
        if (projectCountLabel != null) {
            projectCountLabel.setText(count + " project" + (count == 1 ? "" : "s"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // PROJECT CARD
    // ════════════════════════════════════════════════════════════════

    private Node createProjectCard(String name) {
        HBox card = new HBox(9);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setUserData(name);           // used for selection lookup
        card.getStyleClass().add("project-card");

        Label icon = new Label("\uD83D\uDCC1");   // 📁 folder emoji
        icon.getStyleClass().add("project-card-icon");

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("project-card-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        card.getChildren().addAll(icon, nameLabel);

        // Click to select
        card.setOnMouseClicked(e -> selectProject(name));

        return card;
    }

    private void selectProject(String name) {
        selectedProject = name;
        deleteProjectBtn.setDisable(false);
        highlightCard(name);
        ProjectContext.setCurrentProject(name);
        if (agentPanel != null) agentPanel.setProject(name);
    }

    /**
     * Apply selection highlight to the named card and clear all others.
     * CSS classes handle the visual difference — no inline styles needed.
     */
    private void highlightCard(String name) {
        for (Node node : projectListVBox.getChildren()) {
            if (node instanceof HBox card && card.getUserData() instanceof String cardName) {
                card.getStyleClass().removeAll("project-card", "project-card-selected");
                card.getStyleClass().add(
                        cardName.equals(name) ? "project-card-selected" : "project-card");
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // CREATE PROJECT — GUI Dialog
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void handleCreateProject() {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create New Project");
        dialog.setHeaderText("New Project");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. redis-load-test");
        nameField.setPrefWidth(280);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        TextField descField = new TextField();
        descField.setPromptText("Short description (optional)");
        descField.setPrefWidth(280);
        GridPane.setHgrow(descField, Priority.ALWAYS);

        Label nameHint = new Label("Lowercase letters, numbers, hyphens only. No spaces.");
        nameHint.setStyle("-fx-text-fill: #888888; -fx-font-size: 10;");

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11;");

        grid.add(new Label("Project Name *"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(nameHint, 1, 1);
        grid.add(new Label("Description"), 0, 2);
        grid.add(descField, 1, 2);
        grid.add(errorLabel, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(460);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(createBtn);
        okButton.setDisable(true);
        nameField.textProperty().addListener((obs, o, n) -> {
            okButton.setDisable(n.trim().isEmpty());
            errorLabel.setText("");
        });

        Platform.runLater(nameField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == createBtn) {
            String name = nameField.getText().trim();
            String desc = descField.getText().trim();

            if (!name.matches("[a-zA-Z0-9_\\-]+")) {
                showError("Invalid name",
                        "Name can only contain letters, numbers, hyphens, underscores.");
                return;
            }

            boolean alreadyExists = projectListVBox.getChildren().stream()
                    .anyMatch(n -> name.equals(n.getUserData()));
            if (alreadyExists) {
                showError("Already exists",
                        "A project named \"" + name + "\" already exists.");
                return;
            }

            doCreateProject(name, desc);
        }
    }

    private void doCreateProject(String name, String description) {
        try {
            AppConfig config = ConfigManager.load();
            Path workspace   = Paths.get(config.getWorkspace());

            projectService.createProject(workspace, name, description);

            loadProjects();

            // Auto-select after the Platform.runLater in loadProjects finishes
            Platform.runLater(() -> {
                selectProject(name);
            });

        } catch (IllegalStateException e) {
            showError("Already exists", e.getMessage());
        } catch (Exception e) {
            showError("Failed to create project", e.getMessage());
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE PROJECT — GUI Dialog
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void handleDeleteProject() {
        if (selectedProject == null) {
            showError("No project selected", "Please select a project to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Project");
        confirm.setHeaderText("Delete \"" + selectedProject + "\"?");
        confirm.setContentText(
            "This will permanently delete the project and ALL its data\n" +
            "(tasks, logs, notes, docs).\n\nThis action cannot be undone."
        );
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        TextInputDialog typeConfirm = new TextInputDialog();
        typeConfirm.setTitle("Confirm Deletion");
        typeConfirm.setHeaderText("Type the project name to confirm:");
        typeConfirm.setContentText("Project name:");

        Optional<String> typed = typeConfirm.showAndWait();
        if (typed.isEmpty() || !typed.get().trim().equals(selectedProject)) {
            showError("Deletion cancelled", "Project name did not match. Nothing was deleted.");
            return;
        }

        try {
            AppConfig config = ConfigManager.load();
            Path workspace = Paths.get(config.getWorkspace());
            projectService.deleteProject(workspace, selectedProject);

            if (selectedProject.equals(ProjectContext.getCurrentProject())) {
                ProjectContext.setCurrentProject(null);
            }
            selectedProject = null;
            loadProjects();

        } catch (Exception e) {
            showError("Failed to delete project", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WORKSPACE WATCHER
    // ════════════════════════════════════════════════════════════════

    private void startWorkspaceWatcher() {
        try {
            AppConfig config  = ConfigManager.load();
            Path projectsDir  = Paths.get(config.getWorkspace()).resolve("01_Projects");

            if (!Files.exists(projectsDir)) return;

            watchService = FileSystems.getDefault().newWatchService();
            projectsDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);

            workspaceWatcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "workctl-project-watcher");
                t.setDaemon(true);
                return t;
            });

            workspaceWatcher.scheduleWithFixedDelay(
                    this::pollProjectsDir,
                    1, 1, TimeUnit.SECONDS
            );

        } catch (Exception e) {
            System.err.println("[MainController] Workspace watcher failed: " + e.getMessage());
        }
    }

    private void pollProjectsDir() {
        if (watchService == null) return;

        WatchKey key = watchService.poll();
        if (key != null) {
            boolean changed = key.pollEvents().stream()
                    .anyMatch(e -> e.kind() != StandardWatchEventKinds.OVERFLOW);
            key.reset();
            if (changed) lastProjectEventMs = System.currentTimeMillis();
        }

        if (lastProjectEventMs > 0
                && System.currentTimeMillis() - lastProjectEventMs > 1500) {
            lastProjectEventMs = 0;
            Platform.runLater(this::loadProjects);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MEETINGS SIDEBAR
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void handleCreateMeetingQuick() {
        if (meetingsViewController != null) {
            // Switch to Meetings tab, then open dialog
            mainTabPane.getTabs().stream()
                    .filter(t -> "Meetings".equals(t.getText()))
                    .findFirst()
                    .ifPresent(t -> mainTabPane.getSelectionModel().select(t));
            meetingsViewController.showMeetingDialog(null);
        }
    }

    /** Load compact meeting cards into the sidebar. */
    private void loadSidebarMeetings() {
        try {
            java.util.List<Meeting> meetings = meetingService.listAllMeetings();

            Platform.runLater(() -> {
                if (meetingListSidebarVBox == null) return;
                meetingListSidebarVBox.getChildren().clear();

                if (meetingCountLabel != null) {
                    int count = meetings.size();
                    meetingCountLabel.setText(count + " meeting" + (count == 1 ? "" : "s"));
                }

                for (Meeting m : meetings) {
                    meetingListSidebarVBox.getChildren().add(createSidebarMeetingCard(m));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Node createSidebarMeetingCard(Meeting m) {
        HBox card = new HBox(7);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("project-card");

        Label icon = new Label("\uD83D\uDCC5");   // 📅
        icon.getStyleClass().add("project-card-icon");

        VBox info = new VBox(1);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label titleLbl = new Label(m.getTitle());
        titleLbl.getStyleClass().add("project-card-name");
        titleLbl.setStyle("-fx-font-weight: bold;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);

        String dateStr = m.getDateTime() != null
                ? m.getDateTime().toLocalDate().toString() : "—";
        String proj    = m.getProjectId() != null ? "  ·  " + m.getProjectId() : "  ·  General";
        Label  metaLbl = new Label(dateStr + proj);
        metaLbl.setStyle("-fx-text-fill: #718096; -fx-font-size: 10;");

        // Status dot
        boolean sched = m.getStatus() == MeetingStatus.SCHEDULED;
        Label statusDot = new Label(sched ? "●" : "✓");
        statusDot.setStyle("-fx-text-fill: " + (sched ? "#3b82f6" : "#22c55e")
                + "; -fx-font-size: 10;");

        info.getChildren().addAll(titleLbl, metaLbl);
        card.getChildren().addAll(icon, info, statusDot);

        // Click: switch to Meetings tab and open dialog
        card.setOnMouseClicked(e -> {
            mainTabPane.getTabs().stream()
                    .filter(t -> "Meetings".equals(t.getText()))
                    .findFirst()
                    .ifPresent(t -> mainTabPane.getSelectionModel().select(t));
            if (meetingsViewController != null) {
                meetingsViewController.showMeetingDialog(m);
            }
        });

        return card;
    }

    // ════════════════════════════════════════════════════════════════
    // INTERVIEWS SIDEBAR
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void handleCreateInterviewQuick() {
        // Switch to Interview tab, then open dialog
        mainTabPane.getTabs().stream()
                .filter(t -> "Interview".equals(t.getText()))
                .findFirst()
                .ifPresent(t -> mainTabPane.getSelectionModel().select(t));
        if (interviewViewController != null) {
            interviewViewController.showInterviewDialog(null);
        }
    }

    private void loadSidebarInterviews() {
        try {
            java.util.List<Interview> interviews = interviewService.listAllInterviews();

            Platform.runLater(() -> {
                if (interviewListSidebarVBox == null) return;
                interviewListSidebarVBox.getChildren().clear();

                if (interviewCountLabel != null) {
                    int count = interviews.size();
                    interviewCountLabel.setText(count + " interview" + (count == 1 ? "" : "s"));
                }

                for (Interview iv : interviews) {
                    interviewListSidebarVBox.getChildren().add(createSidebarInterviewCard(iv));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Node createSidebarInterviewCard(Interview iv) {
        HBox card = new HBox(7);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("project-card");

        Label icon = new Label("\uD83C\uDFAF");   // 🎯
        icon.getStyleClass().add("project-card-icon");

        VBox info = new VBox(1);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label titleLbl = new Label(iv.getCompany() + "  \u2014  " + iv.getRole());
        titleLbl.getStyleClass().add("project-card-name");
        titleLbl.setStyle("-fx-font-weight: bold;");
        titleLbl.setMaxWidth(Double.MAX_VALUE);

        String dateStr = iv.getDate() != null ? iv.getDate().toString() : "\u2014";
        Label  metaLbl = new Label(dateStr);
        metaLbl.setStyle("-fx-text-fill: #718096; -fx-font-size: 10;");

        // Result dot
        boolean offered  = iv.getResult() == InterviewResult.OFFERED;
        boolean rejected = iv.getResult() == InterviewResult.REJECTED;
        Label resultDot = new Label(offered ? "\u2714" : (rejected ? "\u2716" : "\u23F3"));
        resultDot.setStyle("-fx-text-fill: "
                + (offered ? "#22c55e" : (rejected ? "#ef4444" : "#94a3b8"))
                + "; -fx-font-size: 10;");

        info.getChildren().addAll(titleLbl, metaLbl);
        card.getChildren().addAll(icon, info, resultDot);

        // Click: switch to Interview tab and open detail view
        card.setOnMouseClicked(e -> {
            mainTabPane.getTabs().stream()
                    .filter(t -> "Interview".equals(t.getText()))
                    .findFirst()
                    .ifPresent(t -> mainTabPane.getSelectionModel().select(t));
            if (interviewViewController != null) {
                interviewViewController.showInterviewDetail(iv);
            }
        });

        return card;
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
