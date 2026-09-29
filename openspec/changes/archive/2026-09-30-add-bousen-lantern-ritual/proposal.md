## Why

忘川灯坛的 pattern 已落盘（`data/gensokyou/rituals/bousen_circle.json`，tiers `[1,2,3]`，累计 **16 / 32 / 64** 根 `minecraft:candle`），但**没有任何行为类绑定**。按 `ritual-core-interface` 的空壳约定，它当前能成型、能开界面、能摆祭品台，但：

- 产灵速率恒为 0（`spiritOutRatePerSecond` 走默认 0 ⇒ **不可被万象共鸣路由选为供灵源**）
- 缓存回落到 `DEFAULT_CORE_CAPACITY = 10000`（与 1/2/3 阶的 50000 / 1000000 / 10000000 全部不符）
- 64 根蜡烛是纯装饰，玩家逐一点燃后没有任何回报

它是本项目**第一个「结构自带大量状态方块、且该状态参与产灵判据」的仪式**。实现前必须先把「蜡烛随机熄灭」这件事的算法与成本口径定死：逐烛逐 tick 的忠实实现会把仪式核心的固定开销放大一个量级，而它并不需要。

## What Changes

- **新增 `BousenBehavior`**，绑定 `gensokyou:bousen_circle`，启用启停通道。
- **全亮门控产灵**：仅当 `enabled == true` 且「结构内全部蜡烛处于点亮态」时产灵；产灵速率 / 缓存上限 / 供灵端点速率三者均按**结构等级分阶取值**。
- **聚合熄灭机制**：每 `extinguishPeriodTicks`（默认 200 tick = 10 秒）结算一次，熄灭根数服从二项分布，熄灭对象从当前点亮集合中均匀抽取。**不做逐烛倒计时、不做逐 tick 扫描、不给蜡烛挂方块实体。**
- **熄灭后自我冻结**：一旦出现熄灭蜡烛（即不再全亮），仪式停止产灵**且不再继续执行自动熄灭结算**，直到玩家把蜡烛重新点亮。
- **启停不改蜡烛**：启停按钮只切换产灵，不点亮也不吹熄任何蜡烛；蜡烛的亮灭完全由玩家的火石操作决定。pattern `toggleable` 由 `false` 改为 `true`。
- **蜡烛属于仪式结构**：打掉任一蜡烛 → 该格不再匹配 → 仪式不成型（沿用框架既有语义，不为本仪式开例外）。
- **新增渲染态 kind `bousen`**：以 8 字节（一个 `long` 位掩码，bit i = 第 i 根蜡烛点亮）同步点亮集合；蜡烛坐标由客户端读**同一份已下发的 pattern JSON** 本地推导，零新增 payload。
- **新增客户端特效**：点亮蜡烛覆淡蓝色 billboard、熄灭蜡烛覆淡红色 billboard（并加大 + 呼吸脉动 + 一道冲天细光柱用于远距离发现），仪式产灵时坛面覆一层淡金铺光。
- **新增四张分阶配置表**（产灵速率 / 缓存上限 / 供灵速率 / 每根熄灭概率）+ 结算周期与重扫周期，全部进 `GensokyouConfig` COMMON。
- **配套**：缓存分派接入 `getCapacity()`、`RitualBehaviors` 注册、zh_cn/en_us lang、`/gs_debug` 探针、Patchouli 指导书条目。

**本期明确不做**（避免范围蔓延）：

- ❌ 批量点灯 / 停机吹熄的仪式化按钮（玩家自行逐根用火石点燃）
- ❌ 熄灭瞬时反馈（音效、迸射粒子）——归入全项目统一的 FX 批次
- ❌ 忘川专属方块 / 新仪式石品阶
- ❌ 配方与产出掉落

## Capabilities

### New Capabilities

- `bousen-lantern-ritual`: 忘川灯坛的产灵门控（全亮 × 启停）、分阶灵力三件套（产灵/缓存/供灵）、聚合熄灭结算与其自我冻结语义、启停对蜡烛状态零干预、蜡烛参与结构完整性、GUI 信息行呈现。

### Modified Capabilities

- `ritual-runtime-fx`: 登记新渲染态 kind `bousen` 的字段语义（`enabled` / `tier` / `movingMask` = 蜡烛点亮位掩码，`linkPos` 不使用），并明确其表现范围由已同步的 pattern 切片本地推导、按现规则 MUST NOT 新增 payload 字段或 tick 广播。

## Impact

**新增文件**

- `ritual/behavior/BousenBehavior.java` — 行为主体
- `ritual/BousenLanterns.java` — 蜡烛位表解析（服务端 `positions(match)` / 客户端 `offsets(pattern, tier)` 共用同一套筛选与排序代码）
- `tools/gen_tex.py` 新增一张 `textures/fx/lantern_halo.png`（白色径向柔光，靠顶点色着色）

**改动文件**

| 文件 | 改动 |
|---|---|
| `data/gensokyou/rituals/bousen_circle.json` | `"toggleable": false` → `true`（改后须重跑 `tools/validate_ritual_pattern.py --test-out`） |
| `ritual/RitualBehaviors.java` | 新增 `BOUSEN` 常量 + `register(...)` |
| `ritual/RitualRenderState.java` | 新增 `KIND_BOUSEN = 10` 及其字段语义注释 |
| `block/entity/RitualCoreBlockEntity.java` | `getCapacity()` 加 `bousen` 分支；`buildRenderState()` 加 `KIND_BOUSEN` 分支；新增两个瞬态字段（点亮掩码 / 点亮计数）+ 存取器 |
| `client/renderer/RitualCoreRenderer.java` | `render()` 分发 + `renderLanterns(...)` |
| `config/GensokyouConfig.java` | 四张分阶表 + 六个标量/周期项 |
| `ritual/command/DebugCommands.java` | `/gs_debug bousen` 探针（机读单行） |
| `assets/gensokyou/lang/{zh_cn,en_us}.json` | 信息行 / tip / lore 键 |
| `assets/gensokyou/patchouli_books/.../entries/` | `ritual_bousen_circle.json`（由 `tools/gen_ritual_book_entries.py` 生成） |

**网络影响**：新增渲染态 kind 复用既有 `RitualRenderState` 通道，稳态零包；最坏情况（每秒一次熄灭）每次 8 字节。**无新增网络包类。**

**性能影响**：每座已加载灯坛每秒新增 64 次 `getBlockState`（与既有 `matchAt` 的约 1240 次方块判定同量级偏低）+ 0.16 次二项采样 + 期望 0.16 次 `setBlock`。**无逐 tick 新增工作。**
