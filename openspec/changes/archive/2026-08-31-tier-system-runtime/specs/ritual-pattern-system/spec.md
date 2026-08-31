# Delta: ritual-pattern-system

## ADDED Requirements

### Requirement: 品阶方块与仪式等级推导
仪式石与祭品台 SHALL 按品阶 0-5 以独立方块存在（`ritual_stone_0..5`、`ritual_pedestal_0..5`），并 SHALL 分别以方块标签（`#gensokyou:ritual_stones`、`#gensokyou:ritual_pedestals`）纳入全部品阶；palette 谓词 SHALL 同时支持两种写法——标签（该位任意品阶）与精确方块名（该位严格指定品阶），无需新谓词语法。匹配成功后 SHALL 从匹配结果推导**仪式等级**：取结构内全部仪式石/祭品台方块品阶的最大值；推导 SHALL 不依赖匹配器遍历逻辑（后处理即可）。

#### Scenario: 标签通配任意品阶
- **WHEN** 某 palette 键声明 `#gensokyou:ritual_stones` 且该位放置 3 级仪式石
- **THEN** 结构匹配成功

#### Scenario: 精确指定品阶
- **WHEN** 某 palette 键声明 `gensokyou:ritual_stone_2` 且该位放置 1 级仪式石
- **THEN** 结构匹配失败；放置 2 级仪式石则成功

#### Scenario: 等级取最高
- **WHEN** 匹配结构内同时存在 0 级、1 级仪式石与 2 级祭品台
- **THEN** 该次匹配推导的仪式等级为 2

#### Scenario: 现有仪式行为不变
- **WHEN** 以任意品阶方块按现有 8 个仪式 JSON 搭建结构
- **THEN** 全部照常成型（当前仪式无品阶门槛，等效 ≥0）
