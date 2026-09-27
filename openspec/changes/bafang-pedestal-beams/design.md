## Context

八方归元的渲染态 `KIND_BAFANG` 只带 `enabled` + `tier`（`RitualCoreBlockEntity:1339-1342`），`linkPos` 为空、`movingMask` 为 0。`BafangGuiyuanBehavior` 本身从不构造渲染态。客户端 BER 据 `tier` 画一个 fresnel 球（`RitualCoreRenderer:626-654`），半径 `0.7 + 1.6×tier`、悬于 `1.0 + 1.3 + 0.18×tier + radius×0.6`——5 阶即半径 8.7 格、悬高约 7.4 格。

「符合要求的灵力核心」的服务端判据在 `BafangGuiyuanBehavior:231-233`：

```java
if (held.isEmpty() || !(held.getItem() instanceof SpiritCoreItem core)
        || !accepts(core.tier(), match.level())) { continue; }
```

即 `instanceof SpiritCoreItem` 且 `coreTier <= ritualLevel`。`accepts` 在 `:65-67`。

## Goals / Non-Goals

**Goals:**

- 让"哪几座台在被识别"在屏幕上直接可读。
- 零新增网络包。
- 焦点核是画面中唯一边界明确的实体；原灵气球退为"场"。

**Non-Goals:**

- 不改八方归元任何数值与托管语义。
- 不改 `RitualRenderState` 布局、不新增 kind。
- 不让焦点核的尺寸/高度随阶级变化（固定 1 格半径、固定顶面 +1.5）。

## Decisions

### D1 — 焦点核的位置与尺寸

`BlockPos core` 的方块顶面在 `core.getY() + 1`；焦点核中心取 `core.getY() + 2.5`（= 顶面 + 1.5），水平取核心格中心 `(0.5, ·, 0.5)`。半径固定 1.0 格，配置化。

**为什么不随阶级放大**：焦点核的语义是"汇流点"，是结构上的一个点；随阶级放大只会让它与大灵气场抢戏。体量变化交给悬在半空的灵气场承担。

### D2 — 不透明 vs 加法

需求明写"不透明"。因此焦点核**不能**用 `SpiritOrbRenderTypes.ORB`（加法 fresnel，加法下的"不透明"只会是一团更亮的白）。走常规 alpha 混合的自发光渲染类型（顶点色写绿、`FULL_BRIGHT` 光照），配一张中心不透明、边缘柔化的实心球贴图。呼吸由 CPU 侧缩放驱动（与现灵气球同法），不引入新的 uniform。

**已知取舍**：实心球会遮住其后的东西。这是"不透明"的直接后果，也是需求要的效果（画面上唯一的实体感来源）。

### D3 — 祭品台坐标的客户端推导链

全部数据已在客户端，无需任何新包：

```
RitualDataSyncPayload（patterns/recipes/smelt_rules，GZIP）
  → ClientRitualData.applyJson → RitualPatternLoader.parseForEdit
  → RitualPattern（客户端侧已重建，含已展开 + 规范排序(y,z,x) 的 LevelSlice.blocks）
  → 取 blocks 中 level() == state.tier() 的那一层
  → 过滤 palette[key] 为「TAG 谓词且标签为 gensokyou:ritual_pedestals」的条目
  → 得到相对核心的 (x, y, z) 偏移，顺序与服务器侧 RitualPedestals.positions() 一致
```

`RitualMatcher.orient` 只做置换与符号翻转，而 pattern 本身已四重对称展开，故偏移集合与旋转无关；`level` 恰为 `renderState.tier()`；`kind == KIND_BAFANG` 唯一确定 patternId。**结论：偏移集合由 (patternId, tier) 完全决定，客户端可零包算出。**

台内是否合格：按 `core.getBlockEntity(offset + corePos)` 取 `RitualPedestalBlockEntity`，读其 `held`（逐 BE 经 `getUpdateTag` 同步），用与服务端同一条判据。注意 `markHeldChanged()` **不广播**，但它只在能量数值变动时调用；**槽内物品的有无走 `setHeld`/`takeHeld`，二者都广播**，故本表现不受影响。

