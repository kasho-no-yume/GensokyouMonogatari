## Context

### 1.21.1 + NeoForge 的药水模型（反编译核实，非推测）

NeoForge 21.1 为 1.21 新增了**真正的药水类型注册表** `Registry<POTION>`（`BuiltInRegistries.POTION`，`Potion implements FeatureElement`）。1.20 时代"荧石 = 所有药水 +1 品质 / 红石 = 所有药水 +1.5 时长 / 下界之泪 = +2 品质"的通用增幅器**在 1.21.1 已被彻底删除**：

| 1.20 及以前 | 1.21.1（本项目） |
|---|---|
| `ingredient → effect` 映射表 | `PotionBrewing.Mix<Holder<Potion>>` 有向边（from 药水 + 试剂 → to 药水） |
| 荧石/红石通用增幅 | 每个变体是**独立注册条目**：`minecraft:strength` / `minecraft:long_strength` / `minecraft:strong_strength` |
| 下界之泪 +2 品质 | **不存在**（`addVanillaMixes` 中无任何 nether tear 条目） |

`Potions.java` 真值（节选，完整表见 D3）：

```
STRENGTH       = MobEffectInstance(DAMAGE_BOOST,     3600, 0)
LONG_STRENGTH  = MobEffectInstance(DAMAGE_BOOST,     9600, 0)   ← 注意 amp 仍是 0
STRONG_STRENGTH= MobEffectInstance(DAMAGE_BOOST,     1800, 1)
STRONG_SLOWNESS= MobEffectInstance(MOVEMENT_SLOWDOWN, 400, 3)   ← 注意 amp 是 3，不是 1
TURTLE_MASTER  = SLOWDOWN(400,3) + RESISTANCE(400,2)             ← base 本身就是 3/2
```

两个推论：
- **"红石 + 荧石同施"在原版确实做不到**（`LONG_STRENGTH` 是 amp 0，`STRENGTH→STRONG` 与 `STRENGTH→LONG` 互斥且无 `STRONG→LONG` 边）。这是本仪式的核心卖点。
- **"原版最高品质"不是统一的 amp 1**：力量顶到 amp 1（力量 2），但缓慢顶到 amp 3（缓慢 IV），神龟 base 就已经是 3/2。因此"品质 +1"若不设地板，会出现**本仪式 3 阶产物弱于原版强效药水的倒挂**。

### 产出构造方式（决定性约束）

`PotionContents` 在 1.21.1 只有三个字段：

```java
record PotionContents(Optional<Holder<Potion>> potion,
                      Optional<Integer>       customColor,
                      List<MobEffectInstance>  customEffects)
```

- `getAllEffects()` = `potion` 的效果 **concat** `customEffects`。若保留 `potion` holder 又改写 `customEffects`，**效果会翻倍**。故改写时长/品质**必须** `potion = Optional.empty()`。
- `PotionItem.finishUsingItem` 走 `PotionContents.forEachEffect`（holder 效果 + customEffects 一起施加），故 `potion` 置空不影响饮用。
- `PotionItem.getDescriptionId` → `Potion.getName(potion(), "item.minecraft.potion.effect.")`。`potion` 为空时返回 lang key `item.minecraft.potion.effect.empty`，原版英文即 **"Uncraftable Potion"**。故凡 `potion` 置空的产物**必须补 `DataComponents.CUSTOM_NAME`**，否则玩家看到"不可酿造药水"。

结论：**1 阶保留 `potion` holder**（效果与原版某条目完全一致，名字白嫖原版 lang）；**2/3 阶 `potion` 置空 + `customEffects` 改写 + `CUSTOM_NAME` 走本 mod 新 lang 键**。

### 现状约束

