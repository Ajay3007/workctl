wo# CLI API Documentation

## `workctl init`

### Description

Initializes workspace and configuration.

### Usage

```bash
./gradlew :cli:run --args="init --workspace=<path>
```

**Example**

```bash
./gradlew :cli:run --args="init --workspace=C:\Users\Ajay.Gupt\Downloads\Work"
```

**OR**

```bash
workctl init --workspace <path>
```

**Example**

```bash
workctl init --workspace "C:\Users\Ajay.Gupt\Downloads\Work"
```

### What It Does

- Creates `.workctl/config.yaml`

- Stores workspace path

- Prepares folder structure

## `workctl project create`

### Description

Creates a new project directory inside workspace.

### Usage

```bash
workctl project create <project-name> --description "text"
```

**Example**

```bash
workctl project create redis-load-test --description "Latency testing"
```

## `workctl project list`

### Description

Lists all projects inside workspace.

### Usage

```bash
workctl project list
```

**Example Output**

```bash
Projects:
  - my-sample-project
  - redis-load-test
```

## `workctl log`

### Description

Adds log entry to project.

### Basic Template Entry

```bash
workctl log <project>
```

Creates full date block.

### Quick Message Entry

```bash
workctl log <project> --message "text"
```

Defaults to **`Done`** section.

### Section-specific Entry

```bash
workctl log <project> --section commands --message "cp file.txt ../config/"

workctl log redis-load-test --section=done --message "Release 1.1.0 offered"
```

#### Supported sections:

- assigned
- done
- changes
- commands
- notes
