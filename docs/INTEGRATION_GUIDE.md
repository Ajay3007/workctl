# ============================================================
# COMPLETE INTEGRATION GUIDE — workctl AI Agent
# ============================================================
# Follow these steps in order. Each step references the exact
# file you need to modify and what to change.
# ============================================================


# ── STEP 1: settings.gradle ─────────────────────────────────
# Add 'agent' to the module list

# BEFORE:
rootProject.name = 'workctl'
include 'core', 'cli', 'gui', 'config'

# AFTER:
rootProject.name = 'workctl'
include 'core', 'cli', 'gui', 'config', 'agent'


# ── STEP 2: Create agent module folder ─────────────────────
## Create this folder structure (copy the agent/ files provided):

```
workctl/
└── agent/
    ├── build.gradle
    └── src/main/java/com/workctl/agent/
        ├── AgentService.java
        ├── AnthropicClient.java
        ├── ContextBuilder.java
        └── tools/
            ├── AgentTool.java
            ├── ListTasksTool.java
            ├── AddTaskTool.java
            ├── MoveTaskTool.java
            ├── SearchLogsTool.java
            └── GetInsightsTool.java
```

# ── STEP 3: cli/build.gradle ────────────────────────────────
# Add agent module dependency

```bash
dependencies {
    implementation project(':core')
    implementation project(':config')
    implementation project(':agent')       # ← ADD THIS
    implementation 'info.picocli:picocli:4.7.5'
}
```

# ── STEP 4: gui/build.gradle ────────────────────────────────
# Add agent module dependency

dependencies {
    # ... existing deps ...
    implementation project(':agent')       # ← ADD THIS
}


# ── STEP 5: config/AppConfig.java ───────────────────────────
# Replace with the updated AppConfig.java (adds anthropicApiKey field)
# File provided: config/AppConfig.java


# ── STEP 6: config/ConfigWriter.java ────────────────────────
# Add anthropicApiKey to the YAML write map

# In ConfigWriter.write(), add:
data.put("anthropicApiKey", config.getAnthropicApiKey());


# ── STEP 7: cli/ConfigCommand.java ──────────────────────────
# Add anthropicApiKey to set/get/show

# In Set.run() switch:
case "anthropicapikey" -> config.setAnthropicApiKey(value);

# In Get.run() switch:
case "anthropicapikey" -> config.getAnthropicApiKey();

# In Show.run():
System.out.println("anthropicApiKey = " +
    (config.getAnthropicApiKey() != null && !config.getAnthropicApiKey().isBlank()
        ? "***configured***" : "NOT SET"));


# ── STEP 8: cli/WorkctlCLI.java ─────────────────────────────
# Register AskCommand

# Add to subcommands list:
AskCommand.class

# Add import:
import com.workctl.cli.commands.AskCommand;


# ── STEP 9: Copy AskCommand.java ────────────────────────────
# Copy cli/AskCommand.java to:
# cli/src/main/java/com/workctl/cli/commands/AskCommand.java


# ── STEP 10: Copy AgentPanel.java ───────────────────────────
# Copy gui/AgentPanel.java to:
# gui/src/main/java/com/workctl/gui/agent/AgentPanel.java


# ── STEP 11: Add AgentPanel tab to GUI ──────────────────────
# In your MainController.java or TaskController, wherever your TabPane is:

# Add import:
import com.workctl.gui.agent.AgentPanel;

# Create panel (do this after project is loaded):
AgentPanel agentPanel = new AgentPanel(currentProject);
Tab agentTab = new Tab("🤖 AI Agent", agentPanel);
agentTab.setClosable(false);
tabPane.getTabs().add(agentTab);

# When user selects a different project, call:
agentPanel.setProject(newProjectName);


# ── STEP 12: Set your API key ───────────────────────────────
# Run once after building:
workctl config set anthropicApiKey sk-ant-api03-YOUR_KEY_HERE
workctl config show   # verify it shows ***configured***


# ── STEP 13: Build and test ─────────────────────────────────
./gradlew build
./gradlew :cli:installDist

# Test CLI:
workctl ask myproject "What tasks are stagnant?"
workctl ask myproject --weekly
workctl ask myproject --insight
workctl ask myproject --act "Add a task to write unit tests for TaskService"

# Test GUI:
./gradlew :gui:run
# → Click "AI Agent" tab → type a question → press Send
