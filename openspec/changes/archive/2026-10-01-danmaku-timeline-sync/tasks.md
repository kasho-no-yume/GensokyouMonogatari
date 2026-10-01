> 每条任务都挂一条**可离线断言**的验收。`DanmakuSyncProbeTest` 已经存在并全绿，
> 它是本变更的回归地板：T1 每一步都必须在它之上变好，且不得让既有断言变差。

## T1. 服务器时间轴（event-sync 的前置，无条件）

### T1-a 时钟估计器（纯函数，无世界）—— 已完成

- [x] 1.1 新增 `danmaku/motion/DanmakuServerClock`：状态为
      `anchorServerTime` / `anchorClientTick` / `rate`（默认 1.0）/ 残差界限 / 陈旧度；
      API 为 `observe(serverGameTime, arrivalClientTick)`、`serverTimeNow(clientTick)`、
      `localTickFor(serverTime)`。无世界依赖，可离线测试。
      **验收**：新测试断言 `rate = 1.0` 时 `localTickFor` 与 `anchorClientTick + Δ` 逐位相等。
- [x] 1.2 速率估计：连续观测拟合斜率，斜率变化 MUST 落在配置区间内（默认 `[0.8, 1.25]`）
      才生效；区间外退回 1.0 并进入 `UNCERTAIN`。
      **验收**：注入 5% 慢（0.95）与 5% 快（1.05），断言 `rate` 收敛到真值 ±0.005。
- [x] 1.3 **残差界限**：残差超界 MUST 拒绝并计数，MUST NOT 因为「看起来更准」而接受。
      **验收**：单点注入 8 tick 离群样本，断言 `rate` 与锚点均不变、拒绝计数 +1。
- [x] 1.4 硬重锚 MUST 连续 N 次同向残差才允许（默认 3），避免单包抖动触发。
      **验收**：单次离群后不得重锚；三次同向后必须重锚。
- [x] 1.5 陈旧降级：超过 `stalenessTicks` 无新观测 ⇒ `UNCERTAIN`，停止外推。
      **验收**：静默 N tick 后 `localTickFor` 返回「不可用」而不是继续外推。

> **实现期修正**：斜率的量纲是「每个服务器 tick 走多少本地 tick」，第一版写成
> 倒数。它有欺骗性——`1.05` 的客户端被估成 `0.9524`，**仍落在可信区间内**，
> 于是区间检查放行了一个方向错误的速率。另两处同源错误：残差判据与重锚 streak
> 都拿「被检验的那个点」参与了判据本身的定义，于是恒为 0、静默失效。三处都写进了
> `DanmakuServerClock` 的 javadoc。

### T1-b 接入 —— 已完成

- [x] 1.6 `DanmakuSampleTimeline` 的换算接受斜率；默认 1.0 时**行为逐位不变**。
      **验收**：既有 `DanmakuSampleTimelineTest` 全部不改期望值即通过。
- [x] 1.7 `AbstractDanmakuProjectile#age()` 的客户端分支改读时钟，
      不再读 `tickCount − anchorTick`。
      **验收**：既有 `DanmakuAgeTest` / `DanmakuRenderStateTest` 不改期望值即通过。

> **分解**：速率是**全局**的（连接的属性），锚点是**逐实体**的（不同实体的
> `tickCount` 互相有偏移，而本地历史按各自 `tickCount` 寻址）。跨维度的
> `getGameTime()` 差异是常数偏移而非速率差，由逐实体锚点吸收；若塞进全局时钟，
> 换维度会表现成一次剧烈速率跳变并触发无谓重锚。

### T1-c 接线与验收 —— 已完成

- [x] 1.8 以 `DanmakuCalibrationPayload` 的 `serverGameTime` 为观测通道（**不加新协议**），
      打开速率估计。观测**每批一次**而非每弹一次：批内样本共享同一采样时刻，
      逐弹提交会让后 N−1 次被判非单调而白白浪费；且观测必须放在「可比性判定之外」，
      否则不可比的样本（恰恰是最能说明速率需要修正的样本）永远等不到速率被修正，
