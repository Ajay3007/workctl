package com.workctl.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-wide theme manager.
 *
 * Two themes: DARK (default) and LIGHT.
 * CSS-based components (sidebar, tab pane, kanban board) update automatically
 * when the scene stylesheet is switched.
 * HTML-rendered components (Stats, Logs, WeeklyReport) register listeners and
 * regenerate their HTML on change.
 */
public class ThemeManager {

    public enum Theme { DARK, LIGHT }

    private static volatile Theme current = Theme.DARK;
    private static final List<Runnable> listeners = new ArrayList<>();

    // ── Theme state ───────────────────────────────────────────────

    public static Theme get()        { return current; }
    public static boolean isDark()   { return current == Theme.DARK; }

    public static synchronized void toggle() {
        current = (current == Theme.DARK) ? Theme.LIGHT : Theme.DARK;
        new ArrayList<>(listeners).forEach(l -> {
            try { l.run(); } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static synchronized void addListener(Runnable l) {
        listeners.add(l);
    }

    // ── CSS resource path (for scene stylesheet) ──────────────────

    public static String cssPath() {
        return isDark()
                ? "/com/workctl/gui/css/dark.css"
                : "/com/workctl/gui/css/light.css";
    }

    public static String toggleIcon() { return isDark() ? "☀" : "🌙"; }

    // ── Semantic colors (for programmatic HTML content) ───────────

    public static String htmlBg()       { return isDark() ? "#0d0f17"  : "#f8fafc"; }
    public static String htmlSurface()  { return isDark() ? "#1a1d2e"  : "#ffffff"; }
    public static String htmlBorder()   { return isDark() ? "#2d3748"  : "#e2e8f0"; }
    public static String htmlText()     { return isDark() ? "#e2e8f0"  : "#1e293b"; }
    public static String htmlMuted()    { return isDark() ? "#a0aec0"  : "#64748b"; }
    public static String htmlDim()      { return isDark() ? "#718096"  : "#94a3b8"; }
    public static String htmlHeading()  { return isDark() ? "#f7fafc"  : "#0f172a"; }
    public static String htmlCode()     { return isDark() ? "#1a2535"  : "#f1f5f9"; }
    public static String htmlCodeText() { return isDark() ? "#63b3ed"  : "#1d4ed8"; }
    public static String htmlLink()     { return isDark() ? "#63b3ed"  : "#2563eb"; }
    public static String htmlQuote()    { return isDark() ? "rgba(37,99,235,0.12)" : "rgba(37,99,235,0.07)"; }
    public static String htmlQuoteText(){ return isDark() ? "#93c5fd"  : "#1e40af"; }

    // ── Semantic colors (for JavaFX inline styles) ────────────────

    public static String cardBg()       { return isDark() ? "#1a1d2e"  : "#ffffff"; }
    public static String cardBgHover()  { return isDark() ? "#212540"  : "#f8fafc"; }
    public static String textPrimary()  { return isDark() ? "#e2e8f0"  : "#1e293b"; }
    public static String textMuted()    { return isDark() ? "#718096"  : "#64748b"; }
    public static String infoBtnBg()    { return isDark() ? "#2d3748"  : "#f1f5f9"; }
    public static String infoBtnText()  { return isDark() ? "#63b3ed"  : "#2563eb"; }
    public static String infoBtnBorder(){ return isDark() ? "#4a5568"  : "#e2e8f0"; }
}
