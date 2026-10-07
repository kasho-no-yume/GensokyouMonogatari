# jar-server-toolchain Specification

## Purpose
TBD - created by archiving change jar-server-toolchain. Update Purpose after archive.
## Requirements
### Requirement: One-command build and GUI server launch
The toolchain SHALL provide a single script that compiles the mod to a jar and then launches the dedicated server with that jar in GUI mode (no `nogui` flag).

#### Scenario: Fresh user runs the script
- **WHEN** the user runs `tools/run_server_jar.ps1`
- **THEN** the script runs `tools/gradle_task.ps1 build` to produce `build/libs/gensokyou-1.0.0.jar`, copies the jar into `server/mods/`, and starts the server without the `nogui` argument.

### Requirement: Incremental build on every invocation
The script SHALL invoke the Gradle build on every run instead of tracking workspace changes.

#### Scenario: No source changes since last build
- **WHEN** the user runs the script twice in a row without changing sources
- **THEN** Gradle performs an incremental no-op build and the script proceeds to launch the server.

### Requirement: Automatic first-time server installation
If the dedicated server is not present, the script SHALL install it automatically before launching.

#### Scenario: Missing server libraries
- **WHEN** `server/libraries/` is missing and the user runs the script
- **THEN** the script downloads `neoforge-21.1.248-installer.jar` (via proxy if configured), runs `--installServer` into `server/`, writes `eula.txt`, and then launches the server.

### Requirement: Managed dependency jars in server/mods
The script SHALL ensure the two hard dependency jars (Patchouli, GeckoLib) exist in `server/mods/` matching the versions pinned in `gradle.properties`.

#### Scenario: Dependency jar missing
- **WHEN** Patchouli or GeckoLib is absent from `server/mods/`
- **THEN** the script copies the matching jar from the local Gradle cache, or downloads it from the corresponding maven repository if not cached.

### Requirement: Agent-safe detached server process
The server process SHALL be spawned detached from the calling shell so the agent tool call returns immediately and is never blocked by inherited handles.

#### Scenario: Script invocation returns
- **WHEN** the script finishes starting the server
- **THEN** it returns control immediately while the server JVM keeps running with its GUI window; the caller's stdout pipe is not held.

