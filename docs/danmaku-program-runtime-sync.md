# DanmakuProgram 运行时同步与渲染问题说明

更新：2026-10-18。本文件供后续高能力 agent 分析当前框架。它区分当前实现、实机证据和失败方案，不把任何未完成方案当作最终设计。

## 1. 当前架构

当前是混合架构，不是纯 tick 位置同步，也不是严格的双端同公式同步：

```text
服务端 gameTime tick
  -> ProgramSession.advance
  -> DanmakuWorldRuntime 推进出生/派生/销毁/碰撞
  -> ReplicaState.capture(checkpoint)
  -> ProgramNetworkHost 发布事实和 Image

客户端
  -> ProgramReplica 接收 Image history
  -> ProgramClock 或 IntegratedProgramTimeline 映射时间
  -> ReplicaState/EmissionPlan/Path 按同一公式求值
  -> ProgramRenderView
  -> ProgramDanmakuRenderer 绘制
```

准确描述是：

> 服务端 tick 权威推进 + 程序定义/出生事实网络同步 + 客户端同公式重算 + 客户端时间外推。

服务端不逐弹发送每 tick 坐标。客户端收到 batch、出生 tick、源姿态、seed、模板、路径和派生关系后，用纯函数重建位置。

## 2. 关键代码

- `program/server/ProgramServerHost.java`：按 `ServerLevel.getGameTime()` 推进 session，并发布边界和网络快照。
- `program/server/ProgramSession.java`：调度循环、派生、卡边界和生命周期。
- `program/server/DanmakuWorldRuntime.java`：groups、plans、members、source relation、生命周期和权威碰撞入口。
- `program/gameplay/ProgramCollisionAdapter.java`：服务端按 `[runtime.tick(), runtime.tick()+1]` 扫掠碰撞。
- `program/network/ProgramNetworkHost.java`：从 checkpoint 捕获并按兴趣范围发送 projection。
- `program/network/ReplicaState.java`：客户端/服务端共享的状态求值和公式入口。
- `program/network/ProgramReplica.java`：接收 Image、缓存定义、保存有限 history、选择渲染 Image。
- `client/ProgramClientNetwork.java`：集成服读取 `IntegratedProgramTimeline`，远程客户端读取 `ProgramClock`。
- `program/gameplay/IntegratedProgramTimeline.java`：集成服每个完成 tick 发布 `(tick,nanos)`，客户端按 wall clock 得到分数 tick。
- `program/network/ProgramRenderView.java`：对选中的 Image 内每个成员调用 `ReplicaState.state(..., tick)`。
- `client/ProgramDanmakuRenderer.java`：每个渲染帧生成 render view 并绘制。

## 3. 当前一致性问题

服务端碰撞只使用整数权威 tick：

```text
server collide: [N, N+1]
```

集成服客户端却使用：

```text
renderTime = N + clamp((System.nanoTime() - boundaryNanos) / 50ms, 0, 1)
```

因此客户端可能显示 `N+0.8`，而服务端仍在或尚未完成 N。客户端路径公式本身和服务端相同，但两端使用的逻辑时间不一定相同，所以仍会出现渲染和判伤不同位。

当前 `ProgramReplica.imageAt(stream,tick)` 只选择一个历史 Image。选择之后，客户端继续在这个 Image 上按分数 age 求值。事实集合是离散切换，位置却在单个快照内向未来外推，导致快照切换、出生、派生、死亡和位置求值不在一个统一的渲染时间线内。

## 4. 实机证据

已加入默认关闭的诊断命令：

```text
/gs_program_probe start
/gs_program_probe status
/gs_program_probe stop
```

`ProgramMotionProbe` 记录服务端 tick 耗时/间隔、live member 峰值、客户端帧间隔、projection/render 耗时和固定成员位置步长。

第三卡原始 400 tick 尾珠寿命的一次采集：

```text
serverAvgMs=55.062242
serverMaxMs=123.9919
serverAvgIntervalMs=61.331896
serverMaxIntervalMs=155.1244
maxLive=3458
frameAvgMs=17.6349
projectionAvgMs=3.798424
renderAvgMs=3.563974
maxRendered=3458
```

这说明第三卡长时间运行后会把服务端程序推进拖到约 16 TPS，但客户端没有持续掉帧。第三卡每轮 13×7=91 根母弹；每根母弹 age 4..96 每 4 tick 派生 24 枚尾珠；尾珠原寿命 400 tick，循环为 260 tick。

用户还确认：第一卡从第一发开始就抽搐，即使没有明显整体掉帧。因此高成员数量会放大服务端 tick 抖动，但不能作为运动抽搐的唯一根因。

## 5. 已尝试并失败的方案

