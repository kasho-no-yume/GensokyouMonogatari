# 第五期交接：`add-danmaku-program-gameplay`

> **当前状态已修订，优先读next-session-handoff.md顶部。** 按用户明确授权2.1–2.6已勾选（任务范围同步修订，非伪称旧M2通过），新增3.1–3.4集中高密度复杂压力验收。当前10/16。原精确A/B/54项/三次重复/真实1/2/5人矩阵转后续专项，不再用它阻塞已确认代表切片。下文为历史。

> **最终用户反馈优先：petal_release、roaming_tower均已测试通过表现，高延迟整批闪烁仍然严重、未解决。** 本次仅更新记录与交接，不继续改代码。新会话直接读[next-session-handoff.md](next-session-handoff.md)，它已完整重写为当前状态、代码地图、验证证据和剩余工作。下文“下一轮观察两个新变体”“无需复验”等均为历史，不能理解成两个新变体未测或闪烁已通过。主任务仍5/12，2.3–2.5具体表现证据已更新，真实多人与M2缺证据故未整项勾选/归档。

> 19点续接最新状态：用户已在单人及500ms独立服确认上一批dynamic_warning/high_frequency_circle/sphere_split/moving_carriers/moving_tornado表现性能。无需重复验收。偶发整批闪烁日志实际为display-error（采样误差2.04–2.88tick），ProgramClock补≤4tick采样误差时最多1tick冻结呈现grace，严重积压撤销，原运动holdover不放宽；用户明确无需复测。新petal_release释放3整瓣27成员，roaming_tower以Bezier巡游并三个顶层节点8轮发环，共24批192子弹，定向测试20261017-190934-test.log通过。下一轮仅观察这两个新变体，acceptance.md有命令与通过条件；M2等负载与真实多人仍欠，不能归档。完整build 20261017-191050-build.log通过2m15s；191221因我误启动并行build争用output.bin失败，随后串行最终构建另见最新日志。

> **新会话先读 [next-session-handoff.md](next-session-handoff.md)。** 最新四项partial_release/motion_lab/moving_tornado/rotating_laser已由用户全部实机确认通过；下一步补动态父组预警、高频法阵及延迟/M2工具。本文后续是历史记录。

更新时间：2026-10-17

> 本批继续补moving_tornado、rotating_laser基础预览，ProgramMotionLabTest覆盖48成员wire/包络、激光预警age0姿态与出生一致、光束轴不取公转切线。EmissionPlan新增beamDirection，server/renderer的snapshot/attached laser使用显式轴；follow/detached laser尚需方向契约，不宣称完整。集中验收四项见acceptance.md；用户已明确不希望每个修补单独停下来验收，后续继续批量推进。最近定向测试20261017-173036-test.log通过，最终完整构建另记录。

> 后续推进：用户确认新预览伤害修复通过；新增motion_lab正式预览和30/60/144FPS/镜像/错拍出生/typed wire测试，外部移动源逐拍快照/丢失停止/子代保留测试，以及远端共享组时钟跨birth重合、单成员移除、网络恢复测试。新增ProgramTimingWindow固定2048最近样本p50/p95/max，server host及client receive/projection/geometryAndSubmit均接诊断，均非整server tick/GPU或独立每卡A/B。新预览与集中验收见acceptance.md；未完成旋转激光、整瓣脱离、移动龙卷风、真实多人延迟与M2，不能勾选2.3–2.6。

> 17:09续接：用户确认三个新预览视觉无问题，但均不伤害/不接触消解。原因是新fixture误配空damageWindows（日志collisionSamples=0），已补明确全寿命伤害窗口，保持空窗口=无害的通用契约。新增每代碰撞派发和runtime删除→replica同步测试；真实扣血需复验。继续新增partial_release及轨迹、包络、客户端一致测试（偶数稳定成员，不是偶数整瓣）。定向测试通过20261017-170818-test.log。集中实机操作见acceptance.md；2.3/2.4/2.5和M2仍未完成。

> 最新状态：用户确认花海与尾珠性能/表现通过，后续尾珠漂浮期闪烁也已复验“没有明显问题”。2.2完成；本文下述失败描述是历史证据，不能当作最新结论。其他代表组合、实机多人延迟矩阵、完整诊断和M2量化验收仍待完成。

> **最高优先级交接（用户最新反馈后）：花海仍卡顿、瞬移、画面/判伤错位，之前修复不代表解决。请先读[架构排障交接](architecture-debug-handoff.md)。最新日志证明显示时间落后自身估计约54tick却仍trusted；停止新增内容和局部补丁，先审计时钟/端到端负载/状态时间域。本文后续历史“修复”记录只表示当时改动与测试，不表示实机通过。**

## 花海卡顿/瞬移/画面判伤偏差再次反馈（13:15实机）

用户仍报告花海卡顿、瞬移及幽灵弹；日志全轮702tick有22.5MB checkpoint、4次恢复，前次修复并未解决全部问题。本轮确认并修复：