- `sunako_circle` pattern 已存在且校验全绿：levels 1/2/3、`tiers:[1,2,3]`、`toggleable:false`，四重展开后 **330 / 838 / 1186 格**，祭品台 **4 / 4 / 8** 个。**本次不改动 pattern。**
- 数值后果：单批满产耗灵 160,000 / 600,000 / 4,000,000，缓存 200,000 / 1,000,000 / 8,000,000。**三阶的瓶颈都是祭品台数，灵力只在缓存被消耗到不足时才成为瓶颈**——这正是"能做几份产几份"的触发场景（1 阶 4 台满产需 16 万，剩 5 万时只炼 1 瓶）。
- `RitualPedestalBlockEntity.setHeld` 有硬钳制：`count > 1` 当场把余量掉地。因此"台位数 = 产量上限"是硬约束，`sanzu_flask` 虽 `stacksTo(16)` 也存不进台子。
- `RitualCoreMenu` 现有两个功能槽（`BatterySlot` index 0、`TargetSlot` index 1），后者校验器在客户端分支**硬编码 `instanceof AmpCoreItem`**，且 `SLOT_FUNCTION_END = 2` 是常量，`quickMoveStack` 的转移区间依赖它。

## Goals / Non-Goals

**Goals:**
- 让 `gensokyou:sunako_circle` 从"空壳 pattern"变成可游玩的完整仪式。
- 建立**注册表驱动**的药水产出管线：新增模组注册 `Potion` 后无需改代码即可被覆盖。
- 把"核心 GUI 额外物品槽"从星移专属硬编码提升为**任意仪式可声明 N 格**的通用机制。
- 把"电池 → 缓存"确立为非发电仪式的默认灵力流向，消除逐个仪式 override 的历史包袱。
- 把核心可测逻辑（药水变换、试剂解析、批量结算）做成**世界无关纯静态函数**，可用 JUnit 直接覆盖。

**Non-Goals:**
- 不做投掷/滞留药水变体（需求明确排除）。
- 不改 `sunako_circle` 结构，不加 2 阶祭品台，不补 2 阶仪式石（设计原样）。
- 不为仪式产出药水做"免二次缩放"标记（`EffectDurationHandler` 的二次乘算属预期）。
- 不做投掷物动画/酿造台动画；特效只用现成光柱（`core.triggerSacrificeFx`）。
- 不加 `/gs_debug` 子命令与实机自动测试 harness。
- 不实现玩家自定义药水配方（超出 `brew_recipes` 数据包声明的粒度）。

## Decisions

### D1. 试剂 → 基础 Potion 的解析：C 方案（显式数据优先 + 酿造台反查回落）

**决策**：新建 `data/gensokyou/brew_recipes/*.json`，由 `RitualBrewRuleLoader` 加载；未被显式声明的试剂**回落到向 `PotionBrewing` 反查**。

反查实现（**启动期跑一次并缓存，运行时绝不重算**）：

```
for (Holder<Potion> probe : BuiltInRegistries.POTION)          // ~40 项
    potionStack = PotionContents.createItemStack(Items.POTION, probe)
    for (reagent : 候选试剂集)                                  // 原版 16 个 + 数据包声明的
        if (potionBrewing.hasMix(reagent, potionStack))
            → 记录 (reagent, probe, potionBrewing.mix(reagent, potionStack))
过滤三条：
  ✗ mix 结果 == 输入（无变化）
  ✗ 结果物品不是 Items.POTION（滤掉容器类 mix：枪粉→喷溅、龙息→滞留）
  ✗ 结果 potion 是 MUNDANE / THICK（1.21 的废招：WATER+任意试剂 都给 MUNDANE）
```

**为什么不用"单一探针"**：用户最初提议用 MUNDANE（地狱疣链的终点）当探针，但 `PotionBrewing.addStartMix` 展开后是

```java
addMix(Potions.WATER,  reagent, Potions.MUNDANE);   // 水 + 烈焰粉 = 平凡（无效果）
addMix(Potions.AWKWARD, reagent, result);          // 苦艾 + 烈焰粉 = 力量 ✓
```

`mix()` 仅在 `mix.from.is(potion)` 命中时替换，故 **MUNDANE 探针对所有 `addStartMix` 试剂一律查不到**。用 AWKWARD 探针可行，但只覆盖"从苦艾出发"这一类边；模组可能注册任意 `from → to` 的边。全注册表扫描是唯一能覆盖模组任意边的做法，代价仅是启动期一次。

