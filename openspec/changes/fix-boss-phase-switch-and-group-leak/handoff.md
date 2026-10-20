# 交接：BOSS 符卡切牌手感 + 程序运行时组泄漏

更新：2026-10-21，本会话结束时的权威状态。**先读本文件，再读 `proposal.md` / `design.md` / `tasks.md`。**
改动**未提交 git**（见 §6 的文件清单）；change **未归档**（tasks 还剩 1 项未勾）。

---

## 1. 一句话结论

用户提了三个问题，本 change 全部落地并有自动化覆盖：

| # | 问题 | 状态 |
|---|---|---|
| 1 | 打着打着崩服（`group limits/slots`） | ✅ 已修。根因是**组表单调增长**，不是别的 |
| 2 | 换符卡阶段不敏感 + 要震撼的切阶段演出 | ✅ 达阈值即切已做；演出做了世界层 + 屏幕层；**实机未验收** |
| 3 | BOSS 激光判定太小 | ✅ 判定半径不再被 0.5 倍率砍半；**声明半径待拍板** |

**进度 29/30。** 剩下的 5.7 是实机验证（见 §5）。

---

## 2. 问题一：崩溃（已修）

### 2.1 根因

崩溃日志 `run/crash-reports/crash-2026-10-20_21.18.11-server.txt`：

```
java.lang.IllegalArgumentException: group limits/slots
  at DanmakuWorldRuntime.createGroup(DanmakuWorldRuntime.java:255)
  at ServerControlOperations$1.lambda$execute$5(ServerControlOperations.java:224)   ← SurfaceFire 分支
```

`createGroup` 第一条守卫是 `groups.size()>=limits.groups()`，**上限 512**
（`BossProgramSettings.java:17` → `Limits(8192, 512, 512, 512, 40000)`）。

组回收判据在 `collect()`：旧实现只回收 `!emitting` 的组。而循环型符卡里组**永远** `emitting=true`：

- `server:group`（`arena`/`aimed`/`offset`）声明在 `AtNode(CYCLE,0)`，
  `ProgramSchedule.repeat` 把循环锚点前移（`:104` 的 `clocks.cycleAt(tick)`），
  故该 op **每个循环边界都重新派发** ⇒ 每轮新建一个组。
- `server:surface_fire` 每拍新建一个一次性组。
- `retain=true`（`CardProgram.group()` 恒传）让循环边界的 `endCycle()` 也跳过它们。

⇒ **组表单调增长，撞 512 后 `createGroup` 抛异常，服务端 tick 崩掉整个战斗。**

数量级（雨卡 200 tick/轮，每轮 22 个组；底噪墙 60 tick/轮 1 个组）：
实机那局约 11 300 tick 后撞顶。

### 2.2 修法：把组的三个状态拆开

```
┌──────────┬──────────────────────────────────────────────────┐
│ emitting │ 能否接受新的发射计划（prepare）                 │
│          │   false = 被取消（stopEmission / cancel / 封口） │
│          │   ★ 退休不动这个标志                            │
├──────────┼──────────────────────────────────────────────────┤
│ retired  │ 「程序不会再引用这个组了」                        │
│          │   ★ 只被 collect() 读；发射路径一律不读          │
├──────────┼──────────────────────────────────────────────────┤
│ members  │ 已出生的批次成员 —— 按各自寿命自然消亡            │
│          │   ★ 谁都不许提前动它                            │
└──────────┴──────────────────────────────────────────────────┘
```

- **退休**（`endCycle` 在循环边界标记上一轮的组，含底噪组）= 「程序不会再引用它了」，
  只影响回收。**不是取消**。
- **封口**（`surface_fire` 组在批次出生时 `commitAtBirth(..., seal=true)`）= 一次性组出生后不再发射。
  这是抄 `server:emission`（`ServerOperations.java:55`）**本仓库已有的正确写法**。
