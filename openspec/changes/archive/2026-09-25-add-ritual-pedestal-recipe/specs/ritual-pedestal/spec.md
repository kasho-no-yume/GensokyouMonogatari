## ADDED Requirements

### Requirement: 祭品台由工作台平滑石祭台配方产出
系统 SHALL 提供普通工作台有序合成配方 `gensokyou:ritual_pedestal`，图案为 `SSS / RPR / SSS`（`S`=`minecraft:smooth_stone`、`R`=`gensokyou:ritual_stone_0`、`P`=`gensokyou:ppoint`），产出 `ritual_pedestal×1`。该配方 MUST NOT 出现在任何 `ritual_recipes/*.json` 中，MUST NOT 声明 `spCost`、`minTier` 或任何灵力消耗，MUST NOT 依赖灵力核心、维度进度或已建成的仪式结构。

#### Scenario: 首个仪式前可造出祭品台
- **WHEN** 玩家尚未建成任何仪式、尚未获得任何灵力核心，仅有平滑石、圆石与 P 点
- **THEN** 玩家可在工作台完成 `SSS / RPR / SSS` 并获得一个祭品台

#### Scenario: 祭品台不需要灵力
- **WHEN** 玩家查看或执行该配方
- **THEN** 配方不消耗灵力，玩家的灵力池为空也能正常合成

#### Scenario: 祭品台不存在仪式产出路径
- **WHEN** 审查任一 `ritual_recipes/*.json`
- **THEN** 没有任何配方以 `ritual_pedestal` 为产物，祭品台只有本条工作台获取路径

#### Scenario: 配方原料全部为开局可得
- **WHEN** 审查配方原料
- **THEN** 原料仅为 `minecraft:smooth_stone`、`gensokyou:ritual_stone_0` 与 `gensokyou:ppoint`，不含需仪式或维度才能获得的材料

### Requirement: 祭品台指导书物品词条展示工作台配方
系统 SHALL 在 `gensokyou:items` 分类建立祭品台 Patchouli 物品词条 `item.gensokyou.ritual_pedestal`，包含 spotlight 说明页与 `patchouli:crafting` 配方页，指向 `gensokyou:ritual_pedestal` 工作台配方。该词条 MUST NOT 挂 `advancement` 或 `secret`，SHALL 常驻可见。

#### Scenario: 词条显示祭品台工作台配方
- **WHEN** 玩家在指导书中打开祭品台词条
- **THEN** 第二页显示 `SSS / RPR / SSS` 工作台配方与平滑石、仪式石、P 点三类原料

#### Scenario: 词条常驻可见
- **WHEN** 玩家尚未获得下界或末地进度
- **THEN** 祭品台词条仍可见且不显示为未解锁
