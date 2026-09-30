## 1. 修既有缺陷（后续全部依赖这两项先正确）

- [x] 1.1 修 `DanmakuEmitter#create` 的 size 串位：把 `(float) speed` 改为按 `Geometry.Shot#size()` 逐发解析（含 `sizeScale` 乘数）
- [x] 1.2 为 1.1 加回归断言：`Geometry.Shot#size()` = 几何声明直径 × 本发尺寸乘数
- [x] 1.3 修 `TrackLint.steadyStateEstimate`：区分「声明正重复周期」与「显式时间线」；后者用区间扫线取最大重叠数，且**权重是颗数而非颗数×停留**（后者会把并发数放大数十倍）
- [x] 1.4 零速率期走接触判伤：`AbstractDanmakuProjectile#tick` 中把「速率曲线当前速率为 0」也计入 `stationary`，且排除编队弹与已结算定时换向的弹

## 2. 编排模型扩展

- [x] 2.1 `SpellCard` 增加循环长度 `cycleTicks`（`<= 0` = 未声明，保持即时切换）
- [x] 2.2 `TrackRunner` 增加挂起切卡：目标卡 ≠ 当前卡且当前卡声明了循环时挂起，至循环边界才切
- [x] 2.3 `TrackRunner` 暴露 `cycleTick()` / `cycleTicks()` / `pending()`；`tick(null, …)` 只推进时钟（调度不依赖世界，故循环边界可离线断言）
- [x] 2.4 拍级寿命：`Track.Beat.lifetimeTicks` → `DanmakuEmitter` → `setLifetimeTicks`
- [x] 2.5 逐发伤害/尺寸乘数：`Geometry.Shot` 增 `damageScale` / `sizeScale`；花心的 4× 与 2× 由 `Beat.centreDamage/centreSize` 驱动

## 3. 新几何

- [x] 3.1 `GRID_FACING`：法平面上按两轴固定角间隔排 `rows × cols`，全部同速同原点同刻（弧面为自然结果）
- [x] 3.2 为 3.1 补 lint（角间隔为 0 时拒绝）与 `Shape` 的分类
- [x] 3.3 `SCATTER_FALL`：水平圆盘内位置与距离均真随机，方向竖直向下
- [x] 3.4 为 3.3 补 lint：需声明世界锚定
- [x] 3.5 `PILLAR_UP`：地面锚定 + 竖直向上（激光的生成点）
- [x] 3.6 `FIRST_AIR_BELOW` / `GROUND_BELOW` / `PLAYER_GROUND` 三种 `Beat.SpawnAnchor`，求值放在 `DanmakuEmitter`（持有 `boss.level()`），几何签名仍不接触世界
- [x] 3.7 `DISC_RING`：垂直于瞄准方向的竖直圆盘，圆心在 `forward × offsetForward`；取 count=1 + 逐拍相位推进即得「依次点亮」
- [x] 3.8 `FLOWER`：1 颗花心 + `count` 颗玫瑰线花瓣，**平面取向与抛射方向各自独立随机**（`isOmniRandom` ⇒ 强制声明无害窗口）
- [x] 3.9 `Shape.Params` 增 `offsetForward` / `offsetUp` 两个纯几何字段（13 个 copy-wither 同步更新）

## 4. 新运动

- [x] 4.1 `Motion.Kind.BURST`（径向爆散）+ `Kind.RECLAIM`（定时重瞄），两者共用爆散年龄/速率/目标三项
- [x] 4.2 爆散方向在爆散当 tick 由「弹自身位置 → 编队帧中心」求值；**自身即参考点时改按朝目标射出**（花心的退化分支）
- [x] 4.3 爆散/重瞄结算后**解除各自能解除的那一个**（帧 / 曲线），否则下一 tick 被按回 0
- [x] 4.4 补 lint：`BURST` 必须挂编队帧（没有参考点就没有径向）
- [x] 4.5 同步：`DanmakuMotionState.PARAM_COUNT` 48 → 52，新增 4 个爆散槽位；NBT 增 `BurstAt/BurstRadial/BurstAim/BurstTarget`
- [x] 4.6 补测试：花心尺寸倍数、各花平面互不相同

