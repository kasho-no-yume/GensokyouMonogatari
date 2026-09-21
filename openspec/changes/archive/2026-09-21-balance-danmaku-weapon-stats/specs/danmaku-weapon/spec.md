## MODIFIED Requirements

### Requirement: 槽1 弹幕核（注册物品，定值）
槽1 SHALL 装入「弹幕核」物品，每个核在注册时**定死**其行为类型与基础乘区，不随机：
- 行为类型复用既有 `AbstractDanmakuProjectile` 子类：球（单发消失）/飞刀（穿透）/灵符（追踪）/激光（延迟站桩AOE）
- 基础乘区 `coreBaseMult` = 「造成玩家灵力伤害 x% 的伤害」中的 x（每核写死，数据驱动配置）
- 每个核 SHALL 声明 `requiredTier`（武器等级门槛）与 `spiritCost`；`coreBaseMult` **不随核心 tier 增长**（gears 成长集中在武器等级倍率，避免双叠）
- 核的**有效 DPS 因子**（`coreBaseMult × 射速`，激光按脉冲数归一见下）SHALL 落在 `[1.5, 2.5] × spiritPower` 带内，按角色分工而非数值碾压

#### Scenario: 核决定行为与基础x
- **WHEN** 装入灵符核（x=120）的武器开火
- **THEN** 生成追踪型弹幕，单发伤害 = 玩家灵力伤害 × 120%

#### Scenario: 四类行为差异不变
- **WHEN** 分别装入球/飞刀/灵符/激光核
- **THEN** 各自复用对应既有弹幕实体的碰撞与渲染行为（穿透/追踪/延迟AOE等），与 `danmaku-*` 各 spec 一致

#### Scenario: 有效DPS因子在带内
- **WHEN** 分别计算六核的 `coreBaseMult × 射速`（激光脉冲归一）
- **THEN** 全部落在 `[1.5, 2.5]`，无单一核以数值碾压其他核

#### Scenario: 核倍率不随阶
- **WHEN** 同一枚弹幕核被不同武器等级使用
- **THEN** 其 `coreBaseMult` 不变，伤害差异只来自武器等级倍率与玩家属性

### Requirement: 伤害公式（MOBA 乘区）
单发弹幕最终伤害 SHALL 按公式结算：
`finalDamage = playerSpiritDamage × coreBaseMult × weaponLevelMult × (1 + ΣruneDmgMult) × critMult`
- `playerSpiritDamage`：玩家灵力伤害属性（经属性套件"灵力强度"键读取），由阶级提升驱动，受配置影响
- `coreBaseMult`：槽1核定值 x%（不随核心 tier 变化）
- `weaponLevelMult`：槽2武器等级对应的增强系数（配置表，**3 档 `1.0 / 2.0 / 4.0`（每级 ×2）**）。武器等级**每级覆盖两个灵启阶**（Lv1=阶1-2 / Lv2=阶3-4 / Lv3=阶5），倍率承担"跨带跃升"而非每阶成长——每阶 DPS ×10 由玩家属性（灵力强度 ×≈9/阶 + 暴击）承担（见 `docs/weapon-design-guidelines.md`）
- `ΣruneDmgMult`：槽3所有"伤害%"词条累加
- `critMult`：暴击乘区——发射时按玩家"暴击率"属性服务端 roll 一次，暴击则取 `1 + 暴击伤害加成`，否则取 1；判定结果与系数 SHALL 写入弹幕 NBT 随弹持久化（命中时不再 roll）
攻速、灵力消耗为独立属性，由槽3词条与弹幕核类型决定，不计入上式。**激光类**核的伤害为脉冲结算：其计入 DPS 预算的有效倍率 SHALL 按 `单脉冲倍率 × 激活期脉冲数 / 冷却周期` 归一，MUST NOT 以单脉冲倍率直接代表总输出。玩家满配（同阶武器 + 无词条球核）的平均 DPS SHALL 满足 `balance-player-monster-stats` 的 ×≈10/阶阶梯，且 1 阶 ≈ 16（钻石剑 ~ 锋利5下界剑之间）。