**替代方案**：
- 纯数据文件（无反查）：模组新试剂无法自动覆盖，玩家需手写条目。**否决**——违背"读取 forge 药水注册"的核心诉求。
- 纯反查（无数据文件）：无法表达"LONG_/STRONG_ 兄弟"与黑名单，且 GUI 无法提前列清单。**否决**。

**性能**：`hasMix` 是 O(mixes) 线性扫（vanilla ~110 条 + 模组追加）。40 × (16+声明数) 次比较 ≈ 数千次，一次性开销可忽略。索引按 `(dimension-independent) reagent item` 缓存，`/reload` 后重建。

**reload 语义**：**每次点击启动时现查**。`/reload` 改写映射后，旧试剂立即变为"无效"，GUI 明示。理由：玩家改数据包后立刻见效，且避免"锁定的 Potion holder 在 reload 后指向已删除条目"的悬空引用。

### D2. 药水阶级变换：以 `Registry<POTION>` 为事实源的 LONG/STRONG fallback 链

**决策**（`PotionTierTransform`，世界无关纯静态）：

```
输入: Holder<Potion> base, int tier (1..3)
输出: PotionContents

effects = base.value().getEffects()            // List<MobEffectInstance>，可能多效果
变换(每个 effect E):
  long_   = 在 Registry<POTION> 中按命名找 base 的 "long_" 兄弟，且其 effects 含同一 effect
  strong_ = 同理找 "strong_" 兄弟
  tier 1:
      E 原样（保留 potion holder，白嫖原版名）
  tier 2:
      E.amplifier = E.amplifier + 1                     ← 无条件（"红石+荧石同施"）
      E.duration = long_ 命中 ? long_.E.duration
                 : strong_ 命中 ? strong_.E.duration       ← 保住瞬发效果的 1 tick
                 : allowDurationFallback ? E.duration × LONG 倍率
                 : E.duration
  tier 3:
      先按上式求出 2 阶结果
      E.amplifier = max(E.amplifier + 1, 原版可达最高 amp)   ← 品质地板
      E.duration  = 2 阶时长（3 阶不再延长）
  构造:
      tier 1 → new PotionContents(Optional.of(base), Optional.empty(), List.of())   // 白嫖原版名
      tier 2/3 → new PotionContents(Optional.empty(), Optional.empty(), 改写后的 effects)
```

**为什么 2 阶要"同时"改两件事**：原版 1.21.1 已删除通用增幅器，长时效与高品质是两条**互斥**的注册条目（`long_strength` 的品质恒为 0）——"8 分钟的力量 II"原版根本造不出来，而这正是本仪式 2 阶的卖点（用户指定样本：力量 1 阶 I/3:00 → 2 阶 II/8:00 → 3 阶 III/8:00）。3 阶则在 2 阶之上再 +1，得到原版不存在的品质 III 而时长不变。

**兄弟查找规则**：`base` 的注册路径去掉 `long_` / `strong_` 前缀后应等于目标路径；modded `Potion` 没有命名约定，故 `brew_recipes` 条目**可选显式写** `"long_potion"` / `"strong_potion"`。

| 缺哪种兄弟 | 时长取法 |
|---|---|
| 无 `long_` 兄弟，有 `strong_` 兄弟 | 取 `strong_` 的真实时长（不缩放）——瞬发效果靠这条保住 1 tick |
| 两者皆无（幸运/潮涌/缠绕/浮肿/寄生） | 默认**保持基础时长**；`extend_without_long` 打开时按 `SUNAKO_LONG_DURATION_MULTIPLIER`（config，默认 **8/3**）合成 |

`amplify_without_strong` 保留解析但**当前实现不再读取**：2 阶的 +1 品质已是无条件动作，该开关已无对应分支。字段仅为兼容既有 `brew_recipes` schema 保留，MUST NOT 依赖它做逻辑判断。

