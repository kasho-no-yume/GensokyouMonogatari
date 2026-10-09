## 1. 程序侧前置确认（阻塞项）

- [x] 1.1 确认原版 `CropBlock` 的种植基底判定形态（`mayPlaceOn` 是否硬编码 `Blocks.FARMLAND`），决定 `spirit_soil_farmland` 是否必须走 `FarmBlock` 路线
- [x] 1.2 确认"仪式结构内方块构件扫描"是否有现成路径（先例 `ToolSacrificeBehavior.countGuideBooks` 扫的是物品）；若无则确定为"祭品台放 `magic_wood` 原木物品"的退化方案
- [x] 1.3 确认 `BrewReagentIndex` 能否直接接受 mod potion id 作为 `potion` 字段（`RitualBrewRule` 的 `Holder<Potion>` 是否覆盖 `Registries.POTION` 的 mod 条目）
- [x] 1.4 确认 neoforge `block_interaction_range` / `entity_interaction_range` 属性在本版本可用作 `MobEffect` 的属性修饰通道

## 2. 一期 · 植物种子化与灵土农业线

- [x] 2.1 注册 4 个种子物品 `spirit_herb_seeds` / `gentian_seeds` / `higanbana_seeds` / `magic_mushroom_spores` + 中英语言键 + 物品模型
- [x] 2.2 注册 4 个作物方块 `*_crop`（`extends CropBlock`，6 阶段，含各阶段方块状态与模型），覆写 `mayPlaceOn` 收录 `farmland || spirit_soil_farmland`；`higanbana_crop` / `magic_mushroom_crop` 额外收录 `higan_soil`
- [x] 2.3 配置作物掉落：成熟 → 1 植物 + 1~2 种子（时运）；未成熟 → 1 种子
- [x] 2.4 新增方块 `spirit_soil_farmland`（灵土耕地）：锄 `spirit_soil` 得到，破坏掉落 `spirit_soil`，贴图与方块状态
- [x] 2.5 实现 `spirit_soil_farmland.randomTick` 生长加速，读基底区分 `higan_soil` 额外倍率；倍率与概率进 `GensokyouConfig`
- [x] 2.6 修改 `data/gensokyou/ritual_loot/kaya_no_hime_circle.json`：`gensokyou_low` → 三种种子、`gensokyou_high` → `magic_mushroom_spores`
- [x] 2.7 用 `tools/gen_tex.py` 补 4 个种子物品贴图、4 个作物各阶段贴图、灵土耕地贴图
- [x] 2.8 编译：`.\tools\gradle_task.ps1 compileJava`
- [ ] 2.9 单机实测：种子种植/成熟/掉落闭环、灵土加速可感知、彼岸土基质生效、旧存档已有植物物品仍可作试剂

## 3. 一期 · mod 药水线与素材认领

- [x] 3.1 注册 mod potion 5 项：`crude_sanzu_potion` / `reiki_recovery` / `spiritual_sight` / `spirit_touch` / `higanbana_poison`，含中英 potion 名与效果名语言键
- [x] 3.2 注册 4 个 `MobEffect` 并实现：持续灵力回复、灵视（`glowing` + 队伍色）、灵触（交互范围属性）、彼岸花毒（80% 免伤 + 契约死亡）
- [x] 3.3 实现彼岸花毒的死亡路径：不挡虚空、不挡 `/kill`；到期/被洗即死；走 `setHealth(0)` + `die()` 绕开图腾，实测图腾不触发
- [x] 3.4 新增炼药台酿造配方：`awkward_potion` + `sanzu_flask` → `crude_sanzu_potion`；`crude_sanzu_potion` + 4 种植物 → 4 种 mod 药水
- [x] 3.5 新增档位配方：长效耗 `moon_sand`、强效耗 `porcelain`，各恰好 1 个
- [x] 3.6 在 `data/gensokyou/brew_recipes/sunako_circle.json` 增补 4 条 mod 试剂条目，并实现"结构内有 `magic_wood` 才接受 mod 试剂"
- [x] 3.7 在 `data/gensokyou/ritual_smelt_recipes/kanayamahiko_circle.json` 增补瓷土 → 瓷器精炼规则
- [x] 3.8 在 `data/gensokyou/ritual_recipes/zaohua_circle.json` 增补 `spirit_core_3/4/5` 三条配方（常世木主材 + ≥1 件其它幻想乡素材）
- [x] 3.9 补齐创造标签页归属、全部新物品的贴图与模型（`gen_tex.py`）
- [x] 3.10 编译：`.\tools\gradle_task.ps1 compileJava`
- [x] 3.11 单机实测：两条产出路径均得 mod 药水、无魔法木时仪式拒绝 mod 试剂、长效/强效档素材门槛生效、原版炉炼不出瓷器、彼岸花毒四条规则逐条验证

