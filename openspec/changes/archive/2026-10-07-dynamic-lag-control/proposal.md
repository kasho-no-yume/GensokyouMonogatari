## Why

延迟参数目前只能在代理启动时通过 CLI 传入；调整必须重启代理，而且现有脚本路径还会连带重新 build 并再拉一个服务端。跑测时需要在不断开连接、不重启服务端的前提下实时调参（例如观察同一场景在 50ms 与 300ms 下的差异）。

## What Changes

- `tools/net_jitter_proxy.py` 新增 `--control <path>`：轮询控制文件（默认 0.5s 间隔，按 mtime 变化热加载），支持在连接存续期间动态修改 `latency` / `jitter` / `downstream_only`，缺省键保持原值。
- `tools/run_server_jar.ps1` 新增 `-SetLag`：在已运行的代理上热更新参数——只写控制文件，不 build、不重启服务端、不重启代理。
- 启动代理时（`-LatencyMs`/`-JitterMs`）自动写初始控制文件并把 `--control` 传给代理。
- `-SetLag` 在代理未运行时给出明确错误。

## Capabilities

### New Capabilities
- `dynamic-lag-control`: 通过控制文件在不重启代理/服务端的情况下实时调整注入延迟与抖动。

### Modified Capabilities
- `server-latency-injection`: 代理启动时增加控制文件通道，作为运行期参数的唯一可变来源。

## Impact

- 修改 `tools/net_jitter_proxy.py` 与 `tools/run_server_jar.ps1`。
- 新增运行期文件 `server/lag_control.json`（`server/` 已在 gitignore 内）。
- 不启用延迟参数时行为不变；`-SetLag` 为纯增量开关。