## 5. 定时重瞄

- [x] 5.1 弹只持目标 entity id（同步 int），在声明年龄当 tick 解算方向
- [x] 5.2 目标解析下沉到 `TrackRunner` 的按人复制循环（`TARGET_AUTO` / `TARGET_RANDOM` 两个哨兵），因为同一拍发 5 份时每份该朝不同的人

## 6. 实体层

- [x] 6.1 `AbstractTouhouBoss#movementLocked()`：锁住时清空游走意图并归零速度，但保留 `faceRandomTarget()`
- [x] 6.2 `BigFairyEntity` 覆写移动锁，按 `runner().cycleTick()` 与卡名判定
- [x] 6.3 `GensokyouConfig` 新增默认攻击项：间隔（复用死键 `bigFairyShotIntervalTicks`，默认改 60）、格数、角间隔、弹速、尺寸、前移量
- [x] 6.4 `BigFairyEntity` 实现默认攻击的第二发射器：全场一份，中心发朝最近玩家

## 7. 符卡表内容

- [x] 7.1 `BIG_FAIRY_PALETTE` 扩到 6 色（含雨用的蓝与激光用的淡蓝白）
- [x] 7.2 阶段 1 花符[弹幕花环]：240 tick 循环，48 拍逐颗（相位 7.5°/拍），等待期可伤，第 60 tick 重瞄射出
- [x] 7.3 阶段 2 花符【花之海洋】：200 tick 循环，16 拍逐朵（每拍 46 发），全向随机 + 逐朵随机平面，花瓣寿命 160 tick，中心弹 4× 伤害 / 2× 直径
- [x] 7.4 阶段 3 夏符【雨季喷泉]：持续型，10 拍逐秒（雨轨 24 发随机撒点 + 激光轨 1 发地面锚定），每第 5 拍用 `PLAYER_GROUND`
- [x] 7.5 lang：zh_cn / en_us 的 `spellcard.gensokyou.big_fairy.1~3` 换成新卡名
- [x] 7.6 三张卡全部通过 `TrackLint`
- [x] 7.7 环弹速度 6 格/秒 → **12 格/秒**（`RING_LAUNCH_SPEED = 0.6` 格/tick，飞行与起飞两处同源）

## 8. 验证

- [x] 8.1 `gradlew build` 通过（613 tests，0 failed）
- [x] 8.2 `BossCardLintTest` / `SpellCardThresholdTest` / `TrackRepeatTimingTest` / `BehaviourDecouplingTest` 对新表通过
- [x] 8.3 新增 `BigFairyCardReworkTest`：24 条断言覆盖新几何、密度估值分流、挂起切卡、逐发旋钮、豁免边界
- [x] 8.4 `openspec validate rework-big-fairy-cards --strict` 通过

## 9. 待办（本次未做）

### 9.1 环弹读档后冻结 —— 等 `danmaku-timeline-sync` / `danmaku-event-sync` 落地后重判

**现象**：环生成过程中退出游戏再进，那批弹停住不动，且约 60 秒后才消失（读起来像「不会消失」）。

**根因是两层叠加**，缺一层症状就不同：

| 层 | 范围 | 性质 | 机制 |
|---|---|---|---|
| **A. `deltaMovement` 从不入盘** | **全模组**所有弹幕、所有来源 | 既有格式缺口，与本变更无关 | `AbstractDanmakuProjectile#addAdditionalSaveData` 只写「运动输入」（曲线 7 项 + 编队帧 16 项 + 轴 + 爆散 4 项），**从不写 `deltaMovement`**。该模型对速率曲线/编队帧/爆散自洽（位置是 `(t, p₀)` 的纯函数，读档能自愈），但**「纯弹道飞行」这一段的速度只存在于 `deltaMovement` 里**，读档后为 `(0,0,0)` ⇒ 冻结。四只 BOSS 的普通弹、小妖精、玩家武器弹幕**全都是这样**，只是普通弹 2 秒就飞出场外没人注意，而环有 3 秒静止期会滞留到存档 |
| **B. `RECLAIM` 的「已结算」标记不可恢复** | 只有本变更新增的定时重瞄 | 本次实现的漏洞 | 标记取 `DATA_HAS_PROFILE = false`。存盘时 `hasSpeedProfile()` 已是 false ⇒ **连曲线本身都不写**；读档后 `SpV3` 缺失 ⇒ `DATA_HAS_PROFILE` 保持默认 false ⇒ `timedTurnSettled()` 立刻为 true ⇒ 换向永不触发 |

