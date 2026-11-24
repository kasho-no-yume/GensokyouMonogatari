# Design: 三阶矿精炼与消费端

## Context

`gensokyou-materials` spec 明写"T1~T2"，并规定只有辰砂/灵铁/星银/鬼石有专属精炼规则；
`kanayamahiko-smelting` spec 的"扩展边界"要求未列出物品不得获得新规则。
本提案通过**新变更**扩展规则清单，不修改上述 spec 的既有语义。

## Decisions

### D1 精炼比例（用户指定）

所有 8 种 T3 原矿按 **原矿 ×1 : 灵炭 ×2** 精炼，与星银同档。
规则走 `data/gensokyou/ritual_smelt_recipes/kanayamahiko_circle.json`，
`auxiliary: gensokyou:spirit_charcoal`、`auxiliary_count: 2`、`result.count: 1`。
方块原矿因在 `c:ores` 标签中而翻倍，实际产出 **2 个精炼产物**；
不另造 `rough_*` 变体，故无"不翻倍"分支。

| 原矿 | 精炼产物 | 新物品 |
|---|---|---|
| `wind_mica` | `mica_sheet` 云母片 | 是 |
| `inkglow_stone` | `ink_pigment` 墨锭 | 是 |
| `ringed_jade` | `jade_strip` 青碧镶条 | 是 |
| `snowmarrow_crystal` | `marrow_glaze` 乳白釉料 | 是 |
| `echo_crystal_ore` | `echo_crystal_shard` 鸣晶碎片 | 否（已有） |
| `rose_garnet` | `garnet_cabochon` 绯红嵌饰 | 是 |
| `mistband_crystal` | `frosted_glass` 雾面玻璃 | 是 |
| `nether_black_sand` | `black_sand_iron` 冥砂铁粒 | 否（已有） |

### D2 消费端数值

价值比 T1:T2:T3 = 1:2:4（体现在碎片/铁粒 ×2 与 4:1 配方）。
每条配方只使用一种 T3 主料 + ≤2 件凡材/T1 辅料。

| 产物 | 配方 | 单区块可做 |
|---|---|---|
| 三阶符纸 `talisman_paper` | 雾苇纤维 ×4 + 原版纸 ×2 → ×2 | 6 张 |
| 三阶共鸣构件 | 鸣晶碎片 ×8 + 灵铁 ×2 → ×1 | 3 件 |
| 深色船锚/链环 | 冥砂铁粒 ×8 + 灵铁 ×2 → ×1 | 4 件 |
| 三阶木构件 | 星冠木板 ×4 → ×1 | 10 件 |
| 透光屏风/灯罩 | 云母片 ×4 + 神木 ×2 → ×1 | 6 件 |
| 青碧嵌片 | 青碧镶条 ×4 + 星冠木板 ×2 → ×1 | 6 件 |
| 乳白釉料 | 乳白釉料 ×4 + 瓷器 ×1 → ×1 | 6 件 |
| 墨锭/纹样颜料 | 墨锭 ×4 + 凡材 → ×1 | 6 件 |
| 绯红嵌饰 | 绯红嵌饰 ×4 + 凡材 → ×1 | 6 件 |

### D3 红线

- T3 **不新增装备阶级**：`gensokyou-equipment-tiers` 已锁定灵铁、星银两档。
- T3 **不进入原版熔炉**：精炼只在煅炉进行，标签只用于翻倍判定，不注册原版烹饪配方。
- T3 产物不要求跨群系集齐；一件成品只吃一种 T3 主料。

## Risks / Trade-offs

- 新增 6 种物品需要物品模型、贴图、中英语言键与创造标签可见性。
- `FantasyMaterialsBookTest.MINERALS` 按精确集合断言，需同步扩充。
- 方块原矿翻倍会让 1 矿 + 2 灵炭 → 2 精炼产物，经济价值高于星银；如需压低，可在后续调整 result.count。
