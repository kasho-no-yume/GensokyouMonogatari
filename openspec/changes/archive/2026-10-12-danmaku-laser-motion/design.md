## Context

`LaserDanmaku` 是本模组唯一一个**完全静止**的弹幕原语：位置与方向在生成时定死，`setVelocity` / `setDirection` / `addVelocity` 覆写为 no-op，`tick()` 只调 `baseTick()`（不调基类的 `tickDanmaku()`，因此不参与位移与扫掠管线）。判伤改用**射线 clip**（`damageEntitiesInBeam`），长度分**权威**（`getActualLength`）与**视觉**（`getRenderLength`）两条裁剪路径。

它**已经**接入了运动输入的**快照纠偏通道**：`motionParams()` 把方向、长度、粗细、延迟、持续、形态写进 `DanmakuMotionState` 的 `P_LASER` 块（`41..47` + `48`），由 `DanmakuMotionState.fingerprint` 覆盖。但该块描述的是一个**常量**姿态，不是年龄的函数。

约束（来自既有 spec 与架构纪律）：

- 姿态与阶段 MUST 是**年龄的纯函数**，双端同构，零逐 tick 包（`danmaku-laser`「阶段 SHALL 由年龄推导」、`danmaku-age-continuity`）。
- 双端各自算一遍的曲线 MUST 只用**四则运算**（`DanmakuSpeedProfile` 的纪律）；含超越函数的 `Rotation.about`（Rodrigues）已在曲射路径上被接受，本次复用不引入新风险类别。
- 形状（几何）与编排（运动）**严格正交**（`danmaku-behaviour`、`BehaviourDecouplingTest`）。
- `DanmakuEmitter` 明确**不对激光施加 `Behaviour`**，理由被写进注释：「延迟期算不算隐藏态没有答案」。本变更必须回答它。

`openspec/changes/fix-danmaku-laser-render-and-frozen-expiry` 正在并行推进激光的渲染豁免与绝对寿命，**与本变更正交**（那条改渲染门控与寿命时钟，本变更改姿态与显隐），二者在 `LaserDanmakuRenderer` / `LaserDanmaku` 上会碰到同一批文件，实现时须协调。

## Goals / Non-Goals

**Goals:**

- 激光姿态成为**年龄的纯函数**：绕（可配的）枢轴旋转 + 恒速平移；旋转支持「扫射到一定角速度后线性减速停住」。
- 激光支持**隐藏态**：复用 `DanmakuPhase` 的周期相位，仅作用于激活期火柱（压暗 + 不判伤），零额外同步。
- 复用基类既有同步字段：**零新 accessor、不动 `PARAM_COUNT`**；姿态输入天然被运动指纹覆盖。
- 回答并固化 `DanmakuEmitter` 悬置的问题：隐藏只作用于激活期。
- 新增 `/danmaku` 测试命令验证手感。

**Non-Goals:**

- 不做轨道层接入（`Shape.LASER` / `Projectile.laser` 运动参数 / `Behaviour`），符卡表本次仍不直接写动态激光。
- 不做平移的速率曲线（「飘着飘着停」）——基类只有一条 profile，本次让给旋转。
- 不用事件/关键帧下发姿态或阶段；不改 `age()`、存在性判据、协议结构。
- 不改激光的判伤频率（5 tick）、伤害口径与四种视觉层的构成。

## Decisions

### D1：姿态 MUST 是年龄的纯函数，不引入事件/关键帧

`pose(age) = (origin, direction)` 由同步参数与年龄闭式求出，双端各算一遍。理由：与 `danmaku-motion-and-rig`「位置是 t 的纯函数」、`danmaku-event-sync-deferred`「事件延后/不需要」一致；事件化会引入「哪一端先到」的收敛问题。**备选**：服务端逐段下发姿态 → 每秒数十包，直接违背低带宽设计；否决。

### D2：扫过角度 = `DanmakuSpeedProfile.travelAt(age)`（复用，不改公式）

球弹用 `DanmakuSpeedProfile` 描述**线速率**的分段线性曲线，`travelAt` 是其**闭式积分**（分段二次，纯四则运算）。激光把同一串数**重新解释为角速度**（deg/tick），`travelAt` 即**扫过的角度**（deg）：

```
θ(age) = profile.travelAt(age)          // deg
O(age) = pivot + R(A, θ(age)) · (O₀ − pivot)
D(age) = R(A, θ(age)) · D₀              // R = Rotation.about
```

于是「以 ω₀ 扫、N tick 内线性减到 0、然后停住」= `DanmakuSpeedProfile.decelerateAndHold(ω₀, N)`——**已有命名工厂，一行**。

理由：你需要的「慢慢停下来」是**斜坡**而非**台阶**。**备选 A**：常量角速度（`DATA_CURVE_RATE` 那套）→ 表达不了减速；**备选 B**：步进关键帧时间表（仿 `DanmakuLegMotion`）→ 只能做台阶式「突然停」，做不出「线性减速」，且要新增一整套同步结构与解释器；**否决**。复用 profile 让「球弹的减速」与「激光的减速」是**同一条曲线族**，手感一致、验证共用。

