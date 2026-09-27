## Context

**现状。** 增幅核（`AmpCoreItem`，武器槽 3）的词条由 `RuneGenerator.ensureGenerated` 在首次获得时懒生成一次，写入 `rune_affixes` 数据组件。词条池是 `GensokyouConfig.RUNE_AFFIX_POOL` 里 5 条 `id,min,max,weight,tier` 字符串，条数由 `RUNE_AFFIX_COUNT`（现为 2/3/4）决定。`RuneGenerator.reroll` 存在但只是 `roll` 的直转调桩。核的属性通过 `RuneSummary`（5 字段定长 record）在 `WeaponFiring.tryFire` 里消费 4 个字段。

**玩家属性侧。** `AttributeKey` 枚举 18 键，`PlayerAttributes.finalValue = base + Σcontribution`（`AttributeMath.finalFrom`，`cap >= 0` 时封顶）。`SpiritPowerData` 提供 `max_spirit` / `spirit_power` 两个**台账字段**作为这两键的 `baseFn`。`GraceNumbers.GRACE_DEFAULT_ROWS` 给出 1~5 阶各键增量表，`YaoyorozuGraceService` 消费它做进阶与洗练。

**八百万神恩的会话范式。** `RitualCoreBlockEntity.GraceSession` 是 BE 内的会话态持有者（`phase` / `cost` / `collected` / `recipeId` / `initiator` / `pendingRefine` / `ticks`），`YaoyorozuGraceService` 推进阶段并解释 `ritual_recipes` 的 `effect`（`grace:advance_N` / `grace:refine_N`），`YaoyorozuGraceBehavior` 把会话态渲染成 `InfoLine[]`。`RitualCoreBlockEntity.getCapacity()` 对神恩/造化走"会话态容量 = 锁定配方 `spCost`，非会话期 = 0"。`RitualRecipeMatcher.apply` **真的消耗** `ingredients`。

**GUI 框架约束。** `RitualCoreScreen` 信息区视口只有 68px（`INFO_MAX_Y(128) - INFO_Y_START(60)`），行高 11px（带图标 18px），内容超出靠 `infoScroll` 滚动且**无滚动条、无更多提示**。可交互信息行（`CONTROL_BUTTON`）通过 `interactiveRowHits` 回报 `RitualCoreMenu.BUTTON_ACTION_BASE + actionId`，`actionId >= 10` 供行为自定义。`RitualCoreMenu` 是唯一容器；右侧按钮列 `actionButtons` 只有 3 槽（y=88/110/132），已被神恩占了 2 个。

**星移的 pattern 已存在。** `data/gensokyou/rituals/seii_circle.json`（浑天仪/星盘主题，`tiers:[1,3,5]`，`levels:1/3/5`，祭品台累计 4/12/20）与 `design/seii/gen_seii.py` 已落盘并通过留空契约自检。缺的是全部程序侧接线。

## Goals / Non-Goals

**Goals:**
- 给增幅核一个"重掷词条"的仪式出口，代价随阶指数增长，成为高阶灵力的长期 sink
- 高阶仪式可洗低阶核，反之不行
- 洗练交互与八百万神恩同构：启动 → 蓄灵 → 演出 → **全量替换 or 全量保留**，玩家只有一次二选一
- 让核能承载玩家属性词条，且走既有加区/乘区管线，不新增第三套结算
- 修好八百万神恩洗练预览的三个缺陷（面板不可见 / 关闭即作废 / initiator 掉线死锁）
- 弹幕核补齐可读 tooltip，让 `range_pct` 这类"看不见的乘区"有文本锚点

**Non-Goals:**
- 数值最终平衡（新预算已落在旧 spec 区间内，但权重分布待实机调）
- 弹幕合成台的"装配后武器属性总览"tooltip
- 玩家在仪式内**预选**词条键（已裁决：系统 roll，玩家只决定去留）
- 修 `healthBonusCap=200` 被神恩 5 阶 810 溢出的既有隐患（`health_bonus` 已排除出池，不被本 change 放大）
- 抽取跨仪式共享的 `ReviewSession` 抽象（见 D12）

## Decisions

### D1. 三条阶轴各管一件事，花费跟核阶

