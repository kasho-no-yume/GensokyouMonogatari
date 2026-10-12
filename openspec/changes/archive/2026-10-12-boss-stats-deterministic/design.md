## Context

`AbstractTouhouBoss` 目前用 `spawnRoll` 给 BOSS 有效生命加 ±25%：

```java
// effectiveHp() = referencePlayerDps(referenceTier()) * bossSeconds() * spawnRoll
// rollStats(): spawnRoll = 1 + (r*2-1) * MONSTER_SPAWN_ROLL(0.25)
```

`rollStats` 由 `SummonBossEffects.spawn()` 与 `BossDebugCommands`（`/gs_boss spawn`）调用，
`spawnRoll` 持久化到 NBT。`baseDanmakuDamage` **不**含 `spawnRoll`（只随 `bossHits` 变），
即弹伤本来就不随机。

规则来源：`docs/mob-design-guidelines.md` §3.1「每次 spawn 在该区间内独立 roll（±25%）」
与 `monster-stat-budget` spec 的同名需求。二者都写的是「**含 BOSS**」。

实测事实（grep 全仓）：

| 引用者 | 用到了什么 |
|---|---|
| `AbstractTouhouBoss` | `rollStats`——唯一使用 `MONSTER_SPAWN_ROLL` 的地方 |
| `BalanceTestService` | 只用 `referencePlayerDps` / `referencePlayerEhp` |
| `SpellCardScaling` | 只在 javadoc 提及 |
| `MonsterStatBudget.forTier` | **无任何调用点**（生产与测试均无） |
| `MONSTER_TRASH/ELITE_HP/HITS`、`MONSTER_TIER_SCALE` | 只在 `forTier` 内被读 |

即：文档说「所有怪都掷」，实现是「只有 BOSS 掷，杂兵根本不掷」，而承载杂兵带宽的 `forTier`
是死代码。

## Goals / Non-Goals

**Goals**

- BOSS 有效生命 / 弹伤 / 挨弹数为**确定值**，与 config 一一对应。
- 秒带调优成为可实现的动作（「T2 偏上」= 直接写数值）。
- 移除随机机制与其 NBT 面。

**Non-Goals**

- 不给杂兵/精英接线 roll。
- 不重新配平既有 BOSS。

## Decisions

### D1 直接删除随机机制，而非「把 spread 设为 0」

删除 `spawnRoll` 字段、`rollStats()`、`RandomSourceLike`、NBT 读写与两处调用点，
`effectiveHp()` 回归 `参照DPS × 秒数`。

理由：把 `MONSTER_SPAWN_ROLL` 设 0 会留下一条**仍然存在**的随机分支与一个会误导后续作者的
config 键（「为什么有 roll 旋钮却恒为 0」）。既然结论是 BOSS 不该有随机，就应把机制删掉；
将来若给杂兵接线，用的应是 `forTier` 的带宽，而不是复用 `MONSTER_SPAWN_ROLL`。

### D2 不改任何 BOSS 的数值，也不重新配平

±25% 是对称的，均值为 1.0。删除后**期望强度不变，仅方差归零**。因此
`bossSeconds` / `bossHits` / config 默认值一律不动，避免把「去随机」和「改数值」两件事混在一起，
让后续实测可归因。

### D3 难度继续由 hits 与 damageScale 表达

弹伤本就不 roll；确定化后 BOSS 的难度差异仍由 `bossHits`（全局单发基准）与每轨
`Track.damageScale()`（靠后符卡加压）承担，与既有设计一致。

### D4 死代码不在本变更处理

`MonsterStatBudget.forTier` 的处置（接线还是删除）见 Open Questions，本变更只记录结论。

## Open Questions

- **`MonsterStatBudget.forTier` 死代码怎么处置？** 两条路：
  (a) **接线**：为杂兵/精英在生成时按其带宽 roll（兑现 `mob-design-guidelines` §3.1 的原始意图）；
  (b) **删除**：若认定「杂兵数值固定」才是想要的，删掉 `forTier` + `MONSTER_TRASH/ELITE_*` +
  `MONSTER_TIER_SCALE`，并同步 §3.1。
  **建议另开变更 `wire-or-drop-monster-band-roll`，待用户定夺。**
- **旧存档中已 roll 的 BOSS 实体**：其 `getMaxHealth` 已由 `applyStats` 落定，变更后不会回溯。
  这属可接受的过渡（下次召唤即确定值）；如需强行归一，需要一次读档迁移，代价大于收益。
