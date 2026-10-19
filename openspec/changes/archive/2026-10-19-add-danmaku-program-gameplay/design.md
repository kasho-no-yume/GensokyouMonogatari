## Context

依赖纯核、服务端和协议全部完成。该阶段是正式卡迁移前的唯一闭环门槛。

## Decisions

2026-10-18最终用户授权：1.5以已有基础诊断与集中压力报告完成验收。本期不再补统一旧后端CPU对照、跨场景统计和细粒度字节拆分；这些仅在实际瓶颈出现时按需开展，不阻塞进入阶段6。此为验收范围修订，不表示延期统计已实现或严格M2已通过。高延迟启动短闪保留为用户接受的限制。

本轮重新准备压力入口（此前独立服残留的ProgramStressHost实现已由用户主动撤回，不能沿用其class作为交付）。`ProgramStressLoad`复用`gs_program_benchmark start stress 800|2000 300 <player>`与正式runtime/网络/碰撞。每200tick出生640/1840颗旋转+呼吸球弹和8道旋转激光，首40颗球弹age60各派生4颗、子寿命80，实际目标峰值808/2008；父寿命200不缩短。每批最多40、单tick显式测试出生预算2048，属于压力fixture例外，不用于正式BOSS R3。先30秒暖机、180秒采集；正常目标authority接近20TPS、program host p95<50ms，整帧p95<33.3ms且无持续>100ms卡顿，网络不随轮数持续放大、恢复不持续累增、End归零；数值作为诊断目标，最终还须用户体验确认。低档失败先诊断，不直接升级高档。单机/独立服证据分别记录。

**用户授权的验收范围修订（当前有效）：** 代表预览及集中验收1–4已获用户确认，2.1–2.5收口为代表切片功能验收，2.6收口为已有性能观察/采集探针。原M2精确50%/110%、三次重复、54证据项和真实1/2/5人全矩阵转后续专项；不是宣称原标准已达成。迁移仍需tasks第3节一次集中高密度复杂压力验收：明确目标峰值/场景/时长，观察逻辑与整帧、网络及恢复有界、碰撞正确和结束清理，再收口2.7。该决定覆盖本文后续历史“完整M2否则不得迁移”措辞。Member/External源FX等未实现能力仍如实记限制，不因代表切片勾选宣称全支持。

基准fixture例外：ProgramBenchmarkLoad固定四档48/480/800/2000，按每批≤48模板拆分，周期与寿命均100tick、速度.08格/tick、碰撞/视觉半径.2，seed20261017。显式测试命令可运行210–600秒；旧后端直接SphereDanmaku，新后端正式ProgramSession/runtime/network/collision，两者共用展开与世界帧，不迁移BossCards。宿主显式4096成员/2048单tick出生/64计划的测试预算不代表正式R3内容规则；碰撞均保留，开阔地站定采样。固定参数属于可重复基准输入，不作平衡config。当前CPU范围仍不同，必须在报告中声明，不能将此probe当完整M2通过。

2026-10-17 20:33续接：ProgramClock.observe按已显示的local+partial边界比较estimate和权威样本，样本仅补该tick内已显示的相位；恢复不回拨local游标。误差上限、严重积压隐藏和grace期限不变。新增离线frame-before-packet/2tick jitter/500ms RTT投影回归，不能外推为实机闪烁根治。普通弹撞块现查询半径扫掠包络内的世界VoxelShape盒，并复用sphereAabb选择首次接触，替代下文历史中心线裁剪限制；激光仍保持原裁剪策略。曲线有限采样限制仍存在。

数据后端复用碰撞规则和视觉资源，HitContext替代必须有Projectile身份的调用。球弹扫掠、静止接触、曲线子段、激光裁剪/节流均保持服务端权威；只有一个后端判伤。billboard相机朝向与世界炮口独立。

### 游戏侧适配边界

碰撞审查补充：`ProgramCollisionSweep`独立承担有界曲线采样、完整半径包络与按沿路径距离选最近命中。递归最多8段，采样检查中点及两个四分点相对弦的误差，超过0.02格且耗尽分段预算则移除成员并发布`collision-curve-budget`，禁止以未达误差目标的长弦判伤。有限采样不能证明任意高频自定义路径的连续误差上界；新增路径仍须满足碰撞时间尺度约束，后续可扩解析区间界接口。实体候选列表顺序不决定中弹目标；整条扫掠包络最后统一扩碰撞半径，避免高速末端漏查。激光Beam投影一次复制记录表，不为每根光束重复复制完整image。普通弹方块碰撞仍为中心线裁剪，体积扫掠待补，不能宣称该部分完整。

