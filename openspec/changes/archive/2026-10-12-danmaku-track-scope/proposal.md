> **状态：存根。** 本变更只有 proposal，没有 design / tasks / spec。
> **不要开始实现**，直到下面的门槛被满足。

## 门槛（先测，再决定要不要做这个变更）

**生成突发的实测带宽基线。** 具体要回答：

1. 一波 N 枚弹的生成，实体数据（`DanmakuMotionState.PARAM_COUNT` = 53 个 int）实际占
   多少字节？N 取 16 / 48 / 128，测 `ClientboundSetEntityDataPacket` 的实际条数与字节。
2. 折算成速率：符卡常见的发射节奏下（`BIG_FAIRY_SHOT_INTERVAL` 默认 60 tick），
   每秒生成突发多少字节？与位置包、玩家交互包相比占多少比例？
3. 共享参数后能省多少？——上界就是 2.1 的结果，因为轨道化只作用于生成期，
   稳态 `SynchedEntityData` 本来就是零。
4. 作为对照，量一次**位置包**的稳态开销。当前 `ModEntityTypes` 里
   `sphere/knife/talisman` 的 `updateInterval = 2`、`laser = 1`，而 `lerpTo`
   落地后已经**完全不消费**位置包（`AbstractDanmakuProjectile#lerpTo` 只计数）。
   4.1 很可能是最大的那一份，而它**不需要**本变更的任何东西 ——
   调大 `updateInterval` 即可，属于独立决策。

## 门槛实测结果（2026-10-12，`profile-boss-network-cost`）

两场真实 BOSS 战（Yamame，`gs_boss spawn`，零延迟计量），服务端包类归因 + 线上字节计量。
关键读数（第二场，193.4s 窗口，逻辑 54.3 MB / 线上 13.6 MB）：

| 问 | 实测答案 |
|---|---|
| 1. 单弹生成成本 | `AddEntity:sphere_danmaku` = **56 B/颗**（压缩前）；每颗另有一份 `SetEntityData`（全部 SetEntityData 均值 46 B，弹幕占约 40%）⇒ 生成 ≈ **~100 B/颗** |
| 2. 折速率 | 7841 颗 / 193.4s ≈ **40 颗/s 均值**；同屏并发 **峰值 1169 / 均值 627** |
| 3. 共享参数可省上界 | 生成期实体数据 ≈ 0.8% 逻辑字节 ⇒ 本变更能碰到的上限 **< 1%** |
| 4. 位置包稳态开销对照 | 弹幕位置包 ≈ 并发 627/s × 193.4s ≈ **1.1 MB 逻辑**（`Pos` 合计 1.45 MB）；且是**纯浪费通道**（客户端 `lerpTo` no-op 全丢），`updateInterval` 已由 2 调到 20 |

全局构成：**区块流式 `LevelChunkWithLight` = 90.2%**；弹幕相关合计（生成 + 位置包 + `danmaku_snapshot` + `danmaku_age` + `danmaku_calibration` + `SetEntityData` 分摊）≈ **4–5 MB ≈ 8–10%**。

**⇒ 判定：本变更应当被「放弃」，不是降级实施。** 它只作用于生成期参数，实测上限 < 1%，
而弹幕总量本身也只占约一成、区块占九成——为不到 1% 引入轨道层（scopeId/version/实例索引/
生命周期）不成立。

**弹幕侧真正值得单独看的两个项（都不是本变更的范畴）：**
- `danmaku_snapshot` = **2.05× 弹数**（16105 / 7841）：一发来自追踪、其余来自 TARGET 段换向
  (`sendTurnSnapshot`) 或客户端重同步请求——是弹幕侧最大单项（1.46 MB），值得确认其归属。
- 位置包纯浪费项（`updateInterval=20` 之后仍 ≈ 1.1 MB/场）：若要再削，方向是让弹幕不产生
  位置包，而不是参数共享。

**为什么曾经放不下**：原因见下面 Why（门槛先于决策、原 tasks 与 design 对不上）。现在门槛已过，
结论是「不做」，于是这个变更可以从「悬置存根」结案。

### 原判定句（保留）

**若 3 的收益不足以justify引入轨道层，本变更应当被放弃而不是降级实施。**
理由写在下面的 Why 里。

## Why（为什么它曾经和时钟捆在一起，现在被拆开）

