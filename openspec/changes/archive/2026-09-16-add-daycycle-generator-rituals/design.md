# Design: add-daycycle-generator-rituals

## Context

`gensokyou:nichirin_circle`（日轮天台）与 `gensokyou:tsukikage_circle`（月影水镜）的 pattern 已定稿（`tiers:[0,1]`、`toggleable:true`、含 `P` 祭品台格位、JEI 名已挂 zh/en），Java 侧零实现。两者是同一"昼夜产能发电机"机制的日月两参数：结构能成型、开界面，但 `RitualBehaviors` 无条目（无 tick、无 out 速率）、`getCapacity()` 回落到 `DEFAULT_CORE_CAPACITY=10000` 且不随阶。需求决策已与用户逐条确认（见 D1–D10）。

### 已核验的 API（`sourcesAndCompiledWithNeoForge_*.jar`，2026-09-16）

- `LevelAccessor.dayTime()` 默认实现 = `getLevelData().getDayTime()`（`Level` 实现 `LevelAccessor extends LevelTimeAccess`）；`Level.getDayTime()` 同为 `levelData.getDayTime()`。**不要用 `LevelTimeAccess.getTimeOfDay(float)`**——它经 `DimensionType.timeOfDay(long)`（`DimensionType.java:168`）返回的是给天空着色用的平滑曲线，不是线性三角。
- `DimensionType.fixedTime()` 为 `OptionalLong`、`hasFixedTime()` 存在——fixed_time 维度取 `fixedTime().orElse(dayTime)` 方与视觉一致。
- `DimensionType.moonPhase(long)` 存在，`getMoonPhase()` 为 `LevelTimeAccess` 默认方法（月相字段留给 D7）。
- `RitualCoreBlockEntity.tickBatteryAutoFill()`（`:808`）：缓存→槽内 `SpiritCoreItem` 按 `fillRatePerSecond` 每秒搬运、`fillCarry` 进位，即"缓存自然回流核心"。
- `SpiritCoreItem.receive(ItemStack, long)` / `getStored` / `fillRatePerSecond` / `capacity`；`refundCached`（`:771`）是不限速直注槽核的先例。
- `ResonanceRelayBehavior` 选源条件：`outRateOf > 0 && core.getStored() > 0`（`:345`），且 out 速率按结算周期 memo（`:343`）；`getStored()` 对非托管核心只反映缓存 `storedSpiritPower`，**不含槽内核**。

## Goals / Non-Goals

**Goals:**
- 日轮/月影成型后可启停地按线性三角时间线产灵，峰值 5×4^L/秒、缓存 10000×4^L，逐秒取整发放。
- 灵力按"核优先 → 缓存 → 缓存回流核 → 作废"分流（发电机语义）。
- 对外供灵上限固定 10000/s，供万象共鸣抽取；任意维度生效。
- 数值全 config 化；GUI 有可读状态行与由来诗；具备调试与测试手段。

**Non-Goals:**
- 不改 pattern JSON（仍 `tiers:[0,1]`，不补更高阶结构）。
- 不做月相/天气/见天加成（D6 仅预留字段）。
- 不做配方、不做启动会话（无 recipe、无 `handlesStartViaUiAction`）。
- 不改 `RitualMatch`/matcher/灵力核心物品/托管（`SpiritBank`）体系。

## Decisions

### D1 产灵时间线：三角函数 + 逐秒四舍五入（否决定点积分）

```
半周期 H = 6000 tick，峰值 P(L) = 5 × 4^L
t = Math.floorMod(fixedTime.orElse(dayTime), 24000)      // 0=日出 6000=正午 12000=日落 18000=午夜

f_solar(t) =  t / H                (0 ≤ t < 6000)
              (12000 − t) / H      (6000 ≤ t < 12000)
              0                    其余
f_lunar(t) =  f_solar((t + 12000) mod 24000)

produced(L, t) = Math.round(
        P(L) × f(t) )              // 逐秒 at ageTicks % 20 == 0
```

