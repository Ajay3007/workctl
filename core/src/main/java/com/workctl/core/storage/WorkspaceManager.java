package com.workctl.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspaceManager {

    public static void initializeWorkspace(Path workspace) throws Exception {
        Files.createDirectories(workspace);
        Files.createDirectories(workspace.resolve("00_Inbox"));
        Files.createDirectories(workspace.resolve("01_Projects"));
        Files.createDirectories(workspace.resolve("02_Commands"));
        Files.createDirectories(workspace.resolve("03_Meetings"));
        Files.createDirectories(workspace.resolve("04_References"));
        Files.createDirectories(workspace.resolve("99_Archive"));
    }
}
