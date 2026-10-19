# BOSS 程序弹幕：独立审计与修复交接

## 0. 范围、版本与本轮状态

- 2026-10-19，第一轮：调查、复现、修复准备。按用户要求，须经用户确认，第二轮才改实现；第二轮无论成功与否，都必须在本文件追加实现与验证记录。
- 审计 HEAD：`4a931d8`；用户指定提交：`c4dd6bc 失败作品第六期`。已读该提交涉及的 `docs/danmaku-program-runtime-sync.md`、`openspec/changes/migrate-boss-cards-to-programs/runtime-playtest-handoff.md`。HEAD 后一个提交不改变相关 Java。
- 原有用户修改：`gradle.properties` 的 JDK 从 `F:/application/jdk-21` 改为 `C:/jdk21`，保留。
- 本轮没有修改产品 Java、符卡参数、配置或旧交接文档，没有启动游戏、没有取得新的实机 TPS/GPU/生存伤害验收结果。
- 代码搜索 MCP 索引时连接中断，退回文件读取；不依赖索引结论。没有委派子代理。

## 1. 判断

**“权威逻辑 + 出生/决定事实同步 + 客户端确定性公式 + 无逐弹实体”可行，应保留。问题集中在运行时执行成本、事实投影丢字段、时钟与事实交付脱节，以及生命周期时间契约不完整。没有证据证明必须在高 TPS 和平滑弹幕之间二选一。**

这不等于当前实现只差一个时钟参数。现在至少有三条独立故障链：

1. 活弹/派生增长 → 重复求值、近域全场扫描、全量投影重建 → tick 耗时及间隔波动。
2. 边界时钟硬夹取、换锚及事实接收不同步 → 即使帧率高、弹量少，也会停顿/跳步。
3. 正式空间订阅丢失瞄准事实 → 两端同一时间也会走不同轨迹。这不能通过平滑时钟解决。

具体热点的毫秒占比仍须测量；不能将静态复杂度分析冒充 JFR，也不能将旧实机 55–62ms 的整段耗时全部归到碰撞。

## 2. 实际链路与必须纠正的旧判断

`ProgramServerHost.tick(LevelTickEvent.Post)`：

1. `ProgramSession.advance(N)` → runtime 的 TERMINATE/CAPTURE/PREPARE/COMMIT/TRAJECTORY/COLLISION/PUBLISH。
2. runtime 的 `collide()` 在 N 内求 `state(N)` 和 `state(N+1)`；adapter 扫掠整个 `[N,N+1]` 并立即伤害/删除。
3. 发布 `IntegratedProgramTimeline` 边界。
4. drain events → checkpoint → `ReplicaState.capture` → bounds → spatial → diff/encode/send。
5. 客户端 payload `enqueueWork` 后安装 Image；渲染线程独立读取共享边界。

因此旧说明“画到 N+0.8 时服务端还没有计算该位置”不适用于已经完成 advance(N) 的普通轨迹：该区间的碰撞已经扫过。`authority=N/render=N+1` 单独不能证明幽灵弹幕。

真正的问题是：**Image(N) 是 N tick 碰撞之后的存活集合，而位置函数仍可向 N+1 求值；N+1 的停止、转向、派生等操作尚未执行。** 不能把 Image 的 tick 无条件解释为一份覆盖所有时间的完整世界状态。

若一颗弹出生于 N，并在 N 的扫掠中命中，碰撞后 snapshot 中它已经不存在，客户端可能从未显示它，却收到伤害。这是当前实现可推导的边界行为，尚未用真实世界实验确认它是否对应用户当时的幽灵命中。

同理，“固定延迟一 tick”只有平滑意义，不自动保证判伤同位：服务端已经执行 `[N,N+1]`，显示再落后会增加判伤领先视觉的距离。第二轮必须明确处理这个问题，不能照抄旧文档的目标公式就宣称修复。

## 3. 确定缺陷及复现

### A. 空间订阅丢失 aimLocal（正确性 P0）

位置：`network/ProgramInterest.java`，`spatial()` 重建 `ReplicaState.Batch`。

原始 Batch 含 `aimLocal`。重建使用只传到 `b.motion()` 的兼容构造器，该构造器把 `aimLocal` 设为 null。`Actions.ChildProgram.aimDirection()` 在 null 时退回模板方向。`BossProgramHost.bind()` 正式安装了 `publishSpatial`，所以这是生产链路，不是闲置辅助函数。

