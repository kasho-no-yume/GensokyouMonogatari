> **第 1 组是准入闸门。** 本变更 MUST NOT 在第 1 组的实测数据到手前进入第 2 组。
> 理由（design.md 决策 6）：本缺陷与「网络滞后严重」在既有诊断上表现相似（硬纠正暴涨、滞后分布右移）。
> 在没有「按来源分类」的年龄偏移数据之前就动手修，会把修法投到错的方向上——而相位推前那类修法会把
> 硬失败变成**静默错渲**，比不修更难查。

## 1. 第 0 步：年龄偏移的测量（准入闸门）

- [x] 1.1 `DanmakuBudget` 新增「年龄偏移」直方图，**不复用** `HARD_CORRECT` / `LAG_BUCKETS`——它们的分母已含未对齐的弹，再加维度会让「哪些数字可信」无法分辨
- [x] 1.2 分类维度：`会话内新发射` / `重建（存档或重新获取）`。判别依据为年龄基准是否非 0，且**两侧同构**（服务端读存档恢复值、客户端读配对包值），故读数可跨端直接对比。这是本缺陷唯一的判别特征
- [x] 1.3 分档边界覆盖 0~4 与 4+ 两段即可——本缺陷的实际量级在单机为几十 tick、专服为数千；**实施前 MUST 先按实测重定上界**，既有的 `LAG_EDGES`（上界 16）在专服上会全部塌进末桶而失去分辨率
- [x] 1.4 测量点放在 `AbstractDanmakuProjectile.lerpTo`：那里同时握有权威位置、客户端自推位置、年龄基准与年龄。**本步 MUST NOT 依赖任何修复**——只读现有 `DATA_*` 与 `tickCount`，双端均可独立运行
- [x] 1.5 输出接入 `/gs_boss danmaku`（呈现形态待定，见 design.md Open Question 4），给出来源的 min / 中位数 / p95
- [ ] 1.6 **主验证途径（不需要存档）**：打一枚编队花或带速率曲线的弹，让它飞向玩家；玩家后撤至该弹走出 `clientTrackingRange`（球/刀/符 = 8 格）再走回。全程不存档。**判据**：「重建」类弹的年龄偏移 p95 显著非零（≳4 tick）而「新发射」类恒为 0 ⇒ 假设成立
- [ ] 1.7 **辅验证途径**：进世界打同样弹幕读一次诊断 → 存档退出 → 重进 → 再打同样弹幕读一次。应观察到同一结论
- [ ] 1.8 若「新发射」类弹的年龄偏移也非零 ⇒ 另有根因，**MUST 停止本变更并重新调查**，MUST NOT 实施任何修法

## 2. 年龄同步量

> 本组与第 1 组共用同一个字段（诊断的分类判据就是年龄基准是否非 0），故建议同一次改动内完成。
> 通道选型见 design.md 决策 1：`SynchedEntityData` **不可用**（配对 bundle 携带的是 `ServerEntity`
> 构造时的快照，滞后可达 `updateInterval` 个 tick），故走**每客户端配对包**。

