# CLI API Documentation -- workctl

This document describes all currently implemented CLI commands in
**workctl**, including usage, examples, and behavior details.

------------------------------------------------------------------------

# 🏁 Initialization

## `workctl init`

### Description

Initializes workspace and configuration.

### Usage

``` bash
workctl init --workspace <path>
```

### Example

``` bash
workctl init --workspace "C:\Users\Ajay.Gupt\Downloads\Work"
```

### What It Does

-   Creates `.workctl/config.yaml`
-   Stores workspace path
-   Prepares folder structure:
    -   `01_Projects/`
-   Creates initial config if missing

------------------------------------------------------------------------

# 📁 Project Commands

## `workctl project create`

### Description

Creates a new project directory inside workspace.

### Usage

``` bash
workctl project create <project-name> --description "text"
```

### Example

``` bash
workctl project create redis-load-test --description "Latency testing"
```

### What It Creates

    workspace/
    └── 01_Projects/
        └── redis-load-test/
            ├── notes/
            │   ├── work-log.md
            │   └── tasks.md

------------------------------------------------------------------------

## `workctl project list`

### Description

Lists all projects inside workspace.

### Usage

``` bash
workctl project list
```

------------------------------------------------------------------------

# 📝 Log Commands

## `workctl log <project>`

### Description

Creates today's structured log block if missing.

### Usage

``` bash
workctl log <project>
```

------------------------------------------------------------------------

## Add Quick Message

``` bash
workctl log <project> --message "text"
```

Defaults to **Done** section.

------------------------------------------------------------------------

## Section-specific Entry

``` bash
workctl log <project> --section commands --message "cp file.txt ../config/"
```

Supported sections:

-   assigned
-   done
-   changes
-   commands
-   notes

------------------------------------------------------------------------

## Multiline Interactive Log

### Defaults to **Done** section.

``` bash
workctl log <project>
```

Then type message and finish with:

    END

### Section-specific Entry

``` bash
workctl log work-control --section <section>
```

Then type message and finish with:

    END

Supported sections:

-   assigned
-   done
-   changes
-   commands
-   notes

------------------------------------------------------------------------

## Editor Mode

``` bash
workctl log <project> --section done --edit
```

Opens configured editor from `config.yaml`.

- Write logs then save and close the file.
- Supports multiline logs

------------------------------------------------------------------------

# ⚙ Config Commands

## `workctl config set editor`

### Description

Sets default editor used for `--edit` mode.

### Usage

``` bash
workctl config set editor notepad
workctl config set editor code
workctl config set editor vim
```

## `workctl config set workspace`

### Description

Sets default workspace used for all operations

### Usage

``` bash
workctl config set workspace C:\Work
```

## `workctl config show`

### Description

List down all configurations from `.workctl/config.yaml`

### Usage

``` bash
workctl config show
```

------------------------------------------------------------------------

# 📌 Task Commands

## `workctl task add`

### Description

Adds new task to project.

### Basic

``` bash
workctl task add <project> "Task description"
```

### Priority Option

```bash
workctl task add work-control "Fix memory leak" -p 1
```

or

```bash
workctl task add work-control "Refactor logs"
```

(Default priority = 2)

### Interactive

``` bash
workctl task add <project>
```

Type multiline description and finish with `END`.

### Editor Mode

``` bash
workctl task add <project> --edit
```

### From File

``` bash
workctl task add <project> --file task.txt
```

------------------------------------------------------------------------

## `workctl task list`

### Description

Lists all tasks grouped by status.

``` bash
workctl task list <project>
```

Statuses:

-   Open
-   In Progress
-   Done

------------------------------------------------------------------------

## `workctl task start`

### Description

Moves task to **In Progress**.

``` bash
workctl task start <project> <task-id>
```

------------------------------------------------------------------------

## `workctl task done`

### Description

Marks task as **Done**.

``` bash
workctl task done <project> <task-id>
```

------------------------------------------------------------------------

## `workctl task show`

### Description

Displays full multiline task description.

``` bash
workctl task show <project> <task-id>
```

## `workctl task delete`

### Description

Delete a task with given ID.

``` bash
workctl task delete work-control -id 42
```

------------------------------------------------------------------------

# 📊 Weekly Summary

## `workctl weekly`

### Description

Generates weekly summary.

### Default (current week)

``` bash
workctl weekly <project>
```

### Custom Range

``` bash
workctl weekly <project> --from 2026-02-11 --to 2026-02-14
```

------------------------------------------------------------------------

# 🔍 Search

## `workctl search`

### Description

Search across logs and tasks.

### Keyword search

``` bash
workctl search redis
```

### Tag search

``` bash
workctl search dpdk --tag
```

------------------------------------------------------------------------

# 🔍 Stats

## `workctl insight <project>`

### Description

Give important stats about the given project.


------------------------------------------------------------------------

# 🧠 Intelligent Features (Implemented)

-   Multiline task support
-   Structured task sections (Open / In Progress / Done)
-   Persistent incremental task IDs
-   Editor integration
-   Interactive mode for logs & tasks
-   Date-aware structured logging
-   Section normalization
-   Weekly custom date range
-   Tag support in logs
-   Search functionality

------------------------------------------------------------------------

# 📦 Runtime Notes

Installed CLI location:

    cli/build/install/workctl/bin/

Windows uses:

    workctl.bat

Linux/Mac uses:

    workctl

Add this folder to PATH to use globally.

------------------------------------------------------------------------

# 🚀 Future Enhancements Planned

-   Task → Log auto-linking (intelligent metadata)
-   Tag analytics
-   Dashboard
-   Stats engine
-   Git integration
-   Native binary build (GraalVM)

------------------------------------------------------------------------

End of CLI API Documentation.
