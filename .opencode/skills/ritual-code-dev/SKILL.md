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
 8. lang                  zh_cn（中文优先；en_us 按需同步，允许滞后）
 9. lang 审计              python tools/lang_audit.py   ← 必须零缺失（zh 口径）
10. 指导书条目             见 §9：生成器命令 + 故事/引言 + 配方页取舍 + 排序/门槛
11. 调试探针              /gs_debug 增子命令（机读单行便于外部 harness）
12. 验证                  gradlew compileJava → runServer 日志 → 实机
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

> 全部 17 个已注册行为均**已实现**（`RitualBehaviors` 静态块）。「占位/未实现」= **无 pattern 数据文件**，不可被玩家搭建、也不补指导书章节。

| patternId | 名称 | 行为 | 备注 |
|---|---|---|---|
| `kagutsuchi_flame_circle` | 迦具土炎祭 | `KagutsuchiFlameBehavior` | 启停吞燃料产灵，tiers 0-3 |
| `yumewatari_circle` | 梦渡之座 | `YumewatariBehavior` | 跳夜产灵 + out 声明，tiers 0-2 |
| `haniyasu_circle` | 埴山姬神之壤 | `HaniyasuBehavior` | 工具献祭（铲），继承 `ToolSacrificeBehavior` |
| `kukunochi_circle` | 久久能智神庭 | `KukunochiBehavior` | 工具献祭（斧） |
| `kaya_no_hime_circle` | 草野姬神花亭 | `KayaNoHimeBehavior` | 工具献祭（锄） |
| `oyamatsumi_circle` | 大山祇神之座 | `OyamatsumiBehavior` | 工具献祭（镐） |
| `watatsumi_circle` | 绵津见神之藏 | `WatatsumiBehavior` | 钓鱼竿献祭（**刻意不继承** `ToolSacrificeBehavior`） |
| `shujou_yoroku_circle` | 众生余录 | `ShujouYorokuBehavior` | 献祭生灵之态 |
| `nichirin_circle` | 日轮天台 | `NichirinBehavior` | 昼夜发电机（继承 `DayCycleGeneratorBehavior`），tiers 0-1 |
| `tsukikage_circle` | 月影水镜 | `TsukikageBehavior` | 昼夜发电机，tiers 0-1 |
| `wujinzang_circle` | 无尽藏 | `WujinzangBehavior` | 托管 128 晶储物，tiers 0-5 |
| `zaohua_circle` | 源初造化之仪 | `ZaohuaCraftingBehavior` | 会话型，tiers 0-5（配方开放/极多） |
| `sair_energy_circle` | 赛尔能源 | `SairEnergyBehavior` | 创造调试无限供灵源，tier 0 |
| `kami_no_megumi_circle` | 八百万神恩 | `YaoyorozuGraceBehavior` | 会话型进阶，tiers 1-5 |
| `bafang_guiyuan_circle` | 八方归元之仪 | `BafangGuiyuanBehavior` | 托管池 + `SpiritBank`，tiers 2-5 |
| `resonance_relay` | 万象共鸣之仪 | `ResonanceRelayBehavior` | 路由塔，tiers 2-5，缓存 0 |
| ⚠️ `barrier_break_circle` | 结界破碎 | `BarrierBreakBehavior` | **占位**：有行为，但无 pattern JSON，不可搭建 |
| ⚠️ `summon_circle` | 召唤 | 仅 `RitualBehaviors.SUMMON` 常量 | **占位**：无行为、无 pattern |

## 9. 指导书补充规范（新增/修改仪式后必做）

仪式实现完成后，SHALL 同步补指导书条目（Patchouli）。规范如下。

### 9.1 条目形态

- 仪式分类下：**第一条 = 仪式入门**（`rituals_basics`，`sortnum: 0`），其后**每个可正常游玩的已实现仪式一条目**。
- 条目名复用 JEI 键 `jei.gensokyou.ritual.<path>`；`icon` 取一个代表性物品。
- **占位/未实现仪式（无 pattern 数据）MUST NOT 建条目**。
- **创造/调试类仪式（如 `sair_energy_circle` 赛尔能源）MUST NOT 建条目**（不属正常游玩内容）。
- 条目级门槛：**最低结构阶 ≥ 1 的仪式**条目挂该阶对应世界进度门槛（`"secret": true`）；最低阶 0 的条目常驻可见。逐阶结构/参数页各挂对应门槛。
- 条目 JSON **由 `tools/gen_ritual_book_entries.py` 生成**（MUST NOT 手改，重跑会覆盖）；分类内顺序由 `sortnum` 决定（入门 0，其后 1..N）。

### 9.2 文案

- 开头**先讲一个短故事**（东方/神话口吻），再接**简短引言**；话不必点破机制（例：八百万神恩直接说「强化仪式」即可，不展开数值）。
- 只维护 `zh_cn`（中文优先）；`en_us` 允许滞后，不强行补英文。
- 每仪式 **1 张** text 页：`gensokyou.book.entry.ritual.<path>.text`，故事与引言写在**同一页**。MUST NOT 一句一页（浪费页数）。
- **换行语法（重要）**：Patchouli 只认 `$(br)`（换行）、`$(br2)`/`$(p)`（段落空行）、`$(li)`（列表）；**MUST NOT 用 `\n`**（不被解析，会显示成方框/乱码）。内部跳转：`$(l:<entry_id>)文字$(/l)`。

### 9.2b 阶级参数与产物

