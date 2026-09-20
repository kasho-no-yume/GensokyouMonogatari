# Proposal: redesign-danmaku-visuals

## Why

弹幕系统当前是"像素描边风"：球弹贴图烤死了红色（非红色弹染色后发黑）、札弹贴图深灰发闷、激光发射端缺乏视觉锚点。整体观感与东方原作"白心 + 色边 + 光晕"的柔光弹幕质感差距明显，需要统一重做表现层。激光与球弹已有的多层渲染管线（亮核/主体/外发光）结构良好，本次在其上重制贴图与分层，不动行为逻辑。

## What Changes

- **球弹重绘（方案 B）**：新贴图 64×64 高清灰白径向渐变（边缘 alpha 平滑衰减，去色设计，颜色完全交给顶点色）；渲染改为三层结构——白色亮核（加法混合、约 0.5×、恒白不染色）+ 渐变主体 × 实体色（本体 RenderType 从 cutout 换 translucent 以支持柔和边缘）+ 现有外发光层保留
- **札弹（跟踪弹）重绘**：新贴图高清白色符纸（去色，染实体色后颜色正确），替代深灰像素纸片；几何与外发光结构不动
- **激光五芒星法阵**：新贴图 256×256 白色线条五芒星 + 外圈环（透明底、去色），程序化高清绘制 + 超采样抗锯齿（非像素画）；法阵在激光 ACTIVE 期渲染于发射端（垂直于光束平面），随激光粗细包络展开/收起，期间持续绕 Z 轴自旋，加法混合染激光实体色
- **预警瞄准线不变**：DELAY 期红色闪烁指示线行为与观感保持现状
- **刀弹不动**

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `danmaku-sphere`: 视觉需求重写——贴图从 16×16 红色像素球改为 64×64 灰白渐变贴图；渲染结构从"本体 + 外发光"两层改为"亮核 + 主体 + 外发光"三层；本体渲染类型从 cutout 改 translucent。行为需求（生成/碰撞/寿命/速度突变 API）不变
- `danmaku-talisman`: 视觉需求修改——贴图从深灰像素纸片改为高清白色符纸（去色、染实体色）；几何与行为需求不变
- `danmaku-laser`: 新增视觉需求——ACTIVE 期发射端渲染五芒星法阵（随包络展开/收起、自旋、染色）；DELAY 期指示线需求不变；其余行为需求不变

## Impact

- **贴图**（新增/替换，程序化生成）：
  - `assets/gensokyou/textures/entity/sphere_danmaku.png`（重制，64×64）
  - `assets/gensokyou/textures/entity/talisman_danmaku.png`（重制，高清）
  - `assets/gensokyou/textures/entity/laser_magic_circle.png`（新增，256×256）
- **代码**：
  - `SphereDanmakuRenderer.java` — 三层结构、亮核层、RenderType 调整
  - `TalismanDanmakuRenderer.java` — 仅换贴图引用（如尺寸/UV 不变则零改动）
  - `LaserDanmakuRenderer.java` — 新增法阵渲染（ACTIVE 期，包络驱动）
  - `DanmakuRenderTypes.java` — 可能新增法阵用加法混合 RenderType（复用现有 `additiveGlow`/`additiveSolid` 亦可）
- **风险**：贴图分辨率变化不影响几何（UV 归一化）；法阵为纯客户端表现，无网络/逻辑改动；加法混合层叠加顺序需遵守 immediate 缓冲别名规则（激光渲染器注释中已记录）
