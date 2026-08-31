# Proposal: sukima-portal-billboard

## Why

隙间传送门目前用原版末地门渲染类型（`RenderType.endPortal`）绘制斜 10° 眼形封闭棱壳，内景是程序化星空——与东方 Project 中隙间「漆黑虚空 + 充满眼睛」的美术方向不符，且末地门着色器无法呈现正式素材。项目已购入隙间正式美术（`textures/entity/sukimatexture.png`，1254² 眼睛虚空图），需要接入渲染；同时未来传送门类技能（瞬移演出、boss 拉人等）需要可复用的传送门绘制基建，当前 90 行棱壳几何与末地门耦合太深，不便复用。

## What Changes

- **BREAKING（视觉重写）**：删除眼形封闭棱壳（前后透镜端面 + 环形侧壁 + 末地门渲染类型）的全部几何；传送门改为 **0 厚度 billboard 单 quad**（宽 1 格 × 高 2 格，保持眼形比例），每帧朝向玩家相机，任意角度均可见。
- **新贴图**：`sukimatexture.png` 经离线烘焙（降采样至 256²、眼形剪影 alpha 遮罩）产出 `textures/entity/sukima_portal.png`（RGBA），作为 billboard 主贴图；眼形遮罩为硬性需求（隙间必须是眼形）。
- **动画**：贴图 UV 随游戏时间缓慢滚动（自定义 RenderType 设 REPEAT wrap），让虚空缓慢流动。
- **眼睑描边层保留**：现有 16×32 眼形描边贴图（`sukima.png`）以主 quad 前方 0.001 偏移的第二个 quad 覆盖，双层结构。
- **复用基建**：billboard quad 绘制收敛为静态工具类 `SukimaPortalQuads`（参数化尺寸/贴图/UV 滚动相位/染色/亮度/透明度），BER 与未来的技能实体渲染器共用；自定义 RenderType 仿照既有 `DanmakuRenderTypes` 模式新增。
- **不影响**：`SukimaBlock` 的 `entityInside` 维度传送、传送冷却、结界仪式放置/移除逻辑全部不变；方块本体仍为空模型、不可破坏、可穿行。

## Capabilities

### New Capabilities

（无——传送门视觉仍归属既有 `sukima-portal-rendering` 能力。）

### Modified Capabilities

- `sukima-portal-rendering`：核心渲染需求反转——「斜 10° 眼形封闭棱壳 + 末地门静态星空内景」改为「0 厚度眼形 billboard + 自有隙间虚空贴图 + UV 滚动动画」；「侧视不消失」「轮廓对齐」场景按 billboard 语义重写；「方块本体视觉不可见」「传送机制零改动」两条需求不变；新增「billboard 绘制工具可复用于技能实体」需求。

## Impact

- **Java（客户端）**：
  - `client/renderer/SukimaPortalRenderer.java` — 重写：删棱壳几何，改 billboard 双层绘制（主贴图 + 描边）
  - 新增 `client/renderer/SukimaPortalQuads.java` — 共享 billboard 绘制工具（BER/ER 通用）
  - `client/renderer/DanmakuRenderTypes.java`（或同级新增）— 新增隙间传送门 RenderType（cutout/translucent + REPEAT wrap + 全亮度）
  - `client/GensokyouTextures.java` — 新增 `SUKIMA_PORTAL` 常量
- **资产**：新增 `textures/entity/sukima_portal.png`（离线烘焙产物）；保留 `sukimatexture.png`（源素材）与 `sukima.png`（描边层）
- **工具**：`tools/textures/` 新增烘焙脚本（降采样 + 眼形遮罩，复用 `SukimaPortalRenderer` 现有眼形函数 `sqrtHalf` 的几何约定）
- **不受影响**：服务端代码、`SukimaBlock`/`SukimaBlockEntity` 逻辑、传送/仪式机制、config
- **已知取舍**：billboard 背面呈镜像贴图（眼形左右对称，可接受）；描边层从「前后两面」减为「单面 + 偏移」
