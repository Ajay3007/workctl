package com.workctl.config;

/**
 * Application configuration object.
 */
public class AppConfig {
    private String workspace;
    private String editor;
    private String dateFormat;

    public AppConfig() {
        // Default values
        this.workspace = System.getProperty("user.home") + "/Work";
        this.editor = "code";
        this.dateFormat = "yyyy-MM-dd";
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getEditor() {
        return editor;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "workspace='" + workspace + '\'' +
                ", editor='" + editor + '\'' +
                ", dateFormat='" + dateFormat + '\'' +
                '}';
    }
}
