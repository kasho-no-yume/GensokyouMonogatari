## 0. 前置：同步字段盘点（门槛）

- [ ] 0.1 盘点 `AbstractDanmakuProjectile` 的 `SynchedEntityData` accessor 与 `DanmakuMotionState.PARAM_COUNT`（现值 63）的占用，列出可复用的空闲位（尤其 `DATA_FLAGS`）与必须打包的字段
- [ ] 0.2 为「前窗无害 / 后窗收束 / 曲射窗口」三项各确定一个**打包后**的同步表示（复用或新增一个 accessor），写成一张字段表
- [ ] 0.3 判定：若打包后仍超预算，登记曲射窗口退回「段式运动近似」的降级路径（design D6 备选）并在本 change 内注明
- [ ] 0.4 若新增/改动参数块，同步更新 `DanmakuMotionState` 的 `P_*` 常量与 `PARAM_COUNT`，并确认指纹 `fingerprint` 覆盖新字段

## 1. 伤害窗口（前窗无害 + 后窗收束）

- [ ] 1.1 在 `AbstractDanmakuProjectile` 增加前窗无害年龄字段与其同步/存档（复用 0.2 的表示）
- [ ] 1.2 在 `onHitEntity`（服务端入口）判前窗：未到年龄则 return（不判伤、不销毁），沿用 `isWhitelisted` 的「穿身而过」语义
- [ ] 1.3 在 `AbstractDanmakuProjectile` 增加后窗收束年龄与淡出时长字段及其同步/存档
- [ ] 1.4 在 `onHitEntity` 判后窗：越过收束年龄则 return（不判伤）
- [ ] 1.5 新增 `DanmakuBudget.RemovalCause.COLLAPSE`，实体在收束年龄 + 淡出时长后以该死因回收
- [ ] 1.6 客户端渲染：`collapseAge .. collapseAge+fade` 内线性降 alpha（走既有 render state，不改判伤）
- [ ] 1.7 `TrackRunner.emitTrack` 把 `beat.harmlessTicks()` 传给 `DanmakuEmitter`，并接线到 1.1 的字段
- [ ] 1.8 `DanmakuEmitter.emit` 增加收束参数入口，由拍声明传入
- [ ] 1.9 单测：无害期内碰撞不结算/不消失；越过后恢复判伤；窗口不改变位置与方向
- [ ] 1.10 单测：收束年龄到点后不判伤、淡出后以 `COLLAPSE` 回收

## 2. 曲射时段窗口

- [ ] 2.1 在 `Behaviour.Motion.curve` 增加窗口字段（起始年龄 + 持续时长），提供不改既有签名的重载（默认无窗口）
- [ ] 2.2 `DanmakuEmitter` 把窗口写入弹体（走 0.2 的同步表示），并在 `AbstractDanmakuProjectile` 的曲射分支按年龄门控旋转
- [ ] 2.3 单测：窗口内每 tick 转向与同参数无窗口曲射一致；窗口外方向冻结；跨界无折角
- [ ] 2.4 单测：未声明窗口时行为与改前逐位相同（回归保护）
- [ ] 2.5 存档/重载：窗口参数往返一致

## 3. 锁轴组与目标快照

- [ ] 3.1 `Beat` 增加编排字段 `Orchestration(int lockGroup, int leadTicks, int collapseAge)`（默认 `NONE`），保留既有构造重载
- [ ] 3.2 `TrackRunner` 增加锁轴快照缓存 `Map<groupKey, Snapshot>`，键含轨道下标与 `lockGroup`
- [ ] 3.3 在组内**最早那一拍**捕获快照：原点 `O`、基 `F/R/U`、各目标胸口点 `T`；组内其余拍复用
- [ ] 3.4 快照在 `cycleTick` 越过循环边界、`selectCard`、`stop` 时清除（含 `DanmakuPreview` 的切卡路径）
- [ ] 3.5 把快照的 `O/F/R/U` 传给 `Geometry.build`（替代当刻眼位与重算 forward），几何层不改
- [ ] 3.6 目标胸口点取脚位上移固定高度；快照目标集合按 `lockedTargets` 口径
- [ ] 3.7 共享轴节点：支持在同一快照帧内按 `O + x·R + y·U + z·F` 声明多个源点（供多节点/多子图案）
- [ ] 3.8 子轮目标快照：目标死亡/退出只取消其余下个人拍，不转派、不改他人拍数
- [ ] 3.9 单测：组内后续拍复用快照（玩家移动不改变方向/原点）；次组重新捕获；循环边界清除
- [ ] 3.10 单测：锁轴下的多拍图案共圆/共面（用 `RingCardCrossBeatConsistencyTest` 的思路，断言漂移为 0）

