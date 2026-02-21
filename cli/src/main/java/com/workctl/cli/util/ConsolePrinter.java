package com.workctl.cli.util;

import com.workctl.core.model.TaskStatus;

import java.util.List;

/**
 * Utility class for printing colored and styled console messages.
 */
public class ConsolePrinter {
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    private static final String RED    = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    static final String CYAN   = "\u001B[36m";
    static final String BOLD   = "\u001B[1m";
    static final String DIM    = "\u001B[2m";

    // ── Basic message methods ─────────────────────────────────────

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

    // ── Layout helpers ────────────────────────────────────────────

    /**
     * Box-drawing header: ┌─ Title ──────...──┐  (total visible width = 44)
     */
    public static void header(String title) {
        int total  = 44;
        // ┌─ (2) + space (1) + title + space (1) + dashes + ┐ (1) = total
        int dashes = total - 5 - title.length();
        if (dashes < 0) dashes = 0;
        System.out.println(
            DIM + "┌─ " + RESET + BOLD + title + RESET + " " +
            DIM + "─".repeat(dashes) + "┐" + RESET
        );
    }

    /** Horizontal separator line (DIM, 44 chars). */
    public static void separator() {
        System.out.println(DIM + "─".repeat(44) + RESET);
    }

    /**
     * Clipboard-style startup banner, similar in structure to Claude Code's logo.
     *
     * Layout (34 chars wide, inner = 30 chars):
     * <pre>
     *                ┌────┐
     *                │░░░░│
     *   ╔════════════╩════╩════════════╗
     *   ║  □ ───────────────────────   ║   workctl  v0.1.0
     *   ║  □ ─────────────────         ║   tasks · logs · AI
     *   ║  □ ─────────────────────     ║   ~/current/dir
     *   ║  □ ─────── ─── ──────────    ║
     *   ║  ✓ ────────────────────── ⚙  ║
     *   ╚══════════════════════════════╝
     * </pre>
     *
     * The clip (┌────┐) sits above the ╩ notches so it looks pinned to the top
     * of the clipboard, exactly like a real physical clipboard clip.
     * The ⚙ gear sits inside the done row near the bottom-right corner.
     * Text (name, tagline, cwd) is right-flush to the clipboard at col 36.
     *
     * Column accounting (0-indexed):
     *   top border  : 2 + ╔ + 12═ + ╩ + 4═ + ╩ + 12═ + ╗  = 34
     *   clip        : 15 spaces + ┌────┐ (┌ at col 15, ┐ at col 20)
     *   content row : 2 + ║ + [30 inner chars] + ║         = 34
     *   inner row   : 2 spaces + symbol + space + dashes + padding = 30
     */
    public static void banner() {
        String cwd = System.getProperty("user.dir")
                         .replace(System.getProperty("user.home"), "~");

        // ── clip: lighter border floating above the clipboard top ────────
        //    ┌ aligns with left ╩ (col 15), ┐ aligns with right ╩ (col 20)
        System.out.println();
        System.out.println("               " + DIM + CYAN + "┌────┐" + RESET);
        System.out.println("               " + DIM + CYAN + "│░░░░│" + RESET);

        // ── clipboard top border — ╩ slots receive the clip's feet ──────
        System.out.println("  " + BOLD + CYAN + "╔════════════╩════╩════════════╗" + RESET);

        // ── checkbox rows — inner = exactly 30 visible chars each ────────
        //    format:  "  " + symbol(1) + " " + dashes(N) + pad(26-N) = 30
        //    row 1  : N=23, pad=3
        clipRow("□", "───────────────────────", "   ");
        //    row 2  : N=17, pad=9
        clipRow("□", "─────────────────",       "         ");
        //    row 3  : N=21, pad=5
        clipRow("□", "─────────────────────",   "     ");
        //    row 4  : N=22 (includes spaces), pad=4
        clipRow("□", "─────── ─── ──────────",  "    ");

        // ── done row: ✓ + gear ⚙ near right edge (inner = 30) ───────────
        //    "  ✓ " (4) + 22 dashes (22) + " ⚙  " (4) = 30
        System.out.println(
            "  " + BOLD + CYAN + "║" + RESET
            + "  " + GREEN + BOLD + "✓" + RESET
            + " " + DIM + "──────────────────────" + RESET
            + " " + BOLD + CYAN + "⚙" + RESET
            + "  " + BOLD + CYAN + "║" + RESET);

        // ── bottom border ────────────────────────────────────────────────
        System.out.println("  " + BOLD + CYAN + "╚══════════════════════════════╝" + RESET);

        // ── labels below the art (aligned under right edge of clipboard) ─
        System.out.println("          " + BOLD + CYAN  + "workctl" + RESET
                         + "  "         + DIM          + "v0.1.0"  + RESET);
        System.out.println("          " + DIM  + "tasks · logs · AI" + RESET);
        System.out.println("          " + DIM  + cwd   + RESET);
        System.out.println();
    }

