package com.workctl.core.service;

import com.workctl.core.domain.Meeting;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing meetings.
 */
public class MeetingService {
    private List<Meeting> meetings;

    public MeetingService() {
        this.meetings = new ArrayList<>();
    }

    public Meeting createMeeting(String projectId, String title, LocalDateTime dateTime) {
        Meeting meeting = new Meeting(projectId, title, dateTime);
        meetings.add(meeting);
        return meeting;
    }

    public Optional<Meeting> getMeeting(String meetingId) {
        return meetings.stream().filter(m -> m.getId().equals(meetingId)).findFirst();
    }

    public List<Meeting> listMeetings(String projectId) {
        List<Meeting> projectMeetings = new ArrayList<>();
        for (Meeting meeting : meetings) {
            if (meeting.getProjectId().equals(projectId)) {
                projectMeetings.add(meeting);
            }
        }
        return projectMeetings;
    }

    public boolean deleteMeeting(String meetingId) {
        return meetings.removeIf(m -> m.getId().equals(meetingId));
    }
}
