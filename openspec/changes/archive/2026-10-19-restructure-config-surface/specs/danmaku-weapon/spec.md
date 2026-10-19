## MODIFIED Requirements

### Requirement: range_pct 词条消费点

`range_pct` 词条的语义 SHALL 唯一为**弹道有效距离**（弹幕从发射到消失/命中所能覆盖的距离），SHALL 覆盖三处消费点：

| 核形态 | 消费字段 | 有效距离定义 |
|---|---|---|
| 激光类 | `FirePattern.laserMaxLength` | 激光束长度 |
| 投射物类 | `FirePattern.lifetimeSeconds` | 弹速 × 存活时间 |
| 符卡类 | 符卡弹道寿命（`lifetimeSeconds`） | 弹速 × 存活时间 |

三处 SHALL 统一施加指数衰减 `× (1 + r)^0.75`（`r` = `range_pct` 累加值），MUST NOT 线性相乘。衰减指数 SHALL 为**代码常量**（战力数值，`config-surface` 三分法），MUST NOT 暴露为配置项。

**MUST NOT 另设硬上限**：由于「同 id 唯一」保证单枚核最多一条 `range_pct`，且一把武器只有 slot3 一个增幅核槽，该词条的最大值已被结构性封顶在 12%（`(1.12)^0.75 ≈ +8.9%` 有效射程），不存在堆叠路径。指数衰减 SHALL 作为唯一的边际收益递减手段，MUST NOT 额外引入数值上限或钳制。

符卡索敌半径（`DanmakuTargetPicker.pick`）SHALL 为**代码常量** `BalanceNumbers.WEAPON_TALISMAN_PICK_RANGE`（40 格），MUST NOT 接入 `range_pct` —— 索敌是"锁定能力"而非"距离"，纳入会使"有效距离"这一可见数值与实际自动锁定范围脱钩。

#### Scenario: 激光核有效距离提升

- **WHEN** 装入带 `range_pct` +12% 词条的增幅核到激光类弹幕核上
- **THEN** 激光长度 ×(1+0.12)^0.75 ≈ ×1.09

#### Scenario: 索敌半径不受影响

- **WHEN** 装入带 `range_pct` +12% 词条的增幅核到符卡类弹幕核上
- **THEN** 符卡索敌半径仍为常量值 40 格，仅弹道射程提升

#### Scenario: 指数衰减生效

- **WHEN** 比较 `range_pct` +12% 与 +24% 两种增幅核对有效射程的影响
- **THEN** 后者提升幅度小于前者两倍（`(1.12)^0.75` 与 `(1.24)^0.75` 之比 < 2）

#### Scenario: 衰减指数与索敌半径不可经 TOML 修改

- **WHEN** 检索衰减指数与索敌半径的来源
- **THEN** 两者唯一来源为代码常量，MUST NOT 存在对应配置键
