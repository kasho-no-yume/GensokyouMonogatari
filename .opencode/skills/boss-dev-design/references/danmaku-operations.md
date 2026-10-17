# 弹幕操作台账

当前续接：用户授权以tasks§3集中压力验收替代旧严格M2阻塞，代表项2.1–2.6已收口，当前10/16；下文5/12及完整旧矩阵阻塞为历史。重新准备`ProgramStressLoad`，通过`gs_program_benchmark start stress 800|2000 300 <player>`复用正式runtime/网络/碰撞；200tick父寿命、age60派生160子弹（寿命80）+8旋转激光，目标峰值808/2008。`tools/program_stress_run.py`保存原始stats/ledger/frames/proxy和停止证据；尚未实机通过。旧build/reports/stress/stress_mixed-r1报告存在恢复数量误报，原stats恢复171/最终173，不作为通过证据。

最后核对：2026-10-17。游戏程序后端已有显式预览闭环，正式BOSS仍走旧后端；实机与M2验收未完成。该台账与代码同步更新；设计文件不是实现证据。

最终用户更新：petal_release、roaming_tower已实机确认表现通过，不再待观察；500ms高延迟整批闪烁仍严重，ProgramClock冻结grace未根治，不标稳定性通过。完整重写的第五期[next-session-handoff.md](../../../../openspec/changes/add-danmaku-program-gameplay/next-session-handoff.md)为下一会话入口，旧段落的待验收措辞属历史。整项网络矩阵/多视角与量化M2仍欠，任务主项5/12，不归档/迁移。

最新用户证据：dynamic_warning/high_frequency_circle/sphere_split/moving_carriers/参数化moving_tornado在单人及500ms独立服通过表现/性能，不能外推量化M2。高延迟闪烁日志为display-error恢复，ProgramClock补最多1tick冻结呈现桥接并有严重积压撤销；用户无需复测。新增petal_release通过现有稳定IndexRange释放3整瓣27成员，roaming_tower以Bezier+HelixColumn+ChildAction顶节点每20tick发环，24批192子弹；ProgramSplitPreviewTest/ProgramMotionLabTest覆盖计数、路径保持、wire/包络、独立子代与清场，新增两项仍待集中观察。

本轮续接：最新用户确认partial_release/motion_lab/moving_tornado/rotating_laser全部实机通过，花海/尾珠及split碰撞修复也已确认。新增dynamic_warning（EmissionPlan共享GroupMotion闭包，预警/激活相同组时钟）、high_frequency_circle（正式CreateEmitter，独立Billboard FX、源闭包/预发射兴趣）、sphere_split（SphereSurface减速/具名段尾替换）、moving_carriers（3×4×8）与参数化HelixColumn。ProgramMotionLabTest、ProgramSplitPreviewTest、ProgramSourceIntegrationTest覆盖FPS、数量、预算拒绝/20次回收、following激光轴及源丢失脱离。新fixture仍待集中实机；外部/成员源FX暂不投影。

ProgramLatencyMatrixTest新增72组双向队列延迟/停顿/低TPS副本测试，输出build/reports/program-latency-matrix.csv，不代替真实多人。ProgramScalingGateTest对完整确定性pattern+seed批次测得48→480逻辑字节2415→2529（1.047倍，门槛≤2），空间子集/例外仍展开。NetProfile.program_tickcost接程序host原始CPU样本，现有ClientFrameProbe可记整帧；tools/program_network_matrix.py与program_ab_report.py准备矩阵/汇总证据。完整等负载M2仍欠，不能进入迁移。

