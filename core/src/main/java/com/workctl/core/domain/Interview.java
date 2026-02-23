package com.workctl.core.domain;

import com.workctl.core.model.InterviewResult;
import com.workctl.core.model.InterviewRound;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Interview {

    // ===================================================
    // ExperienceLink inner class
    //   Stores a previous interview experience reference
    //   e.g. Glassdoor review, LeetCode discuss thread, Notion notes
    // ===================================================

    public static class ExperienceLink {
        private String title;
        private String url;

        public ExperienceLink(String title, String url) {
            this.title = title != null ? title.trim() : "";
            this.url   = url   != null ? url.trim()   : "";
        }

        public String getTitle() { return title; }
        public String getUrl()   { return url; }
        public void setTitle(String t) { this.title = t != null ? t.trim() : ""; }
        public void setUrl(String u)   { this.url   = u != null ? u.trim() : ""; }

        /** Serialize to markdown: - [title](url) */
        public String toMarkdownLine() {
            return "- [" + title + "](" + url + ")";
        }

        /** Parse a markdown link line: - [title](url). Returns null if no match. */
        public static ExperienceLink fromLine(String line) {
            if (line == null) return null;
            Matcher m = Pattern.compile("^- \\[([^\\]]*)]\\(([^)]+)\\)\\s*$").matcher(line.trim());
            if (!m.matches()) return null;
            return new ExperienceLink(m.group(1), m.group(2));
        }
    }

    // ===================================================
    // InterviewQuestion inner class
    // ===================================================

    public static class InterviewQuestion {

        private String  section;    // "DSA", "OS", "Networking", "System Design", etc.
        private String  text;       // question text / name
        private String  url;        // optional link (LeetCode, blog, docs)
        private boolean done;       // answered well / practised
        private boolean important;  // starred / flagged
        private String  notes;      // answer/approach notes (nullable)

        public InterviewQuestion(String section, String text, String url,
                                 boolean done, boolean important, String notes) {
            this.section   = section;
            this.text      = text;
            this.url       = (url != null && !url.isBlank()) ? url.trim() : null;
            this.done      = done;
            this.important = important;
            this.notes     = notes;
        }

        public String  getSection()   { return section; }
        public String  getText()      { return text; }
        public String  getUrl()       { return url; }
        public boolean isDone()       { return done; }
        public boolean isImportant()  { return important; }
        public String  getNotes()     { return notes; }

        public void setSection(String s)    { this.section   = s; }
        public void setText(String t)       { this.text      = t; }
        public void setUrl(String u)        { this.url       = (u != null && !u.isBlank()) ? u.trim() : null; }
        public void setDone(boolean d)      { this.done      = d; }
        public void setImportant(boolean i) { this.important = i; }
        public void setNotes(String n)      { this.notes     = n; }

        /**
         * Serialize to markdown lines.
         *   - [ ] Two Sum (important)
         *     url: https://leetcode.com/problems/two-sum/
         *     > HashMap O(n) approach
         */
        public List<String> toMarkdownLines() {
            List<String> lines = new ArrayList<>();
            StringBuilder sb = new StringBuilder("- ");
            sb.append(done ? "[x]" : "[ ]").append(" ").append(text);
            if (important) sb.append(" (important)");
            lines.add(sb.toString());
            if (url != null) {
                lines.add("  url: " + url);
            }
            if (notes != null && !notes.isBlank()) {
                lines.add("  > " + notes.trim());
            }
            return lines;
        }

        /**
         * Parse a markdown item line like:
         *   - [ ] Two Sum (important)
         *   - [x] LRU Cache
         * The url and notes are attached by the caller after parsing continuation lines.
         */
        public static InterviewQuestion fromLine(String section, String line) {
            if (line == null) return null;
            Pattern p = Pattern.compile("^- \\[([ x])\\] (.+)$");
            Matcher m = p.matcher(line.trim());
            if (!m.matches()) return null;

            boolean done = m.group(1).equals("x");
            String  rest = m.group(2).trim();
            boolean important = false;

            if (rest.endsWith(" (important)")) {
                important = true;
                rest = rest.substring(0, rest.length() - " (important)".length()).trim();
            }
            return new InterviewQuestion(section, rest, null, done, important, null);
        }
    }

    // ===================================================
    // Fields
    // ===================================================

    private String                  id;
    private String                  company;
    private String                  role;
    private LocalDate               date;
    private InterviewRound          round;
    private InterviewResult         result;
    private String                  jobUrl;           // job posting / calendar link
    private String                  notes;            // overall impressions
    private List<ExperienceLink>    experienceLinks;  // links to past experiences
    private List<InterviewQuestion> questions;
    private LocalDate               createdAt;

    // ===================================================
    // Constructor
    // ===================================================

    public Interview(String company, String role, LocalDate date) {
        this.id              = UUID.randomUUID().toString();
        this.company         = company != null ? company.trim() : "";
        this.role            = role    != null ? role.trim()    : "";
        this.date            = date    != null ? date           : LocalDate.now();
        this.round           = InterviewRound.TECHNICAL;
        this.result          = InterviewResult.PENDING;
        this.experienceLinks = new ArrayList<>();
        this.questions       = new ArrayList<>();
        this.createdAt       = LocalDate.now();
    }

    // ===================================================
    // Getters
    // ===================================================

    public String getId()        { return id; }
    public String getCompany()   { return company; }
    public String getRole()      { return role; }
    public LocalDate getDate()   { return date; }
    public InterviewRound getRound()    { return round; }
    public InterviewResult getResult()  { return result; }
    public String getJobUrl()    { return jobUrl; }
    public String getNotes()     { return notes; }
    public LocalDate getCreatedAt() { return createdAt; }

    public List<ExperienceLink> getExperienceLinks() {
        if (experienceLinks == null) experienceLinks = new ArrayList<>();
        return experienceLinks;
    }

    public List<InterviewQuestion> getQuestions() {
        if (questions == null) questions = new ArrayList<>();
        return questions;
    }

    public int getTotalQuestionCount()     { return questions == null ? 0 : questions.size(); }
    public int getDoneQuestionCount()      {
        return questions == null ? 0 :
                (int) questions.stream().filter(InterviewQuestion::isDone).count();
    }
    public int getImportantQuestionCount() {
        return questions == null ? 0 :
                (int) questions.stream().filter(InterviewQuestion::isImportant).count();
    }

    // ===================================================
    // Setters
    // ===================================================

    public void setId(String id)                    { this.id      = id; }
    public void setCompany(String c)                { this.company = c != null ? c.trim() : ""; }
    public void setRole(String r)                   { this.role    = r != null ? r.trim() : ""; }
    public void setDate(LocalDate d)                { this.date    = d; }
    public void setRound(InterviewRound r)          { this.round   = r; }
    public void setResult(InterviewResult r)        { this.result  = r; }
    public void setJobUrl(String u)                 { this.jobUrl  = (u != null && !u.isBlank()) ? u.trim() : null; }
    public void setNotes(String n)                  { this.notes   = n; }
    public void setCreatedAt(LocalDate d)           { this.createdAt = d; }

    public void setExperienceLinks(List<ExperienceLink> links) {
        this.experienceLinks = links != null ? links : new ArrayList<>();
    }

    public void setQuestions(List<InterviewQuestion> q) {
        this.questions = q != null ? q : new ArrayList<>();
    }

    // ===================================================
    // Equality
    // ===================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Interview i)) return false;
        return Objects.equals(id, i.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
