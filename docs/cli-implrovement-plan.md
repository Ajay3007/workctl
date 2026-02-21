```bash
╭─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
│ Plan to implement                                                                                                                   │
│                                                                                                                                     │
│ Plan: workctl CLI Visual & Interactive Modernization                                                                                │
│                                                                                                                                     │
│ Context                                                                                                                             │
│                                                                                                                                     │
│ The workctl CLI currently has basic ANSI color output (5 colors, 4 methods in ConsolePrinter) and raw                               │
│ System.out.println / Scanner usage throughout. The goal is to make it visually polished and                                         │
│ interactive — colored badges, box-drawing headers, progress bars, spinners for AI calls, JLine-backed                               │
│ prompts replacing Scanner, a terminal-width table for task list, and an interactive REPL for the ask                                │
│ command.                                                                                                                            │
│                                                                                                                                     │
│ ---                                                                                                                                 │
│ Phase 1 — Visual Polish (no new dependencies)                                                                                       │
│                                                                                                                                     │
│ 1. Expand ConsolePrinter.java                                                                                                       │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/util/ConsolePrinter.java                                                                    │
│                                                                                                                                     │
│ Add new ANSI constants:                                                                                                             │
│ CYAN = "\u001B[36m"                                                                                                                 │
│ BOLD = "\u001B[1m"                                                                                                                  │
│ DIM  = "\u001B[2m"                                                                                                                  │
│                                                                                                                                     │
│ Add new static methods:                                                                                                             │
│ - header(String title) — box-drawing header: ┌─ Title ────────────────────────┐                                                     │
│ - separator() — prints ─.repeat(44) in DIM                                                                                          │
│ - banner() — styled 3-line app banner with version + tagline                                                                        │
│ - priorityBadge(int p) — P1=RED+BOLD, P2=YELLOW, P3=DIM, returns colored [P1] string                                                │
│ - statusBadge(TaskStatus s) — OPEN=BLUE, IN_PROGRESS=CYAN, DONE=GREEN+DIM, returns string                                           │
│ - progressBar(int done, int total, int width) — [████████░░░░] 4/10                                                                 │
│ - table(String[] headers, List<String[]> rows, int[] widths) — aligned column table                                                 │
│ - padRight(String s, int width) — ANSI-aware padding helper (strips escape codes for length calc)                                   │
│                                                                                                                                     │
│ 2. WorkctlCLI.java — styled no-arg banner                                                                                           │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/WorkctlCLI.java                                                                             │
│                                                                                                                                     │
│ Replace System.out.println("Use 'workctl --help'...") with ConsolePrinter.banner() + ConsolePrinter.info(...).                      │
│                                                                                                                                     │
│ 3. TaskCommand.java — colored badges, styled show + subtask list                                                                    │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/TaskCommand.java                                                                   │
│                                                                                                                                     │
│ - ListTasks.run(): Stop calling taskService.listTasks(). Instead call taskService.getTasks(projectName), group by status, print     │
│ each task with priorityBadge + statusBadge. Add printTaskRow(Task) helper.                                                          │
│ - Show.run(): Replace 10 raw System.out.println calls with separator(), header(), colored badges, progressBar() for subtasks.       │
│ - SubtaskList.run(): Replace "Progress: X/Y done" with progressBar(). Color ✓ green, ○ dim.                                         │
│ - Add.resolveDescription() interactive fallback: Replace System.out.println with ConsolePrinter.info() (full replacement with JLine │
│  in Phase 2).                                                                                                                       │
│                                                                                                                                     │
│ 4. ProjectCommand.java — styled list + warning on delete                                                                            │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/ProjectCommand.java                                                                │
│                                                                                                                                     │
│ - ListCommand: Replace "  - name" bullets with header() + "  ▸ name" in CYAN.                                                       │
│ - DeleteCommand: Replace raw System.out.print with styled warning() (JLine prompt in Phase 2).                                      │
│                                                                                                                                     │
│ 5. InsightCommand.java — box headers + progress bars                                                                                │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/InsightCommand.java                                                                │
│                                                                                                                                     │
│ Replace all --- Section --- headers with header(). Add progressBar() for completion rate and productivity score. Color stagnant     │
│ task count red if > 0, green if 0.                                                                                                  │
│                                                                                                                                     │
│ 6. AskCommand.java — styled output                                                                                                  │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/AskCommand.java                                                                    │
│                                                                                                                                     │
│ Replace raw System.out.println banner with ConsolePrinter.header(). Color Agent: label in CYAN. Use ConsolePrinter.info() for mode  │
│ labels.                                                                                                                             │
│                                                                                                                                     │
│ 7. LogCommand.java + InitCommand.java — use ConsolePrinter consistently                                                             │
│                                                                                                                                     │
│ Replace remaining raw System.out.println success/error strings with ConsolePrinter.success/error/warning.                           │
│                                                                                                                                     │
│ Phase 1 Verification                                                                                                                │
│                                                                                                                                     │
│ ./gradlew :cli:installDist                                                                                                          │
│ ./cli/build/install/cli/bin/cli                        # banner                                                                     │
│ ./cli/build/install/cli/bin/cli task list <project>    # colored badges, grouped                                                    │
│ ./cli/build/install/cli/bin/cli task show <project> 1  # box headers, progress bar                                                  │
│ ./cli/build/install/cli/bin/cli insight <project>      # progress bars, colored sections                                            │
│ ./cli/build/install/cli/bin/cli project list           # ▸ bullets                                                                  │
│                                                                                                                                     │
│ ---                                                                                                                                 │
│ Phase 2 — Interactive Prompts + Spinners (add JLine 3)                                                                              │
│                                                                                                                                     │
│ 1. Add JLine 3 dependency                                                                                                           │
│                                                                                                                                     │
│ File: cli/build.gradle                                                                                                              │
│                                                                                                                                     │
│ implementation 'org.jline:jline:3.27.1'                                                                                             │
│                                                                                                                                     │
│ No changes needed to packageNative jpackage task — JLine is a pure-Java JAR with no native deps. Its dumb terminal fallback         │
│ activates automatically in piped/CI environments.                                                                                   │
│                                                                                                                                     │
│ 2. New CliSpinner.java                                                                                                              │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/util/CliSpinner.java (CREATE)                                                               │
│                                                                                                                                     │
│ Background-thread spinner using \r to overwrite the same line. Uses braille frames ⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏. API:                                 │
│ CliSpinner spinner = new CliSpinner("Thinking");                                                                                    │
│ spinner.start();                                                                                                                    │
│ String result = someBlockingCall();                                                                                                 │
│ spinner.stop();   // clears the spinner line                                                                                        │
│ Guard with if (System.console() == null) return; in start() to skip spinner when output is piped.                                   │
│                                                                                                                                     │
│ 3. New CliPrompt.java                                                                                                               │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/util/CliPrompt.java (CREATE)                                                                │
│                                                                                                                                     │
│ Wraps JLine 3's LineReader. All methods use TerminalBuilder.builder().dumb(true).build() for graceful TTY fallback. Methods:        │
│ - confirm(String question) → styled [y/N] prompt, returns boolean                                                                   │
│ - prompt(String label) → single styled line input                                                                                   │
│ - promptMultiline(String label) → multi-line with >  prefix, END to finish                                                          │
│ - select(String label, String[] options, int defaultIndex) → numbered list selection                                                │
│                                                                                                                                     │
│ 4. AskCommand.java — spinner + REPL mode                                                                                            │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/AskCommand.java                                                                    │
│                                                                                                                                     │
│ - Wrap all three agentService.*() calls with CliSpinner.                                                                            │
│ - When question is blank (currently shows a help message): enter a persistent REPL loop using JLine LineReader with history saved   │
│ to ~/.workctl/ask_history. Prompt: You › . Each input calls agentService.ask() with a spinner. Ctrl+C skips current line, Ctrl+D    │
│ exits.                                                                                                                              │
│                                                                                                                                     │
│ 5. InsightCommand.java — spinner                                                                                                    │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/InsightCommand.java                                                                │
│                                                                                                                                     │
│ Wrap statsService.generateInsights() with CliSpinner("Analyzing project").                                                          │
│                                                                                                                                     │
│ 6. TaskCommand.java + LogCommand.java + ProjectCommand.java — replace Scanner                                                       │
│                                                                                                                                     │
│ - TaskCommand.Add.resolveDescription(): replace Scanner with CliPrompt.promptMultiline()                                            │
│ - LogCommand.resolveMessage() interactive mode: replace Scanner with CliPrompt.promptMultiline()                                    │
│ - ProjectCommand.DeleteCommand: replace Scanner + raw print with CliPrompt.confirm()                                                │
│                                                                                                                                     │
│ Remove import java.util.Scanner from each modified file.                                                                            │
│                                                                                                                                     │
│ Phase 2 Verification                                                                                                                │
│                                                                                                                                     │
│ ./gradlew :cli:installDist                                                                                                          │
│ ./cli/build/install/cli/bin/cli ask <project> "what tasks are open?"  # spinner then result                                         │
│ ./cli/build/install/cli/bin/cli ask <project>                          # REPL mode                                                  │
│ ./cli/build/install/cli/bin/cli insight <project>                      # spinner                                                    │
│ ./cli/build/install/cli/bin/cli log <project>                          # styled "> " multiline prompt                               │
│ ./cli/build/install/cli/bin/cli project delete nonexistent             # styled confirm prompt                                      │
│ ./cli/build/install/cli/bin/cli task list <project> | cat             # no spinner corruption when piped                            │
│ ./gradlew :cli:packageNative && ./build/release/workctl/workctl ask <project> "test"                                                │
│                                                                                                                                     │
│ ---                                                                                                                                 │
│ Phase 3 — Rich Layout + Activity Chart (builds on Phase 2)                                                                          │
│                                                                                                                                     │
│ 1. Terminal-width table for task list                                                                                               │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/TaskCommand.java                                                                   │
│                                                                                                                                     │
│ Use TerminalBuilder.builder().dumb(true).build().getWidth() (fallback 100) to compute dynamic title column width. Columns: ID(4) |  │
│ STATUS(10) | PRI(4) | TITLE(dynamic) | SUBTASKS(10). Truncate titles with ... if too long. Group by status with a separator row     │
│ between groups.                                                                                                                     │
│                                                                                                                                     │
│ 2. Activity bar chart in insight                                                                                                    │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/InsightCommand.java                                                                │
│                                                                                                                                     │
│ Add a 14-day activity chart using insights.getDailyActivity() (already returns Map<LocalDate, Integer>). Normalize bar lengths to   │
│ 20-char width. Format: MM-DD  ████████░░░░░░░░░░░░  3.                                                                              │
│                                                                                                                                     │
│ 3. Remove listTasks() + formatTaskForCli() from TaskService                                                                         │
│                                                                                                                                     │
│ File: core/src/main/java/com/workctl/core/service/TaskService.java                                                                  │
│                                                                                                                                     │
│ Before removing, verify no callers remain:                                                                                          │
│ grep -r "listTasks\|formatTaskForCli" . --include="*.java"                                                                          │
│ Remove both methods if only the old TaskCommand.ListTasks was the caller (already replaced in Phase 1). The getTasks(String         │
│ projectName) method stays — it is the correct data-only API used by the new CLI and the GUI.                                        │
│                                                                                                                                     │
│ 4. AskCommand.java — ensure history directory exists                                                                                │
│                                                                                                                                     │
│ File: cli/src/main/java/com/workctl/cli/commands/AskCommand.java                                                                    │
│                                                                                                                                     │
│ Add Files.createDirectories(historyFile.getParent()) before building the LineReader in runReplMode() to handle users who haven't    │
│ run workctl init yet.                                                                                                               │
│                                                                                                                                     │
│ Phase 3 Verification                                                                                                                │
│                                                                                                                                     │
│ ./gradlew :cli:installDist                                                                                                          │
│ # Resize terminal, run task list — titles truncate, columns align correctly                                                         │
│ ./cli/build/install/cli/bin/cli task list <project>                                                                                 │
│ # Insight shows 14-day activity chart                                                                                               │
│ ./cli/build/install/cli/bin/cli insight <project>                                                                                   │
│ # REPL history persists across sessions (press UP in second session)                                                                │
│ ./cli/build/install/cli/bin/cli ask <project>                                                                                       │
│ # Piped output still works                                                                                                          │
│ ./cli/build/install/cli/bin/cli task list <project> | grep P1                                                                       │
│                                                                                                                                     │
│ ---                                                                                                                                 │
│ Files Summary                                                                                                                       │
│                                                                                                                                     │
│ ┌──────────────────────────────────┬─────────┬───────────────────────────────────────────────────────────────────┐                  │
│ │               File               │  Phase  │                              Action                               │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/util/ConsolePrinter.java     │ 1       │ Expand with 8 new methods + 3 new ANSI codes                      │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/WorkctlCLI.java              │ 1       │ Replace run() with banner()                                       │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/TaskCommand.java    │ 1, 2, 3 │ ListTasks rewrite, Show/SubtaskList styling, Scanner→JLine, table │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/ProjectCommand.java │ 1, 2    │ List styling, Scanner→JLine confirm                               │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/InsightCommand.java │ 1, 2, 3 │ Box headers, progress bars, spinner, activity chart               │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/AskCommand.java     │ 1, 2, 3 │ Styled output, spinner, REPL mode, history fix                    │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/LogCommand.java     │ 1, 2    │ ConsolePrinter usage, Scanner→JLine                               │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/commands/InitCommand.java    │ 1       │ ConsolePrinter usage                                              │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/build.gradle                 │ 2       │ Add org.jline:jline:3.27.1                                        │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/util/CliSpinner.java         │ 2       │ CREATE — background thread spinner                                │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ cli/util/CliPrompt.java          │ 2       │ CREATE — JLine prompt utilities                                   │                  │
│ ├──────────────────────────────────┼─────────┼───────────────────────────────────────────────────────────────────┤                  │
│ │ core/service/TaskService.java    │ 3       │ Remove listTasks() + formatTaskForCli()                           │                  │
│ └──────────────────────────────────┴─────────┴───────────────────────────────────────────────────────────────────┘                  │
│                                                                                                                                     │
│ Key Reuse                                                                                                                           │
│                                                                                                                                     │
│ - taskService.getTasks(String projectName) — already exists, replaces listTasks() in CLI layer                                      │
│ - insights.getDailyActivity() — already returns Map<LocalDate, Integer>, used for activity chart                                    │
│ - task.getDoneSubtaskCount() / task.getTotalSubtaskCount() — used for progress bars in Show/List                                    │
╰─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯
```