- [x] 2.1 `AbstractDanmakuProjectile` 新增两个普通字段：`restoredAge`（服务端，由存档恢复的只读权威）与 `peerAge`（客户端，由配对包写入）。**MUST NOT 新增 `EntityDataAccessor`**——设计决策 2 的净简化
- [x] 2.2 新增 `age()`：服务端返回 `restoredAge + tickCount`，客户端返回 `peerAge + tickCount`；加远超 `MAX_LIFETIME_TICKS` 的钳位。**两侧 MUST NOT 读对方的字段**——服务端年龄永不被客户端改写，客户端基准收到后不再变更
- [x] 2.3 新增 `seedPeerAge(int)`（客户端，包处理器调用）。**未新增 `ageOnServer()`**——服务端侧 `age()` 与 `restoredAge + tickCount` 同义，多包一层只是冗余间接；配对包发送方直接调 `age()`
- [x] 2.4 新增 `network/DanmakuAgePayload`（`playToClient`，`entityId` + `age` 两个 VarInt），在 `ModNetworking` 的既有 `registrar("1")` 组内注册，**不新开版本组**
- [x] 2.5 新增客户端处理器：按 `entityId` 查实体，若为 `AbstractDanmakuProjectile` 则 `seedPeerAge(age)`。查不到时 MUST 静默忽略（实体已被移除是正常竞态）
- [x] 2.6 订阅 `PlayerEvent.StartTracking`：实体为 `AbstractDanmakuProjectile` 时向该玩家单发 `DanmakuAgePayload`。MUST 在 `addPairing` 发出生成包之后才触发（NeoForge 的调用顺序即如此，勿改）
- [x] 2.7 `addAdditionalSaveData` 写入 `age()`（保存时的真实年龄），`readAdditionalSaveData` 读入 `restoredAge`。缺键时按 0 读入（spec 场景「缺失年龄基准的旧存档」），**不报错**
- [x] 2.8 补一个纯静态测试：给定 `restoredAge` 与 `tickCount`，`age()` 在「正常发射（基准 0）」下与 `tickCount` 逐位相等；且 `age()` 对 `restoredAge` 的钳位生效

## 3. 把判据改为年龄

- [x] 3.1 `AbstractDanmakuProjectile` 内全部 `this.tickCount` 换为 `age()`：存活时长（:375）、悬停（:395）、分裂（:400）、速率曲线取值（:419, :422）、编队帧推进项（:440）、帧解析位置（:778）、相位隐藏（:679）、溜め待命（:800）
- [x] 3.2 `TalismanDanmaku.tickHoming` 的 `targetLost` 判据随之改读年龄。**复核结论：无需改动**——该文件的丢失条件是「速度方向与目标方向夹角超阈值」，从头到尾没有引用 `tickCount`，故本条是空任务。`targetLost` 维持普通字段（年龄连续后两端触发时机本就相同，同步化是纯开销）
- [x] 3.3 `LaserDanmaku` 的延迟 / 持续 / 判伤节拍改读年龄（:121, :125, :157, :160, :171）
- [x] 3.4 `splitFired` **维持普通字段**、不改判定条件——同上
- [x] 3.5 全量 grep `tickCount` 复核：除 `age()` 自身的 `+ this.tickCount` 外，弹体运动学与终止判据路径 MUST NOT 再有直接引用

## 4. 编队帧的 tick 编号（可独立回滚）

- [x] 4.1 `framePositionThisTick()` 改为 `framePositionAt(age())`，去掉 `+ 1`
- [x] 4.2 **删除** `:769-779` 那段错误注释（称「`super.tick()` 还没把 `tickCount` 加一」），改写为准确表述：自增发生在 `Level.tickNonPassenger` 调用 `entity.tick()` **之前**，故体内 `tickCount` 已是本 tick 编号
- [x] 4.3 同类错误注释全量排查（本项目多处注释把「1.21.1 的自增位置」写成了旧版行为）
- [ ] 4.4 **实机确认**：编队花「出生即收拢」是否回来了。这一步改的是全部既有编队符卡的观感，MUST 由用户判定，不可由测试代替
- [x] 4.5 `advance = unscale(FrameAdv) * tick` 的无界增长**本变更不修**（design.md 决策 4），但 MUST 在代码旁留一条指向该未修项的注释，避免后续被读成遗漏

## 5. 方向轴的存档完整性

- [x] 5.1 `addAdditionalSaveData` 的方向轴写入条件由 `hasSpeedProfile()` 放宽为「挂帧 **OR** 挂曲线」，**单一写入点**（不在两条路径各写一份——同一 NBT 键写两次会让「键存在」与「键的语义」脱钩）
- [x] 5.2 `readAdditionalSaveData` 读入条件同步放宽
- [x] 5.3 补测试：「有帧、无曲线」的弹的存档判据为真。**未能做完整 NBT 往返**——本仓库测试全为无世界纯静态测试，而构造弹幕实体需游戏 bootstrap（实测 `AbstractDanmakuProjectile` 的静态初始化在测试中直接 `ExceptionInInitializerError`）。故把判据抽成纯静态 `DanmakuAge.axisNeedsPersistence` 并直接断言它，读盘侧则改为按「键是否存在」判定（`tag.contains("SpAxisX")`），不再重复条件
- [x] 5.4 **顺带把年龄规则也抽成纯静态** `DanmakuAge.at(basis, tickCount)`，与 `FormationFrame` / `DanmakuSpeedProfile` 同层。原因同上：规则放值类型里可离线测试，实体只提供两个分量

