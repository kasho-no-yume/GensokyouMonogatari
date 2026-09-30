# 武器设计总纲

> 本文件是 `danmaku-weapon` / `rune-affix-pool` 数值规范的**可读基准**，供以后新增
> 武器/攻击模式（弹幕核类型、firePattern 变体）时参照。与 spec 冲突时以 spec 为准并在此登记。
> 配套：`openspec/specs/danmaku-weapon/spec.md`、`openspec/specs/rune-affix-pool/spec.md`。
> 变更来源：`balance-danmaku-weapon-stats`（与 `balance-player-monster-stats` 配对落地）。

## 1. 武器等级：3 级，每级覆盖两个灵启阶

- 武器等级由槽2「武器等级核」决定，**没有 Lv4/Lv5**（目前）。
- 对应关系：**Lv1 = 阶 1-2，Lv2 = 阶 3-4，Lv3 = 阶 5**。
- `weaponLevelMult` 仅 3 档 `[1.0, 2.0, 4.0]`（每级 ×2）。
- 核的 `requiredTier` 是**武器等级门槛**，不是玩家阶级门槛（炼体只影响最终伤害，不挡装备）。

## 2. 玩家 DPS 的 ×10/阶 由属性承担，武器只给跨带跃升

| 来源 | 每阶贡献 | 说明 |
|---|---|---|
| `spirit_power`（灵力强度） | **×≈9/阶** | DPS 的主力；累计 1 → 10 → 90 → 773 → 6523 |
| 暴击（暴击率×暴伤） | ×≈1.1/阶 | 上限有限（率 ≤0.5、暴伤加成 ≤2.0） |
| 武器等级倍率 | 0（带内）/ **×2（跨带）** | 阶3、阶5 边界各一次跃升，形成"换装"爆点 |
| 增幅核词条 | 见 §5 | max-roll ≤ +80% 有效 DPS |

结果：**每阶总 DPS 至少 ×10**（跨带那两阶约 ×20）。校验表（球核、同阶武器、无词条）：

> **绝对量级已整体 ÷6**（`rebalance-tier1-spirit-and-danmaku-cost`）：阶 1 单发伤害落到约 1，低于石剑（5）与铁剑（6），使原版武器在阶 1 仍保有竞争力。**各阶之间的倍率关系完全不变**，故战斗耗时与压迫感曲线不动。
>
> 4/5 阶的 683、5750 是 `4100/6`、`34500/6` 的舍入值，倍率在 1 位小数上相等（tier4 8.5926→8.5889、tier5 8.4353→8.4386，相对误差 < 0.1%）——**不是严格相等**。

```
阶 1:  1     ×1.066 × 1.0 × 2.5 ≈ 2.67
阶 2:  10    ×1.128 × 1.0 × 2.5 ≈ 28.2  (×10.6)
阶 3:  90    ×1.253 × 2.0 × 2.5 ≈ 564   (×20.0)  ← 换 Lv2 武器
阶 4:  773   ×1.465 × 2.0 × 2.5 ≈ 5666  (×10.0)
阶 5:  6523  ×1.800 × 4.0 × 2.5 ≈ 117414(×20.7)  ← 换 Lv3 武器
```

> 若以后要让武器承担更多，必须同步下调 `spirit_power` 增速，否则总曲线会超 ×10/阶。

## 3. 弹幕核规范（槽1）

- 核在注册时**定死**行为类型与基础乘区；`coreBaseMult` **不随核心 tier 增长**
  （gear 成长只由 `weaponLevelMult` 与词条承担，避免双叠）。
- **有效 DPS 因子**（= 期望每秒伤害 / 灵力强度）必须落在 **`[1.5, 2.5]`** 带内：

```
投射物核：coreBaseMult × 弹数 × (20 / attackRateTicks)
激光核：  单脉冲倍率 × 激活期脉冲数 × (20 / attackRateTicks)
          脉冲数 = max(1, durationTicks / 5)   // 每 5 tick 判伤一次
```

