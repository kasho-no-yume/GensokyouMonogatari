# 占位资产清单

依据 `openspec/project.md` §5 资产规范维护。替换一项销一项（把状态改为 DONE 并注明来源）；
清单清零即占位期结束。重新提取占位可运行 `tools/extract_placeholder_assets.ps1`。
贴图再生成：数据文件在 `tools/textures/*.py`，`python tools/gen_tex.py --write-assets`。
AI 扩散生图（64×64 新绘物品）：`python tools/comfy_pixel_gen.py --spec tools/textures/ai_items.py`，见 gen-textures skill 的「AI 扩散生图管线」一节。

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

> 2026-10-21 起，「新绘物品」统一升到 **64×64**（gen-textures skill 的扩散生图甜区）。
> 来源标注 `comfy_pixel_gen` = AI 扩散生图（Z-Image Turbo + 像素 LoRA → 收口 64×64）；
> `gen_tex` = ASCII 数据文件手绘。**抄原版形状的物品（灵铁/星银 18 件工具盔甲）不参与
> AI 管线**，保持 16×16 与原版剪影对齐（见 gen-textures skill 与
> openspec/specs/gensokyou-equipment-tiers/spec.md 的「外形 MUST 与原版工具/盔甲可辨」）。

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/item/guide_book.png | TODO | 海洋之心 |
| textures/item/spellcard_frame.png | DONE(gen_tex 新绘·白纸卡框，不染层，全符卡共享)·**已无模型引用**（9 张符卡改用各自的卡纸+纹章） | 旗帜图案 |
| textures/item/spellcard_emblem.png | DONE(gen_tex 新绘·灰度法阵染层，按卡主题色 tint，全符卡共享)·**已无模型引用** | 旗帜图案 |
| textures/item/spellcard_<卡id>.png ×9 | DONE(gen_tex 新绘·**竖长方形**符卡卡纸：64×64 画布内嵌 48×64 卡纸（左右各留 8px 透明），ASCII 模板 + 每卡调色板·数据文件 tools/textures/spellcard_cards.py） | 旗帜图案 |
| textures/item/spellcard_<卡id>_emblem.png ×9 | DONE(comfy_pixel_gen AI 重绘·64×64 灰度法阵纹章，缩到 32 居中坐在卡面正中，染层，被 tint 成卡主题色) | 旗帜图案 |
| textures/item/ppoint.png | DONE(旧仓库复制 point/ppoint.png) | 海洋之心 |
| textures/item/bpoint.png | DONE(旧仓库复制 point/bpoint.png) | 海洋之心 |
| textures/item/spellcard_star.png | DONE(旧仓库复制 spellcard/spellcardstar.png) | 海洋之心 |
| textures/item/broken_spell_card_star.png | DONE(旧仓库复制 spellcard/brokenspellcardstar.png) | 海洋之心 |
| textures/item/boss_residual_codex.png | TODO（占位期间拷贝同目录 codex_of_beings.png，待正式美术） | 海洋之心 |
| textures/item/yen.png | DONE(comfy_pixel_gen AI 重绘·64×64 方孔金币) | 海洋之心 |
| textures/item/memory_fragment.png | DONE(comfy_pixel_gen AI 重绘·64×64 破损符纸+符文) | 海洋之心 |
| textures/item/laevatein.png | DONE(comfy_pixel_gen AI 重绘·64×64 红金斜剑·item/handheld 展示) | 三叉戟 |
| textures/item/ritual_wand.png | DONE(comfy_pixel_gen AI 重绘·64×64 紫檀仪式杖) | 海洋之心 |
| textures/item/danmaku_weapon.png | DONE(gen_tex 千机核心形状图标；物品本体走独立渲染器，此图仅作 particle 与静态兜底)·**不进 AI 管线** | 三叉戟 |
| textures/item/core_sphere_single.png | DONE(comfy_pixel_gen AI 重绘·64×64 绿·单发球) | 海洋之心 |
| textures/item/core_sphere_shotgun.png | DONE(comfy_pixel_gen AI 重绘·64×64 绿·散射) | 海洋之心 |
| textures/item/core_knife.png | DONE(comfy_pixel_gen AI 重绘·64×64 绿·刀形) | 海洋之心 |
| textures/item/core_talisman.png | DONE(comfy_pixel_gen AI 重绘·64×64 蓝·御札) | 海洋之心 |
| textures/item/core_laser_gun.png | DONE(comfy_pixel_gen AI 重绘·64×64 蓝·短管) | 海洋之心 |
| textures/item/core_laser_cannon.png | DONE(comfy_pixel_gen AI 重绘·64×64 金·重炮) | 海洋之心 |
| textures/item/core_qilin.png | DONE(comfy_pixel_gen AI 重绘·64×64 绿·锥形) | 弹幕核心 |
| textures/item/core_orb_shell.png | DONE(gen_tex 千机核心中性灰纵向渐变，供理球按品阶色染色) | 弹幕核心 |
| textures/item/spirit_core_0..5.png | DONE(comfy_pixel_gen AI 重绘·64×64 六阶灵力核心：**悬浮灵晶，无底座无环**；阶位靠「体积 / 晶体形态 / 伴飞碎晶数 / 蓝色闪电弧」递进，0 阶粗糙暗灰无光 → 5 阶巨大紫晶 + 5 碎晶 + 强闪电；晶内带青绿能量脉络。0 阶为灰度（无彩）) | 海洋之心 |
| textures/item/weapon_core_lv1.png | DONE(comfy_pixel_gen AI 重绘·64×64 六角棱镜·绿) | 海洋之心 |
| textures/item/weapon_core_lv2.png | DONE(comfy_pixel_gen AI 重绘·64×64 六角棱镜·蓝) | 海洋之心 |
| textures/item/weapon_core_lv3.png | DONE(comfy_pixel_gen AI 重绘·64×64 六角棱镜·金) | 海洋之心 |
| textures/item/sacred_sapling.png | DONE(comfy_pixel_gen AI 重绘·64×64 神圣之木树苗图标；**方块贴图 block/sacred_sapling.png 保持 16×16 不动**，物品模型改指 item/sacred_sapling) | — |
| textures/item/magic_sapling.png | DONE(comfy_pixel_gen AI 重绘·64×64 魔法之木树苗图标；方块贴图保持 16×16) | — |
| textures/item/eternal_sapling.png | DONE(comfy_pixel_gen AI 重绘·64×64 永远之木树苗图标；方块贴图保持 16×16) | — |
| textures/item/amp_core.png | DONE(comfy_pixel_gen AI 重绘·64×64 灰度八角托座，**程序化挖八角孔**露出染层·tint=品阶色) | 海洋之心 |
| textures/item/amp_core_dye.png | DONE(comfy_pixel_gen AI 重绘·64×64 灰度晶石，定尺寸居中坐进托座的孔·染层，tint=实例随机色) | 海洋之心 |
| textures/item/amp_core_t1/2/3.png | 已移除（t1..t3 共享 amp_core+amp_core_dye 双层染色） | 海洋之心 |

## 幻想乡素材（add-gensokyo-material-ladder，gen_tex 新绘·数据文件 tools/textures/gensokyo_materials.py）

| 路径 | 状态 | 占位来源 |
|---|---|---|
| textures/item/cinnabar.png | DONE(gen_tex 新绘·红晶矿) | 海洋之心 |
| textures/item/refined_cinnabar.png | DONE(comfy_pixel_gen AI 重绘·64×64 抛光朱砂矿石结块：圆润水滴形 + 缎面蜡质光泽 + 天然纹理与暗边，**刻意不做成金属锭**（辰砂是硫化汞矿物，与灵铁/星银的铸条形制刻意区分）) | — |
| textures/item/spirit_iron.png | DONE(comfy_pixel_gen AI 重绘·64×64 精炼青灰金属锭（梯形倒角+铸印）) | 海洋之心 |
| textures/item/spirit_iron_ore.png | DONE(gen_tex 新绘·矿石) | 海洋之心 |
| textures/item/star_silver.png | DONE(comfy_pixel_gen AI 重绘·64×64 精炼银白金属锭（梯形倒角+铸印）) | 海洋之心 |
| textures/item/star_silver_ore.png | DONE(gen_tex 新绘·矿石) | 海洋之心 |
| textures/item/oni_stone.png | DONE(gen_tex 新绘·紫灰岩) | 海洋之心 |
| textures/item/spirit_soil.png | DONE(gen_tex 新绘·土堆) | 海洋之心 |
| textures/item/porcelain_clay.png | DONE(gen_tex 新绘·瓷白土堆) | 海洋之心 |
| textures/item/higan_soil.png | DONE(gen_tex 新绘·暗红土堆) | 海洋之心 |
| textures/item/moon_sand.png | DONE(gen_tex 新绘·月黄砂堆) | 海洋之心 |
| textures/item/sacred_wood.png | DONE(gen_tex 新绘·原木) | 海洋之心 |
| textures/item/magic_wood.png | DONE(gen_tex 新绘·紫调原木) | 海洋之心 |
| textures/item/eternal_wood.png | DONE(gen_tex 新绘·青调原木) | 海洋之心 |
| textures/item/sanzu_flask.png | DONE(comfy_pixel_gen AI 重绘·64×64 冥河瓶) | 海洋之心 |
| textures/item/spirit_fish.png | DONE(comfy_pixel_gen AI 重绘·64×64 发光灵鱼) | 海洋之心 |
| textures/item/mermaid_scale.png | DONE(comfy_pixel_gen AI 重绘·64×64 珍珠鳞) | 海洋之心 |
| textures/item/dragon_scale.png | DONE(comfy_pixel_gen AI 重绘·64×64 青绿鳞) | 海洋之心 |
| textures/item/tide_crystal.png | DONE(comfy_pixel_gen AI 重绘·64×64 潮汐晶) | 海洋之心 |
| textures/item/spirit_herb.png | DONE(comfy_pixel_gen AI 重绘·64×64 灵草) | 海洋之心 |
| textures/item/gentian.png | DONE(comfy_pixel_gen AI 重绘·64×64 龙胆) | 海洋之心 |
| textures/item/higanbana.png | DONE(comfy_pixel_gen AI 重绘·64×64 彼岸花) | 海洋之心 |
| textures/item/magic_mushroom.png | DONE(comfy_pixel_gen AI 重绘·64×64 魔法菇) | 海洋之心 |
| textures/item/spirit_charcoal.png | DONE(comfy_pixel_gen AI 重绘·64×64 黑炭带绿焰) | 海洋之心 |
| textures/item/talisman_paper.png | DONE(comfy_pixel_gen AI 重绘·64×64 符纸卷) | 海洋之心 |
| textures/item/sukima_fragment.png | DONE(comfy_pixel_gen AI 重绘·64×64 隙间碎片) | 海洋之心 |

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
| textures/entity/greater_fairy.* | DONE(Blockbench 工程，F:/blockbench/touhou_fairies/大妖精) | 外部美术资源 | big_fairy（GeckoLib：geo/animation/贴图，idle/fly/walk/cast） |
| textures/entity/yamame.* | DONE(Blockbench 工程，F:/blockbench/kurodani) | 外部美术资源 | yamame（GeckoLib：geo/animation/贴图，idle/walk/cast） |
| textures/entity/yakumo_ran.* | DONE(Blockbench 工程，`C:\Users\spdimdriver\Documents\ran`；贴图已暗化 `yakumo_ran.png` + 自发光掩码 `yakumo_ran_glowmask.png`) | 外部美术资源 | yakumo_ran（GeckoLib：geo/animation/贴图，首版 idle） |
| textures/entity/nomen_mask.png | TODO | 复用 danmaku.png（lightorb） | nomen_mask（残影占位，同上） |
| textures/entity/danmaku.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 弹幕投射物 billboard |
| textures/entity/orbit_orb.png | DONE(暂定旧版贴图) | 旧仓库 lightorb.png | 环绕阴阳玉 billboard |
| textures/entity/laser_danmaku.png | DONE(gen_tex 新绘·U跨宽渐隐/V平铺能量条纹) | 占位纯色块 | 激光弹幕光束 |
| textures/entity/laser_cap.png | DONE(gen_tex 新绘·径向渐变圆斑) | 占位纯色块 | 激光弹幕端盖 |
| textures/entity/talisman_danmaku.png | DONE(gen_tex 新绘·符纸灰度可染色) | 占位纯色块 | 灵符弹幕 |
| textures/entity/knife_danmaku.png | DONE(gen_tex 新绘·竖刀刀尖朝下) | 占位纯色块 | 飞刀弹幕 |
| textures/entity/cone_danmaku.png | DONE(gen_tex 鏂扮粯路鐏板害鍥鹃泦锛屽洓鍒楀搴旈敟浣撳洓涓晶闈紝V 鍚戜负灏栫浜啋灏剧鏆? | 鍗犱綅鑹茬悆 | 閿ュ舰寮瑰箷 |
| textures/entity/sukima.png | DONE(gen_tex 新绘·16×32 眼睑轮廓) | （新资产） | 隙间传送门 BER |

## 模型占位说明

- 全部 item 模型为 `item/generated` / `item/handheld` + 自有 layer0 路径
  （武器系 13 个缺失模型已补齐：danmaku_weapon、core_*×6、weapon_core_lv*×3、amp_core_t*×3）
- 全部 block 模型为 `minecraft:block/cube_all` 结构 + 自有 all 贴图路径；
  **例外**：`models/block/sukima.json` 为空元素模型（视觉由 `SukimaPortalRenderer` BER 呈现）
- 生物渲染统一 `HumanoidModel(ModelLayers.ZOMBIE)`，经 `SkinMobRenderer` 缩放区分体型；
  **例外**：小妖精（`fairy`）走 GeckoLib（`GeoEntityRenderer` + `geo/animations/textures/entity/lesser_fairy.*`）
- 正式模型到位时仅覆盖对应文件内容，不改代码与引用
