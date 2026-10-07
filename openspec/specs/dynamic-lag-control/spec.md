# dynamic-lag-control Specification

## Purpose
TBD - created by archiving change dynamic-lag-control. Update Purpose after archive.
## Requirements
### Requirement: Runtime latency adjustment via control file
The proxy SHALL support a `--control <path>` file that is polled for changes and applied to live connections without restarting the proxy or the server.

#### Scenario: Latency changed while a connection is open
- **WHEN** the control file is rewritten with a new `latency` value while a client is connected
- **THEN** subsequent chunks on that connection are delayed using the new value within about 0.5 seconds, and the connection is not dropped.

#### Scenario: Partial update
- **WHEN** the control file contains only `latency` and omits `jitter`/`downstream_only`
- **THEN** the omitted fields keep their current values.

#### Scenario: Malformed control file
- **WHEN** the control file contains invalid JSON
- **THEN** the proxy keeps the last valid values and logs a warning instead of exiting.

### Requirement: Set-lag command without rebuild
The server launch script SHALL accept `-SetLag` to update a running proxy's parameters without building the mod, relaunching the server, or restarting the proxy.

#### Scenario: Update running proxy
- **WHEN** the user runs `tools/run_server_jar.ps1 -SetLag -LatencyMs 250 -JitterMs 80` while a proxy is listening on the lag port
- **THEN** the control file is updated, the script exits without building or launching, and the running proxy picks up the new values.

#### Scenario: No proxy running
- **WHEN** `-SetLag` is used but nothing is listening on the lag port
- **THEN** the script reports an error and does not write a control file.

### Requirement: Control file initialized on proxy start
When starting the proxy with `-LatencyMs`/`-JitterMs`, the script SHALL write the initial control file and pass its path to the proxy.

#### Scenario: Proxy starts with initial parameters
- **WHEN** the user runs `tools/run_server_jar.ps1 -LatencyMs 100 -JitterMs 40`
- **THEN** `server/lag_control.json` contains those values and the proxy is launched with `--control` pointing at it.

