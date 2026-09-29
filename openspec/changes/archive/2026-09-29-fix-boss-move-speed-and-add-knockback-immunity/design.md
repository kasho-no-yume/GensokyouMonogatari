## Context

`FairyMoveControl` 不是原版 `FlyingMoveControl` 的替代品，而是一次**绕行**。原版控制器在 `FlyingMob` 上不产生水平位移（`travel` 只积分 `(xxa, yya, zza)` 输入向量），所以该类改为直接朝目标点累加 `deltaMovement`，让 `FlyingMob.travel` 负责积分与碰撞解算。这是个正确的设计决定，且必须保留——它同时被小妖精与 BOSS 使用。

代价是**速度的入口从 attribute 转移到了 `speedModifier`**，而唯一的上游调用点传的是字面量 `1.0D`。`applyStats()` 里的 `0.3 × moveSpeed()` 仍然照常执行，只是结果无人读取——这是典型的"看起来配了、其实没接线"。

**一个尚未定论的量：终速。** `FairyMoveControl` 是加速度模型（每 tick 累加 accel），终速由 `FlyingMob.travel` 的阻力系数决定，该常数未在本仓库中固定。按 0.9/0.91 两种常见取值估算，当前终速约 **10~12 格/秒**。**该数值必须实机测定**，本设计的所有"新速度"预估都带这个不确定性。

**`bossMoveSpeed` 的语义自相矛盾**：字段注释写"Default wander speed multiplier on the base movement attribute"（乘区读法），但举例写"0.34格-66% -> 0.17格-50%"（绝对格/tick 读法）。两种读法相差约 0.3 倍，直接决定改完之后 Boss 有多慢。

## Goals / Non-Goals

**Goals:**
- 让 `bossMoveSpeed` 成为真正生效的旋钮，且此后可在 config 中直接调参
- 消除 spec 承诺（移速可覆写）与实现（无任何可用入口）之间的背离
- 4 只东方 BOSS 获得完全击退免疫
- 保持 `FairyMoveControl` 的加速度模型与 `FlyingMob.travel` 的碰撞解算不变

**Non-Goals:**
- 不改 `FairyMoveControl` 的加速律（不做速度上限化、不改成直接设定 `deltaMovement`）
- 不改 `ACCEL_FACTOR` / `SLOW_RADIUS` 的数值——它们是相对 `speedModifier=1.0` 调的，改 `speedModifier` 后由 config 承担调节职责
- 不改 BOSS 悬停高度、距离带、游走点选点逻辑
- 不为单只 BOSS 定制速度（大妖精单独 override）——见 D3
- 不改玩家侧的弹幕弹速/伤害/弹耗（属 `rebalance-tier1-spirit-and-danmaku-cost`）

## Decisions

### D1: 只改上游实参，不动 `FairyMoveControl`

把 `setWantedPosition(..., 1.0D)` 改为 `setWantedPosition(..., moveSpeed())`。

**理由**：`MoveControl` 的 `speedModifier` 本就是为此设计的参数，通路早已存在，只是被喂了常量。改动面是一行，`FairyMoveControl` 与小妖精（`FairyEntity`，走 `HoverAboveTargetGoal` 另一条 `setWantedPosition` 调用）均不受影响。

**替代方案（已否决）**：*让 `FairyMoveControl` 回退去读 `FLYING_SPEED` attribute*。这会让 `applyStats()` 现有的 `0.3 × moveSpeed()` 生效，看似更"正确"，但等于把 `0.3` 这个硬编码基数也纳入调参面，且改变了该控制器的契约（它当初存在的原因就是 attribute 不可用）。保留 `speedModifier` 作为唯一入口更干净。

### D2: `bossMoveSpeed` 取"乘区"读法，默认值 0.17 保持不变（目标终速 ≈ 2 格/秒）

**选**：把 `bossMoveSpeed` 明确为 `FairyMoveControl.speedModifier` 的乘区值，默认值 **0.17 保持不变**。

**理由**：与字段名和"multiplier"字样一致；与 `applyStats()` 现有的 `0.3 × moveSpeed()` 结构同构。按当前终速估算（`speedModifier = 1.0` 时约 10~12 格/秒），`0.17` 乘区对应**约 2 格/秒**——正是本次定下的目标速度。

**目标速度 = 2 格/秒**（设计已拍板）。该值约为步行玩家（4.3 格/秒）的一半、疾跑的 47%，在 18 格/秒的球核面前大幅降低前置量需求。

**一个值得注意的巧合**：0.17 恰好就是仓库里**既有的默认值**。也就是说本变更**不需要改动任何默认数值**——只改接线（`speedModifier` 实参）即可从 10~12 格/秒 降到约 2 格/秒。数值本身从来不是问题，问题是它没接上。

