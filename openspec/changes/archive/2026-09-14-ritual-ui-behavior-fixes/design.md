# Design: 仪式 GUI 与信息行为六项修正

## Context

- **停机冻结根因**：1Hz 快照推送 `ModNetworking.sendRitualInfoToViewers` 只存在于行为 `serverTick` 尾部（`KagutsuchiFlameBehavior:84`、`ResonanceRelayBehavior:217`），而 `RitualCoreBlockEntity.serverTick:726` 在 `!enabled` 时提前 return → 停机后 GUI 再无推送。但停机 ≠ 数据不变：共鸣塔 `extractRouted` 不经 enabled 门控仍可抽走缓存；维护断供停机等路径也会改状态。
- **超框根因**：背景贴图 `ritual_core.png` 信息盒右分隔线在 **x=116**（逐像素扫描证实，y=1..149 贯通）。进度条画在 `textX+52 .. +92`：燃烧行带图标 textX=28 → 右缘 120 > 116；悬停高亮 `fill` 画到 PANEL_WIDTH−4=172，同样越界且吞掉按钮列点击（`interactiveRowHits` 宽度 PANEL_WIDTH−8 与启停按钮 x≥120 重叠）。
- **循环 bug**：`nextLinkState` 的 cycle 仅含 [入?, 出?]，"无"不在列——双属性 入↔出 永不到"无"，单属性 cycle.size()=1 原地卡死，与函数注释宣称的"自身两态往复"不符（注释对、实现错）。
- **tip 无实测源**：`TickRateLedger` 只有"剩余额度"，全仓无任何端点级实搬累计量可查。
- **平躺根因**：`RitualPedestalRenderer:68` 激活态仍固定 `XP(90°)` 放倒（注释自述设计），用户要求立起。
- 约束：neoforge-1211-dev 手册红线——速率收发分道、实测吞吐=单调累计差分（禁窗口折算 off-by-one、禁无界 EMA）、数值 compact、tip 内 `\n` 分行。

## Goals / Non-Goals

**Goals:**
- 界面打开期间任意启停态数据 ≤1s 收敛到服务端真值。
- 信息盒可见绘制与命中区不越 x=116 分隔线。
- 产灵/供灵两个概念在配置、方法、spec 三层解耦（数值暂同）。
- 共鸣候选点击可实现（入→出→无）循环，含单属性退化情形。
- tip 并列"声明上限"与"实测吞吐"两类信息。
- 激活悬浮物品立姿自转、静置平躺，过渡平滑无跳变。

**Non-Goals:**
- 不动 ✓✗ 状态标记在右栏（x≥124）的既有版面安排。
- 不做 `resonance-relay-render-perf` 范畴的粒子/渲染态改造。
- 不新增网络包类型；不改 `RitualInfoPayload` 结构。
- 不引入菜单级 viewer 计数注册表（规模不需要，见 D1）。

## Decisions

### D1 快照心跳上移到核心 BE tick 末尾（含停机路径）

`RitualCoreBlockEntity.serverTick` 尾部重构为：

```java
if (core.enabled) {
    if (upkeepTick 失败) { 停机三连; }
    else behavior.serverTick(...);
}
if (core.ageTicks % 20 == 0L) {
    ModNetworking.sendRitualInfoToViewers(serverLevel, pos);   // 全仪式统一，含停机
}
```

- 放行为 tick **之后**（同 tick 内 settle 的新值当帧入快照，无额外 1s 滞后）；upkeep 停机当 tick 也推（顺带修"自动停机按钮不复位"类隐患）。
- 删除行为侧被取代的 1Hz 推送（Kagutsuchi `:84`、Resonance `:217`、BafangGuiyuan `:430`——后者 `serverTick` 仅此一职，删除后覆写整体移除），**保留**事件级即时推送（开关 `clickMenuButton`、点火换批 `tryIgnite:119`）。
- 开销：`sendRitualInfoToViewers` 内部先 `players()` 找持有匹配 `RitualCoreMenu` 者，无 viewer 时仅一次 BE 查找 + 玩家遍历，成型核心数 × 1Hz 量级可忽略。备选"open-menu 计数注册表"被否——当前规模下纯增复杂度。
- 副作用核对：Resonance `uiInfo` 自带 `sweepLinks` 与一次性 statusKey 消费——心跳驱动时同样幂等（点击回包先于下一个心跳 tick，状态行仍由事件推送消费，不丢不重）。

### D2 绘制钳界：新增常量 `INFO_BOX_RIGHT = 112`

（= 贴图分隔线 116 − 4px 视觉余量）

- 进度条：`barX = textX + 52` 不变，`barW = min(40, INFO_BOX_RIGHT - barX)`——燃烧行 80..112（32px），纯文本行 60..100 不变。备选"barX 右对齐 72..112"被否：破坏"进度条紧跟文本"的既视布局。
- 悬停高亮 `fill` 右缘、`interactiveRowHits` 与 `tipRowHits` 的 w 同步钳到 `INFO_BOX_RIGHT`。
- **顺带收益**：点击命中不再覆盖 x≥112 按钮列，修复共鸣塔行悬停/点击吞掉启停按钮的潜在冲突。
- scissor 右缘保持 172（✓✗ 标记在右栏是既有设计，不能被裁）。