- 用户明确要求"期间的灵力四舍五入"（D1=a）→ 取整后的整数即每秒实际产灵，**不做 ×1000 定点进位积分**。`Math.round` 半值向上：日升/日落两侧均向上取，日总量略高于理想三角形，接受。
- 把 `f`/`produced` 做成**世界无关纯静态函数**（仿 `YumewatariBehavior` 的静态结算内核），便于单测与调试命令复用。
- 采样时点 = 每结算秒（`ageTicks % 20 == 0`）读一次 dayTime；直接取整发放，不做周期内积分。

### D2 数值：峰值 5×4^L、缓存 10000×4^L（config 基项）

| 键（COMMON） | 默认 | 语义 |
|---|---|---|
| `nichirinBaseRatePerSecond` / `tsukikageBaseRatePerSecond` | 5.0 (Double) | 0 阶峰值，×4^L |
| `nichirinBaseCapacity` / `tsukikageBaseCapacity` | 10000 (Int) | 0 阶缓存，×4^L |
| `nichirinOutRatePerSecond` / `tsukikageOutRatePerSecond` | 10000 (Int) | 供灵上限，固定不随阶 |
| `tsukikageMoonPhaseScaling` | false | 月相缩放预留位（D7） |

`RitualCoreBlockEntity.getCapacity()` 分派新增 `NICHIRIN`/`TSUKIKAGE` 分支 `daycycleCapacity(level) = base × 4^L`（仿 `kagutsuchiCapacity`/`yumewatariCapacity`，`:206-222`）。pattern 当前 `tiers:[0,1]` → 实际两档：0 阶 5/s+10000、1 阶 20/s+40000；更高阶待 pattern 补结构后**无需改代码**即可生效（公式已按任意 L 写）。

### D3 tick 通道 = `serverTick`（enabled 门控），保留 `toggleable:true`

用户选"启停可控"（B2）。pattern 已 `toggleable:true`（按钮显隐唯一来源），故走 `serverTick`；停机则产灵与缓存回流一并冻结（"停机=冻住整机"）。否决 `serverPassiveTick`（与启停可控冲突；若坚持被动需改 pattern 为 `toggleable:false`）。覆写 `onStructureLost` 清理任何内存态（本设计无跨 tick 持久态，预置以防未来加 FX/表）。

### D4 灵力分流：核优先 → 缓存 → 缓存回流核 → 作废（发电机语义）

产灵（`serverTick`，**enabled 门控**）：

```
produced = D1 取整值
rest = produced
if 槽内是 SpiritCoreItem:
    rest -= SpiritCoreItem.receive(battery, rest)      // 不限速直注（refundCached 先例）
    setBatteryStack(battery)
if rest > 0:
    core.receive(rest)                                  // 进缓存，截到 getCapacity()；余量作废=空烧
```

缓存回流（`serverPassiveTick`，**无门控**，成型即每秒一次）：

```
core.tickBatteryAutoFill()                              // 缓存按核心注灵速率回流入未满核心（carry 进位）
```

- 与迦具土（缓存优先、再缓转核）**相反**：发电机"先灌核、缓存只接溢流"。`usesCoreSocket()` 保持默认 true（槽是产出的首要去处）。
- **启停语义分离（OQ2 决议 = 停机也回流）**：产灵随 enabled 停；缓存向核的回流 MUST NOT 受启停门控，改走 `serverPassiveTick`（成型即跑，先例=梦渡）——"停机只停发电、不停搬运"。
- **OQ1 决议 = 接受字面规则**：非托管核心 `getStored()` 只反映缓存，路由选源要求 `getStored() > 0`；插核且核有头寸时缓存恒 0 → 该发电机暂不可被路由，拔核/核满后缓存有货、路由恢复。核=随身电池、缓存=路由货架，语义自洽。
- 内部产灵走普通 `receive`，不经 `extractRouted`/`receiveRouted` 账本（与迦具土同规约，避免被自身 inRate=0 误截）。

### D5 供灵端点：静态 10000/s

`spiritOutRatePerSecond` 返回 `..._OUT_RATE_PER_SECOND` 定值（跨阶不变）。**MUST 静态**：路由按结算周期 memo 速率（`ResonanceRelayBehavior.java:343`）且以 `>0` 筛源（`:401`）；返回随时间变化的动态速率会导致源在黄昏/夜间从候选消失或周期内闪断。`spiritInRatePerSecond` 不覆写（=0，不可为汇）。

