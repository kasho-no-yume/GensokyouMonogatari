## 0. 前置：同步字段盘点（门槛）

- [x] 0.1 盘点 `AbstractDanmakuProjectile` 的 `SynchedEntityData` accessor 与 `DanmakuMotionState.PARAM_COUNT` 的占用，列出可复用的空闲位（尤其 `DATA_FLAGS`）与必须打包的字段
- [x] 0.2 为「前窗无害 / 后窗收束 / 曲射窗口」三项各确定一个**打包后**的同步表示（复用或新增一个 accessor），写成字段表（见 design.md「编排扩展的同步字段表」）
- [x] 0.3 判定：`PARAM_COUNT` 63→66，未超预算；曲射窗口的段式降级路径保留为备选（design D6）
- [x] 0.4 更新 `DanmakuMotionState` 的 `P_HARMLESS_UNTIL/P_COLLAPSE/P_CURVE_WINDOW` 与 `PARAM_COUNT`；指纹 `fingerprint` 遍历整块，自动覆盖新字段

## 1. 伤害窗口（前窗无害 + 后窗收束）

- [x] 1.1 在 `AbstractDanmakuProjectile` 增加前窗无害年龄字段与其同步/存档（`DATA_HARMLESS_UNTIL` + 参数块）
- [x] 1.2 在判伤入口判前窗：未到年龄则 return（不判伤、不销毁），沿用 `isWhitelisted` 的「穿身而过」语义
- [x] 1.3 在 `AbstractDanmakuProjectile` 增加后窗收束年龄与淡出时长字段及其同步/存档（`DATA_COLLAPSE` 打包 age<<16|fade）
- [x] 1.4 在判伤入口判后窗：越过收束年龄则 return（不判伤）
- [x] 1.5 新增 `DanmakuBudget.RemovalCause.COLLAPSE`，实体在收束年龄 + 淡出时长后以该死因回收
- [x] 1.6 客户端渲染：`collapseAge .. collapseAge+fade` 内线性降 alpha（`SphereDanmakuRenderer` / `LaserDanmakuRenderer` 乘 `collapseFadeFactor()`，不改判伤）
- [x] 1.7 `TrackRunner.emitTrack` 把 `beat.harmlessTicks()` 传给 `DanmakuEmitter`，并接线到 1.1 的字段
- [x] 1.8 `DanmakuEmitter.emit` 增加收束参数入口，由拍声明传入
- [ ] 1.9 单测：无害期内碰撞不结算/不消失；越过后恢复判伤；窗口不改变位置与方向
- [ ] 1.10 单测：收束年龄到点后不判伤、淡出后以 `COLLAPSE` 回收

## 2. 曲射时段窗口

- [x] 2.1 在 `Behaviour.Motion.curve` 增加窗口字段（起始年龄 + 持续时长），提供不改既有签名的重载（默认无窗口）
- [x] 2.2 `DanmakuEmitter` 把窗口写入弹体（`DATA_CURVE_WINDOW` + 参数块），并在 `AbstractDanmakuProjectile` 的曲射分支按年龄门控旋转
- [ ] 2.3 单测：窗口内每 tick 转向与同参数无窗口曲射一致；窗口外方向冻结；跨界无折角
- [ ] 2.4 单测：未声明窗口时行为与改前逐位相同（回归保护）
- [ ] 2.5 存档/重载：窗口参数往返一致

## 3. 锁轴组与目标快照

- [x] 3.1 `Beat` 增加编排字段 `Orchestration(int lockGroup, int leadTicks, int collapseAge)`（默认 `NONE`），保留既有构造重载
- [x] 3.2 `TrackRunner` 增加锁轴快照缓存 `Map<groupKey, LockSnapshot>`，键含轨道下标与 `lockGroup`
- [x] 3.3 在组内**最早那一拍**捕获快照：原点 `O`、基 `F/R/U`、各目标胸口点 `T`；组内其余拍复用
- [x] 3.4 快照在 `cycleTick` 越过循环边界、`selectCard`、`stop` 时清除（含 `DanmakuPreview` 的切卡路径）
- [x] 3.5 把快照的 `O/F/R/U` 传给 `Geometry.build`（替代当刻眼位与重算 forward），几何层不改
- [x] 3.6 目标胸口点取脚位上移 0.9 格；快照目标集合按 `lockedTargets` 口径
- [x] 3.7 共享轴节点：支持在同一快照帧内按 `O + x·R + y·U + z·F` 声明多个源点（由形状与偏移表达，快照提供基）
- [x] 3.8 子轮目标快照：快照保存各目标胸口点，目标死亡/退出时复制份数按快照解析，不转派他人
- [ ] 3.9 单测：组内后续拍复用快照（玩家移动不改变方向/原点）；次组重新捕获；循环边界清除
- [ ] 3.10 单测：锁轴下的多拍图案共圆/共面（用 `RingCardCrossBeatConsistencyTest` 的思路，断言漂移为 0）

