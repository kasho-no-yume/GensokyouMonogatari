---
name: ritual-code-dev
description: 仪式代码开发手册（Java 侧）。需要新增或修改任何仪式行为（RitualBehavior 子类）、注册到 RitualBehaviors、实现产灵/储灵/托管或路由端点、给仪式加 GUI 信息行、config 基项、lang 键或调试命令时必读——含三件套结构、钩子全表、灵力四件套与端点账本、"新增一个仪式"逐步骤 checklist、红线陷阱、调试与验证工具链、参考实现地图。与 ritual-design（只设计 pattern JSON、不读码）互补；NeoForge 通用坑另见 neoforge-1211-dev。
metadata:
  author: bitsson
  version: "1.0"
---

# 仪式代码开发（Java 侧）

## 0. 角色与边界（先读）

- 本 skill 管**代码侧**：行为、注册、灵力四件套、端点、UI 信息行、config、lang、调试。
- **pattern JSON 设计**（`data/gensokyou/rituals/*.json`）走 `ritual-design`（明确"只设计不读码"）。
- **NeoForge/1.21.1 通用坑**走 `neoforge-1211-dev`。
- 写码前先读契约 spec（`openspec/specs/`）：`ritual-lifecycle`、`ritual-core-interface`、`ritual-power-attributes`、`ritual-core-registry`、`ritual-gui-info-lines`，以及该仪式已有的能力 spec。
- **红线：所有可调数值进 `GensokyouConfig`（COMMON），禁止硬编码**；例外须 design.md 记录。

## 1. 一个仪式 = 三件套

```
  pattern JSON             Behavior                     config + lang
 data/gensokyou/rituals/  ritual/behavior/*.java       GensokyouConfig.java
 ┌────────────────┐       ┌────────────────────┐       ┌──────────────────┐
 │ id / anchorKey │       │ implements          │       │ *_BASE_RATE       │
 │ palette        │──────▶│   RitualBehavior    │◀─────▶│ *_BASE_OUT_RATE   │
 │ levels[{adds}] │patternId│  (+ SpiritBank?)   │       │ *_BASE_CAPACITY   │
 │ tiers[]        │       │ 覆写钩子             │       │ lang 信息行/由来诗 │
 │ toggleable     │       └────────────────────┘       └──────────────────┘
 └────────────────┘                 │
        │                           │
        └─ RitualMatcher.matchAt ───┘
           → RitualMatch(patternId, level, anchorPos, keyedPositions, ritualTier)
```

- **patternId → behavior 是注册表硬绑**（`ritual/RitualBehaviors.java`，静态块 `register(...)`）。
- pattern 存在但**未注册** = 能成型、能开界面、缓存回落到 `DEFAULT_CORE_CAPACITY=10000`、**零产出且不可被路由**（`spiritOutRatePerSecond` 默认 0）。仓库里 `haniyasu / kaya_no_hime / kukunochi / oyamatsumi` 就是这种"空壳"。
- `RitualMatch.level()` = 匹配到的层号（由 pattern `levels` 决定）；`ritualTier()` = 结构内仪式石最高品阶（视觉）。跨阶数值一律用 `match.level()`。

## 2. `RitualBehavior` 钩子全表（`ritual/RitualBehavior.java`）

| 钩子 | 何时调用 | 门控 | 用途 |
|---|---|---|---|
| `uiActions(viewer?)` | 组装快照 | — | 注入自定义按钮（id 必须 ≥ 10；0/1 为启停保留） |
| `uiInfo(viewer?)` | 快照推送 | — | 信息行；`defaultUiInfo` 给通用祭品/配方清单 |
| `onUiAction` | 点击注入的按钮/行 | — | 服务端权威执行，返回 FAIL=未处理 |
| `usesCoreSocket` | 菜单构建 | — | 默认 true；false=隐藏灵力核心槽（路由/托管型豁免） |
| `spiritInRatePerSecond` | 端点声明/路由 | — | 受灵汇上限/s，默认 0 |
| `spiritOutRatePerSecond` | 端点声明/路由 | — | 供灵源上限/s，默认 0（**0 就进不了候选**） |
| `serverTick` | 每 tick | **enabled 才跑** | 启停型运转逻辑（自行按 `ageTicks % 20` 控频） |
| `serverPassiveTick` | 每 tick | **无门控** | 成型即跑的被动（如梦渡注灵） |
| `onStart` | 点启动 | — | 前置校验/收费，FAIL 阻止置位 |
| `handlesStartViaUiAction` | 启停通道 | — | 会话型（造化/神恩）独占启停 |
| `onRecipeExecuted` | 启动配方执行后 | — | effect 解释权在行为侧 |
| `onRedstonePulse` | 红石上升沿 | — | 脉冲触发 |
| `onStructureLost` | 结构失效一次 | — | 清理内存态（ledger/meter/FX） |
| `onFormed` | 首次成型 | — | 当前空实现占位（未来大 tileblock 替换点） |
| `onUseItem` / `onUseEmptyHand` | 潜行右键核心 | — | 潜行保留的旧直连链路 |

