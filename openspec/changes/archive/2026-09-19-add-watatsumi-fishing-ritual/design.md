# Design: add-watatsumi-fishing-ritual

## Context

`watatsumi_circle` pattern 已定稿（`levels:[0,1,2]`、`tiers:[0,1,2]`、`toggleable:false`、palette 为海晶/海灯/苔藓等海洋母题），JEI 名称键已挂，但 Java 零实现。它是第 5 个「献祭工具产资源」仪式，却与既有 4 个有本质差异：

- 献祭物是**钓鱼竿**——非 `TieredItem`，没有 wood..netherite 材质维度；既有家族**唯一**的选表依据（材质）失效。
- 产物来自**原版钓鱼掉落表**与**埋藏宝藏表**——含数量（墨囊 ×10）、随机损耗、30 级附魔书/弓/竿、水瓶 NBT，既有 `RitualLootTable.Weighted(Item,double)` 无法表达。
- 解锁维度是**仪式等级**（L1 开宝藏 + 海洋特产），而非既有家族的「头颅条件池」。
- 冷却需**按次动态**（1200t / 触发大奖 12000t），既有家族是固定 config。
- 产出是**两个独立池各半**（钓鱼池 + 海洋特产池），而非「commons 桶 + special 下沉」单池。

已核验 API（sources jar，2026-09-19）：

- `LootContextParamSets.FISHING` = 必需 `ORIGIN` + `TOOL`（可选 `THIS_ENTITY`/`ATTACKING_ENTITY`）；`CHEST` = 必需 `ORIGIN`（可选 entity）。
- `BuiltInLootTables.FISHING_FISH / FISHING_JUNK / FISHING_TREASURE / BURIED_TREASURE` 均在。
- `ReloadableServerRegistries.Holder#getLootTable(ResourceKey<LootTable>)` 可取表；`LootTable#getRandomItems(LootParams, RandomSource)` 返回 `ObjectArrayList<ItemStack>`。
- 原版 `fishing.json` 根表：junk w10 / fish w85 / treasure w5（treasure 条目挂 `in_open_water` 条件）；**子表 `fish/junk/treasure.json` 内部无条件**——故绕开根表、直接掷子表即可精确复刻且不受水域限制。
- 保留 API：`SpiritPowerHelper.canCover/payCost`、`RitualCoreBlockEntity.actionCooldown()/setActionCooldown()`、`RitualPedestals.positions()`、`RitualPedestalBlockEntity.getHeld/setHeld`、光柱 `triggerSacrificeFx` + BER。

已与用户逐条确认：双池各半、L0 出垃圾且无宝藏、L1 宝藏 5%、整表埋藏宝藏、专属数量公式、动态冷却、灵力/容量/受灵共用、**不复用**既有继承链、不设下界/末地池、忽略附魔、任意位置、无天气加成。

## Goals / Non-Goals

**Goals:**

- 实现绵津见为持续运行转化仪式：随机消耗 1 根钓鱼竿 + 扣 `4000×4^L` 灵力 + 双池掷骰空投 + 光柱 + 动态冷却。
- 钓鱼池**精确复刻**原版钓鱼产出（含数量/损耗/附魔/NBT）；L0 移除宝藏并归一化，L1+ 解锁宝藏 5%。
- 海洋特产池数据驱动、权重可调、JEI 可查。
- L2 的 0.1% 整表埋藏宝藏额外奖励与 10 分钟冷却自罚。
- 全部数值走 `GensokyouConfig`（除原版固定类别权重）。

**Non-Goals:**

- 不把绵津见硬塞进 `ToolSacrificeBehavior` 继承链（材质轴/单池模型/头颅条件均不成立）。
- 不做下界/末地条件池；不做头颅消耗。
- 不因钓鱼竿附魔（海之眷顾/饵钓/耐久）改变任何语义。
- 不做水域/天气/时间加成，不做产物无敌。
- 不改既有 4 仪式与其数据、配方系统、灵力核心物品本体。

## Decisions

### D1 独立行为类 `WatatsumiBehavior implements RitualBehavior`

不复用继承链（用户明确「别直接全数复用」）。只调用既有**无状态工具函数**，避免行为逻辑与材质/单池耦合：

| 复用（函数级） | 自实现 |
|---|---|
| 扫祭品台收集合规工具、随机消耗 1 件 | 选池（无材质轴） |
| `SpiritPowerHelper.canCover/payCost` | 双独立池 + 等级门控 |
| `dropHeight` + XZ 圆盘空投 | 数量公式（5/20/80 半半） |
| 光柱触发 + BER（新增色索引 4） | 原版表掷骰接线 |
| `RitualCoreBlockEntity.actionCooldown` 持久化 | 动态冷却、0.1% 额外奖励、GUI/JEI 专属行 |

`RitualBehaviors` 增常量 `WATATSUMI = gensokyou:watatsumi_circle` 与 `register`，并把绵津见纳入容量分派与光柱路径（D8）。

### D2 双独立池，各半掷数

