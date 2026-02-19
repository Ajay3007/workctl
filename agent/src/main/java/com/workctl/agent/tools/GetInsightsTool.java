package com.workctl.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workctl.core.model.ProjectInsights;
import com.workctl.core.service.StatsService;

/**
 * Tool: get_insights
 *
 * Gives the agent access to the computed ProjectInsights object —
 * productivity score, stagnant tasks, completion rate, daily activity heatmap.
 *
 * Claude uses this for the "Generate insights beyond current stats" feature.
 * The difference from the current InsightCommand: Claude gets the raw numbers
 * AND interprets them into narrative advice, not just printouts.
 *
 * Claude will call this when user asks things like:
 *   "How is my productivity this week?"
 *   "Am I on track?"
 *   "Give me a project health summary"
 */
public class GetInsightsTool implements AgentTool {

    private final StatsService statsService = new StatsService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "get_insights";
    }

    @Override
    public String getDescription() {
        return "Get computed project insights and statistics: total/open/done task counts, " +
               "completion rate, productivity score (0-100), number of stagnant tasks " +
               "(not touched in 7+ days), tasks completed this week, and most used tag. " +
               "Use this to answer questions about project health, productivity, or progress.";
    }

    @Override
    public String getInputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {},
                  "required": []
                }
                """;
    }

    @Override
    public String execute(String projectName, String inputJson) {
        try {
            ProjectInsights insights = statsService.generateInsights(projectName);

            StringBuilder sb = new StringBuilder();
            sb.append("Project Insights for: ").append(projectName).append("\n\n");

            sb.append("Task Counts:\n");
            sb.append("  Total:       ").append(insights.getTotalTasks()).append("\n");
            sb.append("  Open:        ").append(insights.getOpenTasks()).append("\n");
            sb.append("  In Progress: ").append(insights.getInProgressTasks()).append("\n");
            sb.append("  Done:        ").append(insights.getDoneTasks()).append("\n\n");

            sb.append("Performance:\n");
            sb.append("  Completion Rate:     ")
              .append(String.format("%.1f", insights.getCompletionRate())).append("%\n");
            sb.append("  Completed This Week: ").append(insights.getCompletedThisWeek()).append("\n");
            sb.append("  Productivity Score:  ")
              .append(String.format("%.1f", insights.getProductivityScore())).append(" / 100\n");
            sb.append("  Stagnant Tasks:      ").append(insights.getStagnantTasks())
              .append(" (open > 7 days without change)\n\n");

            sb.append("Tagging:\n");
            sb.append("  Most Used Tag: #").append(insights.getMostUsedTag()).append("\n\n");

            sb.append("Activity:\n");
            sb.append("  Active Days Logged: ").append(insights.getDailyActivity().size())
              .append(" days in log history\n");

            // Score interpretation for Claude to use in its response
            double score = insights.getProductivityScore();
            String interpretation = score >= 85 ? "Elite Execution 🔥"
                    : score >= 70 ? "Strong Momentum 🚀"
                    : score >= 50 ? "Stable but room to improve ⚖"
                    : score >= 30 ? "Fragmented — needs focus ⚠"
                    : "Stalled — review priorities 🧊";

            sb.append("  Score Interpretation: ").append(interpretation).append("\n");

            return sb.toString();

        } catch (Exception e) {
            return "Error generating insights: " + e.getMessage();
        }
    }
}
