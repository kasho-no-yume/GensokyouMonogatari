# 第五期架构排障交接：实机仍失败，请先审计架构实现

> **本文标题和失败结论属于历史排障。最新接手入口：[next-session-handoff.md](next-session-handoff.md)。** 花海/尾珠及本批四个组合预览已实机通过，剩余工具与完整案例尚需实现，不能继续用旧失败状态覆盖新反馈。

> 最新用户确认：尾珠闪烁修正后“没有明显问题”，花海/尾珠单人预览验收通过，2.2已勾选。继续框架审查发现碰撞候选未按沿路径顺序、扫掠末端未扩半径、曲线超过8段仍静默接受弦；已抽取ProgramCollisionSweep修正，补曲线拒绝/S弯/最近命中/高速末端/静止接触测试。光束事实改为一次构造image，避免每光束复制全表。普通弹撞块依然中心线；有限采样仍非任意Path数学误差证明。不要把本轮修正扩大解释为所有碰撞/性能门槛完成。

> **15:46 后续修复与验证：** 用户复验花海未见闪烁，尾珠在头弹结束后的漂浮期仍闪2–3次。`run/logs/latest.log` 15:08:06/09/18三次 `display-error`，最新采样误差仅约 -1.40/-1.11/-1.20 tick；短暂失信触发恢复请求，尾珠累计 CHECKPOINT 约4.9MB。现将“拟合映射偏移但最新采样仍在2tick内”作为映射重置，保留已显示边界和可信状态；真正失信仍按有界维持/严重恢复处理。ProgramReplica短暂可显示维持不请求全量状态恢复；同订阅恢复保留生效前历史，新订阅安装清空旧历史。新增稀疏抖动1200tick逐帧可见性、尾珠连续发布延长至879tick（成员全灭后订阅释放不要求时钟）、同订阅恢复历史、旧订阅迟包/分块中End、多owner独立结束、1/2/5独立观看者离开/重进/恢复/删除矩阵。47项定向测试通过 `build/agent-logs/20261017-154541-test.log`。观看者矩阵是离线协议测试，不是真实多人RTT/TPS验收。待集中实机复验尾珠漂浮期闪烁、暂停/重启和空间重追踪；不宣称第五期或M2完成。

> **2026-10-17 后续状态更新：** 用户确认上一轮修正后花海、尾珠“性能和表现都达标了，没有卡顿表现也很不错”，但偶发整体闪烁。本文件下述严重失步证据保留作历史根因，不能继续当作最新实机状态。现已修正历史 image 渲染选择、显式时钟恢复、重复解码/求值与摘要热路径；完整测试和构建通过。新一轮闪烁修复给小误差映射失信增加最多 2 个本地 tick 的旧速率维持，误差界为 2 tick，严重积压立即隐藏，短暂恢复保留显示边界；补充时钟 transition 日志和自动化回归。构建含完整测试通过 `build/agent-logs/20261017-145223-build.log`，strict validation通过。**该轮闪烁修复等待客户端复验；多人/延迟矩阵与 M2 量化门槛仍未通过验收。**

> 下一步先让用户在最新客户端完整运行 flower_sea 与 tail_pearls，重点观察尾珠（旧日志中运行中 clockRecoveries 从 1 升至 3），再验证 stop/restart 与单人暂停恢复。若仍闪烁，读取 `[program-clock-transition]` 的 reason/outcome/recoveryError/holdover 与时间戳；仅有5秒一次汇总不能断言具体空帧原因。时钟恢复与网络恢复请求是不同计数。不要为了继续内容开发跳过这一轮复验。

更新时间：2026-10-17，用户最后反馈之后。

## 0. 当前结论与工作性质

当前做的是第五期 gameplay 的**代表切片闭环验证，并据样例修正运行时/同步/渲染实现**，不是在成熟运行时上单纯编写符卡排布。正式 BOSS 卡还没有迁移。本阶段应先证明基础设施正确，才允许进入第六期。

**实机验收失败，不能宣称架构完善或本期完成。** 用户最新反馈：flower_sea 起始几秒明显卡顿，随后弹幕呈幽灵状态，渲染和逻辑位置不一致；前面数轮修复仍未解决。自动化测试通过仅证明各自覆盖的性质，不能抵消用户实机结果。停止新增内容和逐症状补丁，先审计已有实现。不要简单重置时钟或放宽预算后再次让用户盲测。

