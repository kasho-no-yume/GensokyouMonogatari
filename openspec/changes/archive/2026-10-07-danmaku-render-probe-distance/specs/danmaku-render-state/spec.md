## ADDED Requirements

### Requirement: 弹幕可见性上限 MUST 与客户端视距一致

弹幕实体的渲染上限半径 SHALL 直接由客户端视距给出：
`Minecraft.getInstance().options.renderDistance().get() * 16` 格。
覆盖过 `Entity#shouldRenderAtSqrDistance` 的弹幕使用 `max(原式, 视据)`。
服务端跟踪范围 `clientTrackingRange` SHALL 不成为这个上限的事实瓶颈——
弹幕的 `clientTrackingRange` MUST 大于等于最大可能视距对应的地图格数。

#### Scenario: 视距扩展时弹幕范围同步扩展

- **WHEN** 玩家将客户端视距从 8 调到 16
- **THEN** sphere 弹在 8×16=128 至 16×16=256 格区间内仍 SHALL 渲染，MUST NOT
  回到原版「AABB 平均 × 64 × viewScale」的 38~51 格封顶。

#### Scenario: 视距缩小时弹幕范围同步收缩

- **WHEN** 玩家将客户端视距调到 4
- **THEN** sphere 弹在 64 格之外 SHALL 不再渲染，MUST NOT 仍然满屏。

#### Scenario: 长激光不受截断

- **WHEN** 一条 `maxLength` 远大于视据的激光在视野外起始
- **THEN** 视距判定 SHALL 使用 `max(maxLength + 64, 视据半径)`，MUST NOT
  把光束切到比视线短。

### Requirement: 弹幕渲染剖针 SHALL 可按层关断、可读

每帧 SHALL 可在 Profiler 中列出按「本体/外发光/亮核」细分的切片；
`/danmaku layers` 命令 SHALL 能关断一个或多个层并即时反映；
`/gs_boss danmaku` SHALL 同行输出各层每帧 draw/顶点/getBuffer 计数。

#### Scenario: 关断辉光层

- **WHEN** 执行 `/danmaku layers glow`
- **THEN** 辉光层 SHALL 立即停止写入帧缓冲，计数切片中 glow 归零。

#### Scenario: 恢复

- **WHEN** 执行 `/danmaku layers all`
- **THEN** 三个切片的 draw 数 MUST 都回到非零。
