## Context

### 随机性只存在于发射那一刻

要支持的运动形态：

> 一枚弹朝随机方向快速飞去 → 悬停 1 秒 → 再朝随机方向飞 → 悬停 1 秒 →
> 再朝随机方向飞 → 悬停 1 秒 → 最后朝随机方向飘走。共 3 次随机变向。

这不是「服务端在运行中做决策，客户端需要知道」。这是「**发射时抽 3 个签，
之后按签纯函数推演**」。两者的实现代价差一个数量级：

```
运行时决策（事件协议）                    发射时决策（本变更）
──────────────────────                   ──────────────────────
服务端每 tick 可能改状态                  服务端只在 t=0 抽一次
客户端需要有序事件流                      客户端需要同一组数
缺号 / 乱序 / 重放 / 快照交错              什么都没有
```

`Geometry` 已经是后一种形状：几何只在服务端求值一次、烘进出生数据、
客户端从不重算。本变更把它扩展到运动输入。**这是扩展既有纪律，不是引入新范式。**

### 为什么「按服务器时间重放」不可实现

`danmaku-event-sync` 的核心机制是「按事件携带的服务器时间把状态变更插入时间轴」。
`danmaku-timeline-sync` 的 T1 解除了它的**时间映射**前置，但映射 ≠ 重算：

```
DanmakuSampleTimeline
├── record(localTick, age, position, velocity, revision)   ← 写入已发生的事
├── at(localTick) → Entry                                   ← 只读，供比较
└── localTickFor(serverTime) → localTick                    ← 映射（这才是 T1 给的）
```

`Entry` 里**没有运动参数**。编队帧、速率曲线、爆散标记都在 `SynchedEntityData` 上，
是**无年龄的当前状态**。要「从过去某年龄 T 重新推进」就得把这些一起倒带，
然后重跑 `tickDanmaku` —— 而 `tickDanmaku` 会：

```
撞方块 → discard          撞实体 → onHit → discard
fireSplit → 创建真实实体   tickMine → 结算伤害
stationary → checkStationaryEntityHit → 判伤
```

**它不是纯函数。** 仓库里没有任何东西重放时间线，也不应该有。
所以原设计的 §2 规则 3（「迟到事件不得从到达时刻重新开始表现」）在当前架构里
没有对应机制，只有一条**做不到**的要求。

### 三种段来源，以及为什么必须显式区分

段式运动的每段需要一个方向。方向的来源有三种，性质完全不同：

| 段类型 | 方向来源 | 重载可复算 | 稳态带宽 |
|---|---|---|---|
| `FIXED` | 写在段表里 | ✓ | 0 |
| `SEED` | `f(种子, 段号)` | ✓ | 0 |
| `TARGET` | 服务端读目标**实时位置** | ✗ | 换向时一次快照 |

`TARGET` 是结构性盲区，不是实现偷懒：

1. **重载后目标 id 失效。** `DATA_BURST_TARGET` 存 network id，
   玩家重连时重新分配。现有代码里这已经是潜在 bug（见 `spellcard-blockers.md` 阻塞项 C）。
2. **离线期间的目标轨迹客户端从未见过。** 玩家在环悬停的 3 秒里退出是常事，
   他重生在别处 —— 「age 47 那一刻的朝向」在信息论上就不可复算。

⇒ 只有 `TARGET` 段需要保底推送，且推送形态是**一次快照**，不是事件流。

### 段运动不需要「已结算」标记

现有代码为了表达「换向只发生一次」，付了一个专门的坑：

```java
// AbstractDanmakuProjectile:1075
private boolean timedTurnSettled() {
    if (burstAtTick <= 0) return false;
    return isReclaiming() ? !this.hasSpeedProfile() : burstFired();
}

// :1090 —— burstFired 的注释说明了那个坑
// 「已结算」MUST 是独立的一位同步位，不得从别的状态的缺失推断。
// 早先用「帧没了」当前者，于是从没绑上帧的弹从出生 tick 起就被判为已结算，
// 速率曲线与换向双双被跳过：花一路直飞，既不停也不散开。
```