本次仅整理交接，没有继续修改运行时代码。工作区存在多轮未提交实现和用户/先前agent改动；不要reset/checkout或删除日志。此前主交接文档按时间堆积且含已过时判断，遇到矛盾以本文和最新原始日志为准。

## 1. 最重要的新证据：时间域严重偏离且仍标trusted

读取时的`run/logs/latest.log`（下次运行会覆盖）关键行200–206：

```text
13:42:18.347 START owner=cc6836da-3e9d-4a47-8899-b85abd521a70
definition=31631c63df35f14e9e39c7463d4974a73dbf07c0326421e3e1ba19f2c4c08021
13:42:19.191 local=100 render=1318398.0 clockError=0.0 trusted=true failures=0 late=0 transfers=0
13:42:24.192 local=200 render=1318457.2399864208 clockError=53.76001357915811 trusted=true failures=0 late=0 transfers=0
13:42:29.181 local=300 render=1318562.0735703306 clockError=48.8274197683204 trusted=true failures=0 late=0 transfers=0
13:42:34.190 local=400 render=1318667.1160922265 clockError=45.866050630575046 trusted=true failures=0 late=0 transfers=0
13:42:39.183 local=500 render=1318772.124097357 clockError=39.87590264296159 trusted=true failures=0 late=0 transfers=0
```

`clockError`定义是`ProgramClock.estimate(localTick) - time(localTick)`，**不是独立测得的真实网络单程延迟，也不是直接对齐server tick的观测**。但它直接证明客户端显示时间落后其自身估计几十tick，且trusted只反映estimator可用性，没有约束显示时间误差。即使估计本身尚需审计，这个内部不一致也不能算正常平滑。

花心重瞄后速度0.3格/tick，约50tick偏差可产生约15格显示/判伤错位；花瓣速度0.2也可产生约10格错位。当前按最新权威mask立即删除成员、却在旧显示时间求位置，可能让视觉上尚未抵达的弹提前消失。用户称“幽灵弹”不应被狭义解释为死成员复活；也可能是活成员在错误时间/位置显示。

**最高优先级假设：启动负载/事件阻塞导致时钟进入不可信或冻结；恢复后estimator回到TRACKING，而显示时间只能每本地tick额外校正0.05，几十tick积欠持续整轮。** 在±0.05校正、速率约1时，50tick偏差约需1000tick才能消除。此前日志未记录每个observe的Outcome、trusted变化及耗时，无法证明这53tick具体在哪个分支产生。不要将因果链后半段的证据当成已确定了启动卡顿的根因。

## 2. 原设计的硬契约

完整参考：父change的`design.md`、`operations.md`、`migration.md`，及本change的proposal/design/specs/tasks。

- 服务器唯一权威，维度gameTime为共同时间。程序成员不创建Minecraft Entity。
- 固定完整输入+同一时间的路径求值两端一致；随机种子与采样事实同步，不在客户端查询世界决策。
- 组级共享描述、稀疏权威事件、晚加入自足checkpoint；正常飞行不逐弹同步位置。
- 预警与出生共用plan，批准前不得生成危险客户端成员。
- 有界平滑只用于小视觉偏移，不能造成持续数十tick显示/判伤偏离；时钟失信冻结并显式恢复，不能把长期冻结后缓慢追赶当成功恢复。
- 部分网络快照不能部分安装；订阅/恢复不应让旧状态复活。
- 完整同负载CPU/frame-time/带宽与1/2/5人网络矩阵通过才可迁移。当前未通过。

以上是目标，不代表当前代码已兑现。

## 3. 当前实际数据流与代码入口

路径以下均相对`src/main/java/com/bitsson/gensokyou/`。

### 3.1 宿主与authority

`command/ProgramPreviewCommands` → `program/gameplay/ProgramPreviewHost.start`：每维度一个显式session，静止ArmorStand仅作owner。创建runtime、编译definition、install到ProgramSession，注册ProgramServerHost和ProgramNetworkHost。自然结束、stop、重启、owner/viewer失效、卸载有清理。

`program/server/ProgramServerHost`在LevelTickEvent.Post取level.gameTime，`session.advance` → `runtime.drainEvents` → `ProgramNetworkHost.publish`。**现有server Stats计时在publish前结束，因此不包含投影、空间选择、编码、hash、发包；不能用低authority耗时证明整个server tick快。**

