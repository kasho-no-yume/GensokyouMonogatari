## Context

三个事实构成本次重标的全部依据：

**① 击杀所需发数是一个尺度不变的常数。** 玩家伤害与 BOSS 血量同源于 `spirit_power`：

```
单发伤害   = spirit_power × coreBaseMult × weaponLevelMult × critAvg
参考 DPS   = spirit_power × weaponLevelMult × critAvg × refShotsPerSecond(2.5)
BOSS HP    = 参考 DPS × bossSeconds(160)
每触发倍率 = coreBaseMult × 弹数
击杀发数   = BOSS HP / 单发伤害 = 160 × 2.5 / (coreBaseMult × 弹数) = 400 / (coreBaseMult × 弹数)
```

| 核 | coreBaseMult × 弹数 | 击杀发数 | 满池(1000)可负担 @ 新成本 | 判定 |
|---|---|---|---|---|
| 球 | 1.0 × 1 = 1.0 | 400 | 500（成本 2） | ✓ 余量 25% |
| 飞刀 | 1.4 × 1 = 1.4 | 285.7 | 333（成本 3） | ✓ |
| 散弹 | 0.45 × 5 = 2.25 | 177.8 | 250（成本 4） | ✓ |

**分母是「每触发伤害倍率」（`coreBaseMult × 弹数`），不是 docs §3 的「有效 DPS 因子」。** 后者 `= coreBaseMult × 弹数 × (20/attackRate)` 把射速又乘了进去，对球核算出 `160 × 2.5 / 2.5 = 160`——与本节自己写的 400 差一个 `refShotsPerSecond`。推导 `BOSS_HP / 单发伤害` 时能约掉的是 `spirit_power`/`weaponLevelMult`/`critAvg`，剩下 `refShotsPerSecond / (coreBaseMult × 弹数)`。

**任何对 `spirit_power` 的均匀缩放都同时缩小分子分母，发数不变。** 这是"改伤害不影响弹耗结论"的原因。

**② 回灵被设计成可忽略，预算单位是"一根满管"。** config 注释写明 `spirit_regen_rate` 累计占池 0.03%~0.3%/s。阶 1 为 1.2/秒（池的 0.12%/秒），160 秒只回 192。`player-spirit-attributes` spec 也把"挂机回灵不足续航"写成正式要求。所以"这场战斗必须能用一根满管打完"是既有设计意图，不是本变更发明的约束。

**③ 弹耗与伤害不可独立调节。** 二者乘积受 `满池可负担发数 ≥ 击杀所需发数` 约束：

```
1000 / cost ≥ 400 / (coreBaseMult × 弹数)   ⟹   cost ≤ 2.5 × coreBaseMult × 弹数
```

球核 `coreBaseMult=1.0`、弹数 1 ⇒ 上界 `cost ≤ 2.5`，而现值 **10，超标 4 倍**。要让 `cost=10` 反过来合法，只有 `coreBaseMult ≥ 4.0`（伤害翻 4 倍）这一条路——**恰好与本变更"阶 1 伤害应当低于石剑"的目标相反**。故"降弹耗"与"降伤害"在原数值下互斥，先降 `spirit_power` 打开这个窗口，是让两项需求同时成立的前提。

## Goals / Non-Goals

**Goals:**
- 阶 1 单发伤害落到约 1，使原版武器在阶 1 仍有竞争力
- 阶 1 满管可负担发数 ≥ 400，且留有可接受余量
- 严格保持各阶 DPS 比例不变（战斗耗时、压迫感曲线不动）
- 严格保持 `spirit_power` 的全仓库单一消费者结构不被破坏

