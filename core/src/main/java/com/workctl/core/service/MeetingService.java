package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.Meeting;
import com.workctl.core.model.MeetingStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * File-backed meeting service.
 *
 * Each meeting is stored as a single markdown file in 03_Meetings/:
 *   YYYY-MM-DD-HHmm-{slug}.md
 *
 * Pattern mirrors TaskService: load → modify → write.
 */
public class MeetingService {

    // ================================================================
    // PUBLIC API
    // ================================================================

    public Meeting createMeeting(String title, LocalDateTime dateTime, String projectId) {
        Meeting meeting = new Meeting(projectId, title, dateTime);
        saveMeeting(meeting);
        return meeting;
    }

    public void saveMeeting(Meeting meeting) {
        try {
            Path file = findMeetingFile(meeting.getId());
            if (file == null) {
                file = buildMeetingFilePath(meeting);
            }
            Files.writeString(file, formatMeeting(meeting));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save meeting", e);
        }
    }

    /** Alias for saveMeeting — updates an existing meeting on disk. */
    public void updateMeeting(Meeting meeting) {
        saveMeeting(meeting);
    }

    public Optional<Meeting> loadMeeting(String meetingId) {
        try {
            Path file = findMeetingFile(meetingId);
            if (file == null) return Optional.empty();
            return Optional.ofNullable(parseMeeting(Files.readAllLines(file)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** All meetings, sorted by dateTime descending (newest first). */
    public List<Meeting> listAllMeetings() {
        try {
            Path dir = getMeetingsDir();
            List<Meeting> result = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            Meeting m = parseMeeting(Files.readAllLines(p));
                            if (m != null) result.add(m);
                        } catch (Exception ignored) {}
                    });
            }
            result.sort(Comparator.comparing(
                    Meeting::getDateTime,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list meetings", e);
        }
    }

    /** Meetings linked to a specific project, sorted newest first. */
    public List<Meeting> listMeetingsByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) return listAllMeetings();
        return listAllMeetings().stream()
                .filter(m -> projectId.equals(m.getProjectId()))
                .collect(Collectors.toList());
    }

    /** Toggle the done flag on a single action item, then persist. */
    public void toggleActionItem(String meetingId, int index) {
        loadMeeting(meetingId).ifPresent(m -> {
            List<Meeting.ActionItem> items = m.getActionItems();
            if (index >= 0 && index < items.size()) {
                items.get(index).setDone(!items.get(index).isDone());
                saveMeeting(m);
            }
        });
    }