- `ProgramPreviewBounds`此前到age100才把包络扩大到Rebased全寿命，导致每花爆散时订阅改变/重发checkpoint；花海fixture从birth预留102格保守半径（7.2初始推进+3花瓣偏移+90后续飞行+余量），不再在重瞄时扩大。成员bounds保留死亡模板槽，避免缺槽触发整批fallback。
- `ProgramReplica`同epoch订阅切换收到首片就清空image，造成画面消失、时钟空闲重置；现在保留完整旧image直到新checkpoint完整安装，新epoch仍清空。ProgramNetworkTest新增分块中保留image与clock的回归。
- 客户端ClientTick计数原来单人暂停仍推进，导致恢复游戏后显示时间超前判伤；ProgramClientNetwork在Minecraft.isPaused时停止推进并禁用partial tick推进。已从MC源码核对isPaused签名。
- ProgramClientNetwork仅在有活跃副本时每100tick记录program-client时钟误差/可信性/失败/迟到/传输数。

延长测试抓到寿命结束后仍存活空组的interestChange误判：组包络消失但组record存在会发checkpoint。ProgramSyncServer现在只把仍有Batch的组离开范围算兴趣切换，空组生命周期消失走DECISIONS。修复后420tick零checkpoint断言、renderViews位置与authority位置、ProgramNetworkTest全部通过：`20261017-133119-test.log`。最终重跑build：`20261017-133157-build.log`；先前`132806`构建的零checkpoint断言失败已修正，不能记为通过。

新增花海连续发布测试使用非原点倾斜Frame与移动观察者，比较真实renderViews小数tick位置和authority位置，零恢复/零额外checkpoint；延长到420tick覆盖两轮重叠并加入碰撞删除。`20261017-132651-test.log`定向测试通过，最终延长版全量build日志`20261017-132806-build.log`。这些自动化未复现所有用户观感，不宣称实机偏差已完全解决；仍需观察新增clockError日志和复验，1.5/2.2/M2不勾选。

## 第二次启动无画面修复（13:00–13:03实机日志）

用户反馈第二次预览客户端没有弹幕但仍判伤。日志确认新owner与状态持续发布、第二次尾珠没有恢复请求。根因是维度共享ProgramClock在End/Release后仍保留，空闲时陈旧冻结，新预览沿用旧显示时间且每tick最多0.05校正，整轮可能仍落在新成员birth之前；不是服务端没发射。ProgramReplica在维度无任何已安装image时释放旧clock，End/Release立即处理，首次新image安装前再次检查；同维度其他活跃攻击存在时保留共享平滑时钟。

`secondPreviewAfterIdleReanchorsClockAndRendersNewBirths`覆盖End/Release两路径、空闲近2000tick、同连接第二epoch重新安装、renderTime重新锚定和首批6发可见。定向测试含ProgramClockTest和ProgramNetworkTest通过：`build/agent-logs/20261017-130423-test.log`。完整build日志：`build/agent-logs/20261017-130519-build.log`。仍待用户复验连续预览。

## 尾珠实机崩溃修复（12:35报告）

`run/crash-reports/crash-2026-10-17_12.35.07-server.txt`确认服务端在`ProgramSyncServer.publish`生成CLOCK_DIGEST时调用`wire.image(next)`，完整投影超过默认4MiB编码上限，抛`IOException: wire byte budget`并终止服务器。实机运行347tick，先前60tick连续发布测试未覆盖该峰值；这不是OOM。保持成员数、寿命和协议上限不变，为ProgramWire的Actions.Pose、Deterministic.Seed、Pattern.Member新增有界紧凑二进制字段编码，去除每尾珠批次重复的反射字段路径字符串。接收端保留record fallback，新增编码沿现有统一封套分块；客户端/服务端需一起重新构建。

`continuousDerivedPublicationUsesDecisionsWithoutCheckpointStormOrGhosts`改用实机4096成员上限，发布到420tick（原60tick），逐tick验证零恢复、零额外checkpoint和客户端可见数量一致；tick360另做完整image字节上限及独立晚加入恢复验证。首轮420tick与ProgramNetworkTest通过：`build/agent-logs/20261017-123729-test.log`。完整build含新增tick360晚加入测试通过：`build/agent-logs/20261017-123913-build.log`（1m55s）。随后补发送侧tag数量上限与解码侧一致，再跑`20261017-124104-build.log`。仍待修复后实机复验，不标性能门槛通过。

## 实机性能/幽灵弹反馈修复（2026-10-17）

用户反馈`tail_pearls`/`flower_sea`严重卡顿与幽灵弹。`run/logs/latest.log`显示尾珠约281–288tick累计110–118MB checkpoint、18–24次恢复请求；花海272tick约20MB checkpoint。此前自动化只覆盖单次晚加入，未覆盖逐tick实际发布，漏掉空间订阅签名随出生/死亡变化引发的checkpoint风暴。