**Non-Goals:**
- 不改任何核的 `coreBaseMult`、`attackRateTicks`、弹速、射程——它们受 DPS 因子带 `[1.5, 2.5]` 约束，本变更一个都不需要动
- 不改 2 阶及以上的核成本（2 阶回灵 10.8/秒 已超过降耗后球核的 5/秒 净耗，不存在问题）
- 不改 `refShotsPerSecond`、不动 `bossSeconds`、不改 `VANILLA_MAX_HEALTH` 截断逻辑
- 不动仪式耗灵、符卡花费、灵力池量级、回灵曲线
- **不修球核弹速硬编码、`range_pct` 词条在球/飞刀上失效这两个已知缺陷**（另开变更；本变更不依赖它们）

## Decisions

### D1: 用均匀缩放而非重新设计阶梯

**选**：`spirit_power` 增量列整体 ÷6（`6,54,480,4100,34500` → `1,9,80,683,5750`）。

**理由**：均匀缩放数学上保持全部比值不变。累计值 `1, 10, 90, 773, 6523` 的阶间比例 ×10.0 / ×9.0 / ×8.6 / ×8.4 与原 `6, 60, 540, 4640, 39140` 一致（后两阶为舍入，见代价栏）。若重新设计阶梯以凑整，会同时动到 `max_spirit`、`danmaku_reduce` 等同表的 20 行，风险面扩大一个数量级。

**代价**：4 阶的 683 和 5 阶的 5750 是不整除舍入值（4100/6、34500/6）。接受——它们不面向玩家显示，且舍入误差 0.05%。**因此"阶间倍率逐项相等"只在 1 位小数上成立**：tier2 ×10、tier3 ×9 精确相等，tier4 为 8.5926→8.5889（Δ −0.0037）、tier5 为 8.4353→8.4386（Δ +0.0032）。回归测试 MUST 断言"1 位小数相等"或"相对误差 < 0.1%"，**MUST NOT** 写严格相等断言。

**替代方案（已否决）**：
- *重新设计为整齐的 1/9/81/729/6561*：更整，但阶间比例变成 ×9/×9/×9/×9，与现有 ×10.0/×9.0/×8.6/×8.4 不同，会改变各阶战斗耗时的相对关系。
- *只降阶 1、保持高阶*：会让阶 1→2 的落差从 ×10 变成 ×60，直接摧毁进阶仪式（`yaoyorozu-grace-ritual`）的存在感。

### D2: 弹耗 1 阶三核同档下调（球 ÷5 / 飞刀 ÷4.67 / 散弹 ÷5.5），而非只改球核

**选**：球 10→2、飞刀 14→3、散弹 22→4。

**理由**：三核 `requiredTier` 同为 1，是同一个"初始核"档。只降球核会让它以 1/5 的灵力效率碾压同档另两个核，违反 `danmaku-weapon` 现行"各核伤害/灵力效率量级一致"的要求。三核同档下调，把偏移控制在同一量级内。

**核间比例不是精确保持，而是控制在 ±10% 内**——`spiritCost` 是 `ModConfigSpec.IntValue`，2/3/4 是凑整而非 10/14/22 的 ÷5：

| 核 | 成本 | 实际除数 | 伤害/灵力 变更前 | 变更后 | 偏移 |
|---|---|---|---|---|---|
| 球 | 10→2 | ÷5 | ×1 | ×1 | 0% |
| 飞刀 | 14→3 | ÷4.667 | ×1 | ×0.9333 | −6.7% |
| 散弹 | 22→4 | ÷5.5 | ×1.0227 | ×1.125 | +10% |

满足 `danmaku-weapon` 的"**量级**一致"（而非精确相等），且无单核碾压同档。要精确保持需 2/2.8/4.4，但 2.8 在 `IntValue` 下不可表达，且判据本身已全过（见下），不值得为此改 config 类型。

**三核都满足击杀发数不变量，无需任何核豁免**（按 Context ① 的每触发倍率口径）：

- 球 500 ≥ 400，余量 25%（计入 160 秒回灵 192 后合计余量 49%）
- 飞刀 333 ≥ 285.7
- 散弹 250 ≥ 177.8

