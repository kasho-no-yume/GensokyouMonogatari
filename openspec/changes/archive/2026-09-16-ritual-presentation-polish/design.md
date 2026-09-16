# Ritual Presentation Polish — Design

## Context

祭品台两态姿态（静置平躺 / 激活立起悬浮自转）由 a5dbb3b 落地，实机回归：**未激活台面物品躺在约高半格处，且无自转**。无自转 ⇒ 过渡进度 eased≈0 ⇒ 抬升不来自激活态残留，锅在静置绘制路径本身。
- 静置渲染链：`RitualPedestalRenderer` translate(0.5, REST_Y=1.01(+BlockItem 时 +BLOCK_HALF_HEIGHT=0.275), 0.5) → YP(0) → XP(90°) → scale(0.55) → `ItemRenderer.renderStatic(FIXED)`。
- 嫌疑排序：① BlockItem 烘焙模型以角点为枢轴，`BLOCK_HALF_HEIGHT` 按中心写 ⇒ 姿态偏置；② 自定义模型自带 `fixed` 平移；③ REST_Y 基准不符（可能性低）。

新增三项（实机确诊）：
- **姿态状态残留**：`RitualPedestalRenderer.ANIM`（pos → {progress, lastTime}）只在 `render()` 内推进；`held` 为空时 `render()` 提前 return ⇒ FLIGHT 清空台面后进度冻结在 ≈1。新物品放入时 `advanceBlend` 以 clamp(4) 的大 dt 让进度 0.24/帧 回落，叠加自转 `(float) time * SPIN * eased` 用绝对 gameTime ⇒ 数帧内暴旋。
- **飞行位置同步**：`ZaohuaFlightItem` 客户端 `setPos`、服务端不动。反编译源实证：`ServerEntity.sendChanges()` 的 `flag5 = flag4 || tickCount % 60 == 0` 使静止实体每 60 tick 也补发 `ClientboundMoveEntityPacket.Pos`；1.21.1 `Entity.lerpTo` 无插值直接 `setPos`（`lerpTargetX()` 即 `getX()`）⇒ 一帧拉回起点。
- **炎柱超范围**：`RitualFxLayout.flamePillars` 环半径 = `radiusMax(0.42/0.70/0.98) × (0.88~1.12)`，`radiusMax = max(2, structureRadius)`；加每台 `perPedestalBase + tier` 根复制柱。结构为半径 ~9 的圆 ⇒ 火柱出圈、观感复制。

## Goals / Non-Goals

**Goals:**
- 静置态三类物品（2D / 方块 / 3D）底缘贴台，修复悬浮半格。
- 激活性态成为纯渲染态：不持久化、核心为单一事实源。
- 台面姿态表现状态无跨占位残留；自转由相位累积，任何切换不暴旋。
- 造化飞行位置纯客户端、无视服务端位置包，无闪现帧。
- 迦具土燃烧为收束于结构水平半径内的贴地烈火场，无炎柱复制体。

**Non-Goals:**
- 仪式逻辑/结算/配方/产出平衡。
- 焰场以外的其它特效（雾带/闪电/灵气球）。
- GPU 逐帧热扭曲 shader（本期以几何 + 粒子 + 辉光近似）。

## Decisions

### D1 模型感知底缘对齐（原「探针先行」方案已废弃）

实现期对着 1.21.1 反编译源核出原版 FIXED 渲染链的**真实顺序**：
`handleCameraTransforms(先套 display.fixed) → translate(-0.5,-0.5,-0.5)（几何归中） → 画 [0,1]³ 几何`。
据此推翻原两预案（它们都建立在"几何未归中"的错误假设上）：
- 2D 生成物静置本就贴台（归中后竖直半高仅 ~0.017×SCALE）；
- BlockItem 因代码按 `0.5*SCALE` 预抬，而 `block/block` 的 `display.fixed` 自带 `scale 0.5`，等于多抬一半 → 浮高 ~0.1375；
- 自定义 3D 物品的偏移由自身 `display.fixed` 平移/缩放决定（"半格"最可能来源）。

