## Why

现有 6 条弹幕模式需求里，**5 条**要求「位置是 `t` 的函数」，而 `tick()` 只有一个自由度：

```java
// AbstractDanmakuProjectile.java:247
this.setPos(this.getX() + velocity.x, this.getY() + velocity.y, this.getZ() + velocity.z);
```

| # | 需求 | 现状 | 阻塞点 |
|---|---|---|---|
| 1 | 一批弹绕中心 A 排布并绕 A 旋转；A 绕中心 B 旋转前进 | **完全做不到** | `RING_FACING` 发出的是 N 条平行直线弹，出发即散开，无编队概念；`Shot` 无引用中心 |
| 2 | 匀减速至 0 → 悬停 5s → 反向加速 → 过原点销毁 | **做不到** | `configureHover` 到点归零后**永远停住**，无恢复；`lifetimeTicks` 是绝对上限而非分段预算 |
| 5 | 减速至 0 → 悬停 5s → 四面八方炸开 | 部分 | = 需求 2 前半 + 已有 split，但见下 |
| 4 | 玩家周围随机发射点，朝与自身→玩家连线夹角 ≤90° 的激光 | 三个独立阻塞点 | 见下 |

需求 3 由 `danmaku-visual-profile` + `danmaku-behaviour-decoupling` 共同覆盖，需求 6 无法枚举。

### 需求 5 暴露的现存 bug

`SphereDanmaku.spawnSplitChildren` 把子代均分在**垂直于当前速度的一个圆**上。母弹停住时（正是需求 5 的情形）走 `speed < 1.0E-4` 分支，强制 `base = (0,-1,0)`，于是炸成**一个水平圆环**而非「四面八方」。

```
现在（母弹已停）:            需求要的样子:
        ·  ·  ·                    · · · ·
      ·   ●   ·                  ·  ●    ·
        ·  ·  ·                    · · · ·
   一个水平圆 = 玩家可站环心安全区      球面均布（黄金角/Fibonacci）
```

修法：分裂的**分布方式**本身应是参数（`SPLIT_RING` / `SPLIT_SPHERE`），按母弹是否在动来选。

### 需求 4 的三个阻塞点（都不在 `AbstractDanmakuProjectile`）

1. **原点**：所有形状在 BOSS 局部基生成，`forward` 每拍算一次、全部 `Shot` 共用。需求要每发用**自己的** `normalize(player.eye − origin)` 并锥内随机。
2. **方向**：同上，逐发独立。
3. **弹种**：`DanmakuEmitter` 写死 `new SphereDanmaku`。`LaserDanmaku` 是完全不同的类（静止、`noPhysics`、射线判伤、7 个已同步 accessor、客户端独立算 `getPhase()`）。

好消息：**`Geometry` 不需要运行时确定性**。只有服务端发射、客户端只渲染；`pseudo()` 的确定性是为 `TrackLint` 离线断言。需求 4 可用真随机，产出的参数 `LaserDanmaku` 已会同步。所以需求 4 的本质是「`Shot` 能带「归哪个原点、归哪个锥」，且 `emit` 能产出非球弹」。

## 帧同步：结论是**不引入**

带宽账（以「旋转花 48 弹 / 每 30 tick 一轮」= 2 轮/秒为例）：

**A 与 C 的差异不在位置同步——两者都不按帧同步位置。差异全在 spawn 包。**

```
每颗弹的 spawn 成本（vanilla sendPairingData）
  ClientboundAddEntityPacket   UUID 16 + 位置 24 + 速度 24 + type/id/rot 5  ≈ 70 B
  ClientboundSetEntityData…    10 字段 × (id 2B + 类型 1B + 载荷 4B)      ≈ 75 B
                                                                    合计 ~145 B/颗

位置纠正  ClientboundMoveEntityPacket ≈ 12 B，updateInterval(2) ⇒ 0.5 包/秒/颗
```

| | spawn | 位置纠正 | 稳态 |
|---|---|---|---|
| **A** 现有逐弹 | 48×2 轮×145 B = **13.9 KB/s**（98%） | 48×0.5×12 B = **0.3 KB/s**（2%） | **~14 KB/s** |
| **B** rig + 弹持 rigId | 同 A（**spawn 包省不掉**：客户端得有实体才能渲染插值） | 0 | ~14 KB/s |
| **C** 真帧同步（客户端造弹） | 0 | 0 | 2 轮×58 B = **0.12 KB/s** |

两点结论：

