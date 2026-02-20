package com.workctl.gui.controller;

import com.workctl.core.model.Task;
import com.workctl.core.model.Task.SubTask;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;
import com.workctl.gui.ProjectContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.scene.web.WebView;

public class TaskController {

    @FXML private VBox openColumn;
    @FXML private VBox inProgressColumn;
    @FXML private VBox doneColumn;

    @FXML private ScrollPane openScroll;
    @FXML private ScrollPane inProgressScroll;
    @FXML private ScrollPane doneScroll;

    @FXML private Label openCountLabel;
    @FXML private Label inProgressCountLabel;
    @FXML private Label doneCountLabel;

    // Search bar (wired from tasks.fxml)
    @FXML private TextField searchField;
    @FXML private Button    clearSearchBtn;

//    @FXML private TextArea taskInput;
//    @FXML private ComboBox<Integer> priorityComboBox;

    private final TaskService taskService = new TaskService();
    private String currentProject;

    // Current search query — empty string means "show all"
    private String searchQuery = "";

    @FXML
    public void initialize() {

        ProjectContext.addListener(this::setProject);

        ProjectContext.addFileChangeListener(() -> {
            if (currentProject != null) {
                Platform.runLater(this::refreshBoard);
            }
        });

        // IMPORTANT: Attach drop handlers to SCROLLPANES
        setupDropTarget(openScroll, TaskStatus.OPEN);
        setupDropTarget(inProgressScroll, TaskStatus.IN_PROGRESS);
        setupDropTarget(doneScroll, TaskStatus.DONE);

        // ── Search bar wiring ─────────────────────────────────────
        // Live-filter as user types; clear button resets
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            searchQuery = newVal == null ? "" : newVal.trim();
            refreshBoard();
        });