阶段5：`program/gameplay/`已有碰撞/伤害/渲染适配与基础诊断；`ProgramPreviewHost`、`ProgramPreviewPrograms`、`ProgramPreviewBounds`装配正式session、网络、静止owner与线性网格fixture，入口`/gs_program_preview start blue1_slice|stop|stats`。`ProgramPreviewTest`验证编译程序预警/出生/恢复/owner丢失/终止回收、全寿命包络以及正式定义晚加入到render view和End抗旧包重放。完整蓝1/4/6、组合案例、实机多人/延迟和A/B未通过，不能进入迁移。详见[第五期交接](../../../../openspec/changes/add-danmaku-program-gameplay/handoff.md)。

阶段4：`program/network/`已提供有界协议、批次共享/mask投影、纯客户端副本、共享时钟、幂等/tombstone、分片原子提交、按包络订阅/父源闭包、空间分片发布、恢复限流和NeoForge显式接线。协议测试不替代真实延迟、画面或流量性能验收；真实内容包络生产者交gameplay。详见[network-api.md](../../../../openspec/changes/add-danmaku-program-network/network-api.md)。

阶段3：`program/server/`已有runtime、ProgramSession/Execution、编译程序控制操作、GroupMotion、Plan/Commit/Replace、有限查询、发射器、伤害门与显式宿主。`WorldRuntimeTest`验证真实内存事务和session恢复；真实专用服探针验证半砖顶面、首空气、通路与level tick。正式BOSS、网络、碰撞和画面未迁移。详见[server-api.md](../../../../openspec/changes/add-danmaku-program-server/server-api.md)。

2026-10-16：已按[路线图](../../../../openspec/changes/refactor-danmaku-program-runtime/roadmap.md)拆成7个子change。core阶段（`add-danmaku-program-core`）已完成：纯核实现、测试、旧后端花海三次基线与阶段验证均有证据；operations已有离线实现。描述完成不表示游戏能力已可用。

## 状态规则

第五期2.2续接：`ProgramPreviewPrograms.flowerSea/tailPearls`和`start flower_sea|tail_pearls`已实现两轮预览；`ServerControlOperations.RebaseTarget/RadialRelease`接一次权威目标采样/世界路径重置和边界花心径向释放。ProgramBlue1Test覆盖数量/伤害门/跨轮、母死亡/预算拒绝/漂移及网络恢复；实际画面、多人和M2仍待验收，不计为正式卡迁移完成。

代表预览续接：`ProgramPreviewPrograms.blue1/blue4/blue6`已接正式单轮runtime，命令`start blue1|blue4|blue6`；`ServerControlOperations.OffsetGroup`共享帧偏移子组、`Corridor.local`激活前世界通路检查可编码，ProgramBlue1Test验证数量/时序/多人固定槽/恢复/断口/圆弧偏移和正式定义晚加入。已补蓝6未来弧线提示、尾窗淡出和激光裁剪长度事实到客户端（含零长度）；尚无新三卡/动态方块实机证据，不能标M2通过。

第五期续接：普通程序弹已通过`ProgramVisuals`选择DEFAULT/STAR_PRISM本体，`DanmakuMeshes`共享五角星几何，Style.visualRadius与collisionRadius独立；`ProgramCollisionGeometry.sphereAabb`精确球体扫掠排除扩箱角落误中，ProgramCollisionGeometryTest/ProgramPreviewTest验证。完整视觉档案层与实机样式验证仍待完成。预览入口增加`start blue1_slice star`。

- `planned`：已有设计，无新后端实现。
- `implemented`：必须附真实文件/类型/方法；仍需验收。
- `verified`：必须同时附代码入口、测试或实机证据。
- `legacy-only`：只存在于旧实体路径，不代表新后端支持。

一个条目包含多个操作时，实现进度不同时必须拆行。归档change后更新下面链接，不能让技能指向失效路径。

## 新程序操作索引

除下表单独列出的纯核能力外，其余条目仍为planned。真实入口与离线约束见[core-api.md](../../../../openspec/changes/add-danmaku-program-core/core-api.md)。