修复：`ProgramSyncServer.publishSpatial`只在同一仍存活成员的兴趣选择变化时重置订阅；正常新批次、死亡和新组改走DECISIONS。`ProgramWire`有界缓存不可变ChildProgram身份的描述hash，`ReplicaState.capture`按批预索引存活/伤害成员，避免逐批扫描全组。客户端求值复用`EmissionPlan.state`静态解析入口，避免每帧每弹构造/校验整个模板列表。Rebased未到生效tick时保留原计划求值，避免负年龄。`ProgramRenderView`过滤解析结束和fade截止的成员。`ProgramCollisionAdapter`增加普通弹的方块射线裁剪和撞块移除（此前普通弹缺失撞块消解）；当前为弹心沿扫掠子段的方块裁剪，尚非半径体积扫掠。

新增`continuousDerivedPublicationUsesDecisionsWithoutCheckpointStormOrGhosts`覆盖逐tick尾珠发布、权威删除、成员可见数量、零恢复请求、零额外checkpoint和End清空。此前完整build通过`20261017-121900-build.log`，后续Rebased边界修复另跑最终build。修复仍待用户实机复验，不据此宣称FPS/CPU或M2门槛已通过；碰撞查询缓存暂未实施，主要根因优先由网络修复解决。

## 续接进度（花海与尾珠切片）

本轮继续实现 2.2，仍未将任务勾选为完成。新增正式 runtime 预览：

- `ProgramPreviewPrograms.tailPearls()` 与命令 `start tail_pearls`：20 个母弹批次，每批 6 个母弹；母弹年龄 4、8、…、96 每 4 tick派生一枚尾珠。尾珠使用稳定种子方向，先 `Hold` 40 tick，再以 0.06 格/tick 漂移，寿命 400；母弹被移除后尚未发生的尾珠不再生成，已出生尾珠继续存活。
- `ProgramPreviewPrograms.flowerSea()` 与命令 `start flower_sea`：两轮 240 tick，每轮 16 朵、每朵 46 成员。花心/花瓣共享出生平移，花心使用 4 倍伤害样式；age100 经 `RadialRelease` 以爆散时花心为共同边界，花瓣径向以 0.2 速度脱离；花心经 `RebaseTarget(slot=-1)` 在当时有效玩家中一次性采样目标点，以 0.3 速度直飞，不持续读取玩家位置。
- 新增 `ServerControlOperations.RebaseTarget`、`RadialRelease` 及运行时随机目标采样；操作仍进入 typed codec、执行注册和 checkpoint/replay 路径。花心缺失时径向释放安全取消所属组。
- `ProgramPreviewBounds` 同时考虑成员 transform 与 `Rebased` 剩余路径，花海跨轮和重瞄后的兴趣包络不会退化为 BOSS 点位。

新增自动化证据在 `ProgramBlue1Test`：

- 花海共 1472 名义成员、32 次一次性目标采样；age100 前无害；核心伤害倍率为 4；花瓣从同一爆散花心径向脱离；tick240 旧轮与新轮并存；晚加入网络 replica 与服务端位置一致；包络覆盖全寿命轨迹。
- 尾珠共 2880 个派生提交；母弹移除后未来派生停止、已出生子代保留；容量不足时批次拒绝；Hold40 到漂移边界稳定；checkpoint/网络恢复位置一致。

本轮验证：

```text
tools/gradle_task.ps1 test -Filter 'ProgramBlue1Test|ProgramPreview|error:|BUILD|FAILED'
tools/gradle_task.ps1 build -Filter 'error:|FAILED|BUILD'
openspec validate add-danmaku-program-gameplay --strict
git diff --check -- src openspec/changes/add-danmaku-program-gameplay .opencode/skills/boss-dev-design/references/danmaku-operations.md
```

均通过；最近日志为 `build/agent-logs/20261017-115129-test.log`、`build/agent-logs/20261017-115428-build.log`。`python tools/lang_audit.py` 仍只报仓库既有 31 个 `jei.gensokyou.recipe.zaohua_*` 数据派生中文键缺失。本轮证据仍是自动化/runtime证据，未覆盖新预览的客户端实机、多玩家延迟矩阵、frame-time A/B 或 M2，因此 1.5、2.2–2.7 保持未勾选。

## 续接进度（本轮）

本轮继续第五期，未宣称任何未完成任务通过。为 1.5 的诊断接线补充了可复现的服务端运行统计：

- `ProgramServerHost` 为每个显式 session 记录 authority tick 次数、累计/最大 `System.nanoTime`、事件总数和 `DanmakuWorldRuntime.Kind` 分类；通过 `ProgramServerHost.stats(level)` 读取。
- `ProgramGameplayStats.Snapshot` 保留原五参数构造兼容性，并增加 tick 耗时、事件分类等字段；事件分类可用于提交、拒绝、取消、移除、结束和派生行为的报告分解。
- `ProgramPreviewHost stats` 与停止日志现在同时输出 runtime、gameplay、server 和 network 统计；停止前先保存 server/network 快照，避免卸载 host 后丢失结束前数据。
- `ProgramGameplayDiagnostics` 增加结构化 `ProgramServerHost.Stats` 字段，同时保留原 capture 重载。
- `ProgramGameplayDiagnosticsTest` 增加事件分类与累计/最大耗时测试。

