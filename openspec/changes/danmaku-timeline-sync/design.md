## Context

`danmaku-render-state` 已归档并落地实体级的模拟状态、权威样本、渲染偏移、快照恢复和
有限校准。本变更建立在它之上，不重新实现 `lerpTo` 隔离、渲染访问层或恢复状态机。

本变更要修的是那条**被 render-state 依赖、但本身不成立**的前提：
`DanmakuSampleTimeline` 的换算斜率硬编码为 1（`:101`），锚点只在接受快照时设置
（唯一调用点 `DanmakuRenderState.java:172`）。合成实测的结论见 `proposal.md`。

`docs/danmaku-sync-architecture-and-open-problems.md` 是历史调查记录。它的实机读数
（4~8 tick、双峰翻转、847 次硬纠正）被该文档 §5.3、§6.5 自己标注为口径可疑 ——
§5.3 记着一次守卫把 99% 样本丢掉，§6.5 记着 `lagTicks` 的投影被硬纠正是污染的。
本变更的探针结论**取代**那批数字，但**不解释**它们：五种注入失真没有一种能产生
用户报的那个症状，那是另一条机制（见 Open Questions）。

## Goals / Non-Goals

**Goals:**

- 让「服务器时刻 → 本地 tick」的换算带**速率估计**，使同刻比较在任何非 tick 锁定的
  客户端上仍然可达。
- 速率估计 MUST **有界**：吸收传播抖动，拒认真失步。宁可暂时不可比，也不得把 bug
  静默转成错渲。
- 时钟陈旧或无法估计时进入**明确降级**，而不是继续外推。
- 把确定性运动抽成**无世界纯函数**，并让读档后的直线弹道段由轨道时间重算。
- 为 `danmaku-event-sync` 提供可用的服务器时间轴，使「按事件时间插入状态」得以实现。
- 保留服务端命中、伤害、分裂预算、销毁和区块判定的权威性。

**Non-Goals:**

- 不重复实现已归档的 `danmaku-render-state`，也不重新声明它的任何一条 requirement。
- 不实现轨道快照、轨道身份、共享参数下发、实例索引或生命周期（→ `danmaku-track-scope`）。
- 不实现事件缓存、缺号重放、灵符目标事件、分裂事件或激光关键帧。
- 不把客户端轨道计算用于命中、伤害、无敌帧、分裂配额或服务端生命周期。
- 不以「画面不抖」单独证明时间轴正确，也不使用无限相位外推掩盖失步。

## Evidence Boundary

- **已确认（探针离线实测）**：斜率恒为 1 时，速率慢 5% 的客户端从第二个校准样本起
  每一个都被判 `TOO_EARLY`；速率快 5% 的客户端在 `ΔS(r−1)` 超过 40 tick 窗口后被判
  `EXPIRED`。两者都不产生比较误差、不触发恢复。
- **已确认（探针离线实测）**：单向延迟 0~20 tick 与抖动 ±8 不产生比较误差、不触发
  恢复；相位亏欠精确等于延迟。
- **已确认（探针离线实测）**：注入 8 tick 离散年龄基准跳变时，匀速弹走
  `REVISION_MISMATCH`（severe 档，第一个样本即升级），解析式弹额外产生位置误差。
  ⇒ `danmakuMaxLagTicks` **不参与**这类失步的判定，调它对该症状无效。
- **未确认**：用户报出的实机症状（rebuilt 弹 ±4~8 tick 抖动）**不被以上任何一种失真
  解释**。五种注入失真都产生不了它。探针不模拟 `StartTracking` 在实体仍存活时二次
  触发，那需要实机复测。
- **不采用**：把单机内嵌服务端的表现推广到分机环境；把 `lagTicks = error · direction`
  当作年龄差。

## Decisions

### 1. 服务器时间源：`level.getGameTime()`，其余问题不存在

**本条划掉原 Open Question 1（`gameTime` / 模组 epoch / 按维度维护，以及暂停、降速、
tick gap 语义）。**