已用当前 main 编译产物调用真实 `ProgramInterest.spatial` 复现：

```text
aim before=Vec3[x=0.0, y=0.0, z=10.0] after=null
authority=Vec3[x=0.038838648618159555, y=0.0, z=9.805806756909202]
spatialReplica=Vec3[x=2.0, y=0.0, z=10.0]
errorBlocks=1.9707523593328433
```

示例：出生局部位置 (2,0,0)、目标 (0,0,10)、速度 1、age=10。没有时间误差，也没有网络抖动。

影响所有经过空间订阅的 AIM_LOCAL 程序，包含相关蓝符卡；**不是山女第一卡母弹/尾珠抖动的解释**，它们使用 TEMPLATE/SEEDED_SPHERE。需增加空间筛选→编码→解码→replica 的真实全链路 parity，而非只测 capture。

### B. 集成时钟必然有停顿和边界跳变（平滑 P0）

位置：`gameplay/IntegratedProgramTimeline.java:8` 的 `Boundary.time` 与 `publish`。

输出硬限制 `[tick,tick+1]`；旧边界耗尽后冻结，下一边界晚到又直接切到新边界的分数相位。0.5ms 慢校准没有消除两侧夹取的不连续。

真实类最小复现：tick100 在 1000ms 发布，tick101 在 1058ms 发布。同一 now：

```text
late boundary before=101.0 after=101.152 jumpTicks=0.15200000000000102
pause rewind=-0.15200000000000102
```

旧边界在 1050–1058ms 已冻结；发布后瞬跳 0.152 tick。山女母弹速度 0.70 时相当于瞬移约 0.1064 格。暂停直接返回整数 tick 又会倒退；长暂停恢复还有大误差重锚路径。

现有 `IntegratedProgramTimelineTest` 容忍 `<0.19 tick` 跳变，最小复现恰好被它允许。理想 50ms case 只能说明理想情况。

### C. 时间先于事实可见（同步 P0）

位置：`server/ProgramServerHost.java:61` 附近，`client/ProgramClientNetwork.renderViews`，`network/ProgramReplica.imageAt`。

共享边界先 publish，网络随后构造并发送，客户端还要排队安装。渲染可以看到 N 的时钟，却只有 N-k 的 Image。`imageAt` 找到最新的较老 Image 后直接在请求时间求值，无“事实已完整应用至哪个 tick/revision”的约束。

这会让旧成员在 REMOVE/REBASE/CANCEL 等事实到达前继续显示，或让新成员到达时以非零 age 突然出现。仅把共享 publish 移到 send 后仍不等于客户端已安装，不能作为完整修复。

`ProgramSyncServer` 没有变化就不发 Image；`CLOCK_DIGEST` 只校时、不追加 history。因此不能拿最后一个 Image.tick 当“服务器只确认到这里”，也不能假定 history 是每 tick 两份连续快照。需要独立的有序已应用水位（包含无事实变化的时间推进）。

### D. 诊断数据的标签错误（可观测性 P0）

- `ProgramDanmakuRenderer` 把 `views.max(Snapshot.tick)` 传给 probe 的 `renderTime`；Snapshot.tick 来自 Image，不是实际分数求值时间。
- `ProgramMotionProbe.frame` 每帧取 `members().getFirst()`；Map 遍历顺序不构成稳定成员顺序。它并没有按已选 MemberId 查找同一颗弹，成员变化就重置样本。
- 只记最大位移，没有除帧间隔、没有与路径期望速度比较；无法区分掉帧和轨迹跳步。
- `frameAvgMs` 分母是 frames，但间隔样本是 frames-1；偏差小，仍应改。
- `serverAvgMs` 包含 advance + 网络发布等整段，不是碰撞耗时或整个 Minecraft tick 的独立度量。

下一轮应记录真实 renderTime、applied-through tick/revision、选中 Image、固定 ID 的实际/期望位移、冻结/重锚次数，以及 advance/admission/collision/capture/bounds/spatial/diff/encode 分项；有界缓冲，停止采样时导出，不逐帧刷日志。

## 4. 性能实现问题（结构已确认，贡献比例待 profile）

