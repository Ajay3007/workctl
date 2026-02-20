# workctl — CLI API Reference

Complete reference for all CLI commands implemented in workctl.

---

## Quick Reference

| Command | Description |
|---|---|
| `workctl init` | Initialize workspace and config |
| `workctl project create` | Create a new project |
| `workctl project list` | List all projects |
| `workctl project delete` | Delete a project and all its data |
| `workctl log` | Add a work log entry |
| `workctl task add` | Add a new task |
| `workctl task list` | List all tasks by status |
| `workctl task show` | Show full task details + subtasks |
| `workctl task start` | Move task to In Progress |
| `workctl task done` | Mark task as Done |
| `workctl task delete` | Delete a task |
| `workctl task subtask add` | Add a subtask to a task |
| `workctl task subtask done` | Toggle a subtask done/undone |
| `workctl task subtask list` | List subtasks for a task |
| `workctl task subtask delete` | Delete a subtask |
| `workctl weekly` | Generate weekly summary |
| `workctl search` | Search logs by keyword or tag |
| `workctl stats` | Analytics from task lifecycle events |
| `workctl insight` | Intelligent project health insights |
| `workctl ask` | Ask the AI agent a question (read-only) |
| `workctl ask --act` | Ask the AI agent with write mode enabled |
| `workctl ask --weekly` | AI-powered weekly summary |
| `workctl ask --insight` | AI-powered project health insights |
| `workctl config set` | Set a config value |
| `workctl config get` | Get a config value |
| `workctl config show` | Show all config |

---

## Global Help

Every command and subcommand supports `--help`:

```bash
workctl --help
workctl task --help
workctl task subtask --help
workctl log --help
```

---

---

# 🏁 Initialization

## `workctl init`

Initializes workctl for the first time. Creates the config file at `~/.workctl/config.yaml` and sets up the workspace folder structure.

**Only needs to be run once.** If already initialized, it prints a warning and exits without overwriting.

### Usage

```bash
workctl init --workspace <path>
```

### Options

| Option | Short | Required | Description |
|---|---|---|---|
| `--workspace` | `-w` | ✅ Yes | Absolute path to your workspace directory |

### Examples

```bash
workctl init --workspace "C:\Users\Ajay\Work"
workctl init --workspace "/home/ajay/work"
```

### What It Creates

```
~/.workctl/
└── config.yaml          ← stores workspace, editor, dateFormat

<workspace>/
└── 01_Projects/         ← all project folders go here
```

### Default Config Written

```yaml
workspace: "<your-path>"
editor: "code"
dateFormat: "yyyy-MM-dd"
anthropicApiKey: "sk-ant-..."   # required for AI agent
```

---

---

# 📁 Project Commands

## `workctl project create`

Creates a new project directory inside the workspace with all required folder structure and starter files.

### Usage

```bash
workctl project create <project-name>
workctl project create <project-name> --description "text"
```

### Parameters

| Parameter | Description |
|---|---|
| `<project-name>` | Name of the project (used as folder name) |

### Options

| Option | Short | Description |
|---|---|---|
| `--description` | `-d` | Optional description for the project |

### Examples

```bash
workctl project create redis-load-test
workctl project create auth-service --description "OAuth2 implementation"
```

### What It Creates

```
workspace/
└── 01_Projects/
    └── redis-load-test/
        └── notes/
            ├── work-log.md     ← daily log file
            └── tasks.md        ← task board (Kanban storage)
```

---

## `workctl project list`

Lists all projects found under `01_Projects/` in the workspace.

### Usage

```bash
workctl project list
```

### Example Output

```
ℹ Projects:
  - redis-load-test
  - auth-service
  - workctl
```

---

## `workctl project delete`

Permanently deletes a project directory and **all its data** (tasks, logs, notes, docs). Requires the user to retype the project name to confirm — there is no undo.

### Usage

```bash
workctl project delete <project-name>
```

### Confirmation Prompt

```
Type the project name to confirm deletion [redis-load-test]: redis-load-test
✓ Project deleted: redis-load-test
```

If the typed name does not match exactly:
```
⚠ Deletion cancelled — name did not match.
```

### Examples

```bash
workctl project delete redis-load-test
workctl project delete auth-service
```

> **Warning:** This deletes the entire `<workspace>/01_Projects/<project-name>/` directory recursively. All tasks, logs, and notes are lost permanently.

---

---

# 📝 Log Commands

## `workctl log`

Adds an entry to the project's `work-log.md`. Supports four input modes: direct message, interactive, file, and editor. Automatically creates today's date block if it doesn't exist.

