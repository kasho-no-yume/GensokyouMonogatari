# 弹幕同步架构现状与未解问题 —— 专家会诊材料

> **文档性质**：技术现状说明 + 证据汇总 + 待诊问题。不含结论。
> **日期**：2026-09-29
> **代码基线**：`4ede3e9 实现灵浴` + 本次会话的未提交改动
> **平台**：Minecraft 1.21.1 / NeoForge 21.1.248

---

## 0. 一页速览

```
弹幕系统目标      零持续带宽 + 双端各自演算 + 位置纠偏包只作安全网
核心前提          弹位 = f(年龄)，且双端年龄相等
年龄怎么来的      原版 Entity.tickCount —— 它既不入存档、也不同步给客户端  ← 已修
纠偏判据          固定 1.0 格² 撞上稳态误差 δ·v，按速度分裂成两种失败   ← 已改为速度相对
残余问题          读档还原的弹，客户端年龄与服务端差 4~8 tick，双向振荡   ← 未解
```

**为什么值得会诊**：残余问题不在「阈值」也不在「年龄同步」这两层，而在**客户端实体的 tick 计数与权威端的相位关系**。我们用源码核对 + 实机测量把范围收窄到了这一步，但推演不下去了。

---

## 1. 目的：这个系统要什么

弹幕（弹幕 / 弹幕游戏）要同时满足三件事，而这三件事在网络层面互相拉扯：

| 目标 | 含义 | 代价 |
|---|---|---|
| **零持续带宽** | 弹在飞的过程中，服务端不再下发任何数据 | 客户端必须能自己算出弹在哪 |
| **零误差轨迹** | 客户端画的位置与服务端判定的位置一致 | 客户端与服务端必须跑**同一个**纯函数 |
| **高密度** | 数百颗弹同屏 | 每颗都要有可演算的完整参数 |

传统做法是**服务端权威位置 + 客户端插值**：位置每 tick 下发，客户端在两次位置之间插值。带宽 = O(弹数 × 20 tick/s)。400 颗弹约 400×20 = 8000 包/s，这在原版协议上不可接受。

**本项目的选择**：把「弹位」从「累积量」改写为「时间的纯函数」。

```
服务端下发一次：   出生位置 / 方向 / 全部运动参数 / 全部显隐参数
此后每 tick：      服务端自己算一遍；客户端自己算一遍；两��应当相等
位置包：            仍然存在（玩家移动会自然触发），但降级为「纠偏」而非「驱动」
```

这要求参数**一次下发、终生够用**。因此参数不能引用任何实体、不能依赖运行时状态。

---

## 2. 为什么这么设计（关键决策与其理由）

### 2.1 弹幕不继承 `ThrowableProjectile`

`AbstractDanmakuProjectile extends Projectile`（`entity/AbstractDanmakuProjectile.java:84`）。

原版 `ThrowableProjectile` 每 tick 施加 0.99 的空气阻力。弹幕必须匀速，所以绕开它、自己接管 `tick()`。

### 2.2 位置由程序推进，不走原版 `move()`

`AbstractDanmakuProjectile.java:464`：

```java
this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
```

原版 `move()` 会做实体推挤、台阶处理、碰撞箱收缩。对「按脚本编排的弹幕」这些只会造成位置漂移，而漂移会污染纯函数的前提。

命中判定在位移前用自写的 `DanmakuHitScan.sweep` 完成（`:454`），保留「扫掠—命中—销毁」三段语义。

### 2.3 全部参数走 `SynchedEntityData`，一个不落

基类上约 40 个同步字段。设计判据（记在 `danmaku-pipeline-capacity` spec）：

```
可由「年龄」与其它同步量纯函数推导  → 允许普通字段（双端结果天然一致）
否则                                → MUST 走 SynchedEntityData 并纳入存档
```

**注意这条判据里「年龄」这个词是本次会话加进去的**——原先写的是「tick 次数」，而那个前提不成立（见 §5.1）。

### 2.4 超越函数纪律

`DanmakuSpeedProfile` 与 `FormationFrame.scaleAt` 只用四则运算、`frac`、取绝对值，**不用 `sin/cos`**。理由是双端各自求值，超越函数不保证跨平台逐位一致。

**例外**：`Rotation.about` 内部的 Rodrigues 旋转用了 `sin/cos`——它已在曲射路径上被双端使用，编队帧复用它不引入新的风险类别，只是让编队路径**也**接受了这条纪律的放松。