**品质地板的取值**：`原版可达最高 amp` = `max(amp(base), amp(long_), amp(strong_))`，同样可被数据文件 `excluded_effects` 拉黑（黑名单内的效果**跳过品质提升**，只保留原 amp 与时长）。

**为什么要有地板**：没有地板时 3 阶缓慢 = amp 2（缓慢 3），而原版 `strong_slowness` 是 amp 3（缓慢 IV）——本仪式最高阶产物被原版中阶产物压制，玩家会读成"数值倒退"。地板把 3 阶缓慢拉到 amp 3。

**⚠️ 神龟是品质地板唯一会"过头"的效果**：`STRONG_TURTLE_MASTER` 的缓慢是 amp 5，故 3 阶被地板拉到缓 a5 —— 比"基础+2"高一级。这正是 `excluded_effects` 黑名单存在的理由（把 `movement_slowdown` 拉黑即可退回缓 a4）。默认黑名单为空。

**为什么用注册表关系而非硬编码倍率表**：modded `Potion` 的品质天花板与时长分布无法预知，硬编码表要么漏要么猜。注册表关系是唯一自适应的口径。

### D3. 变换结果真值表（原版 1.21.1 全量）

由 `Potions.java` 逐条推导，作为回归基线（**2 阶一律同时 +1 品质并延长时长**）：

```
药水              1阶                2阶                        3阶              依据
力量 strength     a0 3600 (3:00)     a1 9600 (8:00)             a2 9600 (8:00)   用户指定样本
缓慢 slowness     a0 1800 (1:30)     a1 4800 (4:00)             a3 4800         ★地板把 2 顶到 3
缓降 slow_falling a0 1800 (1:30)     a1 4800 (4:00)             a2 4800         仅 LONG
跳跃 leaping      a0 3600            a1 9600                    a2 9600         仅 LONG
迅捷 swiftness    a0 3600            a1 9600                    a2 9600         仅 LONG
抗火 fire_res     a0 3600            a1 9600                    a2 9600         仅 LONG
水下 water_brth   a0 3600            a1 9600                    a2 9600         仅 LONG
夜视 night_vis    a0 3600            a1 9600                    a2 9600         仅 LONG
隐身 invis        a0 3600            a1 9600                    a2 9600         仅 LONG
中毒 poison       a0 900  (0:45)     a1 1800 (1:30)             a2 1800         仅 LONG
虚弱 weakness     a0 1800 (1:30)     a1 4800 (4:00)             a2 4800         仅 LONG
恢复 regeneration a0 900  (0:45)     a1 1800 (1:30)             a2 1800         仅 LONG
神龟 turtle_m     缓a3+抗a2 0:20     缓a4+抗a3 0:40             缓a5+抗a4 0:40   LONG / 地板
治疗 healing      a0 1 (瞬发)        a1 1                       a2 1            仅 STRONG
伤害 harming      a0 1 (瞬发)        a1 1                       a2 1            仅 STRONG
幸运 luck         a0 6000 (5:00)     a1 6000                    a2 6000         ★两兄弟皆无→时长不延长
潮涌 wind_chgd    a0 3600            a1 3600                    a2 3600         ★两兄弟皆无→时长不延长
缠绕 weaving      a0 3600            a1 3600                    a2 3600         ★两兄弟皆无→时长不延长
浮肿 oozing       a0 3600            a1 3600                    a2 3600         ★两兄弟皆无→时长不延长
寄生 infested     a0 3600            a1 3600                    a2 3600         ★两兄弟皆无→时长不延长
```

两点须留意：

- **瞬发效果（治疗/伤害）保住 1 tick**：它们没有 `long_` 兄弟，若改用倍率缩放会把 1 撑成 3。故无 LONG 时改取 `strong_` 的真实时长（也是 1）。
- **末 5 条只涨品质、不涨时长**：原版从未造过它们的长效变体，无从抄值。默认（`extend_without_long` 关）保持基础时长；逐条打开后按 `SUNAKO_LONG_DURATION_MULTIPLIER` 合成。