The log is structured under daily date headers with named sections.

### Sections

| Section | Purpose |
|---|---|
| `assigned` | Tasks assigned or picked up today |
| `done` | Work completed |
| `changes` | Code or config changes made |
| `commands` | Useful commands run |
| `notes` | General notes, observations, blockers |

If no `--section` is given, the entry goes into **Done** by default.

---

### Mode 1 — Direct Message

```bash
workctl log <project> --message "text"
workctl log <project> -m "text"
```

Adds a single-line message to the Done section.

```bash
workctl log redis-load-test -m "Fixed connection pool timeout"
```

---

### Mode 2 — Section-Specific Direct Message

```bash
workctl log <project> --section <section> --message "text"
workctl log <project> -s <section> -m "text"
```

```bash
workctl log redis-load-test --section commands -m "redis-cli --latency-history -h localhost"
workctl log redis-load-test --section notes -m "Pipeline mode shows 40% improvement"
workctl log redis-load-test --section assigned -m "Investigate slow GET on large keys"
```

---

### Mode 3 — Interactive (Multiline)

```bash
workctl log <project>
workctl log <project> --section <section>
```

Prompts for multiline input. Finish with `END` on its own line.

```bash
workctl log redis-load-test --section done
# Enter log message. Type END on a new line to finish:
Fixed the pipeline batch size issue.
Reduced p99 latency from 45ms to 12ms.
END
```

---

### Mode 4 — Editor Mode

```bash
workctl log <project> --edit
workctl log <project> --section <section> --edit
```

Opens the editor configured in `config.yaml` (default: VS Code). Save and close the file to submit the entry.

```bash
workctl log redis-load-test --section done --edit
```

---

### Mode 5 — From File

```bash
workctl log <project> --file <path>
workctl log <project> --section <section> --file <path>
```

Reads the log message from a plain text file.

```bash
workctl log redis-load-test --section notes --file ./today-notes.txt
```

---

### All Options

| Option | Short | Description |
|---|---|---|
| `--message` | `-m` | Log message text |
| `--section` | `-s` | Target section: `assigned`, `done`, `changes`, `commands`, `notes` |
| `--tag` | | Add one or more tags (repeatable) |
| `--file` | | Read message from a file |
| `--edit` | | Open external editor |

### Tagging

```bash
workctl log redis-load-test -m "Fixed pipeline timeout" --tag redis --tag performance
```

Tags are stored in the log metadata and can be searched with `workctl search --tag`.

---

---

# 📌 Task Commands

Tasks are stored in `notes/tasks.md` as structured Markdown. Each task has an ID, priority, status, multiline description, and optional subtasks.

## `workctl task add`

Adds a new task to the project's Open column.

### Usage

```bash
workctl task add <project>
workctl task add <project> --message "text"
workctl task add <project> --message "text" -p 1
workctl task add <project> --edit
workctl task add <project> --file task.txt
workctl task add <project> --message "text" --subtask "step one" --subtask "step two"
```

### Parameters

| Parameter | Description |
|---|---|
| `<project>` | Project name |

### Options

| Option | Short | Default | Description |
|---|---|---|---|
| `--message` | | | Task description (single or multiline with `\n`) |
| `--priority` | `-p` | `2` | Priority: `1` (High), `2` (Medium), `3` (Low) |
| `--tag` | | | Add tag(s) (repeatable) |
| `--subtask` | | | Add subtask(s) (repeatable) |
| `--edit` | | | Open external editor |
| `--file` | | | Read description from file |

### Input Modes

**Mode 1 — Inline message:**
```bash
workctl task add redis-load-test --message "Implement connection pooling" -p 1
```

**Mode 2 — Interactive (no flags):**
```bash
workctl task add redis-load-test
# Enter task description. Type END on a new line to finish:
Implement connection pooling for Redis client.
Supports max 20 connections with auto-eviction.
END
```

**Mode 3 — Editor:**
```bash
workctl task add redis-load-test --edit
```

**Mode 4 — From file:**
```bash
workctl task add redis-load-test --file ./task.txt
```

**Mode 5 — With subtasks (create task and subtasks in one command):**
```bash
workctl task add redis-load-test \
  --message "Build reporting dashboard" \
  -p 2 \
  --subtask "Design data model" \
  --subtask "Implement REST endpoint" \
  --subtask "Write unit tests"
```

### Output

```
✔ Task added successfully.
ℹ 3 subtask(s) added.
```

---

## `workctl task list`

Lists all tasks for a project, grouped by status (Open → In Progress → Done). Tasks are sorted by ID within each group. Subtask progress is shown inline for tasks that have subtasks.

