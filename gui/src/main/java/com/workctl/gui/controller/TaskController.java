package com.workctl.gui.controller;

import com.workctl.core.model.Task;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;
import com.workctl.gui.ProjectContext;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

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

//    @FXML private TextArea taskInput;

//    @FXML private ComboBox<Integer> priorityComboBox;

    private final TaskService taskService = new TaskService();
    private String currentProject;

    @FXML
    public void initialize() {

        ProjectContext.addListener(this::setProject);

        // IMPORTANT: Attach drop handlers to SCROLLPANES
        setupDropTarget(openScroll, TaskStatus.OPEN);
        setupDropTarget(inProgressScroll, TaskStatus.IN_PROGRESS);
        setupDropTarget(doneScroll, TaskStatus.DONE);

//        priorityComboBox.getItems().addAll(1, 2, 3);
//        priorityComboBox.setValue(2); // default medium priority
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
        refreshBoard();
    }

    // ====================================================
    // ADD TASK
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
        editor.setPrefRowCount(18);
        editor.setPrefWidth(450);

        // ---------- Priority Dropdown ----------
        ComboBox<Integer> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll(1, 2, 3);
        priorityBox.setValue(2);

        Label priorityLabel = new Label("Priority:");

        HBox priorityRow = new HBox(10, priorityLabel, priorityBox);

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

        VBox leftPanel = new VBox(10, priorityRow, editor);

        HBox container = new HBox(15.0, leftPanel, preview);
        container.setPrefSize(950, 550);

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
                refreshBoard();
            }
        }
    }


    // ====================================================
    // REFRESH BOARD
    // ====================================================
    private void refreshBoard() {

        if (currentProject == null) return;

        List<Task> tasks = taskService.getTasks(currentProject);

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

        openCountLabel.setText("Open (" +
                grouped.getOrDefault(TaskStatus.OPEN, List.of()).size() + ")");

        inProgressCountLabel.setText("In Progress (" +
                grouped.getOrDefault(TaskStatus.IN_PROGRESS, List.of()).size() + ")");

        doneCountLabel.setText("Done (" +
                grouped.getOrDefault(TaskStatus.DONE, List.of()).size() + ")");
    }

    // ====================================================
    // TASK CARD
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
        // Hover Effect
        // =========================
        card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle() +
                        "-fx-background-color: #f5f7fa;"));

        card.setOnMouseExited(e ->
                card.setStyle(card.getStyle()
                        .replace("-fx-background-color: #f5f7fa;", "")));

        // =========================
        // Click Handling
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
        // Context Menu
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

        menu.getItems().addAll(
                moveOpen,
                moveProgress,
                moveDone,
                new SeparatorMenuItem(),
                priorityMenu
        );

        card.setOnContextMenuRequested(e ->
                menu.show(card, e.getScreenX(), e.getScreenY()));

        // =========================
        // Drag & Drop
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
    // ====================================================

    private void showTaskDetails(Task task) {

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Task Details");

        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType updateBtn = new ButtonType("Update Task");
        ButtonType deleteBtn = new ButtonType("Delete Task", ButtonBar.ButtonData.LEFT);

        dialog.getDialogPane().getButtonTypes().addAll(deleteBtn, updateBtn, closeBtn);

        // =========================
        // LEFT PANEL (Metadata)
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
        // RIGHT PANEL (Markdown)
        // =========================
        WebView preview = new WebView();

        org.commonmark.parser.Parser parser =
                org.commonmark.parser.Parser.builder().build();

        org.commonmark.renderer.html.HtmlRenderer renderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().build();

        String html = renderer.render(parser.parse(task.getDescription()));

        preview.getEngine().loadContent("""
        <html>
        <body style="font-family: Arial; padding: 20;">
        %s
        </body>
        </html>
        """.formatted(html));

        // =========================
        // SPLIT PANE
        // =========================
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPanel, preview);
        splitPane.setDividerPositions(0.35);

        dialog.getDialogPane().setContent(splitPane);
        dialog.getDialogPane().setPrefSize(900, 600);

        // =========================
        // BUTTON ACTIONS
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

    private void showUpdateDialog(Task task) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update Task");

        dialog.getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        TextArea editor = new TextArea(task.getDescription());
        editor.setWrapText(true);
        editor.setPrefSize(600, 400);

        dialog.getDialogPane().setContent(editor);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {

            String newText = editor.getText().trim();

            if (!newText.isBlank()) {
                taskService.updateDescription(
                        currentProject,
                        task.getId(),
                        newText
                );
                refreshBoard();
            }
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