第五期不把程序成员重新包装成 Minecraft `Entity`。服务端每 tick 从 `DanmakuWorldRuntime.Checkpoint` 和当前解析状态生成有限的 `ProgramCollisionSample`，其中至少包含 encounter owner UUID、稳定 `MemberId`、整数 tick 区间、起止 `Path.State`、碰撞半径、伤害倍率、碰撞/可见性门以及 projectile/laser profile。该 sample 是碰撞适配层的输入，不是可同步的实体状态。

碰撞分为三个纯判据入口：球弹和弯曲路径使用误差受限的线段子段扫掠，静止成员使用碰撞体相交，激光使用服务端裁剪后的射线/胶囊几何并按声明的命中节流执行。几何判据只返回按距离排序的目标和命中位置，不直接修改世界、不调用 `hurt`，也不消费随机数。曲线单 tick 的子段数、最大误差和超限策略由 profile/运行时 limits 共同约束；长弦不得代替曲线扫掠。

伤害适配层接收 `ProgramHitContext`，再调用既有 `ModDamageTypes.DANMAKU` 伤害入口。程序后端使用专门的无直接弹体 `DamageSource` 构造：active encounter 的 owner UUID 必须解析为仍存活且已加载的 Minecraft `Entity`，只作为伤害归属的 attacker；不能用成员 ID 或临时弹实体冒充 source。owner 消失时 encounter 已按运行时生命周期清理，适配层也必须 fail closed。无直接弹体意味着依赖 `getDirectEntity()` 的旧“玩家武器弹幕灵汲”分支不会误认 BOSS 程序弹，程序命中仍保留 `LivingIncomingDamageEvent`、`mu_power`、`DANMAKU_PROTECT`、擦弹、护壁、Touhou 战斗事件和伤害统计语义。HitContext 只补充 program/group/batch/member、lane、target slot 和命中 tick 等诊断信息，不另造一套伤害公式。

每个 `(encounterEpoch, MemberId, target UUID, damageWindow)` 在服务端有明确的命中去重/节流状态。球弹的单次命中、静止接触和激光的周期命中分别声明策略；客户端投影、渲染、预警和影子后端都不能触发伤害。一个 encounter 只能安装一个权威判伤后端，旧 EntityBackend 与 `PROGRAM_DATA` 不能同时对同一攻击结算。

### 渲染与预警边界

客户端不为程序成员创建 Minecraft `Entity`。`ProgramReplica` 提供解析位置、方向、生命周期、`Pattern.Style` 和 `Presentation` 状态的批量只读视图；gameplay renderer 将视图按 projectile/visual profile 分桶，复用现有球弹/灵符/激光的材质、颜色、发光、法阵和端盖几何。现有 entity renderer 中依赖实体年龄、实体位置或实体方块裁剪的代码只能复用其几何原语，不能直接作为程序后端的状态源。

预警必须绘制 `EmissionPlan` 的源点、方向和轮廓；预警不创建可伤害成员。激光预警、激活、收束和声音去重都读取同一 plan 与同一时钟锚点。Billboard 的相机朝向只改客户端面片姿态，炮口方向、激光轴和碰撞方向来自程序世界状态。视觉缩放、辉光、闪亮和淡出不得改变 `collisionRadius` 或 `damaging` 门。

### 第一条纵向切片

实现顺序先以一个数据成员完成“runtime checkpoint → 服务端 sample → `ProgramHitContext` → 既有伤害入口”和“checkpoint → replica → 批量球弹/激光视图 → 客户端 renderer”两条链，再接入预览命令。预览必须安装正式 `ProgramSession`、`ProgramNetworkHost` 和同一 runtime；旧 `DanmakuPreview` 的 ArmorStand/TrackRunner 路径只能作为 legacy A/B 对照，不能被称为程序预览。

预览用正式runtime。完成蓝1/4/6、花海、尾珠及四个用户案例，但仅为预览程序，不提前替换正式BOSS卡。需要完整同等负载A/B和1/2/5人延迟、重追踪、取消、回收结果，不能以纯函数测试替代实机。

