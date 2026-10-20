## Context

### 现状一：组表单调增长，撞 512 上限崩服

崩溃日志 `run/crash-reports/crash-2026-10-20_21.18.11-server.txt`：

```
java.lang.IllegalArgumentException: group limits/slots
  at DanmakuWorldRuntime.createGroup(DanmakuWorldRuntime.java:255)
  at ServerControlOperations$1.lambda$execute$5(ServerControlOperations.java:224)   ← SurfaceFire 分支
```

`createGroup` 的第一条守卫是 `groups.size()>=limits.groups()`，`limits.groups()` = **512**（`BossProgramSettings.java:17`）。

组的回收判据在 `collect()`（`DanmakuWorldRuntime.java:657`）：

```java
groups.values().removeIf(g -> !g.emitting && g.members.isEmpty() && g.plans.isEmpty()
        && groups.values().stream().noneMatch(child -> Objects.equals(child.parent, g.id)));
```

只回收 `!emitting` 的组。而循环型符卡里组永远 `emitting=true`：

| 泄漏源 | 为什么每轮新建 | 每轮组数（大妖精） |
|---|---|---|
| `server:group`（`arena`/`aimed`/`offset`） | 声明写在 `AtNode(CYCLE,0)`，`ProgramSchedule` 在**每个循环边界**都重新派发该 op（`:104` 的 `clocks.cycleAt(tick)` 把循环锚点前移，故 `base>=scopeStart` 恒成立） | 底噪墙 1（60 tick/轮）、雨卡 2、花卡 16、环卡 5 |
| `server:surface_fire` | 每拍新建一个一次性组 | 雨卡 10 拍 × 2 = 20（200 tick/轮） |

`retain=true`（`ServerControlOperations.Group` 的第 8 个构造参数，由 `CardProgram.group()` 恒传 `true`）让循环边界的 `endCycle()`（`:633`）也跳过它们：

```java
if (clearRetained || !g.retain) { stopPlans(g.id); cancel(g.id,0,"cycle-end"); }   // retain ⇒ 什么都不做
```

⇒ 组永远回收不掉，直到换卡时 `endCard` 一次性 `cancel` 掉。

```
组表（单调增长）
────────────────────────────────────────────────────────► tick
  底噪墙    1 组 / 60 tick     → 11 000 tick ≈ 183 组
  雨卡     22 组 / 200 tick    → 11 000 tick ≈ 1200 组     ← 撞 512
  ⇒ createGroup 抛异常 → 服务端 tick 崩服
```

对照组：`server:emission`（通用发射操作，`ServerOperations.java:55`）**已经做对了**——`commitAtBirth(..., seal=true)`，出生时封口，成员消亡后被 `collect()` 回收。只有 `server:group` 与 `server:surface_fire` 两条路漏。

现有测试抓不到：`ProgramContentTest.bigFairyRainPeakFitsTheStandardBudgetAndReclaims` 只跑 220 tick（1 轮多一点，按内容手算峰值约 28 组），它那句 `groupsAt201 < peakGroups` 实际是「峰值恰好落在窗口末尾」才成立，不是回收在工作。

### 现状二：切牌被循环边界挂起

`BossProgramBattle.java:55-60`：

```java
if (current >= 0) {
    var card = deck.card(current);
    long elapsed = tick - cardStart;
    if (card.cycleTicks() > 0 && elapsed > 0 && elapsed % card.cycleTicks() != 0) return;   ← 挂起
}
installCard(selected, tick + 1);
```

大妖精雨卡循环 200 tick ⇒ 跨阈值后最多等 10 秒才换牌。这是写进规格的（`danmaku-track-composition` §符卡切换延后到循环边界、`kurodani-yamame` §阶段随血量切换不叠加），不是实现偏差。

### 现状三：激光判定半径被砍半

`Pattern.Style.effectiveCollisionRadius()` = `collisionRadius * 0.5`，`BalanceNumbers.PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE` 的注释写着「普通弹与激光统一应用」。`ProgramCollisionAdapter.java:64` 对两种 profile 一视同仁。

