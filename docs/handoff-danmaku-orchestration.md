# 交接文档：八云蓝残魂 → 弹幕编排扩展

> 状态截至 2026-10-15。本文件是**会话交接**，不是 spec。需求与验收以
> `openspec/changes/` 下的两份变更为准。

## 0. 一句话

为 `sheet/bossspellcard.xlsx` 第三页「八云蓝残魂」的 8 张残式，先补齐**非 shape 的运行时能力**，
再补 **shape 层几何**，最后写 8 张卡本身。目前：

| 变更 | 状态 |
|---|---|
| `extend-danmaku-orchestration`（框架层，非 shape） | **已实现 48/61 + 实机回写 4 处缺陷已修**（可编译、单测绿；见 design.md 末尾「实机验收回写」） |
| `extend-danmaku-shapes`（shape 层） | **只提案，未实现** |
| 8 张残式内容（`BossCards.yakumoRan` / `YakumoRanEntity` 默认攻击重做） | **未开始** |

两份变更均 `openspec validate --strict` 通过。

## 1. 需求来源

- `sheet/bossspellcard.xlsx` 第 3 个 sheet「八云蓝残魂」：8 张卡（残式1~8）+ 共通表演契约 +
  默认攻击重做 + 实现前核对。分析与结论见对话历史与本文件 §3/§4。
- 该 sheet 自己列出的「待实现共通支持」：轮次锁轴/目标快照、组级安全取消、
  分阶段清弹与判伤收束、发射源预警、卡内移动锁。
- 已知的 shape 层缺陷（sheet 验收行）：`CAGE` 不读 `gapDeg`、`FAN` 不读 `phase`、
  `RING_FACING` 只有面内速度、`AROUND_TARGET` 出生点可贴脸。

## 2. 仓库常识（踩过的坑，务必照做）

- **Gradle 一律走 wrapper**：`.\tools\gradle_task.ps1 build|compileJava|test|runClient|runServer`。
  不要裸跑 `gradlew`（会让 opencode 的 shell 管道永不返回）。过滤输出用 `-Filter '...'`，
  不要用管道 `Select-Object`/`Out-String`。
- **绝不要用 PowerShell 的 `Get-Content`/`Set-Content` 改仓库里的 UTF-8 无 BOM 文件**。
  PS 5.1 按系统 ANSI(GBK) 读，会把中文写成乱码（本次已踩：`tasks.md` 被写坏后已用 `write` 工具重写修复）。
  改文本用编辑工具，不要用 shell 重写。
- `logs/` 下的改动来自游戏运行，不是本次改动，不要提交。
- JDK 21 在 `gradle.properties`（`org.gradle.java.home`）。

## 3. 已实现：`extend-danmaku-orchestration`

位置 `openspec/changes/extend-danmaku-orchestration/`（proposal/design/specs/tasks 齐全）。
`tools/gradle_task.ps1 build` 通过（含全部既有测试）。

### 3.1 六项能力（对应 tasks.md）

1. **伤害窗口**（Group 1）
   - 前窗无害：出生后 N tick 不掉血、不消失（**全程可见**）。
   - 后窗收束：到收束年龄停判伤 → 淡出 → 以 `RemovalCause.COLLAPSE` 回收。
   - 关键代码：`AbstractDanmakuProjectile` 的 `damageWindowAllows()`（判伤门，加在
     `onHit` 的 ENTITY 分支与 `checkStationaryEntityHit`）、`collapseFadeFactor()`、
     `collapseFinished()`（在 `tickDanmaku` 回收）；字段 `DATA_HARMLESS_UNTIL`、
     `DATA_COLLAPSE`(age<<16|fade)。渲染淡出在 `SphereDanmakuRenderer` / `LaserDanmakuRenderer`。
   - **语义边界**：与「相位隐藏态」（`Visibility.phaseHide` / `/danmaku wall`）不同——
     相位隐藏是周期性明暗；伤害窗口是一次性年龄门。

2. **曲射时段窗口**（Group 2）
   - `Behaviour.Motion.curve(yaw, pitch, rate, startAge, duration)`；窗口外方向冻结。
   - 实体侧 `curveActiveAt(age)` 门控 `tickDanmaku` 的曲射分支；`DATA_CURVE_WINDOW`。
   - 用途：卡 6「先直飞 → 匀速转 N° → 停止转向 → 继续直飞」，无折角。

