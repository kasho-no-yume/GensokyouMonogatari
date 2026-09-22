# Proposal: fix-danmaku-render-order-and-talisman-loss

## Why

`redesign-danmaku-visuals` 把球弹/札弹本体层从写深度的 `entityCutoutNoCull` 换成了不写深度的自建 `translucent`。实体阶段先于半透明地形（水）与云层绘制，而本体不再写入深度缓冲，导致后二者不被弹幕深度剔除，在视觉上盖住本应位于其前方的弹幕（渲染顺序错误）。

同时灵符的追踪只在目标死亡/卸载时停止：当目标被甩到身后（速度方向与灵符→目标方向夹角 > 150°）时，灵符仍会持续转向、绕圈回头，不符合"弹幕"的行为预期。

## What Changes

- 新增**写深度的 alpha 混合渲染类型**（`DanmakuRenderTypes`），球弹与札弹本体层改用之，恢复被水/云的正确遮挡；该类型开启上传排序（`sortOnUpload`），缓解重叠柔边错序。
- **发光层统一写深度**：球弹/札弹的外发光与亮核、激光的外发光与发射端法阵，从 `additiveGlow`（不写深度）改为既有的 `additiveSolid`（加法 + 写深度 + 排序）。此前外发光仍会被水/云覆盖（激光同样有此问题）。
- 札弹本体改为**满亮度自发光**（`FULL_BRIGHT`），与球弹/激光一致，进入阴影不变暗。
- 灵符新增**追踪丢失**判定：速度方向与灵符→目标方向夹角超过阈值（默认 120°，`config` 可调）时永久停止追踪，之后匀速直线飞行；判定双端各自计算（近似实现），服务端同时清空同步的目标 id。
- 弹幕基类**预留阵营（faction）同步字段**：不接任何行为，仅作为后续"友军不误伤 / 敌我辨识"能力的占位（本次不做敌我辨识）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `danmaku-sphere`: 本体层与发光层视觉需求调整——均改为写深度，恢复被水/云的正确遮挡；行为与其余视觉需求不变。
- `danmaku-talisman`: 新增"追踪丢失（夹角阈值）"行为需求；本体层与发光层改写深度（同 sphere）并改为满亮度自发光。
- `danmaku-laser`: 「相对半透明方块的渲染层级」需求调整——外发光与法阵也改为写深度（原先仅主体写深度）。

## Impact

- **代码**：
  - `client/renderer/DanmakuRenderTypes.java` — 新增写深度的 alpha 混合类型
  - `client/renderer/AbstractDanmakuRenderer.java` — 发光层改用 `additiveSolid`
  - `client/renderer/SphereDanmakuRenderer.java` — 本体层换用新类型
  - `client/renderer/TalismanDanmakuRenderer.java` — 本体层换用新类型 + 满亮度
  - `client/renderer/LaserDanmakuRenderer.java` — 外发光与法阵改用 `additiveSolid`
  - `entity/TalismanDanmaku.java` — 追踪丢失判定
  - `entity/AbstractDanmakuProjectile.java` — 预留 faction 同步字段（无行为）
  - `config/GensokyouConfig.java` — 新增丢失角度阈值项（`danmaku` 段）
  - `lang/zh_cn.json`、`lang/en_us.json` — 配置项文案
- **兼容性**：纯客户端视觉 + 双端同构的追踪判定；新增同步字段默认中性值，旧存档/已生成弹幕落到默认值，无数据迁移。
- **风险**：所有弹幕可见层写深度后，发光轮廓会在水面上按可见形状"占位"（几何上正确的代价），密集重叠时可能有轻微穿插（见 design 的取舍）。