散弹仍定位于近距离/群怪：射程 12.8 格（`projectileSpeed 0.8 × lifetimeSeconds 0.8 × 20`）对 `BigFairyEntity.moveMin()=10` 的飞行 BOSS **够得着但只剩 2.8 格余量**——这是定位描述，不是对数值判据的豁免。

**2 阶及以上不动**：球核降耗后净耗 5/秒，2 阶回灵 10.8/秒 已覆盖，2 阶起为无限续航。

### D3: 作废"每管池 40~100 次触发"，改用击杀发数不变量

**理由**：`danmaku-weapon` 现行"每管池约 40~100 次触发"是一个凭手感定的经验值，没有推导支撑，且与"BOSS 血量 = 参考 DPS × 160 秒"这个真正的预算公式脱节。降耗后 1 阶球核是 500 次触发，落在原规范外 5 倍。

**替换为**：`满池可负担发数 ≥ 同阶 BOSS 击杀所需发数`，即 `max_spirit / spiritCost ≥ bossSeconds × refShotsPerSecond / (coreBaseMult × 弹数)`。该式把"血量预算"和"灵力预算"绑到同一个推导上，可机械校验。**分母是每触发伤害倍率，不是 docs §3 的有效 DPS 因子**（后者含射速，会把球核算成 160 而非 400，见 Context ①）。

**替代方案（已否决）**：*把 40~100 上限改成 500*。数字变成"能用就行的橡皮规范"，失去约束力。

### D4: 迁移方案 = 改 config key 名（`graceTierTable` → `graceTierTableV2`）

**问题**：`GraceNumbers.effectiveRows()` 把 config 行拼在内置默认行**之前**，`entry()` 线性扫描取首个匹配。既有 config 已含 `1,spirit_power,6,0.2`，NeoForge 不会把新默认值写进已有配置 ⇒ **只改默认表对老安装静默无效**（提交了、测试过了、玩家那边什么都没变）。

**选**：把 config 键 `graceTierTable` 改名为 `graceTierTableV2`。既有配置文件里不存在 `V2` 这个键，NeoForge 会补上新默认值 ⇒ 生效。

**理由**：

- **表是唯一事实源**。这是本决策的首要标准——表里写 `1, 9, 80, 683, 5750`，游戏里就是这五个数。任何读 `graceTierTableV2` 推算数值的人（后续维护者、数值包作者）都能算对。
- **无第二个事实源需要保留**。曾评估过"新增读取时缩放因子 `spiritPowerScale`"，已否决：它会让 `graceTierTable` 里的数字变成假值——想调阶 1 到 8 就得改表里的 `6`→`8`，但实际生效是 `8 × 0.1667 = 1.33`，**配置写的数不再等于游戏里的数**，一个量有了两个源。
- **存量表是化石，应当丢弃**。实测本地 `run/config/gensokyou-common.toml` 创建于 `7eb0db1`（重写 grace 默认值那次提交）**之前**，表内容是更早一代的平衡（`max_spirit 200` / `spirit_power 8` / `danmaku_reduce 0.10` / roll 0.15 / 含已退役的 `danmaku_resist`）。它不是"手工调校"，是 NeoForge 从不覆写既有值造成的漂移。改名把 grace 表整体丢掉，正是想要的处置——但**只能丢 list 键**，其余标量键见 D7。
- 改动面是一处 key 名 + 一处声明名。

**代价**：玩家对整张表的手工调校会丢失（`max_spirit` / `danmaku_reduce` / 12 项修士赠礼）。当前为零成本。**注意本决策的边界**：它只作用于 `graceTierTable` 一个键。同文件里其他标量键（`spiritCost`、`bossSeconds*`、`critChanceCap` 等）在玩家 config 中同样会保留旧值——NeoForge 只补缺失键，不改合法但过时的值。本变更不动那些键（除 1 阶三核 `spiritCost`，玩家需自行删行，见 Migration Plan）。