理由是一条实测事实：`Entity.tickCount`（`AbstractDanmakuProjectile#age()` 的自变量）
与 `ServerLevel.getGameTime()` 由**同一个** `Level.tickNonPassenger` 循环驱动，一 tick
一步。因此：

- `/tick freeze` 同时停两者，`/tick sprint` 同步加速两者 —— 语义**天然正确**，
  不需要另造 epoch，也不需要设计暂停/降速语义。
- 弹幕不跨维度。「按维度维护的单调序列」等价于「用该维度的 `getGameTime()`」，
  而现有快照与校准包发的就是它（`DanmakuSyncServer.java:159`）。
- 唯一真实的中断是**区块卸载** —— 弹不 tick 而 `gameTime` 继续走。这由
  `StartTracking` 重新推送快照重新锚定覆盖，不构成时间语义问题。

**因此不引入模组 epoch。** 多一个 epoch 就多一处能与 `gameTime` 混用的机会，
而现有两个包已经免费提供了正确的那个。

### 2. 换算必须带斜率，而不只是锚点

锚点只能**平移**映射。斜率恒为 1 的直线永远够不到 `floor(r·ΔS)`，所以「再加一个锚点」
修不了任何一种速率失配。T1 的核心是给映射一个速率：

```text
localTickFor(serverTime) = anchorClientTick + round(rate · (serverTime − anchorServerTime))
serverTimeNow(clientTick) = anchorServerTime + (clientTick − anchorClientTick) / rate
```

观测通道**已经存在**：`DanmakuCalibrationPayload` 带 `serverGameTime`，
`DanmakuSyncServer` 每 `danmakuCalibrationIntervalTicks` 发一批。**T1 不需要新协议。**
只有当 `danmaku-event-sync` 需要一个独立于弹幕实体的时钟时，才补一个 ~9 字节的 tick 包。

速率用斜率而非「重锚间隔」表达，因为探针显示慢侧的失效**从第二个样本就开始**
（`mappedAheadBy` 每采样间隔 +1 tick），任何以采样间隔为尺度的方案都来不及。

### 3. 速率估计 MUST 有界

这是全部设计里最容易做错、也最危险的一条。`docs/…-open-problems.md` Q6 已经警告过：
相位外推「吸收真 bug —— 把同步 bug 从硬失败静默转成错渲」。一个能把相位平滑调准的
时钟，同样能把 8 tick 的基准跳变平滑掉。

因此：

- 残差超界 ⇒ **拒绝**，并计数。绝不因为「看起来更准」而接受。
- 硬重锚（换锚点）MUST 连续 **N 次同向残差**才允许，避免单包抖动触发。
- 速率变化 MUST 落在配置的允许区间外才生效（默认 `[0.8, 1.25]`，覆盖客户端 lag、
  `/tick` 不一致、以及多倍速回放）；区间外的斜率视为不可信，退回 1.0 并降级。
- **反向测试是本决策的验收核心**：注入 8 tick 离散基准跳变后，时钟 MUST 仍然把它判为
  `REVISION_MISMATCH`。若这条失败，本变更制造的问题比它修复的更糟。

### 4. 陈旧即降级

超过 `stalenessTicks` 没有新观测 ⇒ 状态转 `UNCERTAIN`，停止外推，等待新样本。
「停止外推」意味着画面回到本地模拟轨迹，而不是继续朝一个不可信的时间轴靠。

### 5. 确定性轨道求值是无世界纯函数（T2）

先把现有确定性路径抽成纯函数：直线、速率曲线、曲射、编队帧、悬停/溜め、相位显隐。
接口输入显式包含**轨道时间**、实例索引和完整参数，不读取实体、目标、方块或客户端坐标。

