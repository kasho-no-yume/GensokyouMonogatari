# 占位资产清单

依据 `openspec/project.md` §5 资产规范维护。替换一项销一项（把状态改为 DONE 并注明来源）；
清单清零即占位期结束。重新提取占位可运行 `tools/extract_placeholder_assets.ps1`。

## 占位来源

| 来源 | 原版路径 |
|---|---|
| 三叉戟(武器类) | assets/minecraft/textures/item/trident.png |
| 下界合金胸甲(盔甲类) | assets/minecraft/textures/item/netherite_chestplate.png |
| 旗帜图案(符卡类) | assets/minecraft/textures/item/flower_banner_pattern.png |
|---|---|
| 海洋之心 | assets/minecraft/textures/item/heart_of_the_sea.png |
| 钻石块 | assets/minecraft/textures/block/diamond_block.png |
| Alex 皮肤(slim) | assets/minecraft/textures/entity/player/slim/alex.png |

## 物品贴图（目标：正式物品图标）

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/item/guide_book.png | TODO | 海洋之心 |
| textures/item/musou_fuuin.png | TODO | 旗帜图案 |
| textures/item/light_reflect.png | TODO | 旗帜图案 |
| textures/item/ppoint.png | TODO | 海洋之心 |
| textures/item/bpoint.png | TODO | 海洋之心 |
| textures/item/spellcard_star.png | TODO | 海洋之心 |
| textures/item/broken_spell_card_star.png | TODO | 海洋之心 |
| textures/item/yen.png | TODO | 海洋之心 |
| textures/item/laevatein.png | TODO | 三叉戟 |
| textures/item/summon_catalyst.png | TODO | 海洋之心 |
| textures/item/icicle_fall.png | TODO | 海洋之心 |
| textures/item/cirno_catalyst.png | TODO | 海洋之心 |

## 方块贴图（目标：正式方块材质）

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/block/capacitor.png | TODO | 钻石块 |
| textures/block/generator_core.png | TODO | 钻石块 |
| textures/block/spirit_relay.png | TODO | 钻石块 |
| textures/block/processing_core.png | TODO | 钻石块 |
| textures/block/tempering_altar.png | TODO | 钻石块 |
| textures/block/ritual_core.png | TODO | 钻石块 |
| textures/block/ritual_stone.png | TODO | 钻石块 |

## 实体贴图（目标：角色皮肤；模型当前统一为史蒂夫人形占位）

| 路径 | 状态 | 占位来源 | 使用者 |
|---|---|---|---|
| textures/entity/flandre.png | TODO | Alex 皮肤 | flandre / fake_flandre |
| textures/entity/fairy.png | TODO | Alex 皮肤 | fairy |
| textures/entity/big_fairy.png | TODO | Alex 皮肤 | big_fairy |
| textures/entity/danmaku.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 弹幕投射物 billboard |
| textures/entity/orbit_orb.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 环绕阴阳玉 billboard |

## 模型占位说明

- 全部 item 模型为 `item/generated` / `item/handheld` + 自有 layer0 路径
- 全部 block 模型为 `minecraft:block/cube_all` 结构 + 自有 all 贴图路径
- 生物渲染统一 `HumanoidModel(ModelLayers.ZOMBIE)`，经 `SkinMobRenderer` 缩放区分体型
- 正式模型到位时仅覆盖对应文件内容，不改代码与引用
