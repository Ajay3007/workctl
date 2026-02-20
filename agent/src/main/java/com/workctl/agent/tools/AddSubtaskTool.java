package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.core.service.TaskService;

/**
 * Tool: add_subtask
 *
 * Lets the agent add a subtask to an existing task.
 * Claude calls this after decomposing a goal or when the user asks
 * something like "add a subtask 'Write tests' to task #5".
 *
 * Input:
 *   { "task_id": 5, "title": "Write unit tests" }
 *
 * This is a WRITE tool — only active in write mode.
 */
public class AddSubtaskTool implements AgentTool {

    private final TaskService taskService = new TaskService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "add_subtask";
    }

    @Override
    public String getDescription() {
        return "Add a subtask to an existing task. Use this when the user wants to break " +
               "a task into smaller steps, or when explicitly asked to add a subtask to " +
               "a specific task ID. Each subtask is a short, actionable item that " +
               "contributes to completing the parent task.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "task_id": {
                      "type": "integer",
                      "description": "The ID of the existing task to add the subtask to."
                    },
                    "title": {
                      "type": "string",
                      "description": "Short, actionable subtask title. Keep it concise — one clear action."
                    }
                  },
                  "required": ["task_id", "title"]
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            JsonNode input   = mapper.readTree(inputJson);
            int    taskId    = input.path("task_id").asInt(-1);
            String title     = input.path("title").asText("").trim();

            if (taskId < 0) {
                return "Error: task_id is required and must be a positive integer.";
            }
            if (title.isBlank()) {
                return "Error: subtask title cannot be empty.";
            }

            // Verify the task exists before adding
            var taskOpt = taskService.getTask(projectName, taskId);
            if (taskOpt.isEmpty()) {
                return "Error: Task #" + taskId + " not found in project '" + projectName + "'.";
            }

            taskService.addSubtask(projectName, taskId, title);

            return "Subtask added to Task #" + taskId + " (\"" + taskOpt.get().getTitle() + "\"): " + title;

        } catch (Exception e) {
            return "Error adding subtask: " + e.getMessage();
        }
    }
}