两条纪律不变：`DanmakuSpeedProfile` 与 `FormationFrame` 只用四则运算、`frac`、取绝对值，
不用 `sin/cos`（Rodrigues 旋转是已记录的例外）。**位置必须可闭式求值** —— 这不是美学
要求，`AbstractDanmakuProjectile.java:653-668` 的 rig 之所以能保证「位移后位置逐位等于
解析值」，正是因为每 tick 从解析值重来。

服务端命中仍用实体真实位置和扫掠结果。客户端轨道求值只提供模拟/渲染表现和诊断。

### 5b. 修订：T2 的实际内容是「分类 + 纪律」，不是「抽取」

实施期逐行核对 `tickDanmaku` 后发现，本节原先写的「把六种运动抽成无世界纯函数」
与代码不符，纠正如下：

| 弹种 | 现状 | 是否需要改 |
|---|---|---|
| 编队帧 | `FormationFrame.framePositionAt(age)` / `positionAt(age, axis, advance)` **已经是**无世界纯函数 | 否 |
| 速率曲线 | `DanmakuSpeedProfile.speedAt(age)` / `travelAt(age)` **已经是** | 否 |
| 相位显隐 | `DanmakuPhase.isHidden(age, …)` **已经是** | 否 |
| 悬停 / 溜め | 「速度置零」，平凡闭式 | 否 |
| 直线 | `pos += v`，增量的 | 否（见 5c） |
| **曲射** | `rotateAbout(velocity, axis, ω)` 作用在**上一步的速度**上 | **不可改** |

**曲射为什么不能也变成闭式**：它的解析位置是 `p₀ + Σ R(axis,ω)ⁱv₀`，闭式化要把整条
旋转历史求和。而 `Rotation.about` 用的 `sin/cos` 本就不保证跨端逐位一致（5.1 的纪律），
闭式化会把这个已记录的例外从「一次旋转」放大成「整段轨迹」—— 那是行为变更，不是重构。
曲射的相位恢复属于 `danmaku-event-sync` 的 `REDIRECT`。

⇒ T2 的实际交付物是 `danmaku/motion/DanmakuTrackKinds`：一个把「弹道形式」与
「读档能否自愈」两条判据放在一起的**分类表**。`danmaku-event-sync` 需要查询的
「这项状态能否由轨道时间推导」从此有了一个可调用的答案，而不是让人去读 95 KB 的
`tickDanmaku` 自己推。配一条编译后常量池扫描锁死超越函数纪律。

### 5c. 修订：读档修复是「补一个缺失的数」，不是「由轨道时间重算」

原写法「读档后由轨道时间重算，不再依赖 `deltaMovement`」对**直线弹不成立**：
直弹的位置永远是 `pos += v`，除了速度没有任何可重算的东西。

逐条核对 `readAdditionalSaveData` 后的实际结果：

| 弹种 | 读档后 | 原因 |
|---|---|---|
| 编队帧 | **自愈** | `DATA_HAS_FRAME` 按 `FrameSp` 键存在**推断**恢复，rig 立即用 `positionAt(age)` 覆写位置 |
| 速率曲线 | **自愈** | `DATA_HAS_PROFILE` 按 `SpV3` 键存在推断恢复；`alongAxis` 在速度为零时回落到已持久化的 `axis()` |
| 曲射 | **冻结** | `rotateAbout(零向量, …) ≡ 零向量` |
| 直线 | **冻结** | 速度为零即原地不动 |

⇒ 读档修复的**全部内容**就是给「曲射 + 直线」两类补上 `deltaMovement` 的存档往返
（3 个 double）。给编队弹与速率曲线弹写速度既无必要又**误导** —— 读的人会以为速度
是它们的权威，而它们的位置由 `positionAt(age)` 决定。两个真相源，迟早有一个开始撒谎。

这也与 `danmaku-event-sync/spellcard-blockers.md` 的既有结论一致（它当时估的是
「约 60 行：3 个 double 的存档往返」），并且**只覆盖它列出的主体**，不涉及改向。

服务端补了速度即够：客户端的速度由 `DanmakuSnapshotPayload` 携带，
`StartTracking` 会推一份，不需要另一条通道。

