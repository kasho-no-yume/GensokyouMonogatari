## Why

两个独立的 Boss 缺陷，都会让"打不中第一只 Boss"这件事雪上加霜。

**缺陷一：`bossMoveSpeed` 是一条死配置，位移根本不受它控制。**

`FairyMoveControl` 的类注释自己写明了原因：

> 原版 `FlyingMoveControl` 只对 `yya` 生效，而 `FlyingMob.travel` 只读取 `(xxa, yya, zza)` 输入向量、**不读 `speed`**

所以该控制器改为自行累加 `deltaMovement`，而它唯一读的速度来源是 `MoveControl.speedModifier`：

```java
// FairyMoveControl.java:44
double accel = this.speedModifier * ACCEL_FACTOR * Math.min(1.0, dist / SLOW_RADIUS);
```

调用方却传了字面量：

```java
// AbstractTouhouBoss.java:290
this.moveControl.setWantedPosition(wanderTarget.x, y, wanderTarget.z, 1.0D);
```

`applyStats()` 确实把 `Attributes.MOVEMENT_SPEED` / `FLYING_SPEED` 设成了 `0.3 × bossMoveSpeed = 0.051`，**但没有任何代码读这两个属性**。断链如下：

```
bossMoveSpeed = 0.17
  └─> MOVEMENT_SPEED / FLYING_SPEED = 0.051    ← applyStats() 确实设了
         └─> FairyMoveControl 从不读 attribute      ← 断链点
                └─> speedModifier = 1.0（硬编码）
                       └─> 终速 ≈ 0.5 格/tick ≈ 10~12 格/秒
```

**实际移速约为配置声称值的 10 倍，且快于疾跑的玩家（4.3 格/秒）。** 在 18 格/秒的球核面前，10 格外的飞行目标在弹道飞行时间内位移约 3 格，而它还在悬停微调——实测 30 发不中一发与此完全吻合。

这不是数值没调好，是**通路根本没接上**。config 里 `bossMoveSpeed` 的注释写着"Default wander speed multiplier"，读起来像是可用的旋钮，实际拧了没有任何效果。

**缺陷二：东方 Boss 没有任何击退抗性。** 全项目无一处设置 `Attributes.KNOCKBACK_RESISTANCE`，也未 override `isPushable()`。玩家用原版武器（剑、活塞、爆炸）可以把 Boss 推着走，破坏站桩输出与弹幕节奏。

## What Changes

### 1. 接通 `bossMoveSpeed` → `speedModifier`

`AbstractTouhouBoss` 游走调用改传 `moveSpeed()` 而非字面量 `1.0D`。

`MoveControl.setWantedPosition` 本就接受 `speedModifier` 参数，通路是通的，只是被喂了常量。改完后 `bossMoveSpeed` 成为**真正生效的全局旋钮**——后续可直接在 config 文件里调数值试手感，无需改代码重编译。

影响全部 4 只东方 Boss（`BigFairyEntity` / `KitsuneBiEntity` / `KuzumonoEntity` / `NomenMaskEntity`），四者共用 `bossMoveSpeed()` 且均无 override。

**这同时修好了 spec 与实现的背离**：`remnant-touhou-bosses` 已明文要求"以下参数 SHALL 可被单只 BOSS 覆写：距离带、**移速**、垂直偏好…"，而实现层从未提供过可用的移速入口。

**BREAKING**（行为变更）：4 只 Boss 的实际移速将从约 10~12 格/秒 降至约 **2 格/秒**（步行玩家的一半、疾跑的 47%）。**默认值无需改动**——既有 `bossMoveSpeed = 0.17` 乘区恰好对应目标速度，数值从来不是问题，问题是它没接上。实机测定后若偏离，以实测重标（design Q1）。

### 2. 东方 Boss 击退免疫

`AbstractTouhouBoss.bossAttributes()` 增列 `Attributes.KNOCKBACK_RESISTANCE = 1.0`（原版机制，1.0 = 完全免疫）。

四只 Boss 全部经由 `bossAttributes()` 构建属性，无绕过路径，故一处改动全覆盖。该属性同时覆盖爆炸与活塞击退，不止近战。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `remnant-touhou-bosses`:
  - **修改** `BOSS 移动为距离带加自由游走`：明确移速 MUST 由 `bossMoveSpeed` 经 `MoveControl.speedModifier` 驱动，MUST NOT 存在绕过该配置的硬编码速度
  - **新增** 击退免疫要求：全部东方 BOSS `KNOCKBACK_RESISTANCE` SHALL 为 1.0

## Impact

- `AbstractTouhouBoss` — 游走调用的 `speedModifier` 实参；`moveSpeed()` javadoc；`bossAttributes()` 增一行
- `GensokyouConfig.BOSS_MOVE_SPEED` — 注释重写（原注释混用 "multiplier" 与"格/tick"两种读法，含义自相矛盾）
- **行为变更**：4 只 Boss 的移速手感整体改变。这是本变更中唯一会让"现有体验"发生显著变化的部分
- 无新方块/物品/数据包，无存档格式变更
- 不影响玩家属性、灵力、仪式、Boss 血量与弹幕

### Release Note 草稿

> 本仓库目前没有 CHANGELOG 设施（`git log` 为单行中文标题、无正文）。以下文本留作发布时取用；`bossMoveSpeed` 的语义澄清亦已写入 config 注释本身——那才是玩家实际会读到的地方。

**东方 BOSS 移速大幅下调（修复）**

四只东方 BOSS（大妖精 / 狐火 / 蜘蛛 / 路岐神）此前**完全无视 `bossMoveSpeed` 配置**——控制器只读 `MoveControl.speedModifier`，而代码传的是硬编码常量，导致实际移速约为配置声称值的 10 倍（约 10~12 格/秒，快于疾跑玩家）。现已接通，移速按配置生效，默认降至约 **2 格/秒**。

`bossMoveSpeed` 现在是一个真正可用的旋钮：**语义为乘区，非格/tick**（`FairyMoveControl` 是加速度模型，终速由飞行阻力系数决定）。可直接在 config 中调整，无需改代码。

⚠ **本次变更无法通过 config 回退到旧手感**——旧手感来自硬编码，与 `bossMoveSpeed` 的语义不同。

**东方 BOSS 免疫击退**

四只东方 BOSS 现已完全免疫击退，覆盖近战、TNT 爆炸与活塞。此前可以用原版武器把 BOSS 推着走。