注意 LONG 倍率**不统一**：力量/缓慢/缓降/跳跃/迅捷/抗火/水下/夜视/隐身/虚弱 = 8/3；中毒/恢复/神龟 = 2.0。所以"缺 LONG 兄弟时统一退 8/3"对原版条目永远不触发（它们都有 LONG），只对 modded 条目与上述末 5 条生效。

### D4. 批次触发：一次性按钮，不走启停

**决策**：`handlesStartViaUiAction() → true`，`uiActions` 注入单个「开始炼药」，pattern `toggleable: false`（已就位）。与源初造化同范式。

```
onUiAction(ACTION_BREW):
  1. 解析试剂槽 → base Potion（现查，见 D1）
  2. 扫祭品台：held 是 sanzu_flask 的台位 → valid 列表（其余完全忽略）
  3. unitCost = SUNAKO_UNIT_COST[level]
     affordable = min(valid.size(), core.getStored() / unitCost)      // 不用 canCover
  4. if (affordable <= 0) → 回一条 "灵力不足" 信息，返回 FAIL
  5. core.extract(affordable * unitCost)                                // 一次性抽取
  6. 逐台 pedestal.setHeld(炼好的药水)                                  // 原位替换
  7. core.triggerSacrificeFx(FX_PILLAR_TICKS.get())                      // 光柱
  8. 推送 GUI 快照
```

**为什么用 `core.getStored()/extract` 而非 `SpiritPowerHelper.canCover/payCost`**：用户明确「灵力永远只是使用缓存」。三段式 `available()` 会把半径 3 内其他仪式核心（尤其八方归元那类 `SpiritBank` 的灵力核物品）算进来，与"只用自身缓存"冲突。侯重乃丁坊用三段式是它的既有选择，本仪式不复用。

**"尽力做"的语义**：`affordable = min(台位数, 缓存 ÷ 单价)`，取整除；不做足额预检。1 阶 4 台 + 缓存 5 万 → `min(4, 50000/40000=1) = 1` 瓶。**零消耗返回 FAIL**（没有任何可炼之物时连启动都不启动），部分成功则照常产出。

**为什么瞬间完成**：需求明确"不太需要时间"。整批在同一 tick 内写回，光柱特效掩盖突兀感。代价是无过程感——接受。

### D5. 缓存 / 速率 / 单价：`getCapacity()` 必须显式分派

```
        缓存         inRate/s    单价      祭品台   满批耗灵   缓存÷单价   灌满
 1阶   200,000       10,000     40,000      4      160,000     5 瓶      20.0s
 2阶 1,000,000       50,000    150,000      4      600,000     6 瓶      20.0s
 3阶 8,000,000      200,000    500,000      8    4,000,000    16 瓶      40.0s
```

三档都不是 `base × 4^L` 几何序列，**漏掉 `getCapacity()` 分派会静默回落 `DEFAULT_CORE_CAPACITY = 10000`**（1 阶只够 0 瓶，完全不可用且不报错）。先例：`BousenBehavior.capacityOf` 就是专门为此写的分派。

3 阶缓存由 5,000,000 提到 8,000,000 的理由：8 台 × 500,000 = 4,000,000/批，800 万恰好 = **2 个满批次**。副作用是 3 阶灌满时间从 20s 变 40s——用户决定**不改 inRate**，接受该不对称。

**缓存常驻可见**（非会话态 0 容量）：少名是"蓄水池"模型，玩家要先攒灵力再点火，对齐星移之仪的持久缓存。空闲容量为 0 会让仪式对万象共鸣完全隐身（`spiritOutRatePerSecond > 0` 才有源资格、`getCapacity() > 0` 才接得住），蓄水池就失去意义。

### D6. `RitualExtraSlots`：泛化额外物品槽

**决策**：新增接口 `RitualExtraSlots`，`RitualCoreMenu` 改为按固定上限装配。

