## MODIFIED Requirements

### Requirement: 密集模式下弹幕渲染必须用单通解析
球弹渲染器 SHALL 恒以**单次绘制**呈现每一枚球弹，MUST NOT 随屏上弹数增减而增减渲染层数。密度切换 MUST NOT 成为观感变量：同一枚弹在弹数变化时 MUST 保持逐位相同的观感。

球弹的本体与外辉光 SHALL 由**同一张贴图**承载（见 `danmaku-sphere`），该贴图 MUST 同时携带**实心本体**与**外辉光**，MUST NOT 只留白斑。

球弹路径 SHALL NOT 渲染独立的外发光层与核心亮核层：三层结构使填率随层数线性增长，MUST NOT 以"低密度时好看"为由恢复。

渲染剖针 SHALL 报告**实际绘制的层数与 quad 数**，MUST NOT 把"单层是设计"读作"被密度降级"；球弹的 glow/core 计数恒为 0 是**正确读数**，其输出 MUST 说明这一点。

#### Scenario: 弹数不影响观感
- **WHEN** 屏上弹数从 200 变化到 6000
- **THEN** 同一枚弹的观感逐位相同，MUST NOT 出现"密度高时换了一张图"

#### Scenario: 单层仍是可读的球
- **WHEN** 渲染器在单通下渲染球弹
- **THEN** 其贴图同时携带实心本体与外辉光，MUST NOT 只剩白斑

#### Scenario: 不恢复附加层
- **WHEN** 屏上弹数很低
- **THEN** 球弹仍只提交一个 quad，MUST NOT 因"便宜得起"而多画外发光层

#### Scenario: 剖针如实报告
- **WHEN** 读取渲染剖针
- **THEN** 其层数与 quad 计数反映实际提交量，MUST NOT 与实际提交量矛盾

## ADDED Requirements

### Requirement: 弹幕渲染 SHALL 可在 6000 弹规模下做新旧观感对照压测
`/danmaku wall` SHALL 接受至多 6000 发的数量参数，且该夹具 MUST NOT 受服务端弹幕实体上限节流——它直连实体生成，不经发射器。

系统 SHALL 提供一个**静止、无害、正对相机**的竖直平面弹墙夹具（`/danmaku screen <数量> [距离] [间距]`），其**视锥内弹数** SHALL 可由「数量 / 距离 / 间距 / 视锥尺寸」直接算出，MUST NOT 是数量的隐式函数。既有 `/danmaku wall` 的弹以固定速度推进、撞到玩家即销毁、寿命仅 40 s，MUST NOT 单独用作 180 s 采样窗口的夹具。

球弹贴图 SHALL 支持在**同一构建内热切换**（`/danmaku texture lod|glow`），使新旧两臂**只有贴图不同**，MUST NOT 需要换构建或重编。

对照压测 SHALL 沿用既有证据格式（`metadata.json` / `window.json` / `server.jsonl` / `frames.csv` / `proxy.jsonl`），暖机 SHALL ≥ 30 s、采样 SHALL ≥ 180 s，缺失数据 MUST NOT 以 0 填充。

新观感相对旧观感的**帧时间 p95 劣化 SHALL ≤ 5%**。超标时 MUST 有可执行的降级手段（收窄辉光 / 降辉光 alpha / 收小实心区 / 回落旧贴图），且降级 MUST NOT 改变 quad 面积与 draw call 数——那些是唯一不随贴图内容变化的成本。

#### Scenario: 6000 弹墙可生成
- **WHEN** 执行 `/danmaku wall 6000`
- **THEN** 6000 枚球弹被生成，MUST NOT 被服务端弹幕上限截断

#### Scenario: 屏上弹数可算
- **WHEN** 以固定距离与间距生成 `/danmaku screen 6000`
- **THEN** 视锥内的弹数由「距离 / 间距 / 视锥尺寸」唯一确定

#### Scenario: 夹具撑得住采样窗口
- **WHEN** 生成 `/danmaku screen` 后静置 180 s
- **THEN** 弹数保持不变，MUST NOT 因推进、撞人或寿命而减少

#### Scenario: 同构建热切换
- **WHEN** 执行 `/danmaku texture glow` 后再执行 `/danmaku texture lod`
- **THEN** 球弹贴图随之切换，MUST NOT 需要重启或重编

#### Scenario: p95 门槛
- **WHEN** 两臂压测数据齐备
- **THEN** 新观感臂的帧时间 p95 相对旧观感臂的劣化不超过 5%