| | 迁移前（旧实体） | 程序路径声明 | 程序路径实际判定 |
|---|---|---|---|
| 大妖精雨卡光柱 | `Projectile.laser(80, 0.8, 2, 6)` → **0.8** | 0.4 | **0.2** |
| 八云蓝卡 4 激光 | `Projectile.laser(60, 0.35, ...)` → **0.35** | 0.18 | **0.09** |

旧路径 `LaserDanmaku.damageEntitiesInBeam` 用 `box.inflate(radius)`，**判伤半径 == 渲染半径**；程序路径渲染取 `visualRadius`、判定取 `effectiveCollisionRadius`，判定只有视觉的一半。

### 约束

- **批次（batch）语义已经是对的**：`TailPolicy.RETAIN_UNTIL_LIFETIME` 的含义就是「上一轮的成员留到各自寿命结束」。大妖精三张卡与山女 1~4 卡全是 `RETAIN`，第一批花海不会被第二批顶掉。坏的只是**组壳子**回收不掉。本 change MUST NOT 改动批次语义。
- 每轮一份独立组 id 是「按批次施加的操作只作用于该批次」的**唯一**实现方式（见 D4）。
- 项目无自定义音效资源（`assets/gensokyou/` 下无 `sounds/`），第一版演出只能用原版音效 id。

## Goals / Non-Goals

**Goals:**

- 组数在任何长跑下收敛到上界，不再撞 512 上限崩服。
- 组的「能否继续发射」「程序是否还会引用」「已出生成员」三者分离，任何回收动作都不得提前杀死已出生批次。
- 血量跨阈值即刻切牌，与原作手感对齐。
- 切牌有可辨识的演出，且演出不改变任何判定、伤害与内容时序。
- 激光判定半径按碰撞形态分流，普通弹的平衡倍率不受影响。

**Non-Goals:**

- 不改任何符卡的节拍、伤害、寿命、收束时序与阈值。
- 不改旧实体弹幕路径（`LaserDanmaku` / `DanmakuEmitter` / `TrackRunner`）的行为。
- 不做「同名组跨轮复用同一个 id」的优化（见 D4）。
- 不新增携带卡名字符串的网络包。
- 不引入新的第三方依赖、不做存档格式迁移（运行时 checkpoint 新增一个布尔位，需在 `ProgramSessionCodec` 中往返，见 D1）。

## Decisions

### D1 组的三个状态分离：`emitting` / `retired` / `members`

```
┌──────────┬───────────────────────────────────────────────────────────┐
│ emitting │ 「能否接受新的发射计划（prepare）」                        │
│          │   false = 被取消（stopEmission / cancel / 封口）          │
│          │   ★ 退休不动这个标志                                     │
├──────────┼───────────────────────────────────────────────────────────┤
│ retired  │ 「程序不会再引用这个组了」（循环已过 / 一次性已用完）     │
│          │   ★ 只被 collect() 读；发射路径一律不读                   │
├──────────┼───────────────────────────────────────────────────────────┤
│ members  │ 已出生的批次成员 —— 按各自寿命自然消亡                     │
│          │   ★ 谁都不许提前动它                                     │
└──────────┴───────────────────────────────────────────────────────────┘
```

`retired` 是**纯记账标志**：它只参与回收判据，任何发射/碰撞/表演路径都不得读它。这样「回收」与「取消」彻底解耦。

**为什么不用 `emitting=false` 当退休**：`commit()` 里有 `String reason = !g.emitting ? "stopped" : admission(...)`（`:459`）——非 emitting 的组，出生即被拒。退休若顺手拉低 `emitting`，跨循环边界尚未出生的批次会被静默丢弃（见 D2 的雨卡算例）。

