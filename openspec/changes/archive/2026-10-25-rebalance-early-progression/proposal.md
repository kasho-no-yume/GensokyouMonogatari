# 前期进度门与仪式石配方重排

## Why

当前 0 → 1 阶与 1 → 2 阶各有一处**配方级闭环**，玩家在正常生存下会被卡死：

1. **灵炭闭环**：`1 阶仪式石 → 精炼辰砂 → 灵炭 → 1 阶源初造化 → 1 阶仪式石`。
   1 阶石本身是 0 阶造化配方（这点没错），但它依赖的精炼辰砂需要灵炭，
   而灵炭只有 1 阶造化能出。玩家 0 阶把能建的全建完，仍然出不了第一块 1 阶石。
2. **星银闭环**：`星银 → T2 材料带 → 隙间碎片 → 八云蓝 → 2 阶百鬼夜行 → 2 阶仪式石 → 星银`。
   八云蓝的参照阶是 2，1 阶玩家越阶 10 倍；而 1 阶玩家拿不到星银。
   于是 2 阶仪式石、2 阶灵核、八方归元/万象共鸣/结界引爆全部进不去。

同时，书内「世界进度」门槛与实际配方门槛错位：1 阶内容挂下界，但 1 阶石材料不需要下界；
星银词条挂末地，但真正的钥匙是蓝的隙间碎片；蓝的条目也挂末地，而玩家必须在 T2 之前打她。
这会让玩家在正确的时间看不到正确的书页。

本变更只固化**配方与门槛**层面的决策；书内文案、JEI 标签与相关 spec 同步更新。
玩家符卡增强另立 `buff-player-spellcards`（当前**暂缓**，只保留方向）。

## What Changes

### 1. T2 材料带信物：隙间碎片 → 花精之滴

- `gensokyou_high`（T2 材料带）的祭品台信物从 `gensokyou:sukima_fragment` 改为
  `gensokyou:fairy_nectar`（花精之滴，大妖精的信物）。
- 信物仍然**不被消耗**，仍需至少 1 件解锁；未摆放时 T2 带产出恒为零。
- `sukima_fragment` 仍是八云蓝的主题信物，仍用于 `fox_servant` 等主题卡配方；
  它不再承担材料带钥匙职责。
- 工具献祭（`tool-sacrifice-ritual`）与绵津见特产池（`watatsumi`）同步。

**目的**：让 T2 材料带在 T1 阶段（击败大妖精后）即可解锁，
使「大妖精 → 花精之滴 → 星银/潮汐晶 → 2 阶石 → 八云蓝」成为一条正向链，
而不是让蓝同时当钥匙和门。

### 2. 1 阶进阶耗灵：200 → 200,000

- `grace_advance_1.spCost` 从 200 改为 200,000；`grace_refine_1` 保持 1,000。
- 目的：让首次进阶成为真正的灵力经济里程碑，而不是 200 灵的顺手点击。
- 首进阶仍需要 2 灵铁 + 2 钻石；200,000 灵需由 1 阶灵核（600,000 容量）或
  多枚 0 阶灵核供给，因此玩家必须先搭出 1 阶灵力核心与发电。
- 书内「八百万神恩」条目与进阶成功提示应写明该数值与「建议先备 1 阶灵核」。
- 口径已确认：这里的「1 阶 20w」指**首次进阶 `spCost`**；
  首 BOSS 的 `hyakki_boss_big_fairy.spCost` 不动。

### 3. 1 阶仪式石配方

```text
1× diamond + 2× ritual_stone_0 + 1× cinnabar + 4× blackstone
  → 1× ritual_stone_1
```

- `minTier: 0`（0 阶源初造化可执行），`spCost: 2,000`。
- `Σcount = 8`，恰好填满 0 阶源初造化的 8 个祭品台。
- 关键变化：**不再消耗精炼辰砂**，因此灵炭不再是 1 阶石的前置；
  改用黑石（下界凡材）作为 T1 阶段的维度门槛。
- 这同时把 1 阶石的原料压到可接受量级：
  1 钻石 + 1 辰砂 + 4 黑石 + 2 块 0 阶石（≈ 1/4 P 点）。

### 4. 2 阶仪式石配方

```text
2× ritual_stone_1 + 4× spirit_iron + 1× tide_crystal
+ 1× spirit_herb + 1× gentian + 1× higanbana + 6× purpur_block
  → 4× ritual_stone_2
```

