# Workflows — User Guide

> **[← README](../README.md)** | [CLI Reference](cli-api.md) | [Setup](SETUP.md) | [Distribution Guide](DISTRIBUTION_GUIDE.md) | [Full Docs](workctl-docs.md)

Workflows let you define **reusable step-by-step procedures** (templates) and track named **executions** of those procedures (runs). Each run independently tracks step progress, observations, expected vs. actual results, and notes — making it easy to repeat common procedures across projects while preserving a full history of every execution.

---

## Concepts

| Term | Description |
|------|-------------|
| **Template** | A reusable blueprint of steps. Define it once, use it many times. Stored in `06_Workflows/templates/`. |
| **Run** | A named execution of a template (or a standalone blank run). Tracks progress, notes, and results for each step independently. |
| **Step** | One action inside a template or run. Has a title, description, expected result, optional code blocks, and sub-steps. |
| **Step Status** | `TODO` (not started), `DONE` (completed), `SKIPPED` (not applicable). |
| **Run Status** | `IN_PROGRESS`, `COMPLETED` (all non-skipped steps done), `ABANDONED`. |

---

## Storage Layout

```
<workspace>/
├── 06_Workflows/
│   ├── templates/          ← Reusable procedure blueprints
│   │   └── explore-existing-codebase.md
│   └── runs/               ← Global runs (not project-scoped)
│       └── 2026-02-27-release-checklist.md
│
└── 01_Projects/
    └── <project>/
        └── workflows/      ← Project-scoped runs
            └── 2026-02-27-explore-workctl.md
```

---

## CLI Reference (`workctl flow`)

### Template Commands

#### Create a template
```bash
workctl flow template new "<name>" [--desc "<description>"] [--tags "tag1,tag2"]
```
```bash
# Example
workctl flow template new "Explore Existing Codebase" \
  --desc "Procedure for understanding any new codebase" \
  --tags "dev,onboarding"
```

#### Add steps to a template
```bash
workctl flow template step-add <template-id> "<step title>" \
  [--desc "<guidance>"] [--expected "<expected result>"]
```
```bash
# Example — build up the procedure step by step
workctl flow template step-add 83669d7b "Show recent git commits" \
  --expected "List of commits with messages and dates"

workctl flow template step-add 83669d7b "Find all source files by module" \
  --expected "All .java files organized by module"

workctl flow template step-add 83669d7b "Look for test files" \
  --desc "Check test coverage across all modules" \
  --expected "Returns 0 (no tests) or list of test classes"

workctl flow template step-add 83669d7b "Read key docs and config files" \
  --expected "Understanding of CLAUDE.md, build.gradle, README"

workctl flow template step-add 83669d7b "Count lines in core services" \
  --expected "Total LOC across service layer"
```

#### List all templates
```bash
workctl flow template list
```
Output:
```
┌─ Workflow Templates ────────────────────────┐
ID (short)    Name                              Steps   Tags               Created
────────────────────────────────────────────────────────────────────────────────────
83669d7b      Explore Existing Codebase         5       dev,onboarding     2026-02-27
```

#### Show a template's steps
```bash
workctl flow template show <template-id>
```
```bash
workctl flow template show 83669d7b
```
Output:
```
┌─ Explore Existing Codebase ─────────────────┐
  A procedure for understanding any new codebase
  ID: 83669d7b-cc89-4a57-8f82-9fe6c5114067
  Steps: 5   Tags: dev,onboarding
────────────────────────────────────────────────
  1. Show recent git commits
     Expected: List of commits with messages and dates
  2. Find all source files by module
     Expected: All .java files organized by module
  3. Look for test files
     Check test coverage across all modules
     Expected: Returns 0 or list of test classes
  ...
```

#### Delete a template
```bash
workctl flow template delete <template-id>
```

---

### Run Commands

#### Create a run
```bash
# From a template (steps are copied automatically)
workctl flow new "<run name>" --template <template-id> [--project <project-name>]

# Blank run (no template)
workctl flow new "<run name>" [--project <project-name>]
```
```bash
# Scoped to a project
workctl flow new "Explore workctl — 2026-02-27" --template 83669d7b --project workctl

# Global run (not scoped to a project)
workctl flow new "Release v1.3.0 checklist" --template a1b2c3d4
```

