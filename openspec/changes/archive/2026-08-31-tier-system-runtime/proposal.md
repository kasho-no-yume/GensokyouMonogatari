# Proposal: tier-system-runtime

## Why

品阶体系目前只存在于美术资产层（`_0.._5` 贴图约定），Java 侧没有任何品阶色概念：仪式方块只有单方块、变体贴图闲置；符卡与增幅核因计划「程序化染色」而未出正式贴图；带品阶的物品名字全是无色文本。需要把品阶色收敛为 Java 侧单一色源，并落地三件事：仪式石/祭品台按品阶独立成方块且核心随仪式等级变色、灰度底图 + 运行期 tint 的滤镜式染色、品阶色物品名。

## What Changes

- **品阶色单一色源**：新增 `TierPalette`（品阶 0-5 → 主色 RGB，取值 project.md §5.4），名字染色、物品 tint、未来 UI 强调色统一取色于此。
- **BREAKING（注册名）**：仪式石、祭品台改为按品阶独立注册 `ritual_stone_0..5`、`ritual_pedestal_0..5`（原单方块移除）；各自 BlockEntityType 收编 6 个方块；新增 12 个 BlockItem（品阶色名字）。
- **仪式核心保持单方块**，新增 `tier`（0-5）BlockState 属性：仪式等级 = 匹配结构内**最高品阶的仪式石/祭品台**，匹配/重扫描时写入核心状态，贴图随等级自动切换（现成 `_0.._5` 贴图接线）；结构失效或裸放回落 0 级灰。当前所有仪式 JSON 保持 ≥0 行为（石沿用标签谓词、台改用新标签）。
- **程序化滤镜染色（原版 tint）**：新增 `RegisterColorHandlersEvent.Item` 颜色处理器；assets 只保留**未染色灰度底图**——增幅核 3 张贴图收敛为 1 张灰度底图按品阶 tint；符卡新增共享白纸框（不染）+ 灰度纹章（按卡主题色 tint）双层模型，3 张符卡全部接入。**染色结果不落盘为贴图**。
- **品阶色物品名**：武器等级核、增幅核（按 tier）、弹幕核（按 requiredTier）、仪式石/台 BlockItem 覆写 `getName`，以 `TextColor.fromArgb(品阶色)` 染名；0 级灰不染。
- **新资产**：仪式石 `_0.._5` 变体贴图（扩 `ritual_blocks.py` 生成）、符卡灰度框/纹章底图、增幅核灰度底图；仪式石/台旧无后缀贴图移除。

## Capabilities

### New Capabilities

（无——全部并入既有品阶/仪式能力。）

### Modified Capabilities

- `tier-color-palette`：「品阶变体贴图命名约定」反转——石/台变体升级为独立注册方块（不再「不牵动 blockstate/Java」），无后缀基础贴图随之移除；新增「品阶色单一色源」「程序化滤镜染色（灰度底图 + 运行期 tint，染色不落盘）」「仪式核心随仪式等级变色（tier BlockState 属性）」「品阶色物品名（0 级不染）」四条需求。
- `ritual-pattern-system`：新增「品阶方块与仪式等级推导」需求——石/台按品阶 0-5 注册、谓词可按品阶精确指定或标签通配、仪式等级 = 结构内石/台最高品阶、等级驱动核心外观。

## Impact

- **Java**：新增 `TierPalette`、品阶方块注册（ModBlocks 循环）、`TieredBlockItem`、`RegisterColorHandlersEvent.Item` 处理器、`RitualMatch` 等级推导字段；`ModBlockEntities` 收编方块集合；`RitualCoreBlock`/`BlockEntity` 增加 tier 属性读写；`WeaponLevelCoreItem`/`AmpCoreItem`/`BulletCoreItem` 覆写 `getName`；`SpellCardEffects` 注册项附主题色；`RitualPatternLoader` 不改（标签谓词原样支持品阶方块）。
- **资产**：新增 `ritual_stone_0..5.png`（工具生成）、`spellcard_frame.png`+`spellcard_emblem.png`、`amp_core.png`（灰度）；移除 `ritual_stone.png`、`ritual_pedestal*.png` 无后缀组、`amp_core_t1/2/3.png`；新增 blockstate ×18（石/台各 6 + 核 tier 变体）、block/item 模型、标签 `ritual_pedestals`（`ritual_stones` 收编 6 块）、lang ×12。
- **数据**：8 个仪式 JSON 的 `"P": "gensokyou:ritual_pedestal"` 改 `"#gensokyou:ritual_pedestals"`；其余谓词不变。
- **不受影响**：仪式行为逻辑、配方/JEI、武器机制数值；匹配器与加载器代码零改动。
- **已知取舍**：核心 tier 变化经 setBlock 触发状态更新（带客户端同步）；品阶色 tint 遵循原版乘法语义（灰度底图设计承担滤镜效果）。