```java
public interface RitualExtraSlots {
    int slotCount();                                  // 0 = 不声明
    boolean isSlotValid(int slot, ItemStack stack);  // 服务端权威校验
    default String labelKey() { return "gui.gensokyou.ritual.extra_slot"; }
    default int slotX(int index);                     // 缺省横排一行
    default int slotY(int index);
}
```

BE 侧持有 `ItemStack[MAX_EXTRA_SLOTS]`（上限 4）与一个通用 `ExtraSlotsHandler`：
每格恒为 1 个（与祭品台的一台一件同理），合法性转交行为；落盘键 `ExtraSlots`（ListTag），
**读回时兼容旧键 `SeiiTargetCore` → `extraSlots[0]`**，旧世界里的星移增幅核不会被吞。

**⚠️ 实现期修正：槽位数量按固定上限注册，不是按真实数量动态注册。**
原计划让两侧都按行为声明的 `slotCount()` 装配，但菜单在 `ClientboundOpenScreenPacket`
之后构造，而**该包的附加数据只有 `BlockPos` 一个字段**——客户端拿不到"本仪式有几格"。
两侧注册数量一旦不一致，`ContainerSetContent` 就会抛 `Slot N not in valid range`
并把玩家踢下线。故改为：两侧一律注册 `MAX_EXTRA_SLOTS = 4` 格，可见子集由行为声明经
同步载荷收敛（沿用既有 `TargetSlot.setShown` 范式）。`functionEnd = 1 + MAX` 仍是算出来的，
`quickMoveStack` 三条路径全部改读它。

槽坐标：`(9 + 20i, 61)`，即第 0 格与迁移前的星移目标槽**完全重合**，玩家看到的核位置不变；
4 格右沿 105px，仍在信息盒右钳界 112 之内、不侵入 x≥120 的按钮列（`RitualCoreSlotGeometryTest`
逐项锁定）。单槽时标注画在槽右侧（原行为），多槽时行内放不下，改由信息区 `InfoLine` 说明。

**为什么客户端不校验物品类型**：客户端拿不到服务端的行为实例与数据包映射表，硬编码校验器
（既有 `TargetSlot` 写死 `AmpCoreItem` 就是这个坑的产物）在每新增一个仪式时都会成为必须
同步维护的缺陷源。服务端 `SlotItemHandler` 权威拒收并回滚；已知代价是"能放进去但拿不出"的粗糙感。

**迁移**：星移的 `usesTargetSlot()` → 实现 `RitualExtraSlots`（1 格、坐标不变、
服务端 `isSlotValid` 收 `AmpCoreItem`、沿用原标注键）；`TargetSlot` 类退役，由 `ExtraSlot` 取代；
`SeiiService` 三处改读 `core.extraSlot(SeiiService.SEII_CORE_SLOT)`。

### D7. `refillsCacheFromSocket()` 默认值反转为 `true`

**决策**：默认 `true`（非发电仪式一律"电池 → 缓存"，每 tick `core.tickBatteryToCacheFill()`），5 个发电仪式显式覆写 `false`：

```
KagutsuchiFlameBehavior     （手动 SpiritCoreItem.receive 推电池，见其 177-184 行）
YumewatariBehavior
DayCycleGeneratorBehavior   （Nichirin / Tsukikage 共用基类）
BousenBehavior
SairEnergyBehavior
ResonanceRelayBehavior      （缓存恒 0，防御性标注）
```

`serverPassiveTick` 的调用点已存在（框架统一分发），只需行为各自覆写 `serverPassiveTick` 调 `tickBatteryToCacheFill()`。当前已覆写 `true` 的 5 个（灵浴 / 侯重 / 结界破碎 / 无尽藏 / 百鬼夜行）可**删掉 override**（行为不变，代码更干净），也可保留——建议删，保持"只有例外才 override"的形态。

**受影响的 8 个既有仪式**（当前默认 false，反转后开始接受槽核灌注）：金屋彦、星移、埴山姬、久久能智、草野姬、大山祇、绵津见、众生余录。**逐个实机确认平衡未破坏**是本次的必做验证项——尤其金屋彦，它 `serverTick` 里 `advanceJob` 持续 `core.extract(cost)`，若同时被动补电会变成永动机。

