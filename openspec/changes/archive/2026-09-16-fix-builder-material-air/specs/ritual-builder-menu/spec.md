## MODIFIED Requirements

### Requirement: 材料需求与持有实时对比
菜单 SHALL 按解析后的具体**方块**聚合显示每种材料"需求 ×N / 持有 ×M"；持有数 SHALL 实时读取客户端玩家背包，不足项 SHALL 红色标注。聚合键 SHALL 为方块而非物品（物品仅为展示与持有统计的派生）。

对**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽），该行 SHALL 仍列出，但：图标 SHALL 以方块自身模型渲染、名称 SHALL 使用 `block.getName()`；持有数 SHALL 不统计（SHALL NOT 以 `countItem(Items.AIR)` 的恒 0 结果把此类方块判为缺料），SHALL NOT 标红，并 SHALL 在行内标注"无物品形态"。此类行 SHALL NOT 退化为"空气"。

#### Scenario: 打开菜单即见缺口
- **WHEN** 玩家背包仅有部分材料时打开菜单
- **THEN** 缺口材料行以红色显示，无需先尝试搭建

#### Scenario: 无物品方块行不显示为空气
- **WHEN** 图案含 `potted_dead_bush` 格，玩家打开菜单查看材料区
- **THEN** 该行以盆栽方块图标 + 方块名呈现并标注"无物品形态"，列表内不存在名称为"空气"的行

#### Scenario: 品阶切换不改变无物品方块行
- **WHEN** 玩家在菜单内切换品阶
- **THEN** 无物品方块行的需求格数随累积切片更新，仍不参与持有统计、不标红
