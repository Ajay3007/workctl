package com.workctl.agent.tools;

/**
 * Contract for every tool the AI agent can call.
 *
 * Each tool represents one action the agent can perform on workctl data.
 * The agent decides WHICH tool to call — workctl executes it and returns
 * the result back to the agent for reasoning.
 *
 * Tool Loop:
 *   Claude → tool_use(name, input JSON) → AgentService → tool.execute(input) → tool_result → Claude
 */
public interface AgentTool {

    /**
     * Unique tool name — must match exactly what's registered in the API call.
     * e.g. "list_tasks", "add_task", "search_logs"
     */
    String getName();

    /**
     * Human-readable description sent to Claude so it knows WHEN to use this tool.
     * Be specific — Claude uses this to decide which tool to pick.
     */
    String getDescription();

    /**
     * JSON Schema describing the input parameters Claude must provide.
     * Claude generates the input; this schema tells it what shape to use.
     *
     * Example:
     * {
     *   "type": "object",
     *   "properties": {
     *     "status": { "type": "string", "enum": ["OPEN","IN_PROGRESS","DONE"] }
     *   },
     *   "required": ["status"]
     * }
     */
    String getInputSchema();

    /**
     * Execute the tool with the JSON input Claude provided.
     *
     * @param projectName  current project context
     * @param inputJson    JSON string from Claude's tool_use block
     * @return             plain text result to send back as tool_result
     */
    String execute(String projectName, String inputJson);
}