### D8. 红石触发泛化

**决策**：在 `RitualBehavior` 提供默认实现——

```java
default boolean redstoneTriggersUiAction() { return handlesStartViaUiAction(); }
```

框架侧（`RitualCoreBlock.neighborChanged` 已有红石上升沿分发点）在 `redstoneTriggersUiAction()` 为真且未覆写 `onRedstonePulse` 时，改为执行 `onUiAction(..., 第一个 enabled action id)`。

**为什么需要 opt-out**：八百万神恩（会话是唯一启动路径）、百鬼夜行（"召唤是起手式，让红石脉冲在玩家不在场时自动开打"——代码里有明确注释论证）都必须关掉。已有 2 个反面先例说明这不是"没人想到"，而是刻意设计。

**为什么放在框架而不是让每个行为自己写**：用户诉求是"能手动触发式的仪式都可以红石控制"。逐个覆写 `onRedstonePulse` 是重复劳动且必漏。

### D9. JEI 炼药页签

**决策**：新增静态 `RecipeType<BrewRecipe>`，启动期（配方加载完成后）枚举完整「试剂 → 药水」全集并 `addRecipes`。

**可行性核实**：JEI 19.44 的 `IRecipeManager` **没有 `addCategories`**（页签不能运行时注册），但 `addRecipes` 随时可调。故"启动期静态注册页签 + 之后填充内容"可行。可枚举性成立：候选试剂集 = 原版 `addVanillaMixes` 中出现的 16 个 `addStartMix` 试剂 ∪ `brew_recipes` 声明的全部试剂；候选药水 = `Registry<POTION}` 全量。

**展示形态**：每个「试剂 → 药水」条目平铺 **3 个阶的产物图标**，tooltip 标注"1/2/3 阶"及效果明细。1 阶图标是原版药水（名字白嫖原版 lang），2/3 阶带 `CUSTOM_NAME`。

**配方页 vs 数据包**：`ritual_recipes/*.json` 的 schema 是**核心升级配方**（`ingredients` + `spCost` + `effect`，语义是"祭品台凑料 → 替换核心"），与"试剂 → 药水"无对应位置。故新建 `ritual_brew_recipes` 数据类型，结构照搬 `ritual_smelt_recipes`（金屋彦专属规则表，字段形态最接近）。

### D10. 数值一律进 config

```
SUNAKO_BASE_CAPACITY              = 200000     （1 阶值；2 阶 = ×capacityMultiplier）
SUNAKO_CAPACITY_MULTIPLIER        = 5
SUNAKO_CAPACITY_OVERRIDE_LEVEL3   = 8000000    （3 阶非几何，显式覆盖）
SUNAKO_BASE_IN_RATE_PER_SECOND    = 10000      （1 阶值；2 阶 = ×inRateMultiplier）
SUNAKO_IN_RATE_MULTIPLIER         = 5
SUNAKO_IN_RATE_OVERRIDE_LEVEL3    = 200000     （3 阶是 2 阶的 ×4 而非 ×5，同样要显式值）
SUNAKO_UNIT_COST_LEVEL1/2/3       = 40000 / 150000 / 500000
SUNAKO_LONG_DURATION_MULTIPLIER   = 2.6666667  （8/3）
SUNAKO_STRONG_DURATION_MULTIPLIER = 1.0
```

**阶数偏移**：本仪式最低阶是 1（pattern `levels = 1/2/3`），而项目通用的
`RitualScaling.scale(base, mult, level)` 把 `base` 当作 **0 阶**值。故容量与受灵两处显式用
`level - 1`，使配置里的基值就是"1 阶值"。单价无此问题（显式三档表）。

实现拆在 `SunakoScaling`，每个公式都带"显式传参"重载（口径同 `HoujounoTeihouBehavior`），
使单测能在**不加载 ModConfig** 的前提下断言（纯 JUnit 下 `ConfigValue.get()` 会抛
`Cannot get config value before config is loaded`，只能用 `getDefault()`）。