## 4. 二期 · 灵铁与星银装备

- [x] 4.1 新增两个 tool tier：灵铁（512 耐久 / 8.0 / +3 / 钻石级 / 附魔 10）、星银（2048 / 12.0 / +4 / 下界合金级 / 附魔 18），修复素材为对应金属
- [x] 4.2 注册两套各 5 件工具（剑/镐/斧/锹/锄）+ 4 件盔甲 + 语言键 + 模型 + 贴图
- [x] 4.3 实现灵铁工具"常见方块零耐久"：白名单取原版 `mineable` 相关 tag 与石/土/木/叶/沙/植物交集，排除矿石、mod 素材方块与命名方块
- [x] 4.4 实现星银工具自带精准采集（不走附魔通道）+ 挖 mod 原矿概率追加掉落 1 个原矿方块（概率进配置）
- [x] 4.5 实现两套盔甲的灵力自然回复 +50%（走 `regenBuffer` 等价通道），并验证不改变 `max` 与 `spirit_damage`
- [x] 4.6 实现星银铠整套穿戴时的 `spirit_damage` +8%，单件不生效；确认无任何减伤属性
- [x] 4.7 增补锻造/源初造化配方（`gensokyou:eternal_wood` 等等级材料 + 凡材），须满足 `gensokyo-materials` 的"mod 独有品含 ≥1 mod 料"与"阶级物品含对应阶级 mod 材"
- [x] 4.8 编译 + 单机实测：零耐久边界（石头零、矿石照常）、精准采集、追加掉落、回复加成、套装判定、无减伤红线

## 5. 二期 · 两个妙妙工具

- [x] 5.1 注册整地工具物品 + 语言键 + 贴图
- [x] 5.2 实现清除白名单（石/土/木/叶/植被/沙）与半径配置，`GensokyouConfig` 提供半径与冷却
- [x] 5.3 实现泥土整平（工具内置 → 背包 → 不足只清不填）与创造模式绕过低
- [x] 5.4 实现硬保护：一切 TileEntity、基岩、矿石、黑曜石族、传送门框、自定义名方块
- [x] 5.5 注册灵力引爆器物品 + 实体（`SpiritBombEntity`，非 `PrimedTnt`）
- [x] 5.6 实现右键放置 / 右键开 GUI / 沉睡可收回 / 已启动不可收回 / 实体自主计时
- [x] 5.7 实现配置 GUI：起爆时间 0.1~600s、强度 1.0~12.0、半径 1~24，实时显示估算灵力，不足则启动按钮不可用
- [x] 5.8 实现 `cost = BASE × (强度/4)² × (半径/6)` 扣费（走 `SpiritPowerData.current`，全有全无），`BASE` 进配置
- [x] 5.9 实现全掉落爆炸（`ExplosionInteraction.BLOCK` 语义或自定义 `Explosion` 遍历 `dropResources`）
- [x] 5.10 实现产物实体数上限保护（超限丢弃 + 服务端日志告警）与强度/半径硬钳制
- [x] 5.11 用 `gen_tex.py` 出引爆器沉睡/引信/临界三态占位贴图，并记录正式特效需求（符环自转、内芯脉动随时限加快、颜色随强度青→紫→红、引爆先内缩再炸开）
- [ ] 5.12 编译 + 单机实测：整地工具各类保护逐条验证；引爆器参数/扣费/上限/掉落/离线计时逐条验证

## 6. 收尾

- [x] 6.1 修正 `gensokyo-materials` spec 中"冥河瓶可开启为冥水"的陈旧表述（shadow spec 已在 `specs/gensokyo-materials/spec.md` 给出 MODIFIED 内容，归档时生效）
- [x] 6.2 补 guide book 条目：植物来源变更、mod 药水线、装备线、两个妙妙工具
- [x] 6.3 全量校验 `openspec validate --strict`
- [x] 6.4 全量构建 `.\tools\gradle_task.ps1 build`
- [ ] 6.5 与用户实机走查（种植闭环、药水双路径、装备特技边界、引爆器爆炸与掉落在 1.21 规则下的一致性）
