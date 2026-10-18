# 正式符卡迁移：运行时专项与跑测交接

更新：2026-10-18，本会话结束时的权威状态。**先读本文件，再读历史manifest/交接；历史“等价完成”“实机未执行”不是最新验收结论。**

## 1. 最新用户结论与下一会话目标

用户最终反馈：**新客户端不再明显掉速，但黑谷山女第一卡弹幕仍明显抖动，运动不平滑。尚未解决。**

环境明确为**单人单机/集成服务器**。不要按远程网络延迟排查，不要把日志中的远程ProgramClock拟合值当作当前单人渲染时间。用户要求结束本轮代码修改，在新会话专项解决；**不再让DeepSeek修改**。本轮最终只写交接，不再改Java。

下一会话优先：复现山女首卡→收集逐帧时间/位置与服务端tick耗时→区分呈现时钟跳变、服务端tick波动、网络事实切换、母弹/子弹路径不连续。不要再次只改校准参数、只做理想50ms模拟，然后让用户重启碰运气。

change `migrate-boss-cards-to-programs` **未全部验收、不归档**。未提交git；大量实现及测试仍是untracked，必须保留。会话起点proposal.md/tasks.md已有用户修改，禁止覆盖。日志也有跟踪/未跟踪变化，不能当废文件批量删除。

## 2. 工作摘要与范围

用户原目标：让OpenCode Go DeepSeek V4.1 Flash default独立迁移，以评估文档能否指导较低经验作者。开发会话`ses_eb26da9c3ffeD2qFLPWCA7kK50`。经四轮内容交付和多轮审查后实现：

- 大妖精3卡+5×5默认墙、山女4卡+底噪、蓝8卡（无底噪），合计15卡+2底噪。
- 露米娅/傩面5卡按用户明确排除，不为凑旧20卡表扩大范围。
- 正式实体接血量选卡、HUD、移动锁、后端选择与生命周期；默认config仍legacy，测试需`bossDanmakuBackend="program"`。本地`run/config/gensokyou-common.toml`由用户已改program，以实际文件为准。
- 作者API补真实逐弹目标快照瞄准`aimAtTarget`、倾斜面板、球弹穿墙、走廊检查、地形采样发射和逐拍随机地形发射。
- 修过绝对时间表编译、句柄时序校验、循环尾弹/跨边界预警等框架问题。
- 正式BOSS与预览共用维度运行时，各自encounter隔离；预览停止不结束BOSS，预览不持久化。
- BOSS后端/卡号NBT与程序运行态磁盘存档接线；恢复不重复装卡/底噪。

后续多项问题由观察者直接修复，不能称为DeepSeek独立完成。完整事实记录：[观察记录](../../../docs/boss-program-migration-observation.md)。结论不是单纯文档不足：入口/框架缺陷、作者忽略已说明语义、局部测试过弱、观察者过早认可都存在。

## 3. 抖动与幽灵命中的修复历史（避免重走）

先后尝试，均不能据历史测试通过当实机稳定：

1. `server.gameTime + client.partialTick`：不同线程相位直接拼接，会回退。
2. max(last,time)：防倒退却变成停顿。
3. clientTick+partialTick固定映射：仍有实机不平滑。
4. 自由实时时钟累加：仅限制领先、不限制落后，服务器追赶后可能造成视觉远、判伤已到的“幽灵命中”。已删除该`IntegratedProgramTime`类及其测试。
5. **当前**`IntegratedProgramTimeline`：服务端完成advance后发布(tick,nanos)，客户端用边界内[0,1]tick插值。直接采用每次完成时间仍抖，因此改为预计50ms边界、每tick最大0.5ms慢校准，误差>150ms或tick跳跃>4重锚。**用户确认当前仍抖，不要将此方案视为最终正确设计。**

当前文件与链路：

- `gameplay/IntegratedProgramTimeline.java`：静态跨线程不可变Boundary；`time`硬夹到`tick..tick+1`；publish慢校准。
- `server/ProgramServerHost.java`：LevelTick.Post，session.advance后发布边界，再网络publish。
- `client/ProgramClientNetwork.java`：单人直接取上述boundary，远程才用replica.renderTime；日志`program-integrated-time`才是单人使用的时间。
- `network/ProgramReplica.imageAt`：按呈现时间选history，再ProgramRenderView/ReplicaState求值。
- `ProgramCollisionAdapter`：权威tick..tick+1扫掠；呈现不得任意滞后以“换平滑”。

