## Context

**缺陷一（激光渲染）。** `DanmakuRenderProbe` 的 `ALWAYS_LOD = true`、`dense` 恒真 ⇒ `effectiveGlow()` / `effectiveCore()` 恒 false。`LaserDanmakuRenderer` 的延迟预警线、外发光、亮核、端盖、法阵全部挂在该门控上，于是只剩本体光柱。归档 change `danmaku-dense-render-lod` 的 `danmaku-render-state` 单通条款原文只写「**球/灵符**渲染器 SHALL 发射 body 单通」，激光本不在降级范围内——这是实现把门控做成全局导致的越界。

**缺陷二（冻结弹）。** 原版只在区块处于 `FullChunkStatus.ENTITY_TICKING`（距任一人玩家 ≤ 模拟距离）时 tick 实体。弹幕飞出模拟距离后：区块若在视距内 = 已加载但不 tick（`EntityManager` 里还在）；若超出视距 = 卸载并存档。而弹幕寿命 `age()` 走 `基准 + tickCount`，`tickCount` 只在被 tick 时自增 ⇒ 冻结弹永不判寿命、永不销毁。后果：占满 `DanmakuBudget` 计数 → 逼近 `danmakuEntityCap` 时 BOSS 停发；玩家返回时成片弹幕「复活」。

约束来自现有 spec `danmaku-age-continuity`：年龄驱动运动连续性（编队/曲线/相位），必须保留；且「存续判据 MUST 与丢失原因无关」「MUST NOT 采用重新获取时销毁」。该 spec 第 30 行的前提「服务端那份实体一直未被销毁、一直在 tick」被本缺陷证伪，须修订。

## Goals / Non-Goals

**Goals:**
- 激光延迟预警线与激活期各效果层在任意屏上密度下稳定渲染；球形/灵符的 LOD 单通策略不受影响。
- 弹幕存在性以服务端游戏时间为准：冻结时间计入寿命，过期即销毁，不再堆积、不再复活。
- 冻结弹的计数能在玩家离开期间被回收，cap 不再被僵尸弹锁死。
- 旧存档向前兼容；读档剩余寿命语义保持（服务器关停期间不计入寿命）。
- 不触碰年龄同步 / 运动指纹 / 网络协议。

**Non-Goals:**
- 不做按距离/脱离追踪剔除活弹（明确违反 `danmaku-age-continuity`，会误杀追远玩家的弹）。
- 不采用「区块卸载即删 / 不持久化弹幕」（会破坏存档持久化与插墙飞刀跨重载语义）。
- 不改球形 LOD 观感，不改单通贴图策略。
- 不把 `age()` 改成游戏时间（运动连续性必须留在 tickCount 基准上）。

## Decisions

### D1：激光豁免由渲染器侧旁路，而非关闭全局 LOD
`LaserDanmakuRenderer` 不再读取 `DanmakuRenderProbe.effectiveGlow()/effectiveCore()`，其各层只受显式调试开关（`glowEnabled/coreEnabled/bodyEnabled`，默认全开）约束。球形/灵符维持 `dense` 门控与 LOD 贴图选择。

- 备选 A：`ALWAYS_LOD = false` 恢复真实密度滞回 → 会把球也拉回三层，违背「球保持单通」的既定取舍；否决。
- 备选 B：给 probe 增加「是否可 LOD 化」参数，由各渲染器声明 → 更通用但引入新抽象，本变更只需激光一处，收益不足；记为未来可选项。
- **预警线定位**：红色预警线是玩法信息（告知开火位置与时机），其渲染 MUST NOT 因屏上密度而被抑制。它同样不再受 `dense` 约束；是否受 `/danmaku layers glow` 调试开关约束保持现状（默认开启）。

### D2：存在性时钟 = 服务端游戏时间
新增「出生游戏时间」`birthGameTime`（long，服务端权威）。过期判据：`level.getGameTime() - birthGameTime > lifetimeTicks`。

- 备选：`System.currentTimeMillis` / 真实墙钟 → 服务器关停期间也流逝，读档即过期，违背「读档后寿命按真实年龄扣减（剩余 50 tick）」的既有语义；否决。
- 备选：继续用 `tickCount` → 正是根因；否决。
- `gameTime` 在服务器未运行时不自增，故「世界关停期间不计寿命」自动成立。同一 level 内自洽（出生与判据取同一 level 的 gameTime）。

### D3：`birthGameTime` 必须持久化
NBT 键 `BirthGameTime`（long）。

- 不持久化、读档时用 `gameTime - age` 反推 → 会丢掉「区块卸载期间流逝的游戏时间」，重载后又复活，正是要消的症状；否决。
- 缺键旧存档回退：`birthGameTime = gameTime - restoredAge`（即「从当前已恢复年龄起继续正常寿命」），不劣于变更前，且不报错。

