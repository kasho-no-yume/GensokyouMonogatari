# Design: add-tool-sacrifice-rituals

## Context

四个「献祭工具产资源」仪式 pattern 已定稿：`oyamatsumi_circle`（镐/石）、`kukunochi_circle`（斧/木）、`haniyasu_circle`（铲/土）、`kaya_no_hime_circle`（锄/草）。均 `levels:[0,1,2]`、`tiers:[0,1,2]`；四重对称展开后祭品台数为 **4 / 8 / 12**（level 0/1/2）。Java 侧零实现（未注册 = 能成型、零产出）。JEI 名称已挂。

已有可复用范式：启停型 `serverTick`（Kagutsuchi）、数据驱动 `SimpleJsonResourceReloadListener`（`RitualRecipeLoader`）、JEI 仪式页签（`RitualRecipeCategory`）、灵力三段式 `SpiritPowerHelper.payCost`、祭品台代理箱（核心暴 `IItemHandler`，漏斗可自动铺料）、FX 基建（BER + `LaserDanmakuRenderer` 的 beam 几何）。

需求已与用户逐条确认：持续运行（非一次性）、工具随机消耗、附魔忽略、条件头颅不消耗、总权重模型、光柱仅产出瞬间 + 40t 冷却、JEI 一仪式一页签内按材质翻页。

### 已核验的 API（sources jar，2026-09-16）

- `net.minecraft.world.item.TieredItem#getTier(): Tier`（1.21.1 仍在）；`Tiers` 枚举 = `WOOD/STONE/IRON/DIAMOND/GOLD/NETHERITE`；NeoForge 给自定义 Tier 加了 `getTag()`。
- `ItemTags.PICKAXES / AXES / SHOVELS / HOES`。
- `SpiritPowerHelper.payCost(ServerLevel, BlockPos, RitualCoreBlockEntity, long/int)`：三段式（槽核→自身缓存→周围兜底）全有全无。
- `RitualCoreBlockEntity.getCapacity()` 为**按 patternId 的 if 分派链**，兜底 `DEFAULT_CORE_CAPACITY=10000`；`receive/extract/batteryStack/setBatteryStack` 齐备。
- `RitualPedestalBlockEntity#getHeld/setHeld` 单件不变量；核心代理箱以规范序对应全部台位。

## Goals / Non-Goals

**Goals:**

- 持续运行的资源转化仪式：启动后持续监控祭品台，「合规工具 + 灵力足够 + 冷却到」即结算。
- 结算 = 随机消耗一把工具 + 扣 `4000×4^L` 灵力 + 掷 `20×4^L` 次加权产出 + 同类聚合空投 + 光柱 + 40t 冷却。
- 总权重表模型（commons 桶 + 材质 special + 条件池 nether/end），数据驱动、一仪式一文件。
- 条件头颅（≥3 凋灵骷髅头 / ≥1 龙首）不消耗，仅解锁对应池。
- JEI 可查：每仪式一页签、按工具材质翻页，悬浮列权重（约 X%）与隐藏条件。
- 全部数值走 `GensokyouConfig`（COMMON）。

**Non-Goals:**

- 不做「点一次跑一次」（用户明确拒绝）。
- 不做头颅消耗。
- 不改 4 个 pattern 的结构切片（仅 `toggleable` 一行）。
- 不为 mod 自定义 Tier 做额外适配（读不出回退最低档）。
- 不做产物无敌/耐岩浆（用户明确不要）。
- 不动 `ritual_recipes` 配方系统与既有仪式行为。

## Decisions

### D1 行为族 = 抽象基类 `ToolSacrificeBehavior` + 4 子类

仿 `DayCycleGeneratorBehavior` / `Nichirin` / `Tsukikage`。基类实现全部骨架（tick、扫描、结算、投递、UI），差异点下放为抽象方法：

```
domain langPrefix / accentColor / 域标签
toolTag()            → PICKAXES | AXES | SHOVELS | HOES
lootTableId()        → gensokyou:oyamatsumi ...
```

`RitualBehaviors` 增 4 常量（`OYAMATSUMI/KUKUNOCHI/HANIYASU/KAYA_NO_HIME`）与 4 个 `register`。

### D2 持续运行 = `toggleable:true` + `serverTick` + enabled 门控

