# 正式迁移 manifest（tasks 1.1–1.8）

> 权威数据源：`com.bitsson.gensokyou.danmaku.program.content.BossProgramManifest`。
> 测试 `ProgramManifestTest` 每次构建导出同一份数据到
> `build/reports/danmaku-program/content-manifest.json`。本文件是给人读的副本，
> **与代码冲突时以代码为准**。

## 0. 范围与授权

- 本轮正式迁移 **15 张符卡 + 2 条独立底噪**：大妖精 3、黑谷山女 4、八云蓝 8。
- 露米娅/傩面五卡**明确不迁移**（tasks 1.5：露米娅非正式符卡，后续整卡重做）。
  因此旧「20 卡表」在本轮落为 **15 迁移 + 5 排除**，不自动补全。
- 旧后端保留为默认，程序后端按 config 显式开启（见第 1 节）；同一 encounter 只有一个判伤后端。

## 1. 后端选择与回退入口（tasks 1.1、1.6）

| 项 | 入口 |
|---|---|
| 开战前选择 | `GensokyouConfig.BOSS_DANMAKU_BACKEND` = `legacy`（默认）/ `program` |
| 逐 BOSS 迁移声明 | `AbstractTouhouBoss#programContentId()`，由大妖精/山女/蓝覆写 |
| 会话/宿主 | `gameplay/BossProgramHost.open` + `BossProgramSettings.standard()`（同维度共用一份 settings） |
| 战斗封装 | `gameplay/BossProgramBattle`（切卡/挂起/移动锁/下标）与 `gameplay/BossProgramBackend`（宿主开关） |
| 回退 | 首卡安装前任何失败 → `programFallback`，本场改走旧 `TrackRunner`；**不半程热切** |
| HUD | `activeCardIndex()` 返回程序卡下标；名称键与旧表一致，客户端本地反查不变 |
| 移动锁 | `movementLocked()` 读程序控制轨；旧锁移入 `legacyMovementLocked()` |
| 旧弹加载标记 | `ProgramBackendState`（后端选择+当前卡）落 BOSS NBT；此外新增**运行态磁盘持久化**：共享 `ProgramSession` 快照经有界版本化 codec 存入每维度 `ProgramSessionStore`，重载恢复活弹/预警/派生/剩余寿命/卡进度并做离线时间对齐（见 §9）。旧 legacy 弹幕实体自身存档规则未改动。 |
| 预览统一 | `gs_program_preview start formal_big_fairy|formal_yamame|formal_yakumo_ran`：正式牌的卡 1 走与实战相同的 runtime/session/ops。 |

前置门槛：见 `add-danmaku-program-gameplay` tasks 第 1/2/3 节（用户授权收口）。本 change 不重跑 M2。

## 2. 已迁移内容（15 卡）

| owner | # | lang 键 | 起始占比 | 循环 tick | program id |
|---|---|---|---|---|---|
| big_fairy | 1 | spellcard.gensokyou.big_fairy.1 | 1.00 | 240 | gensokyou:big_fairy_ring |
| big_fairy | 2 | spellcard.gensokyou.big_fairy.2 | 0.66 | 240 | gensokyou:big_fairy_flower |
| big_fairy | 3 | spellcard.gensokyou.big_fairy.3 | 0.33 | 200 | gensokyou:big_fairy_rain |
| yamame | 1 | spellcard.gensokyou.yamame.1 | 1.00 | 200 | gensokyou:yamame_web_card |
| yamame | 2 | spellcard.gensokyou.yamame.2 | 0.75 | 260 | gensokyou:yamame_miasma |
| yamame | 3 | spellcard.gensokyou.yamame.3 | 0.50 | 260 | gensokyou:yamame_nest |
| yamame | 4 | spellcard.gensokyou.yamame.4 | 0.25 | 260 | gensokyou:yamame_fever |
| yakumo_ran | 1 | spellcard.gensokyou.yakumo_ran.1 | 1.000 | 240 | gensokyou:ran_1 |
| yakumo_ran | 2 | spellcard.gensokyou.yakumo_ran.2 | 0.875 | 240 | gensokyou:ran_2 |
| yakumo_ran | 3 | spellcard.gensokyou.yakumo_ran.3 | 0.750 | 240 | gensokyou:ran_3 |
| yakumo_ran | 4 | spellcard.gensokyou.yakumo_ran.4 | 0.625 | 240 | gensokyou:ran_4 |
| yakumo_ran | 5 | spellcard.gensokyou.yakumo_ran.5 | 0.500 | 240 | gensokyou:ran_5 |
| yakumo_ran | 6 | spellcard.gensokyou.yakumo_ran.6 | 0.375 | 240 | gensokyou:ran_6 |
| yakumo_ran | 7 | spellcard.gensokyou.yakumo_ran.7 | 0.250 | 240 | gensokyou:ran_7 |
| yakumo_ran | 8 | spellcard.gensokyou.yakumo_ran.8 | 0.125 | 240 | gensokyou:ran_8 |

