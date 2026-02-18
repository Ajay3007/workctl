package com.workctl.cli.commands;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
        name = "config",
        description = "Manage workctl configuration",
        subcommands = {
                ConfigCommand.Set.class,
                ConfigCommand.Get.class,
                ConfigCommand.Show.class
        }
)
public class ConfigCommand {

    // ======================
    // SET
    // ======================

    @Command(name = "set", description = "Set configuration key")
    static class Set implements Runnable {

        @Parameters(index = "0", description = "Config key")
        private String key;

        @Parameters(index = "1", description = "Config value")
        private String value;

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();

                switch (key.toLowerCase()) {
                    case "editor" -> config.setEditor(value);
                    case "workspace" -> config.setWorkspace(value);
                    case "dateformat" -> config.setDateFormat(value);
                    default -> {
                        System.out.println("Unknown config key: " + key);
                        return;
                    }
                }

                ConfigManager.save(config);

                System.out.println("Config updated: " + key + " = " + value);

            } catch (Exception e) {
                System.out.println("Failed to update config");
                e.printStackTrace();
            }
        }
    }

    // ======================
    // GET
    // ======================

    @Command(name = "get", description = "Get configuration value")
    static class Get implements Runnable {

        @Parameters(index = "0", description = "Config key")
        private String key;

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();

                String value = switch (key.toLowerCase()) {
                    case "editor" -> config.getEditor();
                    case "workspace" -> config.getWorkspace();
                    case "dateformat" -> config.getDateFormat();
                    default -> {
                        System.out.println("Unknown config key: " + key);
                        yield null;
                    }
                };

                if (value != null) {
                    System.out.println(key + " = " + value);
                }

            } catch (Exception e) {
                System.out.println("Failed to read config");
                e.printStackTrace();
            }
        }
    }

    // ======================
    // SHOW
    // ======================

    @Command(name = "show", description = "Show all configuration")
    static class Show implements Runnable {

        @Override
        public void run() {
            try {
                AppConfig config = ConfigManager.load();

                System.out.println("Current workctl configuration:");
                System.out.println("editor     = " + config.getEditor());
                System.out.println("workspace  = " + config.getWorkspace());
                System.out.println("dateFormat = " + config.getDateFormat());

            } catch (Exception e) {
                System.out.println("Failed to load config");
                e.printStackTrace();
            }
        }
    }
}
