# 仪式 Pattern 摘要（机器生成：`python tools/gen_catalog.py`，勿手改）

> 坐标为四分之一规范形（x≥0,z≥0）；「展开格数」= 该级四重对称展开后的建筑总格数
> （该级完整总量，纯增量下含低层全部格）。
> 尝试优先级 = 全层级展开格数总和，大者先试（大仪式不得劫持小仪式建筑）。

## gensokyou:bafang_guiyuan_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 3820
- palette：`C=gensokyou:ritual_core · a=#gensokyou:ritual_pedestals · b=#gensokyou:ritual_pedestals · c=#gensokyou:ritual_pedestals · d=#gensokyou:ritual_pedestals · e=#gensokyou:ritual_stones_2_plus · f=#gensokyou:ritual_stones_3_plus · g=#gensokyou:ritual_stones_4_plus · h=#gensokyou:ritual_stones_5_plus · i=gensokyou:ritual_stone_slab_2 · j=gensokyou:ritual_stone_slab_3 · k=minecraft:quartz_pillar · l=minecraft:purple_stained_glass · m=minecraft:sea_lantern · n=minecraft:soul_lantern · o=minecraft:amethyst_block · p=minecraft:crying_obsidian · q=minecraft:deepslate_bricks · r=minecraft:polished_deepslate · s=minecraft:stone_brick_wall · t=minecraft:end_stone_bricks · u=minecraft:deepslate_tiles · v=minecraft:polished_blackstone_bricks · w=minecraft:polished_basalt · x=minecraft:blackstone_wall`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 2 | 94 | 373 | 100 | y-1:55 y0:21 y1:16 y2:2 |
| 3 | 156 | 621 | 137 | y-1:109 y0:24 y1:17 y2:4 y3:2 |
| 4 | 270 | 1077 | 226 | y-1:179 y0:43 y1:32 y2:5 y3:3 y4:1 y5:1 y6:1 y7:3 y8:2 |
| 5 | 438 | 1749 | 377 | y-1:300 y0:86 y1:34 y2:7 y3:3 y4:1 y5:1 y6:1 y7:3 y8:2 |

## gensokyou:haniyasu_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 919
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:packed_mud · b=minecraft:mud_bricks · c=minecraft:mud_brick_wall · d=minecraft:clay · e=minecraft:coarse_dirt · f=minecraft:rooted_dirt · h=minecraft:mud · i=minecraft:terracotta · k=minecraft:stone_bricks · l=minecraft:tuff · m=minecraft:calcite · n=minecraft:dripstone_block · o=minecraft:gravel · p=minecraft:sand · s=minecraft:lantern · t=minecraft:potted_dead_bush · u=minecraft:potted_fern`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 32 | 116 | 13 | y-3:12 y-2:8 y-1:6 y0:4 y1:2 |
| 1 | 73 | 277 | 25 | y-4:21 y-3:20 y-2:12 y-1:10 y0:6 y1:4 |
| 2 | 136 | 526 | 41 | y-5:35 y-4:35 y-3:24 y-2:14 y-1:12 y0:11 y1:5 |

## gensokyou:kagutsuchi_flame_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 2956
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · B=minecraft:polished_blackstone_bricks · C=gensokyou:ritual_core · D=minecraft:deepslate_bricks · E=minecraft:glowstone · F=minecraft:iron_bars · G=minecraft:purple_stained_glass · H=minecraft:chain · K=minecraft:waxed_copper_block · L=minecraft:soul_lantern · M=minecraft:magenta_stained_glass · O=minecraft:waxed_oxidized_copper · P=#gensokyou:ritual_pedestals · Q=minecraft:coal_block · R=minecraft:lantern · S=minecraft:stone_bricks · T=minecraft:polished_blackstone_brick_slab · V=minecraft:polished_basalt · X=minecraft:crying_obsidian`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 32 | 119 | 18 | y-2:10 y-1:15 y0:6 y1:1 |
| 1 | 92 | 359 | 72 | y-2:10 y-1:51 y0:19 y1:6 y2:3 y3:3 |
| 2 | 209 | 827 | 162 | y-2:10 y-1:73 y0:44 y1:16 y2:13 y3:11 y4:7 y5:25 y6:9 y7:1 |
| 3 | 415 | 1651 | 242 | y-2:10 y-1:124 y0:67 y1:40 y2:32 y3:20 y4:22 y5:33 y6:37 y7:6 y8:11 y9:11 y10:2 |

