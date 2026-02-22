
# workctl — Developer Setup Guide

Step-by-step setup for new developers on macOS, Linux, and Windows.

---

## Prerequisites

| Requirement | Version | Check |
|---|---|---|
| Java (JDK) | 17 or higher | `java -version` |
| Git | Any recent | `git --version` |

> **Note:** You do NOT need to install Gradle — the repo includes the Gradle wrapper (`gradlew` / `gradlew.bat`) which downloads the right version automatically on first run.

### Installing Java 17

**macOS**
```bash
brew install --cask temurin@17
```

**Ubuntu / Debian**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

**Windows**
Download and install from: https://adoptium.net
Choose **Temurin 17 (LTS)**.

Verify after install:
```bash
java -version
# openjdk version "17.x.x" ...
```

---

## 1. Clone the Repository

```bash
git clone https://github.com/Ajay3007/workctl.git
cd workctl
```

---

## 2. Build

```bash
# macOS / Linux
./gradlew clean build

# Windows
gradlew.bat clean build
```

This compiles all five modules: `config`, `core`, `agent`, `cli`, `gui`.

---

## 3. Run the GUI

```bash
# macOS / Linux
./gradlew :gui:run

# Windows
gradlew.bat :gui:run
```

---

## 4. Install the CLI

```bash
# macOS / Linux
./gradlew :cli:installDist

# Windows
gradlew.bat :cli:installDist
```

The CLI binary is placed at:

| Platform | Path |
|---|---|
| macOS / Linux | `cli/build/install/workctl/bin/workctl` |
| Windows | `cli\build\install\workctl\bin\workctl.bat` |

### Add CLI to PATH

**macOS / Linux** — add to `~/.zshrc` or `~/.bashrc`:
```bash
export PATH="$PATH:/path/to/workctl/cli/build/install/workctl/bin"
```

**Windows (PowerShell)** — add permanently via System Properties → Environment Variables, or run:
```powershell
$env:PATH += ";C:\path\to\workctl\cli\build\install\workctl\bin"
```

---

## 5. First-Time Initialization

```bash
# macOS / Linux
workctl init --workspace "/path/to/your/workspace"

# Windows
workctl init --workspace "C:\Users\YourName\Work"
```

This creates `~/.workctl/config.yaml` with your workspace path.

### Set your Anthropic API key (required for AI features)

```bash
workctl config set anthropicApiKey sk-ant-YOUR_KEY_HERE
```

---

## 6. Tab Completion

Tab completion lets you press `Tab` to auto-complete commands and **live project names**.

### Platform support

| Platform | Supported | Notes |
|---|---|---|
| macOS (zsh) | ✓ | Default shell; requires `bashcompinit` bridge (see below) |
| macOS (bash) | ✓ | Less common but works the same as Linux bash |
| Linux (bash) | ✓ | Works out of the box |
| Linux (zsh) | ✓ | Same `bashcompinit` setup as macOS zsh |
| Windows CMD / PowerShell | ✗ | Not supported — completion uses bash syntax |
| Windows Git Bash / WSL | ✓ | Follow the Linux bash steps |

The completion script uses bash `complete` syntax and is generated only on macOS/Linux.
On Windows the `installDist` build step prints a skip notice and continues normally.

### One-time setup

**Step 1 — Build the CLI** (generates `~/.workctl/workctl_completion`):

```bash
./gradlew :cli:installDist
```

**Step 2 — Add to your shell profile:**

**zsh** (`~/.zshrc`) — macOS default and common on Linux:
```zsh
# workctl tab completion (bash-style, bridged via bashcompinit)
autoload -U +X bashcompinit && bashcompinit
autoload -U +X compinit && compinit
[ -f ~/.workctl/workctl_completion ] && source ~/.workctl/workctl_completion
```

**bash** (`~/.bashrc` or `~/.bash_profile`) — Linux default, Git Bash, WSL:
```bash
# workctl tab completion
[ -f ~/.workctl/workctl_completion ] && source ~/.workctl/workctl_completion
```

> The `autoload` lines are **zsh-specific**. Do not add them to a bash profile — they are not needed and will produce errors.

**Step 3 — Reload:**
```bash
source ~/.zshrc    # zsh
source ~/.bashrc   # bash
```

### When do you need to re-source?

| Situation | Re-source needed? |
|---|---|
| New terminal window (profile already updated) | No — loaded automatically |
| You created or deleted a project | **No** — project names are fetched live at tab-press time |
| `installDist` added a new command or flag | Yes — current session needs to reload the script |
| You ran `workctl-build` | No — it calls `source` for you automatically |

Project name completion works by calling `workctl project list --plain` at the moment you press `Tab`, so it always reflects the current workspace without any manual steps. The `--plain` flag is also useful for scripting:

```bash
# Get a plain list of project names (no ANSI, no headers)
workctl project list --plain
```

### What tab completion covers

