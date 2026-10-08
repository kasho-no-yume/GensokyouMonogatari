## Context

付丧之冢 pattern JSON 已存在（0/1/2 阶），但 RitualBehaviors 未注册任何行为。它需要一个「消耗原版不可再生小垃圾 → 产灵」的生成器行为，与同族生成器（迦具土可再生燃料、日轮/月影被动时刻驱动）区分开：它的燃料是考古战利品（20 种陶片 + 16 张唱片），存量有限、不可再生。

- 模式参照：`KagutsuchiFlameBehavior`（点火即吞、缓存→槽核、空烧/停等、供灵基项独立）
- 渲染参照：`SukimaPortalRenderer` 的客户端几何烟（billboard + 透明混合 + large_smoke 帧）与 `RitualRenderState` kind 分发
- 已确认口径：陶片 20 种 + 唱片 16 种为燃料；模板剔除（半可再生）；decorated_pot 剔除（可再生）；不允许服务端刷特效，服务端只下发开关与阶级；产率 50×5^L、总量 ×5^L、缓存 400000×5^L

## Goals / Non-Goals

**Goals:**
- 同构迦具土的缓存/注灵/停等语义，降低新行为的学习成本
- 数值可调（config 基项），燃料表可配
- 黑色烟雾全客户端绘制，服务端零粒子包

**Non-Goals:**
- 不引入新粒子注册（ParticleType）；不改既有仪式的烟雾表现
- 不处理燃料自动漏斗链（祭品台只能手工/投掷交互）
- 不动 watatsumi 的 ritual_special 结构（该表是「产出表」，本仪式燃料是「消耗表」）

## Decisions

1. **串行燃烧 vs 并行**
   整座仪式同一时刻只维持一个燃烧批次（同迦具土口径）：每批产灵速率 = 50×5^L/s。缓存上限按 400000×5^L 与该速率的尺度匹配.
   备选（每祭品台并行）：入账 = 50×5^L×N 槽，为用户弃用所示——实机反馈应以「依次烧」呈现。


2. **单价映射**
   数据包驱动：`data/gensokyou/ritual_special/tsukumogami_fuel.json` 的 `entries: [[itemId, points], ...]`，默认值：20 陶片分布在 7000~20000、16 唱片分布在 15000~45000。升 1 阶总量 ×5（表中值是 L0，运行时 ×5^L）。经 RitualDataSyncPayload 随表快照下发（`tsukumogami_fuel` 键），客户端以同一 parser 重建——故 JEI 与书内页读数一致。
   备选（标签 + 统一值）：丢差异化 → 否决。

3. **速率/总量/缓存公式**
   `rate = TSUKUMOGAMI_BASE_RATE × 5^level`（50）、`itemTotal ×5^level`、`capacity = TSUKUMOGAMI_BASE_CAPACITY × 5^level`（400000）。燃烧时长 = itemTotal/rate，阶级无关。供灵出率基项独立声明、默认与产灵速率同值（同迦具土语义），二者不可复用同一 getter。

4. **FX 通道**
   新增 `RitualRenderState.KIND_TSUKUMOGAMI`（enabled + tier + minY/maxY + burningMask）。服务端每 1Hz 心跳快照下发；**不含任何粒子包**。客户端 `RitualCoreRenderer` 据 state 在全部祭品台位置统一绘制暗色烟雾：上升烟片（large_smoke 纹理 8 帧动画、billboard、alpha 混合），密度/高度/不透明度随阶级增强，空烧/停等淡出。

5. **燃烧批次状态**
   新建 `TsukumogamiState implements RitualBehaviorState`，持单个在烧批次（remainingTicks/totalTicks/fuelIcon/slotIndex）。沉在核心方块实体（同 ReiyokuState 先例）。`onStructureLost` 作废不返还。

## Risks / Trade-offs

- [并行燃烧 × 多槽并行 → 瞬时入账 8×50=400/s，缓存溢出即空烧] → 空烧语义继承迦具土（仍燃烧、不入账），客户端表现不断
- [路由出率默认 50 但 8 槽聚合 400/s → 共鸣网络吃不满并行产出] → 出率基项允许管理员手动上调；本版默认收单流口径（单台满速），不改核心账本
- [客户端几何烟大量 billboard 面线] → 帧数预算（烟柱数量 × 帧帧数）在配置可调；与火焰火场同粒度级复用
- [decorated_pot 剔除 vs 陶片 4 片合成 1 个盘的直觉] → 文档明示其「盘」形态为非燃料，差异在「盘」是容器（可再生合成）
- [每世界供给量不固定] → 陶片/唱片不可复制，总产出上限 ≈ 世界内全部此类物品；不适合长期高吞吐源

## Open Questions

- 出率基项是否默认改为 `rate × 最大并行槽数`？（本版取默认收单流 50×5^L，可调）
- 遗骸/墓室结构（pattern 里 `decorated_pot` 作为陈设出现）是否在产出类掉落表里挂偏向——本期不做
- 黑色烟雾是否接自定义染色粒子 type（需额外注册管线）？本版先走几何烟路径