## gensokyou:kami_no_megumi_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=false · 优先级 7830
- palette：`1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · 4=#gensokyou:ritual_stones_4_plus · 5=#gensokyou:ritual_stones_5_plus · A=minecraft:moss_block · B=minecraft:pink_petals · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:deepslate_bricks · b=minecraft:polished_deepslate · c=minecraft:stone_bricks · d=minecraft:chiseled_stone_bricks · e=minecraft:stone_brick_wall · f=minecraft:iron_bars · g=minecraft:chain · h=minecraft:lantern · i=minecraft:glowstone · j=minecraft:sea_lantern · k=minecraft:dark_oak_log · l=minecraft:dark_oak_planks · m=minecraft:quartz_block · n=minecraft:quartz_pillar · o=minecraft:amethyst_block · p=minecraft:purple_stained_glass · q=minecraft:magenta_stained_glass · r=minecraft:purpur_block · s=minecraft:purpur_pillar · t=minecraft:end_stone_bricks · u=minecraft:crying_obsidian · v=minecraft:oxidized_copper · w=minecraft:deepslate_tiles · x=minecraft:potted_cherry_sapling · y=minecraft:purple_banner · z=minecraft:gold_block`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 53 | 206 | 32 | y-1:21 y0:10 y1:7 y2:7 y3:8 |
| 2 | 186 | 738 | 72 | y-1:43 y0:22 y1:18 y2:19 y3:20 y4:30 y5:8 y6:20 y7:6 |
| 3 | 357 | 1422 | 128 | y-1:73 y0:42 y1:33 y2:35 y3:36 y4:60 y5:8 y6:20 y7:12 y8:6 y9:6 y10:6 y11:6 y12:12 y13:2 |
| 4 | 563 | 2246 | 200 | y-1:111 y0:64 y1:52 y2:55 y3:56 y4:98 y5:9 y6:20 y7:12 y8:6 y9:6 y10:6 y11:6 y12:12 y13:12 y14:6 y15:6 y16:6 y17:6 y18:12 y19:2 |
| 5 | 806 | 3218 | 338 | y-1:183 y0:84 y1:73 y2:74 y3:79 y4:145 y5:12 y6:25 y7:15 y8:7 y9:6 y10:6 y11:6 y12:12 y13:12 y14:6 y15:6 y16:6 y17:6 y18:12 y19:6 y20:4 y21:4 y22:4 y23:4 y24:6 y25:3 |

## gensokyou:kaya_no_hime_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 698
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:moss_block · b=minecraft:rooted_dirt · d=minecraft:poppy · e=minecraft:cornflower · f=minecraft:short_grass · g=minecraft:pink_petals · h=minecraft:dandelion · i=minecraft:coarse_dirt · j=minecraft:oak_log · k=minecraft:oak_planks · o=minecraft:allium · p=minecraft:azure_bluet · q=minecraft:oxeye_daisy · r=minecraft:lily_of_the_valley · s=minecraft:lantern · x=minecraft:red_tulip`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 26 | 98 | 9 | y-1:8 y0:7 y1:2 y2:2 y3:5 y4:2 |
| 1 | 56 | 218 | 25 | y-1:21 y0:18 y1:4 y2:4 y3:7 y4:2 |
| 2 | 97 | 382 | 41 | y-1:35 y0:31 y1:6 y2:6 y3:9 y4:5 y5:1 y6:2 y7:2 |

## gensokyou:kukunochi_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 794
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:stone_bricks · b=minecraft:mossy_cobblestone · c=minecraft:moss_block · d=minecraft:deepslate_tiles · e=minecraft:deepslate_bricks · f=minecraft:chiseled_stone_bricks · g=minecraft:pink_petals · h=minecraft:potted_cherry_sapling · i=minecraft:lantern · j=minecraft:sea_lantern · k=minecraft:dark_oak_log · l=minecraft:dark_oak_planks · m=minecraft:cherry_planks · n=minecraft:cherry_log · o=minecraft:stone_brick_wall · q=minecraft:purple_banner · w=minecraft:potted_fern`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 15 | 54 | 9 | y-1:8 y0:5 y1:2 |
| 1 | 64 | 250 | 36 | y-1:29 y0:17 y1:5 y2:5 y3:2 y4:5 y5:1 |
| 2 | 124 | 490 | 64 | y-1:50 y0:31 y1:10 y2:11 y3:3 y4:6 y5:2 y6:1 y7:6 y8:3 y9:1 |

