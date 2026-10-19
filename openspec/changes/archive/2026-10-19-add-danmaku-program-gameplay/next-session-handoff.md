# 第五期下一会话交接

## 本轮实时更新（优先于以下历史）

最终决定：用户明确同意将1.5按已有基础诊断与压力报告验收，统一旧后端CPU对照、跨场景报告与更细字节拆分转遇到实际瓶颈时按需开展。tasks现16/16，无需追加测试；覆盖下文15/16及“1.5仍阻塞”的历史。允许后续进入阶段6，但本会话没有执行正式BossCards迁移或归档。高延迟启动短闪仍为用户接受的限制，真实多人/1050容量/严格M2不冒充已测。25568延迟代理随后收到harness取消通知（server restarted），不再声称该代理仍运行；后续需要先核实进程。

00:47收口：自然结束验证最终`benchmark idle`，server log记录第二次自然运行`START stress 2000`后`STOP ... runtime=groups0,members0,plans0,emitters0,frames0,locks0,commands0`；3.3已勾。2.7已完成：build003429完整通过、38项clock/network/latency与burst回归通过、strict通过；压力结果新增`pressure-results.md`。当前tasks 15/16，仅剩1.5（完整统一CPU/逻辑线上字节跨场景报告）。真实2/5人等继续保留后续限制；尚未归档/未执行正式BossCards迁移，不能把15/16称整change全部完成。

00:45最终用户口径：用户明确看到高延迟启动约1秒处明显闪烁，但认为高延迟下约47ms可接受，要求停止追求完美修正、优先主任务。短时复验日志显示后续3次queued-samples均renderable=true，恢复0、协议错误0、停止runtime zero；首次display-error约7.76tick造成短隐藏作为已知限制记录。自然结束验证已启动：15秒内观测峰值2008→1848，后台等待210秒后查idle。若idle确认，tasks 3.2/3.3/3.4可收口；不宣称高延迟完全无闪烁、不宣称真实多人/M2。

00:35：用户已退出并授权继续最终短时复验，同时询问带宽/1050容量。已解释高档峰值2000球+8激光、1848稳态低谷；高延迟整连接约11.3KB/s，200KB/s与200kbps须区分，不能线性推最大弹量，1050无实测容量承诺。最终build003429后台运行，wrapper60秒返回；服务端已RCON正常stop。等build哨兵成功后wrapper重启双方，用户连接25568，做约一分钟高延迟短时/自然结束验收；勿重复整套180秒。最新queued holdover尚未实机加载。用户授权重启已充分，无需再问重启许可。

00:30高延迟更新：high-latency-r1完成180.599s，3612tick/20.0001TPS，1848–2008成员；host p95 39.9493ms/max157.4226ms，整帧10469样本p95 18.4703/max37.6647ms，无>100ms帧；恢复0→0，三类协议错误0，连接下行2,033,142bytes，program payload逻辑5,640,585bytes，停止idle。用户反馈“我其实没看见，有可能是我看漏了”，记录为未察觉闪烁非明确消除证明。日志本轮仍3次queued-samples隐藏80–250ms，误差仅约±1tick，另1次display-error。已修queued-samples在原±2tick/最多2local tick内允许holdover，同tick后续严重积压立即撤销；不增容差。新增burst near display/过期/严重撤销测试，002944定向通过。此最新修正尚未装入实机，勿将high-latency-r1当它的验证。剩余收口：最终build、修复版自然结束/重连/单机压力证据、用户最终反馈、报告/tasks；暂不重复180秒完整高档，优先集中短生命周期验收。