#### Scenario: 同武器不同收益
- **WHEN** 低阶新手与高阶老手持同一把满插槽武器
- **THEN** 老手单发伤害显著更高（差距来自 playerSpiritDamage 乘区，核本身数值相同）

#### Scenario: 四类灵力单价错开
- **WHEN** 分别用灵符核（高耗低频）/球核（低耗高频）/激光核（站桩高耗）/飞刀核
- **THEN** 各自单次灵力消耗与射速档位互不相同，形成情境分工

#### Scenario: 暴击发射时定值
- **WHEN** 暴击率 25% 的玩家发射一枚弹幕，发射 roll 命中暴击
- **THEN** 该弹 NBT 记录暴击系数，无论何时命中均按发射时系数结算；未暴击的弹命中时不重 roll

#### Scenario: 等级倍率梯度
- **WHEN** 同一张球核与玩家属性，被 Lv.1 与 Lv.3 武器分别发射
- **THEN** Lv.3 单发伤害约为 Lv.1 的 4 倍（跨带换装的一次跃升，而非每阶成长）

#### Scenario: 武器等级对应两个灵启阶
- **WHEN** 玩家处于 3 阶并已换装 Lv.2 武器
- **THEN** 其同阶 DPS 相对 2 阶（仍为 Lv.1）提升约 ×20（属性 ×10 叠加换装 ×2 跃升）

#### Scenario: 激光脉冲归一
- **WHEN** 计算站桩激光核的有效 DPS 因子
- **THEN** 按激活期实际脉冲数折算后落在 `[1.5, 2.5]` 带内，不因多次结算而超模

### Requirement: 灵力消耗（共用池）
武器每次发射 SHALL 从玩家灵力池（`spirit-power-pool`）扣除由弹幕核类型决定的灵力成本；灵力不足时不予发射并提示。武器与符卡系统共用同一灵力池。灵力成本 SHALL 随核心 `requiredTier` 以 **×10/阶**递增（`spiritCost = coreBaseCost × 10^(requiredTier−1)`），`coreBaseCost` 与 `coreBaseMult` 成正比，使各核"伤害/灵力"效率量级一致，并令核在其预期阶级下每管池约 40~100 次触发。

#### Scenario: 灵力不足不射
- **WHEN** 灵力低于本次发射成本
- **THEN** 不生成弹幕，给出提示，无消耗

#### Scenario: 与符卡争池
- **WHEN** 玩家连续普攻后灵力见底再放符卡
- **THEN** 符卡因灵力不足进入冷却提示，体现资源分配取舍

#### Scenario: 成本随阶递增
- **WHEN** 比较 1 阶球核与 3 阶激光炮核的单次灵力成本
- **THEN** 后者约为前者的 100 倍量级（10 vs 2500），与池量级同步

### Requirement: 槽3 增幅核（程序化生成，NBT词条）
槽3 SHALL 装入「增幅核」物品，其属性**不在注册时定死，而由程序按受控词条池随机生成并写入 NBT**。词条类型、按 tier 的数值范围、权重与词条数量 SHALL 遵循新能力 `rune-affix-pool` 的规范；`damage_pct` 词条改伤害乘区，`attack_rate_pct` 改攻击冷却，`spirit_cost_pct` 改单次灵力消耗，`crit_chance_pct`/`crit_damage_pct` 改暴击属性。增幅核可装备的前提同槽2闸门（武器等级 ≥ 核 tier）。

#### Scenario: 同类型核属性不同
- **WHEN** 两个同 tier 增幅核生成
- **THEN** 各自 NBT 内词条与数值随机不同，玩家可见差异

#### Scenario: 词条改乘区
- **WHEN** 装入「伤害%+15%」「灵力消耗%−10%」两词条的增幅核
- **THEN** 最终伤害乘区 +15%，单次发射灵力消耗 ×0.9

#### Scenario: 高级核词条更多更强
- **WHEN** 比较 T1 与 T3 增幅核
- **THEN** T3 词条数量更多（4 vs 2）、数值范围更高（遵循 `rune-affix-pool` 表）