### E. 每颗派生弹准入都会再算一次全场（高优先）

`DanmakuWorldRuntime.runChildren()` 对每根母弹逐次 prepare/commit；`admission()` 对每名战斗玩家遍历所有存活成员并 `state(m,tick)`，统计近域密度。即使 minimumClearance=0，近域扫描仍执行。

山女第三卡 7 根/20tick，母弹 24 次/4tick 派生；稳态同相位约 28–35 根母弹同时派生。若 live≈3458，单个派生日仅近域扫描就约 9.7万–12.1万次路径求值/玩家，还不含碰撞和客户端。该复杂度是 `O(本tick提交批数 × 活弹数 × 玩家数)`，不能简单视为“三千颗弹自然就该慢”。

修复方向：tick 内缓存位置/近域计数，或空间索引；保留逐次准入和先后顺序；每次 install/remove/replace 更新统计，rebase/follow/目标变化必须使相关缓存失效。不能缓存到下一 tick，也不能只查玩家而跳过对其他 LivingEntity 的命中。

### F. 反复全表运行及计算静态方向

- `runChildren()` 每 tick PREPARE、COMMIT 各执行一次，每次 do/while 至少两趟遍历，即使多数尾珠没有 actions。
- 每活弹 TERMINATE 求值，damaging 再求值，collide 再求 from/until；adapter 忽略可复用端点，sample 又求两端+中点+四分点。普通直线弹正常路径至少约 9 次 state 求值/tick，尚不含 admission。
- `ChildProgram.memberState` 每次重算稳定随机方向、构造 Frame、归一化；尾珠方向与 age 无关，本可以在出生或评价上下文缓存。
- 所有弹幕走曲线细分和世界查询。可对**可证明整个区间为仿射且无分段边界**的路径复用端点，曲线保留自适应采样；不可只凭顶层 Linear 就忽略 transform/group motion。
- `collect()` 按 consumed trigger × group 扫描并拼 UUID 字符串；`ReplicaState.capture` 再为每个 trigger 扫全部 groups。大量派生历史会增加费用。可用结构化 key/所属组索引，但需兼容存档。

### G. 网络少发包不等于服务端少干活

每 tick 执行 checkpoint 全量复制、capture 全量构造 TreeMap/Set/mask、bounds、spatial 分片/复制、diff，最后才决定是否发送变化。

`ProgramMotionBounds.members()` 又调用一次 `groups()`；且它给每个成员的是同一个 group 大包络。当前正式配置仍为每个 batch 排序/建 shard，却没有真正得到逐成员细粒度剔除收益。这部分可复用已计算 group bounds，并在已知整组同包络时采用等价快路径。

不能只凭 revision 不变跳过所有发布：beam length、跟随采样、订阅位置可能变化且未必递增同一个 revision。必须区分静态批次事实和动态决定。

## 5. 已有测试为什么会绿

- `yamameWebAuthorityAndReplicaAgreeForCarriersAndDerivedPearls`、大妖精 parity：使用直接 capture 的 image，同一时间求值；没有 spatial/收包交错，也没有真实碰撞 adapter。
- `aimAtTargetSamplesTheRealSourceToTargetVector`：只断言 authority，漏掉 A。
- `yamameNestTailPearlsEndBeforeTheNextCycle`：实际调用 `battle.install(0)`（默认首卡），没有显式装第三卡，最后只断言 `maxLive>0`。既未证明第三卡，也未证明测试名所称尾珠终止行为。不可拿它做性能/寿命回归依据。
- 现有时钟 jitter 测试允许明显跳步，catchup 测试还明确要求立即重锚，两者没有验证帧间速度连续。

本轮运行现有测试作为基线：

```powershell
.\tools\gradle_task.ps1 test --tests '*IntegratedProgramTimelineTest' --tests '*ProgramContentTest' --tests '*ProgramNetworkTest' -TimeoutSec 60
```

wrapper 60s 返回超时后任务继续；随后读取同一日志确认 **BUILD SUCCESSFUL in 1m 2s**。日志：`build/agent-logs/20261019-112615-test.log`。没有重复启动测试。

最小复现脚本（产品源码未改）：`C:/Users/spdimdriver/AppData/Local/Temp/opencode/DanmakuAuditProbe.java`，执行：

