package com.workctl.gui.controller;

import com.workctl.core.domain.Interview;
import com.workctl.core.domain.Interview.ExperienceLink;
import com.workctl.core.domain.Interview.InterviewQuestion;
import com.workctl.core.domain.PrepTopic;
import com.workctl.core.model.InterviewResult;
import com.workctl.core.model.InterviewRound;
import com.workctl.core.service.InterviewService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InterviewController {

    @FXML
    private ComboBox<String> roundFilterCombo;
    @FXML
    private ComboBox<String> resultFilterCombo;
    @FXML
    private VBox interviewListVBox;

    @FXML
    private TabPane interviewTabPane;
    @FXML
    private Tab prepTab;
    @FXML
    private Label prepTitleLabel;
    @FXML
    private Button addQuestionBtn;
    @FXML
    private VBox prepQuestionsVBox;

    private final InterviewService interviewService = new InterviewService();

    private Interview selectedPrepInterview;

    private Runnable onInterviewChanged;

    private static final DateTimeFormatter CARD_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    // Default topic categories and question sections
    private static final List<String> DEFAULT_CATEGORIES = List.of("DSA", "System Design", "OS", "Networking",
            "OOPS", "Behavioral", "Language/Framework", "Other");
    private static final List<String> DEFAULT_SECTIONS = List.of("DSA", "System Design", "OS", "Networking",
            "OOPS", "Behavioral", "Other");

    // ─────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Round filter
        roundFilterCombo.getItems().add("All Rounds");
        for (InterviewRound r : InterviewRound.values())
            roundFilterCombo.getItems().add(r.label());
        roundFilterCombo.setValue("All Rounds");
        roundFilterCombo.setOnAction(e -> refreshInterviews());

        // Result filter
        resultFilterCombo.getItems().addAll("All Results", "Pending", "Offered", "Rejected");
        resultFilterCombo.setValue("All Results");
        resultFilterCombo.setOnAction(e -> refreshInterviews());

        refreshInterviews();
        // refreshPrepTopics(); // removed until implemented
    }

    public void setOnInterviewChanged(Runnable callback) {
        this.onInterviewChanged = callback;
    }

    public void refresh() {
        refreshInterviews();
        refreshPreparationTab();
    }

    // ─────────────────────────────────────────────────────────────────
    // INTERVIEWS LIST
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void refreshInterviews() {
        try {
            List<Interview> interviews = interviewService.listAllInterviews();

            String roundFilter = roundFilterCombo.getValue();
            if (roundFilter != null && !roundFilter.equals("All Rounds")) {
                interviews = interviews.stream()
                        .filter(iv -> iv.getRound() != null && iv.getRound().label().equals(roundFilter))
                        .toList();
            }

            String resultFilter = resultFilterCombo.getValue();
            if (resultFilter != null && !resultFilter.equals("All Results")) {
                InterviewResult wanted = switch (resultFilter) {
                    case "Offered" -> InterviewResult.OFFERED;
                    case "Rejected" -> InterviewResult.REJECTED;
                    default -> InterviewResult.PENDING;
                };
                interviews = interviews.stream().filter(iv -> iv.getResult() == wanted).toList();
            }

            interviewListVBox.getChildren().clear();

            if (interviews.isEmpty()) {
                Label empty = new Label("No interviews found.  Click '+ New Interview' to add one.");
                empty.setStyle("-fx-text-fill: #718096; -fx-font-size: 12; -fx-padding: 20;");
                interviewListVBox.getChildren().add(empty);
                return;
            }

            for (Interview iv : interviews)
                interviewListVBox.getChildren().add(buildInterviewCard(iv));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // INTERVIEW CARD
    // ─────────────────────────────────────────────────────────────────

    private Node buildInterviewCard(Interview iv) {
        VBox card = new VBox(6);
        card.getStyleClass().add("interview-card");

        // ── Top row: company · role + result pill ──
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label companyLabel = new Label(iv.getCompany());
        companyLabel.getStyleClass().add("interview-card-title");

        Label sep = new Label("\u00B7");
        sep.setStyle("-fx-text-fill: #718096;");

        Label roleLabel = new Label(iv.getRole());
        roleLabel.getStyleClass().add("interview-card-role");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button prepareBtn = new Button("Prepare");
        prepareBtn.getStyleClass().add("task-info-btn");
        prepareBtn.setStyle("-fx-font-size: 11; -fx-padding: 3 8;");
        prepareBtn.setOnAction(e -> {
            e.consume();
            selectedPrepInterview = iv;
            interviewTabPane.getSelectionModel().select(prepTab);
            refreshPreparationTab();
        });

        topRow.getChildren().addAll(companyLabel, sep, roleLabel, prepareBtn, spacer, buildResultPill(iv.getResult()));

        // ── Mid row: date + status + round badge + job URL icon ──
        HBox midRow = new HBox(8);
        midRow.setAlignment(Pos.CENTER_LEFT);

        String dtStr = iv.getDateTime() != null
                ? iv.getDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy \u00B7 h:mm a"))
                : "\u2014";
        Label dateLabel = new Label(dtStr);
        dateLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");
        midRow.getChildren().add(dateLabel);

        Label statusPill = new Label(
                iv.getStatus() == com.workctl.core.model.InterviewStatus.SCHEDULED ? "Scheduled" : "Completed");
        statusPill.getStyleClass()
                .add(iv.getStatus() == com.workctl.core.model.InterviewStatus.SCHEDULED ? "meeting-status-scheduled"
                        : "meeting-status-done");
        midRow.getChildren().add(statusPill);

        if (iv.getRound() != null) {
            Label roundBadge = new Label(iv.getRound().label());
            roundBadge.getStyleClass().add("interview-round-badge");
            midRow.getChildren().add(roundBadge);
        }

        if (iv.getJobUrl() != null) {
            Hyperlink jobLink = new Hyperlink("\uD83D\uDD17 Job Posting");
            jobLink.setStyle("-fx-font-size: 10;");
            jobLink.setOnAction(e -> openUrl(iv.getJobUrl()));
            midRow.getChildren().add(jobLink);
        }

        // ── Bottom row: stats + experience links count ──
        HBox bottomRow = new HBox(12);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        int total = iv.getTotalQuestionCount();
        if (total > 0) {
            int done = iv.getDoneQuestionCount();
            Label qLabel = new Label("\uD83D\uDCAC " + done + "/" + total);
            qLabel.setStyle("-fx-text-fill: " + (done == total ? "#27ae60" : "#718096")
                    + "; -fx-font-size: 10;");
            bottomRow.getChildren().add(qLabel);

            int imp = iv.getImportantQuestionCount();
            if (imp > 0) {
                Label impLabel = new Label("\u2B50 " + imp + " important");
                impLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 10;");
                bottomRow.getChildren().add(impLabel);
            }
        }

        int links = iv.getExperienceLinks().size();
        if (links > 0) {
            Label linksLabel = new Label("\uD83D\uDD17 " + links + " link" + (links == 1 ? "" : "s"));
            linksLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 10;");
            bottomRow.getChildren().add(linksLabel);
        }

        card.getChildren().addAll(topRow, midRow);
        if (!bottomRow.getChildren().isEmpty())
            card.getChildren().add(bottomRow);
        card.setOnMouseClicked(e -> showInterviewDetail(iv));
        return card;
    }

    private Label buildResultPill(InterviewResult result) {
        String text = result == null ? "Pending" : switch (result) {
            case OFFERED -> "Offered";
            case REJECTED -> "Rejected";
            default -> "Pending";
        };
        String styleClass = result == null ? "interview-result-pending" : switch (result) {
            case OFFERED -> "interview-result-offered";
            case REJECTED -> "interview-result-rejected";
            default -> "interview-result-pending";
        };
        Label pill = new Label(text);
        pill.getStyleClass().add(styleClass);
        return pill;
    }

    // ─────────────────────────────────────────────────────────────────
    // PREPARATION TAB
    // ─────────────────────────────────────────────────────────────────

    private void refreshPreparationTab() {
        prepQuestionsVBox.getChildren().clear();

        if (selectedPrepInterview == null) {
            prepTitleLabel.setText("Please select an Interview to prepare.");
            return;
        }

        prepTitleLabel.setText(
                "Preparing for: " + selectedPrepInterview.getCompany() + " \u2014 " + selectedPrepInterview.getRole());

        // Group the selected interview's questions by section
        Map<String, List<Interview.InterviewQuestion>> grouped = new LinkedHashMap<>();
        for (String c : DEFAULT_SECTIONS) {
            grouped.put(c, new ArrayList<>());
        }

        for (Interview.InterviewQuestion t : selectedPrepInterview.getQuestions()) {
            grouped.computeIfAbsent(t.getSection(), k -> new ArrayList<>()).add(t);
        }

        boolean first = true;
        for (String sec : DEFAULT_SECTIONS) {
            List<Interview.InterviewQuestion> items = grouped.get(sec);
            if (items == null || items.isEmpty())
                continue;

            if (!first) {
                Region spacer = new Region();
                spacer.setPrefHeight(10);
                prepQuestionsVBox.getChildren().add(spacer);
            }
            first = false;

            int total = items.size();
            long done = items.stream().filter(Interview.InterviewQuestion::isDone).count();

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);

            Label secLbl = new Label(sec);
            secLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: #1e293b;");
            Label cntLbl = new Label(done + "/" + total);
            cntLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11;");

            Region hSpc = new Region();
            HBox.setHgrow(hSpc, Priority.ALWAYS);

            header.getChildren().addAll(secLbl, cntLbl, hSpc);

            Separator s = new Separator(Orientation.HORIZONTAL);
            s.setStyle("-fx-padding: 0 0 5 0;");

            VBox sectionBox = new VBox(4, header, s);

            for (Interview.InterviewQuestion q : items) {
                sectionBox.getChildren().add(buildQuestionCard(q));
            }

            prepQuestionsVBox.getChildren().add(sectionBox);
        }
    }

    private Node buildQuestionCard(Interview.InterviewQuestion q) {
        VBox card = new VBox(6);
        card.getStyleClass().add("task-card");

        // Top row
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox cb = new CheckBox();
        cb.setSelected(q.isDone());
        cb.setOnAction(e -> {
            q.setDone(cb.isSelected());
            saveCurrentInterviewSilently();
            refreshPreparationTab();
        });

        Label title = new Label(q.getText());
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 13; -fx-text-fill: #334155;");
        if (q.isDone()) {
            title.setStyle("-fx-font-size: 13; -fx-text-fill: #94a3b8; -fx-strikethrough: true;");
        }

        Region spc = new Region();
        HBox.setHgrow(spc, Priority.ALWAYS);

        // Edit/Delete actions
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("task-info-btn");
        editBtn.setStyle("-fx-font-size: 10; -fx-padding: 2 6;");
        editBtn.setOnAction(e -> showStandaloneQuestionDialog(q));

        Button delBtn = new Button("\u2715");
        delBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand; -fx-padding: 0 4;");
        delBtn.setOnAction(e -> {
            selectedPrepInterview.getQuestions().remove(q);
            saveCurrentInterviewSilently();
            refreshPreparationTab();
        });

        topRow.getChildren().addAll(cb, title, spc, editBtn, delBtn);

        // Link row if present
        HBox linkRow = new HBox();
        if (q.getUrl() != null && !q.getUrl().isBlank()) {
            Hyperlink a = new Hyperlink("\uD83D\uDD17 Link");
            a.setStyle("-fx-font-size: 11;");
            a.setOnAction(e -> openUrl(q.getUrl()));
            linkRow.getChildren().add(a);
        }

        if (q.isImportant()) {
            Label imp = new Label("\u2B50 Important");
            imp.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 10;");
            linkRow.getChildren().add(imp);
            HBox.setMargin(imp, new Insets(2, 0, 0, 8)); // visual align
        }

        card.getChildren().add(topRow);
        if (!linkRow.getChildren().isEmpty()) {
            card.getChildren().add(linkRow);
        }

        return card;
    }

    private void saveCurrentInterviewSilently() {
        if (selectedPrepInterview != null) {
            interviewService.saveInterview(selectedPrepInterview);
            refreshInterviews(); // keep the kanban numbers in sync
        }
    }

    @FXML
    private void handleAddQuestion() {
        if (selectedPrepInterview == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Please select an Interview to prepare for first.",
                    ButtonType.OK);
            a.showAndWait();
            return;
        }
        showStandaloneQuestionDialog(null);
    }

    private void showStandaloneQuestionDialog(Interview.InterviewQuestion existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Question" : "Edit Question");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        ComboBox<String> secCombo = new ComboBox<>();
        secCombo.getItems().addAll(DEFAULT_SECTIONS);
        secCombo.setValue(existing == null ? "DSA" : existing.getSection());

        TextField questField = new TextField(existing == null ? "" : existing.getText());
        questField.setPrefWidth(300);

        String linkText = existing == null ? "" : (existing.getUrl() != null ? existing.getUrl() : "");
        TextField urlField = new TextField(linkText);
        urlField.setPromptText("https://... (optional)");

        CheckBox impCheck = new CheckBox("Important");
        impCheck.setSelected(existing != null && existing.isImportant());

        grid.add(new Label("Section:"), 0, 0);
        grid.add(secCombo, 1, 0);
        grid.add(new Label("Question:"), 0, 1);
        grid.add(questField, 1, 1);
        grid.add(new Label("URL:"), 0, 2);
        grid.add(urlField, 1, 2);
        grid.add(impCheck, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(questField::requestFocus);

        dialog.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                String sec = secCombo.getValue();
                String q = questField.getText().trim();
                String link = urlField.getText().trim();
                boolean imp = impCheck.isSelected();

                if (q.isBlank())
                    return;

                if (existing == null) {
                    Interview.InterviewQuestion nq = new Interview.InterviewQuestion(sec, q, link, false, imp, "");
                    if (selectedPrepInterview.getQuestions() == null) {
                        selectedPrepInterview.setQuestions(new ArrayList<>());
                    }
                    selectedPrepInterview.getQuestions().add(nq);
                } else {
                    existing.setSection(sec);
                    existing.setText(q);
                    existing.setUrl(link);
                    existing.setImportant(imp);
                }

                saveCurrentInterviewSilently();
                refreshPreparationTab();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────
    // NEW INTERVIEW HANDLER
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void handleNewInterview() {
        showInterviewDialog(null);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ-ONLY DETAIL VIEW (click on card → opens this)
    // ─────────────────────────────────────────────────────────────────

    public void showInterviewDetail(Interview iv) {
        double screenW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Interview: " + iv.getCompany() + " \u2014 " + iv.getRole());

        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType editBtn = new ButtonType("Edit", ButtonBar.ButtonData.OTHER);
        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(deleteBtn, editBtn, closeBtn);

        // ── Left metadata panel ────────────────────────────────────────
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(20, 16, 20, 20));
        leftPanel.setPrefWidth(210);

        Label companyLbl = new Label(iv.getCompany());
        companyLbl.getStyleClass().add("interview-card-title");
        companyLbl.setStyle("-fx-font-size: 17;"); // override specific size for detail view
        companyLbl.setWrapText(true);

        Label roleLbl = new Label(iv.getRole());
        roleLbl.getStyleClass().add("interview-card-role");
        roleLbl.setWrapText(true);

        leftPanel.getChildren().addAll(companyLbl, roleLbl, buildResultPill(iv.getResult()));

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-padding: 2 0;");
        leftPanel.getChildren().add(sep1);

        // Date & Time
        String dtStr = iv.getDateTime() != null
                ? iv.getDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy \u00B7 h:mm a"))
                : "\u2014";
        Label dateLbl = new Label("\uD83D\uDCC5  " + dtStr);
        dateLbl.setStyle("-fx-font-size: 12;");
        leftPanel.getChildren().add(dateLbl);

        // Status
        Label statusPill = new Label(
                iv.getStatus() == com.workctl.core.model.InterviewStatus.SCHEDULED ? "Scheduled" : "Completed");
        statusPill.getStyleClass()
                .add(iv.getStatus() == com.workctl.core.model.InterviewStatus.SCHEDULED ? "meeting-status-scheduled"
                        : "meeting-status-done");
        leftPanel.getChildren().add(statusPill);

        // Round
        if (iv.getRound() != null) {
            Label roundLbl = new Label(iv.getRound().label());
            roundLbl.getStyleClass().add("interview-round-badge");
            leftPanel.getChildren().add(roundLbl);
        }

        // Job URL
        if (iv.getJobUrl() != null) {
            Hyperlink jobLink = new Hyperlink("\uD83D\uDD17 Job Posting");
            jobLink.setStyle("-fx-font-size: 11;");
            jobLink.setTooltip(new Tooltip(iv.getJobUrl()));
            jobLink.setOnAction(e -> openUrl(iv.getJobUrl()));
            leftPanel.getChildren().add(jobLink);
        }

        // Question stats
        int total = iv.getTotalQuestionCount();
        if (total > 0) {
            Separator sep2 = new Separator();
            leftPanel.getChildren().add(sep2);

            int done = iv.getDoneQuestionCount();
            Label qLbl = new Label("\uD83D\uDCAC " + done + " / " + total + " questions");
            qLbl.setStyle("-fx-font-size: 11; -fx-text-fill: "
                    + (done == total ? "#27ae60" : "#718096") + ";");
            leftPanel.getChildren().add(qLbl);

            int imp = iv.getImportantQuestionCount();
            if (imp > 0) {
                Label impLbl = new Label("\u2B50 " + imp + " important");
                impLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #f59e0b;");
                leftPanel.getChildren().add(impLbl);
            }
        }

        // Experience links count
        int linkCount = iv.getExperienceLinks().size();
        if (linkCount > 0) {
            Label llbl = new Label("\uD83D\uDD17 " + linkCount + " experience link"
                    + (linkCount == 1 ? "" : "s"));
            llbl.setStyle("-fx-font-size: 11; -fx-text-fill: #60a5fa;");
            leftPanel.getChildren().add(llbl);
        }

        // ── Right: WebView with styled HTML content ────────────────────
        final String detailHtml = buildInterviewDetailHtml(iv);
        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);
        webView.getEngine().loadContent(detailHtml);

        // Intercept link clicks → open in system browser, reload content
        webView.getEngine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && (newLoc.startsWith("http://") || newLoc.startsWith("https://"))) {
                openUrl(newLoc);
                Platform.runLater(() -> webView.getEngine().loadContent(detailHtml));
            }
        });

        // ── Layout ─────────────────────────────────────────────────────
        SplitPane splitPane = new SplitPane(leftPanel, webView);
        splitPane.setDividerPositions(0.28);

        double prefW = Math.min(900, screenW - 80);
        double prefH = Math.min(640, screenH - 80);
        dialog.getDialogPane().setPrefSize(prefW, prefH);
        dialog.getDialogPane().setContent(splitPane);

        // ── Button actions ──────────────────────────────────────────────
        dialog.setResultConverter(btn -> {
            if (btn == editBtn) {
                showInterviewDialog(iv);
            } else if (btn == deleteBtn) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete interview \"" + iv.getCompany() + " \u2014 " + iv.getRole() + "\"?");
                confirm.setHeaderText(null);
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
                    interviewService.deleteInterview(iv.getId());
                    afterChange();
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    /** Build styled HTML for the detail WebView. */
    private String buildInterviewDetailHtml(Interview iv) {
        StringBuilder h = new StringBuilder();
        h.append("""
                <html><head><meta charset="UTF-8"><style>
                body { font-family: -apple-system, Arial, sans-serif; font-size: 14px;
                       line-height: 1.6; color: #1a202c; padding: 20px; margin: 0;
                       -webkit-user-select: text; user-select: text; background: #f8fafc; }
                h2 { font-size: 13px; font-weight: 700; color: #4a5568; margin: 18px 0 8px 0;
                     border-bottom: 1px solid #e2e8f0; padding-bottom: 4px;
                     text-transform: uppercase; letter-spacing: 0.06em; }
                .notes-block { background: #fff; border-left: 3px solid #3b82f6;
                               padding: 10px 14px; border-radius: 0 6px 6px 0;
                               border: 1px solid #e2e8f0; font-size: 13px; white-space: pre-wrap; }
                .link-row { display: flex; align-items: center; gap: 6px; padding: 6px 10px;
                            margin: 4px 0; background: #fff; border-radius: 6px;
                            border: 1px solid #e2e8f0; }
                .link-row a { color: #3b82f6; text-decoration: none; font-size: 13px; }
                .link-row a:hover { text-decoration: underline; }
                .sec-label { display: inline-block; background: #e2e8f0; color: #4a5568;
                             font-size: 10px; font-weight: 700; padding: 2px 8px;
                             border-radius: 12px; text-transform: uppercase;
                             letter-spacing: 0.05em; margin-bottom: 4px; }
                .q-row { display: flex; align-items: baseline; gap: 6px; padding: 3px 4px;
                         margin: 2px 0; border-radius: 4px; font-size: 13px; }
                .q-row:hover { background: #edf2f7; }
                .q-done { color: #a0aec0; text-decoration: line-through; }
                .q-check  { color: #27ae60; font-weight: bold; flex-shrink: 0; }
                .q-box    { color: #cbd5e0; flex-shrink: 0; }
                .q-star   { color: #f59e0b; font-size: 11px; }
                .q-url    { font-size: 11px; }
                .q-url a  { color: #3b82f6; text-decoration: none; }
                .q-url a:hover { text-decoration: underline; }
                .q-notes  { font-size: 11px; color: #718096; font-style: italic;
                            padding: 0 0 2px 22px; }
                </style></head><body>
                """);

        // Notes
        if (iv.getNotes() != null && !iv.getNotes().isBlank()) {
            h.append("<h2>Notes</h2>\n");
            h.append("<div class='notes-block'>").append(esc(iv.getNotes())).append("</div>\n");
        }

        // Experience Links
        if (!iv.getExperienceLinks().isEmpty()) {
            h.append("<h2>Experience Links</h2>\n");
            for (ExperienceLink link : iv.getExperienceLinks()) {
                h.append("<div class='link-row'>&#128279;&nbsp;<a href='")
                        .append(esc(link.getUrl())).append("'>")
                        .append(esc(link.getTitle())).append("</a></div>\n");
            }
        }

        // Questions grouped by section
        if (!iv.getQuestions().isEmpty()) {
            h.append("<h2>Questions</h2>\n");

            Map<String, List<InterviewQuestion>> bySection = new LinkedHashMap<>();
            for (InterviewQuestion q : iv.getQuestions()) {
                bySection.computeIfAbsent(q.getSection(), k -> new ArrayList<>()).add(q);
            }

            for (Map.Entry<String, List<InterviewQuestion>> entry : bySection.entrySet()) {
                long secDone = entry.getValue().stream().filter(InterviewQuestion::isDone).count();
                h.append("<div><span class='sec-label'>").append(esc(entry.getKey())).append("</span>")
                        .append(" <small style='color:#a0aec0;'>").append(secDone)
                        .append("/").append(entry.getValue().size()).append("</small></div>\n");

                for (InterviewQuestion q : entry.getValue()) {
                    h.append("<div class='q-row'>");
                    h.append("<span class='").append(q.isDone() ? "q-check" : "q-box").append("'>")
                            .append(q.isDone() ? "&#10003;" : "&#9633;").append("</span>");
                    h.append("<span class='").append(q.isDone() ? "q-done" : "").append("'>")
                            .append(esc(q.getText())).append("</span>");
                    if (q.isImportant())
                        h.append(" <span class='q-star'>&#11088;</span>");
                    if (q.getUrl() != null) {
                        h.append(" <span class='q-url'><a href='").append(esc(q.getUrl()))
                                .append("'>&#128279;</a></span>");
                    }
                    h.append("</div>\n");
                    if (q.getNotes() != null && !q.getNotes().isBlank()) {
                        h.append("<div class='q-notes'>").append(esc(q.getNotes())).append("</div>\n");
                    }
                }
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

    public void showInterviewDialog(Interview existing) {
        boolean isNew = (existing == null);

        // Compute safe dimensions from the primary screen
        double screenW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
        double dialogW = Math.min(620, screenW - 80);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "New Interview" : "Edit Interview");
        dialog.setHeaderText(isNew ? "Schedule Interview Session"
                : existing.getCompany() + " \u2014 " + existing.getRole());
        dialog.getDialogPane().setPrefWidth(dialogW);
        dialog.getDialogPane().setMaxHeight(screenH - 80);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(saveBtn);
        if (!isNew)
            dialog.getDialogPane().getButtonTypes()
                    .add(new ButtonType("Delete", ButtonBar.ButtonData.OTHER));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        VBox mainContent = new VBox(14);
        mainContent.setPadding(new Insets(16, 20, 10, 20));

        // ── Basic fields grid ──────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        TextField companyField = new TextField(existing == null ? "" : nvl(existing.getCompany()));
        companyField.setPromptText("Company name");
        GridPane.setHgrow(companyField, Priority.ALWAYS);

        TextField roleField = new TextField(existing == null ? "" : nvl(existing.getRole()));
        roleField.setPromptText("Job title / role");
        GridPane.setHgrow(roleField, Priority.ALWAYS);

        DatePicker datePicker = new DatePicker(
                existing != null && existing.getDateTime() != null
                        ? existing.getDateTime().toLocalDate()
                        : LocalDate.now());

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

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("Scheduled", "Completed");
        statusCombo
                .setValue(existing == null || existing.getStatus() == com.workctl.core.model.InterviewStatus.SCHEDULED
                        ? "Scheduled"
                        : "Completed");

        ComboBox<String> roundCombo = new ComboBox<>();
        for (InterviewRound r : InterviewRound.values())
            roundCombo.getItems().add(r.label());
        roundCombo.setValue(existing == null ? InterviewRound.TECHNICAL.label()
                : existing.getRound() != null ? existing.getRound().label() : InterviewRound.TECHNICAL.label());

        ComboBox<String> resultCombo = new ComboBox<>();
        resultCombo.getItems().addAll("Pending", "Offered", "Rejected");
        resultCombo.setValue(existing == null ? "Pending"
                : existing.getResult() == null ? "Pending"
                        : switch (existing.getResult()) {
                            case OFFERED -> "Offered";
                            case REJECTED -> "Rejected";
                            default -> "Pending";
                        });

        TextField jobUrlField = new TextField(existing == null ? "" : nvl(existing.getJobUrl()));
        jobUrlField.setPromptText("https://... job posting / calendar link (optional)");
        GridPane.setHgrow(jobUrlField, Priority.ALWAYS);

        TextArea notesArea = new TextArea(existing == null ? "" : nvl(existing.getNotes()));
        notesArea.setPromptText("Overall impressions, what to focus on…");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        grid.add(new Label("Company *"), 0, 0);
        grid.add(companyField, 1, 0);
        grid.add(new Label("Role *"), 0, 1);
        grid.add(roleField, 1, 1);
        grid.add(new Label("Date"), 0, 2);
        grid.add(datePicker, 1, 2);
        grid.add(new Label("Time"), 0, 3);
        grid.add(timeBox, 1, 3);
        grid.add(new Label("Status"), 0, 4);
        grid.add(statusCombo, 1, 4);
        grid.add(new Label("Round"), 0, 5);
        grid.add(roundCombo, 1, 5);
        grid.add(new Label("Result"), 0, 6);
        grid.add(resultCombo, 1, 6);
        grid.add(new Label("Job URL"), 0, 7);
        grid.add(jobUrlField, 1, 7);
        grid.add(new Label("Notes"), 0, 8);
        grid.add(notesArea, 1, 8);

        mainContent.getChildren().add(grid);

        // ── Experience Links section ───────────────────────────────────
        mainContent.getChildren().add(makeDivider("Experience Links",
                "Previous interview experiences, Glassdoor reviews, Notion notes\u2026"));

        List<ExperienceLink> liveLinks = new ArrayList<>(
                existing == null ? List.of() : existing.getExperienceLinks());
        VBox linksList = new VBox(4);
        refreshLinksList(linksList, liveLinks);

        TextField linkTitleField = new TextField();
        linkTitleField.setPromptText("Title  (e.g. \"Glassdoor Review\")");
        HBox.setHgrow(linkTitleField, Priority.ALWAYS);

        TextField linkUrlField = new TextField();
        linkUrlField.setPromptText("https://...");
        HBox.setHgrow(linkUrlField, Priority.ALWAYS);

        Button addLinkBtn = new Button("+ Add");
        addLinkBtn.getStyleClass().add("add-task-btn");
        addLinkBtn.setOnAction(e -> {
            String u = linkUrlField.getText().trim();
            String t = linkTitleField.getText().trim();
            if (!u.isBlank()) {
                liveLinks.add(new ExperienceLink(t.isBlank() ? u : t, u));
                linkTitleField.clear();
                linkUrlField.clear();
                refreshLinksList(linksList, liveLinks);
            }
        });

        HBox addLinkRow = new HBox(6, linkTitleField, linkUrlField, addLinkBtn);
        addLinkRow.setAlignment(Pos.CENTER_LEFT);

        mainContent.getChildren().addAll(linksList, addLinkRow);

        // ── Questions section ──────────────────────────────────────────
        mainContent.getChildren().add(makeDivider("Questions",
                "Track what you\u2019re asked / preparing. Each question can have a link + notes."));

        List<InterviewQuestion> liveQuestions = new ArrayList<>(
                existing == null ? List.of() : existing.getQuestions());
        VBox questionsList = new VBox(4);
        refreshQuestionList(questionsList, liveQuestions);

        ComboBox<String> sectionCombo = new ComboBox<>();
        sectionCombo.setEditable(true);
        sectionCombo.getItems().addAll(DEFAULT_SECTIONS);
        sectionCombo.setValue(DEFAULT_SECTIONS.get(0));
        sectionCombo.setPrefWidth(110);

        TextField qTextField = new TextField();
        qTextField.setPromptText("Question name / topic");
        HBox.setHgrow(qTextField, Priority.ALWAYS);

        TextField qUrlField = new TextField();
        qUrlField.setPromptText("URL (optional)");
        qUrlField.setPrefWidth(160);

        Button addQBtn = new Button("+ Add");
        addQBtn.getStyleClass().add("add-task-btn");
        addQBtn.setOnAction(e -> {
            String txt = qTextField.getText().trim();
            String section = sectionCombo.getValue();
            if (section == null || section.isBlank())
                section = "General";
            if (!txt.isBlank()) {
                String url = qUrlField.getText().trim();
                liveQuestions.add(new InterviewQuestion(section, txt,
                        url.isBlank() ? null : url, false, false, null));
                qTextField.clear();
                qUrlField.clear();
                refreshQuestionList(questionsList, liveQuestions);
            }
        });

        HBox addQRow = new HBox(6, sectionCombo, qTextField, qUrlField, addQBtn);
        addQRow.setAlignment(Pos.CENTER_LEFT);

        mainContent.getChildren().addAll(questionsList, addQRow);

        // ── Wrap in scroll pane — height adapts to screen ─────────────
        // Reserve ~160px for dialog chrome: title bar, header text, button bar
        ScrollPane scroll = new ScrollPane(mainContent);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(560, screenH - 160));
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        dialog.getDialogPane().setContent(scroll);

        // Disable Save until company + role filled
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveBtn);
        Runnable validate = () -> saveButton.setDisable(companyField.getText().trim().isEmpty()
                || roleField.getText().trim().isEmpty());
        validate.run();
        companyField.textProperty().addListener((o, old, n) -> validate.run());
        roleField.textProperty().addListener((o, old, n) -> validate.run());
        Platform.runLater(companyField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty())
            return;
        ButtonType clicked = result.get();

        // ── Delete ──────────────────────────────────────────────────────
        if (clicked.getButtonData() == ButtonBar.ButtonData.OTHER && existing != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete interview \"" + existing.getCompany() + " \u2014 " + existing.getRole() + "\"?");
            confirm.setHeaderText(null);
            if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
                interviewService.deleteInterview(existing.getId());
                afterChange();
            }
            return;
        }

        if (clicked != saveBtn)
            return;

        // ── Save ────────────────────────────────────────────────────────
        String company = companyField.getText().trim();
        String role = roleField.getText().trim();
        if (company.isBlank() || role.isBlank())
            return;

        LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
        java.time.LocalDateTime dateTime = date.atTime(hourSpinner.getValue(), minSpinner.getValue());

        com.workctl.core.model.InterviewStatus status = "Completed".equals(statusCombo.getValue())
                ? com.workctl.core.model.InterviewStatus.COMPLETED
                : com.workctl.core.model.InterviewStatus.SCHEDULED;

        InterviewRound round = InterviewRound.TECHNICAL;
        for (InterviewRound r : InterviewRound.values()) {
            if (r.label().equals(roundCombo.getValue())) {
                round = r;
                break;
            }
        }

        InterviewResult res = switch (resultCombo.getValue()) {
            case "Offered" -> InterviewResult.OFFERED;
            case "Rejected" -> InterviewResult.REJECTED;
            default -> InterviewResult.PENDING;
        };

        if (isNew) {
            Interview iv = interviewService.createInterview(company, role, dateTime);
            iv.setStatus(status);
            iv.setRound(round);
            iv.setResult(res);
            iv.setJobUrl(jobUrlField.getText().trim());
            iv.setNotes(notesArea.getText().trim());
            iv.setExperienceLinks(liveLinks);
            iv.setQuestions(liveQuestions);
            interviewService.saveInterview(iv);
        } else {
            existing.setCompany(company);
            existing.setRole(role);
            existing.setDateTime(dateTime);
            existing.setStatus(status);
            existing.setRound(round);
            existing.setResult(res);
            existing.setJobUrl(jobUrlField.getText().trim());
            existing.setNotes(notesArea.getText().trim());
            existing.setExperienceLinks(liveLinks);
            existing.setQuestions(liveQuestions);
            interviewService.saveInterview(existing);
        }

        afterChange();
    }

    // ─────────────────────────────────────────────────────────────────
    // DIALOG HELPERS
    // ─────────────────────────────────────────────────────────────────

    /** Rebuild the experience-links list inside the dialog. */
    private void refreshLinksList(VBox container, List<ExperienceLink> links) {
        container.getChildren().clear();
        for (int i = 0; i < links.size(); i++) {
            final int idx = i;
            ExperienceLink link = links.get(i);

            Hyperlink hyperlink = new Hyperlink("\uD83D\uDD17 " + link.getTitle());
            hyperlink.setStyle("-fx-font-size: 12;");
            hyperlink.setTooltip(new Tooltip(link.getUrl()));
            hyperlink.setOnAction(e -> openUrl(link.getUrl()));
            HBox.setHgrow(hyperlink, Priority.ALWAYS);

            Button removeBtn = new Button("\u2715");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c;"
                    + " -fx-cursor: hand; -fx-padding: 0 4;");
            removeBtn.setOnAction(e -> {
                links.remove(idx);
                refreshLinksList(container, links);
            });

            HBox row = new HBox(6, hyperlink,
                    new Region() {
                        {
                            HBox.setHgrow(this, Priority.ALWAYS);
                        }
                    }, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().add(row);
        }
    }

    /**
     * Rebuild the questions list inside the dialog. Questions are grouped by
     * section.
     */
    private void refreshQuestionList(VBox container, List<InterviewQuestion> questions) {
        container.getChildren().clear();

        // Group by section for display
        Map<String, List<InterviewQuestion>> bySection = new LinkedHashMap<>();
        for (InterviewQuestion q : questions) {
            bySection.computeIfAbsent(q.getSection(), k -> new ArrayList<>()).add(q);
        }

        for (Map.Entry<String, List<InterviewQuestion>> entry : bySection.entrySet()) {
            Label secHeader = new Label(entry.getKey());
            secHeader.setFont(Font.font(null, FontWeight.SEMI_BOLD, 11));
            secHeader.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 6 0 2 0;");
            container.getChildren().add(secHeader);

            for (InterviewQuestion q : entry.getValue()) {
                final int idx = questions.indexOf(q);

                CheckBox doneCb = new CheckBox(q.getText());
                doneCb.setSelected(q.isDone());
                if (q.isDone())
                    doneCb.setStyle("-fx-text-fill: #888;");
                doneCb.setOnAction(e -> q.setDone(doneCb.isSelected()));
                HBox.setHgrow(doneCb, Priority.ALWAYS);

                // URL link icon
                Button urlBtn = new Button("\uD83D\uDD17");
                urlBtn.setStyle("-fx-background-color: transparent; -fx-font-size: 11; -fx-cursor: hand;"
                        + " -fx-padding: 0 3; -fx-opacity: " + (q.getUrl() != null ? "1.0" : "0.3") + ";");
                urlBtn.setTooltip(new Tooltip(q.getUrl() != null ? q.getUrl() : "No URL set"));
                urlBtn.setOnAction(e -> {
                    if (q.getUrl() != null) {
                        openUrl(q.getUrl());
                    } else {
                        // Prompt for URL
                        TextInputDialog d = new TextInputDialog();
                        d.setTitle("Set URL");
                        d.setHeaderText("Add URL for: " + q.getText());
                        d.setContentText("URL:");
                        d.showAndWait().ifPresent(url -> {
                            if (!url.isBlank()) {
                                q.setUrl(url.trim());
                                urlBtn.setOpacity(1.0);
                                urlBtn.setTooltip(new Tooltip(url.trim()));
                            }
                        });
                    }
                });

                // Star button (important toggle)
                Button starBtn = new Button(q.isImportant() ? "\u2B50" : "\u2606");
                starBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"
                        + " -fx-font-size: 13; -fx-padding: 0 3;");
                starBtn.setOnAction(e -> {
                    q.setImportant(!q.isImportant());
                    starBtn.setText(q.isImportant() ? "\u2B50" : "\u2606");
                });

                Button removeBtn = new Button("\u2715");
                removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c;"
                        + " -fx-cursor: hand; -fx-padding: 0 3;");
                removeBtn.setOnAction(e -> {
                    questions.remove(idx);
                    refreshQuestionList(container, questions);
                });

                HBox row = new HBox(4, doneCb,
                        new Region() {
                            {
                                HBox.setHgrow(this, Priority.ALWAYS);
                            }
                        },
                        urlBtn, starBtn, removeBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-padding: 1 0 1 4;");
                container.getChildren().add(row);
            }
        }
    }

    /** Creates a styled section divider with a bold label and description. */
    private Node makeDivider(String title, String description) {
        VBox box = new VBox(2);
        box.setStyle("-fx-padding: 6 0 2 0;");

        Label titleLbl = new Label(title);
        titleLbl.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label descLbl = new Label(description);
        descLbl.setStyle("-fx-text-fill: #718096; -fx-font-size: 10;");
        descLbl.setWrapText(true);

        Separator sep = new Separator();
        sep.setStyle("-fx-padding: 2 0 0 0;");

        box.getChildren().addAll(sep, titleLbl, descLbl);
        return box;
    }

    /** Opens a URL in the system default browser. Best-effort; never throws. */
    private static void openUrl(String url) {
        if (url == null || url.isBlank())
            return;
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url.startsWith("http") ? url : "https://" + url));
        } catch (Exception ignored) {
        }
    }

    private void afterChange() {
        refreshInterviews();
        refreshPreparationTab();
        if (onInterviewChanged != null)
            onInterviewChanged.run();
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