3. **锁轴组与目标快照**（Group 3）
   - `Track.Beat.Orchestration(lockGroup, leadTicks, collapseAge)` + `Track.Builder.orchestration(...)`
     （builder 级，影响此后添加的拍）。
   - `TrackRunner` 内 `Map<Long, LockSnapshot>`，键 `(trackIndex, lockGroup)`；组首拍捕获
     O/F/R/U + 各目标胸口点（脚位 +0.9）；循环边界 / `selectCard` / `stop` 清除。
     `emitTrack` 对锁轴拍用锁定的 origin/forward/target，不再逐拍重采样。
   - 注意：主 spec `danmaku-track-composition` 早有「跨拍原点锁定」条款，之前实现从未跟上，
     本次补上。

4. **组级安全取消**（Group 4）
   - `TrackRunner.spawnGroupSafe(...)`：整拍出生点净距 < `danmakuMinSpawnDistance`(默认6) ⇒ 整组不发。
   - 用 `lockedTargets`（≤5、非创造/旁观）做距离基准；不平移、不积压。

5. **发射源预警符印**（Group 5）
   - `DanmakuTelegraph` 实体（继承 `Entity`，`noPhysics`、`hurt` 恒 false、不计弹数）。
   - 注册：`ModEntityTypes.DANMAKU_TELEGRAPH`；渲染 `GensokyouClient` 用 `BillboardRenderer`（占位贴图 `ORBIT_ORB`）。
   - 调度：`TrackRunner.scheduleTelegraphs` 在 `step == beat.tick − leadTicks` 生成；
     锁轴组先捕获快照，符印与真弹共用同一原点；符印只活 `lead`，到点自然消失
     （故安全取消时它自然消失，不保留、不转真弹）。

6. **编排静态判据**（Group 6）：`TrackLint.lintOrchestration` —— 领示 ≤ 组首拍、收束 ≤ 循环、
   收束需声明循环。

### 3.2 其它

- **一次性闪亮**：`Behaviour.Visibility.flashAt(age)`；`DATA_FLASH_AGE`；渲染放大脉冲（球弹）。
  视觉 only，**无提示音**（音效声明未做）。
- **同步表示**：`DanmakuMotionState.PARAM_COUNT` 63 → **67**，新增
  `P_HARMLESS_UNTIL=63 / P_COLLAPSE=64 / P_CURVE_WINDOW=65 / P_FLASH_AGE=66`；
  指纹覆盖整块。未声明时默认 0，既有四只 BOSS 零行为变化。
- **配置**（`GensokyouConfig`）：`danmakuCollapseFadeTicks`(10)、`danmakuMinSpawnDistance`(6)、
  `danmakuTelegraphEnabled`(true)。
- **诊断读数**（`DanmakuOrchestrationStats`）：`/gs_boss danmaku` 增加
  `orchestration[lockGroups=.. safetyCancelled=.. telegraphs=.. curveWindowed=..]`；
  `COLLAPSE` 经既有 `removalStats` 输出；`/gs_boss danmaku reset` 一并清零。

### 3.3 改动的文件

```
src/main/java/com/bitsson/gensokyou/
  danmaku/render/DanmakuMotionState.java        （PARAM_COUNT、P_*）
  danmaku/DanmakuEmitter.java                   （伤害窗口/曲射窗口/闪亮接线）
  danmaku/DanmakuBudget.java                    （RemovalCause.COLLAPSE）
  danmaku/DanmakuOrchestrationStats.java        （新，计数）
  danmaku/track/Behaviour.java                  （Motion 曲射窗口、Visibility 闪亮）
  danmaku/track/Track.java                      （Beat.Orchestration、Builder.orchestration）
  danmaku/track/TrackRunner.java                （锁轴快照、安全取消、符印调度、伤害/收束接线）
  danmaku/track/TrackLint.java                  （编排判据）
  entity/AbstractDanmakuProjectile.java         （4 组新字段 + 门控 + 存档）
  entity/DanmakuTelegraph.java                  （新，符印实体）
  registry/ModEntityTypes.java                  （注册 DANMAKU_TELEGRAPH）
  client/GensokyouClient.java                   （符印渲染）
  client/renderer/SphereDanmakuRenderer.java    （收束淡出 + 闪亮）
  client/renderer/LaserDanmakuRenderer.java     （收束淡出）
  config/GensokyouConfig.java                   （3 个新键）
  command/DanmakuTestCommands.java              （验收命令）
  command/BossDebugCommands.java                （读数 + reset）
src/test/java/.../danmaku/track/OrchestrationExtensionTest.java  （新，纯静态）
```

## 4. 实机验收命令（**必须生存模式**，创造/旁观会被跳过且免疫弹幕）

先 `/gs_boss danmaku reset`，跑完读 `/gs_boss danmaku` 的 `orchestration[...]`。

