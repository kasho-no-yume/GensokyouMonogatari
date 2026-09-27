## 0. 前置核验（阻塞后续几何与触发体决策）

- [x] 0.1 **触发体机制已核验。** `Entity.checkInsideBlocks()`（`Entity.java:1027-1057`，由 `Entity.move()` 末尾 `tryCheckInsideBlocks()` 驱动）取实体 AABB 圈出逐格整数区间，对区间内每个方块位置**无条件**调用 `blockState.entityInside(...)`，**不经过任何碰撞形状过滤**。推论：① 触发体恰为「放了 sukima 方块的格子」，这也正是 `noCollission()` 下仍能传送的原因；② 放大视觉**不会**放大触发体，`getShape` / `getCollisionShape` 对此无效 → **原「覆写 getShape 使触发体与眼同高」的设想不成立，已从设计与 spec 中移除**；③ 无需任何额外代码，把方块放在该格已落于可见眼形内的位置即可。放置点定为 `corePos.above(2)`：pattern 在 `(0, 2..7, 0)` 留了 6 格竖直空气井（键 `g` = `gensokyou:air`），即结构自带的「裂口笼」，2× 眼高 4 格完整落入且不与 xz 距离 2 处的四根界柱相交。详见 design.md D6。
- [x] 0.2 **BE 注册无需改动。** ticker 挂在 `SukimaBlock.getTicker`（该类已 `implements EntityBlock`）；`ModBlockEntities.SUKIMA` 的 `BlockEntityType` 构造与 `SukimaBlockEntity::new` 工厂签名不变。
- [x] 0.3 **门体动画状态走自有 update tag。** `RitualCoreBlockEntity.getUpdateTag`（`:1418-1424`）只写 `Rendu` 一个键，不复用核心渲染态通道。
- [x] 0.4 跑 `python tools/validate_ritual_pattern.py` 对改后 pattern 做加载期校验，确认 `requirements` 段被接受。**基线（改前）无 ERROR/WARN；改后（含 requirements）仍无 ERROR/WARN。**

## 1. 配置

- [x] 1.1 新增 `BARRIER_CAPACITY`（默认 5,000,000）、`BARRIER_DRAIN_PER_SECOND`（默认 150,000）、`BARRIER_SUPPLY_HINT`（默认 6,000,000），置于既有 `BUILDER.push("barrier")` 组内。
- [x] 1.2 新增 FX 组项 `SUKIMA_PORTAL_OPEN_TICKS`（40）、`SUKIMA_PORTAL_LID_TRAVEL`（0.35）、`SUKIMA_PORTAL_MOTES_PER_SEC`（30）、`SUKIMA_PORTAL_BURST_TICKS`（60）。
- [x] 1.3 新增 `BARRIER_PORTAL_SCALE`（默认 2.0，写入时乘以 1.0 基准；实际标量 = 配置值）。
- [x] 1.4 删除 `BARRIER_SP_COST` 声明与定义（唯一消费者为将重写的死代码），确认无其他引用。
- [x] 1.5 全部数值进 COMMON 配置，MUST NOT 硬编码（`150,000` / `5,000,000` / `6,000,000` / 尺寸 / 时长 / 粒子率）。

## 2. Pattern 数据