山女首卡具体内容：`content/YamamePrograms.web()`：200tick循环，10拍×6母弹，母弹线性速度.70、寿命100、穿方块；每母弹age4..96每4tick派生尾珠（24次）；尾珠Hold40后稳定随机方向.06漂移，寿命400。另有个人扇与底噪。先区分用户看到抖动的是快速母弹、尾珠漂移还是全场所有运动，不要先改符卡参数掩盖框架问题。

已有测试的边界：

- `IntegratedProgramTimelineTest`覆盖理想帧、追赶和±8ms交替完成时间；**不足以保证单调匀速、真实帧下稳定**，仍可能有边界硬夹、相位重锚造成跳变。
- `ProgramContentTest.yamameWebAuthorityAndReplicaAgreeForCarriersAndDerivedPearls`与大妖精parity测试：同时间两端位置一致，不代表实际使用同一时间、网络history切换正确或实机帧间平滑。
- 用户此前报告幽灵命中；修时钟后没有充分明确的生存命中验收记录，仍需复验，不能视为已解决。

建议采样同一稳定MemberId逐帧renderTime/position/velocity、选中image tick/revision、authority tick与实际纳秒间隔，同时记录服务端advance、网络projection/encode与客户端projection/submit耗时。用数据确认症状，不继续靠感觉调时钟。

## 4. 读档后掉速与存档：最新状态

真实日志曾显示山女读档后服务端约3–4TPS；另有`program snapshot exceeds disk budget`。根因路径：disk codec逐成员写整份EmissionPlan/ChildProgram，解码成不同对象，rebase又逐成员复制；网络描述hash按对象identity缓存，恢复破坏共享会放大编码工作。不是单纯时钟冻结。

最新修复：`server/ProgramSessionCodec` **v2**用有界记录字典去重；兼容v1读取并intern重复record；组内同batch解码与rebase保留plan共享；不提高8MB容量。新增山女首卡160tick真实负载测试，文件<2MB、同batch assertSame(plan)。用户最终确认“不会掉速了”，但还没确认完整保存/重进/切卡等所有项目。

相关文件：`ProgramSessionCodec`、`ProgramSessionStore`、`BossProgramHost`、`BossProgramController.resumed`、`BossProgramBattle.adopt`、`AbstractTouhouBoss`。

- 每维度SavedData，flush时live Capture；NeoForge的LevelEvent.Save晚于SavedData落盘，**不要改回Save事件才setDirty**。
- persistent bind必须attach回调；每tick标dirty，不能只看network revision。
- 预览`openEphemeral`，保存过滤persistent encounter。
- owner暂未加载有600tick宽限；其时间是否应推进/计划是否可能错过、区块卸载后恢复需要继续检查。
- 已结束战斗不得复活；恢复后controller要恢复card计数与backgroundInstalled。
- 编码失败是否保留旧pending、玩家死亡/切维度、孤儿owner、异常数据兼容均待实机/完善回归。

当前日志随重启轮转；必要证据可从`run/logs`历史日志找19:33读档掉速、19:20快照超限。不要覆盖原证据或把旧日志当新版本结果。

## 5. 剩余逐卡跑测与修复清单

所有项均需记录所用代码版本/日志/指令/模式；测试通过不自动代表实机通过。使用生存、非和平验证命中；创造不入目标且免伤，不能用作伤害验收。

