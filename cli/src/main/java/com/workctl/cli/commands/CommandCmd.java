package com.workctl.cli.commands;

import com.workctl.core.domain.CommandEntry;
import com.workctl.core.service.CommandService;
import com.workctl.cli.util.ConsolePrinter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "cmd", description = "Manage reusable CLI commands")
public class CommandCmd implements Runnable {

    @Command(name = "add", description = "Add a new command to a category")
    public void addCommand(
            @Parameters(index = "0", description = "Category (e.g., docker, git)") String category,
            @Parameters(index = "1", description = "The exact CLI command") String commandStr,
            @Option(names = { "-t",
                    "--title" }, required = true, description = "Short description of what the command does") String title,
            @Option(names = { "-n", "--notes" }, description = "Optional extended notes") String notes,
            @Option(names = { "-p",
                    "--project" }, defaultValue = "GLOBAL", description = "Project this applies to (default: GLOBAL)") String project) {

        try {
            CommandService service = new CommandService();
            CommandEntry entry = new CommandEntry(category.toLowerCase(), title, commandStr, notes, project);
            service.saveCommand(entry);
            ConsolePrinter.success("Added command '" + title + "' to category '" + category + "'.");
        } catch (Exception e) {
            ConsolePrinter.error("Failed to add command: " + e.getMessage());
        }
    }

    @Command(name = "list", description = "List saved commands")
    public void listCommands(
            @Parameters(index = "0", arity = "0..1", description = "Optional category filter") String category,
            @Option(names = { "-p", "--project" }, description = "Optional project filter") String project) {

        try {
            CommandService service = new CommandService();
            List<CommandEntry> commands;

            if (category != null && !category.isEmpty()) {
                commands = service.loadCategory(category.toLowerCase());
            } else {
                commands = service.loadAllCommands();
            }

            if (project != null && !project.isEmpty()) {
                commands.removeIf(
                        c -> !c.getProjectTag().equalsIgnoreCase(project) && !c.getProjectTag().equals("GLOBAL"));
            }

            if (commands.isEmpty()) {
                ConsolePrinter.info("No commands found.");
                return;
            }

            for (CommandEntry cmd : commands) {
                System.out.println();
                ConsolePrinter.info(cmd.getTitle() + " [" + cmd.getCategory() + "] (" + cmd.getProjectTag() + ")");
                System.out.println("  > " + cmd.getCommand());
                if (cmd.getNotes() != null && !cmd.getNotes().isEmpty()) {
                    System.out.println("    " + cmd.getNotes().replace("\n", "\n    "));
                }
            }
            System.out.println();

        } catch (Exception e) {
            ConsolePrinter.error("Failed to list commands: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        ConsolePrinter.warning("Please specify a subcommand: 'add' or 'list'. Use 'workctl cmd --help' for info.");
    }
}
