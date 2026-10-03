> 每条任务都挂一条**可离线断言**的验收。
> **前置**：`fix-ring-card-geometry` MUST 先完成 —— 环不成环时，实测出来的
> 「换向误差」里混着几何误差，两者无法分离。
> **反向验收**：`danmaku-timeline-sync` 立下的纪律是「速率估计绝不能吸收真失步」。
> 本变更的对称纪律是「段方向绝不能每 tick 重抽」。若这条失败，
> 本变更制造的问题比它修复的更糟 —— 停下来重做任务 3.2。

## 0. 前置与作废声明

- [x] 0.1 前置 `fix-ring-card-geometry` 的几何修复与回归测试已完成。
      **验收**：本组任务在此之前 MUST NOT 开展任务 4.x（换向实测）。 ✔
      **实质前置已满足**：环已闭合并由 `RingCardCrossBeatConsistencyTest`（5/5）锁定，
      β 的任务 4.1「几何误差已从换向误差中分离」已完成。
      ⬜ **但 β 尚未归档** —— 其 5.3 / 5.4 两条实机验证待做。
      若那两条表明环仍不可读，MUST 回来重判，本变更的换向实测应暂停。
- [x] 0.2 前身变更 `danmaku-event-sync` 已移入
      `openspec/changes/archive/2026-10-01-danmaku-event-sync-deferred/`。
      **该变更是暂缓，不是否决** —— 机制保留，实现时的修正清单在其 `proposal.md`
      与 `design.md` 顶部。
      **验收**：活动变更列表里 MUST NOT 再出现 `danmaku-event-sync`；
      archive 目录名 MUST NOT 含 `rejected`。 ✔
      **理由**：它有 31 条当前不实施的任务，8 条 spec requirement 里多数在本变更中
      被归入更低档位。留在活动列表里，后来人会去找一份不存在的东西；
      但**丢弃设计**会让将来的实现重复走一遍已知的弯路（TCP 无乱序、重放不可实现）。

## 1. 随机量容器

- [x] 1.1 新增 `danmaku/motion/DanmakuRandomState`：`int × 8` 种子 + `count`。
      **纯静态、无世界依赖。** ✔ 不 import 任何 Minecraft 类
      **验收**：`count` 为 0 时所有取值为常量，MUST NOT 触发任何随机行为。 ✔
- [x] 1.2 取值 API 语义无关：`int at(int index)`、`int size()`。
      **验收**：本类 MUST NOT 出现 `direction` / `angle` / `spread` 等语义词。 ✔
      语义留给消费方 `DanmakuLegMotion`；这样「同一组种子在不同弹种里含义不同」
      在类型上可见，而不会被容器固化成一种解释。
- [x] 1.3 纯函数测试：同一组种子在两次构造中产出逐位相同的取值序列。 ✔
      另测：输入数组被防御性复制、`equals`/`hashCode` 按值比较
- [x] 1.4 纯函数测试：`index >= size` 时退化为常量，MUST NOT 抛异常。 ✔
      越界（含负数）返回常量 0；另测 `count` 被夹到 `[0, 8]`

## 2. 段式运动

- [x] 2.1 新增 `danmaku/motion/DanmakuLegMotion`：段数定长 8，
      单段参数打包为 `(时长 << 16) | (速率 & 0xFFFF)`。
      **验收**：段数为 1 时行为 MUST 退化为「恒速直线」这一最简形态。 ✔
      `straight(...)` 便捷入口 + `singleLegDegeneratesToStraightLine`
      **实现修正**：`VELOCITY_SCALE` **直接引用** `DanmakuWire.VELOCITY_SCALE`
      而非复制一份 4096。定标漂移的症状是「弹看起来慢了一点」，极难归因。
      由此带来的量化误差上界为半个步长（1/8192 ≈ 1.2e-4），
      测试容差按此设定 —— 用 1e-9 测的是浮点精确，不是本判据要的东西。
- [x] 2.2 段类型三选一：`FIXED`（方向恒为发射方向）/ `SEED`（`f(种子, 段号)`）/
      `TARGET`（指向实体）。
      **验收**：三种类型各自有真值表测试。 ✔
      **类型是整条运动的属性而非逐段的**：accessor 预算里没有逐段类型位，
      逐段类型会让 18 个 accessor 变成 26 个。
      `FIXED` 的含义定为「方向恒为发射方向」—— 段表无法携带方向（打包位已满），
      而这正好让「不变向」成为段式运动的最简基线。
