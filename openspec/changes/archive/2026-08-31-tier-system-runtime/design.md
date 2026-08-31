# Design: tier-system-runtime

## Context

品阶色环（0-5 灰/绿/蓝/金/红/紫）已登记于 project.md §5.4，`_0.._5` 贴图变体已产（石缺 6 张）。现状：仪式石/核/台各为单方块，核/台变体贴图闲置；匹配器 palette 谓词支持 `_ignore`/`air`/`#tag`/EXACT 四种，8 个仪式 JSON 引用 `ritual_core`（EXACT）、`#gensokyou:ritual_stones`（TAG）、`gensokyou:ritual_pedestal`（EXACT）；`RitualMatch.keyed` 已给出每键命中坐标；`RitualBehavior.onFormed/onStructureLost` 为既有重扫描挂点。染色候选方案中，品阶色固定 6 色使原版多层 tint（乘法滤镜）足够，启动期烘焙/BEWLR/shader 均成本更高而收益不增。

## Goals / Non-Goals

**Goals:** TierPalette 单一色源；石/台 6 品阶独立方块 + 核心随仪式等级变色；灰度底图 + 运行期 tint（增幅核 + 3 符卡）；品阶色物品名（0 级不染）；现有仪式行为零变化。
**Non-Goals:** 不做仪式品阶门槛（未来用「等级 ≥ N」行为层校验）；不做符卡专属美术（共享纹章占位，未来 layer2 扩展）；不动弹幕核贴图（美术定色）；不做高品阶方块配方（获取方式另议）。

## Decisions

### D1 TierPalette：普通类常量表，非 enum 附带映射
`com.bitsson.gensokyou.registry.TierPalette`：`int rgb(int tier)` + `int textColor(int tier)`（`TextColor.fromArgb`）+ `boolean shouldTintName(int tier)`（tier==0 → false）。色值直接搬 §5.4 主色列。放 registry 包使其客户端/服务端均可用（`getName` 双侧调用）。
备选（否决）：enum + ChatFormatting——6 色 RGB 精确值无法用原版格式色表达。

### D2 多级方块注册：循环 + tier 字段
`ModBlocks` 循环注册 `ritual_stone_0..5`（普通 Block）与 `ritual_pedestal_0..5`（`RitualPedestalBlock`，行为类不变），维护 `Map<Block,Integer> TIER_LOOKUP`（或 `TierPalette.tierOf(Block)`）；`ModBlockEntities` 两个 BE 类型的 `Set.of(...)` 收编 6 块；`TieredBlockItem extends BlockItem` 覆写 `getName`。旧 `RITUAL_STONE`/`RITUAL_PEDESTAL` 注册与无后缀贴图删除。
备选（否决）：单一方块 + tier 属性（石/台）——与「每级一种」的需求决议相反，且 BlockItem 无法静态定名。

### D3 仪式等级推导：RitualMatch 后处理字段
`RitualMatch` 增加 `ritualTier()`：matcher 在 `tryPattern` 命中后遍历 `keyed` 各键坐标，凡 `TIER_LOOKUP` 命中者取 max（无石/台则 0）。新增方块标签 `#gensokyou:ritual_pedestals`（6 块）；`#gensokyou:ritual_stones` 收编 6 块。匹配器/加载器核心逻辑零改动——EXACT 谓词天然支持 `ritual_stone_2` 严格圈定。
备选（否决）：谓词语法扩展 `[tier>=N]`——当前无需求（现有仪式全 ≥0），需要时以 pattern 级 `keyTiers` 字段后补。

### D4 核心 tier 属性与更新时机
`RitualCoreBlock` 增加 `IntegerProperty tier(0..5)`，blockstate 六变体 → `ritual_core_0..5` 模型。写入时机：`onFormed`（含结构变更重扫）写 `match.ritualTier()`；`onStructureLost` 写 0；仅与当前 state 值不同才 `setBlock(pos, state.setValue(TIER, n), UPDATE_ALL)`（自带客户端同步）。裸放核心从未 onFormed → 恒 0 灰。
注意：setBlock 会触发邻居更新 → 重扫描幂等（tier 已相等即不写块，无循环）。