- [x] 2.1 **改为无序计数**（实机反馈后重做）：pattern **不**声明 `requirements`；祭品规则为「8 台上任意 4 台星银 + 任意 4 台潮汐晶」，由 `BarrierBreakBehavior` 持有。原因见 design.md D4——`requirements` 只能一条绑一 slot（天然有序），实机表现为「4✓4✗」且玩家无从下手（**「我哪知道是哪四台」**）。界面同时从 8 行匿名 ✓/✗ 改为 2 行带计数。
- [x] 2.2 保持 `tiers: [2]`、`toggleable: false`、palette 与几何不变（重跑生成器后 548 格、y-2..y7、连通分量 129/129 均与改前一致）。
- [x] 2.3 同步更新 `tools/gen_barrier_break.py`（注释写明「刻意不声明 requirements」及其理由），重跑生成器不会覆盖任何东西。
- [x] 2.4 初版曾用 per-slot 绑定，已废弃。规范序**不按轴/斜分组而是交错**（外环 = {0,3,4,7}、内环 = {1,2,5,6}）这一事实保留为测试断言，作为「为何不用 slot 绑定」的佐证。
- [x] 2.5 **离线回归测试** `BarrierOfferingSlotTest`（6 条）：台位恒 8 个（外环 r=6 ×4 + 内环 r=4 ×4）/ **pattern MUST NOT 声明 `requirements`（反向护栏）** / 每类需求量 = 台位总数一半 / 不依赖 consume 模式 / `toggleable=false`+`tiers=[2]` / 规范序交错防回归。
- [x] 2.6 **祭品在开门瞬间被消耗**（实机追加）：每类各扣 4 件，单件不变量（逐台扣 1 件），多放份数保留；闩锁既成后不再校验。`Offerings` 因此记录两类祭品**分别位于哪些台位**而非只记件数。
- [x] 2.7 **【事故防复发】孪生门落点扫描的终止性断言**：断言 `surfaceProbeOffsets(12)` 恰返回 `1 + 4·12·13 = 625` 个候选、中心格在首位、无重复、无越界，并逐圈校验（第 r 圈恰 8r 个格，r=0 为 1）。这把「循环必须终止」从代码评审的注意点变成了 CI 断言——若有人再把步长写成会退化的形式（如 `dx += r` 且 r 从 0 起），测试即失败。
- [x] 2.8 **【事故防复发】门柱插座必须容得下门自己。** 门要放进图案声明为空气的 `(0,2,0)` 插座格，但初版双重失配：（a）生成器把 palette 拼成**并不存在**的 `gensokyou:air`，加载器只认 `minecraft:air`/`air` 为 AIR 谓词，其余走 defaulted 注册表回退成「EXACT 空气」——一个拼错的资源 id 静默换掉了谓词语义，且会让搭建器把空气算进材料清单；（b）`Kind.AIR` 只判 `state.isAir()`，隙间塞不进去，仪式当场不成型。修法：生成器改用 `minecraft:air`；`Predicate.test` 的 AIR 分支改为 `state.isAir() || state.is(ModBlocks.SUKIMA.get())`（隙间无形、无碰撞、无掉落，概念上就是空间的一个洞）。护栏见 2.9。详见 design.md D5.2。
- [x] 2.9 **【事故防复发】palette 空气 id 与插座位置断言**：`portalSocketUsesRealAirPredicate` 扫全 palette 拒绝任何自造 `:air` id；`portalSocketSitsDirectlyAboveTheCore` 钉死核心正上方两格必为门柱插座。
- [x] 2.10 **【事故防复发】客户端渲染不得判 `isClientSide`**：`SukimaPortalRenderer.render()` 初版开头 `if (level == null || level.isClientSide) return;`——而 BER 的 `render()` 本就只在客户端调用，该判断恒真，整个方法体从未执行（症状：门可用、传送正常、唯独零特效）。已移除该判断（保留 null 兜底）。一般教训写进 design.md D5.3：在渲染/同步回调里，侧别判断只能切换**局部**行为，绝不能 `return` 掉方法主体。
- [x] 2.11 **【锯齿回归护栏】眼睑贴图分辨率与抗锯齿**：`eyelidTextureIsHighResolutionWithAntialiasedEdges` 仅依赖 PNG IHDR 宽高与文件体积（无需图像库），断言 1:2 宽高比、宽 ≥64 / 高 ≥128、体积 >1000 字节。若有人把生成器改回 16x32 硬边版本（178 字节）即失败。详见 design.md D5.5。
- [x] 2.12 **【开闭语义护栏】内景不得与眼缘脱钩**：`voidInteriorStaysRegisteredWithTheRim` 扫描 `lensExtent` 在 travel ∈ {0, 0.35, 2.0} 下的全部输出（含过冲段 >1），断言 `voidUpHalf == 2 * lidUpQuadHalf` 恒成立。结构上由 `lidUpQuadHalf` 委托 `voidUpHalf` 保证，不依赖两处常量同时正确。配套 `rimKeepsBoundedOvershoot` 钉住过冲存在且有界（1.0 < peak < 1.35）。见 5.18。
- [x] 2.13 **【架构护栏】编排密度必须与帧率无关**：`emitShatterFx` 只在 BE 的 `fxTicks` 变化时按差值补发（BE 增转态 `lastEmittedFx`，丢包时最多补 2 步）。若无此保护，144fps 下粒子量为 20 tick 的 2.4 倍。
- [x] 5.7 **末影龙死亡爆炸演出**（实机追加）：照搬 vanilla 配方——每 10 tick 在门周 `EXPLOSION_EMITTER`，末段 14 tick 每 tick 一次；偏移按 `scale` 缩放以铺满眼形。**无自定义着色器、不破坏方块**（`HugeExplosionSeedParticle` 是 `NoRenderParticle`，不生成爆炸实体）。详见 design.md D5.4。
- [x] 5.8 **爆发与张眼分离为两条时钟**（实机追加）：`SukimaBlockEntity` 增 `fxTicks`（开门即走，驱动爆炸）与 `openDelay`（耗尽前 `openTicks` 恒 0，眼形闭合）。二者不参与同步也不持久化——延迟期间客户端凭 `openTicks == 0` 自然停在闭眼态。延迟取 `SUKIMA_PORTAL_BURST_TICKS`(60 tick = 3s)，并保留 12 tick 重叠使「余烬未散时眼缝裂开」。详见 design.md D5.6。
- [x] 5.9 **眼睑贴图抗锯齿**（实机追加）：`sukima.png` 由 16x32 改为 128x256，同一套解析轮廓以 8 倍分辨率 + 子像素覆盖率重栅格化（16 级 alpha、929 个抗锯齿像素、3.7 KB）。UV 在渲染器中已归一化，故**无需改任何渲染代码**。详见 design.md D5.5。
- [x] 5.10 顺手修掉客户端紫色爆发的静默截断：时长原为 `BURST_TICKS(60)` 但张开仅 40 tick，尾部被砍且进度 `t` 永远到不了 1；现取 `min(OPEN_TICKS, BURST_TICKS)`。
- [x] 5.11 **[已撤下] 补齐末影龙死亡音效**（实机二次反馈「不震擂」的中间结论）：查证确认“压胀的分量全在音轨上”——vanilla 在开场播 globalLevelEvent(1028)，客户端将其映射为 ENDER_DRAGON_DEATH @ 音量 5.0。粒子只负责「亮」，音轨才给分量。但用户明确拒绝直接复用 vanilla 演出资源，故已整体撤下（见 5.15）。保留本条作为查证记录。
- [x] 5.12 **「复用末影龙本体」经查证不可行**：vanilla 无可复用的死亡特效对象；DragonDeathPhase 挂在 EnderDragon 实例上，构造函数与 doServerTick 均要求非空 EndDragonFight（否则 NPE），且该相位会传送龙并置血为 0 触发真实死亡链。结论写入 design.md D5.7。
- [x] 5.13 纠正一处想当然：`ServerLevel.sendParticles` 的 9 参重载末参是 `speed` 而非 `maxDist`，可见性由内部 32 格 `closerToCenterThan` 保证（曾误以为传 0 会剔除全部粒子）。