### 2.5 超越函数纪律的推论：位置必须可闭式求值

编队弹的位置不是一个累积量，而是每 tick **重置**为解析值：

```java
// AbstractDanmakuProjectile.java:435-447
int tick = this.age();
double advance = hasSpeedProfile() ? travelAt(tick) : unscale(FrameAdv) * tick;
Vec3 next = framePositionThisTick().add(axis().scale(advance));
velocity = next.subtract(this.position());
setDeltaMovement(velocity);
// ...末尾 setPos(pos + velocity) ⇒ 位置逐位落在 next 上
```

**误差不累积**，因为每 tick 都从解析值重来。

### 2.6 编队参数烘进每枚弹，不由实体承载

`FormationFrame`（`danmaku/motion/FormationFrame.java`）是**一组标量**，发射时烘进每枚弹的 `SynchedEntityData`。一轨一份编队帧，48 颗弹各带一份 16 个 int，**每 tick 每弹的编队相关网络负载为零**。

理由：承载编队参数的实体需要生命周期、需要失效处理、需要「弹引用了已失效的编队」这个状态。本设计以「不存在引用」来规避它。

---

## 3. 当前的弹幕同步架构

### 3.1 层次

```
Behaviour（行为：运动 / 分裂 / 显隐 / 编队声明）    ← 声明层，轨级
    │
    ├── Shape + Geometry（形状：玫瑰线 / 晶格 / 环 / 笼 …）  ← 几何层，只产出一组 Shot
    │       产出：List<Shot(origin, direction, planeAxis, params)>
    │
    └── Projectile（弹种：球 / 刀 / 符 / 激光）    ← 实体层
            │
            └── AbstractDanmakuProjectile
                    ├── SphereDanmaku
                    ├── KnifeDanmaku
                    ├── TalismanDanmaku      （额外：追踪目标）
                    └── LaserDanmaku         （额外：延迟线 + 射线判伤）
```

**Shape（15 种，只决定「在哪、朝哪、多快」，不决定「怎么动」）**：
`AIMED_SINGLE` `FAN` `RING_FACING` `RING_HORIZONTAL` `AXIAL_STAR` `CAGE` `SHELL` `FALL_FROM_ABOVE` `RING` `ROSETTE` `AROUND_TARGET` `LATTICE` `CONE_RANDOM` `RADIAL_BURST` `SCATTER_STATIC` `GAP_FAN`

**Behaviour.Motion（6 种，决定运动）**：
`NONE` `GROUND_HUG` `CURVE` `HOVER` `MINE` `SPEED_PROFILE`

**三个轴正交**：`Motion`（怎么动） × `Split`（怎么分） × `Visibility`（怎么显隐），外加轨级的 `Formation`（编队帧声明）。设计上刻意不允许「某几何自带某行为」——那会破坏正交性。

### 3.2 两条弹道，同步方式相同但**对年龄的敏感度天差地别**

这是本文最重要的一张图。

