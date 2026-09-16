## MODIFIED Requirements

### Requirement: 材料不足尽力搭建
无冲突但材料不足时，SHALL 按规范序（列表既有 (y,z,x) 排序）逐格尝试放置：背包有对应物品则扣 1 放置，无则跳过该格继续后续格；SHALL NOT 降品阶、SHALL NOT 重排序补位。完成后 SHALL 回发"已放置 N/M 格"提示。

**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽）SHALL 直接放置、SHALL NOT 参与"背包有对应物品则扣 1"判定、SHALL NOT 因"背包无此物品"被跳过；扣料实现 SHALL NOT 以 `countItem(Items.AIR)`（恒为 0）作为供应判定。

#### Scenario: 半路缺料
- **WHEN** 需 8 个仪式石_2 但背包仅 3 个，无冲突
- **THEN** 规范序前 3 个仪式石格被放置并扣 3 个，其余格跳过，提示放置 3/N

#### Scenario: 创造模式免耗
- **WHEN** 创造模式玩家持构建器右键核心
- **THEN** 全部格位放置成功，背包物品数量不变

#### Scenario: 无物品方块不被跳过
- **WHEN** 生存模式玩家背包无任何盆栽，图案含 `potted_dead_bush` 格且无冲突
- **THEN** 该格照常放置出盆栽，且不消耗背包任何物品
