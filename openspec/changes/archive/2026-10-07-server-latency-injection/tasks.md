## 1. Jitter proxy script

- [x] 1.1 Create `tools/net_jitter_proxy.py` (stdlib asyncio) with args `--listen`, `--target`, `--host`, `--latency`, `--jitter`, `--downstream-only`
- [x] 1.2 Implement per-direction ordered relay: `read -> sleep(max(0, latency + uniform(-jitter, jitter))) -> write`
- [x] 1.3 Set `TCP_NODELAY` on both sockets and log startup parameters

## 2. Script parameters

- [x] 2.1 Add `-LatencyMs`, `-JitterMs`, `-LagPort`, `-LagDownstreamOnly`, `-LagOff` params to `tools/run_server_jar.ps1`
- [x] 2.2 Handle `-LagOff`: stop process listening on `-LagPort` and exit before build/launch
- [x] 2.3 When latency/jitter > 0: kill any existing proxy on `-LagPort`, spawn `python tools/net_jitter_proxy.py` via WMI detached, log to `server/lag_proxy.log`
- [x] 2.4 Print the client connect address (`127.0.0.1:<LagPort>`) when injection is active

## 3. Verification

- [x] 3.1 Unit-check the proxy against a local echo server: measure RTT â‰?2x latency and confirm byte order under jitter
- [x] 3.2 Run `run_server_jar.ps1 -LatencyMs 100 -JitterMs 40`; confirm 25566 listens, 25565 still serves, script returns immediately
- [x] 3.3 Run `-LagOff`; confirm the proxy port is released
- [x] 3.4 Run without latency params; confirm no proxy is started
