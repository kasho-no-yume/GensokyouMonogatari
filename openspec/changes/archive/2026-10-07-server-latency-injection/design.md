## Context

- 已有 `tools/run_server_jar.ps1`：增量构建 jar、同步 `server/mods/`、WMI 点火启动 GUI dedicated server（25565）。
- 目标是让客户端连到一条可控延迟的链路，以复现真实网络抖动。
- Windows 无 `tc netem`；Clash 不注入抖动；Minecraft 走 TCP（有序字节流）。因此代理必须是保序的 TCP 转发。

## Goals / Non-Goals

**Goals:**
- 以 `run_server_jar.ps1` 参数启用/关闭延迟注入，缺省不影响现状。
- 每包抖动：`delay = max(0, latency + uniform(-jitter, +jitter))`。
- 保序、双向可选、低额外开销（`TCP_NODELAY`）。
- 代理与服务端一样后台存活、点火即返回、可幂等重建。

**Non-Goals:**
- 不做真包级乱序/丢包（那需要 IP 层工具如 WinDivert/clumsy；对 TCP 流代理不安全）。
- 不做随时间起伏的慢波（后续可加 `--wave`，本 change 不做）。
- 不改服务端自身的 `server-port`；延迟只体现在代理端口。

## Decisions

1. **自写 Python stdlib 代理（asyncio）** — 零外部下载、双向可控，契合仓库 `tools/*.py` 惯例。替代方案 toxiproxy 需下 exe 且只能快抖动，被否。
2. **逐读取块顺序处理** — 每方向一个 relay 协程：`read → sleep(delay) → write`。因是串行循环，后一块无法越过前一块，天然保序，避免破坏 TCP 流语义。块边界≈TCP 段（MC 设 NODELAY，写入即时成段）。
3. **双向默认，`-LagDownstreamOnly` 可只作用于服务端→客户端** — 真实网络两向都有抖动；但视觉体感主要由下行决定，故留开关。
4. **延迟 = 基准 ± 抖动，下限 0** — 对应 toxiproxy latency toxic 语义；每块独立随机。
5. **生命周期由参数脚本管理** — 启用时先按 LagPort 杀旧代理再重建（幂等）；`-LagOff` 停止并退出。代理用 WMI 脱离父进程，与 `jar-server-toolchain` 同一 spawn 模型。
6. **端口默认 25566** — 避开 25565；脚本打印「客户端连接 127.0.0.1:25566」。

## Risks / Trade-offs

- [Risk] 串行 sleep 使抖动在链路上累积、降低有效吞吐 → MC 本地回环包率低，可接受；如成问题可改"目标发送时刻"调度（toxiproxy 的 timestamp 方案）。
- [Risk] 代理进程残留 → `-LagOff` 与按 LagPort 探测；脚本每次运行先清理。
- [Risk] 客户端连错端口（仍连 25565）导致"没有延迟效果" → 脚本显式打印连接地址。
- [Trade-off] 保序意味着不复现真实网络的偶发乱序；这是 TCP 代理的固有边界，换来确定性与不破坏流。

## 迁移计划

纯新增 + 参数扩展；不带参数时行为与变更前一致，删除脚本即回滚。