    /** Prints one clipboard content row: ║  symbol dashes pad ║ */
    private static void clipRow(String symbol, String dashes, String pad) {
        System.out.println(
            "  " + BOLD + CYAN + "║" + RESET
            + "  " + DIM + symbol + " " + dashes + RESET
            + pad
            + BOLD + CYAN + "║" + RESET);
    }

    // ── Badge helpers ─────────────────────────────────────────────

    /** Colored priority badge: P1=RED+BOLD, P2=YELLOW, P3=DIM. */
    public static String priorityBadge(int p) {
        return switch (p) {
            case 1  -> RED + BOLD + "[P1]" + RESET;
            case 3  -> DIM + "[P3]" + RESET;
            default -> YELLOW + "[P2]" + RESET;
        };
    }

    /** Colored status badge. */
    public static String statusBadge(TaskStatus s) {
        return switch (s) {
            case OPEN        -> BLUE            + "OPEN"        + RESET;
            case IN_PROGRESS -> CYAN            + "IN PROGRESS" + RESET;
            case DONE        -> GREEN + DIM     + "DONE"        + RESET;
        };
    }

    // ── Progress bar ──────────────────────────────────────────────

    /**
     * ASCII progress bar, e.g. {@code [████████░░░░] 4/10}.
     *
     * @param done  number of completed items
     * @param total total number of items
     * @param width number of bar characters
     */
    public static String progressBar(int done, int total, int width) {
        if (total == 0) {
            return DIM + "[" + "░".repeat(width) + "] 0/0" + RESET;
        }
        int filled = (int) Math.round((double) done / total * width);
        int empty  = width - filled;
        return GREEN + "[" + "█".repeat(filled) + RESET
             + DIM   + "░".repeat(empty)         + RESET
             + GREEN + "]"                        + RESET
             + " " + done + "/" + total;
    }

    // ── Table ─────────────────────────────────────────────────────

    /**
     * Print an aligned column table.
     *
     * @param headers column header labels
     * @param rows    data rows (each row is a String array; ANSI codes are allowed)
     * @param widths  visual width for each column
     */
    public static void table(String[] headers, List<String[]> rows, int[] widths) {
        // Header row
        StringBuilder hdr = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            hdr.append(BOLD).append(padRight(headers[i], widths[i])).append(RESET);
            if (i < headers.length - 1) hdr.append("  ");
        }
        System.out.println(hdr);

        // Separator
        int totalW = 0;
        for (int w : widths) totalW += w;
        totalW += (widths.length - 1) * 2;
        System.out.println(DIM + "─".repeat(totalW) + RESET);

        // Data rows
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < widths.length; i++) {
                String cell = (i < row.length && row[i] != null) ? row[i] : "";
                line.append(padRight(cell, widths[i]));
                if (i < widths.length - 1) line.append("  ");
            }
            System.out.println(line);
        }
    }

    // ── String utilities ──────────────────────────────────────────

    /**
     * ANSI-aware pad-right: strips escape codes before measuring visual length,
     * then pads the original string (which may contain codes) with spaces.
     */
    public static String padRight(String s, int width) {
        if (s == null) s = "";
        String stripped = s.replaceAll("\u001B\\[[;\\d]*m", "");
        int visual = stripped.length();
        if (visual >= width) return s;
        return s + " ".repeat(width - visual);
    }
}