修复版高档high-r2-fixed完整180.648s通过采集：3612 host tick、19.9947TPS，1848–2008成员；host p95 41.1486ms/max150.1248ms（非整server tick），整帧10478样本p95 18.2402ms/max30.0393ms，无>100ms帧；恢复0→0，墓碑/边界/批次复活错误均0；程序payload逻辑5,904,238bytes，整连接下行1,772,624bytes（不可混作同口径）；STOP runtime七项零/idle。用户提问窗确认“表现正常，可进入高延迟”。启动期仍有两次display-error恢复，不能称clock全无失信。下一步已启动独立计量延迟代理25568→25565，双向one-way250±30ms，配置RTT约500ms非测量RTT，profile build/reports/stress-wire-latency.jsonl；请用户切25568后agent运行count2000新root high-latency-r1，参数--latency-ms250 --jitter-ms30 --proxy-log该profile。单机、自然结束/重连与最终收口仍欠，任务仍11/16，不冒充全通过。

00:17最新：完整build001220通过2m30s（1053测试）；随后补waiting队列按sequence淘汰旧Decision，长循环回归加到48轮×400=19200成员（超过16384成员预算），38项clock/network/latency定向测试001621通过。新双方已wrapper启动runServerTest001648/runClientProfile001657，等启动后让用户连接25567。尚未开始修复版实机采集。旧低档摘要补出1,818,038连接下行字节，clientProtocolErrors墓碑4/边界228，因此仅性能与用户视觉证据、不算稳定通过。用户已授权退出和重启，不再重复问重启许可。

00:12后续：用户已正常退出客户端并授权继续重启；agent已RCON正常停服。完整build000803发现预警alive=0被误当死批次（ProgramMotionLabTest失败），现deadBatches只接受`!warning`全零批次。已串行重跑完整build `20261018-001220-build.log`，后台待结果。进程重启必须等该结果通过；不要把000803当通过。高延迟尚待复验，不勾3.2–3.4。

00:08更新：低档虽获用户画面正常确认，采样末尾client.log发现`member tombstone budget`随后大量`decision boundary mismatch`，所以不标协议稳定性通过。高档high-r1约127秒采样后agent手动停止保留FAILED和日志，不作为通过；不得继续升级或复用它当完整180秒。根因之一是全死批次仍保留零alive mask，逐成员墓碑却累积至members预算。ProgramReplica现将全死批次压缩为deadBatches、保留部分活批成员墓碑，移除批次用ordinal watermark阻复活；新增24轮×400成员（累计9600）真实runtime→wire→replica回归。单调clock过滤及clock不触发fact恢复一并38项通过`20261018-000733-test.log`。完整build已启动`20261018-000803-build.log`，wrapper60秒返回，后台继续，先读哨兵不要另起构建。尚未重启游戏加载本轮修复，高延迟闪烁仍未实机修复确认。下一步等build→正常退出客户端/停服→wrapper重启双方，再集中跑长循环/高延迟；需要用户操作继续提问窗。

用户确认此前ProgramStressHost/Programs和旧stress脚本已主动撤回，要求重新准备；现复用ProgramBenchmarkHost添加stress模式，入口`gs_program_benchmark start stress 800|2000 300 Dev`。ProgramStressLoad每200tick出生640/1840父弹+8旋转激光，age60首40父弹各派生4子弹（寿命80），目标峰值808/2008，保留碰撞。脚本`tools/program_stress_run.py`采30秒暖机+180秒并保存stats/ledger/frame/proxy；report脚本从原始恢复计数差分，未知不填0。旧stress_mixed-r1的summary恢复0错误，原stats171/最终173，仅留失败诊断。

低档本轮实测`build/reports/stress-v2/low-r1/summary.json`：180.534秒，3611 program host样本，20.0018TPS，648–808成员，host p95 19.188ms/max76.6604ms，整帧10464样本p95 18.3391ms/max41.5612ms，无>100ms帧；恢复2→3（+1），停止idle。用户提问窗确认“低档正常，继续高档”。高档已启动，结果待收；不要重启运行中的游戏或并行构建干扰采样。3.1勾选，当前11/16；单机、延迟、自然结束/重连及最终报告仍待收口。