`SpiritBank`（`ritual/behavior/SpiritBank.java`）：托管型仪式（八方归元）实现它——`stored/capacity/receive/extract` 四件套整体转发到"祭品台物品"而非核心自身。

## 3. 灵力四件套与端点（`block/entity/RitualCoreBlockEntity.java`）

```
 配方/激活费 ─────▶  receive / extract   (普通通道)
 (SpiritPowerHelper  getStored / getCapacity
  三段式：槽核→自身   │  命中 SpiritBank 行为时四件套整体转发
   →周围兜底)         ▼
                   storedSpiritPower (long)  ← 非托管核心的"缓存"
                   batteryStack (ItemStack)   ← 槽内灵力核心（独立于缓存！）

 万象共鸣路由 ────▶ extractRouted / receiveRouted
                   （先经 TickRateLedger 端点账本按 gameTime 幂等限速，再走普通通道）
```

- `getCapacity()` 是**按 patternId 一串 if 分派**：`RESONANCE=0`、`KAGUTSUICHI=base×4^L`、`YUMEWATARI=base×4^L`、`ZAOHUA/KAMI_NO_MEGUMI` 会话态、**兜底 `DEFAULT_CORE_CAPACITY=10000`**。新仪式必须在此加分支，否则不随阶。
- `getStored()` 对非托管核心**只返回缓存**（不含 `batteryStack`）。这点会决定"路由能看到什么"（见 §5）。
- 速率账本：端点自持、按 `gameTime/周期` 幂等锁存，调用方预算只是建议（`ritual-power-attributes`）。**内部产灵走普通 `receive`，不得走 `extractRouted/receiveRouted`**（否则被自身 inRate=0 误截）。
- 缓存的"发电机遇袭"入口：`core.receive(n)`（截到容量）/ `core.extract(n)`；槽核读改：`batteryStack()` / `setBatteryStack(...)`；"缓存→槽核"复用 `core.tickBatteryAutoFill()`（按核 `fillRatePerSecond` 每秒 carry 进位，即"缓存自然回流核心"）。
- 不限速直注槽核先例：`refundCached(...)`。

## 4. 新增一个仪式 —— 逐步骤 checklist

```
 1. pattern JSON           data/gensokyou/rituals/<name>.json      （ritual-design 负责）
 2. Behavior 类            ritual/behavior/<Name>Behavior.java
 3. 注册                   ritual/RitualBehaviors.java（+常量 +register）
 4. 缓存分派               RitualCoreBlockEntity.getCapacity()（仿 kagutsuchiCapacity）
 5. config 基项            config/GensokyouConfig.java（声明块 + defineInRange）
 6. tick 通道选型          启停型→serverTick + pattern "toggleable": true
                          被动型→serverPassiveTick + pattern 不写 toggleable
 7. 端点声明               如需路由：覆写 in/out（双发分道，禁止合并）
 8. lang                  zh_cn/en_us（信息行/由来诗/jei 名）
 9. lang 审计              python tools/lang_audit.py   ← 必须零缺失
10. 调试探针              /gs_debug 增子命令（机读单行便于外部 harness）
11. 验证                  gradlew compileJava → runServer 日志 → 实机
```

## 5. 红线 / 极易踩的坑

| 坑 | 事实 | 出处 |
|---|---|---|
| 启停按钮不出现 | 按钮显隐**唯一**由 pattern `toggleable` 决定；覆写 `serverTick` 却没写 `"toggleable": true` → 该钩子是死路 | neoforge skill / `ritual-core-interface` |
| 速率合并 | in/out 是**两条独立上限**，即便相等也 MUST 分道声明/求和/显示 | `ritual-power-attributes:7` |
| out 速率动态化 | 路由按**结算周期 memo** 端点速率并以 `> 0` 筛源（`ResonanceRelayBehavior.outRateOf/needyEndpoints`）→ 动态速率会致源闪断或夜间消失。供灵上限须静态 | 代码实证 |
| 路由看不见槽核 | 非托管核心 `getStored()` 只反映缓存；"产灵优先注槽核"会使缓存长期为 0 → 该发电机暂不可被路由，须等核饱和/拔核。设计分流时必须自觉此点 | 代码实证 |
| 信息行宽度 | 信息区实得 ~116px（带图标 ~142px）；多字段拼一行必爆。正解：可见行只放短标签，数字明细进 `InfoLine.tipped` 的 tooltip，大数 `InfoLine.compact` | `ritual-gui-info-lines` / neoforge skill |
| 整除截断丢量 | 小数速率用 ×1000 定点 carry（`rateCarry`/`fillCarry`），禁止直接整除归零 | `ritual-power-attributes` |
| 产灵白产 | `serverTick` 内若依赖激活/燃烧态，必须门控（`KagutsuchiFlameBehavior` 曾缺 `isBurning` 门控白产） | 代码注释 |
| 服务端持续粒子 | `level.sendParticles` 在服务端 = 逐追踪玩家网络包；持续表现必须客户端 BER（只同步最小渲染态） | neoforge skill |
| 数值硬编码 | 可调数值一律进 `GensokyouConfig`（COMMON） | neoforge skill |
| 注册表冻结 | 内建 worldgen 注册表（biome_source/density_function_type 等）mod 期不可写，只能数据包 | neoforge skill |
| lang 漏键 | `translatableWithFallback` 的回退裸路径 = 玩家看到没翻译的英文 | neoforge skill |
| 潜行让行 | 核心成型让位必须 `useItemOn` 与 `useWithoutItem` **两处都放行**，否则被 `openOrHint` 抢回开 GUI | neoforge skill |

