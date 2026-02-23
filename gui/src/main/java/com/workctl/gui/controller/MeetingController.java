package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Meeting;
import com.workctl.core.domain.Meeting.ActionItem;
import com.workctl.core.model.MeetingStatus;
import com.workctl.core.service.MeetingService;
import com.workctl.core.service.ProjectService;
import com.workctl.gui.ProjectContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MeetingController {

    @FXML
    private ComboBox<String> projectFilterCombo;
    @FXML
    private ComboBox<String> statusFilterCombo;
    @FXML
    private VBox meetingListVBox;
    @FXML
    private Button newMeetingBtn;

    private final MeetingService meetingService = new MeetingService();
    private final ProjectService projectService = new ProjectService();

    /**
     * Callback fired after any create/update/delete so MainController can refresh
     * the sidebar.
     */
    private Runnable onMeetingChanged;

    private static final DateTimeFormatter CARD_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm");

    // ─────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Status filter options
        statusFilterCombo.getItems().addAll("All", "Scheduled", "Done");
        statusFilterCombo.setValue("All");
        statusFilterCombo.setOnAction(e -> refreshMeetings());

        // Project filter populated after loading projects
        projectFilterCombo.getItems().addAll("All Projects", "General (No Project)");
        projectFilterCombo.setValue("All Projects");
        projectFilterCombo.setOnAction(e -> refreshMeetings());

        loadProjectsIntoFilter();

        // Listen for project selection changes
        ProjectContext.addListener(project -> Platform.runLater(() -> {
            if (project != null) {
                projectFilterCombo.setValue(project);
            }
            refreshMeetings();
        }));

        refreshMeetings();
    }

    /** Called by MainController to register the sidebar-refresh callback. */
    public void setOnMeetingChanged(Runnable callback) {
        this.onMeetingChanged = callback;
    }

    /** Trigger a full refresh (used by MainController after sidebar quick-add). */
    public void refresh() {
        refreshMeetings();
    }

    // ─────────────────────────────────────────────────────────────────
    // FILTER & REFRESH
    // ─────────────────────────────────────────────────────────────────

    private void loadProjectsIntoFilter() {
        try {
            AppConfig config = ConfigManager.load();
            var workspace = Paths.get(config.getWorkspace());
            projectService.listProjects(workspace)
                    .forEach(p -> {
                        if (!projectFilterCombo.getItems().contains(p.getName())) {
                            projectFilterCombo.getItems().add(p.getName());
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    @FXML
    public void refreshMeetings() {
        try {
            String projFilter = projectFilterCombo.getValue();
            String statusFilter = statusFilterCombo.getValue();

            List<Meeting> meetings;
            if (projFilter == null || projFilter.equals("All Projects")) {
                meetings = meetingService.listAllMeetings();
            } else if (projFilter.equals("General (No Project)")) {
                meetings = meetingService.listAllMeetings().stream()
                        .filter(m -> m.getProjectId() == null)
                        .toList();
            } else {
                meetings = meetingService.listMeetingsByProject(projFilter);
            }

            if (statusFilter != null && !statusFilter.equals("All")) {
                MeetingStatus wanted = statusFilter.equals("Scheduled")
                        ? MeetingStatus.SCHEDULED
                        : MeetingStatus.DONE;
                meetings = meetings.stream()
                        .filter(m -> m.getStatus() == wanted)
                        .toList();
            }

            meetingListVBox.getChildren().clear();

            if (meetings.isEmpty()) {
                Label empty = new Label("No meetings found.  Click '+ New Meeting' to create one.");
                empty.setStyle("-fx-text-fill: #718096; -fx-font-size: 12; -fx-padding: 20;");
                meetingListVBox.getChildren().add(empty);
                return;
            }

            for (Meeting m : meetings) {
                meetingListVBox.getChildren().add(buildMeetingCard(m));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // MEETING CARD
    // ─────────────────────────────────────────────────────────────────

    private Node buildMeetingCard(Meeting m) {
        VBox card = new VBox(6);
        card.getStyleClass().add("meeting-card");
        card.setUserData(m.getId());

        // ── Top row: date + status pill ──
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(
                m.getDateTime() != null ? m.getDateTime().format(CARD_DATE_FMT) : "—");
        dateLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean scheduled = m.getStatus() == MeetingStatus.SCHEDULED;
        Label statusPill = new Label(scheduled ? "Scheduled" : "Done");
        statusPill.getStyleClass().add(scheduled ? "meeting-status-scheduled" : "meeting-status-done");

        topRow.getChildren().addAll(dateLabel, spacer, statusPill);

        // ── Title ──
        Label titleLabel = new Label(m.getTitle());
        titleLabel.getStyleClass().add("meeting-card-title");
        titleLabel.setWrapText(true);

        // ── Bottom row: project badge + attendees count + action items ──
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        if (m.getProjectId() != null) {
            Label projBadge = new Label("\uD83D\uDCC1 " + m.getProjectId());
            projBadge.setStyle("-fx-text-fill: #4a90d9; -fx-font-size: 10; -fx-font-weight: bold;");
            bottomRow.getChildren().add(projBadge);
        } else {
            Label generalBadge = new Label("General");
            generalBadge.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 10;");
            bottomRow.getChildren().add(generalBadge);
        }

        if (m.getAttendees() != null && !m.getAttendees().isBlank()) {
            long count = m.getAttendees().chars().filter(c -> c == ',').count() + 1;
            Label attLabel = new Label("\uD83D\uDC65 " + count);
            attLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 10;");
            bottomRow.getChildren().add(attLabel);
        }

        int total = m.getTotalActionItemCount();
        if (total > 0) {
            int done = m.getDoneActionItemCount();
            Label aiLabel = new Label("\u2705 " + done + "/" + total);
            aiLabel.setStyle("-fx-text-fill: " + (done == total ? "#27ae60" : "#718096")
                    + "; -fx-font-size: 10;");
            bottomRow.getChildren().add(aiLabel);
        }

        card.getChildren().addAll(topRow, titleLabel);
        if (!bottomRow.getChildren().isEmpty())
            card.getChildren().add(bottomRow);

        card.setOnMouseClicked(e -> showMeetingDetail(m));
        return card;
    }

    // ─────────────────────────────────────────────────────────────────
    // NEW MEETING HANDLER
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void handleNewMeeting() {
        showMeetingDialog(null);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ-ONLY DETAIL VIEW & EDIT DIALOG
    // ─────────────────────────────────────────────────────────────────

    public void showMeetingDetail(Meeting m) {
        double screenW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Meeting Details");
        dialog.setHeaderText(m.getTitle());

        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType editBtn = new ButtonType("Edit", ButtonBar.ButtonData.OTHER);
        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(deleteBtn, editBtn, closeBtn);

        // ── Left metadata panel ────────────────────────────────────────
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(20, 16, 20, 20));
        leftPanel.setPrefWidth(210);

        Label titleLbl = new Label(m.getTitle());
        titleLbl.getStyleClass().add("meeting-card-title");
        titleLbl.setStyle("-fx-font-size: 16;");
        titleLbl.setWrapText(true);

        boolean scheduled = m.getStatus() == MeetingStatus.SCHEDULED;
        Label statusPill = new Label(scheduled ? "Scheduled" : "Done");
        statusPill.getStyleClass().add(scheduled ? "meeting-status-scheduled" : "meeting-status-done");

        leftPanel.getChildren().addAll(titleLbl, statusPill);

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-padding: 2 0;");
        leftPanel.getChildren().add(sep1);

        // Date
        Label dateLbl = new Label("\uD83D\uDCC5  "
                + (m.getDateTime() != null ? m.getDateTime().format(CARD_DATE_FMT) : "—"));
        dateLbl.setStyle("-fx-font-size: 12;");
        leftPanel.getChildren().add(dateLbl);

        // Project
        if (m.getProjectId() != null) {
            Label projBadge = new Label("\uD83D\uDCC1 " + m.getProjectId());
            projBadge.setStyle("-fx-text-fill: #4a90d9; -fx-font-size: 11; -fx-font-weight: bold;");
            leftPanel.getChildren().add(projBadge);
        } else {
            Label generalBadge = new Label("General");
            generalBadge.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");
            leftPanel.getChildren().add(generalBadge);
        }

        // Action Item stats
        int total = m.getTotalActionItemCount();
        if (total > 0) {
            Separator sep2 = new Separator();
            leftPanel.getChildren().add(sep2);

            int done = m.getDoneActionItemCount();
            Label aiLbl = new Label("\u2705 " + done + " / " + total + " action items");
            aiLbl.setStyle("-fx-font-size: 11; -fx-text-fill: "
                    + (done == total ? "#27ae60" : "#718096") + ";");
            leftPanel.getChildren().add(aiLbl);
        }

        // Attendee count
        if (m.getAttendees() != null && !m.getAttendees().isBlank()) {
            long count = m.getAttendees().chars().filter(c -> c == ',').count() + 1;
            Label attLabel = new Label("\uD83D\uDC65 " + count + " attendee" + (count > 1 ? "s" : ""));
            attLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");
            leftPanel.getChildren().add(attLabel);
        }

        // ── Right: WebView with styled HTML content ────────────────────
        final String detailHtml = buildMeetingDetailHtml(m);
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setContextMenuEnabled(true);
        webView.getEngine().loadContent(detailHtml);

        // ── Layout ─────────────────────────────────────────────────────
        SplitPane splitPane = new SplitPane(leftPanel, webView);
        splitPane.setDividerPositions(0.28);

        double prefW = Math.min(850, screenW - 80);
        double prefH = Math.min(600, screenH - 80);
        dialog.getDialogPane().setPrefSize(prefW, prefH);
        dialog.getDialogPane().setContent(splitPane);

        // ── Button actions ──────────────────────────────────────────────
        dialog.setResultConverter(btn -> {
            if (btn == editBtn) {
                showMeetingDialog(m);
            } else if (btn == deleteBtn) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete meeting \"" + m.getTitle() + "\"?");
                confirm.setHeaderText(null);
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
                    meetingService.deleteMeeting(m.getId());
                    afterChange();
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private String buildMeetingDetailHtml(Meeting m) {
        StringBuilder h = new StringBuilder();
        h.append("""
                <html><head><meta charset="UTF-8"><style>
                body { font-family: -apple-system, Arial, sans-serif; font-size: 14px;
                       line-height: 1.6; color: #1a202c; padding: 20px; margin: 0;
                       -webkit-user-select: text; user-select: text; background: #f8fafc; }
                h2 { font-size: 13px; font-weight: 700; color: #4a5568; margin: 18px 0 8px 0;
                     border-bottom: 1px solid #e2e8f0; padding-bottom: 4px;
                     text-transform: uppercase; letter-spacing: 0.06em; }
                .text-block { background: #fff; border-left: 3px solid #3b82f6;
                              padding: 10px 14px; border-radius: 0 6px 6px 0;
                              border: 1px solid #e2e8f0; font-size: 13px; white-space: pre-wrap; margin-bottom: 12px; }
                .q-row { display: flex; align-items: baseline; gap: 6px; padding: 4px;
                         margin: 3px 0; border-radius: 4px; font-size: 13px; }
                .q-row:hover { background: #edf2f7; }
                .q-done { color: #a0aec0; text-decoration: line-through; }
                .q-check  { color: #27ae60; font-weight: bold; flex-shrink: 0; }
                .q-box    { color: #cbd5e0; flex-shrink: 0; }
                .ai-meta { font-size: 11px; color: #718096; margin-left: 6px; }
                </style></head><body>
                """);

        // Attendees
        if (m.getAttendees() != null && !m.getAttendees().isBlank()) {
            h.append("<h2>Attendees</h2>\n");
            h.append("<div style='font-size: 13px; color: #4a5568;'>").append(esc(m.getAttendees())).append("</div>\n");
        }

        // Agenda
        if (m.getAgenda() != null && !m.getAgenda().isBlank()) {
            h.append("<h2>Agenda</h2>\n");
            h.append("<div class='text-block'>").append(esc(m.getAgenda())).append("</div>\n");
        }

        // Notes
        if (m.getNotes() != null && !m.getNotes().isBlank()) {
            h.append("<h2>Notes & Minutes</h2>\n");
            h.append("<div class='text-block'>").append(esc(m.getNotes())).append("</div>\n");
        }

        // Action Items
        if (m.getActionItems() != null && !m.getActionItems().isEmpty()) {
            h.append("<h2>Action Items</h2>\n");
            for (ActionItem ai : m.getActionItems()) {
                h.append("<div class='q-row'>");
                h.append("<span class='").append(ai.isDone() ? "q-check" : "q-box").append("'>")
                        .append(ai.isDone() ? "&#10003;" : "&#9633;").append("</span>");
                h.append("<span class='").append(ai.isDone() ? "q-done" : "").append("'>")
                        .append(esc(ai.getTitle())).append("</span>");
                String meta = "";
                if (ai.getOwner() != null && !ai.getOwner().isBlank())
                    meta += "owner: " + esc(ai.getOwner());
                if (ai.getDueDate() != null)
                    meta += (meta.isEmpty() ? "" : ", ") + "due: " + ai.getDueDate().toString();
                if (!meta.isEmpty()) {
                    h.append("<span class='ai-meta'>(").append(meta).append(")</span>");
                }
                h.append("</div>\n");
            }
        }

        h.append("</body></html>");
        return h.toString();
    }

    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ─────────────────────────────────────────────────────────────────
    // CREATE / EDIT DIALOG
    // ─────────────────────────────────────────────────────────────────

    /**
     * Opens the meeting detail / edit dialog.
     * Pass null to create a new meeting; pass an existing meeting to edit it.
     */
    public void showMeetingDialog(Meeting existing) {
        boolean isNew = (existing == null);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New Meeting" : "Edit Meeting");
        dialog.setHeaderText(isNew ? "Create Meeting" : existing.getTitle());
        dialog.getDialogPane().setPrefWidth(520);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(saveBtn);
        if (!isNew) {
            dialog.getDialogPane().getButtonTypes().add(
                    new ButtonType("Delete", ButtonBar.ButtonData.OTHER));
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // ── Build form ──
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 10, 20));

        // Title
        TextField titleField = new TextField(existing == null ? "" : existing.getTitle());
        titleField.setPromptText("Meeting title");
        GridPane.setHgrow(titleField, Priority.ALWAYS);

        // Date
        DatePicker datePicker = new DatePicker(
                existing != null && existing.getDateTime() != null
                        ? existing.getDateTime().toLocalDate()
                        : LocalDate.now());

        // Hour + Minute spinners
        int initHour = existing != null && existing.getDateTime() != null
                ? existing.getDateTime().getHour()
                : 10;
        int initMin = existing != null && existing.getDateTime() != null
                ? existing.getDateTime().getMinute()
                : 0;

        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, initHour);
        hourSpinner.setPrefWidth(68);
        hourSpinner.setEditable(true);

        Spinner<Integer> minSpinner = new Spinner<>(0, 59, initMin, 15);
        minSpinner.setPrefWidth(68);
        minSpinner.setEditable(true);

        HBox timeBox = new HBox(6, hourSpinner, new Label(":"), minSpinner);
        timeBox.setAlignment(Pos.CENTER_LEFT);

        // Status toggle
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Scheduled", "Done");
        statusCombo.setValue(existing == null || existing.getStatus() == MeetingStatus.SCHEDULED
                ? "Scheduled"
                : "Done");

        // Project
        ComboBox<String> projectCombo = new ComboBox<>();
        projectCombo.getItems().add("General (No Project)");
        loadProjectsIntoCombo(projectCombo);
        String currentProject = ProjectContext.getCurrentProject();
        if (existing != null && existing.getProjectId() != null) {
            projectCombo.setValue(existing.getProjectId());
        } else if (isNew && currentProject != null) {
            projectCombo.setValue(currentProject);
        } else {
            projectCombo.setValue("General (No Project)");
        }

        // Attendees
        TextField attendeesField = new TextField(
                existing == null ? "" : nvl(existing.getAttendees()));
        attendeesField.setPromptText("Comma-separated names");

        // Agenda
        TextArea agendaArea = new TextArea(
                existing == null ? "" : nvl(existing.getAgenda()));
        agendaArea.setPromptText("Topics to discuss…");
        agendaArea.setPrefRowCount(3);
        agendaArea.setWrapText(true);

        // Notes
        TextArea notesArea = new TextArea(
                existing == null ? "" : nvl(existing.getNotes()));
        notesArea.setPromptText("Meeting notes / minutes…");
        notesArea.setPrefRowCount(4);
        notesArea.setWrapText(true);

        // Action Items
        VBox actionItemsBox = new VBox(5);
        Label aiHeader = new Label("Action Items");
        aiHeader.setStyle("-fx-font-weight: bold;");

        List<ActionItem> liveItems = new ArrayList<>(
                existing == null ? List.of() : existing.getActionItems());
        VBox aiList = new VBox(4);
        refreshActionItemList(aiList, liveItems);

        TextField newItemTitle = new TextField();
        newItemTitle.setPromptText("New action item title");
        HBox.setHgrow(newItemTitle, Priority.ALWAYS);

        TextField newItemOwner = new TextField();
        newItemOwner.setPromptText("Owner (optional)");
        newItemOwner.setPrefWidth(110);

        DatePicker newItemDue = new DatePicker();
        newItemDue.setPromptText("Due");
        newItemDue.setPrefWidth(120);

        Button addItemBtn = new Button("Add");
        addItemBtn.getStyleClass().add("add-task-btn");
        addItemBtn.setOnAction(e -> {
            String t = newItemTitle.getText().trim();
            if (!t.isBlank()) {
                String owner = newItemOwner.getText().trim();
                LocalDate dueDate = newItemDue.getValue();
                liveItems.add(new ActionItem(t,
                        false,
                        owner.isBlank() ? null : owner,
                        dueDate));
                newItemTitle.clear();
                newItemOwner.clear();
                newItemDue.setValue(null);
                refreshActionItemList(aiList, liveItems);
            }
        });

        HBox addItemRow = new HBox(6, newItemTitle, newItemOwner, newItemDue, addItemBtn);
        addItemRow.setAlignment(Pos.CENTER_LEFT);

        actionItemsBox.getChildren().addAll(aiHeader, aiList, addItemRow);

        // ── Layout ──
        grid.add(new Label("Title *"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Date"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("Time"), 0, 2);
        grid.add(timeBox, 1, 2);
        grid.add(new Label("Status"), 0, 3);
        grid.add(statusCombo, 1, 3);
        grid.add(new Label("Project"), 0, 4);
        grid.add(projectCombo, 1, 4);
        grid.add(new Label("Attendees"), 0, 5);
        grid.add(attendeesField, 1, 5);
        grid.add(new Label("Agenda"), 0, 6);
        grid.add(agendaArea, 1, 6);
        grid.add(new Label("Notes"), 0, 7);
        grid.add(notesArea, 1, 7);
        grid.add(actionItemsBox, 0, 8, 2, 1);

        dialog.getDialogPane().setContent(new ScrollPane(grid) {
            {
                setFitToWidth(true);
                setPrefHeight(560);
                setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            }
        });

        // Disable Save until title is non-blank
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        saveButton.setDisable(titleField.getText().trim().isEmpty());
        titleField.textProperty().addListener((o, old, n) -> saveButton.setDisable(n.trim().isEmpty()));

        Platform.runLater(titleField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isEmpty())
            return;
        ButtonType clicked = result.get();

        if (clicked.getButtonData() == ButtonBar.ButtonData.OTHER) {
            // Delete
            if (existing != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete meeting \"" + existing.getTitle() + "\"?");
                confirm.setHeaderText(null);
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
                    meetingService.deleteMeeting(existing.getId());
                    afterChange();
                }
            }
            return;
        }

        if (clicked != saveBtn)
            return;

        // Build / update meeting
        String title = titleField.getText().trim();
        if (title.isBlank())
            return;

        LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
        int hour = hourSpinner.getValue();
        int minute = minSpinner.getValue();
        LocalDateTime dt = date.atTime(hour, minute);

        MeetingStatus status = statusCombo.getValue().equals("Scheduled")
                ? MeetingStatus.SCHEDULED
                : MeetingStatus.DONE;

        String projVal = projectCombo.getValue();
        String projectId = (projVal == null || projVal.equals("General (No Project)")) ? null : projVal;

        if (isNew) {
            Meeting m = meetingService.createMeeting(title, dt, projectId);
            m.setStatus(status);
            m.setAttendees(attendeesField.getText().trim());
            m.setAgenda(agendaArea.getText().trim());
            m.setNotes(notesArea.getText().trim());
            m.setActionItems(liveItems);
            meetingService.saveMeeting(m);
        } else {
            existing.setTitle(title);
            existing.setDateTime(dt);
            existing.setStatus(status);
            existing.setProjectId(projectId);
            existing.setAttendees(attendeesField.getText().trim());
            existing.setAgenda(agendaArea.getText().trim());
            existing.setNotes(notesArea.getText().trim());
            existing.setActionItems(liveItems);
            meetingService.saveMeeting(existing);
        }

        afterChange();
    }

    // ─────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────

    /** Rebuild the action-items checklist inside the dialog. */
    private void refreshActionItemList(VBox container, List<ActionItem> items) {
        container.getChildren().clear();
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            ActionItem ai = items.get(i);

            CheckBox cb = new CheckBox(ai.getTitle()
                    + (ai.getOwner() != null ? "  [" + ai.getOwner() + "]" : "")
                    + (ai.getDueDate() != null ? "  due:" + ai.getDueDate() : ""));
            cb.setSelected(ai.isDone());
            cb.setOnAction(e -> ai.setDone(cb.isSelected()));
            if (ai.isDone())
                cb.setStyle("-fx-text-fill: #888;");

            Button removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c;"
                    + " -fx-cursor: hand; -fx-padding: 0 4;");
            removeBtn.setOnAction(e -> {
                items.remove(idx);
                refreshActionItemList(container, items);
            });

            HBox row = new HBox(8, cb, new Region() {
                {
                    HBox.setHgrow(this, Priority.ALWAYS);
                }
            }, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().add(row);
        }
    }

    private void loadProjectsIntoCombo(ComboBox<String> combo) {
        try {
            AppConfig config = ConfigManager.load();
            var workspace = Paths.get(config.getWorkspace());
            projectService.listProjects(workspace)
                    .forEach(p -> combo.getItems().add(p.getName()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Called after any CUD operation — refreshes the tab list and notifies the
     * sidebar.
     */
    private void afterChange() {
        refreshMeetings();
        if (onMeetingChanged != null)
            onMeetingChanged.run();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
