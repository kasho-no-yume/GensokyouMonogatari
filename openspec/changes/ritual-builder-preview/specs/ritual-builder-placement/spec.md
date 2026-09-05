# ritual-builder-placement Delta

## MODIFIED Requirements

### Requirement: 右键核心触发搭建
手持已选仪式的构建器非潜行右键 `ritual_core` SHALL 走两段式确认（规则见 `ritual-builder-preview`）：与服务端预览态全等匹配的第二击才执行一键搭建，首击仅置入预览；构建器 SHALL 走物品 `useOn` 路径，SHALL NOT 改动 `RitualCoreBlock`/`RitualMatcher` 逻辑。右键非核心方块 SHALL NOT 搭建。搭建算法本体（冲突零容忍、尽力放置）SHALL 保持不变。

#### Scenario: 材料齐全一次成型
- **WHEN** 玩家背包材料充足，持构建器对裸核心完成两段式确认
- **THEN** 全部待放置格按规范序放置完毕，核心周期重扫后识别仪式成型

#### Scenario: 部分结构续搭
- **WHEN** 核心周围已有部分正确方块，玩家两段式确认后
- **THEN** 已满足谓词的格位跳过不消耗，仅补齐缺口格

#### Scenario: 第二击时出现新冲突
- **WHEN** 预览后、第二击前有玩家往目标格塞了方块
- **THEN** 搭建按冲突零容忍中止并下发红框（现有链路），预览清除
