# macOS Packaging — What We Fixed and Why

Notes on the issues found and changes made when building portable macOS executables for workctl.

---

## Problem 1: `packageNative` failing with "destination already exists"

### Error

```text
Error: Application destination directory .../build/release/workctl.app already exists
```

### Root cause

On macOS, `jpackage --type app-image` creates `workctl.app` (a `.app` bundle).
The `cleanPackage` task was deleting `build/release/workctl/` (a plain folder),
which is the output format on Windows and Linux — not macOS.
So on macOS, the old `.app` was never cleaned, and the second build failed.

### Fix — `cli/build.gradle`

```groovy
// Before: only deleted workctl/ (the Windows/Linux output)
def appDir = file("${rootDir}/build/release/workctl")
if (appDir.exists()) appDir.deleteDir()

// After: delete both names — jpackage uses whichever applies per platform
['workctl', 'workctl.app'].each { name ->
    def f = file("${rootDir}/build/release/${name}")
    if (f.exists()) { if (f.isDirectory()) f.deleteDir() else f.delete() }
}
```

Also fixed `packageZip` which was sourcing from `build/release/workctl` on all platforms.
On macOS the zip now correctly includes `workctl.app/**`.

---

## Problem 2: GUI `workctl-gui.app` can't be opened by double-click

### Error

> The application "workctl-gui" can't be opened.

### Root cause

`jpackage` produces an ad-hoc signed bundle (`Signature=adhoc`, no Team ID).
On macOS 15+ (Sequoia/Tahoe), Gatekeeper blocks Finder from launching any app
that does not have valid entitlements — even ones you built yourself locally.

The specific entitlements missing are JVM requirements:

| Entitlement | Why the JVM needs it |
| --- | --- |
| `cs.allow-jit` | HotSpot JIT compiles bytecode to native code at runtime — pages must be both writable and executable simultaneously |
| `cs.allow-unsigned-executable-memory` | JVM interpreter allocates executable memory not covered by a signature |
| `cs.disable-library-validation` | JavaFX dylibs are signed by Gluon, not the same identity as the app — without this, the loader rejects them |

### Diagnosis commands used

```bash
# Check what signature the bundle has
codesign -d --verbose=4 build/release/workctl-gui.app
# Output showed: Signature=adhoc, TeamIdentifier=not set

# Ask Gatekeeper whether it would allow the app to run
spctl --assess --verbose build/release/workctl-gui.app
# Output: rejected
```

### Fix — `gui/src/main/resources/macos-entitlements.plist`

Created a new entitlements file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
    "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key><true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key><true/>
    <key>com.apple.security.cs.disable-library-validation</key><true/>
</dict>
</plist>
```

### Fix — `gui/build.gradle` post-build signing step

Added to `packageApp` `doLast {}`:

```groovy
if (os.isMacOsX()) {
    def appBundle    = file("${rootDir}/build/release/workctl-gui.app").absolutePath
    def entitlements = file("src/main/resources/macos-entitlements.plist").absolutePath
    def proc = new ProcessBuilder(
            'codesign', '--force', '--deep', '--sign', '-',
            '--entitlements', entitlements, '--options', 'runtime', appBundle)
        .inheritIO().start()
    def rc = proc.waitFor()
    if (rc != 0) throw new GradleException("codesign failed (exit ${rc})")
}
```

**Why `ProcessBuilder` and not `project.exec {}`?**
In Gradle 9, `exec {}` and `project.exec {}` are deprecated inside task action
closures. `ProcessBuilder` is plain Java — no Gradle API involved — so it works
in any Gradle version without deprecation warnings.

**What `codesign` flags mean:**

| Flag | Meaning |
| --- | --- |
| `--force` | Replace the existing ad-hoc signature jpackage already applied |
| `--deep` | Recursively sign all nested binaries (runtime/bin, dylibs, frameworks) |
| `--sign -` | `-` means ad-hoc identity (no certificate). Works locally; not accepted by App Store |
| `--entitlements` | Embed the entitlements plist into the signature |
| `--options runtime` | Enable the Hardened Runtime — required for entitlements to be respected on macOS 10.14+ |

### Verify the fix

```bash
# Check entitlements are embedded
codesign -d --entitlements :- build/release/workctl-gui.app

