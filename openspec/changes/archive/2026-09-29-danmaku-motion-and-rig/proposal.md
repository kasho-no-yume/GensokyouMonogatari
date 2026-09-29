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

**⇒ 故不引入 B。** 该节自己的账已经把 B 判死了：它只省 2% 的带宽，而买它的理由（持久编队）**用「烘焙」更好地做到了**——纯函数方案压根不需要一个要活着的 rig。

下面「采取 B」的原始结论**已被后续讨论推翻**，推翻依据是本节自己的两张表：

| | 带宽 | 复杂度 | 持久编队 |
|---|---|---|---|
| **A** 逐弹积分 | 基准 ~14 KB/s | 无 | ✗ 做不到（无编队概念） |
| **B** rig 实体（已否决） | ~14 KB/s（**同 A**） | 新实体类型 + 追踪范围 + 引用解析 + 销毁时序 + 孤儿弹处置 | ✓ |
| **D** 烘焙编队帧 | ~14 KB/s（**同 A**） | 一个 record + 一个解释分支 | ✓ **且无 rig 要维持** |

D 与 B 带宽完全相同（spawn 包在所有方案下都省不掉），但 D 一次性消灭了 B 引入的整类问题：无渲染实体、无追踪范围、无「弹引用失效装置」、无换阶段销毁时序、无孤儿弹处置。D 是 B 的严格更优解。

**D 的额外收益**：客户端与服务端跑同一纯函数、同一批常数，**没有任何量需要收敛**；而 B 有一个隐含的耦合——客户端 rig 的 `tickCount` 若与服务端差 1，整队会偏 `旋转角速度` 度，只能靠 `lerpTo` 阈值硬拽回来。

## What Changes

- **删除 rig 实体方案，改为「编队帧」烘焙进每枚弹。** 编队是纯数据，不是运行时对象。

  ```
  每枚编队弹额外同步（定标 int，条目数恒定）：
     frameCx, frameCy, frameCz        编队参考点（发射时的世界坐标快照，非实体引用）
     frameAxisYaw, frameAxisPitch     环绕平面（沿用 configureCurve 的角度打包约定）
     frameRotRate                     旋转角速度（度/tick）
     scaleBase, scaleAmp, scalePeriod 缩放的基准 / 幅度 / 周期
     scalePetals                      花瓣数（0 = 纯呼吸；>0 = 随方位角开合）

  每 tick 弹位 = p0 + d·s(t) + [ c + S(t)·Rodrigues(axis, R(t), p0−c) − p0 ]
  ```

  额外 9~10 个 int，**只在下发一次**。以「旋转花 48 弹 / 每 30 tick 一轮」= 2 轮/秒算，
  增量约 `48 × 2 × 9 × 4 B ≈ 3.5 KB/s`——与 B 声称要省的那 2% 同量级。
  既然省不下来，**关键不在带宽，而在于 D 顺带消灭了 B 的全部运行时复杂度**。

  `frameAxisYaw/Pitch` MUST 复用 `configureCurve` 已有的 (yaw, pitch) 打包约定，不新造机制。
  **先例**：`TalismanDanmaku.DATA_TARGET_ID` 证明实体 network id 同步这条路可行；D 选择**不用**它。

  - **缩放项的「花瓣」是几何的，不是参数的。** 每瓣十几颗弹的排布属于 `p0`，由新的玫瑰线
    `Shape.ROSETTE` 产出；编队帧只负责「把这堆 `p0` 一起放大再缩回」。两者正交，
    故「张开花形」不需要运动代码里有任何关于「花」的知识。

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

- **新增玫瑰线 `Shape.ROSETTE`。** 极坐标 `r = 基准 + 幅度·cos(花瓣数·θ)` 排布 `p0`，花瓣数 / 每瓣弹数 / 幅度为参数。**纯几何、零时间行为**——张开与旋转一律由编队帧提供，故运动层不需要知道「花」是什么。

- **编队 MUST 声明于轨道，粒度一轨一帧。** 同一轨的多次重复发射共享同一份编队帧（否则 20 次重复会得到 20 个互不相干的编队，队形在两次发射之间就断了）。编队帧数量 MUST NOT 超过轨道数，故 MUST NOT 超过 3。

- **编队 MUST NOT 有独立生命周期。** 换阶段 / BOSS 结束时只**停止发射**；已发出的弹按其纯函数跑完既定寿命。「弹引用失效装置」这一整类状态因此不存在——以「不存在引用」达成，而非以「引用有确定的失效处理」达成。

## Impact

- **改动面**：新增 `FormationFrame`（纯数学 record）+ `danmaku/motion` 解释分支；`AbstractDanmakuProjectile` 增 9~10 个定标 accessor 与编队落位；`Behaviour` 增 `Formation` 声明、`Track` 增 `formation(...)`、`TrackLint` 增互斥判据；`Shape` 增 `ROSETTE`；`Geometry` 增 origin 模式与弹种分派；`SphereDanmaku.spawnSplitChildren` 修正分布。
- **净删除**：`DanmakuRig` 实体、`ModEntityTypes.DANMAKU_RIG` 条目、`InvisibleEntityRenderer`、`TrackRunner.rigs` 列表与 `discardRigs()`、rig 引用 NBT 与脱钩逻辑。
- **依赖**：MUST 依赖 `danmaku-behaviour-decoupling`（运动曲线是 Behaviour 的一半）。
- **执行细节（硬约束）**：
  - **编队帧的参考点必须在发射时快照**。用户已确认编队 MUST NOT 是反应式的（「弹幕本身不需要被击中」「最多也就是射出的时候取玩家方向速度」）——玩家位置/速度只在发射那一 tick 采样一次烘进弹里，此后弹的世界里再无外部输入。代价是 BOSS 若在符卡中途传送，编队不会跟过去；这是刻意的，它换来「双端逐位一致、无任何量需要收敛」。
  - **`S(t)` 用三角波而非 `sin`**：只用 `frac`/取绝对值，无超越函数，与 `DanmakuSpeedProfile` 的纪律一致。代价是 `cos(花瓣数·θ)` 那一步仍要用 `cos`——编队路径因此**接受了超越函数**，这是相对速度曲线的纪律放松，须在代码注释中显式记录理由（`Rodrigues` 已经在用 `sin/cos`，不引入新的风险类别）。
- **已确认的语义（不再是开放问题）**：
  - **悬停期照常判伤。** 隐藏态是唯一免除实体碰撞的运动状态；悬停走 `checkStationaryEntityHit` 的碰撞箱相交判定。溜め维持其独立语义（埋设期不接触判伤）。
  - **返程终点判据用「越过发射点即销毁」**（原开放问题 1）。`DanmakuSpeedProfile.peakTravelTick()` 给出最远点所在 tick，判据为 `t > peakTravelTick 且 travelAt(t) ≤ 0`——两个守卫缺一不可。
  - **编队弹的碰撞与寿命沿用现有管线**，不因编队而豁免。
- **风险**：
  - 需求 2/5 的「过原点销毁」判据仅在速度曲线沿固定轴（无曲射）时成立。与曲射、与编队帧**均**互斥（编队帧已经把位置写死，再叠一层「改速度」等于两个权威同时写位置，表现为弹一卡一卡抽搐）。已由 `TrackLint.lintFormation` 静态拒绝。
  - 花形 60 颗弹若配 60 tick 寿命，稳态并发约 60/周期，需计入 R3 预算（`TrackLint.steadyStateEstimate`）。