- 现役核基准（纯函数核 `item/weapon/CoreMath`）：

| 核 | 行为 | coreBaseMult | attackRate | reqTier | spiritCost | 有效DPS因子 |
|---|---|---|---|---|---|---|
| 球 | 单发消失 | 1.00 | 8 (2.5/s) | 1 | 2 | 2.50 |
| 飞刀 | 穿透 | 1.40 | 12 (1.67/s) | 1 | 3 | 2.33 |
| 散弹 | 5 发短扇 | 0.45（每发） | 24 (0.83/s) | 1 | 4 | 1.88 |
| 灵符 | 追踪 | 1.20 | 16 (1.25/s) | 2 | 120 | 1.50 |
| 激光枪 | 短激光脉冲 | 0.50（每脉冲） | 10 (2/s) | 2 | 50 | 2.00 |
| 激光炮 | 站桩重击 | 0.50（每脉冲） | 60 (0.33/s) | 3 | 2500 | 2.00 |

- 角色差异靠**行为/射速/成本**，不靠数值碾压（同带内有效因子接近）。1 阶三核的"伤害/灵力"效率相对球核偏移 ≤ ±10%（飞刀 ×0.933、散弹 ×1.125）——`spiritCost` 是整数量，2/3/4 是凑整而非 10/14/22 的 ÷5，故**要求量级一致而非精确相等**。

## 4. 灵力成本规范（共用池）

- `spiritCost` 按阶递增（`×10/阶`），`coreBaseCost` 与 `coreBaseMult` 成正比，
  使各核"伤害/灵力"效率保持同一量级。
- **规范性判据：满池可负担发数 ≥ 同阶 BOSS 击杀所需发数。**

```
满池可负担发数 = max_spirit(该核预期阶) / spiritCost
击杀所需发数   = bossSeconds × refShotsPerSecond / (coreBaseMult × 弹数)
成本上界       = max_spirit × coreBaseMult × 弹数 / (bossSeconds × refShotsPerSecond)
```

  分母是**每触发伤害倍率** `coreBaseMult × 弹数`（一次扣一次灵力打出多少倍灵力强度），
  **MUST NOT** 用上面 §3 的"有效 DPS 因子"——后者含射速，用它会把球核算成 160 发而非 400 发。

  阶 1（池 1000、`bossSeconds` 160、`refShotsPerSecond` 2.5）实测：

  | 核 | 每触发倍率 | 击杀所需 | 成本上界 | 取值 | 满池可负担 |
  |---|---|---|---|---|---|
  | 球 | 1.0 | 400 | 2.5 | 2 | 500 |
  | 飞刀 | 1.4 | 285.7 | 3.5 | 3 | 333 |
  | 散弹 | 2.25 | 177.8 | 5.6 | 4 | 250 |

  **散弹不受射程豁免**——弹数（5）抬高每触发倍率，需求发数正比下降。射程只决定其定位
  （近距离/群怪），不影响该判据成立。玩家回灵（池的 0.03%~0.3%/s）不计入判据，仅作余量。
- 5 阶池（10⁷）的主要消耗出口是**高强符卡**，不是武器连射。

## 5. 增幅核词条池规范（槽3）

- **词条类型集合**（同枚核内每类型最多 1 条）：`damage_pct`、`attack_rate_pct`、
  `spirit_cost_pct`、`crit_chance_pct`、`crit_damage_pct`。
- **配置格式**：`id,min,max,weight,tier`（`tier` 为精确档位）。
- **品阶跟随武器等级带**（只有 3 阶：T1=阶1-2 / T2=阶3-4 / T3=阶5）：
- **按 tier 区间**：

| id | T1（阶1-2） | T2（阶3-4） | T3（阶5） | 权重 |
|---|---|---|---|---|
| damage_pct | 3–6% | 7–12% | 12–20% | 10 |
| attack_rate_pct | 3–6% | 6–10% | 10–15% | 8 |
| spirit_cost_pct | −8..−3% | −14..−6% | −22..−10% | 8 |
| crit_chance_pct | 2–4% | 4–7% | 6–12% | 6 |
| crit_damage_pct | 6–12% | 14–25% | 25–45% | 5 |