1. **B 与 A 同量级。** rig 省下的只是「每颗弹多带的那几项同步标量」与位置纠正包，占比 2% 量级。**选 B 的理由不是带宽。**
2. **C 的 118× 全部来自「客户端自己造弹」**——不是省位置包。代价是客户端与服务端**各持一份实体（实体数 2N）**，以及跨平台 transcendental 纪律。而实体数才是本轮已确认的真正瓶颈（`DANMAKU_ENTITY_CAP` 卡的是服务端那一半）。故 C 不划算。

顺带：既然 A 的位置包在现有实现里**大部分被丢弃**（误差 < 1.0 格² 时 `lerpTo` 直接不纠正，包白发），那每弹 0.5 包/秒 基本是纯浪费——这正是 `danmaku-budget-and-sync-correctness` 中「模拟位置 / 渲染位置解耦」的价值：修完后这些包才第一次携带权威位置供插值使用。

两处代价都很硬：
1. **实体数翻倍**——C 要客户端自己造弹来渲染，但服务端为判伤仍需自己那一份，故实际实体数是 2N。而 `DANMAKU_ENTITY_CAP` 卡的是**服务端**那一半。**C 省带宽，但实体数与 tick 数翻倍**——而实体数才是真正瓶颈。
2. **跨平台 transcendental 纪律**——`Vec3` 是 double、Java double 严格 IEEE 754 确定，但 `Math.sin/cos/atan2` **不保证跨平台一致**。现有代码已在用（`rotateAbout:428` 的 `Math.cos/sin`、`curveAxis():307` 的 `Math.sin/cos/atan2`）。双端各算各的时无所谓；一旦客户端的弹也参与判伤，就被绑死在「任何人不许写迭代式 transcendental」上。

**故采取 B：新增「装置/rig」这一层实体，把「一组弹共享的参数」从 N 份变 1 份。** 但须诚实说明其收益边界：**B 的带宽收益只有 2% 量级（B 与 A 同量级）**，spawn 包在任何方案下都省不掉。选 B 的真正理由是它天然支持**持久编队**（见上），以及零自定义包、零跨平台纪律。

若最终不打算做持久编队，则 **A 本身够用，本变更的带宽理由不成立**——此时本变更只剩「一轨一 rig 使多轨道各自持有独立装置」这一项组织性收益。

## What Changes

- **新增 rig（装置）实体，粒度为「一轨一 rig」。** 无渲染、无碰撞、不判伤。其 position = 中心点 A（走 vanilla 位置同步），orbit 目标（中心 B）是 rig 上一组**同步标量**而非实体（用户确认：B 点「什么也没有，只是数学表达上需要一个中心点」）。一个符卡有 1~3 条并发轨道，故可同时存在 1~3 个 rig。

  ```
  rig accessors: orbitCenter(x,y,z) · orbitRadius · orbitRate · orbitPhase
                 orbitNormal(yaw,pitch) · ringRadius · ringRate
  每 tick:  A(t) = B + R₂·(cos(φ₀+ω₂t), sin(φ₀+ω₂t))  在 orbitNormal 平面内
           纯 tickCount 函数 → 双端解析一致

  bullet:  DATA_RIG_ID (int) + DATA_PHASE (float) = 8 字节 EntityData
          p = rig.position() + R₁·(cos(θ₀+ω₁t)·u + sin(θ₀+ω₁t)·v)
  ```

  `orbitNormal` MUST 复用 `configureCurve` 已有的 (yaw, pitch) 打包约定（`AbstractDanmakuProjectile.java:299-326`），不新造机制。
  **带宽**：每弹的 `SynchedEntityData` **条目数从 11 项降到 2 项**（rig 引用 + 自身相位角）。每条目约 7 字节（2 字节 id + 1 字节序列化器类型 + 4 字节载荷），故 48 颗弹一轮省约 48 × 63 B ≈ **3.0 KB/轮**——但相对每轮 48 份 spawn 包（约 7.0 KB）的量级，这只是 **30% 的一轮**，且 spawn 包省不掉。**这是持久编队方案的前置参数共享，不是带宽优化。**
  **先例**：`TalismanDanmaku.DATA_TARGET_ID`（实体 network id，两端可解）已证明这条路可行。

  **rig 的真正价值可能不止于省带宽：持久编队。** 现有形态下环是「每 30 tick 重发一轮 48 颗」；有了 rig 后可以**只发一次 48 颗，让它们长期挂在 rig 上环绕**，靠各自的速度曲线 / 寿命 / 半径收缩收束。带宽从「每轮 3.0 KB」降到「一次 1.5 KB」，且弹幕密度天然平滑（没有每轮的重发尖峰）。代价是孤儿弹的处置复杂化（见「装置的归属与生命周期」）。这是一个 SHOULD 考虑的设计选项，不是本次 MUST 落地的内容。