## 4. 组级安全取消

- [ ] 4.1 `TrackRunner` 在生成前对整拍 `Shot` 逐个算出生点到存活战斗玩家的净距（扣双方碰撞半径；跳过创造/旁观）
- [ ] 4.2 任一发低于 `最小安全距离`（config，默认 6）⇒ 整组本轮不生成；不平移、不积压
- [ ] 4.3 与符印联动：组被取消时通知该组符印淡出（见 5.3）
- [ ] 4.4 config 增加 `danmakuMinSpawnDistance`（默认 6，允许 0 关闭）
- [ ] 4.5 单测：近身整组取消（含组内其他发也不生成）；距离足够即正常发射；配置为 0 时不取消

## 5. 发射源符印

- [ ] 5.1 新增 `DanmakuTelegraph` 实体（继承 `Entity`，非 `LivingEntity`）：同步位置/颜色/形状/剩余 tick/缩放，`noPhysics`、无伤害路径、不参与 `DanmakuBudget` 计数
- [ ] 5.2 注册实体类型与客户端渲染器（公告板符印，倒计时脉动）
- [ ] 5.3 `TrackRunner` 在 `cycleTick == beat.tick − leadTicks` 按锁轴规则捕获快照并生成符印；收到取消通知时淡出
- [ ] 5.4 符印与真弹共用同一份快照（真弹复用，不重采样）
- [ ] 5.5 单测：符印在真弹前 `lead` tick 出现、原点一致、不受弹数上限抑制、不判伤
- [ ] 5.6 config：符印是否存在全局开关（默认开）

## 6. 编排静态判据

- [ ] 6.1 `TrackLint` 增加判据：锁轴组非空、组内拍可枚举
- [ ] 6.2 `TrackLint` 增加判据：`leadTicks ≤ 组首拍 tick`
- [ ] 6.3 `TrackLint` 增加判据：`collapseAge ≤ 符卡循环长度`
- [ ] 6.4 单测：三条非法组合各被拒；合法组合通过；现有四只 BOSS 的符卡表 `lint` 全绿（不因新判据误伤）

## 7. 验收命令与读数

- [ ] 7.1 `/danmaku lock <beats> <interval>`：生成跨拍图案并打印锁定帧读数（原点/朝向），供横移校验刚性
- [ ] 7.2 `/danmaku lock nodes`：在锁定轴上放置多个节点供共线校验
- [ ] 7.3 `/danmaku lock aimed`：AIMED 版锁轴（多人场景验目标快照）
- [ ] 7.4 `/danmaku safety <offset>` 与 `safety watch`：按净距取消/放行，打印「组取消 dist < min」或成功
- [ ] 7.5 `/danmaku telegraph <lead> <count>`：生成符印与真弹，打印实测 lead
- [ ] 7.6 `/danmaku harmless <window>`：生成带前窗无害的弹
- [ ] 7.7 `/danmaku tail <life> <collapse> <fade>`：生成带后窗收束的弹
- [ ] 7.8 `/danmaku curve-window <deg> <startAge> <duration>`：有界平滑转向测试弹
- [ ] 7.9 `/danmaku flash <age>`：一次性年龄闪亮测试弹
- [ ] 7.10 `/gs_boss danmaku` 增加编排汇总行 `orchestration[lockGroups=.. safetyCancelled=.. telegraphs=.. collapseRemoved=.. curveWindowed=..]`，并纳入 `danmaku reset` 清零
- [ ] 7.11 命令命名与帮助文本与既有 `/danmaku` 族一致；权限 2

## 8. 回归与验收

- [ ] 8.1 `tools/gradle_task.ps1 build` 通过；`BossCardLintTest` 等既有测试全绿
- [ ] 8.2 现有四只 BOSS（`/danmaku card big_fairy|yamame|yakumo_ran|nomen_mask` 逐卡）预览观感与改前一致（默认不声明即旧行为）
- [ ] 8.3 逐条跑 proposal「验收指令」表：8 条命令 + 汇总读数的**期望观测**全部成立，且**失败判据**均不出现
- [ ] 8.4 在生存模式、平地与起伏地各跑一次锁轴与安全取消验收（创造模式玩家不计入目标，会给出假绿）
- [ ] 8.5 `openspec validate extend-danmaku-orchestration --strict` 通过
- [ ] 8.6 若任一验收失败：回写 spec/design（不留在注释里），并把失败项升级为独立缺陷变更，MUST NOT 在注释与实现分叉的情况下归档
