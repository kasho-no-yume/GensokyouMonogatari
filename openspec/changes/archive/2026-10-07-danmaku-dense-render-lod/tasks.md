# Tasks

- [x] 1. 用 `tools/gen_tex.py` 生成 `assets/gensokyou/textures/entity/sphere_danmaku_lod.png`（中心填充+外圈，软边与原体一致但中心更饱满）。
- [x] 2. `DanmakuRenderProbe` 增加 dense hysteresis 计数与 `denseMode` 字段。
- [x] 3. `SphereDanmakuRenderer`、`LaserDanmakuRenderer` 的 glow/core 走 probe effective 闸；dense 时 `SphereDanmakuRenderer` body 切球用 `sphere_danmaku_lod`。
- [x] 4. `/gs_boss danmaku` 打印 denseMode 与本轮密度行。
- [x] 5. compileJava 已跑通；旧三层渲染仍存在但 glow/core 被 probe.effective* 短路；sphere body 始终走 LOD 贴图。
