package com.workctl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {

    private static final Path CONFIG_PATH =
            Paths.get(System.getProperty("user.home"), ".workctl", "config.yaml");

    public static AppConfig load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalStateException(
                        "workctl is not initialized.\n" +
                                "Run: workctl init --workspace <path>"
                );
            }
            return ConfigLoader.load(CONFIG_PATH);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to read config at: " + CONFIG_PATH +
                            "\nCheck YAML syntax and permissions.",
                    e
            );
        }
    }

    public static void save(AppConfig config) throws IOException {

        Path configPath = getConfigPath();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writeValue(configPath.toFile(), config);
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }
}
