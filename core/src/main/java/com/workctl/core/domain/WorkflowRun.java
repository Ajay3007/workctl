package com.workctl.core.domain;

import com.workctl.core.model.RunStatus;
import com.workctl.core.model.StepStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An execution instance of a WorkflowTemplate (or a standalone run).
 * Each step tracks its own status, notes, and results.
 */
public class WorkflowRun {

    // ===================================================
    // SubStep inner class (nested inside RunStep)
    // ===================================================

    public static class SubStep {

        private String  title;
        private boolean done;

        public SubStep(String title, boolean done) {
            this.title = title;
            this.done  = done;
        }

        public String  getTitle() { return title; }
        public boolean isDone()   { return done; }

        public void setTitle(String t)  { this.title = t; }
        public void setDone(boolean d)  { this.done = d; }

        /** Serializes to: "    - [x] title" (4-space indent, matches Task.SubTask pattern) */
        public String toMarkdownLine() {
            return "    - " + (done ? "[x]" : "[ ]") + " " + title;
        }

        /** Parses a line like "    - [ ] title" or "    - [x] title". Returns null on no match. */
        public static SubStep fromLine(String rawLine) {
            if (rawLine == null) return null;
            Pattern p = Pattern.compile("^    - \\[([ x])\\] (.+)$");
            Matcher m = p.matcher(rawLine);
            if (!m.matches()) return null;
            return new SubStep(m.group(2).trim(), m.group(1).equals("x"));
        }
    }

    // ===================================================
    // RunStep inner class
    // ===================================================

    public static class RunStep {

        private String       id;
        private String       title;
        private StepStatus   status;
        private String       description;    // nullable — template step guidance, always synced from template
        private String       notes;          // observations while executing, multi-line
        private String       expectedResult; // nullable, copied from template
        private String       actualResult;   // nullable, recorded during run
        private List<String> codeBlocks;     // nullable — template commands (may contain placeholders)
        private String       actualCommand;  // nullable — real command used (placeholders filled in)
        private List<SubStep> subSteps;

        public RunStep(String title) {
            this.id       = UUID.randomUUID().toString();
            this.title    = title != null ? title : "";
            this.status   = StepStatus.TODO;
            this.subSteps = new ArrayList<>();
        }

        public String       getId()             { return id; }
        public String       getTitle()          { return title; }
        public StepStatus   getStatus()         { return status; }
        public String       getDescription()    { return description; }
        public String       getNotes()          { return notes; }
        public String       getExpectedResult() { return expectedResult; }
        public String       getActualResult()   { return actualResult; }
        public String       getActualCommand()  { return actualCommand; }

        public List<String> getCodeBlocks() {
            if (codeBlocks == null) codeBlocks = new ArrayList<>();
            return codeBlocks;
        }

        public List<SubStep> getSubSteps() {
            if (subSteps == null) subSteps = new ArrayList<>();
            return subSteps;
        }

        public int getDoneSubStepCount() {
            if (subSteps == null) return 0;
            return (int) subSteps.stream().filter(SubStep::isDone).count();
        }

        public void setId(String id)                  { this.id = id; }
        public void setTitle(String t)                { this.title = t != null ? t : ""; }
        public void setStatus(StepStatus s)           { this.status = s; }
        public void setDescription(String d)          { this.description = (d != null && !d.isBlank()) ? d.trim() : null; }
        public void setNotes(String n)                { this.notes = n; }
        public void setExpectedResult(String e)       { this.expectedResult = e; }
        public void setActualResult(String a)         { this.actualResult = a; }
        public void setActualCommand(String a)        { this.actualCommand = (a != null && !a.isBlank()) ? a.trim() : null; }
        public void setCodeBlocks(List<String> c)     { this.codeBlocks = c; }
        public void setSubSteps(List<SubStep> s)      { this.subSteps = s != null ? s : new ArrayList<>(); }

