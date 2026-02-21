package com.workctl.cli.util;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * JLine 3-backed interactive prompt utilities.
 *
 * All methods use {@code dumb(true)} terminal mode so they degrade gracefully
 * in piped / CI environments rather than throwing.
 */
public class CliPrompt {

    private static Terminal buildTerminal() throws IOException {
        return TerminalBuilder.builder().dumb(true).build();
    }

    /**
     * Styled {@code [y/N]} confirmation prompt.
     *
     * @return {@code true} if the user typed "y" or "yes" (case-insensitive)
     */
    public static boolean confirm(String question) {
        try {
            Terminal  terminal = buildTerminal();
            LineReader reader  = LineReaderBuilder.builder().terminal(terminal).build();
            String    prompt   = "\u001B[33m⚠ \u001B[0m" + question + " \u001B[2m[y/N]\u001B[0m ";
            String    line     = reader.readLine(prompt);
            terminal.close();
            return line != null &&
                   (line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes"));
        } catch (IOException | EndOfFileException | UserInterruptException e) {
            return false;
        }
    }

    /**
     * Single-line styled input prompt.
     *
     * @return the trimmed input, or {@code ""} on EOF/interrupt
     */
    public static String prompt(String label) {
        try {
            Terminal  terminal = buildTerminal();
            LineReader reader  = LineReaderBuilder.builder().terminal(terminal).build();
            String    promptStr = "\u001B[34mℹ \u001B[0m" + label + ": ";
            String    line      = reader.readLine(promptStr);
            terminal.close();
            return line != null ? line.trim() : "";
        } catch (IOException | EndOfFileException | UserInterruptException e) {
            return "";
        }
    }

    /**
     * Multi-line input with a {@code > } prefix per line.
     * Finishes when the user types {@code END} on its own line or sends EOF (Ctrl+D).
     *
     * @return the accumulated text (trimmed), or {@code ""} on interrupt
     */
    public static String promptMultiline(String label) {
        try {
            Terminal  terminal = buildTerminal();
            LineReader reader  = LineReaderBuilder.builder().terminal(terminal).build();
            System.out.println("\u001B[34mℹ \u001B[0m" + label
                    + " \u001B[2m(type END on a new line to finish)\u001B[0m");
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line;
                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException e) {
                    break;
                } catch (EndOfFileException e) {
                    break;
                }
                if (line == null || "END".equalsIgnoreCase(line.trim())) break;
                sb.append(line).append("\n");
            }
            terminal.close();
            return sb.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Numbered selection list.
     *
     * @param defaultIndex 0-based index of the pre-selected option
     * @return 0-based index of the chosen option, or {@code defaultIndex} on failure
     */
    public static int select(String label, String[] options, int defaultIndex) {
        try {
            Terminal  terminal = buildTerminal();
            LineReader reader  = LineReaderBuilder.builder().terminal(terminal).build();
            System.out.println("\u001B[34mℹ \u001B[0m" + label + ":");
            for (int i = 0; i < options.length; i++) {
                String marker = (i == defaultIndex) ? " \u001B[32m(default)\u001B[0m" : "";
                System.out.println("  " + (i + 1) + ". " + options[i] + marker);
            }
            String line = reader.readLine("Select [" + (defaultIndex + 1) + "]: ");
            terminal.close();
            if (line == null || line.trim().isEmpty()) return defaultIndex;
            try {
                int choice = Integer.parseInt(line.trim()) - 1;
                if (choice >= 0 && choice < options.length) return choice;
            } catch (NumberFormatException ignored) {}
            return defaultIndex;
        } catch (IOException | EndOfFileException | UserInterruptException e) {
            return defaultIndex;
        }
    }
}
