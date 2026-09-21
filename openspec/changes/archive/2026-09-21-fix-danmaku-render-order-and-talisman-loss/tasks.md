## 1. 渲染顺序修复

- [x] 1.1 `DanmakuRenderTypes` 新增写深度的 alpha 混合类型（写掩码 `COLOR_DEPTH_WRITE`、`sortOnUpload(true)`、`NO_CULL`、`TRANSLUCENT_TRANSPARENCY`，沿用现有按纹理缓存模式）
- [x] 1.2 `SphereDanmakuRenderer` 本体层从 `translucent` 换用新类型
- [x] 1.3 `TalismanDanmakuRenderer` 本体层换用新类型，并把本体光照从 `packedLight` 改为 `FULL_BRIGHT`
- [x] 1.4 确认透明角落不会产生方形深度洞（shader `if (color.a < 0.1) discard;`，只按可见轮廓写深度）
- [x] 1.5 发光层统一写深度：基类 `glowRenderType()` 从 `additiveGlow` 改为 `additiveSolid`（球弹/札弹外发光 + 亮核一并生效）
- [x] 1.6 激光外发光（`renderActiveBeam`）与法阵（`renderMagicCircle`）从 `additiveGlow` 改为 `additiveSolid`
- [x] 1.7 更新相关注释（基类、球弹、激光），并确认 `additiveGlow` 仍被水晶/仪式渲染器使用，非死代码

## 2. 灵符追踪丢失

- [x] 2.1 `GensokyouConfig` 的 `danmaku` 段新增 `TALISMAN_TARGET_LOSS_ANGLE_DEG`（默认 150，范围 (90, 180]）及字段声明
- [x] 2.2 `lang/zh_cn.json`、`lang/en_us.json` 补配置项文案（本项目无本地化配置界面，全 mod 惯例为 `.comment()` 即配置文案，未新增无消费方的 lang 键）
- [x] 2.3 `TalismanDanmaku.tickHoming`：当速度方向与灵符→目标方向夹角 > 阈值时停止偏转并标记丢失；丢失后恒直线飞行、不再追踪
- [x] 2.4 丢失判定为不可逆（瞬态 `targetLost`，置位后不再追踪）；服务端在判定丢失时 `setTarget(null)`（同步目标 id 归零）；客户端本地同构判定以消除数据包延迟窗口
- [x] 2.5 确认判定沿用同一 `aimPoint`（目标碰撞箱中心高度）口径，双端可复现

## 3. 预留字段

- [x] 3.1 `AbstractDanmakuProjectile` 新增同步整型 `FACTION`（默认 0 = 中性），注释标注为后续"友军不误伤 / 敌我辨识"能力预留；本 change 不写入、不读取、不影响渲染与判伤

## 4. 验证

- [x] 4.1 编译通过（`gradlew compileJava`，输出重定向到文件后读取）
- [x] 4.2 游戏内：球弹/札弹/激光（含**外发光**与法阵）位于水面/云层**前方**不再被覆盖；观测发光在水面"占位"的硬边与重叠穿插是否可接受
- [x] 4.3 游戏内：札弹在阴影/暗处仍满亮度，与球弹/激光一致
- [x] 4.4 游戏内：灵符目标被甩到身后（>120°）后停止追踪并直线飞行；调低阈值可复现更早丢失，调高（接近 180）则几乎不丢失
- [x] 4.5 对照 `danmaku-sphere` / `danmaku-talisman` 两份 spec delta 逐场景自检
