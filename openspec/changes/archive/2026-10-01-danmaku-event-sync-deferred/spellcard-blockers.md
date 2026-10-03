# 符卡编排当前的阻塞项

> 这不是本 change 的需求，是从 `rework-big-fairy-cards`（大妖精符卡重做）里**带出来**的发现：
> 有两类符卡编排在当前弹幕管线下**做不出来**，或做出来行为是错的。
> 它们各自指向本 change 与 `danmaku-timeline-sync` 的一项既有声明，故记在此处而非那个 change 的设计里。
> 另附阻塞项 A′，它在本 change 内部，零前置依赖。

## 阻塞项 A：读档后弹道段冻结（`deltaMovement` 不入盘）

### 现象

一批**已经发射**的弹，退出游戏再进后停住不动，约 60 秒后才消失（默认寿命 1200 tick）。
读起来像「永远不会消失」。

### 根因

`AbstractDanmakuProjectile#addAdditionalSaveData` 只写「运动输入」：

- 速率曲线 7 项（`SpV0..SpV3` 等）
- 编队帧 16 项（`FrameCx..FrameOrate`）
- 方向轴 3 项（`SpAxisX/Y/Z`）
- 爆散 4 项 + 寿命 + 年龄

**从不写 `deltaMovement`。** 该模型对前三类是自洽的 —— 它们的位置是 `(t, p₀)` 的纯函数，
读档后能自己算回来。

但**「纯弹道飞行」这一段的速度只存在于 `deltaMovement` 里**，读档后为 `(0,0,0)`。

### 影响面

| 范围 | 说明 |
|---|---|
| 四只 BOSS 的普通弹 | 全部是 `Behaviour.NONE` + 匀速直线，速度只在 `deltaMovement` 里 |
| 小妖精 / 玩家武器弹幕 | 同上（它们不走 `TrackRunner`，但同样不写速度） |
| 定时重瞄（`RECLAIM`）的换向**之后**那段 | 换向前半段有速率曲线可自愈，换向之后就只剩 `deltaMovement` |

**为什么这么久没被发现**：普通弹 2 秒就飞出场外，玩家看不见它冻结。
只有「静止等待 N tick 再发射」这类编排会把弹滞留在场上到存档时刻 ——
而那正是大妖精的环（3 秒等待期）。

### 与两个 change 的关系

| change | 覆盖情况 |
|---|---|
| `danmaku-timeline-sync` | **修掉「直线」与「曲射」两类**，落在 T2 任务 2.4（给这两类补 `MotionX/Y/Z` 的存档往返）。<br>⚠️ 原先写的是「由轨道时间重算」，该表述已被本变更修订（`design.md` §5c）：直弹的位置永远是 `pos += v`，除了速度没有任何可重算的东西，真正的修复是补一个缺失的数。<br>另：**编队弹与速率曲线弹读档后并不冻结**（`DATA_HAS_FRAME`/`DATA_HAS_PROFILE` 按键存在推断恢复，rig 与 `alongAxis` 各自能自愈），所以真正冻结的只有上面两类。<br>它明确声明「**改向**、目标状态、分裂结果、激光权威几何和提前销毁不在本变更内实现」，而环弹第 3 秒的重新瞄准就是改向 ⇒ **改向后的弹道段仍不修** |
| `danmaku-event-sync`（本 change） | **是它的第一批迁移对象**：「服务端临时改向或速率变化」。关键帧形态是「服务器时间 + 位置/方向」。给环弹的重瞄发一个带方向的关键帧，两端都能重建速度 |

### 为什么不能顺手就修

`danmaku-event-sync` 解决的是**双端一致**，不是**服务端从 NBT 恢复**。
单机重进时，是**服务端**先从 region 文件把弹读回来、速度为 0，然后才谈上客户端。
事件机制要绕一圈（重进后补发一个关键帧）才能修好一个「NBT 少存了 3 个 double」的问题。

### 建议的落地顺序

1. 等 `danmaku-timeline-sync` 的 **T2 任务 2.4** 落地，先看 A 还剩多少
   （预期只剩「改向后的非确定性弹道段」）。T2 已为**直线与曲射**两类补上速度持久化，
   所以剩下的确实只有改向段 —— 那才是「NBT 少存了 3 个 double」之外的问题
