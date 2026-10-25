## 1. 配方数据

- [x] 1.1 `data/gensokyou/ritual_recipes/zaohua_circle.json`：把 `zaohua_stone_t1` 改成
  `1 diamond + 2 ritual_stone_0 + 1 cinnabar + 4 blackstone → 1 ritual_stone_1`
  （`minTier:0`、`spCost:2000`、`Σcount=8`）。
- [x] 1.2 同文件：把 `zaohua_stone_t2` 改成
  `2 ritual_stone_1 + 4 spirit_iron + 1 tide_crystal + 1 spirit_herb + 1 gentian + 1 higanbana + 6 purpur_block → 4 ritual_stone_2`
  （`minTier:1`、`spCost:20000`、`Σcount=16`）。
- [x] 1.3 `data/gensokyou/ritual_recipes/kami_no_megumi_circle.json`：
  `grace_advance_1.spCost` 200 → 200000；`grace_refine_1` 保持 1000。
- [x] 1.4 断言两条石配方 MUST NOT 含 `refined_cinnabar` / `star_silver` / `sukima_fragment`；
  `Σcount` 分别等于 8 / 16。

## 2. 信物代码

- [x] 2.1 `ToolSacrificeBehavior`：把 T2 带判定从 `countSukimaFragments()` 改为
  `countFairyNectar()`，`gkHigh` 使用 `ModItems.FAIRY_NECTAR`；信物仍不消耗。
- [x] 2.2 `WatatsumiBehavior` 若复制了同一判定，同步改为花精之滴。
- [x] 2.3 单测：无信物 / 仅指导书 / 仅花精之滴 / 双信物四种池组装；
  断言 `sukima_fragment` 不再解锁 `gensokyou_high`。
- [x] 2.4 单测：花精之滴放在祭品台多次结算后仍在原位。

## 3. 指导书

- [x] 3.1 `entries/rituals_basics.json`：在现有页后加两页 `gensokyou:ritual_page`，
  分别用 `recipe: "gensokyou:zaohua_stone_t1"` / `"gensokyou:zaohua_stone_t2"`；
  T1 页常驻，T2 页挂 `advancement: "gensokyou:guide/end_unlock"`。
- [x] 3.2 `entries/material_star_silver.json`：`end_unlock` → `nether_unlock`。
- [x] 3.3 `lang/zh_cn.json` + `en_us.json`：
  `fantasy_materials_overview.p2`、`material_star_silver.p1`、
  `jei.gensokyou.loot.gensokyou_high` 的「隙间碎片」改为「花精之滴」。
- [x] 3.4 信物代码落地后，把 `monsters_fairies.p2` 的「花精之滴是露米娅/主题卡材料」
  补成「中阶材料带信物 + 露米娅/主题卡材料」。
- [x] 3.5 跑 `python tools/lang_audit.py`；确认本次未新增缺键
  （既有 31 个 JEI recipe 键缺失不在本变更范围）。

## 4. 规格与文档同步

- [x] 4.1 确认 `openspec/specs/yaoyorozu-grace-ritual/spec.md` 的 2 阶配方文案已是
  星银/符卡星/灵铁，且 1 阶 `spCost` 注明 200,000。
- [x] 4.2 确认 `README.md` / `openspec/project.md` 的飞行、首 BOSS、入口链已更新。
- [x] 4.3 `openspec/specs/danmaku-weapon-crafting/spec.md` 中
  「T2 材料带唯一钥匙是隙间碎片」改为花精之滴（纯文案，无行为变更）。

## 5. 验证

- [x] 5.1 `python -c "json.load(...)"` 解析 `zaohua_circle.json` / `kami_no_megumi_circle.json`。
- [x] 5.2 `openspec validate rebalance-early-progression --strict` 通过。
- [x] 5.3 实机：0 阶造化造 1 阶石（不再需要精炼辰砂）→ 1 阶造化 → 灵炭 → 精炼辰砂 → 装配台。
- [x] 5.4 实机：花精之滴解锁 T2 材料带；星银页在下界后可见；2 阶石页在末地后可见。
- [x] 5.5 实机：首进阶 200,000 灵可被 1 阶灵核填满；首 BOSS 仍可用书门票。
- [x] 5.6 回归：`sukima_fragment` 仍是八云蓝信物 + `fox_servant` 配方原料。
