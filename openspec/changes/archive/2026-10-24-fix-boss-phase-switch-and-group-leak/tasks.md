## 1. 运行时：组退休与回收（崩服修复，最高优先级）

- [x] 1.1 `DanmakuWorldRuntime.Group` 增加 `boolean retired`（默认 false）；`GroupSnapshot` 增对应字段，`ProgramSessionCodec.writeGroup/readGroup` 往返该位，旧存档缺键按 false 处理
- [x] 1.2 `DanmakuWorldRuntime.collect()` 的回收判据从 `!g.emitting` 放宽为 `(!g.emitting || g.retired)`；在字段声明与判据处写明「`retired` 只被回收读」
- [x] 1.3 `DanmakuWorldRuntime.endCycle()`：取消路径保留 `!g.background` 过滤原样；新增退休路径，把刚走完那一轮（含底噪组）的组标记 `retired = true`，且不动物理属性
- [x] 1.4 `ServerControlOperations` 的 `SurfaceFire` 分支改用 `commitAtBirth(plan, policy, ownershipEnd, true)`（封口），与 `server:emission` 的既有写法对齐
- [x] 1.5 核查 `retired` 的消费点：全仓确认只有 `collect()` 与 `endCycle()` 读它；`prepare` / `commit` / `runChildrenPass` 一律不读（山女尾珠派生依赖 `runChildrenPass` 临时抬升 `emitting`）
- [x] 1.6 核查退休路径不发 `Kind.CANCEL` 事件、不改 `fadeEnd`、不调用 `stopPlans`（退休 ≠ 取消）
- [x] 1.7 核对底噪组被退休后，山女底噪 `END_AT_BOUNDARY` 的旧轮清空行为不变（取消路径未被触碰）

## 2. 判定：激光碰撞半径分流

- [x] 2.1 `ProgramCollisionAdapter.collide()` 按碰撞形态取半径：激光用 `style.collisionRadius()`，弹射物型用 `style.effectiveCollisionRadius()`
- [x] 2.2 `BalanceNumbers.PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE` 的注释改为「仅作用于弹射物型弹幕」
- [x] 2.3 核查 `ProgramRenderView` 中 `effectiveCollisionRadius()` 的消费点（`Member.radius`），确认改判定不动画面；若有其它消费者则改为显式半径字段
- [x] 2.4 测试：激光判定半径 = 声明值；弹射物型判定维持现行倍率；两者互不影响

## 3. 切牌时机：达阈值即切 + 旧弹幕淡出

- [x] 3.1 `BossProgramBattle.tick()` 去掉 `elapsed % cycleTicks` 的挂起判断，改为达阈值即切
- [x] 3.2 走查 `BossProgramBattle.adopt()`（读档恢复）：`cardStart` 记账与立即切换叠加时不得重复安装同一张卡
- [x] 3.3 `DanmakuWorldRuntime.endCard()` 的取消改为带 fade（新增常量 `CARD_END_FADE_TICKS = 30`），旧弹幕 30 tick 淡出且离场期间不再判伤
- [x] 3.4 改写 `ProgramContentTest.cardSwitchSuspendsUntilLoopBoundary` → 断言「达阈值即切」；新增「一次跨多阈值直接落最终卡」「旧弹幕淡出且窗口 ≥ 20 tick」两个用例
- [x] 3.5 确认旧实体后端（`danmaku/track/TrackRunner`）未被改动（该后端随 BOSS 符卡内容整体退役，不另立规格条款）

## 4. 切阶段演出（世界层 + 屏幕层）

- [x] 4.1 `ClientSpellCardNames.put()` 改为返回「下标是否发生变化」；`ClientPayloadHandler.handleSpellCardName` 在变化时通知声明层，`StartTracking` 补发路径不触发（客户端此前无记录）
- [x] 4.2 新建 `client/BossPhaseTransition.java`：按 entityId 记录声明时刻与符卡下标，提供 GUI 渲染入口（`RegisterGuiLayersEvent`）
- [x] 4.3 屏幕宣言层：屏幕中央偏上的符卡名大字 + 背后的旋转法阵（复用 `textures/fx/magic_circle.png`）+ 朱印 + 起始 2~3 tick 的一次白闪；法阵与白闪一律加法混合，MUST NOT 遮挡弹幕
- [x] 4.4 符卡名走 lang 键反查；缺键时跳过整次宣言（MUST NOT 显示原始键名），复用 `TouhouBossBarRenderer` 的未解析键前缀判据
- [x] 4.5 BOSS 宣言姿态：`AbstractTouhouBoss.onProgramCardChanged` 已有钩子；为没有专属动画的 BOSS 提供通用等效表现，MUST NOT 静默无反应
- [x] 4.6 声明音效：使用 `minecraft:entity.ender_dragon.growl`，走既有 `SimpleSoundInstance.forUI` 通路（与程序弹幕音效同一条，不受距离衰减）；在代码注释里登记「原版占位，待换自制素材」
- [x] 4.7 断言演出不影响判定：演出期间弹幕命中与伤害结果与无演出时一致；且弹幕在宣言期间仍然清晰可辨（未被遮挡、未被压暗到不可判读）

## 5. 回归与验证

- [x] 5.1 收敛性测试：同一张符卡连跑 N 个完整循环，断言组数收敛到上界而非随循环数线性增长（替换现有只跑 220 tick 的峰值断言）
- [x] 5.2 两批共存测试：同符卡第二批出生时，第一批仍存活且仍按自身判定生效
- [x] 5.3 跨边界未出生批次测试：预警期跨过循环边界的批次在边界之后照常出生
- [x] 5.4 批次操作不串批测试：释放 / 重新瞄准只作用于触发它的那一批
- [x] 5.5 旧弹幕淡出测试：切牌后旧弹幕在 30 tick 内逐渐消散，且离场期间不判伤
- [x] 5.6 `.\tools\gradle_task.ps1 build` 全量通过（含既有测试集）
- [ ] 5.7 实机验证：一场大妖精打到死，确认不崩、切牌跟手、激光判定与视觉一致、宣言演出可见且不遮挡弹幕