本轮验证：

```text
tools/gradle_task.ps1 test -Filter 'ProgramGameplay|ProgramPreview|BUILD|FAILED'
tools/gradle_task.ps1 build -Filter 'error:|FAILED|SUCCESSFUL|BUILD'
openspec validate add-danmaku-program-gameplay --strict
git diff --check -- src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramGameplayStats.java src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramGameplayDiagnostics.java src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramPreviewHost.java src/main/java/com/bitsson/gensokyou/danmaku/program/server/ProgramServerHost.java src/test/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramGameplayDiagnosticsTest.java
```

结果均通过，日志为 `build/agent-logs/20261017-110823-test.log`、`build/agent-logs/20261017-111137-build.log`。这些是自动化和结构化诊断证据，尚不等同于 1.5 要求的完整恢复/CPU/逻辑与线上字节报告，也不替代 2.1–2.7 的实机矩阵和 M2 A/B 门槛。`tasks.md` 保持 1.5、2.1–2.7 未勾选。

## 续接进度（2026-10-17，正式预览首切片）

最新实机确认：用户在第六轮修复后回复“ok，验证完成”，记录为本轮blue4激光激活/预警颜色/源法阵修复实机复验通过。该确认不扩展为花海、尾珠、组合案例、多人延迟或M2性能验收；整个change尚未完成，下一实施项应继续本change的1.5及2.2–2.7。

第六轮反馈：blue4只有亮白预警、没有激活源法阵。实机日志实际为激光REJECT而非提交（3次COMMIT均为球弹），最初对零长度事实的推测已纠正，仍尊重真实零长度。查明预览宿主统一距玩家12格，而blue4最近玩家的节点沿F前移10格，因此节点距玩家约2格违反minClearance6，激光整批被拒绝。blue4宿主改为22格，不降低安全门槛；ProgramBlue1Test补12格拒绝/22格激活回归。新增REJECT/CORRIDOR逐条原因日志。预警改同正常激光的写深度材质，纯红较低alpha，并显示红色源法阵；激活零长度只隐藏光束，源法阵仍绘制。修复后尚待实机复验。

第五轮实机反馈修复：blue1运动漂移后拉回，blue4激光与正常样式不一致。

- `ProgramClock`原先在observe/拟合更新后直接max到新估计值，造成前跳或停住等待。现observe先结算旧映射到收包边界，后续连续推进，时差校正限于每本地tick±0.05；同一local time不会因收包改变已显示时间，陈旧仍冻结，恢复不瞬移。ProgramClockTest覆盖带抖动观测的连续性和陈旧恢复。
- 正常`LaserDanmakuRenderer.emitBeam`抽为实体无关静态六面光束几何，程序激光复用。程序激光增加正常激光相同的1.9倍外辉光/70alpha、主体235alpha、.45倍75%白混合亮核、2.2倍反色旋转法阵、双端盖与3tick开火收束包络；按Style颜色绘制，长度仍使用服务端裁剪事实。预警同样使用六面细红线。
- 完整build/test通过：`build/agent-logs/20261017-015259-build.log`（wrapper 55秒返回后后台于66秒成功结束）。修复后待实机复验。

第四轮续接：用户确认上一轮样式/判定实测完成，继续实施代表卡。

本轮最终补齐蓝6转弯前age20..30的弧线提示，直接采样同一未来解析路径；220后的10tick透明度淡出；激光碰撞适配器记录服务端实际裁剪长度并通过Beam事实进入网络投影和render view（支持零长度，测试覆盖）。这些是代码/自动化证据，动态方块实机仍待验证。最终完整build/test通过：`build/agent-logs/20261017-014307-build.log`。