- **改 4 个 pattern JSON `"toggleable": false → true`**——启停按钮显隐唯一来源就是它；不改则 `serverTick` 是死路。
- `serverTick` 每 tick 被调用（仅 enabled 时）。因冷却 = 40t（非固定 20t），**不用 `ageTicks%20` 统一节流**，改为每 tick 判冷却。
- 灵力不足 / 无工具 → **待机**（保持 enabled，不自动停机、不报错）。
- **每次结算后强制等 `COOLDOWN_TICKS`（默认 40 = 2s）**，防止满速供灵刷屏。

### D3 每个核心的冷却状态 = BE 新增持久化字段

行为是单例、无 per-core 内存，冷却需要逐核心状态。方案：`RitualCoreBlockEntity` 增一个通用 `int actionCooldown`（NBT 持久化），每 tick 递减；结算时置为 config 值。否决 `WeakHashMap<BlockPos,Integer>`（重载即丢 + 泄漏风险）与塞进行为侧静态表。

### D4 工具识别 = 标签判类别 + `TieredItem.getTier()` 归一材质

- 类别：`stack.is(ItemTags.PICKAXES/AXES/SHOVELS/HOES)`（覆盖原版与进标签的 mod 工具）。
- 材质：`stack.getItem() instanceof TieredItem ti` → `ti.getTier()`；与 `Tiers` 枚举逐项**身份比对**得 `wood/stone/iron/diamond/gold/netherite`；读不出（自定义 Tier 实现）→ **回退最低档 `wood`**。
- **附魔一律按不附魔处理**（不读附魔、不减产、不特殊）。
- 扫描**全部**祭品台，收集所有合规工具 → **随机抽一个台位消耗**（其余原地留守）。

### D5 结算流程（顺序与原子性）

```
serverTick(level, corePos, match, core):
  if cooldown > 0: return
  tools = 扫描全部祭品台，取 (pos, stack) 且类别匹配
  if tools.isEmpty(): return                     // 待机
  cost = BASE_SP_COST × 4^level
  if !SpiritPowerHelper.canCover(level, corePos, core, cost): return   // 待机
  int skulls = 台上凋灵骷髅头数; int heads = 台上龙首数
  随机选一个工具台位 → 扣 1 件（单件不变量 → 置空）
  payCost(level, corePos, core, cost)            // 全有全无；canCover 同 tick 通过则必成
  table = 按材质 + 条件组装权重表
  n = BASE_COUNT × 4^level
  Map<Item,Integer> agg = 掷 n 次逐件加权 → 聚合
  投递：空投落点 + 光柱 FX
  cooldown = COOLDOWN_TICKS
```

顺序理由：**先验证工具与灵力、再提交**（消耗工具在前、扣费在后，两者同 tick 内无并发，`canCover` 通过后 `payCost` 不会再失败）。若实现期发现顺序敏感，改为「扣费 → 消耗工具」并在消耗失败时 `core.receive` 退款。

### D6 权重表模型 = 总权重（相对值，非写死概率）

```
某材质的最终抽取池 = commons桶(权重 = max(0, commonsTotal − Σspecial))
                    ⊕ special[]        （该材质固定加成）
                    ⊕ nether[]         （若 skulls ≥ skullsRequired）
                    ⊕ end[]            （若 heads  ≥ dragonHeadsRequired）
每次抽取按各条目权重归一化取 1 件；某权重为 0 的条目等价于不存在。
```

- **`commonsTotal` 默认 100**：commons 桶权重 = `max(0, 100 − Σspecial)`。
  - **大山祇合金镐** Σspecial = 100 → 桶 = 0（用户明确「不出石头」）。
  - **其余三仪式**合金阶 Σspecial < 100 → 桶非空（用户明确要求合金阶仍出 commons）。
- **nether/end 为额外加权**（不下沉 commons）；加性导致标称百分比略降，用户接受「约 X%」（`约等概率`）。
- **远古残骸**不写代码特判，直接在 `oyamatsumi` 的 `nether` 列表按材质写死：钻石镐 = `diamond_ore 权重 / 10` = 0.1，合金镐 = 0.5（仅地狱条件解锁时出现）。
- 权重可小数（0.1 / 0.5 / 0.6）。掷骰用累计权重法（世界无关纯静态函数，便于 `/gs_debug` 断言）。

### D7 数据文件与加载器

- 位置：`data/gensokyou/ritual_loot/<ritual>.json`（照 `RitualRecipeLoader` 的 loader 写法）。
- `RitualLootLoader extends SimpleJsonResourceReloadListener`：解析、校验、静态注册表、热重载安全。
- Record：