```
   仪式阶 (pattern level 1/3/5)        核阶 (AMP_CORE_T1/T2/T3)      玩家超人类阶
   ───────────────────────────         ──────────────────────         ─────────────
   管「能不能洗」                      管「洗出几条」+「花多少」      管「玩家有什么」
   门槛: L1→T1                        条数: 1 / 3 / 5                提供数值基准
         L3→T1,T2                     花费: 30,000 / 3,000,000
         L5→T1,T2,T3                          / 300,000,000
```

| 仪式阶 | 灵力花费（按核阶） | 缓存（×12/阶） | 受灵速率（×10/阶） | 缓存 ÷ 该阶最高核花费 |
|---|---|---|---|---|
| 1 | T1 = 30,000 | 50,000 | 20,000/s | 167% |
| 3 | T1 = 30,000 / T2 = 3,000,000 | 7,200,000 | 2,000,000/s | 240% |
| 5 | T1 = 30,000 / T2 = 3,000,000 / T3 = 300,000,000 | 1,036,800,000 | 200,000,000/s | 345% |

**受灵速率阶梯的真实推导**：`inrate = 该仪式阶能洗的最高核阶的花费 ÷ 1.5 秒`

```
  L1 最高可洗 T1 = 30,000     ÷ 1.5s =  20,000/s
  L3 最高可洗 T2 = 3,000,000  ÷ 1.5s = 2,000,000/s
  L5 最高可洗 T3 = 300,000,000 ÷ 1.5s = 200,000,000/s
```

于是"自然配对"（1 阶洗 T1 / 3 阶洗 T2 / 5 阶洗 T3）恒为 1.5 秒，**错配更快**（5 阶洗 T1 核只要 0.15 毫秒）。这构成两条互补的阶梯：

- **核阶 = 收益阶梯**（要更多词条、更高数值，必须换更贵的核）
- **仪式阶 = 吞吐阶梯**（同一个核，阶越高灌得越快、台位越多）

低配（1 阶仪式 + T1 核）因此是极易入手的早期循环：30,000 灵力、4 祭品台、1 条词条、1.5 秒。

**为什么花费不跟仪式阶走**（用户裁决为跟核阶）：若跟仪式阶走，5 阶仪式洗 T1 核要 300,000,000，"用高阶仪式洗低阶核"永远是坏选项，仪式阶的提升对玩家零正反馈。跟核阶走之后，仪式阶变成"更快的同一件事"，玩家能明确感知到"我该建 5 阶星移"，同时低阶仪式+低阶核仍然是一个成立的起点。

**"高阶洗低阶"的收益不会因此失控**：5 阶仪式洗 T1 核 = 30,000 灵力 + T1 催化剂 + 1 条词条，花的是低配的钱、拿的是低配的货，只是快。真正想拿 5 条词条就必须有 T3 核，而 T3 核只有 5 阶仪式洗得动。

**备选**：花费跟仪式阶走。已否决（理由同上；用户裁决为跟核阶走）。

### D2. `getCapacity()` 全程 = 阶梯值，不做会话态覆盖

这与神恩/造化的"会话态容量 = spCost、其余期 = 0"**刻意不同**。

```
   星移（全程阶梯）                    神恩（会话态）
   IDLE      cap=ladder  stored≤ladder  IDLE  cap=0        ← 残余灵力卡死
   PAYING    cap=ladder  collect 抽走    PAYING cap=cost
   PERFORM   cap=ladder                  PERFORM cap=cost
   REVIEW    cap=ladder                  REVIEW cap=0
   → 一次洗完剩 ladder−cost，结转下一次    → 无跨会话缓冲
```

代价：`getCapacity() > spCost`，`collect` 期间缓存可被抽到 0 而会话仍在蓄灵（此时来源退化为槽核 + 周围 3 格兜底）。收益：缓存是真实的、跨洗练持久的蓄水池，`ladder/cost` 的 167%/240%/345% 让"蓄满 → 连洗 1/2/3 次"成立，正是"略微减少下一次充灵时间"。

**备选 A**：沿用神恩口径（会话态容量 = spCost）。否决——会让 ×12 缓存阶梯几乎完全失去意义，且神恩的 0 容量会把上一次洗练的剩余灵力锁死。
**备选 B**：缓存 = 花费 × 40%。否决——用户明确给出 ×12/阶 的阶梯。

### D3. 核走核心 GUI 专用槽，不进 ingredients 也不占祭品台