//        priorityComboBox.getItems().addAll(1, 2, 3);
//        priorityComboBox.setValue(2); // default medium priority
    }

    // ====================================================
    // SEARCH — FXML handler for the clear button
    // ====================================================

    @FXML
    private void handleClearSearch() {
        searchField.clear();   // listener fires → searchQuery = "" → refreshBoard()
        searchField.requestFocus();
    }

    // ====================================================
    // DROP TARGET FIX (ScrollPane based)
    // ====================================================

    private void setupDropTarget(Node dropTarget, TaskStatus status) {

        dropTarget.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        dropTarget.setOnDragDropped(event -> {

            Dragboard db = event.getDragboard();

            if (db.hasString()) {

                int taskId = Integer.parseInt(db.getString());

                taskService.updateStatus(
                        currentProject,
                        taskId,
                        status
                );

                refreshBoard();
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }

            event.consume();
        });
    }

    public void setProject(String projectName) {
        this.currentProject = projectName;
        // Reset search when switching projects
        if (searchField != null) searchField.clear();
        refreshBoard();
    }

    // ====================================================
    // ADD TASK — extended with Subtasks panel
    // ====================================================

    @FXML
    private void handleAddTask() {

        if (currentProject == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create New Task");

        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        // ---------- Editor ----------
        TextArea editor = new TextArea();
        editor.setWrapText(true);
        editor.setPrefRowCount(12);   // slightly shorter to make room for subtask panel
        editor.setPrefWidth(450);

        // ---------- Priority Dropdown ----------
        ComboBox<Integer> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(1, 2, 3);
        priorityBox.setValue(2);

        Label priorityLabel = new Label("Priority:");

        HBox priorityRow = new HBox(10, priorityLabel, priorityBox);

        // ---------- Subtask Panel ----------
        List<SubTask> pendingSubtasks = new ArrayList<>();

        VBox subtaskListBox = new VBox(4);

        TextField subtaskField = new TextField();
        subtaskField.setPromptText("Subtask title...");
        HBox.setHgrow(subtaskField, Priority.ALWAYS);

        Button addSubBtn = new Button("+ Add");
        addSubBtn.setStyle("""
            -fx-background-color: #3498db;
            -fx-text-fill: white;
            -fx-background-radius: 4;
            -fx-padding: 4 10;
            """);

        Runnable doAddSubtask = () -> {
            String t = subtaskField.getText().trim();
            if (!t.isBlank()) {
                pendingSubtasks.add(new SubTask(t, false));
                rebuildSubtaskListBox(subtaskListBox, pendingSubtasks);
                subtaskField.clear();
                subtaskField.requestFocus();
            }
        };
        addSubBtn.setOnAction(e -> doAddSubtask.run());
        subtaskField.setOnAction(e -> doAddSubtask.run());

        HBox subtaskInputRow = new HBox(6, subtaskField, addSubBtn);

        Label subtaskSectionLabel = new Label("Subtasks");
        subtaskSectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        VBox subtaskPanel = new VBox(6,
                new Separator(),
                subtaskSectionLabel,
                subtaskListBox,
                subtaskInputRow);
        subtaskPanel.setPadding(new Insets(8, 0, 0, 0));

        VBox leftPanel = new VBox(10, priorityRow, editor, subtaskPanel);
        VBox.setVgrow(editor, Priority.ALWAYS);

        // ---------- Preview ----------
        WebView preview = new WebView();
        preview.setPrefWidth(450);

        org.commonmark.parser.Parser parser =
                org.commonmark.parser.Parser.builder().build();

        org.commonmark.renderer.html.HtmlRenderer renderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().build();

        editor.textProperty().addListener((obs, oldText, newText) -> {

            String html = renderer.render(parser.parse(newText));

            preview.getEngine().loadContent("""
            <html>
            <body style="font-family: Arial; padding:10;">
            %s
            </body>
            </html>
            """.formatted(html));
        });

        HBox container = new HBox(15.0, leftPanel, preview);
        container.setPrefSize(950, 580);

        dialog.getDialogPane().setContent(container);

        javafx.application.Platform.runLater(editor::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {

            String text = editor.getText().trim();
            int priority = priorityBox.getValue();

            if (!text.isBlank()) {
                taskService.addTask(
                        currentProject,
                        text,
                        List.of(),
                        priority
                );

                // Attach pending subtasks to the newly created task
                if (!pendingSubtasks.isEmpty()) {
                    List<Task> all = taskService.getTasks(currentProject);
                    all.stream()
                            .max(Comparator.comparingInt(Task::getId))
                            .ifPresent(newest ->
                                    taskService.setSubtasks(currentProject,
                                            newest.getId(), pendingSubtasks));
                }

                refreshBoard();
            }
        }
    }


    // ====================================================
    // REFRESH BOARD
    // Filters tasks by searchQuery when non-empty.
    // An empty query shows all tasks (normal behaviour).
    // ====================================================
    private void refreshBoard() {

        if (currentProject == null) {
            openColumn.getChildren().clear();
            inProgressColumn.getChildren().clear();
            doneColumn.getChildren().clear();
            return;
        }

        List<Task> tasks = taskService.getTasks(currentProject);

        // ── Apply search filter ───────────────────────────────────
        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase();
            tasks = tasks.stream()
                    .filter(t -> matchesSearch(t, q))
                    .collect(Collectors.toList());
        }

        Comparator<Task> comparator =
                Comparator.comparingInt(Task::getPriority)  // P1 first
                        .thenComparing(Task::getId, Comparator.reverseOrder());

        Map<TaskStatus, List<Task>> grouped =
                tasks.stream()
                        .collect(Collectors.groupingBy(Task::getStatus));

        openColumn.getChildren().setAll(
                grouped.getOrDefault(TaskStatus.OPEN, List.of())
                        .stream()
                        .sorted(comparator)
                        .map(this::createTaskCard)
                        .toList());

        inProgressColumn.getChildren().setAll(
                grouped.getOrDefault(TaskStatus.IN_PROGRESS, List.of())
                        .stream()
                        .sorted(comparator)
                        .map(this::createTaskCard)
                        .toList());

        doneColumn.getChildren().setAll(
                grouped.getOrDefault(TaskStatus.DONE, List.of())
                        .stream()
                        .sorted(comparator)
                        .map(this::createTaskCard)
                        .toList());

        // Update column headers — show match count when filtering
        int openCount     = grouped.getOrDefault(TaskStatus.OPEN, List.of()).size();
        int progressCount = grouped.getOrDefault(TaskStatus.IN_PROGRESS, List.of()).size();
        int doneCount     = grouped.getOrDefault(TaskStatus.DONE, List.of()).size();

        if (searchQuery.isBlank()) {
            openCountLabel.setText("Open (" + openCount + ")");
            inProgressCountLabel.setText("In Progress (" + progressCount + ")");
            doneCountLabel.setText("Done (" + doneCount + ")");
        } else {
            // Show "Open (2 matches)" when filtering
            openCountLabel.setText("Open (" + openCount + " match" + (openCount == 1 ? "" : "es") + ")");
            inProgressCountLabel.setText("In Progress (" + progressCount + " match" + (progressCount == 1 ? "" : "es") + ")");
            doneCountLabel.setText("Done (" + doneCount + " match" + (doneCount == 1 ? "" : "es") + ")");
        }
    }

    /**
     * Returns true if the task matches the search query.
     * Checks: title, full description, tags, and subtask titles.
     */
    private boolean matchesSearch(Task task, String queryLower) {
        // Title / description
        if (task.getDescription().toLowerCase().contains(queryLower)) return true;
        // Tags
        if (task.getTags() != null) {
            for (String tag : task.getTags()) {
                if (tag.toLowerCase().contains(queryLower)) return true;
            }
        }
        // Subtask titles
        for (SubTask st : task.getSubtasks()) {
            if (st.getTitle().toLowerCase().contains(queryLower)) return true;
        }
        return false;
    }

    // ====================================================
    // TASK CARD — original structure + subtask progress bar
    // ====================================================

    private Node createTaskCard(Task task) {

        VBox card = new VBox();
        card.setSpacing(8);
        card.setPadding(new Insets(12));
        card.setStyle("""
        -fx-background-color: white;
        -fx-background-radius: 10;
        -fx-border-radius: 10;
        -fx-border-width: 2;
    """);

        // =========================
        // Priority Colors
        // =========================
        String borderColor;
        String badgeColor;

        switch (task.getPriority()) {
            case 1 -> {
                borderColor = "#e53935";   // Red
                badgeColor = "#e53935";
            }
            case 2 -> {
                borderColor = "#fb8c00";   // Orange
                badgeColor = "#fb8c00";
            }
            case 3 -> {
                borderColor = "#43a047";   // Green
                badgeColor = "#43a047";
            }
            default -> {
                borderColor = "#cccccc";
                badgeColor = "#999999";
            }
        }

        card.setStyle(card.getStyle() + "-fx-border-color: " + borderColor + ";");

        // =========================
        // ID + Priority Row (Stable)
        // =========================
        Label idLabel = new Label("#" + task.getId());
        idLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");

        Label priorityBadge = new Label("P" + task.getPriority());
        priorityBadge.setStyle("""
        -fx-background-color: %s;
        -fx-text-fill: white;
        -fx-padding: 3 8 3 8;
        -fx-background-radius: 14;
        -fx-font-size: 11;
        -fx-font-weight: bold;
    """.formatted(badgeColor));

        HBox metaRow = new HBox(6, idLabel, priorityBadge);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        // =========================
        // Title
        // =========================
        Label titleLabel = new Label(task.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");

        // =========================
        // Info Button
        // =========================
        Button infoBtn = new Button("i");
        infoBtn.setFocusTraversable(false);
        infoBtn.setStyle("""
        -fx-background-color: linear-gradient(to bottom, #42a5f5, #1e88e5);
        -fx-text-fill: white;
        -fx-font-weight: bold;
        -fx-background-radius: 20;
        -fx-min-width: 28;
        -fx-min-height: 28;
        -fx-max-width: 28;
        -fx-max-height: 28;
    """);

        infoBtn.setOnAction(e -> {
            e.consume();
            showTaskDetails(task);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox titleRow = new HBox(titleLabel, spacer, infoBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // =========================
        // Add rows to card
        // =========================
        card.getChildren().addAll(metaRow, titleRow);

        // =========================
        // Subtask progress bar
        // Shows only when the task has subtasks.
        // =========================
        if (task.hasSubtasks()) {
            int done  = task.getDoneSubtaskCount();
            int total = task.getTotalSubtaskCount();

            ProgressBar pb = new ProgressBar((double) done / total);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setPrefHeight(6);
            pb.setStyle(done == total
                    ? "-fx-accent: #27ae60;"    // all done → green
                    : "-fx-accent: #3498db;");  // partial  → blue

            Label subLabel = new Label(done + "/" + total + " subtasks");
            subLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 10;");

            HBox subRow = new HBox(8, pb, subLabel);
            HBox.setHgrow(pb, Priority.ALWAYS);
            subRow.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(subRow);
        }

        // =========================
        // Hover Effect — ORIGINAL UNCHANGED
        // =========================
        card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle() +
                        "-fx-background-color: #f5f7fa;"));

        card.setOnMouseExited(e ->
                card.setStyle(card.getStyle()
                        .replace("-fx-background-color: #f5f7fa;", "")));

        // =========================
        // Click Handling — ORIGINAL UNCHANGED
        // =========================
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                highlightCard(card);
            }
            if (e.getClickCount() == 2) {
                enableInlineEdit(card, task, titleLabel);
            }
        });

        // =========================
        // Context Menu — original items + subtask items
        // =========================
        ContextMenu menu = new ContextMenu();

        MenuItem moveOpen = new MenuItem("Move to Open");
        MenuItem moveProgress = new MenuItem("Move to In Progress");
        MenuItem moveDone = new MenuItem("Move to Done");

        moveOpen.setOnAction(e -> moveTask(task, TaskStatus.OPEN));
        moveProgress.setOnAction(e -> moveTask(task, TaskStatus.IN_PROGRESS));
        moveDone.setOnAction(e -> moveTask(task, TaskStatus.DONE));

        Menu priorityMenu = new Menu("Change Priority");

        MenuItem p1 = new MenuItem("P1 - High");
        MenuItem p2 = new MenuItem("P2 - Medium");
        MenuItem p3 = new MenuItem("P3 - Low");

        p1.setOnAction(e -> updatePriority(task, 1));
        p2.setOnAction(e -> updatePriority(task, 2));
        p3.setOnAction(e -> updatePriority(task, 3));

        priorityMenu.getItems().addAll(p1, p2, p3);

        MenuItem addSubtaskItem    = new MenuItem("➕  Add Subtask");
        MenuItem manageSubtaskItem = new MenuItem("📋  Manage Subtasks"
                + (task.hasSubtasks() ? " (" + task.getTotalSubtaskCount() + ")" : ""));

        addSubtaskItem.setOnAction(e    -> showQuickAddSubtaskDialog(task));
        manageSubtaskItem.setOnAction(e -> showManageSubtasksDialog(task));

        menu.getItems().addAll(
                moveOpen,
                moveProgress,
                moveDone,
                new SeparatorMenuItem(),
                priorityMenu,
                new SeparatorMenuItem(),
                addSubtaskItem,
                manageSubtaskItem
        );

        card.setOnContextMenuRequested(e ->
                menu.show(card, e.getScreenX(), e.getScreenY()));

        // =========================
        // Drag & Drop — ORIGINAL UNCHANGED
        // =========================
        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(task.getId()));
            db.setContent(content);
            event.consume();
        });

        return card;
    }

    // ====================================================
    // QUICK ADD SUBTASK (right-click → "➕ Add Subtask")
    // ====================================================

    private void showQuickAddSubtaskDialog(Task task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Subtask");
        dialog.setHeaderText("Task #" + task.getId() + " – " + task.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField inputField = new TextField();
        inputField.setPromptText("Subtask title...");
        inputField.setPrefWidth(340);

        VBox content = new VBox(10, new Label("Subtask title:"), inputField);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        // Disable OK until user types something
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        inputField.textProperty().addListener((obs, o, n) ->
                okBtn.setDisable(n.trim().isEmpty()));

        Platform.runLater(inputField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String t = inputField.getText().trim();
            if (!t.isBlank()) {
                taskService.addSubtask(currentProject, task.getId(), t);
                refreshBoard();
            }
        }
    }

    // ====================================================
    // MANAGE SUBTASKS (right-click → "📋 Manage Subtasks")
    // ====================================================

    private void showManageSubtasksDialog(Task task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Subtasks – #" + task.getId());
        dialog.setHeaderText(task.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        List<SubTask> workingList = new ArrayList<>(task.getSubtasks());
        VBox listBox = new VBox(4);
        rebuildSubtaskListBox(listBox, workingList);

        TextField inputField = new TextField();
        inputField.setPromptText("New subtask...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.setStyle("""
            -fx-background-color: #3498db;
            -fx-text-fill: white;
            -fx-background-radius: 4;
            -fx-padding: 4 10;
            """);

        Runnable doAdd = () -> {
            String t = inputField.getText().trim();
            if (!t.isBlank()) {
                workingList.add(new SubTask(t, false));
                rebuildSubtaskListBox(listBox, workingList);
                inputField.clear();
                inputField.requestFocus();
            }
        };
        addBtn.setOnAction(e -> doAdd.run());
        inputField.setOnAction(e -> doAdd.run());

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);

        HBox inputRow = new HBox(6, inputField, addBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, scroll, new Separator(), inputRow);
        content.setPadding(new Insets(16));
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(inputField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            taskService.setSubtasks(currentProject, task.getId(), workingList);
            refreshBoard();
        }
    }

    /**
     * Render the working subtask list into a VBox.
     * Each row: checkbox (toggles done in memory) + ✕ delete button.
     */
    private void rebuildSubtaskListBox(VBox box, List<SubTask> list) {
        box.getChildren().clear();
        if (list.isEmpty()) {
            Label empty = new Label("No subtasks yet.");
            empty.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11;");
            box.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            SubTask st    = list.get(i);

            CheckBox cb = new CheckBox(st.getTitle());
            cb.setSelected(st.isDone());
            cb.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(cb, Priority.ALWAYS);
            if (st.isDone()) cb.setStyle("-fx-text-fill: #aaa;");

            // Toggle done state in the working list (no disk write yet)
            cb.selectedProperty().addListener((obs, o, n) -> {
                list.get(idx).setDone(n);
                cb.setStyle(n ? "-fx-text-fill: #aaa;" : "");
            });

            Button delBtn = new Button("✕");
            delBtn.setStyle("""
                -fx-background-color: transparent;
                -fx-text-fill: #e74c3c;
                -fx-font-size: 11;
                -fx-padding: 2 6;
                -fx-cursor: hand;
                """);
            delBtn.setOnAction(e -> {
                list.remove(idx);
                rebuildSubtaskListBox(box, list);
            });

            HBox row = new HBox(6, cb, delBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(row);
        }
    }

    // ====================================================
    // HELPERS — ORIGINAL UNCHANGED
    // ====================================================

    private void updatePriority(Task task, int newPriority) {

        taskService.updatePriority(
                currentProject,
                task.getId(),
                newPriority
        );

        refreshBoard();
    }


    private void highlightCard(VBox card) {

        // Remove highlight from all cards
        openColumn.getChildren().forEach(n ->
                n.setStyle(n.getStyle().replace("-fx-border-color: #4a90e2;", "-fx-border-color: #dddddd;"))
        );

        inProgressColumn.getChildren().forEach(n ->
                n.setStyle(n.getStyle().replace("-fx-border-color: #4a90e2;", "-fx-border-color: #dddddd;"))
        );

        doneColumn.getChildren().forEach(n ->
                n.setStyle(n.getStyle().replace("-fx-border-color: #4a90e2;", "-fx-border-color: #dddddd;"))
        );

        // Highlight selected
        card.setStyle(card.getStyle() +
                "-fx-border-color: #4a90e2;");
    }

    private void enableInlineEdit(VBox card, Task task, Label oldLabel) {

        TextArea editor = new TextArea(task.getDescription());
        editor.setWrapText(true);
        editor.setPrefRowCount(3);

        card.getChildren().set(0, editor);
        editor.requestFocus();

        editor.setOnKeyPressed(event -> {

            switch (event.getCode()) {

                case ENTER -> {

                    // SHIFT + ENTER → New Line
                    if (event.isShiftDown()) {
                        editor.appendText("\n");
                        event.consume();
                        return;
                    }

                    // ENTER → Save
                    event.consume();

                    String newText = editor.getText().trim();
                    if (!newText.isBlank()) {
                        taskService.updateDescription(
                                currentProject,
                                task.getId(),
                                newText
                        );
                    }

                    refreshBoard();
                }

                case ESCAPE -> {
                    refreshBoard(); // Cancel edit
                }
            }
        });

        // Lose focus → auto save
        editor.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                String newText = editor.getText().trim();
                if (!newText.isBlank()) {
                    taskService.updateDescription(
                            currentProject,
                            task.getId(),
                            newText
                    );
                }
                refreshBoard();
            }
        });
    }

    private void moveTask(Task task, TaskStatus newStatus) {

        taskService.updateStatus(
                currentProject,
                task.getId(),
                newStatus
        );

        refreshBoard();
    }

    // ====================================================
    // DETAILS POPUP
    // FIX: Markdown preview now includes subtasks as a
    //      checklist and is selectable/copyable.
    // ====================================================

    private void showTaskDetails(Task task) {

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Task Details");

        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType updateBtn = new ButtonType("Update Task");
        ButtonType deleteBtn = new ButtonType("Delete Task", ButtonBar.ButtonData.LEFT);

        dialog.getDialogPane().getButtonTypes().addAll(deleteBtn, updateBtn, closeBtn);

        // =========================
        // LEFT PANEL (Metadata) — original
        // =========================
        VBox leftPanel = new VBox(15);
        leftPanel.setPadding(new Insets(20));

        Label header = new Label("#" + task.getId());
        header.setStyle("""
        -fx-font-size: 24;
        -fx-font-weight: bold;
        -fx-text-fill: #2c3e50;
    """);

        Label statusLabel = new Label("Status: " + task.getStatus());

        Label priorityLabel = new Label("Priority: P" + task.getPriority());
        String color = switch (task.getPriority()) {
            case 1 -> "#e53935";
            case 2 -> "#fb8c00";
            default -> "#43a047";
        };
        priorityLabel.setStyle("""
        -fx-background-color: %s;
        -fx-text-fill: white;
        -fx-padding: 4 10;
        -fx-background-radius: 12;
    """.formatted(color));

        Label createdLabel = new Label("Created: " + task.getCreatedDate());

        leftPanel.getChildren().addAll(header, statusLabel, priorityLabel, createdLabel);

        // =========================
        // Subtask checklist in details
        // =========================
        if (task.hasSubtasks()) {
            int done  = task.getDoneSubtaskCount();
            int total = task.getTotalSubtaskCount();

            Separator sep = new Separator();

            Label subHeader = new Label("Subtasks (" + done + "/" + total + ")");
            subHeader.setStyle("-fx-font-weight: bold;");

            ProgressBar pb = new ProgressBar((double) done / total);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.setStyle(done == total ? "-fx-accent: #27ae60;" : "-fx-accent: #3498db;");

            VBox checkList = new VBox(4);
            List<SubTask> subtasks = task.getSubtasks();
            for (int i = 0; i < subtasks.size(); i++) {
                final int idx = i;
                SubTask st    = subtasks.get(i);
                CheckBox cb   = new CheckBox(st.getTitle());
                cb.setSelected(st.isDone());
                if (st.isDone()) cb.setStyle("-fx-text-fill: #aaa;");
                // Clicking writes to disk then closes (card re-renders with new progress)
                cb.setOnAction(e -> {
                    taskService.toggleSubtask(currentProject, task.getId(), idx);
                    refreshBoard();
                    dialog.close();
                });
                checkList.getChildren().add(cb);
            }

            leftPanel.getChildren().addAll(sep, subHeader, pb, checkList);
        }

        // =========================
        // RIGHT PANEL — Markdown preview
        // FIX 1: Subtasks are appended to the markdown so they
        //         render as a checklist in the WebView.
        // FIX 2: CSS makes all text selectable and copyable.
        // =========================
        WebView preview = new WebView();
        preview.setContextMenuEnabled(true);   // right-click → Copy in WebView

        org.commonmark.parser.Parser mdParser =
                org.commonmark.parser.Parser.builder().build();

        org.commonmark.renderer.html.HtmlRenderer mdRenderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().build();

        // Build the full markdown — description + subtask checklist
        String fullMarkdown = buildTaskMarkdownForPreview(task);
        String bodyHtml     = mdRenderer.render(mdParser.parse(fullMarkdown));

        preview.getEngine().loadContent(buildSelectableHtml(bodyHtml));

        // =========================
        // SPLIT PANE — ORIGINAL UNCHANGED
        // =========================
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPanel, preview);
        splitPane.setDividerPositions(0.35);

        dialog.getDialogPane().setContent(splitPane);
        dialog.getDialogPane().setPrefSize(900, 600);

        // =========================
        // BUTTON ACTIONS — ORIGINAL UNCHANGED
        // =========================
        dialog.setResultConverter(button -> {

            if (button == updateBtn) {
                showUpdateDialog(task);
            }

            if (button == deleteBtn) {
                confirmAndDelete(task);
            }

            return null;
        });

        dialog.showAndWait();
    }

    /**
     * Build markdown string = description + subtask checklist.
     * Subtasks render as "- [x] Done" / "- [ ] Open" which commonmark
     * converts to list items (standard GFM task-list syntax).
     */
    private String buildTaskMarkdownForPreview(Task task) {
        // Strip inline HTML metadata comments from description
        String desc = task.getDescription()
                .replaceAll("<!--.*?-->", "")
                .trim();

        if (!task.hasSubtasks()) return desc;

        StringBuilder sb = new StringBuilder(desc);
        sb.append("\n\n---\n\n**Subtasks**\n\n");
        for (SubTask st : task.getSubtasks()) {
            sb.append(st.isDone() ? "- [x] " : "- [ ] ")
                    .append(st.getTitle())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * Wrap rendered HTML in a full page with:
     *  - Readable typography matching the app style
     *  - user-select: text so content is selectable and copyable
     */
    private String buildSelectableHtml(String bodyHtml) {
        return """
        <html>
        <head>
        <style>
            body {
                font-family: Arial, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                color: #2c3e50;
                padding: 20px;
                margin: 0;
                /* FIX: make all text selectable */
                -webkit-user-select: text;
                user-select: text;
            }
            h1, h2, h3 { color: #2c3e50; }
            hr { border: none; border-top: 1px solid #dee2e6; margin: 14px 0; }
            ul { padding-left: 20px; }
            li { margin: 4px 0; }
            /* Style task-list checkboxes rendered by commonmark */
            li input[type=checkbox] {
                margin-right: 6px;
                pointer-events: none;   /* view-only — toggling is done in left panel */
            }
            code {
                background: #f4f6f7;
                padding: 2px 5px;
                border-radius: 3px;
                font-family: Consolas, monospace;
                font-size: 12px;
            }
            strong { color: #1a252f; }
            blockquote {
                border-left: 3px solid #3498db;
                margin: 0;
                padding: 6px 14px;
                background: #eaf4fb;
                color: #1a5276;
            }
        </style>
        </head>
        <body>
        %s
        </body>
        </html>
        """.formatted(bodyHtml);
    }

    // ====================================================
    // UPDATE TASK DIALOG
    // FIX: now includes a subtask editor panel alongside
    //      the description TextArea.
    // ====================================================

    private void showUpdateDialog(Task task) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Task");

        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        // ── Description editor — ORIGINAL fix preserved (strip metadata) ──
        String cleanDescription = task.getDescription()
                .replaceAll("\\s*<!--.*?-->\\s*", "").trim();
        TextArea editor = new TextArea(cleanDescription);
        editor.setWrapText(true);
        editor.setPrefRowCount(12);
        editor.setPrefWidth(420);

        // ── Subtask panel (same pattern as Add Task dialog) ──────────────
        List<SubTask> workingSubtasks = new ArrayList<>(task.getSubtasks());
        VBox subtaskListBox = new VBox(4);
        rebuildSubtaskListBox(subtaskListBox, workingSubtasks);

        TextField subtaskField = new TextField();
        subtaskField.setPromptText("New subtask...");
        HBox.setHgrow(subtaskField, Priority.ALWAYS);

        Button addSubBtn = new Button("+ Add");
        addSubBtn.setStyle("""
            -fx-background-color: #3498db;
            -fx-text-fill: white;
            -fx-background-radius: 4;
            -fx-padding: 4 10;
            """);

        Runnable doAddSub = () -> {
            String t = subtaskField.getText().trim();
            if (!t.isBlank()) {
                workingSubtasks.add(new SubTask(t, false));
                rebuildSubtaskListBox(subtaskListBox, workingSubtasks);
                subtaskField.clear();
                subtaskField.requestFocus();
            }
        };
        addSubBtn.setOnAction(e -> doAddSub.run());
        subtaskField.setOnAction(e -> doAddSub.run());

        HBox subtaskInputRow = new HBox(6, subtaskField, addSubBtn);

        ScrollPane subtaskScroll = new ScrollPane(subtaskListBox);
        subtaskScroll.setFitToWidth(true);
        subtaskScroll.setPrefHeight(160);

        Label subHeader = new Label("Subtasks");
        subHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11;");

        VBox subtaskPanel = new VBox(6,
                new Separator(),
                subHeader,
                subtaskScroll,
                subtaskInputRow);
        subtaskPanel.setPadding(new Insets(8, 0, 0, 0));

        // ── Priority dropdown ─────────────────────────────────────────────
        ComboBox<Integer> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(1, 2, 3);
        priorityBox.setValue(task.getPriority());
        HBox priorityRow = new HBox(10, new Label("Priority:"), priorityBox);
        priorityRow.setAlignment(Pos.CENTER_LEFT);

        VBox leftPanel = new VBox(10, priorityRow, editor, subtaskPanel);
        VBox.setVgrow(editor, Priority.ALWAYS);
        leftPanel.setPrefWidth(440);

        // ── Live markdown preview ─────────────────────────────────────────
        WebView preview = new WebView();
        preview.setPrefWidth(420);

        org.commonmark.parser.Parser mdParser =
                org.commonmark.parser.Parser.builder().build();
        org.commonmark.renderer.html.HtmlRenderer mdRenderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().build();

        editor.textProperty().addListener((obs, oldText, newText) -> {
            String html = mdRenderer.render(mdParser.parse(newText));
            preview.getEngine().loadContent(buildSelectableHtml(html));
        });

        // Load initial content
        preview.getEngine().loadContent(
                buildSelectableHtml(mdRenderer.render(mdParser.parse(cleanDescription))));

        HBox container = new HBox(15, leftPanel, preview);
        container.setPrefSize(920, 560);
        dialog.getDialogPane().setContent(container);

        Platform.runLater(editor::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {

            String newText = editor.getText().trim();

            if (!newText.isBlank()) {
                taskService.updateDescription(currentProject, task.getId(), newText);
            }

            // Update priority if changed
            int newPriority = priorityBox.getValue();
            if (newPriority != task.getPriority()) {
                taskService.updatePriority(currentProject, task.getId(), newPriority);
            }

            // Save subtasks
            taskService.setSubtasks(currentProject, task.getId(), workingSubtasks);

            refreshBoard();
        }
    }

    private void confirmAndDelete(Task task) {

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Task");
        confirm.setHeaderText("Delete Task #" + task.getId());
        confirm.setContentText("Are you sure you want to delete this task?");

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {

            taskService.deleteTask(currentProject, task.getId());
            refreshBoard();
        }
    }


}