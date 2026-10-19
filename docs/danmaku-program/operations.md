# 操作目录与作者接口

本文是 design 的组成部分。名字为建议 API 名，语义与组合契约为实现要求；不要求先造一个通用脚本 VM。首批操作覆盖全部现有符卡及组合验收，后续扩展遵循同一注册边界。

**状态：纯核、世界运行、网络与游戏接线已有实现，正式15卡使用程序战斗。** 实际API和限制见[可运行作者指南](../danmaku-program-authoring.md)及[阶段2交接](../../openspec/changes/archive/2026-10-19-add-danmaku-program-operations/operations-api.md)，逐项状态见[操作台账](../../.opencode/skills/boss-dev-design/references/danmaku-operations.md)。本文仍是语义设计目录，表中建议名不全部对应同名Java类型。先读`author-guide.md`理解术语，再读`pattern-recipes.md`看组合案例。§10同样属于本次交付，配方中的future项不属于本次交付。

## 1. 四类扩展点

| 类型 | 输入 → 输出 | 可否读世界 | 新增时需要实现 |
|---|---|---|---|
| PatternGenerator | 类型化参数、frame、seed/index → 有序 MemberTemplate | 否 | 参数 codec、生成、数量/包络上界、fixture |
| Path/TransformEvaluator | 时间、段起态、参数、成员索引 → 位置/方向/速度/变换 | 否 | codec、任意时刻求值、边界连续性、bounds |
| DecisionProvider | server world、已解析上下文 → 小型权威结果 | 仅服务端 | 查询、成本上界、结果 codec、目标失效规则 |
| Presentation/CollisionProfile | 样式/碰撞参数、解析状态 → 画面或服务端接触规则 | 渲染不决定玩法；碰撞由服务端执行 | 专属适配、包络与测试；不更改运动解释器 |

图案作者可以组合多个基础 generator 与路径；确需专有组内表演时注册 `LocalPatternEvaluator(t,index,params)`，必须满足纯函数、恢复、bounds 契约。不能通过自定义图案回调任意发包、生成世界实体或保留隐藏可变状态。

## 2. 通用约定

- 时间内部单位 tick，支持分数时间求值；长度格，速度格/tick，角度 API 明示 deg/rad，内部统一；整数列数不再塞进 `gapDeg`。
- 句柄有类型：FrameRef/TargetSetRef/GroupRef/PlanRef/SelectionRef；只有声明后且作用域存活才能引用。命名不是身份，重复执行有 occurrence。
- 操作分 `纯描述 P`、`服务端决策 D`、`本地表现 V`。已知定时 P/V 不产生逐次网络事件；D 的输出进入 DecisionBatch/checkpoint。
- Select 只选稳定 index/tag/range/targetSlot；基于实时距离的选择必须经 D。MemberTemplate 包含局部坐标/方向、弹种、视觉、碰撞半径、伤害倍率、tag，不含任意世界引用。
- 有限结构默认上限建议：层级8、单路径64段、单定义4096节点、单 batch 512成员（内容常规仍≤48），解码后再验证；这些是防失控上限，可配置但须双端一致。不是为了 accessor 限制内容类型。

## 3. 时间组织（组合器）

| 操作 | 参数/结果 | 语义 |
|---|---|---|
| At | typed time、child | 在指定时间域的绝对时刻执行 |
| Offset | duration、child | 相对当前调度容器起点执行；随Repeat/Every每次occurrence平移，周期内0/20/40子拍使用此项 |
| Sequence | 子节点列表 | 后一项从前一项声明时长末尾开始；不是等待世界条件 |
| Parallel | 子节点列表 | 同起点并行；冲突通道拒绝，不靠执行顺序覆盖 |
| Wait | duration | 只改变程序游标；不让现有弹自动停止 |
| Repeat | count、period、body | 有限重放，body 内偏移必须落在合法区间；每次有独立 occurrence |
| Cycle | length、tailPolicy、body | 卡循环；重置本轮快照，保留显式跨轮组 |
| Every | period、phase、end或untilCardEnd | 持续发射流，所有偏移子拍都生效；不再先用 tick%period==0 过滤整轨 |

At的时钟锚点不随Sequence游标或Repeat次数隐式改变。局部偏移必须使用Offset；GroupTime/MemberAge求值必须提供对应组/成员锚点。

一次性 `Playback.Once` 可包含任意多拍，必须有结束策略；循环 `Playback.Loop(length)` 和持续流显式不同。`endless` 不等于无限寿命。需要等待「目标死亡」等外部事实时用 D 条件事件，不滥用跨轨回调依赖。

## 4. 快照、组与发射

