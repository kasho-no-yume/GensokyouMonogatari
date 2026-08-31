# Tasks: tier-system-runtime

## 1. 品阶色单一色源

- [x] 1.1 新建 `registry/TierPalette`：品阶 0-5 → 主色 RGB（§5.4 值）、`textColor(tier)`、`shouldTintName(tier)`（0 级 false）；注释标注色环出处
- [x] 1.2 新建 `Block→tier` 查表（随注册填充），供匹配推导与 TieredBlockItem 使用

## 2. 多级仪式方块

- [x] 2.1 扩 `tools/textures/ritual_blocks.py` 生成 `ritual_stone_0..5.png`（复用品阶调色板）；运行落位并出 x8 预览目检
- [x] 2.2 `ModBlocks` 循环注册 `ritual_stone_0..5` 与 `ritual_pedestal_0..5`，删除旧 `RITUAL_STONE`/`RITUAL_PEDESTAL`；`ModBlockEntities` 两 BE 类型收编各 6 块
- [x] 2.3 资产：石/台 blockstate ×12（台用 cube_bottom_top 接 `_N` 顶/底贴图）、block 模型 ×12、`TieredBlockItem` ×12（item 模型 + `getName` 品阶色染名、0 级不染）、lang ×12
- [x] 2.4 标签：`#gensokyou:ritual_stones` 收编 6 石；新建 `#gensokyou:ritual_pedestals`（6 台）；8 个仪式 JSON 的 `"P"` 改标签引用
- [x] 2.5 删除无后缀贴图 `ritual_stone.png`、`ritual_pedestal{,_top,_bottom}.png`

## 3. 仪式核心随等级变色

- [x] 3.1 `RitualCoreBlock` 增加 `tier` IntegerProperty（0-5）；blockstate 六变体指向 `ritual_core_0..5` 模型（新模型 JSON ×6 引用现成贴图）
- [x] 3.2 `RitualMatcher` 命中后推导 `ritualTier`（keyed 坐标查 `Block→tier` 取 max）并入 `RitualMatch`
- [x] 3.3 `onFormed` 写 `setValue(TIER, match.ritualTier())`、`onStructureLost` 写 0；仅值变化才 setBlock（防重扫循环）
- [x] 3.4 验证：搭建含 2 级石/台的结构核心呈 `_2` 贴图；拆石失效回落 `_0`；裸放核心恒灰；现有 8 仪式成型行为不变

## 4. 程序化滤镜染色

- [x] 4.1 `GensokyouItemColors`（RegisterColorHandlersEvent.Item）：AmpCoreItem → 品阶色、SpellCardItem 子类 → 卡主题色
- [x] 4.2 `SpellCardEffects` 注册项附主题色字段（musou_fuuin 红 / icicle_fall 冰蓝 / light_reflect 金，初值目检微调）
- [x] 4.3 增幅核：gen_tex 产灰度底图 `amp_core.png`；t1/t2/t3 模型共享底图 + tintindex 0；删 `amp_core_t1/2/3.png`
- [x] 4.4 符卡：产 `spellcard_frame.png`（不染层）与 `spellcard_emblem.png`（灰度染层）两张共享底图；3 张符卡模型改双层（layer1 tintindex 0）
- [x] 4.5 游戏内目检三卡与三核染色效果、非染层不受影响；确认 assets 无染色产物贴图
- [x] 4.6 增幅核双层染色：托座层按品阶色、晶石层按实例随机色（CRYSTAL_COLOR 组件 + RuneGenerator 掷色 + 创造栏预生成 + 托座贴图灰度化）

## 5. 品阶色物品名

- [x] 5.1 `WeaponLevelCoreItem`/`AmpCoreItem`/`BulletCoreItem` 覆写 `getName`（TierPalette 取色、0 级不染；弹幕核按 requiredTier）
- [x] 5.2 验证：物品栏/JEI/核槽 GUI/掉落物名均呈品阶色且与 tint 同值

## 6. 收尾

- [x] 6.1 编译 + 全量回归：仪式匹配/启停/祭品、 JEI 配方卡、武器装核
- [x] 6.2 `docs/asset-placeholder-list.md` 与 project.md §5 命名约定描述同步（变体=真方块、基础贴图仅核保留）