底噪（独立实例，切卡不重置）：

| owner | program id | 说明 |
|---|---|---|
| big_fairy | gensokyou:big_fairy_wall | 5×5 角度网格弹墙，中心朝主目标，公共一份 |
| yamame | gensokyou:yamame_web_shot | 主目标方向蛛丝扇，公共一份 |
| yakumo_ran | （无） | 蓝关闭底噪 |

## 3. 逐卡差异 / 未表达能力登记

各卡 `notes` 已随代码导出。**本轮已补齐为通用能力**（扩展通用作者入口/op，禁止按卡特判）：

- 逐弹朝**真实采样目标点**：`Actions.DirectionRule.AIM_LOCAL` + `MemberProgram.aimAtTarget()`。
  服务端在发射时用组帧把目标点转成本地坐标（真实的源→目标向量，含距离与倾斜），随批次下发；
  客户端用同一点重建方向。测试覆盖距离 12/22 与倾斜目标 dy=0/6。
- 倾斜面板 `normalize(F±.22R)`：`Patterns.TiltedGrid`（按列给 `F±kR`；蓝 3 左/右、蓝 8 副）。
- 走廊方块净空并撤批：`CardProgram.corridor`（蓝 4 激活时）。
- 声音线索：`Presentation.SoundCue`（蓝 5 age30），经渲染投影到客户端。
- 穿透方块的球弹（蛛丝/瘴气）：`CollisionProfile.Projectile(piercesBlocks)` + `MemberProgram.piercing()`。
- 面板预警：每批逐成员的 age-zero 姿态（render warning）。
- **世界采样 → 成员落点**：`ServerControlOperations.SurfaceFire`（`CardProgram.surfaceFire`/`surfaceFireAtTargets`）。
  逐锚点解析 `FIRST_AIR_BELOW`（向下首个空气）/`FIRST_AIR_ABOVE`/`DOWNWARD_SOLID_TOP`；
  **每次调用只建 1 个普通组**（解析点作为显式成员表 `prepareExplicit`），因此预算与 legacy 同量级。
  包络/网络/存档走既有机制。
- 大妖精雨季精确迁移：雨=上方 **60** 格圆盘 24 点 `FIRST_AIR_BELOW` 后下落（顶棚/洞穴生成于空气夹层）；
  光柱=`DOWNWARD_SOLID_TOP` 地面点竖直向上，每第 5 拍逐玩家脚下；跨轮 RETAIN 到自身寿命（同 legacy）。

**仍存在的声明差异**（登记，不伪称等价）：

1. `yamame.1/.3`：周期内显式多拍（10/13 拍），相较旧「只放周期 0 相位」密度上升（有意修复）。
2. 各 BOSS 的底噪/FX 细节（九尾余辉、符印）属 FX，不在程序成员范围。

## 4. 旧表冲突回写（tasks 1.7）

| 范围 | 旧声明 | 迁移基线 | 依据 |
|---|---|---|---|
| yakumo_ran.8 | 表格共通行：终符 `.70` 大玉 | `.40` 普通弹、无大玉 | content-mapping §4 补充 |
| big_fairy.3 | 60 格高处向下首个空气 | 已按 `FIRST_AIR_BELOW`（向下首个空气）精确实现 | 通用 surface 能力扩展现已对齐 |
| yakumo_ran.4 | 激活前走廊方块净空校验 | 已用 corridor 实现 | 通用 op 扩展后对齐 |
| yamame.1/3 | 只放周期 0 相位 | 周期内显式多拍 | content-mapping §3 |
| nomen_mask.1-5 | 含于 20 卡 | 不迁移 | tasks 1.5 |

## 5. 验证证据