#### List runs
```bash
workctl flow list                        # Global runs
workctl flow list --project <name>       # Project-scoped runs
workctl flow list --all                  # All runs (global + all projects)
```

#### Show a run
```bash
workctl flow show <run-id>
```
Output:
```
┌─ Explore workctl — 2026-02-27 ──────────────┐
  Status:  IN PROGRESS   Project: workctl
  Progress: [███░░░░░░░] 1/4
────────────────────────────────────────────────
  ✓ 1. Show recent git commits
     28 commits total, v1.0.0 to v1.2.0 in 2 weeks.
     Expected: List of commits with messages

  ○ 2. Find all source files by module
     Expected: All .java files organized by module

  ○ 3. Look for test files

  ○ 4. Read key docs and config files
```

#### Delete a run
```bash
workctl flow delete <run-id>
```

---

### Step Commands

#### Mark a step done
```bash
workctl flow step done <run-id> <step-number>
```
```bash
workctl flow step done bdc15533 1
# ✓ Step 1 marked DONE
# Progress: [███░░░░░░░] 1/4
```

#### Skip a step
```bash
workctl flow step skip <run-id> <step-number>
```
```bash
workctl flow step skip bdc15533 3
# ✓ Step 3 marked SKIPPED
```

#### Add a note / observation to a step
```bash
# Inline note
workctl flow step note <run-id> <step-number> --message "<text>"

# Open external editor (configured via 'workctl config set editor code')
workctl flow step note <run-id> <step-number> --edit
```
```bash
workctl flow step note bdc15533 1 --message "28 commits total, v1.0 to v1.2 in 2 weeks. No test files found."
```

---

### ID Shorthand

All commands accept either the full UUID or a short prefix (first 8 chars):

```bash
# Full UUID
workctl flow show bdc15533-aed2-4a2f-bf6d-f825e3b8ef71

# Short prefix (8 chars is enough)
workctl flow show bdc15533
```

---

### Typical CLI Workflow

```bash
# 1. Define a reusable template (once)
workctl flow template new "Explore Existing Codebase" --tags "dev"
workctl flow template step-add <id> "Show recent git commits" --expected "Commit list"
workctl flow template step-add <id> "Find source files by module" --expected "File list"
workctl flow template step-add <id> "Check test coverage" --expected "Test classes or 0"
workctl flow template step-add <id> "Read key docs" --expected "Understanding of structure"

# 2. Start a run for a new project
workctl flow new "Explore new-project — 2026-03-01" --template <id> --project new-project

# 3. Work through the steps
workctl flow step done <run-id> 1
workctl flow step note <run-id> 1 --message "15 commits, active since Jan. v0.3.0."
workctl flow step done <run-id> 2
workctl flow step note <run-id> 2 --message "3 modules: api, core, web. ~12k LOC total."
workctl flow step skip <run-id> 3        # no tests in this project
workctl flow step done <run-id> 4
workctl flow step note <run-id> 4 --message "CLAUDE.md found. Uses Spring Boot 3.2."

# 4. View the completed run
workctl flow show <run-id>
```

---

## GUI Guide

### Opening the Workflows Tab

Launch the GUI and click the **Workflows** tab in the tab bar at the top.

---

### Left Panel — Run List

The left panel (30% width) contains:

| Element | Purpose |
|---------|---------|
| **Scope** dropdown | Filter runs: `All`, `Global`, or a specific project name |
| **Template** dropdown | Filter runs by which template they were created from |
| **+ New Run** button | Create a new run (opens dialog) |
| **Manage Templates** button | View, create, add steps to, and delete templates |
| **Run list** | All matching runs, showing name, project, status badge, and step progress |

Click any run in the list to load its detail in the right panel.

---

### Right Panel — Run Detail

When a run is selected:

