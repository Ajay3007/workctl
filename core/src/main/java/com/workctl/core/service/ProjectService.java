package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.file.StandardOpenOption;



public class ProjectService {

    public Project createProject(
            Path workspace,
            String name,
            String description
    ) {
        try {
            Path projectsDir = workspace.resolve("01_Projects");
            Path projectDir = projectsDir.resolve(name);

            if (Files.exists(projectDir)) {
                throw new IllegalStateException(
                        "Project already exists: " + name
                );
            }

            Files.createDirectories(projectDir.resolve("docs"));
            Files.createDirectories(projectDir.resolve("src"));
            Files.createDirectories(projectDir.resolve("notes"));
            Files.createDirectories(projectDir.resolve("logs"));

            Files.writeString(
                    projectDir.resolve("README.md"),
                    """
                    # %s

                    ## Description
                    %s

                    ## Created On
                    %s
                    """.formatted(name, description, LocalDate.now())
            );

            Files.writeString(
                    projectDir.resolve("notes/work-log.md"),
                    """
                    # %s – Work Log

                    ## %s

                    ### Assigned
                    -

                    ### Done
                    -

                    ### Notes
                    -
                    """.formatted(name, LocalDate.now())
            );

            return new Project(
                    UUID.randomUUID().toString(),
                    name,
                    description
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to create project", e);
        }
    }

    public List<Project> listProjects(Path workspace) {
        try {
            Path projectsDir = workspace.resolve("01_Projects");

            if (!Files.exists(projectsDir)) {
                return List.of();
            }

            try (Stream<Path> stream = Files.list(projectsDir)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(path -> new Project(
                                path.getFileName().toString(),   // id (for now)
                                path.getFileName().toString(),   // name
                                ""
                        ))
                        .sorted(Comparator.comparing(Project::getName))
                        .toList();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to list projects", e);
        }
    }

    public void addLogEntry(String projectName,
                            String message,
                            String section,
                            List<String> tags) {

        try {
            AppConfig config = ConfigManager.load();
            Path workspace = Paths.get(config.getWorkspace());

            Path projectDir = workspace
                    .resolve("01_Projects")
                    .resolve(projectName);

            if (!Files.exists(projectDir)) {
                throw new IllegalStateException("Project not found: " + projectName);
            }

            Path logFile = projectDir
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                throw new IllegalStateException("work-log.md not found.");
            }

            List<String> lines = Files.readAllLines(logFile);
            String today = LocalDate.now().toString();

            // 1️⃣ Ensure today's full structured block exists
            int todayIndex = findDateBlock(lines, today);

            if (todayIndex == -1) {
                appendFullTemplate(lines, today);
                todayIndex = findDateBlock(lines, today);
            }

            ensureAllSectionsExist(lines, todayIndex);

            // 2️⃣ If no message → just ensure block exists
            if (message == null || message.isBlank()) {
                Files.write(logFile, lines);
                return;
            }

            // 3️⃣ Default section
            if (section == null || section.isBlank()) {
                section = "done";
            }

            String sectionHeader = mapSection(section);

            // 4️⃣ Prepare tag string
            String tagString = "";
            if (tags != null && !tags.isEmpty()) {
                tagString = tags.stream()
                        .map(t -> "#" + t.toLowerCase())
                        .collect(Collectors.joining(" ", " [", "]"));
            }

            String entry = formatMultilineEntry(message, tagString);

            // 5️⃣ Insert entry into correct section
            insertIntoSection(lines, todayIndex, sectionHeader, entry);

            Files.write(logFile, lines);

        } catch (Exception e) {
            throw new RuntimeException("Failed to update log", e);
        }
    }

    private String formatMultilineEntry(String message, String tagString) {

        String[] lines = message.split("\\R");

        if (lines.length == 0) {
            return "- " + tagString;
        }

        StringBuilder sb = new StringBuilder();

        // First line
        sb.append("- ").append(lines[0]).append(tagString);

        // Remaining lines indented
        for (int i = 1; i < lines.length; i++) {
            sb.append("\n  ").append(lines[i]);
        }

        return sb.toString();
    }


    private int findDateBlock(List<String> lines, String date) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals("## " + date)) {
                return i;
            }
        }
        return -1;
    }


    private void appendFullTemplate(List<String> lines, String date) {

        lines.add("");
        lines.add("## " + date);
        lines.add("");
        lines.add("### Assigned");
        lines.add("-");
        lines.add("");
        lines.add("### Done");
        lines.add("-");
        lines.add("");
        lines.add("### Changes Suggested");
        lines.add("-");
        lines.add("");
        lines.add("### Commands Used");
        lines.add("-");
        lines.add("");
        lines.add("### Notes");
        lines.add("-");
        lines.add("");
        lines.add("---");
    }

    private String mapSection(String section) {
        return switch (section.toLowerCase()) {
            case "assigned" -> "### Assigned";
            case "done" -> "### Done";
            case "changes" -> "### Changes Suggested";
            case "commands" -> "### Commands Used";
            case "notes" -> "### Notes";
            default -> throw new IllegalArgumentException("Invalid section: " + section);
        };
    }


    private void insertIntoSection(List<String> lines,
                                   int todayIndex,
                                   String sectionHeader,
                                   String entry) {

        for (int i = todayIndex; i < lines.size(); i++) {

            // Stop if next date begins
            if (i > todayIndex && lines.get(i).startsWith("## ")) {
                break;
            }

            if (lines.get(i).trim().equals(sectionHeader)) {

                int insertIndex = i + 1;

                // Skip empty line if present
                if (insertIndex < lines.size() && lines.get(insertIndex).isBlank()) {
                    insertIndex++;
                }

                // If placeholder dash exists → replace it
                if (insertIndex < lines.size() && lines.get(insertIndex).trim().equals("-")) {
                    lines.set(insertIndex, entry);
                } else {
                    lines.add(insertIndex, entry);
                }

                return;
            }
        }

        throw new IllegalStateException("Section not found: " + sectionHeader);
    }

    private void ensureAllSectionsExist(List<String> lines, int todayIndex) {

        List<String> requiredSections = List.of(
                "### Assigned",
                "### Done",
                "### Changes Suggested",
                "### Commands Used",
                "### Notes"
        );

        int insertPos = todayIndex + 1;

        // Move to first section under date
        while (insertPos < lines.size() && !lines.get(insertPos).startsWith("###")) {
            insertPos++;
        }

        Set<String> existingSections = new HashSet<>();

        for (int i = todayIndex; i < lines.size(); i++) {

            if (i > todayIndex && lines.get(i).startsWith("## ")) {
                break;
            }

            if (lines.get(i).startsWith("### ")) {
                existingSections.add(lines.get(i).trim());
            }
        }

        for (String section : requiredSections) {

            if (!existingSections.contains(section)) {

                lines.add(insertPos, "");
                lines.add(insertPos + 1, section);
                lines.add(insertPos + 2, "-");

                insertPos += 3;
            }
        }
    }

    public void generateWeeklySummary(String projectName,
                                      String from,
                                      String to,
                                      String sectionFilter) {

        try {
            AppConfig config = ConfigManager.load();
            Path workspace = Paths.get(config.getWorkspace());

            Path logFile = workspace
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                System.out.println("No work log found for project: " + projectName);
                return;
            }

            // 🔹 Date Handling
            LocalDate endDate;
            LocalDate startDate;

            if (to != null && !to.isBlank()) {
                endDate = LocalDate.parse(to);
            } else {
                endDate = LocalDate.now();
            }

            if (from != null && !from.isBlank()) {
                startDate = LocalDate.parse(from);
            } else {
                startDate = endDate.minusDays(6);
            }

            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("--from date cannot be after --to date");
            }

            String content = Files.readString(logFile);

            Map<String, List<String>> collected = new LinkedHashMap<>();
            collected.put("Done", new ArrayList<>());
            collected.put("Changes Suggested", new ArrayList<>());
            collected.put("Commands Used", new ArrayList<>());

            parseWeeklyData(content, startDate, endDate, collected);

            if (sectionFilter != null && !sectionFilter.isBlank()) {

                String mapped = mapSectionFilter(sectionFilter);

                Map<String, List<String>> filtered = new LinkedHashMap<>();

                if (collected.containsKey(mapped)) {
                    filtered.put(mapped, collected.get(mapped));
                } else {
                    System.out.println("Invalid section filter.");
                    return;
                }

                collected = filtered;
            }

            String summary = buildSummary(projectName, startDate, endDate, collected);

            // 🔹 Print to Console
            System.out.println(summary);

            // 🔹 Save to File
            String fileName = "weekly-summary-"
                    + startDate + "_to_" + endDate + ".md";

            Path outputFile = logFile.getParent().resolve(fileName);

            Files.writeString(outputFile, summary);

            System.out.println("\nSaved to: " + outputFile);

        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format. Use yyyy-MM-dd");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate weekly summary", e);
        }
    }

    private void parseWeeklyData(String content,
                                 LocalDate start,
                                 LocalDate end,
                                 Map<String, List<String>> collected) {

        String[] lines = content.split("\n");

        LocalDate currentDate = null;
        String currentSection = null;

        for (String line : lines) {

            line = line.trim();

            // Detect date header
            if (line.startsWith("## ")) {
                currentDate = LocalDate.parse(line.substring(3).trim());
                continue;
            }

            if (currentDate == null) continue;

            // Check date range
            if (currentDate.isBefore(start) || currentDate.isAfter(end)) {
                continue;
            }

            // Detect section
            if (line.startsWith("### ")) {
                currentSection = line.substring(4).trim();
                continue;
            }

            // Collect bullet points
            if (line.startsWith("- ") && currentSection != null) {

                if (collected.containsKey(currentSection)) {
                    String entry = line.substring(2).trim();
                    if (!entry.equals("-") && !entry.isBlank()) {
                        collected.get(currentSection).add(entry);
                    }
                }
            }
        }
    }

    private String mapSectionFilter(String section) {
        return switch (section.toLowerCase()) {
            case "done" -> "Done";
            case "changes" -> "Changes Suggested";
            case "commands" -> "Commands Used";
            default -> throw new IllegalArgumentException("Invalid section filter");
        };
    }

    private String buildSummary(String projectName,
                                LocalDate start,
                                LocalDate end,
                                Map<String, List<String>> collected) {

        StringBuilder sb = new StringBuilder();

        sb.append("# Workctl Summary\n\n");
        sb.append("Project: ").append(projectName).append("\n");
        sb.append("Range: ").append(start).append(" → ").append(end).append("\n\n");

        for (var entry : collected.entrySet()) {

            List<String> items = entry.getValue()
                    .stream()
                    .filter(s -> s != null && !s.isBlank())
                    .toList();

            if (items.isEmpty()) continue;

            sb.append("## ").append(entry.getKey()).append("\n");

            for (String item : items) {
                sb.append("- ").append(item).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    public void search(String query, boolean searchByTag) {

        try {
            AppConfig config = ConfigManager.load();
            Path workspace = Paths.get(config.getWorkspace())
                    .resolve("01_Projects");

            if (!Files.exists(workspace)) {
                System.out.println("Workspace not found.");
                return;
            }

            Files.list(workspace)
                    .filter(Files::isDirectory)
                    .forEach(projectDir -> {

                        Path logFile = projectDir
                                .resolve("notes")
                                .resolve("work-log.md");

                        if (!Files.exists(logFile)) return;

                        try {
                            List<String> lines = Files.readAllLines(logFile);

                            for (String line : lines) {

                                if (searchByTag) {
                                    if (line.contains("#" + query.toLowerCase())) {
                                        printMatch(projectDir.getFileName().toString(), line);
                                    }
                                } else {
                                    if (line.toLowerCase().contains(query.toLowerCase())) {
                                        printMatch(projectDir.getFileName().toString(), line);
                                    }
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

        } catch (IOException e) {
            throw new RuntimeException("Search failed", e);
        }
    }

    private void printMatch(String project, String line) {
        System.out.println("[" + project + "] " + line.trim());
    }


}