### 预览宿主首切片

代表卡预览使用manifest单轮240tick：blue1三轮固定最多5槽四拍；blue4跨球弹/激光共享帧与真实断口、激活前局部走廊检查；blue6两侧共享帧的直线/局部U圆弧/直线。数值为content-mapping的固定验收输入，不作为默认平衡config。新增OffsetGroup通用控制操作和Corridor.local坐标声明；扩展注册保持可编码恢复。预览定义预算显式256KiB，宿主预算1024成员/256单tick出生/128组，协议编码遵守network snapshot字节上限，分块仍遵守messageBytes；正式M2必须计入较大定义的初始流量成本。完整定义校验由冻结execution弱引用缓存，发生恢复/新定义时仍先校验。

实机首切片反馈后，`Pattern.Style`增加显式`visualRadius`；原四参数构造保留并默认等于`collisionRadius`。视觉大小与服务端碰撞大小分别声明，选择五角星不自动套用旧实体的hitboxScale。`ProgramVisuals`解析现有球弹/STAR_PRISM档案和颜色，`DanmakuMeshes`抽取实体无关的五角星柱几何，旧renderer保持原尺寸调用，新renderer按visualRadius绘制。程序成员age用于档案摆动。当前只接本体几何与颜色，完整glow/core/colorMode等档案表现仍待完善。

普通程序弹使用精确球体扫掠/AABB相交：按轨迹跨越目标盒各面的参数分段，在每段求距离平方的二次函数首次进入点，避免直接inflate目标箱在角落产生额外命中。激光仍保留原线段/扩箱规则。本测试切片按用户实机反馈显式设置碰撞半径0.1、视觉半径0.2；这是preview fixture手感调整，不计作A/B等负载证据，正式卡/性能用固定manifest。

`ProgramPreviewHost` 显式装配每维度一个 session，通过 `/gs_program_preview start blue1_slice|stop|stats` 操作。单个静止 ArmorStand 仅作为 encounter owner 与伤害归属；没有 TrackRunner 或程序成员实体。发起者死亡、退出或换维度，以及 owner 失效、维度卸载和重复启动均回收 session，并在网络宿主移除前发布结束事实。客户端只安装同名 operation 定义校验 registry，不安装 authority handlers。

`blue1_slice` 是基础设施测试 fixture，不是完整蓝1符卡：固定五拍，20 tick 预警，3×7 网格去除一列，每拍18发、速度0.2、寿命100 tick；捕获最近生存目标，偏移源点瞄准眼位，以2格净距提交。fixture 的数值作为有界测试输入保存在 `ProgramPreviewPrograms`，属于 config 数值规则的测试例外；伤害仍读取 owner 属性，无该属性的预览 owner 回退到现有 `DANMAKU_BASE_DAMAGE` config。完整蓝1/4/6及A/B使用各自正式等价负载，不能拿此fixture充当验收。

`ProgramPreviewBounds` 仅适用于该线性 snapshot fixture，以图案半径、全寿命推进距离和碰撞半径生成保守世界包络，成员暂共用组包络。它不是任意路径/激光/跟随源的通用 bounds 实现。扩展预览案例时必须同时扩展对应的保守包络生产者。

## Validation / Handoff

最终用户反馈（2026-10-17）：petal_release和roaming_tower表现实机通过；500ms独立服高延迟下整批闪烁仍然严重。以下frozenGrace属于已实施但未根治的缓解，自动化通过不代表高延迟稳定性通过；不要求用户重复验闪烁。最新待办与证据以next-session-handoff.md和acceptance.md为准，尚无完整M2结论，不能归档/迁移。本次交接只改文档。

后续用户已在单人和500ms独立服确认上一批五项表现/性能。闪烁日志证据为display-error误差约2.04–2.88tick后renderable=false，并非最初推测的stale。ProgramClock新增独立冻结呈现grace：仅display-error且|最新样本−显示|≤4，最多1本地tick保留原verified时间，不外推失信映射；原运动holdover仍≤2tick误差/≤2本地tick。严重积压、同tick后续样本超4立即撤销grace；超时隐藏。用户不要求复测，不宣称全网络闪烁根治。

