package com.workctl.cli;

import com.workctl.cli.commands.*;
import com.workctl.cli.util.ConsolePrinter;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@CommandLine.Command(name = "workctl", mixinStandardHelpOptions = true,
        versionProvider = WorkctlCLI.ManifestVersionProvider.class, subcommands = {
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

    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Enumeration<URL> resources = WorkctlCLI.class.getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                Attributes attrs = manifest.getMainAttributes();
                String title   = attrs.getValue("Implementation-Title");
                String version = attrs.getValue("Implementation-Version");
                if ("workctl CLI".equals(title) && version != null) {
                    return new String[]{"workctl " + version};
                }
            }
            return new String[]{"workctl (version unknown)"};
        }
    }
}
