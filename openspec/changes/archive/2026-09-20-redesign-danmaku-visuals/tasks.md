## 1. 贴图资产生成（程序化，非像素画）

- [x] 1.1 写 `tools/gen_danmaku_textures.py`：PIL + 4× 超采样程序化绘制三张贴图（可复跑调参）
- [x] 1.2 生成 `sphere_danmaku.png`（64×64，去色径向渐变，中心近白、边缘 alpha 平滑衰减到 0，无烤色）覆盖旧贴图
- [x] 1.3 生成 `talisman_danmaku.png`（64×64，长宽比与旧 16×16 一致，白色符纸线条 + 淡渐变，去色）覆盖旧贴图
- [x] 1.4 生成 `laser_magic_circle.png`（256×256，白线外圈双线圆环 + 内接五边形 + 五芒星 + 五角点缀，透明底，抗锯齿，去色）
- [ ] 1.5 游戏内检查三张贴图缩放显示下的线条清晰度与边缘平滑度

## 2. 球弹三层渲染

- [x] 2.1 `DanmakuRenderTypes` 新增 `translucent(RenderType)`：alpha 混合、noCull、NEW_ENTITY 顶点格式，走现有缓存模式
- [x] 2.2 `SphereDanmakuRenderer`：本体层换 `translucent`，贴图染实体色、高 alpha 主体 + 平滑渐变边缘
- [x] 2.3 `SphereDanmakuRenderer`：新增白色亮核层（additiveGlow 同贴图，约 0.5× 缩放，恒白 255,255,255，不染实体色）
- [x] 2.4 保留现有外发光层（1.35× additiveGlow），确认三层写入顺序符合缓冲别名规则（同 RenderType 连续写入）
- [ ] 2.5 游戏内验证：红/蓝/绿/随机色球弹颜色均正确不发黑，白心 + 色边 + 光晕结构成立，密集重叠不过曝发灰

## 3. 札弹换装

- [x] 3.1 确认 `TalismanDanmakuRenderer` 几何/UV 无需改动（新贴图长宽比一致），仅验证渲染效果
- [ ] 3.2 游戏内验证：多色札弹染色正确不发暗，与球弹柔光风格统一，追踪/外发光行为不变

## 4. 激光五芒星法阵

- [x] 4.1 `LaserDanmakuRenderer` 新增法阵渲染：ACTIVE 期在局部 z≈0 处输出面向 Z 轴的 quad，additiveGlow(magic_circle) 染激光色、FULL_BRIGHT
- [x] 4.2 法阵缩放联动 `computeEnvelope` 包络（基于激光半径，开火展开/收束收起）
- [x] 4.3 法阵绕 Z 轴恒速自旋（`tickCount + partialTick` 驱动）
- [x] 4.4 遵守缓冲别名规则：按"外发光 → 法阵 → 主体/亮核/端盖"整层连续写入，不交叉取 consumer
- [ ] 4.5 游戏内验证：DELAY 期无法阵（预警线不变）；开火瞬间法阵随光束展开并持续旋转；收束时同步收起；多色激光法阵染色正确；水下/水面遮挡语义不变

## 5. 收尾

- [x] 5.1 编译通过（`gradlew compileJava`）
- [x] 5.2 对照 specs 三份 delta 逐场景自检（球弹三层/任意色、札弹染色、法阵五场景）
- [ ] 5.3 密集弹幕性能抽查：新贴图分辨率与新增层对帧率无可感知影响
