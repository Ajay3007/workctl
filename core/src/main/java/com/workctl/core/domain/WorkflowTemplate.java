package com.workctl.core.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A reusable workflow blueprint. Defines a sequence of steps that can be
 * executed repeatedly as named WorkflowRun instances.
 */
public class WorkflowTemplate {

    // ===================================================
    // TemplateStep inner class
    // ===================================================

    public static class TemplateStep {

        private String       title;
        private String       description;     // nullable, multi-line guidance
        private String       expectedResult;  // nullable
        private List<String> codeBlocks;      // nullable, each entry = content of one fenced block
        private List<String> subStepTitles;   // nullable, plain titles for sub-checklist

        public TemplateStep(String title) {
            this.title = title != null ? title : "";
        }

        public String       getTitle()          { return title; }
        public String       getDescription()    { return description; }
        public String       getExpectedResult() { return expectedResult; }
        public List<String> getCodeBlocks()    { if (codeBlocks == null)    codeBlocks    = new ArrayList<>(); return codeBlocks; }
        public List<String> getSubStepTitles() { if (subStepTitles == null) subStepTitles = new ArrayList<>(); return subStepTitles; }

        public void setTitle(String t)               { this.title = t != null ? t : ""; }
        public void setDescription(String d)         { this.description = d; }
        public void setExpectedResult(String e)      { this.expectedResult = e; }
        public void setCodeBlocks(List<String> c)    { this.codeBlocks = c; }
        public void setSubStepTitles(List<String> s) { this.subStepTitles = s; }

        /**
         * Serializes to a Markdown section block (without the ## Step N: heading).
         * The heading is written by WorkflowService when formatting the full template.
         */
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();

            if (description != null && !description.isBlank()) {
                sb.append(description.trim()).append("\n\n");
            }

            if (expectedResult != null && !expectedResult.isBlank()) {
                sb.append("**Expected:** ").append(expectedResult.trim()).append("\n\n");
            }

            if (codeBlocks != null) {
                for (String block : codeBlocks) {
                    sb.append("```\n").append(block.trim()).append("\n```\n\n");
                }
            }

            if (subStepTitles != null) {
                for (String st : subStepTitles) {
                    sb.append("- [ ] ").append(st).append("\n");
                }
                sb.append("\n");
            }

            return sb.toString();
        }

        /**
         * Parses a TemplateStep from the lines following a "## Step N: title" header.
         * Lines belong to this step until the next "## Step" header or end of list.
         */
        public static TemplateStep fromSection(String title, List<String> lines) {
            TemplateStep step = new TemplateStep(title);

            StringBuilder description = new StringBuilder();
            StringBuilder currentBlock = null;
            String        expectedResult = null;
            List<String>  codeBlocks = new ArrayList<>();
            List<String>  subSteps = new ArrayList<>();

            for (String line : lines) {
                // Code block delimiter
                if (line.startsWith("```")) {
                    if (currentBlock == null) {
                        currentBlock = new StringBuilder();
                    } else {
                        codeBlocks.add(currentBlock.toString().trim());
                        currentBlock = null;
                    }
                    continue;
                }

                if (currentBlock != null) {
                    currentBlock.append(line).append("\n");
                    continue;
                }

                // Expected result
                if (line.startsWith("**Expected:**")) {
                    expectedResult = line.substring("**Expected:**".length()).trim();
                    continue;
                }

                // Sub-step titles (plain checklist items in template)
                if (line.matches("^- \\[[ x]\\] .+")) {
                    subSteps.add(line.substring(6).trim()); // strip "- [ ] "
                    continue;
                }

                // Blank lines as separators — include in description accumulation
                if (!line.isBlank()) {
                    description.append(line).append("\n");
                }
            }

            String desc = description.toString().trim();
            step.setDescription(desc.isBlank() ? null : desc);
            step.setExpectedResult(expectedResult);
            step.setCodeBlocks(codeBlocks.isEmpty() ? null : codeBlocks);
            step.setSubStepTitles(subSteps.isEmpty() ? null : subSteps);
            return step;
        }
    }

    // ===================================================
    // Fields
    // ===================================================

    private String           id;
    private String           name;
    private String           description;  // nullable, overview of the template
    private List<TemplateStep> steps;
    private LocalDate        createdAt;
    private List<String>     tags;

    // ===================================================
    // Constructor
    // ===================================================

    public WorkflowTemplate(String name, String description, List<String> tags) {
        this.id          = UUID.randomUUID().toString();
        this.name        = name != null ? name : "";
        this.description = (description != null && !description.isBlank()) ? description : null;
        this.steps       = new ArrayList<>();
        this.createdAt   = LocalDate.now();
        this.tags        = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    // ===================================================
    // Getters
    // ===================================================

    public String              getId()          { return id; }
    public String              getName()        { return name; }
    public String              getDescription() { return description; }
    public LocalDate           getCreatedAt()   { return createdAt; }

    public List<TemplateStep>  getSteps() {
        if (steps == null) steps = new ArrayList<>();
        return steps;
    }

    public List<String>        getTags() {
        if (tags == null) tags = new ArrayList<>();
        return tags;
    }

    public String getTagsString() {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(",", tags);
    }

    // ===================================================
    // Setters
    // ===================================================

    public void setId(String id)                     { this.id = id; }
    public void setName(String n)                    { this.name = n != null ? n : ""; }
    public void setDescription(String d)             { this.description = d; }
    public void setCreatedAt(LocalDate c)            { this.createdAt = c; }
    public void setSteps(List<TemplateStep> steps)   { this.steps = steps != null ? steps : new ArrayList<>(); }

    public void setTagsFromString(String tagsStr) {
        if (tagsStr == null || tagsStr.isBlank()) {
            this.tags = new ArrayList<>();
        } else {
            this.tags = new ArrayList<>(Arrays.asList(tagsStr.split(",")));
        }
    }

    // ===================================================
    // Equality
    // ===================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowTemplate t)) return false;
        return Objects.equals(id, t.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