决策：**放弃经验常数与实机探针，改按实测包围盒对齐**。
- 新增 `client/renderer/ItemFixedBounds`：读 BakedModel 烘焙几何得 raw AABB，复刻 `FIXED·T(-0.5)` 变换得 inner AABB
  （按 `Item` 缓存；`isCustomRenderer`/无几何回落单位立方体）。
- 静置：`restY = SURFACE_Y + REST_GAP + SCALE*maxZ`（θ=90°，底缘贴台）。
- 激活：`activeY = SURFACE_Y + ACTIVE_GAP - SCALE*minY`（θ=0，底缘离台 ACTIVE_GAP，不切台面）。
- 删除 `REST_Y`/`FLOAT_Y`/`BLOCK_HALF_HEIGHT` 三个经验常数。
- 已知取舍：仅对齐**竖直**底缘；水平居中沿用原版 `translate(-0.5)` 归中（标准模型已居中，偏心自定义模型不在本期）。

### D2 `ritualActive` 去持久化（原）
BE 不再读写 `TAG_RITUAL_ACTIVE`；核心在 `setEnabled`、`stop`、结构重扫/加载重新成型处向台位广播。旧档多余键静默忽略，零迁移。

**实测踩坑（必须记）**：去掉 `RitualActive` 后，`saveAdditional` 在「台面清空」时不再写入任何键 ⇒ update tag 变**空**。
NeoForge 的 `IBlockEntityExtension#onDataPacket` 默认实现在 tag 为空时**直接跳过** `loadWithComponents`：

```java
if (!compoundtag.isEmpty()) { self().loadWithComponents(compoundtag, lookupProvider); }
```

于是客户端永远收不到「清空」，残留上一件物品的渲染，直到重登/区块重载才消失（放置正常因为 tag 非空）。
修法：`RitualPedestalBlockEntity` **覆写 `onDataPacket` 无条件 `loadWithComponents`**，保证空 tag 也把 `held` 清成 EMPTY。
（核对此前能工作：旧代码总写 `RitualActive`，tag 从不为空。）

### D3 客户端 ANIM 缓存语义（原，由 D4 修正）
`ANIM`（pos → `Pose`）的 WeakHashMap 保留，但推进改名 `advanceProgress` 且不再受 `held.isEmpty()` 早退影响（见 D4）。

### D4 姿态状态清零 + 自转相位累积

- **状态清零**：`ANIM` 值改为 per-pos `Pose{progress, lastTime, spinDeg}`；`held` 为空时**移除该 pos 条目**（不再依赖 `render()` 推进来衰减）。这样 FLIGHT 吞掉台面物品后不再留下冻结进度；下一次放入从 progress=0、spinDeg=0 起算。
- **自转相位**：`spinDeg += SPIN_DEGREES_PER_TICK * eased * dt`（dt = 本帧推进的游戏刻增量，clamp 防跳帧；`eased` 为 smoothstep 后的进度）。eased=0 时增量为 0（自然停旋），相位**冻结而不重置**（避免平躺瞬间偏航跳变）；台面清空时相位随 `Pose` 一并移除。**MUST NOT** 使用 `(float) time * SPIN * eased`（绝对时间 × 进度）——该式在 eased 变化的任一帧都会扫过 `Δeased × time × SPIN` 度。
- **同帧一致性**：清零与相位推进都发生在同一个 per-pos 状态对象，渲染读取同一事实源。
- 备选：直接用 progress 当相位（转角 = progress × 常数）——否决：转速与过渡进度刚性耦合，观感死板；独立相位能表达「加速起旋 / 减速停旋」。

### D5 飞行位置客户端独占

