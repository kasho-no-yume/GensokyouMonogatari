# Design

## 上下文与约束

- NeoForge 21.1 / MC 1.21.1。渲染类型：自定义 `DanmakuRenderTypes`
  （translucentDepth / additiveSolid / additiveGlow，全双面、无裁剪、自发光），
  per-texture 缓存。
- 实体的视野剔除走原版两级：`EntityType#clientTrackingRange`（服务端决定是否跟踪，
  块单位）→ `Entity#shouldRenderAtSqrDistance`（客户端每次渲染判断）→ frustum AABB。
- 为什么客户端只能覆写 shouldRenderAtSqrDistance：
  `viewScale = clamp(视据/8, 1, 2.5) × entityDistanceScaling`（LevelRenderer.java:856），
  原版这条公式把实体大小乘进去，**最大 64×2.5×体积**，与用户设视距脱钩。

## 决策点

1. **探针＝调试通道，不进生产默认**：层关断的影响要可观测，但默认生产行为
   不变。渲染切层走 `/danmaku layers`，计数走 `/gs_boss danmaku` 诊断行，
   不影响 API/网络。
2. **渲染距离直接跟客户端视据**：`renderDistance × 16` 半径。对任何时候的
   `clientTrackingRange(32)` 阈值都有余量；激光用 `max(maxLength+64, 半径)`
   并入。原因：弹幕是玩家要主动闪的东西，「忽然消失」读作「判伤幽灵弹」，
   必须和视距一致。
3. **clientTrackingRange 32**：服务端茫然设 512 格的代价可控——位置包已按
   20 tick 间隔且 `lerpTo` no-op，不增加稳态带宽；每枚最多一次 spawn+
   entity-data 突发，已有 `danmaku-track-scope` 提案的带宽基线可以顺带测。
4. **LOD 由剖针结论排名，不预设**：若关断辉光/亮核层立竿见影即 GPU 填率；
   否则多半是 vanilla GL 提交条税（uniform location / VAO 切换）被批数放大——
   那条路先合并批数（单一染色代理 RenderType+顶点色），再看是否需要
   billboard 合批。

## 备选

- **Shaders/Instanced rendering**：太早；MSA/Depth test 不兼容复用；暂不做。
- **把 shouldRenderAtSqrDistance 改成 chunk 视锥裁剪**：弹幕 AABB 已在 frustum
  剔除里，无需重复。
- **CameraOrientation 每帧缓存替代 per-entity mulPose**：收益测过 ≈ 4 份样本
  的级别，不进首批。

## 红线

- 覆写 `shouldRenderAtSqrDistance` 必须同时把 **laser** 合入，单覆盖单忘会让
  不穿墙激光只剩视据长度。
- `/danmaku layers` 关断后**恢复切片要可还原**（每 tick 重置）——
  避免遗忘半层关闭的截图报告。
- 计数器需在 ClientLevel tick 清零，防止层数泄漏跨帧。
