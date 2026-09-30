# rune-affix-pool Specification

## Purpose
TBD - created by archiving change balance-danmaku-weapon-stats. Update Purpose after archive.
## Requirements
### Requirement: 增幅核词条类型集合

增幅核（槽3）词条池 SHALL 由受控类型集合构成，共 **19 键**，分两个来源域：

**玩家属性域（15 键）** —— 词条 id 直接复用 `AttributeKey.id()`，经装备来源的 contribution 层注入玩家属性，与玩家其它来源的属性进同一个加区、同一套硬上限、同一个属性面板。设计立场为"武器的加成也是给玩家的"：

`spirit_regen_rate`、`spirit_power`、`move_speed_bonus`、`graze_chance`、`danmaku_reduce`、`tenacity`、`crit_chance`、`crit_damage`、`spell_amp`、`spell_cdr`、`buff_extend`、`spirit_leech_rate`、`jump`、`phys_resist`、`melee_damage`

**武器专有域（4 键）** —— 玩家属性注册表中无对应物，只改武器自身的乘区：

`damage_pct`（伤害乘区）、`attack_rate_pct`（攻击冷却折减）、`spirit_cost_pct`（单次灵力消耗增减）、`range_pct`（弹道有效距离）

SHALL NOT 进入池的键：`max_spirit`（神恩池台账，属永久属性）、`health_bonus`（永久属性）、`danmaku_resist`（已退役键，config 注明 intentionally absent）。

同一枚增幅核内每个词条 id MUST NOT 出现多于一次。新增词条类型 SHALL 只扩集合，MUST NOT 改动 `rune_affixes` NBT 结构。

#### Scenario: 类型受控

- **WHEN** 生成一枚增幅核
- **THEN** 其词条 id 全部来自上述 19 键，不出现集合外词条

#### Scenario: 同类型不重复

- **WHEN** 一枚 T3 增幅核 roll 出 5 条词条
- **THEN** 5 条 id 互不相同

#### Scenario: 永久属性不入池

- **WHEN** 检查词条池成员
- **THEN** 不含 `max_spirit` 与 `health_bonus`

### Requirement: 词条按核阶的数值范围与权重

词条池 SHALL 只有 **3 个核阶**（T1=灵启阶1-2 / T2=阶3-4 / T3=阶5）。数值来源分两类：

**玩家属性域** —— `value = 采样带% × 对应玩家阶标准属性值`，采样带从 config 按 `核阶,键` 逐键读取，缺省为 T1 `1%~3%` / T2 `2%~5%` / T3 `5%~8%`。参考玩家阶按核阶映射：T1 核取灵启 1 阶标准值、T2 核取 3 阶、T3 核取 5 阶。标准值 SHALL 取自 `grace.graceTierTableV2` 的累加（基准 + Σ各阶增量），MUST NOT 硬编码。

该规则使词条价值随玩家投资等比缩放（永远"占你已有属性的百分之几"），因此不会随阶相对变弱。

**武器专有域** —— 直接百分比，取自 per-tier 表：

| id | T1 | T2 | T3 |
|---|---|---|---|
| `damage_pct` | 3–6% | 7–12% | 12–20% |
| `attack_rate_pct` | 3–6% | 6–10% | 10–15% |
| `spirit_cost_pct` | −8..−3% | −14..−6% | −22..−10% |
| `range_pct` | 3–6% | 6–10% | 8–12% |

**`danmaku_reduce` 特例** —— 该键是无量纲指数 P（受伤 = 原伤 × 2^−P），MUST NOT 按"P 的百分比"给带子（两端都崩：1 阶得 +0.01~0.03 几乎无效，5 阶得 +0.43~0.69 一条词条 26~38% 减伤过肥），SHALL 改用"目标减伤 r → ΔP = −log2(1−r)"反解，r 取该核阶的采样带：

| 核阶 | r | ΔP |
|---|---|---|
| T1 | 1–3% | 0.0144–0.0457 |
| T2 | 2–5% | 0.0288–0.0740 |
| T3 | 5–8% | 0.0740–0.1203 |

数值 SHALL 全部由 config 表驱动，MUST NOT 硬编码于生成器。

#### Scenario: 范围随核阶抬升

- **WHEN** 分别读取 T1 与 T3 的 `damage_pct` 区间
- **THEN** T3 区间整体高于 T1（12–20% vs 3–6%）

#### Scenario: 玩家属性键随投资缩放

- **WHEN** 5 阶玩家（灵力强度标准值 39,140）roll T3 核的 `spirit_power` 词条
- **THEN** 数值落在 39,140 × 5%~8% = 1,957~3,131 区间

