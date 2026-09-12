# ritual-core-interface Delta Spec

## MODIFIED Requirements

### Requirement: 启停操作
JSON 声明 toggleable 的仪式，界面 SHALL 渲染启动/停止按钮；点击经服务端权威校验后生效——启动流程 SHALL 包含祭品门槛、行为前置与配方解析（有配方的仪式必须匹配到可用配方），任一失败给出原因提示且不生效，状态变更 SHALL 同步回界面。toggleable=false 的仪式 SHALL NOT 出现按钮，且按钮显隐 SHALL 自界面首帧起即正确——界面创建时不得出现任何按钮短暂可见再消失的闪烁。

#### Scenario: 服务端拒绝
- **WHEN** 门槛未满足或无可匹配配方时点击启动
- **THEN** 服务端拒绝并回显原因（如缺供清单/无匹配配方），状态保持停止

#### Scenario: 不可开关仪式
- **WHEN** 打开未声明 toggleable（缺省 false）的仪式界面
- **THEN** 自打开的第一帧起即无启停按钮，仅展示信息与清单，全程无按钮闪现

#### Scenario: 可开关仪式按钮呈现
- **WHEN** 打开 toggleable=true 的仪式界面（如加具土命之焰）
- **THEN** 启停按钮在信息推送到达后正常显示（首帧缺失可接受，不得闪烁错位）

## ADDED Requirements

### Requirement: 行为驱动的界面扩展位
仪式框架 SHALL 支持行为侧向界面注入两类扩展：单物品输出/功能槽位（真菜单槽，内容经菜单协议自动同步、服务端权威校验物品类型）与逐 tick 同步的状态数值（如燃烧倒计时进度），使持续型仪式无需一次性快照轮询即可呈现实时状态；具体显示语义由各仪式能力 spec 定义（如 `kagutsuchi-flame-ritual`）。

#### Scenario: 槽位仅收指定物品
- **WHEN** 玩家向仪式界面的输出槽拖入不兼容物品
- **THEN** 槽位拒绝放入，物品回到原处

#### Scenario: 实时数值经菜单协议同步
- **WHEN** 行为更新其注入的倒计时数值
- **THEN** 打开中的界面在下一 tick 内反映新值，无需额外推送