用户补充旧高延迟大批量整批消失再出现严重，要求找原因。确认ProgramReplica.renderTime把时钟不可显示升级为全量事实恢复，增加checkpoint负载；已改为时钟不触发全量恢复（缺包/digest失败仍恢复），ProgramLatencyMatrixTest新增严重积压隐藏→下一独立时钟样本恢复且无checkpoint请求，20261017-235359-test.log通过。该修正尚未装入当前游戏，不能称实机已修。ProgramClock的queued-samples、display-error仍会隐藏，当前零延迟启动也有这两种transition；继续查成批到包/时钟样本选择，不抬误差阈值掩盖严重积压。新增共享owner旧样本回归尚未跑（等待高档采样结束）。

完整build `20261017-234444-build.log`通过2m30s、strict通过（在上述时钟修改之前）。当前独立服/客户端由wrapper runServerTest/runClientProfile启动，25567零延迟计量代理保留、25566旧延迟代理保留。不要杀daemon/清日志。当前新的运行时修改需采样后验证构建并正常重启双方再验。

## 当前接手入口：用户已授权调整验收范围

用户明确要求停止低收益反复A/B跑测，确认代表内容已测，并授权勾选2.1–2.6，新增第3节集中压力测试。现tasks已修订：2.1–2.5按代表功能/表现及现有自动化证据完成；2.6改为性能观察和采集探针收口。**不是原M2精确标准通过，也不是所有历史多人/FX边界都测过。** 2.1–2.6现全部勾选，1.5和2.7仍未勾，第3节3.1–3.4待完成；总计10/16。

精确50%逻辑流量/110%CPU整帧、54项证据、三次重复和真实1/2/5人全矩阵转后续专项，不再阻塞本期。剩余迁移门槛是一次玩家体验导向的高密度复杂压力验收（tasks§3）：先由agent准备真实高密度+复杂组合，明确目标峰值和时长，再统一让用户观察；记录逻辑/整帧/网络/恢复，避免明显持续卡顿、严重闪烁/幽灵、残留和崩溃。通过后收口2.7并进入阶段6。未实现Member/External源FX等仍列限制，不能扩大为已支持。

**下一步不再继续48弹三轮或纠结旧A/B计时范围来阻塞进度。** 不重跑已过验收1–4，不要求用户搭真实5人环境。先准备一次高密度复杂场景和尽量自动的采集；确需用户操作用提问窗集中通知。压力验收未过之前不开始正式BossCards迁移。以下所有5/12、必须完整M2/全矩阵才可勾2.x的历史记录均被本段覆盖。


> 最新22:17：修正版真实48弹首对完成，报告build/reports/bench-openair/compare-48-r1.json。legacy180.005s/3600tick、program180.043s/3601tick全为48；frame p95=18.606600/18.849501ms（1.01305倍），connectionLogicalBytes=1218824/751801、wire=1368110/892748（.65254倍）。CPU p95=.1903/1.1749ms，但旧仅projectile tick、新含advance+collision+publish，不能用6.1739倍判定等范围CPU门槛。双方共同输入/计数probe通过，不代表全轨迹/碰撞/渲染或M2等价。程序STOP runtime七项全零、RCON benchmark idle；两后端均采集成功。原站位预检template23阻挡、附近残留ArmorStand未删除；用户授权后临时上移32格，位置[-66.70823,132.67921,64.95408]通过预检，Dev镜头恢复原值。当前不再要求站定；服务端/客户端/计量代理仍保留。下一步先统一CPU计时范围和弹幕专属字节分类，再决定复测；不要让用户直接扩54项或将M2标过。

> 22:00首对报告已出：build/reports/bench-program-retry/compare-48-r1.json。program窗口180.048s，3601CPU/10442帧，STOP资源零；但实际members全程46，legacy换批36tick为96其余48，负载不等，comparisonReady=false。program少两颗具体碰撞原因未有删除事件证据，不断言根因。新增启动前全寿命球体走廊方块/活体检查、旧实体cohort100tick显式退役、status live字段；脚本暖机每5s核实live、报告loadCounts/loadMatchesRequested/equalLoadVerified。compileJava220049通过，待最终构建；需要新服务端重新跑一对，不沿用旧legacy做正式gate。用户此时无需保持站位；不要未经通知重启/重跑。

