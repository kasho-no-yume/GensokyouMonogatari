# Design: add-shujou-yoroku-ritual

## Context

`shujou_yoroku_circle` pattern 已定稿：`tiers:[0,1,2]`、`toggleable:true`、palette 以书架/石英/紫玻璃等「藏书/余录」为母题，祭品台 `P` 每级各 1 处，经 `RitualPatternLoader.mergeAdds → expandInto` **四重对称展开**后累积为 **4 / 8 / 12** 个。JEI 名称键 `jei.gensokyou.ritual.shujou_yoroku_circle` 已挂。输入载体 `gensokyou:codex_of_beings`（`CodexOfBeingsItem` + `CodexData`，`MAX_CAPTURE=20`，满态 `isFull()`）已实现并暴露只读访问器。**Java 侧行为零实现**：`RitualBehaviors` 无 `SHUJOU`，`getCapacity()` 无分支（兜底 `DEFAULT_CORE_CAPACITY=10000`，不随阶），成型即空转。

既有最接近的模板是 `WatatsumiBehavior`（扫祭品台 → `SpiritPowerHelper.canCover/payCost` → 掷表 → 聚合空投 → 光柱 → 冷却 → UI/debug），但产物来源完全不同：绵津见掷原版钓鱼表，本仪式掷**实体死亡战利品表**并需要**玩家击杀 + 抢夺等级**上下文。

### 已核验的原版机制（sources jar，2026-09-19）

| 机制 | 事实 | 含义 |
|---|---|---|
| 抢夺数量加成 | `EnchantedCountIncreaseFunction.run()` 读 `ATTACKING_ENTITY`，经 `EnchantmentHelper.getEnchantmentLevel(LOOTING, living)`；后者遍历 `Enchantment.getSlotItems → entity.getItemBySlot` **装备槽** | 抢夺等级**无参数式注入**；必须有一个携带抢夺附魔的 `LivingEntity` 充当 `ATTACKING_ENTITY` |
| 抢夺概率加成 | `LootItemRandomChanceWithEnchantedBonusCondition.test()` 同样读 `ATTACKING_ENTITY` 的装备 | 同上（僵尸铁锭/胡萝卜/土豆用） |
| 玩家击杀限定 | `LootItemKilledByPlayerCondition.test()` = `context.hasParam(LAST_DAMAGE_PLAYER)`；该参数类型为 `Player` | 无真玩家则 `killed_by_player` 掉落静默丢失 |
| 苦力怕头 | `Creeper#dropCustomDeathLoot`（代码驱动，killer 为充能苦力怕时掉落） | 不在战利品表；我们从不 `die()` → 天然不产 |
| 苦力怕唱片 | 战利品表内条件 `entity_properties: attacker type #minecraft:skeletons` | 我方 attacker 为玩家 → 条件不命中 → 天然排除 |
| 装备/命名牌 | `LivingEntity#dropEquipment` / `dropCustomDeathLoot`，不走 loot table | 我们只掷 loot table → 不产 |
| 熟肉（炉火） | 表内 `furnace_smelt` 条件：`this.on_fire` 或 `direct_attacker` 主手带 `#smelts_loot` | 默认实体不燃烧、剑无火焰附加 → 生肉（符合"非火焰抢夺3击杀"） |
| FakePlayer 生命周期 | `NeoForgeEventHandler.onDimensionUnload(LevelEvent.Unload)` 已调用 `FakePlayerFactory.unloadLevel` | 无需手动清理，不存在存档内存泄漏 |

> 结论：**"特殊击杀掉落不进池"由战利品表自身语义与"不调用 die()"共同保证，无需任何排除名单/标签。**

已与用户逐条确认的语义：所有典籍各产一份、「等概率且等量」= 完全按战利品表、只认满 20、典籍不消耗、灵力不足整周期不产、特殊掉落无需显式排除、默认实体状态保真度缺口可接受、接受方案 A（FakePlayer 仅作数据 token）。

