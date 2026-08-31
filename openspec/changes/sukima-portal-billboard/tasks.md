# Tasks: sukima-portal-billboard

## 1. 贴图烘焙

- [ ] 1.1 新建 `tools/textures/sukima_portal_bake.py`：sukimatexture.png → LANCZOS 降采样 256² → 眼形剪影 alpha 遮罩（上盖 0.85 / 下弧 1.15 / 半宽 0.5，边缘 1px 渐变）→ 输出 RGBA `sukima_portal.png`，x8 预览图供目检
- [ ] 1.2 运行烘焙，产物落位 `src/main/resources/assets/gensokyou/textures/entity/sukima_portal.png`；确认源素材 `sukimatexture.png` 处置（倾向移出 assets）

## 2. 渲染基建

- [ ] 2.1 新增隙间传送门 RenderType（translucent + noCull + clamp=false REPEAT + 指定贴图），仿 `DanmakuRenderTypes` 模式落位
- [ ] 2.2 新建 `client/renderer/SukimaPortalQuads.java`：`draw(poseStack, bufferSource, texture, halfW, halfH, uPhase, vPhase, tint, alpha, light)`，NEW_ENTITY 顶点格式、全亮度、UV 相位取模；不引用 SukimaBlockEntity 类型
- [ ] 2.3 `GensokyouTextures` 新增 `SUKIMA_PORTAL` 常量

## 3. 渲染器重写

- [ ] 3.1 重写 `SukimaPortalRenderer`：删棱壳几何/`endPortal`/倾角常量，改 billboard（`camera.rotation()` + Y 180°）双层绘制——先主贴图 quad（UV 滚动相位 = gameTime+partialTick 驱动），再 +0.001 偏移描边 quad（`sukima.png`，cutout）
- [ ] 3.2 保持渲染包围盒上扩 1 格（2 格高 quad 防视锥裁剪）；目检滚动方向/速度并定稿

## 4. 验证

- [ ] 4.1 编译通过 + 游戏内目检：正对/侧面/背面观察均为眼形隙间且正对玩家；虚空缓慢流动；黑暗环境全亮度；描边对齐无 z-fighting
- [ ] 4.2 回归：穿行传送门维度切换正常、冷却不重复触发；结界仪式重建/拆除隙间正常