`ProgramSession`/`ProgramExecution`调度类型化操作；`DanmakuWorldRuntime`按TERMINATE/CAPTURE/PREPARE/COMMIT/TRAJECTORY/COLLISION/PUBLISH执行，持有组/plan/成员/重瞄路径等。整数S碰撞覆盖S→S+1。`EmissionPlan.state`用于普通解析；`runtime.rebase`保存`Rebased(origin,path,start,end)`世界路径并发布SOURCE_SAMPLE。

### 3.2 花海fixture

`program/gameplay/ProgramPreviewPrograms.flowerSea`：显式两轮、轮长240；每轮16朵每4tick一朵，每朵Union(core,45 petals)，总1472成员。初始速度曲线0.24→0/60tick，后停；age100前无害，寿命400。固定种子出射轴、花面旋转固化进定义。age100：`RadialRelease`以当前核心为中心给花瓣世界径向0.2路径；`RebaseTarget(slot=-1)`采当时有效目标一次给核心0.3路径。核心半径.55/4倍、花瓣.275/1倍。该fixture是测试内容，不是正式卡迁移，不应拿它当前表现当系统可用性结论。

新增`RebaseTarget`、`RadialRelease`在`ServerControlOperations`的大分支中执行，`ProgramExecution.validateHandles`同步加了识别。这是当前实现方式，不代表已建立完善、可独立扩展的author-facing操作抽象；请审计是否又形成central dispatch维护债。

### 3.3 投影和网络

`ProgramNetworkHost.publish`每tick checkpoint整个session，`ReplicaState.capture`产Image：Group、Batch模板与mask、Motion/Rebased、Instance、采样、派生游标等；碰撞适配器附加Beam；`ProgramPreviewBounds`生成组/成员包络；每观看者`ProgramSyncServer.publishSpatial` → `ProgramInterest.spatial` → 按owner filter → diff → Decision或Start/Checkpoint → codec/chunk/send。ClockDigest默认预览每20tick，摘要目前是`hash(wire.image(next))`，仍编码完整image。

`ProgramWire`通用record fallback为OperationDataCodec展开字符串字段。Batch、Pose、Seed、Member已局部二进制特化；Motion/Rebased等仍多为反射字符串。descriptionHash按ChildProgram身份有界缓存。diff比较不相等对象时可能对双方encodeData。新的record实例/Segments相等性和缓存命中都需真实profiling。

`ProgramInterest.spatial`按成员包络选择模板并保留父源闭包。新增死亡/出生原先误触发订阅换代已局部修复。当前仍持有前后authority/spatial Image对比同一存活成员的选择变化。空组生命周期误判也修过。**这些修补需要整体状态机审计，不保证所有兴趣切换/恢复/多owner情况正确。**

包络现状：`ProgramPreviewBounds`给花海fixture从birth预留102格半径，避免age100临时扩张。每成员暂共用整组box，死亡模板槽也给bounds避免partial-table fallback。**这是fixture专用保守包络，不是通用动态/跟随/任意组合包络方案。** 大box可能使每个成员独立分片，重复订阅、分配和全量过滤仍有成本。不能继续靠扩大半径或提高协议限额代替设计。

### 3.4 客户端接收与渲染

`client/ProgramClientNetwork.handle`通过context.enqueueWork在客户端线程执行`replica.receive(bytes,tick)`；**tick是处理时刻，不是网络到达时刻，更不是server tick**。DEFINITION decode/compile/validate也在此执行，可能阻塞渲染/客户端tick，需测量。

`program/network/ProgramReplica`按dimension+owner的Scope维护epoch/subscription/sequence/Image/history/tombstone，分块完整后装入；缓存definitions/descriptions。新epoch清空，同epoch订阅change现在保留旧image直到整份checkpoint可安装。接收Decision当前会ready decode/apply一次，再install decode/apply一次；validate全image；tombstone集合复制，history保留最多128份Image；这些都可能在客户端制造延迟/GC。

`ProgramClock`每维度一个，复用旧`danmaku/motion/DanmakuServerClock` estimator。estimator有rate范围.8..1.25、残差4、stale200等规则。显示时间`last`和估计分离：time先按rate推进，再每local tick最多±.05校正。trusted仅来自estimator.TRACKING；**没有显示时间误差界和恢复追赶上限**。idle维度在End/Release和首新image前释放旧clock；多活跃scope共享保留。之前为防负age/跳跃做的局部规则必须重新审计。

