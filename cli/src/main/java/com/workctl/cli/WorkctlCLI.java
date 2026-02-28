package com.workctl.cli;

import com.workctl.cli.commands.*;
import com.workctl.cli.util.ConsolePrinter;
import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(name = "workctl", mixinStandardHelpOptions = true, version = "workctl 0.1.0", subcommands = {
        InitCommand.class,
        ProjectCommand.class,
        LogCommand.class,
        WeeklyCommand.class,
        SearchCommand.class,
        TaskCommand.class,
        ConfigCommand.class,
        StatsCommand.class,
        InsightCommand.class,
        AskCommand.class,
        CommandCmd.class,
        FlowCommand.class,
        AutoComplete.GenerateCompletion.class
})
public class WorkctlCLI implements Runnable {

    @Override
    public void run() {
        ConsolePrinter.banner();
        ConsolePrinter.info("Use 'workctl --help' to see available commands.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new WorkctlCLI()).execute(args);
        System.exit(exitCode);
    }
}
