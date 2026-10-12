## ADDED Requirements

### Requirement: Zero-delay metering mode
The proxy SHALL support an optional metering mode (`--profile <path>`) that records per-connection,
per-direction wire byte counts over time windows, and MUST NOT inject delay or rewrite bytes while
metering.

#### Scenario: Profile mode with zero delay
- **WHEN** the proxy is started with `--profile <path>` and latency/jitter are 0
- **THEN** per-connection, per-direction byte counts are written to `<path>` and the relayed byte
  stream is passed through unchanged with no added delay

#### Scenario: Metering does not alter existing behavior
- **WHEN** `--profile` is not supplied
- **THEN** behavior is identical to the current latency-only proxy
