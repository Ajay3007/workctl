# workctl — Complete Documentation

> **[← README](../README.md)** | [CLI Reference](cli-api.md) | [Workflows Guide](workflows-guide.md) | [Setup](SETUP.md) | [Distribution Guide](DISTRIBUTION_GUIDE.md)
>
> A hybrid CLI + GUI developer productivity system built in Java.  
> Filesystem-backed · Markdown-native · AI-powered · Version-control friendly

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Module Structure](#3-module-structure)
4. [Workspace Layout](#4-workspace-layout)
5. [Data Formats](#5-data-formats)
6. [CLI Commands Reference](#6-cli-commands-reference)
7. [GUI Features](#7-gui-features)
8. [AI Agent Integration](#8-ai-agent-integration)
9. [Configuration](#9-configuration)
10. [Build & Run](#10-build--run)

---

## 1. Project Overview

workctl is a developer productivity system that combines:

- **Structured Markdown storage** — tasks and logs live in plain `.md` files, fully git-trackable
- **Kanban task tracking** — Open → In Progress → Done workflow with priorities, subtasks, and toggle/delete
- **Date-aware work logging** — daily structured entries with section normalization
- **Weekly summaries** — auto-generated from log entries across a date range
- **Project insights** — productivity scores, stagnation detection, completion analytics
- **Workflow engine** — reusable procedure templates + named execution runs with per-step tracking (v2.0.0)
- **Command library** — 215 built-in commands (Linux, git, docker, ssh, networking, text-processing) pre-populated on first run (v2.0.0)
- **JavaFX desktop GUI** — nine-tab desktop app with drag-and-drop Kanban, Workflows panel, and more
- **AI agent** — Claude-powered assistant embedded in both CLI and GUI

Everything is stored as Markdown files on your filesystem. There is no database.

---

## 2. Architecture

### 2.1 High-Level Layer Diagram

```
┌─────────────────────────────────────────────────────────┐
│                      User Interface                       │
│                                                           │
│   ┌──────────────────┐       ┌─────────────────────┐    │
│   │    CLI (Picocli)  │       │   GUI (JavaFX)       │    │
│   │  workctl <cmd>   │       │  Kanban + Chat Panel │    │
│   └────────┬─────────┘       └──────────┬──────────┘    │
└────────────│────────────────────────────│────────────────┘
             │                            │
             ▼                            ▼
┌─────────────────────────────────────────────────────────┐
│                     Agent Module                          │
│   AgentService · AnthropicClient · ContextBuilder        │
│   Tools: list_tasks · add_task · move_task               │
│          search_logs · get_insights                       │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                      Core Module                          │
│   TaskService · ProjectService · StatsService            │
│   Markdown Parser/Writer · Domain Models                 │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    Config Module                          │
│   AppConfig · ConfigManager · ConfigLoader               │
│   ~/.workctl/config.yaml                                 │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Filesystem Storage                      │
│   tasks.md · work-log.md · weekly summaries              │
│   ~/Work/01_Projects/<project-name>/notes/               │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Module Dependency Graph

```
config ◄──── core ◄──── agent ◄──── cli
               ▲                     
               └──────────── gui ◄── agent
```

The `config` module has no internal dependencies. `core` depends only on `config`. `agent` depends on `core` and `config`. Both `cli` and `gui` depend on `agent` (which transitively gives them `core` and `config`).

### 2.3 Core Service Interactions (UML Class Diagram)

```
┌──────────────────┐     uses      ┌──────────────────┐
│   TaskService    │◄──────────────│  AgentService    │
│                  │               └──────────────────┘
│ + getTasks()     │     uses      ┌──────────────────┐
│ + addTask()      │◄──────────────│  ListTasksTool   │
│ + updateStatus() │               └──────────────────┘
│ + updateDesc()   │     uses      ┌──────────────────┐
│ + updatePriority │◄──────────────│  AddTaskTool     │
│ + deleteTask()   │               └──────────────────┘
│ + getTask()      │     uses      ┌──────────────────┐
└──────────────────┘◄──────────────│  MoveTaskTool    │
                                   └──────────────────┘

┌──────────────────┐     uses      ┌──────────────────┐
│  ProjectService  │◄──────────────│  SearchLogsTool  │
│                  │               └──────────────────┘
│ + createProject()│
│ + listProjects() │
│ + addLogEntry()  │
│ + genWeeklySumm()│
│ + search()       │
└──────────────────┘

┌──────────────────┐     uses      ┌──────────────────┐
│  StatsService    │◄──────────────│ GetInsightsTool  │
│                  │               └──────────────────┘
│ + generateInsig()│
│ + generate()     │
└──────────────────┘
```

---

## 3. Module Structure

### Gradle Multi-Module Layout

```
workctl/
├── build.gradle                     ← Root build: distribution tasks
├── settings.gradle                  ← include 'core','cli','gui','config','agent'
│
├── config/                          ← Configuration module
│   └── src/main/java/com/workctl/config/
│       ├── AppConfig.java           ← POJO: workspace, editor, dateFormat, apiKey
│       ├── ConfigLoader.java        ← SnakeYAML 2.x reader
│       ├── ConfigManager.java       ← Singleton facade (~/.workctl/config.yaml)
│       └── ConfigWriter.java        ← SnakeYAML writer
│
├── core/                            ← Business logic module
│   └── src/main/java/com/workctl/core/
│       ├── model/
│       │   ├── Task.java            ← Domain: id, description, status, priority, subtasks
│       │   ├── TaskStatus.java      ← Enum: OPEN, IN_PROGRESS, DONE
│       │   ├── StepStatus.java      ← Enum: TODO, DONE, SKIPPED  (workflow steps)
│       │   ├── RunStatus.java       ← Enum: IN_PROGRESS, COMPLETED, ABANDONED
│       │   ├── Project.java         ← Domain: id, name, description
│       │   └── ProjectInsights.java ← Stats result object
│       ├── domain/
│       │   ├── WorkflowTemplate.java← Blueprint with TemplateStep inner class
│       │   ├── WorkflowRun.java     ← Execution with RunStep + SubStep inner classes
│       │   ├── Meeting.java         ← Meeting notes domain model
│       │   ├── Interview.java       ← Interview tracking domain model
│       │   └── WorkspaceManager.java← Folder initialization
│       └── service/
│           ├── TaskService.java     ← Full CRUD on tasks.md + subtask management
│           ├── ProjectService.java  ← Project creation, logging, search
│           ├── StatsService.java    ← Productivity analytics
│           ├── WorkflowService.java ← Template + Run CRUD, Markdown serialization
│           ├── MeetingService.java  ← Meeting notes CRUD (Markdown-persisted)
│           ├── InterviewService.java← Interview CRUD (Markdown-persisted)
│           ├── CommandService.java  ← Command library CRUD
│           └── WeeklyService.java   ← Weekly summary generation
│
├── cli/                             ← Command-line interface module
│   └── src/main/java/com/workctl/cli/
│       ├── WorkctlCLI.java          ← Picocli root command
│       └── commands/
│           ├── InitCommand.java
│           ├── ProjectCommand.java
│           ├── TaskCommand.java     ← Includes SubtaskCommand inner class
│           ├── LogCommand.java
│           ├── WeeklyCommand.java
│           ├── SearchCommand.java
│           ├── StatsCommand.java
│           ├── InsightCommand.java
│           ├── ConfigCommand.java
│           ├── CmdCommand.java      ← Command library CLI
│           ├── MeetingCommand.java  ← Meeting notes CLI
│           ├── FlowCommand.java     ← Workflows CLI (template + run + step)
│           └── AskCommand.java      ← AI agent CLI command
│
├── gui/                             ← JavaFX desktop app module
│   └── src/main/java/com/workctl/gui/
│       ├── WorkctlApp.java          ← JavaFX Application entry point
│       ├── ProjectContext.java      ← Shared static event bus (project selection)
│       ├── controller/
│       │   ├── MainController.java  ← Sidebar project list + tab host
│       │   ├── TaskController.java  ← Kanban board
│       │   ├── LogController.java   ← Work log viewer
│       │   ├── StatsController.java ← Statistics + activity heatmap
│       │   ├── CommandController.java  ← Command library browser
│       │   ├── MeetingController.java  ← Meeting notes manager
│       │   ├── InterviewController.java← Interview tracker
│       │   ├── WorkflowController.java ← Workflow templates + runs
│       │   └── WeeklyReportController.java ← Weekly report generator
│       └── agent/
│           └── AgentPanel.java      ← AI chat panel + Markdown preview
│
└── agent/                           ← AI agent module
    └── src/main/java/com/workctl/agent/
        ├── AgentService.java        ← Main orchestrator
        ├── AnthropicClient.java     ← HTTP + tool-use loop
        ├── ContextBuilder.java      ← Project-aware system prompt builder
        └── tools/
            ├── AgentTool.java       ← Interface
            ├── ListTasksTool.java
            ├── AddTaskTool.java
            ├── MoveTaskTool.java
            ├── SearchLogsTool.java
            └── GetInsightsTool.java
```

---

## 4. Workspace Layout

Initialized with `workctl init --workspace <path>`:

```text
~/Work/                              ← workspace root (configurable)
├── 00_Inbox/                        ← Unprocessed items
├── 01_Projects/                     ← All projects live here
│   └── <project-name>/
│       ├── README.md                ← Project description
│       ├── notes/
│       │   ├── tasks.md             ← All tasks (Kanban source of truth)
│       │   └── work-log.md          ← Daily structured log entries
│       ├── meetings/                ← Project-scoped meeting notes
│       └── workflows/               ← Project-scoped workflow runs
├── 02_Commands/                     ← Command library (one .md per category)
│   ├── docker.md
│   ├── git.md
│   ├── linux.md
│   └── ...
├── 03_Meetings/                     ← Global meeting notes
├── 04_References/                   ← Reference documents
├── 06_Workflows/
│   ├── templates/                   ← Reusable procedure blueprints
│   └── runs/                        ← Global workflow runs
└── 99_Archive/                      ← Archived projects
```

---

## 5. Data Formats

### 5.1 tasks.md Format

```markdown
# Tasks — project-name

<!-- NEXT_ID: 13 -->

## Open
12. [ ] (P1) Fix authentication bug  <!-- created=2026-02-16 -->
    Additional description line here
    Second description line

## In Progress
5. [~] (P2) Refactor logging module  <!-- created=2026-02-10 -->

## Done
3. [x] (P3) Update README  <!-- created=2026-02-01 -->
```

**Task line anatomy:**

```
12. [ ] (P1) Fix authentication bug  <!-- created=2026-02-16 -->
│   │   │    │                        └── metadata (stripped on load, re-added on save)
│   │   │    └── title (first line of description)
│   │   └── priority badge: P1, P2, or P3
│   └── status: [ ]=Open  [~]=In Progress  [x]=Done
└── task ID (auto-incremented, never reused)
```

**Multiline tasks** — continuation lines are indented with 4 spaces:

```
12. [ ] (P1) Task title  <!-- created=2026-02-16 -->
    Line 2 of description
    Line 3 of description
```

### 5.2 work-log.md Format

```markdown
# project-name — Work Log

## 2026-02-19

### Assigned
- Created Task #12 — Fix authentication bug
  <!-- TASK_EVENT: id=12 action=created priority=1 date=2026-02-19 -->

### Done
- Completed Task #3 — Update README [#docs #completed]
  <!-- TASK_EVENT: id=3 action=completed date=2026-02-19 -->

### Changes Suggested
- Proposed refactor of auth module

### Commands Used
- git rebase -i HEAD~3

### Notes
- Discussed deployment timeline with team
```

**Auto-logging:** Every `task add`, `task start`, and `task done` command automatically appends a `TASK_EVENT` block to work-log.md. `StatsService` parses these blocks to compute productivity scores.

### 5.3 Parsing State Machine (TaskService)

```
Start
  │
  ├── Line matches "NEXT_ID: N"     → record nextId, continue
  │
  ├── Line starts with "## "        → set currentStatus (Open/In Progress/Done)
  │
  ├── Line matches task regex        → save previous task, start new task
  │   \d+\. \[(.)\](?: \(P\d\))? (.+)
  │   group(4) = title (stripped of <!-- --> metadata)
  │
  ├── Line starts with "    " (4sp)
  │   ├── starts with "<!--"        → extract createdDate, skip (don't add to description)
  │   └── otherwise                 → append to description (multiline)
  │
  └── End of file                   → save last task
```

---

## 6. CLI Commands Reference

### 6.1 Initialization

```bash
workctl init --workspace <path>
```

Creates workspace folder structure. Writes `~/.workctl/config.yaml`.

---

### 6.2 Project Commands

```bash
# Create a project
workctl project create <name> --description "text"

# List all projects
workctl project list
```

**Project creation** creates the folder `01_Projects/<name>/` with `README.md`, `notes/tasks.md`, and `notes/work-log.md`.

---

### 6.3 Task Commands

```bash
# Add task (inline)
workctl task add <project> "Task title and description"

# Add task with priority (1=High, 2=Medium, 3=Low)
workctl task add <project> "Fix memory leak" -p 1

# Add task in editor (opens VS Code or configured editor)
workctl task add <project> --edit

# Add task from file
workctl task add <project> --file task.txt

# Add task interactively (type content, finish with END)
workctl task add <project>

# List all tasks grouped by status
workctl task list <project>

# Move task to In Progress
workctl task start <project> <id>

# Mark task as Done
workctl task done <project> <id>

# Show full task description
workctl task show <project> <id>

# Delete a task
workctl task delete <project> -id <id>
```

**Task status flow:**

```
  workctl task add        workctl task start      workctl task done
        │                        │                       │
        ▼                        ▼                       ▼
   ┌─────────┐            ┌───────────┐           ┌──────────┐
   │  OPEN   │───────────►│IN_PROGRESS│──────────►│   DONE   │
   └─────────┘            └───────────┘           └──────────┘
        ▲                        │
        └────────────────────────┘
          (context menu in GUI: Move to Open)
```

---

### 6.4 Log Commands

```bash
# Open work-log.md in editor (interactive mode)
workctl log <project>

# Add a log entry inline
workctl log <project> --message "Deployed auth service to staging"

# Add to a specific section
workctl log <project> --section done --message "Completed API integration"

# Add to section using editor
workctl log <project> --section done --edit

# Add with tags
workctl log <project> --tag redis dpdk --message "Tuned packet processing"
```

**Sections:** `assigned` · `done` · `changes` · `commands` · `notes`

**Smart section normalization:** If today's date block doesn't exist, it's created. If a section is missing inside the block, it's added automatically.

---

### 6.5 Weekly Summary Commands

```bash
# Generate summary for current week
workctl weekly <project>

# Custom date range
workctl weekly <project> --from 2026-02-11 --to 2026-02-17

# Show specific section only
workctl weekly <project> --section done
```

Output saved to `notes/weekly-summary-<from>_to_<to>.md`.

---

### 6.6 Search Commands

```bash
# Keyword search across all project logs
workctl search redis

# Tag search
workctl search dpdk --tag
```

Searches all `work-log.md` files in `01_Projects/` recursively.

---

### 6.7 Stats & Insights Commands

```bash
# Computed statistics
workctl stats <project>

# AI-interpretable insights
workctl insight <project>
```

**Productivity Score Formula:**

```
finalScore =
    completionRate    × 0.30    (% of total tasks done)
  + velocityScore     × 0.25    (min(completedThisWeek × 10, 100))
  + focusScore        × 0.15    (100 if open≤7, 70 if ≤15, else 40)
  + stagnationScore   × 0.20    (100 - stagnantCount × 5, min 0)
  + consistencyScore  × 0.10    (10 if tasks completed 3 weeks running)
```

| Score | Status |
|-------|--------|
| 85+ | 🔥 Elite Execution |
| 70–85 | 🚀 Strong Momentum |
| 50–70 | ⚖ Stable but Improve |
| 30–50 | ⚠ Fragmented |
| <30 | 🧊 Stalled |

---

### 6.8 Configuration Commands

```bash
# Show all config
workctl config show

# Set workspace path
workctl config set workspace /path/to/workspace

# Set editor
workctl config set editor code

# Set date format
workctl config set dateFormat yyyy-MM-dd

# Set Anthropic API key (for AI agent)
workctl config set anthropicApiKey sk-ant-api03-YOUR_KEY
```

Config stored at: `~/.workctl/config.yaml`

---

### 6.9 AI Agent Commands

```bash
# Ask a question (read-only)
workctl ask <project> "What did I work on this week?"
workctl ask <project> "Which P1 tasks are stagnant?"
workctl ask <project> "How is my productivity score?"

# Write mode — agent can create and move tasks
workctl ask <project> --act "Break down the auth feature into tasks"
workctl ask <project> --act "Mark task 52 as done"

# AI-powered weekly summary
workctl ask <project> --weekly
workctl ask <project> --weekly --from 2026-02-11 --to 2026-02-17

# AI-powered project insights (richer than workctl insight)
workctl ask <project> --insight
```

---

### 6.10 Command Snippets

```bash
# Save a command string for future reference
workctl cmd add docker "docker kill \$(docker ps -q)" -t "Kill all containers"

# List saved commands
workctl cmd list
workctl cmd list git -p auth-service
```

Stored into markdown files inside `02_Commands`. Useful for tracking reusable CLI syntax.

---

### 6.11 Meetings

```bash
# Create a new meeting timestamp file
workctl meeting redis-load-test "Weekly Sync"
```

Creates a standard meeting notes template inside the `03_Meetings/` folder associated with the specified project.

---

## 7. GUI Features

### 7.1 Layout Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│  workctl                                                                     │
├────────────┬────────────────────────────────────────────────────────────────┤
│            │ Tasks│Commands│Logs│Stats│Meetings│Interview│Workflows│Weekly│AI│
│  Project   ├──────────────────────────────────────────────────────────────  │
│  Explorer  │                                                                 │
│            │                   [Selected Tab Content]                        │
│ ─────────  │                                                                 │
│ project-1  │                                                                 │
│ project-2  │                                                                 │
│ project-3  │                                                                 │
│            │                                                                 │
└────────────┴────────────────────────────────────────────────────────────────┘
```

**Nine tabs** — all share the project selected in the left sidebar:

| Tab | Controller | What it does |
| --- | --- | --- |
| **Tasks** | `TaskController` | Kanban board (Open / In Progress / Done) with drag & drop, subtasks, Markdown editor |
| **Commands** | `CommandController` | Browse, copy, add, and edit the personal command library; filter by category and scope |
| **Logs** | `LogController` | Read-only view of `work-log.md` for the active project |
| **Stats** | `StatsController` | Productivity score, completion rate, stagnation alerts, 30-day activity heatmap |
| **Meetings** | `MeetingController` | Create and view meeting notes; stored as per-project Markdown files |
| **Interview** | `InterviewController` | Track interviews: questions, ratings, candidate notes, outcomes |
| **Workflows** | `WorkflowController` | Manage reusable templates and named procedure runs with step-by-step tracking |
| **Weekly Report** | `WeeklyReportController` | Generate and view weekly summaries for a custom date range |
| **AI Agent** | `AgentPanel` | Claude-powered chat panel with read/write mode and quick-action buttons |

### 7.2 Kanban Board (Tasks Tab)

```
┌──────────────────────────────────────────────────────────────────┐
│  + Add Task    Create a detailed task with Markdown support       │
├───────────────────┬──────────────────┬───────────────────────────┤
│   Open (13)       │  In Progress (2)  │      Done (38)            │
│                   │                   │                           │
│ ┌───────────────┐ │ ┌───────────────┐ │ ┌───────────────┐        │
│ │ #52  [P1]     │ │ │ #60  [P2]     │ │ │ #48  [P3]     │        │
│ │ Add Sub task  │ │ │ Debug Date    │ │ │ Update README │        │
│ │            ⓘ │ │ │ comment    ⓘ │ │ │            ⓘ │        │
│ └───────────────┘ │ └───────────────┘ │ └───────────────┘        │
│                   │                   │                           │
│ ┌───────────────┐ │                   │                           │
│ │ #59  [P2]     │ │                   │                           │
│ │ ...           │ │                   │                           │
│ └───────────────┘ │                   │                           │
└───────────────────┴──────────────────┴───────────────────────────┘
```

**Interactions:**

| Action | Result |
|--------|--------|
| Single click | Highlight card with blue border |
| Double click | Inline edit mode (TextArea replaces title row) |
| Enter (in edit) | Save and exit edit mode |
| Shift+Enter (in edit) | Insert new line |
| Escape (in edit) | Cancel and restore |
| Right-click | Context menu: Move to Open/In Progress/Done, Change Priority |
| Drag card | Drop onto another column's scroll pane to move status |
| Click ⓘ button | Open Task Details dialog |

**Task Details Dialog** (via ⓘ button):
- Left panel: ID, status, priority badge, created date
- Right panel: WebView with CommonMark-rendered description
- Buttons: Update Task (edit full description), Delete Task, Close

**Add Task Dialog:**
- Left: TextArea for Markdown description + priority dropdown (P1/P2/P3)
- Right: Live CommonMark preview (WebView, updates as you type)

### 7.3 Logs Tab

Read-only `TextArea` displaying the raw `work-log.md` for the selected project. Refreshes on project selection.

### 7.4 Stats Tab

```
┌────────────────────────────────────────────────┐
│  Total: 56  │  Open: 18  │  In Progress: 0     │
│  Done: 38                                       │
│                                                 │
│  Completion:  ████████████░░░░  67.9%          │
│                                                 │
│  ⚠  0 tasks stagnant for >7 days               │
│                                                 │
│  Productivity Score: 73.9 / 100                 │
│  ██████████████████░░░░░                       │
│                                                 │
│  Activity Heatmap (last 30 days):               │
│  □ □ ■ □ ■ ■ □                                 │
│  □ ■ ■ ■ □ ■ □                                 │
│  ■ ■ □ ■ ■ ■ ■                                 │
│  ■ ■ ■ ■ □ □ □                                 │
└────────────────────────────────────────────────┘
```

Heatmap colors: `#eeeeee` (0) → `#c6e48b` (1) → `#7bc96f` (2-3) → `#239a3b` (3-5) → `#196127` (5+)

### 7.5 Project Selection Bus (ProjectContext)

All controllers communicate through a static event bus pattern:

```text
User clicks project in sidebar
           │
           ▼
MainController.projectListView listener
           │
           └──► ProjectContext.setCurrentProject(name)
                           │
                           ├──► TaskController          → refreshBoard()
                           ├──► LogController           → read work-log.md
                           ├──► StatsController         → generateInsights()
                           ├──► CommandController       → reload command list
                           ├──► MeetingController       → load meetings
                           ├──► InterviewController     → load interviews
                           ├──► WorkflowController      → filter runs by project
                           ├──► WeeklyReportController  → reset date range
                           └──► AgentPanel              → clear chat, show welcome
```

---

## 8. AI Agent Integration

### 8.1 Agent Architecture

```
User Input (CLI or GUI)
         │
         ▼
  AgentService.ask(project, message, allowWrite)
         │
         ├──► ContextBuilder.buildSystemPrompt()
         │           │
         │           ├── TaskService.getTasks()      → task board snapshot
         │           ├── Read work-log.md            → last 7 days of entries
         │           └── Highlights P1 tasks + stagnant tasks
         │
         ├──► AnthropicClient.chat(systemPrompt, message, tools)
         │
         └──► Tool-Use Loop:
                    │
                    ▼
              POST /v1/messages → Claude API
                    │
              ┌─────┴──────────────────────┐
              │ stop_reason = "tool_use"?  │
              └─────┬──────────────────────┘
                    │ YES
                    ▼
              Execute tool(s):
              ┌──────────────────────────────────────┐
              │  list_tasks    → TaskService          │
              │  add_task      → TaskService (write)  │
              │  move_task     → TaskService (write)  │
              │  search_logs   → Read work-log.md     │
              │  get_insights  → StatsService         │
              └──────────────────────────────────────┘
                    │
                    ▼
              POST /v1/messages with tool_result
                    │
              stop_reason = "end_turn" → return text
```

### 8.2 Tool Reference

| Tool | Mode | What it does |
|------|------|--------------|
| `list_tasks` | Read | Lists tasks filtered by status. Returns ID, title, priority, age, stagnation flag |
| `search_logs` | Read | Searches work-log.md by keyword and date range |
| `get_insights` | Read | Returns full ProjectInsights: productivity score, completion rate, stagnant count |
| `add_task` | **Write** | Creates a new task with description and priority |
| `move_task` | **Write** | Changes a task's status (OPEN / IN_PROGRESS / DONE) |

Write tools (`add_task`, `move_task`) are only registered when `--act` flag (CLI) or **Write mode ON** (GUI) is active.

### 8.3 Context Window (System Prompt)

Every API call includes a dynamically built system prompt containing:

```
Today's date: 2026-02-19
Current project: work-control

=== CURRENT TASK BOARD ===
Total: 56  |  Open: 18  |  In Progress: 0  |  Done: 38

P1 (High Priority) Tasks:
  #52 [OPEN] Add Sub task
  #61 [OPEN] Implement export feature

⚠ Stagnant Tasks (7+ days old, not completed):
  #52 [P1] Add Sub task (12 days)
  #48 [P2] Refactor parser (9 days)

=== RECENT WORK LOG (last 7 days) ===
## 2026-02-19
### Assigned
- Created Task #62 — Write unit tests
### Done
- Completed Task #58 — Fix date comment bug
...

=== YOUR BEHAVIOR ===
- Be concise but insightful. Don't just repeat raw data — interpret it.
- When you notice stagnant P1 tasks, proactively mention them.
[Write mode instructions if --act is active]
```

### 8.4 Agent Modes

| Mode | CLI Flag | GUI Toggle | What Agent Can Do |
|------|----------|------------|-------------------|
| **Read-only** (default) | *(none)* | Write mode OFF | Answer questions, search logs, generate insights |
| **Write mode** | `--act` | Write mode ON | + Create tasks, move task status |
| **Weekly AI summary** | `--weekly` | Weekly Summary button | Calls search_logs + get_insights, writes narrative |
| **AI insights** | `--insight` | Project Insights button | Calls get_insights + list_tasks, interprets data |
| **Goal decomposition** | `--act` + goal | Decompose Goal button (write ON) | Breaks goal into subtasks, creates them |

### 8.5 GUI Agent Panel Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│  🤖 AI Agent   Powered by Claude        [✏ Write Mode: OFF]         │
├──────────────────────────────────────────────────────────────────────┤
│  ℹ Write mode is OFF — agent can read tasks/logs but cannot modify   │
├──────────────────────────────────────────────────────────────────────┤
│ [📅 Weekly Summary] [📊 Project Insights] [⚠ Stagnant] [🔀 Decompose]│
├──────────────────────────┬───────────────────────────────────────────┤
│   CHAT (left)            │   📄 Markdown Preview (right)             │
│                          │                                           │
│  🤖 Hi! I'm your AI     │  Rendered HTML of latest agent response   │
│     assistant...         │  with full formatting:                    │
│                          │  • Tables render as tables                │
│  [User bubble]──────────►│  • **bold** renders bold                  │
│                          │  • # Headers render large                 │
│  🤖 [Agent response]    │  • Bullet points as <ul>                  │
│     [⎘ Copy] [⊞ Preview]│                                           │
│                          │  Right-click → Copy in WebView            │
├──────────────────────────┴───────────────────────────────────────────┤
│  ┌──────────────────────────────────────────┐  ┌──────────────────┐  │
│  │ Ask the agent anything...                │  │     Send ➤       │  │
│  └──────────────────────────────────────────┘  └──────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

**Write mode behavior:**
- **OFF** (default, grey button) — agent is read-only. Safe for all questions and analysis. Info bar shown in blue.
- **ON** (orange button) — agent can call `add_task` and `move_task`. Warning bar shown in yellow. Use when saying *"Break this feature into tasks"* or *"Mark task 52 as done"*.

---

## 9. Configuration

Config file location: `~/.workctl/config.yaml`

```yaml
workspace: "C:/Users/YourName/Work"
editor: "code"
dateFormat: "yyyy-MM-dd"
anthropicApiKey: "sk-ant-api03-..."
```

| Key | Default | Description |
|-----|---------|-------------|
| `workspace` | `~/Work` | Root folder for all projects |
| `editor` | `code` | Editor for `--edit` flag (`code`, `vim`, `nano`, etc.) |
| `dateFormat` | `yyyy-MM-dd` | Date format used in log headers |
| `anthropicApiKey` | *(empty)* | Claude API key for AI agent features |

**Editor integration:** When `--edit` is used, workctl writes content to a temp `.md` file, opens it with `<editor> --wait`, and reads the result after the editor closes. Lines starting with `#` are stripped.

---

## 10. Build & Run

### Prerequisites

- Java 17+
- Gradle (wrapper included: `./gradlew`)
- JavaFX 21 (handled by `org.openjfx.javafxplugin`)

### Build Everything

```bash
./gradlew build
```

### Run CLI (Development)

```bash
./gradlew :cli:run --args="task list myproject"
```

### Install CLI (Production)

```bash
./gradlew :cli:installDist
# Installs to: cli/build/install/workctl/bin/workctl.bat (Windows)
# Add to PATH for global access
```

### Run GUI

```bash
./gradlew :gui:run
```

### Package Native App

```bash
# Both CLI and GUI
./gradlew distAll

# Versioned release folder
./gradlew packageRelease

# ZIP archives
./gradlew zipReleases
```

### First-Time Setup

```bash
# 1. Initialize workspace
workctl init --workspace C:/Users/YourName/Work

# 2. Set API key for AI features
workctl config set anthropicApiKey sk-ant-api03-YOUR_KEY_HERE

# 3. Create a project
workctl project create my-project --description "My first workctl project"

# 4. Add a task
workctl task add my-project "Set up development environment" -p 1

# 5. Ask the AI agent
workctl ask my-project "What tasks do I have open?"
```

---

## Appendix: Known Limitations

| Area | Status | Notes |
|------|--------|-------|
| WeeklyService | Stub | Separate from `ProjectService.generateWeeklySummary()` |
| WorkLogService | Stub | Separate from `ProjectService.addLogEntry()` |
| FileSystemStore | Unused | Defined but not used by main services |
| MarkdownRenderer | Unused | Renders non-persisted domain objects |
