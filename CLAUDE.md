# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Full build
./gradlew clean build

# Run CLI (dev mode)
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli <command>

# Run GUI (dev mode)
./gradlew :gui:run

# Native executables (no JRE required on target)
./gradlew :cli:packageNative    # → build/release/workctl/workctl[.exe]
./gradlew :gui:packageNative    # → build/release/workctl-gui/workctl-gui[.exe]

# Distribution zips
./gradlew distAll               # Both CLI + GUI distributions
./gradlew releaseAll            # Full release artifacts

# Tests
./gradlew test                  # All modules
./gradlew :core:test            # Single module
./gradlew :core:test --tests "com.workctl.core.service.TaskServiceTest" # Single class
```

## Module Structure

Five Gradle submodules with one-way dependency flow:

```
cli  ─┐
gui  ─┼──► core ──► config
agent─┘
```

- **core** — Business logic and Markdown-based persistence. No UI dependencies.
- **cli** — Picocli commands. Entry: `WorkctlCLI.java`. 10 top-level commands.
- **gui** — JavaFX 21 desktop app. Entry: `WorkctlApp.java` + `main.fxml`.
- **agent** — Claude API integration with tool-use loop. Entry: `AgentService.java`.
- **config** — Jackson/SnakeYAML config loading from `~/.workctl/config.yaml`.

## Architecture

### Filesystem as Database

All data lives in plain Markdown files inside a user-configured workspace directory. There is no database or migration system. The workspace layout is:

```
<workspace>/01_Projects/<project-name>/
    tasks.md
    work-log.md
    meetings/
```

### Task Serialization Pattern

Tasks are stored in `tasks.md` as Markdown blocks. The `Task` model serializes via `toMarkdown()` / `fromMarkdownBlock()`. Metadata (creation date, tags) is embedded invisibly as HTML comments:

```markdown
1. [ ] (P2) Build reporting feature  <!-- created=2026-02-20 -->
    Full description here.
    - [ ] Design data model
    - [x] Implement parser
```

When modifying task parsing or storage, both `Task.java` and `TaskService.java` will need changes—they share responsibility for the format.

### AI Agent Tool-Use Loop

`AnthropicClient.java` implements a multi-turn tool-use loop (max 5 iterations):
1. Sends user message + tool definitions to `https://api.anthropic.com/v1/messages`
2. If `stop_reason` is `tool_use`, executes the requested tool and sends results back
3. Repeats until `stop_reason = "end_turn"`

Uses Java 11's built-in `HttpClient` (no external HTTP library). Model is hardcoded to `claude-opus-4-6` with 4096 max tokens.

Agent tools implement the `AgentTool` interface and are passed to `AnthropicClient` as a list. Write-mode tools (`AddTaskTool`, `MoveTaskTool`, `AddSubtaskTool`) are only included when `--act` flag is set or the GUI write-mode toggle is on.

### GUI File Watching

`MainController` and individual panel controllers watch Markdown files using `WatchService` and auto-refresh UI when the CLI modifies files. This keeps CLI and GUI in sync without any IPC. `ProjectContext` is a thread-safe static holder for the currently selected project, shared across all controllers.

### Configuration

`~/.workctl/config.yaml` is the single config source. Key fields: `workspace`, `anthropicApiKey`, `editor`, `dateFormat`. `ConfigManager.load()` is called at startup; services receive the config object rather than reading it directly.

## Key Files for Common Tasks

| Task | Files |
|------|-------|
| Add CLI subcommand | `cli/commands/`, register in `WorkctlCLI.java` |
| Add agent tool | `agent/tools/` (implement `AgentTool`), register in `AgentService.java` |
| Change task Markdown format | `core/model/Task.java` + `core/service/TaskService.java` |
| Add config field | `config/AppConfig.java` + update `~/.workctl/config.yaml` |
| Add GUI panel | New controller + FXML, add tab in `MainController.java` |

## Current Uncommitted Changes

At conversation start, these files had uncommitted modifications:
- `cli/src/main/java/com/workctl/cli/commands/TaskCommand.java`
- `core/src/main/java/com/workctl/core/service/TaskService.java`
- `docs/cli-api_updated.md`

These are related to subtask management enhancements.