## 4. 组级安全取消

- [x] 4.1 `TrackRunner` 在生成前对整拍 `Shot` 逐个算出生点到存活战斗玩家的净距（扣双方碰撞半径；目标口径同 `lockedTargets`，已跳过创造/旁观）
- [x] 4.2 任一发低于 `最小安全距离`（config，默认 6）⇒ 整组本轮不生成；不平移、不积压
- [x] 4.3 与符印联动：组被取消时符印到点自然消失（不再保留、也不转成真弹）
- [x] 4.4 config 增加 `danmakuMinSpawnDistance`（默认 6，允许 0 关闭）
- [ ] 4.5 单测：近身整组取消（含组内其他发也不生成）；距离足够即正常发射；配置为 0 时不取消

## 5. 发射源符印

- [x] 5.1 新增 `DanmakuTelegraph` 实体（继承 `Entity`）：`noPhysics`、无伤害（`hurt` 恒 false）、不可选中/不可推动、不参与 `DanmakuBudget` 计数
- [x] 5.2 注册实体类型（`ModEntityTypes.DANMAKU_TELEGRAPH`）与客户端渲染（复用 `BillboardRenderer`，无新资产）
- [x] 5.3 `TrackRunner.scheduleTelegraphs` 在 `step == beat.tick − leadTicks` 按锁轴规则捕获快照并生成符印；符印到点自然消失
- [x] 5.4 符印与真弹共用同一份快照（领示时刻先捕获，真弹复用，不重采样）
- [ ] 5.5 单测：符印在真弹前 `lead` tick 出现、原点一致、不受弹数上限抑制、不判伤
- [x] 5.6 config：`danmakuTelegraphEnabled` 全局开关（默认开）

## 6. 编排静态判据

- [x] 6.1 `TrackLint` 增加判据：锁轴组非空、组内拍可枚举
- [x] 6.2 `TrackLint` 增加判据：`leadTicks ≤ 组首拍 tick`
- [x] 6.3 `TrackLint` 增加判据：`collapseAge ≤ 符卡循环长度`（且收束需声明循环）
- [x] 6.4 单测：三条非法组合各被拒；合法组合通过（`OrchestrationExtensionTest`）；现有四只 BOSS 的符卡表 `lint` 全绿（`BossCardLintTest` 未受影响）

## 7. 验收命令与读数

- [x] 7.1 `/danmaku lock <beats> <interval>`：生成跨拍图案并打印读数，供横移校验刚性
- [x] 7.2 `/danmaku lock nodes`：以 `lock` 命令覆盖（同一锁定帧内多点由形状表达）
- [x] 7.3 `/danmaku lock` 已走 `TrackRunner` 锁轴路径（含 AIMED 目标快照口径）
- [x] 7.4 `/danmaku safety <offset>` 与 `safety watch` 均已实现（watch：走近走远观察整组开关）
- [x] 7.5 `/danmaku telegraph <lead> <count>`：生成符印与真弹，打印领示 tick
- [x] 7.6 `/danmaku harmless <window>`：生成带前窗无害的弹
- [x] 7.7 `/danmaku tail <life> <collapse> <fade>`：生成带后窗收束的弹
- [x] 7.8 `/danmaku curve-window <deg> <startAge> <duration>`：有界平滑转向测试弹
- [x] 7.9 `/danmaku flash <age>`：一次性闪亮测试弹
- [x] 7.10 `/gs_boss danmaku` 增加编排汇总行 `orchestration[...]`，并纳入 `danmaku reset` 清零
- [x] 7.11 命令命名与帮助文本与既有 `/danmaku` 族一致；权限 2

## 8. 回归与验收

- [x] 8.1 `tools/gradle_task.ps1 build` 通过；既有测试全绿
- [ ] 8.2 现有四只 BOSS 预览观感与改前一致（默认不声明即旧行为）
- [ ] 8.3 逐条跑 proposal「验收指令」表：8 条命令 + 汇总读数的**期望观测**全部成立，且**失败判据**均不出现
- [ ] 8.4 在生存模式、平地与起伏地各跑一次锁轴与安全取消验收（创造模式玩家不计入目标，会给出假绿）
- [x] 8.5 `openspec validate extend-danmaku-orchestration --strict` 通过
- [ ] 8.6 若任一验收失败：回写 spec/design（不留在注释里），并把失败项升级为独立缺陷变更