> program首轮失败根因已定位：LevelTick.Pre读取的是上一已完成Post边界的gameTime，向该tick排PREPARE触发cannot schedule into an executed phase，专用服21:49:33崩溃。ProgramBenchmarkHost改以nextBoundary(gameTime)=gameTime+1调度两后端，ProgramBenchmarkLoadTest按真实Pre相位输入回归通过（20261017-215037-test.log）。脚本暖机每5秒确认负载活跃、清场异常单独记录不覆盖原始故障。失败program目录保留，不记为CPU/M2证据；legacy数据保留，需重启服务仅重试program。

> 首次基准实测进行中：legacy48 r1完成180.044秒、3601 CPU样本/10442帧样本。采样后因program_benchmark_run局部command列表覆盖RCON函数而报TypeError，现已改名sampling_command并补finally关闭连接；agent确认负载仍active后停止，保留原始ledger/frame、复制proxy并生成summary/status-after，无需重跑legacy。数据在build/reports/bench-first/program_48-r1-legacy。program48 r1随后启动，尚待完成通知与对照分析。不能把该首探针当M2通过。

> 最新21:34：用户确认验收清单§1–4已跑过无问题，无需重复；本轮补真正共同低层基准入口而非只写manifest。ProgramBenchmarkLoad/Host、ProgramBenchmarkCommands、tools/program_benchmark_run.py已实现，四档48/480/800/2000、自动30s暖机+180s采样。具体首次真实跑测见acceptance-runbook §6，只跑48弹legacy/program各一次；需用户提供在线客户端，脚本经RCON启动并采集，尚未实际执行，不称端到端实测通过。完整test212654通过2m27s、build213414通过、Python4测试通过。复杂fixture/统一CPU范围/弹幕专属逻辑字节仍欠，M2和大任务保持未完成。

> 2026-10-17 20:33续接更新：本轮已继续代码，以下“本轮仅文档”属于上一会话。ProgramClock.observe原先将整数localTick样本/estimate与已渲染到tick+partial的last比较，最多额外近1tick伪误差；现统一在显示边界比较，恢复保留local游标，未放宽误差/期限。新增ProgramClockTest frame-before-packet+2tick jitter和ProgramLatencyMatrixTest 500ms RTT实际renderTime/renderViews/无恢复请求回归通过。未证明所有实机闪烁已解决；日志reason=none的新时钟行与预览结束相邻，不作为生命周期缺陷的确定证据。普通弹撞块已补世界VoxelShape盒的球体半径扫掠与墙边/角落测试，下文中心线待办由本更新覆盖。完整build含全测试通过build/agent-logs/20261017-203038-build.log（2m20s，wrapper超时后同一后台任务成功，未重启构建）。真实多人、等负载fixture/M2工具及FX/声音边界仍欠，tasks保持5/12。不要求用户重复验已通过预览。

更新时间：2026-10-17。最后用户指令：如实记录测试结果和tasks状态，准备交接，新会话继续剩余事项。本轮仅整理文档，不再修改代码。

## 当前结论与最高优先级

继续 `add-danmaku-program-gameplay`，父change `refactor-danmaku-program-runtime`仅作总设计，不整包apply。全部实现仍在工作区，未提交；不回滚他人改动、不清理日志、不进入正式BossCards迁移。

**用户最新反馈：petal_release、roaming_tower两项均已测试完成，表现没问题；高延迟下整批闪烁仍然严重。** 上一轮1tick frozen grace没有解决问题，不能把自动化通过或之前“无需复测”写成闪烁通过/轻微可忽略。500ms下渲染和逻辑存在偏差被用户接受，不等于接受严重闪烁为正确实现。