- `ZaohuaFlightItem` 覆写 `lerpTo(double x, double y, double z, float yRot, float xRot, int steps)`（1.21.1 唯一签名）为空操作。客户端 `tick()` 的 `pathPos` 解算即唯一位置来源；`xo/yo/zo` 由 `ClientLevel.tickNonPassenger` 的 `setOldPosAndRot()` 正常推进，插值平滑。
- 服务端 tick 不调用 `lerpTo`，覆写对其无影响；服务端仍不移动实体（异常掉落改用曲线解算现值），保持「零逐 tick 发包」。
- 说明：`ServerEntity` 的 60 tick 无条件位置包无法按实体关闭，仍会发出但被忽略，视觉零影响。不引入逐 tick 服务端位置包。
- 备选：服务端沿同曲线移动（`OrbitYinYangOrb` 范式）——否决：实体逐 tick 位移会触发逐 tick 位置包，违背「纯表现不同步」。

### D6 迦具土贴地烈火场

- **布局替换**：`RitualFxLayout.fireBed(core, pedestalPos, tier, structureRadius, ...)` 取代 `flamePillars`。以核心为圆心，在 `R = structureRadius`（下限回落 2）内用确定性分层采样 + 抖动铺点；**半径硬钳到 `structureRadius`**（含抖动后也 ≤ 上限），边缘点做 alpha 衰减。MUST NOT 保留「每台 `3+tier` 根复制柱」。
- **几何**：
  - 贴地低伏火舌：复用 `FxGeometry.emitCrossPlanes`，高约 0.6~1.6 格（随阶级），宽约 0.5~0.9；底缘 alpha 高、顶缘 0。
  - 地面脉动辉光：每点/环带一张水平 `emitCrossGlow`，`sin(now × rate)` 脉动，阶级越大越亮越大 —— 强化「被炙烤」。
  - 核心格位加强（火心），作为视觉中心。
- **粒子**：余烬/火星由**客户端本地** `level.addParticle` 低频发射（不依赖服务端 `sendParticles`）；保留既有低频 `LARGE_SMOKE` 服务端点缀。
- **参数**：`fxFire*`（采样密度、半径比、火舌高/宽、辉光强度、脉动速率、UV 滚动、阶级增量）全部进 `GensokyouConfig`，替代原 `fxFlame*`。
- **贴图**：`tools/gen_tex.py` 产出 `textures/fx/fire_bed.png`（贴地余烬/焦痕）与 `fire_tongue.png`（低伏火舌，可由 `flame_column.png` 改写）。
- 备选 A：火焰贴结构方块表面——需同步结构方块坐标（通道扩容、包体增长），否决。
- 备选 B：保留柱体只收半径——仍无法消除复制体观感，否决（用户点名）。

## Risks / Trade-offs

- [探针需用户实机配合，纯静态无法终局定位] → 探针设计为一次进服 2 分钟可完成；调试 overlay 临时 commit 不进主干。
- [fixed 平移自减对「故意用 fixed 平移做姿态」的自定义模型误伤] → 本 mod 与原版资产核查无此类用法；注释写清。
- [去持久化后台子在核心加载前渲染为静置态] → 可接受（渲染态语义）；核心重扫在同一 tick 补广播。
- [D4 相位与进度耦合导致停用不停转] → 相位增量随 eased，eased=0 时增量为 0，自然停旋。
- [D5 覆写遗漏签名] → 1.21.1 `lerpTo` 仅一个签名（已按反编译源核对）；回归覆盖区块往返重载（teleport 路径）。
- [火毯 overdraw 帧率] → 采样点数量封顶、层数/尺寸随阶级有界；BER 继承视锥/距离剔除。
- [火焰场在非圆结构溢出] → 半径硬钳 `structureRadius` + 边缘 alpha；结构半径缺失时回落最小半径。

## Migration Plan

按项分 commit 落地（D1-D3 原议题 / D4 / D5 / D6）；回滚 revert。`RitualActive` 旧档字段静默忽略；`fxFlame*` 配置键更名、旧键废弃（dev 期无迁移义务）。

## Open Questions

- 静置态是否保留极小离隙（1.01 的 0.01 防 z-fighting）——修复后仍保留 ≥0.01 抬升，不算悬浮（沿用原判断）。
- 火毯是否需要核心格位以外的「焦痕」暗色层——加法混合无法压暗，如需暗化需引入非加法 RenderType（本期不做，标记待议）。