`ProgramClientNetwork`的本地整数tick在ClientTickEvent.Post加1；本次前一轮已加Minecraft.isPaused时不推进，render partial暂停取0。需要核实MC本地tick、帧partial、integrated server暂停、卡顿补tick之间的真实相位；不能认为这已证明时钟正确。

`ProgramDanmakuRenderer`AFTER_PARTICLES → `replica.renderTime(local tick+partial)` → `replica.renderViews` → `ProgramRenderView.capture` → `ReplicaState.state`，再画billboard/laser。**renderViews直接用最新image；ProgramReplica.state测试入口有历史image选择，二者不是相同入口。** Rebased start之前现在fallback原plan（只适合该轨迹是第一次rebase；多次rebase历史语义仍需审计）。显示位置由显示时间求值，成员存活/harmful mask来自最新权威image，可能跨时间域。

### 3.5 判伤

`ProgramCollisionAdapter`每危险成员每tick查询实体、曲线子段、AABB/sphere判据；普通弹新增中心射线逐子段撞块消解，**不是带半径的方块体积扫掠**。激光裁剪+节流。owner属性进入DANMAKU源，无成员实体。每成员多次解析state/世界查询，未有完整空间批处理。

服务端按当前整数tick真实判伤；客户端显示可能落后几十tick。即便state同时间逐位一致，这个组合仍会是幽灵弹。不要单独改伤害半径来掩盖错位。

## 4. 已发生问题、修过什么、没有证明什么

1. 初期尾珠约280tick发110–118MB checkpoint，花海约20MB，多次恢复。修了出生/死亡被当兴趣变化；后续花海仍22.5MB，说明最初修复不足。
2. 尾珠347tick服务端崩：ClockDigest调用完整wire.image超过4MiB。报告`run/crash-reports/crash-2026-10-17_12.35.07-server.txt`；Pose/Seed/Member紧凑编码后420tick测试通过。未证明任意合法程序都有预算闭合和超限安全处理。
3. 第二次启动有判伤没画面：idle clock保留导致新birth不被显示；加idle清理，模拟End/Release后2000tick新epoch测试通过。未证明活跃中因卡顿产生巨大显示误差的恢复。
4. 花海重瞄包络扩张触发checkpoint；死亡模板缺bounds fallback；空组寿命结束误判兴趣变化；同epoch分块先清空画面；暂停仍计时。这些已改，不能当成当前症状完全解决的证据。
5. 最新日志显示零失败/零未完成传输但显示时间落后自身估计约54tick；**这是目前最高证据优先级**，前面“都修好了”的结论不成立。

## 5. 现有测试为何漏掉

`ProgramBlue1Test`已有：花海数量/径向/目标采样/跨轮/单次late join；尾珠数量/母死亡/预算拒绝；连续发布420tick；非原点倾斜frame和移动viewer；renderViews在指定相同tick下与authority比位置；分块/End/Release和第二次启动等。

主要盲区：

- 绝大多数位置测试传入相同tick给server与client，**没有比较真实renderTime(local+partial)输出与当前server时间**。数学对不等于游戏对。
- 即时顺序交付，基本一server tick一local tick；未模拟启动大定义在主线程编译、数帧或几十tick阻塞、同一个localTick处理一批不同serverTick事件、后续速率拟合拒绝/恢复。
- continuous fixture多为纯世界mock，无真实渲染线程、BufferSource、GC、网络排队、实体/方块查询成本、integrated-server catch-up。
- 只检查ProgramClock收包连续/陈旧冻结，**没有断言失信恢复后显示误差在有限窗口内恢复**，甚至“恢复不能跳跃”的测试可能固化了错误长期追赶策略。
- tests的publish经空间过滤但没有完整真实pause、兴趣边界/新订阅分块延迟矩阵；新测试仍不能覆盖所有多scope状态。
- 过去测试名称/任务勾选可能比验证范围宽；1.1–1.4勾选仅是旧接线进度，**不代表实机正确性**。应审核是否拆分或撤销完成结论，不要因4/12而假设基础都可靠。

## 6. 建议接手排查顺序（先观测，不先再补特例）

### P0：保留证据、重现并定义时间一致性断言

