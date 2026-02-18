package com.workctl.cli.commands;

import com.workctl.core.service.MeetingService;
import com.workctl.core.domain.Meeting;
import com.workctl.cli.util.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;

/**
 * Meeting management commands.
 */
@Command(
    name = "meeting",
    description = "Manage meetings"
)
public class MeetingCommand implements Runnable {
    @Parameters(index = "0", description = "Project name/ID")
    private String projectId;

    @Parameters(index = "1", description = "Meeting title")
    private String title;

    private MeetingService meetingService = new MeetingService();

    @Override
    public void run() {
        try {
            Meeting meeting = meetingService.createMeeting(projectId, title, LocalDateTime.now());
            ConsolePrinter.success("Meeting created: " + title);
            ConsolePrinter.info("Meeting ID: " + meeting.getId());
        } catch (Exception e) {
            ConsolePrinter.error("Failed to create meeting: " + e.getMessage());
        }
    }
}
