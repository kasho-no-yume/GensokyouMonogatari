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

## 3. 弹幕编队装置（rig）— 已完成

- [x] `RigOrbit`（`danmaku/motion`）：弹位 = 公转(t) + 自转(t, 相位)，纯函数、零累积误差
- [x] `Rotation`：抽出 Rodrigues 旋转与角度→轴，**曲射与 rig 共用同一条实现**
- [x] `DanmakuRig` 实体：注册为 `danmaku_rig`，无渲染/无碰撞/不判伤/不可拾取
- [x] 装置位置 = `tickCount` + 同步标量的纯函数（`setDeltaMovement(解析终点 − 当前坐标)`，
      交给原版位移，故碰撞与扫掠管线零改动）
- [x] 外层中心为装置上的 3 个标量，**不是实体**
- [x] 环绕平面用 `(yaw, pitch)` 表达，沿用曲射轴的角度打包约定
- [x] 双层环绕：装置绕外层中心公转 + 弹绕装置自转
- [x] `RigOrbit.HORIZONTAL_PITCH_DEG = -90`：曲射轴约定下 `(0,0)` 是**竖直面**，
      水平环绕必须显式写 -90，否则「公转」变成上下翻跟头
- [x] `InvisibleEntityRenderer`：注册空渲染器，避免日志里出现无关的
      "Missing entity renderer" 警告；影子半径置 0

## 4. 编队弹幕的参数带宽 — 已完成

- [x] 每弹仅同步 `DATA_RIG_ID`（网络 id）+ `DATA_RIG_PHASE`（相位角）两个 int
- [x] rig 引用用**实体网络 id**，非装置序号
- [x] 装置参数只存在装置上那一份，48 颗弹不复制
- [x] 多 rig 并存按轨道下标索引（`TrackRunner.rigs`），归属无歧义
- [x] 无位置纠正包依赖：`lerpTo` 见到的误差恒为 0

## 5. 装置的归属与生命周期 — 已完成

- [x] rig 挂在 **`Track`** 上（轨级，非拍级），`TrackRunner.rigFor` 按轨道下标惰性创建
- [x] 装置数 = 声明了 rig 的轨道数，恒 ≤ 轨道数 ≤ 3
- [x] `selectCard` 换阶段与 `stop()` 时统一 `discardRigs()`
- [x] 装置消失后弹**脱钩自由飞行**（沿当前动量），不残留失效引用
- [x] `TrackLint.lintRig` 静态拒绝与 `MINE` / `CURVE` / `SPEED_PROFILE` 的组合

## 6. 发射原点独立于发射者位置（激光环）— 未开始

- [ ] 发射指令自带原点与方向，不由「发射者位置 + 全批共享瞄准方向」推导
- [ ] 支持「以目标周围区域为原点」：发射点区域内采样
- [ ] 逐发方向由**该发自身**的「原点指向目标」连线约束（夹角上限）
- [ ] 不要求发射者实体移动到发射点
- [ ] 翻译层可产出激光弹种，不硬编码为球弹

## 验证状态

- `.\tools\gradle_task.ps1 build` — BUILD SUCCESSFUL，**441 测试全绿**
- `python tools\lang_audit.py` — ok
- `python tools\validate_ritual_pattern.py` — 全部 pattern 通过
- `openspec validate danmaku-motion-and-rig --strict` — valid

测试覆盖：
- `DanmakuSpeedProfileTest` 13 项：闭式积分 vs 中点法数值积分互校、黎曼和误差 ≤ 总变差一半、
  返程弹不早于最远点判为回归
- `SplitSpreadTest` 8 项：环与速度正交、360/count 均分、`count=2` 对径不算退化、
  球面无近重合方向、分派依据是「是否在运动」
- `RigOrbitTest` 13 项：位置是纯函数、求值顺序无关、公转半径恒定、角速度确为度/tick、
  **任意两弹间距恒定（队形不散）**、退化/竖直/负 tick 不产生 NaN
- `BehaviourRigTest` 10 项：声明逐字段还原、寿命夹取、互斥集合、
  lint 报出冲突且不误报合法组合、一轨一装置（覆盖而非追加）、装置数 ≤ 轨道数

## 与 spec 的一处有意偏离

spec 字面写「该符卡所创建的装置数等于其轨道数」。实现取**惰性创建**：
只有显式声明了 rig 的轨道才建装置，未声明的轨道一个实体都不产生。
理由是照字面实现会给不需要编队的轨道也挂一个空装置，白白多出实体与追踪开销，
而「装置数不超过轨道数」这条约束的意图（不超 1~3）依然成立。

