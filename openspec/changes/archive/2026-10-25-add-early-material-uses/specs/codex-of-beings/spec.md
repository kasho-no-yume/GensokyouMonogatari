## ADDED Requirements

### Requirement: 妖精可被众生典籍收录

`gensokyou:fairy`（小妖精）SHALL 可被众生典籍收录；MUST NOT 因为其为
`TouhouMonster` 而排除。典籍的排除集合仍只包含 boss、黑名单与东方 NPC。

收容小妖精 SHALL 与收容普通 mob 同流程：右键移除、无掉落、按 `gensokyou:fairy`
累计计数、满 20 进入满态。大妖精（boss）SHALL 继续被排除。

#### Scenario: 小妖精可收录

- **WHEN** 玩家用众生典籍右键一只小妖精
- **THEN** 小妖精被收容，典籍记录 `gensokyou:fairy` 且计数 +1

#### Scenario: 大妖精仍不可收录

- **WHEN** 玩家用众生典籍右键大妖精
- **THEN** 不收容，典籍状态不变

#### Scenario: 满 20 可镇魂

- **WHEN** 典籍记录 20 只小妖精并放入镇魂返照之仪
- **THEN** 该典籍被识别为有效 species