**连带修正**：`rune-affix-pool` spec 第 41 行以散文形式点名了 `grace.graceTierTable` 这个键。rename 后该引用失效——这是**文档过时而非行为变更**，故列为任务而非 spec delta（`rune-affix-pool` 的规范性内容"取自 grace 表累加、MUST NOT 硬编码"完全不变）。

**替代方案（已否决）**：
- *读取时乘缩放因子*：见上，配置与实值脱节。
- *启动时按 `(tier, key)` 替换 base*（保住 roll 与其他行）：只在"表确有手工调校、须保留"的前提下才值得它的复杂度。实测存量表是化石而非调校，故不值得。

### D5: 数值仍在校准中，spec 锚点宜标注

用户明确"现在就是校准的过程"。因此本变更写入 spec 的阶 1 锚点（伤害约 1）**应被理解为当前标定值而非永久不变量**。spec 的 requirement 保留该锚点（它至少能挡住"不小心调回 6"这类漂移），但在 design 与 tasks 中记录：后续重标时 MUST 同步更新 spec 锚点与 `player-spirit-attributes` 的累乘表。

### D6: `damageScale()` 是全系统唯一非尺度不变项，如实记为阶 1 弹幕 ×2.5

`AbstractTouhouBoss.hurt()`（`:218-226`）在 `damageScale() > 1` 时做 `amount /= scale`。`damageScale() = effectiveHp ≤ 1024 ? 1.0 : effectiveHp/1024`。

| | 变更前 | 变更后 |
|---|---|---|
| 阶 1 BOSS HP | 2560 | 426 |
| `damageScale()` | 2.5 | 1.0 |
| 大妖精单发弹幕 | `EHP/10 ÷ 2.5` = 2.53 | `EHP/10` = 6.32 |
| 玩家实可挨发数 | 25 | 10 |

**这是本变更唯一一处玩家侧承受力下降。** `referencePlayerEhp()` 只读 `health_bonus`/`danmaku_reduce`/`graze_chance`，不随 `spirit_power` 缩放，故玩家侧无任何补偿。

**结论：数值不动，只把表述改对。** 25 那一档是 HP 溢出原版上限后被 `÷2.5` 意外撑出来的，并非预算意图——`bigFairyBossHits = 10` 才是这套预算本来要写的值。变更后玩家挨弹数回到 10，是**把一个意外的宽松修正回设计值**，而不是"白送的净收益"（初稿写成净收益是读反了：被除 = BOSS 变弱，去掉除法 = BOSS 变强 2.5 倍）。

量级校验：大妖精三张卡 `repeatEvery` 50/45/70 tick，每轮 5/12/9 发，按血量分段 54.4/52.8/52.8 秒算，160 秒共约 **527 发**。挨 10 下即死 = 命中 1.9% 即死；挨 25 下 = 4.7%。全大、全慢、全朝前的教学弹幕下（`BossCards.bigFairy()` 注释原话），10 发是合理余量。

### D7: 本地测试环境删 `run/config/gensokyou-common.toml` 重建，而非手改 3 个 spiritCost

**发现**：该文件创建于 `7eb0db1` 之前，此后代码默认值改过 12 个键，文件从未跟上：

| 键 | 存量 | 代码默认 |
|---|---|---|
| grace T1 `max_spirit` | 200 | 1000 |
| grace T1 `spirit_power` | 8 | 6 |
| grace T1 `spirit_regen_rate` | 2 | 1.2 |
| grace T1 `danmaku_reduce` | 0.10 | 1.0 |
| grace roll 分数 | 0.15 | 0.2 / 0.7 / 0.35 |
| grace 含已退役 `danmaku_resist` | 有 | 无 |
| 球/散弹/飞刀 `spiritCost` | 2 / 8 / 4 | 10 / 22 / 14 |
| `bigFairyBossSeconds` | 120 | 160 |
| `bossSecondsT1..T5` | 120/120/180/240/300 | 160/260/380/520/720 |
| `weaponLevelMult` | [1.0, 1.5, 2.25] | [1.0, 2.0, 4.0] |
| `critChanceCap` / `critDamageCap` | 1.0 / 5.0 | 0.5 / 2.0 |