```
┌─ 累加式（直线弹、速率曲线弹） ────────────────────────────────┐
│                                                               │
│   pos ← pos + v                                               │
│   v = 速率曲线(age) 的方向分量                                  │
│                                                               │
│   弹位与【年龄】无关：只取决于「从哪出发 + 走了多少步」          │
│   ⇒ 即使双端年龄差 100 tick，只要步长一致，位置就重合           │
│   ⇒ 这就是 /danmaku wall 重载多少次都不抖的原因（实测）        │
│                                                               │
└───────────────────────────────────────────────────────────────┘

┌─ 解析式（编队帧弹） ──────────────────────────────────────────┐
│                                                               │
│   p(t) = center(t) + S(t)·Rodrigues(axis, R(t), p₀ − c₀) + d·s(t) │
│                                                               │
│   弹位是【年龄】的强函数：旋转 / 缩放 / 推进三项全按 t 解析求值  │
│   ⇒ 弹位误差 ≈ |v| × Δage                                     │
│   ⇒ 误差天然正比于速度                                         │
│   ⇒ 只有这一类弹会因年龄错位而抖                               │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

`/danmaku flower`（玫瑰线花形）同时用到三者：`withSpin(normal, 2.0°/tick)` + `withBreathing(±0.4 / 60 tick)` + 沿法线推进 0.18 格/tick，基半径 3.0。

### 3.3 每 tick 一次的解析求值：自转 + 呼吸 + 推进

```
自转    r × ω          4.8 格 × 2°/tick(0.0349 rad) = 0.168 格/tick
呼吸    r × |dS/dt|    3.0 × (0.4 × 4/60)             = 0.080 格/tick
推进    0.18 格/tick
─────────────────────────────────────────────────────────────
合计    ≈ 0.18 ~ 0.43 格/tick（随 r 与呼吸相位变化）
```

**注意这个量级**：0.4 格/tick × 4 tick 的管线延迟 = 1.6 格误差，**超过旧的 1.0 格² 阈值**。这是 §5.2 的全部由来。

### 3.4 位置包的发送节奏

`ModEntityTypes`（`registry/ModEntityTypes.java`）逐类型配置：

| 实体 | `updateInterval` | `clientTrackingRange` | 含义 |
|---|---|---|---|
| `danmaku` | **10** | 4 | 旧的基础弹种 |
| `sphere_danmaku` | **2** | 8 | 球弹（主力） |
| `knife_danmaku` | **2** | 8 | 飞刀 |
| `talisman_danmaku` | **2** | 8 | 灵符 |
| `laser_danmaku` | **1** | 10 | 激光 |
| `orbit_yin_yang_orb` | 10 | 8 | 阴阳玉（武藏技能） |
| `zaohua_flight_item` | 2 | 8 | 造化飞行原料 |

`updateInterval` 由 `ChunkMap` 传给 `ServerEntity`，决定位置包频率（`ChunkMap.java:1115-1119`）。

**⇒ 球弹的稳态偏移 δ ≈ updateInterval(2) + 管线延迟(2) = 4 tick。** 这与 §6.1 实测的 `fresh-ahead p50=2` 对得上（见 §6.3 关于符号的说明）。

### 3.5 纠偏：`lerpTo` 的位置纠偏

`AbstractDanmakuProjectile.java:865-901`。原版 `Entity.lerpTo` 是无条件 `setPos`；我们改成：

```java
Vec3 error = new Vec3(x, y, z).subtract(this.position());   // 权威位置 − 本地模拟位置
if (!DanmakuCorrection.accepts(error.lengthSqr(), speed, lagWindow, floor)) {
    this.setPos(x, y, z);   // 硬纠正
}
```

判据原本是固定 `1.0 格²`，本次会话改为**速度相对**（`danmaku/motion/DanmakuCorrection.java`）：

```
容差 = max(v × danmakuMaxLagTicks, danmakuCorrectionFloor)
默认 danmakuMaxLagTicks = 4（200ms），floor = 0.25 格
```

### 3.6 命中判定：服务端权威，与位置同步无关

`DanmakuHitScan.sweep`（`danmaku/DanmakuHitScan.java:65`）用**带类型过滤**的实体查询重载（`getEntitiesOfClass(LivingEntity.class, …)`），因此全部弹幕（同一 `EntityType`）被整表跳过，复杂度与弹幕数无关。

判定只在服务端生效；客户端虽然跑同一段代码，但 `onHitEntity` / `onHitBlock` 在 `!(level instanceof ServerLevel)` 时直接返回。

**⇒ 客户端的位置算错不会造成伤害错误，只会造成表现错误。** 这决定了本次问题的严重性定级为「表现缺陷」而非「玩法缺陷」。

### 3.7 诊断：`/gs_boss danmaku`

`danmaku/DanmakuBudget.java`。维度：

| 指标 | 含义 | 本次会话新增 |
|---|---|---|
| `emitted / entityHits / blockHits / damageSum / live / cap` | 数量 | 既有 |
| `tickTime` | 弹幕 tick 累计耗时 | 既有 |
| `hardCorrect[v>=…]` | 硬纠正次数，按弹速分档 | 既有 |
| `lag(n=…, median, p95)` | 滞后分布（**只计非负**） | 既有 |
| `age[来源-落后/超前:…]` | 年龄偏移，按来源 × **符号** | **本次新增** |
| `ageValue[…]` | 客户端年龄的绝对量级 | **本次新增** |

---

## 4. 一个必须先讲的原版事实

以下三条从 NeoForge 21.1.248 反编译源码逐行核实，是全部后续推理的地基：

```java
// Entity.java:195
public int tickCount;

// Entity.java（全文仅 3 处 tickCount 引用，全是水晶音效）
// addAdditionalSaveData / readAdditionalSaveData 均不含 tickCount
// 整个 MC 只有 AreaEffectCloud 自己存了 "Age"