| 范围 | 要跑测/复验的重点 | 当前状态 |
|---|---|---|
| 公共运动 | 山女首卡母弹/尾珠、直线/分段/旋转、暂停恢复、tick波动、稳定帧间位移、与判伤同位 | **首要未解决：用户确认明显抖动** |
| 公共生命周期 | 切卡清旧卡、底噪保留、死亡/删除归零、循环尾弹和跨轮预警、无重复host | 自动化部分覆盖，实机待系统确认 |
| 存档 | 首次保存、退出重进、活弹续飞/剩余寿命、预警→激活、派生、恢复后切卡、不重复底噪、预览不恢复、多owner/卸载 | 代码已补；掉速改善，完整矩阵未验收 |
| 大妖精默认墙 | 后一拍不能清前一拍，config尺寸/速度、数量与寿命 | END误用已改RETAIN，需实机复验 |
| 大妖精1花环 | 48/人共圆、60tick重瞄、120锁位、换轮尾弹、视觉判伤一致 | 自动化有，仍需幽灵命中复验 |
| 大妖精2花海 | 16×46、随机平面、100age释放、花心4倍、尾弹/跨轮、地形与命中 | 用户未给完整通过，待跑 |
| 大妖精3雨季 | 每秒24个新随机点、半径30/上方60、每点1弹、每轮重采样；光柱随机点/每第五拍脚下、2秒预警+6秒激活、10道全激活、顶棚/地面裁剪 | 固定点弹串/中心光柱/净距拒绝/跨轮最后2道取消已修，**尚无最终用户通过** |
| 山女1捕获之网 | 快速母弹、尾珠hold40→漂移、24次派生、母死停发、多目标扇、穿墙 | **用户跑测运动不平滑**；chase源码只slot0需对mapping核对 |
| 山女2瘴气 | 螺旋呼吸、慢雾、随机散射、循环尾弹、密度与视觉身份 | 待实机跑测 |
| 山女3巢 | 13拍/91根与派生、密度上升、多目标、地形 | 待实机跑测 |
| 山女4热病 | 复合并发、运动、密度/派生峰值、命中 | 待实机跑测 |
| 山女底噪 | 朝向语义、config/旧契约、跨轮寿命（源码仍END，需核对是否同大妖精误清） | 待核对/跑测 |
| 蓝1/8 | 真目标距离收敛、5目标槽、锁定快照、副面板12×6=72的几何/节奏 | 自动化有，实机未验收 |
| 蓝2/7 | 公共面板、预警、白点名逐人，公共不能按人复制 | 待实机 |
| 蓝3 | 倾斜交叉、白点名多人、解法 | 待实机 |
| 蓝4 | 走廊被堵撤批、物理断口、方块裁剪长度、球/激光时序 | 待实机 |
| 蓝5 | 停批再出发、age30闪亮与音效、隐藏/判伤无混淆 | 待实机 |
| 蓝6 | 局部U轴弧线、轨迹预告、偏高低目标、末端回F | 待实机；曾误把localY称worldY，勿重复误判 |
| 蓝全卡 | 220停止判伤/10tick淡出/230清完/240新轮、无底噪、下标/HUD | 自动化有，实机待验收 |
| 多人/远程 | 2/5人目标槽/数量、公共一份；高延迟/晚加入/断线；勿把单人结论外推 | 本轮未执行真实多人矩阵 |

权威需求对照：旧`danmaku/track/BossCards.java`、`openspec/changes/refactor-danmaku-program-runtime/content-mapping.md`和本change spec；发现冲突要明确登记来源，不因现有源码或manifest自称“等价”就放弃核对。不得未经授权删功能、缩规模、固定值替代随机/世界输入或提高预算来凑绿。

## 6. 构建与证据

所有Gradle调用必须走`tools/gradle_task.ps1`，禁止裸gradlew。按AGENTS.md处理Windows流式输出，不用Out-String/Select-Object -First截断构建。

最新完整build：`build/agent-logs/20261018-193945-build.log`，**BUILD SUCCESSFUL**（包含codec v2/v1去重与恢复plan共享；其后本轮只加文档）。

其他定向证据：`193618-test.log` codec负载；`192326-test.log`山女同时间parity/时钟；`190810-test.log`大妖精parity/碰撞/时钟；`170011-test.log`雨季逐拍随机/跨轮10激光。这些不等于未执行的实机验收。

历史lang审计有31个既有造化JEI派生键缺失，不能报零缺失；strict历史通过，未来代码/spec改后按需重跑。当前tasks的1.2/1.3/1.6勾选是实现状态，不是上述实机问题已关闭；1.7保持未完成。历史task1.6仍提Save事件是旧文字，以上述flush时Capture说明及实际代码为准。

## 7. 新会话可直接使用的任务说明

> 请先阅读本交接与AGENTS.md、boss/neoforge skill。本轮由你直接专项排查单人程序弹幕运动，不委派DeepSeek。最新用户确认掉速已改善，但山女第一卡仍明显抖动。先做可观测复现与逐帧时序/位置诊断，再修公共框架；避免继续以理想时钟测试代替实机。保持服务端判伤与渲染一致，不以视觉延迟/取消碰撞掩盖问题。随后按本文件逐卡清单跑测与修复，持久化也需真实退出重进验证。保留全部未提交改动及用户原proposal/tasks工作，不归档change，未验证项诚实记录。
