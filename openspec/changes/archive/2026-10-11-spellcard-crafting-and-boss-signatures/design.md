## Context

- 玩家符卡现为**创造独占**：3 张占位卡 + 6 张前期 BOSS 主题卡，均无配方、无掉落。
- `project.md` §3.3 已定：**已学形态唯一经委托习得，道具卡不可转已学**。故本变更只处理
  **道具形态（消耗品）**的获取，不碰技能槽/学卡。
- `broken_spell_card_star` 为 **BOSS 专属稀缺物、无合成路径**（`zaohua-crafting` spec）。
  `spellcard_star` = 8×碎星（造化，tier0，8000 sp），已被 `grace_advance_2`、召唤无面等消费，
  是中期稀缺瓶。
- BOSS 掉落体系（`project.md` §3.5）本就含「符卡（道具形态）」与「合成材料」，本变更与之对齐。
- 造化祭品台累积容量（4 重对称）：tier0=8 / 1=16 / 2=24 / 3=32 / 4=40 / 5=48。
- 露米娅无实体（现有 BOSS：BigFairy / Yamame / KitsuneBi / NomenMask / Flandre / Cirno）。

## Goals / Non-Goals

**Goals:**
- 全部 9 张玩家符卡获得生存获取路径（源初造化 + BOSS 低概率直掉）。
- 引入 3 件 BOSS 签名素材，作为主题卡合成料与未来价值锚。
- 造价按强度分层；签名素材具独立美术。

**Non-Goals:**
- 不改符卡效果/数值/品质模型；不做符卡升品。
- 不实现回收仪式与经济交易系统（只预留语义）。
- 不实现委托/学卡；不改 `SkillStateData` 槽位。
- 不给占位 3 卡绑 BOSS 身份。

## Decisions

### D1 独立 capability：`boss-signature-materials`

签名素材是 **BOSS 掉落物**，不是仪式资源料，也不属 `loot-currency-basics` 的通用货币。
故新开 capability 承载其定义、掉源、用途与价值锚定位，避免污染既有三张 spec。

### D2 主题信物目录 + 掉源（新增 3 + 复用 1）

三只已实现主题 BOSS 各有主题信物；**只新增 2 件**，八云蓝(伪) 复用**既有** `sukima_fragment`
（它已是 `hyakki_boss_kitsunebi` 召唤配方的前置，无需新造物品）。

| 注册 id | 中文 | 主 | 来源 |
|---|---|---|---|
| `fairy_nectar`（新） | 花精之滴 | 大妖精 BigFairy | 击杀保底 1 / kill |
| `earth_spider_silk`（新） | 土蜘蛛之丝 | 黑谷山女 Yamame | 击杀保底 1 / kill |
| `gensokyou:sukima_fragment`（既有，已入 `#boss_signature`） | 隙间碎片 | 八云蓝(伪) KitsuneBi | 击杀山女 / 蓝伪保底 1 / kill |
| `nightfall_core`（新） | 宵暗之核 | 露米娅 Rumia（**未实现**） | 无掉源；本变更已作 `demarcation` 配方料 |

`nightfall_core` 本变更即注册，且已作为 `demarcation` 的配方料；露米娅未实现故**无掉源**，
该卡在 Rumia 落地前不可合成（开发期接受）。Rumia 实现时给该实体声明信物归属即可接通。
（早前"隙间碎片复用不新增掉源"的取舍作废：蓝伪召唤配方本就不该吃隙间碎片，故隙间碎片直接
入 `#gensokyou:boss_signature` 标签、由蓝伪保底掉落，与山女并列。）

- 新签名素材掉源走**实体侧** `dropCustomDeathLoot`，与既有 `broken_spell_card_star` 并列
  （`AbstractTouhouBoss.dropStarFragments` 旁加 `dropSignature`）。
- 数量走 config（`SIGNATURE_DROP_COUNT`，默认 1）。命名 id 为草案，落地前可美术/命名一轮。
- *备选*：为蓝伪另造「式符残片」——否决，隙间碎片主题与调用关系都更贴合，且省一件物品。

### D3 新签名素材独立贴图（不染色）

用户明确要求：签名素材是重要物品，**不得用灰度底图 + 运行期 tint 偷懒**。
新增的 `fairy_nectar` / `earth_spider_silk` / `nightfall_core` 各走 `tools/gen_tex.py` 出**独立 PNG**（最终命名），
与 `project.md` §5.2「替换零成本」一致。（`sukima_fragment` 复用既有资产，不在此列。
§5 占位期约束仍适用于其他未完成资产。）

### D4 符卡配方三档分层

