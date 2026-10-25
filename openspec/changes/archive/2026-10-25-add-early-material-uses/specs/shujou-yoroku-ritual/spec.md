## ADDED Requirements

### Requirement: 妖精实体战利品表

系统 SHALL 为 `gensokyou:fairy` 提供实体战利品表
`data/gensokyou/loot_table/entities/fairy.json`，内容与原代码掉落一致：

- 1 × `gensokyou:memory_fragment`（必掉）；
- 10% × `gensokyou:ppoint`；
- 10% × `gensokyou:bpoint`。

众生余录（`shujou_yoroku_circle`）按 `EntityType#getDefaultLootTable` 掷骰时，
该表 SHALL 被解析并产出上述物品；MUST NOT 因表缺失而静默零产出。

实际击杀小妖精仍走实体代码掉落；新增战利品表 MUST NOT 造成实际击杀的双倍掉落。

#### Scenario: 镇魂 20 只妖精有产出

- **WHEN** 众生余录祭品台上有一本记录满 20 只 `gensokyou:fairy` 的典籍且灵力足额
- **THEN** 结算至少产出 1 个记忆残页，并按概率产出 P/B 点

#### Scenario: 实际击杀不双掉

- **WHEN** 玩家直接击杀一只小妖精
- **THEN** 掉落与既有代码一致，不因新增战利品表额外多掉一份

#### Scenario: 表缺失不再静默

- **WHEN** 审查 `gensokyou:fairy` 的默认战利品表
- **THEN** 表文件存在且包含记忆残页/P/B 点条目，不是空表
