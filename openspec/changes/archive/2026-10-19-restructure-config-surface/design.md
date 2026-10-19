## Context

`gensokyou-common.toml` 是项目唯一的配置（`ModConfig.Type.COMMON`），605 键 / 101 KB / 2,352 行 / 815 处引用 / 122 个文件。它同时承载三种角色：

1. 战力数值（伤害、血量、属性、怪物预算、弹幕武器强度与消耗、符卡）——被 `docs/mob-design-guidelines.md` §3、`docs/weapon-design-guidelines.md` §7 等文档指定「一律走配置」。
2. 非战力经济（仪式产灵/缓存/受灵/消耗/产出、仓储、工具成本）。
3. 客户端表现（约 150 个 `fx*` 几何/颜色键）与 HUD。

三层需求冲突：战力数值需要「精调后锁死、自成体系」；经济需要「玩家可调」；表现只该由玩家本人决定。COMMON 的类型还带来一个既有缺陷——它不被服务端同步，客户端 `RitualTierComponent` 用本地值现算经济数值，dedicated server 上引导书会撒谎。

约束：这是 NeoForge 1.21.1 项目，改动规模大（800+ 引用点）但机械；项目已有多处「数值宿主类」先例（`balance/MonsterStatBudget`、`ritual/RitualScaling`、`ritual/behavior/*Scaling`、`item/weapon/SeiiNumbers`、`spirit/grace/GraceNumbers`、`spirit/SpellCardScaling`），迁移有既成模式。

## Goals / Non-Goals

**Goals:**

- 建立并落地三分法：战力数值=代码常量；非战力经济=SERVER 配置；表现偏好=CLIENT 配置。
- 把 605 键压到 SERVER ~140 + CLIENT ~5，删除 COMMON。
- 修掉「客户端展示值与服务器不符」的既有缺陷（SERVER 配置随登录同步）。
- 用文档（project.md + 新 doc）与技能（5 份 skill）改写规则，并用测试锁定，阻止回潮。
- 使 16 份既有 spec 的「数值 SHALL 为 config 项」与三分法一致。

**Non-Goals:**

- 不改任何战力数值本身（迁移只换载体，默认值不变）。
- 不做配置 GUI（玩家仍编辑 TOML）。
- 不引入 dev-only 热调覆盖层（待后续决定，见 Open Questions）。
- 不为经济数值做数据包（本变更只把它放 SERVER 配置；数据包路线后置）。

## Decisions

### D1 三分法判据与归属

**判据（唯一裁决口径）**：把某键翻倍，是否改变同一场战斗的结果？

- 会 → 战力常量。
- 不会、但改变资源累积/世界节奏 → SERVER 配置。
- 只改画面/HUD/手感 → CLIENT 配置。

**已裁决的灰区**（用户确认）：

| 灰区项 | 裁决 |
|---|---|
| 掉落概率（材料/信物/符卡/拉维坦） | 常量（不选即可调） |
| 装备技能（灵铁免耗、星银精准采集、额外矿概率、星银套装灵力伤害加成） | 常量 |
| 药水（回灵比例、彼岸花毒伤倍率） | 常量 |
| 仪式产出数量 | **可调**，但只暴露每阶倍率 `*_MULT*`；产出基线为常量 |
| 各阶标准灵力池表（`REIYOKU_TIER_MAX_SPIRIT`） | 常量（编码战力曲线） |

否决「默认落入 SERVER 配置」：存疑项必须先记录裁决，默认归常量，避免重新沦为垃圾桶。

### D2 两个配置文件取代 COMMON

| 文件 | 类型 | 内容 | 权威性 |
|---|---|---|---|
| `gensokyou-server.toml` | `ModConfig.Type.SERVER` | 非战力经济（~140 键） | 服务端权威，登录时同步给客户端 |
| `gensokyou-client.toml` | `ModConfig.Type.CLIENT` | 表现偏好（~5 键） | 玩家本地，各人独立 |

为何用 SERVER 而非继续 COMMON：COMMON 两端各读本地文件且不同步；SERVER 由服主定义、自动同步，正好修掉引导书缺陷，并且「玩家配置=服务端规则」的语义与用户诉求一致（单机时玩家即服主，照样可调）。

**约束**：SERVER 配置在客户端仅于登录同步后可用，故注册期/初始化期不得读取（核图标 requiredTier 定色等正好是战力常量，自动归位）。

### D3 常量宿主与迁移模式

战力数值按子系统落为常量类，复用既有模式：

- **本期单宿主**：全部战力数值落在 `balance/BalanceNumbers`（装箱常量），既有 `MonsterStatBudget`、`RitualScaling`、`GraceNumbers`、`SpellCardScaling`、`SeiiNumbers` 改为读它。按子系统再拆分成 `*Numbers` 是后续整理，不在本变更范围内（拆分不影响调用点语义）。
- 沿用 `SunakoScaling` 的**显式传参重载**约定：公式保留 `xxxOf(level, base, mult, ...)` 与 `xxxOf(level)` 两个入口，前者供单测在**不加载 ModConfig** 的前提下断言。
- `.get()` 调用点改为读常量；整数饱和乘、越界夹取、显式三档分派等既有逻辑 MUST 原样搬迁，MUST NOT 顺手「简化」（否则会触到文档记过的静默回落坑，如 `DEFAULT_CORE_CAPACITY=10000`）。

替代方案（否决）：数据包 JSON 承载经济（增加加载与双端同步管道，本期不引入）；保留 COMMON 只删键（换汤不换药，规则仍指向配置）。