```
  核在【核心 GUI 目标物品槽】(x=56, y=40，紧邻灵力核心槽)
        │  显隐由 RitualBehavior.usesTargetSlot 声明（缺省 false）
        ▼
  RitualCoreMenu.TargetSlot  ←→  RitualCoreBlockEntity.seiiTargetStack
                                  （ItemStackHandler 式，限 1 个增幅核，随存档持久化）
```

**为什么不放祭品台**：祭品台一台一件（代码级不变量）且是配方催化剂的载体。星移 1 阶只有 4 台，核若占一台就只剩 3 个催化剂位 —— 而 1 阶配方本来就要 4 味药（辰砂/灵铁/下界合金/钻石）。核有自己的 GUI 槽位后，4 个台位全部还给催化剂。

**为什么不进 ingredients**：`RitualRecipeMatcher.apply` 会**真的扣掉** takes。若把 `AmpCoreItem` 写成 ingredient，洗练的赌注在启动瞬间就被吃掉，`abortSession` 的退款路径也拿不回来。

```
  启动校验 ──▶ 读核心目标槽找 AmpCoreItem（不消耗）
            ──▶ effect = seii:core_N 编码目标核阶
            ──▶ RitualRecipeMatcher 只匹配催化剂（match: MAX，多余的留台）
  蓄满     ──▶ apply(takes) 扣催化剂；核一个组件都没动
            ──▶ SeiiNumbers.roll(...) 结果暂存进 SeiiSession.pending
  REVIEW   ──▶ 采纳 = 写回核组件 + rune_rerolls 减半；保留 = 丢弃暂存 + rune_rerolls +1
  abort    ──▶ refundCached + 清 session；核原样
```

**为什么不用 `matchMax`**：`matchMax` 按 `recipe.totalCount()`（Σcount）取最大者。核不在 ingredients 后，1/2/3 阶核的配方在台面上可能同时命中（催化剂量不同），Σcount 排序会选错。改为在行为侧按"目标槽里的核阶"直接选目标配方，`effect` 字符串承载核阶（照 `YaoyorozuGraceService.effectOf` 的 `grace:refine_N` 范式）。

**核必须是裸核**：核在武器 `slot3` 里时目标槽是空的（仪式看不见它）。玩家须先用弹幕合成台取核。指导书需写明。

**采纳时的服务端复验**（神恩没有这个竞态，因为神恩改的是玩家自己）：核可能已被另一名玩家从目标槽取走或换掉。采纳必须复验核阶不变 + 当前 `rune_affixes` 仍等于暂存前快照，不匹配则拒绝写入并提示。

### D4. 暂存-提交：赌注在 REVIEW 落定前不暴露

`GraceSession.stageRefine` 在 `applyNow`（PERFORM **之前**）就 roll 好了，roll 挂 5 秒再问要不要。进阶无所谓，洗练是赌注（300M 沉没成本），所以：
- 暂存结果只放 BE 的 `SeiiSession.pending`，**核组件零写入**
- abort 路径（结构失效 / 配方丢失 / initiator 掉线 / 玩家主动取消）一律不动核
- 采纳时才 `core.setSeiiTargetStack(核副本.set(RUNE_AFFIXES, new))`

**采纳时的服务端复验**（神恩没有这个竞态，因为神恩改的是玩家自己）：核可能已被另一名玩家从目标槽取走，或已被换成另一枚。采纳必须复验核阶不变 + 当前 `rune_affixes` 仍等于暂存前快照，不匹配则拒绝写入并提示。

### D5. 19 键池：一个效果一个 id，砍掉 `crit_*_pct`

```
玩家属性 15 键（走 PlayerAttributes contribution，语义是"武器给玩家的加成"）
  spirit_regen_rate  spirit_power  move_speed_bonus  graze_chance
  danmaku_reduce     tenacity      crit_chance       crit_damage
  spell_amp          spell_cdr     buff_extend       spirit_leech_rate
  jump               phys_resist   melee_damage

武器专有 4 键（走 WeaponFiring 乘积，玩家属性里没有对应物）
  damage_pct   attack_rate_pct   spirit_cost_pct   range_pct（新增）
```

**排除**：`max_spirit`（神恩台账，永久池）、`health_bonus`（永久属性）、`danmaku_resist`（已退役键，config 注明 intentionally absent）。

