package com.workctl.gui.controller;

import com.workctl.core.domain.CommandEntry;
import com.workctl.core.domain.Project;
import com.workctl.core.service.CommandService;
import com.workctl.core.service.ProjectService;
import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
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

public class CommandController {

    @FXML
    private ComboBox<String> projectFilterCombo;
    @FXML
    private ListView<String> categoryListView;
    @FXML
    private TextField searchField;
    @FXML
    private VBox commandsContainer;
    @FXML
    private Label headerLabel;

    private final CommandService commandService = new CommandService();
    private final ProjectService projectService = new ProjectService();

    private List<CommandEntry> allCommands;

    // State
    private String selectedCategory = "All"; // "All" or a specific category

    // Constants
    private static final String ALL_PROJECTS = "All Projects";

    @FXML
    public void initialize() {
        setupFilters();
        loadData();

        // Listen for project context changes from the sidebar
        ProjectContext.addListener(newVal -> {
            if (newVal != null && projectFilterCombo.getItems().contains(newVal)) {
                projectFilterCombo.getSelectionModel().select(newVal);
            } else {
                projectFilterCombo.getSelectionModel().select(ALL_PROJECTS);
            }
        });

        // Search listener
        searchField.textProperty().addListener((obs, oldVal, newVal) -> renderCommands());

        // Category selection
        categoryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedCategory = newVal;
                headerLabel.setText(selectedCategory.equals("All") ? "All Commands" : selectedCategory + " Commands");
                renderCommands();
            }
        });
    }

    private void setupFilters() {
        projectFilterCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateCategoriesForScope();
            renderCommands();
        });
        refreshProjects();
    }

    public void refreshProjects() {
        try {
            AppConfig config = ConfigManager.load();
            List<Project> projects = projectService.listProjects(Paths.get(config.getWorkspace()));

            List<String> items = projects.stream().map(Project::getName).collect(Collectors.toList());
            if (!items.contains("workctl")) {
                items.add(0, "workctl");
            }
            items.add(0, "GLOBAL");
            items.add(0, ALL_PROJECTS);

            String currentSelection = projectFilterCombo.getValue();
            projectFilterCombo.setItems(FXCollections.observableArrayList(items));

            if (currentSelection != null && items.contains(currentSelection)) {
                projectFilterCombo.getSelectionModel().select(currentSelection);
            } else {
                projectFilterCombo.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCategoriesForScope() {
        if (allCommands == null)
            return;

        String scope = projectFilterCombo.getValue();
        if (scope == null)
            scope = ALL_PROJECTS;

        final String activeScope = scope;
        List<String> validCategories = allCommands.stream()
                .filter(c -> {
                    if (activeScope.equals(ALL_PROJECTS))
                        return true;
                    return c.getProjectTag().equals(activeScope);
                })
                .map(CommandEntry::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        validCategories.add(0, "All");

        String currentCategory = selectedCategory;
        categoryListView.setItems(FXCollections.observableArrayList(validCategories));

        if (validCategories.contains(currentCategory)) {
            categoryListView.getSelectionModel().select(currentCategory);
        } else {
            categoryListView.getSelectionModel().select("All");
            selectedCategory = "All";
        }

        headerLabel.setText(selectedCategory.equals("All") ? "All Commands" : selectedCategory + " Commands");
    }

    private void loadData() {
        allCommands = commandService.loadAllCommands();
        updateCategoriesForScope();
        renderCommands();
    }

    private void renderCommands() {
        if (allCommands == null) {
            return;
        }
        commandsContainer.getChildren().clear();

        String searchTarget = searchField.getText().toLowerCase();
        String activeProjectFilter = projectFilterCombo.getValue();

        List<CommandEntry> filtered = allCommands.stream()
                // Filter by Category
                .filter(c -> selectedCategory.equals("All") || c.getCategory().equals(selectedCategory))
                // Filter by Project Context strictly
                .filter(c -> {
                    if (activeProjectFilter == null || activeProjectFilter.equals(ALL_PROJECTS))
                        return true;
                    return c.getProjectTag().equals(activeProjectFilter);
                })
                // Filter by Search Text
                .filter(c -> {
                    if (searchTarget.isEmpty())
                        return true;
                    return c.getTitle().toLowerCase().contains(searchTarget)
                            || c.getCommand().toLowerCase().contains(searchTarget);
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label("No commands found.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 20;");
            commandsContainer.getChildren().add(empty);
            return;
        }

        for (CommandEntry cmd : filtered) {
            commandsContainer.getChildren().add(buildCommandCard(cmd));
        }
    }

    private Node buildCommandCard(CommandEntry cmd) {
        VBox card = new VBox(8);
        card.getStyleClass().add("task-card"); // Reuse existing styling
        card.setPadding(new Insets(12));

        // Header: Title and Project Tag
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(cmd.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        title.setWrapText(true);

        Label tag = new Label(cmd.getProjectTag());
        tag.getStyleClass().add("project-pill"); // Reuse project pill styling if available
        tag.setStyle(
                "-fx-font-size: 10px; -fx-padding: 2 6; -fx-background-color: #e2e8f0; -fx-background-radius: 10; -fx-text-fill: #475569;");
        tag.setMinWidth(Label.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Actions
        Button editBtn = new Button("Edit");
        editBtn.setStyle("-fx-font-size: 11px;");
        editBtn.setMinWidth(Button.USE_PREF_SIZE);
        editBtn.setOnAction(e -> handleEditCommand(cmd));

        Button delBtn = new Button("Delete");
        delBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c;");
        delBtn.setMinWidth(Button.USE_PREF_SIZE);
        delBtn.setOnAction(e -> {
            commandService.deleteCommand(cmd);
            loadData();
        });

        header.getChildren().addAll(title, tag, spacer, editBtn, delBtn);

        // Command Block
        HBox cmdBlock = new HBox(10);
        cmdBlock.setAlignment(Pos.CENTER_LEFT);
        cmdBlock.setStyle("-fx-background-color: #1e293b; -fx-padding: 10; -fx-background-radius: 6;");

        Label cmdText = new Label(cmd.getCommand());
        cmdText.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-text-fill: #10b981; -fx-font-weight: bold;");
        cmdText.setWrapText(true);

        // Fix for long text pushing layout:
        HBox.setHgrow(cmdText, Priority.ALWAYS);
        cmdText.setMaxWidth(Double.MAX_VALUE);

        Button copyBtn = new Button("📄 Copy");
        copyBtn.setStyle("-fx-font-size: 11px;");
        copyBtn.setMinWidth(Button.USE_PREF_SIZE); // Maintain button size
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(cmd.getCommand());
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("✓ Copied");
            Platform.runLater(() -> {
                try {
                    Thread.sleep(1500);
                } catch (Exception ignored) {
                }
                Platform.runLater(() -> copyBtn.setText("📄 Copy"));
            });
        });

        cmdBlock.getChildren().addAll(cmdText, copyBtn);

        card.getChildren().addAll(header, cmdBlock);

        // Notes block if present
        if (cmd.getNotes() != null && !cmd.getNotes().isEmpty()) {
            Label notes = new Label(cmd.getNotes());
            notes.setWrapText(true);
            notes.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px; -fx-padding: 5 0 0 0;");
            card.getChildren().add(notes);
        }

        return card;
    }

    @FXML
    public void handleAddCommand() {
        showCommandDialog(null);
    }

    private void handleEditCommand(CommandEntry cmd) {
        showCommandDialog(cmd);
    }

    private void showCommandDialog(CommandEntry existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Command" : "Edit Command");

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField catField = new TextField(
                existing == null ? (selectedCategory.equals("All") ? "" : selectedCategory) : existing.getCategory());
        catField.setPromptText("e.g. docker, git, gradle");

        TextField titleField = new TextField(existing == null ? "" : existing.getTitle());
        titleField.setPromptText("What does this do?");

        TextArea cmdField = new TextArea(existing == null ? "" : existing.getCommand());
        cmdField.setPromptText("Enter the CLI command");
        cmdField.setPrefRowCount(3);
        cmdField.setStyle("-fx-font-family: 'Consolas', monospace;");

        TextArea notesField = new TextArea(existing == null ? "" : existing.getNotes());
        notesField.setPromptText("Any additional notes or explanations...");
        notesField.setPrefRowCount(3);

        ComboBox<String> projectCombo = new ComboBox<>();
        projectCombo.getItems().addAll(projectFilterCombo.getItems());
        projectCombo.getItems().remove(ALL_PROJECTS);

        if (existing != null) {
            projectCombo.getSelectionModel().select(existing.getProjectTag());
        } else {
            String activeFilter = projectFilterCombo.getValue();
            if (activeFilter != null && !activeFilter.equals(ALL_PROJECTS)) {
                projectCombo.getSelectionModel().select(activeFilter);
            } else {
                projectCombo.getSelectionModel().select("GLOBAL");
            }
        }

        grid.add(new Label("Category *"), 0, 0);
        grid.add(catField, 1, 0);
        grid.add(new Label("Title *"), 0, 1);
        grid.add(titleField, 1, 1);
        grid.add(new Label("Command *"), 0, 2);
        grid.add(cmdField, 1, 2);
        grid.add(new Label("Target *"), 0, 3);
        grid.add(projectCombo, 1, 3);
        grid.add(new Label("Notes"), 0, 4);
        grid.add(notesField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Validation
        Node saveBtn = dialog.getDialogPane().lookupButton(saveBtnType);
        saveBtn.setDisable(existing == null);

        Runnable validator = () -> {
            boolean valid = !catField.getText().trim().isEmpty() &&
                    !titleField.getText().trim().isEmpty() &&
                    !cmdField.getText().trim().isEmpty();
            saveBtn.setDisable(!valid);
        };

        catField.textProperty().addListener((o, old, nw) -> validator.run());
        titleField.textProperty().addListener((o, old, nw) -> validator.run());
        cmdField.textProperty().addListener((o, old, nw) -> validator.run());

        Platform.runLater(titleField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result == saveBtnType) {
                CommandEntry entry = existing == null ? new CommandEntry() : existing;

                // If category changed, we need to delete the old one first because the file
                // changes
                if (existing != null && !existing.getCategory().equals(catField.getText().trim())) {
                    commandService.deleteCommand(existing);
                }

                entry.setCategory(catField.getText().trim().toLowerCase().replaceAll("\\s+", "-"));
                entry.setTitle(titleField.getText().trim());
                entry.setCommand(cmdField.getText().trim());
                entry.setProjectTag(projectCombo.getValue());
                entry.setNotes(notesField.getText().trim());

                commandService.saveCommand(entry);
                loadData();
            }
        });
    }
}