```powershell
& C:/jdk21/bin/java.exe --class-path build/classes/java/main C:/Users/spdimdriver/AppData/Local/Temp/opencode/DanmakuAuditProbe.java
```

其内容调用真实时钟与 spatial 类，结果见 §3。临时脚本不随仓库分发；第二轮应将这些失败案例变成正式回归测试。

## 6. 第二轮实施顺序（待用户确认）

1. 修 A、D，并补能在旧实现失败的全链路回归。保留原始寿命400、数量、伤害与图案。
2. 用正式山女 card1/card3、至少两个以上周期的相同 seed/玩家/地形建立分项基线；先处理 E、F、G，比较 CPU/分配/求值次数与 p50/p95/p99，不仅看平均 TPS。无世界 JVM 基准必须明确不含真实世界碰撞，不能冒充实机。
3. 重建 C 的事实水位与 B 的呈现时间线：按已应用事实推进，一次读取一致的帧输入；保持纯函数分数时间求值，禁止重置每帧年龄或仅 lerp position。需包含暂停/恢复、订阅更替、清卡、出生/死亡、rebase/派生、激光和 presentation。
4. 优先评估小且有界的呈现缓冲，吸收正常 tick 抖动。**在实现前明确 swept interval、terminal effective time 与画面缓冲的相位关系。** 不能仅把现有时钟减一就交付；必要时给终止事实/最后轨迹定义有效时间，避免伤害已结算但出生帧被整个抹掉。任何缓冲的实际延迟/空间偏差要记录；服务器长停顿时不存在零延迟、绝对平滑、绝不外推三者兼得，冻结/恢复行为必须有可验证上界。
5. 若缓冲必须配套改变权威碰撞区间，先用“出生当 tick 即命中”“下一边界 rebase”“玩家移动穿过静止弹”等案例确认语义，再修改；不得为了好看扩碰撞箱、取消碰撞、缩弹量或自由跑显示时钟。远程路径要单独验证，不能从集成服结论外推。
6. 定向回归→完整 build→可执行的实机采样；不以单测通过宣布“完全解决”。不归档当前 migration change。

## 7. 验收与失败交接要求

| 项目 | 必须证明的结果 |
|---|---|
| 数据一致性 | AIM_LOCAL、partial mask、spatial subset、序列化往返后，同 ID 同时间 position/direction/phase 与 authority 一致 |
| 时序 | 40/50/60/100ms、不规则完成、批量交付、暂停恢复；记录真实时间单调性、边界速度突变、最大事实落差 |
| 生命周期 | 出生即命中、母死停派生、换卡、订阅改变、END/RELEASE 不复活、不越界求值 |
| 高负载 | 明确第三卡而非首卡；原400tick寿命跑至稳态；相同条件记录优化前后分项与p95/p99 |
| 碰撞 | 生存/非和平，静止与移动玩家、静止尾珠/高速母弹、方块遮挡；保存命中tick与显示时间证据 |
| 完整功能 | 激光隐藏/长度/预警、旋转与分段、存档恢复共享、多人订阅没有因缓存改变语义 |

第二轮在此追加：实际修改文件/动机；失败方案及撤回情况；每条缺陷修复状态；测试命令与日志；实机有无执行和环境；剩余风险；接手者可直接执行的下一步。保留本轮结论作为历史，后续推翻某条须注明证据，不静默改成“已解决”。

## 8. 第二轮实现记录

2026-10-19 第二轮已执行。用户授权修复，并特别要求关注未测符卡的公共问题。

### 8.1 实际修改