- **Header**: Run name, status badge (IN PROGRESS / COMPLETED / ABANDONED), project, creation date, step progress count
- **Step list**: Each step shown as a card with:
  - **Step number badge** (circular)
  - **Status symbol**: `✓` Done · `–` Skipped · `○` Todo
  - **Step title**
  - **Action buttons**: `✓ Done`, `→ Skip`, `+ Note`
  - **Expandable content**: notes, expected result, actual result (shown below the header row)
- **Delete button** (top-right): removes the run

---

### Creating a Template (GUI)

1. Click **Manage Templates** in the left panel
2. Click **+ New Template**
3. Fill in: Name (required), Description (optional), Tags (comma-separated)
4. Click **Create**
5. Back in the templates dialog, click **+ Step** on your template to add steps one by one

---

### Creating a Run (GUI)

1. Click **+ New Run**
2. Fill in:
   - **Run name** — descriptive name for this execution (e.g. `Explore workctl — 2026-02-27`)
   - **From template** — select a template to copy its steps, or leave as `(none — blank run)`
   - **Project scope** — select a project, or leave as `(global)` for workspace-wide runs
3. Click **Create**
4. The new run appears in the list and is automatically selected

---

### Working Through Steps (GUI)

For each step in the right panel:

| Action | How |
|--------|-----|
| Mark done | Click **✓ Done** button on the step row |
| Skip step | Click **→ Skip** button on the step row |
| Add observation | Click **+ Note** → type your observation in the dialog → OK |
| View details | Notes, expected, and actual results are shown below the step header |

When all non-skipped steps are marked done, the run automatically transitions to **COMPLETED**.

---

### Filtering Runs (GUI)

- Use the **Scope** dropdown to show runs for a specific project
- Use the **Template** dropdown to show only runs created from a particular template
- When you select a project in the main sidebar, the Scope filter automatically switches to that project

---

## Markdown File Format

All workflow data is stored as plain Markdown — readable, editable, and Git-friendly.

### Template file (`06_Workflows/templates/explore-existing-codebase.md`)
```markdown
# Explore Existing Codebase

<!-- WORKFLOW_TEMPLATE: id=83669d7b-... created=2026-02-27 tags=dev,onboarding -->

A procedure for understanding any new codebase.

## Step 1: Show recent git commits

Check the recent commit history to understand development activity.

**Expected:** List of commits with messages and dates

## Step 2: Find all source files by module

**Expected:** All .java files organized by module
```

### Run file (`01_Projects/workctl/workflows/2026-02-27-explore-workctl.md`)
```markdown
# Explore workctl — 2026-02-27

<!-- WORKFLOW_RUN: id=bdc15533-... templateId=83669d7b-... project=workctl status=COMPLETED created=2026-02-27 completed=2026-02-27 -->

## Step 1: Show recent git commits
<!-- STEP: id=d772e7d9-... status=DONE -->

28 commits total, v1.0.0 to v1.2.0 in 2 weeks. Active development.

**Expected:** List of commits with messages and dates

## Step 2: Find all source files by module
<!-- STEP: id=5fdad4fe-... status=DONE -->

77 Java files across 5 modules. Core has 3,475 lines in service layer.

**Expected:** All .java files organized by module

## Step 3: Look for test files
<!-- STEP: id=f141fb09-... status=SKIPPED -->

**Expected:** Returns 0 (no tests) or list of test classes
```

---

## Tips

- **Short IDs**: You only need the first 8 characters of any UUID in CLI commands.
- **Reuse templates**: The same template (e.g. `Release Checklist`, `Onboarding`, `Debug Production Issue`) can be run for every project or sprint.
- **Notes accumulate**: Calling `workctl flow step note` multiple times appends to the step's notes — useful for logging observations as you work.
- **Auto-completion**: Runs auto-transition to `COMPLETED` when all non-skipped steps are done.
- **Git-friendly**: Commit your `06_Workflows/` and `<project>/workflows/` directories to version-control for a full audit trail of how you worked.
- **Edit directly**: Since files are plain Markdown, you can edit them in any editor. The GUI and CLI will pick up changes on next load.