- `minTier: 1`（1 阶源初造化可执行），`spCost: 20,000`。
- 原料 `Σcount = 16`，恰好填满 1 阶源初造化的 16 个祭品台；
  **产出 4 块**，使 32 块 2 阶石从 32 次合成降到 8 次。
- 关键变化：不再消耗星银，改用**潮汐晶（T2 材料带）+ 紫珀块（末地凡材）**
  作为 T2 门槛；三种植物把农业仪式纳入 T2 石的成本。
- 与 T1 石一样，原料总量与对应阶源初造化的台位数严格相等，
  这条不变量应写进 spec 与校验。

### 5. 仪式石配方进书

- 在「仪式列表」第一条「仪式入门」中补充**仪式石升阶**页：
  - 0 阶石 → 1 阶石（`zaohua_stone_t1`）
  - 1 阶石 → 2 阶石（`zaohua_stone_t2`）
- 用 `gensokyou:ritual_page` 按**完整配方 id** 绑定，
  MUST NOT 依赖全局配方排序或 JEI。
- 建议：0 阶/1 阶页常驻；2 阶页可挂 `guide/end_unlock`，或不挂门槛
  （配方卡上的紫珀块本身就会提示末地）。二选一需在实施前定。

### 6. 书内文案与门槛同步

- `fantasy_materials_overview.p2`：把「摆上隙间碎片」改为「摆上花精之滴」。
- `material_star_silver.p1`：把「以隙间碎片为信物」改为「以花精之滴为信物」；
  该页门槛从 `guide/end_unlock` 改为 `guide/nether_unlock`
  （T2 材料带在 T1 阶段即可解锁；末地门槛只属于 2 阶石/2 阶结构）。
- `jei.gensokyou.loot.gensokyou_high`：改为「需 花精之滴（不消耗）」。
- `monsters_fairies.p2`：删除「击败大妖精直接取得这本书」的过时说法；
  「花精之滴是中阶材料带信物」的表述随信物代码一起改
  （在信物代码落地前，先写成「召唤露米娅与主题符卡的材料」，避免书当场说谎）。
- `spellcards_overview.p3`：删除「BOSS 低概率直接掉落」的承诺
  （当前默认概率为 0；符卡系统暂缓，等 `buff-player-spellcards` 落地后再恢复文案）。
- `term_basics.p2`：把「超人类阶级」改为「修行者阶级」，与命名规范一致。
- `npcs_overview`：在幻想乡前隐藏该章节，或至少写「尚未相遇」，
  MUST NOT 让玩家打开一个只有概述、没有任何角色条目的空章。
- README：删除「飞行按阶级耗灵、五阶几乎免费」的旧描述，改为
  「进阶后永久解锁创造飞行；实测耗灵会让 BOSS 战不可玩，故不加耗灵」。
- `project.md` §3.2 的「5%/2%/1%/0.5%/0% 飞行费率」同步改为「飞行不耗灵」。
- `yaoyorozu-grace-ritual` spec：清理 2 阶配方旧文案
  （不再写隙间碎片，改为星银/符卡星/灵铁），并注明 1 阶 `spCost = 200,000`。

**书内文案修正状态**：

| 位置 | 现状 | 目标 | 状态 |
|---|---|---|---|
| `fantasy_materials_overview.p2` | 隙间碎片 | 花精之滴 | 随信物代码一起改 |
| `material_star_silver.p1` + 页面门槛 | 隙间碎片 / `end_unlock` | 花精之滴 / `nether_unlock` | 随信物代码一起改 |
| `jei.gensokyou.loot.gensokyou_high` | 隙间碎片 | 花精之滴 | 随信物代码一起改 |
| `monsters_fairies.p2` | 大妖精掉书 | 花精之滴 + 9 残页 | 已改（T2 带信物表述待信物代码） |
| `spellcards_overview.p3` | BOSS 低概率直掉 | 暂未开放 | 已改 |
| `term_basics.p2` | 超人类 | 修行者 | 已改 |
| `npcs_overview` | 有章无条目 | 尚未相遇 | 已改 |
| 「仪式入门」条目 | 无仪式石配方 | 加 0→1、1→2 两页 | 待实施 |
| `README.md` / `project.md` | 旧飞行/首 BOSS/入口链 | 新文本 | 已改 |