1. **A 已修复**：`ProgramInterest.spatial` 保留 `aimLocal`。继续追查后修复 `Actions.emittedState` 的跟随源求值漏传 aimLocal，以及 runtime/replica 绑定编队激光方向漏用 aimLocal。不是山女特判。
2. **E 已优化**：`DanmakuWorldRuntime` 对当前 tick 的不可变世界快照轨迹缓存整数边界状态；follow/group attachment/detached 轨迹绕过缓存，换 tick 清空，remove/replace 清除，rebase/attach 清除。派生阶段内近域计数先建立一次，install/remove/replace 增量维护；跟随源安装后失效；阶段结束销毁统计。保留原来的准入顺序、扣除 replacement 父弹、预算与拒绝语义。
3. **F 部分优化**：无 actions 的尾珠不再进入派生处理集合/触发额外迭代；碰撞细分复用已经算出的 from/until，仍保留中点/四分点及递归曲线检查；修复 `terminateInvalid` 对每个 encounter 又遍历全部 encounter 的嵌套调用（也避免多 owner grace 一 tick 被递减多次）。没有加入仅凭 Linear 类型跳过曲线的危险捷径。
4. **G 部分优化**：相同包络的 spatial 批次直接按共同包络选成员，省掉没有剔除收益的 shard 排序/分配。`ProgramMotionBounds.members(projection,groups)` 增加可复用入口；目前正式 NetworkHost 的函数接口仍调用单参数版本，因此**生产端重复 groups 计算尚未消除**，不要误记为已修复。
5. **B/C 新呈现方案已接入**：新增 `ProgramPresentationClock`；客户端不再用跨线程 `IntegratedProgramTimeline` 作为绘制时钟。新增有序 `BOUNDARY` 消息（枚举末尾），每个可见 owner 每 tick 在事实消息之后发送一次，包含 tick/revision、无坐标；无事实变化时照样推进水位。Replica 必须已有匹配 revision 的完整 Image 才确认边界；缺事实时等待/恢复。维度取可见 owner 的最小确认水位。
6. 新时钟初始目标为 `authority-1`，按帧积分，正常速率限制 0.9–1.1 tick/50ms；输出不超过已确认 tick；普通收包不会改写已显示姿态。耗尽确认窗口冻结。暂停保留分数相位，恢复首帧不消耗暂停墙钟时间。落后超过4tick时显式重锚并记 resets（这是已知不连续，不声称极端停顿仍连续）。同一 epoch 释放后同 tick 重入会重新发 boundary。
7. **D 部分修复**：probe 记录真正绘制的分数 time，按已选 MemberId 查找成员，找不到才重选；frame 间隔分母改 frames-1。新增 `[program-presentation] time=... confirmed=... freezes=... resets=...`。删除不再驱动渲染的 `[program-integrated-time]` 日志，避免被误读。尚未补齐分项性能/JFR、归一化速度、命中逐帧环形缓冲。

旧 IntegratedProgramTimeline 及 ProgramClock 保留作旧测试/诊断，但均不再决定生产 renderViews 的时间。真正入口是 `ProgramClientNetwork.renderViews -> replica.presentationTime -> ProgramPresentationClock`。

### 8.2 回归证据

- 先新增球/激光 AIM_LOCAL 的 spatial→publish→wire→receive 回归，在旧实现上失败于 aimLocal 丢失。日志 `build/agent-logs/20261019-114154-test.log`，1 test failed。修复后通过。
- `ProgramNetworkTest.presentationWaitsForOrderedFactsAndAdvancesAcrossUnchangedImages`：无变化 Image 仍推进确认；故意先送 boundary、后送删除事实，确认时间不会越过旧水位，事实补齐后恢复。
- `ProgramPresentationClockTest`：8ms 渲染步长、交替42/58ms边界；收包同一时刻不瞬跳、时间单调、每帧位移时间有上界、断更不超 authority、暂停恢复不倒退。此测试不等于所有真实帧率和100ms停顿都已实测。
- `ProgramAdmissionWorkTest`：100母弹同tick独立派生，父路径求值≤200（边界与终点各一次）；nearLimit=150时仍准确拒绝50次，nearLimit=250时全部接受。该证据是算法调用次数，不是 Minecraft 毫秒/TPS。
- 原 `yamameNestTailPearlsEndBeforeTheNextCycle` 改成真正装第三卡、确认 cardIndex=3，原寿命400，跑到519tick，断言峰值>3000并保留跨轮尾珠；测试名改为 `yamameNestRetainsAuthoredTailPearlsAcrossCycles`。
- 新增 **全部15张正式符卡×5目标槽** 的 `everyFormalCardPreservesFractionalPoseThroughSpatialWireProjection`：每20tick采样，至240tick，分数相位0/.375/.875，检查经过 spatial 和 wire image 往返后的 position；激光另检查 direction。全部通过。世界为 SurfaceWorld 测试替身，没有真实方块/实体碰撞；并非每张卡完整生命周期或多人实机验收。
- 定向日志：`114437-test.log`（网络/时钟/operations/source）、`114654-test.log`（准入/第三卡/碰撞扫掠）、`114837-test.log`（全部正式卡空间链路）。
- 最终完整 `tools/gradle_task.ps1 build -TimeoutSec 60`：`build/agent-logs/20261019-114939-build.log`，**BUILD SUCCESSFUL in 42s**，编译、全部测试、打包内容守卫通过。
- Python XML 汇总最终 `build/test-results/test/TEST-*.xml`：**145 suites，1128 tests，0 failures，0 errors，0 skipped**。最初 PowerShell 默认编码解析中文 XML 报错，那个计数作废，以 Python 汇总为准。
- 产物：`build/libs/gensokyou-1.0.0.jar`。修改尚未 git commit。测试导致已有受跟踪 `logs/debug*.log*`/latest 日志轮转，保留不清理；原 `gradle.properties` 用户修改保留。