- 自动化（内存权威，非实机）：
  - `ProgramContentTest`：清单/下标/语言键、蓝 220 收束与 230 清场、选择规则、大妖精环 48/人 + 120
    锁位、山女 10 拍 60 根、蓝逐卡五人数量 `{540,144,138,62,108,96,174,612}`、蓝 1 出生 tick =
    映射拍点、蓝 4 corridor 撤批、卡 5 声音到达渲染投影、piercing 标志、**大妖精雨季 surface 落点
    （雨在高空、光柱在地面）+ 标准包络**、**蓝 8 副轨为单块 12 成员倾斜面板（列位 ±5、F±.22R）**、
    **收敛真实采样源→目标（距离 12/22、倾斜 dy=0/6）**、**卡 6 弧线在本地 F/R 平面且末端回 F**、
    **顶棚/洞穴下 FIRST_AIR_BELOW 与 FIRST_AIR_ABOVE/DOWNWARD_SOLID_TOP 三查询区分**、
    **雨季真实峰值落在原预算内（组数/成员）且过轮回收**、**预警未落点即被订阅**、
    卡内禁 `END_ENCOUNTER`、循环覆盖跨度、按血量装卡。
  - `ProgramManifestTest`：17 条目（15 卡 + 2 底噪）、排除 5 卡。
  - `CardTimelineContractTest`：乱序 `at`、引用前未创建被拒、END_AT_BOUNDARY 真清旧轮、
    RETAIN_UNTIL_LIFETIME 保留旧轮。
  - `ProgramExecutionValidationTest`：**故意乱序的嵌套 Parallel/At** 仍按真实时间序通过校验；
    group 早于 capture、capture 晚于使用均被拒。
  - `ProgramBackendStateTest`：后端标记 NBT 往返、旧存档缺键回退不报错。
  - `ProgramSessionCodecTest`：**真实写文件→读回→新实例解码→restore**；运动/re-aim 逐 tick 逐位一致、
    跨轮尾弹、派生尾珠、未出生预警窗口、剩余寿命随 rebase 保持；已结束 encounter 不复现；坏数据被拒；
    `persistentOnly` 过滤（持久+预览混合只留持久、纯预览为空、过滤后可往返）。
  - `ProgramResumeTest`：resume 不重复安装、**恢复真实 card 身份**（切卡结束旧卡、底噪仅一份）、
    不同 owner 独立且结束一个不波及另一个、**未加载 owner 在 grace 内不清**。
  - `ProgramSessionStoreTest`：无捕获时保留上一份不过期为空、live 捕获在 flush 时编码、坏 NBT 降级为空。
- wrapper 完整 build `build/agent-logs/20261018-174714-build.log` 通过（143 suites / 1118 tests /
  0 failures / 0 errors）；OpenSpec strict 通过；lang 审计仍只失败于既有 31 个
  `jei.gensokyou.recipe.zaohua_*` 数据派生键。

## 6. 未执行验收（不得伪称通过）

- 实机地形：雨/光柱（含顶棚/洞穴）、激光断口、piercing 穿墙在真实方块地形中的行为。
- 生存模式命中与伤害吞没（创造/和平/吸收）实机确认。
- 多人（2/5 人）可读性与帧率实机矩阵（自动化只覆盖权威数量与网络投影）。
- 远程客户端高延迟/断网观感。
- 旧存档中既有 legacy 弹幕实体在程序后端下的续行、以及程序模式 BOSS 读档续卡（NBT 标记已测）。

## 7. 迁移中修复/新增的框架能力（均带测试）

1. `author/CardProgram#compile`：绝对 `AtNode` 时间轴误用 `SequenceNode`（duration 求和）→ 多拍
   `loop()` 被拒；改 `ParallelNode` 并按 `(tick,插入)` 排序时间轴。
2. `server/ProgramExecution`：句柄校验改为**真实时间序**（按 `(domain, earliest, 插入)` 排序后遍历，
   Once 时按 `(earliest,插入)`）。不再依赖 AST 列表序，也不依赖 `CardProgram` 的排序。
3. `server/DanmakuWorldRuntime#endCycle`：只看 `Group.retain`，`CardProgram` 恒传 true →
   `END_AT_BOUNDARY` 不清旧轮长寿命成员；新增 `clearRetained`，`ProgramSession` 在 END_AT_BOUNDARY
   强制清旧轮。
4. `CollisionProfile.Projectile` 无穿墙标志；新增 `piercesBlocks` + `MemberProgram.piercing()` +
   `ProgramCollisionAdapter` 跳过方块裁剪。
5. 作者入口缺收敛/倾斜/走廊/世界采样落点：新增 `Actions.DirectionRule.AIM_LOCAL` +
   `MemberProgram.aimAtTarget()`、`Patterns.TiltedGrid`、`CardProgram.corridor`、
   `ServerControlOperations.SurfaceFire`（均为通用原语，不含按卡分支）。