- `collect()` 判据从 `!emitting` 放宽为 `(!emitting || retired)`。

### 2.3 ⚠️ 三个必须避开的坑（改这个模块前先读）

**坑 1：退休不能设 `emitting=false`。**
`commit()` 里有 `String reason = !g.emitting ? "stopped" : admission(...)`（`DanmakuWorldRuntime.java:459`）
—— 非 emitting 的组，出生即被拒。退休若顺手拉低 `emitting`，
**跨循环边界尚未出生的批次会被静默丢弃**（雨卡拍距 20、预警 80 ⇒ 拍 120/140/160/180 的雨
出生 tick 落在下一轮，这正是「上一批还没出生就被顶掉」）。

**坑 2：退休不能清 `g.plans`。**
`g.plans` 是「已 prepare、未出生」的批次；清空它，跨边界的 pending commit 会因
`commit()` 里 `!plan.equals(g.plans.get(...))` 而静默失败。

**坑 3：退休不能挡 `prepare()`。**
山女丝线母弹挂了子派生（`YamamePrograms.java:54` 的 `.emit(new Selection.Tag("carrier"), ...)`），
`runChildrenPass()` 靠**临时把 `emitting` 抬回 true**（`DanmakuWorldRuntime.java:845-853`）
才 prepare 得进去。退休若禁了 prepare，尾珠派生会直接抛异常崩服。

⇒ **`retired` 只能被 `collect()` 读。** 任何「给 `retired` 加语义」的后续改动都会破坏跨边界批次。

### 2.4 明确**没有**做的优化（别再提）

**「同名组跨轮复用同一个 id」被显式否掉。** 它违反「每批各自生命周期」：

```
花符（240 tick/轮，每朵一组，寿命 400，爆散在 +100）

cycle 0   tick   0   声明 flower0 → 组 G0，发射 → 批次 A（1核心+45花瓣，寿命400）
          tick 100   release(G0) + reaim(G0)      ← 只作用于批次 A
cycle 1   tick 240   声明 flower0 → 发射 → 批次 B（寿命400）
          tick 340   release(?) + reaim(?)        ← 必须只作用于批次 B

批次 A 到 tick 400 才死 ⇒ tick 340 时 A 与 B 同时活着
```

若复用同一个 id：`reaim(group, -1, tag("core"), 0.3)` 命中组内**所有**核心 → A 和 B 的核心一起被重瞄；
`release(group, core→petals, 0.2)` 命中组内**所有**花瓣 → A 的花瓣被二次径向爆散。
**每轮一份独立组 id + 循环作用域句柄（`instance:[cycle]:flower0`）正是「按批次施加的操作只作用于该批次」的机制本身。**

---

## 3. 问题二：切牌时机 + 演出

### 3.1 达阈值即切

`BossProgramBattle.tick()` 去掉 `elapsed % cycleTicks` 的挂起判断；`cardStart` 随之成为死字段，一并删除。
`adopt()`（读档恢复）复查过：认得出定义哈希 → 下标已等于当前卡，不会重复安装；
认不出（内容改版）→ 下标 -1，随后的 `tick` 按当前血量直接落到应处的符卡。

旧实体后端（`danmaku/track/TrackRunner.java:182` 的 `selectCard`）**一行不动** ——
它服务的 BOSS 符卡内容要随旧后端整体退役。规格侧**没有**为它保留任何条款
（「符卡切换延后到循环边界」整条 REMOVED）。

### 3.2 旧弹幕 30 tick 淡出离场

`DanmakuWorldRuntime.endCard()` 的取消从 `cancel(g.id, 0, ...)` 改成 `cancel(g.id, CARD_END_FADE_TICKS, ...)`，
`CARD_END_FADE_TICKS = 30`（1.5 秒）。离场期间成员被换成 `harmful=false`（不再判伤）但仍在飞。