# Should print the three keys from macos-entitlements.plist
```

### What still shows a warning on first launch?

Even with correct entitlements, macOS Gatekeeper will block the **first** launch
of any app that is not signed with an Apple Developer ID certificate + notarized.
This is unavoidable without a paid developer account ($99/year).

**One-time bypass for locally built apps:**

1. Try to open — get the "can't be opened" dialog — click OK
2. Open System Settings → Privacy & Security
3. Scroll to the Security section
4. Click **Open Anyway** next to "workctl-gui was blocked"

After this, the app opens normally every time.

---

## Problem 3: CLI reporting version `0.1.0` instead of `2.0.0`

### Root cause

The version was hardcoded in the `@CommandLine.Command` annotation:

```java
@CommandLine.Command(name = "workctl", version = "workctl 0.1.0", ...)
```

The actual project version (`2.0.0`) lives in `build.gradle` and gets written
into the JAR manifest as `Implementation-Version` by the `jar` task — but the
annotation never read it.

### Fix — `WorkctlCLI.java`

Replaced the hardcoded string with a `versionProvider` that reads from the
manifest at runtime:

```java
@CommandLine.Command(name = "workctl",
        versionProvider = WorkctlCLI.ManifestVersionProvider.class, ...)
public class WorkctlCLI implements Runnable {
    ...
    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Enumeration<URL> resources = WorkctlCLI.class.getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                Attributes attrs = manifest.getMainAttributes();
                String title   = attrs.getValue("Implementation-Title");
                String version = attrs.getValue("Implementation-Version");
                if ("workctl CLI".equals(title) && version != null) {
                    return new String[]{"workctl " + version};
                }
            }
            return new String[]{"workctl (version unknown)"};
        }
    }
}
```

**Why iterate all manifests?**
The JVM classpath contains many JARs (picocli, jline, etc.), each with its own
`META-INF/MANIFEST.MF`. We iterate until we find the one whose
`Implementation-Title` is `"workctl CLI"` — the value set by `cli/build.gradle`:

```groovy
jar {
    manifest {
        attributes 'Implementation-Title': 'workctl CLI',
                   'Implementation-Version': version   // ← comes from root build.gradle
    }
}
```

Now bumping the version in `build.gradle` automatically propagates to
`workctl --version` with no other changes needed.

---

## Problem 4: App icon not showing in Finder / Dock

### Root cause

`jpackage` picks up the icon via the `--icon` flag. Neither the CLI nor the GUI
had an `.icns` file (the format macOS requires for app bundles). Only a Windows
`.ico` file existed at `cli/src/main/resources/icon.ico`. Without `--icon`, jpackage
uses a generic Java coffee-cup placeholder.

| Platform | Required format | Tool to create it |
| --- | --- | --- |
| macOS | `.icns` | `sips` + `iconutil` (built into macOS) |
| Windows | `.ico` | Any image converter |
| Linux | `.png` | Any image editor |

### How `.icns` works

An `.icns` file is not a single image — it is a container that holds the same
icon at multiple resolutions so macOS can render it at any size (menu bar,
Finder list view, Dock, Quick Look):

| File name inside iconset | Displayed size | Actual pixels |
| --- | --- | --- |
| `icon_16x16.png` | 16 pt | 16×16 |
| `icon_16x16@2x.png` | 16 pt on Retina | 32×32 |
| `icon_32x32.png` | 32 pt | 32×32 |
| `icon_32x32@2x.png` | 32 pt on Retina | 64×64 |
| `icon_128x128.png` | 128 pt | 128×128 |
| `icon_128x128@2x.png` | 128 pt on Retina | 256×256 |
| `icon_256x256.png` | 256 pt | 256×256 |
| `icon_256x256@2x.png` | 256 pt on Retina | 512×512 |
| `icon_512x512.png` | 512 pt | 512×512 |
| `icon_512x512@2x.png` | 512 pt on Retina | 1024×1024 |

### Commands used to generate `icon.icns`

```bash
# 1. Create a temporary iconset folder (must end in .iconset)
ICONSET=$(mktemp -d)/workctl.iconset
mkdir -p "$ICONSET"
SRC="assets/images/workctl-icon.png"   # 1024x1024 source

# 2. Use sips to resize to every required size
for size in 16 32 64 128 256 512; do
    sips -z $size $size "$SRC" --out "$ICONSET/icon_${size}x${size}.png"
    double=$((size * 2))
    sips -z $double $double "$SRC" --out "$ICONSET/icon_${size}x${size}@2x.png"
done

