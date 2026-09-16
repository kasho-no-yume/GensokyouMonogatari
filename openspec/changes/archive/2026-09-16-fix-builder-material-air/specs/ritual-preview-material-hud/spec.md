## MODIFIED Requirements

### Requirement: 缺口数量计算
某方块的缺少数量 SHALL 为 `max(0, 需求格数 − 玩家背包持有数)`；持有数 SHALL 按该方块对应物品精确匹配、遍历全部 36 格背包槽位，与搭建扣料口径一致。HUD 仅显示实际会放置的方块类型（`resolveState` 成功解析者）。

**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽）SHALL 特殊处理：其缺口恒为 0、SHALL NOT 出现在 HUD 中，HUD 的门控（"至少一种缺口 > 0"）SHALL NOT 因其点亮。持有数统计 SHALL NOT 依赖 `countItem(Items.AIR)`（该调用恒为 0，会把此类方块误判为永久缺料）。

#### Scenario: 摆放方块缺口当帧缩减
- **WHEN** HUD 显示"仪式石 ×5"期间玩家手动放下一块仪式石（该格转为已满足）
- **THEN** 缺口当帧变为 ×4，无任何服务端交互

#### Scenario: 持有充足的方块不上榜
- **WHEN** 某方块需求 4、持有 10
- **THEN** 该行不出现在 HUD 中

#### Scenario: 无物品方块不产生缺口
- **WHEN** 预览中某待放置格解析为 `potted_dead_bush`（无物品形态）且玩家背包无任何盆栽
- **THEN** 该方块不出现在 HUD 缺口列表，且当其余方块缺口全为 0 时 HUD 整体不显示