**为什么退休不能挡 `prepare()`**：山女丝线母弹挂了子派生（`YamamePrograms.java:54` 的 `.emit(new Selection.Tag("carrier"), ...)`），`runChildrenPass()` 靠**临时把 `emitting` 抬回 true** 才 prepare 得进去（`:845-853`）。若退休禁了 prepare，尾珠派生会直接抛异常崩服。

**替代方案（已否）**：
- *给组加引用计数，程序释放句柄时减一* —— 句柄在循环边界已被 `releaseOldOccurrences` 清掉，计数会漏；且句柄释放与「程序不再引用」不是同一件事。
- *给组加 `lastTouchedTick`，按超时 GC* —— 需要猜一个「多久算没人用」的阈值，跨循环边界未出生的批次会被误杀，且不可判定。

### D2 退休的触发点 = 循环边界（`endCycle`）

`endCycle(encounter, card, cycle-1, clear)` 在每个循环边界被 `ProgramSession.advance` 调用（`:42`），它按 `g.card==card && g.cycle==cycle` 精确命中「刚走完的那一轮」的组。新增行为：

```java
for (Group g : ...) if (g.encounter.equals(encounter) && g.card==card && g.cycle==cycle) {
    if (!g.background && (clearRetained || !g.retain)) { stopPlans(g.id); cancel(g.id,0,"cycle-end"); }  // 取消路径：原样
    else g.retired = true;                                                                              // 退休路径：新增
}
```

退休路径**覆盖底噪组**（`!g.background` 只保留在取消路径上），因为底噪才是每 60 tick 攒一个组的大头。

**跨边界未出生批次必须活着**：雨卡拍距 20、预警 80 ⇒ 拍 120/140/160/180 的雨，出生 tick 落在**下一轮**（200/220/240/260）。这些批次的组属于第 0 轮，在 tick 200 的边界会被标记退休。退休不动 `emitting`、不动 `g.plans` ⇒ `commit()` 照常放行 ⇒ 雨照常落下。**这正是「每批各自生命周期」的含义**：这一批还没出生，就不该被上一轮的边界顶掉。

**替代方案（已否）**：*在同名组再次声明时退休上一份声明*。语义上更贴近「声明」，但上一轮声明的句柄已在边界被 `releaseOldOccurrences` 清掉，得另建一张「声明表」映射逻辑名 → 最新组 id，并处理「同名组声明了但上一轮还没到边界」的交错。复杂度显著高于按轮退休，且按轮退休已经足够收敛。

### D3 一次性组在出生时封口（seal）

`ServerControlOperations` 的 `SurfaceFire` 分支改用 `commitAtBirth(plan, policy, ownershipEnd, true)`（第四参 `seal=true`），与 `server:emission` 保持一致：`commit` 落地的瞬间 `stopPlans` ⇒ `emitting=false` ⇒ 该组不再发射 ⇒ 成员消亡后被 `collect()` 回收。

`stopPlans` 只做 `emitting=false; plans.clear()`，**一个成员都不碰** ⇒ 批次寿命不受影响。

### D4 否掉「同名组跨轮复用同一个 id」

这是本 change 明确**不做**的优化，因为它违反「每批各自生命周期」。

花符（240 tick/轮，每朵一组，寿命 400，爆散在 +100）：

```
cycle 0   tick   0   声明 flower0 → 组 G0，发射 → 批次 A（1核心+45花瓣，寿命400）
          tick 100   release(G0) + reaim(G0)      ← 只作用于批次 A
cycle 1   tick 240   声明 flower0 → 发射 → 批次 B（寿命400）
          tick 340   release(?) + reaim(?)        ← 必须只作用于批次 B

批次 A 到 tick 400 才死 ⇒ tick 340 时 A 与 B 同时活着
```

若复用同一个 id（G0 ≡ G1）：

