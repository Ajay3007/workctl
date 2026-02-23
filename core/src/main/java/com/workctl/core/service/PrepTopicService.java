package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.PrepTopic;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Manages the interview prep-topics checklist.
 * Single file: 05_Interviews/prep-topics.md
 *
 * Three-level hierarchy: Category (DSA) → Section (Arrays) → Item (Two pointer technique)
 *
 * File format:
 *   ## DSA
 *   ### Arrays
 *   - [x] Two pointer technique <!-- id=uuid -->
 *   - [ ] Sliding window        <!-- id=uuid -->
 *
 *   ## OS
 *   - [x] Process vs Thread <!-- id=uuid -->    ← item with no section
 */
public class PrepTopicService {

    private static final String DIR_NAME  = "05_Interviews";
    private static final String FILE_NAME = "prep-topics.md";

    // ================================================================
    // PUBLIC API
    // ================================================================

    public List<PrepTopic> loadAll() {
        try {
            Path file = getPrepTopicsFile();
            if (!Files.exists(file)) return new ArrayList<>();
            return parse(Files.readAllLines(file));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<PrepTopic> topics) {
        try {
            Files.writeString(getPrepTopicsFile(), format(topics));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save prep topics", e);
        }
    }

    /** Add a new topic item. Pass null/blank section to add directly under the category. */
    public PrepTopic addTopic(String category, String section, String name) {
        List<PrepTopic> all = loadAll();
        PrepTopic topic = new PrepTopic(category, section, name, false);
        all.add(topic);
        saveAll(all);
        return topic;
    }

    public void toggleDone(String id) {
        List<PrepTopic> all = loadAll();
        all.stream()
           .filter(t -> t.getId().equals(id))
           .findFirst()
           .ifPresent(t -> t.setDone(!t.isDone()));
        saveAll(all);
    }

    public void deleteTopic(String id) {
        List<PrepTopic> all = loadAll();
        all.removeIf(t -> t.getId().equals(id));
        saveAll(all);
    }

    // ================================================================
    // FORMATTING
    // ================================================================

    private String format(List<PrepTopic> topics) {
        StringBuilder sb = new StringBuilder("# Interview Prep Topics\n");

        // Group: category → section (null key = no section) → items
        Map<String, Map<String, List<PrepTopic>>> tree = new LinkedHashMap<>();
        for (PrepTopic t : topics) {
            tree.computeIfAbsent(t.getCategory(), k -> new LinkedHashMap<>())
                .computeIfAbsent(t.getSection() != null ? t.getSection() : "", k -> new ArrayList<>())
                .add(t);
        }

        for (Map.Entry<String, Map<String, List<PrepTopic>>> catEntry : tree.entrySet()) {
            sb.append("\n## ").append(catEntry.getKey()).append("\n");

            for (Map.Entry<String, List<PrepTopic>> secEntry : catEntry.getValue().entrySet()) {
                String section = secEntry.getKey();

                if (!section.isBlank()) {
                    sb.append("### ").append(section).append("\n");
                }

                for (PrepTopic t : secEntry.getValue()) {
                    sb.append("- ").append(t.isDone() ? "[x]" : "[ ]")
                      .append(" ").append(t.getName())
                      .append(" <!-- id=").append(t.getId()).append(" -->")
                      .append("\n");
                }
            }
        }

        return sb.toString();
    }

    // ================================================================
    // PARSING
    // ================================================================

    private List<PrepTopic> parse(List<String> lines) {
        List<PrepTopic> result = new ArrayList<>();
        String currentCategory = "General";
        String currentSection  = null;

        for (String line : lines) {
            if (line.startsWith("## ")) {
                currentCategory = line.substring(3).trim();
                currentSection  = null;
                continue;
            }

            if (line.startsWith("### ")) {
                currentSection = line.substring(4).trim();
                continue;
            }

            if (line.trim().startsWith("- [")) {
                boolean done = line.trim().startsWith("- [x]");
                String rest  = line.trim().substring(5).trim();

                // Extract optional <!-- id=... -->
                String id   = null;
                String name = rest;
                var idM = java.util.regex.Pattern
                        .compile("<!--\\s*id=([\\w-]+)\\s*-->")
                        .matcher(rest);
                if (idM.find()) {
                    id   = idM.group(1);
                    name = rest.substring(0, idM.start()).trim();
                }

                PrepTopic t = new PrepTopic(currentCategory, currentSection, name, done);
                if (id != null) t.setId(id);
                result.add(t);
            }
        }

        return result;
    }

    // ================================================================
    // FILE UTILITIES
    // ================================================================

    private Path getPrepTopicsFile() throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace()).resolve(DIR_NAME);
        Files.createDirectories(dir);
        return dir.resolve(FILE_NAME);
    }
}