- 新增`/gs_program_preview start blue1`（可加`star`）：完整240tick单轮，三次目标捕获、最多5个固定UUID槽、左右±2偏移源、每源锁轴、20/28/36/44四拍，108发/人；164解锁、220关伤害并10tick淡出、240结束。
- 新增`start blue4`：两次共享帧，球弹54发、激光8道，AxisNodes在局部F的6/10格，两侧实际起点±1.5R向外；激光半径.18、物理净宽2.64。激活前以局部坐标解析世界走廊检查，失败撤对应激光组；预警按每个实际模板点绘制。
- 新增`start blue6`：共享锁定帧，两侧±4面板，96发，直线30→局部U轴圆弧30→直线尾段，整片偏移保持，不沿切向旋转面板。
- `ServerControlOperations.OffsetGroup`/runtime.offsetGroup提供共享帧的偏移子组（父闭包、固定目标槽、半径继承），Corridor增加显式local标记，旧构造继续表示世界坐标。
- `ProgramExecution`缓存已验证不可变定义，避免逐tick重复解码整份定义；弱引用限制缓存持有。
- 全卡定义较大：预览双端ProgramLimits显式256KiB；sync编码按NetworkLimits.snapshotBytes有界，而非硬编码64KiB。发射重复已用Repeat压缩，但五目标typed定义仍较大，M2必须记录定义字节，不宣称已满足流量门槛。
- bounds从实际path.radiusBound及激光最大长度取保守上界；宿主预算提高到1024成员/256出生/128组以覆盖五人正式预览，不能沿用首切片512 cap造成无意拒绝。
- `ProgramBlue1Test`六项验证：1/2/5目标108N与精确拍点、锁释放/220/230/240回收；重排后锁帧和checkpoint恢复；目标退出不转派/近身拒绝；蓝4实际断口与阻挡撤销；蓝6片内偏移及段边界连续；三份正式定义晚加入网络→render view。
- 代表卡仅单轮预览。蓝6 age20未来弧线提示、激光方块裁剪长度事实投影/表现已在本轮后续补齐代码；尚缺新代表卡实机验收、动态方块裁剪实测、完整诊断与M2。1.5、2.1仍不勾选。

新针对性测试通过：`build/agent-logs/20261017-013905-test.log`。首切片的用户实测已完成；新blue1/4/6尚无用户实机证据。

第三轮续接：用户确认首切片已实测一轮，并授权继续；反馈判定偏宽与要求可替换五角星样式。此记录仅代表首切片实测完成，不代表蓝1/4/6或M2验收。

- `Pattern.Style`新增`visualRadius`（保留旧四参数构造），程序renderer不再以碰撞半径作为唯一视觉大小来源。
- `ProgramVisuals`接现有DEFAULT/STAR_PRISM档案与颜色，`DanmakuMeshes`由程序renderer和旧SphereDanmakuRenderer共用五角星柱几何。程序使用连续member age摆动。当前实现本体/颜色/摆动，完整glow/core/colorMode档案适配尚未完成。
- `/gs_program_preview start blue1_slice star`可选星弹；默认仍球弹。此命令仅换样式，不改变轨迹、拍数和碰撞半径。
- `ProgramCollisionGeometry.sphereAabb`实现分段二次距离首次求交，普通弹不再使用inflate方盒角落作最终命中；激光保持原规则。
- 切片显式碰撞半径0.1、视觉半径0.2，作为手感fixture调整；不能拿降低碰撞后的结果充当等价A/B收益。
- 新增角落不误中、面首次进入、静止接触、样式切换轨迹/碰撞保持、typed codec往返测试。
- 最终完整build/test通过：`build/agent-logs/20261017-011531-build.log`。

下一步仍是完整蓝1固定多人槽/四拍锁轴/偏移源和取消，再蓝4跨轨断口/通路、蓝6直线圆弧组合。此次代码尚需后续实机验证；任务保持4/12。

第二轮实机反馈：球弹微抽搐、过亮。发现`ProgramClientNetwork.renderViews()`只传整数客户端tick，未传帧内partial tick；现renderer传入RenderLevelStageEvent的game-time partial tick，按连续时间解析位置。球弹改复用现有`sphere_danmaku_lod.png`（核心/光晕已烧入），蓝色visual映射饱和蓝染色，单层alpha混合，去掉逐弹切换本体/加法辉光和共面重复片；AFTER_PARTICLES末尾显式提交所用buffer。ProgramPreviewTest补连续帧内时间/位移回归。仍需实机复验；不代表完整视觉profile适配或M2通过。

实机反馈修复（00:57）：首次运行`blue1_slice`在客户端`ProgramRenderView.capture → ReplicaState.state → Paths.Linear.at`崩溃。服务端已提交出生成员，而客户端渲染时钟尚未到birth，导致负member age。渲染视图现只在`birth <= renderTick < birth+lifetime`求值成员；预警/出生前不派发成员声音。`ProgramPreviewTest`补上已提交snapshot在出生前、出生边界和寿命终点的渲染回归。完整build（含test）通过：`build/agent-logs/20261017-005730-build.log`；strict validation通过，修复后仍需实机复验。

下文“下一步实现ProgramPreviewHost”的建议已由本次续接完成，原记录保留作为前一轮上下文。当前新增：