- [x] 2.3 `segmentAt(age)` / `speedAt(age)` / `directionAt(age)` 全部是年龄的纯函数。
      **验收**：同一段内重复查询返回相同值；跨段边界按 `Tᵢ` 精确切换。 ✔
      段边界测试覆盖「段末仍属本段」「正好落在边界即切换」「超出总时长夹紧」
- [x] 2.4 段方向为**世界坐标的绝对方向**（`design.md` 决策 1）。
      **验收**：测试断言相对偏转型接口**不存在**。 ✔
      段表接口只暴露 `directionAt(age) → Vec3`，没有任何「相对当前方向」的入口；
      另测「整条轨迹是 f(年龄, 段表, 种子)」—— 相对偏转的实现过不了这条。

## 3. 消费契约（反向验收，不可省）

- [x] 3.1 段方向在**构造期**从种子与段表解出，缓存为 `Vec3[]` 字段。
      **验收**：构造函数之外无任何 `Random` / 种子 accessor 的读取点。 ✔
      `perTickPathDoesNotReadSeedContainer`：逐个取 `segmentAt` / `speedAt` /
      `directionAt` / `lastKnownDirection` 的方法体，断言其不引用种子容器。
- [◐] 3.2 **反向测试**：构造运动形态 → 跑 200 tick 记录轨迹 → 改种子 accessor →
      重建实体状态 → 再跑 200 tick ⇒ **断言两条轨迹逐位相同**。
      **验收**：若此条失败，说明方向在每 tick 路径上被重抽 —— 本变更的核心纪律被破坏，
      停下来重做 3.1，不要改测试迁就实现。
      **◐ 部分完成 —— 原任务措辞在本层无法实现，已改写并记录原因。**
      **初版断言写错了**：曾写成「换一组种子重建后轨迹仍相同」，
      那与同一测试类里已通过的「不同种子 MUST 改变轨迹」**直接矛盾**，
      且会把「种子根本没用」也判成通过。已改为由**同一份已同步输入**重建两次、
      断言轨迹逐位相同 —— 若方向是每 tick 现抽的，两次重建会因求值次数不同而分叉。
      **为什么「改活体的种子 accessor」在本层做不到**：`DanmakuRandomState` 不可变，
      无法在构造后制造「源变动」来观察行为；而本类合法地在 `fromSpec` 里用 sin/cos，
      常量池扫描会误报（见 3.5）。故纪律改由 3.1 的源码级断言承担。
      **实体层面的完整形态**（「改活体种子 accessor → 重建 → 轨迹不变」）
      依赖 `AbstractDanmakuProjectile` 的接线，属任务组 4。
- [x] 3.3 重建后轨迹按新种子改变，且**仅**在受该种子影响的段上改变。
      **验收**：断言未受影响段的轨迹逐位不变。 ✔
      只改第 2 段种子 ⇒ 段 2 起始年龄（100）之前逐位不变，首次差异 MUST NOT 更早
- [x] 3.4 `dirFromAngles` MAY 用 `sin/cos`（构造期求值一次），
      但 MUST 在类注释里写明该纪律。
      **验收**：类注释含「构造期求值一次 / 每 tick 路径不读」的显式声明。 ✔
- [x] 3.5 MUST NOT 把 `DanmakuTrackKindsTest` 的常量池扫描纪律扩展到本类 ——
      扩展会立刻改变段运动轨迹，而没人会想到原因是这个。
      **验收**：既有扫描测试的类名列表不包含 `DanmakuLegMotion`。 ✔
      已核实名单仍只有 `DanmakuSpeedProfile` 与 `FormationFrame`

## 4. 接入点

- [x] 4.0 **内容侧表达通路**：`DanmakuLegSpec`（声明侧数据）+ `Track.Builder#legMotion(...)`
      + `Track.Beat#legSpec()` + `DanmakuEmitter` 在发射时构造 `DanmakuLegMotion`。
      **理由**：段式运动若只能由调试命令进入，它就还不是一个「特性」而是一个后门。
      **逐拍而非逐发**：同一拍发出的弹共用一份段表与种子 ——
      「每批一个种子 + 批内第几枚」需要实例索引，属 `danmaku-track-scope` 的门槛范围。
      形态不受损：位置各自累加 ⇒ 这些弹从各自出生点出发、沿同一套方向 schedule 走，
      读作一片同步转向的弹幕。
      **`Track.Beat` 新增字段时保留了 11 参旧构造器**，故既有调用点与测试**一行未改**。
