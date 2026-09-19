# Proposal: add-watatsumi-fishing-ritual

## Why

绵津见神之藏（`watatsumi_circle`）的 0~2 阶 pattern 与 JEI 名称已落盘，但 `toggleable:false` 且 Java 侧零实现——成型后无任何行为、零产出。它是第 5 个「献祭工具产资源」仪式，但献祭的是**无材质**的钓鱼竿、产物来自**原版钓鱼与埋藏宝藏掉落表**、且按**等级**解锁宝藏/海洋特产，套不进既有 4 材质驱动的 `ToolSacrificeBehavior`。本变更把它实现为独立的持续运行转化仪式。

## What Changes

- 新建**独立** `WatatsumiBehavior implements RitualBehavior`（**不继承** `ToolSacrificeBehavior`；仅复用无状态工具函数：祭品台扫描/随机消耗 1 件、`SpiritPowerHelper` 三段式扣费、空投落点、光柱触发）。**BREAKING**：无（纯新增）。
- 持续运行（pattern `toggleable: false → true`）：启动后每 tick 监控祭品台，「有合规钓鱼竿 + 灵力足够 + 冷却已过」即结算，否则待机（不停机、不报错）。
- 结算产出 = **两个独立池，各占一半掷数**：
  - **钓鱼池**（掷数 L0/L1/L2 = 5 / 10 / 40）：**直接掷原版** `BuiltInLootTables.FISHING_FISH / FISHING_JUNK / FISHING_TREASURE`，完整保真数量/损耗/附魔/NBT；类别权重 fish 85 / junk 10 / treasure 5，treasure 仅 L1+ 解锁，L0 把 treasure 整条移除后归一化（fish ≈89.5% / junk ≈10.5%）。
  - **海洋特产池**（掷数 0 / 10 / 40，L1+ 解锁）：自定义加权数据（海底神殿特产 + 珊瑚/海带/海泡菜/海草等钓不上来的海洋特产）。
- **2 级额外奖励**：每次结算 0.1% 概率 → 掷**整表** `BuiltInLootTables.BURIED_TREASURE`（含保底海洋之心）作为独立一份空投，不额外扣灵力、同一光柱；命中则本次冷却置 12000t(10min)，否则 1200t(1min)。
- 冷却由「固定 config」改为**按次动态**：`RitualCoreBlockEntity.actionCooldown` 写入本次结算决定的值。
- 专属 config 旋钮（COMMON）：数量基值 5、每阶倍率 4、基础冷却 1200t、额外奖励冷却 12000t、额外奖励概率 0.001；灵力消耗 `4000×4^L`、缓存容量、受灵速率沿用既有共用 `SACRIFICE_*`。
- 新建自定义物品标签 `gensokyou:fishing_rods`（含 `minecraft:fishing_rod`）。
- FX：`RitualRenderState` 光柱色索引新增 **4（水蓝）** 并扩展客户端 `pillarColor`；`RitualBehaviors` 侧把绵津见纳入光柱/容量分派。
- GUI：状态行（待机/缺竿/缺灵力/冷却中/结算中）、冷却、工具数、当前等级、宝藏解锁状态；**不含**下界/末地条件行。
- JEI：绵津见专属页签，按**等级 3 页**（L0 仅钓鱼池；L1/L2 双池），标注各池掷数与权重近似概率。
- 调试：`/gs_debug` 增绵津见探针（列出两池组装结果、判定等级解锁、试掷）。

## Capabilities

### New Capabilities

- `watatsumi-fishing-ritual`: 绵津见神之藏行为契约——持续运行与结算节奏、钓鱼竿识别与消耗、双独立池各半产出、原版钓鱼表精确掷骰（数量/NBT 保真）、海洋特产池、等级门控（宝藏/特产）、2 级 0.1% 埋藏宝藏额外奖励与动态冷却、空投与光柱、GUI 状态行、专属 config 旋钮。

### Modified Capabilities

- `jei-ritual-display`: 新增绵津见专属页签（按等级 3 页翻页、双池展示）；钓鱼池数据源为原版掉落表而非 `ritual_loot`，故「数据单源」与「工具献祭权重卡」等条目的适用边界需扩展。

## Impact

- 代码：新建 `ritual/behavior/WatatsumiBehavior.java`、`ritual/WatatsumiSpecialLoot.java` + 加载器（或等价数据层）；改 `ritual/RitualBehaviors.java`、`config/GensokyouConfig.java`、`block/entity/RitualCoreBlockEntity.java`（容量分派）、`client/renderer/RitualCoreRenderer.java`（色索引 4）、`jei/`（绵津见卡类别/页签登记）、`ritual/command/DebugCommands.java`。
- 资源：`data/gensokyou/ritual_special/watatsumi_special.json`（独立 schema 的海洋特产池数据）、`data/gensokyou/tags/item/fishing_rods.json`（新建）、`data/gensokyou/rituals/watatsumi_circle.json`（`toggleable`）、`assets/gensokyou/lang/{zh_cn,en_us}.json`。
- 依赖：`BuiltInLootTables.{FISHING_FISH,FISHING_JUNK,FISHING_TREASURE,BURIED_TREASURE}`、`LootParams` / `LootContextParamSets.{FISHING,CHEST}`、`ReloadableServerRegistries`、`SpiritPowerHelper`、既有祭品台代理箱与光柱 BER、既有 JEI 页签框架。
- 不动：其余 4 个献祭仪式及其行为/数据、`ritual_recipes` 配方系统、灵力核心物品本体。
