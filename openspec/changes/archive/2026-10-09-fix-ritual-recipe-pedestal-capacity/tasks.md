## 1. 八百万神恩 1~2 阶重制

- [x] 1.1 `grace_advance_1`：改为 `spirit_iron ×2 + diamond ×2`（Σ=4）
- [x] 1.2 `grace_refine_1`：改为 `spirit_iron ×2 + netherite_scrap ×2`（Σ=4）
- [x] 1.3 `grace_advance_2`：改为 `star_silver ×4 + spellcard_star ×2 + sukima_fragment ×2`（Σ=8）
- [x] 1.4 `grace_refine_2`：改为 `star_silver ×3 + spellcard_star ×1 + sukima_fragment ×4`（Σ=8）
- [x] 1.5 确认四条配方的 `minTier`/`minPlayerTier`/`spCost`/`effect` 未被改动

## 2. 八百万神恩 3~5 阶补量填满

- [x] 2.1 `grace_advance_3`：补至 `diamond_block ×4 + emerald_block ×4 + sea_lantern ×4`（Σ=12）
- [x] 2.2 `grace_refine_3`：补至 `diamond_block ×4 + gold_block ×8`（Σ=12）
- [x] 2.3 `grace_advance_4`：补至 `netherite_scrap ×6 + diamond_block ×5 + end_rod ×5`（Σ=16）
- [x] 2.4 `grace_refine_4`：补至 `netherite_scrap ×4 + emerald_block ×12`（Σ=16）
- [x] 2.5 `grace_advance_5`：补至 `netherite_ingot ×4 + beacon ×1 + amethyst_block ×15`（Σ=20）
- [x] 2.6 `grace_refine_5`：补至 `netherite_ingot ×4 + echo_shard ×16`（Σ=20）

## 3. zaohua 超量配方修正

- [x] 3.1 `zaohua_stone_t1`：改为 `diamond ×4 + ritual_stone_0 ×2 + refined_cinnabar ×2`（Σ=8）
- [x] 3.2 `zaohua_codex_of_beings`：`spirit_herb` 4→3（Σ=16）
- [x] 3.3 确认两条配方产物/spCost/minTier 未变，且未与进行中的 `add-gensokyou-material-uses` 冲突（必要时先归档/合并）

## 4. 容量不变量校验

- [x] 4.1 扩展离线校验（或 CI 对表测试）：遍历 `ritual_recipes/*.json`，按其 `minTier` 对应的展开后 pattern 切片统计祭品台数，断言 `Σcount ≤ pedestals(minTier)`，违规报 ERROR
- [x] 4.2 对八百万神恩追加 `Σcount == pedestals(minTier)` 断言
- [x] 4.3 确保校验不依赖加载期跨重载器调用（离线/CI 路径）

## 5. 验证

- [x] 5.1 运行校验器：0 ERROR / 0 WARN（含新增容量规则）
- [x] 5.2 `.\tools\gradle_task.ps1 build` 通过；日志 `Loaded N ritual recipes` 无 `Rejected`
- [ ] 5.3 进游戏：1 阶神恩结构摆齐 `grace_advance_1` 原料可正常启动（不再静默失配）；0 阶造化可造 1 阶仪式石；1 阶造化可造众生典籍
- [ ] 5.4 确认 JEI 配方卡数量与材料展示正确，无裸 id
