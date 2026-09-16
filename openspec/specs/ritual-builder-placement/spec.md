# ritual-builder-placement Specification

## Purpose
TBD - created by archiving change ritual-builder. Update Purpose after archive.
## Requirements
### Requirement: 右键核心触发搭建
手持已选仪式的构建器非潜行右键 `ritual_core` SHALL 走两段式确认（规则见 `ritual-builder-preview`）：与服务端预览态全等匹配的第二击才执行一键搭建，首击仅置入预览；右键成型核心的让位与分发规则见 `ritual-builder-upgrade`。构建/预览/材料计算的目标切片 SHALL 为图案中 `level ==` 所选阶级的累积切片（`sliceFor`），SHALL NOT 恒取最高阶切片；所选阶级在图案 `levels` 中无对应层级时 SHALL 回发非法选择提示，不置预览、不搭建、不崩溃。右键非核心方块 SHALL NOT 搭建。搭建算法本体（冲突零容忍、尽力放置、规范序）SHALL 保持不变。`RitualMatcher` 逻辑 SHALL NOT 改动；`RitualCoreBlock` 仅允许"成型核心对持杖右键让位"这一处改动（实现含 `useItemOn` 与主手补调的 `useWithoutItem` 两道守卫）。

#### Scenario: 材料齐全一次成型
- **WHEN** 玩家背包材料充足，持构建器对裸核心完成两段式确认
- **THEN** 全部待放置格按规范序放置完毕，核心周期重扫后识别仪式成型

#### Scenario: 各阶级建各阶形态
- **WHEN** 对裸核心选阶级 0 完成建造，再选阶级 2 对同一核心完成建造
- **THEN** 第一次成形后建筑仅为阶级 0 累积切片轮廓；第二次只补齐阶级 2 相对已满足格的差量格位，阶级 2 成形后为完整阶级 2 轮廓

#### Scenario: 部分结构续搭
- **WHEN** 核心周围已有部分正确方块，玩家两段式确认后
- **THEN** 已满足谓词的格位跳过不消耗，仅补齐缺口格

#### Scenario: 第二击时出现新冲突
- **WHEN** 预览后、第二击前有玩家往目标格塞了方块
- **THEN** 搭建按冲突零容忍中止并下发红框（现有链路），预览清除

#### Scenario: 所选阶级无对应层级
- **WHEN** 图案 `levels` 仅含 0..3，构建器选中阶级 5 右键核心
- **THEN** 回发非法选择提示，不放置任何方块

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

**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽）SHALL 直接放置、SHALL NOT 参与"背包有对应物品则扣 1"判定、SHALL NOT 因"背包无此物品"被跳过；扣料实现 SHALL NOT 以 `countItem(Items.AIR)`（恒为 0）作为供应判定。

#### Scenario: 半路缺料
- **WHEN** 需 8 个仪式石_2 但背包仅 3 个，无冲突
- **THEN** 规范序前 3 个仪式石格被放置并扣 3 个，其余格跳过，提示放置 3/N

#### Scenario: 创造模式免耗
- **WHEN** 创造模式玩家持构建器右键核心
- **THEN** 全部格位放置成功，背包物品数量不变

#### Scenario: 无物品方块不被跳过
- **WHEN** 生存模式玩家背包无任何盆栽，图案含 `potted_dead_bush` 格且无冲突
- **THEN** 该格照常放置出盆栽，且不消耗背包任何物品

### Requirement: 标签谓词按品阶实例化
搭建时 TAG 谓词格 SHALL 实例化为所选品阶对应的方块（`ModBlocks.tierOf == 所选品阶`）；EXACT 谓词格 SHALL 使用谓词内固定方块。若标签内**无任何**受品阶方块（`tierOf` 恒为 -1，如单方块化的 `#gensokyou:ritual_pedestals`），SHALL 回退取标签内唯一成员方块，MUST NOT 因"找不到匹配品阶"而跳过该格。

#### Scenario: 标签格用品阶方块
- **WHEN** 选中品阶 2，某格谓词为 `#gensokyou:ritual_stones`
- **THEN** 该格放置 `ritual_stone_2`

#### Scenario: 无阶标签回退唯一成员
- **WHEN** 选中品阶 2，某格谓词为 `#gensokyou:ritual_pedestals`（标签仅含单方块 `ritual_pedestal`）
- **THEN** 该格放置 `ritual_pedestal`，不因品阶 2 无对应台子而跳过

### Requirement: 图案失效宽限
搭建时若选择组件内图案 id 已不存在（数据包变更），SHALL NOT 崩溃，SHALL 回发"仪式不存在，请重选"提示。

#### Scenario: 热重载后图案消失
- **WHEN** 玩家持选中已删除图案的构建器右键核心
- **THEN** 不放置任何方块，回发失效提示

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

