# 占位资产清单

依据 `openspec/project.md` §5 资产规范维护。替换一项销一项（把状态改为 DONE 并注明来源）；
清单清零即占位期结束。重新提取占位可运行 `tools/extract_placeholder_assets.ps1`。
贴图再生成：数据文件在 `tools/textures/*.py`，`python tools/gen_tex.py --write-assets`。

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
| textures/item/spellcard_frame.png | DONE(gen_tex 新绘·白纸卡框，不染层，全符卡共享) | 旗帜图案 |
| textures/item/spellcard_emblem.png | DONE(gen_tex 新绘·灰度法阵染层，按卡主题色 tint，全符卡共享) | 旗帜图案 |
| textures/item/musou_fuuin.png | 已移除（改共享 spellcard_frame/emblem 双层染色） | 旗帜图案 |
| textures/item/light_reflect.png | 已移除（改共享 spellcard_frame/emblem 双层染色） | 旗帜图案 |
| textures/item/ppoint.png | DONE(旧仓库复制 point/ppoint.png) | 海洋之心 |
| textures/item/bpoint.png | DONE(旧仓库复制 point/bpoint.png) | 海洋之心 |
| textures/item/spellcard_star.png | DONE(旧仓库复制 spellcard/spellcardstar.png) | 海洋之心 |
| textures/item/broken_spell_card_star.png | DONE(旧仓库复制 spellcard/brokenspellcardstar.png) | 海洋之心 |
| textures/item/yen.png | TODO | 海洋之心 |
| textures/item/memory_fragment.png | DONE(gen_tex 新绘·破损书页+符文) | 海洋之心 |
| textures/item/laevatein.png | TODO | 三叉戟 |
| textures/item/summon_catalyst.png | TODO | 海洋之心 |
| textures/item/icicle_fall.png | 已移除（改共享 spellcard_frame/emblem 双层染色） | 海洋之心 |
| textures/item/cirno_catalyst.png | TODO | 海洋之心 |
| textures/item/ritual_wand.png | TODO | 海洋之心 |
| textures/item/danmaku_weapon.png | DONE(gen_tex 新绘「灵装发射器·雏」) | 三叉戟 |
| textures/item/core_sphere_single.png | DONE(gen_tex 新绘·绿/单点图标) | 海洋之心 |
| textures/item/core_sphere_shotgun.png | DONE(gen_tex 新绘·绿/三点图标) | 海洋之心 |
| textures/item/core_knife.png | DONE(gen_tex 新绘·绿/刀形图标) | 海洋之心 |
| textures/item/core_talisman.png | DONE(gen_tex 新绘·蓝/符形图标) | 海洋之心 |
| textures/item/core_laser_gun.png | DONE(gen_tex 新绘·蓝/短管图标) | 海洋之心 |
| textures/item/core_laser_cannon.png | DONE(gen_tex 新绘·金/十字图标) | 海洋之心 |
| textures/item/weapon_core_lv1.png | DONE(gen_tex 新绘·六角棱镜绿·1刻痕) | 海洋之心 |
| textures/item/weapon_core_lv2.png | DONE(gen_tex 新绘·六角棱镜蓝·2刻痕) | 海洋之心 |
| textures/item/weapon_core_lv3.png | DONE(gen_tex 新绘·六角棱镜金·3刻痕) | 海洋之心 |
| textures/item/amp_core.png | DONE(gen_tex 新绘·金属托座不染层) | 海洋之心 |
| textures/item/amp_core_dye.png | DONE(gen_tex 新绘·灰度晶石染层，运行期按品阶 tint) | 海洋之心 |
| textures/item/amp_core_t1/2/3.png | 已移除（t1..t3 共享 amp_core+amp_core_dye 双层染色） | 海洋之心 |

## 幻想乡素材（add-gensokyo-material-ladder，gen_tex 新绘·数据文件 tools/textures/gensokyo_materials.py）

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/item/cinnabar.png | DONE(gen_tex 新绘·红晶矿) | 海洋之心 |
| textures/item/spirit_iron.png | DONE(gen_tex 新绘·青灰金属·成品) | 海洋之心 |
| textures/item/spirit_iron_ore.png | DONE(gen_tex 新绘·矿石) | 海洋之心 |
| textures/item/star_silver.png | DONE(gen_tex 新绘·银白金属·成品) | 海洋之心 |
| textures/item/star_silver_ore.png | DONE(gen_tex 新绘·矿石) | 海洋之心 |
| textures/item/oni_stone.png | DONE(gen_tex 新绘·紫灰岩) | 海洋之心 |
| textures/item/spirit_soil.png | DONE(gen_tex 新绘·土堆) | 海洋之心 |
| textures/item/porcelain_clay.png | DONE(gen_tex 新绘·瓷白土堆) | 海洋之心 |
| textures/item/higan_soil.png | DONE(gen_tex 新绘·暗红土堆) | 海洋之心 |
| textures/item/moon_sand.png | DONE(gen_tex 新绘·月黄砂堆) | 海洋之心 |
| textures/item/sacred_wood.png | DONE(gen_tex 新绘·原木) | 海洋之心 |
| textures/item/magic_wood.png | DONE(gen_tex 新绘·紫调原木) | 海洋之心 |
| textures/item/eternal_wood.png | DONE(gen_tex 新绘·青调原木) | 海洋之心 |
| textures/item/sanzu_flask.png | DONE(gen_tex 新绘·冥河瓶) | 海洋之心 |
| textures/item/spirit_fish.png | DONE(gen_tex 新绘·发光灵鱼) | 海洋之心 |
| textures/item/mermaid_scale.png | DONE(gen_tex 新绘·珍珠鳞) | 海洋之心 |
| textures/item/dragon_scale.png | DONE(gen_tex 新绘·青绿鳞) | 海洋之心 |
| textures/item/tide_crystal.png | DONE(gen_tex 新绘·潮汐晶) | 海洋之心 |
| textures/item/spirit_herb.png | DONE(gen_tex 新绘·灵草) | 海洋之心 |
| textures/item/gentian.png | DONE(gen_tex 新绘·龙胆) | 海洋之心 |
| textures/item/higanbana.png | DONE(gen_tex 新绘·彼岸花) | 海洋之心 |
| textures/item/magic_mushroom.png | DONE(gen_tex 新绘·魔法菇) | 海洋之心 |
| textures/item/spirit_charcoal.png | DONE(gen_tex 新绘·灵炭) | 海洋之心 |
| textures/item/talisman_paper.png | DONE(gen_tex 新绘·符纸) | 海洋之心 |
| textures/item/sukima_fragment.png | DONE(gen_tex 新绘·隙间碎片) | 海洋之心 |