## gensokyou:nichirin_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 480
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:stone_bricks · b=minecraft:polished_andesite · d=minecraft:chiseled_stone_bricks · e=minecraft:stone_brick_wall · h=minecraft:lantern · j=minecraft:sea_lantern · n=minecraft:quartz_pillar · p=minecraft:potted_cherry_sapling · z=minecraft:gold_block`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 25 | 94 | 20 | y-1:18 y0:4 y1:2 y2:1 |
| 1 | 98 | 386 | 81 | y-1:64 y0:21 y1:6 y2:3 y3:2 y4:2 |

## gensokyou:oyamatsumi_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 1187
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:stone · b=minecraft:cobblestone · c=minecraft:mossy_cobblestone · d=minecraft:tuff · e=minecraft:deepslate · f=minecraft:cobbled_deepslate · g=minecraft:andesite · h=minecraft:calcite · i=minecraft:dripstone_block · j=minecraft:moss_block · k=minecraft:stone_bricks · l=minecraft:deepslate_tiles · m=minecraft:polished_deepslate · n=minecraft:chiseled_stone_bricks · o=minecraft:coal_block · p=minecraft:waxed_copper_block · q=minecraft:raw_iron_block · r=minecraft:raw_copper_block · s=minecraft:lapis_block · t=minecraft:amethyst_block · u=minecraft:lantern · w=minecraft:potted_fern · x=minecraft:potted_brown_mushroom · y=minecraft:potted_dead_bush`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 20 | 77 | 10 | y-1:9 y0:5 y1:3 y2:3 |
| 1 | 79 | 313 | 25 | y-1:20 y0:15 y1:10 y2:10 y3:7 y4:7 y5:10 |
| 2 | 200 | 797 | 64 | y-1:45 y0:30 y1:16 y2:13 y3:9 y4:9 y5:13 y6:46 y7:19 |

## gensokyou:resonance_relay

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 4107
- palette：`2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · 4=#gensokyou:ritual_stones_4_plus · A=minecraft:amethyst_block · C=gensokyou:ritual_core · D=minecraft:purple_stained_glass · G=minecraft:magenta_stained_glass · H=minecraft:iron_bars · L=minecraft:soul_lantern · Q=minecraft:quartz_block · S=minecraft:deepslate_bricks · U=minecraft:purpur_block · V=minecraft:purpur_pillar · W=minecraft:sea_lantern · X=minecraft:waxed_oxidized_copper · g=gensokyou:air`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 2 | 68 | 263 | 29 | y-1:25 y0:5 y1:4 y2:4 y3:4 y4:4 y5:4 y6:4 y7:4 y8:4 y9:6 |
| 3 | 194 | 764 | 41 | y-1:25 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 |
| 4 | 264 | 1041 | 53 | y-1:35 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 y23:4 y24:4 y25:4 y26:34 y27:4 y28:4 y29:6 |
| 5 | 515 | 2039 | 72 | y-1:47 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 y23:4 y24:4 y25:4 y26:34 y27:4 y28:4 y29:6 y30:4 y31:4 y32:4 y33:54 y34:54 y35:54 y36:7 y37:15 y38:8 y39:11 y40:3 y41:7 y42:4 y43:6 y44:3 y45:1 |

## gensokyou:shujou_yoroku_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 991
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · A=minecraft:polished_andesite · B=minecraft:stone_bricks · C=gensokyou:ritual_core · D=minecraft:chiseled_stone_bricks · F=minecraft:iron_bars · G=minecraft:chain · H=minecraft:deepslate_tiles · J=minecraft:dark_oak_log · K=minecraft:bookshelf · L=minecraft:lantern · M=minecraft:sea_lantern · N=minecraft:end_stone_bricks · P=#gensokyou:ritual_pedestals · Q=minecraft:quartz_pillar · R=minecraft:purpur_block · S=minecraft:purple_stained_glass · U=minecraft:quartz_block · V=minecraft:purpur_pillar · W=minecraft:stone_brick_wall · X=minecraft:amethyst_block · Z=minecraft:soul_lantern`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 28 | 109 | 25 | y-1:20 y0:7 y1:1 |
| 1 | 78 | 309 | 64 | y-1:49 y0:14 y1:7 y2:2 y3:2 y4:4 |
| 2 | 144 | 573 | 121 | y-1:94 y0:16 y1:8 y2:3 y3:3 y4:5 y5:5 y6:5 y7:3 y8:2 |

