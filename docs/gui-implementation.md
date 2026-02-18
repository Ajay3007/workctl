# GUI EVOLUTION

## 🏗 GUI Architecture

Inside gui module:

```bash
gui/
├── WorkctlApp.java
├── controller/
│    ├── MainController.java
│    ├── ProjectController.java
│    ├── TaskController.java
│    ├── LogController.java
│    └── StatsController.java
├── view/
│    ├── main.fxml
│    ├── project.fxml
│    ├── tasks.fxml
│    ├── logs.fxml
│    └── stats.fxml
```

We use:

- FXML for layout
- Controllers for logic
- Services from core

------------------------------------------------------------------------

## 🎯 Minimal Viable GUI (Phase 1)

We implement:

1. Project Explorer
2. Task List (grouped)
3. Add Task dialog
4. Log Viewer (read-only)
5. Stats Viewer
- No editing yet.
- No drag-and-drop yet.

We will build:

```bash
BorderPane
├── Left   → Project Explorer
└── Center → TabPane
    ├── Tasks
    ├── Logs
    └── Stats
```

And all business logic stays in `core`.


------------------------------------------------------------------------

## 🔥 What We Build First?

We must not build everything at once.

Correct order:

### 1. Project Explorer + Load Project

Load real projects from:

```bash
workspace/01_Projects/
```

Display them in:

```java
ListView<Project>
```

When clicked:
- → Load tasks
- → Load logs
- → Update stats


### 2. Task Board View

For selected project:

Load from TaskService
Populate:

- openColumn

- inProgressColumn

- doneColumn

Each task becomes a small card:

```bash
[#14] Implement auto linking
```

Later:

- Drag & drop between columns

- Click → open detail pane

### 3. Add Task Dialog



### 4. Log Viewer

Load:

```bash
notes/work-log.md
```

- Show current date block

- Add live refresh on selection

Option A (simple now):

- Show raw markdown in TextArea

Option B (later):

- Live Markdown preview (WebView + HTML rendering)

### 5. Stats Panel

Start simple:

- Total tasks

- Open count

- In progress count

- Done count

When you select a project:

Stats tab should show:

```bash
Total Tasks: 15
Open: 6
In Progress: 2
Done: 7
Completion: 46%
[██████░░░░░]
```

Later:

- Completion trend

- Tag analytics

- Burn-down chart

### 6. Markdown preview

### 7. Log editor

------------------------------------------------------------------------

## 🧠 INTELLIGENCE LAYER – Phase 1

### 1️⃣ Auto Smart Summary Generator

Command:

```bash
workctl insight <project>
```

GUI:

A new "Insights" tab.

It should generate:

- Tasks completed this week

- Tasks still open

- Most active tag

- Productivity score

- Completion velocity

This converts raw logs → meaning.

### 3️⃣ Smart Tag Detection

When adding log or task:

If message contains:

- redis → auto-add #redis

- dpdk → auto-add #dpdk

- release → auto-add #release

No manual tagging required.

This is subtle but powerful intelligence.

### 4️⃣ Stale Task Detector

Highlight in GUI:

- Tasks open > 5 days

- Tasks in progress > 3 days

Color:

- Orange = warning

- Red = stale

This reduces invisible drift.

### 5️⃣ Pattern Recognition

Later stage:

- Most productive day of week

- Average completion time

- Task churn rate

- Completion consistency graph

Now workctl becomes a behavioral analyzer.

## 🎯 Final UX Behavior

Now you have:

**Interaction	-> Behavior**

Single Click	-> Highlight

Double Click	-> Edit

Enter	->Save

Shift + Enter	-> New line

Esc	-> Cancel

Click outside	-> Nothing

Right click	-> Move options

ℹ Button	-> Detail modal

Drag & Drop -> Move across columns

