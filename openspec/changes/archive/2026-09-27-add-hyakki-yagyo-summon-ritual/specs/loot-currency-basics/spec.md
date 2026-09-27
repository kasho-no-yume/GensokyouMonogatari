## MODIFIED Requirements

### Requirement: 合成配方最小集
SHALL 提供仪式石、仪式核心、祭品台的合成配方（JSON 数据驱动），材料以本阶段产出物为主。召唤催化剂 SHALL NOT 再提供合成配方（该物品已随 `flandre-boss-low-tier` 的催化剂链路退役而删除）；作为替代，召唤类仪式的祭品需求 SHALL 由 `ritual_recipes` 数据承载（见 `hyakki-yagyo-summon`）。

#### Scenario: 闭环可达
- **WHEN** 玩家从零开始（无创造物品）积累材料
- **THEN** 可合成出百鬼夜行结构所需的全部方块与祭品台，其祭品原料可经既有产出链获取
