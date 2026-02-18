package com.workctl.gui.controller;

import com.workctl.core.service.ProjectService;
import com.workctl.core.domain.Project;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/**
 * Project management controller.
 */
public class ProjectController {
    @FXML
    private TextField projectNameField;

    @FXML
    private TextField projectDescriptionField;

    @FXML
    private ListView<String> projectListView;

    private ProjectService projectService = new ProjectService();

    @FXML
    public void initialize() {
        // Initialize controller
    }

    @FXML
    public void createProject() {
        String name = projectNameField.getText();
        String description = projectDescriptionField.getText();
        
        if (!name.isEmpty()) {
//            Project project = projectService.createProject(name, description);
//            projectListView.getItems().add(name);
//            projectNameField.clear();
//            projectDescriptionField.clear();
        }
    }
}
