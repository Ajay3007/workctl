package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.WorkflowRun;
import com.workctl.core.domain.WorkflowTemplate;
import com.workctl.core.model.RunStatus;
import com.workctl.core.model.StepStatus;
import com.workctl.core.service.ProjectService;
import com.workctl.core.service.WorkflowService;
import com.workctl.gui.ProjectContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WorkflowController {

    // ── FXML fields ───────────────────────────────────────────────────

    @FXML private ComboBox<String> scopeFilterCombo;
    @FXML private ComboBox<String> templateFilterCombo;
    @FXML private Button newRunBtn;
    @FXML private ListView<WorkflowRun> runListView;

    @FXML private VBox   runHeaderBox;
    @FXML private Label  runNameLabel;
    @FXML private Label  runStatusLabel;
    @FXML private Label  runMetaLabel;
    @FXML private Label  runProgressLabel;
    @FXML private Button syncTemplateBtn;
    @FXML private Button deleteRunBtn;
    @FXML private VBox   stepsContainer;

    // ── Services ──────────────────────────────────────────────────────

    private final WorkflowService workflowService = new WorkflowService();
    private final ProjectService  projectService  = new ProjectService();

    // ── State ─────────────────────────────────────────────────────────

    private WorkflowRun  selectedRun;
    private List<WorkflowRun> allRuns = new ArrayList<>();

    private static final String ALL_SCOPE     = "All";
    private static final String GLOBAL_SCOPE  = "Global";
    private static final String ALL_TEMPLATES = "All Templates";

    // ── Initialization ───────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupRunListView();
        loadScopeOptions();
        loadTemplateFilter();
        loadRuns();

        // Respond to project selection from sidebar
        ProjectContext.addListener(project -> Platform.runLater(() -> {
            if (project != null && scopeFilterCombo.getItems().contains(project)) {
                scopeFilterCombo.getSelectionModel().select(project);
            } else {
                scopeFilterCombo.getSelectionModel().select(ALL_SCOPE);
            }
        }));

        // Scope filter change
        scopeFilterCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> filterRuns());

        // Template filter change
        templateFilterCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> filterRuns());

        // Hide run detail until a run is selected
        clearRunDetail();
    }

    // ── Filter / Scope ───────────────────────────────────────────────

    private void loadScopeOptions() {
        try {
            AppConfig config = ConfigManager.load();
            List<String> items = new ArrayList<>();
            items.add(ALL_SCOPE);
            items.add(GLOBAL_SCOPE);
            projectService.listProjects(Paths.get(config.getWorkspace()))
                    .forEach(p -> items.add(p.getName()));

            String current = scopeFilterCombo.getValue();
            scopeFilterCombo.setItems(FXCollections.observableArrayList(items));
            if (current != null && items.contains(current)) {
                scopeFilterCombo.getSelectionModel().select(current);
            } else {
                scopeFilterCombo.getSelectionModel().select(ALL_SCOPE);
            }
        } catch (Exception e) {
            scopeFilterCombo.setItems(FXCollections.observableArrayList(ALL_SCOPE, GLOBAL_SCOPE));
            scopeFilterCombo.getSelectionModel().select(0);
        }
    }

    private void loadTemplateFilter() {
        try {
            List<String> items = new ArrayList<>();
            items.add(ALL_TEMPLATES);
            workflowService.listTemplates().forEach(t -> items.add(t.getName()));
            String current = templateFilterCombo.getValue();
            templateFilterCombo.setItems(FXCollections.observableArrayList(items));
            if (current != null && items.contains(current)) {
                templateFilterCombo.getSelectionModel().select(current);
            } else {
                templateFilterCombo.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            templateFilterCombo.setItems(FXCollections.observableArrayList(ALL_TEMPLATES));
            templateFilterCombo.getSelectionModel().select(0);
        }
    }

    // ── Data Loading ─────────────────────────────────────────────────

    private void loadRuns() {
        try {
            allRuns = workflowService.listAllRuns();
        } catch (Exception e) {
            allRuns = new ArrayList<>();
        }
        filterRuns();
        loadTemplateFilter();
    }

    private void filterRuns() {
        String scope    = scopeFilterCombo.getValue();
        String tplName  = templateFilterCombo.getValue();

        List<WorkflowRun> filtered = allRuns.stream()
                .filter(r -> {
                    if (scope == null || scope.equals(ALL_SCOPE)) return true;
                    if (scope.equals(GLOBAL_SCOPE)) return r.getProjectId() == null;
                    return scope.equals(r.getProjectId());
                })
                .filter(r -> {
                    if (tplName == null || tplName.equals(ALL_TEMPLATES)) return true;
                    if (r.getTemplateId() == null) return false;
                    // Match template by name
                    return workflowService.loadTemplate(r.getTemplateId())
                            .map(t -> t.getName().equals(tplName))
                            .orElse(false);
                })
                .collect(Collectors.toList());

        runListView.setItems(FXCollections.observableArrayList(filtered));

        // Re-select previously selected run if still present
        if (selectedRun != null) {
            filtered.stream()
                    .filter(r -> r.getId().equals(selectedRun.getId()))
                    .findFirst()
                    .ifPresentOrElse(
                            r -> runListView.getSelectionModel().select(r),
                            this::clearRunDetail
                    );
        }
    }

    // ── ListView setup ────────────────────────────────────────────────

    private void setupRunListView() {
        runListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(WorkflowRun run, boolean empty) {
                super.updateItem(run, empty);
                if (empty || run == null) {
                    setGraphic(null);
                    return;
                }
                VBox cell = new VBox(3);
                cell.setPadding(new Insets(6, 8, 6, 8));

                Label name = new Label(run.getName());
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                name.setWrapText(true);

                HBox meta = new HBox(8);
                meta.setAlignment(Pos.CENTER_LEFT);

                Label statusLbl = new Label(statusText(run.getStatus()));
                statusLbl.setStyle(statusStyle(run.getStatus()) + " -fx-font-size: 10px;");

                Label projLbl = new Label(run.getProjectId() != null ? run.getProjectId() : "global");
                projLbl.setStyle("-fx-text-fill: #787c99; -fx-font-size: 10px;");

                int done  = run.getDoneStepCount();
                int total = run.getSteps().size();
                Label progress = new Label(done + "/" + total + " steps");
                progress.setStyle("-fx-text-fill: #787c99; -fx-font-size: 10px;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                meta.getChildren().addAll(statusLbl, projLbl, spacer, progress);

                cell.getChildren().addAll(name, meta);
                setGraphic(cell);
            }
        });

        runListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, run) -> {
                    if (run != null) showRunDetail(run);
                    else clearRunDetail();
                });
    }

    // ── Run Detail ────────────────────────────────────────────────────

    private void showRunDetail(WorkflowRun run) {
        selectedRun = run;

        runNameLabel.setText(run.getName());
        runStatusLabel.setText(statusText(run.getStatus()));
        runStatusLabel.setStyle(statusStyle(run.getStatus()));

        String meta = (run.getProjectId() != null ? "Project: " + run.getProjectId() : "Global")
                + "   Created: " + (run.getCreatedAt() != null ? run.getCreatedAt() : "—");
        runMetaLabel.setText(meta);

        int done  = run.getDoneStepCount();
        int total = run.getActiveStepCount();
        runProgressLabel.setText("Progress: " + done + "/" + total + " steps");

        deleteRunBtn.setVisible(true);
        // Show Sync button only for template-based runs
        syncTemplateBtn.setVisible(run.getTemplateId() != null);

        buildStepRows(run);
    }

    private void clearRunDetail() {
        selectedRun = null;
        runNameLabel.setText("Select a run from the list");
        runStatusLabel.setText("");
        runMetaLabel.setText("");
        runProgressLabel.setText("");
        syncTemplateBtn.setVisible(false);
        deleteRunBtn.setVisible(false);
        stepsContainer.getChildren().clear();

        Label hint = new Label("Select a workflow run from the left panel to view its steps.");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 30; -fx-font-size: 13px;");
        stepsContainer.getChildren().add(hint);
    }

    // ── Step Rows ─────────────────────────────────────────────────────

    private void buildStepRows(WorkflowRun run) {
        stepsContainer.getChildren().clear();

        if (run.getSteps().isEmpty()) {
            Label empty = new Label("This run has no steps yet.");
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

            Button addFirstStep = new Button("+ Add Step");
            addFirstStep.setStyle("-fx-font-size: 12px;");
            addFirstStep.setOnAction(e -> showAddRunStepDialog(run));

            VBox emptyBox = new VBox(10, empty, addFirstStep);
            emptyBox.setStyle("-fx-padding: 20;");
            stepsContainer.getChildren().add(emptyBox);
            return;
        }

        for (int i = 0; i < run.getSteps().size(); i++) {
            stepsContainer.getChildren().add(buildStepRow(run, i));
        }

        // Always allow adding more steps
        Button addStepBtn = new Button("+ Add Step");
        addStepBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6 14;");
        addStepBtn.setOnAction(e -> showAddRunStepDialog(run));
        VBox addRow = new VBox(addStepBtn);
        addRow.setStyle("-fx-padding: 8 0 0 0;");
        stepsContainer.getChildren().add(addRow);
    }

    private Node buildStepRow(WorkflowRun run, int index) {
        WorkflowRun.RunStep step = run.getSteps().get(index);
        StepStatus status = step.getStatus();

        VBox card = new VBox(0);
        card.getStyleClass().addAll("workflow-step-row",
                status == StepStatus.DONE    ? "workflow-step-done"    :
                status == StepStatus.SKIPPED ? "workflow-step-skipped" : "workflow-step-todo");

        // ── Header row ────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(7, 12, 7, 12));

        // Step number badge
        Label numBadge = new Label(String.valueOf(index + 1));
        numBadge.getStyleClass().add("step-number-badge");
        numBadge.setMinWidth(24);
        numBadge.setMinHeight(24);
        numBadge.setAlignment(Pos.CENTER);

        // Status symbol
        Label statusIcon = new Label(stepSymbol(status));
        statusIcon.getStyleClass().add(statusIconClass(status));
        statusIcon.setMinWidth(18);

        // Title
        Label title = new Label(step.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        title.getStyleClass().add("step-title-text");
        if (status == StepStatus.SKIPPED) {
            title.setStyle(title.getStyle() + " -fx-opacity: 0.5;");
        }
        HBox.setHgrow(title, Priority.ALWAYS);
        title.setWrapText(true);

        Region spacer = new Region();
        spacer.setMinWidth(12);

        // Action buttons
        Button doneBtn = new Button("✓ Done");
        doneBtn.setStyle("-fx-font-size: 11px;");
        doneBtn.setDisable(status == StepStatus.DONE);
        doneBtn.setOnAction(e -> {
            workflowService.updateStepStatus(run.getId(), index, StepStatus.DONE);
            refreshRunDetail();
        });

        Button skipBtn = new Button("→ Skip");
        skipBtn.setStyle("-fx-font-size: 11px;");
        skipBtn.setDisable(status == StepStatus.SKIPPED || status == StepStatus.DONE);
        skipBtn.setOnAction(e -> {
            workflowService.updateStepStatus(run.getId(), index, StepStatus.SKIPPED);
            refreshRunDetail();
        });

        WorkflowRun.RunStep stepForBtn = run.getSteps().get(index);
        boolean hasNote = stepForBtn.getNotes() != null && !stepForBtn.getNotes().isBlank();
        Button noteBtn = new Button(hasNote ? "✎ Note" : "+ Note");
        noteBtn.setStyle("-fx-font-size: 11px;");
        noteBtn.setOnAction(e -> showNoteDialog(run, index));

        // Move up / down / delete — available for all runs
        Button upBtn = new Button("↑");
        upBtn.setStyle("-fx-font-size: 11px;");
        upBtn.setDisable(index == 0);
        upBtn.setOnAction(e -> {
            workflowService.moveRunStep(run.getId(), index, true);
            refreshRunDetail();
        });

        Button downBtn = new Button("↓");
        downBtn.setStyle("-fx-font-size: 11px;");
        downBtn.setDisable(index == run.getSteps().size() - 1);
        downBtn.setOnAction(e -> {
            workflowService.moveRunStep(run.getId(), index, false);
            refreshRunDetail();
        });

        Button delBtn = new Button("×");
        delBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #e53e3e;");
        delBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete step " + (index + 1) + ": \"" + step.getTitle() + "\"?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Delete Step");
            confirm.showAndWait().ifPresent(b -> {
                if (b == ButtonType.YES) {
                    workflowService.deleteRunStep(run.getId(), index);
                    refreshRunDetail();
                }
            });
        });

        header.getChildren().addAll(numBadge, statusIcon, title, spacer, doneBtn, skipBtn, noteBtn, upBtn, downBtn, delBtn);

        card.getChildren().add(header);

        // ── Expandable detail ─────────────────────────────────────────
        VBox detail = buildStepDetail(run, index);
        if (detail != null) {
            Separator sep = new Separator();
            sep.setStyle("-fx-opacity: 0.3;");
            card.getChildren().addAll(sep, detail);
        }

        return card;
    }

    private VBox buildStepDetail(WorkflowRun run, int index) {
        WorkflowRun.RunStep step = run.getSteps().get(index);

        boolean hasContent = (step.getDescription() != null && !step.getDescription().isBlank())
                || (step.getNotes() != null && !step.getNotes().isBlank())
                || (step.getExpectedResult() != null)
                || (step.getActualResult() != null)
                || !step.getCodeBlocks().isEmpty()
                || step.getActualCommand() != null
                || !step.getSubSteps().isEmpty();

        if (!hasContent) return null;

        VBox detail = new VBox(4);
        detail.setPadding(new Insets(4, 10, 8, 40));
        detail.setStyle("-fx-opacity: " + (step.getStatus() == StepStatus.SKIPPED ? "0.5" : "1.0") + ";");

        // Guidance — inline for single-line, compact multi-line for longer text
        if (step.getDescription() != null && !step.getDescription().isBlank()) {
            Label guidanceLbl = new Label("Guidance:");
            guidanceLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #7c3aed; -fx-min-width: 65;");
            if (!step.getDescription().contains("\n")) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                TextField tf = selectableTextField(step.getDescription(),
                        "-fx-font-size: 11px; -fx-text-fill: #6d28d9; -fx-font-style: italic;");
                HBox.setHgrow(tf, Priority.ALWAYS);
                row.getChildren().addAll(guidanceLbl, tf);
                detail.getChildren().add(row);
            } else {
                TextArea ta = selectableTextArea(step.getDescription(),
                        "-fx-font-size: 11px; -fx-text-fill: #6d28d9; -fx-font-style: italic;");
                detail.getChildren().addAll(guidanceLbl, ta);
            }
        }

        // Notes — user observations, selectable text
        if (step.getNotes() != null && !step.getNotes().isBlank()) {
            TextArea notes = selectableTextArea(step.getNotes(),
                    "-fx-font-size: 12px; -fx-text-fill: #a9b1d6;");
            detail.getChildren().add(notes);
        }

        // Expected result — selectable single-line
        if (step.getExpectedResult() != null) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label("Expected:");
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #787c99;");
            TextField val = selectableTextField(step.getExpectedResult(),
                    "-fx-font-size: 12px; -fx-text-fill: #787c99;");
            HBox.setHgrow(val, Priority.ALWAYS);
            row.getChildren().addAll(lbl, val);
            detail.getChildren().add(row);
        }

        // Actual result — selectable single-line
        if (step.getActualResult() != null) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label("Actual:");
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #73daca;");
            TextField val = selectableTextField(step.getActualResult(),
                    "-fx-font-size: 12px; -fx-text-fill: #73daca;");
            HBox.setHgrow(val, Priority.ALWAYS);
            row.getChildren().addAll(lbl, val);
            detail.getChildren().add(row);
        }

        // ── Template commands + Copy/Set Command actions ──────────────
        List<String> codeBlocks = step.getCodeBlocks();
        if (!codeBlocks.isEmpty()) {
            // Show all template command blocks — selectable
            for (String block : codeBlocks) {
                TextArea code = selectableCodeArea(block,
                        "-fx-text-fill: #7dcfff; -fx-control-inner-background: #0d1520;");
                detail.getChildren().add(code);
            }

            // Action row: Copy (smart) + Set Command + "Actual Command:" header if set
            HBox actionRow = new HBox(6);
            actionRow.setAlignment(Pos.CENTER_LEFT);

            Button copyBtn = new Button("📋 Copy");
            copyBtn.setStyle("-fx-font-size: 10px;");
            copyBtn.setTooltip(new Tooltip("Copy actual command (or template command if not set)"));
            copyBtn.setOnAction(e -> {
                String toCopy = step.getActualCommand() != null
                        ? step.getActualCommand()
                        : codeBlocks.get(0);
                ClipboardContent content = new ClipboardContent();
                content.putString(toCopy);
                Clipboard.getSystemClipboard().setContent(content);
            });

            Button setCmdBtn = new Button("✏ Set Command");
            setCmdBtn.setStyle("-fx-font-size: 10px;");
            setCmdBtn.setTooltip(new Tooltip("Fill in placeholder values and record the actual command used"));
            setCmdBtn.setOnAction(e -> showSetCommandDialog(run, index, codeBlocks.get(0)));

            actionRow.getChildren().addAll(copyBtn, setCmdBtn);

            if (step.getActualCommand() != null) {
                Label hint = new Label("▼ Actual Command:");
                hint.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #73daca;");
                actionRow.getChildren().add(hint);
            }

            detail.getChildren().add(actionRow);

            // Actual command block — teal background, selectable
            if (step.getActualCommand() != null) {
                TextArea actualCode = selectableCodeArea(step.getActualCommand(),
                        "-fx-text-fill: #c0caf5; -fx-control-inner-background: #0e2233; "
                        + "-fx-border-color: #73daca; -fx-border-width: 1;");
                detail.getChildren().add(actualCode);
            }
        }

        if (!step.getSubSteps().isEmpty()) {
            VBox subStepsBox = new VBox(3);
            for (WorkflowRun.SubStep ss : step.getSubSteps()) {
                Label line = new Label((ss.isDone() ? "✓ " : "○ ") + ss.getTitle());
                line.setStyle("-fx-font-size: 12px; -fx-text-fill: "
                        + (ss.isDone() ? "#73daca" : "#787c99") + ";");
                subStepsBox.getChildren().add(line);
            }
            detail.getChildren().add(subStepsBox);
        }

        return detail;
    }

    /**
     * Read-only TextArea that looks like a label (transparent background, no border).
     * Auto-sizes height to content. Text is selectable and copyable.
     */
    private static TextArea selectableTextArea(String text, String extraStyle) {
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        int rows = (int) text.chars().filter(c -> c == '\n').count() + 1;
        ta.setPrefRowCount(Math.min(rows, 4));
        ta.setStyle(extraStyle
                + " -fx-background-color: transparent;"
                + " -fx-control-inner-background: transparent;"
                + " -fx-border-color: transparent;"
                + " -fx-focus-color: transparent;"
                + " -fx-faint-focus-color: transparent;");
        ta.setMaxWidth(Double.MAX_VALUE);
        return ta;
    }

    /**
     * Read-only TextField that looks like a label (transparent background, no border).
     * Text is selectable and copyable.
     */
    private static TextField selectableTextField(String text, String extraStyle) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.setStyle(extraStyle
                + " -fx-background-color: transparent;"
                + " -fx-border-color: transparent;"
                + " -fx-focus-color: transparent;"
                + " -fx-faint-focus-color: transparent;");
        return tf;
    }

    /**
     * Read-only TextArea styled as a code block (monospace font, dark background).
     * Text is selectable and copyable.
     */
    private static TextArea selectableCodeArea(String text, String extraStyle) {
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        int rows = (int) text.chars().filter(c -> c == '\n').count() + 1;
        ta.setPrefRowCount(Math.min(rows, 5));
        ta.setStyle("-fx-font-family: 'Consolas','Courier New',monospace;"
                + " -fx-font-size: 11px;"
                + " -fx-background-radius: 4;"
                + " -fx-border-radius: 4;"
                + " -fx-focus-color: transparent;"
                + " -fx-faint-focus-color: transparent;"
                + " " + extraStyle);
        ta.setMaxWidth(Double.MAX_VALUE);
        return ta;
    }

    private void refreshRunDetail() {
        if (selectedRun == null) return;
        // Reload the run from disk to get fresh state
        workflowService.loadRun(selectedRun.getId()).ifPresent(fresh -> {
            selectedRun = fresh;
            // Update list item in place
            int idx = runListView.getItems().indexOf(fresh);
            if (idx >= 0) {
                runListView.getItems().set(idx, fresh);
                runListView.getSelectionModel().select(idx);
            }
            showRunDetail(fresh);
        });
    }

    // ── Dialogs ────────────────────────────────────────────────────────

    @FXML
    public void handleNewRun() {
        List<WorkflowTemplate> templates;
        try { templates = workflowService.listTemplates(); }
        catch (Exception e) { templates = List.of(); }
        final List<WorkflowTemplate> templateList = templates;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Workflow Run");
        ButtonType saveBtnType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        applyGridColumns(grid);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Explore workctl — 2026-02-27");
        nameField.setPrefWidth(280);

        ComboBox<String> templateCombo = new ComboBox<>();
        templateCombo.getItems().add("(none — blank run)");
        templateList.forEach(t -> templateCombo.getItems().add(t.getName()));
        templateCombo.getSelectionModel().select(0);

        ComboBox<String> projectCombo = new ComboBox<>();
        projectCombo.getItems().add("(global — not project-scoped)");
        try {
            AppConfig config = ConfigManager.load();
            projectService.listProjects(Paths.get(config.getWorkspace()))
                    .forEach(p -> projectCombo.getItems().add(p.getName()));
        } catch (Exception ignored) {}
        // Pre-select current project if one is active
        String currentProject = ProjectContext.getCurrentProject();
        if (currentProject != null && projectCombo.getItems().contains(currentProject)) {
            projectCombo.getSelectionModel().select(currentProject);
        } else {
            projectCombo.getSelectionModel().select(0);
        }

        grid.add(new Label("Run name *"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("From template"), 0, 1);
        grid.add(templateCombo, 1, 1);
        grid.add(new Label("Project scope"), 0, 2);
        grid.add(projectCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
        saveBtn.setDisable(true);
        nameField.textProperty().addListener((o, old, n) -> saveBtn.setDisable(n.trim().isEmpty()));
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            try {
                String name = nameField.getText().trim();
                String templateSel = templateCombo.getValue();
                String templateId  = null;
                if (templateSel != null && !templateSel.startsWith("(none")) {
                    templateId = templateList.stream()
                            .filter(t -> t.getName().equals(templateSel))
                            .map(WorkflowTemplate::getId)
                            .findFirst().orElse(null);
                }
                String projectSel = projectCombo.getValue();
                String projectId  = (projectSel != null && !projectSel.startsWith("(global")) ? projectSel : null;

                WorkflowRun run = workflowService.createRun(name, templateId, projectId);
                loadRuns();
                // Select the new run
                runListView.getItems().stream()
                        .filter(r -> r.getId().equals(run.getId()))
                        .findFirst()
                        .ifPresent(r -> {
                            runListView.getSelectionModel().select(r);
                            runListView.scrollTo(r);
                        });
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to create run: " + e.getMessage()).showAndWait();
            }
        });
    }

    @FXML
    public void handleSyncTemplate() {
        if (selectedRun == null || selectedRun.getTemplateId() == null) return;

        // Resolve template name for the confirmation message
        String tplName = workflowService.loadTemplate(selectedRun.getTemplateId())
                .map(WorkflowTemplate::getName)
                .orElse("the template");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Sync from Template");
        confirm.setHeaderText("Update run from \"" + tplName + "\"?");
        confirm.setContentText(
                "This will update step titles, expected results, and commands to match the latest template.\n\n"
                + "Your progress (status, notes, actual results, actual commands) will NOT be changed.\n"
                + "New template steps will be appended to this run.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                boolean ok = workflowService.syncRunFromTemplate(selectedRun.getId());
                if (ok) {
                    refreshRunDetail();
                } else {
                    new Alert(Alert.AlertType.ERROR,
                            "Sync failed: template not found or run has no template link.")
                            .showAndWait();
                }
            }
        });
    }

    @FXML
    public void handleDeleteRun() {
        if (selectedRun == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete run \"" + selectedRun.getName() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Delete Workflow Run");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                workflowService.deleteRun(selectedRun.getId());
                selectedRun = null;
                loadRuns();
                clearRunDetail();
            }
        });
    }

    @FXML
    public void handleManageTemplates() {
        showTemplatesDialog();
    }

    private void showTemplatesDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Workflow Templates");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(620);

        VBox root = new VBox(10);
        root.setPadding(new Insets(16));

        // Header + New Template button
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Workflow Templates");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button newTplBtn = new Button("+ New Template");
        newTplBtn.setOnAction(e -> {
            dialog.close();
            Platform.runLater(this::showNewTemplateDialog);
        });
        topBar.getChildren().addAll(title, spacer, newTplBtn);
        root.getChildren().add(topBar);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(380);
        VBox templateList = new VBox(8);
        templateList.setPadding(new Insets(4));

        List<WorkflowTemplate> templates;
        try { templates = workflowService.listTemplates(); }
        catch (Exception ex) { templates = List.of(); }

        if (templates.isEmpty()) {
            Label empty = new Label("No templates yet. Create one to define reusable procedures.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 20;");
            templateList.getChildren().add(empty);
        } else {
            for (WorkflowTemplate t : templates) {
                templateList.getChildren().add(buildTemplateCard(t, dialog));
            }
        }

        scroll.setContent(templateList);
        root.getChildren().add(scroll);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private Node buildTemplateCard(WorkflowTemplate template, Dialog<?> parentDialog) {
        VBox card = new VBox(6);
        card.getStyleClass().add("task-card");
        card.setPadding(new Insets(10));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(template.getName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        HBox.setHgrow(name, Priority.ALWAYS);

        Label steps = new Label(template.getSteps().size() + " steps");
        steps.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        Button viewBtn = new Button("Steps");
        viewBtn.setStyle("-fx-font-size: 11px;");
        viewBtn.setOnAction(e -> showTemplateStepsDialog(template));

        Button addStepBtn = new Button("+ Step");
        addStepBtn.setStyle("-fx-font-size: 11px;");
        addStepBtn.setOnAction(e -> {
            parentDialog.close();
            Platform.runLater(() -> showAddTemplateStepDialog(template));
        });

        Button delBtn = new Button("Delete");
        delBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c;");
        delBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete template \"" + template.getName() + "\"?",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(b -> {
                if (b == ButtonType.YES) {
                    workflowService.deleteTemplate(template.getId());
                    loadTemplateFilter();
                    parentDialog.close();
                    showTemplatesDialog();
                }
            });
        });

        header.getChildren().addAll(name, steps, viewBtn, addStepBtn, delBtn);
        card.getChildren().add(header);

        if (template.getDescription() != null) {
            Label desc = new Label(template.getDescription());
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
            card.getChildren().add(desc);
        }

        return card;
    }

    private void showTemplateStepsDialog(WorkflowTemplate templateArg) {
        // Reload template from disk so we always show the latest state
        final WorkflowTemplate[] tplHolder = {
            workflowService.loadTemplate(templateArg.getId()).orElse(templateArg)
        };

        Dialog<Void> d = new Dialog<>();
        d.setTitle("Template: " + tplHolder[0].getName());
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setPrefWidth(560);

        VBox root = new VBox(8);
        root.setPadding(new Insets(16));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        d.getDialogPane().setContent(scroll);

        // Rebuild the step list inside root — called after every edit/delete/reorder
        Runnable refresh = () -> {
            WorkflowTemplate tpl = workflowService.loadTemplate(tplHolder[0].getId())
                    .orElse(tplHolder[0]);
            tplHolder[0] = tpl;
            root.getChildren().clear();

            Label header = new Label("Steps in \"" + tpl.getName() + "\":");
            header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
            root.getChildren().add(header);

            if (tpl.getSteps().isEmpty()) {
                Label empty = new Label("No steps defined yet. Use '+ Step' in the templates list to add one.");
                empty.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 10;");
                root.getChildren().add(empty);
                return;
            }

            for (int i = 0; i < tpl.getSteps().size(); i++) {
                final int idx = i;
                WorkflowTemplate.TemplateStep s = tpl.getSteps().get(i);

                VBox row = new VBox(4);
                row.getStyleClass().add("workflow-step-row");
                row.setPadding(new Insets(8, 10, 8, 10));

                // ── Step header: number + title + action buttons ──────
                HBox stepHeader = new HBox(8);
                stepHeader.setAlignment(Pos.CENTER_LEFT);

                Label numLbl = new Label(String.valueOf(i + 1));
                numLbl.getStyleClass().add("step-number-badge");
                numLbl.setMinWidth(24); numLbl.setMinHeight(24);
                numLbl.setAlignment(Pos.CENTER);

                Label titleLbl = new Label(s.getTitle());
                titleLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
                HBox.setHgrow(titleLbl, Priority.ALWAYS);
                titleLbl.setWrapText(true);

                Region sp = new Region(); sp.setMinWidth(8);

                Button upBtn = new Button("↑");
                upBtn.setStyle("-fx-font-size: 11px;");
                upBtn.setDisable(i == 0);
                upBtn.setOnAction(e -> {
                    workflowService.moveTemplateStep(tpl.getId(), idx, true);
                    root.getParent(); // force layout
                    d.getDialogPane().requestLayout();
                    Platform.runLater(() -> root.getChildren().stream()
                            .findFirst().ifPresent(n -> ((Runnable) root.getUserData()).run()));
                });

                Button downBtn = new Button("↓");
                downBtn.setStyle("-fx-font-size: 11px;");
                downBtn.setDisable(i == tpl.getSteps().size() - 1);
                downBtn.setOnAction(e -> {
                    workflowService.moveTemplateStep(tpl.getId(), idx, false);
                    Platform.runLater(() -> ((Runnable) root.getUserData()).run());
                });

                Button editBtn = new Button("Edit");
                editBtn.setStyle("-fx-font-size: 11px;");
                editBtn.setOnAction(e -> {
                    d.close();
                    Platform.runLater(() -> showEditTemplateStepDialog(tplHolder[0], idx));
                });

                Button delBtn = new Button("×");
                delBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #e53e3e;");
                delBtn.setOnAction(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Delete step " + (idx + 1) + ": \"" + s.getTitle() + "\"?",
                            ButtonType.YES, ButtonType.NO);
                    confirm.showAndWait().ifPresent(b -> {
                        if (b == ButtonType.YES) {
                            workflowService.deleteTemplateStep(tpl.getId(), idx);
                            ((Runnable) root.getUserData()).run();
                        }
                    });
                });

                stepHeader.getChildren().addAll(numLbl, titleLbl, sp, upBtn, downBtn, editBtn, delBtn);
                row.getChildren().add(stepHeader);

                // ── Step detail: description, expected, commands ───────
                if (s.getDescription() != null) {
                    Label desc = new Label(s.getDescription());
                    desc.setWrapText(true);
                    desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-padding: 2 0 0 32;");
                    row.getChildren().add(desc);
                }
                if (s.getExpectedResult() != null) {
                    Label exp = new Label("Expected: " + s.getExpectedResult());
                    exp.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 0 0 0 32;");
                    exp.setWrapText(true);
                    row.getChildren().add(exp);
                }
                for (String block : s.getCodeBlocks()) {
                    Label code = new Label(block);
                    code.setWrapText(true);
                    code.setMaxWidth(Double.MAX_VALUE);
                    code.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; "
                            + "-fx-font-size: 11px; -fx-text-fill: #10b981; "
                            + "-fx-background-color: #1e293b; -fx-padding: 8; -fx-background-radius: 4;");
                    row.getChildren().add(code);
                }

                root.getChildren().add(row);
            }
        };

        // Store refresh callback in root's userData so buttons can call it
        root.setUserData(refresh);
        refresh.run();
        d.showAndWait();
    }

    private void showNewTemplateDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Workflow Template");
        ButtonType saveBtnType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        applyGridColumns(grid);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Explore an Existing Codebase");
        nameField.setPrefWidth(280);

        TextArea descField = new TextArea();
        descField.setPromptText("Optional description...");
        descField.setPrefRowCount(3);

        TextField tagsField = new TextField();
        tagsField.setPromptText("e.g. dev,release,onboarding");

        grid.add(new Label("Name *"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Tags"), 0, 2);
        grid.add(tagsField, 1, 2);

        dialog.getDialogPane().setPrefWidth(480);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> {
            Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
            if (saveBtn != null) {
                saveBtn.setDisable(true);
                nameField.textProperty().addListener((o, old, n) -> saveBtn.setDisable(n.trim().isEmpty()));
            }
            nameField.requestFocus();
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            try {
                String tags = tagsField.getText().trim();
                List<String> tagList = tags.isBlank() ? List.of()
                        : List.of(tags.split(","));
                workflowService.createTemplate(
                        nameField.getText().trim(),
                        descField.getText().trim().isEmpty() ? null : descField.getText().trim(),
                        tagList);
                loadTemplateFilter();
                showTemplatesDialog();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to create template: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void showAddTemplateStepDialog(WorkflowTemplate template) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Step to: " + template.getName());
        ButtonType saveBtnType = new ButtonType("Add Step", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        applyGridColumns(grid);

        TextField titleField = new TextField();
        titleField.setPromptText("Step title");
        titleField.setPrefWidth(280);

        TextArea descField = new TextArea();
        descField.setPromptText("Guidance / description for this step...");
        descField.setPrefRowCount(2);

        TextField expectedField = new TextField();
        expectedField.setPromptText("What you expect to see / find");

        TextArea commandsField = new TextArea();
        commandsField.setPromptText("Shell commands for this step (optional)\ne.g.  docker build -t myapp:latest .\n      docker push registry/myapp:v1.2.0");
        commandsField.setPrefRowCount(4);
        commandsField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");

        grid.add(new Label("Title *"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Expected result"), 0, 2);
        grid.add(expectedField, 1, 2);
        grid.add(new Label("Commands"), 0, 3);
        grid.add(commandsField, 1, 3);

        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> {
            Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
            if (saveBtn != null) {
                saveBtn.setDisable(true);
                titleField.textProperty().addListener((o, old, n) -> saveBtn.setDisable(n.trim().isEmpty()));
            }
            titleField.requestFocus();
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            try {
                String cmds = commandsField.getText().trim();
                workflowService.addTemplateStep(
                        template.getId(),
                        titleField.getText().trim(),
                        descField.getText().trim().isEmpty() ? null : descField.getText().trim(),
                        expectedField.getText().trim().isEmpty() ? null : expectedField.getText().trim(),
                        cmds.isEmpty() ? null : cmds);
                loadTemplateFilter();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to add step: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void showEditTemplateStepDialog(WorkflowTemplate template, int stepIndex) {
        WorkflowTemplate.TemplateStep existing = template.getSteps().get(stepIndex);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Step " + (stepIndex + 1) + ": " + existing.getTitle());
        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        applyGridColumns(grid);

        TextField titleField = new TextField(existing.getTitle());
        titleField.setPrefWidth(280);

        TextArea descField = new TextArea(existing.getDescription() != null ? existing.getDescription() : "");
        descField.setPromptText("Guidance / description for this step...");
        descField.setPrefRowCount(2);

        TextField expectedField = new TextField(existing.getExpectedResult() != null ? existing.getExpectedResult() : "");
        expectedField.setPromptText("What you expect to see / find");

        // Pre-fill commands from first code block if present
        String existingCommands = existing.getCodeBlocks().isEmpty() ? "" : existing.getCodeBlocks().get(0);
        TextArea commandsField = new TextArea(existingCommands);
        commandsField.setPromptText("Shell commands for this step (optional)");
        commandsField.setPrefRowCount(4);
        commandsField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");

        grid.add(new Label("Title *"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Expected result"), 0, 2);
        grid.add(expectedField, 1, 2);
        grid.add(new Label("Commands"), 0, 3);
        grid.add(commandsField, 1, 3);

        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> {
            Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
            if (saveBtn != null) {
                saveBtn.setDisable(titleField.getText().trim().isEmpty());
                titleField.textProperty().addListener((o, old, n) -> saveBtn.setDisable(n.trim().isEmpty()));
            }
            titleField.requestFocus();
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            try {
                String cmds = commandsField.getText().trim();
                workflowService.editTemplateStep(
                        template.getId(),
                        stepIndex,
                        titleField.getText().trim(),
                        descField.getText().trim().isEmpty() ? null : descField.getText().trim(),
                        expectedField.getText().trim().isEmpty() ? null : expectedField.getText().trim(),
                        cmds.isEmpty() ? null : cmds);
                loadTemplateFilter();
                // Reopen the steps dialog for this template with fresh data
                workflowService.loadTemplate(template.getId()).ifPresent(this::showTemplateStepsDialog);
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to save step: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void showAddRunStepDialog(WorkflowRun run) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Step");
        ButtonType saveBtnType = new ButtonType("Add Step", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        applyGridColumns(grid);

        TextField titleField = new TextField();
        titleField.setPromptText("Step title");
        titleField.setPrefWidth(280);

        TextField expectedField = new TextField();
        expectedField.setPromptText("What you expect to see / find (optional)");

        TextArea commandsField = new TextArea();
        commandsField.setPromptText("Shell commands for this step (optional)\ne.g.  docker build -t myapp:latest .\n      docker push registry/myapp:v1.2.0");
        commandsField.setPrefRowCount(4);
        commandsField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");

        grid.add(new Label("Title *"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Expected result"), 0, 1);
        grid.add(expectedField, 1, 1);
        grid.add(new Label("Commands"), 0, 2);
        grid.add(commandsField, 1, 2);

        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> {
            Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
            if (saveBtn != null) {
                saveBtn.setDisable(true);
                titleField.textProperty().addListener((o, old, n) -> saveBtn.setDisable(n.trim().isEmpty()));
            }
            titleField.requestFocus();
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            try {
                String expected = expectedField.getText().trim();
                String cmds = commandsField.getText().trim();
                workflowService.addRunStep(
                        run.getId(),
                        titleField.getText().trim(),
                        null,
                        expected.isEmpty() ? null : expected,
                        cmds.isEmpty() ? null : cmds);
                refreshRunDetail();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to add step: " + e.getMessage()).showAndWait();
            }
        });
    }

    private void showNoteDialog(WorkflowRun run, int stepIndex) {
        WorkflowRun.RunStep step = run.getSteps().get(stepIndex);
        boolean hasNote = step.getNotes() != null && !step.getNotes().isBlank();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle((hasNote ? "Edit" : "Add") + " Note — Step " + (stepIndex + 1));
        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(480);

        VBox content = new VBox(8);
        content.setPadding(new Insets(16));

        Label hdr = new Label("Step " + (stepIndex + 1) + ": " + step.getTitle());
        hdr.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        hdr.setWrapText(true);

        TextArea noteField = new TextArea(hasNote ? step.getNotes() : "");
        noteField.setPromptText("Enter your observations, findings, or notes for this step...");
        noteField.setPrefRowCount(7);
        noteField.setWrapText(true);

        content.getChildren().addAll(hdr, noteField);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(noteField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result != saveBtnType) return;
            String note = noteField.getText().trim();
            // Replaces the full notes content (handles both add and edit)
            workflowService.setStepNotes(run.getId(), stepIndex, note.isEmpty() ? null : note);
            refreshRunDetail();
        });
    }

    private void showSetCommandDialog(WorkflowRun run, int stepIndex, String templateCommand) {
        WorkflowRun.RunStep step = run.getSteps().get(stepIndex);
        String currentActual = step.getActualCommand();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Set Command — Step " + (stepIndex + 1) + ": " + step.getTitle());
        ButtonType saveBtnType  = new ButtonType("Save",  ButtonBar.ButtonData.OK_DONE);
        ButtonType clearBtnType = new ButtonType("Clear", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, clearBtnType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(500);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        // Template command shown as read-only reference
        Label tplHdr = new Label("Template command (reference):");
        tplHdr.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        Label tplCode = new Label(templateCommand);
        tplCode.setWrapText(true);
        tplCode.setMaxWidth(Double.MAX_VALUE);
        tplCode.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; "
                + "-fx-font-size: 11px; -fx-text-fill: #94a3b8; "
                + "-fx-background-color: #334155; -fx-padding: 8; -fx-background-radius: 4;");

        // Editable field pre-filled with actual command or template command
        Label actualHdr = new Label("Actual command (replace placeholders with real values):");
        actualHdr.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        actualHdr.setWrapText(true);

        TextArea actualField = new TextArea(currentActual != null ? currentActual : templateCommand);
        actualField.setPrefRowCount(5);
        actualField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 11px;");

        content.getChildren().addAll(tplHdr, tplCode, actualHdr, actualField);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(actualField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result == saveBtnType) {
                String cmd = actualField.getText().trim();
                workflowService.setStepActualCommand(run.getId(), stepIndex, cmd.isEmpty() ? null : cmd);
                refreshRunDetail();
            } else if (result == clearBtnType) {
                workflowService.setStepActualCommand(run.getId(), stepIndex, null);
                refreshRunDetail();
            }
        });
    }

    // ── Layout helpers ────────────────────────────────────────────────

    /** Pins label column to 130 px and lets the field column fill the rest. */
    private static void applyGridColumns(GridPane grid) {
        ColumnConstraints labelCol = new ColumnConstraints(130);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
    }

    // ── Style helpers ─────────────────────────────────────────────────

    private static String statusText(RunStatus s) {
        if (s == null) return "IN PROGRESS";
        return switch (s) {
            case IN_PROGRESS -> "IN PROGRESS";
            case COMPLETED   -> "COMPLETED";
            case ABANDONED   -> "ABANDONED";
        };
    }

    private static String statusStyle(RunStatus s) {
        if (s == null) return "-fx-text-fill: #e0af68;";
        return switch (s) {
            case IN_PROGRESS -> "-fx-text-fill: #e0af68;";   // gold
            case COMPLETED   -> "-fx-text-fill: #73daca;";   // teal
            case ABANDONED   -> "-fx-text-fill: #565f89;";   // muted purple
        };
    }

    private static String stepSymbol(StepStatus s) {
        return switch (s) {
            case DONE    -> "✓";
            case SKIPPED -> "–";
            case TODO    -> "○";
        };
    }

    private static String statusIconClass(StepStatus s) {
        return switch (s) {
            case DONE    -> "step-status-done";
            case SKIPPED -> "step-status-skipped";
            case TODO    -> "step-status-todo";
        };
    }
}
