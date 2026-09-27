# spirit-core-crafting Specification

## Purpose
灵力核心 0～2 阶的生存获取路径与指导书展示：零阶为不消耗灵力的工作台 bootstrap（保证最早期仪式供能链可启动、不产生死锁），一阶与二阶由源初造化之仪产出；灵力核心始终保持独立供能边界，不参与弹幕武器槽与玩家灵力池扣费。

## Requirements

### Requirement: 零阶灵力核心是可启动的工作台 bootstrap
系统 SHALL 为 `spirit_core_0` 提供普通工作台无序合成 `gensokyou:spirit_core_0`，消耗 `ritual_stone_0×4 + ppoint×4 + minecraft:amethyst_block×1`，产出 `spirit_core_0×1`。该配方 MUST NOT 要求灵力，`MUST NOT` 出现在源初造化之仪中，`MUST NOT` 被 `danmaku_weapon` 等战斗物品门控。

#### Scenario: 制作零阶灵力核心
- **WHEN** 玩家在工作台按 4 仪式石、4 P 点、1 紫水晶块完成无序合成
- **THEN** 系统不消耗任何灵力，产出容量 50,000、注灵速率 1,000/s 的零阶灵力核心

#### Scenario: 开局不存在供灵死锁
- **WHEN** 玩家尚未获得任何灵力核心，也没有其它给仪式供灵的手段
- **THEN** 仍可直接制作零阶灵力核心并将其手动装入仪式核心槽

#### Scenario: 入门电池不依赖战斗系统
- **WHEN** 玩家没有任何弹幕主武器或武器核
- **THEN** 仍可制作灵力核心 0 阶

### Requirement: 一阶灵力核心由一阶造化产出
系统 SHALL 在源初造化之仪中提供 `zaohua_spirit_core_1` 配方，且 MUST NOT 提供普通工作台替代配方。该配方 SHALL 使用 `minTier:1`、`spCost:20000`，消耗 `ritual_stone_1×1 + spirit_iron×1 + minecraft:soul_sand×4 + minecraft:quartz×4`，产出 `spirit_core_1×1`。

#### Scenario: 制作一阶灵力核心
- **WHEN** 玩家在 1 阶源初造化之仪摆齐一阶核心原料并完成合成
- **THEN** 系统消耗 20,000 灵力与一份原料，产出容量 600,000、注灵速率 8,000/s 的一阶灵力核心

#### Scenario: 一阶电池不消耗武器框
- **WHEN** 玩家执行一阶灵力核心仪式配方
- **THEN** `danmaku_weapon` 不参与匹配且不会被消耗或复制

### Requirement: 二阶灵力核心由二阶造化产出
系统 SHALL 在源初造化之仪中提供 `zaohua_spirit_core_2` 配方，且 MUST NOT 提供普通工作台替代配方。该配方 SHALL 使用 `minTier:2`、`spCost:50000`，消耗 `ritual_stone_2×1 + star_silver×2 + tide_crystal×1 + sukima_fragment×1`，产出 `spirit_core_2×1`。该配方的生存可达性 SHALL 依赖现有中阶材料链，但本能力 MUST NOT 顺带定义 `sukima_fragment` 的来源。

#### Scenario: 制作二阶灵力核心
- **WHEN** 玩家在 2 阶源初造化之仪摆齐二阶核心原料并完成合成
- **THEN** 系统消耗 50,000 灵力与一份原料，产出容量 7,200,000、注灵速率 64,000/s 的二阶灵力核心

#### Scenario: 中阶信物来源不由本能力修复
- **WHEN** 玩家因尚无隙间碎片而无法满足二阶配方
- **THEN** 配方保持正确但不可执行，本变更不新增其它替代信物来源

### Requirement: 灵力核心指导书按物品展示配方
系统 SHALL 为 `spirit_core_0`、`spirit_core_1`、`spirit_core_2` 分别建立 `gensokyou:items` 类别的 Patchouli 物品词条。其中 `spirit_core_1`、`spirit_core_2` 通过完整 `gensokyou:zaohua_spirit_core_N` 配方 ID 展示各自源初造化配方，`spirit_core_0` 通过 `patchouli:crafting` 展示工作台配方。源初造化仪式指导词条 MUST NOT 重复列出这些配方。

#### Scenario: 灵力核心词条显示准确配方
- **WHEN** 玩家打开任一灵力核心 0～2 阶的指导书词条
- **THEN** 页面显示该品阶自身的原料与产物；仪式产物另显示结构等级与灵力费用，工作台产物不显示灵力费用

#### Scenario: 零阶核心词条不指向仪式配方
- **WHEN** 玩家打开零阶灵力核心词条
- **THEN** 第二页为工作台配方页，且不存在指向 `zaohua_spirit_core_0` 的页面

#### Scenario: 配方排序不影响物品词条
- **WHEN** 源初造化仪式配方列表顺序改变
- **THEN** 灵力核心物品词条仍按配方 ID 显示正确配方

### Requirement: 灵力核心保持独立供能边界
本变更新增的灵力核心配方 SHALL 只定义生存获取，不得把灵力核心加入弹幕主武器三槽，不得改变玩家灵力池作为弹幕发射消耗来源的规则，也不得改变仪式核心槽、产灵回流、归元托管或八方归元的容量计算。

#### Scenario: 灵力核心仍插入仪式核心槽
- **WHEN** 玩家将新制的一阶灵力核心放入支持核心槽的仪式
- **THEN** 仪式按该物品既有容量与速率接受供灵或注灵，物品数据组件行为不变

#### Scenario: 武器仍消耗玩家灵力池
- **WHEN** 玩家使用已装核的弹幕主武器发射
- **THEN** 扣费仍来自玩家灵力池，不扣除任何 `spirit_core_*` 物品内储能

#### Scenario: 灵力核心不能装入武器
- **WHEN** 玩家尝试把任意 `spirit_core_*` 放入装配台三个武器核槽
- **THEN** 装配台拒绝该物品且不改变武器槽位数据

### Requirement: 三至五阶灵力核心仍后置
系统 MUST NOT 在本变更中新增 `spirit_core_3`、`spirit_core_4` 或 `spirit_core_5` 的普通合成或仪式产出配方。

#### Scenario: 高阶核心仍仅由开发入口取得
- **WHEN** 开发人员通过创造栏或测试环境取得三至五阶灵力核心
- **THEN** 既有物品行为可用，但生存玩家无本变更新增的获取路径
