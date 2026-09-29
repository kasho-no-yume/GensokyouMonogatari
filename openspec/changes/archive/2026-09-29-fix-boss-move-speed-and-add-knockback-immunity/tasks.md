## 1. 接通移速配置

- [x] 1.1 `AbstractTouhouBoss` 游走调用 `setWantedPosition(..., 1.0D)` → `setWantedPosition(..., moveSpeed())`
- [x] 1.2 确认 `FairyMoveControl` 未读取 `MOVEMENT_SPEED` / `FLYING_SPEED`（仅读 `this.speedModifier`），保持现状
- [x] 1.3 重写 `GensokyouConfig.BOSS_MOVE_SPEED` 注释：只保留"乘区"一种读法，删掉"0.34格/0.17格"的格/tick 举例
- [x] 1.4 **实机测定**：在 `bossMoveSpeed = 0.17` 下测出 BOSS 的实际水平位移速率（design Q1），记录实测值 `已由用户实机验证`

## 2. 实机确认（依赖 1.4）

- [x] 2.1 **无需改动 `bossMoveSpeed` 默认值**——既有 0.17 乘区即为目标终速 ~2 格/秒，本变更只改接线
- [x] 2.2 实测 BOSS 水平位移速率；与 2 格/秒目标对比，若显著偏离则**以实测重标 `bossMoveSpeed` 默认值** `已由用户实机验证`
- [x] 2.3 观察距离带内游走是否仍覆盖上/下/左/右（spec 要求）。2 格/秒下可能显得迟缓；若退化为"几乎不动"，上调 `bossMoveSpeed`（2~4 格/秒区间）而非改结构 `已由用户实机验证`
- [x] 2.4 **MUST NOT** 为追手感去调 `FairyMoveControl.ACCEL_FACTOR`——两个旋钮会耦合、无法独立归因（已遵守：`ACCEL_FACTOR` 保持 0.0533 未动）

## 3. 击退免疫

- [x] 3.1 `AbstractTouhouBoss.bossAttributes()` 增列 `Attributes.KNOCKBACK_RESISTANCE, 1.0D`
- [x] 3.2 逐个核验四只 BOSS（`BigFairyEntity` / `KitsuneBiEntity` / `KuzumonoEntity` / `NomenMaskEntity`）均经由该工厂、无绕过路径
- [x] 3.3 实机验证：钻石剑击打 BOSS 不推动；TNT 爆炸与活塞同样不推动 `已由用户实机验证`
- [x] 3.4 确认无任何符卡/机制依赖击退——全仓库 `KNOCKBACK_RESISTANCE` 仅本次新增的写入点、无任何读取方；`isPushable()` 仅 `TouhouNpcEntity` override（非 BOSS，且该机制与击退无关）；四只 BOSS 类均无 `setDeltaMovement` 调用

## 4. 验证与收尾

- [x] 4.1 跑 `.\tools\gradle_task.ps1 build` 确认编译通过（BUILD SUCCESSFUL，含测试）
- [x] 4.2 排序依赖核实——**结论：不存在依赖，本任务为笔误，已更正**。本变更的验收判据是「实测位移速率」「游走覆盖上/下/左/右」「击退免疫」三项，**全部与武器伤害/弹耗正交**：位移速率由 `speedModifier` 与 `FlyingMob.travel` 阻力决定，击退免疫由 attribute 决定，均不受 `rebalance-tier1-spirit-and-danmaku-cost` 影响。`rebalance-tier1` 也不必等本变更落地
- [x] 4.3 release note 写明：4 只东方 BOSS 移速手感整体变慢，且**旧手感无法通过 config 回退**（它来自硬编码）
- [x] 4.4 release note 写明 `bossMoveSpeed` 语义澄清（乘区，非格/tick），玩家可自行调参
