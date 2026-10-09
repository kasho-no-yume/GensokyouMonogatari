## Context

现有四只「百鬼夜行」召唤 BOSS 中，除大妖精外都是没有正式模型的占位残影，靠一套自创的
「序·破·結 / 缺段」装置区分。黑谷山女（土蜘蛛）的 Bedrock 模型已就绪，可进现有 GeckoLib
管线（`BigFairyGeoModel` / `BigFairyGeoRenderer` 同款）。本设计解决两件事：

1. 把鬼蛛替换为黑谷山女（真角色 + 4 符卡）；
2. 撤销缺段机制，让召唤 BOSS 只靠**角色自身的签名弹幕主题**区分。

轨道层（`danmaku/track/`）已具备所需原语，**本变更不新增 Shape / Behaviour**：

| 原语 | 用途 |
|---|---|
| `RADIAL_BURST` / `FAN` / `RING` / `SHELL` / `CAGE` / `FALL_FROM_ABOVE` / `SCATTER_FALL` | 几何 |
| `Motion.burst`（需 `Formation.reference()`） | 射出→收回→崩解再炸 |
| `Motion.reclaim` | 停住→重瞄→射出 |
| `Motion.speedProfile`（四则运算，双端逐位一致） | 螺旋外扩 / 出→停→回→出 |
| `Formation.spin` / `Formation.reference` | 环自转 / 参考点 |
| `Track.phaseStep` | 逐波角度推进（「崩解方向」逐波旋转） |

## Goals / Non-Goals

**Goals:**
- 黑谷山女成为 T1 召唤 BOSS，接在大妖精之后，4 张签名符卡各是一个**不同的游玩意念**，
  而非「线更多」的换皮；弹幕可从原作的两套母题（網 / 瘴）考证而来。
- 彻底撤销缺段机制：spec、`TrackLint` 断言、测试一并清理，不留半套术语。
- 山女继承鬼蛛的**进度位**（L1 召唤、保底掉隙间碎片），使大妖精→L2 的进度链不断。

**Non-Goals:**
- 不实现「狐火→八云蓝残影」「傩→露米娅」的替换（后续独立变更）。
- 不新增 Shape / Behaviour / 弹种；不改轨道调度、命中、同步管线。
- 不改 `HyakkiYagyoBehavior` 的会话语义。

## Decisions

### D1 · 山女是普通 BOSS，缺段退场

山女是土蜘蛛妖怪本人，不是「缺了一段的符卡成了精」；她原作的核心招（瞄准丝、螺旋、毒雨）
恰是「破」那一半。硬套缺破会砍掉她一半机制。故：

- 删除 `remnant-touhou-bosses` 的「残影的身份与缺段约束」整条 requirement；
- 弃用 `TrackLint.hasNoAimedTrack` / `TrackLint.allTracksEndless`，并删除
  `BossCardLintTest` 中对应断言的测试；
- `Track.terminates` / `.endless()` 是**通用能力**，保留；狐火、傩不再被**强制**，
  但傩现有 `.endless()` 调用可原样保留（那是风格选择，不再是契约）。

*备选（未采纳）*：把缺破转移给别的 BOSS——会再造一个「为机制而生」的怪，正是要消除的东西。

### D2 · 新 id `yamame`，删除 `kuzumono`

`ModEntityTypes.KUZUMONO` 删除，新增 `YAMAME`（id `yamame`）。实体类
`KuzumonoEntity` → `YamameEntity`。不保留 `kuzumono` 别名：旧存档中已降临的鬼蛛实体失效，
这是用户确认的取舍。

### D3 · 四张签名符卡

**红线：每一发都得是朝着玩家去的。** 只用「朝着玩家 / 绕着玩家 / 压在玩家脚下」的几何。
MUST NOT 用往 BOSS 侧边放的 `RING` / `RADIAL_BURST` / `SHELL` / `CAGE`（`SELF_AXIS`）——
那些是从 BOSS 身上朝侧面发出、垂直于「BOSS→玩家」连线的环，**打不到人、也不逼走位**，
是纯粹的花瓶。（本设计第一版正是栽在这里，见下方「修订记录」。）

允许的几何（全部 `AIMED` 或玩家锚定）：

| 原语 | 威胁方式 |
|---|---|
| `FAN` / `AIMED_SINGLE`（AIMED） | 朝玩家扇形直射 |
| `AROUND_TARGET`（AIMED） | 绕玩家生成、从四面八方朝玩家合拢 |
| `LATTICE`（AIMED，激光） | 绕玩家的凌乱激光网，`aimBias` 比例直瞄 |
| `DISC_RING` + `RECLAIM`（AIMED） | 在 BOSS 身后立墙、悬停后整面朝玩家压来 |
| `PILLAR_UP` + `PLAYER_GROUND`（激光） | 从玩家脚下炸起的光柱 |

