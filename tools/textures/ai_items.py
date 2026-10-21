# ai_items.py - ComfyUI 像素生图规格（tools/comfy_pixel_gen.py 用）
#
# 每项：prompt（正面）/ seed（可复现）/ size（目标像素栅格）/ colors（量化色数）
# prompt 开头统一用 "pxlstl, pixel art" 触发本机的 Z-Image 像素 LoRA，
# 结尾由 comfy_pixel_gen.py 自动补 PROMPT_TAIL（硬边 / 少色 / 满画幅 / 纯色底）。
#
# 【重要约定】
#   - 所有贴图按项目 gen-textures skill 规范走 64×64（新绘物品的「扩散生图甜区」）。
#     item/generated 不声明尺寸，换尺寸只改 PNG，不动 Java 与模型 JSON。
#   - 「染色层」必须是灰度：MC 的 tint 是 RGB 乘法，灰度底 × 主题色 = 同明暗的彩色。
#     故带 grayscale: True 的项（符卡纹章）不能带任何固有色相。
#   - 「数据图」一律不碰：amp_core / amp_core_dye / core_orb_shell 是染色层/遮罩，
#     spellcard_frame / spellcard_emblem 是共享底图（保留在原处，不被任何模型引用）。
#   - 典籍（guide_book / codex_of_beings / boss_residual_codex）、
#     bpoint / ppoint、spellcard_star / broken_spell_card_star 按约定不改。
#
# 【弹幕核的两种保真手段】
#   1. 品阶配色写进 prompt（1 绿 / 2 蓝 / 3 琥珀），保持玩家的颜色识别。
#   2. 左下 3×3「类型微图标」AI 画不出，用 overlays 从原 16×16 贴图抠出放大贴回
#      （源图坐标写 box，目标坐标写 dest，scale 放大倍数）。