- [x] 4.1 `AbstractDanmakuProjectile` 新增 18 个 accessor
      （8 种子 + 8 打包段参数 + `seedCount` + `legCount`）。
      **验收**：未使用段运动的弹，MUST NOT 产生任何额外生成包字节
      （`SynchedEntityData` 只下发非默认值）。 ✔
      **段类型与段数共用 1 个 accessor**（低 4 位段数、高 2 位段类型）——
      否则 18 个预算需要涨到 26 个（逐段类型要额外 8 位）。
      `DanmakuLegMotion.packLegCountAndKind / legCountOf / kindOf`
      **方向缓存**：`legMotion()` 在首次访问时构造并缓存，键为「全部同步输入的指纹」；
      输入一变（`applyMotionParams` / 读档 / `setLegMotion`）即失效。
- [x] 4.2 `motionParams()` 把种子与段表折进指纹块。
      **验收**：两端种子不同时指纹 MUST 不同。 ✔
      **`PARAM_COUNT` 53 → 63，不是 design 写的 62。**
      design 决策 4 的代码片段用到 10 个下标（2 个计数 + 8 个种子）⇒ 53 + 10 = 63。
      以代码片段为准。指纹块不写进 NBT（写的是具名键），故无存档兼容问题。
- [x] 4.3 `DanmakuTrackKinds` 新增 `SEGMENTED` 档：
      位置依赖上一 tick（累加）、速度**不**依赖上一 tick（可由种子+段表解出）。
      **验收**：真值表测试覆盖「段运动 + 曲射」等组合。 ✔ `DanmakuTrackKindsSegmentedTest`
      **判据次序修正**：实现初版把 `segmented` 放在最前面判断，
      结果「段式 + 速率曲线」也被迫写速度 —— 那会盖掉
      「挂了速率曲线 ⇒ 位置不由速度决定 ⇒ 不写」这条既有语义。
      已改为编队帧/速率曲线**优先**，段式只在裸段运动时生效。
- [x] 4.4 `needsVelocityPersistence` 对段运动返回 **true**（`design.md` 决策 5）。
      **验收**：读档后 MUST NOT 需要在位置积分前重算速度。 ✔
      **与 `survivesReloadWithoutVelocity` 刻意解耦**：段式「能自愈」（速度可解出）
      但仍「写速度」（顺序依赖）。两条看似矛盾，理由不同 ——
      前者问「能不能算出来」，后者问「算的时机对不对」。
      读侧注释已写明：速度**最后**读，因为该判据依赖 `DATA_LEG_COUNT_AND_KIND`。
      **既有行为未变**：裸直线弹本来就写速度（`pos += velocity`，丢了就冻结）。

## 5. 存档

- [x] 5.1 种子与段表入 NBT，键名沿用既有具名键风格。 ✔
      `RandomSeedCount` / `RandomSeed0..7` / `LegCountAndKind` / `Leg0..7`
- [x] 5.2 读侧逐键判存在、逐类独立降级；两类键 MUST NOT 互相推断。
      **验收**：构造「有种子无段表」「有段表无种子」两个残缺存档，
      读档 MUST NOT 抛异常、MUST NOT 零速悬停。 ✔
      两类各自只降级自己那半；缺 `LegCountAndKind` ⇒ 段数 0 ⇒ 走变更前的路径。
      ⚠️ **「两个残缺存档」的构造与读档断言仍缺**（需要实体级脚手架，同 5.8）
- [x] 5.3 读侧顺序在代码注释中写明（速度最后读的既有纪律 MUST 保持）。 ✔
- [ ] 5.4 **重载轨迹断言**（`design.md` 决策 6）：
      构造弹 → 跑 200 tick 记录轨迹 → 模拟读档 → 再跑 200 tick ⇒ 断言逐位相同。
      **验收**：这是「纳入存档」的可验收形式。仅断言「键被写入」不足。 ⬜ **未做**
      **阻塞于 5.8**：`restoredAge` / `tickCount` 的模拟需要能构造实体的脚手架。