    public boolean deleteMeeting(String meetingId) {
        try {
            Path file = findMeetingFile(meetingId);
            if (file == null) return false;
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ================================================================
    // FILE I/O
    // ================================================================

    private Path getMeetingsDir() throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace()).resolve("03_Meetings");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Scan 03_Meetings/ for the file that contains id=<meetingId>.
     * Returns null if not found.
     */
    private Path findMeetingFile(String meetingId) throws IOException {
        Path dir = getMeetingsDir();
        if (!Files.exists(dir)) return null;
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> {
                    try {
                        return Files.readString(p).contains("id=" + meetingId);
                    } catch (Exception e) { return false; }
                })
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Build the initial file path for a new meeting.
     * Format: YYYY-MM-DD-HHmm-{slug}.md
     * Appends -2, -3 … if there is a name collision.
     */
    private Path buildMeetingFilePath(Meeting m) throws IOException {
        Path dir = getMeetingsDir();
        LocalDateTime dt = m.getDateTime() != null ? m.getDateTime() : LocalDateTime.now();
        String slug = buildSlug(m.getTitle());
        String base = dt.toLocalDate()
                + "-"
                + String.format("%02d%02d", dt.getHour(), dt.getMinute())
                + "-"
                + slug;
        Path path = dir.resolve(base + ".md");
        int suffix = 2;
        while (Files.exists(path)) {
            path = dir.resolve(base + "-" + suffix++ + ".md");
        }
        return path;
    }

    private String buildSlug(String title) {
        if (title == null || title.isBlank()) return "meeting";
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    // ================================================================
    // SERIALIZATION
    // ================================================================

    private String formatMeeting(Meeting m) {
        StringBuilder sb = new StringBuilder();

        // Title
        sb.append("# ").append(m.getTitle()).append("\n\n");

        // Metadata comment — single line for easy parsing
        sb.append("<!-- MEETING_META: id=").append(m.getId());
        if (m.getProjectId() != null) sb.append(" project=").append(m.getProjectId());
        LocalDateTime dt = m.getDateTime();
        if (dt != null) {
            sb.append(" date=").append(dt.toLocalDate());
            sb.append(" time=").append(
                    String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
        }
        sb.append(" status=").append(
                m.getStatus() != null ? m.getStatus() : MeetingStatus.DONE);
        if (m.getCreatedAt() != null) {
            sb.append(" created=")
              .append(m.getCreatedAt().truncatedTo(ChronoUnit.SECONDS));
        }
        sb.append(" -->\n\n");

        // Sections
        sb.append("## Attendees\n");
        sb.append(m.getAttendees() != null ? m.getAttendees().trim() : "").append("\n\n");

        sb.append("## Agenda\n");
        sb.append(m.getAgenda() != null ? m.getAgenda().trim() : "").append("\n\n");

        sb.append("## Notes\n");
        sb.append(m.getNotes() != null ? m.getNotes().trim() : "").append("\n\n");

        sb.append("## Action Items\n");
        for (Meeting.ActionItem ai : m.getActionItems()) {
            sb.append(ai.toMarkdownLine()).append("\n");
        }

        return sb.toString();
    }

    // ================================================================
    // PARSING
    // ================================================================

    private Meeting parseMeeting(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;

        Meeting meeting  = null;
        String  section  = null;
        StringBuilder attendees = new StringBuilder();
        StringBuilder agenda    = new StringBuilder();
        StringBuilder notes     = new StringBuilder();
        List<Meeting.ActionItem> actionItems = new ArrayList<>();

        Pattern metaPattern  = Pattern.compile("<!-- MEETING_META:([^>]+)-->");
        Pattern idP       = Pattern.compile("id=([\\w-]+)");
        Pattern projectP  = Pattern.compile("project=(\\S+)");
        Pattern dateP     = Pattern.compile("date=(\\d{4}-\\d{2}-\\d{2})");
        Pattern timeP     = Pattern.compile("time=(\\d{2}:\\d{2})");
        Pattern statusP   = Pattern.compile("status=(\\w+)");
        Pattern createdP  = Pattern.compile("created=(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})");

        for (String line : lines) {

            // Title (first # heading)
            if (line.startsWith("# ") && meeting == null) {
                meeting = new Meeting(null, line.substring(2).trim(), LocalDateTime.now());
                continue;
            }

            // Metadata
            Matcher metaM = metaPattern.matcher(line);
            if (metaM.find() && meeting != null) {
                String meta = metaM.group(1);

                Matcher idM = idP.matcher(meta);
                if (idM.find()) meeting.setId(idM.group(1));

                Matcher projM = projectP.matcher(meta);
                if (projM.find()) meeting.setProjectId(projM.group(1));

                // Reconstruct dateTime from date + time fields
                LocalDate    date   = null;
                int          hour   = 0, minute = 0;
                Matcher dateM = dateP.matcher(meta);
                if (dateM.find()) {
                    try { date = LocalDate.parse(dateM.group(1)); } catch (Exception ignored) {}
                }
                Matcher timeM = timeP.matcher(meta);
                if (timeM.find()) {
                    try {
                        String[] parts = timeM.group(1).split(":");
                        hour   = Integer.parseInt(parts[0]);
                        minute = Integer.parseInt(parts[1]);
                    } catch (Exception ignored) {}
                }
                if (date != null) meeting.setDateTime(date.atTime(hour, minute));

                Matcher statusM = statusP.matcher(meta);
                if (statusM.find()) {
                    try { meeting.setStatus(MeetingStatus.valueOf(statusM.group(1))); }
                    catch (Exception ignored) {}
                }

                Matcher createdM = createdP.matcher(meta);
                if (createdM.find()) {
                    try { meeting.setCreatedAt(LocalDateTime.parse(createdM.group(1))); }
                    catch (Exception ignored) {}
                }
                continue;
            }

            // Section headers
            if (line.startsWith("## ")) {
                section = line.substring(3).trim();
                continue;
            }

            if (section == null || meeting == null) continue;

            // Accumulate section content
            switch (section) {
                case "Attendees"    -> { if (!line.isBlank()) attendees.append(line).append("\n"); }
                case "Agenda"       -> { if (!line.isBlank()) agenda.append(line).append("\n"); }
                case "Notes"        -> { if (!line.isBlank()) notes.append(line).append("\n"); }
                case "Action Items" -> {
                    Meeting.ActionItem ai = Meeting.ActionItem.fromLine(line);
                    if (ai != null) actionItems.add(ai);
                }
            }
        }

        if (meeting != null) {
            meeting.setAttendees(attendees.toString().trim());
            meeting.setAgenda(agenda.toString().trim());
            meeting.setNotes(notes.toString().trim());
            meeting.setActionItems(actionItems);
        }

        return meeting;
    }
}