        /** Returns a CLI-friendly symbol for the step status. */
        public String statusSymbol() {
            return switch (status) {
                case DONE    -> "✓";
                case SKIPPED -> "–";
                case TODO    -> "○";
            };
        }

        /**
         * Serializes to the Markdown section body (excluding the "## Step N: title" heading).
         */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();

            // Step metadata comment
            sb.append("<!-- STEP: id=").append(id)
              .append(" status=").append(status.name())
              .append(" -->\n\n");

            if (description != null && !description.isBlank()) {
                sb.append("**Guidance:**\n").append(description.trim()).append("\n\n");
            }

            if (notes != null && !notes.isBlank()) {
                sb.append(notes.trim()).append("\n\n");
            }

            if (expectedResult != null && !expectedResult.isBlank()) {
                sb.append("**Expected:** ").append(expectedResult.trim()).append("\n\n");
            }

            if (actualResult != null && !actualResult.isBlank()) {
                sb.append("**Actual:** ").append(actualResult.trim()).append("\n\n");
            }

            if (codeBlocks != null) {
                for (String block : codeBlocks) {
                    sb.append("```\n").append(block.trim()).append("\n```\n\n");
                }
            }

            if (actualCommand != null && !actualCommand.isBlank()) {
                sb.append("**Actual Command:**\n```\n").append(actualCommand.trim()).append("\n```\n\n");
            }

            if (subSteps != null) {
                for (SubStep ss : subSteps) {
                    sb.append(ss.toMarkdownLine()).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        }

        /**
         * Parses a RunStep from lines following a "## Step N: title" header.
         * The first line must be the <!-- STEP: ... --> metadata comment.
         */
        public static RunStep fromSection(String title, List<String> lines) {
            RunStep step = new RunStep(title);

            StringBuilder notes = new StringBuilder();
            StringBuilder guidanceBuilder = new StringBuilder();
            StringBuilder currentBlock = null;
            boolean       inGuidance = false;
            boolean       inActualCommandBlock   = false;
            boolean       nextBlockIsActualCommand = false;
            List<String>  codeBlocks = new ArrayList<>();
            List<SubStep> subSteps = new ArrayList<>();

            Pattern stepMetaP  = Pattern.compile("<!-- STEP: id=([\\w-]+) status=(\\w+) -->");
            Pattern expectedP  = Pattern.compile("^\\*\\*Expected:\\*\\* (.+)$");
            Pattern actualP    = Pattern.compile("^\\*\\*Actual:\\*\\* (.+)$");

            boolean metaParsed = false;

            for (String line : lines) {
                // Metadata comment (first line of section body)
                if (!metaParsed) {
                    Matcher mm = stepMetaP.matcher(line.trim());
                    if (mm.find()) {
                        step.setId(mm.group(1));
                        try { step.setStatus(StepStatus.valueOf(mm.group(2))); } catch (Exception ignored) {}
                        metaParsed = true;
                        continue;
                    }
                }

                // Code block delimiter
                if (line.startsWith("```")) {
                    if (currentBlock == null) {
                        currentBlock = new StringBuilder();
                        inActualCommandBlock = nextBlockIsActualCommand;
                        nextBlockIsActualCommand = false;
                    } else {
                        String captured = currentBlock.toString().trim();
                        if (inActualCommandBlock) {
                            step.setActualCommand(captured.isEmpty() ? null : captured);
                        } else {
                            codeBlocks.add(captured);
                        }
                        currentBlock = null;
                        inActualCommandBlock = false;
                    }
                    continue;
                }

                if (currentBlock != null) {
                    currentBlock.append(line).append("\n");
                    continue;
                }

                // Actual Command label — next code block belongs to actualCommand
                if (line.trim().equals("**Actual Command:**")) {
                    nextBlockIsActualCommand = true;
                    inGuidance = false;
                    continue;
                }

                // Guidance label — following lines are template description until blank
                if (line.trim().equals("**Guidance:**")) {
                    inGuidance = true;
                    continue;
                }

                // Accumulate guidance content
                if (inGuidance) {
                    if (line.isBlank()) {
                        inGuidance = false;
                        // blank line falls through; notes accumulator ignores blank lines
                    } else {
                        guidanceBuilder.append(line).append("\n");
                        continue;
                    }
                }

                // Sub-steps (4-space indented checkboxes)
                SubStep ss = SubStep.fromLine(line);
                if (ss != null) {
                    subSteps.add(ss);
                    continue;
                }

                // Expected / Actual result
                Matcher expM = expectedP.matcher(line.trim());
                if (expM.matches()) {
                    step.setExpectedResult(expM.group(1));
                    continue;
                }

                Matcher actM = actualP.matcher(line.trim());
                if (actM.matches()) {
                    step.setActualResult(actM.group(1));
                    continue;
                }

                // Everything else goes into notes
                if (!line.isBlank()) {
                    notes.append(line).append("\n");
                }
            }

            String g = guidanceBuilder.toString().trim();
            step.setDescription(g.isBlank() ? null : g);
            String n = notes.toString().trim();
            step.setNotes(n.isBlank() ? null : n);
            step.setCodeBlocks(codeBlocks.isEmpty() ? null : codeBlocks);
            step.setSubSteps(subSteps.isEmpty() ? null : subSteps);
            return step;
        }
    }

