package com.workctl.gui;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class SetupWizard {

    /**
     * Shows a setup dialog for initializing the workctl workspace.
     * Returns true if successfully initialized, false if cancelled.
     */
    public static boolean run(Stage ownerStage) {
        Alert intro = new Alert(Alert.AlertType.INFORMATION,
                "Welcome to workctl!\n\nIt looks like you haven't set up your workspace yet.\nPlease select a folder where workctl will create your project tracking repository (e.g., ~/Work).",
                ButtonType.OK, ButtonType.CANCEL);
        intro.setHeaderText("First Time Setup");
        intro.setTitle("workctl Setup");

        if (intro.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL) {
            return false;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select workctl Workspace Directory");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File selectedDir = chooser.showDialog(ownerStage);

        if (selectedDir == null) {
            return false; // User cancelled
        }

        // Initialize and save
        try {
            AppConfig config = new AppConfig();
            config.setWorkspace(selectedDir.getAbsolutePath().replace("\\", "/"));

            // Ensure the directory itself is created if the user typed something new
            File workspaceDir = new File(config.getWorkspace());
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs();
            }

            try {
                com.workctl.core.storage.WorkspaceManager.initializeWorkspace(workspaceDir.toPath());
            } catch (Exception ex) {
                throw new IOException("Failed to build workspace folder structure", ex);
            }

            // Ensure the ~/.workctl directory exists before saving config
            File configDir = ConfigManager.getConfigPath().getParent().toFile();
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            ConfigManager.save(config);

            Alert success = new Alert(Alert.AlertType.INFORMATION,
                    "Workspace configuration saved successfully.\nYour workspace is located at: "
                            + config.getWorkspace(),
                    ButtonType.OK);
            success.setHeaderText("Setup Complete");
            success.showAndWait();

            return true;
        } catch (IOException e) {
            Alert err = new Alert(Alert.AlertType.ERROR, "Failed to save configuration:\n" + e.getMessage(),
                    ButtonType.OK);
            err.showAndWait();
            return false;
        }
    }
}
