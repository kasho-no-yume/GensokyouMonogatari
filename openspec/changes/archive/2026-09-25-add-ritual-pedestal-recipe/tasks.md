## 1. 配方

- [x] 1.1 确认祭品台无任何生存获取路径（无普通合成、无仪式产出、无战利品），定位为开局死锁点
- [x] 1.2 核对祭品台为单一注册方块（品阶=blockstate 自动染色），确认不需要 1/2 阶分阶配方
- [x] 1.3 校准 bootstrap 物价（`ritual_stone_0` 0.125 P点/块、`ritual_core` 4 P点）与首个仪式需求量（4～8 个祭品台）

## 2. 实现

- [x] 2.1 新增 `data/gensokyou/recipe/ritual_pedestal.json`：`minecraft:crafting_shaped`、`SSS/RPR/SSS`、产出 1 个
- [x] 2.2 生成器 `ITEM_RECIPE_ENTRIES` 增加祭品台 `crafting` 条目，并支持按物品覆盖说明文本键
- [x] 2.3 生成 `item_ritual_pedestal.json` 词条（`gensokyou:items`，无 advancement/secret）
- [x] 2.4 补齐 `item.gensokyou.ritual_pedestal` 与 `item_recipe.pedestal.p1` 中英文键

## 3. 自动化验证

- [x] 3.1 新增 `tools/audit_ritual_pedestal_recipe.py`：校验图案、原料、数量、产物、无灵力字段、无仪式产出路径、词条页型与语言键
- [x] 3.2 新增 `RitualPedestalRecipeResourceTest`：锁定 bootstrap 约束与词条引用
- [x] 3.3 运行目标测试、全量 `gradlew test` 与 `gradlew build`（204 项测试全绿；顺带把上一轮审计/测试里「`item_*.json` 恰好 13 个」的全局计数改为按预期清单校验，避免后续新增词条误报）
- [x] 3.4 运行 `python tools/lang_audit.py` 与 `openspec validate add-ritual-pedestal-recipe --strict`

## 4. 回归检查

- [x] 4.1 无头服务端加载无配方解析错误，仪式配方总数不变（`Loaded 1337 recipes`，较上轮 +1；`Loaded 28 ritual recipes` 不变；`Done (1.633s)!`）
- [x] 4.2 确认未改动祭品台方块/物品注册、blockstate、方块实体、渲染与结构匹配（本轮主源码零改动）
- [x] 4.3 确认 `ritual_stone_0`、`ritual_core`、`spirit_core_0` 等既有 bootstrap 定价未被改动