| 操作 | 复用后发生什么 | 后果 |
|---|---|---|
| `reaim(group, -1, tag("core"), 0.3)` | 命中组内**所有**核心 → A 与 B 的核心一起被重新瞄准 | ✘ 批次 A 被二次操作 |
| `release(group, core→petals, 0.2)` | 命中组内**所有**花瓣 → A 的花瓣被二次径向爆散 | ✘ 批次 A 被二次爆散 |
| `Clocks` 刷新 | cycle 时钟推进 → A 的 CYCLE 域表演窗口相位漂移 | ✘ |

**每轮一份独立组 id + 循环作用域句柄（`instance:[cycle]:flower0`）正是「按批次施加的操作只作用于该批次」的机制本身**，不是浪费。D2 的退休方案保留了这个机制。

### D5 切牌即刻切换（程序后端）

**后端范围**：本条只改**程序后端**（`BossProgramBattle`）。旧实体后端（`danmaku/track/TrackRunner.java:182` 的 `selectCard`）的挂起分支**一行不动** —— 那条通路服务的 BOSS 符卡内容要随旧后端整体退役，届时该分支一并消失，为它单独立规格条款没有意义。因此规格侧**不保留任何旧后端的豁免或保留条款**：旧条款整条移除，新条款只约束程序后端。

`BossProgramBattle.tick()` 去掉循环边界挂起：

```java
public void tick(double hpFraction, long tick) {
    if (!active()) return;
    int selected = deck.select(hpFraction);
    if (selected == current) return;
    installCard(selected, tick + 1);          // 去掉 elapsed % cycleTicks 的挂起判断
}
```

- `deck.select` 是血量的单调函数 ⇒ 不会来回抖。
- 跨过多张阈值时按「起始门槛已达成的最后一张」直接落到最终应处的符卡（既有规则，不变）。
- 新符卡的循环从 0 重新计时（`installCard` 置 `cardStart = firstTick`）。
- 旧符卡的实例在 `session.endCard` 中被移除，其循环不再推进 ⇒ 不会与新符卡叠加。

**旧弹幕淡出而非消失**：`runtime.endCard` 的 `cancel(g.id, 0, "card-end")` 改成带 fade 的取消 ⇒ 旧弹幕边飞边消散、边消散边无害（`cancel` 在 fade>0 时把成员快照换成 `harmful=false` 并置 `fadeEnd`，客户端按 `group.fadeEnd()` 画淡出）。这是既有的淡出机制，切牌只是开始使用它。**淡出窗口取 30 tick（1.5 秒）** —— 够看清「旧的一波退场、新的接上」，又不至于让旧弹在场上滞留过久。

**替代方案（已否）**：*保留挂起但把挂起窗口缩短到 N tick*。原作是达阈值即切，缩短窗口仍是折中，且会引入「有时切有时不切」的不可预期手感。

### D6 切阶段演出：世界层 + 屏幕层，第 3 层换成「旧弹幕退场」

原作的冲击 = 音效 + 浮空立绘 + 符卡名。本项目**没有立绘**，但世界观里「符卡是写出来的二维物」——用**符纸 + 法阵 + 朱印**替代立绘比立绘更贴。

