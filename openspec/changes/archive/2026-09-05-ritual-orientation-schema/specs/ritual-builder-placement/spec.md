# ritual-builder-placement Delta

## ADDED Requirements

### Requirement: 带朝向格位的放置
搭建时，图案条目声明了 `orientation` 的格位 SHALL 放置应用朝向后的 `BlockState`（种类解析规则不变：EXACT 用固定方块、TAG 按所选品阶实例化；朝向经 kind 映射写入对应方块属性）；冲突预检与"已满足"判定 SHALL 采用含朝向的同一谓词（朝向不符的已放置方块视为冲突格，走红框中止路径）。条目不带 `orientation` 时 SHALL 维持 `defaultBlockState()` 现状行为。格位方块不具备所声明 kind 的属性（TAG 运行期才可知）时 SHALL 跳过该格并计入"无法放置"，SHALL NOT 崩溃。

#### Scenario: 楼梯按朝向放置
- **WHEN** 图案某格第 5 位为 `5`（north_top），玩家背包有对应楼梯且该格为空气
- **THEN** 放置出的楼梯 `HORIZONTAL_FACING=north`、`HALF=top`

#### Scenario: 朝向错误视为冲突
- **WHEN** 目标格已被同种但朝向不符的楼梯占据
- **THEN** 计入冲突，整体中止并下发红框

#### Scenario: 无朝向条目行为不变
- **WHEN** 搭建既有 7 图案（全部条目无 `orientation`）
- **THEN** 放置结果与变更前逐格一致
