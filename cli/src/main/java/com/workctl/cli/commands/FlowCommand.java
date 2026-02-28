package com.workctl.cli.commands;

import com.workctl.cli.util.ConsolePrinter;
import com.workctl.cli.util.EditorUtil;
import com.workctl.core.domain.WorkflowRun;
import com.workctl.core.domain.WorkflowTemplate;
import com.workctl.core.model.RunStatus;
import com.workctl.core.model.StepStatus;
import com.workctl.core.service.WorkflowService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * workctl flow — Manage workflow templates and runs.
 *
 * Templates are reusable step-by-step blueprints.
 * Runs are named executions of a template (or standalone), each tracking
 * step progress, notes, and results independently.
 */
@Command(
    name = "flow",
    description = "Manage workflow templates and runs",
    subcommands = {
        FlowCommand.TemplateGroup.class,
        FlowCommand.NewRun.class,
        FlowCommand.ListRuns.class,
        FlowCommand.ShowRun.class,
        FlowCommand.DeleteRun.class,
        FlowCommand.StepGroup.class
    }
)
public class FlowCommand implements Runnable {

    private static final WorkflowService workflowService = new WorkflowService();

    @Override
    public void run() {
        ConsolePrinter.info("Usage: workctl flow <template|new|list|show|step|delete>");
        ConsolePrinter.plain("  workctl flow template new \"My Procedure\"");
        ConsolePrinter.plain("  workctl flow new \"Run name\" --template <id> --project <name>");
        ConsolePrinter.plain("  workctl flow list [--project <name>]");
        ConsolePrinter.plain("  workctl flow show <run-id>");
        ConsolePrinter.plain("  workctl flow step done <run-id> <step-num>");
        ConsolePrinter.plain("Run 'workctl flow --help' for full usage.");
    }

    // ================================================================
    // TEMPLATE GROUP
    // ================================================================

    @Command(
        name = "template",
        description = "Manage workflow templates",
        subcommands = {
            FlowCommand.TemplateNew.class,
            FlowCommand.TemplateList.class,
            FlowCommand.TemplateShow.class,
            FlowCommand.TemplateStepAdd.class,
            FlowCommand.TemplateDelete.class
        }
    )
    static class TemplateGroup implements Runnable {
        @Override
        public void run() {
            ConsolePrinter.info("Usage: workctl flow template <new|list|show|step-add|delete>");
            ConsolePrinter.plain("Run 'workctl flow template --help' for details.");
        }
    }

    @Command(name = "new", description = "Create a new workflow template")
    static class TemplateNew implements Runnable {

        @Parameters(index = "0", description = "Template name")
        private String name;

        @Option(names = {"--desc", "--description"}, description = "Template description")
        private String description;

        @Option(names = {"--tags"}, description = "Comma-separated tags (e.g. dev,release)")
        private String tags;

        @Override
        public void run() {
            try {
                List<String> tagList = (tags != null && !tags.isBlank())
                        ? List.of(tags.split(","))
                        : List.of();
                WorkflowTemplate template = workflowService.createTemplate(name, description, tagList);
                ConsolePrinter.success("Template created: " + template.getName());
                ConsolePrinter.info("ID: " + template.getId());
                ConsolePrinter.plain("  Add steps: workctl flow template step-add " + template.getId().substring(0, 8) + "... \"Step title\"");
            } catch (Exception e) {
                ConsolePrinter.error("Failed to create template: " + e.getMessage());
            }
        }
    }

    @Command(name = "list", description = "List all workflow templates")
    static class TemplateList implements Runnable {

        @Override
        public void run() {
            try {
                List<WorkflowTemplate> templates = workflowService.listTemplates();
                if (templates.isEmpty()) {
                    ConsolePrinter.info("No templates found. Create one: workctl flow template new \"My Procedure\"");
                    return;
                }

                ConsolePrinter.header("Workflow Templates");
                String[] headers = {"ID (short)", "Name", "Steps", "Tags", "Created"};
                int[]    widths  = {12, 32, 6, 18, 12};

                List<String[]> rows = templates.stream().map(t -> new String[]{
                    t.getId().substring(0, 8),
                    t.getName(),
                    String.valueOf(t.getSteps().size()),
                    t.getTagsString().isBlank() ? "—" : t.getTagsString(),
                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : "—"
                }).toList();

                ConsolePrinter.table(headers, rows, widths);
            } catch (Exception e) {
                ConsolePrinter.error("Failed to list templates: " + e.getMessage());
            }
        }
    }

    @Command(name = "show", description = "Show a template's steps")
    static class TemplateShow implements Runnable {

        @Parameters(index = "0", description = "Template ID (or partial prefix)")
        private String templateId;

