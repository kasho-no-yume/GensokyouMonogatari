# Design: sukima-portal-billboard

## Context

现渲染器 `SukimaPortalRenderer`（188 行）画斜 10° 眼形封闭棱壳：`renderShell` 用 `RenderType.endPortal()`（POSITION 格式、无 UV）填前后端面+侧壁，`renderOutline` 用 cutout 贴 16×32 描边。素材 `sukimatexture.png`（1254² RGB 无 alpha，眼睛虚空图）待接入。项目已有两块可复用基建：`BillboardRenderer`（实体 billboard：`cameraOrientation()` + Y 轴 180° 翻转）与 `DanmakuRenderTypes`（自定义 RenderType 模式）。眼形几何约定：尖端在中线，上盖 0.85、下弧 1.15（格），半宽 0.5，`sqrtHalf(t)` 弧函数。

## Goals / Non-Goals

**Goals:** 棱壳 → 0 厚度 billboard；接入烘焙后的隙间虚空贴图 + UV 滚动；描边层保留；绘制收敛为 BER/ER 共用工具；传送逻辑零改动。

**Non-Goals:** 不实现技能实体/网络包（仅预留工具 API）；不做自定义 shader；不改动 `SukimaBlock`/`SukimaBlockEntity` 服务端行为；不做贴图运行时再加工。

## Decisions

### D1 billboard 朝向：`camera.rotation()` 四元数，不用几何计算
`poseStack.mulPose(camera.rotation())` 后 Y 轴转 180°（沿用 `BillboardRenderer` 验证过的绕法）。BER 内经 `Minecraft.getInstance().getBlockEntityRenderDispatcher().camera` 取相机（或 main camera，实现时择一常量注入），ER 内用既有 `entityRenderDispatcher.cameraOrientation()`。
备选（否决）：手动算 yaw/pitch 欧拉角——绕序/翻转易错，已有验证过的四元数路径。

### D2 贴图烘焙：离线 Python 脚本，256² + 眼形遮罩
`tools/textures/sukima_portal_bake.py`：PIL 打开 `sukimatexture.png` → LANCZOS 降采样 256² → 按眼形参数（上盖 0.85 / 下弧 1.15 / 半宽 0.5，与渲染几何同源）逐像素算 alpha 遮罩（弧线内侧不透明、外侧透明，边缘 1px 渐变防锯齿）→ 输出 RGBA `sukima_portal.png`。UV 映射沿用现约定：U=0..1 ↔ x=-0.5..0.5，V=0..1 ↔ 上盖顶..下弧底。
理由：与项目「ASCII 像素图/工具产图」资产管线一致；运行时处理（NativeImage）徒增客户端复杂度且 1254² 源图无需进包。

### D3 RenderType：自定义 `SukimaPortalRenderTypes.portal(texture)`
`RenderType.create`：translucent（烟雾边缘柔和；单 quad 无自重叠，排序安全）+ `TextureStateShard(texture, false, false)`（clamp=false → REPEAT，UV 滚动所需）+ noCull（背面可见）+ 全亮度（光照不取世界光，顶点写 `LightTexture.FULL_BRIGHT`）。
备选（否决）：cutout——硬裁掉烟雾柔边；vanilla `entityTranslucentCull`——clamp=true 无法滚动 UV。

### D4 UV 滚动：顶点 UV 相位偏移，参数传入工具
`SukimaPortalQuads.draw(..., float uPhase, float vPhase)` 内对 UV 加相位后取模；BER 每帧传 `(0, -gameTime*speed + partialTick*speed)`（负号 = 虚空向上吸入感，方向实现时目检调整）。滚动速度作常量（如 1/64 每秒），必要时后续提为 config。

### D5 双层结构：主 quad + 描边 quad（局部 +0.001 z 偏移）
描边沿用 `RenderType.entityCutoutNoCull(GensokyouTextures.SUKIMA)` 与 NEW_ENTITY 顶点格式；主 quad 亦用 NEW_ENTITY 格式以统一顶点写入（染色/overlay/光照字段顺带齐备）。绘制顺序：先主后描边。
背面镜像：noCull 单 quad 背面自然镜像；眼形与贴图左右对称，不额外画背面翻转 quad。

### D6 复用工具 API：纯函数 + 全参数化
```java
SukimaPortalQuads.draw(PoseStack, MultiBufferSource,
    ResourceLocation texture, float halfW, float halfH,
    float uPhase, float vPhase, int rgbaTint, int alpha, int light)
```
不依赖任何项目类型（除贴图常量）；描边层为 BER 侧的第二次调用（不同贴图/z 偏移），不进工具。渲染包围盒维持现 `AABB` 上扩 1 格（2 格高 quad 防视锥裁剪）。

### D7 渲染器重写后删除的代码
棱壳条带几何（`renderShell`/`shellQuad`/`shellVertex`/`sqrtHalf` 的渲染侧使用）、`endPortal` 渲染类型引用、10° 倾角常量全部删除；眼形弧函数移入烘焙脚本（渲染侧不再需要）。

## Risks / Trade-offs

- [billboard 与方块选择框错位] → 视觉居中于方块（中心 y=1.0 处，跨两格），选择框维持单格；属既有状态，目检确认即可
- [REPEAT wrap 下 UV 取模精度] → 相位用 float 取模到 [0,1) 后再写入顶点，长时间运行无漂移
- [translucent 与相邻半透明块排序] → 单 quad 自身无重叠；与其它 translucent 排序由 vanilla 距离排序处理，风险低
- [1254² 源图保留在 textures/entity 目录] → 不被引用即不进构建产物感知路径，但会进 jar；可选移至 `tools/textures/` 源素材区（实现时决定，倾向移出）
- [背面镜像] → 眼形+眼睛贴图对称，接受；若未来换非对称贴图，工具加 `mirrorBack` 参数即可

## Migration Plan

纯客户端视觉替换，无数据迁移。回滚 = git revert；`sukima.png` 描边贴图与传送逻辑全程未动。

## Open Questions

- 滚动方向（吸入 vs 吐出）与速度：目检定，速度是否提 config 后议。
- 源素材 `sukimatexture.png` 是否移出 assets 目录：倾向移至 `tools/textures/`，待实现时确认。
