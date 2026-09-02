# Tasks: sukima-portal-billboard

## 1. 贴图烘焙

- [x] 1.1 新建 `tools/textures/sukima_portal_bake.py`：sukimatexture.png → LANCZOS 降采样 256² → 眼形剪影 alpha 遮罩（上盖 0.85 / 下弧 1.15 / 半宽 0.5，边缘 1px 渐变）→ 输出 RGBA `sukima_portal.png`，x8 预览图供目检
- [x] 1.2 运行烘焙，产物落位 `src/main/resources/assets/gensokyou/textures/entity/sukima_portal.png`；确认源素材 `sukimatexture.png` 处置（倾向移出 assets）

## 2. 渲染基建

- [x] 2.1 新增隙间传送门 RenderType（translucent + noCull + clamp=false REPEAT + 指定贴图），仿 `DanmakuRenderTypes` 模式落位
- [x] 2.2 新建 `client/renderer/SukimaPortalQuads.java`：`draw(poseStack, bufferSource, texture, halfW, halfH, uPhase, vPhase, tint, alpha, light)`，NEW_ENTITY 顶点格式、全亮度、UV 相位取模；不引用 SukimaBlockEntity 类型
- [x] 2.3 `GensokyouTextures` 新增 `SUKIMA_PORTAL` 常量

## 3. 渲染器重写

- [x] 3.1 重写 `SukimaPortalRenderer`：删棱壳几何/`endPortal`，改 0 厚度 quad 分层绘制（初版 billboard + UV 滚动，后按目检反馈修正为世界固定，见 5.1–5.3）
- [x] 3.2 保持渲染包围盒上扩 1 格（2 格高 quad 防视锥裁剪）

## 4. 验证

- [x] 4.1 编译通过 + 游戏内目检（多轮迭代目检通过：billboard 正对玩家、锚定虚空内景、黑红背景、眼密度达标）
- [x] 4.2 回归（纯客户端视觉改动，服务端传送/仪式逻辑未触碰；用户游戏内持续使用正常）

## 5. 目检反馈修正（billboard → 世界固定）

- [x] 5.1 去 billboard：移除 `camera.rotation()`，恢复世界固定朝向 + 绕 Z 10° 倾角（末地门式「框与内景分离、内景静止」质感）
- [x] 5.2 去 UV 滚动：内景完全静止；工具 API 相位参数保留，BER 传 0
- [x] 5.3 描边层改前后两层（±0.001）：主层 translucent 透明像素照写深度，单侧描边在对侧会被深度遮挡
- [x] 5.4 编译通过（BUILD SUCCESSFUL）

## 6. 二轮目检反馈修正（世界固定 → billboard 框 + 末地门内景）

- [x] 6.1 恢复 billboard（`camera.rotation()` + YP 180°）+ 绕视线轴 10° 倾角：每帧正对玩家
- [x] 6.2 内景改为眼形透镜面几何（16 条带扇面）+ `RenderType.endPortal()`——实测 shader 源码确认屏幕投影锚定 + 16 层 GameTime 漂移机理，billboard 下仍呈「锚定的虚空」，与框分离
- [x] 6.3 去 0 厚度贴图内景与前后双描边：描边层改 billboard 局部 +z 0.001 单层（billboard 恒面向相机）
- [x] 6.4 编译通过（BUILD SUCCESSFUL）

## 7. 三轮目检反馈修正（自定义着色器：黑红背景 + 眼睛减量）

- [x] 7.1 新增 `gensokyou:sukima_portal` 核心着色器（json/vsh/fsh，克隆 end portal 机理）：底色乘数 `vec3(0.6,0.32,0.32)` 黑红背景；`COLORS[]` 换黑红调色板；层缩放 8.5→2.35、层数 15→6（眼睛密度降约一个数量级）
- [x] 7.2 `RegisterShadersEvent` 注册于 `GensokyouClient`；`SukimaPortalRenderTypes.voidPortal` 换用自定义着色器分片
- [x] 7.3 编译通过（BUILD SUCCESSFUL）