### 6. 与 render-state 的接入边界

渲染偏移仍由 render-state 管理。轨道求出的模拟位置是基准；可信样本之间的有限视觉追赶
MUST NOT 改写轨道时间、速率估计或服务端判定。`DanmakuMaxLagTicks` 的语义被**更正**为
「映射误差的 tick 上界」（不是网络延迟容忍度），其 config 注释同步修正 —— 现在的注释仍在
描述 render-state 之前的世界。

### 7. T3 移出，且门槛写进变更而不是默契

轨道快照、轨道身份、共享参数下发、实例索引、生命周期与灰度迁移的价值完全取决于一个
**尚未做的生成突发带宽基线**。原 `tasks.md` 把它们写成无条件任务，原 `design.md` §6 说
「只有实测确认收益才做」—— 两者对不上，结果是本变更可能永远归档不掉。

它们移出到 `danmaku-track-scope`，门槛写进那个变更的 proposal。`danmaku-event-sync`
MUST NOT 等它。

## Risks / Trade-offs

- 速率估计本身可能不稳。斜率用区间 + 残差双重约束，且默认退回 1.0；最坏情况退回
  当前行为，不会更差。
- 闭式求值抽取触及 `tickDanmaku`（95 KB 单文件）的核心分支。逐条抽、逐条跑既有
  `FormationFrameTest` / `DanmakuSpeedProfileTest` / `NormalTravelTest`；任一条既有
  测试的期望值被迫改动即视为回归，不许改测试迁就实现。
- 浮点与超越函数可能跨端产生差异。遵守现有数值纪律；不能逐位重建的运动不进确定性轨道。
- 读档后由轨道时间重算位置，会改变「普通弹 2 秒飞出场外」这类观察窗口下的行为。
  预期是修好冻结，但 MUST 有回归测试覆盖「读档后弹仍以正确速度离场」。
- 探针不覆盖 `StartTracking` 二次触发与真实 chunk 重载。这两条只能实机验证。

## Migration Plan

1. T1-a：实现无世界时钟估计器（斜率 + 残差界限 + 陈旧降级），纯函数，探针可断言。
2. T1-b：接入 `DanmakuSampleTimeline` 与 `age()`，斜率默认 1.0 —— **行为逐位不变**。
3. T1-c：接线校准包为观测通道，打开速率估计。跑探针全套 + 既有 render-state 测试。
4. T1-d：诊断接入 `/gs_boss danmaku`，`recordSampleNotComparable` 获得消费者。
5. 交棒：`danmaku-event-sync` 可开工。
6. T2：落 `DanmakuTrackKinds` 分类表 + 超越函数纪律锁 + 速度存档往返（见 5b / 5c）。
7. 归档。T3 另立 `danmaku-track-scope`，先测带宽。

## Open Questions

- **实机症状仍未归因。** 用户报的 rebuilt 弹 ±4~8 tick 抖动不被五种注入失真解释。
  下一步应是实机在 `StartTracking` 打出 `entityId / serverAge / 已配对次数`，
  验证 `docs/…` §6.7 机制 A（`seedPeerAge` 在客户端实体仍存在时被二次改写）。
  注意 render-state 落地后 `age()` 在 `ageAnchored` 时**不读** `peerAge`，
  所以该路径可能已失效 —— 但「可能」需要实测，不需要推理。
- 速率的可信区间 `[0.8, 1.25]` 是拍的。需要实机在弱机与多倍速下测残差分布再收窄。
- 校准包是否需要提频以支撑速率估计。探针显示采样率**不改变**误差（只改成本），
  但它决定斜率的收敛速度与 `stalenessTicks` 的取值。
- T2 的读档重算是否应推广到曲射/编队弹，还是只做直线。推广面更大，
  但「编队弹的位置已经是解析式」意味着它可能根本不需要 —— 需先确认它读档后是否也冻结。
