package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool: search_logs
 *
 * Lets the agent search through work-log.md entries by keyword or date range.
 * This gives the agent "memory" of what the user worked on.
 *
 * Claude will call this when user asks things like:
 *   "What did I work on last Tuesday?"
 *   "Find all log entries about Redis"
 *   "What commands did I run this week?"
 */
public class SearchLogsTool implements AgentTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "search_logs";
    }

    @Override
    public String getDescription() {
        return "Search through the project's work log entries. Can search by keyword " +
               "or filter by date range. Returns matching log entries with their dates " +
               "and sections (Assigned, Done, Notes, Commands Used). Use this to answer " +
               "questions about past work or find specific log entries.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "description": "Search term to find in log entries. Use empty string to get all entries in date range."
                    },
                    "from_date": {
                      "type": "string",
                      "description": "Start date in yyyy-MM-dd format. Defaults to 7 days ago."
                    },
                    "to_date": {
                      "type": "string",
                      "description": "End date in yyyy-MM-dd format. Defaults to today."
                    }
                  },
                  "required": ["keyword"]
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            String keyword = input.path("keyword").asText("").toLowerCase();
            String fromStr = input.path("from_date").asText("");
            String toStr = input.path("to_date").asText("");

            LocalDate fromDate = fromStr.isBlank()
                    ? LocalDate.now().minusDays(7)
                    : LocalDate.parse(fromStr);

            LocalDate toDate = toStr.isBlank()
                    ? LocalDate.now()
                    : LocalDate.parse(toStr);

            AppConfig config = ConfigManager.load();
            Path logFile = Paths.get(config.getWorkspace())
                    .resolve("01_Projects")
                    .resolve(projectName)
                    .resolve("notes")
                    .resolve("work-log.md");

            if (!Files.exists(logFile)) {
                return "No work log found for project: " + projectName;
            }

            List<String> lines = Files.readAllLines(logFile);
            List<String> results = new ArrayList<>();

            LocalDate currentDate = null;
            String currentSection = null;
            boolean inRange = false;

            for (String line : lines) {
                String trimmed = line.trim();

                // Detect date header: ## 2026-02-19
                if (trimmed.startsWith("## ")) {
                    try {
                        currentDate = LocalDate.parse(trimmed.substring(3).trim());
                        inRange = !currentDate.isBefore(fromDate) && !currentDate.isAfter(toDate);
                        currentSection = null;
                    } catch (Exception ignored) {
                        // Not a date header (could be ## Open etc), skip
                    }
                    continue;
                }

                if (!inRange) continue;

                // Detect section header: ### Done
                if (trimmed.startsWith("### ")) {
                    currentSection = trimmed.substring(4).trim();
                    continue;
                }

                // Skip metadata comment blocks
                if (trimmed.startsWith("<!--")) continue;

                // Match bullet entries
                if (trimmed.startsWith("- ") && currentSection != null) {
                    String entry = trimmed.substring(2).trim();

                    // Apply keyword filter
                    if (keyword.isBlank() || entry.toLowerCase().contains(keyword)) {
                        results.add("[" + currentDate + "] [" + currentSection + "] " + entry);
                    }
                }
            }

            if (results.isEmpty()) {
                return "No log entries found" +
                       (keyword.isBlank() ? "" : " matching '" + keyword + "'") +
                       " between " + fromDate + " and " + toDate + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.size()).append(" log entries:\n\n");
            results.forEach(r -> sb.append("• ").append(r).append("\n"));

            return sb.toString();

        } catch (Exception e) {
            return "Error searching logs: " + e.getMessage();
        }
    }
}