```
┌──────────────────────────────────────────────────────────────┐
│ 层 3 弹幕层：本期不做「真定格」，改做「旧弹幕退场」          │
│                                                              │
│   ✘ 真定格（子弹时间）——评估结论：麻烦，不做                  │
│     客户端弹幕呈现时钟 ProgramPresentationClock：             │
│       if (authority-time>4) { time=authority-1; resets++; }  │
│     落后超过 4 tick 就强制重锚（防无限外推）。要让弹幕定格    │
│     15 tick，必须让 authority 停止推进 ⇒ 改的是这个时钟的    │
│     契约，而它有独立规格（danmaku-program-gameplay          │
│     §有界显示时钟恢复）与独立测试，失败模式是客户端与服务端  │
│     失步。麻烦度不在代码量，在契约。                          │
│                                                              │
│   ✘ 全屏压暗——评估结论：更糟，不做                           │
│     弹幕在压暗期间仍然判伤。压暗 = 玩家看不清却照样挨打。     │
│                                                              │
│   ✔ 旧弹幕退场（本期做）                                     │
│     服务端 cancel 带 30 tick fade：旧弹幕边飞边消散、         │
│     边消散边无害。与白闪、横幅叠在一起，读作「旧的一波被     │
│     抹掉，新的接上」。                                        │
├──────────────────────────────────────────────────────────────┤
│ 层 2 屏幕层：本期做                                          │
│   屏幕中央偏上：符卡名大字 + 背后的旋转法阵                   │
│     （复用 fx/magic_circle.png）+「符」朱印                  │
│   + 起始 2~3 tick 的一次白闪（复用 ShatterScreenFx 的        │
│     additive 手法）                                           │
│   + 声明音效                                                 │
│   ★ 法阵与白闪一律加法混合 ⇒ 不遮挡弹幕                     │
├──────────────────────────────────────────────────────────────┤
│ 层 1 世界层：本期做（几乎零成本）                            │
│   旧弹幕淡出（D5 顺带达成）                                   │
│   BOSS 触发一次「宣言」姿态（已有 onProgramCardChanged 钩子）  │
└──────────────────────────────────────────────────────────────┘
```

**声明事件复用现有包**：`SpellCardNamePayload(entityId, cardIndex)` 已在切牌时单播给跟踪者。客户端按「符卡下标**发生变化**」区分「宣言」与「状态补发」：

- 切牌 ⇒ 客户端已有旧下标 ⇒ 收到不同下标 ⇒ 播宣言。
- `StartTracking` 补发 ⇒ 客户端对该实体**没有**记录 ⇒ 只更新状态，不播宣言。

`ClientSpellCardNames` 在 `EntityLeaveLevelEvent` 与登出时清空 ⇒ entityId 被回收后不会误播。

**MUST NOT 新增携带卡名字符串的包**：符卡名由 `BossProgramCards` 的 lang 键在客户端本地反查（既有 `touhou-boss-bar` 契约）。

**MUST NOT 让演出改变判定**：横幅、白闪、姿态、音效全部是表现层；符卡内容的节拍与时序不动。

### D7 激光判定半径按碰撞形态分流

`ProgramCollisionAdapter.collide()`：

```java
double hitRadius = laser ? style.collisionRadius()            // 激光：声明值，不打折
                         : style.effectiveCollisionRadius();  // 普通弹：×0.5 维持现状
```

`BalanceNumbers.PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE` 的注释同步改为「仅作用于弹射物型弹幕」。

`ProgramRenderView` 里那个 `effectiveCollisionRadius()` 只喂给 `Member.radius`，而客户端渲染器读的是 `style().visualRadius()` ⇒ **改判定半径不会动画面**，视觉/判定比例从「1:2」回到「1:1」。

**声明半径是否补回迁移前数值**（大妖精 0.4→0.8、八云蓝 0.18→0.35）是一个**独立的数值决策**，见 Open Questions。本 change 默认**只修分流、不动声明值**：分流后判定 == 视觉，与旧路径「判定 == 渲染」的口径一致；而更细的光柱在 3D 里更好躲，属于内容调优。

### D8 回归测试：收敛性而非峰值

新增断言「组数随循环数**收敛**」而不是「峰值不超过 512」：

```
跑 N 个循环后，断言 groups(第 N 轮) ≤ groups(第 2 轮) + 1 轮增量
外加：长跑 10 000 tick 后组数 ≤ 明确上界
```

峰值断言在新方案下恒真（峰值从一开始就很低），抓不到泄漏；收敛断言才能。

## Risks / Trade-offs