## 6. 验证

- [x] 6.1 纯静态测试（本仓库现有测试全为无世界的纯静态测试）：年龄跨「重建获取」连续性、双客户端异时配对、钳位、负输入归零、缺键退化、方向轴判据边界、编队出生当 tick 取 t=0。新增 `DanmakuAgeTest`（10 项，全部通过）
- [x] 6.2 ~~实机：**后撤再贴脸**（路径 ②）后弹不再抽搐~~ **作废（2026-09-29）——验收对象已归因到 `danmaku-lag-smoothing`。** 实测证明：① 直弹走 `pos += v` 累加式，**与年龄无关**，故 `wall` 重载多少次都不抖（该场景对本变更是空转）；② 编队弹的抖动按**速度**分层（外圈抖、内圈只锯齿），即阈值 `1.0 格²` 撞上稳态误差 `L·v` 的按速度分裂，属 `danmaku-lag-smoothing` 候选 A 的判据。留在本变更会导致两个变更互相持有对方的前置
- [x] 6.3 ~~实机：存档读档后编队花 / 减速曲线弹不再抽搐~~ **作废（2026-09-29），理由同 6.2。** 本变更对「位置抖动」的实际收益是**把灾难档拆掉**：无年龄基准时客户端按 `f(0..n)` 算而服务端在 `f(T..T+n)`，误差 `|v|·T` 可达数十格、横跨半屏；修复后降到 `|v|·δ`（δ 为管线延迟量级）。残余那一档不属本变更
- [ ] 6.4 实机：读档后弹的剩余寿命按真实年龄扣减（不白活 `MAX_LIFETIME_TICKS`）、分裂时刻不提前、相位隐藏不错位。**本变更唯一的实机验收项**——这三项验的是年龄对「非位置语义」的正确性，与位置抖动正交，候选 A 修不了、只有本变更能修，且可独立验证
- [x] 6.5 自动化 **MUST NOT 试图**覆盖：弹幕「看着是否平滑」「编队观感是否更好」——这两项只能实机人工判断
- [x] 6.6 `.\tools\gradle_task.ps1 build` 通过，退出码透传
- [x] 6.7 **记录诊断闸门的结论**（闸门的设计用途即在此，故 MUST 落成文字）：实测「重建弹的年龄偏移 p50=4 / p95=16」，且 `unseeded:n=0`、`ageValue[11-100:3954 101-500:2985]`——**年龄同步机制本身工作正常，剩余的位置抖动是另一个缺陷**。闸门给出「不是我」是成功结果，前提是它被记下来，否则下一个接手的人还会把这两件事当成一件

## 7. 收尾

- [x] 7.1 ~~重新评估 `danmaku-lag-smoothing` 的准入判据~~ **已定（2026-09-29，用户）**：该提案**实施候选 A**。其 `lag` / `hardCorrect` 读数在本变更修好之后才第一次具备准入意义——修复前它们被年龄失步污染
- [x] 7.2 复核 `danmaku-lag-smoothing` proposal 的前提句「客户端自 spawn 起已知道整条未来轨迹」：**该句在读档 / 重新获取场景下曾不成立，本变更已使其成立**。但它**不是**准入障碍——位置抖动的成因是速度阈值，不是知识缺失。改写时 MUST 把这两件事分开，否则候选 C（相位推前）会被当成解药，而它会把硬失败变成静默错渲
- [x] 7.3 决定 `advance` 无界增长：**本变更不修**（design.md 决策 4），代码旁已留指向该未修项的注释。是否另开变更由用户定，暂不处理