#### Scenario: 低阶玩家同键数值更小

- **WHEN** 1 阶玩家（灵力强度标准值 6）roll T1 核的 `spirit_power` 词条
- **THEN** 数值落在 6 × 1%~3% = 0.06~0.18 区间，相对其自身属性为同比例

#### Scenario: 护壁按减伤语义反解

- **WHEN** roll T3 核的 `danmaku_reduce` 词条
- **THEN** 数值落在 0.0740~0.1203 指数区间（等效受伤 ×0.93~0.92），而非 8.6 的 5%~8%

#### Scenario: 权重抽取

- **WHEN** 大量生成同核阶增幅核
- **THEN** 出现频率大致符合 config 权重比例

### Requirement: 词条数量随核阶

单枚增幅核 roll 的词条条数 SHALL 为 **T1 = 1 条、T2 = 3 条、T3 = 5 条**，且不超过受控类型集合大小。条数 SHALL 从 config 读取。

#### Scenario: 高阶核更多词条

- **WHEN** 生成 T1 与 T3 增幅核
- **THEN** T1 为 1 条、T3 为 5 条

### Requirement: 词条总增益预算

全部词条按最大 roll 组合时，对玩家有效 DPS 的总增益 SHALL ≤ **+80%**；该约束 MUST 由"同 id 唯一 + 上表区间 + 19 键池"共同保证，MUST NOT 依赖运行时再次封顶。

参考口径（5 阶满属性玩家装 T3 核的最坏 5 条组合：`spirit_power` +8% / `damage_pct` +20% / `attack_rate_pct` +15% / `crit_chance` +2.8 点 / `crit_damage` +12 点）：

```
1.08 (灵力强度) × 1.20 (伤害乘区) × 1.176 (攻速) × 1.057 (暴击) = +61%
各条取采样带中点时 = +48%
```

两者均落在 +80% 预算内。生成结果与预算校验 SHALL 可被单测覆盖。

#### Scenario: 最大 roll 不超模

- **WHEN** 构造一枚理论最大 roll 的 T3 增幅核
- **THEN** 其有效 DPS 增益 ≤ +80%，不破坏玩家 ×10/阶 曲线

#### Scenario: 典型值在目标区间

- **WHEN** 取各词条采样带中点生成 T3 增幅核
- **THEN** 有效 DPS 增益约 +48%~+60%

#### Scenario: 撞 cap 的键按生效值呈现

- **WHEN** 5 阶玩家（韧性标准值 0.70、硬上限 0.75）roll T3 核的 `tenacity` 词条（+0.035~0.056）
- **THEN** 顶带部分被硬上限截断；洗练预览展示的是结算后的**生效**增量而非 roll 原始值，capped 行带标记

### Requirement: 洗练软保底

增幅核 SHALL 携带一个独立的洗练度计数器（数据组件 `rune_rerolls : int`，MUST NOT 改动 `rune_affixes` 结构）。语义：

- 玩家选择"保留原词条"时 `rune_rerolls += 1`
- 玩家选择"全部采纳"时 `rune_rerolls = rune_rerolls / 2`（向下取整，MUST NOT 清零，以便"这枚核被洗过多次"成为可见的长期履历）

每次 roll 前，采样区间 SHALL 按洗练度向好的一侧平移：

```
t    = min(rune_rerolls, PITY_CAP) / PITY_CAP        PITY_CAP = 10
lo'  = lo + (hi - lo) × t × 0.5
hi'  = lo + (hi - lo) × (1 + t × 0.5)
value = uniform(lo', hi')
```

`t = 0` 时为原区间；`t ≥ PITY_CAP` 时下界抬至原上界之上。玩家属性域的采样带 SHALL 同法平移（对"标准值 × 采样带%"整体乘以 `1 + t × 0.5`）。

计数器 SHALL 在核的 tooltip 中显示为"洗练度 N/10"。`PITY_CAP` SHALL 来自 config。

#### Scenario: 洗练度抬升品质

- **WHEN** 某核 `rune_rerolls` = 10 时洗练
- **THEN** 采样区间下界等于无保底时的原上界，roll 值整体高于洗练度 0 时

#### Scenario: 采纳减半不清零

- **WHEN** `rune_rerolls` = 7 的核洗练后玩家采纳
- **THEN** `rune_rerolls` 变为 3（不是 0）

#### Scenario: 保底不改变条数与 id

- **WHEN** 洗练度 10 的 T3 核洗练
- **THEN** 仍为 5 条互不相同的词条，仅数值抬高
