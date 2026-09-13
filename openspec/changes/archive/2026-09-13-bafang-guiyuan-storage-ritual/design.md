# Design: bafang-guiyuan-storage-ritual

## Context

pattern `gensokyou:bafang_guiyuan_circle` 已定稿（2~5 阶，tiers [2,3,4,5]；祭品台 key a/b/c/d 逐阶新增，展开后台数累积 4/8/16/24；均绑 `#gensokyou:ritual_pedestals`）。核心 BE 现有灵力实现为单字段 `storedSpiritPower` + `getCapacity()` 按图案硬分派（共鸣=0 / 加具土命=公式 / 兜底=`CAPACITOR_CAPACITY` 配置）。`SpiritCoreItem` 六阶定值：容量 50000×12ⁿ、速率 1000×8ⁿ，`tier()` 可感。万象共鸣按 `spiritIn/OutRatePerSecond()>0` 选端点、每 tick 直推 `receive/extract`。扣费链 `SpiritPowerHelper.drainCapacitorsAround` 有 3 个调用点（BE 启动扣费 ×2、BarrierBreak ×1）。

## Goals / Non-Goals

**Goals:**
- 八方归元 = 聚合蓄电池组：灵力物理托管于台上核心，仪式对外是单一 `(Σstored, Σcap, Σrate)` 储灵池。
- 与共鸣网络零胶水集成：声明 in/out 速率即成为双向端点。
- 清退 generator/capacitor/tempering 三具孤儿与其配置/语言/死数据，扣费链改道不断线。

**Non-Goals:**
- 不碰玩家灵力池（无存取按钮、无 onUse 直连）。
- 不改 pattern JSON、不做仪式配方、不做投料自动化。
- 不动 `BarrierBreakBehavior` 功能与隙间方块（pattern 后补另立项）。
- 不删 `temperLevel` 附件字段（旧档兼容），只是再无增长途径。

## Decisions

### D1 托管模型（否决"仪式自记账"）
`stored ≡ Σ核心已存`、`capacity ≡ Σ核心容量`，receive/extract 逐核读写物品数据组件。灵力守恒由物品载体天然保证：拔走带电核心=带走灵力；容量波动无需 clamp，杜绝蒸发/复制漏洞。备选"抽象池 + 浮动容量"被否——容量缩小时 stored>cap 无解。

### D2 BE 分派：`SpiritBank` 小接口 + 解析钩子
新增接口（behavior 包）：

```java
public interface SpiritBank {
    long stored(ServerLevel level, BlockPos corePos, RitualMatch match);
    long capacity(ServerLevel level, BlockPos corePos, RitualMatch match);
    long receive(ServerLevel level, BlockPos corePos, RitualMatch match, long want);
    long extract(ServerLevel level, BlockPos corePos, RitualMatch match, long want);
}
```

`RitualCoreBlockEntity` 增私有 `resolveBank()`：`activeMatch==null` 或行为非 SpiritBank → 走既有字段/兜底路径；否则四件套整体转发。`BafangGuiyuanBehavior implements SpiritBank`。否决"在 BE 里继续 switch 图案 id"——那会把共鸣/加具土命/归元三个 if 越堆越长；接口化让 `getCapacity` 现有分派一并按 `instanceof SpiritBank` 收拢（共鸣的 0 容量与加具土命维持现状不强行迁移，避免本变更扩大）。`getCapacity` 兜底值：删 `CAPACITOR_CAPACITY` 配置，改代码常量 `DEFAULT_CORE_CAPACITY = 10_000L`（杂项仪式缓冲语义不变）。

### D3 速率方案 b：每核独立限速、台位并行（收/发两向分道）
识别核心按规范序（z,x,y）枚举。每次 `receive/extract` 内：本 tick 该核预算 = `floor(该方向速率×1000/20) + carry_i`（×1000 定点进位，仿 `fillCarry`；deposit 用 inRate、extract 用 outRate），实收 = min(预算/1000, 空位或存量, 剩余请求)。carry 分道存 behavior 内静态 `Map<corePos, Map<pedestalPos, Long>>`×2（不持久化，`onStructureLost` 清理——与 ResonanceRelay `TOWERS` 同款先例）。**声明层两管道独立**：`spiritInRatePerSecond = Σ inRate`、`spiritOutRatePerSecond = Σ outRate`；"inRate = outRate = 核心单一定值"的同源装配收敛于唯一方法 `coreRates()`——将来核心拆双速率或仪式加方向乘数只改这一处。实际收/发由供需截断、彼此可不等，各 ≤ 各自最大（上限语义沿用 ritual-power-attributes）。方案 a（×10 无视核速）被否：速率特例破坏核心 tooltip 诚实性、插满台位无收益、需新增配置常数且 L5 满配吞吐反而更低。