### Usage

```bash
workctl task list <project>
```

### Example Output

```
Open

  #3 [ ] [P1] Implement connection pooling  [1/3 subtasks]
       ✓ Design schema (done)
       ○ Write implementation
       ○ Write tests

  #5 [ ] [P2] Add rate limiting

In Progress

  #2 [~] [P2] Build reporting dashboard  [2/4 subtasks]
       ✓ Design data model (done)
       ✓ REST endpoint (done)
       ○ Frontend chart
       ○ Unit tests

Done

  #1 [x] [P3] Update README
```

The status indicator in the task header:

| Symbol | Meaning |
|---|---|
| `[ ]` | Open |
| `[~]` | In Progress |
| `[x]` | Done |

---

## `workctl task show`

Displays full details for a single task: ID, priority, status, created date, full description, and all subtasks with their done/open state and 0-based index.

The subtask index shown here is what `task subtask done` and `task subtask delete` use.

### Usage

```bash
workctl task show <project> <task-id>
```

### Example Output

```
Task #3  [P1]  OPEN
Created: 2026-02-18

Description:

Implement connection pooling for Redis client.
Supports max 20 connections with auto-eviction.

Subtasks (1/3 done):
  0.  ✓  Design schema
  1.  ○  Write implementation
  2.  ○  Write tests
```

---

## `workctl task start`

Moves a task from Open to **In Progress**. Automatically logs a `started` event to `work-log.md`.

### Usage

```bash
workctl task start <project> <task-id>
```

### Example

```bash
workctl task start redis-load-test 3
# ✔ Task moved to In Progress.
```

---

## `workctl task done`

Marks a task as **Done**. Automatically logs a `completed` event to `work-log.md`. This event is used by `stats` and `insight` to calculate completion rate and productivity score.

### Usage

```bash
workctl task done <project> <task-id>
```

### Example

```bash
workctl task done redis-load-test 3
# ✔ Task marked as Done.
```

---

## `workctl task delete`

Permanently deletes a task from the project. The task ID is retired and will not be reused.

### Usage

```bash
workctl task delete <project> -id <task-id>
```

### Example

```bash
workctl task delete redis-load-test -id 7
# ✔ Task deleted successfully.
```

---

---

# ✅ Subtask Commands

Subtasks are children of a task — short, actionable steps that contribute to completing the parent task. They are stored directly in `tasks.md` as indented checkbox lines.

Each subtask has a **0-based index** within its parent task. Use `task subtask list` to see the current indexes before running `done` or `delete`.

---

## `workctl task subtask add`

Adds a new subtask to an existing task. The subtask starts in the open (not done) state.

### Usage

```bash
workctl task subtask add <project> <task-id> "<title>"
```

### Example

```bash
workctl task subtask add redis-load-test 3 "Write unit tests"
# ✔ Subtask added to Task #3 ("Implement connection pooling"): Write unit tests
```

---

## `workctl task subtask list`

Lists all subtasks for a task with their 0-based index, done/open state, and overall progress. Use this to find the correct index before running `done` or `delete`.

### Usage

```bash
workctl task subtask list <project> <task-id>
```

### Example Output

```
Subtasks for Task #3 – Implement connection pooling

Progress: 1/3 done

  0.  ✓  Design schema
  1.  ○  Write implementation
  2.  ○  Write tests
```

If no subtasks exist:
```
ℹ No subtasks yet. Add one with: task subtask add redis-load-test 3 "<title>"
```

---

## `workctl task subtask done`

Toggles a subtask between done and open. If it was open, it becomes done. If it was already done, it becomes open again.

### Usage

```bash
workctl task subtask done <project> <task-id> <subtask-index>
```

### Example

```bash
# Mark subtask 1 as done
workctl task subtask done redis-load-test 3 1
# ✔ Subtask 1 "Write implementation" marked done.

# Toggle it back to open
workctl task subtask done redis-load-test 3 1
# ✔ Subtask 1 "Write implementation" marked open.
```

If the index is out of range:
```
✗ Invalid index 5. Valid range: 0–2. Run 'task subtask list' to see indexes.
```

---

## `workctl task subtask delete`

Permanently removes a subtask from a task by its 0-based index.

**Note:** After deletion, the remaining subtasks re-index. Run `task subtask list` again to confirm new indexes if needed.

### Usage

```bash
workctl task subtask delete <project> <task-id> <subtask-index>
```

### Example

```bash
workctl task subtask delete redis-load-test 3 2
# ✔ Subtask 2 "Write tests" deleted from Task #3.
```

