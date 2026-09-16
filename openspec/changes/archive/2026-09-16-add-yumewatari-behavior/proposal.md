# Proposal: add-yumewatari-behavior

## Why

仪式「梦渡之座」（`gensokyou:yumewatari_circle`）的结构 pattern（0~2 阶）与 JEI 名称已落盘，但 Java 侧零实现——它是一个"睡眠经济"仪式：玩家在仪式范围内的高床入睡并成功触发跳过夜晚时，按床上生物数一次性产出灵力。为仪式体系补上这条"以原版睡觉玩法换灵力"的产出通道，让守夜/牧场式村民睡眠产生收益。

## What Changes

- 新增 `YumewatariBehavior` 并注册到 `RitualBehaviors`（常量 `YUMEWATARI`）：被动型结算仪式，成型即生效，无需启动（pattern 不含 `toggleable`，不走 serverTick/enabled 链路）。
- 新增睡眠结算通道：监测 `ServerLevel` 时间被睡眠推进（夜晚跳过或雷雨白天睡，由 sleep 路径触发而非 `/time`）的当 tick，扫描维度内成型梦渡核心，按"核心等高平面上、结构 XZ 包围盒内、正在睡觉的玩家+村民所在合规床数"一次性产出 `床数 × 10000×4^阶级` 灵力。
- 灵力入账三分流：先填满仪式缓存（上限 `40000×4^阶级`，`getCapacity()` 分派新增 yumewatari 分支）→ 溢出部分不限速直注槽内灵力核心（复用 `SpiritCoreItem.receive`，仿 `refundCached` 先例）→ 核心也满则作废。
- 供灵端点：`spiritOutRatePerSecond` 恒 1,000,000/tick-秒（不随阶级），供万象共鸣路由抽取。
- `RitualCoreRegistry` 新增按图案过滤的维度级遍历查询（结算侧需要找到维度内全部成型梦渡核心，现有 API 只有锚点半径查询）。
- 新增配置基项（`GensokyouConfig`）：单生物产灵基准 10000、缓存上限基准 40000、供灵速率 1000000。
- GUI 信息行：梦渡核心 UI 展示当前合规床数/单晚预估产出等（短行 + tip 明细，遵守 InfoLine 宽度红线）；lang 键补齐（含 `tools/lang_audit.py` 回归）。

## Capabilities

### New Capabilities

- `yumewatari-ritual`: 梦渡之座行为——合规床判定（包围盒×等高×床两格在内）、睡眠生物统计（玩家+村民，结算 tick 快照）、跳夜结算与产灵公式、缓存/灵力核心分流与溢出作废、供灵速率声明、GUI 信息展示。

### Modified Capabilities

- `ritual-core-registry`: 新增"按图案遍历维度内成型核心"查询需求（结算钩子的候选发现入口；现有 XZ 半径查询无法覆盖全维度）。

## Impact

- 代码：`ritual/behavior/YumewatariBehavior.java`（新建）、`ritual/RitualBehaviors.java`、`ritual/RitualCoreRegistry.java`、`block/entity/RitualCoreBlockEntity.java`（容量分派）、`config/GensokyouConfig.java`、`event/`（新睡眠事件处理器，或并入 `ModBusEvents`）。
- 资源：`assets/gensokyou/lang/{zh_cn,en_us}.json` 新增 `gui.gensokyou.ritual.yumewatari.*`。
- 依赖：原版睡眠机制（`Player#sleep`→`MinecraftServer` 跳夜、`Villager#isSleeping`/床占位）、`BlockTags.BEDS`；NeoForge 无现成"跳夜完成"事件，检测方案见 design。
- 不动：pattern JSON（已定稿）、配方系统（梦渡无配方）、灵力核心物品本体。
