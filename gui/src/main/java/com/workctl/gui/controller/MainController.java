package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Project;
import com.workctl.core.service.ProjectService;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.agent.AgentPanel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainController {

    @FXML private ListView<String> projectListView;
    @FXML private TabPane mainTabPane;
    @FXML private Label projectCountLabel;

    private final ProjectService projectService = new ProjectService();
    private AgentPanel agentPanel;

    private ScheduledExecutorService workspaceWatcher;
    private WatchService watchService;
    private volatile long lastProjectEventMs = 0;

    @FXML
    public void initialize() {
        try {
            // ── AI Agent tab (added in code, not FXML) ────────────
            agentPanel = new AgentPanel(null);
            Tab agentTab = new Tab("🤖 AI Agent", agentPanel);
            agentTab.setClosable(false);
            mainTabPane.getTabs().add(agentTab);

            // ── Load projects ─────────────────────────────────────
            loadProjects();

            // ── Project selection listener ────────────────────────
            projectListView.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            ProjectContext.setCurrentProject(newVal);
                            agentPanel.setProject(newVal);
                        }
                    });

            // ── Watch workspace for CLI-created projects ──────────
            startWorkspaceWatcher();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LOAD PROJECTS
    // ════════════════════════════════════════════════════════════════

    private void loadProjects() {
        try {
            AppConfig config = ConfigManager.load();
            Path workspace   = Paths.get(config.getWorkspace());
            List<Project> projects = projectService.listProjects(workspace);

            // Remember current selection so we can restore after refresh
            String selected = projectListView.getSelectionModel().getSelectedItem();

            projectListView.getItems().setAll(
                    projects.stream().map(Project::getName).toList()
            );

            updateCountLabel(projects.size());

            // Restore selection if project still exists
            if (selected != null && projectListView.getItems().contains(selected)) {
                projectListView.getSelectionModel().select(selected);
            }

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

        // Disable Create until name is typed
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

            if (projectListView.getItems().contains(name)) {
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

            // Auto-select and notify all tabs + agent panel
            projectListView.getSelectionModel().select(name);
            ProjectContext.setCurrentProject(name);
            agentPanel.setProject(name);

        } catch (IllegalStateException e) {
            showError("Already exists", e.getMessage());
        } catch (Exception e) {
            showError("Failed to create project", e.getMessage());
            e.printStackTrace();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WORKSPACE WATCHER — auto-refresh when CLI creates/deletes project
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

        // 1.5s debounce — let CLI finish writing all project files before reading
        if (lastProjectEventMs > 0
                && System.currentTimeMillis() - lastProjectEventMs > 1500) {
            lastProjectEventMs = 0;
            Platform.runLater(this::loadProjects);
        }
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