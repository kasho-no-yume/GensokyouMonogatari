# npc-dialogue Specification

## ADDED Requirements

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
