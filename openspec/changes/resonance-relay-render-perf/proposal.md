## Why

万象共鸣塔的运行态表现（螺旋、传输光束）目前由**服务端**每 tick 调 `level.sendParticles` 实现——这在网络层就是逐追踪玩家广播 `ClientboundLevelParticlesPacket`，是真金白银的网络包；光束还是每 tick 发。满配 5 阶（8 入 × 32 出 = 256 对）全在搬运时可达 `2×实搬对数` 束、数量级 10⁴ 粒/tick 的广播量，属于纯视觉却高成本的开销。同时路由结算在内层对每个 (源,汇) 对重算端点速率，而归元端每次都要重扫全部台位（O(入×出×台位) 次 BE/物品读取）。

上一轮 `normalize-spirit-transfer` 已把结算粒度收到"每结算周期"，但这两项（R7 渲染、R8 速率重扫）作为遗留明确记录未做。本变更收口它们。

## What Changes

- **螺旋/光束改客户端 BER 本地渲染**：给 `RitualCoreBlockEntity` 增加**载荷极小的渲染态客户端同步**（仅 `enabled` + 结构包围盒 + 每条链接的目标坐标与 in/out 方向 + 本周期"正在搬运"位掩码），新增 `RitualCoreRenderer` 在客户端绘制螺旋与光束，删除服务端 `sendParticles`。同步仅在状态变化时推送（仿 Mekanism 只同步一个 scale 的做法），稳态零持续包。
- **光束"仅真传输才亮"改为按结算周期同步一个搬运位掩码**：服务端在每个结算周期把"本周期实搬 > 0 的链接"打成 bitmask 随渲染态下发，客户端据此决定哪些通道画光束；不再逐 tick 发粒子包。
- **路由端点速率 per-period memo**：`routeTick` 开头对本周期涉及的源/汇各解析一次 `spiritIn/OutRatePerSecond` 并缓存，消除内层 O(入×出×台位) 重复重扫。
- 保持结算语义不变：仍是每结算周期、端点账本权威、FCFS、加权水位分配；本变更只动"表现如何到达客户端"与"速率解析次数"。

### 参考与启发（其他科技模组：Mekanism）

调研 `mekanism/Mekanism` 的电力实现，三条做法值得直接借用：

1. **显示"最后一次结算的精确吞吐"，不做任意窗口差分**。`EnergyNetwork.onUpdate()` 每 tick `prevTransferAmount = tickEmit(...)`，`getFlowInfo()` 直接显示 `prevTransferAmount`（"X/t"）。因此读数恒定、无窗口相位抖动——这正是上一轮把归元/路由展示改成"最近周期精确汇总"的依据。**启发：延续该口径，不要在渲染/展示层再做差分或平均。**
2. **客户端 BER 渲染 + 只同步紧凑派生量**。`DynamicBufferedNetwork.onUpdate()` 每 tick 只算一个 `currentScale` 浮点，变化才 `needsUpdate = true`；`RenderUniversalCable` 在客户端用 `network.currentScale` 画线缆，源码注释明确写 **"we don't actually ever sync the energy value to the client"**——不传能量值，只传渲染所需的标量。**启发：共鸣塔也应只下发"渲染所需的最小状态"（enabled/包围盒/端点/搬运位掩码），而非逐 tick 粒子。**
3. **公平分配 = 等额试算 + 让位回填**。`EmitUtils.sendToAcceptors` 先 `sendPossible` 试算、`while(amountPerChanged) shiftNeeded` 回填、再 `sendRemainingSplit`——即"先按份数均分、装不下的让位给其余"。这是**均分**语义。**启发/差异：本仓库归元按"各核速率上限加权"分配（需求方裁定），并用跨周期 WFQ 累加器保证低速率核不饥饿；Mekanism 的等额让位可作为"未来若改回均分"的参考实现。**

附带观察（暂不采纳，记为将来方向）：Mekanism 用**网络级**的持久 buffer + 每 tick 网络结算 + 容量求和，天然解决"多塔共拉一源"的跨塔仲裁（对应本仓库搁置的方案 C）；本变更不动结算架构。

## Capabilities

### New Capabilities
<!-- 无：渲染态同步与速率 memo 均属既有 resonance-relay-ritual 的表现/性能修正。 -->

### Modified Capabilities
- `resonance-relay-ritual`: 运行态螺旋粒子、传输光束两项要求由"服务端逐 tick 发粒子"改为"客户端 BER 渲染 + 仅变化时同步紧凑渲染态"，并把"仅真传输才亮"改为按结算周期同步搬运位掩码；新增渲染态同步要求（紧凑、仅变化推送、零稳态包）；结算要求补"端点速率 per-period memo"。

## Impact

- **Java**：`ritual/behavior/ResonanceRelayBehavior`（删除服务端 `emitSpiral/emitBeam/sendParticles`、每周期产出搬运位掩码、结算期速率 memo）；`block/entity/RitualCoreBlockEntity`（新增渲染态字段与 `getUpdateTag/getUpdatePacket` 或定向 S2C payload、变化时推送）；新增 `client/renderer/RitualCoreRenderer`（+ `GensokyouClient` 注册）；可能新增一个 S2C 渲染态 payload（观测量小，仅变化时发）。
- **网络**：稳态下从"每 tick 粒子包"降为"状态变化时 1 个小包"；省去满配时 10⁴/tick 的粒子广播。
- **CPU**：路由内层速率重扫由 O(入×出×台位) 降为 O(端点) / 周期。
- **测试**：搬运位掩码生成、渲染态序列化/反序列化、memo 与逐次重扫结果一致的单测；渲染器需实机观感验收。
- **不动**：结算/账本/WFQ 语义、GUI 的 `RitualInfoPayload` 1Hz 推送、pattern/配置数值。

## Non-Goals

- 不改结算架构（不引入 Mekanism 式网络级共享 buffer / 跨塔仲裁；方案 C 仍搁置）。
- 不改分配语义（维持按核速率加权 + WFQ，不改均分）。
- 不重绘线缆/tileblock 外观，仅螺旋与光束的到达方式。
