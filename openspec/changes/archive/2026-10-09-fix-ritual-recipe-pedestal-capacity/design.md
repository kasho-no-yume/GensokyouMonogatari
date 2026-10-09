## Context

仪式配方的 `ingredients[].count` 语义 = 占用几个祭品台（`RitualPedestalBlockEntity` 强制「一台一件」，台面容量恒为 1）。`RitualRecipeMatcher.allocate` 把各台面池容量（每台 1）与配方需求做贪心分配，`Σcount > 台位数` 时无法分配 → 返回 empty，**配方永久静默失配**。

八百万神恩 pattern 每阶 4 台，累积 `4 / 8 / 12 / 16 / 20`（1~5 阶）；zaohua pattern 累积 `8 / 16 / 24 ...`。现行配方用量对照：

| 配方 | minTier | 台位 | 现行 Σ | 结论 |
|---|---|---|---|---|
| grace_advance_1 | 1 | 4 | 10 | 超量，永久失配 |
| grace_refine_1 | 1 | 4 | 10 | 超量 |
| grace_advance_2 | 2 | 8 | 10 | 超量 |
| grace_refine_2 | 2 | 8 | 12 | 超量 |
| grace_advance_3 | 3 | 12 | 10 | 未填满 |
| grace_refine_3 | 3 | 12 | 10 | 未填满 |
| grace_advance_4 | 4 | 16 | 12 | 未填满 |
| grace_refine_4 | 4 | 16 | 8 | 未填满 |
| grace_advance_5 | 5 | 20 | 11 | 未填满 |
| grace_refine_5 | 5 | 20 | 9 | 未填满 |
| zaohua_stone_t1 | 0 | 8 | 12 | 超量 |
| zaohua_codex_of_beings | 1 | 16 | 17 | 超量 |

玩家阶级与结构阶级的过滤（`YaoyorozuGraceService.startSession`）：`advance_N` 要求 `minTier=N` 且玩家阶级 `==N-1`；`refine_N` 要求 `minTier=N` 且玩家阶级 `≥N`。二者玩家侧互斥 → 同阶 advance/refine 即便共用物品类型也不会同时可选。

## Goals / Non-Goals

**Goals:**
- 让 4 条失配配方的 `Σcount` 落在台位预算内，且 1~2 阶材料符合进阶设计。
- 八百万神恩逐阶「恰好填满台位」。
- 为仪式配方建立可复用的容量不变量与校验，防止复发。

**Non-Goals:**
- 不改动 `RitualRecipeLoader` 的拒载语义（容量违规仍是数据侧问题，不改运行时行为）。
- 不重做八百万神恩 pattern（不动祭品台数量/位置）。
- 不做 3~5 阶的材料带重新设计——本变更仅补量填满，材料带留待后续变更。

## Decisions

### D1：只改配方数据，不动 pattern
超量根因在配方用量，而非台位不足。加台位会改旗舰建筑与 builder/测试包，成本与风险都更高。故仅调整配方。

### D2：八百万神恩逐阶恰好填满台位
该仪式配方少，留余量无意义；逐阶填满同时形成「祭坛越大、供奉越重」的可读节奏。目标 `Σcount`：1~5 阶 = 4 / 8 / 12 / 16 / 20。

**1~2 阶重制（新材料带）：**

| 配方 | 材料 | Σ |
|---|---|---|
| grace_advance_1 | `gensokyou:spirit_iron` ×2 + `minecraft:diamond` ×2 | 4 |
| grace_refine_1 | `gensokyou:spirit_iron` ×2 + `minecraft:netherite_scrap` ×2 | 4 |
| grace_advance_2 | `gensokyou:star_silver` ×4 + `gensokyou:spellcard_star` ×2 + `gensokyou:sukima_fragment` ×2 | 8 |
| grace_refine_2 | `gensokyou:star_silver` ×3 + `gensokyou:spellcard_star` ×1 + `gensokyou:sukima_fragment` ×4 | 8 |

- 1 阶下界合金放在 refine（已进阶者），初次进阶 `advance_1` 只吃灵铁+钻石，避免凡人第一步卡下界合金。
- 同阶 advance/refine 共用物品类型、仅改配比——因 D2 的玩家阶级互斥（D3）安全。

**3~5 阶沿用现材料、补量填满：**

| 配方 | 材料 | Σ | 原 Σ |
|---|---|---|---|
| grace_advance_3 | `minecraft:diamond_block` ×4 + `minecraft:emerald_block` ×4 + `minecraft:sea_lantern` ×4 | 12 | 10 |
| grace_refine_3 | `minecraft:diamond_block` ×4 + `minecraft:gold_block` ×8 | 12 | 10 |
| grace_advance_4 | `minecraft:netherite_scrap` ×6 + `minecraft:diamond_block` ×5 + `minecraft:end_rod` ×5 | 16 | 12 |
| grace_refine_4 | `minecraft:netherite_scrap` ×4 + `minecraft:emerald_block` ×12 | 16 | 8 |
| grace_advance_5 | `minecraft:netherite_ingot` ×4 + `minecraft:beacon` ×1 + `minecraft:amethyst_block` ×15 | 20 | 11 |
| grace_refine_5 | `minecraft:netherite_ingot` ×4 + `minecraft:echo_shard` ×16 | 20 | 9 |

### D3：不改配方 id / minTier / minPlayerTier / spCost / effect
仅改 `ingredients`。玩家侧语义、灵力花费、效果 id 全部保持不变，避免连带改动行为与 spec 的其它要求。

### D4：容量不变量置于 `ritual-recipes`
在通用配方能力新增约束：`Σcount ≤ pedestals(minTier 对应结构阶级)`；八百万神恩额外要求 `=`（逐阶填满）。实现为**离线/CI 校验**（遍历 `ritual_recipes/*.json` × 展开后的 pattern 切片，比对台位数），MUST NOT 依赖 loader 跨重载器调用。可选：加载期以 WARN 提示超量配方（需 pattern 数据就绪，谨慎处理加载顺序）。

### D5：zaohua 两条按现有材料就近下调
- `zaohua_stone_t1`：与 spec 场景「4 钻石 + 4 仪式石 0 = 8 台」对齐，去掉/裁剪精炼辰砂 —— 建议 `diamond` ×4 + `ritual_stone_0` ×2 + `refined_cinnabar` ×2 = 8（仍保留辰砂语义）。
- `zaohua_codex_of_beings`：`memory_fragment` ×8 + `talisman_paper` ×4 + `spirit_herb` ×3 + `book` ×1 = 16（灵草 4→3）。

## Risks / Trade-offs

- **[3~5 阶材料曲线暂时下沉]** 2 阶抬到星银/符卡星后，3 阶仍用凡材（钻石块/绿玉块），2→3 之间会出现成本「凹坑」 → 本变更显式接受；后续「神恩 3~5 阶材料带」变更收口。
- **[refine 成本偏高]** 2 阶洗练一次吃 star_silver×3 + 符卡星×1 + 隙间碎片×4（可重复洗练） → 计数可调；spCost（3000）仍是主闸门，材料为签名。
- **[容量校验放置]** 加载期跨 loader 校验有加载顺序耦合 → 优先做成离线/CI 对表测试；加载期仅可选 WARN。
- **[zaohua 与进行中的 add-gensokyou-material-uses 同文件]** 两者都改 `zaohua_circle.json` → 实施时先确认该变更已归档或合并其改动，避免冲突。

## Open Questions

- 1~2 阶精确配比（尤其 refine 侧）是否需要再调？当前为草案。
- `zaohua_stone_t1` 保留 2 精炼辰砂 + 2 仪式石 0 是否符合造化配方原意？
