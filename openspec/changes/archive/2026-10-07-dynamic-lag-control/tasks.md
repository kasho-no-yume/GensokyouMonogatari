## 1. Proxy control channel

- [x] 1.1 Add `--control <path>` to `tools/net_jitter_proxy.py`
- [x] 1.2 Introduce a mutable shared config object; relay reads latency/jitter/downstream_only per chunk
- [x] 1.3 Add a poller task (0.5s, mtime-based) applying partial updates and tolerating malformed JSON

## 2. Script integration

- [x] 2.1 On proxy start, write `server/lag_control.json` and pass `--control` to the proxy
- [x] 2.2 Add `-SetLag` handling before build: require a proxy on `-LagPort`, merge/rewrite the control file, then exit
- [x] 2.3 Support partial updates via `$PSBoundParameters` so unspecified keys are preserved

## 3. Verification

- [x] 3.1 Start proxy with latency 100; measure MC ping RTT through the proxy
- [x] 3.2 `-SetLag -LatencyMs 300 -JitterMs 0`; confirm RTT increases without reconnect and proxy logs the reload
- [x] 3.3 Confirm `-SetLag` with no proxy running errors out and writes nothing
