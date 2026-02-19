package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.core.model.Task;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;

import java.util.List;

/**
 * Tool: list_tasks
 *
 * Lets the agent read all tasks for the current project, optionally
 * filtered by status. This is the agent's primary "eyes" on your task board.
 *
 * Claude will call this when user asks things like:
 *   - "What tasks are stagnant?"
 *   - "How many P1 tasks are open?"
 *   - "What am I currently working on?"
 */
public class ListTasksTool implements AgentTool {

    private final TaskService taskService = new TaskService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "list_tasks";
    }

    @Override
    public String getDescription() {
        return "List all tasks for the current project. Optionally filter by status " +
               "(OPEN, IN_PROGRESS, DONE). Returns task ID, title, priority, status, " +
               "and created date for each task. Use this to answer questions about " +
               "what tasks exist, their priorities, or their current state.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "status_filter": {
                      "type": "string",
                      "enum": ["ALL", "OPEN", "IN_PROGRESS", "DONE"],
                      "description": "Filter tasks by status. Use ALL to get everything."
                    }
                  },
                  "required": ["status_filter"]
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            String filter = input.path("status_filter").asText("ALL");

            List<Task> tasks = taskService.getTasks(projectName);

            // Apply filter
            List<Task> filtered = tasks.stream()
                    .filter(t -> {
                        if ("ALL".equals(filter)) return true;
                        return t.getStatus().name().equals(filter);
                    })
                    .toList();

            if (filtered.isEmpty()) {
                return "No tasks found" + (filter.equals("ALL") ? "" : " with status " + filter) + ".";
            }

            // Format as readable text for Claude to reason about
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(filtered.size()).append(" task(s):\n\n");

            for (Task t : filtered) {
                sb.append("Task #").append(t.getId()).append("\n");
                sb.append("  Title:    ").append(t.getTitle()).append("\n");
                sb.append("  Status:   ").append(t.getStatus()).append("\n");
                sb.append("  Priority: P").append(t.getPriority()).append("\n");
                sb.append("  Created:  ").append(t.getCreatedDate()).append("\n");

                // Calculate how old this task is
                long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                        t.getCreatedDate(), java.time.LocalDate.now());
                if (daysOld > 0 && t.getStatus() != TaskStatus.DONE) {
                    sb.append("  Age:      ").append(daysOld).append(" days old");
                    if (daysOld > 7) sb.append(" ⚠ STAGNANT");
                    sb.append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return "Error listing tasks: " + e.getMessage();
        }
    }
}