| 操作 | 类型 | 关键参数 / 输出 |
|---|---|---|
| CaptureTargets | D | 距离/资格/最多5 → 固定目标槽集合；按距离选最近主目标，同距用UUID打破平局，然后固定槽号 |
| CaptureFrame | D | owner眼位、target point、up策略 → O/F/R/U；近竖直用上一有效R，首次用确定回退轴 |
| SampleTargetPoint | D | targetSlot、采样时刻、胸口/眼位 → 固定世界点或明确无目标 |
| ResolveSurface | D | 采样点、向下/首空气、最大步数 → 坐标与有效mask |
| CreateGroup | P | parent、frameRef、timeOrigin、tags、owner/card/cycle归属、tail/cancel策略 |
| ForEachTarget | P | 固定目标槽集合、body → 每槽独立子组；不遍历实时列表 |
| PrepareEmission | P或含D结果 | pattern、source变换、direction规则、birthTick → 不可变 PlanRef |
| Telegraph | V | PlanRef、lead、source/outline/beam/path显示、颜色 → 无害显示 |
| CommitBatch | D | PlanRef、安全策略、配额 → 全批通过或拒绝结果 |
| Emit | P（受Commit门控） | 已批准PlanRef、member program → 实例化同一成员集合 |
| Select | P | tag/index/targetSlot → 只读成员选择器 |
| SpawnChild | P+准入D | 父成员解析姿态、child pattern/program、继承策略 → 派生组 |

宏 `EmitWithWarning` 只是 Prepare→Telegraph→Commit→Emit 的组合，不另写一套隐藏生成逻辑。共享轴可以跨 lane 引用同一个 FrameRef。偏移源瞄准必须使用 `(targetPoint - actualSource)`，不能沿用未偏移 O→T 方向。

为蓝卡1同组四拍：锁轴时准备左右两源与目标点，四个Plan共享该输入；同组首次符印持续存在并在每次发射短闪，不因每拍重新预警而重采样。

## 5. 图案生成器（不承担生命周期）

| 图案 | 专属参数 | 需要它的内容 |
|---|---|---|
| Point / Union | 一点；多个生成器与tag | 花心+花瓣、双源 |
| Ring / DiscRing | count、radius、gapAngle、phase、plane | 大妖精环、现行环阵 |
| Fan | count、spreadAngle、centerRotation | 底噪、窄扇；phase确实影响方向 |
| AngularGrid | rows/cols、yaw/pitchSpacing | 大妖精5×5弧面墙；不得混成平行面板 |
| ParallelGrid | rows/cols、spacingX/Y、mask、offset | 蓝3×3、4×3、3×7挖门 |
| Rose / Spiral | petalCount/samples；turns/rMin/rMax/handedness | 花海、山女呼吸螺旋 |
| ConeScatter / VolumeScatter | 分布、角包络/体积、seed channel | 随机蛛丝、毒雾、雨、地雷 |
| SurfaceRays / AxisNodes | 节点距离列表、每节点方向/内缩 | 光柱；蓝节点6/10及±R双射线 |
| Shell / Cage / Radial | 面/半径/缺口/数量、速度方向规则 | 露米娅现有母题与兼容内容 |
| AroundPoint / Lattice | 半径区间、瞄准比例、散射角 | 旧蓝迁移探针及既有调试能力 |

ParallelGrid 的门是显式列 mask，不复用角度。3×7去中央列真实18发；AxisNodes 的count是节点数，展开数另算，2节点×2方向=4激光。`Union(core,petals)` 共享组快照，核心tag决定4倍伤害和单独重瞄，不在 Rose 内写死花心。

旧形状输出与新生成器不必同类名；必须记录方向、数量、缺口和局部坐标对应。形状不强制所有运动沿法线：推进路径由作者明确选法线、径向或自定义方向。既有「法线推进」作为螺旋/花形内容约束，不能升级成所有图案的数学限制。

## 6. 运动与变换原子

| 操作 | 输入 | 输出/组合约定 |
|---|---|---|
| Hold | duration或明确holdUntilEnd | 固定位置；保持判伤除非单独关闭 |
| Linear | direction、speed、duration/tail | 世界/局部直线路径 |
| SpeedCurve | fixedDirection、(tick,speed)关键帧 | 分段线性速度闭式积分；支持0/负速 |
| Arc | 起始切向、旋转轴、角速率、线速度、duration | 恒速弧线位移；角速率0退化直线 |
| Segments | typed path段、显式tail | 缓存段起止位置/速度；尾部Continue/Hold/End必须选一种 |
| Rotate | axis、angleCurve、pivot | 旋转组/局部坐标或激光朝向；作用通道显式 |
| Orbit | radius、axis、angularCurve、phase | 对中心增加环绕偏移，和自转分别声明 |
| ScaleCurve / Breathe | scale曲线；base/amplitude/period | 改局部偏移，不改组平移和默认碰撞半径 |
| ComposeTransform | 有序变换列表 | 非交换次序显式，不能靠API调用偶然顺序 |
| OrientAlongPath | pathRef、up策略 | 仅显式开启才让整片平面随切线转向 |
| AimAtPoint | 已采样点、起点 | 纯方向表达式；实时采样由D完成 |
| RadialDirection | 参考中心、当前位置、退化方向 | 爆散方向；中心弹退化策略必须声明 |
| Detach | selection、effectiveTick、velocityPolicy | 连续世界位置、切向速度或新方向；保留取消归属 |

