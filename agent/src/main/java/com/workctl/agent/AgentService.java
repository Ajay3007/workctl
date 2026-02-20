package com.workctl.agent;

import com.workctl.agent.tools.*;
import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentService
 *
 * The main entry point for all AI agent interactions.
 * Both the CLI (AskCommand) and GUI (AgentPanel) call this class.
 *
 * It wires together:
 *   ContextBuilder  → builds project-aware system prompt
 *   AnthropicClient → handles HTTP + tool-use loop with Claude API
 *   Tools           → the actions Claude can take on workctl data
 *
 * Two modes:
 *   ask()  → read-only: answers questions, summarizes, gives insights
 *   act()  → read+write: can also add tasks, add subtasks, move task status
 *
 * Usage from CLI:
 *   workctl ask myproject "What did I work on this week?"
 *   workctl ask myproject --act "Break down the logging feature into tasks"
 *
 * Usage from GUI:
 *   AgentService service = new AgentService();
 *   String response = service.ask(projectName, userMessage, false);
 */
public class AgentService {

    private final ContextBuilder contextBuilder = new ContextBuilder();

    /**
     * Send a message to the AI agent.
     *
     * @param projectName   the workctl project to work with
     * @param userMessage   the user's question or instruction
     * @param allowWrite    if true, agent can add tasks, add subtasks, and move task status
     * @return              Claude's response as a plain string
     */
    public String ask(String projectName, String userMessage, boolean allowWrite) {

        try {
            // 1. Load API key from config
            AppConfig config = ConfigManager.load();
            String apiKey = config.getAnthropicApiKey();

            if (apiKey == null || apiKey.isBlank()) {
                return "⚠ Anthropic API key not configured.\n" +
                        "Run: workctl config set anthropicApiKey sk-ant-YOUR_KEY_HERE";
            }

            // 2. Build context-rich system prompt
            String systemPrompt = contextBuilder.buildSystemPrompt(projectName, allowWrite);

            // 3. Set up tools
            List<AgentTool> tools = buildTools(allowWrite);

            // 4. Call Claude API with tool-use loop
            AnthropicClient client = new AnthropicClient(apiKey);
            return client.chat(systemPrompt, userMessage, tools);

        } catch (Exception e) {
            return "Agent error: " + e.getMessage();
        }
    }

    /**
     * Specialized: generate an intelligent weekly summary using AI.
     * Called by --ai flag on weekly command.
     *
     * @param projectName   project to summarize
     * @param fromDate      start date (yyyy-MM-dd)
     * @param toDate        end date (yyyy-MM-dd)
     */
    public String weeklyAiSummary(String projectName, String fromDate, String toDate) {

        String prompt = """
                Generate an intelligent weekly summary for this project.

                Date range: %s to %s

                Please:
                1. Call search_logs to get what was done in this period
                2. Call get_insights to understand project health
                3. Write a clear, narrative summary covering:
                   - What was accomplished this week
                   - Key highlights or milestones
                   - Blockers or stagnant items that need attention
                   - Recommended focus for next week
                   - Overall project health assessment

                Write in a professional but conversational tone, as if writing
                a standup update or weekly report.
                """.formatted(fromDate, toDate);

        return ask(projectName, prompt, false);
    }

    /**
     * Specialized: decompose a high-level goal into tasks.
     * Called by task add --ai "your goal here"
     *
     * @param projectName   project to add tasks to
     * @param goal          high-level goal description
     */
    public String decomposeGoal(String projectName, String goal) {

        String prompt = """
                The user wants to achieve this goal: "%s"

                Please:
                1. Call list_tasks first to see what already exists (avoid duplicates)
                2. Break the goal into 3-6 specific, actionable tasks
                3. For each task:
                   - Make it concrete and completable in 1-2 days
                   - Assign a realistic priority (P1 only if truly blocking)
                   - Call add_task to create it
                4. For tasks that have clear sub-steps, call add_subtask to add them
                5. After creating all tasks, summarize what you created

                Think step by step before creating tasks.
                """.formatted(goal);

        return ask(projectName, prompt, true); // write mode ON for task creation
    }

    /**
     * Specialized: AI-enhanced project insights.
     * Called by insight <project> --ai
     *
     * @param projectName   project to analyze
     */
    public String aiInsights(String projectName) {

        String prompt = """
                Analyze this project and give me intelligent insights.

                Please:
                1. Call get_insights to get the computed statistics
                2. Call list_tasks with ALL to see the full task board
                3. Interpret the data and provide:
                   - A plain-English assessment of project health
                   - What the productivity score means in context
                   - Which specific tasks are most at risk of being forgotten
                   - Concrete recommendations for this week
                   - One thing the developer is doing well

                Be specific — reference actual task IDs and dates where relevant.
                """;

        return ask(projectName, prompt, false);
    }

    /**
     * Build the list of tools available to Claude.
     * Read tools are always available. Write tools require allowWrite=true.
     *
     * Write tools:
     *   - add_task      → create a new task
     *   - add_subtask   → add a subtask to an existing task  (NEW)
     *   - move_task     → change task status
     */
    private List<AgentTool> buildTools(boolean allowWrite) {
        List<AgentTool> tools = new ArrayList<>();

        // Read-only tools — always available
        tools.add(new ListTasksTool());
        tools.add(new SearchLogsTool());
        tools.add(new GetInsightsTool());

        // Write tools — only when user explicitly opts in
        if (allowWrite) {
            tools.add(new AddTaskTool());
            tools.add(new AddSubtaskTool());   // NEW — agent can now create subtasks
            tools.add(new MoveTaskTool());
        }

        return tools;
    }
}