**⚠️ 连带改动（当时没料到的）**：`cancel` 的 fade **时长**原先没有进网络投影，
客户端的淡出公式固定除以 10（`ProgramRenderView`）。30 tick 的窗口会变成
「前 20 tick 满亮 + 后 10 tick 淡出」⇒ 旧弹与新弹同亮 1 秒，玩家分不清谁在退场。
所以给 `GroupSnapshot` / `ReplicaState.Group` 加了 `fadeTicks`，客户端按各组自己的窗口等比推进：

```java
double fade = group.fadeEnd()==Long.MAX_VALUE ? 1
        : clamp((group.fadeEnd()-tick)/(double)Math.max(1,group.fadeTicks()), 0, 1);
```

**这条链路是「淡出时长 → checkpoint → 网络 → 客户端」的完整一条**，
以后再加别的淡出时长需求时按这条走，不要再写死除数。

### 3.3 演出（`client/BossPhaseTransition.java`，新增）

原作的冲击 = 音效 + 浮空立绘 + 符卡名。本项目**没有立绘**，用
**符名 + 法阵 + 朱印**替代（更贴「符卡是写出来的二维物」的世界观，见 boss-dev-design §16）。

```
t=0     白闪（0.26s，alpha ≤0.35）+ 音效(minecraft:entity.ender_dragon.growl)
        + BOSS 宣言姿态
        屏幕中央偏上：符卡名大字（横向拉开）+ 背后旋转法阵(加法混合) + 「符」朱印
t=0..30 旧弹幕 30 tick 淡出离场
```

- **触发**：符卡下标**真的变了**（`ClientSpellCardNames.put()` 改为返回是否变化）。
  「玩家开始观测时补发当前下标」**不触发** —— 晚进场的玩家不该被补播一遍宣言。
- **不遮挡弹幕**：法阵走加法混合（`POSITION_TEX_COLOR` 四边形，手搓 —— 原版 `blit` 没有颜色属性）；
  白闪刻意压到 0.35；文本是一条细横带。
  **不做全屏压暗**：弹幕在压暗期间仍然判伤，压暗 = 「看不清却挨打」。
- **不做真定格/子弹时间**：客户端弹幕呈现时钟 `ProgramPresentationClock` 有
  「落后超 4 tick 就强制重锚」的契约（防无限外推），要定格 15 tick 得改它的契约，
  失败模式是客户端与服务端失步。详见 `design.md` D6。
- 音效是**原版占位**（项目无 `sounds/` 资源），代码注释里登记了「待换自制素材」。
- BOSS 通用宣言姿态：`AbstractTouhouBoss.onProgramCardChanged` 默认给一次符光爆发
  （一次性粒子，单次广播包）；大妖精/山女在它之上叠加各自的 cast 动画；
  八云蓝没有 cast 动画，靠这个兜底。

---

## 4. 问题三：激光判定

`Pattern.Style.hitRadius(profile)`：激光取**声明值**，弹射物型维持 ×0.5。

| | legacy（旧实体） | 程序路径 authored | 改后实际判定 |
|---|---|---|---|
| 大妖精雨卡光柱 | `Projectile.laser(80, 0.8, 2, 6)` → 0.8 | 0.4 | **0.4**（原 0.2） |
| 八云蓝卡 4 激光 | `Projectile.laser(60, 0.35, ...)` → 0.35 | 0.18 | **0.18**（原 0.09） |

顺带**删掉了 `ProgramRenderView.Member.radius` 这个死字段**：它从没被任何地方读过，
却装着 `effectiveCollisionRadius()`，留着就是「判定半径的第二份可能与服务端口径不一致的副本」。
删掉后「改判定不动画面」成为结构保证。

---

## 5. 待办

### 5.1 tasks 5.7：实机验证（唯一未勾项）

打一场大妖精到死，确认五件事：

