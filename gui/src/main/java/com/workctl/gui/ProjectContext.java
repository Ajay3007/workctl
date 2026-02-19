package com.workctl.gui;

import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * ProjectContext — shared event bus for the GUI.
 *
 * Two event types:
 *   1. Project switch  → listeners fire when user selects a different project
 *   2. File change     → fileChangeListeners fire when tasks.md or work-log.md
 *                        changes on disk (triggered by CLI commands, AI agent, etc.)
 *
 * The WatchService runs on a daemon thread and debounces rapid file-system
 * events (e.g. an editor saving multiple times) with a 500ms delay before
 * notifying listeners on the JavaFX thread via Platform.runLater().
 */
public class ProjectContext {

    // ── Project-switch listeners ──────────────────────────────────
    private static String currentProject;
    private static final List<Consumer<String>> listeners = new ArrayList<>();

    // ── File-change listeners ─────────────────────────────────────
    // Called when tasks.md OR work-log.md changes for the current project
    private static final List<Runnable> fileChangeListeners = new ArrayList<>();

    // ── WatchService state ────────────────────────────────────────
    private static WatchService watchService;
    private static ScheduledExecutorService watchExecutor;
    private static Path watchedDir;             // currently watched notes/ directory
    private static volatile long lastEventMs;   // for debouncing

    // ── Project switch ────────────────────────────────────────────

    public static void setCurrentProject(String project) {
        currentProject = project;
        listeners.forEach(l -> l.accept(project));
    }

    public static String getCurrentProject() {
        return currentProject;
    }

    public static void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    // ── File-change listeners ─────────────────────────────────────

    /**
     * Register a callback that fires on the JavaFX thread whenever
     * tasks.md or work-log.md changes for the currently selected project.
     */
    public static void addFileChangeListener(Runnable listener) {
        fileChangeListeners.add(listener);
    }

    /** Notify all file-change listeners (always on JavaFX thread). */
    public static void notifyFileChanged() {
        Platform.runLater(() -> fileChangeListeners.forEach(Runnable::run));
    }

    // ── WatchService ──────────────────────────────────────────────

    /**
     * Start watching the notes/ directory for a project.
     * Called by LogController and StatsController after project is set.
     * Safe to call multiple times — stops the previous watcher first.
     *
     * @param notesDir  Path to the project's notes/ folder
     *                  e.g. ~/Work/01_Projects/my-project/notes/
     */
    public static void watchProjectDir(Path notesDir) {
        stopWatcher();

        if (notesDir == null || !Files.exists(notesDir)) return;

        try {
            watchedDir   = notesDir;
            watchService = FileSystems.getDefault().newWatchService();
            notesDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            watchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "workctl-file-watcher");
                t.setDaemon(true);  // won't prevent JVM shutdown
                return t;
            });

            watchExecutor.scheduleWithFixedDelay(
                    ProjectContext::pollWatchService,
                    0, 300, TimeUnit.MILLISECONDS
            );

        } catch (IOException e) {
            System.err.println("[ProjectContext] WatchService failed: " + e.getMessage());
        }
    }

    /** Stop the current watcher (if any). */
    public static void stopWatcher() {
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
            watchExecutor = null;
        }
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignored) {}
            watchService = null;
        }
        watchedDir = null;
    }

    /**
     * Poll the WatchService for events.
     * Runs every 300ms on the watcher thread.
     * Debounces: waits 500ms after the last event before notifying.
     */
    private static void pollWatchService() {
        if (watchService == null) return;

        WatchKey key = watchService.poll();
        if (key == null) {
            // No new events — check if debounce window has elapsed
            if (lastEventMs > 0
                    && System.currentTimeMillis() - lastEventMs > 500) {
                lastEventMs = 0;
                notifyFileChanged();
            }
            return;
        }

        boolean relevant = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            Path changed = (Path) event.context();
            String name  = changed.getFileName().toString();
            // Only react to the two files we care about
            if (name.equals("tasks.md") || name.equals("work-log.md")) {
                relevant = true;
            }
        }

        if (relevant) {
            lastEventMs = System.currentTimeMillis();
        }

        key.reset();
    }
}