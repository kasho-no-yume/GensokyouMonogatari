## Context

上一轮 `resonance-relay-routing` / `bafang-guiyuan-storage-ritual` 交付后，灵力链路可用但结算语义分裂：

- **三层各写限速、时钟不一**：`KagutsuchiFlameBehavior.settlePerSecond` 每秒一次入账（`:155`）；`ResonanceRelayBehavior.routeTick` 每 tick 每对结算（`:210`，预算 `min(源out份额, 汇in份额)/20` + 每塔 carry）；`BafangGuiyuanBehavior.transfer` 每次 `receive/extract` 调用发放一次预算（`:207`，`tickAllowanceBudget`）。
- **限速权不在端点**：端点的收/发速率由调用方（路由器）与调用次数决定。多塔共拉一源或同 tick 多笔调用会重复发放满额预算（归档 `tasks.md` 遗留 10.1）。
- **分配与 spec 不符**：归元 `transfer` 顺序 `min(remaining,…)` 灌满首核，与 spec `bafang-guiyuan-ritual` 的"四核同 tick 均衡进账"冲突（遗留 10.3）。
- **展示层过读**：归元 `actualRates` 用 `w.in*20/elapsed`，而 `w.in` 是含当前 tick 共 `elapsed+1` 个采样的累计和，off-by-one 系统性过读（1280 输入读数 1600）；窗口无封顶导致静默残影（遗留 10.2）。路由用 τ≈2.5s 的 EMA（`:203`）造成启动显示滞后。

需求方已拍板两条语义前提：**① 生产者 = "只能输出的电池"**（内部充能节奏与缓冲合法，输出上限须全局强制）；**② 汇内额度按各核速率上限加权分配，收/发两向同规则**。

## Goals / Non-Goals

**Goals:**
- 把"速率结算是谁的责任"收敛为单一规范：**每个端点自持每 tick 速率账本，账本是唯一权威**。
- 生产者按输出电池建模，不改变产出节奏，仅由端点账本强制全局放电上限。
- 归元汇内分配改为按核速率加权的水位分配（收/发分道），对齐既有 spec。
- 实测吞吐展示改为单调计数器差分，消除过读与残影。
- 明确同 tick 争用为 FCFS，次序由路由 tick 顺序决定、与启停历史无关。

**Non-Goals:**
- 不做跨塔全局带宽仲裁 / 公平轮转（明确留待将来的中央结算）。
- 不改变生产者 1Hz 充能 + 缓冲的既有节奏，不把缓冲当隐藏缓存清除。
- 不新增配置项，不改 pattern / 配方 / 核心定值。
- 不重构 `RitualBehavior` 端点属性接口为统一端点契约（保留现有 `spiritIn/OutRatePerSecond`；只增补账本职责）。

## Decisions

### D1 端点每结算周期速率账本（唯一权威）

引入共享 helper（建议 `ritual/behavior/TickRateLedger`，或单文件值类），承载单方向的：

```
tick        // 上次结算的周期序号（gameTime / period）
remaining   // 本周期剩余可准予整数单位
carry       // <1000 的定点零头，跨周期累计
```

`refill/grant(now, ratePerSecond, periodTicks, …)`：`floorDiv(now, period) != tick` 时重置 `remaining = rate × period/20 + carry/1000`、`carry = (carry + rate × period × 50) % 1000`，再返回 `min(want, remaining)` 并扣减。同一周期内重复调用共享同一份 `remaining` → **幂等**，多塔/多调用不再超发（周期默认 20t = 1s，见 D7）。

- 源端：新增 `RitualCoreBlockEntity.extractRouted()`（**路由面向**）叠加**输出账本**（rate = `behavior.spiritOutRatePerSecond`），再走普通 `extract()`。这是"电池放电总闸"。
- 汇端：新增 `receiveRouted()`（**路由面向**）叠加**输入账本**（rate = `behavior.spiritInRatePerSecond`），再走普通 `receive()`；Bafang 另有逐核账本（见 D3）。
- **为何用 routed 变体而非直接改普通 extract/receive**：`SpiritPowerHelper` 的定向扣费与加具土命内部产灵/记账共用普通 `receive/extract`，MUST NOT 被端点账本限速（spec 明令不改既有定向链路）。路由器改调 routed 变体，普通路径保持原语义。
- 生产者的内部产灵走普通 `receive()`（不经账本，其 inRate=0），故无需额外 `depositInternal`。
- 账本生命周期：`TickRateLedger` 随 BE 实例字段（同 `rateCarry/fillCarry` 先例）自动随卸载/失效丢弃，并在 `setRemoved()` 与结构失效处 `clearRoutedLedgers()`；不持久化。Bafang 的逐核账本与 `Meter` 为静态表，随 `onStructureLost` 清理。