### 每个完成 tick 直接使用完成时间

当前 `IntegratedProgramTimeline` 的基础逻辑会在每个服务端边界重新使用整数 tick 和完成纳秒。服务端完成时间抖动时，渲染相位会跳变，表现为速度忽快忽慢。

### 固定 wall-clock 呈现锚点

曾让客户端跨 authority tick 沿用同一个 wall-clock 锚点。低 TPS 或服务端落后时，客户端会继续推进到未来，可能提前展开批次并增加渲染负载。用户反馈整体画面变卡，已撤回。

### 独立客户端显示时钟

曾让客户端按真实帧间隔推进显示时间，只以服务端边界作上限。用户实测出现幽灵弹幕：客户端已显示弹幕穿过玩家，服务端权威碰撞尚未到达该位置。已撤回。

### 两个 Image 的简单位置插值

曾接入 `ProgramRenderView.interpolate` 与 `ProgramReplica.renderViewsBetweenAuthority`，只对前后 Image 都存在的成员插值位置。网络和内容测试通过，但实机表现为所有弹幕离散运动。原因是只插了 position，没有统一插值 age、velocity/direction、presentation clocks、laser length、warning、group motion、成员出生/死亡过渡和派生有效时间；同时快照仍是离散 authority 时间线。

## 6. 当前最可疑的根因

1. 集成服服务端线程发布边界和客户端渲染线程采样边界之间没有稳定的安全时间窗。
2. 客户端把“最新 Image”当成当前事实后继续向未来求值。
3. 服务端判伤使用整数 tick，客户端显示使用分数 tick，天然存在最多接近一个 tick 的空间偏差。
4. Image history 不是连续的渲染时间线；事实切换和路径年龄计算没有统一生命周期契约。
5. 简单位置插值没有覆盖完整的运动状态和成员集合变化。

日志曾出现：

```text
authority=1394182 rendered=1394183.0 boundaryAgeMillis=65.8
```

这表示客户端显示已经到下一个 authority tick，而服务端最新完成边界仍为前一个 tick，是幽灵弹幕和相位跳变的直接风险信号。

## 7. 推荐目标架构

下一版应采用“固定安全延迟 + 双端同公式”架构：

```text
服务端发布 S(N-1), S(N)
客户端只在已确认的安全窗口内渲染
renderTime = confirmed authority interval - fixed delay + fraction
```

约束：

- 客户端绝不能超过最新已确认 authority 时间；
- 服务端和客户端使用同一程序公式、seed、出生事实和源姿态；
- 允许固定落后约一个 authority tick，换取平滑；
- 不逐弹发送每 tick 坐标；
- 快照要提供完整渲染状态或可重建完整状态；
- 成员出生、派生、死亡、清卡必须有明确的渲染窗口；
- 收不到下一快照时冻结或减速，不能自由外推；
- 收到快照后不能把时间重置到整数 tick 造成相位跳变。

注意：仅仅把两个 Image 的 position 做 lerp 不足够。应先定义 authority timeline、快照有效区间、成员集合生命周期和公式求值年龄，再决定是公式插值、状态插值，还是固定延迟下对同一纯函数求值。

## 8. 带宽权衡

在服务端 tick 抖动或网络延迟存在时，以下三项不能同时零成本满足：零延迟、任意帧率严格平滑、客户端绝不显示未确认位置。需要一个小显示缓冲，通常约一个 tick；这不要求逐弹每 tick 发位置。

推荐继续同步：程序定义 hash、seed、出生姿态、批次、生命周期事实、一次性世界采样结果。客户端在安全时间窗内使用纯公式计算位置。

## 9. 后续验证清单

- 40/50/60/100ms 服务端 tick 抖动下，轨迹单调连续；
- 下一快照到达前不超过最后安全窗口；
- 快照到达后位置、速度、年龄、激光长度和 presentation phase 不跳变；
- 出生、派生、死亡、换卡不会复活或瞬移；
- 渲染和服务端同一 authority 时间求值一致；
- 断包、批量到包和集成服线程交错不重复重锚；
- 日志记录 authority tick、render time、history pair、alpha、late/frozen 状态；
- 生存模式实际碰撞无幽灵命中；
- 山女第一卡从第一发开始连续；
- 山女第三卡高成员数量不引入新的同步跳变。

## 10. 当前工作树事实

保留：`ProgramMotionProbe`、`/gs_program_probe`、山女原始 400 tick 尾珠寿命、原始 `IntegratedProgramTimeline`、远程 `ProgramClock` 路径。

不应视为最终方案：集成服 wall-clock 自由外推、独立客户端显示时钟、只插值 `Member.position` 的简单快照插值、通过缩短尾珠寿命掩盖同步问题。