```bash
workctl <TAB>                # lists all commands
workctl project <TAB>        # create  list  delete
workctl insight <TAB>        # lists your actual project names (live)
workctl task list <TAB>      # lists your actual project names (live)
workctl task add <TAB>       # lists your actual project names (live)
workctl project delete <TAB> # lists your actual project names (live)
workctl log <TAB>            # lists your actual project names (live)
workctl ask <TAB>            # lists your actual project names (live)
```

---

## 7. Developer Shortcut — `workctl-build`

Instead of running Gradle commands manually every time, add this function to your shell profile. It rebuilds everything, reloads completions in the **current** shell session, and launches the GUI in one command.

> **Note:** The build automatically regenerates `~/.workctl/workctl_completion` on disk (via the `generateCompletion` Gradle task). The `source` call in `workctl-build` then reloads it into the current session. New terminal windows always get the latest version from the file automatically.

### macOS / Linux (zsh or bash)

Add to `~/.zshrc` or `~/.bashrc`:

```bash
# Rebuild workctl CLI + GUI, reload completions, and launch GUI
workctl-build() {
    pushd /path/to/workctl > /dev/null
    ./gradlew clean :cli:installDist :gui:installDist \
        && source ~/.workctl/workctl_completion \
        && echo "✓ CLI + GUI built and completions reloaded" \
        && echo "🚀 Launching GUI..." \
        && ./gradlew :gui:run &
    popd > /dev/null
}
```

Replace `/path/to/workctl` with the actual path where you cloned the repo.

Reload and use:
```bash
source ~/.zshrc   # load the function once
workctl-build     # use from anywhere, any time
```

### Windows (PowerShell)

Add to your PowerShell profile (`$PROFILE`):

```powershell
function workctl-build {
    $repoPath = "C:\path\to\workctl"   # update this
    Push-Location $repoPath
    .\gradlew.bat clean :cli:installDist :gui:installDist
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ CLI + GUI built" -ForegroundColor Green
        Write-Host "🚀 Launching GUI..." -ForegroundColor Cyan
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$repoPath'; .\gradlew.bat :gui:run"
    }
    Pop-Location
}
```

Open your profile for editing:
```powershell
notepad $PROFILE
```

Paste the function, save, then reload:
```powershell
. $PROFILE
workctl-build
```

### What `workctl-build` does

```
workctl-build
    ├── Clean previous build artifacts
    ├── Build CLI  → cli/build/install/workctl/bin/workctl
    ├── Build GUI  → gui/build/install/workctl-gui/
    ├── Reload tab completions (macOS/Linux)
    └── Launch GUI in background (terminal stays free)
```

---

## 8. Verify Everything Works

```bash
# Check CLI is working
workctl --help

# Initialize if not done yet
workctl init --workspace "/your/workspace"

# Create a test project
workctl project create test-project

# List projects
workctl project list

# Launch GUI (if not already running via workctl-build)
./gradlew :gui:run
```

---

## Troubleshooting

### `java: command not found`
Java is not installed or not on PATH. Install JDK 17 (see Prerequisites above).

### `./gradlew: Permission denied` (macOS / Linux)
```bash
chmod +x gradlew
```

### Tab completion doesn't work at all
Verify the completion file exists and is sourced:
```bash
ls ~/.workctl/workctl_completion   # should exist after installDist
type _complete_workctl_dynamic     # should print a function definition
```
If missing, run `./gradlew :cli:installDist`, then check your shell profile has the `source` line (see Section 6).

### Tab completion shows filesystem paths instead of project names
The completion file is not loaded in the current session. Reload it:
```bash
source ~/.workctl/workctl_completion
```
Or run `workctl-build` — it reloads automatically.

Also verify `workctl` is on PATH (needed for `_workctl_project_names` to call it at tab-press time):
```bash
which workctl
```

### Tab completion doesn't show a newly created project
This should not happen — project names are fetched live at tab-press time via `workctl project list --plain`. If it does, check that the project was created successfully:
```bash
workctl project list --plain
```

### Tab completion: zsh shows `autoload: function not found` or similar
You may have added the `autoload` lines to a bash profile instead of zsh, or vice versa. The `autoload -U +X bashcompinit` lines are **zsh-only**. For bash, remove them — just the `source` line is needed.

### GUI doesn't launch on Linux
JavaFX requires a display. Make sure you're running in a desktop environment, not a headless SSH session.

### `anthropicApiKey` not set — AI features return errors
```bash
workctl config set anthropicApiKey sk-ant-YOUR_KEY
```

---

## Project Layout Reference

```
workctl/
├── core/       Business logic, Markdown persistence, task/log/stats services
├── cli/        Picocli commands (WorkctlCLI.java is the entry point)
├── gui/        JavaFX desktop app (WorkctlApp.java + main.fxml)
├── agent/      Claude API integration, tool-use loop
├── config/     YAML config loading (AppConfig.java)
└── docs/
    ├── SETUP.md        ← you are here
    ├── cli-api.md      ← full CLI command reference
    └── dev-notes.md    ← critical gotchas for developers
```

---

*Last updated: 2026-02-21 — tab completion section expanded with platform matrix, re-source guide, and `--plain` flag docs*