**为什么砍 `crit_chance_pct` / `crit_damage_pct`**：`PlayerAttributes.rollCrit`（第 155~164 行）已经是 `finalValue(CRIT_CHANCE) + extraChance` / `1 + finalValue(CRIT_DAMAGE) + extraDamage` —— **两者本来就加在同一个加区、受同一个 cap**，没有乘区冲突，纯设计重复。但量级差 4~6 倍：

| id | T3 核实际值 |
|---|---|
| `crit_chance`（玩家侧，8% × 5阶标准 35%） | +2.8 点 |
| `crit_chance_pct`（旧武器表 6~12%） | +6~12 点 |

同时进池会让玩家抽到两条同义词条、tooltip 撞名。留玩家侧还额外获得：暴击进核心界面的属性面板、与其他 13 键遵守同一条采样规则、`WeaponFiring` 的 `rollCrit` 两参数退化为单参数。

**"池大小"与"加区/乘区"是正交的两件事**（这是本 change 最容易混淆的一点）：

```
  问题 A：哪些词条会出现          问题 B：出现的词条怎么进公式
  （池：19 个 id × 权重             （加区/乘区 —— 完全复用既有管线，
   × 不重复 × 采样带）               零结构改动）
        ↓                                  ↓
   List<RuneAffix>                  finalDamage = 灵力强度 × 弹幕核乘区
                                    × 武器等级乘区 × (1+伤害增幅) × 暴击倍率
                                    灵力强度 = 台账 + Σcontribution(永久+临时)
```

### D6. 两类数值来源，玩家属性键必须 per-key 表

"标准值的百分比"这条规则在字面意义上不能统一套用：

```
按 1阶属性 × 1~3% 算（读法①：百分比的百分比）
  health_bonus 10 × 1~3% = +0.1~0.3 HP        ← 舍入误差
  crit_chance 0.06 × 1~3% = +0.06%~0.18%     ← 舍入误差
按 +1~+3 个百分点算（读法②）
  health_bonus +1~3 vs 10  = 相对 +10~30%     ✅
  move_speed   +1~3 vs 5   = 相对 +20~60%     ❌ 过强
  max_spirit   1000×1~3%  = +10~30 vs 1000   ❌ 几乎无用
```

所以：玩家属性键 = `per-key 采样带% × 对应玩家阶标准属性值`，采样带从 config 读、缺省 1~3% / 2~5% / 5~8%；武器专有键 = per-tier 直接百分比表。

这条规则的**优点**是自动随玩家投资缩放（永远"占你已有属性的百分之几"），所以不会随阶相对变弱。**代价**是撞 cap：

| 键 | 5 阶标准 | cap | 余量 | T3 词条需求 | 占比 |
|---|---|---|---|---|---|
| `tenacity` | 0.70 | 0.75 | 0.05 | +0.035~0.056 | **70~112%（顶带全被截）** |
| `spell_cdr` | 0.35 | 0.40 | 0.05 | +0.018~0.028 | 35~56% |
| `buff_extend` | 0.90 | 1.00 | 0.10 | +0.045~0.072 | 45~72% |
| `crit_chance` | 0.35 | 0.50 | 0.15 | +0.018~0.028 | 12~19% |

`tenacity` 在 5 阶会被 cap 吃掉顶带。**因此 REVIEW 面板必须显示"结算后生效值"而非 roll 原始值**（`PlayerAttributes.breakdown(key).capped` 已有 `capped` 标志可用），否则玩家看到 +5.6% 装上后只有 +5.0% 会认为是 bug。

### D7. `danmaku_reduce` 换成"减伤%"语义

P 是无量纲指数（受伤 = 原伤 × 2^-P）。直接按"P 的百分比"给带子在两端都崩：

```
按 8.6 × 5~8% = +0.43~0.69  →  受伤 ×0.742~0.624   （一条词条 26~38% 减伤，太肥）
按固定 +0.1~0.3 点         →  1阶 P=1.0 时受伤 ×0.933~0.812（低阶过强）
                             5阶 P=8.6 时受伤 ×0.937~0.879（高阶过弱）
```

改为**先定目标减伤 r，再反解 ΔP = -log2(1-r)**：

| 核阶 | r | ΔP |
|---|---|---|
| T1 | 1~3% | 0.0144~0.0457 |
| T2 | 2~5% | 0.0288~0.0740 |
| T3 | 5~8% | 0.0740~0.1203 |