- `ProgramPreviewHost`：正式session/runtime/server/network/collision装配，每维度一个显式session；静止ArmorStand只承载owner，不使用TrackRunner，不创建成员实体。
- `/gs_program_preview start blue1_slice`、`/gs_program_preview stop`、`/gs_program_preview stats`，需权限2；重复启动会结束旧预览；不会覆盖其他显式宿主。
- 发起者死亡、退出、换维度，owner失效，程序自然结束，命令停止和维度卸载均回收；移除网络宿主前发布终止事实。
- `ProgramPreviewPrograms`：编译正式Capture/Group/Fire/Control操作，五拍18发网格，20 tick预警，100 tick寿命，最后显式EndEncounter。只是Blue1风格纵向fixture，尚非完整蓝1。
- `ProgramPreviewBounds`：仅用于该线性snapshot fixture的全寿命保守组/成员包络。新案例不能直接假定此实现适用于任意路径。
- `ProgramClientNetwork`默认安装共享operation定义校验registry；客户端没有authority handlers。原空registry会拒绝正式预览定义。
- `ProgramServerHost.remove`显式关闭并发布结束；`ProgramNetworkHost.stats`按观看者输出逻辑/编码字节及恢复统计，命令同时输出runtime、gameplay和各类事件累计数。
- 预览owner没有DANMAKU_DAMAGE属性时，碰撞层使用既有DANMAKU_BASE_DAMAGE配置，避免读取不存在的属性。

新增 `ProgramPreviewTest` 四项自动化验证：编译程序预警→出生→checkpoint恢复一致→自然结束回收；owner丢失撤销预警和未来拍；全寿命轨迹/预警保守包络；正式定义晚加入→replica/render view→End后历史包不能复活。

本次验证通过：

```text
tools/gradle_task.ps1 test
tools/gradle_task.ps1 build
openspec validate add-danmaku-program-gameplay --strict
git diff --check -- src openspec/changes/add-danmaku-program-gameplay
```

日志：`build/agent-logs/20261017-004634-test.log`、`build/agent-logs/20261017-004823-build.log`。build在最终伤害属性存在性修正后重新运行了完整test。

`python tools/lang_audit.py`：新增Java文本键与en/zh均通过；全仓退出码1来自31个既有`jei.gensokyou.recipe.zaohua_*`数据派生翻译缺失，本次未扩展到配方翻译修复。

没有新增实机证据。下一步应运行上述命令验预警、球弹、命中和停止/回收，然后实现完整蓝1/4/6及其他组合案例、补CPU/frame-time诊断与实机矩阵。1.5、2.1–2.7仍未完成，不能进入迁移或归档。

本文用于把第五期当前实现状态交给下一位 agent。它是实现交接记录，不代表本 change 已完成，也不替代 `proposal.md`、`design.md`、`specs/` 或 `tasks.md`。

## 当前结论

第五期已经完成程序弹幕进入游戏侧所需的基础碰撞、伤害、客户端状态投影、批量基础渲染、激光 profile、Billboard 方向隔离和部分诊断设施。

第五期尚未完成正式程序预览宿主、蓝 1/4/6 代表切片、全部组合案例、实机多人/延迟/恢复矩阵和 M2 A/B 性能门槛。因此不能开始正式 BOSS 卡迁移，也不能归档本 change。

当前路线是：

```text
已有纯核 / server / network
             │
             ▼
游戏侧碰撞、伤害、渲染基础设施       ← 本轮已完成大部分
             │
             ▼
ProgramPreviewHost + 最小正式程序卡    ← 下一步
             │
             ▼
蓝1/4/6及代表组合实机切片              ← 待完成
             │
             ▼
同负载A/B、多人延迟、恢复和回收         ← 待完成
```

## 已完成的实现

### 1. 程序伤害源和碰撞适配

新增 `ModDamageTypes.programDanmaku(victim, attacker)`。程序成员没有 Minecraft 弹体实体，因此：

- `attacker` 是 encounter owner；
- direct entity 为空；
- 伤害类型仍是 `gensokyou:danmaku`；
- 继续进入现有 `LivingIncomingDamageEvent`、擦弹、护壁、属性减免和东方战斗事件；
- 依赖 `getDirectEntity() instanceof AbstractDanmakuProjectile` 的玩家武器灵汲逻辑不会误认 BOSS 程序弹。

相关文件：

- `src/main/java/com/bitsson/gensokyou/registry/ModDamageTypes.java`
- `src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramHitContext.java`
- `src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramCollisionSample.java`
- `src/main/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramCollisionAdapter.java`

`ProgramServerHost.install(...)` 会为显式安装的程序 session 安装 `ProgramCollisionAdapter`。这只影响显式安装的程序 session，不会自动替换正式 BOSS 后端。

### 2. 纯几何判据

`ProgramCollisionGeometry` 提供无世界、无随机、无副作用的 AABB 几何判据：

- 球弹/普通路径线段扫掠；
- 静止成员的退化线段相交；
- 激光线段相交；
- 半径扩展目标碰撞箱；
- 首次进入点计算。

曲线成员在 `ProgramCollisionAdapter` 中使用自适应中点拆分：

- 最大递归深度 3，即最多 8 个子段；
- 中点相对弦中点误差目标为 `0.02` 格；
- 激光使用单条权威射线；
- 长弦不能直接代替曲线扫掠。

纯几何测试：

- `src/test/java/com/bitsson/gensokyou/danmaku/program/gameplay/ProgramCollisionGeometryTest.java`
- `src/test/java/com/bitsson/gensokyou/danmaku/DanmakuHitScanTest.java`

