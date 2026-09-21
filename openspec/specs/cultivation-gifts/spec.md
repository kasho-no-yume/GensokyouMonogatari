# cultivation-gifts Specification

## Purpose
TBD - created by archiving change add-cultivation-gifts. Update Purpose after archive.
## Requirements
### Requirement: 修灵馈赠三键
玩家属性注册表 SHALL 新增三键（与东方弹幕战斗解耦的"修灵馈赠"）：
- `jump`（跳跃，flat，单位=格，硬上限 3.0）
- `phys_resist`（物抗，百分比，硬上限 0.85）
- `melee_damage`（近战，flat，硬上限 100）

三键 SHALL 与其余键同走"配置基准 + Σ阶级贡献"分层结算，且均标记为不可被降神变身改写。
旧档缺该附件时三键取基准 0。

#### Scenario: 键注册与基准
- **WHEN** 新玩家进入世界
- **THEN** 跳跃/物抗/近战最终值均为 0（无馈赠）

#### Scenario: 硬上限
- **WHEN** 跳跃各来源累加到 3.5、物抗累加到 0.9、近战累加到 120
- **THEN** 生效值分别为 3.0、0.85、100

#### Scenario: 不进改写域
- **WHEN** 降神变身临时层尝试改写 `jump`/`phys_resist`/`melee_damage`
- **THEN** 改写被拒（三键不在可改写白名单）

### Requirement: 跳跃原版属性桥
`jump` 最终值 SHALL 经 `minecraft:jump_strength` 原版属性的固定 id modifier 追加实现，语义为"额外跳跃高度（格）"，
并 SHALL 在属性变更后整体幂等重算（非累加）。换算 SHALL 由配置/常量驱动，MUST NOT 硬编码散落。

#### Scenario: 幂等重算
- **WHEN** 跳跃贡献从 +1.2 格变为 +1.8 格
- **THEN** `jump_strength` 的该 modifier 值为对应 +1.8 格的换算值（不是在上次基础上再叠加）

#### Scenario: 最高 +3 格
- **WHEN** 5 阶玩家（跳跃 3.0）
- **THEN** 其跳跃高度较未强化约提升 3 格

### Requirement: 物抗独立减伤乘区
`phys_resist` SHALL 对**非弹幕**伤害生效，按独立于原版护甲/附魔的乘区计算：`实际伤害 = 原伤 × (1 − 物抗)`，
`物抗` 在消费前钳到 [0, 硬上限 0.85]。弹幕伤害 MUST NOT 走本通道（走弹幕护壁指数）。
**豁免**：`GENERIC_KILL`（`/kill`）与 `FELL_OUT_OF_WORLD`（虚空）伤害 MUST NOT 被物抗减免。

#### Scenario: 独立乘区
- **WHEN** 物抗 50% 的玩家受到 100 点普通物理伤害（护甲另算）
- **THEN** 套件阶段乘 0.5，护甲按其自身规则继续结算

#### Scenario: 弹幕不受物抗影响
- **WHEN** 玩家受到 `gensokyou:danmaku` 伤害
- **THEN** 不走物抗，仅走擦弹/护壁指数管线

#### Scenario: 斩杀与虚空豁免
- **WHEN** 对玩家执行 `/kill` 或使其坠落虚空
- **THEN** 伤害不被物抗减免

#### Scenario: 封顶
- **WHEN** 物抗来源累加超过 0.85
- **THEN** 按 0.85 生效

### Requirement: 近战附加物理伤害
系统 SHALL 在近战伤害（伤害类型 `minecraft:player_attack`）且攻击者为玩家时，把该玩家的 `melee_damage` 最终值加到伤害上（附加物理伤害）；并 SHALL 先加附加、再乘受害者物抗（若受害者亦为玩家）。非玩家来源或非近战伤害 MUST NOT 获得该附加。

#### Scenario: 近战加伤
- **WHEN** 近战 +25 的玩家用剑攻击一只生物
- **THEN** 该次伤害在武器基础值上额外 +25 后进入结算

#### Scenario: 非近战不触发
- **WHEN** 玩家用弹幕或弓箭攻击
- **THEN** 不获得近战附加

### Requirement: 修灵馈赠逐阶数值
三键 SHALL 在 config `grace` 表按阶级以"增量 + roll"定义，并使 **T5 满 roll 恰为给定上限**（跳跃 3.0 格、物抗 0.85、近战 100），累计中点为：

| 阶 | 跳跃(+格) 累计中点 | 物抗 累计中点 | 近战(+伤) 累计中点 |
|---|---|---|---|
| 1 | 0.4 | 4% | 5 |
| 2 | 0.8 | 12% | 12 |
| 3 | 1.2 | 24% | 25 |
| 4 | 1.6 | 40% | 50 |
| 5 | 2.0（满 roll 3.0） | 63%（满 roll 0.85） | 80（满 roll 100） |

逐阶增量与 roll：`jump` 0.4（roll 0.5）；`phys_resist` 0.04/0.08/0.12/0.16/0.23（roll 0.35）；`melee_damage` 5/7/13/25/30（roll 0.25）。数值 SHALL 全部可配置，洗练 SHALL 整组重掷这三键；硬上限 SHALL 等于满 roll 值（3.0 / 0.85 / 100）。

#### Scenario: 逐阶可得
- **WHEN** 玩家逐阶进阶
- **THEN** 三键按上表累计提升，允许 roll 落在区间内

#### Scenario: 满 roll 达上限
- **WHEN** 5 阶玩家三键均 roll 到区间上界
- **THEN** 跳跃=3.0 格、物抗=0.85、近战=100（等于各自硬上限）

#### Scenario: 洗练重掷
- **WHEN** 对某阶执行洗练
- **THEN** 该阶三键贡献随整组重掷