// Entity.java:3562  recreateFromPacket
public void recreateFromPacket(ClientboundAddEntityPacket packet) {
    this.syncPacketPositionCodec(d0, d1, d2);
    this.moveTo(d0, d1, d2);
    this.setXRot(packet.getXRot());
    this.setYRot(packet.getYRot());
    this.setId(i);
    this.setUUID(packet.getUUID());
}                       // ← 不设 tickCount，也不用 packet.getData()

// ServerLevel.java:772  与  ClientLevel.java:298
public void tickNonPassenger(Entity p_entity) {
    p_entity.setOldPosAndRot();
    p_entity.tickCount++;        // ← 自增在 Level，不在 Entity.tick()
    ... p_entity.tick(); ...
}
```

**三个推论：**

1. `tickCount` **不入存档** ⇒ 读档后实体年龄归零，但位置被 NBT 还原到「年龄 T」的坐标
2. `tickCount` **不随生成包下发** ⇒ 客户端实体永远从 0 起步
3. 自增在 `Level` 且在 `entity.tick()` **之前** ⇒ 代码体内 `tickCount` 已是本 tick 编号

**第 3 条的连带后果**：`AbstractDanmakuProjectile.java:769-779` 原本的注释（称「`super.tick()` 还没把 `tickCount` 加一」，故需 `+1`）是错的，编队帧的 tick 编号整体超前 1 tick。本次已修正。

**另有一条**：`ClientboundAddEntityPacket` 的 `data` int **不是空槽**。`Projectile.getAddEntityPacket` 用它下发 owner 实体 id，客户端用 `getEntity(packet.getData())` 反查。它已被 `Projectile` 占用。

---

## 5. 遇到的问题

### 5.1 问题一：双端年龄不相等（已修）

**现象**：存档退出再进入，原本在飞的弹开始抽搐、不断被拽。

**根因**：客户端实体每「丢掉又重新拿到」就自 0 重数，服务端那份一直在 tick。触发路径有五条，**不止读档**：

```
① 世界读档
② 玩家走出 clientTrackingRange 再走回来     ← 多人正常游玩中每分每秒发生
③ 客户端卸载区块而服务端仍加载
④ 末影箱 / 跨维度 / 传送 / 重生
⑤ 弹所在区块卸载后重新加载（BOSS 战跨区块边界）
```

服务端在这五条路径下的状态**完全一致**：那份实体一直未被销毁、一直在 tick。**故服务端根本区分不出「读档」与「后撤回来」**——这条事实直接否掉了「重新获取时销毁该弹」这个廉价修法（它会让玩家每次后撤就永久失去一片弹幕）。

**修法**（`danmaku-age-continuity`，已归档 `openspec/changes/archive/2026-09-29-danmaku-age-continuity/`）：

```
服务端  age() = restoredAge + tickCount     restoredAge ← 存档「保存那一刻的真实年龄」
客户端  age() = peerAge     + tickCount     peerAge     ← 每次配对单发的包
```

- 纯函数规则抽到 `danmaku/motion/DanmakuAge.java`（可离线测试）
- 配对包 `network/DanmakuAgePayload.java`（`playToClient(entityId, age)`，约 5 字节，持续带宽为零）
- 由 `PlayerEvent.StartTracking` 单发，紧跟生成包之后，客户端首个 tick 即就位
- 全部 10 处 `tickCount` 改读 `age()`

**验证结果**：`unseeded-*` 恒为 0（配对包每次都送达），`ageValue[1-10:305 11-100:1403 101-500:638]`（客户端确实知道自己 11~500 tick 老）。

**⇒ 问题一已修。但它不解释用户观察到的抖动。**

### 5.2 问题二：纠偏阈值与速度无关（已改为速度相对）

**现象**：外圈角速度大的弹**基本一定会抖**；内圈基本不抖，但花瓣弧线**出现锯齿**。

**根因**：稳态误差 = `δ × v`，而阈值是固定的 `1.0 格²`。存在一条 `v = 1/δ` 的分界线：

```
v < 1/δ  →  误差恒低于阈值  →  永不纠正  →  永久滞后 δ 个 tick  →  读作「弧线锯齿」
v > 1/δ  →  每个位置包都超阈值  →  每 updateInterval 个 tick 硬拽一次  →  读作「抖动」
```

**这不是「抗抖动」，是「按速度分裂成滞后与抖动两种失败」。** 慢弹的 1 格误差比快弹的 1 格误差严重得多，固定阈值却一视同仁。

**修法**（`danmaku/motion/DanmakuCorrection.java`）：容差改为 `max(v × 4, 0.25 格)`。配置项 `danmakuMaxLagTicks`（0~40，默认 4，设 0 = 退回旧行为）、`danmakuCorrectionFloor`（0~2，默认 0.25）。

**预期效果**：`δ ≈ 4` 的主体被接受，抖动消失；`δ > 4` 的部分**继续被拒**。

### 5.3 问题三：诊断工具本身有缺陷（已修，但误导了归因过程）

这条值得单列，因为它**污染了 §6 之前的所有读数**。

`recordLag` / `recordAgeOffset` 最初都有 `lagTicks < 0` 就丢弃的守卫。后果：

```
实测样本 10677 个  →  读数只计入 91 个  →  丢弃的 10586 个（99%）全是「客户端超前」