### D4 台位枚举：从 palette 反查，不写死 key
`RitualPatternLoader.byId(patternId)` → 遍历 palette 值等于 `#gensokyou:ritual_pedestals` 的 char 集合 → 并 `match.positionsOf(key)` → 去重排序。每调用最多 24 格 BE 查询，频率（共鸣每 tick 探测 + GUI 刷新）可忽略，不做缓存。a/b/c/d 与 Kagutsuchi `P` 的 key 差异被完全吸收，pattern 零改动。

### D5 阶级门槛与未识别
`stack.getItem() instanceof SpiritCoreItem c && c.tier() <= match.level()` → 计入 Σ； SpiritCoreItem 但 `tier > level` → 计入"未识别"提示数（占台不计数）；其他物品 → 静默忽略（不占灵）。不拦放置：祭品台/代理箱链路照旧，自动化塞进的高阶核由玩家管道自理。

### D6 扣费链改道：泛化 SpiritPowerHelper
`capacitorsAround` → `storagesAround`：过滤条件由 `isPattern(CAPACITOR)` 改为 `activeMatch != null && 非中心格 && getStored() > 0`；`drainCapacitorsAround/hasCapacitorAround` 随改名，经 `extract()` 扣取（归元 BE 自动落到托管核心）。语义上"附近有余灵者皆可为充值来源"——归元成为正式提现场，加具土命缓冲亦可就近付款（其缓冲本就是灵力，可接受）。BarrierBreak 调用点零逻辑改动。

### D7 端点声明与"常活"语义
`spiritIn/OutRatePerSecond = Σ识别核心速率`（零核=0 → 共鸣候选过滤自动隐身，拔核后旧链接由既有 `needyEndpoints` 过滤停灌、`sweepLinks` 不清除——插回即复活）。储灵聚合是**按需计算**（receive/extract/capacity 现算），不经 `serverTick`，因此**无需 enabled 即常驻可充可放**；`serverTick`（enabled 时）仅做 1Hz `sendRitualInfoToViewers` 供 GUI 数字滚动。启动零核心不拦（onStart 默认 SUCCESS），信息栏提示。

### D8 GUI 信息行（InfoLine，行为自主渲染）
- 托管行：`灵核 N/台数 · 已存 X · 容量 Y · 速率 Z/s`（数字过 `compactNumber`，复用共鸣工具）。
- 未识别警告行（有才显示）：`N 个更高阶核心未识别（本仪式 ≤X 阶）`。
- 零核提示行：`台上无可用灵力核心`。
- 固定头"灵力 X/Y"行经 D2 分派自动变 Σ 口径，零改动。

### D9 删除清单（精确到项）
类文件 ×3（Generator/Capacitor/Tempering）；`RitualBehaviors` 常量+注册 ×3 及 import；`GensokyouConfig`：`CAPACITOR_CAPACITY` `CAPACITOR_TRANSFER_RATE` `GENERATOR_SP_PER_SECOND` `GENERATOR_PUSH_INTERVAL_TICKS` `TEMPER_SP_COST_BASE` `TEMPER_SP_COST_GROWTH`；lang zh/en：`jei.gensokyou.ritual.{generator,capacitor,tempering}_circle`、`msg.gensokyou.capacitor_{withdraw,deposit}`、temper 系列（`gui...temper`、`msg...temper_*`，`msg.gensokyou.temper_no_power` 若仍被启动扣费引用则改键保留）；`data/gensokyou/spirit_processing/*.json` ×4；`GensokyouConfig` 中 spiritDamage 注释提及 tempering 的措辞顺改。

## Risks / Trade-offs

- [共鸣读端点速率每 tick 重扫台位] 24 格 BE 查询 × 少量端点，可忽略；如实测异常再加 per-tick memo。
- [carry 键为 pedestalPos，热插拔核心残留旧进位] 进位量级 <1/1000 单位/核，换核即覆盖，无累积误差；结构失配全清。
- [旧档 bafang 电容余额蒸发] WIP 阶段用户已确认零迁移。
- [temperLevel 成僵尸字段] 保留读取与 HUD，无增长途径；将来淬体重做直接复用。
- [加具土命缓冲被就近扣费抽干导致电池断充] 属可接受博弈（缓冲就是灵钱）；如需保护后续给扣费源排序策略，不在本变更。
- [`getCapacity` 分派重构波及共鸣/加具土命] 仅新增 `resolveBank()` 前置分支，两既有分支原样保留，回归测试盯住"零缓存塔灵力行隐藏"场景。

## Migration Plan

WIP 阶段无存档迁移：删配置项即生效（旧 toml 多余键被忽略）；成型 bafang 的兜底余额自然作废；tempering 玩家等级数值留在档里无人消费。回滚 = revert 提交。

## Open Questions

- 无。交互细节（不碰玩家池、不拦零核心、不识别即占台）均已由需求方拍板。