**那个坑是径向爆散自己挖的** —— 爆散会**解除编队帧**，
而「帧没了」有歧义（「被解除」≠「从来没绑上」）。

段运动不解除任何东西。段索引是年龄的纯函数：

```
段 0: 时长  1s   速率 快   方向 dir(seed₀)      T₀ = 0
段 1: 时长 60s   速率 0    方向 保持            T₁ = 1
段 2: 时长  1s   速率 快   方向 dir(seed₁)      T₂ = 61
...
段索引   = 第一个满足 ΣTⱼ ≤ age 的 j          ← 纯函数，不存
段内已飞 = age − Tᵢ                            ← 纯函数，不存
方向     = f(seedᵢ, 段号)                       ← 纯函数，不存
```

**「已换过」这个歧义从根上不存在。**

### PRNG 状态放哪：两个选项，性质不同

```
放法 A：种子进同步数据，每 tick 现算          放法 B：算好的方向进同步数据
──────────────────────────────              ──────────────────────────────
同步量：3 × int 种子（12 B）                 同步量：3 × 2 × short 偏航俯仰

每 tick：Random(seed).nextInt()            每 tick：读字段

双端一致？                                双端一致？
  需要 Random 逐位一致 ✓                     字段相同即一致 ✓ 无计算
  且调用次数相同 ✓                           不受 PRNG 实现影响
  且 不能有额外调用点 ✗

失败模式：                                 失败模式：
  某端多抽一次 → 永久分叉                    字段写错 → 那一段方向错
  且 无任何症状                              但 校准能发现（位置会偏）
```

**取 B。** 三条理由：

1. **本仓库已经在 `SplitSpread` 上做过同样的选择**：

   > **本类只在服务端分裂那一 tick 求值一次**，结果作为速度经实体数据同步到客户端，
   > 因此不参与双端逐 tick 复算——`sin/cos` 在这里无害

   `SplitSpread` 用了 `sin/cos` 但被豁免，**因为它只求值一次**。
   `DanmakuLegMotion` 的段方向要做完全一样的选择。

2. **放法 A 会把新弹种推进 `DanmakuTrackKinds` 的 `INCREMENTAL` 档** ——
   也就是「读档冻结」和「事件化」的来源。放法 B 让它落在新的 `SEGMENTED` 档。

3. **放法 A 的失败模式是静默的。** 某端多抽一次（例如某个 early-return 分支
   在两端走向不同）→ 从此分叉，而校准只比位置、位置分叉要累积到肉眼可见才超容差。
   放法 B 的失败模式是**响亮的**（方向错 → 位置立刻错 → 校准发现）。

### `DanmakuTrackKinds` 需要第三档

```
                  依赖上一 tick  依赖上一 tick   双端
                  的位置         的速度         逐位一致
────────────────  ────────────  ────────────  ─────────
CLOSED_FORM            ✗              ✗           ✓
  编队帧 / 速率曲线
INCREMENTAL            ✓              ✓           ✗
  直线 / 曲射（sin/cos 逐 tick 累积）
SEGMENTED              ✓              ✗           ✓      ← 新增
  段式运动
```

段运动每 tick 做 `pos += vᵢ`，所以**位置是累加的**（读档需要速度，与直线弹同）；
但 `vᵢ = dir(seedᵢ) · speedᵢ` 只依赖种子与段表，**不依赖上一 tick 的速度** ——
所以两端逐位一致。

这正是它与 `INCREMENTAL` 的分界，也是它能进闭式分支的全部理由。

## Goals / Non-Goals

**Goals:**

- 支持「N 次一次性随机变向」的段式运动，N ≤ 8。
- 随机量在发射时全部确定，之后双端纯函数推演，稳态**零额外带宽**。
- 段方向是**世界坐标的绝对方向**，使整条轨迹是年龄的纯函数。
- 重载后轨迹与从未卸载时**逐位相同**。
- 种子与段表入 NBT，并被运动指纹覆盖。
- 激光改为真的穿墙，判伤与视觉统一，删掉整条方块裁剪路径。
- 为 `TARGET` 段保留一次快照的保底推送。

**Non-Goals:**

