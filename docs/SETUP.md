
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

## 6. Tab Completion (macOS / Linux only)

Tab completion lets you press `Tab` to auto-complete commands and **project names**.

### One-time setup

After building the CLI, run:

```bash
./gradlew :cli:installDist   # generates ~/.workctl/workctl_completion
```

Then add these lines to `~/.zshrc` (zsh) or `~/.bashrc` (bash):

```bash
# workctl tab completion
autoload -U +X bashcompinit && bashcompinit
autoload -U +X compinit && compinit
source ~/.workctl/workctl_completion
```

Reload your shell:
```bash
source ~/.zshrc   # or source ~/.bashrc
```

### What tab completion covers

```bash
workctl <TAB>               # lists all commands
workctl project <TAB>       # create  list  delete
workctl insight <TAB>       # lists your actual project names
workctl task add <TAB>      # lists your actual project names
workctl project delete <TAB> # lists your actual project names
```

> **Windows:** Bash/zsh completion is not supported in CMD or PowerShell.
> Git Bash or WSL users can follow the macOS/Linux steps above.

---

## 7. Developer Shortcut — `workctl-build`

Instead of running Gradle commands manually every time, add this function to your shell profile. It rebuilds everything, reloads completions, and launches the GUI in one command.

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

### Tab completion shows filesystem paths instead of project names
The completion file needs to be reloaded after a rebuild:
```bash
source ~/.workctl/workctl_completion
```
Or just run `workctl-build` which reloads automatically.

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

*Last updated: 2026-02-21*