## Goals / Non-Goals

**Goals:**

- 实现众生余录为启停型持续转化仪式：每 1 分钟按「满 20 典籍所指 species（去重）」掷原版死亡战利品表产出物品。
- 忠实复刻玩家击杀掉落：L0 无抢夺、L1/L2 抢夺 3、L2 产物 ×4。
- 特殊击杀掉落自动排除，且不引入排除名单。
- 灵力：固定周期、全有全无扣费、按阶放大的缓存与成本、固定高速受灵端点。
- 全部可调数值走 `GensokyouConfig`（COMMON）。
- GUI/调试/纯内核齐备，便于回归断言。

**Non-Goals:**

- 不消耗典籍；不改 `codex-of-beings` 物品/组件/收容机制。
- 不改 `shujou_yoroku_circle.json` pattern（结构已定稿）。
- 不产经验、不推进成就/击杀统计、不复制 Luck 药水加成。
- 不模拟装备掉落、命名牌、驯服状态；不重放实体具体 NBT/变种状态。
- 不实现数据驱动的排除名单或自定义掉落池。
- 不新增逐 tick 粒子；光柱沿用既有限制。

## Decisions

### D1 独立行为类与 tick 通道

新建 `ShujouYorokuBehavior implements RitualBehavior`（**不**实现 `SpiritBank`）。pattern 已 `toggleable:true`，故用 `serverTick`（受 `enabled` 门控，`RitualCoreBlockEntity` 第 1507 行起）：每 tick 调一次，内部以 `core.actionCooldown() > 0` 早退，结算末尾 `setActionCooldown(SHUJOU_CYCLE_TICKS)`。`RitualBehaviors` 增常量 `SHUJOU = gensokyou:shujou_yoroku_circle` 与 `register`。

### D2 典籍识别与去重

```
for pos in RitualPedestals.positions(match):            // 规范序 y,z,x；4/8/12 个
    held = pedestal.getHeld()
    if held is CodexOfBeingsItem && CodexOfBeingsItem.isFull(held):
        species = CodexOfBeingsItem.getSpecies(held)     // Optional<ResourceLocation>
        若 species 可解析为 EntityType → 记入去重集合
```

- 去重键 = `species`（`重复类型的典籍视作一个`）；顺序取祭品台规范序，保证输出确定性。
- 空书、未满（count<20）、species 缺失、species 已不在实体注册表 → 跳过。
- MUST NOT 消耗典籍（`getHeld` 只读）。

### D3 结算成本与全有全无

```
N = 去重后 species 数
cost = N × SHUJOU_BASE_SP_COST × SHUJOU_SP_COST_MULT^level      // 默认 N × 10000 × 4^L
if N == 0 → 空转（不扣费、不产、不停机）
if !SpiritPowerHelper.canCover(level, corePos, core, cost) → 整周期不执行
else payCost(cost) → 逐 species 掷表
```

- 成本按**去重后**的 species 数计（3 本僵尸书 = 1 个 species = 1×成本）。
- 全有全无（用户确认）：不足则本周期完全不产，保持 enabled 等待灵力。

### D4 玩家击杀上下文 = FakePlayer 数据 token

```
token = FakePlayerFactory.get(serverLevel, SHUJOU_PROFILE)   // 专用 GameProfile，缓存于工厂，不入世界
token.setItemInHand(MAIN_HAND, level >= 1 ? lootingSword() : ItemStack.EMPTY)
damageSource = serverLevel.damageSources().playerAttack(token)
```

