## ADDED Requirements

### Requirement: 高阶仪式石配方
系统 SHALL 为 `ritual_stone_2` 提供稳定的正式配方，SHALL NOT 使其依赖临时占位配方。系统 SHALL 为 `ritual_stone_3`、`ritual_stone_4`、`ritual_stone_5` 各提供至少一条配方。

本条在占位阶段仅声明需求存在，SHALL NOT 视为已实现。配方归属（源初造化之仪 / 其他仪式 / 工作台）与每阶的世界进度门槛由实施时定义。

在配方落地前，依赖 `#ritual_stones_3_plus` 及以上标签的仪式结构 SHALL 视为内容尚未开放，而非「材料不足以建造」。

#### Scenario: 需求已声明未实现
- **WHEN** 审查 `ritual_stone_3` / `4` / `5` 的生产途径
- **THEN** 存在明确的需求记录，但尚未有可执行的配方

#### Scenario: 2 阶配方不得为占位
- **WHEN** 审查 `ritual_stone_2` 的配方来源
- **THEN** 其配方为设计意图所固化者，SHALL NOT 依赖临时占位

#### Scenario: 高阶仪式未开放
- **WHEN** 玩家尝试建造 `ritual_stones_3_plus` 标签的仪式
- **THEN** 该仪式被视为内容未开放，SHALL NOT 呈现为可建造但材料不足