```
总量 total(L) = WATATSUMI_BASE_COUNT × WATATSUMI_COUNT_MULT^L = 5 × 4^L
specialCount(L) = (L == 0) ? 0 : total(L) / 2
fishingCount(L) = total(L) − specialCount(L)
```

| 等级 | 总量 | 钓鱼池 | 特产池 | 宝藏 | 埋藏宝藏 | 冷却 |
|---|---|---|---|---|---|---|
| 0 | 5 | 5 | 0 | ✗ | ✗ | 1200t |
| 1 | 20 | 10 | 10 | 5% | ✗ | 1200t |
| 2 | 80 | 40 | 40 | 5% | 0.1%/次 | 1200t（命中→12000t）|

两池**各自独立**按累计权重法掷各自次数，产出聚合后统一空投。

### D3 钓鱼池 = 直接掷原版子表（保真）

每次钓鱼池掷骰：

```
1) 选类别：优先池权重 { fish 85, junk 10, treasure L≥1 ? 5 : 0 }
   L0 时 treasure 权重为 0 → 归一化到 fish≈89.5% / junk≈10.5%
2) 掷该类别子表：
   FISHING_FISH | FISHING_JUNK | FISHING_TREASURE
   LootParams = new LootParams.Builder(level)
       .withParameter(ORIGIN, core中心Vec3)
       .withParameter(TOOL, chosenRodStack)   // 消耗前复制
       .create(LootContextParamSets.FISHING)
   table.getRandomItems(params, level.random) → 1 个 ItemStack（数量/损耗/附魔/NBT 全保真）
```

- **绕开根表** `FISHING`：根表的 treasure 条目挂 `in_open_water` 实体条件（需 `THIS_ENTITY` 钩子），直接掷三类子表即可精确复刻且无需水域，且 treasure 由等级门控而非水域门控。
- 类别权重为**原版固定值**，写成行为内常量并注释来源（非 config）；L0 归一化在选类别时用 `fish:junk` 相对权重即可。
- 否决「手写 ritual_loot JSON」路线：数量/损耗/附魔/NBT 全丢，且与原版表漂移。

### D4 海洋特产池 = 专属小数据文件

- 位置 `data/gensokyou/ritual_special/watatsumi_special.json`（独立目录，避免与 `RitualLootTable` 的材质驱动 schema/加载器混淆）。
- 结构：`{ "entries": [["minecraft:kelp", 15], ...] }`；数组支持字符串或 `[itemId, weight]`？统一 `[itemId, weight]`（照既有 `Weighted` 解析）。
- 加载器 `WatatsumiSpecialLootLoader extends SimpleJsonResourceReloadListener`：校验 item id 存在、权重有限非负、非空；注册进 `ReloadListenerHandler`。
- 记录复用 `RitualLootTable.Weighted` 与纯内核 `RitualLootTable.sumWeight/roll/rollMany`（世界无关，调试可直调）。
- 草案权重（"海洋特产高一些"，合计 126 → 神殿 ≈40% / 海产 ≈60%，实现期可调）：

| 分组 | 条目 | 权重 |
|---|---|---|
| 海底神殿 | prismarine_shard · prismarine_crystals · sea_lantern · dark_prismarine · prismarine · sponge · wet_sponge | 20 · 12 · 6 · 5 · 4 · 3 · 1 |
| 珊瑚/海带/海泡菜 | kelp · sea_pickle · 五色 coral_block · 五色 coral_fan · seagrass | 15 · 12 · 各 5 · 各 3 · 8 |

### D5 等级门控（替代头颅条件池）

- `treasureUnlocked = level ≥ 1`；`specialUnlocked = level ≥ 1`。
- 无 nether/end 池、无头颅统计/消耗。GUI 不显示头颅条件行（D10）。
- L0 = 5 次纯钓鱼池（无宝藏、无特产）。

### D6 动态冷却

- 结算末尾：`core.setActionCooldown(bonusTriggered ? WATATSUMI_BONUS_COOLDOWN_TICKS : WATATSUMI_BASE_COOLDOWN_TICKS)`。
- 与既有家族「从固定 config 写冷却」不同，因绵津见需按次自罚；BE 字段本身已经是通用 `int`，无需改结构。
- 冷却期间 `serverTick` 早退；GUI 剩余秒数沿用既有显示。

### D7 2 级额外奖励（埋藏宝藏）

```
if (level >= 2 && level.random.nextDouble() < WATATSUMI_BONUS_CHANCE) {
    bonus = true;
    LootParams chestParams = new LootParams.Builder(level)
        .withParameter(ORIGIN, core中心Vec3).create(LootContextParamSets.CHEST);
    getLootTable(BURIED_TREASURE).getRandomItems(chestParams, level.random)
        → 独立聚合后与主产出一起/紧随空投（同一落点规则 + 同一光柱）
}
```

- 整表（含保底 `heart_of_the_sea`、铁/金/TNT、绿宝石/钻石/海晶砂粒、皮革胸甲/铁剑、熟鱼、水肺药水），用户确认。
- 不额外扣灵力、不改变主产出数量；只改本次冷却。

### D8 config 旋钮与共享项