## Risks / Trade-offs

**[1] 反转 `refillsCacheFromSocket()` 默认值会改变 8 个既有仪式的运行时行为**
→ Mitigation：D7 列出的 5 个发电仪式逐个显式覆写 `false`；8 个受影响仪式（金屋彦/星移/四工具献祭/绵津见/众生余录）逐个实机跑一遍原有流程，确认没有出现"永动机"或"能量白产"。金屋彦风险最高（`serverTick` 持续抽电）。

**[2] `potion` holder 置空导致物品名退化为 "Uncraftable Potion"**
→ Mitigation：2/3 阶构造路径强制设 `CUSTOM_NAME`；把"产物必须有自定义名"写进 `PotionTierTransform` 的构造器断言，并在 lang 审计里覆盖 `item.gensokyou.potion.t{2,3}.*` 前缀。

**[3] 品质地板让 3 阶缓慢（a3）超出原版 `strong_slowness` 的语义预期**
→ Mitigation：`excluded_effects` 黑名单已预留；玩家可用数据包把某个效果拉黑，退回纯 `+1` 口径。默认黑名单为空（全部启用地板）。

**[4] 全注册表扫描的启动期开销随模组药水数线性增长**
→ Mitigation：缓存索引，只在 `/reload` 与首次访问时重建；`hasMix` 结果按 `(reagent, potion)` 记忆化去重。极端情况（数百个 modded `Potion` × 数十个试剂）仍在数十毫秒量级。

**[5] 客户端槽位放行不校验，非法物能被放进槽里**
→ Mitigation：服务端 `SlotItemHandler` 权威拒收并回滚；GUI 在槽位上方渲染当前试剂对应的产物预览，让玩家一眼看出"这个放对了"。接受"能放不能留"的粗糙感，换取"新增仪式无需同步客户端白名单"。

**[6] 一次性批次 + 无过程感，玩家可能反复点击刷光柱**
→ Mitigation：`serverPassiveTick` 的 `tickBatteryToCacheFill` 每 tick 补电，单次批次最快也在数百毫秒内耗尽 4 万起步的灵力；重复点击在缓存不足时直接 FAIL 并回信息行。

**[7] `sunako_circle` 的 2 阶增量一个仪式石都没有，2 阶产量与 1 阶相同（4 瓶）但单价贵 3.75 倍**
→ 已确认为设计原样，**不改**。风险是 2 阶的"性价比拐点"只体现在药水品质而非产量，若实测失衡再议。

## Migration Plan

1. 先落地纯逻辑层（`PotionTierTransform` / `BrewReagentIndex`）与 JUnit，用 D3 的真值表做回归基线。
2. 再落地 `RitualExtraSlots` 泛化机制 + 星移迁移，**单独验证星移洗练全流程无回归**（这是唯一有既有玩家可见行为的改动）。
3. 再改 `refillsCacheFromSocket()` 默认值 + 5 个发电仪式覆写，**单独验证 8 个受影响仪式**。
4. 最后接仪式本体（行为 / 容量分派 / 祭品台结算 / GUI / 数据包 / JEI / 指导书）。
5. 回滚：各步独立可 revert。`RitualExtraSlots` 迁移若出问题，可临时保留 `usesTargetSlot` 分支双跑一版。

## Open Questions

- **无阻塞项**。以下为实现期可微调项，不影响架构：
  - 2 阶"本阶新增 0 祭品台 / 0 仪式石"若实测导致 2 阶完全不值得建，可考虑后续补 4 台（会改 pattern，需重跑 `validate_ritual_pattern.py --test-out`）。
  - 3 阶 inRate 是否从 200,000 提到 400,000 以恢复"三档均 20 秒灌满"——当前按用户决定保持 200,000。
  - `sunako_circle` 的 3 阶祭品台位（canonical `["P",0,0,8]`）是否需要视觉上更明确的"炼药位"标识——属美术范畴，不在本次。