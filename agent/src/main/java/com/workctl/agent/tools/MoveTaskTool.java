package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.core.model.TaskStatus;
import com.workctl.core.service.TaskService;

/**
 * Tool: move_task
 *
 * Lets the agent move a task between Open → In Progress → Done.
 * This is a WRITE tool — only available when user passes --act flag.
 *
 * Claude will use this when user says things like:
 *   "Mark task 52 as done"
 *   "Start working on task 60"
 *   "Move task 48 back to open"
 */
public class MoveTaskTool implements AgentTool {

    private final TaskService taskService = new TaskService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "move_task";
    }

    @Override
    public String getDescription() {
        return "Move a task to a different status: OPEN, IN_PROGRESS, or DONE. " +
               "Use this when the user wants to update the state of a specific task. " +
               "You must know the task ID — call list_tasks first if unsure.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "task_id": {
                      "type": "integer",
                      "description": "The numeric ID of the task to move (e.g. 52)"
                    },
                    "new_status": {
                      "type": "string",
                      "enum": ["OPEN", "IN_PROGRESS", "DONE"],
                      "description": "The target status to move the task to"
                    }
                  },
                  "required": ["task_id", "new_status"]
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            int taskId = input.path("task_id").asInt();
            String statusStr = input.path("new_status").asText();

            TaskStatus newStatus = switch (statusStr) {
                case "OPEN" -> TaskStatus.OPEN;
                case "IN_PROGRESS" -> TaskStatus.IN_PROGRESS;
                case "DONE" -> TaskStatus.DONE;
                default -> throw new IllegalArgumentException("Unknown status: " + statusStr);
            };

            // Verify task exists first
            var task = taskService.getTask(projectName, taskId);
            if (task.isEmpty()) {
                return "Error: Task #" + taskId + " not found.";
            }

            taskService.updateStatus(projectName, taskId, newStatus);

            return "Task #" + taskId + " (" + task.get().getTitle() + ") " +
                   "moved to " + statusStr + " successfully.";

        } catch (Exception e) {
            return "Error moving task: " + e.getMessage();
        }
    }
}
