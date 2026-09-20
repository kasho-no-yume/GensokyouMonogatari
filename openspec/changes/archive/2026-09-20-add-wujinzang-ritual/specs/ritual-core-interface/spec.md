# ritual-core-interface Specification (delta: add-wujinzang-ritual)

## ADDED Requirements

### Requirement: 行为专属屏幕分派

核心右键界面 SHALL 允许行为指定专属屏幕。无尽藏之仪 SHALL 打开其专属宽屏终端（见 `wujinzang-storage-terminal`），其余仪式 SHALL 打开通用仪式面板。分派 SHALL 在服务端按匹配图案决定，客户端据菜单类型渲染对应屏幕；编辑杖与潜行例外沿既有规则。

#### Scenario: 无尽藏用专属屏幕
- **WHEN** 玩家右键成型无尽藏核心
- **THEN** 打开无尽藏终端

#### Scenario: 其余仪式用通用面板
- **WHEN** 玩家右键其他图案成型核心
- **THEN** 打开通用仪式面板

#### Scenario: 潜行仍让行
- **WHEN** 玩家潜行右键无尽藏核心且手持方块
- **THEN** 不打开终端，执行原物品链