        @Override
        public void run() {
            try {
                WorkflowTemplate template = resolveTemplate(templateId);
                if (template == null) return;

                ConsolePrinter.header(template.getName());
                if (template.getDescription() != null) {
                    ConsolePrinter.plain("  " + template.getDescription());
                }
                ConsolePrinter.plain("  ID: " + template.getId());
                ConsolePrinter.plain("  Steps: " + template.getSteps().size() + "   Tags: " + template.getTagsString());
                ConsolePrinter.separator();

                List<WorkflowTemplate.TemplateStep> steps = template.getSteps();
                if (steps.isEmpty()) {
                    ConsolePrinter.info("No steps yet. Add: workctl flow template step-add " + template.getId().substring(0, 8) + "... \"Step title\"");
                    return;
                }
                for (int i = 0; i < steps.size(); i++) {
                    WorkflowTemplate.TemplateStep s = steps.get(i);
                    ConsolePrinter.plain("  " + (i + 1) + ". " + s.getTitle());
                    if (s.getDescription() != null) {
                        ConsolePrinter.plain("     " + s.getDescription().replace("\n", "\n     "));
                    }
                    if (s.getExpectedResult() != null) {
                        ConsolePrinter.plain("     Expected: " + s.getExpectedResult());
                    }
                    if (!s.getSubStepTitles().isEmpty()) {
                        for (String ss : s.getSubStepTitles()) {
                            ConsolePrinter.plain("       - " + ss);
                        }
                    }
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to show template: " + e.getMessage());
            }
        }
    }

    @Command(name = "step-add", description = "Add a step to an existing template")
    static class TemplateStepAdd implements Runnable {

        @Parameters(index = "0", description = "Template ID (or partial prefix)")
        private String templateId;

        @Parameters(index = "1", description = "Step title")
        private String title;

        @Option(names = {"--desc", "--description"}, description = "Step description / guidance")
        private String description;

        @Option(names = {"--expected"}, description = "Expected result of this step")
        private String expected;

        @Override
        public void run() {
            try {
                WorkflowTemplate template = resolveTemplate(templateId);
                if (template == null) return;
                workflowService.addTemplateStep(template.getId(), title, description, expected);
                int stepNum = template.getSteps().size() + 1;
                ConsolePrinter.success("Added step " + stepNum + ": " + title);
            } catch (Exception e) {
                ConsolePrinter.error("Failed to add step: " + e.getMessage());
            }
        }
    }

    @Command(name = "delete", description = "Delete a workflow template")
    static class TemplateDelete implements Runnable {

        @Parameters(index = "0", description = "Template ID (or partial prefix)")
        private String templateId;

