## Why

祭品台 `ritual_pedestal` 是所有仪式图案的承台组件（源初造化 0/1/2 阶分别需要 8/16/24 个），但它目前在生存里**完全无法获得**：没有工作台配方，也没有任何仪式配方产出它，只有破坏自身掉落的 loot table。结果是玩家即使造出仪式石与仪式核心，也无法摆出第一个祭品台，整条仪式链在开局即死锁。

祭品台是仪式的输入接口，因此它的获取配方 MUST NOT 依赖任何仪式、灵力核心或维度进度——这与 0 阶灵力核心作为无灵力 bootstrap 的定位一致。

## What Changes

- 新增 `data/gensokyou/recipe/ritual_pedestal.json`：工作台有序合成 `SSS / RPR / SSS`（`S`=`minecraft:smooth_stone`、`R`=`gensokyou:ritual_stone_0`、`P`=`gensokyou:ppoint`），产出祭品台 ×1。
- 该配方只用开局可得材料：平滑石、0 阶仪式石（圆石 + P 点）与 P 点，不消耗灵力、不需要灵力核心、不需要任何仪式建筑。
- 祭品台**不分阶**（品阶由仪式核心在结构重扫时写入 `tier` blockstate 属性），因此本变更只提供一条配方，不引入 1/2 阶祭品台物品或升级路径。
- 在 `gensokyou:items` 分类新增祭品台 Patchouli 物品词条：spotlight 说明页 + `patchouli:crafting` 工作台配方页，常驻可见（无 secret 门槛）。

## Capabilities

### New Capabilities
<!-- 无新增能力。 -->

### Modified Capabilities
- `ritual-pedestal`: 增加祭品台的生存获取路径与指导书物品词条要求（不改动既有的单方块品阶染色、存取与渲染需求）。

## Impact

- 数据：新增 1 个普通合成配方（`recipe/ritual_pedestal.json`），新增 1 个 Patchouli 物品词条。
- 生成器：`tools/gen_ritual_book_entries.py` 的 `ITEM_RECIPE_ENTRIES` 增加一条 `crafting` 来源条目，并支持按物品覆盖说明文本键。
- 语言：新增 `item.gensokyou.ritual_pedestal` 与祭品台词条说明的中英文键。
- 验证：新增 `tools/audit_ritual_pedestal_recipe.py` 与 `RitualPedestalRecipeResourceTest`，锁定 bootstrap 约束（不得出现灵力消耗、不得由仪式产出、原料必须全部为开局可得）。
- 兼容：不改方块/物品注册、blockstate、方块实体、渲染与结构匹配；祭品台仍为单一注册方块。已有玩家世界中的祭品台无迁移问题。
