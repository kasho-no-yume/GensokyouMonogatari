# boss_signatures - BOSS 主题信物物品图标（spellcard-crafting-and-boss-signatures）
# 说明：三件独立贴图（非灰度染色）。o 描边 / a 基色 / b 高光 / c·d 暗部与点缀 / n·e 亮芯。
# fairy_nectar 花精之滴：金色灵液滴（大妖精）
# earth_spider_silk 土蜘蛛之丝：土褐色丝线卷（黑谷山女）
# nightfall_core 宵暗之核：暗紫发光核（露米娅，预注册）

PAL_item_fairy_nectar = {
    '.': None, 'o': (58, 40, 16), 'c': (176, 116, 34), 'a': (240, 186, 74),
    'b': (255, 226, 132), 'n': (255, 248, 214),
}
TEX_item_fairy_nectar = [
    "................",
    ".......oo.......",
    "......obno......",
    "......obbo......",
    ".....obaaao.....",
    "....obaaaaao....",
    "...obaaaaaaao...",
    "...obaaaaaaao...",
    "...obaaaaaaao...",
    "....obaaaaco....",
    "....obaaaaco....",
    ".....obaaco.....",
    "......obco......",
    ".......oo.......",
    "................",
    "................",
]

PAL_item_earth_spider_silk = {
    '.': None, 'o': (40, 28, 18), 'c': (86, 60, 38), 'a': (150, 112, 74),
    'b': (198, 160, 116),
}
TEX_item_earth_spider_silk = [
    "................",
    "...oooooooooo...",
    "..obbbbbbbbbbo..",
    "..occcccccccco..",
    "...obaaaaaabo...",
    "...ocaaaaaaco...",
    "...obaaaaaabo...",
    "...ocaaaaaaco...",
    "...obaaaaaabo...",
    "...ocaaaaaaco...",
    "...obbbbbbbbo...",
    "..obbbbbbbbbbo..",
    "..occcccccccco..",
    "...oooooooooo...",
    "................",
    "................",
]

PAL_item_nightfall_core = {
    '.': None, 'o': (8, 5, 16), 'a': (52, 34, 78), 'b': (96, 64, 138),
    'e': (224, 198, 252),
}
TEX_item_nightfall_core = [
    "................",
    ".....oooooo.....",
    "...oobaaaaabo...",
    "..obbaaaaaaabo..",
    ".obaaaaaaaaaabo.",
    "obaaaaaaaaaaaabo",
    "obaaaaaaaaaaaabo",
    "obaaaaaeeaaaaabo",
    "obaaaaaeeaaaaabo",
    "obaaaaaaaaaaaabo",
    ".obaaaaaaaaaabo.",
    "..obbaaaaaaabo..",
    "...oobaaaaaoo...",
    ".....oooooo.....",
    "................",
    "................",
]

TEXES = {
    "item/fairy_nectar": TEX_item_fairy_nectar,
    "item/earth_spider_silk": TEX_item_earth_spider_silk,
    "item/nightfall_core": TEX_item_nightfall_core,
}
