# Proposal: add-cultivation-gifts（修灵馈赠——三项玩家强化属性）

> 需求依据：玩家直接提出"和东方无关的、修灵的馈赠"——跳跃、非弹幕抗性、近战物理伤害。
> 依赖 `player-attribute-suite`（属性注册表/分层结算）与 `superhuman-temper`（阶级 roll 表）。

## Why

现有 15 键玩家属性全部围绕弹幕战斗（灵力/护壁/暴击等），缺少"修灵带来的身体强化"维度：
跳跃、抗打击、近战。补三项可让成长兼顾探索/近战，且与既有点设计解耦。

## What Changes

- **注册表 15 → 18 键**（`player-attribute-suite`）：新增
  - `jump`（跳跃，flat，单位=格）→ 桥接到 `minecraft:jump_strength` 属性，**最多 +3 格**
  - `phys_resist`（物抗，百分比，cap **0.85**）→ **独立于原版护甲的乘区**：非弹幕伤害 `×(1−物抗)`
  - `melee_damage`（近战，flat）→ 玩家近战（`player_attack`）附加物理伤害，**最多 +100**
- **短命名**：显示名 `跳跃 / 物抗 / 近战`（原"跳跃提高/非弹幕抗性提升/近战物理伤害提升"过长）
- **阶级 roll 表新增 3 行 × 5 阶**（`superhuman-temper`），roll ±35%
- **数值**（增量 → 累计中点；**T5 满 roll = 给定上限**，用户委托设计）：

| 阶 | 跳跃(+格) 累计中点 | 物抗 累计中点 | 近战(+伤) 累计中点 |
|---|---|---|---|
| 1 | 0.4 | 4% | 5 |
| 2 | 0.8 | 12% | 12 |
| 3 | 1.2 | 24% | 25 |
| 4 | 1.6 | 40% | 50 |
| 5 | 2.0（满 roll **3.0**） | 63%（满 roll **85%**） | 80（满 roll **100**） |

- 逐阶增量：`jump` 0.4（roll 0.5）；`phys_resist` 0.04/0.08/0.12/0.16/0.23（roll 0.35）；`melee_damage` 5/7/13/25/30（roll 0.25）
- **硬上限 = 满 roll 值**（3.0 / 0.85 / 100），作为来源叠加的安全闸

- **豁免**：`/kill`（GENERIC_KILL）与虚空（FELL_OUT_OF_WORLD）不受物抗影响（同东方怪抗性约定）
- 数值全部走 config；`phys_resist` 退役前的 `danmaku_resist` 无关

## Capabilities

### New Capabilities

- `cultivation-gifts`：`jump`/`phys_resist`/`melee_damage` 三键的定义、逐阶数值范围、封顶、三项消费点（跳跃原版属性桥、非弹幕独立减伤乘区、近战附加伤害）与豁免规则

### Modified Capabilities

- `player-attribute-suite`：属性注册表由 15 键扩为 18 键；补充"独立乘区"在非弹幕减伤上的用法说明
- （`superhuman-temper` 的阶级表为 config 数据，非需求变更；新键的逐阶数值与消费点由 `cultivation-gifts` 承载）

## Impact

- **代码**：`spirit/attr/AttributeKey`（+3 键）、`AttributeBridgeIds` + `PlayerAttributes.refreshBridged`（跳跃桥）、新事件处理器（非弹幕减伤 + 近战附加）、`GensokyouConfig`（`playerAttributes` 段 + 表行）
- **测试**：`AttributeKeyRegistryTest`（15→18）
- **文档**：`docs/weapon-design-guidelines.md` 不涉；属性说明可入 `docs/` 或 project.md 引用
- **兼容**：旧档缺该附件取基准 0；`grace_tier_N` 新增键按新表 roll

## Out of Scope

- 跳跃的二段跳/空中控制
- 近战附加伤害的额外特效（击退/破甲）
- 与降神变身的改写域（这三键不进变身白名单）