## 3. 核心方块实体与方块

- [x] 3.1 `RitualCoreBlockEntity` 新增闩锁持久化布尔字段 + getter/setter + `saveAdditional`/`loadAdditional` 分支；缺字段时按未闩锁处理（向后兼容既有存档）。
- [x] 3.2 `getCapacity()` 新增 `BARRIER_BREAK` 分支返回 `BARRIER_CAPACITY`。
- [x] 3.3 `RitualCoreBlock.onRemove` 追加传送门清理：主世界侧 + 幻想乡侧，经 `serverLevel.getServer().getLevel(gensokyoKey)` 跨维度移除，幂等。
- [x] 3.4 确认闩锁字段不参与 `enabled` 的任何判定路径。

## 4. 行为重写（BarrierBreakBehavior）

- [x] 4.1 删除 `onStart` 及其中的 `GraceService.requireGrace` 拦截与 `SpiritPowerHelper` 三段式扣费。
- [x] 4.2 `usesCoreSocket()` 返回 **`true`**（实机后按用户要求开放核心灵力槽；`refillsCacheFromSocket()` 返回 `true`，走电池→缓存方向，tick 内**先补料后扣费**）。详见 design.md D1.1。
- [x] 4.3 `refillsCacheFromSocket()` 返回 `true`（电池→缓存；与 `tickBatteryAutoFill()` 方向相反，二者 MUST NOT 同时启用，否则形成「缓存↔槽核」闭环空转）。
- [x] 4.4 `spiritInRatePerSecond` 动态解析（**路由口径**）：扫描 `RitualCoreRegistry.formedWithin` 得附近已成型 `BAFANG_GUIYUAN` 核心，取其聚合 outRate 求和；无合格归元返回 0。**刻意不含槽核速率**——槽核不经路由器，计入会虚增汇端预算。`Supply` 另统计 `bankStored` 与 `socketStored/socketRate` 供界面呈现（空核不报速率，与路由器源筛选 `getStored()>0` 同口径）。
- [x] 4.5 `serverPassiveTick` 实现充能态扣费：每秒扣 `BARRIER_DRAIN_PER_SECOND`（×1000 定点或整除均可，`150,000` 与 20 tick 互质整除，无需 carry）。
- [x] 4.6 状态机：NO_NETWORK / INSUFFICIENT / CHARGING / AWAITING / OPEN；OPEN 后停止扣费并置闩锁。
- [x] 4.7 祭品轮询 ≥5 Hz（`ageTicks % 4 == 0`），调用行为侧无序计数 `offerings(...)`；CHARGING 与 AWAITING 均持续轮询。
- [x] 4.8 达成条件时（`open`）：置闩锁 → **扣除祭品（每类 4 件）** → 在 `corePos.above(2)` 与幻想乡侧落点旁 4 格处各放置一扇 `sukima`（写入尺寸标量，置为开启态）→ 击退 + 分层音效 + 向附近玩家播报。
- [x] 4.9 幻想乡侧落点复用 `getHeightmapPos(MOTION_BLOCKING, BlockPos.ZERO)` 并横向偏移；放置前过一遍安全落点校验（脚下实心、头顶两格空气、非流体系、最低高度以上）；已存在门则跳过并在界面提示。开门那次允许跨维度区块加载，稳态复查仅在区块已加载时进行（否则无人在幻想乡时该区块会在加载↔卸载间反复抖动）。**落点搜索已抽为纯函数 `surfaceProbeOffsets`（见 4.13）。**
- [x] 4.13 **【事故修复】孪生门落点搜索曾冻结全世界。** 初版为内联三重 `for`，环半径同时充当步长（`dx += r`）而 r 从 0 起 → `r == 0` 时步长为 0、内层循环永不推进。该函数位于 `open()` 的服务端主线程路径上，实测开门瞬间世界静止、存档无法进入（主线程不返回，autosave/`/save` 排不上队，日志无任何线索）。修复：抽为世界无关纯函数 `surfaceProbeOffsets(maxRadius)`（中心格单独处理，r 从 1 起、步长恒为 1），候选数恒为 `1 + 4R(R+1)`（R=12 → 625），**可断言**。防复发见 2.7。详见 design.md D5.1。
- [x] 4.14 已全仓扫描同型写法（循环变量兼任步长）：仅 `SpawnSafetyHandler:51-52` 形似，但其步长为 `Math.max(16, r/4)`、r 从 64 起、且有 `break outer`，安全无虞。
- [x] 4.10 `onStructureLost` 分流：`core.activeMatch() == null` → 保留闩锁与门；非 null（变成别的仪式）→ 清闩锁并向两扇门请求关闭。
- [x] 4.11 `onFormed` 无操作（占位扩展点保持）。
- [x] 4.12 导出 `public static String debugSummary(...)` 单行 `key=value`。