三阶体感一致（就是"减伤 5~8%"），实现是一条 `Math.log`，与 `AttributeMath.mitigate` 同一套数学。该键因此需要**第三种 band 类型**（绝对点数而非百分比），config 行要能表达。

### D8. 软保底计数器挂在核上，不挂在 BE 会话里

新增 `ModDataComponents.RUNE_REROLLS : int`（不动 `rune_affixes` 结构）。

```
保留（拒绝）→ rune_rerolls += 1
采纳         → rune_rerolls = rune_rerolls / 2   （向下取整，不清零）

roll 时区间向好的一侧平移：
  t    = min(rune_rerolls, PITY_CAP) / PITY_CAP          PITY_CAP = 10
  lo'  = lo + (hi - lo) × t × 0.5
  hi'  = lo + (hi - lo) × (1 + t × 0.5)
  value = uniform(lo', hi')
```

t=0 正常区间；t=0.5（洗练度 5）区间上移半个带宽；t≥CAP（洗练度 10）下界抬到原上界之上。

**为什么必须软保底**：保留虽然不额外扣费，但每次启动都全价扣（材料 + 灵力都沉没），理性玩家会一直洗到"比现在好"为止。没有保底 = 期望收敛但方差无界的老虎机，运气差的玩家能连续 20 次全拒绝。

**为什么挂核不挂 BE**：跟物走（换仪式、拆结构、换地图都不丢）；BE 会话态是易失的且换仪式就清零；可在核的 tooltip 上写"洗练度 7/10"给玩家可感知的奔头。

**为什么不清零**：`/2` 而非 `=0`，让"这枚核被洗了很多次"成为一条可见的长期履历，同时避免玩家停在 9 反复横跳。

**备选**：第 N 次硬保底（必出一条上界值）。否决——需要仲裁"哪一条"，且玩家会故意保留来刷保底，反而加速消耗。软保底不破坏"每次都是全新 roll"的赌博感。

### D9. `range_pct` 语义切割 + 指数衰减

```
有效距离 = 弹道能打多远                     ← range_pct 只乘这里
  激光核    laserMaxLength        × (1+r)^0.75
  投射物    lifetimeSeconds       × (1+r)^0.75   → 射程 = speed × lifetime
  符卡核    talisman 弹道寿命       × (1+r)^0.75
──────────────────────────────────────────────
索敌能不能锁到人 = 锁定能力，不是距离         ← talismanPickRange 不接入
```

**语义切割**：`WEAPON_TALISMAN_PICK_RANGE`（`DanmakuTargetPicker.pick`，40 格）塞进"有效距离"是纯语义污染 —— 玩家看到"+有效距离"却发现自动锁定范围也变强了。切掉后 `range_pct` 对符卡核只吃到一项（三项里的一项），收益比其他核低一档，但 tooltip 如实显示，不撒谎。

**指数衰减**：`^0.75` 一个浮点解决线性堆叠（r=12% → +9% 而非 +12%），也让"有效射程"在多次叠加时不至于爆掉。弹幕核 tooltip 会显示"有效射程 = 弹速 × 存活时间"，让这个乘区有文本锚点。

**备选**：给符卡核的索敌半径打 ×0.5 折扣。否决——折扣保留了语义错误，只是掩盖了它。

### D10. 核给的玩家属性走 `temp` 层的独立来源命名空间，绝不碰台账

```
  玩家主手武器 slot3 的 AmpCoreItem
        │  affixId ∈ AttributeKey.id() 的那些
        ▼
  PlayerAttributes.setTemp(player, key, "seii_rune_<槽位>", value)
        ▼
  PlayerAttributesData.totalContribution = Σpermanent + Σtemp      ← 纯加法
        ▼
  AttributeMath.finalFrom(base, Σcontrib, cap) = min(base + Σcontrib, cap)
```

**用户提出的关键疑问**："加一个 `equipped` 层会不会变成第二个独立乘区？将来想做 `base*(1+乘区1)*(1+乘区2)` 容易爆。"

**核实结论：不会，因为属性容器里根本没有乘区。** `totalContribution` 是平的和，`finalFrom` 是 `min(base + Σ, cap)`，全链路零乘法。乘区只存在于消费点（`WeaponFiring` 的伤害乘积、`rollCrit` 的 `1+critDamage`、`mitigate` 的 `2^-P`）。加多少个加区 source 都不会产生新乘区。

