# danmaku-weapon Specification

## Purpose

玩家核心战斗主武器「符星铳」的弹幕核属性呈现，以及增幅核 `range_pct` 词条在弹道距离上的消费点。

## MODIFIED Requirements

### Requirement: 槽3 增幅核（程序化生成，NBT词条）

槽3 SHALL 装入「增幅核」物品，其属性**不在注册时定死，而由程序按受控词条池随机生成并写入 NBT**。词条类型、按核阶的数值范围、权重与词条数量 SHALL 遵循能力 `rune-affix-pool` 的规范。

词条分为两个来源域：

- **玩家属性域（15 键）**：`crit_chance` / `crit_damage` 等 id 直接对应 `AttributeKey`，经**装备来源的 contribution 层**注入玩家属性，与玩家其它来源的属性同加区、同硬上限、同属性面板。`crit_chance` / `crit_damage` MUST NOT 再以 `crit_chance_pct` / `crit_damage_pct` 形式重复表达（见 `rune-affix-pool` 的 REMOVED 章节）。
- **武器专有域（4 键）**：`damage_pct` 改伤害乘区、`attack_rate_pct` 改攻击冷却、`spirit_cost_pct` 改单次灵力消耗、`range_pct` 改弹道有效距离。

增幅核可装备的前提同槽2闸门（武器等级 ≥ 核 tier）。

#### Scenario: 同类型核属性不同

- **WHEN** 两个同核阶增幅核生成
- **THEN** 各自 NBT 内词条与数值随机不同，玩家可见差异

#### Scenario: 词条改乘区

- **WHEN** 装入「伤害%+15%」「灵力消耗%−10%」两词条的增幅核
- **THEN** 最终伤害乘区 +15%，单次发射灵力消耗 ×0.9

#### Scenario: 玩家属性词条进加区

- **WHEN** 主手武器 slot3 的增幅核带 `crit_chance` +2.8 点词条，玩家打开核心界面查看属性面板
- **THEN** 暴击率最终值包含该 +2.8 点增量（与其他来源同加区累加），摘下武器后该增量消失

## ADDED Requirements

### Requirement: range_pct 词条消费点

`range_pct` 词条的语义 SHALL 唯一为**弹道有效距离**（弹幕从发射到消失/命中所能覆盖的距离），SHALL 覆盖三处消费点：

| 核形态 | 消费字段 | 有效距离定义 |
|---|---|---|
| 激光类 | `FirePattern.laserMaxLength` | 激光束长度 |
| 投射物类 | `FirePattern.lifetimeSeconds` | 弹速 × 存活时间 |
| 符卡类 | 符卡弹道寿命（`lifetimeSeconds`） | 弹速 × 存活时间 |

三处 SHALL 统一施加指数衰减 `× (1 + r)^0.75`（`r` = `range_pct` 累加值），MUST NOT 线性相乘。衰减指数 SHALL 来自 config。

**MUST NOT 另设硬上限**：由于「同 id 唯一」保证单枚核最多一条 `range_pct`，且一把武器只有 slot3 一个增幅核槽，该词条的最大值已被结构性封顶在 12%（`(1.12)^0.75 ≈ +8.9%` 有效射程），不存在堆叠路径。指数衰减 SHALL 作为唯一的边际收益递减手段，MUST NOT 额外引入数值上限或钳制。

`GensokyouConfig.WEAPON_TALISMAN_PICK_RANGE`（符卡索敌半径，`DanmakuTargetPicker.pick`）SHALL NOT 接入 `range_pct` —— 索敌是"锁定能力"而非"距离"，纳入会使"有效距离"这一可见数值与实际自动锁定范围脱钩。

#### Scenario: 激光核有效距离提升

- **WHEN** 装入带 `range_pct` +12% 词条的增幅核到激光类弹幕核上
- **THEN** 激光长度 ×(1+0.12)^0.75 ≈ ×1.09

#### Scenario: 索敌半径不受影响

- **WHEN** 装入带 `range_pct` +12% 词条的增幅核到符卡类弹幕核上
- **THEN** 符卡索敌半径仍为配置值 40 格，仅弹道射程提升

#### Scenario: 指数衰减生效

- **WHEN** 比较 `range_pct` +12% 与 +24% 两种增幅核对有效射程的影响
- **THEN** 后者提升幅度小于前者两倍（`(1.12)^0.75` 与 `(1.24)^0.75` 之比 < 2）

### Requirement: 弹幕核属性 tooltip

`BulletCoreItem` SHALL 按 `FirePattern` 的三种构造形态分型渲染完整 tooltip，MUST NOT 只显示灵启阶要求。数值全部取自 `CoreStats` 与 `FirePattern` 的 Supplier，**有效 DPS 因子 SHALL 复用 `CoreMath.bulletDpsFactor` / `CoreMath.laserDpsFactor`**（同一批 world-independent 纯函数，已被预算校验覆盖）。

投射物类 SHALL 展示：灵启阶要求、伤害倍率、攻击间隔（tick 与 发/秒）、单发耗灵、弹数、散布角、弹速、存活时间、**有效射程**（= 弹速 × 存活时间）、有效 DPS 因子。
激光类 SHALL 展示：伤害倍率、攻击间隔、单发耗灵、激光长度、激光半径、启动延迟、持续时间（含激活期脉冲数）、有效 DPS 因子。
符卡类 SHALL 展示：伤害倍率、攻击间隔、单发耗灵、索敌距离、锁定灵敏、弹速、有效 DPS 因子。

tooltip 展示的弹幕核数值为**核自身基准值**，MUST NOT 反映增幅核 `range_pct` 等词条的装配后加成（tooltip 无法获知父武器的其它槽位）。

#### Scenario: 投射物核 tooltip 完整

- **WHEN** 玩家悬浮查看「弹幕核·球」
- **THEN** 显示伤害倍率、攻击间隔、单发耗灵、弹数、散布、弹速、存活时间、有效射程与有效 DPS 因子

#### Scenario: 有效射程有文本锚点

- **WHEN** 玩家查看弹幕核 tooltip
- **THEN** "有效射程"行显示为弹速 × 存活时间的乘积，使 `range_pct` 词条的作用对象可被玩家理解

#### Scenario: 激光核分型

- **WHEN** 玩家悬浮查看激光类弹幕核
- **THEN** 显示激光长度/半径/启动延迟/持续时间与脉冲数，不显示弹数与散布角

#### Scenario: 无灵启阶要求时省略

- **WHEN** 查看 `requiredTier` 为 1 的弹幕核
- **THEN** 不显示"灵启 N 阶"行（与 `AmpCoreItem` 现有行为一致）