### D3：姿态参数**复用基类既有字段**（零新 accessor）

基类已携带、且**已经在运动指纹里**的槽位正好够用：

| 用途 | 字段 | 指纹槽 |
|---|---|---|
| 枢轴 / 出生点 | 编队帧 `DATA_FRAME_CX/CY/CZ`（枢轴）与 `DATA_FRAME_OX/OY/OZ`（出生点−枢轴） | `P_FRAME_BASE` |
| 旋转轴 | 编队帧 `DATA_FRAME_AXIS_YAW/PITCH` | `P_FRAME_BASE` |
| 角速度曲线 | `DATA_HAS_PROFILE` + `DATA_PROFILE_*` | `P_HAS_PROFILE` / `P_PROFILE_BASE` |
| 平移方向 | `DATA_AXIS_X/Y/Z` | `P_AXIS_X/Y/Z` |
| 平移速率 | `DATA_FRAME_ADVANCE_SPEED` | `P_FRAME_ADVANCE` |
| 显隐 | `DATA_PHASE_PERIOD/DUTY/OFFSET` | `P_PHASE_*` |

`LaserDanmaku` 覆写了 `tick()` 且不调基类 `tickDanmaku()`，故这些字段对激光**没有第二个消费者**，语义重载不产生行为冲突。**取法 B**（新增激光专用字段）语义更干净，但新字段必须进指纹 ⇒ `PARAM_COUNT` 63→N，改动面大且要同步编解码两处；**本次不走**，记为将来可选。

**代价（必须正视）**：同一字段对球弹与激光含义不同。缓解：① 在字段定义处与 `LaserDanmaku` 文档写明「激光重解释为角度/姿态」；② 补测试断言「激光不进入基类 profile 路径」（即 `LaserDanmaku.tick` 不调用 `tickDanmaku`）；③ 姿态输入若泄露给球弹路径即为 bug，由指纹 + 断言双守。

### D3a：实现期补充——复用需要基类暴露少量写入法

D3 的前提是「基类字段可被激光写入」，但那些字段是 `private static final`，子类够不着。故实现时在基类补了**少量泛用方法**（不新增 accessor、不动 `PARAM_COUNT`）：

| 新方法 | 用途 | 复用的字段 |
|---|---|---|
| `configureAngularProfile(profile)` | 写角速度曲线，**不过滤** `varies()` | `DATA_PROFILE_*` + `DATA_HAS_PROFILE` |
| `setMotionAxis(Vec3)` / `motionAxis()` | 平移方向 | `DATA_AXIS_X/Y/Z` |
| `configureAdvanceSpeed(double)` / `advanceSpeed()` | 平移速率 | `DATA_FRAME_ADVANCE_SPEED` |
| `setBehaviorFlag(int, boolean)` / `hasBehaviorFlag(int)` | 置/清行为标志位 | `DATA_FLAGS` |

- **恒定角速度必须走同一条路径**：`configureSpeedProfile` 用 `varies()` 拒收恒速，而「匀速扫射」是最基本形态。新增的 `configureAngularProfile` 不设该门槛，故恒速与斜坡共用 `travelAt` 一条闭式路径（这也是本变更为何需要它）。
- **枢轴 / 出生点 / 旋转轴**复用 `bindToFrame`（`center=pivot`、`offset=出生点−pivot`、`axis=旋转轴`），不新增字段。
- **基准方向语义**：`DATA_DIR_*` 明确为**基准方向 D₀**（输入，进指纹）；旋转后的**当前方向**由 `getCurrentDirection()` 现算，供渲染 / 判伤 / 裁剪使用。二者在静止激光上相等。
- **位置历史（task 2.3）**：核实 `ServerLevel` / `ClientLevel` 的 `tickNonPassenger` 在 `tick()` <b>之前</b>调用 `setOldPosAndRot()`，故移动激光的渲染插值无需额外代码。
- **存档持久化**：基类已把 profile / frame / axis / phase 写进 NBT，复用后激光姿态随存档免费得到持久化，缺键旧存档退化为恒等（静止）姿态。

### D4：平移取恒速（取法 A 的收尾）

基类只有**一条** profile。本次把它给**旋转**（减速停是核心手感），平移取**恒速**（`DATA_AXIS_*` 方向 + `DATA_FRAME_ADVANCE_SPEED` 标量）。需求 3 只要求「能够平移」，恒速满足。**平移也走速率曲线**在需要时另开一步（届时按取法 B 加第二条 profile）。

### D5：枢轴默认 = 发射点

`O(age) = pivot + R(A,θ)·(O₀ − pivot)`。`pivot = O₀`（默认）⇒ 位置不动、只有方向转（**原地旋转**）；`pivot = O₀ + offset` ⇒ origin 沿圆弧走（**绕外部点挥扫**）。枢轴偏移作为姿态参数同步。理由：需求 1 明说「默认是发射点」，而「绕外部点」由同一公式免费得到，不需要第二种运动类型。