## 6. 调试与验证工具链

| 手段 | 命令 / 位置 |
|---|---|
| 结构化调试 | `/gs_debug` 子命令：`spirit` / `kagutsuchi` / `bafang` / `yumewatari` / `settle` / `beds` / `temp` / `grace` …（`ritual/command/DebugCommands.java`） |
| 结构采集 | `/gs_ritual_capture <name> <r> <h>` |
| 自检 | `/gs_ritual_selftest` |
| pattern 离线校验 | `tools/validate_ritual_pattern.py`（`--test-out` 生成 e2e 数据包，测**成型/负查**，不测 behavior；见 `ritual-e2e-test-pack`） |
| 加载期验证 | `runServer --console=plain` → 看 `Done (` 与 `Errors in registry` 区块；详情常在 debug.log |
| 机读单行 | 行为导出 `debugSummary` 风格单行（如 `BafangGuiyuanBehavior.debugSummary`）供外部 harness 解析 |
| lang 审计 | `python tools/lang_audit.py`（退出码 0） |
| Java 符号核验 | 反编译源 jar：`C:\Users\etsuraku\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_*.jar`（`[IO.Compression.ZipFile]` 读条目） |

> 注意：行为逻辑**没有单测框架**；`ritual-e2e-test-pack` 只覆盖 pattern。新仪式请把可测内核做成**世界无关纯静态函数**（先例：`YumewatariBehavior` 的静态扫描/结算、`BafangGuiyuanBehavior.weightedSplit`），再用 `/gs_debug` 探针实机断言。

## 7. 参考实现地图（抄谁）

| 想做什么 | 抄这个 |
|---|---|
| 启停型 + 产灵 + 槽核注灵 + 定点进位 | `ritual/behavior/KagutsuchiFlameBehavior.java` |
| 被动型 + out 声明 + 溢出直注槽核 + 缓存回流 | `ritual/behavior/YumewatariBehavior.java` |
| 托管池 + 加权水位分配 + 实测吞吐 + 内存账本 | `ritual/behavior/BafangGuiyuanBehavior.java`（+ `SpiritBank`、`TickRateLedger`） |
| 路由 + 候选/链接 + 端点账本 | `ritual/behavior/ResonanceRelayBehavior.java` |
| 会话型（付费→演出）启停 | `ritual/behavior/ZaohuaCraftingBehavior` / `YaoyorozuGraceBehavior` |
| 时间驱动型产灵（昼夜发电机） | 见进行中变更 `add-daycycle-generator-rituals`（日轮天台/月影水镜） |

## 8. 现有仪式清单与状态

| patternId | 名称 | 行为 | 备注 |
|---|---|---|---|
| `resonance_relay` | 万象共鸣 | `ResonanceRelayBehavior` | 路由塔，缓存 0 |
| `barrier_break_circle` | 结界 | `BarrierBreakBehavior` | — |
| `kagutsuchi_flame_circle` | 加具土命之焰 | `KagutsuchiFlameBehavior` | 吞燃料产灵，tiers 0-3 |
| `bafang_guiyuan_circle` | 八方归元 | `BafangGuiyuanBehavior` | 托管池，tiers 2-5 |
| `zaohua_circle` | 造化 | `ZaohuaCraftingBehavior` | 会话型 |
| `kami_no_megumi_circle` | 八百万神恩 | `YaoyorozuGraceBehavior` | 会话型 |
| `yumewatari_circle` | 梦渡之座 | `YumewatariBehavior` | 跳夜产灵，tiers 0-2 |
| `nichirin_circle` | 日轮天台 | 待实现 | 昼夜发电机（变更 `add-daycycle-generator-rituals`） |
| `tsukikage_circle` | 月影水镜 | 待实现 | 昼夜发电机（同上） |
| `haniyasu` / `kaya_no_hime` / `kukunochi` / `oyamatsumi` | — | 无 | 空壳 pattern（能成型、无行为） |
