## MODIFIED Requirements

### Requirement: 动态材料 tooltip
构建器 tooltip SHALL 显示：所选仪式名、所选品阶，以及该仪式每种所需方块的"需求 ×N / 持有 ×M"；持有不足的行 SHALL 红色显示，充足 SHALL 正常色显示。未选择时 SHALL 显示操作提示。聚合键 SHALL 为方块而非物品。

对**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽），该行 SHALL 仍显示，但 SHALL 使用 `block.getName()` 作为名称、SHALL NOT 以 `countItem(Items.AIR)`（恒为 0）统计持有、不予标红、并 SHALL 标注"无物品形态"；SHALL NOT 显示为"空气"。

#### Scenario: 材料不足显示
- **WHEN** 玩家持有 3 个仪式石_2，构建器选中"生成仪式圈 · 品阶 2"（需 8 个）
- **THEN** tooltip 仪式石行显示"×8 / 持有 ×3"且为红色

#### Scenario: 持有数实时更新
- **WHEN** 玩家拾取更多仪式石后再次悬停构建器
- **THEN** tooltip 持有数随之增大

#### Scenario: 盆栽行不显示为空气
- **WHEN** 构建器选中含盆栽格的仪式，玩家悬停构建器
- **THEN** tooltip 中该行为盆栽方块名 + "无物品形态"，不为红字，且不存在名称为"空气"的行