全部 `mode:"activation"` / `match:"max"` / `minTier:1`；分层靠**原料稀有度**而非结构阶
（避免把早期卡锁到二阶仪式结构之后）。

| 档 | 卡 | 原料（草案） | Σ |
|---|---|---|---|
| 低 | `spider_web`、`plague_repay` | 碎星×2 + 土蜘蛛之丝×1 + 主题料 + `talisman_paper`/`memory_fragment` | ≤ 11 |
| 中 | `healing_garden`、`flower_armor` | **符卡星×1** + 花精之滴×1 + 主题料 + 知识料 | ≤ 11 |
| 中 | `demarcation`（吃宵暗之核） | 符卡星×1 + 宵暗之核×1 + 主题料 + 知识料 | ≤ 10 |
| 高 | `fox_servant` | **符卡星×2** + **隙间碎片×1** + 主题料 + 知识料 | ≤ 14 |
| 高 | `musou_fuuin`（通用，无信物） | 符卡星×2 + 知识料 + 主题料 | ≤ 14 |
| 中 | `icicle_fall`（通用，无信物） | 符卡星×1 + 冰系主题料 + 知识料 | ≤ 10 |
| 低 | `light_reflect`（通用，无信物） | 碎星×2 + 光系主题料 + 知识料 | ≤ 8 |

- 所有配方 mod 料由 `碎星/符卡星`（+ 信物/主题 mod 料）满足。
- spCost 随档：低 12000 / 中 24000 / 高 48000（可配，落 JSON）。
- **占用竞争提示**：高/中档吃 `符卡星`，与 `grace_advance_2` 共享稀缺瓶——这是有意设计
  （消耗卡应当"贵"）。

### D5 占位 3 卡与 `demarcation`

占位卡（无 BOSS 归属）走通用配方、不含信物。`demarcation` 属露米娅主题，本变更即吃
「宵暗之核」（见 D2/D4）；该信物目前无掉源，故本卡在 Rumia 落地前不可合成（开发期接受）。
将来实现 Rumia 时只需给该实体声明信物归属即可打通。主题卡一律绑定其本命信物，占位卡走通用料。

### D6 BOSS 低概率直掉成品卡

BOSS 击杀 SHALL 以 config 概率（`BOSS_CARD_DROP_CHANCE`）直接掉落**自己的**成品道具卡，
作为运气捷径。与昂贵配方并存。

### D7 未来出口只预留

主题信物（新签名素材与复用的隙间碎片）的**回收仪式**（换高价值物）与**经济交易系统**为
后续独立变更。本变更只在 capability spec 中声明"主题信物 SHALL 可作为价值锚/通用产出被后续
系统消费"，不实现。

### D8 归属映射走默认方法（不破坏既有实体）

用 `TouhouBoss` 接口的**默认方法**（`signatureItem()` / `themedCard()` 返回 `Optional`/空）
声明归属：大妖精→`fairy_nectar`、山女→`earth_spider_silk`、蓝伪→`sukima_fragment`；
未覆写者不参与信物/成品卡掉落。遵循 `boss-tier` spec
"TouhouBoss 新增方法 SHALL 为带缺省实现、MUST NOT 抽象"。

## Risks / Trade-offs

- [消耗卡造价过重] → 分层：低档只用碎星；签名保底 1/kill 稳定供给；直掉成品卡作捷径；
  数值落 JSON + config，实测可调。
- [符卡星被进阶与卡配方双抢] → 有意收紧；如过紧，可把中档降到碎星、或提高 boss 星掉率。
- [Rumia 缺口] → `demarcation` 走通用配方，不阻塞；无前置依赖。
- [主题信物成为死重] → 保底掉 + 直掉卡消化；未来回收/交易兜底（用户已确认经济系统可抹平）。
- [品阶染色惯例被"独立贴图"打破] → 这是用户明确决策；仅在签名素材上例外，不扩散。

## Migration Plan

- 纯新增：数据配方 + 3 物品注册（`fairy_nectar` / `earth_spider_silk` / `nightfall_core`）+
  掉落挂点 + config 项 + 美术/lang（蓝伪复用既有隙间碎片；`nightfall_core` 无掉源）。
- 无存档结构变更。回滚：移除配方/物品/掉落挂点/config 即可。

## Open Questions

- 三档的确切原料与 spCost（倾向实测微调，先落草案值）。
- 签名素材掉率/数量是否随 BOSS 阶变化（当前统一 1）。
- `fox_servant` 等高档卡是否需 `minTier:2` 而非 1（视实机节奏）。
- 签名素材 id / 中文名的最终美术命名。
