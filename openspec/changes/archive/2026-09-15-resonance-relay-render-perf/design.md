## Context

`ResonanceRelayBehavior.serverTick` 每 tick 服务端 `level.sendParticles` 画紫色螺旋（间隔 {8,6,4,2}t）与传输光束（每实搬通道 2 束、≤48 粒/束）。服务端粒子 = 逐追踪玩家 `ClientboundLevelParticlesPacket`；满配 5 阶可达 `2×实搬对数` 束、~10⁴ 粒/tick。核心 BE 目前**没有** `getUpdateTag/getUpdatePacket`，客户端不知道链接/启停，故当初只能用服务端粒子。项目已有 BER 基建（`RitualPedestalRenderer`、`SukimaPortalRenderer`、`GensokyouClient.registerBlockEntityRenderer`）。

同时 `routeTick` 内层对每个 (源,汇) 对重算 `outRateOf/inRateOf`，而归元端每次都要重扫全部台位 → O(入×出×台位)。

参考 `mekanism/Mekanism`：`DynamicBufferedNetwork.onUpdate` 每 tick 只算一个 `currentScale` 浮点、变化才置 `needsUpdate`；`RenderUniversalCable` 客户端用该 scale 画线缆，源码注释明确"不把能量值同步给客户端"；吞吐显示用 `EnergyNetwork.prevTransferAmount`（最后一次结算的精确量）。本设计据此把"表现"从服务端粒子改为"客户端 BER + 仅变化同步的最小渲染态"。

## Goals / Non-Goals

**Goals:**
- 螺旋/光束改客户端 BER 本地绘制，删除服务端 `sendParticles`；稳态零持续包。
- 提供仪式核心的"渲染态"客户端同步（紧凑、仅变化推送、含搬运位掩码），并自动覆盖区块加载。
- 路由端点速率 per-period memo，消除 O(入×出×台位) 重复重扫。
- 结算/账本/WFQ/展示语义完全不变。

**Non-Goals:**
- 不改结算架构，不引入网络级共享 buffer / 跨塔仲裁。
- 不改分配语义（仍按核速率加权 + WFQ）。
- 不新增/修改玩法判定，渲染态纯客户端只读。
- 不重绘 tileblock 外观。

## Decisions

### D1 同步通道：复用原版方块实体同步（`getUpdateTag` / `getUpdatePacket`）

给 `RitualCoreBlockEntity` 覆写 `getUpdateTag`（含一个紧凑的渲染态子 tag）与 `getUpdatePacket`（`ClientboundBlockEntityDataPacket.create(this)`）；状态变化时 `level.sendBlockUpdated(pos, state, state, BlockEntityUpdateType.BLOCK_UPDATE)`。

选它的理由（对照 Mekanism + 成本）：
- **自动覆盖区块加载**：玩家首次追踪该区块时由 `getUpdatePacket` 自动带状态，无需自定义 start-tracking 钩子。
- **零新协议**：不新增 payload 注册/编解码。
- 载荷小：渲染态 = `enabled(1B) + minY/maxY(8B) + 链接数组(每链接 8B pos + 1B dir) + movingMask(long)`；L5 最多 40 链接 ≈ 400B，且仅变化时发。
- **备选**：自定义 S2C payload → 需自行处理 chunk-load 重发与追踪玩家枚举，收益不足以抵消复杂度，否决。**备选**：继续服务端粒子仅降频 → 仍是网络包、仍非本地渲染，治标不治本，否决。

**稳态零持续包的关键**：仅当**序列化后的渲染态变化**时才 `sendBlockUpdated`。`movingMask` 在持续搬运且集合不变时保持不变 → 不再发；只有某通道"起搬/停搬"、链接增删、启停、结构变高时才发。

### D2 渲染态内容（最小化）

- `enabled`
- `structureMinY/structureMaxY`（结构纵向包围盒）
- 链接列表：按确定的规范序（inLinks 再 outLinks）序列化为 `(BlockPos, directionIN/OUT)[]`
- `movingMask`：与链接列表同序的 bitmask，标记"最近一次结算周期实搬 > 0"的通道（`long` 足够 L5 的 ≤40 条；配额若将来超 64 条改用 `long[]`，本变更先按 `long` + 断言）

客户端 BE 持有该态；**不**参与任何服务端逻辑。未 enabled/失效时下发清零态。

### D3 客户端渲染器

新增 `client/renderer/RitualCoreRenderer implements BlockEntityRenderer<RitualCoreBlockEntity>`，在 `GensokyouClient` 注册：
- **螺旋**：据 `enabled` + 包围盒 + `level.getGameTime()` 本地推进相位绘制（视觉规则沿用现服务端参数：间隔 {8,6,4,2}t、单次量随阶级、绕核心纵轴、半径 ~3）。
- **光束**：仅对 `movingMask` 置位且链接仍解析到目标核心的通道绘制"塔顶→目标"线；出绿/入青蓝；按距离抽稀、单束点数 ≤ 上限。目标坐标沿用同步的链接坐标。
- 距离/视锥剔除交给 BER 常规机制；对同塔多条光束维持全局点数上限防极端场景。

### D4 路由端点速率 per-period memo

`routeTick` 开头对本周期**涉及的全部端点**各解析一次：
- `outRateCache: Map<BlockPos, Long>`（源 out 速率）、`inRateCache: Map<BlockPos, Long>`（汇 in 速率）；
- `needyEndpoints` 与配对预算改用缓存；每周期清空重建。
结果与逐次重扫逐位一致（纯缓存），仅把 O(入×出×台位) 降为 O(端点)/周期。

### D5 删除服务端粒子

移除 `ResonanceRelayBehavior.emitSpiral/emitBeam` 及所有 `level.sendParticles` 调用；`serverTick` 只保留：周期边界结算 + 每秒 GUI 推送；每周期结算后计算 `movingMask` 并与上次比较，变化则 `sendBlockUpdated`。螺旋/光束的表现完全移交客户端。

## Risks / Trade-offs

- [R1 客户端渲染态依赖 BE 同步，区块刚载入可能一瞬无渲染] → `getUpdatePacket` 随区块下发，属常规；首帧后即生效，可接受。
- [R2 movingMask 与链接列表索引错位] → 二者同源于同一规范序快照，序列化/反序列化各配单测；链接变更时同步重置 mask。
- [R3 极端满配下客户端光束绘制压力] → 保留距离抽稀 + 全局点数上限；必要时对远处光束降级为单点脉冲。
- [R4 用 `long` 存 mask 的配额上限] → 当前 L5 最多 40 链接 < 64；design 记录"配额翻倍越过 64 时改 long[]"并加断言。
- [R5 memo 引入缓存不一致] → 纯周期内缓存、每周期重建；单测断言 memo 与逐次重扫结果一致。
- [R6 渲染态被误当玩法依据] → 服务端权威不变，客户端仅绘制；同步包不含任何可作弊的结算输入。

## Migration Plan

开发期，无存档迁移。渲染态为运行时同步数据，不入 NBT 存档（或仅入 `getUpdateTag` 的瞬时 tag）。回滚 = revert。

## Open Questions

- 光束的最终美术形态（粒子带 vs. 半透明 ribbon/贴图）留待实现期与美术（astra）确认；本变更先保功能等价（线状粒子）。
