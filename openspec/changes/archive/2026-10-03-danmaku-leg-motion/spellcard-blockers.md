# 符卡编排的阻塞项（承接自已暂缓的 `danmaku-event-sync`）

> 原变更已**暂缓**（机制保留，不否决）并移入
> `../archive/2026-10-01-danmaku-event-sync-deferred/`，理由见其 `proposal.md` 顶部。
> 它的有序事件机制（信封 / 序号 / 缺号 / 乱序暂存 / 按服务器时间重放）
> **现在没有用户**：首批需求全部落在「发射时烘定」或「客户端派生」两档。
> 但机制保留，且实现时有三处必须修正（TCP 无乱序、缺号不发生、重放不可实现）——
> 清单在该变更 `design.md` 顶部。
>
> 本文件保留它 `spellcard-blockers.md` 的完整分析，标注每一条的**当前状态**。

## 阻塞项 A：读档后弹道段冻结 —— **已由 `danmaku-timeline-sync` T2 解决**

### 现象（历史）

一批**已经发射**的弹，退出游戏再进后停住不动，约 60 秒后才消失（默认寿命 1200 tick）。

### 根因（历史）

`AbstractDanmakuProjectile#addAdditionalSaveData` 只写「运动输入」，**从不写
`deltaMovement`**。该模型对速率曲线与编队帧是自洽的（位置是 `(t, p₀)` 的纯函数），
但**纯弹道飞行**这一段的速度只存在于 `deltaMovement` 里，读档后为 `(0,0,0)`。

### 修复

`danmaku-timeline-sync` T2 任务 2.4 落地了 `MotionX/Y/Z` 三个 double 的存档往返，
判据是新增的 `DanmakuTrackKinds.needsVelocityPersistence`。

逐条核对后的实际结论（比原分析更精确）：

| 弹种 | 读档后 | 原因 |
|---|---|---|
| 编队帧 | **自愈** | `DATA_HAS_FRAME` 按 `FrameSp` 键存在推断恢复，rig 从第一 tick 起用 `positionAt(age)` 覆写位置 |
| 速率曲线 | **自愈** | `DATA_HAS_PROFILE` 按 `SpV3` 键存在推断恢复；`alongAxis` 在速度为零时回落到 `axis()` |
| 曲射 | 冻结 → **已修** | `rotateAbout(零向量,…) ≡ 零向量` |
| 直线 | 冻结 → **已修** | 速度为零即原地不动 |

**「定时的环弹重瞄」这一段也已覆盖**：换向会清掉 `DATA_HAS_PROFILE` 且环无编队帧，
于是换向后的弹在 `DanmakuTrackKinds` 眼里就是**普通直线弹** ⇒ 速度被写入 ⇒ 读档后不冻结。

⇒ 阻塞项 A **完全关闭**。给编队弹与速率曲线弹写速度是**误导** ——
速度不是它们的权威。

## 阻塞项 A′：`targetLost` 不同步不入盘 —— **由 `danmaku-talisman-target` 解决**

`TalismanDanmaku#targetLost` 既不在 `SynchedEntityData`、也不入 NBT，却在双端各自被置位，
并直接短路 `tickHoming`。它同时违反 `danmaku-pipeline-capacity` 准入判据的两半。

另有第二处缺陷：`DATA_TARGET_ID` 存的是会复用的 network id，目标玩家死亡后
灵符会安静地追上一个无关的新实体，无报错无日志。

⇒ 完整分析、决策与任务见 `../danmaku-talisman-target/`。

## 阻塞项 B：无目标可瞄时的降级语义 —— **仍未决（内容决策）**

`TargetMode.AIMED` 的语义是「有一发是给你的」。没有给的人时怎么办，
原实现用 `Math.max(1, size)` 兜底，于是「零目标」被当成「一个人」：
锚点落回 BOSS 自己的脚、瞄准方向竖直向下、重瞄解析不出目标。

现已改为「瞄准型轨道在目标集为空时**整轨不发射**」（`TrackRunner#emitTrack:279`）。
但有两件事仍未定：

- **BOSS 该不该在无目标时保持沉默？** 野生 BOSS 按 `remnant-touhou-bosses`
  的「玩家可离开祭坛」条款不绑定场地，玩家离开 64 格后 BOSS 就是完全安静的。
  这与「索敌范围」是两件事：半径是**有依据**的（见 `LOCK_RADIUS`），
  但「半径外彻底静默」这个**可观测行为**目前没有任何 spec 条款。
- **默认攻击在无目标时是否也该沉默？** `BigFairyEntity#tickDefaultAttack`
  当前直接 `return`（即沉默）。它是「底噪」，但「底噪需要观众」这个判断
  没有写进任何 spec。

两条都属**内容决策**，建议在 `danmaku-track-composition` 或 `remnant-touhou-bosses`
里补条款，而不是留在实现里。**本变更不处理。**

## 阻塞项 C：`TARGET` 段的目标引用在重载后失效 —— **由 `danmaku-leg-motion` 部分解决**

段式运动有三种段来源，其中 `TARGET`（朝实体重瞄）无法由种子复算：

```
                    重载前        重载后
──────────────────────────────────────────────
玩家 entity id       417          1283    ← 重连时重新分配
DATA_BURST_TARGET    417          417     ← NBT 里的旧值
level.getEntity(417) 玩家         某只不相干的怪 / null
方向                  正确          垃圾
```

这在**现有代码里已经是潜在 bug**（`addAdditionalSaveData` 写了
`tag.putInt("BurstTarget", ...)`，但没有任何重解析逻辑），与本变更无关。

环卡上这条几乎必然触发：环在 age 1~59 悬停（重载落在这个窗口内概率极高），
而它的换向在 age 60。**且 48 颗弹里每颗的目标都是不同玩家**（按人复制）。

`danmaku-leg-motion` 的处理：段类型显式区分三种来源，`TARGET` 段由服务端在换向
tick 推一次快照。**目标引用的重载重解析不在本变更范围内** ——
它是灵符/爆散两处的共性问题，应在 `danmaku-target-state` 能力下统一处理。

## 相关：本次一并被钉住的判据

以下几条在 `rework-big-fairy-cards` 里落地为 lint 规则或结构约束，
**与同步机制无关但会影响未来所有符卡作者**，列此备查：

| 判据 | 位置 |
|---|---|
| 多拍显式时间线 MUST 声明循环长度 | `TrackLint#lintTimelineReplays` |
| 径向爆散 MUST 挂参考点帧 | `TrackLint#lintFormation` |
| 方向全向随机的形状 MUST 声明无害窗口且 ≤ 寿命一半 | `TrackLint#lintBeat` |
| 锁定半径单一来源（`bossAttributes` 不接受 followRange） | `AbstractTouhouBoss.LOCK_RADIUS` |
| 「已发生」MUST 是独立状态位，不得从别的状态的缺失推断 | `AbstractDanmakuProjectile#DATA_BURST_FIRED` |
| 跨拍形状的发射原点 MUST 在一轨内锁定 | **本文件所属变更 `fix-ring-card-geometry` 新增** |
