## Why

幻想乡素材体系（`gensokyo-materials`）已经建成了「22 种素材 + 5 大生产仪式」的产线，但产出端是断的：22 种素材里只有约 9 种在配方中出现过，辰砂/精辰砂、灵铁、星银、潮汐晶、神木、灵草、符纸、灵炭、碎符卡星之外，**其余 13 种成品没有任何消耗出口**（鬼石、灵土、瓷土、彼岸土、月砂、魔法木、常世木、龙胆、彼岸花、瓶装三途川水、灵鱼、人鱼鳞、龙鳞）。

更具体的两处结构性缺口：

1. **植物没有农业循环**。灵草/龙胆/彼岸花/魔法菇全是 `BushBlock`（`instabreak()`），没有种子、没有生长阶段、不能骨粉催熟——放下→打掉→原样回收，种植收益恒为零。茅野姬仪式因此只能是"一次性抽卡"。
2. **没有 mod 专属药水线**。少名渡汤之仪的 `brew_recipes` 只接受原版试剂→原版药水，`gensokyou`_low/_high 带（灵草/龙胆/彼岸花/魔法菇、瓶装三途川水/灵鱼/人鱼鳞/潮汐晶/龙鳞）被完全排除在酿造体系之外。`gensokyo-materials` spec 里"冥河瓶 SHALL 可开启为冥水"这句从未实现，与现状脱节。

同时，玩家侧缺少承接终局素材的目标：没有 mod 盔甲与 mod 工具（除 Laevatein 一把剑），也没有面向大型仪式建筑施工的辅助道具。

## What Changes

- **灵土耕地方块（新增阻塞解除项）**：新增 `spirit_soil_farmland` 灵土耕地，由锄头右键 `spirit_soil` 得到，作为 mod 作物的种植基底；其随机刻给上方作物额外生长加速。原版 `CropBlock` 的种植基底判定是否硬编码 `Blocks.FARMLAND` 由程序侧确认，若硬编码则 4 个 mod 作物覆写 `mayPlaceOn` 收录 `farmland || spirit_soil_farmland`。
- **植物种子化（BREAKING 数据变更）**：新增 `spirit_herb_seeds` / `gentian_seeds` / `higanbana_seeds` / `magic_mushroom_spores` 四种种子与对应 `CropBlock` 作物方块；**改造 `kaya_no_hime_circle.json` 的 `gensokyou_low` / `gensokyou_high` 带，产出物由植物本体改为种子**。植物本体改为农业产出的药水试剂。
- **mod 专属药水线**：新增 mod potion `crude_sanzu_potion`（粗制冥汤）与四种 mod 药水。**少名渡汤之仪路径**沿用其既有机制——祭品台放 `sanzu_flask`（即冥水）、试剂槽放 mod 植物、产物原位替换为 mod 药水；该路径需结构内放置 `magic_wood` 才解锁 mod 药水炼制。**炼药台路径**为两步酿造：`minecraft:awkward_potion` + `sanzu_flask` → 粗制冥汤，粗制冥汤 + mod 植物 → mod 药水。长效档耗 `moon_sand`、强效档耗 `porcelain`（瓷器）。`RitualBrewRule` 已有 `long_potion` / `strong_potion` 显式兄弟指针，对无命名约定的 mod 药水天然支持，数据侧零改动。
- **mod 药水效果**：新增 4 个 `MobEffect` —— 持续灵力回复、灵视（绿光穿墙显形）、灵触（触及范围+攻击距离）、彼岸花毒（80% 免伤契约型，不挡虚空，到期或被洗即死，目标不触发图腾）。
- **灵铁 / 星银工具与盔甲**：两套全套（各 5 工具 + 4 盔甲）。灵铁工具挖常见方块零耐久；星银工具自带精准采集且挖 mod 原矿概率额外掉落原矿方块。灵铁铠防御≈钻石、星银铠防御略高于下界合金，二者均带**灵力自然回复 +50%**；星银铠另有少量 `spirit_damage` 加成，**不提供任何丹幕减伤**。
- **两个妙妙工具**：① 整地工具——大范围清除地形白名单（石/土/木/叶/植被）并把地面整平为泥土，无耐久 + 长冷却，硬保护一切 TileEntity、基岩、矿石与传送门框；② 灵力引爆器——可放置实体 + 右键配置 GUI（起爆时间/强度/半径），启动消耗玩家灵力池，不足则不可启动，爆炸掉落全部被毁方块。
- **剩余素材认领**：瓷土→精炼为瓷器（mod 药水强效档必需封装耗材）、彼岸土→彼岸花/魔法菇专属基质并额外加速、月砂→mod 药水长效档必需触媒、魔法木→少名渡汤之仪解锁 long/strong 档位的不可消耗构件、常世木→`spirit_core_3/4/5` 主材。鬼石与龙鳞本轮按用户决定保留不动。
- **文档对齐**：修正 `gensokyo-materials` 中"冥河瓶可开启为冥水"的陈旧表述——瓶装三途川水**即**冥水，是 mod 药水线的独占基液，不发生"开启"。

