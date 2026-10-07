## Why

弹幕密集场景（wall 600 ~ 800）下双加 alpha 层（glow + core）会让弱 GPU 从「流畅」掉到「卡顿」，
而全 3 层又是东方弹幕可读性的唯一面貌。按屏幕上的弹数做一个**密度门控、同视觉证迹但不同绘制遍数的
LOD texture**——仅在密集时切 swap 到单张「烧好」的纹理（外圈 halo + 截断的高亮心），保留单次 alpha
混合流程，不比 body-only 更重，从根本上绕开 2 个加法层的 fill-rate 爆炸。

后续目标：如果这张单层纹理在各场景下都能替代三层渲染质量的线，渲染器可以全部消灭
glow/core 额外层，不再走 LOD。当前 proposal 先把它当密度验证项。

## What Changes

- 新增「单层烧好」弹幕贴图（halo + 发光纹理的中心填充），先在 dense 场景下启用验证。
- 渲染器选择路径：`denseThreshold` 的 hysteresis 触发（保留原 LOD 模式）/后续可升级成永久全量替换。
- `DanmakuRenderProbe` 继续提供密度统计，并把 `denseMode` 记入其 summary。

## Capabilities

### New Capabilities

### Modified Capabilities
- `danmaku-render-state`: 解析新的 LOD texture 决策条目需在渲染上下文里。

## Impact

- `SphereDanmakuRenderer` 的 body texture 取得点加入密度选择。
- `DanmakuRenderProbe` 加入 `denseMode` 摘要字段。
- Body 渲染仍保留 `renderProbe` body slice 计数（层掩码下）。
- 不影响网络、碰撞。