**备选**：把账本放在路由器（现状）→ 多塔重复发放，否决。放在中央服务（方案 C）→ 过度，待跨塔公平成为真实需求再上。

### D2 生产者 = 只能输出的电池

产出仍为 `20×4^L /s`、`ageTicks%20==0` 一次性入缓冲（缓冲是合法储电，不改）。变化仅在**输出侧**：`extract` 由 D1 的输出账本按 `spiritOutRatePerSecond` 全局截断，因此多塔合计 ≤ 放电上限。`inRate` 保持 0，永不被选作汇。启动从空缓冲开始，头 ~1s 无电可放属预期，不是缺陷；不再尝试"瞬间到顶"。

### D3 归元汇内分配：按核速率加权的"水位分配"（收/发分道）

把 `transfer` 的"规范序逐个 `min(remaining,…)` 灌满"改为：

1. 候选核 = 有头寸者（存入看空位、取出看存量）。
2. 目标额度按各核**速率上限 `rate_i` 加权**分配：`share_i ∝ rate_i`；单核再受其**本周期剩余额度**（D1 逐核账本，按周期锁存）与头寸封顶。
3. 被封顶核让出的份额在未封顶核间**按同权重回填**，直至额度用尽或全部封顶。
4. ×1000 定点 + 逐核 carry，保证小数不截断；收/发各用独立账本（`IN_LEDGER`/`OUT_LEDGER` 分道，由"每次调用进位"改为"每周期剩余额度"）。
5. 取整残余用**跨周期持久的每核小数余量累加器（WFQ）**分配：每笔把本核小数余量累加，整数余量发给累加值最大者并扣 `Σw`。否则低速率核的小数余量恒小于高速率核，会被"最大余数法"永久饿死（实测：23×T5 + 1×T4 时 T4 恒为 0）。

因 `Σ（rate_i × 周期/20） = (Σrate_i) × 周期/20`，当请求额 `A ≤ Σ预算` 时不会触碰预算上限，加权分配即 `A·rate_i/Σrate`；封顶回填主要处理已满/已空核。

**备选**：等分（按核数）→ 需求方最终裁定为**按速率加权**；保留顺序灌满 / 最大余数法不累积 → 使低速率核长期饥饿且与 spec 冲突，否决。

### D4 实测吞吐：单调计数器差分

每个端点维护单调递增的 `movedIn/movedOut` 累计（每笔实转 `+=`，不重置）。显示 = `(now-prev)/(nowTicks-prevTicks) × 20`，按 `gameTime` 精确折算，天然无 off-by-one、静默自然趋 0。采样窗 SHALL ≥ 一个结算周期：窗口不足一个周期时沿用上次读数（不重算、不回写窗口），否则"关/开 GUI 落在周期内"会以短窗空结算误显示为 0；窗口起点在 Meter/TowerState 创建时初始化为当前时刻，避免首次读数被大 dt 摊成 0。

- 归元：删除 `WINDOWS/Window/account/actualRates` 的"窗口重置+折算"逻辑，改为计数器 + 上次采样快照。
- 路由：`emaFlow` 改为同源差分（如需平滑，用 ≤1s 的固定窗，不再用无界 EMA）；连同候选 tooltip 口径统一。

**备选**：保留窗口但修 off-by-one（`elapsed+1`）→ 仍带窗口相位别名与静默残影，不如计数器直截。

### D5 同结算周期争用 = FCFS，次序与启停历史无关

端点账本按"谁先调用谁先吃"裁决（周期内 FCFS）。调用顺序 = 各路由器 `serverTick` 的执行顺序（方块实体 tick 顺序），与塔的启动先后、启停切换无关：暂停 B 塔再启动，A 塔仍按原 tick 顺序先取额度，不会变为后到。此语义写入 spec，避免"先到先得"被误读为历史性先占。