petal_release为compositeMotion的整瓣变体：Rose5×9，Union core占index0；sample0..44的瓣归属floorMod((sample+4)/9,5)，选择瓣0/2/4共27成员，通过现有IndexRange/RadialRelease同tick执行，core未改变故共同中心一致；预留全寿命释放包络。roaming_tower复用参数化HelixColumn(5,2,3,16)，Bezier平移；稳定顶层索引15/31/47每20tick发8弹Radial共8轮，24批192子代，snapshot独立直线，最后子代生命周期后End。静态descendantBound保守3072（全部48模板上界），没有降低预算门槛。preview固定数值例外，完整测试与真实观察分别记录。

2026-10-17续接：EmissionPlan增加可选GroupMotion闭包，Fire的FOLLOW_SOURCE在有组运动时显式携带它；普通Emitter的FOLLOW_SOURCE仍由已有source引用解析，不隐式绑输出组。预警成员局部age固定0，父组按绝对tick运动；出生后成员age前进且共享组锚保持，服务端准入和replica使用同一闭包。Batch投影、mask patch、空间过滤保留该闭包，bounds预留全寿命组平移/旋转包络。新增dynamic_warning边界/晚加入测试。

CreateEmitter Billboard现由ReplicaState.Emitter源组闭包产生独立FX投影，ProgramDanmakuRenderer按当前camera画阵盘，世界炮口只来自源姿态和nozzle。支持GroupPose FX，MemberPose/ExternalEntitySource FX暂不画，不能用输出组冒充源。high_frequency_circle双炮口各80次、每tick3弹、寿命100，正式snapshot子弹共480；20次低预算拒绝/清场测试，预发射FX包络独立于Batch。Emitter源闭包进入兴趣依赖。数值为preview fixture例外。

激光following方向取当前source pose+nozzle+成员transform；源丢失DETACH时WorldDetached保存明确轴向，后续server/replica不取平移切线。Rebase仍按显式新运动方向。ProgramSourceIntegrationTest覆盖斜交速度/轴向、wire及源丢失保持。

48→480首次逻辑字节7546→54747（7.255倍）未过硬门槛；ProgramWire对完整模板与已注册pattern+seed生成结果精确相等时发送生成描述，子集/例外仍展开。接收端仅做纯Pattern生成，不运行客户端authority或查询世界；摘要始终对展开语义编码，避免缓存状态影响digest。优化后2415→2529（1.047倍），ProgramScalingGateTest保留≤2倍断言和JSON报告。分块测试用显式1024消息预算，不靠旧展开冗余制造分块。此结果不代表完整M2或所有空间子集缩放通过，双方需同步更新协议实现。

72组延迟组合见ProgramLatencyMatrixTest及CSV：双向恢复请求/响应队列、TCP顺序、独立副本、停顿与低TPS；真实客户端不由这些副本替代。tools/program_network_matrix.py复用已有proxy准备真实矩阵与50ms计量；proxy改为有界64块队列按到达时间延迟，防串行每块叠加。已有ClientFrameProbe整帧读数和baseline_window采样窗口可以复用，NetProfile新增program_tickcost行；program_ab_report.py自动汇总原始样本p95和连接逻辑/线上字节，明确CPU范围不同和等负载证据欠缺，不自动批准M2。

旋转激光方向审查：snapshot/attached激光的方向取成员模板轴，经成员transform.direction及source/group帧变换；节点平移速度不能充当光束轴。普通弹保持速度方向；following/detached laser尚需专门方向契约覆盖，暂不扩大支持声明。预警取EmissionPlan在age0的变换位置与轴，保证提交边界一致。rotating_laser fixture40tick固定出生姿态预警，激活后节点公转+自身旋转，24格不穿墙/10tick伤害间隔；不是父组预警全程运动探针。moving_tornado为48局部helix成员+Breathe/Rotate/Arc Translate的有界基础fixture，均为测试数值例外。

motion_lab独立放在ProgramMotionPreviewPrograms，避免参考卡目录继续增长；三个共享帧offset组展示Wave、Bezier及Mirror，六次10tick错拍发射，每批明确birth/member-age与20tick提前量。FPS测试按30/60/144采样相同解析时间，无逐帧积分。fixture数值为测试例外。host时延使用ProgramTimingWindow固定2048条最近样本，读取诊断时排序并给nearest-rank p50/p95/max，tick只写环形数组；统计为host热路径，不冒充整个server tick或GPU frame-time。