### D5 染色管线：原版多层 tint
- 处理器：`RegisterColorHandlersEvent.Item` 注册单入口 `GensokyouItemColors`——`AmpCoreItem` → layer0 托座=品阶色、layer1 晶石=实例随机色（`crystal_color` 组件，缺失回退品阶色）；`SpellCardItem 子类` → layer1 灰度纹章按卡主题色染（layer0 卡框不染）。所有返回色 SHALL 带 0xFF alpha（1.21.1 ItemRenderer 提取 alpha 乘顶点色，缺省即整层透明——踩坑已记录 skill）。
- 增幅核：灰度金属托座 `amp_core.png`（tintindex 0 → 品阶色）+ 灰度晶石 `amp_core_dye.png`（tintindex 1 → 实例随机色）；`RuneGenerator.ensureGenerated` 首次获取时与词条同机掷定 `crystal_color`（随机色相、固定 s0.80/v1.0，与弹幕取色同纪律）；新组件 `CRYSTAL_COLOR`（Codec.INT + VAR_INT）持久化并同步客户端；创造栏预生成使展示即所得；删 `amp_core_t1/2/3.png`。副产：tint 实时取 tier，早前「改 config 不跟色」局限自动消除。
- 符卡：`spellcard_frame.png`（白纸+边框+卡名区，不染层）+ `spellcard_emblem.png`（灰度纹章，染层）；每卡模型 layer0=frame、layer1=emblem(tintindex 1)；主题色并入 `SpellCardEffects` 注册项（musou_fuuin=红、icicle_fall=冰蓝、light_reflect=金，初值实现时目检微调）。
- 仪式石/台方块物品：无需 tint（贴图已按品阶定色），仅名字染色。

### D6 名字染色：四个类覆写 getName
`WeaponLevelCoreItem`（tier）、`AmpCoreItem`（tier）、`BulletCoreItem`（requiredTier supplier）、`TieredBlockItem`（tier）→ `Component.translatable(descriptionId).withColor(textColor(tier))`，tier==0 不染。GUI 核槽/JEI/掉落名自动继承。
备选（否决）：lang 文件 `§` 色码——格式色不精确且双语文件重复维护。

### D7 资产与数据清单
- 新增贴图：`ritual_stone_0..5.png`（扩 `tools/textures/ritual_blocks.py` 调色板复用）、`spellcard_frame.png`、`spellcard_emblem.png`、`amp_core.png`（灰度）。
- 删除贴图：`ritual_stone.png`、`ritual_pedestal{,_top,_bottom}{,_0..5}.png` 无后缀组（顶部/底部贴图保留 `_N` 后缀组）、`amp_core_t1/2/3.png`。
- blockstate：石 6（cubeall）、台 6（cube_bottom_top）、核 1（tier 六变体）。
- item 模型：石/台 12（parent 方块模型）、增幅核 3、符卡 3（双层）。
- lang：石/台 12 条 + en_us 同步。
- 数据：8 个仪式 JSON 的 pedestal 引用改 `#gensokyou:ritual_pedestals`；其余谓词不动。

## Risks / Trade-offs

- [旧方块移除导致旧存档内石/台变空气] → 开发期可接受（与既有改名先例一致）；提案已标 BREAKING
- [核心 setBlock 触发邻居更新的连锁重扫] → tier 相同即短路，无写块无循环；重扫描本身幂等
- [乘法 tint 的滤镜上限] → 染区按「高光近白、阴影近黑」设计灰度底图即可获得层次；不满足目检预期时再评估启动期烘焙（逃生舱，本变更不实现）
- [弹幕核 requiredTier 来自 config supplier，名字色随 config 变] → 与贴图定色规则同源，行为一致，接受
- [符卡共享纹章三卡同形] → 占位形态已确认；未来单卡细节以 layer2 追加，机制不动

## Migration Plan

注册名变更（石/台）+ 核心属性新增均无数据迁移（开发期）。回滚 = git revert + 还原 8 个 JSON 与标签文件。资产删除项在 git 历史可恢复。

## Open Questions

- 符卡主题色三色初值（红/冰蓝/金的精确 RGB）：目检后定稿。
- 石/台物品命名格式（如「仪式石（2级）」vs「仪式石 · 2级」）：lang 落地时定。
- `ritual_pedestal_top/bottom` 的 `_0..5` 后缀组是否沿用现成图：沿用（已按品阶成套产出）。