### 8.3 必须诚实保留的限制

**这次交付是有自动化证据的修复尝试，不是实机问题全部关闭。未启动客户端，未进行新的生存模式命中验收、真实远程多人、保存退出重进、GPU帧时或JFR性能采样。不能声称TPS已经恢复20，不能声称所有幽灵命中已消失。**

1. 显示采用约一 tick 的安全缓冲，但服务端碰撞仍提前扫描 `[N,N+1]`。因此渲染与实际伤害时刻仍有相位差；0.70格/tick的母弹，一个tick差即约0.70格。出生当tick命中后不进碰撞后Image的问题**没有修**。没有为了画面把伤害延后、扩大判定或取消碰撞。下一位若仍观察到幽灵命中，优先测这条，不应继续只改时钟参数。
2. `BOUNDARY` 避免客户端读到未完整交付的事实时间，但 history 仍保存离散的碰撞后 Image；没有增加命中点/终止分数时刻，END/RELEASE仍立即清空。因此复杂终止过渡不是完整连续世界回放。
3. 长停顿确认窗口耗尽会冻结；>4tick落后显式跳转，resets计数可见；小缓冲不能吞掉任意长抖动。速率微调也可能仍有可感知速度变化，须实机观察。
4. 新网络 Kind 需要两端更新同一构建。旧客户端不认识 BOUNDARY，不能混用。本轮没有变更外层 payload 注册版本字符串。
5. 每可见owner每tick一条小边界消息，换来精确无变化时间水位；完整带宽/scaling现有测试通过，但真实多boss带宽未测。
6. stable seed direction/Frame 的跨次求值缓存、consumed trigger索引、静态batch投影缓存、真实空间索引仍未实现。不能把列出的全部性能问题标“解决”。
7. 新边界状态缓存依赖 plan 轨迹不可变（符合现有Path契约）。新增外部依赖路径时不得直接藏世界输入进 Path；当前follow/group/rebase走绕过缓存分支。修改准入期动作时要维护增量统计失效约束。

### 8.4 接手者直接执行

1. 读本节→核对工作树→使用最终jar重启客户端。确认 `bossDanmakuBackend="program"`。生存/非和平运行山女首卡、第三卡、蓝瞄准卡与移动/旋转激光。
2. `/gs_program_probe start` 后分别采集开局、原400tick尾珠稳态；`/gs_program_probe stop` 保存摘要。查 `[program-presentation]`：真实time、confirmed、freezes、resets；不要再用已废弃的integrated时间日志判定。
3. 画面测试可用现有 `/gs_program_preview start formal_yamame`、`formal_big_fairy`、`formal_yakumo_ran` 和 `rotating_laser`/`moving_carriers`/`dynamic_warning`；`/gs_program_preview stop` 结束。预览不是正式生存命中验收。
4. 若仍幽灵命中，加入同MemberId的“碰撞区间/命中时刻/终止事实/客户端真实renderTime”环形采样，优先验证 §8.3.1 的区间相位差，再决定是否需要权威碰撞与生命周期契约重构。不要拿buffer平滑代替这项。
5. 若TPS仍低，profile `admission/collision/capture/spatial/encode`，重点查真正方块查询与全量投影；以相同seed/玩家位置/活弹数比较，记录p95/p99。当前只证明调用次数降低，不具备前后实机时间对照。
6. 完成后追加实机结果，不归档 migration change，直到未完成验收矩阵确实关闭。