- **L0**：空手 token → `killed_by_player` 成立、抢夺 0（无 looting 函数加成）。
- **L1/L2**：主手 = 附魔 `minecraft:looting` 等级 `SHUJOU_LOOTING_LEVEL`（默认 3）的剑（如 `Items.DIAMOND_SWORD`，除抢夺外无其它附魔，避免 `smelts_loot` 等副作用）。
- token 仅是 loot context 的「玩家凭证」，**从不加入世界、从不调用 `die()`/攻击**；NeoForge 自动在维度卸载时清理。
- 用 `FakePlayer` 而非中性实体，因为 `LAST_DAMAGE_PLAYER` 参数类型为 `Player`；这是唯一能同时满足「抢夺 3 精确 + `killed_by_player` 掉落完整」的路径（用户已确认方案 A）。

### D5 战利品表掷骰

```
for species in dedupedSpecies:
    EntityType<?> type = ...
    LootTable table = serverLevel.getServer().reloadableRegistries()
            .getLootTable(type.getDefaultLootTable())
    Entity victim = type.create(serverLevel)          // 默认状态；若 null 跳过
    victim.moveTo(core center)
    LootParams params = new LootParams.Builder(serverLevel)
        .withParameter(THIS_ENTITY, victim)
        .withParameter(ORIGIN, Vec3.atCenterOf(corePos))
        .withParameter(DAMAGE_SOURCE, damageSource)
        .withParameter(ATTACKING_ENTITY, token)
        .withParameter(DIRECT_ATTACKING_ENTITY, token)
        .withParameter(LAST_DAMAGE_PLAYER, token)
        .create(LootContextParamSets.ENTITY)
    outputs += table.getRandomItems(params, serverLevel.random)
```

- `getDefaultLootTable()` 由 `EntityType` 直接给出，无需真的生成实体即可取表；`victim` 仅作 `THIS_ENTITY` 供 `entity_properties`（如 sheep 颜色、slime 尺寸）读取，默认状态即可。
- 参数集 `ENTITY` 仅强制 `THIS_ENTITY`；其余可选，但为语义完整全部显式提供。

### D6 特殊击杀掉落排除语义（无名单）

- 代码驱动（苦力怕头、装备、命名牌）：从不调用 `die()`/`dropEquipment` → 不产。
- 表内 attacker/killer 条件（苦力怕被骷髅杀掉的唱片）：token 为玩家 → 不命中 → 不产。
- 玩家击杀限定（`killed_by_player`）：token 存在 → **正常产出**（这正是"视为玩家击杀"要保留的）。
- 结果：无需维护任何排除 item 标签/配置。

### D7 L2 产物翻倍与聚合投递

- L2 时对**全部产物**逐栈 `count *= SHUJOU_L2_OUTPUT_MULT`（默认 4）。
- 同名物品聚合为 `Map<Item, Integer>`；按 `maxStackSize` 拆成 ≤64 的栈。
- 投递复用 `WatatsumiBehavior.dropStacks(level, corePos, stacks)`（XZ 圆盘半径 `RITUAL_OUTPUT_DROP_RADIUS` + Y 复用献祭家族 `ToolSacrificeBehavior.dropHeight`），不设无敌/防护。

### D8 缓存容量与受灵端点

| 项 | 值 | 实现 |
|---|---|---|
| 容量 | `SHUJOU_BASE_CAPACITY × SHUJOU_CAPACITY_MULT^level`（默认 40000×20^L = 40k/800k/16M） | `getCapacity()` 增 `patternId == SHUJOU` 分支（新 helper，仿 `kagutsuchiCapacity`） |
| 受灵 | `SHUJOU_SPIRIT_IN_RATE`（默认 1,000,000/s，固定不随阶） | 覆写 `spiritInRatePerSecond` |
| 供灵 | 0 | 不覆写 `spiritOutRatePerSecond` |
| 内部扣费 | 走普通 `payCost` 通道，MUST NOT 走 `extractRouted/receiveRouted` | 不实现 `SpiritBank` |

L0 容量 40000 恰为一次满台（4×10000）成本；L1/L2 为多次结算缓冲。

### D9 config 基项（COMMON）

