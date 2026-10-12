## Why

「狐火『無序』」是**八云蓝的占位残影**：实体 id 叫 `kitsunebi`、用 Alex 人形贴图渲染、符卡表
还是早已作废的「序·破·結 / 缺段」装置留下的产物（三张卡、主题「起手即峰值、没有预备拍」）。
它由百鬼夜行 **L2**（8 个祭品台）召唤，信物是隙间碎片。

现在八云蓝的**正式 Blockbench 模型已到位**（166 方块 / 19 骨骼 / 九条独立尾巴 / 256×256 贴图），
角色定位也需重定：她是妖妖梦 **EX 面 BOSS / PH 面道中 BOSS**，应是 T2 层级的正主。

同时经济上有一处错位：隙间碎片目前既是 **T1 山女**的保底掉落，又是**「解锁 T2 材料带与 L2 祭坛的
唯一前置」**，还是 **L2「蓝」召唤配方**的钥匙——于是「要召蓝先要隙间碎片、要隙间碎片先要召蓝」
构成潜在环路，且违背「2 阶道具不该依赖隙间碎片、隙间碎片应是 2→3 门槛」的定位。

本变更把这只残影升级为**正式的 T2 正主八云蓝**：换模型、重写为 8 张考据符卡、确定其身份与数值，
并重整隙间碎片的经济定位。

## What Changes

- **身份与命名**：`gensokyou:kitsunebi` → `gensokyou:yakumo_ran`（类 `KitsuneBiEntity` →
  `YakumoRanEntity`）。中英 `entity` / `jei` / `spellcard` / `bosses.json` / 配方 effect id 同步改名。
  占位主题「狐火『無序』/ 缺段」整体作废。
- **定位与数值**：`bossTier = 2`、`referenceTier = 2`；秒数取 T2 带**偏上**（依赖更早的
  change `boss-stats-deterministic` 落地后才能精确设定为确定值）；挨弹数偏高。符卡 **3 → 8** 张。
- **模型渲染**：接入 GeckoLib（`geo/animations/textures/entity/yakumo_ran.*`，照抄山女通路），
  **退出** `RemnantBossRenderer`；残影外观 = **暗化贴图 + 狐火/符纸自发光**；动画首版只用 `idle`
  兼用；按模型尺寸重设 hitbox 与视锥参数。
- **8 张考据符卡**（母题见 design.md）：仙狐思念 / 前鬼后鬼的守护 / 狐狸妖怪激光 / 四面楚歌 /
  十二神将之宴 / 片面接触 / 式神大星 / 饭纲权现降临。全部用 3D 合法几何、过 R1/R2/R3，
  **MUST NOT** 复用「朝没人的地方放烟花」的自轴环形花瓶。
- **激光运动的轨道层接线**（引擎顺带项）：激光**实体层**已支持绕枢轴旋转/平移
  （见 capability `danmaku-laser`），但**轨道层尚未把姿态参数喂给它**（`Projectile` 无旋转字段、
  `DanmakuEmitter` 对激光不施加行为）。本变更 SHALL 让**卡表可直接声明激光旋转/扫射**，
  供卡 3「狐狸妖怪レーザー」与卡 4「四面楚歌」使用。
- **默认攻击**：狐火单/三连（独立于 `BossCards` 的常驻底噪，config 驱动）。
- **掉落与经济重整**：
  - 蓝的**主题信物 = 隙间碎片**（击杀保底掉落，复用既有物）；
  - **黑谷山女删除隙间碎片硬掉**（其主题信物仍为土蜘蛛之丝）；
  - 百鬼夜行 **L2「蓝」的召唤钥匙 `sukima_fragment` → `fairy_nectar`**（花精之滴：1 阶可达、
    与其余百鬼配方互斥）；
  - **隙间碎片从 T2 合成料中抽出**：`zaohua_spirit_core_2`（灵力核心 2 阶）与
    `kami_no_megumi` 的 `grace_advance_2` / `grace_refine_2` **移除**隙间碎片；
  - **隙间碎片重新定位为「2→3 门槛」**：由 T2 正主（蓝）产出，服务于 T3 层级的门控。当前
    无实际 T3 消费者（同 `nightfall_core` 的处境），SHALL 在规格中登记为**预留门槛**；
  - `fox_servant`（蓝的主题卡）**保留**隙间碎片——主题卡使用主题信物，属"主题卡破例"例外。

