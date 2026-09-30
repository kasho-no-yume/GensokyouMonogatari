## Why

`danmaku-render-state` 已经落地实体级的模拟/权威/渲染状态隔离、快照恢复与有限校准，
并且正确地把「位置包」降级成了纯诊断。剩下的问题不在它没覆盖的那部分，而在它
**依赖的一条前提不成立**：

`DanmakuSampleTimeline` 把服务器时刻换算成本地 tick 时**斜率硬编码为 1**
（`DanmakuSampleTimeline.java:101`），且锚点只在接受完整快照时设置、此后永不重锚
（全仓库唯一调用点 `DanmakuRenderState.java:172`）。

### 合成实测

`src/test/java/com/bitsson/gensokyou/danmaku/render/DanmakuSyncProbeTest.java`（12 条，
全离线，驱动**真实的** `DanmakuRenderState` / `DanmakuSampleTimeline`）：

| 注入的失真 | 比较误差 | 恢复次数 | 自检可达性 |
|---|---|---|---|
| 单向延迟 0~20 tick | 0 | 0 | 保持 |
| 抖动 ±8 tick | 0 | 0 | 保持（约半数样本 `TOO_EARLY`，属数据不足） |
| **速率慢 5%** | **0** | **0** | **第 2 个样本起永久 `TOO_EARLY`** |
| 速率快 5% | 0 | 0 | 窗口内保持；>40 s 后永久 `EXPIRED` |
| 离散年龄基准跳变 8 tick（匀速弹） | 0 | 1，**第一个样本**就升级 | 走 `REVISION_MISMATCH` |

三条与直觉相反、且每条都推翻了一种既有说法：

1. **延迟与抖动不产生误差。** 映射在**年龄空间**做，单向延迟被吸收成两侧同时带有的
   常数偏置，误差相消。相位亏欠精确等于延迟（`delayShowsUpAsAConstantPhaseDeficit`
   断言 `== 6.0`）—— 那是客户端预测架构的固有代价，客户端**应该**领先，不是缺陷。
2. **速率失配也不产生误差、也不触发恢复。** 所以恢复风暴的成因既不是速率失配，
   也不是延迟。
3. **速率失配的真正后果是自检静默失效。** 慢侧从第二个样本起，每一个样本都指向本地
   尚未推进到的 tick；`recordCalibration` 对 `TOO_EARLY`/`EXPIRED` 既不升级也不发请求
   （设计如此 —— 它们是「数据不足」而非「偏差」），于是 render-state 的**全部校验能力
   在任何一个非 tick 锁定到服务端的客户端上整体失效**，且没有任何症状。
   `DanmakuSyncStats.recordSampleNotComparable` 已经在计数，但没有任何东西消费它。

两侧**不对称**：慢侧立即且永久，快侧要等 `ΔS(r−1)` 超过 40 tick 窗口（约 40 s）才永久。
慢侧严格更糟，也是玩家更容易遇到的方向。

### 为什么这阻塞 `danmaku-event-sync`

`danmaku-event-sync` 的核心机制是「按事件携带的服务器时间把状态变更插入时间轴」
（`danmaku-event-sync/design.md:70`），并把「不实现服务器时间轴」列为 Non-Goal
（`:21`）。要在客户端插入，就必须回答：**本地历史里哪个 tick 对应服务器时刻 T？**

今天的答案是那条斜率为 1 的直线。在慢客户端上它指向**未来**，于是

```
事件在服务器时刻 T 生效并到达（客户端本地 tick L_c = 0.95·ΔS）
  正确落点   0.95·ΔS ≤ L_c  →  在过去，本可重放
  时间线说   1.00·ΔS > L_c  →  在未来，「还没发生」
  ⇒ 挂进 pendingEvents 等待，而这个差距每 tick 拉大 ⇒ 永远追不上那个落点
  ⇒ 缓存溢出或 predictionDeadline 触发 → UNCERTAIN → 请求快照
```

**慢客户端上每一个事件都生来不可应用。** 快客户端对称（落点掉出 40 tick 窗口）。
叠加 `DEFAULT_WINDOW_TICKS = 40`（可重放历史恒为 2 秒），event-sync 的重放机制在当前
时间轴上**无法实现** —— 这不是边缘情况，是常态。

## What Changes

本变更分两层交付，**T1 无条件、T2 无条件、T3 移出**：

- **T1（时钟）**：把「服务器时刻 → 本地 tick」的换算从斜率恒为 1 改成**带速率估计**的
  映射，并给它加上残差界限与陈旧降级。这是本变更的**完成条件之一**。
- **T2（纯函数轨道求值）**：把直线、速率曲线、曲射、编队帧、悬停/溜め、相位显隐从
  `tickDanmaku` 抽成无世界纯函数；并让读档后的**直线弹道段**由轨道时间重算，
  不再依赖 `deltaMovement`（见 `spellcard-blockers.md`）。这是完成条件之二。
- **T3（轨道层）**：共享编队参数、轨道快照、`scopeId`/`scopeVersion`、实例索引、
  生命周期、灰度迁移。**移出到 `danmaku-track-scope`**，因为它的价值完全取决于一个
  尚未做的生成突发带宽基线，而 `tasks.md` 原来把它写成无条件任务 —— 两者对不上，
  结果是本变更可能永远归档不掉，而 event-sync 在等它。

## Capabilities

### New Capabilities

- `danmaku-timeline`: 客户端的服务器时间轴估计（带斜率、有界、陈旧可降级）与无世界
  确定性轨道求值。

### Modified Capabilities

- `danmaku-pipeline-capacity`: 同步准入判据从「年龄 + 其它同步量」改为
  「服务器时间轴 + 轨道参数 + 轨道索引」；`danmakuMaxLagTicks` 的语义被更正为
  **映射误差上界**（不是网络延迟容忍度）。

### 不再触碰

- `danmaku-render-state`: 已归档且 spec 已建立（7 条 requirement）。本变更不再
  重复声明它的任何一条 —— 重复声明就是制造第二个写入者。

## Dependencies and Non-Goals

- 前置：已归档的 `danmaku-render-state` 作为实体级兼容与恢复基线。不得把普通位置包
  接回模拟坐标。
- 后置：`danmaku-event-sync` 在 **T1 完成后即可开工**，MUST NOT 等 T3。
- 不在本变更中实现：通用事件缓存、事件序号重放、灵符目标事件、分裂事件、激光关键帧、
  轨道快照、轨道身份、共享参数下发。
- 不改变服务端命中、伤害、分裂预算、销毁和其他玩法权威逻辑。
- 不以「画面不抖」单独证明时间轴正确，也不使用无限相位外推掩盖失步。

## Impact

- 影响 `AbstractDanmakuProjectile#age()` 的客户端分支与 `DanmakuSampleTimeline` 的
  换算斜率；`DanmakuSyncServer` 的校准包**已经是**时钟的观测通道（它带
  `serverGameTime`），T1 不需要新协议。
- 诊断：`/gs_boss danmaku` 新增锚点年龄、速率估计、残差分布、重锚/拒绝/陈旧计数。
  `recordSampleNotComparable` 从「只计数」升级为「有消费者」。
- 验收全部可离线断言（探针），不依赖 dedicated server；实机回归作为补充。
