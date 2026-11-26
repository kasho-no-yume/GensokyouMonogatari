# quest-navigation-hud Specification

## Purpose

为每位玩家当前追踪任务的导航目标显示开放世界式屏幕方向/距离提示。

## ADDED Requirements

### Requirement: 固定尺寸且不受遮挡的屏幕空间导航标记

导航标记 SHALL 使用屏幕空间 HUD 表现目标方向与距离，不得实现为贴在世界目标上的实体/几何 billboard。标记尺寸 SHALL 固定，不随目标距离缩放；标记 MUST NOT 被方块、实体或世界深度遮挡。

#### Scenario: 目标被墙遮挡

- **WHEN** 追踪目标位于视野内但被方块挡住
- **THEN** 方向标记和距离 SHALL 仍清晰显示在 HUD 上

#### Scenario: 远近尺寸一致

- **WHEN** 同一导航目标从远处逐渐接近
- **THEN** 标记的逻辑屏幕尺寸 MUST 保持不变，只有方向/距离读数变化

### Requirement: 标记表达方向与距离

HUD SHALL 根据玩家视点与导航目标显示方向提示及以方块为单位的三维直线距离。目标位于屏幕外或相机后方时，方向提示 SHALL 钳制到屏幕边缘并指向目标方向。HUD SHALL 只渲染当前玩家正在追踪的任务当前阶段的一个导航目标。

#### Scenario: 目标在视野外

- **WHEN** 当前追踪目标位于相机视野之外
- **THEN** HUD SHALL 在屏幕边缘显示朝向目标的固定尺寸提示和距离

#### Scenario: 多个任务有目的地

- **WHEN** 玩家接受了多个配置导航目标的任务
- **THEN** HUD MUST 只为当前唯一追踪任务显示一个目标标记

#### Scenario: 阶段切换目标

- **WHEN** 被追踪任务进入配置了不同目标的新阶段
- **THEN** HUD SHALL 切换到新阶段的导航目标，不继续指向旧阶段位置

#### Scenario: 完成目标后返回领奖者

- **WHEN** 被追踪任务完成全部目标进入待回报
- **THEN** 导航 SHALL 自动指向奖励回报 NPC；即使材料交给了其他 NPC，也 MUST 指向实际领奖者，成功领奖后 SHALL 清除该任务的导航

### Requirement: 追踪与目标解析状态同步

追踪选择与服务端任务阶段 SHALL 决定 HUD 目标。取消追踪、任务回报完成、目标失效或任务阶段无导航目标时，旧标记 MUST 在下一次有效状态同步后隐藏。导航目标处于其他维度时，系统 MUST NOT 显示虚假的跨维度方向；任务界面 SHALL 能告知目标维度。

#### Scenario: 取消追踪后隐藏

- **WHEN** 玩家取消追踪或追踪任务完成
- **THEN** HUD SHALL 隐藏之前的方向提示

#### Scenario: 无导航目标

- **WHEN** 当前阶段未配置导航目标
- **THEN** HUD MUST NOT 显示上一个阶段残留目标或默认方向

#### Scenario: 目标位于其他维度

- **WHEN** 玩家与目标不在同一维度
- **THEN** HUD MUST NOT 计算跨维度箭头，任务簿 SHALL 显示目标所在维度