| 命令 | 期望观测 | 失败判据 |
|---|---|---|
| `/danmaku harmless 40` | 前方 12 发弹墙，全程可见；40 tick 内站进去不掉血，之后恢复掉血 | 出生即掉血；过了窗口仍不判伤 |
| `/danmaku wall 20 40 50`（对照） | 周期性忽明忽暗、暗期不掉血 | —（这是既有相位隐藏，用来区分语义） |
| `/danmaku tail 200 120 10` | 年龄 120 停判伤、淡出、约 130 消失；`COLLAPSE` 死因 | 到 200 才消失且全程判伤 |
| `/danmaku curve-window 90 20 40` | 直飞 20t → 40t 内水平平滑转 90° → 直飞；平面弧 | 螺旋 / 折角 / 出生即转 |
| `/danmaku lock 8 20` | BOSS 身后 6 格、半径 4 的环逐颗点亮 8 颗；横移时圆刚性、圆心不动 | 全落在同一角（只见一颗）/ 圆被拉成螺线 |
| `/danmaku safety 3` vs `/danmaku safety 10` | 3 格整组不发（`safetyCancelled`+1）；10 格正常发 | 贴脸照发 / 只取消部分 |
| `/danmaku safety watch` | 朝装置走近 6 格内整组消失，走远恢复 | 不随距离变化 |
| `/danmaku telegraph 20 9` | 先亮符印约 20t → 真弹同原点冒出；`telegraphs`+1 | 无符印直接发 / 原点不一致 |
| `/danmaku flash 30` | 5 颗静止弹第 30 tick 同时放大一帧 | 无闪 / 持续闪 |

## 5. 未完成（`tasks.md` 中 13 项）

- **实体级单测 9 条**：`1.9 / 1.10 / 2.3 / 2.4 / 2.5 / 3.9 / 3.10 / 4.5 / 5.5`。
  都需要能构造 `Level`/实体的测试脚手架——即变更 `danmaku-entity-test-scaffold`
  （已存在但可能仍未落地，开工前先确认）。
- **实机与回写 4 条**：`8.2`（现有四 BOSS 回归）、`8.3`（上表逐条）、
  `8.4`（生存/起伏地）、`8.6`（失败则回写 spec/design，并升级为独立缺陷变更）。

## 6. 后续路线

1. **补完 `extend-danmaku-orchestration`**：先跑 §4 实机；再做 §5 的实体级单测
   （需脚手架）；按 8.6 回写任何失败项。
2. **实现 `extend-danmaku-shapes`**：刚性平行阵列（`PARALLEL_ARRAY`）、带门面板（`PANEL`）、
   轴节点双向激光（`NODE_RAYS`），并修 `CAGE`/`FAN` 两个既有缺陷。
   设计见该变更的 `design.md`（含参数字段映射表；`gapDeg` 当门宽列数等）。
3. **写 8 张残式**：`BossCards.yakumoRan()` 重写 + `YakumoRanEntity` 默认攻击重做
   （sheet 要求取消独立 3 发狐火扇）。逐卡按 sheet 的「锁轴组 / 领示 / 收束」声明填 `orchestration`，
   `collapseAge` 取 220（卡内相对 tick）。
4. 之后才谈实机调平衡与数值（T2、320s、挨弹数 9 等仍是占位目标）。

## 7. 已知限制 / 待决

- **符印渲染是占位**：固定贴图 + 固定缩放，未实现「同步颜色/形状/倒计时脉动」
  （spec 未把它列为 MUST，仅设计里提过）。
- **闪亮无音效**：`danmaku-visual-profile` 那条「提示音只播一次」的 scenario 是条件式，
  当前 `Visibility` 没有音效字段，故不会触发。
- **安全取消基准是 `lockedTargets`**（≤5、跳过创造/旁观），不是「全场所有玩家」。
  远处不在锁定半径内的玩家不计入。
- **收束不跨卡强制清弹**：proposal 明确 Non-Goals。收束由每颗弹自己的年龄门兑现
  （`collapseAge = 卡内收束 tick − beat.tick`）；「死亡立即清全部归属攻击」未做。
- **`harmlessTicks` 之前是死字段**：曾被 `TrackLint` 读、运行期从不读；本次才接线。

## 8. 常用命令速查

```powershell
.\tools\gradle_task.ps1 build                 # 编译 + 测试
.\tools\gradle_task.ps1 test --tests '*OrchestrationExtensionTest'
openspec status --change extend-danmaku-orchestration --json
openspec validate extend-danmaku-orchestration --strict
openspec validate extend-danmaku-shapes --strict
openspec instructions apply --change extend-danmaku-orchestration --json
```

游戏内：`/danmaku ...`（测试台，权限 2）、`/gs_boss danmaku [reset]`、
`/gs_boss spawn <id> [秒] [挨弹数]`、`/danmaku card <boss> [卡序] [秒] [伤害]`。