### D6 维度无关计时、不判天空

任意维度用 `level.dayTime()`（fixed_time 维度用 `fixedTime().orElse(dayTime)`）——下界/末地照产（用户指定 D4）。**不**判 `dimensionType().hasSkyLight()`、**不**判 `canSeeSky()`（D5）。`doDaylightCycle=false` 时 dayTime 冻结 → 恒定产灵（写入 spec 场景，属预期）。

### D7 月相字段预留、v1 不启用

仅落 `tsukikageMoonPhaseScaling=false` 配置占位与注释，MUST NOT 参与计算；后续如需按 `DimensionType.MOON_BRIGHTNESS_PER_PHASE`（新月=0）缩放再启用。日轮无对应字段。

### D8 GUI：状态行 + 由来诗（遵守 InfoLine 宽度红线）

覆写 `uiInfo`：一行短状态（当前实际产灵 x/s + 时段标签：上升/正午/下降/夜间），大数走 `InfoLine.compact`、明细进 `tipped` tip；其下固定由来诗（`lore_1..5`）。因 pattern 无 `requirements`，通用清单无内容，**不**额外渲染产灵/容量数值行（沿用梦渡瘦身先例）。新增 lang 键，落盘后跑 `python tools/lang_audit.py` 零缺失。

### D9 可测试与调试

- 纯静态 `produced(dayTime, level, solar)` 单测/断言友好。
- `DebugCommands` 增 `/gs_debug nichirin|tsukikage <corePos>`：回显当前 dayTime、时段、`f(t)`、实际产灵、缓存/槽核存量；`/gs_debug <...> at <dayTime> <corePos>` 可注入任意时刻验证取整边界（日出/正午/日落/午夜与 0.5 半值点）。
- 离线 `tools/validate_ritual_pattern.py` 不受影响（不改 pattern）；`runServer` 断言加载无 registry 错误。

### D10 共同行为抽象

日/月仅"时段函数 + 配置基项"不同，其余完全一致。实现取**一个共享纯函数 + 一个 `boolean solar` 参数**（可放同一工具类或抽象基类），两个 `RitualBehavior` 实现各自 `patternId` 绑定；避免两份近乎重复的 tick/分流代码。

## Risks / Trade-offs

- **[取整半值向上] 日总量略高于理想三角形** → 用户指定四舍五入，接受；如需精确积分再改 D1（退路=定点进位）。
- **[核优先使路由饿死（OQ1 已决：接受）] 插入有头寸的核时缓存常为 0，发电机在该核饱和前不可被路由** → 按字面规则实现并文档化；核=随身电池、缓存=路由货架。若日后要"路由始终可用"，退路：产灵改"缓存优先再缓转核"（=迦具土口径）。
- **[停机后仍回流（OQ2 已决：回流）] 停止态缓存继续搬进核** → 产灵走 `serverTick`、回流走 `serverPassiveTick`，两通道分离；"停止"仅停产灵。
- **[全维度照产] 下界/末地也产灵** → 用户指定 D4，接受。
- **[doDaylightCycle=false 恒定产灵]** → dayTime 冻结，属预期，写入 spec 场景。
- **[满缓存空烧]** → 用户指定 D3，接受；无报错、无落物。
- **[config Int 溢出] 4^L×10000** → 当前 L≤1（≤40000），int 安全；若未来 pattern 开到高阶，`getCapacity` 返回 long，config 用 IntValue 在 L≥5 时仍 ≤ 1.024e7，安全。

## Migration Plan

纯新增：两行为类 + 注册、`getCapacity` 两分支、config 键、lang 键、调试命令、新 skill 文档。无存档/数据迁移。回滚 = 删两处注册与 config 键，两仪式退化为"成型但无行为"（与现状一致）。

## Open Questions

（OQ1 = 接受核优先、路由暂不可选中；OQ2 = 停机也回流，走无门控通道；均已由用户确认并落入 D4。OQ3 = GUI 仅短状态行 + tip，不加峰值进度条。）