2. 改向段的修复**不是**速度持久化能解决的：曲射的解析位置是
   `p₀ + Σ R(axis,ω)ⁱv₀`，闭式化要把整条旋转历史求和，而 `Rotation.about` 的
   `sin/cos` 本就不保证跨端逐位一致。速度持久化能让它**不再冻住**，
   但相位会与冻结前不同 ⇒ 这一段仍然需要本 change 的 `REDIRECT`
3. 在本 change 的设计里把「**定时重瞄后的弹道段**」点名为关键帧的**首个候选用例**，
   使那条兼容路径将来能被干净接管，而不是两套状态并存
4. 届时把「爆散/重瞄已结算」的标记一并换成显式的持久化状态位 ——
   早期实现曾用「编队帧没了」当前者，而帧的缺失有歧义
   （「被解除」≠「从来没绑上」，后者会让弹从出生 tick 起就跳过速率曲线与换向）。
   该标记现已改为独立同步位 `DATA_BURST_FIRED`，本条只是记录它曾经过渡形态

### 阻塞项 A′（本 change 内部，零前置依赖，可先做）

`TalismanDanmaku#targetLost` **既不在 `SynchedEntityData`、也不入 NBT**
（`addAdditionalSaveData` 只写 `Sensitivity`），却在双端各自被置位，并直接短路
`tick` 里的转向逻辑（`:99`、`:125`）。

后果有两条，比 A 更窄但更确定：

- **双端分歧**：客户端依据自己看到的目标位置与角度独立判定，服务端依据自己的。
- **服务端自己会忘**：读档后 `targetLost` 归零，而 `DATA_TARGET_ID` 也不持久化，
  于是这枚灵符既没有目标、也不知道自己曾经丢失过 —— 与「服务端是目标状态权威」的
  声明直接矛盾。

它同时违反 `danmaku-pipeline-capacity` 的准入判据（不可由已同步状态推导的状态
MUST 经同步下发，**并且** MUST 纳入存档），两头都违反。

⇒ 把它补进 `SynchedEntityData` 与存档是 `TARGET_LOST` 的最小实现，
**不依赖 `danmaku-timeline-sync` 的任何部分**，可先于事件协议主体落地。

## 阻塞项 B：无目标可瞄时的降级语义（已就地修，但语义待定）

`TargetMode.AIMED` 的语义是「有一发是给你的」。没有给的人时怎么办，
原实现用 `Math.max(1, size)` 兜底，于是「零目标」被当成「一个人」：
锚点落回 BOSS 自己的脚、瞄准方向竖直向下、重瞄解析不出目标。

现已改为「瞄准型轨道在目标集为空时**整轨不发射**」。但有两件事本 change 没有决定：

- **BOSS 该不该在无目标时保持沉默？** 野生 BOSS 按 `remnant-touhou-bosses`
  的「玩家可离开祭坛」条款不绑定场地，玩家离开 64 格后 BOSS 就是完全安静的。
  这与「索敌范围」是两件事：半径是**有依据的**（见 `LOCK_RADIUS`），
  但「半径外彻底静默」这个**可观测行为**目前没有任何 spec 条款
- **默认攻击在无目标时是否也该沉默？** `BigFairyEntity#tickDefaultAttack`
  当前直接 `return`（即沉默）。它是「底噪」，但「底噪需要观众」这个判断
  没有写进任何 spec

两条都属内容决策，建议在 `danmaku-track-composition` 或 `remnant-touhou-bosses`
里补条款，而不是留在实现里。

## 相关：本次一并被钉住的判据

以下三条在 `rework-big-fairy-cards` 里落地为 lint 规则或结构约束，
**它们与本 change 无关但会影响未来所有符卡作者**，列此备查：

| 判据 | 位置 |
|---|---|
| 多拍显式时间线 MUST 声明循环长度 | `TrackLint#lintTimelineReplays` |
| 径向爆散 MUST 挂参考点帧 | `TrackLint#lintFormation` |
| 方向全向随机的形状 MUST 声明无害窗口且 ≤ 寿命一半 | `TrackLint#lintBeat` |
| 锁定半径单一来源（`bossAttributes` 不接受 followRange） | `AbstractTouhouBoss.LOCK_RADIUS` |
| 「已发生」MUST 是独立状态位，不得从别的状态的缺失推断 | `AbstractDanmakuProjectile#DATA_BURST_FIRED` |