### D6 路由器职责降级为"建议 + 触发器"

`ResonanceRelayBehavior` 保留 `needyEndpoints` 与每对预算计算（用于把源速率在自家多汇间分摊、把汇 in 速率在多源间分摊——这是端点做不到的），但该预算**仅作建议**，最终实搬由 `extract/receive` 的端点账本 + 头寸截断。移除 reliance on 自身截断正确性；路由不再需要自己的每对 carry 来防超发（可保留用于把建议值平滑到 tick，但不是权威）。

### D7 结算粒度：以秒为本位（可参数化），替代"每 tick"

新增 `SETTLE_PERIOD_TICKS`（默认 20 = 1 秒，进 `GensokyouConfig`）。端点账本的"本周期额度"口径改为 `速率 × PERIOD / 20`，幂等键 = `gameTime / PERIOD`；FCFS、定点进位、收/发分道、加权水位分配全部不变，只是周期从 1 tick 变 PERIOD。路由器仅在周期边界执行搬运结算。

收益：结算与物品写入次数、端点速率重扫次数（见 R8）、以及（若暂不改 BER）光束粒子发包数各降 PERIOD 倍；且与生产者既有的 1Hz 充能天然对齐，消除"产 1Hz / 搬每 tick"的错拍。代价：灵力按秒成桶交付（在本项目容量/速率量级下不可感知），亚秒级即时性明确放弃。

**备选**：保持每 tick → CPU/包量高 20×，否决；混合（大速率每 tick、小速率每秒）→ 复杂度不值，否决。

## Risks / Trade-offs

- [R1 账本静态表的生命周期与区块卸载] → 优先存 BE 运行时字段（随 BE 卸载/失效自然丢弃）；若复用静态表则必须挂 `onStructureLost` + `setRemoved` 清理（沿用 `TOWERS/WINDOWS` 先例）。
- [R2 加权水位分配的定点回填实现复杂度] → 纯函数内核（可单测），与 `SpiritCoreView` 同风格；无世界依赖。
- [R3 多塔 FCFS 的"靠前塔长期优先"] → 需求方已知并接受；写入 Non-Goals，留待中央结算（将来 C）。
- [R4 生产者内部入账改走新路径可能漏改调用点] → 全仓库检索 `core.receive(`，仅加具土命内部产灵一处需改道；其余调用点语义不变。
- [R5 展示从"窗口均值"变"瞬时差分"后数字跳动] → 允许 ≤1s 有界平滑；数值必须来自单调计数器，不含别名。
- [R6 端点账本与路由器建议值可能"建议 > 实给"] → 属预期（建议只是触发上限，端点才是真闸）；GUI 只显示实测，不显示建议。
- [R7 螺旋/光束现为服务端每 tick `level.sendParticles`（实为 `ClientboundLevelParticlesPacket` 逐玩家广播，是真网络包）] → 满配 L5 光束可达 `2×实搬对数` 束、~10⁴ 粒/tick 的网络量。正解 = 客户端 BER 本地渲染（需给 `RitualCoreBlockEntity` 补 `getUpdateTag/getUpdatePacket`，把 enabled + 解析后的端点坐标 + 包围盒同步到客户端；项目已有 `RitualPedestalRenderer`/`SukimaPortalRenderer` 基建），零持续包；退路 = 降频 + 每周期全局粒子上限。记为后续优化（tasks §9）。
- [R8 路由每 tick 对每个 (源,汇) 对重算 `inRateOf(sink)`，而归元每次都要重扫全部台位] → O(入×出×台位) 次 BE/物品读取，满配可达 ~10⁵/tick/塔。按 D7 降为每秒后缓解 20×；仍建议端点速率 per-period memo（tasks §9）。

## Migration Plan

开发期，无存档迁移。账本/计数器均为运行时内存态，旧世界加载后从零重建。回滚 = revert 本变更提交。

## Open Questions

- 无阻塞项。展示平滑窗长度（瞬时 vs ≤1s）留待实现期按观感定，兜底默认瞬时差分。
