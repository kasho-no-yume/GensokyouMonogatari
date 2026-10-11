## ADDED Requirements

### Requirement: 实用工具的生存获取配方
灵力筑基器（`gensokyou:landscaping_tool`）与灵力引爆器（`gensokyou:spirit_bomb`）SHALL 各具备一条生存获取配方，且两配方 MUST NOT 依赖任何 T2 及以上材料、BOSS 掉落或碎符卡星。

- 灵力筑基器 SHALL 经原版工作台以 `crafting_shaped` 产出，3×3 排布 SHALL 为
  四角 `minecraft:iron_ingot`、四边中 `gensokyou:ritual_stone_0`、
  正中 `gensokyou:refined_cinnabar`（图案 `ISI / SCS / ISI`）。
- 灵力引爆器 SHALL 经源初造化之仪配方 `zaohua_spirit_bomb` 产出，该配方 SHALL 声明
  `mode:"activation"`、`match:"max"`、`minTier:1`、`spCost:20000`，原料 SHALL 为
  `gensokyou:ritual_stone_1×1 + gensokyou:spirit_iron×2 + gensokyou:refined_cinnabar×2
  + minecraft:gunpowder×4 + minecraft:amethyst_shard×2`，产出 `gensokyou:spirit_bomb×1`。

#### Scenario: 工作台可造筑基器
- **WHEN** 玩家在 3×3 工作台按配方摆放仪式石 0 / 铁锭 / 精炼辰砂
- **THEN** 产出 1 个灵力筑基器物品

#### Scenario: 一阶造化可造引爆器
- **WHEN** 玩家在 ≥1 阶源初造化之仪摆齐引爆器原料、灵力足额并触发合成
- **THEN** 消耗 20,000 灵力与一份原料，产出一个灵力引爆器

#### Scenario: 零阶造化不可造引爆器
- **WHEN** 玩家在 0 阶源初造化的祭品台摆齐引爆器原料并触发
- **THEN** 配方因 `minTier:1` 不匹配被拒绝执行，不消耗灵力与原料、不产出物品

#### Scenario: 入口配方不含高阶料
- **WHEN** 审查两工具配方的全部原料
- **THEN** 每一种均可在未击败任何 BOSS、且未取得任何 T2 材料的前提下取得