客户端同样记录最近2048条receive（解包/定义校验/安装）、projection（真实renderViews求值）、geometryAndSubmit（CPU几何输出与buffer提交）的p50/p95/max；每100tick诊断输出，退出清空。样本为最近窗口而非每张卡独立统计，不测GPU完成时刻，不等同整帧frame-time；后续M2须使用同负载独立对照与整帧测量。

新增fixture伤害窗口遗漏修正：split三代与composite明确配置MEMBER_AGE[0,lifetime)，空damageWindows仍表示无害，不改变通用语义。partial_release复用compositeMotion构造，仅在card120对petals∩IndexModulo(2,0)做RadialRelease(.15)，其余成员保持原解析路径。此选择是偶数成员而非偶数整瓣，完整整瓣探针仍待补。出生即预留原轨迹包络+.15*lifetime的保守脱离范围，避免释放时订阅扩张。以上数值属于预览fixture例外。

新增集中验收fixture：`split_emit`/`split_replace`共享两代2×2子图，只改ChildActionKind，N=6、每代35tick、20tick预警，分别7N/4N；`composite_motion`为core+45petals，Breathe→Rotate→Arc Translate，中心运动最后相加，不被呼吸缩放。数值均为预览fixture的config例外，不是正式平衡；不宣称完整额外组合探针或M2通过。验收入口见acceptance.md。

稀疏观测拟合与显示失步分开判定：当 estimator 仍 TRACKING，但拟合映射相对显示超过2tick、最新权威采样与显示仍在2tick内时，只重置估计映射，保留显示边界，不进入隐藏/恢复请求。这处理稀疏heartbeat拟合噪声而非扩大显示误差预算。短暂holdover可显示期间不请求全量事实恢复；同订阅checkpoint保留有界历史，新订阅提交清空旧历史。新增1/2/5独立离线观看者退出/重入/恢复矩阵，不替代实机网络延迟矩阵。

2026-10-17 时钟恢复审查：小误差仍按每本地 tick 最多 0.05 校正；映射失信或显示偏差超过 2 tick 时进入显式恢复。仅当最新服务端观测与显示时间差不超过 2 tick 时，允许旧速率最多维持 2 个本地 tick（100ms @20TPS）的连续显示；同 tick 后续积压观测超过误差界立即撤销维持。恢复在后续本地 tick 的权威观测上完成，小误差且未超时保留显示边界，严重失步重新锚定；不得把几十 tick 差额继续当小误差平滑。该常量属于协议显示恢复容差，不是游戏平衡数值。客户端记录失信原因、最近 estimator outcome、触发误差、维持状态、失信与恢复计数。此策略需真实客户端复验闪烁和画面/判伤一致性，尚不代表延迟矩阵通过。

花海与尾珠预览采用显式两轮有界时间线（花海240tick轮、尾珠200tick轮），随后等待最后子代寿命再结束 encounter，暂不替换正式卡。`flower_sea`每轮16×46发，随机出射/花平面参数由稳定种子固化进定义，age100经`RadialRelease`以同一边界花心释放花瓣，`RebaseTarget(slot=-1)`在当时最多5个有效战斗目标中选一次并发布目标采样与解析世界路径。该操作无目标时取消所属组。`tail_pearls`每20tick六母弹、每母age4..96每4tick一尾珠，尾珠Hold40后稳定种子球面漂移，寿命400。预览数值是content-mapping固定验收fixture的config例外；显式4096存活成员上限用于容纳跨轮，不能当成性能通过。`ProgramPreviewBounds`纳入成员变换和Rebased路径全剩余寿命范围；仅覆盖这些snapshot预览，不声明通用following-source包络。

客户端共享时钟的估计映射与已显示时间分离：收包边界先按旧映射结算；可信期间按估计速率推进，小偏移每本地tick最多校正0.05。失信后按上述有界维持/明确恢复规则执行，禁止大误差长期追赶。程序激光复用旧renderer的六面几何，层参数、端盖、法阵与3tick包络对齐既有激光，不为程序成员创建实体；长度来自服务端Beam事实。

执行总migration全部M2门槛：确定性无逐弹网络实体、逻辑流量降低≥50%、CPU与frame-time p95≤基线110%、恢复正确和资源回收。全部案例后再作阶段结论。未通过留在本change，不进入正式迁移。
