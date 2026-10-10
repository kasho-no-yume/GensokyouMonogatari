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
- [x] 2.9 单机实测：种子种植/成熟/掉落闭环、灵土加速可感知、彼岸土基质生效、旧存档已有植物物品仍可作试剂

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

- [x] 5.1 注册灵力筑基器方块 + BlockEntity + 方块物品 + 语言键 + 贴图/模型
- [x] 5.2 实现清除白名单（石/土/木/叶/植被/沙）与长/宽/高尺寸配置，`GensokyouConfig` 提供最大/默认尺寸与冷却
- [x] 5.3 实现底层铺泥（装置内置 → 背包 → 不足只清不填）与创造模式绕过低
- [x] 5.4 实现硬保护：一切 TileEntity、基岩、矿石、黑曜石族、传送门框、mod 素材方块
- [x] 5.5 注册灵力引爆器方块 + BlockEntity（有碰撞箱与贴图，非实体）
- [x] 5.6 实现放置 / 右键开 GUI / 沉睡可收回 / 已启动不可收回 / BE 自主计时
- [x] 5.7 实现配置 GUI：起爆时间 0.1~600s、强度 1.0~12.0、半径 1~24，实时显示估算灵力，不足则启动按钮不可用
- [x] 5.8 实现 `cost = BASE × (强度/4)² × (半径/6)` 扣费（走 `SpiritPowerData.current`，全有全无），`BASE` 进配置
- [x] 5.9 实现全掉落爆炸（`ExplosionInteraction.BLOCK` 语义 + 半径二次伤害 + 方块全掉落）
- [x] 5.10 实现产物实体数上限保护（超限丢弃 + 服务端日志告警）与强度/半径硬钳制
- [x] 5.11 用 `gen_tex.py` 出引爆器沉睡/引信/临界三态占位贴图，并记录正式特效需求（符环自转、内芯脉动随时限加快、颜色随强度青→紫→红、引爆先内缩再炸开）
- [x] 5.12 灵力筑基器配置 GUI：长(X)/宽(Z)/高(Y) 三滑块 + 启动按钮 + 范围线框渲染（`LandscapingBoxRenderer`，对称展开）
- [x] 5.13 编译 + 单测：灵力筑基器/引爆器两方块与 BE、菜单、payload、渲染器全部通过
- [x] 5.14 单机实测：灵力筑基器各类保护与尺寸/线框逐条验证；引爆器参数/扣费/上限/掉落/离线计时逐条验证

## 6. 收尾

- [x] 6.1 修正 `gensokyo-materials` spec 中"冥河瓶可开启为冥水"的陈旧表述（shadow spec 已在 `specs/gensokyo-materials/spec.md` 给出 MODIFIED 内容，归档时生效）
- [x] 6.2 补 guide book 条目：植物来源变更、mod 药水线、装备线、两个妙妙工具
- [x] 6.3 全量校验 `openspec validate --strict`
- [x] 6.4 全量构建 `.\tools\gradle_task.ps1 build`
- [x] 6.5 与用户实机走查（种植闭环、药水双路径、装备特技边界、引爆器爆炸与掉落在 1.21 规则下的一致性）

## 7. 实机走查问题修复（本次追加）