`CurveWindow` 不再是全局开关：`Segments(Linear(30), Arc(30), LinearTail)` 自然表达。减速曲射可以在未来增加 `ArcLengthPath + distanceCurve` 扩展；首版不伪称任意速度函数×任意转向都有闭式解，不足时注册有确定性误差界的路径求值器，仍不能读世界。

段边界默认位置连续；平滑要求切向速度连续。`Detach/Retarget` 允许有意速度跳变，必须声明 `discontinuousVelocity=true`，不能把它叫平滑转弯。蓝卡6为25°/30tick，即约16.6667°/秒；起始±25°，圆弧结束严格回F。

## 7. 派生、世界触发与表现

| 操作 | 类型 | 语义 |
|---|---|---|
| EmitAlongPath | P+Commit D | 每N tick以父成员在该时刻的解析世界位置生成子组；即有伤害尾珠，不等同渲染拖尾 |
| SplitAt | 宏 | 到点执行ReplaceMembers；结束母弹与生成子代为同一事务，失败策略必填，详见§10 |
| TriggerProximity | D | 服务端查触发半径、一次性触发标记；未触发期可显式关闭接触伤害 |
| DamageWindow | P | 半开区间伤害门，可MemberAge或CycleTime；不改变位置 |
| VisibilityCycle | P/V | 周期正常/半透明隐藏；普通弹和激光隐藏时仍可见，alpha及亮度降低，完全跳过实体/方块碰撞，伤害与DamageWindow取AND |
| Flash / SoundCue | V | 一次性时间提示、尺寸/亮度脉冲、音效去重；物理尺寸不变 |
| Fade | V | alpha曲线；不自动等同停判伤，Collapse宏组合二者 |
| LaserWindow | 宏 | 同一路径的预警线→有害光束→关闭，复用姿态通道 |
| LockBossMovement | D（owner控制） | 声明区间占用移动锁，结束/取消释放；不改变已有通用游走算法 |

尾珠静止40tick后随机漂移，由同一个子程序 `Hold→Linear(seedDirection)` 表达；方向seed可由父member和childOrdinal派生，不逐珠发送随机向量。尾珠出生是否受预算允许仍必须权威确认，可批量编码准入mask。

激光的position/orientation和球弹来自相同变换模型，只有碰撞与视觉profile不同，不再另设一套互不相容的LaserMotion。

## 8. 生命周期原子

| 操作 | 范围 | 语义 |
|---|---|---|
| StopEmission | group/tree/lane/card | 停止未来发射，已发成员继续 |
| EndMembers | selection | 即时撤销判伤与显示，归还配额 |
| Collapse | scope、fadeTicks | 即时关伤害+停止未来发射+预警撤销+淡出；后代一并处理 |
| CancelScope | scope、reason、policy | 权威中断，同一语义可用于安全拒绝/目标失效/预算拒绝 |
| EndCard / EndEncounter | owner作用域 | 清所有所属攻击、派生、预警和控制锁；背景是否包含由scope决定 |
| Lifetime | MemberAge deadline | 到点结束，不改变路径 |
| EndOnReturn | path progress条件 | 在已越过最远点后过原点终止；不是默认曲线属性 |

CancelGroup 不会撤回过去伤害；若要求预警开始前保证整轮一定成功，只能使用已知静态条件，不能预知未来玩家位置。蓝采用逐批出生检查、失败即取消该组剩余与在场成员。

## 9. 作者示例（伪代码，非最终API）

```text
card(loop=240, switchAtBoundary=true):
  at(cycle=0): frame = CaptureFrame(primaryTarget)
  group(left, frame):
    at(cycle=0):
      plan = PrepareEmission(ParallelGrid(4,3,1.5,2), source=(-4,0,0), birthTick=20)
      Telegraph(plan, lead=20)
    at(cycle=20):
      if CommitBatch(plan, minClearance=6) accepted:
        Emit(plan,
          groupPath=Segments(
            Linear(direction=rotateAround(frame.U,F,+25deg), speed=.20, duration=30),
            Arc(axis=frame.U, turn=-25deg, speed=.20, duration=30),
            ContinueLinear()),
          memberAgeLifetime=120)
  at(cycle=220): Collapse(cycleGroups, fade=10)
```

