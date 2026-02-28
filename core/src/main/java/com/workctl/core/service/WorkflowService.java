package com.workctl.core.service;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import com.workctl.core.domain.WorkflowRun;
import com.workctl.core.domain.WorkflowTemplate;
import com.workctl.core.model.RunStatus;
import com.workctl.core.model.StepStatus;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * File-backed service for Workflows.
 *
 * Templates: <workspace>/06_Workflows/templates/{slug}.md
 * Global runs: <workspace>/06_Workflows/runs/YYYY-MM-DD-{slug}.md
 * Project runs: <workspace>/01_Projects/{project}/workflows/YYYY-MM-DD-{slug}.md
 *
 * Pattern mirrors MeetingService: each entity is one .md file; find by scanning for id= substring.
 */
public class WorkflowService {

    // ================================================================
    // TEMPLATE PUBLIC API
    // ================================================================

    public WorkflowTemplate createTemplate(String name, String description, List<String> tags) {
        WorkflowTemplate template = new WorkflowTemplate(name, description, tags);
        saveTemplate(template);
        return template;
    }

    public void saveTemplate(WorkflowTemplate template) {
        try {
            Path file = findTemplateFile(template.getId());
            if (file == null) {
                file = buildTemplateFilePath(template);
            }
            Files.writeString(file, formatTemplate(template));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save workflow template", e);
        }
    }