**为什么不给 `KIND_BAFANG` 加 `linkPos` + 位掩码**：那要新增同步、且信息量与客户端本地推导完全重复。`KIND_KANAYAMAHIKO` 那种做法适用于"燃烧与否由服务端按 tick 判定"的场景；本处判据是纯函数（物品类型 + 阶级），本地可判。

### D4 — 逐台包络

每台一条独立包络（`target = 该台当前是否合格`），沿用共鸣塔 `BOLT_ENVELOPES` 的形状：`WeakHashMap<corePos, float[]>`，长度 = 当前阶的台数（2 阶 4 台 → 5 阶 24 台）。台数变化时重建数组并保留已有分量（与 `boltEnvelopes` 现有做法一致）。渐变长度复用 `FX_RAMP_TICKS`。

台数上限为结构上限，L5 为 24，远小于 `MAX_CHANNELS = 64`。

### D5 — 颜色分工

焦点核与激光 MUST NOT 同为绿色，否则 24 条绿激光叠在绿核上糊成一团。故：核为绿（沿用 `spirit_orb.json` 的 `Tint` 绿 `0.42,1.0,0.55` 同族），激光为**青白**（拟 `#C8F0FF`）。激光复用 `fx/bolt_core.png` + `fx/bolt_glow.png`，**零新贴图**。

### D6 — 灵气场从"球"改为"场"

当前 `spirit_orb.fsh` 是 `rim = pow(1-ndv, 2.2)` + `shell = rim²×0.9 + rim×0.35`，边缘衰减偏快 → 读作一颗有边界的球。改为：

- fresnel 指数下调（2.2 → 约 1.2），使衰减沿视线更平缓；
- 增加一项低密度内填充（如 `density = 0.16 + shell`），使体内不是全透、也不是实心；
- 顶点 alpha 由 220 下调（与新焦点核共存时，场必须退到背景）。

半径与悬高沿用 `FX_ORB_*` 现有键，不改数值——先只改观感，等实机看到再调。

**验收标准**：场的外缘 MUST NOT 出现可辨识的硬轮廓；画面里唯一有明确边界的东西 SHALL 是焦点核。

### D7 — BufferSource 别名规则

`MultiBufferSource#getBuffer(不同 RenderType)` 会**立即结算上一批**。因此激光 MUST 按 RenderType 分趟提交（先取光晕缓冲写完全部光晕段，再取亮芯缓冲写完全部亮芯段），MUST NOT 先把所有 consumer 取出来再交叉写。`RitualCoreRenderer:266-268` 的注释即此坑（首测崩溃 "Not building!"）。

## Risks / Trade-offs

- **焦点核是不透明的**，会挡住核心方块本身的一小片视野。位置在顶面 +1.5、半径 1，遮住的是核心上方的空气，风险可接受；若实机觉得挡视线，把半径做成可配置下调即可。
- **客户端本地判定与服务端判定重复实现**，存在漂移风险。缓解：判据保持为两条（`instanceof SpiritCoreItem` + `tier <= 仪式阶`），并在两侧的注释里互相指认；一旦服务端判据变化，MUST 同时改客户端（`RitualRenderStateTest` 一类的编译期断言无法覆盖此跨端一致性，只能靠代码评审与实机对照）。
- **24 台全满时 24 条激光同帧绘制**。每条为有限段数的米字面片，总量为台数有界的常数，符合「网格优先的特效总则」的无预算上限要求。
- **`ClientRitualData` 的缓存加载时机**：若某客户端在 pattern 缓存就绪前就渲染出核心，祭品台列表会是空的，表现为"焦点核亮着但没有激光"。缓解：列表为空时直接不画激光（而非画 0 条的错误几何），并依赖 `RitualDataSyncPayload` 在登录时下发这一既有事实。
