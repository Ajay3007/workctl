package com.workctl.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProjectContext {

    private static String currentProject;
    private static final List<Consumer<String>> listeners = new ArrayList<>();

    public static void setCurrentProject(String project) {
        currentProject = project;
        listeners.forEach(listener -> listener.accept(project));
    }

    public static String getCurrentProject() {
        return currentProject;
    }

    public static void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }
}
