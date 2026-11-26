# npc-dialogue Specification

## Purpose
NPC 对话系统：节点图数据模型、服务端权威的选择回报流、长文本分页 + 选项的对话界面、对话内开启交易的衔接。本轮为代码定义图，datapack 加载器与主线剧情脚本为后续增量。
## Requirements
### Requirement: 对话图数据模型
对话 SHALL 以节点图组织：`DialogueGraph{ id, nodes, entry }` / `DialogueNode{ 多行文本, 选项列表 }` / `DialogueOption{ 标签, 下一节点(可空=结束), 动作(可空) }`；数据结构 SHALL 以 record + Codec 定义，形状与未来 datapack JSON 一一对应（本轮为代码定义图，无数据包加载器）。

#### Scenario: 选项跳转
- **WHEN** 玩家点击指向另一节点的选项
- **THEN** 服务端推进到目标节点并同步新文本与选项

#### Scenario: 选项结束对话
- **WHEN** 玩家点击 `next` 为空的选项
- **THEN** 对话界面关闭，服务端会话销毁

### Requirement: 服务端权威流程
对话 SHALL 服务端权威：开启与推进均由服务端下发（S2C 同步包：实体、节点文本、选项）；玩家选择经 C2S 包回报，服务端 SHALL 校验会话存在与选项索引合法，非法请求拒绝；界面关闭 SHALL 经 C2S 关闭包回报，服务端 SHALL 校验实体匹配后销毁会话；会话 SHALL 在界面关闭或玩家远离 NPC 后失效。

#### Scenario: 伪造选择被拒绝
- **WHEN** 客户端发送不存在会话或越界选项索引的回报包
- **THEN** 服务端拒绝执行且无副作用

#### Scenario: 关闭销毁会话
- **WHEN** 玩家 ESC 关闭对话界面
- **THEN** 客户端发送关闭包，服务端销毁会话，后续对该会话的选择回报被拒绝

#### Scenario: 远离失效
- **WHEN** 对话期间玩家远离 NPC 超过交互距离
- **THEN** 会话失效，后续选择无效

### Requirement: 长对话与选项界面
对话界面 SHALL 支持多行长文本（自动换行分页、超长可滚动）与 1..N 个选项按钮；ESC SHALL 关闭对话；风格与原版 UI 一致。

#### Scenario: 长文本分页
- **WHEN** 节点文本超过一页容量
- **THEN** 界面分页/滚动展示全部文本且不截断

#### Scenario: 多选项呈现
- **WHEN** 节点包含 2 个以上选项
- **THEN** 选项按序以按钮呈现，点击即回报服务端

### Requirement: 对话内衔接交易
对话动作 SHALL 支持 `OPEN_TRADE`：服务端收到选择后 SHALL 关闭对话会话并打开该 NPC 的交易界面。

#### Scenario: 对话转交易
- **WHEN** 玩家在对话中点击带交易动作的选项
- **THEN** 对话关闭，打开该 NPC 的原版交易界面

### Requirement: 任务对话确认与会话修订校验

对话图选项 MAY 包含任务动作，任务目标 SHALL 绑定稳定 NPC 角色 ID 及动作 ID。系统 SHALL 仅在玩家点击对应选项、服务端会话与当前 NPC 身份/维度/距离/节点修订号均有效时推进目标。打开对话、否定或取消 MUST NOT 完成目标。同一次按钮事件 MUST NOT 被复用于后续阶段；延迟的旧选项请求及旧关闭请求 MUST NOT 改变新节点或新会话。

#### Scenario: 对话准备确认

- **WHEN** 当前阶段要求向指定角色确认准备开始，玩家在该角色的对话中点击绑定的“是”按钮
- **THEN** 服务端 SHALL 推进该阶段的对话目标；打开对话或点击“否” SHALL 保持原进度

#### Scenario: 重复按钮请求

- **WHEN** 同一个节点修订号的按钮请求被重复或延迟发送
- **THEN** 该请求 SHALL 至多执行一次，后续阶段 MUST NOT 因旧请求而推进

#### Scenario: NPC 恢复或交互过期

- **WHEN** NPC 已被恢复为新 UUID 的复制体，或玩家死亡、换维度、离开交互距离
- **THEN** 原会话 SHALL 失效，旧按钮 MUST NOT 操作复制体或改变任务

### Requirement: NPC 任务操作按玩家生成

NPC 对话 SHALL 按玩家生成可接取、材料交付、奖励领取及任务对话入口，且支持分页浏览。收件 NPC 与回报 NPC 不同时 SHALL 分别显示交付与领奖入口；原有对话与交易入口 SHALL 保持可访问。

#### Scenario: 异地材料交付

- **WHEN** 阿求发布的委托要求向灵梦交付材料
- **THEN** 灵梦的任务对话 SHALL 提供该玩家的交付按钮，目标完成后阿求 SHALL 提供领奖按钮