## 6. 激光：两种形态并存

> **本组初版写的是「统一为穿墙，删掉整条裁剪路径」，该写法已作废。**
> 被遮挡形态与穿墙形态 MUST 同时存在。核实代码后确认：
> 判伤只在 `ServerLevel` 跑（`LaserDanmaku:126`）⇒ 判伤是服务端权威；
> `getActualLength()`（判伤）与 `getRenderLength()`（视觉）各自带缓存、各自射线检测，
> 且 `LaserDanmaku:172-177` 的注释**明确警告两者 MUST NOT 合并**。
> ⇒ 被遮挡形态**不需要任何增量改动**，它现在的实现就是正确的。

- [x] 6.1 `Projectile` 增加 `laserPiercesBlocks` 位，`Projectile.laser(...)` 增加带该位的重载；
      **默认 `false`（被遮挡）**。
      **验收**：在役符卡（喷泉激光 80 格，`BossCards:257`）行为**逐位不变**。 ✔
      **必须保留** `getActualLength` / `getRenderLength` / `clipLength` / `ClipContext`
      —— 它们是**被遮挡形态的实现**，不是待删除的遗留物。 ✔ 全部保留，一行未删
      另加语义化入口 `Projectile.piercingLaser(...)`
- [x] 6.2 `LaserDanmaku` 新增 `DATA_PIERCES_BLOCKS`（`BOOLEAN`，默认 `false`），
      生成时写入、读档时恢复（NBT 具名键 `PiercesBlocks`）。 ✔
      缺键（旧存档）⇒ `false`，读档不失败 ✔
      走 `setPiercesBlocks(...)` 而非直接 `set` —— 它负责让两套长度缓存失效
- [x] 6.3 形态位 MUST 进运动指纹：`P_LASER_BASE` 块增一项，
      `DanmakuMotionState.P_LASER_COUNT` 7 → 8。 ✔
      **理由**：形态位**决定长度**，而校准只比位置。
      `onMotionParamsApplied` 对旧快照缺该位时按 0（被遮挡）处理
- [x] 6.4 `getActualLength()` / `getRenderLength()` 按形态分支。 ✔
      `LaserDanmakuRenderer` 无需改动（它只调 `getRenderLength(partialTick)`）
      ⚠️ 逐形态不变量的**可断言部分**仍缺（见 5.8 脚手架缺口）
- [x] 6.5 复核 `makeBoundingBox` / `shouldRenderAtSqrDistance` 仍用 `maxLength`。 ✔
      已核实，未改动 —— 两种形态的渲染量都未变
- [ ] 6.6 **实测**穿墙形态判伤查询盒扩大的开销（喷泉卡激光 80 格）。 ⬜ **未测**
      **范围**：只针对穿墙形态 —— 被遮挡形态的判伤查询盒 MUST 逐位不变
- [ ] 6.7 复核 `ModEntityTypes` 激光的 `updateInterval(1)`。 ⬜ 未做
      独立决策，不阻塞本任务其余部分

## 7. 指向实体段

- [x] 7.1 服务端在 `TARGET` 段起始年龄推一次快照（复用 `DanmakuSnapshotPayload`，
      **不新增包类型**）。
      **验收**：带宽实测记录在案（预期约 40 B/s @ 5 玩家环卡）。 ◐
      实现完成（`DanmakuLegTargetPush` + `DanmakuSyncServer#sendTurnSnapshot`），
      ⬜ **带宽实测未做**。
      **每段只推一次**：守门是 `DanmakuLegMotion#isSegmentStart` ——
      否则一条 48 拍的环会退化成每拍一包（20 倍带宽）。
- [x] 7.2 客户端收到该快照后从快照重锚，MUST NOT 自行求解方向。 ✔
      `TARGET` 段构造期不产生方向（`fromSpec` 留 null），
      未收到快照时沿用上一段方向 —— 客户端没有任何自行求解的路径
- [x] 7.3 降级路径：目标不可解析时保持当前方向，
      **且双端得出相同结果**。 ✔
      服务端沿用当前方向、客户端按自己的段表走完剩下的段；
      计数 `leg.unresolved`