原 `danmaku-timeline-sync` 把两件事放在一个变更里：

- **服务器时间轴** —— 修一个真实且严重的缺陷（速率失配导致自检静默失效，
  并使 `danmaku-event-sync` 的「按事件时间插入状态」无法实现）；
- **轨道层** —— 共享编队参数、轨道快照、`scopeId`/`scopeVersion`、实例索引、生命周期。

两者的耦合只有一处：都提到「服务器时间」。而 `danmaku-event-sync` 需要的是**时间**，
不是轨道参数 —— 这一点经逐条核对其首批事件得到确认：

| 事件 | 寻址粒度 | 现有基元是否已足够 |
|---|---|---|
| `TARGET_CHANGED` | 单枚灵符 | `TalismanDanmaku` 的 `DATA_TARGET_ID` 已在 `SynchedEntityData` |
| `TARGET_LOST` | 单枚灵符 | 同上（另见下） |
| `REDIRECT` | 单枚弹 | 实体 UUID + `revisionFor(age)` |
| `GEOMETRY_KEYFRAME` | 一枚激光 = 一个实体 | 同上 |
| `PHASE_CHANGED` | 可能需要轨道 | 但 event-sync 自己的 design §4 已设门槛「只有在阶段不能由轨道时间重建时才启用」，而阶段边界已列入时间轴 ⇒ 大半被设计掉了 |
| `DESPAWN` / `SPLIT` | 条件性 | 实体移除已覆盖 |

event-sync 的信封定义里 `scopeId` 本就写作「轨道**或**实体身份」，
其 Open Question 也建议「实体生命周期和目标状态保留实例身份」。
⇒ **轨道层买到的是 track-wide 事件的 O(1) 寻址，而唯一需要它的 `PHASE_CHANGED`
已被它自己的门槛设计掉了。**

所以 event-sync MUST NOT 等本变更。反过来，本变更也不能等 event-sync ——
`GEOMETRY_KEYFRAME` 与 `REDIRECT` 若将来改用轨道 scope，届时再定。

## 拆开的另一个理由：原 tasks.md 与 design.md 对不上

原 `design.md` §6 的迁移顺序是「测量基线 → … → 轨道快照/实例 → … → 关闭位置驱动」，
并写明「只有实测确认收益才做」。但原 `tasks.md` §3（轨道快照与实例身份，5 条任务）
是**无条件**的。

两者对不上的后果不是文档瑕疵：它让那一个变更**可能永远归档不掉** ——
§3 的价值取决于一个尚未做的基线，基线可能证明不值得，于是 §3 悬着、变更悬着，
而 event-sync 在等它。OpenSpec 以变更为归档单位，所以「先做前两节」在纸面上无法表达。

拆开之后，「哪部分无条件下一步做完」变成结构而不是默契。

## What Changes（门槛通过后才展开）

- 把共享的编队帧 / 速率曲线参数从「每枚弹各带一份」改为「按轨道下发一份，
  实体只带轨道引用与实例索引」。
- 轨道快照：`scopeId` / `scopeVersion` / 起始时间与年龄 / 生命周期 / 完整参数 /
  实例索引范围。
- 实例身份：实体 id 会复用，跨追踪周期 MUST 用 UUID 或等价身份。
- 生命周期：轨道结束、实体移除、切世界时的状态清理。
- 按实体类型灰度，以及关闭对应类型位置驱动的带宽门槛。

## 术语

**本变更不使用「轨道」这个词。** 本仓库里「轨道」已经指
`danmaku/track/Track.java` —— 「一条轨道 = 一张符卡内的一条独立节拍时间轴」，
且已有 spec `danmaku-track-composition`。原变更引入的「轨道」（共享运动参数的载体）
是同名同字不同物，会让下游 spec 直接读不懂。

本变更统一用 **scope** 指代共享运动参数的载体，与变更名一致。

## 与其它变更的关系

- **不阻塞** `danmaku-event-sync`：event-sync 的时间轴前置由 `danmaku-timeline-sync`
  的 T1 提供。
- **不阻塞** `danmaku-timeline-sync`：T1（时钟）与 T2（纯函数求值 + 直线读档重算）
  都不依赖本变更。
- 反向依赖：若将来 `GEOMETRY_KEYFRAME` / `REDIRECT` 决定改用 scope 寻址，
  届时本变更须先落地。
