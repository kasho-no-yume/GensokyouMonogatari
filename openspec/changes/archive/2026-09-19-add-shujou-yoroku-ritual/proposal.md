# Proposal: add-shujou-yoroku-ritual

## Why

「众生余录」（`gensokyou:shujou_yoroku_circle`）的 pattern 与 JEI 名称键已落盘（`tiers:[0,1,2]`、`toggleable:true`、祭品台经四重对称展开为 4/8/12 个），其输入载体「众生典籍」（`codex-of-beings`）也先行走完并明确「为后续『众生余录』仪式提供只读访问器」。但仪式本身 Java 侧零实现——成型后无行为、零产出，且核心缓存落到 `DEFAULT_CORE_CAPACITY=10000` 不随阶。

本变更为该 pattern 补上行为：把「已收容完成的众生」（满 20 的典籍）转化为**持续的灵力→物品转化机**——按典籍所指实体类型，定期掷该实体的**原版死亡战利品表**产出掉落，并为高阶附加抢夺 3 与产物翻倍。它消费灵力而非消耗物品，是献祭家族之后又一类持续转化仪式。

## What Changes

- 新建 `ShujouYorokuBehavior implements RitualBehavior`（启停型，pattern 已 `toggleable:true`；不实现 `SpiritBank`）。
- **典籍识别**：仅认 `CodexOfBeingsItem.isFull()`（count==20）的典籍；按 `species` 去重，重复类型视作一个；MUST NOT 消耗典籍（常驻蓝图）。
- **结算节奏**：每 `SHUJOU_CYCLE_TICKS`（默认 1200 = 1 分钟）结算一次。
- **全有全无扣费**：单次成本 = 去重后 species 数 × `SHUJOU_BASE_SP_COST × SHUJOU_SP_COST_MULT^level`（默认 `10000 × 4^L`）；存量不足则整周期不执行（不部分产出、不自动停机）。
- **缓存容量**：`SHUJOU_BASE_CAPACITY × SHUJOU_CAPACITY_MULT^level`（默认 `40000 × 20^L`），按 patternId + level 分派进 `RitualCoreBlockEntity.getCapacity()`。
- **受灵端点**：固定 `SHUJOU_SPIRIT_IN_RATE`（默认 1,000,000/s）；MUST NOT 声明供灵速率。
- **产物 = 原版死亡战利品表**：对每个去重 species，取其 `EntityType.getDefaultLootTable()`，用**玩家击杀上下文**掷一次：
  - L0：视为**玩家击杀（无抢夺）**；
  - L1/L2：视为**抢夺 3 击杀**；
  - L2：在抢夺 3 基础上，本次**全部产物 ×4**（`SHUJOU_L2_OUTPUT_MULT`）。
- **玩家上下文用 FakePlayer 数据 token**（专用 GameProfile、从不加入世界、不调用任何击杀路径）：提供 `LAST_DAMAGE_PLAYER`（满足 `killed_by_player` 掉落）并携带抢夺 3 武器（满足 `enchanted_count_increase` / `random_chance_with_enchanted_bonus` 从 `ATTACKING_ENTITY` 装备读取抢夺等级）。L0 令牌空手。NeoForge 已在 `LevelEvent.Unload` 自动调用 `FakePlayerFactory.unloadLevel`，无手动清理。
- **特殊击杀掉落天然排除**（无需排除名单）：代码驱动掉落（苦力怕炸骷髅掉头、装备/命名牌）因从不调用 `die()` 而不产；战利品表内 attacker 条件（如苦力怕被骷髅击杀掉唱片需 `attacker` ∈ `#minecraft:skeletons`）因 token 为玩家而不命中。
- **产出投递**：同名物品聚合、按最大堆叠拆分，复用献祭家族空投圆盘与 `dropHeight`；结算瞬间触发光柱（新增**色索引 5：灵魂紫**）。
- **专属 config 基项**（COMMON）：容量基值/倍率、灵力成本基值/倍率、周期 tick、受灵速率、L2 产物倍率、抢夺等级。
- **GUI 状态行**（短标签 + tooltip，遵守信息区宽度红线）与 **`/gs_debug shujou` 探针**；提供世界无关纯内核（成本式、容量式、去重、L2 倍率）供断言。

## Capabilities

### New Capabilities

- `shujou-yoroku-ritual`: 众生余录行为契约——典籍识别与去重（仅满 20、不消耗）、固定周期结算、全有全无灵力扣费、按实体原版死亡战利品表产出、玩家击杀上下文与抢夺等级（L0 无抢夺 / L1+ 抢夺 3）、L2 产物翻倍、特殊击杀掉落排除语义、缓存容量与受灵端点、聚合空投、光柱、GUI 状态行、专属 config 与调试探针。

### Modified Capabilities

（无。`codex-of-beings` 的物品与访问器契约不变，仅被读取；`ritual-power-attributes` 的端点声明为通用机制，本仪式遵循既有约束。）

## Impact

- 代码：新增 `ritual/behavior/ShujouYorokuBehavior.java`；改 `ritual/RitualBehaviors.java`（`SHUJOU` 常量 + 注册 + 光柱色索引 5）、`config/GensokyouConfig.java`（专属基项）、`block/entity/RitualCoreBlockEntity.java`（`getCapacity()` 分派）、`client/renderer/RitualCoreRenderer.java`（色索引 5）、`ritual/command/DebugCommands.java`（探针）。
- 资源：`assets/gensokyou/lang/{zh_cn,en_us}.json`（状态/等级/成本等键）。pattern JSON 与 tag 已就位，不改。
- 依赖：`EntityType#getDefaultLootTable`、`LootContextParamSets.ENTITY`、`LootContextParams.{THIS_ENTITY,ORIGIN,DAMAGE_SOURCE,ATTACKING_ENTITY,DIRECT_ATTACKING_ENTITY,LAST_DAMAGE_PLAYER}`、NeoForge `FakePlayerFactory` / `FakePlayer`、`Enchantments.LOOTING`、`CodexOfBeingsItem` 只读访问器、`RitualPedestals`、`SpiritPowerHelper`、`WatatsumiBehavior.dropStacks` + `ToolSacrificeBehavior.dropHeight`、既有光柱 BER。
- 不动：`codex-of-beings` 物品本体与数据组件、`shujou_yoroku_circle.json`、其他仪式与其数据、配方系统、灵力核心物品本体。