| 类别 | 操作 | 状态 | 代码/验证 |
|---|---|---|---|
| 网络 | Definition/Start/Checkpoint/Decision/ClockDigest/Resync/End、mask与谱系恢复 | verified（协议与双端模拟） | program/network；ProgramNetworkTest；实际多人/性能归gameplay |
| 兴趣 | 攻击包络/父源闭包/稳定身份空间分片 | verified（协议发布与重进入） | ProgramInterest/ProgramSyncServer.publishSpatial/ProgramNetworkHost.spatial；ProgramNetworkTest；真实内容包络生产者待gameplay |
| 纯核时间 | At/Offset/Sequence/Parallel/Wait/Repeat/Cycle/Every、Once/Loop/Stream | verified（离线） | program/ProgramSchedule、ProgramTime、ClockAnchors；ProgramCoreTest |
| 纯核编译 | hash/codec/注册/引用/通道/拓扑/数量寿命包络 | verified（离线，保守静态分析） | ProgramCompiler/ProgramCodec/ProgramBudgetReport；ProgramValidationTest、ProgramCoreTest |
| 时间扩展 | AtSegmentEnd/Stagger | verified（纯求值/描述） | ops/Actions、Transforms；ProgramOperationsTest；调度接线待server |
| 世界输入 | CaptureTargets/CaptureFrame/SampleTargetPoint/ResolveSurface/TriggerProximity | verified（服务端算法/有限查询） | server/DecisionProvider、ServerLevelDecisions、DanmakuWorldRuntime；WorldRuntimeTest；runProgramProbe真实方块查询；未迁移正式卡 |
| 准入 | CommitBatch | verified（服务端事务） | server/DanmakuWorldRuntime；WorldRuntimeTest完整batch/净距/近域/工作单位预算；不是CPU性能验收 |
| 组执行 | CreateGroup/PrepareEmission/Emit | verified（显式服务端实例） | server/ServerOperations、ServerControlOperations、ProgramSession；WorldRuntimeTest编译程序多拍共享组/恢复 |
| 目标展开 | ForEachTarget | implemented（固定槽描述；由Group.slot显式展开） | ops/Actions；server固定槽，不重排转派；内容宏归迁移 |
| 成员选择 | Select/AimAtPoint/RadialDirection | verified（纯函数） | ops/Selection、MemberMotion；ProgramOperationsTest |
| 发射源 | CreateEmitter/MemberPose/GroupPose/ExternalEntitySource | verified（服务端采样/解析姿态） | server/DanmakuWorldRuntime、GroupMotion；WorldRuntimeTest逐发UUID/丢源/旋转移动源/稳定batch；外部源仅snapshot |
| 分裂 | ReplaceMembers/SplitAt | verified（服务端内存事务） | server/DanmakuWorldRuntime.replace；WorldRuntimeTest净占用、两种scope/reject、去重、3→6→12两代事务与恢复；真实碰撞派生仍待gameplay |
| 随机谱系 | SeedDirection | verified（纯函数） | math/Deterministic、ops/Actions.SeedDirection；两代删支与球面分布测试 |
| 基础运动 | Hold/Linear/SpeedCurve/Arc/Segments/Detach/RebasePath | verified（纯函数） | ops/Paths、MemberMotion；ProgramOperationsTest |
| 变换 | Rotate/Orbit/ScaleCurve/Breathe/ComposeTransform/MirrorTransform/PhaseOffset | verified（离线） | ops/Transforms、Actions；ProgramOperationsTest |
| 扩展路径 | WaveOffset/BezierPath/TimeMap/OrientAlongPath | verified（纯函数） | ops/Paths、Transforms；ProgramOperationsTest |
| 图案 | Point/Union/Ring/DiscRing/Fan/AngularGrid/ParallelGrid/Rose/Spiral/散射/Shell/Cage/Radial/AroundPoint/Lattice/雨/球面/螺旋柱/轴节点/SurfaceRays | verified（纯生成） | ops/Patterns；ProgramOperationsTest真实数量/边界/编码；DiscRing使用Ring描述 |
| 扩展注册 | Path/Pattern与record codec | verified（离线） | ops/OperationLibrary、OperationDataCodec；OperationExtensionTest；真实网络待gameplay |
| 派生 | EmitAlongPath/SpawnChild | verified（服务端ChildAction/Emitter调度） | server/DanmakuWorldRuntime；WorldRuntimeTest母弹死亡/跨轮/独立孩子/准入；真实碰撞触发归gameplay |
| 子程序组合 | ChildProgram/ChildAction、组与成员独立时钟、父变换闭包、方向通道 | verified（纯描述与求值） | ops/Actions、MemberMotion、OperationLibrary.childProgramType；OperationCompositionTest：7N/4N、48批共圆、嵌套codec；准入事务待server |
| 视觉与判伤 | Telegraph/Billboard/Flash/SoundCue/Fade/DamageWindow/VisibilityCycle/LaserWindow | verified（描述与相位求值，画面/判伤未接入） | ops/Presentation；ProgramOperationsTest；实际声音/渲染/碰撞待gameplay |
| 管理 | StopEmission/EndMembers/Collapse/CancelScope/EndCard/EndEncounter/Lifetime/EndOnReturn/LockBossMovement | verified（服务端状态） | server/DanmakuWorldRuntime、ProgramSession、ProgramServerHost；WorldRuntimeTest淡出/锁/卸载/20次回收；BOSS移动锁消费者待gameplay |

