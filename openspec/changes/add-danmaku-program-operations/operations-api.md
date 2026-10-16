# Operations API / stage 2 handoff

代码根：`src/main/java/com/bitsson/gensokyou/danmaku/program/`。本阶段提供纯求值与可编码描述，服务端权威事务和游戏表现分别由server/gameplay接线。

## 数学与时钟

`math/Vec3`、`Frame`、`ScalarCurve`不依赖Minecraft注册表。长度为格，时间为tick，速度为格/tick，所有角度为rad。超越函数统一StrictMath；`Path.State.quantized()`在输出边界量化到2^-20格，不对中间运算逐步量化。负零向量规范为正零。黄金数值fixture见`ProgramOperationsTest.numericalPolicyHasPortableGoldenFixtures`；本机Windows/JDK21通过，尚未在其他操作系统/JDK实跑。

`Deterministic.Seed(root,lineage)`逐级child(稳定序号)，用途channel与索引独立取值；不保留推进式随机状态。UniformSphere均匀采样cos极角，PlaneCircle和Cone使用明确轴。

`Paths.Segments`构造时缓存每段起点与结束时间；段必须从零位移开始，1..64段，标签唯一，尾部CONTINUE/HOLD/END必填。段内方向为显式输入，不隐式把下一段方向旋到上一段切线。Arc使用稳定sinc积分，支持沿轴分量和零角速。SpeedCurve允许零和负速。Bezier是参数时间推进，非恒弧长；WaveOffset提供解析导数；Rebase固定边界世界位置，速度跳变必须声明。

`Transforms.Compose`列表按应用顺序执行，例如`[Scale,Breathe,Rotate,Translate]`；平移路径与局部变化分开。镜像保存视觉朝向策略。OrientAlongPath显式求Frame；静止时需要fallback。Stagger保留原始index，负age表示尚未出生，由调度者排除，不能压成0提前生成。

## 图案和成员

`Pattern`是开放接口：generate(seed)、countBound、radiusBound。Member保存稳定index、局部位置/方向、Style、tags、targetSlot。Style中的伤害和碰撞半径独立于局部缩放。ParallelGrid省略列后不重排index；Union按子图案及子序号形成固定输出。AxisNodes展开数为节点×方向数。SurfaceRays只消费已经解析的点，不查地形。Ring/DiscRing共用Ring的圆周点描述，Radial把同一方向集放在同一出生点。Shell是带共同方位缺口的纬度层，Cage是有门的柱层；迁移旧Shape具体参数映射仍由阶段6登记。

`MemberMotion.Attached`按组时钟与成员延迟求世界状态；Detached在生效时从解析世界位置继续，支持切线或显式新速度，保留Ownership。AimAtPoint从actualSource到已采样点计算，不使用未偏移的旧原点。

复审修订：Attached显式分离groupOrigin和成员timeOrigin，transformClock选择GROUP或MEMBER_AGE；parents保存由内到外的有限父变换闭包，各层有自己的时间原点。directionAt独立求方向，旋转/镜像处理静止激光朝向，不从速度推断。旧七参数构造器仅为同批出生的便捷形式。共享组补发必须使用完整构造器。

## 编码与扩展

1. `OperationLibrary.dataRegistry()`返回可扩展的白名单record注册表。使用稳定namespaced类型id注册自定义Path/Pattern record，然后freeze。
2. `OperationLibrary.pathType(codec)`与`patternType(codec)`安装到原有OperationRegistry，再freeze；编译器、ProgramSchedule与ProgramCodec保持原样。
3. PathParameters使用PathSpec描述；`evaluator()`安装一次后保留其结果，避免每帧重建Segments。简单路径直接`PathSpec.of(path)`。
4. `OperationDataCodec`按记录字段编码，浮点为hex字符串，集合有序、Set规范排序；拒绝未知类型、非规范字段、过深/过长/过多输入。只调用本地显式注册的record构造器，wire不能指定Java类名。
5. childProgramType将完整子树计数、代数和最后子代寿命纳入原操作封套。descriptionType用于其他描述，调用者必须提供准确的引用/通道contract；不能用零contract掩盖预算。Paths.Segments在任意Path字段中编码为PathSpec，解码时一次构造带缓存的Segments，ChildProgram/Translate/Rebase均可嵌套。终止型内路径不能藏在Segments中复活：内路径边界ended时构造即拒绝，终止应放在外层尾策略。

