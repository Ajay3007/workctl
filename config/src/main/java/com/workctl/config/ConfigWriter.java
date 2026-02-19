package com.workctl.config;

import org.yaml.snakeyaml.Yaml;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigWriter {

    public static void write(Path configPath, AppConfig config) throws Exception {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workspace", config.getWorkspace());
        data.put("dateFormat", config.getDateFormat());
        data.put("editor", config.getEditor());
        data.put("anthropicApiKey", config.getAnthropicApiKey());

        Yaml yaml = new Yaml();

        try (Writer writer = Files.newBufferedWriter(configPath)) {
            yaml.dump(data, writer);
        }
    }
}