本会话按用户要求停止实现并交接。下一会话允许审查/重构已有实现，优先调查高延迟显示/恢复链路；同时继续剩余自动化与M2工具。用户已明确不要求反复实测闪烁，不要每修一点就停工；能自主完成的持续推进，新实机需求集中交付。

## 已确认的实机证据（不要重复要求）

- 花海、尾珠：性能与表现通过，早前尾珠漂浮闪烁曾获修复确认；该历史确认不能覆盖现在独立服高延迟闪烁。
- split_emit、split_replace、composite_motion：视觉通过；fixture补全damageWindows后碰撞伤害也获确认。
- partial_release、motion_lab、moving_tornado、rotating_laser：用户确认全部通过。
- dynamic_warning、high_frequency_circle、sphere_split、moving_carriers、参数化moving_tornado：用户在单人存档与500ms延迟独立服务器测试，表现和性能符合预期。
- petal_release、roaming_tower：本轮用户确认表现通过，仍指出高延迟闪烁严重。测试环境继承上述上下文；未获得独立2/5玩家、双视角、精确RTT/抖动测量等证据，不自行补全。

任务总状态仍为5/12：1.1–1.4及2.2勾选；1.5、2.1、2.3–2.7保留未完成。tasks中更新了2.3–2.5的具体已过/欠缺项。大任务同时含网络/外部源/多视角等内容，不能仅凭两个新预览表现通过整项勾选。1.1–1.4为历史接线进度，不承诺已无所有边界缺陷。OpenSpec artifacts的isComplete不代表实现全部完成。

## 本轮之前新增实现地图

Java根目录 `src/main/java/com/bitsson/gensokyou/`。

### 计划、预警、激光方向

- `danmaku/program/server/EmissionPlan`可选GroupMotion闭包，`ServerControlOperations.Fire`的FOLLOW_SOURCE显式携带本组运动；预警局部age0，父组用绝对tick，出生后继续同一组锚。
- `network/ReplicaState.Batch`、ProgramWire编码、mask patch、ProgramInterest空间过滤保留motion；ProgramPreviewBounds预留全寿命包络。
- `DanmakuWorldRuntime.direction`、`ReplicaState.direction`按source pose+nozzle+成员transform求following激光轴；WorldDetached保存源丢失边界轴，避免平移切线替代。已有ProgramSourceIntegrationTest离线覆盖，不能外推任意源/重置场景。

### 正式预览

- `gameplay/ProgramPreviewPrograms`：blue1/4/6、花海/尾珠、两代split、composite/partial/petal release。
- `gameplay/ProgramMotionPreviewPrograms`：motion_lab、rotating_laser/dynamic_warning、high_frequency_circle、moving_carriers、moving_tornado/roaming_tower。
- 命令 `command/ProgramPreviewCommands`，宿主ProgramPreviewHost，bounds为ProgramPreviewBounds；统一 `/gs_program_preview start <id>|stop|stats`。
- sphere_split：SphereSurface初代6颗Outward、SpeedCurve减速、具名段尾35tick替换，两次得到12/24；累计42。原split_emit/replace为独立基础fixture。
- moving_carriers：3母弹，age20/40/60/80各发8散射，总96子代；母寿命81覆盖最后发射，子代snapshot独立。
- high_frequency_circle：2个正式CreateEmitter，first20/period1/count80，每炮口每tick3弹，共480，子寿命100；Billboard由ReplicaState.Emitter独立FX投影，不是碰撞成员，发射前有独立包络。仅GroupPose源FX有接线，MemberPose/ExternalEntitySource FX暂不绘制。ProgramInterest保留Emitter.sourceGroup闭包。
- moving_tornado：HelixColumn(height5,turns2,arms3,samples16)、Breathe/Rotate/Arc整体推进，48成员。
- roaming_tower：同柱改Bezier整体移动，顶点index15/31/47从age20每20tick发8弹Radial共8轮，24批192子代；最后子代寿命结束后End。静态descendantBound3072是保守模板上界，不是实际192。
- petal_release：core index0、Rose5×9占index1..45，瓣号floorMod((sample+4)/9,5)，选择0/2/4瓣共27成员，card120通过现有IndexRange/RadialRelease同tick释放；其余两瓣/花心保持。旧partial_release仍是偶数成员，不要混淆。