        @Override
        public void run() {
            try {
                WorkflowTemplate template = resolveTemplate(templateId);
                if (template == null) return;
                boolean deleted = workflowService.deleteTemplate(template.getId());
                if (deleted) {
                    ConsolePrinter.success("Template deleted: " + template.getName());
                } else {
                    ConsolePrinter.error("Could not delete template.");
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to delete template: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // RUN — NEW
    // ================================================================

    @Command(name = "new", description = "Create a new workflow run")
    static class NewRun implements Runnable {

        @Parameters(index = "0", description = "Run name")
        private String name;

        @Option(names = {"--template", "-t"}, description = "Template ID to base this run on")
        private String templateId;

        @Option(names = {"--project", "-p"}, description = "Project to scope this run to")
        private String project;

        @Override
        public void run() {
            try {
                String resolvedTemplateId = null;
                if (templateId != null && !templateId.isBlank()) {
                    WorkflowTemplate template = resolveTemplate(templateId);
                    if (template == null) return;
                    resolvedTemplateId = template.getId();
                }

                WorkflowRun run = workflowService.createRun(name, resolvedTemplateId, project);
                ConsolePrinter.success("Run created: " + run.getName());
                ConsolePrinter.info("ID: " + run.getId());
                if (project != null) ConsolePrinter.plain("  Project: " + project);
                ConsolePrinter.plain("  Steps: " + run.getSteps().size());
                ConsolePrinter.plain("  View: workctl flow show " + run.getId().substring(0, 8) + "...");
            } catch (Exception e) {
                ConsolePrinter.error("Failed to create run: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // RUN — LIST
    // ================================================================

    @Command(name = "list", description = "List workflow runs")
    static class ListRuns implements Runnable {

        @Option(names = {"--project", "-p"}, description = "Filter by project (omit for global runs)")
        private String project;

        @Option(names = {"--all", "-a"}, description = "Show all runs (global + all projects)")
        private boolean all;

        @Override
        public void run() {
            try {
                List<WorkflowRun> runs = all
                        ? workflowService.listAllRuns()
                        : workflowService.listRuns(project);

                if (runs.isEmpty()) {
                    ConsolePrinter.info("No runs found.");
                    return;
                }

                ConsolePrinter.header("Workflow Runs");
                String[] headers = {"ID (short)", "Name", "Project", "Status", "Progress", "Created"};
                int[]    widths  = {12, 28, 14, 14, 10, 12};

                List<String[]> rows = runs.stream().map(r -> new String[]{
                    r.getId().substring(0, 8),
                    r.getName(),
                    r.getProjectId() != null ? r.getProjectId() : "global",
                    runStatusBadge(r.getStatus()),
                    r.getDoneStepCount() + "/" + r.getSteps().size(),
                    r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—"
                }).toList();

                ConsolePrinter.table(headers, rows, widths);
            } catch (Exception e) {
                ConsolePrinter.error("Failed to list runs: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // RUN — SHOW
    // ================================================================

    @Command(name = "show", description = "Show a workflow run with all steps")
    static class ShowRun implements Runnable {

        @Parameters(index = "0", description = "Run ID (or partial prefix)")
        private String runId;

        @Override
        public void run() {
            try {
                WorkflowRun run = resolveRun(runId);
                if (run == null) return;

                ConsolePrinter.header(run.getName());
                ConsolePrinter.plain("  Status:  " + runStatusBadge(run.getStatus())
                        + "   Project: " + (run.getProjectId() != null ? run.getProjectId() : "global"));
                int done  = run.getDoneStepCount();
                int total = run.getActiveStepCount();
                ConsolePrinter.plain("  Progress: " + ConsolePrinter.progressBar(done, total, 10));
                ConsolePrinter.separator();

                List<WorkflowRun.RunStep> steps = run.getSteps();
                if (steps.isEmpty()) {
                    ConsolePrinter.info("No steps in this run.");
                    return;
                }

                for (int i = 0; i < steps.size(); i++) {
                    WorkflowRun.RunStep s = steps.get(i);
                    String symbol = s.statusSymbol();
                    String stepLine = "  " + symbol + " " + (i + 1) + ". " + s.getTitle();
                    if (s.getStatus() == StepStatus.SKIPPED) {
                        stepLine += "  (SKIPPED)";
                    }
                    ConsolePrinter.plain(stepLine);

                    if (s.getNotes() != null && !s.getNotes().isBlank()) {
                        ConsolePrinter.plain("     " + s.getNotes().replace("\n", "\n     "));
                    }
                    if (s.getExpectedResult() != null) {
                        ConsolePrinter.plain("     Expected: " + s.getExpectedResult());
                    }
                    if (s.getActualResult() != null) {
                        ConsolePrinter.plain("     Actual:   " + s.getActualResult());
                    }
                    if (!s.getSubSteps().isEmpty()) {
                        for (WorkflowRun.SubStep ss : s.getSubSteps()) {
                            ConsolePrinter.plain("       " + (ss.isDone() ? "[x]" : "[ ]") + " " + ss.getTitle());
                        }
                    }
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to show run: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // RUN — DELETE
    // ================================================================

    @Command(name = "delete", description = "Delete a workflow run")
    static class DeleteRun implements Runnable {

        @Parameters(index = "0", description = "Run ID (or partial prefix)")
        private String runId;

        @Override
        public void run() {
            try {
                WorkflowRun run = resolveRun(runId);
                if (run == null) return;
                boolean deleted = workflowService.deleteRun(run.getId());
                if (deleted) {
                    ConsolePrinter.success("Run deleted: " + run.getName());
                } else {
                    ConsolePrinter.error("Could not delete run.");
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to delete run: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // STEP GROUP
    // ================================================================

    @Command(
        name = "step",
        description = "Update a step in a workflow run",
        subcommands = {
            FlowCommand.StepDone.class,
            FlowCommand.StepSkip.class,
            FlowCommand.StepNote.class
        }
    )
    static class StepGroup implements Runnable {
        @Override
        public void run() {
            ConsolePrinter.info("Usage: workctl flow step <done|skip|note> <run-id> <step-num>");
            ConsolePrinter.plain("Run 'workctl flow step --help' for details.");
        }
    }

    @Command(name = "done", description = "Mark a step as done")
    static class StepDone implements Runnable {

        @Parameters(index = "0", description = "Run ID (or partial prefix)")
        private String runId;

        @Parameters(index = "1", description = "Step number (1-based)")
        private int stepNum;

        @Override
        public void run() {
            try {
                WorkflowRun run = resolveRun(runId);
                if (run == null) return;
                boolean ok = workflowService.updateStepStatus(run.getId(), stepNum - 1, StepStatus.DONE);
                if (ok) {
                    WorkflowRun updated = workflowService.loadRun(run.getId()).orElse(run);
                    ConsolePrinter.success("Step " + stepNum + " marked DONE");
                    int done = updated.getDoneStepCount();
                    int total = updated.getActiveStepCount();
                    ConsolePrinter.plain("  Progress: " + ConsolePrinter.progressBar(done, total, 10));
                    if (updated.getStatus() == RunStatus.COMPLETED) {
                        ConsolePrinter.success("Run completed! All steps done.");
                    }
                } else {
                    ConsolePrinter.error("Step " + stepNum + " not found in run.");
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to update step: " + e.getMessage());
            }
        }
    }

    @Command(name = "skip", description = "Mark a step as skipped")
    static class StepSkip implements Runnable {

        @Parameters(index = "0", description = "Run ID (or partial prefix)")
        private String runId;

        @Parameters(index = "1", description = "Step number (1-based)")
        private int stepNum;

        @Override
        public void run() {
            try {
                WorkflowRun run = resolveRun(runId);
                if (run == null) return;
                boolean ok = workflowService.updateStepStatus(run.getId(), stepNum - 1, StepStatus.SKIPPED);
                if (ok) {
                    ConsolePrinter.success("Step " + stepNum + " marked SKIPPED");
                } else {
                    ConsolePrinter.error("Step " + stepNum + " not found in run.");
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to skip step: " + e.getMessage());
            }
        }
    }

    @Command(name = "note", description = "Add a note or observation to a step")
    static class StepNote implements Runnable {

        @Parameters(index = "0", description = "Run ID (or partial prefix)")
        private String runId;

        @Parameters(index = "1", description = "Step number (1-based)")
        private int stepNum;

        @Option(names = {"--message", "-m"}, description = "Note text")
        private String message;

        @Option(names = {"--edit"}, description = "Open editor to write the note")
        private boolean edit;

        @Override
        public void run() {
            try {
                WorkflowRun run = resolveRun(runId);
                if (run == null) return;

                String note;
                if (edit) {
                    note = EditorUtil.openEditorAndCapture();
                } else if (message != null && !message.isBlank()) {
                    note = message;
                } else {
                    ConsolePrinter.error("Provide --message \"text\" or --edit to open an editor.");
                    return;
                }

                boolean ok = workflowService.addStepNote(run.getId(), stepNum - 1, note);
                if (ok) {
                    ConsolePrinter.success("Note added to step " + stepNum);
                } else {
                    ConsolePrinter.error("Step " + stepNum + " not found in run.");
                }
            } catch (Exception e) {
                ConsolePrinter.error("Failed to add note: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // HELPERS — ID RESOLUTION (supports full UUID or 8-char prefix)
    // ================================================================

    private static WorkflowTemplate resolveTemplate(String idOrPrefix) {
        List<WorkflowTemplate> templates = workflowService.listTemplates();

        // Exact match first
        WorkflowTemplate exact = templates.stream()
                .filter(t -> t.getId().equals(idOrPrefix))
                .findFirst().orElse(null);
        if (exact != null) return exact;

        // Prefix match
        List<WorkflowTemplate> matches = templates.stream()
                .filter(t -> t.getId().startsWith(idOrPrefix))
                .toList();
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) {
            ConsolePrinter.error("Ambiguous prefix '" + idOrPrefix + "' — matches " + matches.size() + " templates.");
            return null;
        }
        ConsolePrinter.error("Template not found: " + idOrPrefix);
        return null;
    }

    private static WorkflowRun resolveRun(String idOrPrefix) {
        List<WorkflowRun> runs = workflowService.listAllRuns();

        WorkflowRun exact = runs.stream()
                .filter(r -> r.getId().equals(idOrPrefix))
                .findFirst().orElse(null);
        if (exact != null) return exact;

        List<WorkflowRun> matches = runs.stream()
                .filter(r -> r.getId().startsWith(idOrPrefix))
                .toList();
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) {
            ConsolePrinter.error("Ambiguous prefix '" + idOrPrefix + "' — matches " + matches.size() + " runs.");
            return null;
        }
        ConsolePrinter.error("Run not found: " + idOrPrefix);
        return null;
    }

    private static String runStatusBadge(RunStatus s) {
        if (s == null) return "IN PROGRESS";
        return switch (s) {
            case IN_PROGRESS -> "\u001B[36mIN PROGRESS\u001B[0m";
            case COMPLETED   -> "\u001B[32mCOMPLETED\u001B[0m";
            case ABANDONED   -> "\u001B[2mABANDONED\u001B[0m";
        };
    }
}