保存latest/debug日志、crash报告和本地变更。固定同一world/玩家/视角/种子，不改弹量/寿命。为同一稳定MemberId记录：serverTick、生效tick/birth、server区间位置、消息采样/到达/处理时刻、local tick+partial、estimator time、display time、两处各自求值位置、mask effective tick。

明确“误差来自描述、时间、安装边界还是画面几何”，不要再把客户端state在指定tick一致当终点。优先复现最新日志的0→54tick误差变化，记录每个observe Outcome、availability切换、rate、anchor、elapsed、累计冻结时长。

### P1：重新审计时钟设计与信任边界

审计复用旧entity estimator是否适合高频组事件；REJECTED_RATE_RANGE/OUTLIER/STALENESS如何恢复，observe的处理延迟是否被错误当rate；同localTick多包是否使窗口失真。trusted必须覆盖显示时刻与权威估计之间的误差，不只是映射拟合成功。

确定小误差平滑与严重失步恢复的不同语义，明确可见修正/冻结/恢复条件和时间上界。不要只把±.05改大、max(last,newEstimate)、或每包硬重锚；这会把幽灵弹换成瞬移。也不能系统性延迟所有判伤或降低弹量掩盖问题。需要设计级决定和对应测试。

### P2：启动卡顿与端到端CPU profiling

分离测量：服务端advance、checkpoint/capture、facts、bounds、per-viewer select/diff/encode/digest/send；客户端解包、定义decode/compile、image ready/install/validate、history/tombstone、render view求值、vertex输出、buffer flush、GC。记录p50/p95/max与开始100tick事件时间线。

候选热点：大ProgramDefinition/重复ChildProgram编码与同步编译；Motion/Rebased反射字段重复；每tick全image复制/过滤/排序/diff；ClockDigest全量序列化；无变化对象但引用不同导致encode比较；每个checkpoint复制整个image/history；group共用大box导致细粒度shard退化；每弹每帧HashSet/stream/Vec3与重复memberState(age0)；多次renderer RenderType切换/深度alpha。**目前没有profile证据，不得把候选项写成已确定根因。**

### P3：状态安装时间域与恢复闭包

审计最新mask与落后显示时间能否混用，是否需要有界事实时间线；renderViews与state历史入口统一语义；多次Rebase前态不能仅fallback最初plan；同epoch保留旧image时如何阻止过时Decision误应用；tombstone、subscription新旧快照、End交错、分块超时与完整提交。

事件存在effectiveTick还不够：真正渲染调用必须按正确时间使用对应状态。恢复不能回放命中，也不能无界存历史。做暂停/低TPS/主线程卡顿/多批同tick处理/晚到重瞄/重复启动/多owner测试。

### P4：碰撞与表现的真实契约

确认花海无害窗口、birth/age100、fade/lifetime、同tick碰撞和移除对渲染含义；确实要表现但不伤害的阶段不能误叫幽灵；核心/花瓣视觉半径和现有profile visualScale匹配碰撞。普通弹撞块目前只是中心线；曲线误差超8段仍接受弦的实现也不满足原设计超限拒绝契约，应独立审计。

### P5：稳定之后再恢复内容开发

建立可重复启动/连续两次/暂停恢复/负载扰动的实机与自动化gate，测量明确最大显示时间误差、世界位置误差和恢复时长。代表切片是架构验证器，作者以后不应为每张卡修网络/时钟/dispatch。达不到约束时应该重构相关层，保留可复用纯数学与内容数据，而不是继续堆fixture-specific补丁。

## 7. 验证与工作区约定

所有Gradle必须用`tools/gradle_task.ps1`，wrapper超时构建仍在后台，读取日志最终BUILD结果。相关skill：neoforge-1211-dev、boss-dev-design、openspec-apply-change。不要裸gradlew，不删除现有未提交变更，不进入正式BossCards迁移。

最近修复前完整构建通过：`build/agent-logs/20261017-133157-build.log`，2m9s。它通过的是自动化检查，**随后13:42用户实机仍失败**。本次仅文档整理，不需重跑Gradle；可以严格验证OpenSpec。

交接必须保留以下状态：第五期未验收，1.5/2.1–2.7未完成，M2不通过/尚无通过证据。用户希望先得到可靠、可复用的运行时，明确授权下一位agent审计并必要时重构此前实现，而不是继续填符卡。
