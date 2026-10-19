## Why

`gensokyou-common.toml`（COMMON，605 键 / 101 KB / 2,352 行 / 815 处代码引用）同时扮演三种互不相干的角色：玩家可调的玩法经济、精调后的战力数值、以及客户端表现参数。后果有三：

1. **战力可被改坏**。伤害、BOSS 血量、怪物预算、弹幕武器消耗、玩家属性表全部可被任意 TOML 编辑。而这些数值是「自成体系、按本 mod 武器精调」的，不应允许调整（用户决策）。
2. **客户端数字与服务端不符**。COMMON 不被同步，`client/book/RitualTierComponent` 等客户端组件直接读本地 config 现算经济数值并在引导书里展示；dedicated server 上引导书会显示与服务器实际规则不同的数字。
3. **默认入口就是「往配置里塞」**。`openspec/project.md` §4 / §3.5 / §9、`neoforge-1211-dev`、`ritual-code-dev`、`boss-dev-design`、`player-spellcard-design` 五份 skill，以及 `docs/mob-design-guidelines.md` §3、`weapon-design-guidelines.md` §7 都写着「数值一律进 `GensokyouConfig`（COMMON）」。规则本身在制造臃肿。

顺带暴露的既有缺陷：16 个键在代码中从未被读取（如 `BOSS_SECONDS_T1/T3/T4/T5`、`MONSTER_SPAWN_ROLL`、`WAND_MAX_DIMENSION`）；BOSS 血量存在两套并行的口径（通用 `MONSTER_BOSS_HP_MIN/MAX` 与每 BOSS `*BossSeconds`）。

## What Changes

### 1. 建立三分法配置面（本变更的核心）

任何数值按「把该键翻倍，是否改变同一场战斗的结果？」归入三个去向之一：

| 桶 | 去向 | 内容 |
|---|---|---|
| 战力数值 | **代码常量**，MUST NOT 配置化 | 伤害、血量、属性、怪物/ BOSS 预算、弹幕武器强度与**消耗**、符卡数值、掉落概率、装备强化、药水、弹幕内部容差 |
| 玩法经济与世界节奏 | **SERVER 配置**（服务端权威 + 登录同步） | 仪式产能/容量/受灵/**消耗**/冷却/产出、仓储上限、整地/引爆器成本、刷新概率覆盖 |
| 表现偏好 | **CLIENT 配置** | 画质档、绘制距离、镜头抖动、预警符、HUD 血条、工具默认值 |

**BREAKING**：删除 COMMON 注册与 `gensokyou-common.toml`；已有的 605 键中约 465 个不再可配置（改为常量），约 138 个迁入 SERVER 配置，约 5 个迁入 CLIENT 配置。

### 2. 新增 `gensokyou-server.toml`（SERVER，约 138 键）

承载非战力可调项：`barrier`、`ritual` 经济、`shujou`、`houjounoTeihou`、`bousen`、`sunako`、`omoikane`、`tsukumogami`、`spiritSoil`、`landscaping`、`spiritBomb`、`chinkon`、`seii`、`reiyoku`、`wujinzang`、`crystal`、`summon`，外加小妖精等刷新概率的**倍率覆盖**。

- 仪式**产出数量**只保留每阶倍率 `*_MULT*`，产出基线固定为常量（用户决策）；产能/容量/受灵/消耗的 `BASE_*` 与 `*_MULT*` 均保留（用户点名的「基础消耗与基础缓存」）。
- 仪式专属 `fx*` 键全部离开配置，改为常量，由 CLIENT 画质档统一收口。

### 3. 新增 `gensokyou-client.toml`（CLIENT，约 5 键）

`fxQuality`（off/low/medium/high）、`fxDrawDistance`、`cameraShake`（0 可关，无障碍）、`bossBarWidth`、`builderOutlineSeconds`。**用画质档把约 150 个 `fx*` 键收成一个入口**（画质档到常量的映射为本变更后续接线项）。

> 注：整地/引爆器默认尺寸与引信、弹幕预警符在服务端也被读取（方块实体/轨道），故归 SERVER 而非 CLIENT。

### 4. 战力数值迁为代码常量（约 470 键离开配置）

沿既有宿主模式（`balance/MonsterStatBudget`、`ritual/RitualScaling`、`ritual/behavior/*Scaling`、`item/weapon/SeiiNumbers`、`spirit/grace/GraceNumbers`、`spirit/SpellCardScaling`）落为常量，`.get()` 调用点改为直接读常量；`testHarness` 迁为 dev-only 常量。

### 5. 刷新概率：数据包默认 + 配置覆盖