## 5. 隙间方块实体与方块

- [x] 5.1 `SukimaBlockEntity` 升为持有状态：`scale`（默认 1.0）、`openTicks`、`closingTicks`、尺寸 getter/setter、save/load 分支。
- [x] 5.2 新增服务端 ticker：推进 `openTicks` 至满；`closingTicks > 0` 时倒数并在归零时 `removeBlock`。
- [x] 5.3 实现 `getUpdateTag`/`getUpdatePacket` 同步 `scale` / `openTicks` / `closingTicks`（自有 tag，不复用核心渲染态）。
- [x] 5.4 **结论：不需要覆写 `getShape`。** 0.1 已证实 `entityInside` 逐格无条件派发、碰撞形状完全无关，触发体恒为「放了 sukima 方块的格子」。方块置于 `corePos.above(2)`（pattern 自带的 6 格裂口笼空气井底部），该格在视觉上已落在眼形下半部内 → 「站在眼中」。零额外代码。
- [ ] 5.5 跨维度 `setBlock` / `removeBlock` 的区块加载开销**留待实机观察**（§9.7）。当前实现为开门/关门各一次跨维度 `setBlock`，属单次事件；若实测在主世界核心旁造成可见卡顿，再改为投递到目标维度 tick 队列。
- [x] 5.6 确认 `noLootTable` + 不可破坏属性保持不变。

## 6. 渲染

- [x] 6.1 `SukimaPortalRenderer` 的 `HALF_W` / `UPPER_LID` / `LOWER_LID` / `CENTER_Y` / `RECT_HALF_H` 全部乘以 `scale`，默认 1.0 时与变更前逐项一致。
- [x] 6.2 `getRenderBoundingBox` 随 `scale` 放大。
- [x] 6.3 `SukimaPortalQuads` 新增 UV 子区间参数（起点 + 尺寸），MUST NOT 引入 `SukimaBlockEntity` 专属依赖。
- [x] 6.4 **实现修正：不需要新贴图。** 眼睑切分改由 `SukimaPortalQuads.drawUvRange` 的 UV 子区间完成（`V_TIP = (UPPER/(UPPER+LOWER)) = 0.425`，与 `sukima.py` 的 `_TIP_ROW=13.6/32` 同源），原 `sukima.png` 保持不变。design D7 中「输出上下两张贴图」是实现期的猜测，UV 子区间以零新资产达成同一效果且不会被生成器覆盖，故不改贴图、不改 `sukima.py`。
- [x] 6.5 眼睑上下两片已实现。**实现修正：沿中线「平移」改为「压扁」。** 眼尖本就落在中线上、两片自然位置已以中线相接；若只做平移，闭合时上片会滑到下片所在位置，把上眼睑的弧形画在下方——读作眼睛翻转而非闭合。压扁则两片各自向中线收拢，`s=0` 时精确塌成中线上的一条缝。UV 区间与被压扁的 quad 高度成正比，故压扁是**等比**的，不产生形变错觉。
- [x] 6.6 `open` 缓动曲线用标准 **easeOutBack**（`c1 = 1.70158 × (1 + lidTravel)`），过冲幅度由 `sukimaPortalLidTravel` 线性缩放，0 即无过冲；全程 `SUKIMA_PORTAL_OPEN_TICKS`。关门方向复用同曲线反向，故闭眼亦带回弹。
- [x] 6.7 内景几何按同一 `open` 缩放。
- [x] 6.8 启动爆发：纯客户端，由 `openTicks` 驱动；白闪 + 多圈冲击环扩张 + 紫色余烬长尾，`SUKIMA_PORTAL_BURST_TICKS` 全程；非破坏性 + 实体击退；分层音效。
- [x] 6.9 环境粒子：BER 内 `addParticle`，`DustParticleOptions(紫)` 沿眼睑椭圆轨道公转 + 上浮，`SUKIMA_PORTAL_MOTES_PER_SEC` 定点发射；密度随 `open` 渐入。
- [ ] 6.10 目检确认（**需实机**）：默认 1.0 门与变更前视觉一致；2.0 门横竖各放大一倍；开合动画读作眼睑开合而非整图缩放；爆发与紫色微粒观感达标。

## 7. GUI

- [x] 7.1 `uiInfo` 追加供能诊断行（≤3 行）：状态行 + 蓄能进度条 + **入流分列行（路由 %s + 槽核 %s，提示内含归元托管核数/存量、槽核存量/输出）**。`INSUFFICIENT` 用红色并给差额。
- [x] 7.2 五态状态行；`INSUFFICIENT` MUST 显式表达，否则玩家把进度条倒退误判为缺陷。`NO_NETWORK` 的文案**不点名任何品阶**（design.md D3.1）。
- [x] 7.3 蓄能行用既有 `InfoLine.progress`（`BafangGuiyuanBehavior:401,412-421` 模板）：可见行 `compact`、提示行原始值。
- [x] 7.4 祭品区改为**两行带计数**的 `CONTROL_ITEM`（`BarrierBreakBehavior.offerRow`）：「奉上 3/4」+ 图标 + ✓/✗，提示内写明「不拘台位，任意摆放即可；开启瞬间即被消耗」。因 pattern 已无 `requirements`，`defaultUiInfo` 不产出祭品行，故由行为侧自行给出。
- [x] 7.5 幻想乡侧门被跳过时给出状态行提示。
- [x] 7.6 宽度假定：带 progress 条的行可见文字 ≤5 汉字。

