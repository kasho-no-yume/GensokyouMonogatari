## 1. 矿产下界池

- [x] 1.1 在 `data/gensokyou/ritual_loot/oyamatsumi_circle.json` 的每个工具档 `nether` 列表追加 `["minecraft:blackstone", 4]`
- [x] 1.2 同文件每个工具档追加 `["minecraft:basalt", 4]`
- [x] 1.3 人工核对：黑石/玄武岩在 wood~netherite 全 6 档均存在，其余三个仪式的 ritual_loot 未改

## 2. 灵力筑基器工作台配方

- [x] 2.1 新增 `data/gensokyou/recipe/landscaping_tool.json`（`crafting_shaped`，`ISI/SCS/ISI`：I=iron_ingot、S=ritual_stone_0、C=refined_cinnabar）
- [x] 2.2 进游戏验证：3×3 摆料可产出 `gensokyou:landscaping_tool`

## 3. 灵力引爆器造化配方

- [x] 3.1 在 `data/gensokyou/ritual_recipes/zaohua_circle.json` 追加 `zaohua_spirit_bomb`（activation / max / minTier 1 / spCost 20000；ritual_stone_1×1 + spirit_iron×2 + refined_cinnabar×2 + gunpowder×4 + amethyst_shard×2 → spirit_bomb×1）
- [x] 3.2 确认 Σcount=11 ≤ 1 阶造化祭品台容量 16（`RitualRecipePedestalCapacityTest`）

## 4. 验证

- [x] 4.1 运行 `.\tools\gradle_task.ps1 build`（或 `compileJava test`）确认数据加载与容量校验通过
- [x] 4.2 游戏内验证：引爆器 0 阶造化拒绝、1 阶造化可造；筑基器工作台可造
- [x] 4.3 JEI 检查：引爆器配方出现在造化配方页；筑基器出现在工作台配方页