**两个后果**：

1. 玩家实际经历的失败形态与初稿诊断不同：存量下 `spirit_power 8`、HP = `21.2 × 120` = 2540、击杀需 **300** 发（`bossSeconds` 是 120 不是 160）、覆盖率 220/300 ≈ **73%**——而非初稿按代码默认算出的 400 发 / 30%。结论一致，机制不同。
2. 存量 `danmaku_reduce 0.10`（而非 1.0）意味着护壁指数只有设计值的 1/10，玩家额外多吃伤害；`critChanceCap 1.0` / `critDamageCap 5.0` 让暴击期望与预算公式脱节。

**选**：删文件重建，NeoForge 按当前代码默认值写全部键。

**改名救不了这些键（已实跑验证）**：把 `graceTierTable` 改名为 `graceTierTableV2` 后，NeoForge 日志出现

```
List on key graceTierTableV2 is deemed to need correction, as it is null, not a list, or the wrong size.
```

——**只有 `graceTierTableV2` 被补上**（因为该键在旧文件里完全不存在）。其余 11 个键在旧文件里**存在且类型合法**，NeoForge 只修正"缺失或类型不对"的键，**不修正"合法但过时"的值**。实跑后实测仍为化石值：

| 键 | 仅改名 | 删文件重建 |
|---|---|---|
| `graceTierTableV2` | ✅ 1 / 9 / 80 / 683 / 5750 | ✅ 同 |
| `bigFairyBossSeconds` | ❌ 120 | ✅ 160 |
| `bossSecondsT1` / `T5` | ❌ 120 / 300 | ✅ 160 / 720 |
| `weaponLevelMult` | ❌ [1.0, 1.5, 2.25] | ✅ [1.0, 2.0, 4.0] |
| `critChanceCap` / `critDamageCap` | ❌ 1.0 / 5.0 | ✅ 0.5 / 2.0 |
| 三核 `spiritCost` | ❌ 2 / 8 / 4 | ✅ 2 / 4 / 3 |

即：**改名只丢 grace 表，其余标量键必须靠删文件**。D4（面向真实玩家的老 config）与 D7（面向本地测试环境）解决不同问题，**两者都需要，不可互相替代**。

**否决**：手改 3 个 `spiritCost`。`spiritCost` 确是 `defineInRange` 标量、改代码默认值不作用于已有 config（与 `bossMoveSpeed` 同形），但存量文件在 12 个键上全面过时，只补 3 个会留下混合态：届时 `bigFairyBossSeconds` 仍是 120，运行时"击杀所需发数"是 300 而非 400，**新的不变量单测会断言一个存量 config 并不满足的性质**。删文件一步到位。

**顺带**：存量 grace 表的 `danmaku_resist` 行当前是**活的**——`AttributeKey.java:40` 仍声明该键且 cap 为 `-1D`（不封顶），`rollTier()` 遍历 `AttributeKey.values()`，故存量配置下每次进阶都会把它 roll 进 contributions。删文件或改名后即消失。

## Risks / Trade-offs

**[老配置静默不生效]** → 必须在实现前定死迁移方案，且在 tasks 里加一条"用旧 config 文件实跑验证阶 1 伤害确实变为约 1"的验证步骤，不能只跑内存单测。

**[`DanmakuWeaponBalanceTest.tierOneSphereDpsLandsBetweenDiamondAndSharpnessFive` 会红]** → 它断言阶 1 DPS ∈ `11.2~17.6`，是"钻石剑级"这个已被本变更否决的旧目标。改测试不是"让测试过"，是**把设计意图换成新锚点（阶 1 伤害约 1、低于石剑）**，须在测试注释里写明为何改。

