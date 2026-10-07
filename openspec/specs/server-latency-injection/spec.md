# server-latency-injection Specification

## Purpose
TBD - created by archiving change server-latency-injection. Update Purpose after archive.
## Requirements
### Requirement: Parameter-driven latency injection
The server launch script SHALL accept parameters to enable per-packet latency and jitter injection on the client-to-server link.

#### Scenario: Latency parameters supplied
- **WHEN** the user runs `tools/run_server_jar.ps1 -LatencyMs 120 -JitterMs 40`
- **THEN** a TCP proxy listens on the lag port (default 25566) forwarding to the server on 25565, and the script prints the address the client should connect to.

#### Scenario: No latency parameters
- **WHEN** the script runs without `-LatencyMs`/`-JitterMs`
- **THEN** no proxy is started and behavior is identical to the plain jar-server launch.

### Requirement: Per-packet jitter with base latency
The proxy SHALL delay each relayed chunk by `max(0, latency + uniform(-jitter, +jitter))` milliseconds while preserving byte order.

#### Scenario: Jitter applied per chunk
- **WHEN** traffic passes through the proxy with latency 120 and jitter 40
- **THEN** each chunk incurs a delay within 80..160 ms, and chunks are delivered in their original order.

### Requirement: Directional control
The proxy SHALL apply injection to both directions by default, and to the server-to-client direction only when `-LagDownstreamOnly` is set.

#### Scenario: Downstream-only mode
- **WHEN** `-LagDownstreamOnly` is supplied
- **THEN** only the server-to-client direction is delayed; client-to-server traffic passes through without added delay.

### Requirement: Idempotent lifecycle and explicit stop
Re-running the script SHALL replace any existing proxy on the lag port with the new parameters, and `-LagOff` SHALL stop the proxy and exit.

#### Scenario: Re-run with new parameters
- **WHEN** the script is run again with different `-LatencyMs`/`-JitterMs`
- **THEN** the previous proxy on the lag port is stopped and a new proxy with the new parameters is started.

#### Scenario: Stop the proxy
- **WHEN** the user runs `tools/run_server_jar.ps1 -LagOff`
- **THEN** any proxy on the lag port is stopped and the script exits without building or launching the server.

### Requirement: Order-preserving TCP relay
The proxy SHALL forward the TCP byte stream without reordering bytes.

#### Scenario: Sequential chunks stay ordered
- **WHEN** multiple chunks are relayed with differing random delays
- **THEN** they are written to the target in the same order they were read.

