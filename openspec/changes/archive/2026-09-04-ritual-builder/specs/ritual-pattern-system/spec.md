## ADDED Requirements

### Requirement: 仪式品阶集合声明
仪式图案 JSON SHALL 支持可选字段 `tiers`：一个 0-5 的整数数组，声明该仪式在构建器下允许实例化标签格位（`#ritual_stones`/`#ritual_pedestals`）的品阶集合。缺省（字段不存在）时 SHALL 等效全部品阶 `[0,1,2,3,4,5]`，保证既有图案无需改动即向后兼容。数组为空或含越界值（<0 或 >5）时 loader SHALL 拒载该文件并在日志报明原因。

#### Scenario: 缺省全阶
- **WHEN** 某仪式 JSON 不含 `tiers` 字段
- **THEN** 其允许品阶为全部 0-5，构建器菜单显示 6 个品阶按钮

#### Scenario: 限定子集
- **WHEN** 某仪式 JSON 声明 `"tiers": [2,3,4]`
- **THEN** 构建器菜单仅显示品阶 2/3/4 三个按钮，默认选中 2

#### Scenario: 越界拒载
- **WHEN** 某仪式 JSON 声明 `"tiers": [0,7]`
- **THEN** 该文件被拒绝加载，日志指出品阶越界