    // ===================================================
    // Fields
    // ===================================================

    private String        id;
    private String        name;
    private String        templateId;   // nullable — ID of the template this run was created from
    private String        projectId;    // nullable — project scope
    private RunStatus     status;
    private LocalDate     createdAt;
    private LocalDate     completedAt;  // nullable
    private List<RunStep> steps;

    // ===================================================
    // Constructor
    // ===================================================

    public WorkflowRun(String name, String templateId, String projectId) {
        this.id         = UUID.randomUUID().toString();
        this.name       = name != null ? name : "";
        this.templateId = (templateId != null && !templateId.isBlank()) ? templateId : null;
        this.projectId  = (projectId  != null && !projectId.isBlank())  ? projectId  : null;
        this.status     = RunStatus.IN_PROGRESS;
        this.createdAt  = LocalDate.now();
        this.steps      = new ArrayList<>();
    }

    // ===================================================
    // Getters
    // ===================================================

    public String        getId()          { return id; }
    public String        getName()        { return name; }
    public String        getTemplateId()  { return templateId; }
    public String        getProjectId()   { return projectId; }
    public RunStatus     getStatus()      { return status; }
    public LocalDate     getCreatedAt()   { return createdAt; }
    public LocalDate     getCompletedAt() { return completedAt; }

    public List<RunStep> getSteps() {
        if (steps == null) steps = new ArrayList<>();
        return steps;
    }

    public int getDoneStepCount() {
        if (steps == null) return 0;
        return (int) steps.stream().filter(s -> s.getStatus() == StepStatus.DONE).count();
    }

    public int getActiveStepCount() {
        if (steps == null) return 0;
        return (int) steps.stream().filter(s -> s.getStatus() != StepStatus.SKIPPED).count();
    }

    // ===================================================
    // Setters
    // ===================================================

    public void setId(String id)                  { this.id = id; }
    public void setName(String n)                 { this.name = n != null ? n : ""; }
    public void setTemplateId(String t)           { this.templateId = t; }
    public void setProjectId(String p)            { this.projectId = (p != null && !p.isBlank()) ? p : null; }
    public void setStatus(RunStatus s)            { this.status = s; }
    public void setCreatedAt(LocalDate c)         { this.createdAt = c; }
    public void setCompletedAt(LocalDate c)       { this.completedAt = c; }
    public void setSteps(List<RunStep> steps)     { this.steps = steps != null ? steps : new ArrayList<>(); }

    // ===================================================
    // Equality
    // ===================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowRun r)) return false;
        return Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