# 3. Pack into .icns
iconutil -c icns "$ICONSET" -o icon.icns
```

**`sips`** (Scriptable Image Processing System) is a macOS command-line tool for
image transformations. `-z width height` resizes in-place.

**`iconutil`** is the macOS tool that converts an `.iconset` folder into a
single binary `.icns` file. It requires the folder name to end in `.iconset`
and all the correctly-named PNG files to be present.

The source PNG must be at least 1024×1024 for the `@2x` Retina slots. The
project's `assets/images/workctl-icon.png` was exactly 1024×1024, so no
upscaling was needed.

### Where the icon file goes

```
cli/src/main/resources/icon.icns   ← used by :cli:packageNative
gui/src/main/resources/icon.icns   ← used by :gui:packageApp
```

### Fix — `cli/build.gradle` (CLI was missing `--icon` entirely)

The GUI's `packageApp` task already had icon handling for all platforms.
The CLI's `packageNative` task had none. Added:

```groovy
// Platform-specific icon
if (isWindows) {
    def icon = file("src/main/resources/icon.ico")
    if (icon.exists()) cmd += ['--icon', icon.absolutePath]
    cmd += ['--win-console']
} else if (isMacOS) {
    def icon = file("src/main/resources/icon.icns")
    if (icon.exists()) cmd += ['--icon', icon.absolutePath]
    cmd += ['--mac-package-identifier', 'com.workctl.cli']
} else {
    def icon = file("src/main/resources/icon.png")
    if (icon.exists()) cmd += ['--icon', icon.absolutePath]
}
```

The `if (icon.exists())` guard means a missing icon file is silently skipped
rather than causing a build failure — jpackage falls back to its default icon.

### Verify the icon is embedded after build

```bash
ls build/release/workctl.app/Contents/Resources/
# workctl.icns  ← should be present

