## Context

- `tools/net_jitter_proxy.py` 现为启动即定参的 asyncio TCP 保序代理；`tools/run_server_jar.ps1` 用 `-LatencyMs/-JitterMs` 启动它，用 `-LagOff` 停止。
- 需求：连接存续期间热调参，且不触发 rebuild/重启服务端。

## Goals / Non-Goals

**Goals:**
- 运行期修改 `latency`/`jitter`/`downstream_only`，立即对后续数据块生效。
- 调参路径轻量：只写一个文件，不碰 build、server、proxy 进程。
- 参数可被脚本或人工编辑驱动（便于做随时间起伏的波形）。

**Non-Goals:**
- 不做 HTTP 控制端点（避免多开端口）。
- 不改变已建立连接之外的行为；不保证已 sleep 中的数据块参数回溯。

## Decisions

1. **控制文件 + 轮询（而非 HTTP/信号）** — 无新端口、可手改、崩溃后文件仍在；0.5s 生效延迟对跑测无感。替代方案 HTTP 端点被否（多端口、代码多）。
2. **文件为运行期唯一真源，启动时由脚本写入** — 启动 CLI 参数只用于生成初值并写文件；代理启动即加载该文件，之后以文件为准。避免"重启后旧参数残留覆盖 CLI"的歧义：脚本每次启动都覆写文件。
3. **部分更新语义** — 文件中缺失的键保持当前值，便于 `-SetLag -LatencyMs 250` 只改一项。
4. **共享内存态对象** — asyncio 单线程，relay 每块读取可变对象字段，无需锁。
5. **`-SetLag` 前置校验** — 先探测 `-LagPort` 是否在监听，未运行则报错，不静默写一个无效文件。

## Risks / Trade-offs

- [Risk] 代理被替换/未运行时写文件无效 → `-SetLag` 先探测端口。
- [Risk] 手改 JSON 语法错误 → 代理捕获解析异常并保留上一组有效值，只打印告警。
- [Trade-off] 0.5s 生效延迟；可接受，换取零端口与可脚本化。

## 迁移计划

纯增量；不带 `-SetLag` 时行为与变更前一致。
