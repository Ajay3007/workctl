# workctl Distribution Guide

> **[← README](../README.md)** | [CLI Reference](cli-api.md) | [Workflows Guide](workflows-guide.md) | [Setup](SETUP.md) | [Full Docs](workctl-docs.md)

## Which option should I use?

| Scenario | Command | What you share | Java needed? |
|----------|---------|----------------|--------------|
| Quick share (Java users) | `./gradlew :cli:zipDist` | `workctl-1.0.0-dist.zip` | ✅ Java 17+ |
| Fat JAR (Java users) | `./gradlew :cli:shadowJar` | `workctl-1.0.0-all.jar` | ✅ Java 17+ |
| Portable GUI (no Java) | `./gradlew :gui:runtimeZip` | `workctl-gui-1.0.0-portable.zip` | ❌ None |
| Native .exe (no Java) | `./gradlew packageAll` | `build/release/` folder | ❌ None |
| Both + one ZIP | `./gradlew zipRelease` | `workctl-1.0.0-windows.zip` | ❌ None |

---

## Option 1: Portable ZIP (Easiest, no Java needed)

### CLI portable ZIP
```bash
./gradlew :cli:installDist :cli:zipDist
```
Share: `cli/build/distributions/workctl-1.0.0-dist.zip`

Recipient extracts and runs `workctl-1.0.0/bin/workctl.bat`
**Requires Java 17+ on their machine.**

### GUI portable ZIP (no Java needed)
```bash
./gradlew :gui:runtimeZip
```
Share: `gui/build/distributions/workctl-gui-1.0.0-portable.zip`

Recipient extracts and runs `workctl-gui-1.0.0/bin/workctl-gui.bat`
**No Java or JavaFX required.** JavaFX is bundled inside the zip.

---

## Option 2: Fat JAR (CLI only, simplest single file)

```bash
./gradlew :cli:shadowJar
```
Share: `cli/build/libs/workctl-1.0.0-all.jar`

Recipient runs:
```bash
java -jar workctl-1.0.0-all.jar task list myproject
```
**Requires Java 17+ but no classpath setup.**

---

## Option 3: Native App (Best for end users, no Java needed)

Requires JDK 17+ with jpackage (included in JDK since Java 14).

```bash
./gradlew packageAll
```

Produces:
```
build/release/
  workctl/              ← CLI native app folder
    workctl.exe         ← users run this directly
  workctl-gui/          ← GUI native app folder
    workctl-gui.exe     ← users run this directly
```

To then ZIP both for easy sharing:
```bash
./gradlew zipRelease
```
Produces: `build/release/workctl-1.0.0-windows.zip`

**No Java or JavaFX required on the target machine.**

### For an .msi installer (optional)

1. Install WiX Toolset 3.x from https://github.com/wixtoolset/wix3/releases
2. Add to your PATH
3. In `build.gradle` root, change `"--type", "app-image"` to `"--type", "msi"`
4. In `gui/build.gradle`, change `installerType = "app-image"` to `installerType = "msi"`
5. Run `./gradlew packageAll`

This produces proper `.msi` installers with an uninstaller.

---

## Plugin prerequisite: shadow plugin

The shadow plugin for the fat JAR needs to be resolvable. Add this to
`settings.gradle` if you get a plugin resolution error:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

---

## Recommended workflow

### During development
```bash
# CLI
./gradlew :cli:installDist
# then run: cli/build/install/workctl/bin/workctl.bat

# GUI (IntelliJ run config or)
./gradlew :gui:run
```

### For sharing with other developers (have Java)
```bash
./gradlew :cli:shadowJar      # fat JAR for CLI
./gradlew :gui:runtimeZip     # portable GUI zip
```

### For sharing with non-technical users (no Java)
```bash
./gradlew packageAll
./gradlew zipRelease
# Share: build/release/workctl-1.0.0-windows.zip
```
## Steps to build executables for distribution

### On Windows

#### Step 1 — Build everything first:

```bash
./gradlew clean build
```

#### Step 2 — Package CLI:

```bash
bash./gradlew :cli:packageNative
# Output: build/release/workctl/workctl.exe
```

#### Step 3 — Package GUI:

```bash
bash./gradlew :gui:packageNative
# Output: build/release/workctl-gui/workctl-gui.exe
```

#### Step 4 — Zip both for sharing:

```bash
bash./gradlew :cli:packageZip
./gradlew :gui:packageZip
# Output: cli/build/distributions/workctl-1.0.0-windows.zip
#         gui/build/distributions/workctl-gui-1.0.0-windows.zip
```

---

### What others do to install

**CLI users** — extract zip, add the folder to their PATH:

```
workctl-1.0.0/workctl.exe   ← run directly or add to PATH
```

**GUI users** — extract zip, double-click:

```
workctl-gui-1.0.0/workctl-gui.exe   ← just double-click
```

No Java, no JavaFX, no Gradle needed on their machine.

### On macOS (to build Mac executables)

Before running, update gui/build.gradle ext block with your Mac JavaFX paths:

```bash
macos: [
  jmods:     "/Library/javafx-sdk-21/javafx-jmods-21",  // ← your Mac path
  nativeLib: "/Library/javafx-sdk-21/lib",
  nativeExt: ".dylib"
]
```