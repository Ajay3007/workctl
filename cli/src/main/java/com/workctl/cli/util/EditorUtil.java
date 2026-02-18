package com.workctl.cli.util;

import com.workctl.config.AppConfig;
import com.workctl.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class EditorUtil {

    public static String openEditorAndCapture() throws IOException, InterruptedException {

        AppConfig config = ConfigManager.load();

        String editor = config.getEditor();
        if (editor == null || editor.isBlank()) {
            editor = "code"; // default fallback
        }

        Path tempFile = Files.createTempFile("workctl-log-", ".md");

        Files.writeString(tempFile,
                "# Write your log below\n" +
                        "# Save and close editor when done\n\n");

        ProcessBuilder pb;

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase()
                .contains("win");

        boolean needsWaitFlag = editor.toLowerCase().contains("code");

        if (isWindows) {

            if (needsWaitFlag) {
                pb = new ProcessBuilder(
                        "cmd", "/c",
                        editor,
                        "--wait",
                        tempFile.toString()
                );
            } else {
                pb = new ProcessBuilder(
                        "cmd", "/c",
                        editor,
                        tempFile.toString()
                );
            }

        } else {

            if (needsWaitFlag) {
                pb = new ProcessBuilder(
                        editor,
                        "--wait",
                        tempFile.toString()
                );
            } else {
                pb = new ProcessBuilder(
                        editor,
                        tempFile.toString()
                );
            }
        }

        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Editor exited with code: " + exitCode);
        }

        String content = Files.readString(tempFile);

        Files.deleteIfExists(tempFile);

        return content.lines()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.joining("\n"))
                .trim();
    }

}
