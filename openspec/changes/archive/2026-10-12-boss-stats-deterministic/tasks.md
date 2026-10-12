## 1. 源码：移除 BOSS 出生随机

- [x] 1.1 `AbstractTouhouBoss`：删除 `spawnRoll` 字段、`rollStats()`、`RandomSourceLike` 接口；
      `effectiveHp()` 改为 `referencePlayerDps(referenceTier()) * bossSeconds()`（不再乘 `spawnRoll`）
- [x] 1.2 `AbstractTouhouBoss`：删除 `spawnRoll` 的 NBT 读写（`addAdditionalSaveData` /
      `readAdditionalSaveData` 中的 `SpawnRoll` 键）；读档时忽略旧键即可
- [x] 1.3 `SummonBossEffects.spawn()`：移除 `boss.rollStats(...)` 调用
- [x] 1.4 `BossDebugCommands`（`/gs_boss spawn`）：移除 `rollStats` 调用；保留 `debugSetSeconds` /
      `debugSetHits` 作为唯一的手动设定入口
- [x] 1.5 确认 `GensokyouConfig.MONSTER_SPAWN_ROLL` 不再被任何代码引用后，决定是保留并标注为
      「杂兵专用（暂未接线）」还是随 Open Question 一并处置

## 2. 规格与文档同步

- [x] 2.1 `monster-stat-budget` spec：把「怪物数值 spawn 区间 roll」改为「杂兵与精英 roll；
      BOSS 数值确定」，并补 BOSS 确定的 scenario
- [x] 2.2 `remnant-touhou-bosses` spec：从「BOSS 数值以秒带与挨弹带约束」删除 spawn ±25% 条款
      与其「在 roll 区间内」的场景表述
- [x] 2.3 `kurodani-yamame` spec：把「秒带内」场景的 `+ spawn ±25% roll` 去掉
- [x] 2.4 `docs/mob-design-guidelines.md` §3.1：把「每次 spawn 在该区间内独立 roll」收窄到
      「杂兵/精英」，并加一行说明 BOSS 数值确定
- [x] 2.5 `boss-dev-design` skill §5 / §9：同步「秒数确定、无出生随机」的表述

## 3. 验证

- [x] 3.1 `.\tools\gradle_task.ps1 build` 通过（编译 + 既有测试不减少）
- [x] 3.2 grep 确认 `rollStats` / `spawnRoll` / `RandomSourceLike` 不再有任何引用
      （仅剩 `GensokyouConfig` 的 config 键名 `"spawnRoll"`——有意保留，改名会重置玩家配置）
- [x] 3.3 实机：`/gs_boss spawn <id>` 连续两次，确认有效生命 / 弹伤 / 挨弹数完全一致
- [x] 3.4 实机：确认移动、符卡、掉落等行为不受影响（本变更只动数值确定化）
- [x] 3.5 记录最终结论与 `forTier` 死代码审计结果，回填 design.md 的 Open Questions
