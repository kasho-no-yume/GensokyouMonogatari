# Tasks: add-shujou-yoroku-ritual

## 1. 前置核验与配置

- [x] 1.1 在 sources jar 复核符号：`EntityType#getDefaultLootTable`、`LootContextParamSets.ENTITY`（仅强制 `THIS_ENTITY`）、`LootContextParams.{THIS_ENTITY,ORIGIN,DAMAGE_SOURCE,ATTACKING_ENTITY,DIRECT_ATTACKING_ENTITY,LAST_DAMAGE_PLAYER}`、`LootTable#getRandomItems(LootParams,RandomSource)`、`EntityType#create(Level)`
- [x] 1.2 复核 `EnchantedCountIncreaseFunction` / `LootItemRandomChanceWithEnchantedBonusCondition` 读 `ATTACKING_ENTITY` 装备、`LootItemKilledByPlayerCondition` 读 `LAST_DAMAGE_PLAYER`、`NeoForgeEventHandler` 已自动 `FakePlayerFactory.unloadLevel`
- [x] 1.3 `GensokyouConfig` 新增旋钮（COMMON）：`shujouBaseCapacity`=40000、`shujouCapacityMult`=20、`shujouBaseSpCost`=10000、`shujouSpCostMult`=4、`shujouCycleTicks`=1200、`shujouSpiritInRate`=1000000、`shujouL2OutputMult`=4、`shujouLootingLevel`=3

## 2. 行为与框架触点

- [x] 2.1 新建 `ritual/behavior/ShujouYorokuBehavior.java`（`implements RitualBehavior`，启停型 `serverTick`；不实现 `SpiritBank`）
- [x] 2.2 `RitualBehaviors` 增常量 `SHUJOU` 与 `register(SHUJOU, new ShujouYorokuBehavior())`
- [x] 2.3 `RitualCoreBlockEntity.getCapacity()` 增 `patternId == SHUJOU` 分支 → `shujouCapacity(level)`
- [x] 2.4 覆写 `spiritInRatePerSecond` 返回 `shujouSpiritInRate`（固定不随阶）；不覆写 out
- [x] 2.5 世界无关纯内核：`speciesCost(level)`、`totalCost(level,n)`、`capacity(level)`、`l2Multiplier(level)`、`isValidCodex(stack)` 静态函数

## 3. 玩家击杀上下文与战利品掷骰

- [x] 3.1 专用 `FakePlayer` 凭证：固定 `GameProfile`（稳定 UUID + 可辨识名），经 `FakePlayerFactory.get(serverLevel, PROFILE)` 获取（不入世界；NeoForge 自动清理）
- [x] 3.2 L1+ 给凭证主手装配 `minecraft:looting` = `shujouLootingLevel` 的剑（仅此附魔）；L0 置空手；每结算前确保状态匹配当前等级
- [x] 3.3 `DAMAGE_SOURCE = serverLevel.damageSources().playerAttack(token)`；`LootParams` 显式写入 `THIS_ENTITY`/`ORIGIN`(核心中心)/`ATTACKING_ENTITY`/`DIRECT_ATTACKING_ENTITY`/`LAST_DAMAGE_PLAYER`
- [x] 3.4 逐 species：`entityType.create(serverLevel)` 造默认状态临时实体（null → 跳过）→ `moveTo` 核心中心 → 取 `getDefaultLootTable()` 掷表
- [x] 3.5 扫描去重：遍历 `RitualPedestals.positions(match)`，仅收 `CodexOfBeingsItem.isFull` 的典籍，按 species 去重（规范序），未知 species 跳过；不消耗典籍
- [x] 3.6 结算流程：N==0 空转 → `canCover(N×cost)` 全有全无 → `payCost` → 逐 species 掷表 → L2 全产物 ×`shujouL2OutputMult` → 聚合拆栈 → 空投 → `setActionCooldown(shujouCycleTicks)` → 光柱 → 通知 viewers

## 4. 产物投递与 FX

- [x] 4.1 聚合投递复用 `WatatsumiBehavior.dropStacks`（XZ 圆盘 `RITUAL_OUTPUT_DROP_RADIUS` + Y 用 `ToolSacrificeBehavior.dropHeight`），不设无敌
- [x] 4.2 `RitualBehaviors.sacrificeColorIndex(SHUJOU)` 返回 5；`RitualCoreRenderer.pillarColor` 增 `case 5` 紫色；`RitualRenderState` 色索引注释扩为 `0..5`；`syncRenderState` 将 SHUJOU 纳入光柱路径
- [x] 4.3 仅结算瞬间 `triggerSacrificeFx`（复用既有，不新增逐 tick 粒子）

## 5. UI / 文案 / 调试

- [x] 5.1 `uiInfo`：状态行（无典籍/缺灵力/冷却中/结算中）+ 有效 species 数/祭品台数 + 单次成本（紧凑值 + tooltip 精确值）+ 周期秒数 + 等级 + L2 状态
- [x] 5.2 `zh_cn`/`en_us` 补键（状态行、标签、成本 tooltip）
- [x] 5.3 `python tools/lang_audit.py` 退出码 0
- [x] 5.4 `DebugCommands` 增 `/gs_debug shujou <corePos>`（结算态摘要 + 试掷）与无结构依赖的 `/gs_debug shujou_roll <species> <level>`（直验 FakePlayer+抢夺掷表）

## 6. 测试与验证

- [x] 6.1 `gradlew compileJava` 通过
- [x] 6.2 `runServer --console=plain` 启动：`Done (2.287s)`、`Loaded 15 ritual patterns`、无 registry/loader 错误；崩溃为测试数据包 `forceload` 触发的 Server Watchdog（与本变更无关）。FakePlayer 运行期路径不在加载阶段触发，归 6.3/6.4
- [x] 6.3 运行期探针断言：去重后成本（3 本同种=1×；4 种=4×）；0 阶容量=40000 恰覆盖满台；短缺 1 点不产；L0 无抢夺有玩家击杀掉落；L1 抢夺 3 生效；L2 产物 ×4；特殊掉落（苦力怕头/唱片/装备）不出现
      - 无结构：`/gs_debug shujou_roll "minecraft:creeper" 1`（应无 music_disc/head）、`/gs_debug shujou_roll "minecraft:zombie" 0`（可有铁锭=玩家击杀）vs `2`（抢夺3 数量更高）
      - 有结构：`/gs_debug shujou <corePos>` 打印计数/成本/容量/试掷
- [x] 6.4 实机验收（用户执行）：摆满典籍启动后每分钟自动产出、紫色光柱仅产出时出现、空投落点、路由受灵、典籍不被消耗；`validate_ritual_pattern.py` 确认 pattern 不受影响