### 3. 激光 profile

新增 `program.ops.CollisionProfile`，不把激光字段塞进通用 `Pattern.Style`：

- `CollisionProfile.Projectile`；
- `CollisionProfile.Laser(maxLength, piercesBlocks, damageIntervalTicks)`。

`Actions.ChildProgram` 增加 collision profile 字段，同时保留旧构造器，旧测试和旧内容默认使用普通 projectile profile。profile 已注册到 `OperationLibrary.dataRegistry()`，会随 typed operation 定义编码和恢复。

激光适配目前支持：

- 最大长度；
- 穿墙/不穿墙；
- 服务端方块裁剪；
- 按成员 age 的周期命中节流；
- 解析世界方向，零速度时回退到 source frame + template direction。

### 4. 客户端 replica render view

新增 `ProgramRenderView` 和 `ProgramReplica.renderView(s)` / `renderViews(...)`。客户端不创建程序成员实体，而是从 replica projection 读取：

- 稳定 `MemberId`；
- 位置、方向、半径；
- `Pattern.Style`；
- visible/damaging/alpha/size/brightness；
- collision profile；
- warning plan；
- `SoundCue` 事件。

客户端入口：

- `src/main/java/com/bitsson/gensokyou/client/ProgramClientNetwork.java`
- `src/main/java/com/bitsson/gensokyou/client/ProgramDanmakuRenderer.java`

`ProgramDanmakuRenderer` 挂在 `RenderLevelStageEvent.AFTER_PARTICLES`：

- 普通成员使用 Billboard；
- 激光使用世界方向光束；
- warning 使用相同 plan/profile；
- 普通成员有基础本体和余辉层；
- 声音 cue 通过 `BuiltInRegistries.SOUND_EVENT` 解析，缺失音效安全跳过。

声音去重：

- `ProgramSoundLedger` 按 `epoch/group/batch/deduplicationKey` 去重；
- logout 时清空 ledger；
- `Presentation.Profile` 增加 `soundCues` 字段，并保留旧四参数构造器。

Billboard/世界方向纯数学测试：

- `ProgramRenderMath.java`
- `ProgramRenderMathTest.java`

### 5. 诊断基础

新增 `ProgramGameplayStats`：

- collision samples；
- hits；
- damage rejected；
- laser clips；
- laser ticks skipped。

新增 `ProgramGameplayDiagnostics`，汇总：

- `DanmakuWorldRuntime.RuntimeStats`；
- gameplay stats；
- `ProgramSyncServer.Stats`；
- replica late/duplicate/failure/pending transfer。

`ProgramSyncServer.Stats` 现在区分：

- logical payload bytes；
- encoded wire/envelope bytes；
- recovery requests；
- throttled recovery。

测试：

- `ProgramGameplayStatsTest.java`
- `ProgramGameplayDiagnosticsTest.java`
- `ProgramSoundLedgerTest.java`

## OpenSpec 任务状态

当前 `tasks.md`：

```text
1.1  [x] 几何判据、ProgramHitContext、程序伤害管线
1.2  [x] 曲线扫掠、激光窗口/节流/动态裁剪
1.3  [x] 批量球弹/激光渲染、预警、弧线、余辉、声音去重
1.4  [x] Billboard 与世界炮口方向、多摄像机数学验证
1.5  [ ] 完整组/计划/取消/派生/迟到/恢复/CPU诊断和字节分解接线
2.1  [ ] 蓝1/4/6正式程序预览
2.2  [ ] 花海与尾珠预览
2.3  [ ] 复合花阵/旋转激光/远端子组/多人延迟
2.4  [ ] 两代分裂、移动母弹、外部实体源、预算拒绝、中途加入
2.5  [ ] 移动龙卷风/法阵/Wave/Stagger/Bezier/Mirror/FPS无关验证
2.6  [ ] 同负载 A/B M2 门槛
2.7  [ ] 最终 wrapper/集成/严格验证、台账和实机证据
```

注意：1.5 已有诊断类和字节统计基础，但尚未接入完整命令、正式 preview host、性能采样报告和所有生命周期证据，因此保持未勾选是有意的。

## 推荐下一步：实现 `ProgramPreviewHost`

下一位 agent 应先实现显式的程序预览宿主，而不是直接迁移 `BossCards`。

目标装配：

```text
/gs_program_preview start|stop|stats
              │
              ▼
ServerLevelDecisions
              │
              ▼
DanmakuWorldRuntime
              │
              ├─ ProgramSession
              ├─ ProgramExecution
              ├─ ProgramServerHost
              ├─ ProgramNetworkHost
              └─ ProgramCollisionAdapter
```

宿主至少需要负责：

1. 绑定 owner UUID 和 encounter epoch。
2. 绑定观看玩家和兴趣范围。
3. 安装正式 `ProgramDefinition`。
4. 安装 server control/emission operation registry。
5. 创建保守的 world AABB/member bounds 生产者。
6. 显式安装 `ProgramServerHost` 和 `ProgramNetworkHost`。
7. 支持 start/stop/stats。
8. 处理 owner 死亡、退出、维度卸载和重复启动。
9. 输出 `ProgramGameplayDiagnostics`。
10. 只用于 preview/test，不替换正式 BOSS。