`data/gensokyou/neoforge/biome_modifier/add_fairies.json`（`weight: 8`）保持为默认；SERVER 配置提供**倍率**覆盖（配置语义为相对数据包权重的倍率，而非绝对值），由生成事件按概率放行/取消实现。

### 6. 规范落地（防止回潮）

- `openspec/project.md` §4 改写为三分法，§3.5 / §5.4 / §9 一并改口；新增 `docs/config-guidelines.md`（判据 + 决策树 + 三桶 + 灰区登记 + 「新增可调项」checklist）。
- 5 份 skill（`neoforge-1211-dev`、`ritual-code-dev`、`boss-dev-design`、`player-spellcard-design`；另 `ritual-design` 只涉及叙述同步）与 4 份文档（`mob-design-guidelines`、`weapon-design-guidelines`、`spell-card-development-guide`、`new-ritual-checklist`）中「可调数值一律进 `GensokyouConfig`（COMMON）」的话术全部替换。
- 可选加固（本变更纳入）：`ConfigSurfaceTest` 断言只注册 SERVER + CLIENT、键集合 ⊆ 白名单、SERVER 配置不含战力键；并立规则「新增可调项 MUST 先经用户确认或由用户主动提出」（不设键数上限）。

## Capabilities

### New Capabilities

- `config-surface`: 配置面的三分法定义与验收规则——什么可以进配置、进哪个配置、禁区（战力数值）、默认值与命名、新增项须用户确认、防回潮测试。

### Modified Capabilities

以下现有 capability 的要求文本明写了「数值 SHALL 为 `GensokyouConfig`（COMMON）项」，与三分法冲突，需要 delta：

- `reiyoku-ritual`: 「容量分派与配置基项」与「运行态特效」中的 config 归属改写（经济 → SERVER，灵气/水体表现参数 → 常量 + CLIENT 画质档）
- `bousen-lantern-ritual`: 「分阶灵力三件套」驱动来源由 COMMON 列表 → SERVER 配置
- `superhuman-temper`: 「数值全部可配置」改为「阶级属性表/飞行费率=战力常量；演出时长/受灵速率=可调项」
- `ritual-runtime-fx`: 「特效参数配置化与资产管道」改为常量 + 画质档收口
- `sukima-portal-rendering`: 特效尺寸/粒子/时序由 config → 常量（画质档）
- `omoikane-enchant-ritual`: 「成型与容量分派」「指导书条目与本地化」的 config 归属改写
- `watatsumi-fishing-ritual`: 「专属配置旋钮」改为 SERVER 配置，产出只留倍率
- `seii-reroll-ritual`: 「灵力花费与充能阶位」改为 SERVER 配置
- `sunako-brew-ritual`: 「成型与容量分派」改为 SERVER 配置
- `shujou-yoroku-ritual`: 「专属配置旋钮」改为 SERVER 配置，产物倍率保留
- `kagutsuchi-flame-ritual`: 「可燃物选取与点火即吞」的黑名单归属与「燃烧批次与产出速率」的速率归属
- `danmaku-weapon`: 「range_pct 词条消费」的索敌半径由 config → 常量
- `chinkon-hanshou-ritual`: 「专属配置旋钮」改为 SERVER 配置
- `sair-energy-ritual`: 「容量分派与配置基项」「固定供灵端点」改为常量
- `ritual-builder-conflict-feedback`: 「线框限时且可刷新」的持续时长由 config → CLIENT 设置

> 已核查、**不需改动**：`resonance-relay-ritual`「旧两点中继拆除」仅描述拆除旧 `RELAY_*` 配置项，与三分法不冲突。

## Impact

- **配置类**：`GensokyouConfig`（COMMON）删除；新增 `GensokyouServerConfig` + `GensokyouClientConfig`；`Gensokyou.java` 注册行改写。
- **代码**：`GensokyouConfig.*` 引用 815 处 / 122 文件，按子系统替换为常量；`client/book/RitualTierComponent`（75 处）与 `client/renderer/RitualCoreRenderer`（153 处）为主要客户端改动面。
- **文档/规范**：`openspec/project.md`、`docs/config-guidelines.md`（新）、4 份 design doc、5 份 skill、16 份 spec。
- **存档**：无存档格式变更；旧 `gensokyou-common.toml` 的键不再被读取，玩家自定义值回落为默认（预期行为，需在 CHANGELOG 说明）。
- **风险**：刷新概率覆盖机制（生物权重在数据包加载期烘焙，运行期无可变权重事件，需用生成事件近似或重建 `MobSpawnSettings`）；SERVER 配置在客户端的可用时机（仅登录同步后可用——注册期/初始化期不得依赖，恰好这些都是战力常量）。
