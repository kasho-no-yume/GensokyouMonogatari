## 1. 姿态输入与同步字段

- [x] 1.1 固化「复用基类字段」映射表并写进代码文档：旋转轴 → 编队帧 axis；角速度曲线 → `DATA_HAS_PROFILE` + `DATA_PROFILE_*`；平移方向 → `DATA_AXIS_X/Y/Z`；平移速率 → `DATA_FRAME_ADVANCE_SPEED`；枢轴/出生点 → 编队帧 center/offset。字段定义处与 `LaserDanmaku` 类注释均写明「激光对同一字段重解释为角度/姿态」，并补进 `design.md` D3a
- [x] 1.2 厘清既有下标重叠：`P_LASER_COUNT` 8 → 7，穿墙形态位移入 `DATA_FLAGS` 的空闲位 `FLAG_LASER_PIERCE`（bit 5），激光块止于 47，不再与 `P_BURST_AT = 48` 重叠；球弹/爆散弹既有行为未改
- [x] 1.3 提供写入姿态参数的入口（`LaserDanmaku#configureLaserMotion`）；姿态输入复用既有同步字段，经 `motionParams()` / `onMotionParamsApplied()` 自动进入运动指纹覆盖
- [x] 1.4 `LaserDanmaku.tick()` 只调 `baseTick()`、不调基类 `tickDanmaku()`，语义重载字段结构性不会进入球弹路径（无世界不可测，由构造保证并注释）
- [x] 1.5 `.\tools\gradle_task.ps1 compileJava` 通过

## 2. 姿态求值（旋转 + 平移）

- [x] 2.1 `LaserDanmaku` 增加由年龄求姿态的纯函数：`θ = speedProfile().travelAt(age)`；`R = Rotation.about(axis, toRadians(θ))`；`origin = center + R·offset + motionAxis·advanceSpeed·age`；`direction = R·baseDir`
- [x] 2.2 `tick()` 每 tick 用解析值覆写位置（`applyLaserPose` → `setPos`），误差不累积；方向不写回，由 `getCurrentDirection()` 现算
- [x] 2.3 核实 `ServerLevel` / `ClientLevel` 的 `tickNonPassenger` 在 `tick()` 前调用 `setOldPosAndRot()`（从 neoform 源确认），位置历史由框架维护，无需额外代码
- [x] 2.4 复核两套长度缓存：`onMotionParamsApplied` 末尾统一失效；`getRenderLength` 的缓存键含起点，移动时按起点距离失效
- [x] 2.5 `makeBoundingBox()` 改用 `getCurrentDirection()` + 当前 `position()`
- [x] 2.6 NBT：复用基类已持久化的 profile / frame / axis / phase；缺键旧存档 ⇒ 参数默认 ⇒ 恒等（静止）姿态
- [x] 2.7 新增 `LaserMotionMathTest`：`travelAt` 角度数值、原地旋转位置不变、绕外部点圆弧半径恒定、减速停住总角 = 闭式积分、旋转+平移复合

## 3. 隐藏态（仅激活期火柱）

- [x] 3.1 定义 `LaserDanmaku#isActiveHidden() = getPhase()==ACTIVE && isHidden()`
- [x] 3.2 `damageEntitiesInBeam()` 由 `tick()` 的 `!isActiveHidden()` 门控（隐藏期不判伤）
- [x] 3.3 `LaserDanmakuRenderer` 逐层乘暗态系数 `HIDDEN_DIM`：同时缩 alpha 与 RGB（加法层贡献按平方下降）；渲染类型/写深度不变
- [x] 3.4 `renderDelayIndicator()` 不经过隐藏门控（预警线不受影响）
- [x] 3.5 手动：命令生成周期明灭激光，核对隐藏期光束内实体不掉血、画面读作变暗、延迟期预警线照常

## 4. 渲染与相机

- [x] 4.1 预警线随旋转：渲染器改用 `getCurrentDirection()`，由同一旋转量驱动，无需新分支
- [x] 4.2 手动/测试：`getRenderLength` 在移动起点下仍满足逐形态不变量（同形态视觉长度 == 判伤长度）
- [x] 4.3 手动：快速扫动下光束不被视锥剔除；「激光相对半透明方块/云的渲染层级」不回归

## 5. 调试命令

- [x] 5.1 `command/DanmakuTestCommands.java` 新增 `/danmaku spin-laser [ω°/s] [减速秒] [枢轴偏移] [平移速度] [长度] [延迟秒] [持续秒] [周期] [占空比%]`
- [x] 5.2 手动：命令生成旋转激光，核对「扫射 → 慢慢停」「平移」「旋转 + 平移复合」「周期明灭」

## 6. 验证与回归

- [x] 6.1 `.\tools\gradle_task.ps1 build` 全绿
- [x] 6.2 手动：既有 `/danmaku laser` / `piercing-laser` / `laser-ring` / `web` 行为不回归（静止姿态与变更前一致）
- [x] 6.3 手动：与并行变更 `fix-danmaku-laser-render-and-frozen-expiry` 协调（渲染门控豁免 + 绝对寿命不受影响）
- [x] 6.4 `openspec validate danmaku-laser-motion --strict` 通过