调色盘 `YAMAME_PALETTE`（容量 6 ≥ 最大并发轨道 3）：

```
0xE0A24B 金     蛛丝       0xC8383E 赤   瘴（奇波）
0xD9683A 陶土   地蜘蛛     0x8E5FD8 紫   瘴（偶波）
0xB8D24A 病黄绿 毒/热病    0x5FBF7A 绿   标记
```

**卡 1 · 罠符「キャプチャーウェブ」 Capture Web**（100% 起，循环 160t）
意念：蛛丝朝着你收拢——瞄准扇直取站位 + 绕你合拢的一圈丝。站在原点必死，得踩着缝走。

| 轨 | 色 | 几何 | 行为 | 周期 |
|---|---|---|---|---|
| 張網 | 金 | `AROUND_TARGET` count 18, radius 7, spread 14°, **AIMED** | 绕玩家生成、朝玩家合拢 | `repeatEvery(20)` |
| 追い糸 | 陶土 | `FAN` count 7, spread 22°, **AIMED** | 朝玩家扇形直射 | `repeatEvery(20)` |

**卡 2 · 瘴符「フィルドミアズマ」 Filled Miasma**（75% 起，循环 240t）
意念：瘴气充满你所在的洞窟——绕你自转收拢的涡旋 + 一圈凌乱激光网（部分直瞄）。

| 轨 | 色 | 几何 | 行为 | 周期 |
|---|---|---|---|---|
| 瘴気の渦 | 赤 | `AROUND_TARGET` count 20, radius 7, spread 16°, **AIMED** | 绕玩家旋转收拢 | `repeatEvery(16)`, `phaseStep(12)` |
| 瘴気の網 | 病绿 | `LATTICE` 激光 count 14, radius 9, spread 40°, aimBias 0.4, **AIMED** | 绕玩家激光网，部分直瞄 | `repeatEvery(46)` |

**卡 3 · 蜘蛛「石窟の蜘蛛の巣」 Cave Spider's Nest**（50% 起，循环 220t）
意念：蜘蛛收网——身后立墙悬停后朝你压来 + 脚下炸起垂丝光柱 + 一圈络丝持续收拢。

| 轨 | 色 | 几何 | 行为 | 周期 |
|---|---|---|---|---|
| 結界網 | 陶土 | `DISC_RING` count 30, radius 5, offsetForward -8, **AIMED** | `Motion.reclaim(停 46t → 朝玩家射出)` | `repeatEvery(50)` |
| 垂れ糸 | 金 | `PILLAR_UP` 激光 count 2, **`PLAYER_GROUND`** | 玩家脚下光柱（预警 1.6s / 持续 3s） | `repeatEvery(44)` |
| 絡み糸 | 紫 | `AROUND_TARGET` count 16, radius 6, spread 16°, **AIMED** | 绕玩家合拢 | `repeatEvery(22)`, `phaseStep(45)` |

**卡 4 · 瘴気「原因不明の熱病」 Unexplained Fever**（25% 起，循环 240t，`damageScale ↑`）
意念：高热之涡——更快更密的收拢涡旋 + 更密激光网 + 脚下毒柱连发。

| 轨 | 色 | 几何 | 行为 | 周期 |
|---|---|---|---|---|
| 熱病 | 赤 | `AROUND_TARGET` count 20, radius 8, spread 14°, **AIMED** | 绕玩家快速收拢 | `repeatEvery(16)`, `phaseStep(14)`, `damageScale 1.3` |
| 瘴気の網 | 紫 | `LATTICE` 激光 count 18, radius 10, spread 45°, aimBias 0.45, **AIMED** | 更密激光网 | `repeatEvery(38)`, `damageScale 1.3` |
| 毒雨 | 病绿 | `PILLAR_UP` 激光 count 3, **`PLAYER_GROUND`** | 脚下毒柱连发 | `repeatEvery(30)`, `damageScale 1.35` |

**修订记录（v1 → v2）**：v1 用了 `RING`/`RADIAL_BURST`/`SHELL`/`CAGE` 且全是 `SELF_AXIS`，
读作「BOSS 侧边放环」，打不到人也逼不动走位。v2 全线换成上表允许的几何。