### 网络压缩与测量

- 首次48→480初始逻辑字节7546→54747，7.255倍，失败；优化后2415→2529，1.047倍，通过≤2倍硬门槛。`ProgramScalingGateTest`保留断言，输出`build/reports/program-scaling-gate.json`。
- ProgramWire对完整templates与注册pattern+seed生成结果精确相等时发生成描述，空间子集/例外仍展开；接收只做纯Pattern生成，不执行authority。digest用展开语义，不依赖压缩缓存；新增跨独立wire/子集测试。注意完整匹配检查本身有生成/比较成本，M2需profiling。
- 同一实现中Batch/Emitter/WorldDetached编码有变化，双方必须同步更新；协议兼容性需后续审查，不能混用旧jar。
- `ProgramLatencyMatrixTest`72组：1/2/5独立副本 × RTT0/100/250 × 抖动0/30 × stall500/2000 × TPS20/10。下行有序队列、恢复请求上行排队，重追踪/同刻几何/End清空；输出CSV。不是真实客户端，更不是覆盖500ms实机闪烁的证明。
- `tools/net_jitter_proxy.py`已有压缩后连接字节计量，已改有界64块队列按到达时间延迟，保TCP顺序，避免逐chunk串行叠加；`tools/test_net_jitter_proxy.py`有突发时序/顺序测试。
- `tools/program_network_matrix.py prepare`生成72行pending-real-clients清单，`relay --rtt 250 --jitter 30`启动现有代理与50ms计量；不能凭生成清单标实测通过。
- 既有`ClientFrameProbe`/wrapper任务runClientProfile记录整帧（原版读数滞后一帧），`tools/baseline_window.py`可采样ledger窗口。
- NetProfile.programTick写program_tickcost（host advance+碰撞+publish+回调CPU），recentHost/receive/projection/geometryAndSubmit仍是局部最近窗口；不是GPU完成时间。
- `tools/program_ab_report.py`汇总window.json/server.jsonl/frames.csv/proxy.jsonl、p95及连接总字节。只属初步工具，CPU测量范围不同、字节是连接总流量，尚不能替代danmaku等负载报告/自动批准M2。勿称完备一键A/B已实现。

## 高延迟闪烁：现状与排查入口

- `ProgramClock`分离估计映射和显示时间，小误差每tick最多0.05校正；可信拟合偏差>2但最新采样误差≤2时mapping reset保显示；小误差移动holdover≤2本地tick。
- 最新缓解：`display-error`且|最新样本−显示|≤4时额外最多1本地tick frozenGrace；时间冻结，不标trusted；同tick积压/误差>4撤销。ProgramClockTest验证触发、冻结、超时和积压，但**用户仍反馈严重闪烁**。
- `client/ProgramClientNetwork.renderViews`在clock不可renderable时整维度返回空；`ProgramReplica.renderTime`还会请求恢复。恢复等下一独立样本，500ms环境可能等得远超1tick，grace不能覆盖。这里只是值得验证的因果假设，需读最新日志/复现，不直接放宽隐藏门槛掩盖错位。
- ProgramReplica同订阅checkpoint保history、新订阅完整安装清history；分块安装前保旧完整image；renderViews/state按imageAt选历史。还应审查全局时钟与owner多stream样本、同tick积压、历史切换、恢复请求是否必要、End/Release与真空帧等。
- 日志关键字：program-clock-transition、program-clock（含frozenGrace）、program-client、program-preview STOP与networkBeforeEnd。之前本地run/logs/latest.log有display-error约2.04–2.88tick、恢复等待5–12本地tick；最新实机日志可能已覆盖，不将旧日志当最终全因果证据。日志不清理。
- 前一轮最初猜stale并尝试修改，已撤销；最终依据display-error。别重复把stale推测当实测根因。严重积压禁止长期外推，不能靠抬容差制造“无闪烁”假通过。

