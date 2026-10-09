## Why

两个弹幕缺陷：

1. **激光的预警线与多层观感消失。** `dd1dc72 优化弹幕` 引入的 `DanmakuRenderProbe` 把 `ALWAYS_LOD = true` 且 `dense` 恒真，于是 `effectiveGlow()` / `effectiveCore()` 永远返回 false。该降级本意只针对**球/灵符**（`danmaku-render-state` 的单通 LOD 条款只写了「球/灵符渲染器」），但实现把门控做成了全局——激光延迟期的**红色预警线**、激活期的**外发光/亮核/端盖/法阵**被一并关掉，只剩一根本体光柱。

2. **远处弹幕冻结后不销毁、堆积成灾。** 弹幕飞出模拟距离后区块虽已加载但进入「非实体 tick」状态（`ServerLevel` 不再 tick 它）。而弹幕寿命判据挂在 `age() = 基准 + tickCount` 上，`tickCount` 只在被 tick 时自增，于是冻结弹永不判寿命、永不销毁；它们一直占着 `DanmakuBudget` 的计数，逼近 `danmakuEntityCap` 时会令 BOSS 全面停发，玩家走回冻结区域时又成片「复活」继续飞。

现有 spec `danmaku-age-continuity` 基于一个**错误前提**——「服务端那份实体一直未被销毁、一直在 tick」。实际超出模拟距离后服务端同样停 tick，这正是根因。且该 spec 的「跨区块边界 BOSS 战…按其真实年龄继续飞行」与「过期就该死」的新诉求冲突，需一并修订。

## What Changes

- **激光渲染豁免密度 LOD（BREAKING 于观感，非接口）**：`LaserDanmakuRenderer` 不再读取 `DanmakuRenderProbe` 的密度门控；延迟期预警线与激活期外发光/亮核/端盖/法阵无条件渲染。仅保留显式调试层开关（`/danmaku layers`）可关断。球形/灵符的 LOD 单通策略保持不变。
- **弹幕存在性改用服务端游戏时间**：每枚弹记录并持久化「出生游戏时间」，当 `当前游戏时间 − 出生时间 > 寿命` 时销毁——**无论其所在区块是否在实体 tick 范围内**。
- **低频过期清理扫**：服务端定期（节流）扫描世界内弹幕，对**不 tick 的冻结弹**强制套用同一过期判据，回收实体计数。该扫描**只删已过期弹**，MUST NOT 按距离/脱离追踪剔除活弹（维持「存续判据与丢失原因无关」）。
- **旧存档回退**：缺出生时间键的旧存档按 `出生 = 当前游戏时间 − 已恢复年龄` 重建，行为不劣于变更前，且不报错。
- **插墙飞刀**：其「插驻剩余 tick」是独立于常规寿命的计时，必须一并纳入同一绝对截止机制。

## Capabilities

### New Capabilities

<!-- 无新增能力。 -->

### Modified Capabilities

- `danmaku-laser`: 新增「激光渲染 MUST NOT 被弹幕密度 LOD 降级」要求——预警线与各效果层在任意屏上密度下均渲染。
- `danmaku-age-continuity`: 新增「弹幕存在性以服务端游戏时间为准」要求（出生时间持久化 + 冻结弹过期清理）；修订原「弹体年龄在存档/读档后双端连续」要求中「服务端一直在 tick」的错误前提与「跨区块 BOSS 战继续飞行」场景，使之与绝对寿命相容。

## Impact

- 渲染：`client/renderer/LaserDanmakuRenderer.java`；`DanmakuRenderProbe` 的 `effectiveGlow/Core` 门控范围（激光不再消费）。球形 `SphereDanmakuRenderer` 不动。
- 实体生命周期：`entity/AbstractDanmakuProjectile.java`（出生时间字段、NBT 读写、过期自检）、`entity/LaserDanmaku.java`（其自覆写 tick 需自检）、`entity/KnifeDanmaku.java`（插驻截止）。
- 服务端：新增/复用一个服务端 tick 扫描（过期清理），可能复用 `DanmakuBudget` 的 per-level 计数与 `ServerTickEvent`。
- 配置：可能新增清理扫间隔配置项（默认开启）。
- 规格：`openspec/specs/danmaku-laser`、`openspec/specs/danmaku-age-continuity`。
- 兼容性：存档向前兼容（旧键缺失走回退）；不改同步协议与运动指纹。
