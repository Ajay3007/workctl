package com.workctl.cli.commands;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigWriter;
import com.workctl.core.storage.WorkspaceManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(
        name = "init",
        description = "Initialize workctl workspace and configuration"
)
public class InitCommand implements Runnable {

    @Option(
            names = {"-w", "--workspace"},
            description = "Workspace directory path",
            required = true
    )
    private String workspacePath;

    @Override
    public void run() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".workctl");
            Path configFile = configDir.resolve("config.yaml");

            if (Files.exists(configFile)) {
                System.out.println("⚠ workctl is already initialized.");
                System.out.println("Config: " + configFile);
                return;
            }

            Path workspace = Paths.get(workspacePath).toAbsolutePath();

            Files.createDirectories(configDir);
            WorkspaceManager.initializeWorkspace(workspace);

            AppConfig config = new AppConfig();
            config.setWorkspace(workspace.toString().replace("\\", "/"));
            config.setDateFormat("yyyy-MM-dd");
            config.setEditor("code");

            ConfigWriter.write(configFile, config);

            System.out.println("✅ workctl initialized successfully!");
            System.out.println("Workspace: " + workspace);
            System.out.println("Config file: " + configFile);

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize workctl");
            e.printStackTrace();
        }
    }
}
