# 前期内容变更 · 会话交接

> 生成日期：2026-10-24
> 目的：本会话上下文较长，后续可分别在新会话处理下列 change，不必回溯聊天记录。
>
> **2026-10-25 更新：`rebalance-early-progression` 与 `add-early-material-uses` 已实施并归档**
> （`openspec/changes/archive/2026-10-25-rebalance-early-progression/`、
> `openspec/changes/archive/2026-10-25-add-early-material-uses/`），
> delta specs 已合并进 `openspec/specs/`。本文档以下内容保留为历史决策记录；
> 只剩 `buff-player-spellcards` 仍为 PARKED。

## 1. 当前 change 一览

| change | 状态 | 产物 | 说明 |
|---|---|---|---|
| `rebalance-early-progression` | **4/4 apply-ready** | proposal / design / specs / tasks | 1/2 阶仪式石配方、T2 信物、1 阶进阶 200k、书页/文案 |
| `add-early-material-uses` | **4/4 apply-ready** | proposal / design / specs / tasks | B 点、灵鱼药水、人鱼鳞/龙鳞甲、妖精典籍 |
| `buff-player-spellcards` | **PARKED，proposal-only** | proposal | 玩家符卡增强方向；当前半成品，暂不实施 |

先跑：

```powershell
openspec status --change rebalance-early-progression
openspec status --change add-early-material-uses
```

两者均应显示 `All artifacts complete!`。

## 2. 已直接修改的文件（不要在实施时重复改）

这些不是 proposal，而是本会话已落地的文档/数据修正：

- `README.md`：首 BOSS、芙兰/莱瓦汀、飞行、妖精掉落、已可玩内容。
- `openspec/project.md`：飞行不耗灵、大妖精为召唤 BOSS、入口链 9 残页、
  道具符卡低门槛标为待完善。
- `openspec/specs/yaoyorozu-grace-ritual/spec.md`：2 阶配方旧文案清理
  （星银/符卡星/灵铁），1 阶 `spCost=200,000` 注明。
- `src/main/resources/assets/gensokyou/lang/zh_cn.json` / `en_us.json`：
  `monsters_fairies`、`spellcards_overview`、`term_basics`、`npcs_overview`。
- `src/main/resources/data/gensokyou/recipe/bpoint.json`：已改为 3×3 八 P → 1 B。

## 3. 故意“只写进 proposal、还没改”的部分

这些必须和代码/数据同批落地，MUST NOT 只改文本：

- `fantasy_materials_overview.p2`、`material_star_silver.p1` + 页面门槛、
  `jei.gensokyou.loot.gensokyou_high`：等 `ToolSacrificeBehavior` 的信物改成花精之滴。
- 「仪式入门」两条仪式石配方页：等 `zaohua_circle.json` 配方改完再加。
- `monsters_fairies.p2` 的“花精之滴是中阶带信物”表述：等信物代码落地。

## 4. `rebalance-early-progression` 关键决策

- 1 阶石：`1 diamond + 2 ritual_stone_0 + 1 cinnabar + 4 blackstone → 1 stone`，
  `minTier 0`，`Σcount 8`。
- 2 阶石：`2 T1 + 4 spirit_iron + 1 tide_crystal + 1 spirit_herb + 1 gentian + 1 higanbana + 6 purpur → 4 stones`，
  `minTier 1`，`Σcount 16`；**产出 4 块/次**。
- T2 材料带信物：`sukima_fragment` → `fairy_nectar`；信物不消耗。
- `material_star_silver` 页：`end_unlock` → `nether_unlock`；2 阶石/结构/核心/蓝仍 `end_unlock`。
- `grace_advance_1.spCost`：200 → 200,000；`grace_refine_1` 保持 1,000。
- 玩家神恩等级不门控仪式/召唤；T0 可搭高阶祭坛；首 BOSS 书门票保留，
  重复打 BOSS 由镇魂解决。
- 3 阶以上仪式石不敲定；3+ 阶配方后续再设计。

## 5. `add-early-material-uses` 关键决策

- B 点：8 P → 1 B；符卡配方低 1 / 中 2 / 高 2。
  `musou_fuuin` 加 2 后恰为 16，MUST NOT 加 4。
- 灵鱼：`spirit_fish` 在 Sunako 酿 `gensokyou:dolphins_grace`；
  海豚恩惠无强效档，**只做长效档**，长效消耗月砂。
- 人鱼鳞甲：钻石甲数值 + 全套水下呼吸 + **仅水下**夜视；
  难判断时允许出水保留最多 5 秒；配方 5/8/7/4。
- 龙鳞甲：钻石甲数值 + 全套**火焰伤害全免**（Fire Resistance 语义）+
  100% 抗击退；配方 5/8/7/4。
- 妖精：`gensokyou:fairy` 可被众生典籍收录；
  补 `loot_table/entities/fairy.json`（1 残页必掉、10% P、10% B），
  镇魂可产出；实际击杀不双掉；大妖精仍排除。

## 6. `buff-player-spellcards`（PARKED）

- 只保留诊断：道具卡品质从未掷出、无想/冰击 α=0、光反空实现、
  召唤基数跟不上武器 T3/T5 倍率、BOSS 直掉默认 0、首张卡太晚。
- 启动前先读 `proposal.md` 的 Open Questions 与 `player-spellcard-design` skill。
- B 点配方已移交 `add-early-material-uses`，不要在这个 change 里重复设计。

## 7. 已知既有问题（不是本次引入）

- `python tools/lang_audit.py` 仍报 31 个 `jei.gensokyou.recipe.*` 缺键；
  与本次文案改动无关，不要误判为本变更回归。
- `openspec validate --strict` 对 `buff-player-spellcards` 会报 `no deltas`
  （它是 PARKED 的 proposal-only；不要为此强行补 specs）。

## 8. 建议的开工顺序

1. 先做 `rebalance-early-progression`：它解开 0→1 与 1→2 的配方闭环，
   是后续所有 T1/T2 内容的前置。
2. 再做 `add-early-material-uses`：B 点/药水/盔甲/典籍都是独立材料补全。
3. 最后视情况启动 `buff-player-spellcards`；它需要先定每阶预算，
   不要直接乘常数。

## 9. 验证命令

```powershell
openspec validate rebalance-early-progression --strict
openspec validate add-early-material-uses --strict
python tools/lang_audit.py
```

实机路线：

- 0 阶造化造 1 阶石（不再需要精炼辰砂）→ 1 阶造化 → 灵炭 → 精炼辰砂 → 装配台。
- 花精之滴解锁 T2 材料带；星银页在下界后可见；2 阶石页在末地后可见。
- 首进阶 200k；首 BOSS 书门票。
- 8 P → 1 B；B 点符卡；灵鱼药水；两套甲全套/缺件；20 妖精镇魂。