| 要确认的 | 怎么看 |
|---|---|
| **不再崩** | 打满一局（3 张符卡 + 底噪），观察是否还有 `group limits/slots` |
| **切牌跟手** | 血量跨阈值后应当**立刻**换牌（不再等 10 秒） |
| **激光判定** | 大妖精雨卡光柱：站在光柱视觉边缘，判定应当与视觉粗细一致（判定 == 视觉半径 0.4） |
| **宣言演出** | 切牌瞬间：白闪 + 符卡名 + 法阵 + 音效 + BOSS 姿态；横幅**不挡弹幕** |
| **旧弹幕淡出** | 旧的一波应当**渐退**而不是瞬间消失；淡出期间不判伤 |

验证要点：
- 用**生存模式**。创造模式玩家免疫非 `bypasses_invulnerability` 伤害且不进目标选择，
  伤害验收一律无效（boss-dev-design 坑 10）。
- 血条观感、弹幕密度、命中判定分开看，不要用「掉血快慢」混着判。

### 5.2 design 里仍开放的待定项

**激光的「声明半径」是否补回迁移前数值。**
分流后判定 == 视觉，比例对了，但整体比旧版细一半（大妖精 0.4 vs 旧 0.8；八云蓝 0.18 vs 旧 0.35）。
当前**默认不补**（更细的光柱在 3D 里更好躲，属内容调优）。
实机看过光柱的手感后再定：补回 = 判定与旧版完全一致但更难躲；不补 = 判定==视觉但更细。

### 5.3 后续可做（本 change 范围外）

- **真定格/子弹时间**（切牌时全屏弹幕定格）：需改 `ProgramPresentationClock` 的有界漂移契约，
  并加历史图像存活保证。`design.md` D6 有评估。
- **自制符卡宣言音效**：项目目前无 `assets/gensokyou/sounds/`。
- 旧实体后端随 BOSS 符卡内容整体退役后，删掉 `danmaku-track-composition` 里
  「符卡切换延后到循环边界」的残留（已在本次 REMOVED，但要确认旧后端退役时没有别处引用）。

---

## 6. 改动清单（未提交 git）

### 新增

| 文件 | 内容 |
|---|---|
| `src/main/java/.../client/BossPhaseTransition.java` | 切阶段演出：状态 + GUI 层 + 音效 |
| `src/test/java/.../client/BossPhaseTransitionTest.java` | 宣言触发规则（补发 vs 真切牌、entityId 复用） |
| `src/test/java/.../danmaku/program/server/GroupLifecycleTest.java` | 退休语义 4 条：成员留存、跨边界未出生批次、两轮组独立、切牌淡出 |

### 修改（main）

| 文件 | 改动 |
|---|---|
| `danmaku/program/server/DanmakuWorldRuntime.java` | `Group.retired`、`GroupSnapshot.fadeTicks`、`CARD_END_FADE_TICKS=30`、`collect()` 判据、`endCycle()` 退休路径、`endCard()` 淡出 |
| `danmaku/program/server/ServerControlOperations.java` | `surface_fire` 封口（`commitAtBirth(..., true)`） |
| `danmaku/program/server/ProgramSessionCodec.java` | 磁盘格式 **v2 → v3**（新增 `retired` 位与 `fadeTicks`），v1/v2 仍可读 |
| `danmaku/program/gameplay/BossProgramBattle.java` | 去掉循环边界挂起；删 `cardStart` |
| `danmaku/program/gameplay/ProgramCollisionAdapter.java` | 按碰撞形态取判定半径 |
| `danmaku/program/ops/Pattern.java` | 新增 `Style.hitRadius(profile)` |
| `danmaku/program/network/ProgramRenderView.java` | 删死字段 `Member.radius`；淡出按各组窗口等比 |
| `danmaku/program/network/ReplicaState.java` | `Group` 增 `fadeTicks` |
| `balance/BalanceNumbers.java` | 倍率常量注释改为「仅作用于弹射物型弹幕」 |
| `client/ClientSpellCardNames.java` | `put()` 返回「下标是否变化」 |
| `client/ClientPayloadHandler.java` | 变化时通知宣言层 |
| `client/TouhouBossBarRenderer.java` | 实体离开/登出时清宣言状态 |
| `entity/AbstractTouhouBoss.java` | `onProgramCardChanged` 默认给一次符光爆发 |
| `entity/BigFairyEntity.java` / `entity/YamameEntity.java` | 叠加 `super`（保留爆发）+ 各自 cast 动画 |