- **词条数量**按 tier：T1 2 / T2 3 / T3 4。
- **总预算**：max-roll 增幅核有效 DPS 增益 **≤ +80%**（≈0.3 个阶级），典型 ≈ +55%；
  由"同 id 唯一 + 上表区间"保证，不做运行时再封顶。
- NBT 结构 `rune_affixes`（id + value 列表）保持稳定；新增词条类型只扩集合，不改结构。

## 6. 新增一个核/攻击模式的操作清单

1. 选**行为类型**（球/飞刀/灵符/激光）与 **firePattern**（弹数/张角/弹速/射程；激光的长度/半径/延迟/持续）。
2. 定**角色**（持续/穿透/爆发/追踪/站桩AOE）与 **requiredTier**（1~3，对应武器等级带）。
3. 反解 `coreBaseMult`/`attackRate` 使**有效 DPS 因子落在 [1.5, 2.5]**（用 §3 公式，激光务必脉冲归一）。
4. 按 §4 定 `spiritCost`：先用 `上界 = max_spirit(预期阶) × coreBaseMult × 弹数 / (bossSeconds × refShotsPerSecond)` 反解，再向下取整到能覆盖击杀需求的最小值。
5. 若引入新词条类型：扩 §5 类型集合 + 各 tier 区间 + 权重 + lang 键；重算总预算 ≤ +80%。
6. 在 `docs/asset-placeholder-list.md` 登记贴图；补模型/语言；核名按 `requiredTier` 染品阶色。
7. 更新 spec 与两张表（§3 核表、§5 词条表），并跑 `CoreMath`/预算单测。

## 7. 变更流程

- 改数值先改本文档与 spec，再改 `GensokyouConfig`；禁止在行为代码里硬编码。
- 与玩家曲线（`balance-player-monster-stats`）联动：任何改动都要重跑 §2 的 ×10/阶校验。

## 8. 改 `spirit_power` 时的牵连面

**单一消费者结论：`spirit_power` 的伤害读取只有一条路径。**
`WeaponFiring:61` → `PlayerAttributes.spiritPower(player)`（`PlayerAttributes.java:161`）→ `AttributeKey.SPIRIT_POWER`
→ `ModAttachments.get(p).spiritDamage()`。全仓库无第二个调用方。

因此改 `spirit_power` 阶级表会同时移动两处，且**成比例**：

```
单发伤害   = spirit_power × coreBaseMult × weaponLevelMult × critAvg
参考 DPS   = spirit_power × weaponLevelMult × critAvg × refShotsPerSecond
BOSS HP    = 参考 DPS × bossSeconds          （经 MonsterStatBudget.referencePlayerDps 自动跟随）
```

两者同源于 `spirit_power`，**均匀缩放不改变击杀所需发数**（见 §4）。不受影响的是仪式耗灵、
符卡花费、灵力池量级、回灵曲线、修士赠礼——它们走各自的表项。

### 唯一非尺度不变项：`damageScale()`

`AbstractTouhouBoss.hurt()`（`:218-226`）在 `damageScale() > 1` 时做 `amount /= scale`，
而 `damageScale() = effectiveHp ≤ 1024 ? 1.0 : effectiveHp/1024`——**原版 `MAX_HEALTH` 上限**。

```
BOSS HP 跨过 1024 时，弹幕伤害被折算，玩家承受力随血量预算漂移。
```

**例**：阶 1 BOSS HP 从 2560 降到 426（`spirit_power` ÷6 的连带结果）时，`damageScale()`
从 2.5 回到 1.0，弹幕不再折算 ⇒ 单发伤害 ×2.5，玩家可挨发数 25→10。

**凡是改动 `spirit_power` 或 `bossSeconds` 导致某阶 BOSS HP 跨越 1024，必须重核该阶弹幕伤害。**
这是"均匀缩放保持战斗形状"唯一的例外。
