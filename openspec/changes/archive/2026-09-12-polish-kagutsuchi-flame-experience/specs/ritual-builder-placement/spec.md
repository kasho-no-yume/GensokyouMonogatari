# ritual-builder-placement Delta Spec

## MODIFIED Requirements

### Requirement: 标签谓词按品阶实例化
搭建时 TAG 谓词格 SHALL 实例化为所选品阶对应的方块（`ModBlocks.tierOf == 所选品阶`）；EXACT 谓词格 SHALL 使用谓词内固定方块。若标签内**无任何**受品阶方块（`tierOf` 恒为 -1，如单方块化的 `#gensokyou:ritual_pedestals`），SHALL 回退取标签内唯一成员方块，MUST NOT 因"找不到匹配品阶"而跳过该格。

#### Scenario: 标签格用品阶方块
- **WHEN** 选中品阶 2，某格谓词为 `#gensokyou:ritual_stones`
- **THEN** 该格放置 `ritual_stone_2`

#### Scenario: 无阶标签回退唯一成员
- **WHEN** 选中品阶 2，某格谓词为 `#gensokyou:ritual_pedestals`（标签仅含单方块 `ritual_pedestal`）
- **THEN** 该格放置 `ritual_pedestal`，不因品阶 2 无对应台子而跳过
