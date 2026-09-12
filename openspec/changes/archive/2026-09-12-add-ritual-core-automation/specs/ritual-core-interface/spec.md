# ritual-core-interface Delta Spec

## MODIFIED Requirements

### Requirement: 祭品要求核对清单
requirements 非空的仪式，界面 SHALL 按 slot 规范序逐项列出祭品要求：所需物品图标与满足状态（✓/✗）；单条要求恒为 1 件（见 `ritual-offerings`），清单 SHALL NOT 显示数量；物品不足时 SHALL 能直观看出缺哪一台缺什么。

#### Scenario: 缺供定位
- **WHEN** 四台供品中第三台缺失
- **THEN** 清单第三项显示 ✗ 及所需物品图标，其余项为 ✓

### Requirement: 启停操作
JSON 声明 toggleable 的仪式，界面 SHALL 渲染启动/停止按钮；点击经服务端权威校验后生效——启动流程 SHALL 包含祭品门槛、行为前置与配方解析（有配方的仪式必须匹配到可用配方），任一失败给出原因提示且不生效，状态变更 SHALL 同步回界面。toggleable=false 的仪式 SHALL NOT 出现按钮，且按钮显隐 SHALL 自界面首帧起即正确——界面创建时不得出现任何按钮短暂可见再消失的闪烁。

#### Scenario: 服务端拒绝
- **WHEN** 门槛未满足或无可匹配配方时点击启动
- **THEN** 服务端拒绝并回显原因（如缺供清单/无匹配配方），状态保持停止

#### Scenario: 不可开关仪式
- **WHEN** 打开 toggleable=false 的仪式界面（如加具土命之焰）
- **THEN** 自打开的第一帧起即无启停按钮，仅展示信息与清单，全程无按钮闪现

#### Scenario: 可开关仪式按钮呈现
- **WHEN** 打开 toggleable=true 的仪式界面
- **THEN** 启停按钮在信息推送到达后正常显示（首帧缺失可接受，不得闪烁错位）