- 参数 SHALL 显示在**该阶结构页之后**的 `gensokyou:ritual_tier_page` 上，由客户端组件 `RitualTierComponent` 按 `tier` 从 `GensokyouConfig` **现算具体数值**（不写公式）；MUST NOT 加「会话型／启停型」等标签。产灵类给产灵/输出速率；功能类给缓存/受灵/耗灵；特殊类给专有项（如共鸣塔的输入/输出连接数与半径）。阶级页只显示 `minTier == 该阶` 的配方；开放/极多配方的仪式由生成器写 `"show_recipes": false`，组件据此不渲染配方（先例：源初造化）。
- 生产/献祭类仪式的配方/掉落 SHALL 用 `gensokyou:loot_page`（`RitualLootComponent`）以**物品图标网格**呈现，顶部摆出**献祭工具**；4 个工具仪式（铲/斧/锄/镐）各按工具材质出 **6 页**（分常规/下界/末地池），绵津见按等级出 **3 页**（**所有产物混排单网格，不分池**）。概率由 `tools/gen_ritual_book_entries.py` 按 `ritual_loot` 权重归一算出后内联（口径与 JEI `RitualLootCardWrapper` 完全一致）。
- 关联物品（如众生典籍、仪式构建器）SHALL 用 `$(l:<entry_id>)…$(/l)` 链接到物品词条；词条不存在时先建**占位条目**（`category: gensokyou:items`）。

### 9.3 结构与参数（分阶）

- 批量生成：`python tools/gen_ritual_book_entries.py`（读 `rituals/*.json` + `ritual_recipes/`）。
- 单仪式：`python tools/gen_ritual_multiblock.py --ritual <json> --recipes-dir <dir> --entry-out <entry.json> --name-key jei.gensokyou.ritual.<path> --category gensokyou:rituals --icon <id> --sortnum <n> [--no-recipes] [--text-page k1 --text-page k2]`
- 逐阶产出 `patchouli:multiblock` 结构页 + `gensokyou:ritual_tier_page` 参数页，自动挂阶门槛。
- **阶门槛 = 世界进度**（`gen_ritual_multiblock.GATE_BY_LEVEL`）：1 阶←下界、2 阶←末地、3 阶←幻想乡、4/5 阶暂用 `guide/tier_4/5`（temperLevel 过渡）。
- 坑：Patchouli `DenseMultiblock` 保留 `'0'`（**唯一中心**）、`' '`（空气）、`'_'`（任意），且要求图案**恰有一个中心**。palette 里常有键 `"0"`（0 阶仪式石），生成器已自动把非单字符/保留字符的键重映射到安全字符；改生成器时 MUST 保持该逻辑，否则报 `A structure can't have two centers` / `no center`。

### 9.4 换行 / 分页纪律（防超框）

- 页高仅 156px、页宽 116px。自定义组件文本 SHALL 经 `Font.split(text, 116)` 自动换行（`RitualTierComponent` / `RitualPageComponent` 已内置 `drawWrapped`）。
- 分阶页**不放「搭建材料」段**（历史超框主因，见 change `complete-ritual-book-entries` D3）。
- Patchouli **无运行时自动分页**：长文（故事/引言）在生成期拆成多张 `patchouli:text` 页；单体自定义组件内容控制在可读行数内。
- 原则：宁可生成期拆页/删段，也不让单页塞满。`book.json` 可设 `"text_overflow_mode": "resize"|"truncate"|"overflow"` 兜底（缺省走 Patchouli 全局 RESIZE）。

### 9.5 配方页取舍

补配方页 ⟺ 有 `ritual_recipes` 数据 **且** 配方数 ≤ 12 **且** 作者未显式指认为开放式/极多配方；否则**不补**。

- 例：八百万神恩 10 条 → 补；源初造化（现 2 条，但显式指认为数百上千条开放式）→ 不补。
- **拿不准阈值、或某仪式是否算「极多/开放式」时，向用户询问。**

### 9.6 生成与验证流程（逐步骤）

```
1. python tools/gen_ritual_book_entries.py     # 批量重生成条目（勿手改条目 JSON）
2. lang/zh_cn.json                              # 条目名复用 jei 键；正文 .text；必要时物品占位词条
3. python tools/lang_audit.py                   # MUST 退出码 0（中文优先口径：en ⊆ zh）
4. gradlew compileJava                          # 改了 client/book 组件时
5. openspec validate <change> --strict
```

关键文件地图：

| 关注点 | 位置 |
|---|---|
| 条目 JSON | `assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/`（`ritual_<path>.json`、`rituals_basics.json`、物品占位词条） |
| 页面模板 | `.../en_us/templates/`：`ritual_page`（配方卡）、`ritual_tier_page`（阶级参数）、`loot_page`（产出网格） |
| 客户端组件 | `client/book/`：`RitualTierComponent`（阶级参数，按 config 现算）、`RitualPageComponent`（配方卡）、`RitualLootComponent`（献祭产出网格 + 工具）、`RitualPageProcessor`（变量透传） |
| 门槛 advancement | `data/gensokyou/advancement/guide/{nether,end,gensokyo}_unlock.json`、`tier_4/5.json`（过渡） |
| 结构/产出生成 | `tools/gen_ritual_multiblock.py`（结构 + gate 映射）、`tools/gen_ritual_book_entries.py`（批量条目 + 产出概率） |
| 数据源 | `data/gensokyou/rituals/*.json`、`ritual_recipes/*.json`、`ritual_loot/*.json`、`ritual_special/*.json` |