## 剩余工作顺序

1. 优先建立500ms（含抖动/积压）真实renderTime→renderable→renderViews逐帧离线复现，检查冻结grace不足与恢复策略；用户无需反复测试。需要日志时先自行读取，勿先新增一堆内容。
2. 审查未闭环边界：普通弹撞块仍中心线裁剪非半径体积扫掠；有限曲线采样不是任意高频Path数学误差证明；法阵Member/External源FX、高频声音不补播的非空cue场景、双视角/真实外部实体源等。不要以空sounds测试宣称完整声音链通过。
3. 真实1/2/5客户端矩阵仍欠。500ms单客户端独立服确认不等价RTT0/100/250×抖动0/30、目标重排/死亡/退出/晚加入/重追踪/低TPS全矩阵。有环境限制就如实保留，不让用户先自行找玩家/配代理。
4. 1.5诊断与2.6 M2：先补共同低层legacy/program等负载fixture、统一采样窗口和自动报告，不拿内容缺拍/不同弹量对比。固定硬件/JDK/分辨率/视距/压缩/seed/动作，30s暖机+180s采样×3。场景48/480、800/2000、花海/尾珠、蓝8五人、旋转激光动态方块、加入突发。
5. M2硬门槛：常规运动无逐弹Entity流；逻辑弹幕字节降≥50%；server danmaku CPU p95和client整帧p95≤旧110%；48→480初始逻辑≤2倍（仅该探针已有证据）；End资源归零、重复20次无增长、恢复按活跃状态有界。逻辑/封套/压缩后线上字节分开。未过不迁移/不归档。
6. 蓝1/4/6完整实机/动态方块等证据按acceptance/tasks核实；不要把历史首切片/blue4修复确认外推成所有卡全通过。任务2.7最终构建与证据收口在全部必要验收之后再勾。

本轮非验收工具收口：`tools/program_m2_manifest.py prepare`生成9个场景×3次重复×legacy/program的54项证据清单；`validate`要求每项有metadata/window/server/frames/proxy。`program_ab_report.py`现在要求metadata包含共同scenario/seed/hardware/JDK/分辨率/视距/压缩配置及至少30秒暖机、180秒采样，并检查legacy/program元数据相等。工具只报告dataReady，不自动批准M2。标准库测试为`python -m unittest tools.test_program_m2_manifest tools.test_net_jitter_proxy`。

## 验证与环境约定

- 最新最终完整build（含全测试）通过：`build/agent-logs/20261017-191330-build.log`，2m14s。
- 较早完整build191050通过2m15s；191221因agent误启动并行build，output.bin争用失败，不能当源码测试失败或通过证据。之后已串行重跑成功，切勿同时启动test/build。
- 最新功能定向测试190934通过；ProgramClock的明确断言190759通过。strict和git diff --check此前通过，本轮文档改完再次执行。
- lang审计只报既有31个`jei.gensokyou.recipe.zaohua_*`数据派生中文键，无新增Java文本键缺失。
- 所有Gradle必须`tools/gradle_task.ps1`。wrapper超时任务继续后台，先读日志/exit哨兵等它结束，不再次启动竞争任务；不用裸gradlew、Out-String、Select-Object -First截断输出。
- JDK21：F:/application/jdk-21。用户客户端/独立服可能仍在运行，不擅自结束它们或清理持锁文件。
- 继续代码前按需加载boss-dev-design、neoforge-1211-dev、openspec-apply-change；本交接不是重新实现已过内容的指令。

参考：本change proposal/design/specs/tasks/acceptance，父change migration.md §4–5、pattern-recipes.md与content-mapping.md；handoff.md和architecture-debug-handoff.md为历史证据，本文最新用户反馈优先。操作台账在`.opencode/skills/boss-dev-design/references/danmaku-operations.md`。