ls build/release/workctl-gui.app/Contents/Resources/
# workctl-gui.icns  ← should be present
```

---

## Problem 5: ZIP loses execute permissions — binary refuses to run after unzip

### Symptom

A user downloads the macOS ZIP from GitHub, unzips it, runs `xattr -dr com.apple.quarantine workctl-gui.app`, and then gets this error when trying to open it:

```text
Error Domain=RBSRequestErrorDomain Code=5 "Launch failed."
NSUnderlyingError: NSPOSIXErrorDomain Code=111 "Launchd job spawn failed"
```

Or from the terminal:

```bash
./workctl-gui.app/Contents/MacOS/workctl-gui
# zsh: permission denied: ./workctl-gui.app/Contents/MacOS/workctl-gui
```

### Root cause

Gradle's built-in `Zip` task (the `Zip` task type) does **not** preserve Unix file permissions. When it zips the `.app` bundle, every file inside is stored with mode `0644` (read-write for owner, read-only for others). The binary at `Contents/MacOS/workctl-gui` needs `0755` (executable). When the recipient unzips and tries to launch, macOS can't execute the binary.

You can verify permission loss:

```bash
# Check permissions inside the ZIP without extracting
unzip -v workctl-gui-2.0.0-macos.zip | grep "MacOS/"
# Broken: shows -rw-r--r--  (no x bit)
# Fixed:  shows -rwxr-xr-x
```

**Why Gradle's Zip task does this:**
Gradle's `Zip` is designed to be cross-platform (including Windows, which has no Unix permission model). To stay portable, it defaults all files to `0644` and all directories to `0755`. There is no built-in option to preserve the original file modes from the source filesystem.

### Fix — use macOS system `zip` command via ProcessBuilder

The system `zip` tool (shipped with macOS) preserves Unix file modes in the archive. The fix is to replace the Gradle `Zip` task type with a plain task that shells out to `zip -r` on macOS.

**Before** (`cli/build.gradle` and `gui/build.gradle`):

```groovy
// ❌ Gradle's Zip task — strips execute permissions on macOS
tasks.register('packageZip', Zip) {
    dependsOn packageNative
    archiveFileName      = "workctl-${version}-${platformSuffix}.zip"
    destinationDirectory = layout.buildDirectory.dir('distributions')

    if (isMacOS) {
        from("${rootDir}/build/release") { include 'workctl.app/**' }
    } else {
        from("${rootDir}/build/release/workctl")
    }
    into("workctl-${version}")
}
```

**After** (`cli/build.gradle`):

```groovy
// ✅ Plain task — uses system zip on macOS to preserve execute permissions
tasks.register('packageZip') {
    group       = 'workctl distribution'
    description = 'Zip the native CLI package for sharing (no Java needed)'
    dependsOn   packageNative

    doLast {
        def distDir = new File("${buildDir}/distributions")
        distDir.mkdirs()
        def zipPath = new File(distDir, "workctl-${version}-${platformSuffix}.zip").absolutePath

        if (isMacOS) {
            // System zip preserves Unix execute permissions — Gradle's Zip task does not
            def proc = new ProcessBuilder('zip', '-r', zipPath, 'workctl.app')
                    .directory(file("${rootDir}/build/release"))
                    .inheritIO()
                    .start()
            if (proc.waitFor() != 0) throw new GradleException("zip failed")
        } else {
            ant.zip(destfile: zipPath) {
                zipfileset(dir: "${rootDir}/build/release/workctl", prefix: "workctl-${version}")
            }
        }
    }
}
```

**After** (`gui/build.gradle`):

```groovy
tasks.register('packageZip') {
    group       = 'workctl distribution'
    description = 'Create a ready-to-share zip of the packaged app'
    dependsOn   packageApp

    doLast {
        def platform = os.isWindows() ? 'windows' : (os.isMacOsX() ? 'macos' : 'linux')
        def distDir  = new File("${buildDir}/distributions")
        distDir.mkdirs()
        def zipPath  = new File(distDir, "workctl-gui-${version}-${platform}.zip").absolutePath

        if (os.isMacOsX()) {
            // System zip preserves Unix execute permissions — Gradle's Zip task does not
            def proc = new ProcessBuilder('zip', '-r', zipPath, 'workctl-gui.app')
                    .directory(file("${rootDir}/build/release"))
                    .inheritIO()
                    .start()
            if (proc.waitFor() != 0) throw new GradleException("zip failed")
        } else if (os.isWindows()) {
            ant.zip(destfile: zipPath) {
                zipfileset(dir: "${rootDir}/build/release/workctl-gui", prefix: 'workctl-gui')
            }
        } else {
            ant.zip(destfile: zipPath) {
                zipfileset(dir: "${rootDir}/build/release", includes: '*.deb')
            }
        }
    }
}
```

**Why `ant.zip` for Windows/Linux?**
`ant.zip` (Gradle's built-in Ant bridge) also does not preserve Unix permissions — but that's fine:

- **Windows**: `.exe` files have no Unix permission concept. `workctl-gui.exe` runs regardless.
- **Linux**: The `.deb` package handles permissions internally via the package manifest.

Only macOS needs the system `zip`.

### Verify the ZIP has execute permissions

After rebuilding, check that the execute bit survives inside the ZIP:

```bash
# CLI
unzip -v cli/build/distributions/workctl-2.0.0-macos.zip | grep "MacOS/"
# Should show: -rwxr-xr-x  or  100755

# GUI
unzip -v gui/build/distributions/workctl-gui-2.0.0-macos.zip | grep "MacOS/"
# Should show: -rwxr-xr-x  or  100755
```

### Manual fix if you already distributed a broken ZIP

If users have already downloaded the broken ZIP:

```bash
# After unzipping:
chmod +x workctl-gui.app/Contents/MacOS/workctl-gui
codesign --force --deep --sign - workctl-gui.app
xattr -dr com.apple.quarantine workctl-gui.app
open workctl-gui.app
```

---

## Appendix: Signing with an Apple Developer Certificate

Currently the app uses **ad-hoc signing** (`--sign -`), which works locally but
requires a one-time "Open Anyway" bypass on every new machine. Signing with a
real Apple Developer ID removes that friction and allows the app to open on any
Mac without Gatekeeper warnings.

### What you need

| Requirement | Cost | Where |
| --- | --- | --- |
| Apple Developer Program membership | $99/year | [developer.apple.com/programs](https://developer.apple.com/programs/) |
| Mac with Xcode installed | Free | Mac App Store |
| **Developer ID Application** certificate | Included | Created in Xcode or developer.apple.com |

> There are two certificate types. Use **Developer ID Application** (for
> distributing outside the App Store). "Apple Distribution" is only for
> App Store submissions.

---

### Step 1 — Enroll in the Apple Developer Program

1. Go to [developer.apple.com/programs](https://developer.apple.com/programs/)
2. Sign in with your Apple ID → click **Enroll**
3. Pay the $99/year fee
4. Wait for approval (usually instant for individuals, 1–2 days for organisations)

---

### Step 2 — Create a Developer ID Application certificate

#### Option A — via Xcode (easier)

1. Open Xcode → **Settings** → **Accounts** tab
2. Add your Apple ID if not already there
3. Select your team → click **Manage Certificates**
4. Click **+** → choose **Developer ID Application**
5. Xcode creates and installs the certificate into your Keychain automatically

#### Option B — via developer.apple.com (manual)

1. Go to [developer.apple.com/account/resources/certificates](https://developer.apple.com/account/resources/certificates)
2. Click **+** → choose **Developer ID Application** → Continue
3. Follow the CSR instructions (create via Keychain Access → Certificate Assistant)
4. Download the `.cer` file and double-click to install into Keychain

**Verify the certificate is installed:**

```bash
security find-identity -v -p codesigning | grep "Developer ID Application"
# Should print something like:
# 1) ABC123DEF456... "Developer ID Application: Your Name (TEAMID)"
```

---

### Step 3 — Sign the app with your certificate

Replace `--sign -` (ad-hoc) with your certificate's Common Name or SHA-1 hash:

```bash
# Using the Common Name (easier to read)
codesign --force --deep \
  --sign "Developer ID Application: Your Name (TEAMID)" \
  --entitlements gui/src/main/resources/macos-entitlements.plist \
  --options runtime \
  build/release/workctl-gui.app