- [x] 7.1 工具 tooltip 无数值：灵铁/星银 5 件工具的 `Item.Properties` 补 `.attributes(createAttributes(...))`，攻击伤害/攻速恢复可见且生效（耐久由 `TieredItem` 自动套用）
- [x] 7.2 盔甲无耐久：灵铁（×33）/星银（×40）盔甲补 `.durability(type.getDurability(multiplier))`
- [x] 7.3 灵力引爆器改实体 → 方块 + BlockEntity：新增 `SpiritBombBlock`/`SpiritBombBlockEntity`，注册 BE、方块物品改 BlockItem；删除 `SpiritBombEntity`/`SpiritBombRenderer` 与实体注册
- [x] 7.4 引爆器右键恒开界面（`useItemOn`/`useWithoutItem`），`SpiritBombMenu`/`SpiritBombConfigPayload` 改按方块实体定位
- [x] 7.5 灵力筑基器改可放置方块 + BlockEntity + GUI：新增 `LandscapingBlock`/`LandscapingBlockEntity`/`LandscapingMenu`/`LandscapingScreen`，长/宽/高对称展开
- [x] 7.6 灵力筑基器范围线框：`LandscapingBoxRenderer`（BER）逐方块绘制，尺寸经 BE 同步；配置项 `maxSize`/`defaultSize`
- [x] 7.7 payload 与注册：`LandscapingStatePayload`/`LandscapingConfigPayload` + 客户端 handler + `ModNetworking`/`ModMenus`/`GensokyouClient` 接线
- [x] 7.8 资源与语言键：引爆器/灵力筑基器方块模型与贴图、`block.*` 与 `gui.gensokyou.landscaping.*` 中英键
- [x] 7.9 编译 + 全量 `.\tools\gradle_task.ps1 build` 通过
- [x] 7.11 修复 GUI 固定显示「已不在世界上」：客户端工厂从 `openMenu(provider, pos)` 的 extraData 读回真实坐标；界面 `init()` 据此<b>一次性请求</b>状态（`*RequestPayload`），服务端回应——不再靠「开屏同 tick 发包」（会被丢），也不再每 tick 补发
- [x] 7.12 引爆器/灵力筑基器方块模型改异形：引爆器为分层圆弹体 + 引信；灵力筑基器为「底部工具头 + 上立手柄」，逐面 UV 采样自身贴图
- [x] 7.13 两方块碰撞/选取/遮蔽形状贴合模型（引爆器 2~14 方柱、灵力筑基器头+柄），且 `getLightBlock=0` + `propagatesSkylightDown=true`，消除「小模型占满整格」的整块阴影/地面方形投影
- [x] 7.14 修复拖动被强制刷回/界面闪烁：移除每 tick 补发；状态只在界面 init 请求与参数提交时各回发一次，客户端仅在服务端值真正变化时回写滑块
- [x] 7.15 修复滑块无法拖动：本版本 `AbstractContainerScreen.mouseDragged` 恒吞且不转发给聚焦控件；两个配置界面覆写 `mouseDragged` 把拖动交给 `getFocused()`
- [x] 7.16 修复绿框预览不更新：`LandscapingBlockEntity.setParams` 补 `syncToClients()`（此前只 `setChanged()`，客户端 BE 参数不变）
- [x] 7.17 灵力筑基器模型发黑：模型逐面 UV 采样的是带透明像素的工具贴图，默认 solid 渲染层把透明处涂黑；UV 收窄到纯不透明区 + 该方块渲染层设为 `cutout`
- [x] 7.18 引爆器基础消耗改为 555（含 run 配置同步）：满强度/半径 = 555 × 9 × 4 ≈ 20000
- [x] 7.19 引爆器起爆后本体掉回物品（爆炸之后再生成，避免被炸飞/炸没）
- [x] 7.20 灵力筑基器参数：长(X)/宽(Z) 各自独立可调、以方块为中心；高(Y) 以方块为底向上。config 用 maxSize/maxHeight/defaultSize/defaultHeight
- [x] 7.21 灵力筑基器线框两趟绘制：深度测试实色 + 关深度 alpha 0.5，被遮挡处半透明
- [x] 7.22 灵力筑基器白名单加入树叶（`BlockTags.LEAVES`）
- [x] 7.23 灵力筑基器地板改为「方块下面一格」（`boxMin.y = pos.y - 1`，盒体自该层向上）
- [x] 7.24 灵力筑基器改名（`item/block/gui` 显示名；id 仍 `landscaping_tool`）
- [x] 7.25 线框改在 `RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS)` 绘制（不再走 BER，BER 阶段早于水会被水盖住）；实例经 `LandscapingBlockEntity.clientActive()` 弱引用集合遍历
- [x] 7.26 文档统一改名为「灵力筑基器」
- [x] 7.27 灵力筑基器启动消耗灵力 = 基数 × 长 × 宽 × 高（基数默认 4，拉满 32×32×24 ≈ 10 万）；GUI 显示估算消耗、不足则启动按钮置灰
- [x] 7.28 灵力筑基器改为「平等删除」：除仪式结构（仪式石族/核心/祭品台/归元晶/隙间）与基岩外一律删除；底层铺泥不覆盖基岩与仪式结构
- [x] 7.29 补指导书条目：灵力筑基器、灵力引爆器（条目 JSON + 中英文本）
- [x] 7.10 单机实测：工具 tooltip 数值、引爆器碰撞/右键/起爆、灵力筑基器放置/右键/尺寸/线框/保护逐条验证

## 8. 第二轮实机走查问题修复（本次追加）

- [x] 8.1 补 4 个 mod 药水效果图标：`textures/mob_effect/{reiki_recovery,spiritual_sight,spirit_touch,higanbana_poison}.png`（18x18，`tools/textures/mod_effects.py` + `gen_tex.py`）
- [x] 8.2 修复彼岸花毒"到期即死"失效：原版自然到期只发 `MobEffectEvent.Expired`（`tickEffects` 直接 `iterator.remove()`，不经过 `removeEffect()`，故<b>不发</b> `Remove`）。补接 `Expired`，与 `Remove`（牛奶/指令）共用同一 `killByContract` 路径
- [x] 8.3 去掉灵视/彼岸花毒的"II"：删 `strong_spiritual_sight` / `strong_higanbana_poison` 注册与炼制配方；新增 `NoAmplifierEffect` 标记接口，`PotionTierTransform` 对之只延时不拔品质（原版夜视口径）
- [x] 8.4 回灵汤改瞬发：`ReikiRecoveryEffect.isInstantenous()=true` + `applyInstantenousEffect` 一次性回复「max × 配置比例 × (品质+1)」（I=10%/II=20%）；删 `long_reiki_recovery`（时长对瞬发无意义，原版瞬间治疗口径）；config `reikiRecoveryPerSecond` → `reikiRecoveryPercent`
- [x] 8.5 移除少名渡汤 mod 试剂的 `magic_wood` 构件门槛：删 `SunakoBrewing.hasBrewingCore`/`isModReagent` 及 GUI `no_magic_wood` 状态行；mod 试剂与其它试剂走完全相同的结算路径
- [x] 8.6 补 mod 药水的喷溅/滞留/药箭语言键（`item.minecraft.{splash_potion,lingering_potion,tipped_arrow}.effect.<id>`），并清理已删除档位的旧键
- [x] 8.7 灵视生效距离改为至少覆盖服务端视野距离（`viewDistance × 16` 格），保证"被服务端绘制给客户端的实体都能看到"；`spiritualSightRadius` 降级为额外下限
- [x] 8.8 更新单测（`BrewReagentIndexTest` 档位形状、`PotionTierTransformTest` 品质无意义效果）与 `.\tools\gradle_task.ps1 build` 全绿
