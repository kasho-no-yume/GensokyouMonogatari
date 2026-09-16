# Proposal: add-tool-sacrifice-rituals

## Why

四个「献祭工具产资源」仪式的 pattern（`oyamatsumi_circle` 大山祇神之座、`kukunochi_circle` 久久能智神庭、`haniyasu_circle` 埴山姬神之壤、`kaya_no_hime_circle` 草野姬神之亭，均 0~2 阶）与 JEI 名称已落盘，但 Java 侧零实现——成型后无任何行为、零产出。本变更把它们做成**持续运行的资源转化仪式**：启动后持续监控祭品台，消耗工具 + 灵力，按工具材质加权掷出该领域资源，形成「灵力 → 基础资源」的规模化产线。

## What Changes

- 新增 `ToolSacrificeBehavior` 抽象基类 + 4 子类（石/木/土/草域），注册到 `RitualBehaviors`（4 个常量）。
- **持续运行型**（`toggleable: true`）：启动后 `serverTick` 持续扫描祭品台；满足「有合规工具 + 灵力足够 + 冷却到」即随机消耗一把工具、扣灵力、掷产物、进入冷却等待。绝不「点一次跑一次」。
- 工具识别：按 `#minecraft:pickaxes/axes/shovels/hoes` 判类别，`TieredItem.getTier()` 归一材质（wood/stone/gold/iron/diamond/netherite；mod 工具同法，读不出回退最低档）；附魔按不附魔处理。
- 产出：每次结算掷 `20 × 4^level` 次加权表 → 每件独立抽 1 物品 → 同类聚合成叠 → 从核心上方 ≤20 格「最高无遮挡处」落下（无需无敌）。
- 灵力：每次结算扣 `4000 × 4^level`（走既有三段式 `SpiritPowerHelper.payCost`：槽核→缓存→周围兜底）；灵力不足保持待机不停机。声明大 `spiritInRatePerSecond` 作为万象共鸣路由的受灵汇。
- 条件解锁：祭品台存在 **≥3 个凋灵骷髅头** → 解锁地狱池；存在 **≥1 个龙首** → 解锁末地池。**这些头颅不消耗**，只作条件。
- 新数据源 `data/gensokyou/ritual_loot/*.json`（一仪式一文件）+ `RitualLootLoader`：每仪式 `commons` 桶 + 6 材质 × {`special`, `nether`, `end`} 权重表；权重为**总权重**（相对值，非写死概率）。
- FX：**仅产出结算那一刻**核心升起巨大光柱后消失（服务端同步最小状态 + 客户端 BER）；运行/充能期无光柱。结算后强制等 2 秒（40t）再继续。
- JEI：每仪式一专属页签，页签内按工具材质翻页（6 条配方），卡面列该材质产物，悬浮项显示权重（约 X%）与隐藏条件。
- config 旋钮（COMMON）：基础产出数 20、每阶倍率 4、基础灵力消耗 4000、每阶倍率 4、受灵速率、冷却 40t、落高上限 20、骷髅头门槛 3、缓存基值。
- lang 键 + `tools/lang_audit.py` 回归；`/gs_debug` 增献祭调试探针。
- **pattern JSON 4 个 `"toggleable": false → true`**（启停按钮唯一来源，纯代码面开关）。

## Capabilities

### New Capabilities

- `tool-sacrifice-ritual`: 工具献祭仪式族的行为契约——持续运行监控与结算节奏（工具随机消耗/灵力扣费/冷却）、工具类别与材质归一、总权重表模型（commons 桶 + 材质 special + 条件池 nether/end）、逐件独立加权掷骰与同类聚合、产物空投落点、光柱 FX、条件头颅不消耗语义、GUI 状态行。

### Modified Capabilities

- `jei-ritual-display`: 数据单源从「仅 `ritual_recipes`」扩展为「`ritual_recipes`（配方卡）+ `ritual_loot`（献祭权重卡）」；新增按工具材质翻页的献祭卡需求（权重百分比 + 隐藏条件）。

> 说明：行为注册、缓存容量分派、受灵端点声明、启停链路均落在新能力 `tool-sacrifice-ritual` 内；既有 `ritual-lifecycle` / `ritual-power-attributes` / `ritual-core-interface` 的通用契约无需求变更，故不列。

## Impact

- 代码：`ritual/behavior/ToolSacrificeBehavior.java`（新建，含 4 子类或 4 个独立类）、`ritual/RitualBehaviors.java`、`ritual/RitualLootLoader.java`+`RitualLootTable.java`（新建）、`block/entity/RitualCoreBlockEntity.java`（容量分派）、`config/GensokyouConfig.java`、`jei/`（新卡类型/类别）、`client/renderer/`（光柱 BER，参考 `LaserDanmakuRenderer`）、`ritual/command/DebugCommands.java`。
- 资源：`data/gensokyou/ritual_loot/{oyamatsumi,kukunochi,haniyasu,kaya_no_hime}.json`（新建）、4 个 pattern JSON（`toggleable`）、`assets/gensokyou/lang/{zh_cn,en_us}.json`、可能的 BEAM 贴图（gen-textures）。
- 依赖：`TieredItem.getTier()`、`ItemTags.{PICKAXES,AXES,SHOVELS,HOES}`、`SpiritPowerHelper.payCost`、既有祭品台代理箱与 `RitualPedestalBlockEntity`、既有 JEI 页签框架、既有 FX/BER 基建。
- 不动：4 个 pattern 的结构切片（除 `toggleable`）、配方系统（`ritual_recipes`）、灵力核心物品本体、既有仪式行为。
