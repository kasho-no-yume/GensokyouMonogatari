# Proposal: polish-kagutsuchi-flame-experience

## Why

加具土命之焰仪式行为已落地但实机暴露三个体验缺陷：火焰粒子锁死在核心一格、随等级几无观感差异；仪式 GUI 菜单无玩家背包槽，灵力核心必须靠手持右键台面等方式间接装入；祭品台按 6 品阶独立注册，建造时选台/凑台成本与视觉收益不成比例。三者都是上轮 change 未跑实机验证（tasks 7.2-7.4 未勾选）漏出的问题，一并修正。

## What Changes

- **粒子（方案 B）**：燃烧期发射点从"核心单点"改为"核心 + 每座祭品台位 + 同半径随机环点"的多点火柱，柱内 FLAME 数量与发射间隔随阶级（`match.level()`）增强；大烟雾点缀。覆盖半径随结构规模自然放大，停等/待机零粒子的既有约束不变。
- **GUI 背包**：`RitualCoreMenu` 补玩家主仓 3×9 + 快捷栏 1×9 真槽位，实现 `quickMoveStack`（shift 转移进电池槽）；`RitualCoreScreen` 面板 200×150 加高至 200×234（信息区 150 + 背包区 84），背景贴图 `ritual_core.png` 重绘。所有共用该界面的仪式一并获得背包区。
- **BREAKING——祭品台并阶**：删除 `ritual_pedestal_1..5`，仅存单方块 `ritual_pedestal`（放置恒 0 阶灰外观）；新增 `tier`(0-5) BlockState 属性，由核心 20t 重扫写入 `ritualTier` 实现"随仪式等级变色（同仪式核心）"。dev 世界中已放置的高阶台子随方块移除而消失，不做迁移。创造栏只保留一个祭品台。
- 品阶供给链收窄：仪式等级（`ritualTier`）推导来源从"仪式石/祭品台最高品阶"变为"仅仪式石最高品阶"；构建器 TAG 格位对无该品阶成员的标签（祭品台）回退取唯一成员；编辑器反导与校验器的台子 `_N_plus` 特例全部移除。

## Capabilities

### New Capabilities

（无——三项均落在既有 capability 上）

### Modified Capabilities

- `kagutsuchi-flame-ritual`：「火焰粒子表现」要求重写——多点火柱（核心+祭品台+环插值）、覆盖范围与密度随阶级增强。
- `ritual-core-interface`：新增「界面含玩家物品栏」要求——菜单注册 36 个背包槽、shift 转移、面板加高；灵力核心槽语义不变。
- `ritual-pedestal`：新增「单方块品阶变色」要求——唯一注册方块 + `tier` BlockState 属性由核心重扫写入仪式等级；原「祭品台存取」交互要求不变。
- `ritual-pattern-system`：「品阶方块与仪式等级推导」修改——祭品台退出品阶方块族，等级推导仅取仪式石最高品阶；`tiers` 字段作用范围措辞随之收窄。
- `tier-color-palette`：「品阶变体贴图命名约定」修改——祭品台从"多级方块族"类翻转到"单方块品阶变色"类（与仪式核心同类）；`_0.._5` 模型/贴图按既有资产保留。
- `ritual-builder-placement`：「标签谓词按品阶实例化」修改——标签内无所选品阶成员时 SHALL 回退取标签唯一成员（祭品台），材料清单同口径。
- `ritual-capture-diff`：key 定序规则修改——祭品台不再反导品阶标签，恒命中 `#gensokyou:ritual_pedestals`。
- `ritual-editor-save-validate`：规则④品阶下限修改——台子特例（`_2_plus` 被迫升档、1 阶缺档）随并阶删除。

## Impact

- **Java**：`KagutsuchiFlameBehavior`（粒子重写）、`RitualCoreMenu`/`RitualCoreScreen`（背包槽+布局）、`ModBlocks`/`ModItems`/`ModBlockEntities`/`ModCreativeTabs`（注册收敛）、`RitualPedestalBlock`（加 `tier` 属性）、`RitualCoreBlockEntity`（重扫写台子 tier）、`RitualBuilderPlacement.blockOfTier`（回退）、`RitualDiffCapture`/`RitualPatternValidator`/`JsonTagIndex`/`RegistryBlockTagIndex`（编辑器与校验）、`DebugCommands`/`RitualRecipeCategory`（引用点）；测试 `RitualDiffCaptureTest`。
- **资源**：删 5 blockstate + 5 loot_table + 4 个 `ritual_pedestals_N_plus` 标签 + 5 item model + 10 lang key ×2 语言；`ritual_pedestals.json` 改单成员；新增 `ritual_pedestal.json` blockstate（tier 0-5 → 既有 6 模型）与无后缀基础贴图；重绘 `textures/gui/ritual_core.png`（200×150→200×234）。
- **存档**：BREAKING，dev 阶段按仓库惯例不迁移（design.md 先例：无存档兼容压力）。
- **规范**：`openspec/project.md` §5 品阶资产约定同步更新。