6. `DecisionProvider.resolveSurface` 新增 `FIRST_AIR_BELOW`（向下首个空气），与
   `FIRST_AIR_ABOVE`/`DOWNWARD_SOLID_TOP` 三选一；精确表达「高处落雨」。
7. `SurfaceFire` 预算：原按「每解析点一组」会与 legacy 同寿命下超出 512 组上限；改为
   **每次调用单组 + 显式成员表**（`EmissionPlan`/`prepareExplicit`/`ReplicaState.validate` 改为 512 上限），
   并将 `BossProgramSettings` 配额**恢复原值** `(8192,512,512,512,40000)`；测试证明真实峰值与过轮回收。
8. 旧弹加载标记：`ProgramBackendState` + 实体 NBT 持久化/恢复；旧存档缺键回退。

## 8. 仍需人工实机的具体步骤

1. 附身/旁观：`/gamemode survival`，确认难度非和平，站在空旷处。
2. `/gs_program_preview start formal_big_fairy` → 观察 `花符[弹幕花环]` 共圆与重瞄；`stop` 后
   用正式战斗（config `bossDanmakuBackend=program`）走到**雨季喷泉**：确认雨从**60 格高空**落下、
   光柱从地面升起，每第 5 拍压在脚下；在洞穴/顶棚下确认雨生成于空气夹层而非方块内。
3. `/gs_program_preview start formal_yakumo_ran` 看仙狐思念收敛；正式战斗逐卡核对
   双使交替/交差余焰/狐狸妖怪激光（走廊被方块堵住时该批激光应消失）/留影（闪亮+音效）/双使回转
   （弧线预告）/双使合奏/九尾余光（副面板两端列 ±5）。
4. 多人：2/5 名玩家，核对个人槽不串槽、公共组只一份、HUD 符卡名与下标一致。
5. 读档：战斗中存档退出再进入，确认后端不回退、符卡不回到首卡；旧世界里既有 legacy 弹幕实体
   在程序后端启用后仍按剩余寿命续飞（`danmaku-age-continuity`）。
6. 运行态持久化（§9）：战斗中单人保存并完全退出到标题，再进入：确认活弹/预警/派生/剩余寿命与
   HUD 卡名延续、离线期间未消耗寿命、底噪与卡不重复安装；BOSS 死亡后退出再进入应无残留战斗。

## 9. 运行态磁盘持久化（设计、证据、限制）

**问题**：单人战斗中退出存档重进时，程序弹幕全部消失——原先只持久化了 backend/card 标记，
`ProgramSession`（活弹、预警、派生、未来调度、剩余寿命、卡进度）在内存中丢失。

### 设计

- **有界版本化 codec** `server/ProgramSessionCodec`：对 `ProgramSession.Snapshot`（含完整
  `DanmakuWorldRuntime.Checkpoint`）做定长字段序 + 显式 primitive 编码；嵌套类型经既有
  `OperationDataCodec`（`disk:nozzle`/`disk:rebased`/`disk:world_detached` 等），**不用 Java 对象
  反序列化**；程序定义用 `ProgramCodec`（含 schema）随快照存一次、解码时重新编译校验。所有长度
  先校验再分配；坏 magic/版本/截断/越界 → `IllegalArgumentException`，调用方记日志并安全降级为
  新会话（丢弃该快照），不崩溃。
- **每维度单槽** `gameplay/ProgramSessionStore`（`SavedData`）：因一个维度只有一份共享 session，
  故按维度存一份快照（含所有 owner 的 encounter），不是按 owner 各存一份。
- **写时机** `BossProgramHost`：`LevelEvent.Save`（世界写盘前，encounter 尚活动）与 `LevelEvent.Unload`
  各写一次；unload 只在仍活动时写，**绝不用空快照覆盖已有快照**。
- **恢复**：重载后首个 `open` 时读槽 → 解码 → **rebase** 离线时长 → `ProgramSession.restore`。
  rebase 会平移所有绝对服务端时刻（tick、plan birth、ownershipEnd、commands、emitter origin、
  clocks、`detached` 的 `Rebased`/`WorldDetached`、`GroupMotion`/`Layer`/`Nozzle`、cursor anchors 与
  nextTick），因此**离线不消耗寿命/预警/调度**；`runtime.tick()` 对齐到 `now-1`，使下一个权威边界
  能正常 `advance`。