| 键（COMMON） | 默认 | 说明 |
|---|---|---|
| `watatsumiBaseCount` | 5 | 0 阶总量（×4^L）|
| `watatsumiCountMult` | 4 | 每阶倍率 |
| `watatsumiBaseCooldownTicks` | 1200 | 基础冷却（1 min）|
| `watatsumiBonusCooldownTicks` | 12000 | 命中额外奖励冷却（10 min）|
| `watatsumiBonusChance` | 0.001 | L2 额外奖励概率 |
| 共用 | — | `SACRIFICE_BASE_SP_COST`(4000)×`SACRIFICE_SP_COST_MULT`(4)、`SACRIFICE_SPIRIT_IN_RATE`、`SACRIFICE_FALL_MAX_HEIGHT`、`SACRIFICE_BASE_CAPACITY`、`FX_PILLAR_*` |

- 灵力消耗 = `4000×4^L`（L0 4000 / L1 16000 / L2 64000）；容量 = `SACRIFICE_BASE_CAPACITY×4^L`；受灵速率共用——均按「共用」确认。

### D9 FX 新色索引 4（水蓝）

- `RitualBehaviors.sacrificeColorIndex(WATATSUMI)` 返回 4；`isToolSacrifice` 纳入绵津见（复用容量分派 + 光柱 kind）。
- 客户端 `RitualCoreRenderer.pillarColor` 增 `case 4`（水蓝，如 `{90,180,200}`）；`RitualRenderState.period` 注释由 `0..3` 扩为 `0..4`。
- 仅结算瞬间触发，沿用既有限制。

### D10 GUI 信息行

状态行（待机 / 缺钓鱼竿 / 缺灵力 / 冷却中 / 结算中）+ 基础冷却时长 + 冷却剩余秒数 + 祭品台钓鱼竿计数 + 当前等级 + 宝藏解锁（L≥1）+ 特产解锁（L≥1）。**不含**下界/末地行。短标签 + tooltip，遵守信息区宽度红线。

### D11 JEI（按等级 3 页）

- 绵津见登记**专属**页签，页签内按等级 0/1/2 三张卡（JEI 原生翻页）。
- 每张卡：标题（绵津见神之藏 + L阶）、输入（钓鱼竿代表物）、掷数说明（钓鱼 n / 特产 m）、产物网格。
- 钓鱼池产物**硬编码原版三类子表权重**（fish/junk/treasure 的 item→weight 静态表，含数量/附魔说明），保证 dedicated server 也可见；特产池由同步数据派生。悬浮显示「约 X%」。
- 理由：JEI 纯客户端，dedicated server 上拿不到服务端原版掉落表；原版权重稳定，硬编码 + 注释可接受。归属 `jei-ritual-display` 能力并修订其「数据单源」。

### D12 世界无关纯内核 + 调试探针

- `fishingCount(level)` / `specialCount(level)` / `treasureUnlocked(level)` / `specialUnlocked(level)` / 类别权重选择 做成静态纯函数。
- `/gs_debug watatsumi <corePos>`：打印等级、竿计数、两池组装（含约 %）、判定宝藏/特产解锁、试掷一次。
- 复用 `RitualLootTable` 的纯掷骰内核。

## Risks / Trade-offs

- **[原版表掷骰需满足参数集]** `FISHING` 必需 `ORIGIN`+`TOOL`、`CHEST` 必需 `ORIGIN`；缺参数会抛 → 结算时用核心中心 + 被消耗竿副本构造 `LootParams`；`TOOL` 只影响 `enchant_with_levels`/luck 语义，无幸运算子时等价基础表。
- **[原版权重跨版本变动 → JEI 硬编码显示漂移]** → JEI 显示加「原版」注记；若未来需运行时读取，仅单机可用，暂不做。
- **[0.1% 大奖过强]** 整表含保底海洋之心 → 用户确认；10 分钟冷却自罚 + 概率可 config 调低。
- **[L2 满速 80 件/分钟产量巨大]** → 用户指定 1 分钟基础冷却；数量/倍率可 config。
- **[mod 自定义钓鱼竿]** `TieredItem` 无关，按 `gensokyou:fishing_rods` 标签识别；不在标签内的 mod 竿不识别 → 文档化，标签可经数据包扩展。
- **[附魔被忽略]** 与既有家族一致，用户确认；海之眷顾不提升产出。
- **[空投落点受地形遮挡]** 沿用既有 `dropHeight`（首个遮挡前最高可穿过格，≤上限）；超限无产物。

## Migration Plan

纯新增行为/数据/标签/注册 + `watatsumi_circle.json` 一行 `toggleable`。无存档迁移。回滚：移除注册项 + `toggleable` 改回 false，并可选移除新数据文件 → 退回「成型但无行为」。

## Open Questions

- 特产池是否纳入 `prismarine_bricks`（暂不纳入；若加则补权重）。
- 五色珊瑚是否 block/fan 全要（草案全要；若嫌条目多可只留块）。
- 额外奖励与主产出是「合并为一次聚合」还是「两份独立聚合」——视觉效果同，取实现简单者（两份聚合、同落点规则）。