---

---

# 📊 Weekly Summary

## `workctl weekly`

Generates a structured weekly summary from `work-log.md`. Scans the log for date blocks falling within the requested range and prints entries grouped by section.

### Usage

```bash
# Current week (last 7 days)
workctl weekly <project>

# Custom date range
workctl weekly <project> --from <date> --to <date>

# Filter to a specific section
workctl weekly <project> --section done
workctl weekly <project> --from 2026-02-10 --to 2026-02-14 --section done
```

### Options

| Option | Description |
|---|---|
| `--from` | Start date in `yyyy-MM-dd` format |
| `--to` | End date in `yyyy-MM-dd` format |
| `--section` | Filter output to one section: `done`, `changes`, `commands` |

### Examples

```bash
workctl weekly redis-load-test
workctl weekly redis-load-test --from 2026-02-10 --to 2026-02-16
workctl weekly redis-load-test --section done
workctl weekly redis-load-test --from 2026-02-10 --to 2026-02-16 --section changes
```

---

---

# 🔍 Search

## `workctl search`

Searches across all project log files in the workspace for a keyword or tag. Results are printed with the matching project, date, and line.

### Usage

```bash
# Keyword search (scans log content)
workctl search <keyword>

# Tag search (scans log metadata tags)
workctl search <tag> --tag
```

### Options

| Option | Description |
|---|---|
| `--tag` | Treat the query as a tag name |

### Examples

```bash
workctl search "pipeline timeout"
workctl search redis --tag
workctl search performance --tag
```

---

---

# 📈 Stats

## `workctl stats`

Parses `TASK_EVENT` metadata embedded in `work-log.md` (written automatically whenever tasks are created, started, or completed) and prints a raw analytics report.

Use `workctl insight` for a more interpreted, human-readable version of these numbers.

### Usage

```bash
workctl stats <project>
```

### What It Computes

| Metric | How It's Calculated |
|---|---|
| Tasks created | Count of `created` events in log |
| Tasks completed | Count of `completed` events in log |
| Completed this week | `completed` events in last 7 days |
| Average completion time | Mean days from `created` to `completed` per task |
| Stagnant tasks | Open tasks with no status change in >7 days |
| Top tag | Most frequently used tag across all logged events |
| Daily activity | Number of events per calendar day (used for heatmap in GUI) |
| Productivity score | Formula: `(completionRate × 0.5) + (weeklyVelocity × 0.4) − (stagnantTasks × 5)`, clamped to 0–100 |

### Example

```bash
workctl stats redis-load-test
```

---

---

# 🧠 Insight

## `workctl insight`

Generates an intelligent project health report by combining live task data from `tasks.md` with event history from `work-log.md`. This is a richer, more interpreted version of `stats`.

### Usage

```bash
workctl insight <project>
```

### Example Output

```
📊 Project Insights: redis-load-test

--- Task Overview ---
Total Tasks:    12
Open:            4
In Progress:     2
Done:            6

--- Performance ---
Completed This Week: 2
Completion Rate:     50.00%
Productivity Score:  62.40 / 100
Stagnant Tasks (>7 days): 1

--- Intelligence ---
Most Used Tag: #redis
Active Days (Heatmap entries): 8
```

### Metrics Explained

**Completion Rate** — percentage of all tasks ever created that are now in Done status.

**Productivity Score** — a composite score out of 100:
- 50% weight on overall completion rate
- 40% weight on tasks completed in the last 7 days (weekly velocity)
- Penalty of 5 points per stagnant task (open/in-progress with no change in 7+ days)
- Clamped between 0 and 100

**Stagnant Tasks** — tasks that are not Done and whose last status change (created, started) was more than 7 days ago. These are flagged as at-risk.

**Active Days** — count of unique calendar days with any task event. Used to render the activity heatmap in the GUI.

---

---

# 🤖 AI Agent

## `workctl ask`

Sends a question or instruction to the Claude AI agent. The agent reads your project's `tasks.md` and `work-log.md`, reasons over them, and responds in natural language.

Requires `anthropicApiKey` to be set in `~/.workctl/config.yaml`.

**By default the agent is read-only** — it can answer questions but cannot modify any files. Pass `--act` to enable write mode.

---

### Mode 1 — Ask a question

```bash
workctl ask <project> "<question>"
```

```bash
workctl ask redis-load-test "What did I work on this week?"
workctl ask redis-load-test "Which P1 tasks are stagnant?"
workctl ask redis-load-test "Summarize the open tasks"
```

---

### Mode 2 — Give an instruction (write mode)

