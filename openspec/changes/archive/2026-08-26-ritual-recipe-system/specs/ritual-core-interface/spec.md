# ritual-core-interface Specification (delta)

## ADDED Requirements

### Requirement: 可用配方清单
界面 SHALL 展示当前等级下该仪式的全部可用配方：名称（语言键回退 id）、满足状态（✓/✗）；✗ 时 SHALL 给出台面缺项或多余物品的摘要。运行中的仪式 SHALL 显示当前激活配方。

#### Scenario: 缺料摘要
- **WHEN** 台面缺少某配方的部分原料
- **THEN** 该配方条目显示 ✗ 并列出所缺物品与数量

#### Scenario: 激活配方显示
- **WHEN** 仪式处于运行态且由配方驱动
- **THEN** 界面标明当前执行的配方名称

## MODIFIED Requirements

### Requirement: 启停操作
JSON 声明 toggleable 的仪式，界面 SHALL 渲染启动/停止按钮；点击经服务端权威校验后生效——启动流程 SHALL 包含祭品门槛、行为前置与配方解析（有配方的仪式必须匹配到可用配方），任一失败给出原因提示且不生效，状态变更 SHALL 同步回界面。toggleable=false 的仪式 SHALL NOT 出现按钮。

#### Scenario: 服务端拒绝
- **WHEN** 门槛未满足或无可匹配配方时点击启动
- **THEN** 服务端拒绝并回显原因（如缺供清单/无匹配配方），状态保持停止

#### Scenario: 不可开关仪式
- **WHEN** 打开 toggleable=false 的仪式界面
- **THEN** 无启停按钮，仅展示信息与清单