### 7. README 的首 BOSS/莱瓦汀表述

- README 仍以芙兰朵露与莱瓦汀作为「第一个 BOSS / 招牌武器」，
  但当前芙兰没有生成通路，莱瓦汀仅在芙兰掉落。
- 本变更只做**文档校正**：把首 BOSS 章节改为当前可遭遇的大妖精/山女/露米娅，
  把芙兰与莱瓦汀标注为后续内容；不新增生成通路。

## Capabilities

### Modified Capabilities

- `gensokyo-materials`：T2 材料带信物改为花精之滴；材料带与结构阶门槛分离。
- `tool-sacrifice-ritual`：信物判定从隙间碎片改为花精之滴。
- `zaohua-crafting`：1 阶石配方更新；新增 2 阶石配方与「Σcount = 台位数」不变量。
- `guide-book`：仪式入门新增仪式石升阶页；材料页门槛与文案同步。
  spec 的「进度解锁机制」需明确**材料带门槛**（指导书/花精之滴）与
  **结构阶门槛**（下界/末地）是两条轴，星银页挂 `nether_unlock`，
  2 阶石/2 阶结构/2 阶核心与蓝仍挂 `end_unlock`。
- `superhuman-temper` / `yaoyorozu-grace-ritual`：1 阶进阶 `spCost` 改为 200,000。
- `danmaku-weapon-crafting`：文中「T2 材料带唯一钥匙是隙间碎片」改为花精之滴。

## Impact

- 数据：`data/gensokyou/ritual_recipes/zaohua_circle.json`、
  `data/gensokyou/ritual_recipes/kami_no_megumi_circle.json`、
  工具献祭/绵津见信物判定、相关 lang 与 Patchouli 条目。
- 代码：`ToolSacrificeBehavior` 的信物计数与 `gkHigh` 判定；
  `WatatsumiBehavior` 若复制了同一判定需同步。
- 书/文档：`fantasy_materials_overview`、`material_star_silver`、
  `monsters_fairies`、`term_basics`、`jei` 标签、`README.md`、`openspec/project.md`。
- 验证：`zaohua` 两条新配方的 `Σcount` 与台位数断言；
  两枚信物的池组装单测；书页配方 id 解析；`lang_audit.py`；
  实机走一遍 0 阶 → 1 阶石 → 1 阶造化 → 灵炭 → 精炼辰砂 → 装配台。

## 已确认的设计取舍

- **2 阶石产出 4 块/次**：32 块 2 阶石只需 8 次合成；
  原料需求同比降到 1/4（16 块 1 阶石、32 灵铁、8 潮汐晶、24 株植物、48 紫珀块）。
- **`material_star_silver` 直接挂 `nether_unlock`**，不新建 advancement；
  末地门槛只属于 2 阶石/2 阶结构/2 阶核心与蓝。
- **玩家神恩等级不门控仪式与召唤**：T0 玩家理论上可以搭 5 阶祭坛，
  这是有意设计。因此本变更**不**给召唤配方加 `minPlayerTier`。
- **首 BOSS 的书门票保留**：重复打 BOSS 的问题由「镇魂返照」解决，
  不把引导书改成 9 残页。
- **1 阶进阶 `spCost = 200,000`** 保留，不再调整节奏。

## 后续补充（另立 `add-early-material-uses`）

- B 点参与符卡合成（可独立先行；不依赖符卡数值重做）。
- 灵鱼可炼药，效果为原版海豚的 Dolphin's Grace（成品只有长效档）。
- 人鱼鳞盔甲：钻石甲数值 + 全套水下呼吸 + 仅水下的夜视。
- 龙鳞盔甲：钻石甲数值 + 全套火焰伤害全免 + 100% 抗击退。
- 妖精必须能被众生典籍收录，且镇魂要产出纸/P 点；
  当前 mod 生物没有原版战利品表，需要补实体表或特判。

## 后续不敲定

- **3 阶以上仪式石**：本次不固化「Σcount = 台位数 + 产出 4」是否延续；
  3+ 阶配方留待后续设计再定，本变更只锁定 1/2 阶。
- **`yaoyorozu-grace-ritual` 旧文案**：已按实际数据清理
  （2 阶配方使用星银/符卡星/灵铁，不再写隙间碎片；
  1 阶 spCost 改为 200,000 并注明）。