```java
record RitualLootTable(ResourceLocation patternId, ResourceLocation toolTag,
                       double commonsTotal, int skullsRequired, int dragonHeadsRequired,
                       List<Weighted> commons, List<TierTable> tables)
record TierTable(String tier, List<Weighted> special, List<Weighted> nether, List<Weighted> end)
record Weighted(Item item, double weight)
```

- 校验：未知 item id / 负权重 / 空表 → 拒载该文件并报因；`tier` 必须 ∈ {wood,stone,gold,iron,diamond,netherite}；缺 `tables` 条目该材质回退最低档或按空 special 处理（实现时定，默认第一档）。

### D8 条件头颅 = 存在即解锁、不消耗

- 每 tick 扫描时统计台上 `Items.WITHER_SKELETON_SKULL` 与 `Items.DRAGON_HEAD` 数量。
- `skulls ≥ skullsRequired` → 并入 `nether`；`heads ≥ dragonHeadsRequired` → 并入 `end`。
- 头颅**不消耗**，长期占台。L0 只有 4 台 → 「1 工具 + 3 骷髅头」刚好占满，**龙首必须升到 1 阶**（有意设计，写进 spec 场景）。

### D9 灵力端点与容量分派

- `usesCoreSocket()` 默认 true（可插灵力核心供能）。
- `spiritInRatePerSecond` = config 大值（`SACRIFICE_SPIRIT_IN_RATE`），使仪式可被万象共鸣路由选为受灵汇。
- `spiritOutRatePerSecond` = 0（不供灵）。
- `getCapacity()` 分派链新增 4 个分支 → `sacrificeCapacity(level) = SACRIFICE_BASE_CAPACITY × 4^L`（仿 `kagutsuchiCapacity`）。

### D10 config 旋钮（COMMON）

| 键 | 默认 | 用途 |
|---|---|---|
| `sacrificeBaseCount` | 20 | 0 阶单次产出件数（×4^L） |
| `sacrificeCountMult` | 4 | 每阶产出倍率 |
| `sacrificeBaseSpCost` | 4000 | 0 阶单次灵力消耗（×4^L） |
| `sacrificeSpCostMult` | 4 | 每阶灵力倍率 |
| `sacrificeSpiritInRate` | 100000 | 受灵速率（路由汇，大值） |
| `sacrificeCooldownTicks` | 40 | 结算后强制冷却（2s） |
| `sacrificeFallMaxHeight` | 20 | 核心上方空投最大高度 |
| `sacrificeSkullsRequired` | 3 | 地狱池头颅门槛 |
| `sacrificeDragonHeadsRequired` | 1 | 末地池龙首门槛 |
| `sacrificeBaseCapacity` | 10000 | 缓存容量基值（×4^L） |
| FX 相关（光柱高度/时长/宽度） | — | 见 D11 |

### D11 FX = 产出瞬间光柱（客户端 BER + 服务端最小状态）

- 服务端只在**结算瞬间**把「光柱剩余刻」（或起始 gameTime + 时长）写入核心的同步渲染态（复用既有 `RitualRenderState` / BE `getUpdatePacket` 链路）。
- 客户端 BER（抄 `RitualCoreRenderer` 结构 + `LaserDanmakuRenderer` / `FxGeometry` 的 beam 几何）渲染从核心升起的巨大光柱，带 ramp in/out（复用 `fxRampTicks` 口径），**结束后消失**，不做持续粒子。
- 红线：服务端 `sendParticles` 逐 tick 播放 = 网络包风暴，禁止；必须 BER。
- 贴图：优先复用 laser beam 贴图改色；若需独立材质 → 走 gen-textures skill 新做一张（实现期定）。

### D12 产出投递 = 同类聚合 + 空投落点

- 掷 `n = 20×4^L` 次 → `Map<Item,Integer>` → 按 `maxStackSize` 拆叠（减少实体数，防卡服）。
- 落点：**XZ 复用既有被动掉落圆盘**（`RITUAL_OUTPUT_DROP_RADIUS`，均匀圆盘随机）——用户明确「复用那个随机甩」。
- 高度：从核心 `+1` 向上扫，取 `(coreY, coreY+maxHeight]` 内**连续为空（可穿过）的最高 y**（首个遮挡方块前），在此生成产物自由落下。
- **不设无敌 / 不防岩浆**（用户明确不要）。

### D13 JEI 展示