独立扩展示例见`OperationExtensionTest`，不修改核心或公共封套。真实网络恢复由阶段4/5复验。

## 运行期交接描述

`Actions`提供CreateGroup/ForEachTarget/PrepareEmission/Emit、Source、Binding、ChildProgram、CreateEmitter、EmitAlongPath、ReplaceMembers/SplitAt、SegmentEndTrigger、Stagger/PhaseOffset/SeedDirection、Decision和Lifecycle。通用Decision/Lifecycle枚举是有界描述入口，具体世界查询、作用域释放与事务算法尚未接入。Replace必须提供原子scope和onReject，netMemberIncrease只计算直接子代净占用，完整派生准入由server负责。

ChildProgram的actions是实际不可变子图，每项ChildAction含选择器、AtAge/AtPathSegmentEnd触发、整数周期/轮数、EMIT或REPLACE、绑定、拒绝/原子范围、炮口曲线与下一代ChildProgram。构造器遍历子树校验声明派生数和代数，不接受空子图凭空申报派生；计数保守按全部母成员，选择器不用于缩小上界。触发必须落在母程序寿命内。memberState按FIXED/TEMPLATE/SEEDED_SPHERE显式选择路径方向并应用局部变换；随机seed由完整父谱系传入。权威执行仍由阶段3承担。

AuthorityDescriptions提供具体CaptureTargets/CaptureFrame/SampleTarget/ResolveSurface/Proximity/Commit/Cancel/EndOnReturn策略，保存目标句柄、槽号、采样部位、up/上一R/回退轴、查询模式、安全距离、失败范围、归属清理和返回进度条件。后续server使用这些具体类型；旧Decision/Lifecycle只作类别标签，不能替代具体策略。

Source包含MemberPose/GroupPose与ExternalEntitySource(UUID)。外部实体仅worldSnapshot；源丢失停止未来发射。Pose含Frame、线速度与世界角速度。CreateEmitter逻辑炮口与Billboard视觉旋转分开；整数tick first/period/count。emittedState消费mock/权威Pose求子路径：快照出生后不再查询当前源，addSourceVelocity一次加入出生源切向速度。followSource需要当前源Pose；源失效时返回empty，由server根据Binding发布END或最后权威姿态脱离结果，不在纯函数里伪造最后姿态。

Presentation提供Window、VisibilityCycle、Flash、SoundCue、Fade、Billboard、Telegraph、Profile及LaserWindow。时间域显式传入Clocks，窗口半开；隐藏关闭伤害，Fade单独不关闭伤害。Flash不改碰撞半径。SoundCue只描述时间和去重键，不播放声音；LaserWindow只求预警/有害/关闭相位，不裁剪世界光束。

## 证据与边界

- ProgramOperationsTest：黄金数值、乱序seek、段边界、48拍共圆、25度转弯、复合花阵部分脱离、Bezier/Wave/Rebase、缺口相位、真实18/4发、全部图案边界、两代谱系随机、判伤显隐、发射源快照/跟随与编码。
- OperationExtensionTest：独立路径/图案注册、原封套往返和调度。
- OperationCompositionTest：实际48批不同出生共享组时钟与父变换、静止激光方向、分段路径嵌套编码、两代图真实7N展开/4N末代、移动母弹nxm描述、移动螺旋柱与旋转法阵描述。纯计划展开预设全部准入，不模拟服务端事务。
- wrapper build与OpenSpec严格验证通过。跨平台实际执行、真实分裂事务、游戏渲染/声音/碰撞、带宽和旧后端性能对照均不属于本阶段已验证证据。
