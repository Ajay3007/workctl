package com.workctl.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workctl.agent.tools.AgentTool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * AnthropicClient
 *
 * Thin HTTP wrapper around the Anthropic Messages API.
 * Uses Java 11's built-in HttpClient — no external HTTP library needed.
 *
 * Handles:
 *   - Building the request JSON (messages, tools, system prompt)
 *   - Sending the POST request to api.anthropic.com
 *   - Parsing the response (text blocks and tool_use blocks)
 *   - The tool_result loop (sending tool results back to Claude)
 *
 * The tool loop works like this:
 *
 *   Request 1: user message + tool definitions
 *   Response 1: Claude says "use list_tasks with {status_filter: 'OPEN'}"
 *               → stop_reason = "tool_use"
 *
 *   Request 2: same messages + tool_result with what list_tasks returned
 *   Response 2: Claude gives final answer
 *               → stop_reason = "end_turn"
 *
 * This loop repeats until stop_reason = "end_turn".
 * Max 5 iterations to prevent infinite loops.
 */
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-opus-4-6";
    private static final int MAX_TOKENS = 4096;
    private static final int MAX_TOOL_ITERATIONS = 5;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public AnthropicClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Send a user message with full tool-use loop.
     *
     * @param systemPrompt  built by ContextBuilder — project state + instructions
     * @param userMessage   what the user typed
     * @param tools         list of tools Claude can call
     * @return              Claude's final text response after all tool calls
     */
    public String chat(String systemPrompt,
                       String userMessage,
                       List<AgentTool> tools) throws Exception {

        // Build the initial messages array
        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        // Tool-use loop
        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {

            // Build request body
            String requestBody = buildRequestBody(systemPrompt, messages, tools);

            // Send request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Anthropic API error " + response.statusCode() + ": " + response.body());
            }

            JsonNode responseJson = mapper.readTree(response.body());
            String stopReason = responseJson.path("stop_reason").asText();
            JsonNode contentBlocks = responseJson.path("content");

            // ── End of conversation ──────────────────────────────────────
            if ("end_turn".equals(stopReason)) {
                return extractText(contentBlocks);
            }

            // ── Tool use requested ───────────────────────────────────────
            if ("tool_use".equals(stopReason)) {

                // Add Claude's response to message history
                ObjectNode assistantMsg = mapper.createObjectNode();
                assistantMsg.put("role", "assistant");
                assistantMsg.set("content", contentBlocks);
                messages.add(assistantMsg);

                // Execute each tool call and collect results
                ArrayNode toolResults = mapper.createArrayNode();

                for (JsonNode block : contentBlocks) {
                    if (!"tool_use".equals(block.path("type").asText())) continue;

                    String toolName = block.path("name").asText();
                    String toolUseId = block.path("id").asText();
                    String toolInput = block.path("input").toString();

                    // Find and execute the matching tool
                    String toolResult = tools.stream()
                            .filter(t -> t.getName().equals(toolName))
                            .findFirst()
                            .map(t -> {
                                try {
                                    // The tool receives projectName from context
                                    // We extract it from systemPrompt (simple approach)
                                    String projectName = extractProjectName(systemPrompt);
                                    return t.execute(projectName, toolInput);
                                } catch (Exception e) {
                                    return "Tool error: " + e.getMessage();
                                }
                            })
                            .orElse("Unknown tool: " + toolName);

                    // Format as tool_result block
                    ObjectNode resultBlock = mapper.createObjectNode();
                    resultBlock.put("type", "tool_result");
                    resultBlock.put("tool_use_id", toolUseId);
                    resultBlock.put("content", toolResult);
                    toolResults.add(resultBlock);
                }

                // Add tool results as user message (Anthropic API requirement)
                ObjectNode toolResultMsg = mapper.createObjectNode();
                toolResultMsg.put("role", "user");
                toolResultMsg.set("content", toolResults);
                messages.add(toolResultMsg);

                // Continue loop → Claude will now reason with tool results
                continue;
            }

            // Unexpected stop reason
            return extractText(contentBlocks);
        }

        return "Agent reached maximum tool iterations. Please try a simpler query.";
    }

    /**
     * Build the JSON request body for the Anthropic API.
     */
    private String buildRequestBody(String systemPrompt,
                                    ArrayNode messages,
                                    List<AgentTool> tools) throws Exception {

        ObjectNode body = mapper.createObjectNode();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.set("messages", messages);

        // Register tools so Claude knows what it can call
        if (!tools.isEmpty()) {
            ArrayNode toolsArray = mapper.createArrayNode();

            for (AgentTool tool : tools) {
                ObjectNode toolDef = mapper.createObjectNode();
                toolDef.put("name", tool.getName());
                toolDef.put("description", tool.getDescription());

                // Parse the JSON schema string into a proper JSON node
                JsonNode schemaNode = mapper.readTree(tool.getInputSchema());
                toolDef.set("input_schema", schemaNode);

                toolsArray.add(toolDef);
            }

            body.set("tools", toolsArray);
        }

        return mapper.writeValueAsString(body);
    }

    /**
     * Extract all text blocks from Claude's response content array.
     */
    private String extractText(JsonNode contentBlocks) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentBlocks) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString().trim();
    }

    /**
     * Extract project name from the system prompt.
     * ContextBuilder always includes "Current project: <name>"
     */
    private String extractProjectName(String systemPrompt) {
        for (String line : systemPrompt.split("\n")) {
            if (line.startsWith("Current project: ")) {
                return line.replace("Current project: ", "").trim();
            }
        }
        return "unknown";
    }
}
