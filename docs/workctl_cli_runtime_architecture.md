# Workctl CLI Runtime Architecture & Execution Model

------------------------------------------------------------------------

## 1. Running via Gradle (Development Mode)

When using:

    ./gradlew :cli:run --args="..."

Execution flow:

    PowerShell → Gradle → Java → Workctl main()

Gradle acts as a launcher: - Compiles the project - Builds the
classpath - Executes:

    java -cp <classpath> com.workctl.cli.WorkctlCLI

Gradle is not part of runtime --- it is only a development helper.

------------------------------------------------------------------------

## 2. What `installDist` Does

Command:

    ./gradlew :cli:installDist

This generates a distribution folder:

    cli/build/install/cli/
    │
    ├── bin/
    │   ├── workctl.bat   (Windows launcher)
    │   └── workctl       (Linux/macOS launcher)
    │
    └── lib/
        ├── cli.jar
        ├── picocli.jar
        ├── snakeyaml.jar
        └── other dependency jars

This allows the application to run without Gradle.

------------------------------------------------------------------------

## 3. What `workctl.bat` Actually Does

The generated launcher script effectively runs:

    java -cp "<all jars in lib>" com.workctl.cli.WorkctlCLI %*

So when you type:

    workctl log redis-load-test --message "text"

It internally executes:

    java -cp <classpath> com.workctl.cli.WorkctlCLI log redis-load-test --message text

Gradle is no longer involved at runtime.

------------------------------------------------------------------------

## 4. What is Classpath?

Classpath (`-cp`) tells Java where to find:

-   Compiled classes
-   Dependency JAR files

Example:

    java -cp cli.jar;picocli.jar;snakeyaml.jar com.workctl.cli.WorkctlCLI

If classpath is incorrect, Java cannot locate required classes.

------------------------------------------------------------------------

## 5. How Dependencies Are Resolved at Runtime

### Build Time

Gradle downloads dependencies from Maven Central and stores them in the
Gradle cache.

### Runtime

The launcher script includes all required JAR files in the `lib/`
directory inside the classpath.

Therefore, runtime does not require Gradle.

------------------------------------------------------------------------

## When Would You Need to Recreate `workctl.bat`?

Only if you:

- Change applicationName

- Change main class

- Change Gradle application config

- Change distribution type

- For normal logic updates → no need.

## Important Concept

When you add `C:\Users\AjayGupt\Downloads\csp\projects\workctl\cli\build\install\workctl\bin` PATH to ENV Path:

PATH works like this:

When you type:

```bash
workctl
```

Windows:

1. Looks in each folder in `PATH`

2. Searches for:

    - workctl.exe

    - workctl.bat

    - workctl.cmd

3. Executes the first match

So filename matters, not folder rename.

### Cleaner Way (Optional but Recommended)

Instead of manually deleting old launchers every time, run:

```bash
./gradlew :cli:clean
./gradlew :cli:installDist
```

Or even:

```bash
./gradlew clean :cli:installDist
```

That ensures no leftover files.

## 6. What is a Fat JAR?

A Fat JAR (also called Uber JAR) is a single JAR file containing:

-   Your compiled classes
-   All dependency classes

Instead of:

    lib/
      many jars

You get:

    workctl-all.jar

Run using:

    java -jar workctl-all.jar

### Advantages

-   Simpler distribution
-   Single file deployment
-   Easier Docker packaging
-   Ideal for GitHub releases

------------------------------------------------------------------------

## 7. What is a Native Binary?

A Native Binary is a compiled executable that runs without requiring a
JVM.

Example:

    workctl.exe

It runs directly as a system executable.

------------------------------------------------------------------------

## 8. What is GraalVM?

GraalVM is a high-performance JVM that can compile Java applications
into native binaries using:

    native-image

It converts:

    workctl.jar

Into:

    workctl.exe

### Benefits

-   Faster startup time
-   Lower memory usage
-   No Java installation required
-   Professional CLI feel

### Trade-offs

-   Longer build time
-   More complex setup
-   Some reflection configuration required

------------------------------------------------------------------------

## 9. Execution Flow Summary

### Development Mode

    PowerShell → Gradle → Java → WorkctlCLI

### Installed CLI Mode

    PowerShell → workctl.bat → Java → WorkctlCLI

Gradle is only required during development and building.

------------------------------------------------------------------------

## 10. Key Takeaway

A CLI tool is simply:

-   A `main()` method
-   Packaged classes and dependencies
-   A launcher script that invokes Java with the correct classpath

Workctl is now a fully packaged Java CLI application that runs
independently of Gradle.