### D4 刷新概率：数据包默认 + SERVER 倍率覆盖

数据包 `add_fairies.json`（`weight: 8` 等）保持为默认。SERVER 配置提供**倍率**（`1.0` = 采用数据包值）。

机制取舍：

- **(a) 生成事件概率过滤**（推荐）：在 `MobSpawnEvent`/`FinalizeSpawn` 阶段按 `min(1, 目标/默认)` 放行或取消。简单、不重建数据、近似，但只能表达「压制」（倍率 > 1 无法凭空提升既有权重，除非在事件里补生成）。
- **(b) 重建 `MobSpawnSettings`**：精确，但需在数据包加载后重写各 biome 的 spawner 表，侵入大、易与其它 mod 的 biome modifier 冲突。

决定采用 (a)，并把配置语义明确为**倍率上限**（>1 视为 1 并在注释说明），避免承诺做不到的提升语义。

### D5 客户端展示改用同步值

`client/book/RitualTierComponent`、GUI 信息行、tooltip 中「按 `GensokyouConfig` 现算」的路径，改为读取 SERVER 配置（同步后）或同一份常量。这样引导书在 dedicated server 上显示服务器实际值，修复既有 bug。渲染期读取是安全的（同步在登录时完成）。

### D6 特效收口为画质档

约 150 个 `fx*` 几何/颜色/层数键改为客户端常量；CLIENT 配置只暴露 `fxQuality`（off/low/medium/high）与 `fxDrawDistance`，由一个 `VisualPreferences` 门面把档位映射到密度/层数/绘制距离。逐效果几何不再可调。已确认：这些键的「调参不重编译」诉求让位于「玩家不需要逐个调雾带厚度」。

### D7 迁移与键漂移

- 旧 `gensokyou-common.toml` 不再被读取（类型变更 + 文件删除）。NeoForge 不会把值迁移到新文件；玩家自定义过的数值回落为默认/新默认——这是预期行为，需在 CHANGELOG 说明，并提示可删除旧文件。
- 旧文件里的**死键**（16 个从未被读取，如 `BOSS_SECONDS_T1/T3/T4/T5`、`WAND_MAX_DIMENSION`）随删除一并消失，不迁移。
- 本变更**不改默认数值**，故对「没改过配置」的玩家零可见变化；对改过的玩家，战力相关改动会被忽略（这正是目的）。
- 建议给 SERVER 配置的根加一段说明性 comment：本文件只含非战力数值，战力数值不可配置。

### D8 规范落点与防回潮

- `openspec/project.md` §4 改写为三分法；§3.5 / §5.4 / §9 改口；新增 `docs/config-guidelines.md`。
- 5 份 skill（`neoforge-1211-dev`、`ritual-code-dev`、`boss-dev-design`、`player-spellcard-design`；`ritual-design` 叙述同步）与 4 份 docs 的话术替换。
- 测试锁定：`ConfigSurfaceTest` 断言只注册 SERVER+CLIENT、两配置键集合 ⊆ 白名单、SERVER 不含战力键形态；新增/删除键必须更新白名单。
- **新增可调项需用户确认**（recorded in config-guidelines）：不设键数上限——集合规模由用户按内容推进决定；实现者 MUST NOT 自行新增可调项，必须向用户确认或由用户主动提出。规范只约束「准入判断」与「不得擅自添加」。

## Risks / Trade-offs

- **刷新覆盖机制表达力有限** → 明确定义为「倍率上限」并在注释写清；未来若要精确提升，再评估重建 `MobSpawnSettings`。
- **815 引用点重构面大** → 按子系统分批迁移（每批可独立编译与跑测），不一次性大改；先用 `ConfigSurfaceTest` + 编译器帮助定位遗漏。
- **搬家时误改数值逻辑（饱和乘/夹取/显式分派）** → 要求逐段 diff 对比默认值，保留显式传参重载，单测从断言 `getDefault()` 改为断言常量值。
- **SERVER 配置早于同步不可用** → 明确禁止注册期/初始化期读取；把这类消费者全部归为常量。
- **老玩家数值回落** → CHANGELOG 说明 + 保留「能力」（经济仍可在新文件里调），战力改动属预期失效。
- **16 份 spec 文本同步遗漏** → 本变更以 delta spec 覆盖，`openspec validate` 后逐条核对。

## Migration Plan

1. 规范先行：project.md + `docs/config-guidelines.md` + 新 spec `config-surface` + 5 份 skill / 4 份 docs 话术替换（本变更即可独立合入，先止血）。
2. 数值沉降：逐子系统把战力 `GensokyouConfig.X.get()` 改为常量（`*Numbers`/`*Scaling`），同步改单测。
3. 新配置面：新增 `GensokyouServerConfig`（经济）与 `GensokyouClientConfig`（表现 + 画质档），接入 `VisualPreferences`；客户端展示改读同步值。
4. 删 COMMON：移出注册、删除类、删除 `run/config/gensokyou-common.toml`（本地）。
5. 锁定与收尾：`ConfigSurfaceTest`、16 份 spec 文本同步、CHANGELOG。

回滚：本变更不涉及存档格式；回滚只需还原代码与重新引入 COMMON 注册，玩家侧无数据损坏。

## Open Questions

1. 不设键数上限：可调项集合的规模由用户按内容推进决定；规范只要求「新增须用户确认」。
2. 是否保留最终例外的少量真实玩家战力偏好（当前裁决为战力全锁）。
3. 是否在后续引入 dev-only 覆盖层（保留策划实机热调手感）；本期不做。