## Capabilities

### New Capabilities

- `yakumo-ran`: 八云蓝正主的身份与命名、GeckoLib 残影渲染、8 张符卡的结构与母题、默认攻击、
  T2 数值定位与掉落。

### Modified Capabilities

- `danmaku-track-composition`: 新增「激光运动的轨道层表达」——卡表 SHALL 能声明激光的绕枢轴旋转/平移并传给激光实体。
- `remnant-touhou-bosses`: 「BOSS 符卡即阶段」的名单与符卡数、「BOSS 数值以秒带与挨弹带约束」
  的数值表、「召唤 BOSS 的身份与名单」、以及「残影占位渲染」由四只对应的描述更新为蓝的正式化。
- `boss-signature-materials`: 「八云蓝(伪)」更正为「八云蓝」；隙间碎片不再「与山女并列」，
  改由蓝唯一产出并登记为**预留 T3 门槛**。
- `boss-summon-effect`: 「死亡结算」场景删除「黑谷山女额外保底掉隙间碎片」。
- `kurodani-yamame`: 「掉落与进度位」由「保底掉隙间碎片」改为「不再掉隙间碎片；隙间碎片的
  门控角色移交给 T2 正主」。

## Impact

- 源码：`ModEntityTypes`、`KitsuneBiEntity`→`YakumoRanEntity`、`RemnantBossRenderer`、
  `danmaku/track/Projectile` 与 `danmaku/DanmakuEmitter`（激光姿态接线）、
  `GensokyouClient`（GeckoLib 注册）、`BossCards`（`kitsuneBi()`→`yakumoRan()` 8 张 +
  `KITSUNEBI_PALETTE`→`RAN_PALETTE`）、`GensokyouConfig`（蓝的秒数/挨弹数项）、`ModBusEvents`、
  `GensokyouTextures`、`SummonBossEffects`（effect id 改名）、`YamameEntity`（删隙间碎片硬掉）。
- 资源：`geo/animations/textures/entity/yakumo_ran.*`（由 `C:\Users\spdimdriver\Documents\ran`
  转换并暗化）、`lang` zh_cn/en_us、`bosses.json`。
- 数据：`hyakki_yagyo_circle.json`（L2 钥匙换料）、`zaohua_circle.json`（`spirit_core_2`、
  `fox_servant`）、`kami_no_megumi_circle.json`（grace 1/2 阶）。
- 测试：`BossCardLintTest`（`spellCardCounts` 3→8、遍历改名）、`SpellCardThresholdTest`、
  `TrackRepeatTimingTest`、`SummonBossRecipeCapacityTest`（L2 换料后重验互斥与 Σcount）。
- 文档：`docs/asset-placeholder-list.md`（kitsunebi 行销项）、`boss-dev-design` skill。
- **依赖**：change `boss-stats-deterministic`（秒数确定化）——未落地时秒数按 T2 带内取值并标 TBD。
  激光**实体层**（旋转/平移）已由 `danmaku-laser` 提供，本变更只补**轨道层**接线。

## Non-Goals（本变更明确不做）

- **不修改激光实体的姿态求值**（旋转/平移/隐藏/指纹已由 `danmaku-laser` 提供）；本变更只补**轨道层**接线。
- **不实现符纸弹种。** 轨道层 `Projectile` 仍只有 SPHERE/LASER；符纸意象暂以弹色/几何表达。
- **不实现召唤分身（式神橙 / Still-hunter Chen）。** 按 boss-dev skill，召唤物需独立变更。
- **不处理傩面（→ 露米娅）。** 那是另一条独立变更。
- **不重新配平其它 BOSS。**