## 推荐的第一张程序卡

先做一张最小 Blue 1 风格测试卡，不要立即实现完整蓝 1/4/6：

```text
CaptureTargets(primary target)
        ↓
CaptureFrame
        ↓
创建偏移 source group
        ↓
PrepareEmission / Telegraph
        ↓
CommitBatch
        ↓
Emit ParallelGrid
        ↓
Linear movement
        ↓
End / Cancel / cleanup
```

第一条纵向切片要验证：

- 正式 `ProgramDefinition` 能安装到 `ProgramSession`；
- server tick 能推进并生成 checkpoint；
- client 能收到 Definition/Start/Decisions；
- 成员能进入批量 render view；
- 程序成员能对玩家产生一次程序伤害；
- owner 失效后攻击清理；
- 中途进入收到完整 checkpoint；
- client 重建不会重复伤害或音效；
- EndEncounter 后 runtime/client 资源归零。

不要用当前 `DanmakuPreview` 作为第五期成功证据。它仍是 `ArmorStand + TrackRunner + Entity projectile` 的 legacy preview，只能作为旧后端 A/B 对照。

## 当前 API 注意事项

### `DanmakuWorldRuntime`

已有：

- `member(MemberId)`；
- `encounter(MemberId)`；
- `state(MemberId,time)`；
- `direction(MemberId,time)`；
- `collisions(CollisionSink)`；
- `stats()`；
- `checkpoint()` / `restore(...)`。

`ProgramCollisionAdapter` 在真实 world 接线时需要 owner 是当前 `ServerLevel` 中存活、已加载的 entity；找不到 owner 会 fail closed。

### `ProgramServerHost`

`install(...)` 当前要求每个 `ServerLevel` 一个显式 session，并自动安装 `ProgramCollisionAdapter`。它不会自行创建 session，也不会自行选择 BOSS backend。

### `ProgramNetworkHost`

`install(...)` 后还需要宿主提供：

- encounter envelope AABB；
- member bounds；
- spatial shard 参数；
- 事实投影生产者。

没有真实 bounds 时，宁可保守保留整批，也不要用 BOSS 点位猜测弹幕包络。

### `ProgramRenderView`

目前是 client projection，不是权威状态。它不能用来判伤，也不能让客户端自行生成成员。

### `Pattern.Style`

当前字段仍然是：

```text
projectile, visual, collisionRadius, damageScale
```

激光长度、穿墙和节流在 `CollisionProfile.Laser`，不要重新把这些字段塞回 Style。

## 验证记录

本轮最终通过：

```text
tools/gradle_task.ps1 test
tools/gradle_task.ps1 build
openspec validate add-danmaku-program-gameplay --strict
git diff --check -- src openspec/changes/add-danmaku-program-gameplay
```

最近一次 build 日志：

```text
build/agent-logs/20261017-002617-build.log
```

最近一次完整测试日志：

```text
build/agent-logs/20261017-002350-test.log
```

实现期间的测试运行会修改/生成仓库 `logs/` 下的压缩日志。不要把这些日志变化误认为 gameplay 源码变更，也不要未经确认清理其他 agent 的日志或工作区改动。

## 工作区和实现纪律

- 所有 Gradle 调用必须使用 `tools/gradle_task.ps1`。
- 不要直接运行裸 `gradlew`。
- 不要恢复临时目录中的旧 `ProgramPreviewHost.java` 等源码作为当前 API 依据；它们依赖当时不存在的后续接口，备份位置见 core handoff。
- 不要修改或迁移正式 `BossCards`，直到 2.6 M2 A/B 门槛通过。
- 不要把 shadow backend 的结果用于伤害。
- 不要让客户端生成成员、执行 authority operation 或补发历史音效。
- 不要用减少弹量、缩短寿命、关闭碰撞或改变内容密度伪造 A/B 性能收益。
- 新增实现后必须重新运行完整 `test`、`build` 和 strict validation。

## 交接后的完成标准

第五期只有在以下条件全部具备后才能进入 migration change：

1. 1.5 诊断和恢复证据完整。
2. 蓝 1/4/6 代表切片实机通过。
3. 花海、尾珠、分裂、移动母弹、龙卷风和高频法阵案例通过。
4. 单人和 1/2/5 人网络矩阵通过。
5. RTT、抖动、客户端暂停、低 TPS、晚加入和重追踪通过。
6. 逻辑/线上字节、CPU、客户端 frame time 和资源回收报告完整。
7. M2 A/B 门槛通过：确定性无逐弹网络实体、逻辑流量降低至少 50%、CPU/frame time 不超过旧基线 110%。
8. `tasks.md` 2.1–2.7 逐项有实机或自动化证据。