**[`VANILLA_MAX_HEALTH` 截断行为改变 → 阶 1 弹幕伤害 ×2.5]** → 阶 1 BOSS HP 从 2560 降到 426，低于 1024 上限，`AbstractTouhouBoss.damageScale()` 从 2.5 回到 1.0，`hurt()` 不再 `amount /= 2.5`。**这是玩家侧承受力下降，不是收益**（初稿读反了）：大妖精单发 2.53 → 6.32，玩家实可挨 25 → 10 发。EHP 不随 `spirit_power` 缩放，故无补偿。之所以接受，是因为 25 那一档来自 HP 溢出原版上限后的意外除法，`bigFairyBossHits = 10` 才是预算意图。详见 D6。**若日后阶 1 BOSS 血量被调回 1024 以上会重新触发，属预期行为。**

**[`新不变量单测可能断言存量 config 不满足的性质`]** → D7 已识别：存量 config 的 `bigFairyBossSeconds` 是 120 而非 160，运行时击杀所需发数是 300 而非 400。单测按代码默认写 400，若本地 config 未重建就会与实跑行为脱节。处置见 task 1.5。

**[玩家可见数字全线缩小 6 倍，可能造成旧 Wiki/教程失效]** → 属正常重标后果，release note 中显式说明。

**[`docs/weapon-design-guidelines.md` 与实现漂移]** → 该文档 §2 的校验表、§3 的核表都是绝对数值。改动 MUST 同步更新，否则下一位改数值的人会按旧表推导。列入 tasks。

**[散弹 ÷5 后 250 次触发 < 球核口径的 400]** → **已消解，非风险。** 初稿拿 400 当所有核的统一门槛才需要豁免；按 Context ① 的每触发倍率口径，散弹只需 177.8 发（0.45×5=2.25 倍率），250 ≥ 177.8 天然通过。spec 中的射程豁免条款一并删除。

## Migration Plan

无存档格式变更、无新方块/物品/数据包。

**三个独立的存量。** 初稿只列出两个、且断言"存档一律不迁移"——**该断言与代码不符**：`GraceService.migrateIfNeeded()`（`GraceService.java:140-165`）已实现旧表存档的自动重 roll。

```java
private static final float LEGACY_MAX_SPIRIT_T1_THRESHOLD = 500F;
boolean legacyScale = data.hasLedgerTier(1) && data.ledgerMaxGain(1) < 500F;
if (!emptyLedger && !legacyScale) { return; }   // 已是新表
// 否则按当前表逐阶重 roll 覆盖 —— 顺带清掉已退役的 danmaku_resist 贡献
```

`AttributeKey.SPIRIT_POWER` 确实直读存档字段（`p -> ModAttachments.get(p).spiritDamage()`），与 config 无关——但**重 roll 会把 ledger 换成当前表的 roll 结果**，所以阈值之下的档会自动吃到新表。分三族：

| 族 | 判别（tier-1 `ledgerMaxGain`） | 结果 |
|---|---|---|
| A. 旧表档 | < 500（旧表 1 阶 `max_spirit` 基准 200、roll 0.15 ⇒ 170~230） | ✅ **自动重 roll**，且重 roll 读 config ⇒ D4 改名后自动吃到新表。同时清掉已退役 `danmaku_resist` 贡献 |
| B. 已在旧代码默认量级 | 800~1200（旧代码默认 1 阶 `max_spirit` 1000、roll 0.2） | ⚠ **不迁移**，`spiritDamage` 保持 6 ⇒ 武器伤害仍 6.4 而非 1.07。**本变更的已知代价，非缺陷** |
| C. 变更后新进阶 | — | ✅ 拿新表 |