但顺着这个疑问发现了**比新增层更小的方案**：

```
temp 层的语义 = 「不入存档 · 由外部瞬态派生 · 整体丢弃即恢复」
装备的语义    = 「不入存档 · 由外部瞬态派生 · 整体丢弃即恢复」    ← 生命周期完全一致
```

两者是同一种东西，只是 `sourceId` 不同。所以**不新增 `equipped` 层**，改为把白名单校验从"层"移到"来源命名空间"：

```
现在：  setTemp → if (key == null) reject; if (!key.isTransformRewritable()) reject
                  ↑ 这是「层可写哪些键」的约束

改为：  setTemp → if (key == null) reject
                  if (isTransformSource(sourceId) && !key.isTransformRewritable()) reject
                  ↑ 这是「降神变身可改写哪些键」的约束，与层无关
```

`transformRewritable` 描述的是**降神变身**的改写域，把它当成"整个 temp 层可写哪些键"是把两件事混为一谈。

**收益**：`PlayerAttributesData` 不动（仍两层）、`totalContribution` 不动、`CODEC` 不动、`clearTemp` 不动。11 个白名单外的键（`graze_chance` / `danmaku_reduce` / `tenacity` / `crit_chance` / `crit_damage` / `spell_cdr` / `buff_extend` / `spirit_leech_rate` / `jump` / `phys_resist` / `melee_damage`）自然可写。清理装备贡献时按 `sourceId` 前缀遍历移除，不影响变身来源。

**"武器的加成也是给玩家的"** 这条设计立场在实现上的落点：核的属性和玩家其它来源的属性进同一个加区、同一个面板、同一套 cap，不开特例。

**`MAX_SPIRIT` / `SPIRIT_POWER` 的台账红线**（用户曾质疑"为什么灵力伤害能被绕开进阶"，核实后澄清：它不会 —— `WeaponFiring` 第 34 行的 `GraceService.requireGrace` 已把凡人拦死，阶还是得升）。真正的风险只在实现路径：两键的 `baseFn` 直接读 `ModAttachments` 台账，`GraceNumbers` 也把它们单列。**核给的 `spirit_power` MUST 走 `temp` 层的 `seii_rune_*` 来源，MUST NOT 改写台账**，否则会出现"戴上武器 +3000、摘下武器 -3000"的假永久属性，并与 `GraceService.applyRefine` 的整组替换互相覆盖。

**备选 A**：新增 `equipped` 第三层。已否决 —— 语义上更干净，但要动容器结构、`totalContribution`、`CODEC`、`clearTemp` 与 `player-attribute-suite` 的层模型 spec，收益仅是命名好看。用户的"避免第二个乘区"顾虑促成了这次重新评估。

### D11. REVIEW 面板留在核心 GUI，不新开界面

用户裁决：核的洗练预览放在仪式核心界面内。理由是避免"新 payload + 新 Screen + 新决策包 + 菜单替换触发 `PlayerContainerEvent.Close`"这一串连锁（后者会直接引爆 D12 的 bug）。

代价是 33 行内容挤 68px 视口。用三个廉价改动根治，不新增任何机制：

```
  ① 决策按钮置顶到信息行首位        采纳/保留 永远在视口内，不需滚动
  ② REVIEW 期隐藏玩家属性面板       appendAttributePanel 仅在 phase != REVIEW 时追加
                                    行数 33 → 17
  ③ 视口外可交互行自动滚动对齐      payload 里存在 interactive() 行落在视口外时
                                    把 infoScroll 对齐到它（通用行为，非神恩专属）
  ④ 滚动条滑块                     内容超视口时在信息盒右缘渲染 3px 滑块
```

不改 `actionButtons` 布局（只有 3 槽 y=88/110/132，神恩已占 2 个，再加两个会撞 `INVENTORY_TOP_Y=158` 的背包区）。

**顺带修一个既有绘制 bug**：`InfoLine` 的三态标记（✓/✗）原本固定绘制在 `STATE_X = 124`，而右侧按钮列从 `TOGGLE_BUTTON_X = 120` 起 —— 标记实际画进了按钮列里。原 spec 写的是"右栏 ✓✗ 状态标记按现行定位豁免于该钳制"，等于把 bug 写进了规范。星移的阶梯行（`ladder_row` 用 `state` 标锁/解锁）第一次让带标记的行与按钮列重叠，问题暴露。改为右对齐到信息盒内壁（`INFO_BOX_RIGHT - 1`），并让 `maxTextWidth` 为标记预留 8px。