**验收仍需实机确认**：终速由 `FlyingMob.travel` 的阻力系数决定，该常数不固定在本仓库中，2 格/秒是估算值。实现后 MUST 实测，若显著偏离则以实测为准重标 `bossMoveSpeed` 默认值，并把注释改写为不含歧义的单读法。

**替代方案（已否决）**：*取绝对格/tick 读法（`bossMoveSpeed` 直接就是目标速度）*。语义更直观，但要达成需把 `FairyMoveControl` 从加速度模型改成速度设定（`deltaMovement = dir × target`），会丢掉 `FlyingMob.travel` 提供的阻力积分与碰撞解算——正是该类注释所珍视的。改动面和风险都大得多。

### D3: 全局修复，不为单只 BOSS 开特例

**选**：修 `AbstractTouhouBoss` 的共享调用点，4 只 Boss 同步变慢。

**理由**：
- 缺陷在共享通路上，逐个 override 只是把 bug 复制四份
- 其余 3 只（狐火、蜘蛛、路岐神）同样移速过快、同样打不中
- spec 承诺的"单只覆写移速"能力由此自然获得——`moveSpeed()` 本就是 `protected`，任何 Boss 将来都可覆写，无需额外机制

**代价**：无法只修大妖精而保留其余 3 只的旧手感。若后续发现某只 Boss 需要不同速度，那是 override 的正当用途，不是回退理由。

### D4: 击退免疫走 `KNOCKBACK_RESISTANCE` attribute

在 `bossAttributes()` 增列 `Attributes.KNOCKBACK_RESISTANCE, 1.0D`。

**理由**：原版机制，四只 Boss 全部经由 `bossAttributes()` 构建属性（已逐个核验），一处改动全覆盖，无需在实体类重复。同时覆盖近战、爆炸、活塞三类击退源。

**替代方案（已否决）**：*在 `hurt()` 里手动抵消击退*。绕过 attribute 会漏掉爆炸/活塞，且与原版机制重复实现。

## Risks / Trade-offs

**[实测终速与 2 格/秒的预估偏离]** → `FlyingMob.travel` 的阻力系数不固定在本仓库，2 格/秒是估算。任务 1.4 MUST 实测；若偏离，以实测重标默认值。注意此时**偏移的是 `bossMoveSpeed` 默认值，不是 `ACCEL_FACTOR`**，保持单一可调旋钮。

**[2 格/秒下"游走而非环绕"场景可能退化]** → `remnant-touhou-bosses` 要求位移覆盖上/下/左/右。2 格/秒跨越 10~20 格距离带需 5~10 秒，垂直方向的游走会显得迟缓。**这是本变更的主要 UX 风险**，需实机观察（任务 2.3）。若确实退化为"几乎不动"，可上调 `bossMoveSpeed` 而无需改结构——这正是把它接通的价值。

**[无法回退到旧手感]** → 旧手感来自硬编码 `1.0D`，与 `bossMoveSpeed` 语义不同。revert 代码即可回到旧行为，但 revert 后玩家会遭遇"配置项再次失效"。release note 需明说。

**[`ACCEL_FACTOR = 0.0533` 是按 `speedModifier = 1.0` 调的]** → 该常数保持不变，调参职责完全转移到 `bossMoveSpeed`。若新速度手感不理想，**不要再动 `ACCEL_FACTOR`**，否则两个旋钮耦合、无法独立归因。

**[四条 Boss 共享同一速度导致手感同质]** → 接受。差异化应由各自的招式与轨道承担（`remnant-touhou-bosses` 的"角色定位"要求），不是靠游走速度。

**[击退免疫可能让某些击杀手段失效]** → 需确认是否已有依赖击退的符卡/机制。已核：项目内无任何实体或符卡读取 `KNOCKBACK_RESISTANCE`，也无 override `isPushable()`，故判定为无依赖。

## Migration Plan

纯代码 + config 默认值变更，无存档格式变更、无数据包。revert 即回滚。

## Open Questions

- ~~**Q1**：`bossMoveSpeed` 在 `speedModifier = 0.17` 下的实测终速是多少？~~ → **已定目标 2 格/秒**；0.17 乘区即为此值，故默认值无需改动。**残留动作**：实现后实测确认（任务 1.4），偏离则以实测重标。
- ~~**Q2**：新默认值应让 BOSS 处于什么量级？~~ → **已定：2 格/秒**（步行玩家的一半、疾跑的 47%）。
- **Q3（残留观察项）**：2 格/秒下"游走而非环绕"是否仍成立。任务 2.3 实机观察，若退化则上调 `bossMoveSpeed`（2~4 格/秒区间内）而非改结构。
- **Q4**：是否顺带统一 `FairyEntity`（小妖精）那条 `setWantedPosition` 调用的 speedModifier？它当前传的值与 BOSS 不同（走 `HoverAboveTargetGoal`），本次不动——小妖精不是本变更的投诉对象。但两条调用点的 speedModifier 语义是否应当一致，值得记录。
