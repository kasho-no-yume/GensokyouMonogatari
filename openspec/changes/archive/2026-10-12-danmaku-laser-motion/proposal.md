## Why

激光是目前唯一**完全静止**的弹幕原语：`LaserDanmaku` 在生成时定死位置与方向，`setVelocity` / `setDirection` / `addVelocity` 全是 no-op，`tick()` 只调 `baseTick()`。而东方原作的激光手感核心恰恰是**动态**的——绕发射点扫射、绕外部点挥扫、整体平移，以及在持续期内间歇显隐。轨道/行为层也帮不上忙：`DanmakuEmitter` 明确**不对激光施加 `Behaviour`**（原注释理由：延迟期算不算隐藏态没有答案）。于是「会动的激光」现在根本写不出来。

本变更给激光原语补上**运动**与**显隐**两轴，并在同一变更里正面回答那个被悬置的问题：隐藏只作用于激活期火柱。

## What Changes

- **激光姿态 SHALL 由年龄推导**（纯函数、双端同构、零逐 tick 包）：
  - **绕点旋转**：扫过的角度由 `DanmakuSpeedProfile` 的闭式积分 `travelAt(age)` 给出（与球弹共用同一条分段线性速率曲线，**只用四则运算**）⇒ 直接支持「以一定角速度扫射、在 N tick 内线性减到 0、然后停住」（`decelerateAndHold`）。默认枢轴 = **发射点**（原地旋转），可指定任意枢轴（绕外部点挥扫）。
  - **平移**：整体沿发射方给定的方向**恒速**平移。
- **旋转 / 平移 / 隐藏参数复用基类既有同步字段**（`DATA_PROFILE_*` 作角速度、编队帧 `center/offset/axis` 作枢轴/出生点/旋转轴、`DATA_AXIS_*` 作平移方向、`DATA_PHASE_*` 作显隐）⇒ 本变更 MUST NOT 新增 accessor、MUST NOT 改 `PARAM_COUNT`。
- **激光 SHALL 支持隐藏态**：复用 `DanmakuPhase`（周期/占空比/相位，年龄推导，零额外同步），即「同弹幕逻辑」。隐藏态**仅作用于激活期火柱**：压暗所有渲染层（加法层按暗态系数平方压暗）、跳过实体判伤；**延迟期红色预警线不受影响**（预警是公平性信息，属 R1）。
- **旋转期间预警线 SHALL 跟随旋转**：渲染器本就用 `getLaserDirection()`，由同一旋转矩阵驱动后天然跟随，无需额外分支。
- 调试：新增 `/danmaku` 动态激光测试命令验证手感。
- **BREAKING（spec 级，非接口/存档）**：`danmaku-laser` 现行「Laser stationary behavior」要求（激光位置与方向在生成后不可变）被本变更取代。

**明确不做（本次范围外）：** 轨道层接入（`Shape.LASER` / `Projectile.laser` 运动参数 / `Behaviour`），即不让符卡表直接写动态激光；平移的速率曲线（「飘着飘着停」）——基类只有一条 profile，本次留给旋转，平移取恒速；用事件或关键帧下发姿态/阶段——两者 MUST 继续是年龄的纯函数。

## Capabilities

### New Capabilities
<!-- 无新增能力：动态激光属于既有 danmaku-laser 能力的行为扩展。 -->

### Modified Capabilities

- `danmaku-laser`: 将「静止行为」条款改为「动态姿态（旋转 + 平移）」；新增「激光隐藏态（仅激活期火柱）」与「旋转期预警线跟随」两条要求；延迟期与几何条文中「从生成位置沿发射方向」的措辞改为「从当前姿态位置沿当前方向」。

## Impact

- **实体**：`entity/LaserDanmaku.java` —— 每 tick 由年龄求姿态（`setPos(pivot + R(A,θ)·(O₀−pivot))` 与 `setLaserDirection(R(A,θ)·D₀)`）；隐藏门控接入 `damageEntitiesInBeam()`；两套长度缓存（`getActualLength` / `getRenderLength`）与 `makeBoundingBox()` MUST 随移动起点更新；`motionParams()` / `onMotionParamsApplied()` 纳入新姿态字段（复用既有槽位）。
- **同步**：`danmaku/render/DanmakuMotionState.java` —— 复用既有 fingerprint 槽位；同时**厘清一处已存在的下标重叠**（激光块占 `41..48`，而 `P_BURST_AT = 48`，两者共用 48；今日因激光与爆散弹不同源而不冲突，本变更一并修正）。
- **渲染**：`client/renderer/LaserDanmakuRenderer.java` —— 各层乘暗态系数（加法层平方）；预警线随旋转基本零改动。
- **命令行**：`command/DanmakuTestCommands.java` —— 新增动态激光测试命令。
- **规格**：`openspec/specs/danmaku-laser`（delta）。
- **兼容性**：不新增同步字段种类、不改协议结构；旧存档中相关字段为默认值时姿态退化为**恒等（静止）**，行为不劣于变更前。
