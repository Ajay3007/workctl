package com.workctl.core.storage;

import com.workctl.core.domain.WorkLogEntry;
import com.workctl.core.domain.Meeting;
import com.workctl.core.domain.WeeklySummary;

/**
 * Renders domain objects to markdown format.
 */
public class MarkdownRenderer {

    public static String renderWorkLog(WorkLogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(entry.getDate()).append("\n\n");
        sb.append("### Assigned\n");
        entry.getAssigned().forEach(item -> sb.append("- ").append(item).append("\n"));
        sb.append("\n### Done\n");
        entry.getDone().forEach(item -> sb.append("- ").append(item).append("\n"));
        sb.append("\n### Changes Suggested\n");
        entry.getChangesSuggested().forEach(item -> sb.append("- ").append(item).append("\n"));
        sb.append("\n### Commands Used\n```bash\n");
        entry.getCommandsUsed().forEach(cmd -> sb.append(cmd).append("\n"));
        sb.append("```\n\n");
        if (!entry.getNotes().isEmpty()) {
            sb.append("### Notes\n").append(entry.getNotes()).append("\n");
        }
        return sb.toString();
    }

    public static String renderMeeting(Meeting meeting) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(meeting.getTitle()).append("\n\n");
        sb.append("**Date & Time:** ").append(meeting.getDateTime()).append("\n\n");
        if (meeting.getAttendees() != null) {
            sb.append("**Attendees:** ").append(meeting.getAttendees()).append("\n\n");
        }
        if (meeting.getAgenda() != null) {
            sb.append("**Agenda:** ").append(meeting.getAgenda()).append("\n\n");
        }
        if (meeting.getNotes() != null) {
            sb.append("**Notes:**\n").append(meeting.getNotes()).append("\n");
        }
        return sb.toString();
    }

    public static String renderWeeklySummary(WeeklySummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Weekly Summary\n\n");
        sb.append("**Week:** ").append(summary.getWeekStartDate()).append(" to ")
                .append(summary.getWeekEndDate()).append("\n\n");
        sb.append("## Accomplishments\n");
        summary.getAccomplishments().forEach(item -> sb.append("- ").append(item).append("\n"));
        sb.append("\n## Challenges\n");
        summary.getChallenges().forEach(item -> sb.append("- ").append(item).append("\n"));
        sb.append("\n## Next Week Plan\n");
        summary.getNextWeekPlan().forEach(item -> sb.append("- ").append(item).append("\n"));
        return sb.toString();
    }
}