- 每仪式**一个专属页签**（`DEDICATED_TABS` 增 4 行），页签内**按工具材质 6 页**（JEI 原生翻页，不拆成 24 张独立卡）。
- 卡面：标题（仪式名 + 材质）、工具输入槽（标签代表物）、产物网格。
- **悬浮**：每个产物条目显示权重 → `约 X%`（X = 该权重 / 当前池总权重 × 100，取一位小数）；卡脚/悬浮列出隐藏条件（`≥3 凋灵骷髅头 → 解锁：…`、`龙首 → 解锁：…`）。
- 数据源为 `ritual_loot`（非 `ritual_recipes`）→ 需修订 `jei-ritual-display` 的「数据单源」需求。

### D14 调试探针（可测内核）

- 权重掷骰 / 表组装 / 落点计算做成**世界无关纯静态函数**（先例 `DayCycleGeneratorBehavior.triangle`、`BafangGuiyuanBehavior.weightedSplit`）。
- `/gs_debug sacrifice <corePos>`：打印识别到的工具与材质、条件计数、组装后的完整权重表（含约 %）、试掷一次结果；便于 harness 断言。

### D15 commons 桶的内部比例与每材质覆盖

- 顶层 `commons[]` 为**桶内相对权重**；实际权重 = 相对权重 × `桶权重 / Σ相对权重`，其中 `桶权重 = max(0, commonsTotal − Σspecial)`。
- 允许每材质可选 `commonsOverride[]` 整体覆盖顶层 commons——用于「高阶适当提高稀有石（紫水晶块等）概率」；缺省用顶层。
- 这让大山祇木镐 `coal 0.5` 的标称概率精确 = 0.5%（0.5 / 100），符合用户原始百分比。

## 四张权重表（草案，可调）

> 单位 = 总权重（相对值）；`石/木/土/草` 为 commons 桶内相对权重（会被按桶权重缩放）。

### 大山祇神之座（镐 / 石）

| 材质 | special | 桶权重 |
|---|---|---|
| wood | `coal` 0.5 | 99.5 |
| stone | `coal_ore` 1 · `iron_ore` 0.5 | 98.5 |
| gold | `lapis_ore` 1 · `coal_ore` 2 | 97 |
| iron | `iron_ore` 5 · `gold_ore` 1 · `lapis_ore` 3 · `redstone_ore` 3 · `diamond_ore` 0.1 | 87.9 |
| diamond | `iron_ore` 10 · `gold_ore` 3 · `diamond_ore` 1 | 86 |
| netherite | `diamond_ore` 5 · `iron_ore` 25 · `gold_ore` 10 · `lapis_ore` 30 · `redstone_ore` 30 | **0（不出石头）** |

- commons：`stone` 60 · `deepslate` 12 · `cobblestone` 8 · `granite` 4 · `diorite` 4 · `andesite` 4 · `tuff` 2 · `calcite` 0.6 · `dripstone_block` 0.6 · `amethyst_block` 0.2（稀有，高阶 `commonsOverride` 提升）。
- **nether（≥3 凋灵骷髅头）**：`netherrack` 12 · `magma_block` 0.2 · `nether_quartz_ore` = 该材质 `lapis_ore` 权重（无则省略：gold 1 / iron 3 / netherite 30）· `ancient_debris` = `diamond_ore` 权重 / 10（diamond 0.1 / netherite 0.5，其余无）。
- **end（≥1 龙首）**：`end_stone` 12 · `purpur_block` 0.2。

### 久久能智神庭（斧 / 木）

| 材质 | special |
|---|---|
| wood | `apple` 0.5 |
| stone | `apple` 1 · `cocoa_beans` 0.5 |
| gold | `bamboo` 1 · `apple` 2 |
| iron | `apple` 5 · `bamboo` 1 · `cocoa_beans` 3 |
| diamond | `apple` 10 · `bamboo` 3 |
| netherite | `apple` 5 · `bamboo` 25 · `cocoa_beans` 10 · `honeycomb` 30（Σ=70 → 桶非空） |

- commons：各色原木（`oak_log` 20 · `spruce_log` 12 · `birch_log` 12 · `cherry_log` 8 · `dark_oak_log` 8 · `acacia_log` 6 · `jungle_log` 6 · `mangrove_log` 4）· `oak_planks` 12 · `stick` 8 · 幼苗（`oak_sapling` 1 · `spruce_sapling` 0.5 · `cherry_sapling` 0.5）。
- **nether（≥3 凋灵骷髅头）**：`crimson_stem` 6 · `warped_stem` 6 · `nether_wart_block` 3 · `shroomlight` 0.5。
- **end（≥1 龙首）**：`chorus_flower` 2 · `chorus_fruit` 1。

