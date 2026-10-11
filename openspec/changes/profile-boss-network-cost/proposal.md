## Why

弹幕架构的核心目标之一是「最低带宽 + 最高表现质量」，但**一场真实 BOSS 战到底消耗多少
链路字节，从未被测过**。`danmaku-track-scope` 把「生成突发的实测带宽基线」写成进门门槛
（`tasks.md` 至今标着「带宽实测未做」），却没有任何工具能产出它。

现状是：仓库里三份文档给出三个差两个数量级的估计（500 弹 4 KB/s、48 弹×2 轮/s 14 KB/s、
环卡 8 B/s/玩家），因为它们各自在量不同的东西。没有实测，就无法回答「上千弹时会不会在
较低带宽的链路上出问题」——而这正是弹幕密度继续提升前必须知道的门槛。

同时，现有延迟模拟工具 `tools/net_jitter_proxy.py` 已经**站在链路上、看得到每一个字节**，
却只用来注入延迟。把它从「延迟注入器」扩成「链路字节观测点」，是这份基线最廉价的来源——
不需要 Wireshark、不需要额外抓包工具。

## What Changes

观测分两层，各补对方缺的一块：

- **线路字节层（代理）**：`net_jitter_proxy.py` 新增 `--profile <path>`，在**不注入任何延迟**
  的情况下，按时间窗口记录每个连接、每个方向的**线上字节量**（含压缩后效果）与时序。
  它只数字节、不解析协议，故不受加密/压缩影响，永远有效。这是 profile 曲线的骨架。
- **包类归因层（服务端）**：新增一个服务端 Netty 出站观测 handler，按 `Packet` 类名
  （`AddEntity` / `SetEntityData` / `MoveEntity` / `RemoveEntities` / 区块 / 交互 / …）、
  按实体类型、按玩家、按 tick 统计字节。它位于帧编码器之上，**免疫加密与压缩**，
  给出「谁的字节是什么」。这是曲线的分解。
- **游戏侧「事件标记流」**：BOSS 召唤 / 死亡 / 符卡切换时写一行带服务端 tick 与墙上时钟的
  marker（JSONL）。符卡切换**已有广播路径**（`AbstractTouhouBoss#announceCardName`），
  标记流只是把它同时落盘。
- **离线合成工具** `tools/boss_net_profile.py`：把代理原始输出、服务端归因输出与 marker 流
  按时间 join，产出一份 **BOSS 战运行账**（run ledger）：
  - CSV：逐窗口字节序列 + 包类分解 + 关联的符卡段；
  - **PNG profile 图**（用已有的 PIL 渲染，不引入 matplotlib）；
  - 文本摘要：峰值、均值、单弹放大率、各符卡占比、两层字节的对账差。
- **一键接线**：`run_server_jar.ps1` 新增 `-Profile`，以零延迟启动代理计量模式并指定输出路径。

**产出物**：跑一场 `gs_boss spawn` 的 BOSS 战，得到一份可复现的「召唤→死亡」带宽 profile，
回答 `danmaku-track-scope` 的门槛问题。

## Capabilities

### New Capabilities

- `boss-network-profile`: 从服务端到单个客户端的链路消耗观测——事件标记流、包类/实体归因的
  服务端观测、BOSS 战运行账（逐窗口字节序列 + 包类分解 + 符卡归因）、CSV/PNG/文本报告产出，
  以及「不需要外部抓包工具」这一约束。

### Modified Capabilities

- `server-latency-injection`: 代理除延迟注入外，SHALL 支持一个**计量模式**（零延迟、只观测），
  记录每连接每方向的线上字节量与时序。现有延迟语义不变，且计量模式不解析协议层内容
  （故对加密/压缩免疫）。

## Impact

- 修改 `tools/net_jitter_proxy.py`（新增计量模式）、`tools/run_server_jar.ps1`（新增 `-Profile`）。
- 新增 `tools/boss_net_profile.py`（离线合成 + 报告）。
- 新增服务端 Netty 出站观测 handler 与游戏侧事件标记落盘（挂在既有 BOSS 生命周期/符卡切换钩子）。
- 新增关闭/查询该观测的命令入口（权限 2，与 `/gs_boss` 一致）。
- 运行期产物落在 `server/`（已在 gitignore 内），不污染仓库。
- 依赖本机 `python`（仓库既有工具已依赖）。报告图用 PIL，**不引入新依赖**。
- 不改任何弹幕/同步行为、不改任何网络包格式、不改 `updateInterval` 或追踪范围。

## Non-Goals（本变更明确不做）

- **不做写入限速 / 瓶颈 sweep。** 本变更只做「观测」，因为门槛先于决策：只有先测出
  offered load 曲线，才知道该模拟多窄的链路。合成瓶颈（token bucket + 带宽档位扫描）
  留给后继变更 `simulate-link-bottleneck`，其设计已在本变更 `design.md` 中留接口。
- **不做丢包 / 乱序模拟。** relay 在 TCP 之上，丢块 = 永久数据损坏；真丢包需 IP 层工具
  （WinDivert/clumsy），不在本变更。
- **不在代理侧解析 Minecraft 协议。** 见 `design.md` 决策：压缩阈值状态机 + 登录/游玩两套
  包 id 命名空间 + 版本耦合，代价高于收益；包类归因改由服务端 handler 承担，更稳更全。
- **不做完整协议解码。** 服务端 handler 只按包类与实体类型归因，不逐字段解析。
- **不要求 Wireshark / pktmon 等外部抓包工具。** profile 由链路上的代理与服务端自身产出。
