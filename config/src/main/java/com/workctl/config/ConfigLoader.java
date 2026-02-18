package com.workctl.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    public static AppConfig load(Path configPath) {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);

            // 🔑 EXPLICIT constructor required in SnakeYAML 2.x
            Constructor constructor = new Constructor(AppConfig.class, options);
            Yaml yaml = new Yaml(constructor);

            try (InputStream in = Files.newInputStream(configPath)) {
                return yaml.load(in);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to read config at: " + configPath +
                            "\nCause: " + e.getMessage(),
                    e
            );
        }
    }
}
