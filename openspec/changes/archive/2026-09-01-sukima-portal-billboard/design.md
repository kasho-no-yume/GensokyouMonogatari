# Design: sukima-portal-billboard

## Context

原渲染器画斜 10° 眼形封闭棱壳（`RenderType.endPortal()` 填端面+侧壁）。项目购入眼睛虚空素材 `sukimatexture.png`（1254²）。项目已有 `BillboardRenderer`（billboard 绕法）与 `DanmakuRenderTypes`（自定义 RenderType 模式）两块基建。

> **修订史（两轮目检反馈）**：
> ① 初版「billboard + 眼睛虚空贴图 + UV 滚动」→ 否决：billboard 使框与内景整体随视角摆动像一张图，UV 上飘诡异；
> ② 二版「世界固定 + 贴图静止」→ 否决：整幅静态贴图糊在框上没有分离感，且要求恢复「一直对着玩家」；
> ③ 本版结论：**内景必须是末地门式「锚定虚空」而非贴图**，框 billboard 正对玩家。本文件为 ③ 版设计。

## 核心洞察：末地门着色器为何能「锚定」

反编译客户端 jar 实测 `rendertype_end_portal.vsh/.fsh`：

```
vsh: texProj0 = projection_from_position(gl_Position)  ← 从裁剪空间(屏幕)投影采样
fsh: color = end_sky(屏幕投影UV) × COLORS[0]           ← 底色屏幕锚定
      + Σ 16 层 end_portal(屏幕投影UV × 层矩阵)         ← 层矩阵含旋转/缩放/
      fragColor = vec4(color, 1.0)                        GameTime 平移 → 视差纵深
```

效果采样坐标是**裁剪空间投影**（屏幕锚定），与几何朝向/贴图无关，且 16 层各异变换产生层叠视差——这就是「窗」质感的机理：**内景不是画在框上的图，而是一扇朝向虚空深处的窗**。因此它可以放在 billboard 上而不破坏锚定感。同时输出不透明（alpha=1）、不吃光照——黑暗环境天然全亮。

## Goals / Non-Goals

**Goals:** billboard 正对玩家 + 绕视线轴 10° 倾角；眼形透镜面内景以**自定义着色器**（克隆 end portal 机理）渲染自有眼睛虚空贴图（0 厚度、无侧壁）；黑红调色板 + 低层密度（目检调参）；眼睑描边框保留；绘制收敛为 BER/ER 共用工具；传送逻辑零改动。

**Non-Goals:** 不实现技能实体/网络包（仅预留工具 API）；不改动 `SukimaBlock`/`SukimaBlockEntity` 服务端行为；不做贴图运行时再加工。

### 目检调参记录（第三轮反馈：黑红背景 + 眼睛减量）

- **背景偏黑黑红**：原版调色板 COLORS[] 为青蓝色系，把暗红纹理压成黑青——乘数封顶各通道上限，改贴图无法逆转 → 克隆 shader 换黑红调色板；底色乘数 `vec3(0.6, 0.32, 0.32)` 压绿蓝保红。
- **眼睛减 80%+**：眼睛密度 = 层数 × 层缩放平铺面积。层数 15→6（JSON `EndPortalLayers`），层缩放 8.5→2.35（`mat2(2.6 - layer * 0.25)`）——平铺密度降约一个数量级，剩余眼更大更稀。

## Decisions

### D1 朝向：billboard 恢复（相机四元数 + YP 180°）+ 视线轴 10° 倾角
`translate(0.5, CENTER_Y, 0.5)` → `mulPose(dispatcher.camera.rotation())` → `mulPose(Axis.YP.rotationDegrees(180))` → `mulPose(Axis.ZP.rotationDegrees(10))`。倾角在 billboard 局部空间施加 = 呈现在视平面内，眼睑恒斜 10°。目检明确要求「一直对着玩家」，二版世界固定方案否决。

### D2 内景：眼形透镜面几何 + 自定义着色器（克隆 end portal 机理）
`gensokyou:sukima_portal` 核心着色器（`RegisterShadersEvent` 注册，vsh 克隆原版、fsh 换黑红调色板与低层参数）+ `MultiTextureStateShard` 双槽位绑定自有眼睛虚空贴图（Sampler0 底色 / Sampler1 层叠视差）。内景剪影由**几何**承载（16 竖条带扇面，`sqrtHalf(t)` 弧函数，正反两面绕序，尖端退化 quad 合法）——着色器输出不透明、不吃光照。仅前后两个透镜面（z=0），无侧壁（0 厚度）。着色器文件：`assets/gensokyou/shaders/core/sukima_portal.{json,vsh,fsh}`；调参点：fsh 的 `COLORS[]`（黑红调色板）、层缩放公式、json 的 `EndPortalLayers`。

### D3 眼睑框：billboard 局部 +z 0.001 偏移的描边 quad
`RenderType.entityCutoutNoCull(GensokyouTextures.SUKIMA)`，经 `SukimaPortalQuads` 绘制。billboard 恒面向相机 → 单层描边足够（局部 +z 即朝观察者），无二版世界固定的对侧深度遮挡问题。描边取世界光（原版行为）。

### D4 贴图烘焙资产：保留备用，当前不进渲染管线
`sukimatexture.png` 留 `tools/textures/`；烘焙产物 `sukima_portal.png`（256² 眼形遮罩 + 无缝化）与 `GensokyouTextures.SUKIMA_PORTAL`、`SukimaPortalRenderTypes.portal()` 保留为未来技能/自定义着色器方案的现成资产，当前渲染不引用贴图内景。

### D5 复用工具 API：纯函数 + 全参数化
```java
SukimaPortalQuads.draw(PoseStack, MultiBufferSource, ResourceLocation texture,
    float halfW, float halfH, float uPhase, float vPhase, int tint, int alpha, int light)
SukimaPortalQuads.draw(PoseStack, MultiBufferSource, RenderType, ...)  // 核心：任意 RenderType
```
不依赖任何项目类型；朝向由调用方施加（本 BER billboard、未来 ER 同绕法）。渲染包围盒维持 `AABB` 上扩 1 格。

### D6 渲染器删除的代码
棱壳侧壁几何、`endPortal` 之外的自定义 RenderType 引用（内景回归 vanilla）、世界固定朝向、UV 滚动相位驱动（着色器自带 GameTime 漂移）。`sqrtHalf`/条带扇面保留（内景几何）。

## Risks / Trade-offs

- [billboard 穿行时近裁剪面闪烁] → 相机穿过 quad 平面固有现象，单帧可接受
- [endPortal 输出不透明 → 内景无半透明柔边] → 眼形剪影由几何精确承载，无需 alpha
- [视觉退离购入素材] → 素材与烘焙资产保留，未来「虚空中的眼睛」需自定义核心着色器（RegisterShadersEvent），另立 change
- [描边与内景深度] → 内景不透明写深度，描边 +0.001 更近必过深度测试，无 z-fighting
- [billboard 与方块选择框错位] → 视觉居中方块（跨两格），选择框单格，属既有状态

## Migration Plan

纯客户端视觉替换，无数据迁移。回滚 = git revert；`sukima.png` 描边贴图与传送逻辑全程未动。

## Open Questions

- 内景是否叠加眼睛素材元素（自定义 shader 或半透明叠层）：待用户目检后决定。
