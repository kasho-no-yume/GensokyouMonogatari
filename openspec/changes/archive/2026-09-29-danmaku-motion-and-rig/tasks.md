# 弹幕运动与编队（change 4）任务清单

对应 `specs/danmaku-motion/spec.md` 的 6 条 Requirement。

## 1. 弹位作为时间的函数（速率曲线）— 已完成

- [x] `DanmakuSpeedProfile`：三段 `(v,p)` + 尾段速率的 record，仅四则运算（不碰 `sin/cos`，跨平台一致）
- [x] 工厂 `constant` / `decelerateAndHold` / `decelerateAndReturn` 覆盖 5 种母题
- [x] `speedAt(int)` / `speedAt(double)` / `travelAt(int)` / `peakTravel()` / `peakTravelTick()`
- [x] `AbstractDanmakuProjectile`：7 个定标整数 + 3 个方向轴分量，`DATA_HAS_PROFILE`
- [x] `Behaviour.Motion.SPEED_PROFILE` 与 `DanmakuEmitter` 分派
- [x] 返程弹的「越过发射点即销毁」判据
- [x] profile / axis 的 NBT 持久化（`SpV0..SpV3` + `SpAxisX/Y/Z`）
- [x] 终止判据与曲射互斥——`Motion.Kind` 是单值枚举，`switch` 分支天然互斥，
      组合在类型上不可表达，无需额外静态检查

## 2. 分裂的分布方式 — 已完成

- [x] `SplitSpread`：`ring`（垂直于母弹速度的圆）/ `fibonacciSphere`（黄金角球面均布）
- [x] 分派依据是「母弹是否在运动」，非零速时的任意方向近似
- [x] `SphereDanmaku.spawnSplitChildren` 改用 `SplitSpread`，移除私有副本
- [x] 子代初速：运动母弹沿用自身速率；静止母弹走 `STATIONARY_SPLIT_SPEED` 下限

## 3. 弹位可由编队帧表达 — 已完成

- [x] `FormationFrame`（`danmaku/motion`）：`p(t) = c + S(t)·Rodrigues(axis, R(t), u₀) + d·s(t)`
- [x] **无任何编队实体**：`DanmakuRig` / `ModEntityTypes.DANMAKU_RIG` / `InvisibleEntityRenderer`
      / `TrackRunner.rigs` / 脱钩逻辑 **全部删除**
- [x] 外层中心为发射时的**世界坐标快照**（3 个标量），非实体引用
- [x] 环绕平面用 `(yaw, pitch)`，沿用曲射轴的角度打包约定
- [x] `FormationFrame.HORIZONTAL_PITCH_DEG = -90`：曲射轴约定下 `(0,0)` 是**竖直面**
- [x] 缩放 = 基准 ± 幅度 × **三角波**（无超越函数），以基准为中点往返
- [x] 位置每 tick 被**重置**为解析值（`解析终点 − 当前坐标` 当速度），误差不累积
- [x] `Rotation`：抽出 Rodrigues 旋转与角度→轴，曲射与编队帧共用同一条实现
- [x] NBT 持久化 12 个分量（`FrameCx`…`FrameSp`）

## 4. 编队的参数带宽 — 已完成

- [x] 每弹**出生时**同步 12 个定标 int，此后每 tick 零负载
- [x] 条目数恒定，**不随队形规模增长**
- [x] 位置纠正包仍存在但**正确性不依赖它**（`lerpTo` 见到的误差恒为 0）
- [x] 「相位角」被整个删掉——它本就是 `u₀` 的一部分

## 5. 编队的归属与生命周期 — 已完成

- [x] 声明在 **`Track`** 上（轨级），`TrackRunner.formationOf` 只读不建实体
- [x] 一轨一份，重复 `formation(...)` 是覆盖而非追加
- [x] 编队数 ≤ 轨道数 ≤ 3
- [x] 换阶段 / `stop()` **只停止发射**，已发的弹跑完既定寿命
- [x] 「弹引用失效装置」**不存在**——以「不存在引用」达成
- [x] `TrackLint.lintFormation` 静态拒绝与 `MINE` / `HOVER` / `CURVE` 的组合；
      **不**拒绝 `SPEED_PROFILE`（它是推进项的来源，与编队帧相加）

## 6. 玫瑰线花形 — 已完成

