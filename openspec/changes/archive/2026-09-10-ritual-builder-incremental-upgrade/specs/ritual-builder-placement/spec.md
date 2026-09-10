# ritual-builder-placement Delta

## MODIFIED Requirements

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