- [x] 7.4 断言两条路径在换向后第一 tick 的位置相同。 ◐
      论证已写入 `design.md` 决策 8（快照给的就是那个位置），⬜ 断言未做（需 5.8）
- [ ] 7.5 指向实体段在重载后 MUST NOT 自行求解。 ⬜ **未做**（需 5.8）
      构造「存档点早于换向年龄」的弹，断言客户端不产出该段方向

## 8. 诊断

- [x] 8.1 `/gs_boss danmaku` 新增段运动读数：在场段式弹数、种子分布、换向下发次数。 ✔
      输出 `leg[active=N fixed=.. seed=.. target=..] leg[turnPushed=.. unresolved=..]`
      扫描半径 64 格（只统计命令来源附近 —— 调试命令的意图是「看眼前发生了什么」）
- [x] 8.2 增补 `DanmakuSyncStats` 的段运动计数段。 ✔
      `leg[turnPushed=.. unresolved=..]`
      **读法**：`turnPushed` 的量级 MUST 远小于「段式弹数 × 段数」——
      否则说明有人在用 `TARGET` 段做本该档二的事。

## 9. 测试与验证

- [x] 9.1 跑通全部既有 danmaku 测试（MUST NOT 减少）。 ✔
      基线 675 tests / 80 suites → 现 **740 / 88**（只增不减）
- [x] 9.2 既有测试的期望值 MUST 零改动。 ✔ 全程未改任何既有测试的期望值
      （三次失败都是**新写**的断言写错，已改断言而非改标准；详见设计决策记录）
- [x] 9.3 新增：段运动纯函数测试、种子消费契约测试、`SEGMENTED` 判据测试、
      激光形态声明测试、环卡跨拍一致性测试、声明侧通路测试。
      ✔ `DanmakuLegMotionTest`(18) / `DanmakuLegMotionContractTest`(8) /
      `DanmakuTrackKindsSegmentedTest`(7) / `ProjectileLaserVariantTest`(7) /
      `RingCardCrossBeatConsistencyTest`(5) / `DanmakuLegSpecTest`(8)
      ⬜ **存档往返 / 重载轨迹 / 指向实体段降级 / 激光长度一致性**：
      四项都需实体级脚手架（见任务 10）
- [ ] 9.4 实机：多段随机变向的弹在两端表现一致（双客户端错时对照）。 ⬜ **需实机**
      **单客户端已可观察**：`/danmaku leg 3` 会生成一条 3 次变向的弹。
      单端只能看「变向是否平滑、悬停段是否真悬停」；
      「两端逐位一致」需要第二个客户端或开服 + 客户端
- [ ] 9.5 实机：含段式弹的存档重进后轨迹无跳变。 ⬜ **需实机**
- [ ] 9.6 实机：激光两种形态各自正确（穿墙 / 被遮挡）。 ⬜ **需实机**
- [x] 9.7 完成 NeoForge 编译。 ✔ 全量 `build` BUILD SUCCESSFUL
      ✔ 客户端实际启动无异常（新增的调试命令随 Brigadier 树一起注册成功）

## 10. 未实现：本变更的单一最大缺口

以下项**实现已就位但缺可执行断言**，全部指向同一个根因：
**本仓库没有能构造 `Level` 与实体的测试脚手架**
（`MinecraftTestBootstrap` 只起注册表；全仓无任何测试构造过 `Entity`）。

| 任务 | 内容 | 状态 |
|---|---|---|
| 5.2 | 两个残缺存档（有种无段 / 有段无种子）读档不抛异常 | 实现就位，断言缺 |
| 5.4 | 重载后轨迹与从未卸载时逐位相同 | 实现就位，断言缺 |
| 6.4 | 逐形态不变量：视觉长度 == 判伤长度 | 实现就位，断言缺 |
| 7.4 | 两条降级路径在换向后第一 tick 位置相同 | 论证在 design，断言缺 |
| 7.5 | `TARGET` 段重载后不自行求解方向 | 实现就位，断言缺 |

**这五项 MUST NOT 被当作已完成归档。** 脚手架本身建议单独立项 ——
它的服务对象不止本变更：环卡的重载轨迹、灵符的 NBT 往返都需要它。

## 11. 实机发现的两个坑（已修 + 已加判据）