- **接入而不重复安装**：`BossProgramController.resumed` + `BossProgramBattle.adopt` 绑定既有 encounter，
  **不再装底噪/卡**；HUD 卡下标由 `currentDefinitionHash` 反查 deck。
- **不恢复已结束战斗**：战斗结束时 encounter 被 end；此时写盘得到空 encounters → 清槽。
- **不同 owner 独立**：同一 session 内多 encounter 一并持久化/恢复；结束一个不影响另一个。
- **预览不持久化**：只有 `BossProgramHost` 写该槽，`ProgramPreviewHost` 不触碰。
- **实体卸载**：`AbstractTouhouBoss.remove` 只在 `KILLED`/`DISCARDED` 关闭战斗；区块/维度卸载不再关闭，
  以便 unload 时能持久化。

### 证据（自动化）

- `server/ProgramSessionCodecTest`：真实**写文件→读回→新实例解码→restore**，断言
  运动/re-aim 逐 tick 逐位一致、跨轮尾弹、派生尾珠、未出生预警窗口、剩余寿命随 rebase 保持一致；
  已结束 encounter 不再出现；空/短/坏版本字节被拒。
- `gameplay/ProgramResumeTest`：resume 不重复安装（instance 数不变）、HUD 卡下标由定义哈希恢复、
  不同 owner 独立且结束一个不波及另一个。
- 完整 build `build/agent-logs/20261018-172641-build.log`：142 suites / 1112 tests / 0 failures。

### 限制 / 未执行

- **实体+维度端到端（真实存盘重启）未纳入自动化**：需专用服务器进程；codec/restore/resume 已用真实
  文件 IO 与内存世界覆盖，但“单人退出→重进”仍列为 §8 的人工步骤。
- 恢复成功后立即清槽：若服务器在恢复后、下次保存前崩溃，该快照已消费，弹幕不再恢复（可接受并记录）。
- 快照版本 v1；未来不兼容时按“未知版本→丢弃并新会话”处理，不做自动迁移。
- owner 实体已不存在时，恢复出的 encounter 会在首个 tick 被 `owner-unavailable` 正常清理。

### 观察者复核后的修复（第二轮）

1. **controller 生命周期身份**：`BossProgramController.resumed` 现从 session 恢复 `card`（`issuedCard`）
   与 `backgroundInstalled`。恢复后 `card()` 结束的是真实旧卡（非 card 0），`background()` 抛“已安装”
   不再叠一层。测试 `resumedSwitchEndsTheRealPreviousCardAndKeepsOneBackground`。
2. **预览不写盘**：不再靠“预览不调用 store”。`ProgramPreviewHost` 改走 `BossProgramHost.openEphemeral`；
   宿主区分持久/临时 owner，落盘前用 `ProgramSessionCodec.persistentOnly` 只保留持久 encounter（及其组/成员/
   派生/句柄过滤），纯预览结果为空不写。测试 `persistentFilterDropsEphemeralEncounters`。
3. **owner 加载次序**：恢复时为每个 encounter 设 `ownerGrace`（600 tick）。`terminateInvalid` 在 grace 内
   不结束该 owner，也**不使其目标失效/停发**；`Capture`/`Group` 在 owner 位姿不可得时安全 no-op
   （`ownerPoseOptional`）而非崩溃。测试 `restoredOwnersSurviveUntilTheirEntityLoads`（100 tick 内不清，
   超 grace 后清理）。
4. **close 保留未绑定恢复**：仅当 session 再无活动 encounter 才清槽并卸载宿主；否则保留宿主等待 owner
   绑定，仅 `markDirty`。
5. **不再恢复即清槽**：去掉 `restore` 后的 `clear()`；上一份有效快照保留到下一次有效 flush 覆盖或真正结束
   （`clear`）。终止后槽为空 → 不复活。
6. **落盘时机（源码核实）**：NeoForge 21.1.248 `ServerLevel.save` 先 `saveLevelData()`（flush
   `DimensionDataStorage`）**再** post `LevelEvent.Save`。故 store 改为持 **live `Capture`**：flush 时
   编码当前 session；宿主在 `runtime.revision()` 变化时 `markDirty`，保证常规自动保存/关服会写入。
   `unload` 只把最新编码放进 `pending` 并 `detach`，绝不以空覆盖。

**新增限制**：恢复后、下一次 flush 前进程崩溃仍会用旧快照（不再是“清槽丢失”）；grace 期间若 owner
始终不出现，600 tick 后该 encounter 被清理。