「客户端超前」意味着自变量对不上 —— 那是失步，不是延迟，比滞后更糟。
它被守卫整个藏起来了，于是每一份「lag=1、很健康」的读数
实际只描述了不到 1% 的样本。
```

**这直接造成了一个自相矛盾、无法解释的读数**：`hardCorrect=3415` 而 `age[rebuilt]=61`。

现在按符号分桶（6 段）：

```
age[fresh-behind fresh-ahead rebuilt-behind rebuilt-ahead unseeded-behind unseeded-ahead]
```

### 5.4 问题四：读档还原的弹，客户端年龄仍差 4~8 tick（**未解**）

这是本次要会诊的主体。现象与数据见 §6。

---

## 6. 未解问题的证据

### 6.1 最新实测（修复问题一、二、三之后）

```
[GS-DANMAKU] emitted=1 live=61 cap=500
hardCorrect[v>=0.00:0 v>=0.15:840 v>=0.30:7 v>=0.50:0 v>=1.00:0]     → 847 次
lag(n=699 median=4.0 p95=8.0)
age[fresh-behind:n=0
     fresh-ahead :n=976,min=0,p50=2,p95=2
     rebuilt-behind:n=699,min=0,p50=4,p95=8
     rebuilt-ahead :n=671,min=2,p50=8,p95=8
     unseeded-behind:n=0 unseeded-ahead:n=0]
