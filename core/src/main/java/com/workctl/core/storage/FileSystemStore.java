package com.workctl.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles file system operations for storing and retrieving data.
 */
public class FileSystemStore {
    private WorkspaceManager workspaceManager;

    public FileSystemStore(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    public void createDirectory(Path path) throws IOException {
        Files.createDirectories(path);
    }

    public void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes());
    }

    public String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path));
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public void deleteFile(Path path) throws IOException {
        Files.delete(path);
    }
}