- 不等 `danmaku-track-scope`（批级种子 + 实例索引留到其门槛通过后）。
- 不做有界但非一次性的随机（「每 tick 抖动」类）。那种形态一旦失步就是永久分叉，
  没有可重新发现的一致态。
- 不做变长段表。段数定长 8，单段参数打包成一个 int。
- 不改 `DanmakuSpeedProfile` 一个字 —— 它被编队弹的 `travelAt` 闭式积分复用。
- 不改现有任何弹种的运动行为。
- 不做受墙阻挡的激光（用户已确认：表现和逻辑统一，都穿墙）。
- 不让客户端推演结果参与命中、伤害、分裂配额或销毁结算。

## Decisions

### 1. 段方向是绝对方向，不是相对偏转

```
绝对方向（取）                      相对偏转（不取）
─────────────                      ─────────────
dir = dirFromAngles(seed)           dir = rotate(当前方向, seed 给的偏航俯仰)

✔ 完全无状态                       ✔ 符卡作者能表达「相对刚才往左偏 30°」
✘ 作者要自己算世界朝向              ✘ 依赖当前方向 ⇒ 依赖累积状态
                                     ⇒ 变成 INCREMENTAL
                                     ⇒ 进「读档冻结」和「事件化」的路
```

绝对方向让整条轨迹成为 `f(年龄, 段表, 种子)`，这是重载可复算的前提。
相对偏转做不到这一点，而绝对方向已经能满足需求。

**`dirFromAngles` 允许用 `sin/cos`** —— 它在**构造期**求值一次并缓存为字段。
这与 `SplitSpread` 的豁免同构。

### 2. 段方向在构造期解出并缓存，每 tick 路径不读种子

```java
public final class DanmakuLegMotion {
    // 构造期：解出并缓存
    private final Vec3[] cachedDirections;   // 每段一个

    public static DanmakuLegMotion fromSpec(int legCount, int[] packedLegs,
                                            DanmakuRandomState random) { ... }

    // 每 tick 路径：只读缓存与年龄
    public int segmentAt(int age) { ... }
    public double speedAt(int age) { ... }
    public Vec3 directionAt(int age) { return cachedDirections[segmentAt(age)]; }
}
```

**契约**：

> 段方向 MUST 在构造期从种子解出并存为字段；
> 每 tick 路径 MUST NOT 读种子 accessor。

**验证方式**：比常量池扫描更好 —— `DanmakuLegMotion` 里**允许** `sin/cos`
（构造期求值一次），常量池扫描会误报。改为轨迹比对断言：

```
构造运动形态 → 跑 200 tick 记录轨迹
→ 改种子 accessor → 重建实体状态 → 再跑 200 tick
→ 断言两条轨迹逐位相同
```

这条断言直接抓到「偷偷每 tick 重抽」。

### 3. 段表用打包 int，种子 8 个，accessor 共 18 个

```
段参数打包：(时长 << 16) | (速率 & 0xFFFF)
  时长 0~65535 tick，速率 0~65535（定标：实际格/tick = 值 / 4096，与既有
  DanmakuWire.VELOCITY_SCALE 一致）

accessor 清单
──────────────────────────────────────────────────
  seeds[0..7]     int     ×8      8 个
  legs[0..7]      int     ×8      8 个（打包后的段参数）
  seedCount       byte    ×1
  legCount        byte    ×1
──────────────────────────────────────────────────
                                          18 个
```

**为什么在意 accessor 数量**：`SynchedEntityData` 用 `Object[]` + 装箱 `Integer`，
定义在 `AbstractDanmakuProjectile` 上 ⇒ 四种弹种都吃这份内存。
18 个 accessor × 2000 弹 × 约 24 B ≈ **860 KB**。可接受，但值得压。

**生成包不受影响**：未使用段运动的弹全是默认值，
`SynchedEntityData` 只下发非默认值 ⇒ **零线上成本**。

**16 + 2 的方案被否**（每段两个 short，1600 KB）—— 打包成一个 int 收益直接可见。

### 4. 种子必须折进运动指纹

