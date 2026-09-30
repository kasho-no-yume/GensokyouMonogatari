## Why

大妖精现有三张符卡（散華 / 旋風 / 落華）是「教学档」占位内容：每张卡**一条轨道、一个形状、`Behaviour.NONE`**，在 160 秒里把同一个模式重复 20 遍；稳态并发峰值 11 颗，而预算是 120 颗。14 种几何里只用了 3 种，6 种运动一种没用，激光、编队帧、分裂、显隐全部空置。

更关键的是**三处声明与实际不符**，使「教学档」这个定位本身是假的：

- `DanmakuEmitter#create` 把**弹速**传进了 `SphereDanmaku` 构造器的 `size` 形参，于是所有 BOSS 弹幕的实际直径 = 弹速。大妖精声明的 `size` 0.95 / 0.85 / 1.05 实际发出来是 0.30 / 0.28 / 0.22——比小妖精（0.4）还小。「全大慢弹」从未成立。
- `Track.color()` 全仓库无人读取，运行时颜色取自 `TrackRunner#emitTrack` 的 `palette.at(卡内轨序)`，三张卡实际同色。
- `Geometry` 的 `FAN` 分支不消费 `phase`，`散華扇` 的 `phaseStep(4)` 无效，那个扇形从不转动。

同时，符卡切换是**半途截断**的：`syncCard` 每 tick 按血量阈值判定，命中即刻换卡并把轨道 tick 归零，于是玩家会看到一张符卡刚演到一半就被拦腰切走。

## What Changes

1. **符卡切换延后到循环边界**（**BREAKING**：相位时序变更）。血量跨阈值时挂起切卡，等当前符卡的一个完整循环走完再切。符卡↔符卡因此永不叠加半程。
2. **大妖精符卡表整体替换**为三张有阶段结构的内容：花符[弹幕花环]（每 12 秒一轮，6 秒悬停放环 + 6 秒游走）、花符【花之海洋】（10 秒周期，16 朵花全向抛射）、夏符【雨季喷泉】（持续 raining + 地射激光）。设计原文与逐条决策见 `design.md`。
3. **新增默认攻击**：每 3 秒一发 5×5 弹幕墙（两轴各 10° 间隔，同速发射、弧面由固定角偏自然形成），全场一份，与全部三张符卡叠加。
4. **新增一批编排原语**，逐条对应 `design.md` §3：
   - 拍级寿命（花瓣 8 秒）
   - 逐发伤害乘数（中心大弹 4×）
   - 二维角度栅格几何（5×5 墙）
   - 体积内随机撒 N 点（雨的 24 点）
   - 地面锚定生成点（地射激光 + 雨的「往下找第一个空气」）
   - 逐发随机平面（全向抛射的花，花平面与抛射方向不必成法线）
   - 定时径向爆散（花瓣悬停满 2 秒后朝各自半径反向射出）
   - 零速率期走接触判伤（环的 3 秒等待期可伤玩家）
   - 定时重瞄（环内弹在第 3 秒起飞时重新瞄准）
   - 移动锁（阶段 1 悬停不动、保留转向）
5. **修 `DanmakuEmitter` 的 size 串位**（**BREAKING**：四只 BOSS 的全部弹幕观感与判定尺寸同时改变，不只大妖精）。
6. **修 `TrackLint.steadyStateEstimate`**：该估值把「显式枚举拍」（`repeatEvery = 0`）当成永久每拍发射，对任何会停的编排都算爆一个数量级。本变更的三张卡全是显式时间线，不修则全部 lint 红。
7. **放宽 R1 的随机包络规则**：无约束全向随机 MUST 以「该发在固定年龄前无害」兑现公平性，而非以锥包络。这是 R1 自身「生成点在视野锥内**或该发有预警**」的兑现方式，不是放宽。
8. **作废** `remnant-touhou-bosses` 中「大妖精 SHALL 作为教学档：其弹幕 MUST 全大、全慢、且全部朝玩家前向」这条要求。

### 明确不在本次范围

- 符卡名显示在血条下方（需自绘 HUD + 新同步包）。
- `bigFairyBossSeconds` 的最终值（先保持 160，实测后再定）。

## Capabilities

### New Capabilities

（无 —— 新原语全部落在既有弹幕能力的 requirement 上，不另立能力。）

### Modified Capabilities

- `remnant-touhou-bosses`: 作废「大妖精教学档：全大全慢全前向」；新增「默认攻击与全部符卡叠加」与「大妖精符卡表的三段结构」
- `danmaku-track-composition`: 符卡切换延后到循环边界；随机包络规则改为「以无害期兑现」；密度估值口径修正；新增拍级寿命、逐发伤害乘数、二维角度栅格、体积随机撒点、地面锚定、逐发随机平面六条原语要求
- `danmaku-motion`: 新增「定时径向爆散」与「零速率期接触判伤」两条运动要求

## Impact

**数据/内容**
- `danmaku/track/BossCards.java` — `bigFairy()` 整段替换；`BIG_FAIRY_PALETTE` 扩到 6 色
- `config/GensokyouConfig.java` — 新增默认攻击的频率/格数/角间隔/弹速/颜色/尺寸项；复用已存在的死键 `bigFairyShotIntervalTicks`
- `entity/BigFairyEntity.java` — 第二发射器（默认攻击）、移动锁覆写、色盘

**编排模型**
- `danmaku/track/Shape.java` / `Shape.Params` — 新几何 + 新参数（拍级寿命、逐发伤害、锚定模式）
- `danmaku/track/Geometry.java` — 对应分支
- `danmaku/track/Behaviour.java` — 新 `Motion.Kind`（径向爆散）
- `danmaku/track/SpellCard.java` — 循环长度声明
- `danmaku/track/TrackRunner.java` — 挂起切卡、循环边界信号
- `danmaku/track/TrackLint.java` — 估值修正 + 新形状的 R1/R2 判据 + 「全向随机须配无害期」判据
- `entity/AbstractTouhouBoss.java` — 挂起切卡消费 + 移动锁覆写点

**翻译层与弹体**
- `danmaku/DanmakuEmitter.java` — **size 串位修复**、地面锚定、拍级寿命下发
- `entity/AbstractDanmakuProjectile.java` — 零速率期接触判伤、定时重瞄（目标 entityId + 起飞帧解算方向）

**测试**
- `BossCardLintTest`（`bigFairy().size() == 3` 不变，但需新增断言）
- `TrackRepeatTimingTest` / `SpellCardThresholdTest` — 遍历全表，隐式受新表影响
- 新增：size 串位回归、挂起切卡时序、显式时间线估值、地面锚定

**玩家可见影响**
- 四只 BOSS 的弹幕直径从「= 弹速」变为「= 声明的 size」，碰撞箱同步变化 ⇒ 命中判定与手感全体改变
- 大妖精的战斗结构从「3 个模式各重复 20 遍」变为三段有演出节奏的内容
- 多人时阶段 1 的环按人复制（5 人 = 5 环 = 240 弹/轮）