```

To automate this in `gui/build.gradle`, replace the `-` in the signing step:

```groovy
// In gui/build.gradle, find the ProcessBuilder signing step and change:
'--sign', '-',
// to:
'--sign', 'Developer ID Application: Your Name (TEAMID)',
```

Or read it from `~/.gradle/gradle.properties` so it's not hardcoded:

```properties
# ~/.gradle/gradle.properties
macSignIdentity=Developer ID Application: Your Name (TEAMID)
```

```groovy
// gui/build.gradle
ext {
    macSignIdentity = project.findProperty('macSignIdentity') ?: '-'
    // Falls back to ad-hoc (-) if not set
}

// In the signing ProcessBuilder:
'--sign', macSignIdentity,
```

---

### Step 4 — Notarize the app

Signing proves *who* built the app. **Notarization** is Apple scanning it for
malware and stamping it as safe. Without notarization, signed apps still get a
Gatekeeper warning on the first launch.

**Create an app-specific password** (for CI/automation):

1. Go to [appleid.apple.com](https://appleid.apple.com) → Security → App-Specific Passwords
2. Generate a password → save it

**Submit for notarization:**

```bash
# Zip the app first (notarytool requires a zip or dmg)
ditto -c -k --keepParent build/release/workctl-gui.app /tmp/workctl-gui.zip

# Submit to Apple's notarization service
xcrun notarytool submit /tmp/workctl-gui.zip \
  --apple-id "you@example.com" \
  --team-id "YOURTEAMID" \
  --password "xxxx-xxxx-xxxx-xxxx" \
  --wait