- **新增一维标量速度曲线原语。** 位置 = 速度曲线沿固定轴的积分；速度由关键帧表线性插值。**一个原语盖掉一族东方母题**：

  | 需求 | 关键帧 `[(t, v), …]` |
  |---|---|
  | 减速-悬停-反向 | `[(0,v₀), (t₁,0), (t₁+100,0), (t₁+100+t₃,−v_end)]` |
  | 减速-悬停-炸开 | `[(0,v₀), (t₁,0), (t₁+100,0)]` + 那一刻 split |
  | 定速直射 | `[(0,v₀)]` |
  | 溜め | `[(0,0)]` |
  | 加速推进 | `[(0,v₀),(t,v₁)]` |

  - 表达形式为 `1 个行为 id + 4 个 float`。**加新行为 MUST NOT 改线上格式**，只在解释器加一个 `case`——这是「最低带宽」诉求的正确落点。
  - 判据（承接 `danmaku-budget-and-sync-correctness` 的字段准入条款）：**凡不可由 `tickCount` 派生的弹体状态 MUST 走 `SynchedEntityData`。**

- **修正分裂的分布方式。** 按母弹是否在动选 `SPLIT_RING`（垂直于速度的圆）/ `SPLIT_SPHERE`（黄金角球面均分），替换现有「母弹停住时强制 `(0,-1,0)`」的近似。

- **`Geometry` 引入 `OriginMode` 与逐发方向。** `Shot` 记录携带自己的 origin 与 direction；`DanmakuEmitter` 改为可产出非球弹（球弹 / 激光 / 后续弹种）。用户确认需求 4 的发射点「解耦 boss 位置」——boss 不必真的移动过去，故**不需要 rig 层**。

- **rig MUST 显式归属轨道。** 轨道号 MUST 作为 rig 的一个同步字段存在，弹幕侧以「rig 的 networkId」定位（而非「rig 号」），使多 rig 并存时归属关系无歧义。
- **rig 的生命周期 MUST 与符卡阶段绑定。** 换阶段时旧 rig MUST 被销毁，其上尚未消失的子弹 MUST 得到确定的处置（沿自身速度曲线继续飞完 / 一并清除），MUST NOT 留下带孤儿弹的残留 rig。

## Impact

- **改动面**：新增 rig 实体 + `ModEntityTypes` 条目 + 追踪范围决策；`AbstractDanmakuProjectile` 增运动曲线解释器与 rig 引用；`Geometry` / `Shape` / `DanmakuEmitter` 增 origin 模式与弹种分派；`SphereDanmaku.spawnSplitChildren` 修正分布。
- **依赖**：MUST 依赖 `danmaku-behaviour-decoupling`（运动曲线是 Behaviour 的一半）。
- **执行细节（硬约束）**：
  - **引用者必须先于被引用者存在**——客户端要先知道 rig 的 networkId 才能解析子弹的 rigId。rig MUST 在该轮第一发之前 ≥1 tick 生成。
  - **`clientTrackingRange` 当前偏小**——`SPHERE_DANMAKU` 为 8 格（激光 10）。R₁=6 + R₂=4 时最外圈离 rig 有 10 格。环半径上到 6+ 之前必须调。
- **已确认的语义（不再是开放问题）**：
  - **悬停期照常判伤。** 隐藏态是唯一免除实体碰撞的运动状态；悬停走 `checkStationaryEntityHit` 的碰撞箱相交判定。溜め维持其独立语义（埋设期不接触判伤）。
- **风险**：
  - 需求 2/5 的「过原点销毁」判据：`dot(pos − origin, 初始方向) < 0` 表示已越过发射点。该判据**仅在速度曲线沿固定轴（无曲射）时成立**——若弹同时开了曲射，路径已偏离初始直线，`dot` 不再代表「沿初始方向的前进距离」，判据失效。与曲射互斥，须在 lint 层断言。
  - 多 rig 并存时，**一个符卡最多 3 个 rig**（对应 `TrackLint.MAX_TRACKS`），且 rig 的创建与销毁 MUST 与轨道的 `repeatEvery` 节奏解耦——rig 是持续装置，轨道是周期发射，两者生命周期不同。

## Open Questions

1. 需求 2 的反向加速终点用「过原点销毁」还是「回到原点即销毁」？前者严格按需求文本，后者更宽容。
2. 换阶段时 rig 上未消尽的子弹处置方式：沿曲线飞完，还是一并清除？