- **[退休标志被误当作取消]** 后续有人给 `retired` 加语义（例如「退休即停止发射」）会直接破坏跨边界批次 → `retired` 的定义写进 spec，且在 `Group` 字段上标注「只被 collect() 读」。
- **[`endCycle` 的 `!g.background` 过滤被顺手删掉]** 会让山女底噪的 `END_AT_BOUNDARY` 语义开始生效（旧轮被清空），那是内容行为变更 → 取消路径保留过滤，退休路径单独覆盖底噪；改动后需比对山女底噪的弹幕存量。
- **[切牌即刻化让符卡被截断]** 花符每 4 tick 一朵共 64 tick，中途切牌会截断未放出的花 → 这是原作行为（牌被打穿即结束），不是缺陷；但需要在实机确认「截断后没有半朵花悬在半空」。
- **[切牌即刻化与 `cardStart` 记账]** `BossProgramBattle.adopt()`（读档恢复）把 `cardStart` 设为 `controller.runtimeTick()`，恢复后第一轮循环边界可能与立即切换叠加 → 恢复路径需单独走查，确认不会重复安装同一张卡。
- **[淡出期间旧弹幕仍占预算]** 淡出的旧弹幕仍计入 `liveMembers` 与网络开销 → 淡出窗口 30 tick，确认不撞 `liveMembers` 上限（8192）。
- **[演出层的客户端帧开销]** 全屏 additive 叠层 + 法阵旋转在低端机上可能掉帧 → 白闪与法阵用同一批次提交，帧预算参照 `danmaku-render-state` 的密度降级思路。
- **[宣言窗口与新卡第一波重叠]** 立即切牌意味着新卡的第一拍在切换后的下一 tick 就开始，可能与横幅同屏 → 用加法混合保证弹幕不被遮挡；部分内容的批次自带预警（雨卡 80 tick），环卡的环弹生成在 BOSS 身后 6 格，实机上确认不构成「看不清却挨打」。
- **[音效用原版 id 撞车]** `minecraft:entity.ender_dragon.growl` 在末影龙战也会播 → 作为占位登记，后续换自制素材。

## Migration Plan

1. 先落运行时修复（D1~D3）与收敛回归测试（D8），跑 `compileJava` + 相关测试。
2. 再落判定分流（D7）。
3. 再落切牌即刻化与淡出（D5）。
4. 最后落演出层 1 + 2（D6）。
5. 全量 `build` + 实机：一场大妖精打到死，确认不崩、切牌跟手、激光判定与视觉一致、宣言演出可见。

**回滚**：三部分互相独立，可单独回滚。运行时修复回滚后仍会崩服（这是既有缺陷），故优先级最高、必须先落。

**存档**：`Group` 新增 `retired` 布尔位 ⇒ `ProgramSessionCodec` 需读写该字段；旧存档缺该键时按 `false` 处理（等价于「未退休」，行为与今天一致，只是会继续泄漏直到该轮结束）。

## Open Questions

1. **激光声明半径是否补回迁移前数值？** 分流后大妖精光柱判定 0.4（旧 0.8）、八云蓝 0.18（旧 0.35）。补回则判定与旧版完全一致但光柱更粗更难躲；不补则判定==视觉但整体比旧版细。默认不补，待实机确认。

## 已定事项（用户决策，2026-10-20）

| 议题 | 决定 |
|---|---|
| 旧实体后端（BOSS 符卡 legacy） | 不改、不为它立规格条款 —— 该后端将随符卡内容整体退役，届时一并删除 |
| 演出第 3 层 | 不做「真定格」与「全屏压暗」（见 D6 的评估）；第 3 层改为**旧弹幕 30 tick 服务端淡出** |
| 声明音效 | `minecraft:entity.ender_dragon.growl` —— 1.21.1 的 `SoundEvents` 里没有专门的「复活」音效，这条吼叫（音量 2.5）就是「龙出现/咆哮」那一声。走既有 `SimpleSoundInstance.forUI` 通路（与程序弹幕音效同一条），故不受距离衰减 |
| 切牌无敌帧 | **不加** —— 无敌帧会让实测时长偏离 `bossSeconds` 目标（boss-dev-design §5） |
| 旧弹幕淡出窗口 | **30 tick**（1.5 秒） |