```bash
workctl ask <project> --act "<instruction>"
```

With `--act`, the agent can create tasks and move task status. Without it, the same instruction is answered but no files are changed.

```bash
workctl ask redis-load-test --act "Break down the logging feature into subtasks"
workctl ask redis-load-test --act "Mark task 5 as done"
workctl ask redis-load-test --act "Add a P1 task: Fix connection timeout on large payloads"
```

---

### Mode 3 — AI weekly summary

```bash
workctl ask <project> --weekly
workctl ask <project> --weekly --from <date> --to <date>
```

Generates a narrative weekly summary powered by the AI, richer than `workctl weekly`.

```bash
workctl ask redis-load-test --weekly
workctl ask redis-load-test --weekly --from 2026-02-10 --to 2026-02-16
```

---

### Mode 4 — AI project insights

```bash
workctl ask <project> --insight
```

Generates an intelligent project health report — a richer, interpreted version of `workctl insight`.

```bash
workctl ask redis-load-test --insight
```

---

### All Options

| Option | Description |
|---|---|
| `--act` | Enable write mode — agent can add tasks and change task status |
| `--weekly` | Generate AI-powered weekly summary instead of answering a question |
| `--insight` | Generate AI-powered project health insights |
| `--from` | Start date for `--weekly` mode (`yyyy-MM-dd`) |
| `--to` | End date for `--weekly` mode (`yyyy-MM-dd`) |

---

### Read-only vs Write mode

| Mode | What the agent can do |
|---|---|
| Default (no `--act`) | Read tasks and logs, answer questions, generate summaries |
| `--act` | Everything above + add tasks, move task status |

---

---

# ⚙ Config Commands

Configuration is stored at `~/.workctl/config.yaml`. The following keys are supported:

| Key | Description | Default |
|---|---|---|
| `workspace` | Path to your projects workspace | set on `init` |
| `editor` | Editor command for `--edit` mode | `code` |
| `dateFormat` | Date format for log headers | `yyyy-MM-dd` |

---

## `workctl config set`

Sets a single configuration key to a new value and saves it to `config.yaml`.

### Usage

```bash
workctl config set <key> <value>
```

### Supported Keys

```bash
workctl config set editor code
workctl config set editor notepad
workctl config set editor vim
workctl config set editor nano

workctl config set workspace "C:\Users\Ajay\Work"
workctl config set workspace "/home/ajay/work"

workctl config set dateFormat "yyyy-MM-dd"
workctl config set anthropicApiKey sk-ant-YOUR_KEY
```

---

## `workctl config get`

Reads and prints a single configuration value.

### Usage

```bash
workctl config get <key>
```

### Examples

```bash
workctl config get editor
# editor = code

workctl config get workspace
# workspace = C:/Users/Ajay/Work
```

---

## `workctl config show`

Prints all configuration values at once.

### Usage

```bash
workctl config show
```

### Example Output

```
Current workctl configuration:
editor     = code
workspace  = C:/Users/Ajay/Work
dateFormat = yyyy-MM-dd
```

---

---

# 📦 Installation & Runtime

### Build

```bash
./gradlew build
./gradlew clean :cli:installDist
```

### Installed Binary Location

```
cli/build/install/workctl/bin/
```

| Platform | Binary |
|---|---|
| Windows | `workctl.bat` |
| Linux / macOS | `workctl` |

Add this directory to `PATH` to use `workctl` globally from any terminal.

### Run Without Installing

```bash
./gradlew :cli:run --args="task list myproject"
```

---

---

# 🗂 Storage Format

All data is stored as plain Markdown files — no database required.

| File | Location | Contains |
|---|---|---|
| `config.yaml` | `~/.workctl/config.yaml` | Global CLI config |
| `tasks.md` | `<workspace>/01_Projects/<project>/notes/tasks.md` | All tasks + subtasks |
| `work-log.md` | `<workspace>/01_Projects/<project>/notes/work-log.md` | Daily logs + task events |

### tasks.md Format

```markdown
# Tasks – redis-load-test

<!-- NEXT_ID: 6 -->

## Open

3. [ ] (P1) Implement connection pooling  <!-- created=2026-02-18 -->
    Supports max 20 connections with auto-eviction.
    - [ ] Design schema
    - [x] Write implementation
    - [ ] Write tests

## In Progress

## Done

1. [x] (P3) Update README  <!-- created=2026-02-15 -->
```

Because storage is plain Markdown, all project data is fully **Git-versionable** and human-readable without workctl installed.

---

---

*Last updated: 2026-02-21*
*Keep this file in sync with any CLI command additions or changes.*