### D6：隐藏态复用 `DanmakuPhase`，且**仅激活期**生效

`hidden(age) = getPhase() == ACTIVE && DanmakuPhase.isHidden(age, period, duty, offset)`。

- 复用 `DanmakuPhase` = 「同弹幕逻辑」的字面兑现：周期/占空比/相位三项标量，年龄推导，**零额外同步包**（`danmaku-visual-profile` 相位隐藏条款）。
- **仅激活期**是回答 `DanmakuEmitter` 悬置问题的裁决：**延迟期预警线是玩法信息**（R1：前向威胁靠预警兑现），隐藏它会让旋转扫射变成「无预警的不可见威胁」，违反可读性契约。
- 周期相位天然允许**发射期内反复切换**（需求 4），无需事件。

### D7：隐藏渲染逐层压暗，加法层按平方

沿用 `danmaku-visual-profile` 已确立的相位隐藏渲染规则：**每一层**都降 alpha；**加法层**（外发光、亮核）同时降 alpha 与 RGB，即贡献按暗态系数**平方**下降；**alpha 混合层**（主体）只降 alpha、不压暗 RGB。隐藏态**渲染类型不变**（仍写深度）。激光整体走加法/自发光，故其所有层按平方口径处理。**不这么做**的症状已被 spec 记录：加法层不平方压暗会读作「隐藏态比常态更亮」。

### D8：旋转期预警线跟随旋转

渲染器本就以 `getLaserDirection()` 对齐坐标系，而该方向现由同一个 `R(A,θ)` 驱动 ⇒ 预警线**天然跟随**，无需新分支。公平性：玩家在 DELAY 期就能读出扫向来向与时机。

### D9：姿态输入 MUST 进运动指纹

新姿态字段（D3 表）**全部已在 fingerprint 覆盖范围内**——这是取法 A 的额外红利。若两端姿态参数不一致，指纹必不同、纠偏通道可检出。**MUST NOT** 存在「只由 `SynchedEntityData` 携带却不进指纹」的姿态字段（参照 `danmaku-laser` 对穿墙形态位的既有限定）。

### D10：厘清既有下标重叠

`P_LASER_BASE = 41`、`P_LASER_COUNT = 8` ⇒ 激光块占 **`41..48`**，而 `P_BURST_AT = 48`。索引 48 被两者共写。今日因激光与爆散弹不同源而不冲突，但属隐患。本变更一并**修正**（调整常量或明确归并），确保姿态参数落点无歧义。

## Risks / Trade-offs

- [移动激光的位置历史 `xo/yo/zo` 可能未被 `baseTick()` 维护，渲染插值逐 tick 跳] → **实现期第一件要验证的事**：若 `baseTick()` 不更新位置历史，须显式补 `setOldPosAndRot()`（或改调 `super.tick()` 的安全路径）。
- [两条裁剪路径（`getActualLength` / `getRenderLength`）随移动起点更新，逐形态不变量可能被破坏] → 保留「同一形态视觉长度 == 判伤长度」的既有不变量，补测试；起点不同仍须两次裁剪。
- [`makeBoundingBox()` 对快速扫动滞后，视锥剔除可能在两 tick 间裁掉光束] → 包围盒并入**当前 tick** 姿态；必要时按最大单 tick 扫过角适度外扩（有界）。
- [语义重载 `DATA_PROFILE_*`（球=线速、激光=角速）易误用] → 见 D3 三条缓解；补断言测试。
- [相位计时相对 `age()==0`，而激活期从 `delay` 起，激活期内的明灭窗口被延迟相位平移] → 定参时用 `phaseOffset` 对齐；文档记录。
- [与并行变更 `fix-danmaku-laser-render-and-frozen-expiry` 触碰同一批文件] → 实现顺序上让渲染门控豁免先落，本变更只在其上加「乘暗态系数」。

## Migration Plan

1. 无存档迁移：新增语义全部复用既有字段，旧存档中这些字段为默认值 ⇒ 姿态退化为**恒等（静止）**，行为不劣于变更前。
2. 回滚：还原代码即可；不新增 NBT 键、不改协议结构。
3. 落地顺序：先姿态（旋转 + 平移）→ 再隐藏（判伤门控 + 渲染压暗）→ 最后 `/danmaku` 命令与手感核对。

## Open Questions

- `/danmaku` 新命令的参数形态（角速度/减速 tick/轴/枢轴/平移/周期/占空比），是否复用 `/danmaku laser` 加可选参数，还是新命令 `spin-laser`？倾向新命令，避免既有命令签名变长。
- 平移的速率曲线（需求外）何时做？倾向：确有内容需要时按取法 B 开独立步骤。
- 旋转轴是否要与「曲射轴」共用约定（`Rotation.axisFromAngles` / `anglesFromAxis`）——复用以保证「角度↔轴」不手算（该换算错了不报错，是实测过的静默失败）。