同样的路径可以换 Ring、Rose 或激光节点；同样的方阵可以换速度曲线，或以子组身份加自转。为这类替换修改实体主循环、增加全员通用字段或复制同步恢复逻辑，均视为组合架构未达标。

## 10. 常用扩展基础（本次必须实现）

| 操作 | 类别 | 明确语义 |
|---|---|---|
| AtSegmentEnd | 组合器P | 引用有限段标签，在结束边界运行一次；不是每tick检查浮点速度是否等于0 |
| Stagger | 组合器P | 按稳定index/tag给子程序增加index×delayStep时间偏移；删除成员不压缩索引 |
| ReplaceMembers | 事务宏P+D | 固定母弹边界状态，检查净预算后原子结束母弹/创建子代；scope为perParent或allSelectedParents；onReject为endParent/keepParent，必须填写 |
| SeedDirection | 表达式P | UniformSphere/Cone/PlaneCircle；seed含完整父谱系、代数、孩子序号、用途channel；球面不能用纬度均匀采样造成两极堆积 |
| SphereSurface | 图案P | count/radius/distribution/seed，位置位于球面；方向独立选Outward/Inward/固定方向 |
| SphereDirections | 图案P | 从同一点向球面方向发射，可规则均匀或seed随机；不同于在球面上出生 |
| HelixColumn | 图案P | 局部竖轴、height/turns/arms/samples/radiusCurve；有限螺旋点集 |
| CreateEmitter | 组合P | sourceRef、orientation曲线、schedule、childProgram、onSourceLost；本身无碰撞，可关联有碰撞carrier |
| MemberPose / GroupPose | 纯SourceRef | 每次发射时求指定成员/组的位置、姿态、速度；不读渲染坐标 |
| ExternalEntitySource | D SourceRef | UUID引用；每次发射服务端采样，结果附入Commit；onLost=stopEmission；禁止本地插值作权威 |
| Billboard | 表现V | texture/size/tint/visualAngleCurve；面向摄像机只用于画图，不参与炮口方向 |
| WaveOffset | 路径/变换P | 沿声明局部轴添加A×sin(2πt/period+phase)，提供解析速度，幅度不积累 |
| BezierPath | 路径P | 明确2/3阶控制点与TimeMap(u)，u∈[0,1]；首版非恒弧长速度，尾策略必填 |
| MirrorTransform | 变换P | 指定局部平面镜像位置和方向，保持index/归属；明确处理视觉朝向 |
| PhaseOffset | 表达式P | 给角度/周期函数增加固定相位或index×phaseStep，不改变其他实例 |
| RebasePath | P或D结果驱动 | effectiveTick以解析位置作为新段起点、替换未来路径；采样目标来自D；明确速度是否跳变 |

CreateEmitter是逻辑发射节点，可绑定有害成员、无害路径节点或普通世界实体。后者位置是D输入，前两者为纯时间函数。Emitter只决定下一发在哪里出生，不决定已发弹是否跟随。

Emit必须声明`attachment=worldSnapshot | followSource`和`velocity=absolute | addSourceVelocity`。worldSnapshot出生后独立飞；followSource保留父变换依赖。absolute使用声明世界速度；addSourceVelocity把出生时源的世界切向速度相加一次。世界快照出生不必Detach，因为从未绑定；Detach只用于已经follow的成员。ExternalEntitySource首版仅支持worldSnapshot，持续贴附留给future采样跟踪。

源消失停止未来发射，已发worldSnapshot成员继续。followSource必须声明`onSourceLost=end | detachAtLastAuthoritativePose`。普通成员死亡不等于CancelScope；Replace结束母弹时先烘定子代世界起态，不能将孩子一起取消。卡/Encounter结束仍清全部后代。

替换事务：采样母弹边界→保留将释放配额→检查全部子代→同一提交更新存活mask和成员→发布同revision结果。一个母弹换2颗只需净增加1名额。keepParent失败时消费该次触发，按tail继续，不每tick重试；成功与拒绝都记账。详见pattern-recipes第一例。

高频首版为整数tick周期和同tick多炮口，按服务器tick而非FPS调度；准入在同tick合并为DecisionBatch。固定角速率无需每帧同步。亚tick发射留作后续扩展。

默认变换顺序：`world = capturedFrame × Translate(groupPath) × Rotate(groupAngle) × Scale(groupScale) × localPosition`。从右向左：先局部缩放，再绕本组原点旋转，再平移，最后映射世界。自定义Compose显式列出次序；不把同一推进速度重复加在组与成员上。
