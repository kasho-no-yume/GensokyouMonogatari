## Why

Dedicated-server 跑测目前在完美网络（回环，延迟≈0）下进行，无法暴露真实玩家会遇到的延迟敏感问题：客户端预测/回摆、服务端 tick 与网络抖动交互、超时与重传边界。需要一个能在「客户端↔服务端」链路上注入可控、可复现延迟波动的机制。

## What Changes

- 新增 `tools/net_jitter_proxy.py`：纯 Python 标准库的 TCP 转发代理，逐读取块注入「基准延迟 ± 每包抖动」，保持字节顺序，双向可分别启用，socket 设 `TCP_NODELAY`。
- 扩展 `tools/run_server_jar.ps1`，以参数方式挂载/关闭该代理：
  - `-LatencyMs <int>`：基准延迟（0 = 不启用）。
  - `-JitterMs <int>`：每包抖动幅度（±）。
  - `-LagPort <int>`：代理监听端口，默认 25566。
  - `-LagDownstreamOnly`：仅对「服务端→客户端」方向注入。
  - `-LagOff`：停止现有代理并退出（不构建、不启动服务端）。
- 代理在服务端脚本点火后同样后台存活；脚本打印客户端应连接的端口。
- 重跑时先清理 LagPort 上的旧代理，再按新参数重建（幂等）。

## Capabilities

### New Capabilities
- `server-latency-injection`: 通过 `run_server_jar.ps1` 参数在客户端↔服务端链路上注入每包抖动的可复现网络延迟。

### Modified Capabilities
- （无）。`jar-server-toolchain` 的既有行为不变，仅新增可选参数。

## Impact

- 新增 `tools/net_jitter_proxy.py`；修改 `tools/run_server_jar.ps1`。
- 代理仅监听回环端口，默认 25566，需与 `server-port`（25565）及 `run/`、`run-test/` 区分。
- 依赖本机 `python`（仓库既有工具已依赖，如 `tools/rcon_cmd.py`）。
- 客户端必须连接代理端口才能获得延迟效果；不启用参数时行为与现状完全一致。