**lint / 数值**：所有轨色在色盘内、每卡 ≤3 轨且视觉独占；`LATTICE` / `PILLAR_UP` 是静止射线，
其 `Shape.Params#speed` MUST 填 `0`（否则密度估值把它当飞弹，实测虚高）；稳态并发实测
**约 45~48 / 玩家**（`shippedCardsHaveDensityHeadroom` 上限 60），与大妖精同量级且全部瞄人。

### D4 · 默认攻击（不进 BossCards）

一条常驻底噪：每 ~40t 朝最近玩家吐一小组蛛丝 `FAN` count 5 / spread 40° / speed 0.35（金），
频率与参数从 config 读，由 `YamameEntity` 的第二发射器实现，与任意符卡叠加。`FAN` 的方向
由「BOSS→目标」给出，故同样是朝着玩家去的。

### D5 · 掉落与召唤配方

- **掉落**：`starDropCount = 3`（碎符卡星）+ 保底 `SUKIMA_FRAGMENT` ×1 + 少量 `YEN`——
  与鬼蛛一致，保住 L2 祭坛与 T2 材料带的前置。
- **配方**（占位，`hyakki_boss_yamame`，L1，Σcount=4 ≤ 台数 4）：

```jsonc
{ "item": "gensokyou:broken_spell_card_star", "count": 1 },  // 钥匙：大妖精掉
{ "item": "gensokyou:ritual_stone_1",         "count": 1 },
{ "item": "gensokyou:spirit_iron",            "count": 1 },
{ "item": "gensokyou:refined_cinnabar",       "count": 1 }
```

钥匙沿用进度链（大妖精 → 第二只 T1），故与他配方互斥（`SummonBossRecipeCapacityTest` 覆盖）。

### D6 · 渲染走 GeckoLib，不复用残影通路

山女有正式模型，`RemnantBossRenderer` 移除 kuzumono 绑定；新增
`YamameGeoModel` + `YamameGeoRenderer`，资源来自 `F:/blockbench/kurodani/`
（`geo/entity/kurodani.geo.json`、`animations/entity/kurodani.animation.json`、
`textures/entity/kurodani.png`）。碰撞箱与缩放使模型总高与 `sized()` 对齐。

### D7 · 数值（T1，秒带目标）

`YAMAME_BOSS_SECONDS = 200`、`YAMAME_BOSS_HITS = 8`（原鬼蛛 190/8；4 卡更长，故略增秒数）。
秒带 120~900 内。**这是目标值不是保证**：必须 `/gs_boss spawn yamame <秒> <挨弹>` 实测，
按实测把秒数钉回 config。

## Risks / Trade-offs

- **[删除 kuzumono 破坏旧存档]** → 经用户确认的取舍；迁移节记录。若需兼容，可后续补一个
  从 `kuzumono` 映射到 `yamame` 的加载钩子（本变更不做）。
- **[kurodani.png 仅约 1.5 KB]** → 接入前确认是完整 128×128 贴图而非占位色块；若为占位，
  按 `gen-textures` 手册重绘。
- **[kurodani.geo.json 约 407 KB]** → 确认 bone/cube 数与渲染开销；若过重，评估简化或 LOD。
- **[撤销缺段动摇既有 spec 语言]** → 一次性清干净（含注释与断言），避免「半套术语」再绊人。
- **[秒数靠猜]** → 明确标为待实测，不写死；见 D7。

## Migration Plan

1. 规格先行：改 `remnant-touhou-bosses`、`boss-summon-effect`，新增 `kurodani-yamame`；
2. 引擎侧撤销缺段（`TrackLint` 方法 + 测试）——先做，避免山女的瞄准轨被误拦；
3. 资源搬运（geo/anim/tex）+ 渲染接入；
4. 实体与注册替换（`KuzumonoEntity` → `YamameEntity`、`ModEntityTypes`、`SummonBossEffects`、
   config、`DanmakuPreview`）；
5. 符卡表与调色盘 + 配方/tag/lang；
6. `gradlew build` → `lang_audit` → `openspec validate --strict`；
7. 实机：`/gs_boss spawn yamame` 实测四卡可读性与秒数，回写 config。

**回滚**：本变更为内容替换，回滚即还原上述文件（`kuzumono` 与其符卡表在 git 历史中）。

## Open Questions

- `kurodani.png` / `kurodani.geo.json` 的质量与开销是否达标？（见 Risks）
- 卡 4 的「毒雨」是否与卡 3 的「垂れ糸」在观感上重复？实测后或需二选一或加视觉区分。
- 狐火 / 傩 现有 `.endless()` 调用是否保留？（本设计倾向保留，仅去其契约地位）
- 山女的默认攻击颜色是否与卡 1 的「張網」金冲突？（预留可调）