# --wait blocks until Apple finishes (usually 1-5 minutes)
```

**Staple the ticket** (embeds the approval so the app works offline):

```bash
xcrun stapler staple build/release/workctl-gui.app
```

**Verify:**

```bash
spctl --assess --verbose build/release/workctl-gui.app
# Should print: accepted (notarized)
```

After stapling, the app opens on any Mac with no warnings — no "Open Anyway",
no Privacy & Security bypass needed.

---

### Summary: ad-hoc vs Developer ID vs Notarized

| | Ad-hoc (current) | Developer ID signed | Developer ID + Notarized |
| --- | --- | --- | --- |
| Works on your own Mac | ✅ (after one bypass) | ✅ | ✅ |
| Works on other Macs | ⚠️ needs bypass | ⚠️ needs bypass | ✅ no prompts |
| Cost | Free | $99/year | $99/year |
| Gatekeeper warning | Yes | Yes | No |
| Recommended for | Development | Internal sharing | Public distribution |

---

## Building ZIPs and Uploading to a GitHub Release

### What the ZIP contains

`packageZip` wraps the native `.app` bundle into a single ZIP file so it can be
shared or attached to a GitHub release. On macOS:

- CLI ZIP → contains `workctl-2.0.0/workctl.app/` (run `Contents/MacOS/workctl` from terminal)
- GUI ZIP → contains `workctl-2.0.0/workctl-gui.app/` (double-click to open)

Recipients just unzip and run — no Java, no JavaFX, no Gradle needed.

---

### Step 1 — Build the ZIPs

#### Fresh build from scratch (recommended before any release)

```bash
./gradlew clean :cli:packageZip :gui:packageZip
```

What each task does in order:

1. `clean` — deletes all previous `build/` output across every module
2. `:cli:packageNative` (triggered automatically by `packageZip`) — runs `jpackage` to create `build/release/workctl.app`
3. `:cli:packageZip` — zips `workctl.app` → `cli/build/distributions/workctl-2.0.0-macos.zip`
4. `:gui:packageApp` (triggered automatically by `packageZip`) — runs `jpackage` + re-signs with entitlements → `build/release/workctl-gui.app`
5. `:gui:packageZip` — zips `workctl-gui.app` → `gui/build/distributions/workctl-gui-2.0.0-macos.zip`

#### Output files

```text
cli/build/distributions/workctl-2.0.0-macos.zip        ← CLI (63 MB)
gui/build/distributions/workctl-gui-2.0.0-macos.zip    ← GUI (110 MB)
```

The platform suffix (`macos`, `windows`, `linux`) is added automatically based
on which OS the build runs on, via the `platformSuffix` variable in
`cli/build.gradle`:

```groovy
ext {
    platformSuffix = isWindows ? "windows" : isMacOS ? "macos" : "linux"
}
```

---

### Step 2 — Log in to GitHub CLI (one-time)

GitHub CLI (`gh`) is used to interact with releases. Authentication is required
once per machine:

```bash
gh auth login
# Choose: GitHub.com → HTTPS → Login with a web browser
# A one-time code is shown → opens browser → paste code → authorize
```

Verify it worked:

```bash
gh auth status
# Should print: Logged in to github.com as <your-username>
```

---

### Step 3 — Check the existing release

Before uploading, verify the release exists and see what's already attached:

```bash
gh release view v2.0.0
# Shows: release title, tag, description, and list of assets
```

To see just the asset names and sizes in a compact format:

```bash
gh release view v2.0.0 --json assets \
  --jq '.assets[] | "\(.name)  \(.size / 1048576 | floor)MB"'
```

---

### Step 4 — Upload the ZIPs to the release

```bash
gh release upload v2.0.0 \
  cli/build/distributions/workctl-2.0.0-macos.zip \
  gui/build/distributions/workctl-gui-2.0.0-macos.zip \
  --clobber
```

**Flags explained:**

| Flag | Meaning |
| --- | --- |
| `v2.0.0` | The release tag to upload to |
| (file paths) | One or more files to attach — space-separated |
| `--clobber` | Overwrite if a file with the same name already exists on the release |

Without `--clobber`, uploading a file that already exists on the release will
fail with an error. Use it whenever you rebuild and re-upload to replace an
older asset.

---

### Step 5 — Verify the upload

```bash
gh release view v2.0.0 --json assets \
  --jq '.assets[] | "\(.name)  \(.size / 1048576 | floor)MB"'
```

After the macOS upload the output should show all four assets:

```text
workctl-2.0.0-macos.zip       62MB
workctl-2.0.0-windows.zip     56MB
workctl-gui-2.0.0-macos.zip   109MB
workctl-gui-2.0.0-windows.zip 142MB
```

---

### Full workflow — one command sequence for future releases

```bash
# 1. Build fresh ZIPs
./gradlew clean :cli:packageZip :gui:packageZip

# 2. Upload to the GitHub release (replace tag as needed)
gh release upload v2.0.0 \
  cli/build/distributions/workctl-2.0.0-macos.zip \
  gui/build/distributions/workctl-gui-2.0.0-macos.zip \
  --clobber

# 3. Verify
gh release view v2.0.0 --json assets \
  --jq '.assets[] | "\(.name)  \(.size / 1048576 | floor)MB"'
```

### Why the ZIPs are large (60–110 MB)

The ZIP includes a full bundled JRE (Java Runtime Environment) inside the `.app`
bundle. This is the tradeoff of `--type app-image` — the recipient needs no Java
installed, but the file is bigger than a plain JAR.

| What's inside the ZIP | Approximate size |
| --- | --- |
| Bundled JRE (trimmed by jlink) | ~50–80 MB |
| App JARs (workctl code + dependencies) | ~10–30 MB |
| JavaFX native libraries (GUI only) | ~40–60 MB |