### 修改（test）

| 文件 | 改动 |
|---|---|
| `danmaku/program/content/ProgramContentTest.java` | 删「循环边界挂起」用例 → 换成「达阈值即切」+「一次跨多阈值」；**新增组数收敛用例**；**新增两批共存用例** |
| `danmaku/program/server/WorldRuntimeTest.java` | 2 处：endCard 契约从「立即清空」改为「结束但无害」 |
| `danmaku/program/gameplay/ProgramResumeTest.java` | 1 处：同上（「上一张卡的组已结束」改为「不再发射且成员无害」） |
| `danmaku/program/author/ProgramAuthoringTest.java` | 1 处：同上 |
| `danmaku/program/gameplay/ProgramCollisionGeometryTest.java` | 新增「碰撞半径倍率只作用于弹射物型」用例 |

**4 个既有测试编码的是「endCard = 立即清空」这个旧契约**，淡出语义下必须改。
改的时候不要只是把期望值改大 —— 要改成断言「结束但无害」（`MemberSnapshot.harmful`）。

---

## 7. 验证证据

| 证据 | 位置 | 结论 |
|---|---|---|
| 全量 build | `build/agent-logs/20261021-003328-build.log` | **BUILD SUCCESSFUL**（1180+ 测试全绿） |
| 定向测试 | `build/agent-logs/20261021-002741-test.log` | 4 个旧测试修正后全绿 |
| 服务端启动 | `build/agent-logs/20261021-002936-runServer.log` | `Done (1.259s)!`，无 ERROR/FATAL/Exception |
| lang 审计 | `python tools/lang_audit.py` | 本次改动**零缺失**；31 条缺失全是**既有的** `jei.gensokyou.recipe.zaohua_*` 派生键（与本次无关，是既有基线） |
| 崩溃复现对照 | 临时把退休与封口还原成旧行为后跑 `ProgramContentTest` | 旧行为 `48 → 114` 组（正好 3 轮 × 22）→ **FAIL**；修复后 `48 → 58` → PASS |

**最后那条最重要**：收敛性回归测试**确实能抓到原缺陷**（临时还原验证过），
不是写完就绿、实则抓不到东西的假测试。

---

## 8. 给下一会话的注意事项

1. **所有 Gradle 调用走 `tools/gradle_task.ps1`**，禁止裸 `gradlew`（AGENTS.md）。
2. **改动未提交 git**：`src/` 下 20 个文件 modified + 3 个 untracked，
   `openspec/changes/fix-boss-phase-switch-and-group-leak/` 整个目录 untracked。**保留，不要清理。**
3. **change 未归档**：tasks 5.7 未勾。`openspec validate --strict` 当前通过。
4. **磁盘格式升到 v3**：旧存档（v1/v2）仍可读，缺 `retired` 按 false、缺 `fadeTicks` 按 0。
   行为等价于「未退休」—— 即继续泄漏直到该轮结束，与改动前一致。**读档不崩，但读档后的战斗仍可能有旧组残留。**
5. **不要给 `retired` 加语义**（§2.3 的三个坑）。
6. **不要把「同名组跨轮复用」当优化捡回来**（§2.4）。
7. 改 `endCard`/淡出相关代码时注意 §3.2 的 fade 时长链路。
8. 实机验收用**生存模式**，创造模式不参与目标选择且免伤（boss-dev-design 坑 10）。
9. lang 审计的 31 条缺失是既有基线，不要因为它报非零退出码就以为是自己引入的。