ITEMS = {
    # ================================================================ 弹幕核
    # 类型微图标 box/dest 见各自 overlays；dest 统一贴到成品左下角。
    #
    # 【取向】本 mod 是「幻想魔法」定位：弹幕核是魔法造物，不是机械零件。
    # 所以激光类不能画成枪/炮（那会变科技风），要画成「法阵聚焦器 / 符具 / 灵晶」。
    # 剪影必须一眼可辨：飞刀核要像飞刀，麟弹核要像锥形弹，散弹核要像 5 发扇形弹丸。
    "item/core_sphere_single": {
        "prompt": (
            "a single bullet core, one perfectly round glowing green energy orb seated in "
            "a small dark metal cradle with brass trim"
        ),
        "overlays": [{"source": "item/core_sphere_single",
                      "box": [0, 12, 3, 15], "dest": [0, 52], "scale": 4}],
    },
    "item/core_sphere_shotgun": {
        "prompt": (
            "five separate round green bullet orbs, spread far apart in a wide V shaped fan, "
            "each smooth green sphere clearly separated from the others by empty space, "
            "seen from behind as they fly away"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, person, human, "
            "hand, arm, character, multiple objects, tiny object, small object, distant object, "
            "excessive empty space, text, letters, watermark, signature, ui, border, frame, "
            "3d render, octane, unreal engine, blurry, jpeg artifacts, "
            "fungi, mushroom, plant, stem, stalk, tentacle, sprout, flower, "
            "firework, explosion, sparkler, radial burst, sun rays, "
            "cluster, bunch, grapes, pile, touching, overlapping"
        ),
        "overlays": [{"source": "item/core_sphere_shotgun",
                      "box": [0, 12, 3, 15], "dest": [0, 52], "scale": 4}],
    },
    "item/core_knife": {
        "prompt": (
            "a flying knife projectile, a long thin straight throwing knife with the blade "
            "pointing straight down, polished silver white blade with a faint green glowing "
            "edge, a small crossguard and a short dark handle at the top"
        ),
        "pad": True,
        "overlays": [{"source": "item/core_knife",
                      "box": [0, 12, 3, 15], "dest": [0, 52], "scale": 4}],
    },
    "item/core_qilin": {
        "prompt": (
            "a cone bullet core, a wide flat arrowhead shaped dart pointing to the right, a "
            "sharp triangular tip, bright white star glint at the point, green plate and a "
            "dark metal mount at the rear"
        ),
        "pad": True,
        "overlays": [{"source": "item/core_qilin",
                      "box": [0, 12, 3, 15], "dest": [0, 52], "scale": 4}],
    },
    "item/core_talisman": {
        "prompt": (
            "a vertical red paper talisman, a tall narrow rectangle of vermilion ofuda "
            "paper standing upright, a dense column of many small meaningless curled "
            "spiral doodles and squiggle sigils painted in black down the middle, a small "
            "gold seal near the bottom, torn paper edges, no frame and no holder"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, person, human, "
            "hand, arm, character, multiple objects, tiny object, small object, distant object, "
            "excessive empty space, watermark, signature, ui, border, frame, "
            "3d render, octane, unreal engine, blurry, jpeg artifacts, "
            "text, letters, words, kanji, chinese characters, japanese characters, "
            "alphabet, writing, calligraphy, readable characters, typography, "
            "medallion, token, pendant, badge, round, circle, coin, amulet in a frame"
        ),
        "overlays": [{"source": "item/core_talisman",
                      "box": [0, 13, 2, 15], "dest": [0, 56], "scale": 4}],
    },
    "item/core_laser_gun": {
        "prompt": (
            "a magic focusing focus, a slender blue crystal prism set in an ornate brass "
            "claw mount, engraved with glowing runes, a short dark wooden grip at the "
            "bottom, arcane sorcery device, not a firearm"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, person, human, "
            "hand, arm, character, multiple objects, tiny object, small object, distant object, "
            "excessive empty space, text, letters, watermark, signature, ui, border, frame, "
            "3d render, octane, unreal engine, blurry, jpeg artifacts, "
            "gun, pistol, rifle, firearm, shotgun, cannon, sci-fi, futuristic, technology, "
            "circuit board, neon, chrome, steel barrel"
        ),
        "overlays": [{"source": "item/core_laser_gun",
                      "box": [0, 13, 3, 14], "dest": [0, 60], "scale": 4}],
    },
    "item/core_laser_cannon": {
        "prompt": (
            "a magic focusing prism, a large blue crystal lens held in an ornate brass "
            "claw stand engraved with glowing runes, an amplifier of sorcery, not a weapon"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, person, human, "
            "hand, arm, character, multiple objects, tiny object, small object, distant object, "
            "excessive empty space, text, letters, watermark, signature, ui, border, frame, "
            "3d render, octane, unreal engine, blurry, jpeg artifacts, "
            "gun, cannon, barrel, sci-fi, futuristic, technology, circuit board, neon, chrome"
        ),
        "overlays": [{"source": "item/core_laser_cannon",
                      "box": [1, 12, 4, 15], "dest": [0, 52], "scale": 4}],
    },

    # ================================================================ 增幅核（双层染层）
    # layer0 = 金属托座（灰度，tint=品阶色）/ layer1 = 晶石（灰度，tint=实例随机色）。
    # 两层都必须是**灰度**：MC 的 tint 是 RGB 乘法，带固有色相会把品阶/随机色染脏。
    # 对齐靠代码不靠运气：托座程序化挖孔（hole），晶石定尺寸居中（fit）。
    "item/amp_core": {
        "prompt": (
            "an octagonal metal mount wheel for holding a gem, brushed grey metal with rivets "
            "around the rim, a mounting bracket, a socket in the middle"
        ),
        "grayscale": True,
        "hole": 22,
        "hole_shape": "octagon",
    },
    "item/amp_core_dye": {
        "prompt": (
            "a cut crystal gem, a square faceted jewel with a bright white star glint in "
            "the upper left corner, sharp clean facets"
        ),
        "grayscale": True,
        "fit": 28,
    },

    # ================================================================ 武器等级核
    # 统一六角棱镜外形 + 品阶配色；白色刻痕数 = 等级（1/2/3），写进 prompt。
    "item/weapon_core_lv1": {
        "prompt": (
            "a weapon level core, a hexagonal prism energy cell, dark metal rim, a small "
            "green faceted gem in the center with exactly one short white notch line on the "
            "upper gem facet"
        ),
    },
    "item/weapon_core_lv2": {
        "prompt": (
            "a weapon level core, a hexagonal prism energy cell, dark metal rim, a bright "
            "blue faceted gem in the center with exactly two short white notch lines on the "
            "upper gem facet"
        ),
    },
    "item/weapon_core_lv3": {
        "prompt": (
            "a weapon level core, a hexagonal prism energy cell, ornate brass rim, a large "
            "amber faceted gem in the center with exactly three short white notch lines on "
            "the upper gem facet"
        ),
    },

    # ================================================================ 灵力核心（电池）
    # 0 阶无彩（灰度）→ grayscale: True；1..5 阶分别为绿 / 蓝 / 琥珀 / 红 / 紫。
    # 【无底座】核心是「悬浮的灵晶」，不是插在金属座上的宝石 —— 不要底座/托架/环。
    # 【蓝绿脉络】晶体内部要有蓝绿色能量脉络，表示灵力在流动。
    "item/spirit_core_0": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a small rough uncut grey crystal shard, chipped uneven natural edges, occupying only the middle third of the frame, dim and unlit, dull and plain, nothing else beside it, the crystal body is clearly grey, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
        "grayscale": True,
    },
    "item/spirit_core_1": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a small green crystal cut with one clean facet, a soft glow inside, one tiny crystal shard floating beside it, nothing else in the picture, the crystal body is clearly green, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },
    "item/spirit_core_2": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a blue crystal cleanly cut with several facets, a brighter core, two small crystal shards floating beside it, nothing else in the picture, the crystal body is clearly blue, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },
    "item/spirit_core_3": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, an amber crystal with many sharp facets, a bright strong core, three small crystal shards floating scattered beside it, bright blue lightning arcs leaping between them, the crystal body is clearly amber, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },
    "item/spirit_core_4": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a large heavy red crystal with many deep facets, a large brilliant core filling much of the frame, four small crystal shards floating scattered beside it, bright blue lightning arcs leaping between them, the crystal body is clearly red, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },
    "item/spirit_core_5": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a huge purple crystal, a massive brilliant core with deep glowing cracks across its surface, filling almost the whole frame, five small crystal shards floating scattered beside it, bright blue lightning arcs connecting them, the most powerful of them all, the crystal body is clearly purple, subtle cyan energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },
    "item/spirit_core": {
        "prompt": (
            "a free floating spirit energy crystal, no stand and no base and no "
            "socket, a clean faceted crystal with a bright core, two small crystal "
            "shards floating beside it, the crystal body is clearly green, subtle cyan "
            "energy veins branching through it"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "stand, base, pedestal, socket, holder, mount, ring, halo, orbit, orbiting ring, "
            "band, collar, crown, disc, bowl, cup, frame, wreath, circle around it, "
            "worm, snake, tentacle, ribbon, hair, root, branch"
        ),
        "pad": True,
    },

    # ================================================================ 符卡（9 张）
    # 两层分工明确：
    #   layer0 = 卡纸 —— **不由 AI 画**，走 tools/textures/spellcard_cards.py 的
    #            ASCII 模板 + 每卡调色板（几何卡框是 ASCII 的强项，AI 只会画成
    #            一整块色板 + 假边框）。
    #   layer1 = 灰度纹章 —— AI 画（有机法阵 ASCII 画不出），必须灰度无色相，
    #            否则乘 tint 会把主题色染脏；fit 让它缩在卡面正中而不是铺满。
    # 卡片与纹章的 PNG 由本管线产出（/item/spellcard_<card>.png 与 _emblem.png）。
    "item/spellcard_musou_fuuin_emblem": {
        "prompt": (
            "a small magic seal emblem, a dense ring of runic script around a hollow center, "
            "a heavy outer ring with small notches, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_icicle_fall_emblem": {
        "prompt": (
            "a small magic seal emblem, six pointed ice crystal spokes radiating from a "
            "hollow center, thin outer ring, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_light_reflect_emblem": {
        "prompt": (
            "a small magic seal emblem, a radiant sunburst of thin rays around a hollow "
            "center, thin outer ring, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_healing_garden_emblem": {
        "prompt": (
            "a small magic seal emblem, a ring of small five petal blossoms around a hollow "
            "center, thin outer ring, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_flower_armor_emblem": {
        "prompt": (
            "a small magic seal emblem, a layered rose of overlapping petals around a hollow "
            "center, thin outer ring, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_demarcation_emblem": {
        "prompt": (
            "a small magic seal emblem, a square boundary inscribed inside a ring, angular "
            "geometric strokes around a hollow center, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_spider_web_emblem": {
        "prompt": (
            "a small magic seal emblem, a radial spider web of straight spokes and "
            "concentric threads around a hollow center, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_plague_repay_emblem": {
        "prompt": (
            "a small magic seal emblem, a ring of small cross shaped marks around a hollow "
            "center, thin outer ring, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },
    "item/spellcard_fox_servant_emblem": {
        "prompt": (
            "a small magic seal emblem, a stylized fox head silhouette inside a ring of "
            "wispy smoke curls, isolated emblem on an empty background"
        ),
        "grayscale": True,
        "colors": 12,
        "fit": 32,
    },

    # ================================================================ 灵铁 / 星银 工具与盔甲
    # 【不进本管线】按 gen-textures skill 与 openspec/specs/gensokyou-equipment-tiers：
    #   灵铁/星银 18 件是「原版剪影 + 本 mod 配色」的约定（gensokyou_equipment.py 明写
    #   "vanilla-derived shape, mod palette"），spec 也要求「外形 MUST 与原版工具/盔甲可辨」。
    #   → 保持 16×16 ASCII，不走 AI 管线。
    #   （原版剪影 + AI 画的刀剑剪影对不上，还会丢掉「可辨」这条硬要求。）
    # 灵铁 / 星银 金属锭本身是「成品」不是剪影，在下面「矿物与金属锭」组里重绘。

    # ================================================================ 矿物与金属锭
    "item/spirit_iron": {
        "prompt": (
            "a neatly smelted spirit iron ingot, a trapezoidal cast metal bar with bevelled "
            "edges and a flat polished top, a small square stamped emblem on the surface, "
            "clean smooth facets, teal cyan metal"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "rough, unrefined, raw, ore, stone, rock, lump, nugget, natural, rusty, dirt"
        ),
    },
    "item/star_silver": {
        "prompt": (
            "a neatly smelted star silver ingot, a trapezoidal cast metal bar with bevelled "
            "edges and a flat polished top, a small square stamped emblem on the surface, "
            "clean smooth facets, pale silver white metal"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "rough, unrefined, raw, ore, stone, rock, lump, nugget, natural, rusty, dirt"
        ),
    },
    "item/refined_cinnabar": {
        "prompt": (
            "a polished piece of refined cinnabar, a smooth rounded mineral nodule like "
            "tumbled cinnabar, an irregular teardrop shape, rich saturated scarlet red, a "
            "waxy satin sheen rather than a mirror shine, a slightly uneven natural "
            "surface with faint mineral striations, a darker shadowed rim, soft "
            "translucent thin edges, no flat faces and no sharp edges, pure and flawless"
        ),
        "pad": True,
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, multiple objects, "
            "excessive empty space, text, letters, watermark, 3d render, blurry, "
            "metal, metallic, ingot, bar, billet, steel, chrome, gold, bronze, copper, "
            "specular, mirror, silver, "
            "rough, unrefined, raw, ore, gravel, dusty, gritty, cracked, rusty, dirt, "
            "rubble, debris, cube, block, dice, box, brick, "
            "flat faces, sharp edges, straight lines, geometric, carved cube, gemstone cut"
        ),
    },
    "item/rough_cinnabar_ore": {
        "prompt": "a rough cinnabar ore chunk, dull grey stone with bright red mineral flecks",
    },
    "item/rough_spirit_iron_ore": {
        "prompt": "a rough spirit iron ore chunk, grey stone with teal cyan metal flecks",
    },
    "item/rough_star_silver_ore": {
        "prompt": (
            "a rough ore chunk, a dark grey rock with chunky lumps of bright silver metal "
            "embedded in it, nuggets of shiny silver breaking out of the surface"
        ),
        "pad": True,
    },

    # ================================================================ 货币
    "item/yen": {
        "prompt": (
            "a round gold coin, polished gold metal, a square hole cut in the centre, a "
            "raised rim and a small embossed emblem on the face"
        ),
    },

    # ================================================================ 树苗（3 棵幻想乡之木）
    # 树苗的**物品图标**原先直接引用方块贴图（block/<sapling>，cross 模型 16×16），
    # 所以之前漏掉了。这里单独出 64×64 物品图标，物品模型改指 item/<sapling>；
    # **方块贴图保持 16×16 不动**（方块尺寸是硬约定，且 cross 精灵的格子感是刻意的）。
    # 三棵树的主题色取自 tools/textures/gensokyo_trees.py 的叶色调色板。
    "item/sacred_sapling": {
        "prompt": (
            "a small tree seedling, a tiny sapling with a thin brown stem and a few bright "
            "green leaves, a single young sprout, isolated on a plain background"
        ),
        "pad": True,
    },
    "item/magic_sapling": {
        "prompt": (
            "a small tree seedling, a tiny sapling with a thin dark stem and a few violet "
            "purple leaves, a single young sprout, isolated on a plain background"
        ),
        "pad": True,
    },
    "item/eternal_sapling": {
        "prompt": (
            "a small tree seedling, a tiny sapling with a thin pale stem and a few silvery "
            "pale blue white leaves, a single young sprout, isolated on a plain background"
        ),
        "pad": True,
    },

    # ================================================================ 幻想乡材料
    "item/porcelain": {
        "prompt": "a shard of fine white porcelain, a glossy curved ceramic fragment with a faint blue glaze",
    },
    "item/tide_crystal": {
        "prompt": "a tide crystal, a translucent cyan sea crystal with a watery inner glow",
    },
    "item/mermaid_scale": {
        "prompt": "a mermaid scale, a single iridescent pink teardrop scale with a soft pearl sheen",
    },
    "item/dragon_scale": {
        "prompt": (
            "a single dragon scale, one rounded teardrop reptilian scale with a hard pointed "
            "tip, deep teal blue with a lighter rim and a subtle ridge down the middle"
        ),
        "pad": True,
    },
    "item/fairy_nectar": {
        "prompt": "a drop of fairy nectar, a glowing golden amber droplet in a tiny glass vial",
    },
    "item/earth_spider_silk": {
        "prompt": (
            "a silk cocoon of earth spider silk, a pale grey white oval cocoon with a soft "
            "fibrous texture and a few loose brown threads hanging from the bottom, "
            "no wooden spool and no winding"
        ),
        "pad": True,
    },
    "item/nightfall_core": {
        "prompt": (
            "a nightfall core, a dark violet orb, a glowing crescent moon rune carved into "
            "its surface, a hollow shadowed socket in the middle, a bright rim light on the "
            "upper left edge"
        ),
    },
    "item/sukima_fragment": {
        "prompt": "a sukima fragment, a broken shard of a purple reality rift, swirling violet edges and a dark hollow core",
    },
    "item/talisman_paper": {
        "prompt": (
            "a rolled bundle of cream rice paper strips tied with a red cord, torn deckled "
            "edges, a faint red printed border on the paper"
        ),
    },
    "item/spirit_charcoal": {
        "prompt": "a piece of spirit charcoal, a black lump of burning charcoal with a soft emerald green glow seeping out of its cracks",
    },
    "item/memory_fragment": {
        "prompt": "a memory fragment, a torn scrap of glowing pale parchment with faint violet letters",
    },
    "item/sanzu_flask": {
        "prompt": "a sanzu flask, a small round glass medicine flask with a cork stopper and a red paper talisman tied around the neck",
    },
    "item/spirit_fish": {
        "prompt": "a spirit fish, a small pale blue fish with translucent fins and a faint green glow",
    },
    "item/spirit_herb": {
        "prompt": (
            "a spirit herb sprig, a single thin green stem with narrow pointed leaves, a "
            "small bunch of fresh green leaves, no flowers"
        ),
        "pad": True,
    },
    "item/spirit_herb_seeds": {
        "prompt": (
            "three individual small pale green oval seeds, smooth glossy grains, clearly "
            "separated from each other, not touching, on an empty background"
        ),
        "pad": True,
    },
    "item/gentian": {
        "prompt": "a gentian flower, a deep blue bell shaped blossom with a green stem",
    },
    "item/gentian_seeds": {
        "prompt": (
            "three individual tiny brown flower seeds, smooth teardrop shapes, separated "
            "from each other, not touching"
        ),
    },
    "item/higanbana": {
        "prompt": "a higanbana flower, a crimson spider lily with long curling stamens and a pale yellow center",
    },
    "item/higanbana_seeds": {
        "prompt": (
            "three individual glossy black flower seeds, small round beads, separated from "
            "each other, not touching"
        ),
    },
    "item/magic_mushroom": {
        "prompt": "a magic mushroom, a pale pink mushroom cap with white spots and a short cream stalk",
    },
    "item/magic_mushroom_spores": {
        "prompt": (
            "three individual pale lavender mushroom spores, tiny soft round puffs, "
            "separated from each other, not touching"
        ),
    },
    "item/crystal": {
        "prompt": (
            "a single diamond shaped crystal, a rhombus gem with deep violet and blue "
            "facets and a bright cyan glowing core in the middle, a pointed bottom tip"
        ),
        "negative": (
            "photorealistic, realistic, smooth rendering, soft edges, smooth gradients, "
            "anti-aliased edges, detailed background, scenery, environment, person, human, "
            "hand, arm, character, multiple objects, tiny object, small object, distant object, "
            "excessive empty space, text, letters, watermark, signature, ui, border, frame, "
            "3d render, octane, unreal engine, blurry, jpeg artifacts, "
            "speckles, confetti, glitter, mosaic, shattered, noise, rainbow bands"
        ),
    },

    # ================================================================ 仪式器物与造物
    # 【整地器 / 引爆器是方块实体】物品图标必须与方块贴图同款：
    #   block/landscaping_tool.png = 灰石刃 + 棕木柄（刃朝上的农具）
    #   block/spirit_bomb_*.png     = 暗红方盒 + 金边金十字 + 顶部引信
    # 画成别的东西（现代锤子 / 黑炸弹）会和世界里的方块对不上。
    "item/ritual_wand": {
        "prompt": (
            "a slender ritual magic wand, dark violet wooden shaft, a teal cyan gemstone set "
            "in a brass ring at the top, thin gold bands on the handle"
        ),
    },
    "item/ritual_builder": {
        "prompt": (
            "a ritual builder staff held diagonally, warm brown wooden shaft, a teal cyan "
            "crystal core set in brass at the top, two small copper rings on the shaft"
        ),
    },
    "item/landscaping_tool": {
        "prompt": (
            "a landscaping tool, a wide flat grey stone blade head pointing up on top of a "
            "short brown wooden handle, a simple rustic craftsman farming tool, a plain "
            "wooden shaft, no metal parts and no trigger"
        ),
        "pad": True,
    },
    "item/spirit_bomb": {
        "prompt": (
            "a spirit bomb device, a dark crimson cube box with gold yellow trim along its "
            "edges and a gold cross symbol on the front face, a short gold fuse stub on top, "
            "a square red charm box"
        ),
        "pad": True,
    },
    "item/laevatein": {
        "prompt": (
            "an angled sword sprite pointing up to the right, a blazing red and gold "
            "fantasy sword with a bright glowing edge and a dark hilt"
        ),
    },

    # ================================================================ 未挂模型的预留贴图
    "item/cirno_catalyst": {
        "prompt": (
            "a small catalyst vial, a pale blue glass bottle with a snowflake frost pattern "
            "and a cork stopper"
        ),
    },
    "item/summon_catalyst": {
        "prompt": (
            "a summoning catalyst, a small dark stone disc with a glowing violet sigil "
            "carved into its face"
        ),
    },
}
