package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.CommandEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class CommandService {

    private Path getCommandsDir() {
        AppConfig config = ConfigManager.load();
        Path workspace = Paths.get(config.getWorkspace());
        Path commandsDir = workspace.resolve("02_Commands");

        try {
            if (!Files.exists(commandsDir)) {
                Files.createDirectories(commandsDir);
            }

            // Auto-populate defaults for new or existing workspaces that don't have them
            Path coreCommandsFile = commandsDir.resolve("workctl-core.md");
            if (!Files.exists(coreCommandsFile)) {
                Files.createFile(coreCommandsFile); // Touch file to prevent recursive population triggers
                DefaultCommandsGenerator.populate(this);
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not create commands directory", e);
        }
        return commandsDir;
    }

    public List<String> listCategories() {
        Path dir = getCommandsDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString().replace(".md", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public List<CommandEntry> loadAllCommands() {
        List<CommandEntry> all = new ArrayList<>();
        for (String category : listCategories()) {
            all.addAll(loadCategory(category));
        }
        return all;
    }

    public List<CommandEntry> loadCategory(String category) {
        Path file = getCommandsDir().resolve(category + ".md");
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        List<CommandEntry> commands = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file);

            CommandEntry current = null;
            StringBuilder notes = new StringBuilder();

            for (String line : lines) {
                line = line.trim();

                if (line.startsWith("## ")) {
                    if (current != null) {
                        current.setNotes(notes.toString().trim());
                        commands.add(current);
                    }
                    current = new CommandEntry();
                    current.setCategory(category);
                    current.setTitle(line.substring(3).trim());
                    notes = new StringBuilder();
                } else if (current != null) {
                    if (line.startsWith("`") && line.endsWith("`")) {
                        current.setCommand(line.substring(1, line.length() - 1));
                    } else if (line.startsWith("<!-- project=")) {
                        String tag = line.replace("<!-- project=", "").replace("-->", "").trim();
                        current.setProjectTag(tag);
                    } else if (line.startsWith("<!-- id=")) {
                        String id = line.replace("<!-- id=", "").replace("-->", "").trim();
                        current.setId(id);
                    } else if (!line.isBlank()) {
                        if (line.startsWith("> ")) {
                            notes.append(line.substring(2)).append("\n");
                        } else {
                            notes.append(line).append("\n");
                        }
                    }
                }
            }

            if (current != null) {
                current.setNotes(notes.toString().trim());
                commands.add(current);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return commands;
    }

    public void saveCommand(CommandEntry entry) {
        List<CommandEntry> existing = loadCategory(entry.getCategory());

        // Remove existing if updating
        existing.removeIf(e -> e.getId().equals(entry.getId()));
        existing.add(entry);

        saveCategory(entry.getCategory(), existing);
    }

    public void deleteCommand(CommandEntry entry) {
        List<CommandEntry> existing = loadCategory(entry.getCategory());
        boolean removed = existing.removeIf(e -> e.getId().equals(entry.getId()));

        if (removed) {
            saveCategory(entry.getCategory(), existing);
        }
    }

    private void saveCategory(String category, List<CommandEntry> commands) {
        Path file = getCommandsDir().resolve(category + ".md");

        try {
            if (commands.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(category).append(" Commands\n\n");

            for (CommandEntry cmd : commands) {
                sb.append("## ").append(cmd.getTitle()).append("\n");
                sb.append("`").append(cmd.getCommand()).append("`\n");

                if (cmd.getNotes() != null && !cmd.getNotes().isBlank()) {
                    for (String noteLine : cmd.getNotes().split("\n")) {
                        sb.append("> ").append(noteLine).append("\n");
                    }
                }

                sb.append("<!-- project=").append(cmd.getProjectTag()).append(" -->\n");
                sb.append("<!-- id=").append(cmd.getId()).append(" -->\n\n");
            }

            Files.writeString(file, sb.toString());

        } catch (IOException e) {
            throw new RuntimeException("Failed to save commands category: " + category, e);
        }
    }
}
