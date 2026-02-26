package com.workctl.cli;

import com.workctl.core.domain.CommandEntry;
import com.workctl.core.service.CommandService;

public class BulkImport {
    public static void main(String[] args) throws Exception {
        CommandService service = new CommandService();
        add(service, "workctl-core", "Initialize Workspace", "workctl init --workspace <path>",
                "Creates config.yaml and the project folder structure");

        add(service, "workctl-project", "Create Project", "workctl project create <name>",
                "Creates a new project directory with standard Markdown files");
        add(service, "workctl-project", "List Projects", "workctl project list",
                "Lists all available projects in the workspace");
        add(service, "workctl-project", "Delete Project", "workctl project delete <name>",
                "Permanently deletes a project and all its data");
        add(service, "workctl-project", "Create Meeting", "workctl meeting <project> \"<title>\"",
                "Creates a timestamped meeting markdown file");

        add(service, "workctl-task", "Add Task", "workctl task add <project> \"<title>\"",
                "Creates a new task in the Open status");
        add(service, "workctl-task", "List Tasks", "workctl task list <project>",
                "Lists all tasks grouped by status for a project");
        add(service, "workctl-task", "Show Task Details", "workctl task show <project> <id>",
                "Shows a task's full description and subtasks");
        add(service, "workctl-task", "Start Task", "workctl task start <project> <id>", "Moves a task to In Progress");
        add(service, "workctl-task", "Complete Task", "workctl task done <project> <id>", "Marks a task as Done");
        add(service, "workctl-task", "Delete Task", "workctl task delete <project> -id <id>",
                "Permanently deletes a task by ID");

        add(service, "workctl-subtask", "Add Subtask", "workctl task subtask add <project> <task-id> \"<title>\"",
                "Adds a subtask to an existing task");
        add(service, "workctl-subtask", "List Subtasks", "workctl task subtask list <project> <task-id>",
                "Lists subtasks and their 0-based indexes");
        add(service, "workctl-subtask", "Toggle Subtask Done", "workctl task subtask done <project> <task-id> <idx>",
                "Toggles completion state of a subtask");
        add(service, "workctl-subtask", "Delete Subtask", "workctl task subtask delete <project> <task-id> <idx>",
                "Deletes a subtask by its index");

        add(service, "workctl-log", "Log Work", "workctl log <project> -m \"<message>\"",
                "Adds a quick log entry to work-log.md");
        add(service, "workctl-log", "Search Logs", "workctl search \"<keyword>\"",
                "Searches across all project work-logs");
        add(service, "workctl-log", "Weekly Summary", "workctl weekly <project>",
                "Generates a weekly text summary from logs");

        add(service, "workctl-insight", "Computed Stats", "workctl stats <project>",
                "Computes task completion rates and activity metrics");
        add(service, "workctl-insight", "Project Health Insights", "workctl insight <project>",
                "Generates a human-readable health report");

        add(service, "workctl-ai", "Ask AI Question", "workctl ask <project> \"<query>\"",
                "Asks Claude a read-only question about the project");
        add(service, "workctl-ai", "Instruct AI to Act", "workctl ask <project> --act \"<instruction>\"",
                "Allows AI to write tasks and change statuses");
        add(service, "workctl-ai", "AI Weekly Summary", "workctl ask <project> --weekly",
                "Uses AI to generate a narrative weekly summary");
        add(service, "workctl-ai", "AI Insights", "workctl ask <project> --insight",
                "Uses AI to analyze and interpret project stats");

        add(service, "workctl-config", "Show Config", "workctl config show", "Lists all currently set config values");
        add(service, "workctl-config", "Set Config Value", "workctl config set <key> <value>",
                "Updates a value in config.yaml");

        add(service, "workctl-cmd", "Add Tracked CLI Command",
                "workctl cmd add <category> \"<command>\" -t \"<title>\"", "Saves a useful command to reference later");
        add(service, "workctl-cmd", "List Tracked Commands", "workctl cmd list", "Lists all saved snippet commands");

        System.out.println("Done inserting commands.");
    }

    private static void add(CommandService service, String category, String title, String cmdRaw, String notes) {
        try {
            CommandEntry entry = new CommandEntry(category, title, cmdRaw, notes, "work-control");
            service.saveCommand(entry);
            System.out.println("Added: " + title);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