### D12. 会话态持有者：第三份复制，不抽 `ReviewSession`

`RitualCoreBlockEntity` 已有两份同构的会话态持有者：`craft`（造化 `CraftSession`）与 `grace`（神恩 `GraceSession`）。星移是第三份。

**照范式新增 `SeiiSession`**（约 120 行）而不是抽公共父类：既有代码的既定做法就是"每个会话型仪式一份"（`ritual-code-dev` 的参考实现地图明确列 `ZaohuaCraftingBehavior` / `YaoyorozuGraceBehavior` 两个独立先例），抽取会迫使两个**已在服役**的仪式在无关 feature 里改动，收益（省 ~80 行）不抵回归风险。共享的只有 `ritual_recipes` 的 `effect` 字符串范式。

代价：REVIEW 写盘与待决规则（D14）要在两处各做一次。可接受。

### D13. 弹幕核 tooltip 分型渲染

`BulletCoreItem.appendHoverText` 现在只有一行"灵启 N 阶"。按 `FirePattern` 的三个构造分型渲染，数值全部走 `CoreStats` / `FirePattern` 的 Supplier，**DPS 因子复用现成的 `CoreMath.bulletDpsFactor` / `laserDpsFactor`**（同一批纯函数，已被预算校验覆盖）。

```
弹幕核·球
  灵启 1 阶
  ─────────
  伤害倍率 ×1.0        攻击间隔 8 tick (2.5/s)
  单发耗灵 10
  ─────────
  弹数 3   散布 15.0°   弹速 40.0
  存活 2.0s  →  有效射程 80 格        ← range_pct 乘的就是这个
  ─────────
  有效 DPS ×2.50
```

激光核换 `激光长度 / 半径 / 启动延迟 / 持续时间(脉冲数)`，符卡核换 `索敌距离 / 锁定灵敏 / 弹速`。

**做不了的事**：tooltip 拿不到父武器，拿不到 `slot3` 的 `range_pct`，所以弹幕核的"有效射程"显示的是**核自身基准值**而非装配后值。装配后总览留给弹幕合成台（Non-Goal）。

### D14. 待决状态的裁决规则

| 维度 | 裁决 |
|---|---|
| 存续 | **永久**，无超时。`REVIEW` 态无限期保留待决预览 |
| 归属 | **绑定发起玩家**（`initiator`）。决策权不转移给任何其他人 |
| 他人干扰 | 他人打开/关闭该核心界面 MUST NOT 影响该待决 |
| 他人可用性 | 他人 SHALL 能正常使用该核心 —— 包括启动自己的洗练（按既有语义，新执行作废旧预览） |
| 收场 | 绑定的玩家做出决策、或任何人启动新一次洗练（作废旧预览）、或结构失效 |

**为什么不设超时**：待决态 `enabled` 已被置 false，不消耗任何世界资源，唯一成本是 BE 的 NBT 里存一份暂存词条。且"任何人都能启动新洗练把它顶掉"本身就是一条可靠的收场路径，不是死锁 —— 所以**原设计的"initiator 掉线时转移决策权"整条撤销**，神恩的"启动玩家掉线死锁"缺陷因此自动消解。

**为什么绑定玩家**：洗练的 300M 沉没成本与"我的核"绑定，替他做决定（无论接受还是保留）都是越权。永久 + 绑定是最诚实的语义：你不回来决定，这份 roll 就静静等着，不会被系统替你消费掉。

## Risks / Trade-offs

**[新词条池的 DPS 预算需要重算，但不必然超支]** → 砍掉 `crit_*_pct` 后反而落回旧 spec 区间。T3 核最坏组合（`spirit_power` 8% / `damage_pct` 20% / `attack_rate_pct` 15% / `crit_chance` +2.8 / `crit_damage` +12，对 5 阶满属性玩家）= `1.08 × 1.20 × 1.176 × 1.057 = +61%`；各取中点 = `+48%`。旧 `rune-affix-pool` spec 的预算是"最大 ≤ +80%、典型 +50~60%"，**两者都满足**。→ 仍需重写 spec 的 per-affix 数值表（crit 两行彻底变了、池从 5 键到 19 键），但不是 BREAKING 的预算突破。