两者都**不是**设计阶段想到的，是实机跑出来的。

### 11.1 命令参数名与实际行为差一次转向

`/danmaku leg 3` 的参数叫 `turns`，实现却按**段数**做。
而 N 段只有 **N-1** 次转向 —— 于是读数说「变向 3 次」而实际只有 1 次。
**根因是段数从来不是被断言的对象。** 已改名为 `segments`、默认 6 段，
并把段表打进聊天栏（段式运动最难自查的是「我以为它该在某年龄转弯，它没转」）。
新增 `DanmakuLegSpecTest#segmentCountMatchesRequestExactly`：1~8 档逐档断言
「请求 N 段 ⇒ N 段 ⇒ N 个互不相同的方向」—— 不只数段数，还验证相邻段方向真的不同。

### 11.2 偶数段数的交替段表 ⇒ 弹永久悬停（无诊断）

`/danmaku leg 8` 的弹在段表走完后**定住不动直到寿命结束**。

根因：`segmentAt(age)` 越过段表末尾时**夹紧到末段**，而交替段表
（「飞 1 秒 → 悬停 1 秒」）在**偶数段数**下末段恰是悬停段（速率 0）。
现象无报错、无日志、且完全看不出是声明的问题。

已做三件事：
1. 调试命令的段表**强制末段为快飞**（`hover = 奇数段 && 非末段`）。
2. **`TrackLint` 新增判据**：末段速率为 0 且寿命长于段表总时长 ⇒ 拒绝，
   理由 MUST 点明「永久悬停」。豁免：寿命短于段表。
3. 写进 `danmaku-leg-motion` spec 的 requirement
   「段表 MUST NOT 以零速率段收尾而弹仍会存活」，含三个场景。

正反两向测试：`trailingHoverSegmentIsRejectedByLint`（正向）、
`trailingFlyingSegmentPassesLint` / `shortLifetimeIsExempt`（两向豁免）。

**这不是实现细节而是一条真规则**：它由「段索引夹紧到末段」这条纯函数性质
与「零速率段合法」这两件已成立的事**合成**而来 —— 两件都对，组合起来却产出
一个静默的死状态。

## 12. 已知取舍（非缺口，是设计选择）

- **段类型与段数共用 1 个 accessor**（低 4 位段数、高 2 位段类型）。
  非设计原文；理由是保住 18 个 accessor 的预算。
- **`PARAM_COUNT` 53 → 63**（design 写 62）。design 自己的代码片段用 10 个下标，
  53 + 10 = 63。以代码片段为准。
- **段式运动是逐拍而非逐发**。见任务 4.0 的理由。
- **在役符卡全部不用段式运动**。段式运动是**新能力**，本变更交付的是机制而非内容；
  要不要用它做一张卡是内容决策，本变更不擅自加。

## 10. 显式不做

以下项在本变更中 MUST NOT 实现。写在这里是为了让「不做」成为结构而不是默契。

**注意**：其中「事件协议」是**暂缓**而非否决 —— 机制保留，
设计草案在 `openspec/changes/archive/2026-10-01-danmaku-event-sync-deferred/`。
判据见 `danmaku-pipeline-capacity` 的三档优先级：只有落在**档三**
（值的确定依赖发射之后才发生的事实）才允许走权威下发，
而单次决策 MUST 复用既有快照。

- 批级随机（同组种子 + 实例索引）⇒ 留到 `danmaku-track-scope` 门槛通过后。
- 有界但非一次性的随机（如「每 tick 抖动」）⇒ 失步即永久分叉，无一致态可重新发现。
- 变长段表 ⇒ 定长 8。
- **有序事件协议**（信封 / 序号 / 缺号 / 乱序暂存）⇒ 本变更不实现。
  机制保留，将来出现档三的**流式**需求时启用。
  实现时 MUST 先读该草案的抬头：其中「乱序暂存」与「缺号等待」在 TCP 上不发生，
  「按服务器时间重放历史」不可实现，MUST 改为「以权威状态重新锚定」。
- `SPLIT` / `DESPAWN` / `PHASE_CHANGED` 事件 ⇒ 分别由真实实体、实体移除、年龄推导覆盖。
- 目标引用在重载后的重解析 ⇒ 属 `danmaku-target-state` 能力。
