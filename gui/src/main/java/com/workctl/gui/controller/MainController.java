package com.workctl.gui.controller;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Project;
import com.workctl.core.service.ProjectService;
import com.workctl.gui.ProjectContext;
import com.workctl.gui.agent.AgentPanel;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MainController {

    @FXML
    private ListView<String> projectListView;

    @FXML
    private TabPane mainTabPane;    // wire the TabPane from FXML

    private final ProjectService projectService = new ProjectService();

    private AgentPanel agentPanel;   // hold reference to update on project change


    @FXML
    public void initialize() {

        try {
            // Create AgentPanel and add as a tab
            agentPanel = new AgentPanel(null);
            Tab agentTab = new Tab("🤖 AI Agent", agentPanel);
            agentTab.setClosable(false);
            mainTabPane.getTabs().add(agentTab);

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
                            agentPanel.setProject(newVal);   // notify agent panel
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