## Capabilities

### New Capabilities
- `spirit-crop-farming`: mod 植物种子化与农业循环——种子与作物方块、灵土耕地与生长加速、彼岸土专属基质、作物掉落规则。
- `gensokyou-mod-potions`: mod 专属药水线——粗制冥汤的双路径产出、4 种 mod 药水与 long/strong 档、冥水对 mod 线的独占门槛、4 个 mod 药水效果的行为规则。
- `gensokyou-equipment-tiers`: 灵铁与星银两套工具/盔甲的数值阶梯与特技规则（零耐久挖掘白名单、精准采集、原矿追加掉落、灵力自然回复、丹幕不加防）。
- `gensokyou-utility-tools`: 整地工具与灵力引爆器的行为规则、保护清单、参数与灵力计费。

### Modified Capabilities
- `gensokyo-materials`: 植物类素材由"仪式产出"改为"农业产出"（种子由仪式出、植物由作物出）；修正冥河瓶表述；登记种子、瓷器、粗制冥汤等新物品的素材目录归属。
- `tool-sacrifice-ritual`: `kaya_no_hime_circle` 的 `gensokyou_low` / `gensokyou_high` 带产出物改为种子。
- `sunako-brew-ritual`: 允许 `gensokyou:sanzu_flask` 作为 reagent 炼出 mod potion `crude_sanzu_potion`；新增"放置魔法木才解锁 long/strong 档位"的构件规则。
- `kanayamahiko-smelting`: 新增瓷土→瓷器的精炼规则。
- `zaohua-crafting`: 新增 `spirit_core_3` / `spirit_core_4` / `spirit_core_5` 配方（常世木主材）。

## Impact

**新增注册（约 33 条目）**
- 物品（14）：4 种子、瓷器、粗制冥汤、4 mod 药水、灵铁 5 工具、星银 5 工具
- 盔甲（8）：灵铁 4 件、星银 4 件
- 方块/实体（5）：4 作物方块、`spirit_soil_farmland`、灵力引爆器实体
- 整地工具与灵力引爆器物品（2）
- 效果（4）+ 4 个 mod potion 注册

**改动**
- `ModItems` / `ModBlocks` / `ModMobEffects` / `ModCreativeTabs`
- 新增 `mod` 侧 `potion` 注册（`Registries.POTION`）
- 新增灵力引爆器实体 + `Menu`/`Screen` + 自定义爆炸逻辑
- `data/gensokyou/ritual_loot/kaya_no_hime_circle.json`（**破坏性数据变更，存档兼容**）
- `data/gensokyou/ritual_smelt_recipes/kanayamahiko_circle.json`
- `data/gensokyou/ritual_recipes/zaohua_circle.json`
- `data/gensokyou/brew_recipes/sunako_circle.json`
- 炼药台酿造配方（需新增 mod potion 的 `PotionBrewing` 注册与 4 条 mod mix）
- `assets/gensokyou/lang/{zh_cn,en_us}.json` 与全部新物品的模型/贴图
- guide book 条目（植物来源、药水线、装备线）

**风险点**
- 大半径全掉落爆炸会刷出海量 item entity，必须加产物实体数上限与半径/强度硬上限
- 彼岸花毒的"到期即死 / 洗掉即死"必须走不触发图腾的死亡路径
- 灵力引爆器是新增实体 + 新 GUI + 新爆炸逻辑，是本变更工作量最大的单点
- `spirit_soil_farmland` 作为 `FarmBlock` 变体会引入踩踏塌陷与邻水判定，需确认是否符合"永不变质承托方块"约束（`ritual-design` 手册 §3 的承托方块红线同样适用于此处）

**分期建议**：一期为种子化 + 耕地 + 药水线（先把"种植→炼药"闭环立起来并可验证）；二期为装备工具 + 两个妙妙工具。
