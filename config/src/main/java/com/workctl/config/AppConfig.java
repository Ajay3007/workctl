package com.workctl.config;

/**
 * Application configuration object.
 *
 * Stored at: ~/.workctl/config.yaml
 *
 * Example config.yaml after adding AI key:
 *   workspace: "C:/Users/Ajay/Work"
 *   editor: "code"
 *   dateFormat: "yyyy-MM-dd"
 *   anthropicApiKey: "sk-ant-api03-..."
 */
public class AppConfig {
    private String workspace;
    private String editor;
    private String dateFormat;
    private String anthropicApiKey; // NEW: for AI agent

    public AppConfig() {
        this.workspace = System.getProperty("user.home") + "/Work";
        this.editor = "code";
        this.dateFormat = "yyyy-MM-dd";
        this.anthropicApiKey = "";
    }

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }

    public String getEditor() { return editor; }
    public void setEditor(String editor) { this.editor = editor; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    // NEW
    public String getAnthropicApiKey() { return anthropicApiKey; }
    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "workspace='" + workspace + '\'' +
                ", editor='" + editor + '\'' +
                ", dateFormat='" + dateFormat + '\'' +
                ", anthropicApiKey='" +
                (anthropicApiKey != null && !anthropicApiKey.isBlank() ? "***configured***" : "NOT SET") +
                "'}";
    }
}
