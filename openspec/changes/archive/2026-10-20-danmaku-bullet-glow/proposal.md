# 球弹本体与外发光：单通贴图化 + 6000 弹对照压测

## Why

普通圆弹现在**只有一层贴图**，而这一层既不是"球"也没有"发光"：

- 现役贴图 `sphere_danmaku_lod.png` 的实心核只占贴图宽度的 **43.75%**、占面积 **15%**，
  核外是一圈**全透明**的环缝（d 14~20），再外面是**三档硬边台阶**（alpha 70 / 140 / 25，d 20~29）。
  视觉结果是一个**甜甜圈**，不是一颗球——这是"单调"的主要来源，与"有没有辉光"无关。
- 辉光层代码**还在**（`AbstractDanmakuRenderer#renderGlow` / `glowRenderType` /
  `Profile.glowScale=1.35` / `glowAlpha=110`），但被
  `DanmakuRenderProbe.ALWAYS_LOD = true` → `dense ≡ true` → `effectiveGlow() ≡ false`
  **永久短路**，实体路径与程序路径都只剩 1 个 quad / 1 次 draw。
- 当年砍掉三层的理由是**填率**而非 draw call
  （`2026-10-07-danmaku-dense-render-lod/design.md`：800×3 层 = 9600 顶点 / 2400 getBuffer，
  "body-only ≡ 三层时间中的 1/3"，"Double additive 未排序后单体负载仍不可控，**只能靠减遍数**"，
  "不做 GPU instancing：fragment/过绘的主因仍在"）。

也就是说：**为了活下去，把"球"和"光"一起扔了**。本变更要把这两样东西在**单通**前提下拿回来。

## What Changes

### A. 球弹视觉（主体）

1. **新贴图承载本体与外辉光，单 quad / 单 pass / 单 draw。**
   球弹的观感由**一张贴图**表达：实心本体 + 平滑外晕。不新增任何渲染层、
   不新增 draw call、不新增顶点。
2. **本体恢复成"球"。** 实心区从 43.75% 宽恢复到 ~60% 宽，中间不再有透明环缝；
   边缘从三档硬台阶改为连续衰减。
3. **辉光随 size 等比。** 贴图映射到 `size × size` 的 quad，故辉光半径天然随弹体尺寸缩放，
   无需额外参数。
4. **写深度保留。** 沿用 `translucentDepth`（写深度），辉光区与本体区一并写深度。
   这是**现状**，不是新增开销。
5. **实体路径与程序路径共用同一张贴图与同一套常量。**
   `ProgramDanmakuRenderer` 目前自己 hardcode 了 `SPHERE` 与 `bodyType`，与
   `SphereDanmakuRenderer` 各写各的——本次一并收敛，否则两条路径观感分叉。

### B. 压测对照（验收手段，与 A 同等重要）

6. **`/danmaku wall` 弹数上限 2000 → 6000。**
7. **新增 `/danmaku screen <数量> [距离] [间距]`**：静止、无害、正对玩家的弹墙，
   用于把**屏上弹数**变成可控变量（见 design.md 的几何分析：现有 `wall` 的弹数
   与屏上弹数不是线性关系，6000 弹墙里只有约一半在视锥内）。
8. **新旧贴图可在同一构建内热切换**（`/danmaku texture lod|glow`），
   使对照压测不必换构建、不必换二进制。
9. **修复渲染剖针**：当前 `glow`/`core` 计数恒为 0（层被永久短路），
   `/danmaku layers` 已失去诊断意义；本变更让剖针能报出**实际绘制层数与顶点/缓冲数**。

### C. 明确不做

- **不复活三层结构**，不加任何额外 pass，不做 GPU instancing，不做粒子外发光。
- **不改模型类几何**（五角星柱 `STAR_PRISM`）：其顶点开销特征与四边形不同，另行处理。
- **不改灵符**：其外发光沿 +Y 偏移，已有"仰视时无光晕"的已知瑕疵，属另一条线。
- **不改激光**：激光本就豁免密度 LOD，四层（亮核/主体/外发光/端盖）今天全开，观感已可。
  仅在压测中一并取基线，确认新贴图不影响激光路径。
