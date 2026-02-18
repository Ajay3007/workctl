package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Project;
import com.workctl.core.service.ProjectService;
import com.workctl.gui.ProjectContext;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainController {

    @FXML
    private ListView<String> projectListView;

    private final ProjectService projectService = new ProjectService();

    @FXML
    public void initialize() {

        try {
            AppConfig config = ConfigManager.load();
            Path workspace = Paths.get(config.getWorkspace());

            List<Project> projects = projectService.listProjects(workspace);

            for (Project p : projects) {
                projectListView.getItems().add(p.getName());
            }

            projectListView.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((obs, oldVal, newVal) -> {
                        if (newVal != null) {
                            ProjectContext.setCurrentProject(newVal);
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