### D4：过期自检放在实体自身 tick 内，仅服务端
`AbstractDanmakuProjectile` 与 `LaserDanmaku` 各自的 tick 路径（`tickDanmaku()` / `tick()`）在服务端执行一次 O(1) 比较。

- 性能：每枚正在 tick 的弹每 tick 一次 long 读 + 减法 + 比较。1000 弹 × 20 tps ≈ 2 万次/秒，纳秒级；相对该弹本 tick 既有的 `super.tick()` / 扫掠 / 解析运动，是噪声。**不引入任何新遍历**。
- 客户端不执行该自检（`!level.isClientSide` 守卫）：客户端 gameTime 与服务器不同步，且客户端弹由服务端移除包收敛。

### D5：低频服务端清理扫，只对「已过期」的冻结弹生效
冻结弹自身不 tick，D4 自检不跑，故需一个节流的服务端扫描对冻结弹套用**同一过期判据**，回收 cap。

- 扫描条件：`instanceof AbstractDanmakuProjectile` 且 `gameTime - birth > limit`。**MUST NOT 含距离/追踪条件**（维持「存续判据与丢失原因无关」）。
- 频率：节流（建议每 20~40 tick）。可选 config 开关，默认开启。
- 实现备选：① `level.getAllEntities()` + `instanceof` 过滤——零簿记、无泄漏风险，但会遍历非弹幕实体；② 复用 `DanmakuBudget` 已有的 `EntityJoinLevelEvent/EntityLeaveLevelEvent` 钩子维护 per-level 弹幕集合——遍历量精确，但需处理引用泄漏与跨维度移除。**倾向 ②**（弹幕数量可控且集中，遍历最省），若实现复杂度失控则退回 ①。

### D6：插墙飞刀纳入同一绝对截止
飞刀插墙后以 `stickRemaining` 独立计时（`KnifeDanmaku.tick`）。插墙时应把截止点改写为 `gameTime + tickConfig(knifeStickTicks)`，使冻结的插墙刀也按游戏时间到期；否则冻结的插墙刀永不递减、永久留存。

### D7：`age()` 与存在性判据解耦
`age()` 继续用 `tickCount` 基准（运动连续性、spec 要求、双端同步都依赖它）。绝对寿命是**独立**的存在性判据，不改 `age()` 语义、不进运动指纹、不参与客户端重建。

### D8：修订 `danmaku-age-continuity` 的错误前提
原要求正文「服务端那份实体一直未被销毁、一直在 tick」改为「一直未被销毁；在实体 tick 范围内一直 tick，超出模拟距离时暂停 tick，其存在性由绝对寿命单独裁决」。原场景「跨区块边界的 BOSS 战…按其真实年龄继续飞行」补充「未被绝对寿命淘汰者」；「后撤再贴脸 MUST NOT 被销毁」保持，但明确「按游戏时间到达寿命终点而销毁」不属于该禁令（那是存续判据本身，与丢失原因无关）。

## Risks / Trade-offs

- [冻结弹在玩家离开、BOSS 仍发射时仍占 cap，直到下一次清理扫] → 扫节流 ≤1s，且只对已过期者生效；实测中 cap 回收延迟不超过约 1 秒。
- [扫描可能被人多/实体多的服务端放大成本] → 默认复用 per-level 弹幕集合 + 节流；仅当集合维护复杂时退回 `getAllEntities` 过滤，两种都在弹幕量级下可忽略。
- [`birthGameTime` 初始化时机：实体构造早于入世界 / 读档覆盖] → 用哨兵值（如 `Long.MIN_VALUE`）表示「未设置」，首个服务端 tick 惰性初始化；读档时以 NBT 为准。
- [游戏时间在极端情形（`/time set` 不回拨 gameTime，只有 dayTime 可调）不受影响] → 出生与判据同源，无风险。
- [调试开关误关导致预警线消失] → 仅算子/调试用，默认开启；不改变「正常游戏必可见」。
- [观感回归] → 激光恢复三层；需实机核对水面/云层前的层级（`danmaku-laser` 既有「激光相对半透明方块的渲染层级」条款仍须成立）。

## Migration Plan

1. 代码：先加 `birthGameTime`（字段 + NBT + 自检），再加清理扫；渲染豁免独立提交。
2. 存档：向前兼容，无需迁移脚本。旧存档缺 `BirthGameTime` 走回退。
3. 回滚：还原代码即可；带 `BirthGameTime` 的存档被旧代码读取时该键被忽略（NBT 不报未知键），无破坏。

## Open Questions

- 清理扫实现取 ① `getAllEntities` 还是 ② per-level 集合？（见 D5，实现时按复杂度定，倾向 ②）
- 清理扫间隔与是否加 config 项：建议 interval 默认 40 tick，做成 config 以便压测。
- 预警线是否要完全不受 `/danmaku layers` 约束？（倾向保留约束，默认开）