- **不引入新的混合模型、不引入自定义着色器**：本变更走**纯贴图**路线
  （唯一限制见 design.md 的 D5）。
  唯一允许的渲染类型改动是给 `DanmakuRenderTypes` 加一个**带线性过滤 + mipmap 的
  `translucentDepth` 变体**（混合 / 写深度 / 剔除状态完全复用，只换采样状态）——
  现役一律 `setFilter(false, false)`（最近邻、无 mipmap），对软晕是灾难。

## Capabilities

### New Capabilities

（无。本变更不引入新能力面。）

### Modified Capabilities

- `danmaku-sphere`: 「Sphere visual glow effect」由"渲染一个独立的外发光层"
  改为"本体与外辉光由**同一张贴图**承载、单通渲染"；
  「Sphere texture asset」追加本体/辉光的构图与通道语义规定。
- `danmaku-render-state`: 「密集模式下弹幕渲染必须用单通解析」改为
  "球弹**恒为**单通、密度切换不再是观感变量"；
  新增「弹幕渲染 SHALL 可在 6000 弹规模下做新旧贴图对照压测」一条。
- `danmaku-visual-profile`: 「核心层与发光层半径可调」追加
  "四边形几何的球弹辉光**由贴图承载**，档案的发光字段 MUST NOT 被当作球弹辉光半径的调节手段"。

## Impact

**代码**

- `SphereDanmakuRenderer`：本体层贴图指向新图；`renderGlow` 对四边形几何不再调用
  （它今天已被 probe 短路，此处是把它从"靠短路生效"变成"明确不适用"）。
- `ProgramDanmakuRenderer`：`SPHERE` 常量指向新图；`bodyType` 与实体路径共用同一 RenderType。
- `DanmakuRenderTypes` / `DanmakuRenderProbe`：不变（沿用 `translucentDepth`）。
- `DanmakuTestCommands`：`/danmaku wall` 上限 6000；新增 `/danmaku screen`；新增 `/danmaku texture`。
- `tools/textures/`：新增球弹贴图的数据文件（`tools/gen_tex.py` 的 ASCII + 调色板管道，
  或 `tools/gen_danmaku_textures.py` 的超采样管道——软晕需要后者，ASCII 硬边做不出连续衰减）。

**资源**

- 新增 `assets/gensokyou/textures/entity/sphere_danmaku_glow.png`（**新文件名，不改旧图**：
  旧图的 alpha 语义与新图不同，资源包替换旧图不应被静默改变语义）。
- `sphere_danmaku_lod.png` 保留（作为对照臂与回滚点）。

**spec**

- `danmaku-sphere`、`danmaku-render-state`、`danmaku-visual-profile` 三份主 spec 的
  对应 requirement 被本变更的 delta 取代。

**不涉及**

- 服务端判定、同步、协议、存档：本次**零**服务端改动。
- 平衡数值：`BalanceNumbers.DANMAKU_ENTITY_CAP = 2000` **不动**。
  `/danmaku wall` 走 `addFreshEntity` 直连，**不经** `DanmakuEmitter`，
  故不受该上限节流；压测靠命令参数上限放开，而不是动平衡常量。

## 验收

1. **观感**：新贴图在暗背景下读作"一颗发光的球"（有实心本体 + 软外晕），
   MUST NOT 读作环/甜甜圈；亮背景下的表现按 design.md D6 记为已知限制。
2. **性能对照**（硬门槛）：
   - 夹具：`/danmaku screen 6000 <距离> <间距>`，同一机位、同一客户端、同一构建热切换贴图。
   - 指标：客户端帧时间 p50 / p95 / p99（30s 暖机 + 180s 采样），
     外加 `DanmakuRenderProbe` 的 draw / 顶点 / getBuffer 计数。
   - 门槛：**新贴图臂的 p95 帧时间相对旧贴图臂的劣化 MUST ≤ 5%**；
     若超出门槛，按 design.md D7 的降级阶梯收窄辉光，直到达标。
3. **规模**：压测在 6000 弹下完成，且 MUST 同时记录 2000 弹档位，
   用于回答"原来的 2000 弹上限到底在哪"。
4. **深度**：辉光区写深度后，弹后方的水/云被弹的软圆轮廓遮挡，
   MUST NOT 出现方形空洞。
5. **两条路径同观**：实体弹幕与程序化弹幕在同一场景下的球弹 MUST 呈现同一贴图。
