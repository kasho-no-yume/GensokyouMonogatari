# ritual-pattern-system Delta Spec

## MODIFIED Requirements

### Requirement: 品阶方块与仪式等级推导
仪式石 SHALL 按品阶 0-5 以独立方块存在（`ritual_stone_0..5`），并以方块标签 `#gensokyou:ritual_stones` 纳入全部品阶；祭品台 SHALL 为单一注册方块 `ritual_pedestal`（无品阶变体），标签 `#gensokyou:ritual_pedestals` 仅含此一方块。palette 谓词 SHALL 同时支持两种写法——标签（该位任意合格方块）与精确方块名，无需新谓词语法。匹配成功后 SHALL 从匹配结果推导**仪式等级**：取结构内全部**仪式石**方块品阶的最大值（祭品台不贡献品阶，其外观由该推导值反向驱动，见 `ritual-pedestal`）；推导 SHALL 不依赖匹配器遍历逻辑（后处理即可）。

#### Scenario: 石标签通配任意品阶
- **WHEN** 某 palette 键声明 `#gensokyou:ritual_stones` 且该位放置 3 级仪式石
- **THEN** 结构匹配成功

#### Scenario: 精确指定品阶
- **WHEN** 某 palette 键声明 `gensokyou:ritual_stone_2` 且该位放置 1 级仪式石
- **THEN** 结构匹配失败；放置 2 级仪式石则成功

#### Scenario: 等级取石之最高
- **WHEN** 匹配结构内同时存在 0 级、2 级仪式石与若干祭品台
- **THEN** 该次匹配推导的仪式等级为 2（祭品台不参与最大值计算）

#### Scenario: 现有仪式行为不变
- **WHEN** 以合格方块按仓库现有仪式 JSON 搭建结构
- **THEN** 全部照常成型（当前仪式无品阶门槛，等效 ≥0）

### Requirement: 仪式品阶集合声明
仪式图案 JSON SHALL 支持可选字段 `tiers`：一个 0-5 的整数数组，声明该仪式在构建器下允许实例化**受品阶标签格位**（`#ritual_stones` 系列）的品阶集合；祭品台标签 `#ritual_pedestals` 因单方块化不受该字段影响。缺省（字段不存在）时 SHALL 等效全部品阶 `[0,1,2,3,4,5]`，保证既有图案无需改动即向后兼容。数组为空或含越界值（<0 或 >5）时 loader SHALL 拒载该文件并在日志报明原因。

#### Scenario: 缺省全阶
- **WHEN** 某仪式 JSON 不含 `tiers` 字段
- **THEN** 其允许品阶为全部 0-5，构建器菜单显示 6 个品阶按钮

#### Scenario: 限定子集
- **WHEN** 某仪式 JSON 声明 `"tiers": [2,3,4]`
- **THEN** 构建器菜单仅显示品阶 2/3/4 三个按钮，默认选中 2

#### Scenario: 越界拒载
- **WHEN** 某仪式 JSON 声明 `"tiers": [0,7]`
- **THEN** 该文件被拒绝加载，日志指出品阶越界
