# Proposal: sukima-portal-billboard

## Why

隙间传送门目前用原版末地门渲染类型（`RenderType.endPortal`）绘制斜 10° 眼形封闭棱壳，内景是程序化星空——与东方 Project 中隙间「漆黑虚空 + 充满眼睛」的美术方向不符，且末地门着色器无法呈现正式素材。项目已购入隙间正式美术（`textures/entity/sukimatexture.png`，1254² 眼睛虚空图），需要接入渲染；同时未来传送门类技能（瞬移演出、boss 拉人等）需要可复用的传送门绘制基建，当前 90 行棱壳几何与末地门耦合太深，不便复用。

## What Changes

- **BREAKING（视觉重写）**：删除眼形封闭棱壳的侧壁与封闭结构（前后透镜端面保留眼形剪影）；传送门改为 **0 厚度眼形窗**：整体 billboard 每帧正对玩家（相机四元数 + Y 180° + 绕视线轴 10° 倾角），内景为眼形透镜面几何填原版**末地门渲染类型**（屏幕投影锚定 + 16 层 GameTime 分层漂移 = 「锚定的虚空」，与框分离），无侧壁。
- **内景机理**：末地门着色器自裁剪空间投影采样（实测 shader 源码确认），效果锚定屏幕而非几何表面——billboard 下依然呈「窗」质感，不会糊成静态图；输出不透明、不吃环境光（全亮度固有）。
- **眼睑描边层保留**：16×32 眼形描边贴图（`sukima.png`）cutout 单层 quad，billboard 局部 +z 偏移 0.001 覆盖于内景前方。
- **复用基建**：描边绘制收敛为 `SukimaPortalQuads`（参数化尺寸/贴图/UV 相位/染色/亮度/透明度，BER/ER 共用）；自定义 `SukimaPortalRenderTypes.portal()` 保留为未来技能的贴图传送门能力。
- **自定义着色器**：新增 `gensokyou:sukima_portal` 核心着色器（克隆原版 end portal 机理：裁剪空间投影采样 + 分层视差），黑红调色板、层缩放 2.35、6 层——背景呈偏黑黑红、眼睛密度降约一个数量级；经 `RegisterShadersEvent` 注册。
- **不影响**：`SukimaBlock` 的 `entityInside` 维度传送、传送冷却、结界仪式放置/移除逻辑全部不变；方块本体仍为空模型、不可破坏、可穿行。

## Capabilities

### New Capabilities

（无——传送门视觉仍归属既有 `sukima-portal-rendering` 能力。）

### Modified Capabilities

- `sukima-portal-rendering`：渲染需求反转——「斜 10° 眼形封闭棱壳」改为「0 厚度眼形窗（billboard 正对玩家 + 视线轴 10° 倾角；眼形透镜面内景保留末地门渲染类型，无侧壁）」；「方块本体视觉不可见」「传送机制零改动」两条需求不变；新增「绘制工具可复用于技能实体」需求。

## Impact

- **Java（客户端）**：
  - `client/renderer/SukimaPortalRenderer.java` — 重写：删棱壳侧壁，改 billboard 眼形窗（末地门内景 + 描边框）
  - 新增 `client/renderer/SukimaPortalQuads.java` — 共享 billboard 绘制工具（BER/ER 通用）
  - 新增 `client/renderer/SukimaPortalRenderTypes.java` — 虚空内景 RenderType（自定义着色器 + 双槽位自有贴图）与贴图传送门 RenderType（translucent + REPEAT wrap，未来技能预留）
  - `client/GensokyouClient.java` — 新增 `RegisterShadersEvent` 处理器注册隙间虚空着色器
  - `client/GensokyouTextures.java` — 新增 `SUKIMA_PORTAL` 常量
- **着色器资产**：新增 `shaders/core/sukima_portal.{json,vsh,fsh}`（克隆 end portal 机理，黑红调色板可调）
- **资产**：新增 `textures/entity/sukima_portal.png`（离线烘焙产物，当前渲染未引用、备用）；源素材移至 `tools/textures/sukimatexture.png`；保留 `sukima.png`（描边层）
- **工具**：`tools/textures/` 新增烘焙脚本（降采样 + 眼形遮罩 + 无缝化，与渲染侧眼形几何约定同源）
- **不受影响**：服务端代码、`SukimaBlock`/`SukimaBlockEntity` 逻辑、传送/仪式机制、config
- **已知取舍**：billboard 穿行时近裁剪面单帧闪烁（固有现象）；内景为末地门星空、不含眼睛素材元素（自定义着色器另立 change）；眼形剪影由几何承载（endPortal 不采样贴图）