>     两者互相锁死。
      **验收**：`rateMismatch*` 系列断言翻转 —— 慢 5% 的
      `TOO_EARLY` 计数从 15/16 降到 **0**；比较误差仍为 0；恢复次数仍为 0。
- [x] 1.9 **反向测试（不可省）**：注入 8 tick 离散年龄基准跳变，时钟 MUST **仍然**把它
      判为 `REVISION_MISMATCH` 并升级。
      **验收**：`discreteAgeBasisStepOnAnIncrementalBulletIsCaughtImmediately` 仍绿。
      若此条失败，本变更制造的问题比它修复的更糟 —— 停下来重做 1.3/1.4。
- [x] 1.10 抖动回归：延迟 0~20、抖动 ±8 全部条件下误差为 0、恢复为 0。
      **验收**：`networkDelayAndJitterCannotProduceErrorOrResync` 仍绿。
- [x] 1.11 诊断接入 `/gs_boss danmaku`：新增 `clock` 行（可用性、速率、残差、
      重锚/拒绝/陈旧计数），`DanmakuSyncStats` 增 `clock[...]` 段。
- [x] 1.12 更正 `danmakuMaxLagTicks` 与 `danmakuCalibrationIntervalTicks` 的语义与
      config 注释：前者是**映射误差上界**（不是网络延迟容忍度），且**对离散基准跳变
      无效**；后者现在**同时是时钟的观测通道**，置 0 不只是减少检查，而是让时钟无法
      学到客户端的快慢。现在的注释仍在描述 render-state 之前的世界。
- [x] 1.13 在 `danmaku-event-sync` 的前置依赖里注明：T1 完成后即可开工，MUST NOT 等
      `danmaku-track-scope`。

> 探针的「速率已知」模式已改为走**真实接线**（`DanmakuClientClock` →
> `DanmakuSampleTimeline.setRate` → `DanmakuSampleCheck.compare`），
> 不再是手抄的模型 —— 否则那条取证只是自说自话。

## T2. 弹道形式分类与读档速度 —— 已完成

> **实施期修订**：本节原写「把六种确定性运动抽成无世界纯函数」+「读档后由轨道时间
> 重算」。逐行核对 `tickDanmaku` 与 `readAdditionalSaveData` 后发现两条都不成立，
> 已按 `design.md` §5b / §5c 收窄。修订依据见各自的「实测」小节。

- [x] 2.1 **分类表**（`danmaku/motion/DanmakuTrackKinds`）：把「弹道相对轨道时间的
      形式」与「读档能否自愈」两条判据放在一处，供 `danmaku-event-sync` 查询
      「这项状态能否由轨道时间推导」。
      **实测**：编队帧 / 速率曲线 / 相位显隐**本来就是**无世界纯函数，无需改动；
      曲射**不可**闭式化（`rotateAbout` 作用在上一步速度上，而闭式化会把
      `Rotation` 的 `sin/cos` 例外从「一次旋转」放大成「整段轨迹」= 行为变更）。
      **验收**：`formOf` / `survivesReloadWithoutVelocity` / `needsVelocityPersistence`
      的真值表；并断言「曲射 + 速率曲线」按形式归 CLOSED_FORM 但靠 profile 自愈 ——
      两条判据不可互相替代，合并会让这枚弹读档后冻结。
- [x] 2.2 超越函数纪律锁：编译后常量池扫描，断言 `DanmakuSpeedProfile` 与
      `FormationFrame` 不引用 `sin/cos/tan/sqrt/pow/exp/log`；同时断言
      `Rotation` **仍然**有（它是已记录的例外，纪律不得顺手扩展过去 ——
      扩展会立刻改变编队弹轨迹，而没人会想到原因是这个）。
      **验收**：`speedProfileAndFormationFrameStayTranscendentalFree`、
      `rotationIsTheOneRecordedException`。实测两个纪律类当前**零**超越函数。