### 埴山姬神之壤（铲 / 土）

| 材质 | special |
|---|---|
| wood | `clay_ball` 0.5 |
| stone | `clay` 1 · `flint` 0.5 |
| gold | `flint` 1 · `clay` 2 |
| iron | `clay` 5 · `flint` 1 |
| diamond | `clay` 10 · `flint` 3 |
| netherite | `clay` 5 · `flint` 25（Σ=30 → 桶非空） |

- commons：`dirt` 30 · `coarse_dirt` 12 · `rooted_dirt` 12 · `mud` 10 · `sand` 12 · `red_sand` 8 · `gravel` 10 · `moss_block` 6。
- **nether（≥3 凋灵骷髅头）**：`soul_sand` = 该材质 `clay` 权重 × 0.8 · `soul_soil` = 该材质 `clay` 权重 × 0.6（「权重和黏土相同稍低」）。
- **end：无**（用户定：铲无末地配方，龙首对埴山姬无效）。

### 草野姬神之亭（锄 / 草）

| 材质 | special |
|---|---|
| wood | `wheat_seeds` 0.5 |
| stone | `wheat_seeds` 1 · `melon_seeds` 0.5 |
| gold | `melon_seeds` 1 · `pumpkin_seeds` 2 |
| iron | `wheat_seeds` 5 · `melon_seeds` 3 · `pumpkin_seeds` 3 · `glow_berries` 0.1 |
| diamond | `wheat_seeds` 10 · `glow_berries` 1 |
| netherite | `wheat_seeds` 5 · `melon_seeds` 10 · `pumpkin_seeds` 30 · `glow_berries` 30（Σ=75 → 桶非空） |

- commons：`short_grass` 30 · `fern` 12 · `moss_carpet` 10 · `pink_petals` 6 · `dandelion` 6 · `poppy` 6 · `cornflower` 4 · `azure_bluet` 4 · `oxeye_daisy` 4 · `allium` 4 · 郁金香（红/橙/白/粉各 3）· `lily_of_the_valley` 2。
- **nether（≥3 凋灵骷髅头）**：`nether_wart` 3 · `crimson_roots` 2 · `warped_roots` 2 · `crimson_fungus` 1 · `warped_fungus` 1 · `twisting_vines` 1 · `weeping_vines` 1 · `shroomlight` 0.5。
- **end：无**（紫颂花/果只归斧头配方）。

## Risks / Trade-offs

- **[L0 台位刚好塞满] 3 骷髅头 + 1 工具占满 4 台 → 龙首必须升 1 阶** → 有意设计，写进 spec 场景；玩家升级后 8 台充裕。
- **[总权重加性 → 标称概率漂移] nether/end/远古残骸额外加权使各项实际 ≈% 略低于标称** → 用户接受「约」语义；JEI 显示改用「约 X%」。
- **[mod 自定义 Tier 读不出] 回退最低档可能白送或吃亏** → 文档化；仅覆盖 `TieredItem` 子类。
- **[产物无无敌/不防岩浆] 落入岩浆即毁** → 用户明确要求不做；落点在核心上方，风险由玩家承担。
- **[光柱 FX 频繁] 满速供灵下** → 40t 冷却 + 仅结算瞬间播放，上限 0.5 次/秒。
- **[行为单例无 per-core 态] 冷却丢失会连发** → D3：BE 持久化 `actionCooldown`。
- **[JEI 数据单源 requirement 变更]** → 同步修订 `jei-ritual-display` delta spec。
- **[光柱贴图] 可能需新材质** → 优先复用 laser beam 贴图改色；确需新做则走 gen-textures。
- **[山顶/半空结构落点] 遮挡扫描仅到 +20** → 超出则无产物生成（待机）；实现时按「首个遮挡方块前」严格取值并加日志。

## Migration Plan

纯新增数据/行为/注册 + 4 个 pattern JSON 一行开关（`toggleable`）。无存档/数据迁移。回滚：移除 4 个注册项 + `toggleable` 改回 `false` → 退回「成型但无行为」现状。

## Open Questions

- 光柱贴图是否复用 laser beam（实现期定）。
- nether/end 具体权重为草案值，待实机手感微调（用户已认可先出草案）。