## gensokyou:tsukikage_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 480
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:deepslate_bricks · b=minecraft:polished_deepslate · c=minecraft:chiseled_deepslate · d=minecraft:dark_prismarine · e=minecraft:deepslate_brick_wall · i=minecraft:blue_ice · j=minecraft:sea_lantern · o=minecraft:potted_blue_orchid · w=minecraft:deepslate_tiles`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 25 | 94 | 20 | y-1:18 y0:4 y1:2 y2:1 |
| 1 | 98 | 386 | 81 | y-1:64 y0:21 y1:6 y2:3 y3:2 y4:2 |

## gensokyou:watatsumi_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 918
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · D=minecraft:polished_deepslate · E=minecraft:deepslate_tiles · F=minecraft:quartz_block · G=minecraft:quartz_pillar · H=minecraft:waxed_copper_block · I=minecraft:gold_block · P=#gensokyou:ritual_pedestals · a=minecraft:mossy_cobblestone · b=minecraft:cobblestone · d=minecraft:tuff · e=minecraft:gravel · f=minecraft:prismarine_bricks · g=minecraft:dark_prismarine · h=minecraft:sea_lantern · i=minecraft:moss_block · j=minecraft:coarse_dirt · k=minecraft:packed_mud · m=minecraft:lantern · n=minecraft:potted_fern · p=minecraft:stone_brick_wall · q=minecraft:blue_ice · s=minecraft:stone_bricks · t=minecraft:polished_andesite · u=minecraft:dark_oak_log · v=minecraft:dark_oak_planks · w=minecraft:chiseled_stone_bricks · x=minecraft:waxed_oxidized_copper · z=minecraft:chain`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 19 | 70 | 16 | y-1:13 y0:4 y1:2 |
| 1 | 62 | 242 | 40 | y-1:31 y0:9 y1:5 y2:4 y3:3 y4:5 y5:5 |
| 2 | 156 | 606 | 64 | y-1:50 y0:15 y1:9 y2:8 y3:5 y4:7 y5:7 y6:6 y7:26 y8:16 y9:6 y10:1 |

## gensokyou:yumewatari_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=false · 优先级 970
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · a=minecraft:deepslate_bricks · b=minecraft:polished_deepslate · c=minecraft:deepslate_tiles · d=minecraft:stone_bricks · e=minecraft:chiseled_stone_bricks · f=minecraft:dark_oak_log · g=minecraft:dark_oak_planks · h=minecraft:sea_lantern · i=minecraft:lantern · j=minecraft:chain · l=minecraft:purple_stained_glass · m=minecraft:amethyst_block · p=minecraft:potted_cherry_sapling · q=minecraft:waxed_oxidized_copper · r=minecraft:soul_lantern · t=minecraft:polished_basalt · u=minecraft:crying_obsidian · v=minecraft:end_stone_bricks · w=minecraft:purpur_block · z=minecraft:magenta_stained_glass`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 21 | 78 | 16 | y-1:13 y0:5 y1:1 y2:1 y3:1 |
| 1 | 71 | 278 | 50 | y-1:38 y0:8 y1:3 y2:3 y3:3 y4:2 y5:14 |
| 2 | 155 | 614 | 100 | y-1:80 y0:12 y1:6 y2:6 y3:6 y4:5 y5:20 y6:17 y7:3 |

## gensokyou:zaohua_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=false · 优先级 6922
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · 4=#gensokyou:ritual_stones_4_plus · 5=#gensokyou:ritual_stones_5_plus · A=minecraft:stone_bricks · B=minecraft:chiseled_stone_bricks · C=gensokyou:ritual_core · D=minecraft:deepslate_bricks · G=minecraft:quartz_block · I=minecraft:quartz_bricks · K=minecraft:soul_lantern · L=minecraft:lantern · M=minecraft:sea_lantern · N=minecraft:end_stone_bricks · O=minecraft:purpur_block · P=#gensokyou:ritual_pedestals · Q=minecraft:amethyst_block · R=minecraft:purple_stained_glass · S=minecraft:magenta_stained_glass · U=minecraft:chain · V=minecraft:waxed_copper_block · X=minecraft:cherry_log · Z=minecraft:potted_cherry_sapling · a=minecraft:gold_block · b=minecraft:purpur_pillar · c=minecraft:stone_brick_wall · d=minecraft:purple_banner · e=minecraft:glowstone · h=minecraft:deepslate_tiles`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 31 | 115 | 16 | y-2:13 y-1:13 y0:4 y1:1 |
| 1 | 86 | 335 | 49 | y-3:25 y-2:38 y-1:16 y0:5 y1:2 |
| 2 | 176 | 695 | 100 | y-4:42 y-3:67 y-2:41 y-1:17 y0:6 y1:3 |
| 3 | 301 | 1195 | 169 | y-5:53 y-4:95 y-3:71 y-2:43 y-1:20 y0:11 y1:8 |
| 4 | 443 | 1763 | 256 | y-6:67 y-5:120 y-4:100 y-3:72 y-2:44 y-1:21 y0:11 y1:8 |
| 5 | 707 | 2819 | 400 | y-7:115 y-6:182 y-5:128 y-4:102 y-3:74 y-2:46 y-1:24 y0:18 y1:11 y2:7 |