`DanmakuMotionState.fingerprint` 覆盖 `params[0..52]`，作用是「两端手上的是不是
同一份运动输入」。种子直接影响运动，**必须被覆盖**：

```java
// AbstractDanmakuProjectile#motionParams()
params[DanmakuMotionState.P_RANDOM_COUNT] = this.randomSeedCount();
params[DanmakuMotionState.P_RANDOM_LEGS]  = this.legCount();
for (int i = 0; i < DanmakuRandomState.MAX_SEEDS; i++) {
    params[DanmakuMotionState.P_RANDOM_BASE + i] = this.randomSeed(i);
}
```

`PARAM_COUNT` 53 → 62。accessor 本身照旧（生成包带过去），
`motionParams()` 只负责把它们折进指纹。**两条路都覆盖。**

**保存兼容性已核**：`addAdditionalSaveData` 写的是逐个具名键
（`"FrameCx"`、`"SpV0"`…），不写 params 块，所以 `PARAM_COUNT` 变大不影响旧存档。

### 5. `SEGMENTED` 档的持久化判据：写速度

`needsVelocityPersistence` 对段运动返回 **true**。理由不是「速度不可推导」——
它是可推导的 —— 而是**顺序依赖**：

服务端从存档的 `pos` 继续，读侧重算速度必须**先于**位置积分发生。
现有代码已经因为 `DATA_HAS_FRAME` / `DATA_HAS_PROFILE` 的推断顺序踩过一次
（`AbstractDanmakuProjectile:1953` 的注释：「速度 MUST 最后读」）。

宁可多存 3 个 double。段参数与种子也一并入 NBT，
这样**档位不依赖任何一个键的推断结果**。

### 6. 重载可复算性

这是本变更最重要的性质，也是现有 spec 没有覆盖的半句。
`danmaku-pipeline-capacity` 只要求「纳入存档」，没有要求
「重载后与存档前逐位一致」。

```
离线前：age 47，段 1（悬停），方向 = dir(seed₀)
读档后：age 47（restoredAge + tickCount）
        段 1 = f(47)                    ← 纯函数
        方向 = f(seed₀, 段号)            ← 纯函数，种子从 NBT 恢复
        ⇒ 与从未卸载时逐位相同
```

**可离线断言**（任务的验收核心）：

```
构造弹 → 跑 200 tick 记录轨迹
→ 把状态搬到新实例（模拟读档：restoredAge = 200，tickCount = 0）
→ 再跑 200 tick
→ 断言两条轨迹逐位相同
```

### 7. 激光：两种形态并存（被遮挡 / 穿墙）

**本决策初版写的是「统一为穿墙，删掉裁剪路径」，依据是「用户已确认表现与逻辑统一」。
该确认已被后续要求取代**：被遮挡形态与穿墙形态**必须同时存在**，作为两种激光形态。

初版把「两端裁剪可能分歧」当成了「裁剪这个形态本身错了」。核实代码后，
事实是：

```
damageEntitiesInBeam()  只在 ServerLevel 跑          ⇒ 判伤是服务端权威，无分歧问题
getActualLength()       服务端判伤用，自己缓存       ⇒ 权威输入
getRenderLength()       客户端视觉用，自己缓存 + 自己的 clipLength(origin)
LaserDanmaku:172-177 的注释明确警告：两者 MUST NOT 合并，
                        「改成一个方法会同时把渲染状态喂进玩法判定」
```

所以**今天没有不一致** —— 各自裁剪、各自成立。被遮挡形态**不需要任何增量改动**，
它现在的实现就是正确的。真正存在的只是：客户端若未加载射线上的区块，
它裁出来的**视觉**会比服务端的判伤范围长 —— 这是**既有**状况，且只影响视觉。

**这恰恰是「值得有穿墙形态」的理由，而不是「该删掉被遮挡形态」的理由。**

```
                        被遮挡形态（默认）           穿墙形态（须显式声明）
判伤长度                getActualLength()           getMaxLength()
视觉长度                getRenderLength(pt)         getMaxLength()
clipLength / ClipContext 保留                        保留（被遮挡形态在用）
两套长度缓存            保留                        不需要
视觉包围盒              getMaxLength()（不变）      getMaxLength()（不变）
```

