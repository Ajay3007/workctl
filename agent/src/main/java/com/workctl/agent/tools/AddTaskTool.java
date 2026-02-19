package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.core.service.TaskService;

import java.util.List;

/**
 * Tool: add_task
 *
 * Lets the agent create a new task in the project.
 * This is the key tool for the "task decomposer" feature.
 *
 * Flow:
 *   User: "workctl ask myproject --act 'Build the weekly report feature'"
 *   Claude: reasons about the goal, calls add_task multiple times with subtasks
 *   Agent: calls TaskService.addTask() for each one
 *   Claude: confirms what was created
 *
 * NOTE: This is a WRITE tool — it modifies tasks.md.
 * The AgentService only enables write tools when user passes --act flag,
 * keeping read-only mode safe by default.
 */
public class AddTaskTool implements AgentTool {

    private final TaskService taskService = new TaskService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "add_task";
    }

    @Override
    public String getDescription() {
        return "Create a new task in the project. Use this to decompose a high-level " +
               "goal into specific actionable subtasks. Each task should be a clear, " +
               "concrete action. Set priority: 1=High (urgent/blocking), " +
               "2=Medium (normal work), 3=Low (nice to have).";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "description": {
                      "type": "string",
                      "description": "Clear, actionable task description. First line is the title."
                    },
                    "priority": {
                      "type": "integer",
                      "enum": [1, 2, 3],
                      "description": "1=High, 2=Medium, 3=Low"
                    }
                  },
                  "required": ["description", "priority"]
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            String description = input.path("description").asText();
            int priority = input.path("priority").asInt(2);

            if (description == null || description.isBlank()) {
                return "Error: task description cannot be empty.";
            }

            taskService.addTask(projectName, description, List.of(), priority);

            // Get the newly created task to confirm its ID
            List<com.workctl.core.model.Task> tasks = taskService.getTasks(projectName);
            int newId = tasks.stream()
                    .mapToInt(com.workctl.core.model.Task::getId)
                    .max()
                    .orElse(-1);

            return "Task created successfully: #" + newId +
                   " [P" + priority + "] " + description.split("\\R")[0];

        } catch (Exception e) {
            return "Error creating task: " + e.getMessage();
        }
    }
}
