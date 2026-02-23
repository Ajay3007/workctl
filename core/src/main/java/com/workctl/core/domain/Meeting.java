package com.workctl.core.domain;

import com.workctl.core.model.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Meeting {

    // ===================================================
    // ActionItem inner class
    // ===================================================

    public static class ActionItem {

        private String    title;
        private boolean   done;
        private String    owner;    // nullable — unassigned
        private LocalDate dueDate;  // nullable

        public ActionItem(String title, boolean done, String owner, LocalDate dueDate) {
            this.title   = title;
            this.done    = done;
            this.owner   = owner;
            this.dueDate = dueDate;
        }

        public String    getTitle()   { return title; }
        public boolean   isDone()     { return done; }
        public String    getOwner()   { return owner; }
        public LocalDate getDueDate() { return dueDate; }

        public void setTitle(String t)      { this.title   = t; }
        public void setDone(boolean d)      { this.done    = d; }
        public void setOwner(String o)      { this.owner   = o; }
        public void setDueDate(LocalDate d) { this.dueDate = d; }

        /**
         * Serialize to a markdown line.
         * Format: - [ ] title (owner=X due=YYYY-MM-DD)
         * The trailing (…) is omitted when neither owner nor due date is set.
         */
        public String toMarkdownLine() {
            StringBuilder sb = new StringBuilder("- ");
            sb.append(done ? "[x]" : "[ ]").append(" ").append(title);

            boolean hasOwner = owner != null && !owner.isBlank();
            boolean hasDue   = dueDate != null;

            if (hasOwner || hasDue) {
                sb.append(" (");
                if (hasOwner) sb.append("owner=").append(owner);
                if (hasOwner && hasDue) sb.append(" ");
                if (hasDue) sb.append("due=").append(dueDate);
                sb.append(")");
            }
            return sb.toString();
        }

        /**
         * Parse a line like:
         *   - [ ] Fix the bug (owner=Ajay due=2026-02-24)
         *   - [x] Done item
         * Returns null if the line does not match.
         */
        public static ActionItem fromLine(String rawLine) {
            if (rawLine == null) return null;
            Pattern p = Pattern.compile("^- \\[([ x])\\] (.+)$");
            Matcher m = p.matcher(rawLine.trim());
            if (!m.matches()) return null;

            boolean done  = m.group(1).equals("x");
            String  rest  = m.group(2);
            String  title = rest;
            String  owner = null;
            LocalDate dueDate = null;

            // Extract optional trailing (owner=X due=DATE)
            Pattern metaP = Pattern.compile("\\s*\\(([^)]+)\\)\\s*$");
            Matcher metaM = metaP.matcher(rest);
            if (metaM.find()) {
                String meta = metaM.group(1);
                title = rest.substring(0, metaM.start()).trim();

                Matcher ownerM = Pattern.compile("owner=([^\\s)]+)").matcher(meta);
                if (ownerM.find()) owner = ownerM.group(1).trim();

                Matcher dueM = Pattern.compile("due=(\\d{4}-\\d{2}-\\d{2})").matcher(meta);
                if (dueM.find()) {
                    try { dueDate = LocalDate.parse(dueM.group(1)); } catch (Exception ignored) {}
                }
            }
            return new ActionItem(title, done, owner, dueDate);
        }
    }

    // ===================================================
    // Fields
    // ===================================================

    private String           id;
    private String           projectId;    // null = not linked to a project
    private String           title;
    private LocalDateTime    dateTime;
    private MeetingStatus    status;
    private String           attendees;
    private String           agenda;
    private String           notes;
    private List<ActionItem> actionItems = new ArrayList<>();
    private LocalDateTime    createdAt;

    // ===================================================
    // Constructor
    // ===================================================

    public Meeting(String projectId, String title, LocalDateTime dateTime) {
        this.id          = UUID.randomUUID().toString();
        this.projectId   = (projectId != null && !projectId.isBlank()) ? projectId : null;
        this.title       = title != null ? title : "";
        this.dateTime    = dateTime;
        this.status      = deriveStatus(dateTime);
        this.createdAt   = LocalDateTime.now();
        this.actionItems = new ArrayList<>();
    }

    /** Auto-derives status: meetings in the past default to DONE. */
    private static MeetingStatus deriveStatus(LocalDateTime dt) {
        if (dt == null) return MeetingStatus.SCHEDULED;
        return dt.isBefore(LocalDateTime.now()) ? MeetingStatus.DONE : MeetingStatus.SCHEDULED;
    }

    // ===================================================
    // Getters
    // ===================================================

    public String          getId()          { return id; }
    public String          getProjectId()   { return projectId; }
    public String          getTitle()       { return title; }
    public LocalDateTime   getDateTime()    { return dateTime; }
    public MeetingStatus   getStatus()      { return status; }
    public String          getAttendees()   { return attendees; }
    public String          getAgenda()      { return agenda; }
    public String          getNotes()       { return notes; }
    public LocalDateTime   getCreatedAt()   { return createdAt; }

    public List<ActionItem> getActionItems() {
        if (actionItems == null) actionItems = new ArrayList<>();
        return actionItems;
    }

    public int getDoneActionItemCount() {
        if (actionItems == null) return 0;
        return (int) actionItems.stream().filter(ActionItem::isDone).count();
    }

    public int getTotalActionItemCount() {
        return actionItems == null ? 0 : actionItems.size();
    }

    // ===================================================
    // Setters
    // ===================================================

    public void setId(String id)                    { this.id = id; }
    public void setProjectId(String p)              { this.projectId = (p != null && !p.isBlank()) ? p : null; }
    public void setTitle(String t)                  { this.title = t != null ? t : ""; }
    public void setDateTime(LocalDateTime dt)       { this.dateTime = dt; }
    public void setStatus(MeetingStatus s)          { this.status = s; }
    public void setAttendees(String a)              { this.attendees = a; }
    public void setAgenda(String a)                 { this.agenda = a; }
    public void setNotes(String n)                  { this.notes = n; }
    public void setCreatedAt(LocalDateTime c)       { this.createdAt = c; }

    public void setActionItems(List<ActionItem> ai) {
        this.actionItems = ai != null ? ai : new ArrayList<>();
    }

    // ===================================================
    // Equality
    // ===================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Meeting m)) return false;
        return Objects.equals(id, m.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