    public Optional<WorkflowTemplate> loadTemplate(String templateId) {
        try {
            Path file = findTemplateFile(templateId);
            if (file == null) return Optional.empty();
            return Optional.ofNullable(parseTemplate(Files.readAllLines(file)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public List<WorkflowTemplate> listTemplates() {
        try {
            Path dir = getTemplatesDir();
            List<WorkflowTemplate> result = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            WorkflowTemplate t = parseTemplate(Files.readAllLines(p));
                            if (t != null) result.add(t);
                        } catch (Exception ignored) {}
                    });
            }
            result.sort(Comparator.comparing(WorkflowTemplate::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list workflow templates", e);
        }
    }

    public boolean deleteTemplate(String templateId) {
        try {
            Path file = findTemplateFile(templateId);
            if (file == null) return false;
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Adds a step to the end of an existing template and persists it.
     */
    public WorkflowTemplate addTemplateStep(String templateId, String title,
                                             String description, String expectedResult) {
        return addTemplateStep(templateId, title, description, expectedResult, null);
    }

    public WorkflowTemplate addTemplateStep(String templateId, String title,
                                             String description, String expectedResult, String commands) {
        Optional<WorkflowTemplate> opt = loadTemplate(templateId);
        if (opt.isEmpty()) throw new RuntimeException("Template not found: " + templateId);
        WorkflowTemplate template = opt.get();
        WorkflowTemplate.TemplateStep step = new WorkflowTemplate.TemplateStep(title);
        step.setDescription(description);
        step.setExpectedResult(expectedResult);
        if (commands != null && !commands.isBlank()) {
            step.getCodeBlocks().add(commands);
        }
        template.getSteps().add(step);
        saveTemplate(template);
        return template;
    }

    /**
     * Replaces the content of an existing template step in-place and persists it.
     */
    public WorkflowTemplate editTemplateStep(String templateId, int stepIndex,
                                              String title, String description,
                                              String expectedResult, String commands) {
        Optional<WorkflowTemplate> opt = loadTemplate(templateId);
        if (opt.isEmpty()) throw new RuntimeException("Template not found: " + templateId);
        WorkflowTemplate template = opt.get();
        List<WorkflowTemplate.TemplateStep> steps = template.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size())
            throw new RuntimeException("Step index out of range: " + stepIndex);
        WorkflowTemplate.TemplateStep step = steps.get(stepIndex);
        step.setTitle(title);
        step.setDescription(description == null || description.isBlank() ? null : description);
        step.setExpectedResult(expectedResult == null || expectedResult.isBlank() ? null : expectedResult);
        step.getCodeBlocks().clear();
        if (commands != null && !commands.isBlank()) {
            step.getCodeBlocks().add(commands);
        }
        saveTemplate(template);
        return template;
    }

    /**
     * Removes the step at stepIndex from a template and persists it.
     */
    public WorkflowTemplate deleteTemplateStep(String templateId, int stepIndex) {
        Optional<WorkflowTemplate> opt = loadTemplate(templateId);
        if (opt.isEmpty()) throw new RuntimeException("Template not found: " + templateId);
        WorkflowTemplate template = opt.get();
        List<WorkflowTemplate.TemplateStep> steps = template.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size())
            throw new RuntimeException("Step index out of range: " + stepIndex);
        steps.remove(stepIndex);
        saveTemplate(template);
        return template;
    }

    /**
     * Moves a template step up (moveUp=true) or down (moveUp=false) by one position.
     */
    public WorkflowTemplate moveTemplateStep(String templateId, int stepIndex, boolean moveUp) {
        Optional<WorkflowTemplate> opt = loadTemplate(templateId);
        if (opt.isEmpty()) throw new RuntimeException("Template not found: " + templateId);
        WorkflowTemplate template = opt.get();
        List<WorkflowTemplate.TemplateStep> steps = template.getSteps();
        int target = moveUp ? stepIndex - 1 : stepIndex + 1;
        if (target < 0 || target >= steps.size()) return template; // already at boundary
        WorkflowTemplate.TemplateStep moving = steps.remove(stepIndex);
        steps.add(target, moving);
        saveTemplate(template);
        return template;
    }

    // ================================================================
    // RUN PUBLIC API
    // ================================================================

    /**
     * Creates a new run, optionally from a template (copies steps) and/or scoped to a project.
     */
    public WorkflowRun createRun(String name, String templateId, String projectId) {
        WorkflowRun run = new WorkflowRun(name, templateId, projectId);

        // Copy steps from template if provided
        if (templateId != null && !templateId.isBlank()) {
            loadTemplate(templateId).ifPresent(t -> {
                List<WorkflowRun.RunStep> steps = new ArrayList<>();
                for (WorkflowTemplate.TemplateStep ts : t.getSteps()) {
                    WorkflowRun.RunStep rs = new WorkflowRun.RunStep(ts.getTitle());
                    rs.setDescription(ts.getDescription());
                    rs.setExpectedResult(ts.getExpectedResult());
                    rs.setCodeBlocks(ts.getCodeBlocks().isEmpty() ? null : new ArrayList<>(ts.getCodeBlocks()));
                    // Copy sub-step titles as unchecked sub-steps
                    if (!ts.getSubStepTitles().isEmpty()) {
                        List<WorkflowRun.SubStep> subSteps = ts.getSubStepTitles().stream()
                                .map(title -> new WorkflowRun.SubStep(title, false))
                                .collect(Collectors.toList());
                        rs.setSubSteps(subSteps);
                    }
                    steps.add(rs);
                }
                run.setSteps(steps);
            });
        }

        saveRun(run);
        return run;
    }

    public void saveRun(WorkflowRun run) {
        try {
            Path file = findRunFile(run.getId());
            if (file == null) {
                file = buildRunFilePath(run);
            }
            Files.writeString(file, formatRun(run));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save workflow run", e);
        }
    }

    public Optional<WorkflowRun> loadRun(String runId) {
        try {
            Path file = findRunFile(runId);
            if (file == null) return Optional.empty();
            return Optional.ofNullable(parseRun(Files.readAllLines(file)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Lists runs scoped to a project. Null = global runs only.
     */
    public List<WorkflowRun> listRuns(String projectId) {
        try {
            List<WorkflowRun> result = new ArrayList<>();
            if (projectId == null || projectId.isBlank()) {
                collectRunsFromDir(getGlobalRunsDir(), result);
            } else {
                collectRunsFromDir(getProjectRunsDir(projectId), result);
            }
            result.sort(Comparator.comparing(WorkflowRun::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list workflow runs", e);
        }
    }

    /**
     * Lists all runs — global + every project's workflows directory.
     */
    public List<WorkflowRun> listAllRuns() {
        try {
            List<WorkflowRun> result = new ArrayList<>();

            // Global runs
            collectRunsFromDir(getGlobalRunsDir(), result);

            // All project runs
            AppConfig config = ConfigManager.load();
            Path projectsDir = Paths.get(config.getWorkspace()).resolve("01_Projects");
            if (Files.isDirectory(projectsDir)) {
                try (var stream = Files.list(projectsDir)) {
                    stream.filter(Files::isDirectory).forEach(projDir -> {
                        try {
                            Path workflowsDir = projDir.resolve("workflows");
                            collectRunsFromDir(workflowsDir, result);
                        } catch (Exception ignored) {}
                    });
                }
            }

            result.sort(Comparator.comparing(WorkflowRun::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to list all workflow runs", e);
        }
    }

    public boolean deleteRun(String runId) {
        try {
            Path file = findRunFile(runId);
            if (file == null) return false;
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Removes the step at stepIndex from a run and persists it.
     */
    public WorkflowRun deleteRunStep(String runId, int stepIndex) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) throw new RuntimeException("Run not found: " + runId);
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size())
            throw new RuntimeException("Step index out of range: " + stepIndex);
        steps.remove(stepIndex);
        saveRun(run);
        return run;
    }

    /**
     * Moves a run step up (moveUp=true) or down (moveUp=false) by one position.
     */
    public WorkflowRun moveRunStep(String runId, int stepIndex, boolean moveUp) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) throw new RuntimeException("Run not found: " + runId);
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        int target = moveUp ? stepIndex - 1 : stepIndex + 1;
        if (target < 0 || target >= steps.size()) return run; // already at boundary
        WorkflowRun.RunStep moving = steps.remove(stepIndex);
        steps.add(target, moving);
        saveRun(run);
        return run;
    }

    /**
     * Appends a new step to an existing run and persists it.
     */
    public WorkflowRun addRunStep(String runId, String title, String description, String expectedResult) {
        return addRunStep(runId, title, description, expectedResult, null);
    }

    public WorkflowRun addRunStep(String runId, String title, String description,
                                   String expectedResult, String commands) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) throw new RuntimeException("Run not found: " + runId);
        WorkflowRun run = opt.get();
        WorkflowRun.RunStep step = new WorkflowRun.RunStep(title);
        step.setExpectedResult(expectedResult);
        if (commands != null && !commands.isBlank()) {
            step.getCodeBlocks().add(commands);
        }
        run.getSteps().add(step);
        saveRun(run);
        return run;
    }

    // ================================================================
    // STEP OPERATIONS  (load → modify → save)
    // ================================================================

    public boolean updateStepStatus(String runId, int stepIndex, StepStatus newStatus) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) return false;
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size()) return false;
        steps.get(stepIndex).setStatus(newStatus);
        // Auto-complete run if all non-skipped steps are done
        long active = steps.stream().filter(s -> s.getStatus() != StepStatus.SKIPPED).count();
        long done   = steps.stream().filter(s -> s.getStatus() == StepStatus.DONE).count();
        if (active > 0 && done == active) {
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(LocalDate.now());
        }
        saveRun(run);
        return true;
    }

    public boolean addStepNote(String runId, int stepIndex, String note) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) return false;
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size()) return false;
        WorkflowRun.RunStep step = steps.get(stepIndex);
        String existing = step.getNotes();
        step.setNotes(existing == null || existing.isBlank() ? note : existing + "\n" + note);
        saveRun(run);
        return true;
    }

    /** Replaces the entire notes field for a step (used by the edit-note dialog). */
    public boolean setStepNotes(String runId, int stepIndex, String notes) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) return false;
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size()) return false;
        steps.get(stepIndex).setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        saveRun(run);
        return true;
    }

    public boolean setStepActualResult(String runId, int stepIndex, String actualResult) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) return false;
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size()) return false;
        steps.get(stepIndex).setActualResult(actualResult);
        saveRun(run);
        return true;
    }

    public boolean setStepActualCommand(String runId, int stepIndex, String command) {
        Optional<WorkflowRun> opt = loadRun(runId);
        if (opt.isEmpty()) return false;
        WorkflowRun run = opt.get();
        List<WorkflowRun.RunStep> steps = run.getSteps();
        if (stepIndex < 0 || stepIndex >= steps.size()) return false;
        steps.get(stepIndex).setActualCommand(command);
        saveRun(run);
        return true;
    }

    /**
     * Syncs a run's steps from the latest state of its source template.
     * Safe / non-destructive rules:
     *   - For steps that exist in both (matched by index): update title, expectedResult, codeBlocks.
     *     Never touch status, notes, actualResult, or actualCommand — those are user execution data.
     *   - If template has MORE steps than the run: append the new steps as fresh TODO steps.
     *   - If template has FEWER steps than the run: keep the extra run steps unchanged.
     * Returns false if the run has no templateId or the template cannot be found.
     */
    public boolean syncRunFromTemplate(String runId) {
        Optional<WorkflowRun> runOpt = loadRun(runId);
        if (runOpt.isEmpty()) return false;
        WorkflowRun run = runOpt.get();
        if (run.getTemplateId() == null) return false;

        Optional<WorkflowTemplate> tplOpt = loadTemplate(run.getTemplateId());
        if (tplOpt.isEmpty()) return false;
        WorkflowTemplate tpl = tplOpt.get();

        List<WorkflowRun.RunStep>        runSteps = run.getSteps();
        List<WorkflowTemplate.TemplateStep> tplSteps = tpl.getSteps();

        // Update existing steps (matched by index)
        int common = Math.min(runSteps.size(), tplSteps.size());
        for (int i = 0; i < common; i++) {
            WorkflowRun.RunStep        rs = runSteps.get(i);
            WorkflowTemplate.TemplateStep ts = tplSteps.get(i);
            rs.setTitle(ts.getTitle());
            rs.setDescription(ts.getDescription());  // template guidance — always synced
            rs.setExpectedResult(ts.getExpectedResult());
            rs.setCodeBlocks(ts.getCodeBlocks().isEmpty() ? null : new ArrayList<>(ts.getCodeBlocks()));
            // Note: status, notes, actualResult, actualCommand are intentionally left unchanged
        }

        // Append steps that exist in the template but not yet in the run
        for (int i = runSteps.size(); i < tplSteps.size(); i++) {
            WorkflowTemplate.TemplateStep ts = tplSteps.get(i);
            WorkflowRun.RunStep rs = new WorkflowRun.RunStep(ts.getTitle());
            rs.setDescription(ts.getDescription());
            rs.setExpectedResult(ts.getExpectedResult());
            rs.setCodeBlocks(ts.getCodeBlocks().isEmpty() ? null : new ArrayList<>(ts.getCodeBlocks()));
            runSteps.add(rs);
        }

        saveRun(run);
        return true;
    }

    // ================================================================
    // FILE I/O — DIRECTORIES
    // ================================================================

    private Path getTemplatesDir() throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace()).resolve("06_Workflows").resolve("templates");
        Files.createDirectories(dir);
        return dir;
    }

    private Path getGlobalRunsDir() throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace()).resolve("06_Workflows").resolve("runs");
        Files.createDirectories(dir);
        return dir;
    }

    private Path getProjectRunsDir(String projectId) throws IOException {
        AppConfig config = ConfigManager.load();
        Path dir = Paths.get(config.getWorkspace())
                .resolve("01_Projects")
                .resolve(projectId)
                .resolve("workflows");
        Files.createDirectories(dir);
        return dir;
    }

    // ================================================================
    // FILE I/O — FINDING
    // ================================================================

    private Path findTemplateFile(String templateId) throws IOException {
        Path dir = getTemplatesDir();
        if (!Files.exists(dir)) return null;
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> {
                    try { return Files.readString(p).contains("id=" + templateId); }
                    catch (Exception e) { return false; }
                })
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Searches global runs dir first, then all project workflows dirs.
     */
    private Path findRunFile(String runId) throws IOException {
        // Check global runs
        Path globalDir = getGlobalRunsDir();
        Path found = findInDir(globalDir, runId);
        if (found != null) return found;

        // Check all project workflows dirs
        AppConfig config = ConfigManager.load();
        Path projectsDir = Paths.get(config.getWorkspace()).resolve("01_Projects");
        if (!Files.isDirectory(projectsDir)) return null;
        try (var stream = Files.list(projectsDir)) {
            for (Path projDir : stream.filter(Files::isDirectory).toList()) {
                Path workflowsDir = projDir.resolve("workflows");
                found = findInDir(workflowsDir, runId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Path findInDir(Path dir, String id) {
        if (!Files.isDirectory(dir)) return null;
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .filter(p -> {
                    try { return Files.readString(p).contains("id=" + id); }
                    catch (Exception e) { return false; }
                })
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void collectRunsFromDir(Path dir, List<WorkflowRun> result) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .forEach(p -> {
                    try {
                        WorkflowRun r = parseRun(Files.readAllLines(p));
                        if (r != null) result.add(r);
                    } catch (Exception ignored) {}
                });
        }
    }

    // ================================================================
    // FILE I/O — PATH BUILDING
    // ================================================================

    private Path buildTemplateFilePath(WorkflowTemplate t) throws IOException {
        Path dir = getTemplatesDir();
        String slug = buildSlug(t.getName());
        Path path = dir.resolve(slug + ".md");
        int suffix = 2;
        while (Files.exists(path)) {
            path = dir.resolve(slug + "-" + suffix++ + ".md");
        }
        return path;
    }

    private Path buildRunFilePath(WorkflowRun run) throws IOException {
        Path dir = run.getProjectId() != null
                ? getProjectRunsDir(run.getProjectId())
                : getGlobalRunsDir();
        String date = run.getCreatedAt() != null ? run.getCreatedAt().toString() : LocalDate.now().toString();
        String slug = buildSlug(run.getName());
        String base = date + "-" + slug;
        Path path = dir.resolve(base + ".md");
        int suffix = 2;
        while (Files.exists(path)) {
            path = dir.resolve(base + "-" + suffix++ + ".md");
        }
        return path;
    }

    private String buildSlug(String title) {
        if (title == null || title.isBlank()) return "workflow";
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    // ================================================================
    // SERIALIZATION — TEMPLATE
    // ================================================================

    private String formatTemplate(WorkflowTemplate t) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(t.getName()).append("\n\n");

        sb.append("<!-- WORKFLOW_TEMPLATE: id=").append(t.getId());
        if (t.getCreatedAt() != null) sb.append(" created=").append(t.getCreatedAt());
        String tags = t.getTagsString();
        if (!tags.isBlank()) sb.append(" tags=").append(tags);
        sb.append(" -->\n\n");

        if (t.getDescription() != null && !t.getDescription().isBlank()) {
            sb.append(t.getDescription().trim()).append("\n\n");
        }

        List<WorkflowTemplate.TemplateStep> steps = t.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            WorkflowTemplate.TemplateStep step = steps.get(i);
            sb.append("## Step ").append(i + 1).append(": ").append(step.getTitle()).append("\n\n");
            sb.append(step.toMarkdown());
        }

        return sb.toString();
    }

    // ================================================================
    // PARSING — TEMPLATE
    // ================================================================

    private WorkflowTemplate parseTemplate(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;

        WorkflowTemplate template = null;
        StringBuilder description = new StringBuilder();
        boolean metaParsed = false;

        // Collect step sections: list of (title, lines)
        List<String[]> stepSections = new ArrayList<>();  // [0]=title, [1..]=content lines (joined)
        String currentStepTitle = null;
        List<String> currentStepLines = new ArrayList<>();

        Pattern metaP  = Pattern.compile("<!-- WORKFLOW_TEMPLATE:([^>]+)-->");
        Pattern idP    = Pattern.compile("id=([\\w-]+)");
        Pattern dateP  = Pattern.compile("created=(\\d{4}-\\d{2}-\\d{2})");
        Pattern tagsP  = Pattern.compile("tags=([^\\s>]+)");
        Pattern stepP  = Pattern.compile("^## Step \\d+: (.+)$");

        for (String line : lines) {

            // Title
            if (line.startsWith("# ") && template == null) {
                template = new WorkflowTemplate(line.substring(2).trim(), null, null);
                continue;
            }

            // Metadata comment
            Matcher metaM = metaP.matcher(line);
            if (metaM.find() && template != null && !metaParsed) {
                String meta = metaM.group(1);
                Matcher idM = idP.matcher(meta);
                if (idM.find()) template.setId(idM.group(1));
                Matcher dateM = dateP.matcher(meta);
                if (dateM.find()) {
                    try { template.setCreatedAt(LocalDate.parse(dateM.group(1))); } catch (Exception ignored) {}
                }
                Matcher tagsM = tagsP.matcher(meta);
                if (tagsM.find()) template.setTagsFromString(tagsM.group(1));
                metaParsed = true;
                continue;
            }

            // Step header
            Matcher stepM = stepP.matcher(line);
            if (stepM.matches()) {
                // Save previous step
                if (currentStepTitle != null) {
                    stepSections.add(buildStepEntry(currentStepTitle, currentStepLines));
                }
                currentStepTitle = stepM.group(1).trim();
                currentStepLines = new ArrayList<>();
                continue;
            }

            if (currentStepTitle != null) {
                currentStepLines.add(line);
            } else if (template != null && metaParsed && !line.isBlank()) {
                description.append(line).append("\n");
            }
        }

        // Save last step
        if (currentStepTitle != null) {
            stepSections.add(buildStepEntry(currentStepTitle, currentStepLines));
        }

        if (template != null) {
            String desc = description.toString().trim();
            if (!desc.isBlank()) template.setDescription(desc);

            List<WorkflowTemplate.TemplateStep> steps = new ArrayList<>();
            for (String[] entry : stepSections) {
                List<String> sLines = Arrays.asList(entry).subList(1, entry.length);
                steps.add(WorkflowTemplate.TemplateStep.fromSection(entry[0], sLines));
            }
            template.setSteps(steps);
        }

        return template;
    }

    private String[] buildStepEntry(String title, List<String> contentLines) {
        String[] entry = new String[1 + contentLines.size()];
        entry[0] = title;
        for (int i = 0; i < contentLines.size(); i++) {
            entry[i + 1] = contentLines.get(i);
        }
        return entry;
    }

    // ================================================================
    // SERIALIZATION — RUN
    // ================================================================

    private String formatRun(WorkflowRun run) {
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(run.getName()).append("\n\n");

        sb.append("<!-- WORKFLOW_RUN: id=").append(run.getId());
        if (run.getTemplateId() != null) sb.append(" templateId=").append(run.getTemplateId());
        if (run.getProjectId()  != null) sb.append(" project=").append(run.getProjectId());
        sb.append(" status=").append(run.getStatus() != null ? run.getStatus().name() : RunStatus.IN_PROGRESS.name());
        if (run.getCreatedAt()   != null) sb.append(" created=").append(run.getCreatedAt());
        if (run.getCompletedAt() != null) sb.append(" completed=").append(run.getCompletedAt());
        sb.append(" -->\n\n");

        List<WorkflowRun.RunStep> steps = run.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            WorkflowRun.RunStep step = steps.get(i);
            sb.append("## Step ").append(i + 1).append(": ").append(step.getTitle()).append("\n");
            sb.append(step.toMarkdown());
        }

        return sb.toString();
    }

    // ================================================================
    // PARSING — RUN
    // ================================================================

    private WorkflowRun parseRun(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;

        WorkflowRun run = null;
        boolean metaParsed = false;

        List<String[]> stepSections = new ArrayList<>();
        String currentStepTitle = null;
        List<String> currentStepLines = new ArrayList<>();

        Pattern metaP       = Pattern.compile("<!-- WORKFLOW_RUN:([^>]+)-->");
        Pattern idP         = Pattern.compile("id=([\\w-]+)");
        Pattern templateIdP = Pattern.compile("templateId=([\\w-]+)");
        Pattern projectP    = Pattern.compile("project=(\\S+)");
        Pattern statusP     = Pattern.compile("status=(\\w+)");
        Pattern createdP    = Pattern.compile("created=(\\d{4}-\\d{2}-\\d{2})");
        Pattern completedP  = Pattern.compile("completed=(\\d{4}-\\d{2}-\\d{2})");
        Pattern stepP       = Pattern.compile("^## Step \\d+: (.+)$");

        for (String line : lines) {

            // Title
            if (line.startsWith("# ") && run == null) {
                run = new WorkflowRun(line.substring(2).trim(), null, null);
                continue;
            }

            // Metadata
            Matcher metaM = metaP.matcher(line);
            if (metaM.find() && run != null && !metaParsed) {
                String meta = metaM.group(1);
                Matcher idM = idP.matcher(meta);
                if (idM.find()) run.setId(idM.group(1));
                Matcher tplM = templateIdP.matcher(meta);
                if (tplM.find()) run.setTemplateId(tplM.group(1));
                Matcher projM = projectP.matcher(meta);
                if (projM.find()) run.setProjectId(projM.group(1));
                Matcher statusM = statusP.matcher(meta);
                if (statusM.find()) {
                    try { run.setStatus(RunStatus.valueOf(statusM.group(1))); } catch (Exception ignored) {}
                }
                Matcher createdM = createdP.matcher(meta);
                if (createdM.find()) {
                    try { run.setCreatedAt(LocalDate.parse(createdM.group(1))); } catch (Exception ignored) {}
                }
                Matcher completedM = completedP.matcher(meta);
                if (completedM.find()) {
                    try { run.setCompletedAt(LocalDate.parse(completedM.group(1))); } catch (Exception ignored) {}
                }
                metaParsed = true;
                continue;
            }

            // Step header
            Matcher stepM = stepP.matcher(line);
            if (stepM.matches()) {
                if (currentStepTitle != null) {
                    stepSections.add(buildStepEntry(currentStepTitle, currentStepLines));
                }
                currentStepTitle = stepM.group(1).trim();
                currentStepLines = new ArrayList<>();
                continue;
            }

            if (currentStepTitle != null) {
                currentStepLines.add(line);
            }
        }

        if (currentStepTitle != null) {
            stepSections.add(buildStepEntry(currentStepTitle, currentStepLines));
        }

        if (run != null) {
            List<WorkflowRun.RunStep> steps = new ArrayList<>();
            for (String[] entry : stepSections) {
                List<String> sLines = Arrays.asList(entry).subList(1, entry.length);
                steps.add(WorkflowRun.RunStep.fromSection(entry[0], sLines));
            }
            run.setSteps(steps);
        }

        return run;
    }
}