**不变量从来是逐形态的**：对**同一个形态**，视觉长度 MUST 等于判伤长度。
初版把它特化成「永远等于 `maxLength`」，那是在删形态，不是在修不变量。

**形态标志放在 `Projectile` 上，默认 `false`（被遮挡）** ——
这样在役符卡（喷泉激光 80 格，`BossCards:257`）行为**逐位不变**，
穿墙作为可选能力存在。全仓只有 3 个 `Projectile.laser(...)` 构造点，加位成本极低。

**形态位 MUST 进运动指纹**（`motionParams()` 的 `P_LASER_BASE` 块，`P_LASER_COUNT` 7 → 8）：
它**决定长度**，而校准通道只比位置 —— 不进指纹则两端形态不一致无法被检测。
这与 `danmaku-pipeline-capacity` 的「随机量 MUST 被运动指纹覆盖」同源。

**`GEOMETRY_KEYFRAME` 依然不需要，且理由更强了**：保留被遮挡形态**不引入任何新的同步** ——
两端各自射线检测，判伤由服务端权威。上面那条「视觉可能长于判伤范围」的既有偏差
不因保留形态而改变。

**渲染量不变**：`makeBoundingBox`（`LaserDanmaku:237`）与
`shouldRenderAtSqrDistance`（`:245`）用的都已经是 `getMaxLength()`，
且**两种形态都如此**。

**只有穿墙形态会扩大判伤查询盒**：其 AABB 从「裁剪后」变成 `maxLength`
（喷泉卡激光 80 格），`getEntitiesOfClass` 会扫更多 section。
被遮挡形态的查询盒**逐位不变**。实测只针对穿墙形态（任务 6.5）。

### 8. `TARGET` 段的保底推送：换向 tick 的一次快照

```java
// 服务端，段类型为 TARGET 且到达段起始年龄时
if (legMotion.requiresServerDecisionAt(age)) {
    DanmakuSyncServer.sendTurnSnapshot(trackingPlayers, bullet);
}
```

带宽实测（环卡，5 玩家）：

```
48 颗 / 240 tick 循环 / 每名玩家 → 每 5 tick 1 颗换向
每玩家 0.2 次/秒 × 5 玩家 = 1 次/秒
40 字节 × 1 = 40 B/s
```

**两条降级路径必须视觉一致**：

```
服务端在换向年龄：
  目标可解析 ──► 推快照（position + velocity），客户端从快照重锚
  目标不可解析 ──► 保持当前方向，客户端按自己的段表走完剩下的段
```

第二条是**常态**（玩家在悬停的 3 秒里退出是常事）。
两条路径在换向后的第一 tick 位置相同（快照给的就是那个位置）。

### 9. 档三不是兜底借口：三档优先级的设计意图

`TARGET` 段是本变更唯一用到档三（运行期权威下发）的地方。
把它单列成一节，是因为**档三最容易被误用** —— 它看起来是「最通用、最不容易出错」
的选项：不需要考虑重放、不需要考虑双端一致、快照一发完事。

实际恰好相反。档三的成本不在实现，在于**它放弃了可校验性**：

```
档一 / 档二                         档三
──────────                         ────
双端可逐位复算                      双端各持一份状态，客户端只能对齐
⇒ 分叉可被离线测试断言              ⇒ 分叉只在校准时被发现，且需累积到超容差
⇒ 读档后可断言「与从未卸载时相同」  ⇒ 读档后无法断言
⇒ 带宽 0                          ⇒ 每次生效一次包
```

所以 `danmaku-pipeline-capacity` 的三档优先级是**自上而下**的，
并在 spec 里列了四条**不得当作档三正当理由**的情形：

| 声称的理由 | 实际档位 |
|---|---|
| 「这个值是随机的」 | 档二 —— 抽种子 |
| 「要看另一个实体的位置」 | 若那个实体的运动可推导 ⇒ 档一或档二 |
| 「要看方块／世界状态」 | 若可在发射时采样 ⇒ 档二（激光长度就是这样改成标量的） |
| 「两端各自推导就行」 | **缺陷。** 不是档位 |

