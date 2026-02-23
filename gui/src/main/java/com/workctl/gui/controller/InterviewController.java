package com.workctl.gui.controller;

import com.workctl.core.domain.Interview;
import com.workctl.core.domain.Interview.ExperienceLink;
import com.workctl.core.domain.Interview.InterviewQuestion;
import com.workctl.core.domain.PrepTopic;
import com.workctl.core.model.InterviewResult;
import com.workctl.core.model.InterviewRound;
import com.workctl.core.service.InterviewService;
import com.workctl.core.service.PrepTopicService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
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

    @FXML private ComboBox<String> roundFilterCombo;
    @FXML private ComboBox<String> resultFilterCombo;
    @FXML private VBox             interviewListVBox;

    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private VBox             prepTopicsVBox;

    private final InterviewService interviewService = new InterviewService();
    private final PrepTopicService prepTopicService = new PrepTopicService();

    private Runnable onInterviewChanged;

    private static final DateTimeFormatter CARD_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");

    // Default topic categories and question sections
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("DSA", "System Design", "OS", "Networking", "Behavioral", "Language/Framework");
    private static final List<String> DEFAULT_SECTIONS =
            List.of("DSA", "System Design", "OS", "Networking", "Behavioral", "Other");

    // ─────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Round filter
        roundFilterCombo.getItems().add("All Rounds");
        for (InterviewRound r : InterviewRound.values()) roundFilterCombo.getItems().add(r.label());
        roundFilterCombo.setValue("All Rounds");
        roundFilterCombo.setOnAction(e -> refreshInterviews());

        // Result filter
        resultFilterCombo.getItems().addAll("All Results", "Pending", "Offered", "Rejected");
        resultFilterCombo.setValue("All Results");
        resultFilterCombo.setOnAction(e -> refreshInterviews());

        // Category filter for Prep Topics
        categoryFilterCombo.getItems().add("All Categories");
        categoryFilterCombo.getItems().addAll(DEFAULT_CATEGORIES);
        categoryFilterCombo.setValue("All Categories");
        categoryFilterCombo.setOnAction(e -> refreshPrepTopics());

        refreshInterviews();
        refreshPrepTopics();
    }

    public void setOnInterviewChanged(Runnable callback) { this.onInterviewChanged = callback; }

    public void refresh() { refreshInterviews(); refreshPrepTopics(); }

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
                    case "Offered"  -> InterviewResult.OFFERED;
                    case "Rejected" -> InterviewResult.REJECTED;
                    default         -> InterviewResult.PENDING;
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

            for (Interview iv : interviews) interviewListVBox.getChildren().add(buildInterviewCard(iv));
        } catch (Exception e) { e.printStackTrace(); }
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
        companyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");

        Label sep  = new Label("\u00B7");
        sep.setStyle("-fx-text-fill: #718096;");

        Label roleLabel = new Label(iv.getRole());
        roleLabel.setStyle("-fx-font-size: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(companyLabel, sep, roleLabel, spacer, buildResultPill(iv.getResult()));

        // ── Mid row: date + round badge + job URL icon ──
        HBox midRow = new HBox(8);
        midRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(iv.getDate() != null ? iv.getDate().format(CARD_DATE_FMT) : "\u2014");
        dateLabel.setStyle("-fx-text-fill: #718096; -fx-font-size: 11;");
        midRow.getChildren().add(dateLabel);

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
        if (!bottomRow.getChildren().isEmpty()) card.getChildren().add(bottomRow);
        card.setOnMouseClicked(e -> showInterviewDetail(iv));
        return card;
    }

    private Label buildResultPill(InterviewResult result) {
        String text = result == null ? "Pending" : switch (result) {
            case OFFERED  -> "Offered";
            case REJECTED -> "Rejected";
            default       -> "Pending";
        };
        String styleClass = result == null ? "interview-result-pending" : switch (result) {
            case OFFERED  -> "interview-result-offered";
            case REJECTED -> "interview-result-rejected";
            default       -> "interview-result-pending";
        };
        Label pill = new Label(text);
        pill.getStyleClass().add(styleClass);
        return pill;
    }

    // ─────────────────────────────────────────────────────────────────
    // PREP TOPICS — 3-LEVEL VIEW (Category → Section → Items)
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void refreshPrepTopics() {
        try {
            List<PrepTopic> topics = prepTopicService.loadAll();

            String catFilter = categoryFilterCombo.getValue();
            if (catFilter != null && !catFilter.equals("All Categories")) {
                topics = topics.stream()
                        .filter(t -> t.getCategory().equalsIgnoreCase(catFilter))
                        .toList();
            }

            prepTopicsVBox.getChildren().clear();

            if (topics.isEmpty()) {
                Label empty = new Label("No prep topics yet.  Click '+ Add Topic' to get started.");
                empty.setStyle("-fx-text-fill: #718096; -fx-font-size: 12; -fx-padding: 10;");
                prepTopicsVBox.getChildren().add(empty);
                return;
            }

            // Build tree: category → section (or "" if none) → items
            Map<String, Map<String, List<PrepTopic>>> tree = new LinkedHashMap<>();
            for (PrepTopic t : topics) {
                String sec = t.getSection() != null ? t.getSection() : "";
                tree.computeIfAbsent(t.getCategory(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(sec, k -> new ArrayList<>())
                    .add(t);
            }

            for (Map.Entry<String, Map<String, List<PrepTopic>>> catEntry : tree.entrySet()) {
                prepTopicsVBox.getChildren().add(buildCategoryBlock(catEntry.getKey(), catEntry.getValue()));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Node buildCategoryBlock(String category, Map<String, List<PrepTopic>> sections) {
        VBox block = new VBox(2);
        block.setStyle("-fx-padding: 0 0 10 0;");

        // ── Category header + "Add Section" button ──
        HBox catHeader = new HBox(8);
        catHeader.setAlignment(Pos.CENTER_LEFT);
        catHeader.setStyle("-fx-padding: 6 0 4 0; -fx-border-color: transparent transparent #4a5568 transparent;"
                + " -fx-border-width: 0 0 1 0;");

        Label catLabel = new Label(category);
        catLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
        catLabel.getStyleClass().add("prep-topic-category");

        // count
        long total = sections.values().stream().mapToLong(List::size).sum();
        long done  = sections.values().stream().flatMap(Collection::stream).filter(PrepTopic::isDone).count();
        Label progress = new Label(done + "/" + total);
        progress.setStyle("-fx-text-fill: " + (done == total && total > 0 ? "#27ae60" : "#718096")
                + "; -fx-font-size: 10;");

        Region catSpacer = new Region();
        HBox.setHgrow(catSpacer, Priority.ALWAYS);

        Button addSectionBtn = new Button("+ Section");
        addSectionBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #60a5fa;"
                + " -fx-font-size: 10; -fx-cursor: hand; -fx-padding: 1 6;");
        addSectionBtn.setOnAction(e -> showAddSectionDialog(category));

        Button addItemBtn = new Button("+ Item");
        addItemBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8;"
                + " -fx-font-size: 10; -fx-cursor: hand; -fx-padding: 1 6;");
        addItemBtn.setOnAction(e -> showAddTopicDialog(category, null));

        catHeader.getChildren().addAll(catLabel, progress, catSpacer, addItemBtn, addSectionBtn);
        block.getChildren().add(catHeader);

        // ── Sections ──
        for (Map.Entry<String, List<PrepTopic>> secEntry : sections.entrySet()) {
            String sectionName = secEntry.getKey();
            List<PrepTopic> items = secEntry.getValue();

            if (!sectionName.isBlank()) {
                // Section sub-header
                HBox secHeader = new HBox(6);
                secHeader.setAlignment(Pos.CENTER_LEFT);
                secHeader.setStyle("-fx-padding: 6 0 2 8;");

                Label secLabel = new Label(sectionName);
                secLabel.setFont(Font.font(null, FontWeight.SEMI_BOLD, 12));
                secLabel.setStyle("-fx-text-fill: #94a3b8;");

                long sDone  = items.stream().filter(PrepTopic::isDone).count();
                Label secProg = new Label(sDone + "/" + items.size());
                secProg.setStyle("-fx-text-fill: #718096; -fx-font-size: 10;");

                Region sSpacer = new Region();
                HBox.setHgrow(sSpacer, Priority.ALWAYS);

                Button addToSecBtn = new Button("+ Item");
                addToSecBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8;"
                        + " -fx-font-size: 10; -fx-cursor: hand; -fx-padding: 1 4;");
                addToSecBtn.setOnAction(e -> showAddTopicDialog(category, sectionName));

                secHeader.getChildren().addAll(secLabel, secProg, sSpacer, addToSecBtn);
                block.getChildren().add(secHeader);
            }

            // Items
            for (PrepTopic t : items) {
                block.getChildren().add(buildTopicRow(t, !sectionName.isBlank()));
            }
        }

        return block;
    }

    private Node buildTopicRow(PrepTopic topic, boolean indented) {
        CheckBox cb = new CheckBox(topic.getName());
        cb.setSelected(topic.isDone());
        if (topic.isDone()) cb.setStyle("-fx-text-fill: #888;");
        cb.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cb, Priority.ALWAYS);
        cb.setOnAction(e -> {
            prepTopicService.toggleDone(topic.getId());
            refreshPrepTopics();
        });

        Button removeBtn = new Button("\u2715");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c;"
                + " -fx-cursor: hand; -fx-padding: 0 3;");
        removeBtn.setOnAction(e -> {
            prepTopicService.deleteTopic(topic.getId());
            refreshPrepTopics();
        });

        HBox row = new HBox(4, cb, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 2 0 2 " + (indented ? "20" : "4") + ";");
        return row;
    }

    // ─────────────────────────────────────────────────────────────────
    // ADD TOPIC / SECTION HANDLERS
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void handleAddTopic() {
        showAddTopicDialog(null, null);
    }

    private void showAddTopicDialog(String preCategory, String preSection) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Prep Item");
        dialog.setHeaderText("New Study Item");
        dialog.getDialogPane().setPrefWidth(380);

        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16, 20, 10, 20));

        ComboBox<String> catCombo = new ComboBox<>();
        catCombo.setEditable(true);
        catCombo.getItems().addAll(DEFAULT_CATEGORIES);
        catCombo.setValue(preCategory != null ? preCategory : DEFAULT_CATEGORIES.get(0));
        GridPane.setHgrow(catCombo, Priority.ALWAYS);

        ComboBox<String> secCombo = new ComboBox<>();
        secCombo.setEditable(true);
        secCombo.setPromptText("e.g. Arrays, Trees  (optional)");
        if (preSection != null) secCombo.setValue(preSection);
        // Populate existing sections for selected category
        populateSectionCombo(secCombo, catCombo.getValue());
        catCombo.setOnAction(e -> populateSectionCombo(secCombo, catCombo.getValue()));
        GridPane.setHgrow(secCombo, Priority.ALWAYS);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Two Pointer, TCP/IP Handshake");
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("Category"),  0, 0); grid.add(catCombo,  1, 0);
        grid.add(new Label("Section"),   0, 1); grid.add(secCombo,  1, 1);
        grid.add(new Label("Item *"),    0, 2); grid.add(nameField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Button addButton = (Button) dialog.getDialogPane().lookupButton(addBtn);
        addButton.setDisable(true);
        nameField.textProperty().addListener((o, old, n) -> addButton.setDisable(n.trim().isEmpty()));
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == addBtn) {
                String name     = nameField.getText().trim();
                String category = nvl(catCombo.getValue(), DEFAULT_CATEGORIES.get(0));
                String section  = nvl(secCombo.getValue(), null);
                if (!name.isBlank()) {
                    prepTopicService.addTopic(category, section, name);
                    updateCategoryFilter(category);
                    refreshPrepTopics();
                }
            }
        });
    }

    private void showAddSectionDialog(String category) {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Section");
        d.setHeaderText("New section under \"" + category + "\"");
        d.setContentText("Section name (e.g. Arrays, Trees):");
        d.showAndWait().ifPresent(sectionName -> {
            if (!sectionName.isBlank()) {
                // Add a placeholder item under this section so the section persists
                prepTopicService.addTopic(category, sectionName.trim(), "(placeholder — replace me)");
                refreshPrepTopics();
            }
        });
    }

    private void populateSectionCombo(ComboBox<String> secCombo, String category) {
        String current = secCombo.getValue();
        secCombo.getItems().clear();
        if (category != null) {
            prepTopicService.loadAll().stream()
                    .filter(t -> t.getCategory().equalsIgnoreCase(category)
                              && t.getSection() != null)
                    .map(PrepTopic::getSection)
                    .distinct()
                    .forEach(secCombo.getItems()::add);
        }
        if (current != null && !current.isBlank()) secCombo.setValue(current);
    }

    private void updateCategoryFilter(String category) {
        if (!categoryFilterCombo.getItems().contains(category)) {
            categoryFilterCombo.getItems().add(category);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // NEW INTERVIEW HANDLER
    // ─────────────────────────────────────────────────────────────────

    @FXML
    public void handleNewInterview() { showInterviewDialog(null); }

    // ─────────────────────────────────────────────────────────────────
    // READ-ONLY DETAIL VIEW  (click on card → opens this)
    // ─────────────────────────────────────────────────────────────────

    public void showInterviewDetail(Interview iv) {
        double screenW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth();
        double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Interview: " + iv.getCompany() + " \u2014 " + iv.getRole());

        ButtonType closeBtn  = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType editBtn   = new ButtonType("Edit",  ButtonBar.ButtonData.OTHER);
        ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(deleteBtn, editBtn, closeBtn);

        // ── Left metadata panel ────────────────────────────────────────
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(20, 16, 20, 20));
        leftPanel.setPrefWidth(210);

        Label companyLbl = new Label(iv.getCompany());
        companyLbl.setStyle("-fx-font-size: 17; -fx-font-weight: bold;");
        companyLbl.setWrapText(true);

        Label roleLbl = new Label(iv.getRole());
        roleLbl.setStyle("-fx-font-size: 12; -fx-text-fill: #718096;");
        roleLbl.setWrapText(true);

        leftPanel.getChildren().addAll(companyLbl, roleLbl, buildResultPill(iv.getResult()));

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-padding: 2 0;");
        leftPanel.getChildren().add(sep1);

        // Date
        Label dateLbl = new Label("\uD83D\uDCC5  "
                + (iv.getDate() != null ? iv.getDate().format(CARD_DATE_FMT) : "\u2014"));
        dateLbl.setStyle("-fx-font-size: 12;");
        leftPanel.getChildren().add(dateLbl);

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
                    if (q.isImportant()) h.append(" <span class='q-star'>&#11088;</span>");
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
        if (s == null) return "";
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
        if (!isNew) dialog.getDialogPane().getButtonTypes()
                .add(new ButtonType("Delete", ButtonBar.ButtonData.OTHER));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        VBox mainContent = new VBox(14);
        mainContent.setPadding(new Insets(16, 20, 10, 20));

        // ── Basic fields grid ──────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);

        TextField companyField = new TextField(existing == null ? "" : nvl(existing.getCompany()));
        companyField.setPromptText("Company name");
        GridPane.setHgrow(companyField, Priority.ALWAYS);

        TextField roleField = new TextField(existing == null ? "" : nvl(existing.getRole()));
        roleField.setPromptText("Job title / role");
        GridPane.setHgrow(roleField, Priority.ALWAYS);

        DatePicker datePicker = new DatePicker(
                existing != null && existing.getDate() != null ? existing.getDate() : LocalDate.now());

        ComboBox<String> roundCombo = new ComboBox<>();
        for (InterviewRound r : InterviewRound.values()) roundCombo.getItems().add(r.label());
        roundCombo.setValue(existing == null ? InterviewRound.TECHNICAL.label()
                : existing.getRound() != null ? existing.getRound().label() : InterviewRound.TECHNICAL.label());

        ComboBox<String> resultCombo = new ComboBox<>();
        resultCombo.getItems().addAll("Pending", "Offered", "Rejected");
        resultCombo.setValue(existing == null ? "Pending"
                : existing.getResult() == null ? "Pending"
                : switch (existing.getResult()) {
                    case OFFERED -> "Offered"; case REJECTED -> "Rejected"; default -> "Pending"; });

        TextField jobUrlField = new TextField(existing == null ? "" : nvl(existing.getJobUrl()));
        jobUrlField.setPromptText("https://... job posting / calendar link (optional)");
        GridPane.setHgrow(jobUrlField, Priority.ALWAYS);

        TextArea notesArea = new TextArea(existing == null ? "" : nvl(existing.getNotes()));
        notesArea.setPromptText("Overall impressions, what to focus on…");
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        grid.add(new Label("Company *"), 0, 0); grid.add(companyField, 1, 0);
        grid.add(new Label("Role *"),    0, 1); grid.add(roleField,    1, 1);
        grid.add(new Label("Date"),      0, 2); grid.add(datePicker,   1, 2);
        grid.add(new Label("Round"),     0, 3); grid.add(roundCombo,   1, 3);
        grid.add(new Label("Result"),    0, 4); grid.add(resultCombo,  1, 4);
        grid.add(new Label("Job URL"),   0, 5); grid.add(jobUrlField,  1, 5);
        grid.add(new Label("Notes"),     0, 6); grid.add(notesArea,    1, 6);

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
                linkTitleField.clear(); linkUrlField.clear();
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
            String txt     = qTextField.getText().trim();
            String section = sectionCombo.getValue();
            if (section == null || section.isBlank()) section = "General";
            if (!txt.isBlank()) {
                String url = qUrlField.getText().trim();
                liveQuestions.add(new InterviewQuestion(section, txt,
                        url.isBlank() ? null : url, false, false, null));
                qTextField.clear(); qUrlField.clear();
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
        Runnable validate = () ->
                saveButton.setDisable(companyField.getText().trim().isEmpty()
                        || roleField.getText().trim().isEmpty());
        validate.run();
        companyField.textProperty().addListener((o, old, n) -> validate.run());
        roleField.textProperty().addListener((o, old, n) -> validate.run());
        Platform.runLater(companyField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty()) return;
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

        if (clicked != saveBtn) return;

        // ── Save ────────────────────────────────────────────────────────
        String company = companyField.getText().trim();
        String role    = roleField.getText().trim();
        if (company.isBlank() || role.isBlank()) return;

        LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();

        InterviewRound round = InterviewRound.TECHNICAL;
        for (InterviewRound r : InterviewRound.values()) {
            if (r.label().equals(roundCombo.getValue())) { round = r; break; }
        }

        InterviewResult res = switch (resultCombo.getValue()) {
            case "Offered"  -> InterviewResult.OFFERED;
            case "Rejected" -> InterviewResult.REJECTED;
            default         -> InterviewResult.PENDING;
        };

        if (isNew) {
            Interview iv = interviewService.createInterview(company, role, date);
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
            existing.setDate(date);
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
                    new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().add(row);
        }
    }

    /** Rebuild the questions list inside the dialog. Questions are grouped by section. */
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
                if (q.isDone()) doneCb.setStyle("-fx-text-fill: #888;");
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
                        new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
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
        if (url == null || url.isBlank()) return;
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url.startsWith("http") ? url : "https://" + url));
        } catch (Exception ignored) {}
    }

    private void afterChange() {
        refreshInterviews();
        refreshPrepTopics();
        if (onInterviewChanged != null) onInterviewChanged.run();
    }

    private static String nvl(String s, String fallback) { return (s == null || s.isBlank()) ? fallback : s.trim(); }
    private static String nvl(String s) { return s == null ? "" : s; }
}
