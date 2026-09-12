# ritual-editor-anchor Specification

## Purpose
TBD - created by archiving change ritual-wand-dev-editor. Update Purpose after archive.
## Requirements
### Requirement: 编辑杖创造模式硬闸
仪式编辑杖（由构造杖重构）全部编辑功能（锚定、菜单、力建、捕获、保存）SHALL 仅对 `hasInfiniteMaterials()`（创造模式）玩家生效；非创造玩家右键核心 SHALL NOT 锚定、SHALL NOT 打开菜单，仅回显不可用提示。权限校验 SHALL 位于服务端每一处 C2S 动作入口，UI 层隐藏 SHALL NOT 被视为防御。

#### Scenario: 生存模式拒绝
- **WHEN** 生存模式玩家手持编辑杖右键任意仪式核心
- **THEN** 不发生锚定、不打开界面，回显"仅创造模式可用"提示

#### Scenario: 伪造包被拒
- **WHEN** 非创造玩家向服务端发送力建/捕获/保存的 C2S payload
- **THEN** 服务端丢弃该请求，可回显权限提示

### Requirement: 右键核心纯锚定
创造模式玩家持编辑杖非潜行右键 `ritual_core` SHALL 仅将该核心坐标+维度写入杖上锚定组件（锚定编辑位置），无论该核心所在仪式成型与否；SHALL NOT 搭建、SHALL NOT 打开核心界面、SHALL NOT 产生任何方块变更。右键非核心方块 SHALL NOT 锚定。潜行右键（任意目标）SHALL 打开编辑菜单（复用构建杖零槽菜单握手范式）。

#### Scenario: 成型核心也只锚定
- **WHEN** 创造玩家持编辑杖右键一个已成型仪式的核心
- **THEN** 杖组件记录该坐标与维度，核心界面不打开，世界无任何变化

#### Scenario: 重复锚定改锚
- **WHEN** 已锚定玩家再右键另一仪式核心
- **THEN** 组件更新为新锚点，旧锚定状态丢弃

### Requirement: 编辑会话状态持久于杖组件
锚定点、当前维度、所选仪式+阶级（复用 `BuilderSelection` 结构）、以及**按阶级分别记忆的工作区参数（长/宽/高/y偏移）** SHALL 全部存于编辑杖的 Data Component；工作区为锚点相对量，改锚后各阶级参数照常可用；关服重启后杖上状态 SHALL 完整保留。

#### Scenario: 工作区按阶级独立
- **WHEN** 玩家为同一仪式的阶级 0 与阶级 2 分别调整工作区尺寸
- **THEN** 两份参数互不覆盖，切换到对应阶级时各自生效

#### Scenario: 重启恢复会话
- **WHEN** 玩家锚定并配好工作区后重进世界
- **THEN** 杖上锚定、选择与工作区参数完整恢复

### Requirement: 锚定惰性失效
每次编辑动作（开菜单后的力建/捕获/保存执行时）服务端 SHALL 校验锚定点仍为 `ritual_core` 且玩家处于同维度；不满足 SHALL 拒绝动作并回显"锚定失效，请重新右键核心"，SHALL NOT 崩溃。

#### Scenario: 核心被拆后动作被拒
- **WHEN** 玩家锚定后拆除该核心，再触发力建确认
- **THEN** 动作被拒并提示锚定失效，世界无变化

