package com.workctl.cli.util;

/**
 * Utility class for printing colored console messages.
 */
public class ConsolePrinter {
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";

    public static void success(String message) {
        System.out.println(GREEN + "✓ " + message + RESET);
    }

    public static void info(String message) {
        System.out.println(BLUE + "ℹ " + message + RESET);
    }

    public static void warning(String message) {
        System.out.println(YELLOW + "⚠ " + message + RESET);
    }

    public static void error(String message) {
        System.out.println(RED + "✗ " + message + RESET);
    }

    public static void plain(String message) {
        System.out.println(message);
    }
}