阶段2交接与限制：[operations-api.md](../../../../openspec/changes/add-danmaku-program-operations/operations-api.md)。数值fixture目前仅本机Windows/JDK21实跑，不等于已完成多平台验证。

快速配方：[两代分裂、移动母弹、龙卷风、旋转法阵](../../../../openspec/changes/refactor-danmaku-program-runtime/pattern-recipes.md)。通俗术语与写作步骤：[author-guide.md](../../../../openspec/changes/refactor-danmaku-program-runtime/author-guide.md)。

真实反弹、连续追踪、力场、传送、倒流和亚tick发射是future方向，**不计为本次交付，也没有可用API**。

## 当前旧后端入口（legacy-only，不保证所有组合可用）

| 能力 | 查阅代码 | 注意 |
|---|---|---|
| 时间与发射 | `danmaku/track/Track.java`、`TrackRunner.java` | 服务端调度，不能当组级双端程序 |
| 几何 | `danmaku/track/Shape.java`、`Geometry.java` | 通用Params有历史复用；核对字段是否实际消费 |
| 曲射/悬停/速率/爆散/回收 | `danmaku/track/Behaviour.java`、`entity/AbstractDanmakuProjectile.java` | 有互斥和顺序覆盖，使用前读实际分支 |
| 编队数学 | `danmaku/motion/FormationFrame.java` | 纯函数部分可复用，不等于新逻辑组已经实现 |
| 段式 | `danmaku/motion/DanmakuLegMotion.java` | 旧段类型与字段限制不属于新程序契约 |
| 激光 | `danmaku/track/Projectile.java`、`entity/LaserDanmaku.java` | 旧LaserMotion与新统一姿态模型不同 |

上表路径均相对`src/main/java/com/bitsson/gensokyou/`。标记legacy-only只表示发现入口，不表示已对所有参数完成验证。
# 第五期低层基准补充（2026-10-17）

`ProgramBenchmarkLoad`/`ProgramBenchmarkHost`提供显式48/480/800/2000线性负载；`gs_program_benchmark`选legacy或program，后者接正式runtime/network/collision。两端共用展开输入、seed、周期、寿命和半径。代码已implemented，轨迹/2000弹跨周期准入回收的离线测试verified；真实客户端采样pending，不代表复杂场景或M2通过。执行入口`tools/program_benchmark_run.py`及第五期`acceptance-runbook.md`§6。无新增正式BOSS原语，无BossCards迁移。
