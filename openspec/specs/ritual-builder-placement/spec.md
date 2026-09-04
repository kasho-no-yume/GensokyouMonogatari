# ritual-builder-placement Specification

## Purpose
TBD - created by archiving change ritual-builder. Update Purpose after archive.
## Requirements
### Requirement: 右键核心触发搭建
手持已选仪式的构建器非潜行右键 `ritual_core` SHALL 触发一键搭建；构建器 SHALL 走物品 `useOn` 路径，SHALL NOT 改动 `RitualCoreBlock`/`RitualMatcher` 逻辑。右键非核心方块 SHALL NOT 搭建。

#### Scenario: 材料齐全一次成型
- **WHEN** 玩家背包材料充足，持构建器右键裸仪式核心
- **THEN** 全部待放置格按规范序放置完毕，核心周期重扫后识别仪式成型

#### Scenario: 部分结构续搭
- **WHEN** 核心周围已有部分正确方块，玩家再次右键构建器
- **THEN** 已满足谓词的格位跳过不消耗，仅补齐缺口格

### Requirement: 冲突零容忍全量中止
搭建前 SHALL 对全部目标格做冲突预检：任一目标位被"不满足该格谓词"的方块占据时，SHALL 中止整个搭建、SHALL NOT 放置任何一格、SHALL NOT 消耗任何材料，并触发冲突红框提示。

#### Scenario: 一格被占即全停
- **WHEN** 图案 8 格中仅 1 格被石头占据，其余全为空气且材料充足
- **THEN** 一格也不放置，材料不扣，被占格显示红色线框

#### Scenario: 正确方块不算冲突
- **WHEN** 目标位已被满足该格谓词的方块占据（如已摆对的仪式石）
- **THEN** 该格视为已满足、跳过，不计入冲突

### Requirement: 材料不足尽力搭建
无冲突但材料不足时，SHALL 按规范序（列表既有 (y,z,x) 排序）逐格尝试放置：背包有对应物品则扣 1 放置，无则跳过该格继续后续格；SHALL NOT 降品阶、SHALL NOT 重排序补位。完成后 SHALL 回发"已放置 N/M 格"提示。

#### Scenario: 半路缺料
- **WHEN** 需 8 个仪式石_2 但背包仅 3 个，无冲突
- **THEN** 规范序前 3 个仪式石格被放置并扣 3 个，其余格跳过，提示放置 3/N

#### Scenario: 创造模式免耗
- **WHEN** 创造模式玩家持构建器右键核心
- **THEN** 全部格位放置成功，背包物品数量不变

### Requirement: 标签谓词按品阶实例化
搭建时 TAG 谓词格 SHALL 实例化为所选品阶对应的方块（`ModBlocks.tierOf == 所选品阶`）；EXACT 谓词格 SHALL 使用谓词内固定方块。

#### Scenario: 标签格用品阶方块
- **WHEN** 选中品阶 2，某格谓词为 `#gensokyou:ritual_stones`
- **THEN** 该格放置 `ritual_stone_2`

### Requirement: 图案失效宽限
搭建时若选择组件内图案 id 已不存在（数据包变更），SHALL NOT 崩溃，SHALL 回发"仪式不存在，请重选"提示。

#### Scenario: 热重载后图案消失
- **WHEN** 玩家持选中已删除图案的构建器右键核心
- **THEN** 不放置任何方块，回发失效提示