- [x] `Shape.ROSETTE`：`r = 基准 + 幅度·cos(花瓣数·θ)`，`Shape.Params` 新增 `petals` / `radialAmp`
- [x] 每瓣成簇：先按花瓣分组，再在瓣内小角度展开（不是整圈均分）
- [x] 每颗弹的**行进方向 = 平面法线**（不是径向）⇒「沿法线后退」= 整组前后进退
- [x] 纯几何（方向）+ 编队帧（平面内开合）承担「花瓣张开/闭合」
- [x] 幅度夹到 `[0, base]`；花瓣数 < 2 归零退化成圆环（`cos θ` 会给心脏线，与「参数 0 即圆」的约定不符）
- [x] 花蕊由独立的一颗大弹产生，出生点 = 编队中心 ⇒ 偏移 0 ⇒ 钉住不飘

## 7. 「过原点销毁」改为可选终止条件 — 已完成

- [x] `Motion` 新增 `diesAtOrigin`，默认 **false**
- [x] `DanmakuSpeedProfile` 只保留**查询**（`peakTravelTick` / `returnedToOrigin`），不再蕴含生死
- [x] 实体侧 `DATA_DIES_AT_ORIGIN` + NBT 持久化
- [x] `Formation.conflictsWith`：`SPEED_PROFILE` 仅在**勾了** `diesAtOrigin` 时与编队帧互斥
- [x] 理由：反向加速是纯运动，编队花后撤就需要它退回去继续飞；
      而那条判据问的是「推进项回到零点」，挂了编队帧后弹的实际位置在花瓣上，不是同一件事

## 8. 外层公转（两重公转）— 已完成

- [x] `FormationFrame` 新增 `orbitAxisYaw/Pitch` / `orbitRadius` / `orbitRateDegPerTick`（+4 个同步 int）
- [x] `center(t)` 是编队中心自身的位置；`orbitRadius = 0` 退化成单层
- [x] `Formation.withOrbit(...)` / `withBreathing(...)` 可组合叠加
- [x] 同轴 vs 异轴的实测结论（写进 javadoc 与断言）：
      **同轴不是「退化成一层」**——弹仍留在公转平面内，得平面玫瑰线（风车）；
      异轴才让弹离开平面，得三维 Lissajous。配轴决定的是「出不离开平面」。
- [x] `active()` 要求公转的**半径与角速度同时非零**（半径 0 时该层不产生位移）

## 9. 调试指令 — 已完成（语义已按图一/图二修正）

- [x] `/danmaku spiral [N] [内层自转] [外层半径] [外层公转] [呼吸幅度] [呼吸周期] [速度]`
      环与环心**都在面向玩家的平面内**转（两层同轴），整组**沿法线**推进 ⇒ 螺纹线
- [x] `/danmaku flower [花瓣] [每瓣弹数] [基准半径] [幅度] [自转] [呼吸幅度] [呼吸周期] [花蕊大小] [retreat]`
      平面内呼吸开合 + 沿法线推进 + 独立花蕊；
      `retreat` 让**法线方向**的推进变成「减速→停 3 秒→反向加速」
- [x] 参数树拆成 `spiralNode()` / `flowerNode()` 两个方法——9 层 `.then()` 嵌套的括号
      极易数错（本次就数错过三次），且默认值只在一处

### 「行进方向 = 平面法线」这一条的修正记录

早先两个演示指令都把弹的**初速方向设成了平面内的径向**，于是：

- 螺旋：整组在平面里向外扩散，**沿法线毫无分量** ⇒ 看着是「一个转着的环」，
  不是「一个朝你压过来的螺旋」；
- 花：`retreat` 变成花瓣朝花心收拢（那是第二套开合机制），整组同样不动。

改成：初速一律取**平面法线**，平面内的远离/靠近交给呼吸缩放。
补了 `NormalTravelTest` 5 项把「平面内编排与法线推进正交」钉成契约：
推进项 MUST 全部落在法线上，且 MUST NOT 污染平面内两个坐标。

顺带加了 `Rotation.anglesFromAxis`（角度→轴的逆变换）与
`Formation.withSpin(Vec3, …)` / `withOrbit(Vec3, …)`——
让调用方直接写「用视线方向当轴」，不必手算 yaw/pitch（那个换算错了不报错，
只是旋转莫名其妙跑到了另一个平面上）。
`FormationFrameTest` 补了角度↔轴往返无损的断言。