## 8. lang 与指导书

- [x] 8.1 `zh_cn.json` 补齐：五态状态键、供能诊断行键与提示键、祭品需求文案、爆发相关提示。
- [x] 8.2 复核并改写既有 `gensokyou.book.entry.ritual.barrier_break_circle.text`：改为**两条供灵途径**（灵力槽 / 网络）的机制陈述 + 5,000,000 缓存 + 150,000 流失 + 三核同半径约束 + 闩锁语义。**MUST NOT 点名任何具体品阶的灵力核心**——本仪式是进入幻想乡的入口，供灵物必须在此之前可得（design.md D3.1）。已加「文案不得承诺不可得品阶」的自检场景。
- [x] 8.3 `python tools/gen_ritual_multiblock.py --ritual barrier_break_circle.json ...` 生成结构页与阶级参数页（sortnum 19，entry_gate `guide/end_unlock`，2 页）。
- [x] 8.4 `RitualTierComponent` 追加 barrier 分支：显示缓存容量 / 自然流失 / 受灵上限（**写「视归元托管数而定」而非具体数字**——它随托管核变动，写死即谎报）/ 建议备料。
- [x] 8.5 `python tools/gen_ritual_book_entries.py` 批量重生成条目（勿手改条目 JSON）。
- [x] 8.6 条目正文写明：两条供灵途径、5,000,000 缓存、每秒流失 150,000、建议备料 6,000,000、三核须同处一塔覆盖半径内、闩锁后不可逆。**通篇不出现具体品阶**。
- [x] 8.7 `python tools/lang_audit.py` 退出码 0。
- [x] 8.8 `en_us.json` 按需同步，允许滞后——本变更全部新增文案仅落 `zh_cn`（zh-only +15），`lang_audit` 口径为 en ⊆ zh，退出码 0。

## 9. 调试与验证

- [x] 9.1 `/gs_debug barrier <x> <y> <z>` 探针：单行 `state=… stored=… cap=… drainPerSec=… bank=… bankStored=… bankOut=… socketStored=… socketOut=… inRate=… net=… latch=… portal=… twin=…`。
- [x] 9.2 `python tools/validate_ritual_pattern.py --test-out` 生成 e2e 数据包，确认 pattern 成型/负查通过。
- [x] 9.3 `./tools/gradle_task.ps1 compileJava` 零错误。
- [ ] 9.4 实机：无归元时进度条不动且界面有指引 → 归元 2 核时进度条倒退且提示供灵不足 → 3 核时缓升 → 4 核时 47 秒充满 → 祭品齐则开门、缺一则待献祭 → 补齐即开。**注：末段（4 核 47 秒）依赖 `spirit_core_2` 可达，当前内容状态下无法在纯净生存复现，见 design.md D3.2；实机须由测试环境 `/give` 供给。**
- [x] 9.4a **祭品「4✓4✗」已定位并结案**。实机转储证据显示 8 行 `need=` 全部与几何吻合——绑定本身无误，4 个 ✗ 全是物品放反。用户指出根因：**「祭品台本来就该无序，有序的就是有问题的」**。遂废弃 per-slot 绑定，改为行为侧无序计数（见 2.1），界面同步改为两行带计数。`/gs_debug barrier` 的逐台位转储保留为诊断手段（末行给计数判据）。
- [ ] 9.4b 实机（新）：核心灵力槽放入灵核后，`/gs_debug barrier` 的 `socketStored/socketOut` 随之变化，缓存按该速率上升；空核时 `socketOut=0`（不虚报）。
- [ ] 9.5 实机：祭品**无序**摆放（4 星银 + 4 潮汐晶任意分配、内外环混杂）→ 判定满足；缺一件 → 待献祭；补齐即开。开门瞬间**祭品被扣除**（每类 4 件，多放保留），闩锁后再拆柱补回 → 门仍开、祭品无需重奉。
- [ ] 9.6 实机：把核心改造成别的仪式 → 两扇门闭眼后自删；挖掉核心 → 同样自删且幻想乡侧无残留。
- [ ] 9.7 实机：传送往返 —— 主世界进幻想乡，落地不在门内，返程可回主世界。
- [ ] 9.8 实机：两套结界仪式共存时幻想乡侧不互相覆盖。
- [ ] 9.9 目检：默认尺寸的隙间门（如由其他来源放置）视觉与变更前一致。

## 10. 依赖