- [x] 2.3 位置闭式求值：断言同一龄的解析位置与求值历史无关（长时间运行不累积误差）。
      这是 rig 存在的**全部**理由；改成「在上一 tick 位置上叠加增量」会让误差单调增长，
      而症状是弹道缓慢发散，几乎不可能被归因到那一行。
      **验收**：`formationPositionIsClosedFormSoErrorDoesNotAccumulate`。
- [x] 2.5 **编队弹读档后是否冻结：不冻结。** `DATA_HAS_FRAME` 按 `FrameSp` 键存在
      **推断**恢复，rig 从第一个 tick 起就用 `positionAt(age)` 覆写位置。
      速率曲线弹同理（`DATA_HAS_PROFILE` 按 `SpV3` 推断 + `alongAxis` 回落到 `axis()`）。
      ⇒ 真正冻结的只有**曲射**与**直线**两类。**因此 2.4 不推广到编队弹**，
      并且给它们写速度是**误导**（速度不是它们的权威）。
- [x] 2.4 读档速度往返：新增 `MotionX/Y/Z` 三个 double，**仅**对
      `needsVelocityPersistence` 为真的弹种写盘与读盘。
      **读侧必须最后读** —— 判据依赖 `DATA_HAS_FRAME`/`DATA_HAS_PROFILE`，而这两个是
      按键存在推断出来的，提前读会拿到尚未推断的 `false`，于是给一枚编队弹安上本不该
      存在的速度。
      **缺键一律退化为零速度**，即本变更之前的行为：旧存档全都没有这个键，
      缺键时抛异常会让读档直接失败 —— 那比冻结严重得多。
      **验收**：`velocityRoundTripsExactly`（逐项 putDouble，不被量化再吃一次）、
      `unneededKindsWriteNothing`、`missingKeyDegradesToTheOldBehaviour`、
      `partialTagDegradesPerComponent`（逐项降级，不整体丢弃）。
      **仍需实机**：存档→读档后弹以正确速度离场（不是停住 60 秒后消失），
      覆盖普通弹、玩家武器弹与「静止等待 N tick」编排三类。纯函数层已覆盖，
      NBT 与实体的接缝留给 3.4。

## 验证与交付

- [x] 3.1 跑通探针全套 + 既有 danmaku 测试全集（T2 完成后为 675 tests / 80 suites，
      不得减少）。
- [ ] 3.2 dedicated server 双客户端错时追踪：区分正常传播延迟、时钟偏移、实体缺失与
      真实轨道不一致。**这是探针覆盖不到的部分**（二次 `StartTracking`、真实 chunk 重载）。
- [x] 3.3 实机在 `onStartTracking` 打出 `entityId / serverAge / 已配对次数`，归因 rebuilt 弹
      ±4~8 tick 抖动 —— 验证 design「Open Questions」机制 A（`seedPeerAge` 在客户端实体仍存在时
      被二次改写）。注意 render-state 落地后 `age()` 在 `ageAnchored` 时**不读** `peerAge`，
      所以该路径可能已失效；「可能」需要实测，不需要推理。
      计数口径已先修：`StopTracking` 现在结束追踪周期（`endTrackingPeriod`）。原先
      `trackingPairings` 永不清零，于是「玩家飞远再回来」的合法重新配对与真正的重复触发被数在
      一起 —— 实测 `integrity[pairing=2727]` 与同处注释「健康状态 MUST 为 0」自相矛盾。
      修完后 `pairing` 只统计同一追踪周期内的重复，该数才可用于判定。
- [ ] 3.4 完成 NeoForge 编译与客户端实机回归。

## 移出本变更

以下进入 `danmaku-track-scope`，其 proposal 里写明门槛为**生成突发的实测带宽基线**，
且不阻塞任何其它变更：

轨道快照编码、`scopeId`/`scopeVersion`、实例索引与实例身份绑定、轨道生命周期与清理、
共享编队参数下发、单一实体类型灰度、关闭位置驱动的带宽门槛。
