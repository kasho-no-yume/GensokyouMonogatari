## Why

召唤型东方 BOSS 目前**每次出生都会把自己的生命值随机 ±25%**：`AbstractTouhouBoss.rollStats()`
令 `spawnRoll = 1 ± MONSTER_SPAWN_ROLL(0.25)`，写进 NBT、存活期间不重掷，而
`effectiveHp = 参照DPS(referenceTier) × bossSeconds × spawnRoll`。

对 BOSS 而言这是设计错误：

- **BOSS 是玩家要学习、复盘、反复挑战的「一段戏」**，它的时长与难度必须是**可调的确定值**。
  随机寿命让配平与实测无法归因——「这次打得久」到底是数值不对，还是掷到了 +20%？
- **随机直接冲掉了「秒带」这个目标**：`bossSeconds` 是精确的秒数目标，而 ±25% 让实际落点
  横跨相邻两阶的带宽（T2 标称 260s 掷后在 [195, 325]，上沿已越过 T3 下沿 285）。
  于是「把某只 BOSS 定在 T2 偏上」这种调整在实现层**根本做不到**。
- **它违反秒带语义**：`docs/mob-design-guidelines.md` §3.1 说秒带是弹性目标而非保证——
  弹性来自无敌时间与 BOSS 移动造成的有效 DPS 下降，**不应**再叠加一层人为随机。

同时审计发现：承载「同阶怪物数值预算」带宽的 `MonsterStatBudget.forTier(...)` 与
`MONSTER_TRASH/ELITE_HP/HITS`、`MONSTER_TIER_SCALE` **没有任何生产或测试调用点**
（唯一引用 `MonsterStatBudget` 的生产代码只调 `referencePlayerDps/Ehp`）。
即「出生随机」当前**只落在 BOSS 身上**，与文档意图（所有怪都按带 roll）完全相反。

本变更把随机从 BOSS 移除，使 BOSS 数值确定，并记录 `forTier` 死代码的审计结论。

## What Changes

- **BOSS 数值确定化**：撤销召唤型 BOSS 的 ±25% 出生随机。`effectiveHp` 恒等于
  `参照DPS(N) × bossSeconds`，`baseDanmakuDamage` 恒等于 `参照EHP(N) / bossHits`。
- **移除随机机制**：删除 `spawnRoll` 字段、`rollStats()`、`RandomSourceLike` 及其 NBT 持久化，
  并清理 `SummonBossEffects.spawn()` 与 `/gs_boss` 调试命令里的调用点。旧存档里残留的
  `SpawnRoll` 键被忽略，无需迁移。
- **难度表达不变**：难度差异继续由 `bossHits` 与轨道 `damageScale` 承担，MUST NOT 借确定化
  重新配平既有 BOSS（对称随机均值为 1.0，撤销只去除方差）。
- **审计 `MonsterStatBudget.forTier` 死代码**：记录其无调用点的事实，并在 design 中给出
  「接线杂兵/精英 roll」或「删除死代码」的后继选项（本变更不做决定，见 Open Questions）。
- **同步规则文档**：`docs/mob-design-guidelines.md` §3.1 与 `boss-dev-design` skill 中
  「每次 spawn 在该区间内独立 roll」的表述收窄为「杂兵/精英」。
- **`/gs_boss spawn <id> [秒数] [挨弹数]`** 从「先掷再覆盖」变成纯粹的手动设定入口。

## Capabilities

### Modified Capabilities

- `monster-stat-budget`: 「怪物数值 spawn 区间 roll」收窄为**杂兵/精英**；新增 BOSS 数值确定的规范。
- `remnant-touhou-bosses`: 「BOSS 数值以秒带与挨弹带约束」移除 spawn ±25% roll 条款，
  改为 BOSS 生命严格等于 `参照DPS × 秒数`。
- `kurodani-yamame`: 「数值以秒带与挨弹带约束」的「秒带内」场景去掉 roll 表述。

## Impact

- 源码：`entity/AbstractTouhouBoss`、`ritual/behavior/SummonBossEffects`、`command/BossDebugCommands`。
- 文档：`docs/mob-design-guidelines.md` §3.1；`boss-dev-design` skill §5。
- 数值：**均值不变，仅去方差**。各 BOSS 的 `bossSeconds` / `bossHits` / config 默认值一律不动。
  新 BOSS（如八云蓝）可自本变更起精确设定秒数。
- 兼容：旧存档中 BOSS 的 `SpawnRoll` NBT 键被忽略；已存在的 BOSS 实体其 `maxHealth` 已由
  `applyStats` 落定，不会回溯，行为无跳变（下次召唤即确定值）。
- 测试：无既有测试引用 `rollStats` / `spawnRoll` / `MonsterStatBudget`（已核实），故无测试破坏。

## Non-Goals（本变更明确不做）

- **不给杂兵/精英接线 roll。** 这需要把 `MonsterStatBudget.forTier` 接进生成路径，范围与风险
  都独立，留给后继变更（见 design.md 的 Open Questions）。
- **不重新配平任何 BOSS。** 去随机与改数值是两件事，混做会让下一次实测无法归因。
- **不修改秒带数值、不修改挨弹数、不修改任何 `damageScale`。**
- **不触碰 BOSS 的移动、符卡、掉落与渲染实现。**