ageValue[0:0 1-10:305 11-100:1403 101-500:638 500+:0]
```

**交叉校验**：`976 + 699 + 671 = 2346 = 305 + 1403 + 638` ⇒ 诊断内部自洽（问题三已解决）。

### 6.2 符号约定

`lagTicks = (权威位置 − 本地模拟位置) · v̂ / |v|`

```
正  ⇒  权威位置在本端【前方】  ⇒  本端画得比它【老】⇒ 客户端【落后】
负  ⇒  权威位置在本端【后方】  ⇒  本端画得比它【新】⇒ 客户端【超前】
```

「超前」= 客户端按的时间轴比服务端靠前 ⇒ **要么年龄基准大，要么本地 tick 跑得快。**

### 6.3 关键读数：fresh 那一列是健康基线

```
fresh-behind:n=0                  ← 一次都没有
fresh-ahead :n=976, p50=2, p95=2 ← 全部，且只有 2 tick，零方差
```

**⇒ 新发射的弹：客户端稳定超前 2 tick，无任何离散。** 2 tick ≈ `updateInterval(2)`。这是「客户端预测 + 服务端权威」架构的固有代价，**不是缺陷**。有了这一列作基线，其余列才可解读。

### 6.4 异常只在 rebuilt 那一列

| 分组 | n | 偏移 | A 的容差（4v） | 结果 |
|---|---|---|---|---|
| `fresh-ahead` | 976 | 2 tick ⇒ `2v` | `4v` | 接受，0 硬纠正 ✓ |
| `rebuilt-behind` | 699 | 4~8 tick ⇒ `4v`~`8v` | `4v` | 边界，部分接受 |
| `rebuilt-ahead` | **671** | **8 tick ⇒ `8v`** | `4v` | **拒绝 ⇒ 抖动** |

**⇒ 847 次硬纠正几乎全部落在那 671 个「超前 8 tick」的样本上。**

**⇒ 候选 A 按设计正确地拒绝了它**——8 tick 的分歧不能用滞后解释，那是真失步。这正是 spec 里写的「真失步 MUST 以硬纠正形式暴露，不允许被平滑吸收」。

### 6.5 分布是双峰的，且符号会翻转

`rebuilt` 同时出现 `-4~8` 与 `+8` 两簇。**一个恒定偏移不可能产生符号翻转。**

**我们的怀疑（未证实）**：测量本身被污染。`lagTicks` 里的 `v` 是弹每 tick 自算的 `next - position`（`AbstractDanmakuProjectile.java:445`）。而硬纠正会把 `position` 直接设成服务端坐标，于是**下一个 tick 的 `v` 变成从「被拽回的点」指向「解析点」的巨大向量**，再喂进下一次投影。误差方向因此可能来回翻。

**⚠️ 若这条成立，则这 671 的幅度不可信，但「它们超阈值了」是真的**（847 次硬纠正是独立计数）。

### 6.6 排除项（已用实验或源码验证，不是推测）

| 假设 | 排除依据 |
|---|---|
| 配对包没送达 | `unseeded-*` 恒为 0 |
| 客户端年龄基准错 | `ageValue[11-100:1403 101-500:638]`，客户端知道自身年龄 |
| 客户端不 tick 远处实体导致计数冻结 | 查 `ClientLevel.tickEntities()`：**没有** simulationDistance 过滤，`tickingEntities` 里每个实体都 tick |
| 速度骑在 1.0 格² 阈值上（对 fresh 弹） | fresh 误差 `2v` < 容差 `4v`；且 `/danmaku flower` 单独放不抖 |
| 阴阳玉 / 造化原料在抖 | 同为该类实体；花弹不抖 |
| 帧 tick 编号 off-by-one | 两端同式，不产生失步 |
| 旧存档缺键（缺省退化） | 新版本重写存档后现象仍在 |
| 事件订阅没落到 game bus | `DanmakuBudget` 用同样的类级注解处理 `EntityJoinLevelEvent`，`live=61` 非零证明有效 |

### 6.7 仍然可疑、但未排除的机制

**A. `StartTracking` 重复触发导致 `peerAge` 被刷新，而客户端实体没有重建。**
若 `addPairing` 在客户端实体仍存在时再次触发，`seedPeerAge` 会把 `peerAge` 改写为服务端**当前**年龄，而客户端 `tickCount` 继续累加 ⇒ 客户端年龄**凭空前跳**。这与「超前 8 tick」方向一致。
**未验证。** 需要在 `onStartTracking` 里打出 `entityId / serverAge / 已配对次数`。

**B. 客户端与权威端的 tick 推进速率不一致。**
`ServerLevel` 与 `ClientLevel` 都以 20 Hz 自增（§4），但**内嵌服务端与客户端是不同线程**，理论上一侧可能落后。`ClientLevel.tickEntities` 里还有 `tickRateManager.isEntityFrozen` 与 `EntityTickEvent.Pre` 取消两条路径会跳过 `tickNonPassenger`。
**未验证。** 需要比对两端某枚弹的 `age()` 时间序列。

**C. `ageValue` 分布下界异常。**
客户端年龄落在 `1-10:305` 这一档。若配对包正确，重载时的弹不该有一批年龄只有个位数。可能是刚被重新追踪的新实体（旧弹在视野外，玩家靠近才被追踪 ⇒ **老弹被新追踪**，`ageValue` 记录的是它此刻的年龄，那 305 个低龄样本恰恰是「刚被追踪的旧弹」）。**这条其实是自洽的，但需要确认。**

---

## 7. 尝试过的解法与结果

| # | 尝试 | 结果 | 证据 |
|---|---|---|---|
| 1 | 读源码核实 `tickCount` 的持久化与同步行为 | ✅ 确认是根因 | §4 |
| 2 | 定位触发条件：发现不止读档 | ✅ 否掉「读档即销毁」修法 | ②③ 两条路径与 ① 同构 |
| 3 | 年龄同步量（服务端存档 + 客户端配对包） | ✅ **问题一已修** | `unseeded=0` |
| 4 | 编队帧 tick 编号 off-by-one | ✅ 修正（观感变化待验） | §4 推论 3 |
| 5 | 编队弹方向轴存档缺失 | ✅ 修正 | 写盘条件「挂帧 OR 挂曲线」 |
| 6 | 修诊断：负投影不再丢弃 | ✅ **问题三已修** | `976+699+671 = 2346` 自洽 |
| 7 | 修诊断：直方图数组全为 null 导致 NPE | ✅ 修正 | 曾致客户端 FATAL 断线 |
| 8 | 纠偏阈值改速度相对（候选 A） | ⚠️ **部分**：`δ≈4` 主体被接受，`δ=8` 仍被拒 | §6.4 |
| 9 | 按「外圈抖内圈不抖」反推速度分界线 | ✅ 确认机制，但**不是全部** | §5.2 |
| 10 | 查「客户端不 tick 远处实体」 | ❌ 排除 | §6.6 |
| 11 | 查「速度骑阈值」（对 fresh 弹） | ❌ 排除 | §6.6 |

### 走过的弯路（供会诊参考，避免重复）

- **归因偏误**：连续三轮追「重载失步」，而数据两次明说「速度」——外圈抖内圈不抖就是速度梯度的签名。
- **诊断误导**：问题三的守卫让 99% 的样本消失，导致「硬纠正 3415 次 vs rebuilt 样本 61 个」这种自相矛盾的读数无法解释，浪费了数轮。
- **档位取错**：`DanmakuBudget` 原注释写着「1~4 tick 仍是亚像素级，≥8 tick 才肉眼可见」——**把「1 tick」当成了「1 px」**。实测换算（1080p / FOV 70 / 5 格距离，1 格 ≈ 154 px）：

  ```
  滞后 1 tick → 0.15~0.5 格 →  23 ~  77 px   肉眼明显
  滞后 4 tick → 0.6 ~2.0 格 →  92 ~ 308 px   明显偏移
  滞后 8 tick → 1.2 ~4.0 格 → 185 ~ 616 px   大幅错位
  ```

  **任何以「亚像素级」为由判定「现存方案足够」的结论都建立在这个错算上。** 已修正。

---

## 8. 请专家判断的问题

### Q1（最要紧）为什么重载还原的弹，客户端年龄与服务端差 4~8 tick 且符号会翻转？

已知：配对包每次送达（`unseeded=0`）；客户端年龄量级正确（11~500）；fresh 弹零离散。

请重点看：
- §6.7 机制 A（`StartTracking` 重复触发）与 B（两端 tick 速率）——哪个更可能，或者都不是？
- 是否有我们没想到的第三条路径？

### Q2 `age[]` 的测量方法本身可信吗？

`lagTicks = error·v̂/|v|` 里的 `v` 来自「解析终点 − 当前坐标」，而当前坐标会被硬纠偏改写。**这构成一个反馈回路**（§6.5）。

- 能否用不依赖 `v` 的方式测年龄偏移？
- 或者：硬纠正发生的那一 tick 及随后一 tick是否应直接跳过采样？

### Q3 架构层面：这两条弹道应该统一吗？

现在「累加式」对年龄不敏感、「解析式」对年龄强敏感，**同一个缺陷只在一半弹种上显现**。代价是：
- 只有解析式需要年龄同步机制
- 直弹的「相位无关」是个偶然性质（它来自 `pos += v` 的实现细节，不是设计出来的），任何把它改成「按时间步进」的重构都会让它变敏感

**是否应该让直弹也走 `pos = f(age)`**（牺牲带宽换一致性）？还是维持现状并把「两种弹道」写进 spec 作为显式约束？

### Q4 位置包的节奏是否有更好的选择？

现在球弹 `updateInterval=2`，`δ ≈ 4`。可选方向：
- 降到 1（δ≈2，误差减半，带宽翻倍）
- 改成**按速度自适应**（快弹高频、慢弹低频）
- 保持 2，靠判据吸收（现状）

### Q5 「客户端预测 + 服务端权威」是否是这个游戏形态的正确选择？

替代方案是**服务端权威位置 + 客户端只做渲染插值**（传统做法）。代价是带宽 O(弹数 × 20)。400 颗弹约 8000 包/s。

弹幕游戏的核心体验是「看清弹幕的轨迹并做出反应」，位置精度直接决定可玩性。**但我们目前的架构对「客户端与服务端不共机」这件事没有任何防护**——所有正确性都建立在「纯函数 + 年龄相等」这两个前提上，任何一个被破坏就退化成可见抖动，而没有降级路径。

**是否存在一个「带宽 O(弹数) 但不依赖双端一致」的中间方案？** 例如：每 tick 只下发位置（量化到 1/4096 格，约 3~6 字节/弹/次），客户端只做插值不做预测。

### Q6 相位推前（候选 C）值不值得做？

提案里的候选 C：`L̂ = EMA((权威位置 − 本地位置)·v̂/|v|²)`，渲染位置取 `f(age + L̂)`。

**已知风险**（提案 `:115` 自己写的）：「`L̂` 吸收真 bug——把同步 bug 从硬失败静默转成错渲」。

现在 §6.4 的数据说明：如果实施 C，`L̂` 会估出 8，弹被**平滑地画在错误的位置**。**这比抖动更难查。**

- 在「Q1 的根因未找到」这个前提下，C 是否应该被冻结？
- 是否有办法给 `L̂` 加一个可信度判据（例如：年龄偏移在两个连续位置包之间是否稳定），使「真失步」不会被吸收？

---

## 附录 A：最小复现步骤

```
1. 打开一个已有弹幕的存档
2. 靠近 BOSS，让编队花 / 玫瑰线弹幕在飞
3. ★ 弹幕在飞时存档并退出
4. 重新进入，观察到抖动的那批弹
5. /gs_boss danmaku
```

对照实验（用于分离变量）：

| 编号 | 实验 | 期望（若机制 A 成立） |
|---|---|---|
| A1 | 新世界，不存档，直接放 `/danmaku flower` | **不抖**（已验证） |
| A2 | 读档后放 `/danmaku wall`（累加式） | **不抖**（已验证） |
| A3 | 读档后等抖动那批弹自己飞完，再放新花 | 旧的抖，新的不抖 |
| A4 | 读档后**不靠近** BOSS（弹在追踪范围外），等它们飞完 | 无抖动可见（未被追踪） |

## 附录 B：关键源码位置

| 主题 | 位置 |
|---|---|
| 弹幕基类 / tick / 存档 | `src/main/java/com/bitsson/gensokyou/entity/AbstractDanmakuProjectile.java` |
| 纠偏 `lerpTo` | 同上 `:865-901` |
| 解析求值（编队接管） | 同上 `:435-447`、`:777-779` |
| 年龄规则 | `src/main/java/com/bitsson/gensokyou/danmaku/motion/DanmakuAge.java` |
| 纠偏判据 | `src/main/java/com/bitsson/gensokyou/danmaku/motion/DanmakuCorrection.java` |
| 编队帧 | `src/main/java/com/bitsson/gensokyou/danmaku/motion/FormationFrame.java` |
| 速率曲线 | `src/main/java/com/bitsson/gensokyou/danmaku/motion/DanmakuSpeedProfile.java` |
| Rodrigues 旋转 | `src/main/java/com/bitsson/gensokyou/danmaku/motion/Rotation.java` |
| 行为正交分解 | `src/main/java/com/bitsson/gensokyou/danmaku/track/Behaviour.java` |
| 形状 → Shot | `src/main/java/com/bitsson/gensokyou/danmaku/track/Geometry.java`、`Shape.java` |
| 发射翻译层 | `src/main/java/com/bitsson/gensokyou/danmaku/DanmakuEmitter.java` |
| 命中判定 | `src/main/java/com/bitsson/gensokyou/danmaku/DanmakuHitScan.java` |
| 诊断 | `src/main/java/com/bitsson/gensokyou/danmaku/DanmakuBudget.java` |
| 配对包 + 订阅 | `src/main/java/com/bitsson/gensokyou/network/DanmakuAgePayload.java`、`ModNetworking.java` |
| 实体注册（追踪/更新率） | `src/main/java/com/bitsson/gensokyou/registry/ModEntityTypes.java` |

## 附录 C：相关 OpenSpec 变更

| 变更 | 状态 | 位置 |
|---|---|---|
| `danmaku-age-continuity` | 已归档 | `openspec/changes/archive/2026-09-29-danmaku-age-continuity/` |
| `danmaku-lag-smoothing` | 活跃（提案，无 tasks） | `openspec/changes/danmaku-lag-smoothing/` |
| `danmaku-behaviour-decoupling` | 已归档 | `openspec/changes/archive/2026-09-29-danmaku-behaviour-decoupling/` |
| `danmaku-motion-and-rig` | 已归档 | `openspec/changes/archive/2026-09-29-danmaku-motion-and-rig/` |
| `danmaku-pipeline-capacity` | 规范已建立 | `openspec/specs/danmaku-pipeline-capacity/` |
| `danmaku-motion` | 规范已建立 | `openspec/specs/danmaku-motion/` |
