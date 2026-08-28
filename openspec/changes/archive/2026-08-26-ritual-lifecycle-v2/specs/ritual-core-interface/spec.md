# ritual-core-interface Specification (delta)

## ADDED Requirements

### Requirement: 界面开启方式
玩家右键已成型的仪式核心 SHALL 打开仪式界面——无论空手或持物，无例外；显示内容可由各仪式扩展。结构未成型时右键 SHALL 给出提示并维持既有物品链。潜行右键 SHALL 保留原链路（贴放方块 / 旧行为直连交互）。

#### Scenario: 成型必开
- **WHEN** 右键任一已成型仪式的核心（含手持物品时）
- **THEN** 打开该仪式界面

#### Scenario: 未成型提示
- **WHEN** 右键未组成任何结构的仪式核心
- **THEN** 显示结构不完整提示，不打开界面，潜行时物品链正常

#### Scenario: 潜行让行
- **WHEN** 玩家潜行右键核心且手持方块
- **THEN** 不打开界面，执行原有物品链（如贴放方块）

### Requirement: 仪式自定义操作
行为 SHALL 可通过 uiActions 向界面注入自定义操作按钮（框架启停之外），点击经服务端 onUiAction 权威执行并刷新界面。

#### Scenario: 电容存取按钮
- **WHEN** 打开电容环界面并点击取出/存入按钮
- **THEN** 灵力按既有规则转移并在界面上看到数值刷新

### Requirement: 仪式信息展示
界面 SHALL 展示当前匹配的仪式名称、可达层级与运行状态（运行中/已停止），以及该核心的概要数据（如存储灵力、既有链接状态）。

#### Scenario: 信息核对
- **WHEN** 玩家打开运行中的发电机环界面
- **THEN** 显示发电机名称、层级、运行中状态与相关数值

### Requirement: 祭品要求核对清单
requirements 非空的仪式，界面 SHALL 按 slot 规范序逐项列出祭品要求：所需物品图标 ×数量 与满足状态（✓/✗）；物品不足时 SHALL 能直观看出缺哪一台缺什么。

#### Scenario: 缺供定位
- **WHEN** 四台供品中第三台缺失
- **THEN** 清单第三项显示 ✗ 及所需物品，其余项为 ✓

### Requirement: 启停操作
JSON 声明 toggleable 的仪式，界面 SHALL 渲染启动/停止按钮；点击经服务端权威校验后生效（启动走门槛+消耗流程，失败给出原因提示），状态变更 SHALL 同步回界面。toggleable=false 的仪式 SHALL NOT 出现按钮。

#### Scenario: 服务端拒绝
- **WHEN** 门槛未满足时点击启动
- **THEN** 服务端拒绝并回显原因（如缺供清单），状态保持停止

#### Scenario: 不可开关仪式
- **WHEN** 打开 toggleable=false 的仪式界面
- **THEN** 无启停按钮，仅展示信息与清单