| 键 | 默认 | 说明 |
|---|---|---|
| `shujouBaseCapacity` | 40000 | 0 阶缓存容量 |
| `shujouCapacityMult` | 20 | 每阶容量倍率 |
| `shujouBaseSpCost` | 10000 | 0 阶单 species 灵力成本 |
| `shujouSpCostMult` | 4 | 每阶成本倍率 |
| `shujouCycleTicks` | 1200 | 生产间隔（1 分钟） |
| `shujouSpiritInRate` | 1000000 | 受灵速率（/s，固定） |
| `shujouL2OutputMult` | 4 | 2 阶产物倍率 |
| `shujouLootingLevel` | 3 | 1 阶及以上模拟的抢夺等级 |

### D10 光柱色索引 5（灵魂紫）

- `RitualBehaviors.sacrificeColorIndex(SHUJOU)` 返回 5；客户端 `RitualCoreRenderer.pillarColor` 增 `case 5` 紫色；`RitualRenderState` 色索引注释扩为 `0..5`。
- 仅结算瞬间 `triggerSacrificeFx`，不新增逐 tick 粒子。

### D11 GUI 信息行

状态行（待机：无典籍 / 未满典籍忽略 / 缺灵力 / 冷却中 / 结算中）+ 有效 species 数 / 祭品台数 + 单次成本 + 周期秒数 + 当前等级 + L2 状态。短标签 + tooltip，遵守信息区宽度红线（`ritual-gui-info-lines`）。

### D12 世界无关纯内核 + 调试探针

- 纯静态：`speciesCost(level)`、`totalCost(level, n)`、`capacity(level)`、`l2Multiplier(level)`、`isValidCodex(stack)` 判定。
- `/gs_debug shujou <corePos>`：打印等级、祭品台/满典籍/去重 species 计数、单 species 成本与总成本、容量、L2 倍率、以及一次试掷结果摘要。

## Risks / Trade-offs

- **[默认实体状态导致变种掉落漂移]** 只记录 species，`type.create` 得默认状态：羊恒白羊毛、史莱姆恒最小尺寸、兔/狐/猫/马等取默认变种；且装备/命名牌本就不产 → 用户已确认可接受。
- **[FakePlayer 语义]** 它是 `ServerPlayer`，但从不入世界、不被 tick、不执行击杀；仅作 loot context 凭证。NeoForge 负责维度卸载清理。
- **[抢夺来自装备而非参数]** 若未来需要"抢夺 3 + 火焰附加"之类组合，需给 token 换更复杂的装备；v1 只放抢夺，保证生肉、排除 `smelts_loot`。
- **[`killed_by_player` 掉落依赖 token 存在]** 若哪天移除 token，僵尸铁锭/胡萝卜/土豆等会静默消失；以 spec 固化该依赖。
- **[高产量]** L2 满台 12 species × 翻倍 4 → 物品实体数量可观；聚合拆栈 + 单次空投，必要时可调 `shujouL2OutputMult`。
- **[不产经验/Luck/成就]** 与"与玩家击杀一次无异"在**掉落**层面一致，其余副作用刻意不复刻；以 spec 固化边界。
- **[空投落点受遮挡]** 沿用 `dropHeight`（首个遮挡前最高可穿过格，≤上限）；超限产物丢失，与既有家族一致。

## Migration Plan

纯新增：新行为类 + 注册 + 容量分派 + config + lang + 光柱色 + 调试探针。无存档迁移（典籍组件与 pattern 不变）。回滚：移除 `register(SHUJOU, ...)` 与容量分支 → 退回「成型但无行为、缓存 10000」。

## Open Questions

- 光柱紫的具体 RGB（实现期取一个与灵魂/余录主题协调的值）。
- 是否要在 GUI 额外显示"已忽略的未满典籍数"（噪音与有用性权衡）——默认只显示有效 species 数。
- `shujouCapacityMult` 在 3 级以上的溢出边界（当前 tiers 仅到 2；计算用 `long` 累积，配置用 `LongValue` 或 `IntValue` 视最终值域定）。