- [ ] 10.1 `tune-resonance-relay-radius` 落地后方可实机验证三节点布局（2 阶 ±40 覆盖）。
- [ ] 10.2 `add-ritual-stone-higher-tier-recipes` 与 `add-sukima-fragment-source` 落地后方可从零跑通生存链路；在此之前实机验证依赖测试环境供给 `ritual_stone_2` 与 `spirit_core_2`。
- [x] 5.15 **[已被 5.19 取代]** 「结界崩解」三拍编排（服务端版）：蓄能 → 崩解 → 余波。**该版的三个偏差导致实机仍无「白光球 + 白光柱」**：粒子紫而非白、壳层压扁且粒子相对缩小（读作「一团小光点」而非「一个球」）、光柱只有 2 个水平方向而非「四周」。
- [x] 5.17 **[已作废]** 粒子改为纯位置投放。**其「零往返」结论是错的**——纯位置投放恰恰**要求**服务端逐粒发包，3850 粒即 3850 个包。正确做法见 5.19。
- [x] 5.18 **【修自己的 bug】内景与眼缘脱钩**（实机连续三次反馈「开闭不正确、像上下浮动」）：结构上开闭轴本就随整体绕 Z 倾 10°（与用户所述一致），真缺陷是上一轮给内景虚空与眼睑外框用了两条不同曲线，而两者在几何上是同一条边界（`虚空上缘 = UPPER_LID*scale*s = 2*(0.5*UPPER_LID*scale*s)`）。`s` 不同则黑色内景溢出或小于眼缘，表现为开闭进度错位——看起来像轴向错了，实为常量错位。修法：统一为 `lensExtent(t, travel)`，并让 `lidUpQuadHalf` 委托 `voidUpHalf`，使 2:1 成为结构上不可脱钩的事实。保留横向恒满宽。详见 design.md D5.10。
- [x] 5.19 **「结界崩解」编排迁客户端并改为白光**（实机第四次反馈）：整套粒子编排移到 `SukimaPortalRenderer#emitShatterFx`，服务端只剩三条音效与 `fxTicks`（新增同步）。**理由：数千粒走服务端就是数千包/tick，客户端本地产生是零往返。** 崩解段现为：全星球壳 22 颗/tick（粒子分四档尺寸 1.8→2.8→3.9→5.2 随半径递增，相邻 tick 叠加成厚光壳）+ 每 2 tick **十二个全球方向**白光柱（每条 18 颗）+ 每 4 tick 水平冲击环。单 tick 约 500 粒。详见 design.md D5.9。
- [x] 5.20 **【架构陷阱】BER 每帧调用 vs 密度必须每 tick**：`render()` 每帧跑，若每帧都执行一遍编排，144fps 下粒子量是 20 tick 的 2.4 倍。故 BE 增转态 `lastEmittedFx`，只在 `fxTicks` 变化时按差值补发（丢包最多补 2 步）。「把东西从服务端搬到客户端」时必须同时搬这类帧率无关性要求。
- [x] 5.21 **【单测陷阱】`lensExtent` 改为纯函数**：单测环境未加载配置规范，`lensExtent` 自读配置会抛「Cannot get config value before config is loaded」。改为接收 `travel` 参数、配置由调用方读取。**附带发现**：改之前那条新测试其实是空的（两边同为依赖变量的线性式，恒等式自然成立）——是这次失败暴露了问题。
- [x] 5.22 **【最大视觉误判】弃用 `ParticleTypes.DUST`，改用 `ParticleTypes.GLOW`**（实机第五次反馈「没有任何特效只有原版粒子」）：`DustParticleBase` 的 `getRenderType()` 是 `PARTICLE_SHEET_OPAQUE`（**完全不混合**，画出来是实心小方块，叠多少颗都不变亮），且 `lifetime = (int)(8.0/rand) * scale`（**寿命随尺寸线性放大**，大粒子必然拖成 2 秒的飘散雾）。两条都与「一团白光」相反。`GLOW` 具备所需的 `PARTICLE_SHEET_TRANSLUCENT` + 随年龄升全亮；代价是尺寸由 provider 固定（约 0.75 格），故改用「密度随半径递增 + 内层粗粒」积出体积。渲染器内**全部**粒子（含环境微粒与启动爆发）已统一走 `GLOW`。详见 design.md D5.11。
- [x] 5.23 修掉一处错字级遗留：环境微粒里混进了一颗 `ParticleTypes.WITCH`（女巫变形粒子），已改为普通光斑。
- [x] 5.24 **【同步语义 bug】倒计时不能当分拍边界**：`openDelay` 是每 tick 递减的倒计时，却被客户端当作「蓄能段结束 tick」使用——客户端拿到的是不断变化的剩余值，分拍边界会随之漂移；`playShatterCues` 拿递减值去比较则永远对不上。新增不可变字段 `fxChargeEnd`（请求时的延迟值）专供分拍与音效使用，同步的也是它。
- [x] 5.25 **【排查失误自记】** 曾把 PID 1884 报成「冻结的服务端」，随后用 `CommandLine -match 'devlaunch|gradle-wrapper'` 复查，该过滤式匹配不到任何东西，我却据此宣称「进程已消失」。实际它只是 Gradle daemon（命令行含 `gradle-daemon-main-9.2.1.jar`，不含那两个关键词）。**过滤器写错时「查不到」是空结果，不能当肯定证据。**
- [x] 5.16 **声音改为本模组搭配**：蓄能底噪 AMETHYST_BLOCK_RESONATE@0.55（深而长），崩解瞬间 WARDEN_SONIC_BOOM@0.85 + AMETHYST_CLUSTER_BREAK@0.70。已确认不含 ENDER_DRAGON_DEATH。
- [x] 5.17 **粒子改为纯位置投放**：一律 count=1、偏移为 0，不依赖 sendParticles 各重载的初速度语义；运动感完全由「每 tick 在哪」表达，结果确定可复现。约 3850 粒/门（含环与光束），约 19 包/粒；两扇门在不同维度，故每维度约一半。
- [x] 5.26 **【连续第三次选错粒子】改用 ParticleTypes.EXPLOSION + END_ROD**（实机第六次反馈「只有绿色十字粒子」）：GLOW 是**荧乌贼**粒子，GlowSquidProvider 里颜色是随机的 setColor(0.6,1.0,0.8) / setColor(0.08,0.4,0.4)——**绿/深青**，且 quadSize *= 0.75 带随机抖动只有约 0.5×0.5 格。**用户看到的「绿色十字粒子」就是它**，说明客户端一直在跑新代码、是我选的粒子不对。现改为：白光球 = EXPLOSION（HugeExplosionParticle，全亮、PARTICLE_SHEET_LIT、四帧星芒、quadSize = 2.0*(1-mult*0.5)、寿命 6..9 tick，尺寸随半径递增），白光柱 = END_ROD。渲染器内已无 ParticleTypes.GLOW。详见 design.md D5.12。
- [x] 5.27 **【日志暴露的真 bug】指导书数据未随 pattern 重生成**：Patchouli 整本书编译失败（Error loading and compiling book gensokyou:gensokyou_book ← CommandSyntaxException: 未知方块 'gensokyou:air'）。**改 pattern 不会自动重生成书数据**——书里的调色板是生成期快照进 JSON 的，两者互不联动。已改为 minecraft:air。
- [x] 5.28 **【教训】选粒子必须读 provider 源码**：DUST「能指定颜色和大小」所以成了默认选项，但渲染类型（不混合）与寿命公式（随尺寸放大）都不适合光效；GLOW 名字像通用发光，实际是荧乌贼专属的绿色。**名字与用途无关，源码才是**；「半透明 + 全亮 + 寿命不随尺寸变化」三条缺一不可。连续两轮都靠描述性猜测定位，直到用户明确说「绿色十字」才去读 GlowSquidProvider——**症状描述里往往已经藏着答案**。
- [x] 5.29 **自建光斑粒子 + 自生成纹理，彻底不用 vanilla 粒子**：SukimaGlowParticle extends Particle，自己实现 
ender(VertexConsumer, Camera, float) 提交朝向相机的 billboard quad；渲染状态由自定义 ParticleRenderType（**接口，只有 begin(Tesselator, TextureManager)**）直接绑定自生成纹理 shatter_glow.png（64x64 径向光斑，91 级 alpha）。**关键障碍**：TextureSheetParticle 要求的 SpriteSet 来自 ParticleTextures 图集，而该图集在资源加载时已 stitch 完毕，自带 PNG 取不到——故必须绕过图集。**且无需注册粒子类型**：演出纯客户端、从不经网络，因而不需要 ParticleOptions / MapCodec / StreamCodec 样板，渲染器直接 particleEngine.add(...)。SukimaPortalRenderer 现已零 vanilla 粒子引用。详见 design.md D5.13。
- [x] 5.30 **【教训】动手前先查清接口形态**：写第一版自建粒子时，我以为 ParticleRenderType 是 RenderType 的包装，于是按该假设写了一版完全跑不通的代码（RENDERTYPE_LIGHTMAP_SHADER 不在 RenderStateShard 上、getRenderType() 返回类型也不对、gravity 是 float 不是 double）。**连续踩坑后想「自己写一个」是可以的，但必须先查清接口长什么样**，而不是凭印象动手。
- [x] 5.31 **【状态泄漏】自建粒子接上后整个隙间什么都不剩：ParticleRenderType 只有 egin() 一个方法、**没有 end()**，而 vanilla 每个实现都在 egin() 里把状态设全（包括显式 depthMask(true) / defaultBlendFunc() 恢复上一组）——即 egin() 承担「设置 + 复原」两职。初版为了「加法混合 + 不写深度」写了 lendFunc(SRC_ALPHA, ONE) 与 depthMask(false) 却从未恢复 → 溢出到后续所有渲染，虚空内景与眼睑一并不见。修法：egin() **严格照抄 vanilla PARTICLE_SHEET_TRANSLUCENT 全套状态**，只换贴图；观感调优等底线拿到后再做。同时修掉顶点格式错配：DefaultVertexFormat.PARTICLE 只有 POSITION/COLOR/TEX_0/UV1，不存在法线与 overlay 元素，不应调 setNormal/setOverlay。详见 design.md D5.14。
- [x] 5.32 **【真凶】RenderSystem 调用顺序：先 setShaderTexture 再 setShader**：粒子已生成、纹理已加载（glId=60）、几何正确（基向量均为单位向量）、发射连续（spawn #1..#7200 横跨 40 秒），但**屏幕上什么都没有且不报任何错**。根因是 RenderSystem.setShaderTexture(i, rl) **只把纹理 id 写进 RenderSystem 的槽位数组**，真正把它推给已绑定着色器实例的是 setShader 内部的 shaderInstance.setSamplerTexture(...)；而 ParticleEngine 的调用顺序是**先 setShader（此时槽位里还是上一组的原版粒子图集）、再 egin()**。于是按直觉在 egin() 里写「先 setShader 后 setShaderTexture」，着色器仍在采**原版粒子图集**，而本粒子用 0..1 的 UV —— 正好落在图集左上角的空白处。**vanilla 的 egin() 从不调 setShader，所以这个陷阱对它是隐形的。**
- [x] 5.33 **【诊断流程本身的可复用教训】** 这一轮能定位，靠的不是推理而是**四层一次性日志**：(a) 编排是否运行 + level 实际类型 → (b) spawn 是否被调用 → (c) 
ender() 是否被调用 + 真实顶点/基向量 → (d) 纹理是否加载（class + glId）+ 发射计数（区分「只跑 2 帧」与「持续发射」）。每层都排除了一个候选，第四层才逼出真正的排序问题。**在渲染/GL 这类没有异常、只有像素的链路上，逐层埋点远快于逐层推理**——我此前连续三轮靠推理全部走偏。
- [x] 5.34 **【改走第二条路】光球与光束改为直接用几何绘制，不再经过粒子引擎**：实测证明粒子链路本身全对（spawn 调用、纹理 glId、基向量单位、发射连续 40 秒 7200 粒）却仍无像素——ParticleRenderType 的约定（引擎先 setShader、egin() 后置、无 end()）实在太容易踩。故整段「结界崩解」改为**在隙间自己的 billboard 姿态内、用 SukimaPortalQuads + SukimaPortalRenderTypes.portal 直接画 quad**——与虚空内景/眼睑**同一条已验证可见**的通道。副产品：由于姿态已是 billboard，光斑只需在**眼形平面内**按二维角度摆放，**不再需要任何三维方向计算**（斐波那契球、GOLDEN_ANGLE 全部删掉）。自建粒子类 SukimaGlowParticle 与其纹理绑定一并删除。
- [x] 5.35 顺带清掉两处历史包袱：旧的粒子版 emitBurst（三颗 EXPLOSION_EMITTER 起爆 + 冲击环）与 emitMotes（ParticleTypes.DUST 紫色微粒）已随三拍编排重写而**完全冗余**，一并删除——这同时消除了用户此前看到的「绿色十字粒子」来源（GLOW 是荧乌贼粒子）。
- [x] 5.36 **【工具链教训】脚本化改文件的风险**：本轮用脚本做索引式切片替换，接连留下**重复的字段与方法块**（两份 FIB/GOLDEN_ANGLE/glowQuad、两套 emitShatterStep），编译错误信息还因缓存指向已被修掉的行号，一度让我以为诊断错位。**在已被反复脚本化编辑过的文件上继续用索引切片是危险的**——应当先打印结构大纲核对边界，或干脆重写整个文件。
- [x] 5.37 **【真根因·非渲染问题】演出只播一次且播完即止，导致后续测试全部落空**：日志里**一条 [DIAG] 都没有**，说明 emitShatterFx 每次都在守卫处直接返回。仪式是闩锁的（arrierLatched），门一旦打开就长期存在，服务端方块实体的 xTicks 早已饱和到 200 且不再增长；客户端此后进服加载区块，拿到的 xTicks 已是 200 → 	 >= SHATTER_TOTAL → **整段演出被整段跳过，一步都不跑**。这也解释了此前「时好时坏」：唯一那次粒子连刷 40 秒（spawn #1..#7200）正是 xTicks 卡死而 lastEmittedFx 因区块反复卸载被重置为 0，导致同一段被反复重播。**这是埋雷：一次性演出 + 持久化方块 = 无法复现，而我一直误判为渲染故障。**
- [x] 5.38 **【修复】客户端补播**：新增转态 xPlayed。若客户端发现门已老化（xTicks >= 200）且自己尚未播过，则一帧内快进播放最后 90 tick（覆盖整个崩解段）后标记完成。这同时修掉了一个真实缺陷——**强设长期封闭时，后来进服的玩家永远看不到崩解演出**。正常路径在首次收到 xTicks 时即标记。
- [x] 5.39 **【工具链教训·埋点位置】** 上一条 quad 计数日志挂在方法末尾，而蓄能/崩解两段都提前 
eturn，**永远打不出来**。已挪到方法入口并无条件触发。**「日志挂在会提前 return 的分支之后」等于没埋**——和「过滤器写错时『查不到』不能当否定证据」是同一类错误：观测手段本身失效，却把失效当成了观测结果。

---

## 11. 归档移交（见 README.md 归档说明）

- [x] 11.1 「结界崩解」演出（5.15 / 5.17 / 5.19 / 5.26 / 5.29 / 5.34 / 5.35）**移交** `openspec/changes/barrier-shatter-fx`，本变更不再承载。失败复盘见 `docs/barrier-shatter-fx-postmortem.md`。
- [x] 11.2 记录未完成条目的去向：5.5 留作已知优化债；6.10 并入 `barrier-shatter-fx` 任务 4.1；9.4~9.9 阻塞于 `spirit_core_2` / `ritual_stone_2` 可达性；10.1 / 10.2 阻塞于依赖变更。
- [x] 11.3 记录**归档顺序约束**：本变更 MUST 先于 `sukima-eye-open-axis` 与 `barrier-shatter-fx` 归档（后两者 MODIFY 的 Requirement 只在本变更 delta 里存在）。
- [ ] 11.4 遗留待办（不在本变更范围内，随 `barrier-shatter-fx` 或后续变更处理）：跨维度 `setBlock` 改投 tick 队列；`spirit_core_2` / `ritual_stone_2` 的生存可达性。