**`tenacity` / `spell_cdr` / `buff_extend` 撞 cap，玩家看到的 roll 值与生效值不符** → REVIEW 面板显示 `PlayerAttributes.breakdown(key).capped` 判定后的**生效**增量，capped 行加标记。

**`setTemp` 白名单不覆盖 11/15 池内键** → D10 已裁决：把白名单校验从"层"移到"来源命名空间"，装备来源（`seii_rune_*`）不受 `isTransformRewritable` 约束。容器结构、`totalContribution`、`CODEC` 均不动。需回归确认降神变身的改写域未因此扩大（`isTransformSource` 判定必须让现有变身 sourceId 全部走白名单分支）。

**300M 的单次花费需要新的灵力供给规模** → 5 阶玩家满池 10,000,000，300M = 30 个满池。缓存/inrate 阶梯（1.04e9 / 2e8 per s）已保证 1.5s 灌满，前提是玩家有对应规模的供灵（万象共鸣路由 + 高阶产能）。上线后需观察 5 阶玩家的灵力收入是否支撑得起，不足时先调 inrate 阶梯（会破坏 D1 的 1.5s 不变式，属可接受代价）。

**`RuneSummary` 从 5 字段定长 record 改成注册表驱动是破坏性改动** → 唯一消费点是 `WeaponFiring.tryFire`（已确认全仓仅此一处），改动面可控。

**核的 `rune_rerolls` 允许被复制/合成刷保底** → 与 `rune_affixes` 一样可被复制。这是词条类物品的既有性质，不新增风险面。

**`danmaku_resist` 是死键但仍在 `AttributeKey` 枚举里** → 本次只在池构建时排除，不动枚举（避免波及其他 spec）。

**待决永久化会让台面核长期携带过期暂存** → 无害（NBT 一份小数据），且任何人都能启动新洗练顶掉它。若玩家把该核直接装进武器，暂存数据不影响词条本身（暂存只在 BE 会话里，不写核）。

**演出的美术效果无法由 agent 验证** → FX 编排可一次写对逻辑，但"好不好看"必须由用户进游戏实机看（`RitualCoreScreen` 之外的 client BER 渲染，agent 不启动服务器）。分阶递进的三档强度需实机调。

## Migration Plan

1. 先落 config（19 键 per-tier 表 + 阶梯基项 + 三种 band 类型），旧 `runeAffixPool` / `runeAffixCount` 保留读取一个发布周期后删除
2. 落 `SeiiNumbers`（纯静态、可单测）+ 单测，再接 `RuneGenerator`
3. 落 `setTemp` 的来源命名空间白名单改造 + 装备注入（`seii_rune_*` 来源）
4. 落 `SeiiSession` / `SeiiService` / `SeiiBehavior` + 注册 + `getCapacity` 分支
5. 落 `ritual_recipes/seii_circle.json` + lang + `/gs_debug seii` 探针
6. 落 `range_pct` 三消费点 + 弹幕核 tooltip
7. 落神恩三处修复（REVIEW 写盘、关界面不void、面板可读）
8. 落 `RitualCoreScreen` 自动对齐/滚动条
9. 落分阶 FX 编排（client BER）
10. 指导书条目（`tools/gen_ritual_multiblock.py` 单仪式模式）

回滚：配置层可独立回滚（阶梯与表都是 config）。行为层 `RitualBehaviors.SEII` 注销即让星退回"空壳"（可成型、零产出），无需拆除结构。

## Open Questions

- 祭品配方具体选材（3 条配方，按核阶分级：`core_1` ≤3 催化剂 / `core_2` ≤11 / `core_3` ≤19）。倾向 `amethyst_shard`（星盘刻度）、`spyglass`（观测镜）、`lodestone`（天极定位）、`copper_ingot`（铜环）、`echo_shard` / `ender_pearl`（挪星），或本模组的 `star_silver` / `refined_cinnabar` / `sukima_fragment` 做后期阶
- 星移 `PERFORM` 分阶 FX 的具体强度与时序（1 阶地面星盘微光 / 3 阶门楣灯 + 铜环转 / 5 阶天极星点亮 + 光柱冲天），逻辑可一次写对，美术需实机评审