## 方块贴图（目标：正式方块材质）

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/block/capacitor.png | TODO | 钻石块 |
| textures/block/generator_core.png | TODO | 钻石块 |
| textures/block/spirit_relay.png | TODO | 钻石块 |
| textures/block/processing_core.png | TODO | 钻石块 |
| textures/block/tempering_altar.png | TODO | 钻石块 |
| textures/block/ritual_core.png | DONE(gen_tex 新绘；另备 `_0.._5` 品阶变体，基础图=`_0` 灰版；单方块 tier 属性切变体) | 钻石块 |
| textures/block/ritual_pedestal_0..5.png | DONE(gen_tex 新绘；每品阶独立方块，另备 `_top/_bottom_N` 分面变体，无无后缀基础版) | 钻石块 |
| textures/block/ritual_stone_0..5.png | DONE(gen_tex 新绘石砖+品阶色眼形符文；每品阶独立方块，无无后缀基础版) | 钻石块 |
| textures/block/sukima.png | 已移除（传送门视觉改 BER 渲染，见 entity/sukima.png；方块模型为空） | 钻石块 |

## 实体贴图（目标：角色皮肤；模型当前统一为史蒂夫人形占位）

| 路径 | 状态 | 占位来源 | 使用者 |
|---|---|---|---|
| textures/entity/flandre.png | TODO | Alex 皮肤 | flandre / fake_flandre |
| textures/entity/fairy.png | TODO | Alex 皮肤 | big_fairy / cirno（小妖精已改 GeckoLib） |
| textures/entity/lesser_fairy.png | DONE(外部 Bedrock 模型贴图，cutout) | 外部美术资源 | fairy（GeckoLib） |
| textures/entity/lesser_fairy.*（复用） | 复用(占位) | 复用妖精 geo/动画/贴图 | balance_test_boss（测试 BOSS，GeckoLib 模型复用 lesser_fairy，仅放大） |
| textures/entity/big_fairy.png | TODO | Alex 皮肤 | big_fairy |
| textures/entity/danmaku.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 弹幕投射物 billboard |
| textures/entity/orbit_orb.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 环绕阴阳玉 billboard |
| textures/entity/laser_danmaku.png | DONE(gen_tex 新绘·U跨宽渐隐/V平铺能量条纹) | 占位纯色块 | 激光弹幕光束 |
| textures/entity/laser_cap.png | DONE(gen_tex 新绘·径向渐变圆斑) | 占位纯色块 | 激光弹幕端盖 |
| textures/entity/talisman_danmaku.png | DONE(gen_tex 新绘·符纸灰度可染色) | 占位纯色块 | 灵符弹幕 |
| textures/entity/knife_danmaku.png | DONE(gen_tex 新绘·竖刀刀尖朝下) | 占位纯色块 | 飞刀弹幕 |
| textures/entity/sukima.png | DONE(gen_tex 新绘·16×32 眼睑轮廓) | （新资产） | 隙间传送门 BER |

## 模型占位说明

- 全部 item 模型为 `item/generated` / `item/handheld` + 自有 layer0 路径
  （武器系 13 个缺失模型已补齐：danmaku_weapon、core_*×6、weapon_core_lv*×3、amp_core_t*×3）
- 全部 block 模型为 `minecraft:block/cube_all` 结构 + 自有 all 贴图路径；
  **例外**：`models/block/sukima.json` 为空元素模型（视觉由 `SukimaPortalRenderer` BER 呈现）
- 生物渲染统一 `HumanoidModel(ModelLayers.ZOMBIE)`，经 `SkinMobRenderer` 缩放区分体型；
  **例外**：小妖精（`fairy`）走 GeckoLib（`GeoEntityRenderer` + `geo/animations/textures/entity/lesser_fairy.*`）
- 正式模型到位时仅覆盖对应文件内容，不改代码与引用