**A 一修，B 的症状就消失**：速度从盘上回来，而 `timedTurnSettled()` 为 true 恰好意味着「别再重算」，弹沿原方向继续飞 —— 对一颗已发射的弹这正是对的。故 B 无需独立修。

**与两个在途 change 的关系**（已核对它们的 proposal）：

- `danmaku-timeline-sync` 枚举的确定性运动含「直线」，读档后从轨道时间重算而非靠 `deltaMovement` ⇒ **顺带修掉 A 的大半**。但它明确声明「**改向**、目标状态、分裂结果、激光权威几何和提前销毁不在本变更内实现」，而环弹第 3 秒的重新瞄准就是改向；改向后的弹道段按它自己的分类属非确定性，会「继续使用实体级快照/关键帧兼容路径」⇒ **不修 B**。
- `danmaku-event-sync` 的第一批迁移对象含「服务端临时改向或速率变化」，关键帧形态是「服务器时间 + 位置/方向」⇒ **这是 B 的架构归宿**。但它解决的是双端一致，不是「服务端从 NBT 恢复」：单机重进时是服务端先从 region 文件把弹读回来、速度为 0。事件要绕一圈（重进后补发关键帧）才能修好一个「NBT 少存 3 个 double」的问题。

**将来该怎么做**（等那两个落地后重判）：

1. 先看 `danmaku-timeline-sync` 落地后 A 还剩多少。若「直线」已由轨道时间接管，则只需为**非确定性弹道段**补速度持久化（约 60 行：3 个 double 的存档往返 + 回归测试）。
2. 那份速度持久化**不是会被作废的无用功** —— 环弹改向后的弹道段恰好落在 timeline-sync 声明的「非确定性 ⇒ 走实体级兼容路径」里，速度持久化就是那条兼容路径本身。
3. 在 `danmaku-event-sync` 的 design 里把「定时重瞄后的弹道段」点名为关键帧的**首个候选用例**，使那条兼容路径将来能被干净接管，而不是两套状态并存。
4. 届时把 B 的标记一并换成**显式的持久化状态位**：`DATA_HAS_PROFILE = false` 这个 flag 在别处一律表示「没挂曲线」，被本变更赋予第三种含义（已结算），是个设计味道。速度持久化之后它不再致害，但不该留着。

### 9.2 密度豁免待实测复核

当前声明 `densityWaiver = 800`（实际估值 598，预算 120）。豁免理由写在 `SpellCard#densityWaiver` 的 javadoc 里：598 假设玩家站在爆散中心，而花是在 BOSS 身边爆散的、距离带下界 10 格，玩家物理上到不了花心。测过中弹率与画面密度之后，要么给估值模型补上距离项（正解），要么撤掉豁免并降低该卡密度。

### 9.3 实体上限 800 的余量实测

阶段 2 峰值在场 736（上限的 92%）。阶段 1 的环（无寿命约束，开阔地可能堆到 ~1,200）与阶段 3 的雨（60 格内无地面时按 1200 tick 默认寿命堆到 ~1,440）都是**地形依赖**，需在真实地形上量。

### 9.4 `bigFairyBossSeconds` 实测后回写

当前保持 160（D23）。

### 9.5 符卡名上屏

已在 `boss-bar-tier-and-spellcard-name` 中实现，本变更只提供了三张新卡的名字条目（`spellcard.gensokyou.big_fairy.1~3`，zh_cn + en_us）。