**本地测试档属 A 族**（D7 的存量 config 产出 170~230 的 ledger），所以**不需要删档**——初稿的 task 1.6「删档重来」据此撤销，改为验证自动迁移确实生效。

**不会误触发循环**：变更后 1 阶 `max_spirit` 基准 1000、roll 0.2 ⇒ `ledgerMaxGain(1) ∈ [800,1200]`，仍 ≥ 500，阈值判别保持有效。

若将来 B 族也要迁移，检测依据是旧值集合 `6, 60, 540, 4640, 39140`（`spiritDamage` 落在其中即判定），重算 `graceLedger` 即可——复杂度与当初否决的 C 方案相同，故本变更不做。

| 存量 | 位置 | 处理 |
|---|---|---|
| config 的 grace 表 | 玩家 config 文件里的 `graceTierTable` | ✅ D4 改名 `graceTierTableV2` → 老配置缺该键 → 自动采用新表 |
| config 的其余标量键 | 玩家 config 里的 `spiritCost` / `bossSeconds*` / `critChanceCap` 等 | ⚠ **改名不覆盖**（实测：NeoForge 只补缺失键）。玩家需自行删行或重置 config |
| 本地 config 的 12 个过时键 | `run/config/gensokyou-common.toml` | ✅ D7 删文件重建 |
| **B 族存档的灵力强度** | `SpiritPowerData.spiritDamage` = 6 | ⚠ 不迁移，已知代价 |

**另注：1 阶三核的 `spiritCost` 是普通数值 config（`defineInRange`），不是 list。** 改代码默认值**不会**作用于已有 config——玩家 config 里已写着 `spiritCost = 10 / 22 / 14` 会盖住新默认值（与本次 `bossMoveSpeed` 遇到的情形完全相同），**改名也救不了它**（改名只作用于 grace 表那一个键）。**本地已由 D7 删文件覆盖；真实玩家需自行删除 `power.weapon.core*.spiritCost` 三行或重置 config**——列入 release note。

回滚：纯 config 数值 + key 名变更，revert 提交即可，无数据修复需求。

## Open Questions

（无阻塞项。）

- ~~**Q1**：`graceTierTable` 的迁移方案选哪个？~~ → **已定：方案 B（改 key 名为 `graceTierTableV2`）**，理由见 D4。
- ~~**Q4**：`damageScale()` 回到 1.0 是收益还是代价？~~ → **已定：是代价**（阶 1 弹幕 ×2.5、挨弹数 25→10），但接受，因为 10 才是 `bigFairyBossHits` 的预算意图。数值不动，表述改正，见 D6。
- ~~**Q5**：散弹是否需要射程豁免？~~ → **已定：不需要**。按每触发倍率口径散弹只需 177.8 发，250 天然通过，spec 豁免条款删除，见 Context ① 与 D2。
- ~~**Q6**：本地 config 怎么处理？~~ → **已定：删文件重建**，不手改 3 个 spiritCost，见 D7。
- ~~**Q7**：存量存档要不要迁移？~~ → **已定：分三族，A 族由既有 `migrateIfNeeded` 自动重 roll，B 族不迁移为已知代价**。初稿「一律不迁移 / 删档重来」与代码不符，已撤销，见 Migration Plan。
- **Q2**：`refShotsPerSecond = 2.5` 这个锚点在数值全线下移 6 倍后是否仍然合适？它当前等于球核射速，是"击杀 400 发"这个常数的来源。本变更不动它，但下移后"160 秒 = 400 发"的隐含语义是否还清晰，值得在 `monster-stat-budget` spec 里补一句显式说明。
- **Q3**：既然数值仍在校准，后续重标 `spirit_power` 时如何避免 spec 锚点漂移？当前约定：spec 保留阶 1 锚点作为"至少别调回旧值"的护栏，重标时 MUST 同步改 spec + `player-spirit-attributes` 累乘表 + 回归测试三处。是否需要一个脚本来自动比对三者一致性，暂不决定。