### D3 速率分道：新配置基项，方法各走各

- `GensokyouConfig`：`KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND`（默认 20.0，范围 0..1e6，注释"路由供灵上限基值，等级 N ×4^N；与产灵基项独立"）。
- `KagutsuchiFlameBehavior`：新增 `maxOutputRatePerSecond(int level)`（新配置 × 4^L），`spiritOutRatePerSecond` 改用它；`productionRatePerSecond` 仅供 `settlePerSecond`。两方法 javadoc 明确"语义：产灵=内部生成入账；供灵上限=路由可抽取上限；二者 MUST NOT 互相引用"。
- 数值暂同（都默认 20×4^L），行为不变、无平衡性影响；未来可独立调。
- 同步修正手册"同源取值只允许出现在一个装配点"精神：加具土两式各自独立装配，不再共享函数。

### D4 三态循环：cycle 恒含"无"

```java
List<Integer> cycle = new ArrayList<>();
if (canIn)  cycle.add(LINK_IN);
if (canOut) cycle.add(LINK_OUT);
cycle.add(LINK_NONE);                 // 恒含：任何已链接候选可单独取消
int pos = cycle.indexOf(current);
return pos < 0 ? LINK_NONE             // 属性失效的残余链接：点击即解除
              : cycle.get((pos + 1) % cycle.size());
```

- 双属性：入→出→无→入；单属性：入↔无 / 出↔无；无属性残余：→无（静默解除，原语义保留）。
- 配额检查分支（next 为入/出时顶满回显）天然不再拦截"→无"——取消永远可用。
- 提为包私有纯函数 + 单元测试矩阵（`ResonanceRelayBehaviorTest`：3×3 属性 × 3 当前态断言下一态与可达"无"）。

### D5 tip 实测吞吐：端点全局累计计数 + 展示层差分

- `RitualCoreBlockEntity` 增运行时态（不持久化）`routedInTotal/routedOutTotal`，在 `receiveRouted/extractRouted` 实际入账处 `+= moved`。全局口径=跨所有塔，正是"该仪式当前实际入/出"。
- `ResonanceRelayBehavior` 增静态采样表 `Map<BlockPos, long[]{inTotal, outTotal, sampleGameTime, inRate, outRate}>`（ConcurrentHashMap；规模上界=世界成型核心数，随卸载负差分自愈，见下）。`tipOf` 构建时按手册红线差分：`rate = (total − lastTotal) × 20 / (now − lastTime)`；`now − lastTime < 结算周期` → 沿用上次读数（关/开界面不闪零）；total 回退（BE 重载计数归零）→ 视为重新播种、读数取 0。
- tip 模板：具备 out 属性者加"实际供灵 %s/s"行、in 者加"实际受灵 %s/s"行，与"供/受灵上限"分行并列、数值 `compactNumber`。zh/en 双语言键同步改。
- 备选"塔侧 per-pair 流量表"被否：只见自家通道，无法回答"端点全局实际入/出"。八方归元的 `Meter`（池→托管核二维表）结构上服务于另一域（托管池内部），不可复用于 BE 间路由端点，但差分口径范式保持一致。

### D6 悬浮姿态：X 倾角随 blend 插值

`RitualPedestalRenderer`：`XP(90°)` → `XP(90° × (1 − eased))`，置于 YP 自转之后（eased=0 平躺如故，eased=1 立牌绕世界 Y 转盘点转盘，中段平滑立起）。`FLOAT_Y` 1.22 → 1.34：立姿半高 ~0.28 + 浮动 ±0.04，底缘 ≥1.02 不切台面；`BLOCK_HALF_HEIGHT` 抬升逻辑保留。观感数值实施后游戏内目测定稿。

## Risks / Trade-offs

- **双通道推送重复**（心跳 + 事件级同 tick 两包）→ 心跳只认 `%20`，事件推送不改 `ageTicks` 相位；重复仅 ≤1Hz 全量快照（含 tip 构建），共鸣候选 uiInfo 有 O(候选) 成本但仅在 GUI 打开时发生，可接受。
- **与 `resonance-relay-render-perf`（进行中，0/20）文件重叠**：双方都改 `ResonanceRelayBehavior`——本变更动 `nextLinkState/tipOf/uiInfo 签名外区域`，对方动粒子/routeTick memo。约定：本变更先落（小、独立），render-perf 续做时以 tasks 1/4/5 改动后文件为基线；`uiInfo` 内 `sendRitualInfoToViewers` 删除若与对方 task 冲突，以本变更为权威。
- **tip 采样表驻留内存** → 键=BlockPos 值 5×long，千级核心也无感；BE 重载负差分自动重播种。
- **钳界后进度条变短（40→32px）** → 视觉权衡换整体不越框；如显局促可后续把图标行文本改 tip 化，本次不做。
- **FLOAT_Y 全局抬高影响非方块物品观感** → 1.34 仍在"悬浮于台面"语义带内，游戏内复核，必要时按 blockShaped 分档。

## Migration Plan

纯行为修正：无存档迁移（累计计数为运行时态）、无配置迁移（新键默认即旧等效值）。回滚 = revert 提交。

## Open Questions

（无——已确认八方归元 `BafangGuiyuanBehavior:430` 存在同型 1Hz 行为内推送，列入 D1 删除清单。）