## 10. 发射原点独立于发射者位置（激光环）— 已完成

- [x] `Shape.AROUND_TARGET`：发射点采样自**目标周围**的区域，**不在 BOSS 身上** ⇒ 发射者无需移动
- [x] 每一发的方向由**该发自身**的「原点 → 目标」连线决定（全批共用方向会退化成「一道墙」）
- [x] 方向被**夹角上限**包络；上限 180°（完全自由，含**从背后射**）
      ——刻意不收紧：激光有 `Phase.DELAY` 预警，**公平性来自预警而非方向**。
      早先硬夹 90° 的理由（「背后射读作护住玩家、闪避直觉失效」）是错的，已撤回
- [x] `Geometry.build` 新增带 `target` 的重载；旧签名保留（缺省 `target = origin + forward`）
- [x] 新增 `Projectile` 弹种轴（`SPHERE` / `LASER`），与 `Shape`（几何）、`Behaviour`（行为）三者正交
- [x] `DanmakuEmitter` 按弹种分派，**MUST NOT 再写死 `new SphereDanmaku`**
- [x] **相位隐藏上提到 `AbstractDanmakuProjectile`**——显隐是 Behaviour 的一轴，与弹种正交。
      留在球弹上会让「激光配相位隐藏」**静默失效**（行为被无声丢弃，现象是激光完全不闪）
- [x] lint 判据：区域半径须为正、瞄准夹角须在 [0,180]、激光配速率曲线视为无效配置
- [x] `/danmaku laser-ring [N] [区域半径] [瞄准夹角] [长度] [粗细] [延迟秒] [持续秒]`
      走的是与符卡完全相同的路径，故它跑得通就说明符卡里写得出来

## 验证状态

- `.\tools\gradle_task.ps1 build` — BUILD SUCCESSFUL，**510 测试全绿**
- `python tools\lang_audit.py` — ok
- `python tools\validate_ritual_pattern.py` — 全部 pattern 通过
- `openspec validate danmaku-motion-and-rig --strict` — valid

测试覆盖（本轮新增 26 项）：
- `FormationFrameTest` 14 项：恒等帧逐位还原 p₀、纯函数性、求值顺序无关、旋转是刚体变换、
  呼吸以基准为中点往返、推进项干净叠加、纯旋转时两两间距恒定、呼吸时两两间距等比变化
- `FormationOrbitTest` 6 项：半径 0 钉住中心、公转半径恒定、公转真的搬动整队、
  异轴离开公转平面、同轴留在平面内、公转保持内部形状
- `RosetteGeometryTest` 11 项：弹数、半径区间、幅度真产生瓣、花瓣数影响形状、共面、
  **行进方向 = 平面法线**、全批方向一致、幅度夹取、花瓣数退化、**两笔都画到**、无角度空洞
- `BehaviourFormationTest` 13 项：销毁默认关闭、曲线仍能回答零点、编队 + 销毁被拒、
  编队 + 后撤合法、烘焙无损、花蕊钉住、互斥集合、lint 不误报、一轨一编队、编队数 ≤ 轨道数
- `NormalTravelTest` 5 项：平面内编排与法线推进正交、花形边开合边推进、螺旋螺纹、角度轴往返
- `AroundTargetGeometryTest` 8 项：发射点不在 BOSS 上、落在声明区域内、逐发方向独立、
  零夹角精确指向、夹角被上限约束、180° 硬夹、确定性、退化路径无 NaN
- `LatticeGeometryTest` 9 项：发射点真随机（互不重复 + 距离分散）、落在环带内（不贴脸）、
  瞄准分布同时含直瞄/中等偏角/大幅偏角、直瞄比例可控（0.1/0.3/0.6）、夹角受 90° 上限约束、
  同种子可复现、不同种子给出不同的网、无随机源重载不崩

## 已知缺口与实机反馈（留给后续 proposal）

- [ ] `LATTICE` 的默认值偏挤。玩家实机反馈：**太密、也太近**——不好躲，且容易被击飞。
      往松快调的起点：发数降到 12~14、球半径放大到 8~10（内半径随之从外径的 1/3 拉开）。
      玩家明确表示**本轮不改**，故原样保留为已验收效果。
- [ ] `danmakuEntityCap` 代码默认 800，但 `run/config/gensokyou-common.toml` 仍持久化为 500，
      两处不一致。