最后一条最重要。本仓库的历史两次撞上同一类错误：
`danmaku-timeline-sync` 的 T1 处理的是「速率失配让自检静默失效」，
本变更处理的是「双端各自抽签」。两者是**同一句话的两个实例** ——
把「客户端自己也能算」当成「不需要同步」，而那个「也能算」的算法依赖本端状态，
跨端必然分叉。而这类分叉**没有任何现有机制能发现**（校准只比位置）。

**什么才真正属于档三**：值的确定需要「发射之后才发生的事实」。
现有的四个候选场景（外部作用改变弹道、服务端对玩家行为的反应式决策、
有界但非一次性的随机、移除击���免疫后的弹道跟随）在当前代码里**都是空的** ——
这正是机制保留、当前不建的理由。机制本身不否决，
设计草案保留在 `archive/2026-10-01-danmaku-event-sync-deferred/`。

## Risks / Trade-offs

- **`sin/cos` 在 `DanmakuLegMotion` 里出现。** 这是**记录在案的例外**，
  与 `Rotation`（Rodrigues）同级。必须：
  - 在类注释里写明「构造期求值一次，每 tick 路径不读」的纪律；
  - 由轨迹比对断言锁住（决策 2）；
  - **MUST NOT** 把现有那条常量池扫描纪律扩展到本类 ——
    扩展会立刻改变段运动的轨迹，而没人会想到原因是这个。
- **段数定长 8 的浪费。** 简单运动（直线、现有 profile）用不到段表，
  但 accessor 定义在基类上，所有弹种都占那 18 个槽。约 860 KB @ 2000 弹。
- **读侧顺序依赖。** 决策 5 已用「写速度」绕开，但段参数与种子的读取顺序
  仍需在实现时明确注释。
- **激光判伤查询盒变大。** 需实测（任务 5.4）。
- **`TARGET` 段在重载后必然有一段无主时间。** 从存档点���换向年龄之间，
  客户端按自己的段表走（`SEED` 段）或等快照（`TARGET` 段）。
  这是可接受的：它与「客户端在收到任何状态前按旧状态表现」是同一条纪律。

## Migration Plan

1. **前置**：`fix-ring-card-geometry` 完成（环闭合后才能分离几何误差与换向误差）。
2. 加 `DanmakuRandomState`（种子容器，无世界依赖）与纯函数测试。
3. 加 `DanmakuLegMotion`（段表 + 三种段类型 + 构造期方向解出）。
4. 接入点：`AbstractDanmakuProjectile` 的 18 个 accessor + `motionParams()` 折指纹。
5. `DanmakuTrackKinds` 加 `SEGMENTED` 档 + 持久化判据 + 真值表测试。
6. 存档：种子与段表入 NBT（读侧顺序明确注释）。
7. 轨迹比对断言（决策 2、6 的验收核心）。
8. 激光穿墙：删裁剪路径，判伤改 `maxLength`。
9. `TARGET` 段：换向 tick 的一次快照 + 两条降级路径。
10. 诊断接入 `/gs_boss danmaku`。
11. 跑通全部既有 danmaku 测试（不得减少）。
12. 实机回归。

## Open Questions

- 段数上限**定为 8**（用户已确认）。理由：8 已覆盖「5 段 + 余量」，
  而多留的段是**永久**的内存开销（`SyncedEntityData` 的 accessor 定义在基类上，
  四种弹种都吃）。将来若需要更长编排，改动是机械的（一个常量 + 一个 accessor 分组 +
  `PARAM_COUNT`），而现在多留的槽位是每天都要付的。
- 批级随机（同组种子 + 实例索引）留到 `danmaku-track-scope`。若其带宽门槛迟迟不测，
  这部分能力会一直缺位 —— 但那是 `danmaku-track-scope` 自己的门槛问题，
  不是本变更能解的。
- 目标引用在重载后的重解析**不在本变更范围内**。它是灵符与爆散两处的共性问题，
  应在 `danmaku-talisman-target` 建立的 `danmaku-target-state` 能力下统一处理。
