# 仪式 Pattern 摘要（机器生成：`python tools/gen_catalog.py`，勿手改）

> 坐标为四分之一规范形（x≥0,z≥0）；「展开格数」= 该级四重对称展开后的建筑总格数
> （该级完整总量，纯增量下含低层全部格）。
> 尝试优先级 = 全层级展开格数总和，大者先试（大仪式不得劫持小仪式建筑）。

## gensokyou:bafang_guiyuan_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=false · 优先级 3820
- palette：`C=gensokyou:ritual_core · a=#gensokyou:ritual_pedestals · b=#gensokyou:ritual_pedestals · c=#gensokyou:ritual_pedestals · d=#gensokyou:ritual_pedestals · e=#gensokyou:ritual_stones_2_plus · f=#gensokyou:ritual_stones_3_plus · g=#gensokyou:ritual_stones_4_plus · h=#gensokyou:ritual_stones_5_plus · i=gensokyou:ritual_stone_slab_2 · j=gensokyou:ritual_stone_slab_3 · k=minecraft:quartz_pillar · l=minecraft:purple_stained_glass · m=minecraft:sea_lantern · n=minecraft:soul_lantern · o=minecraft:amethyst_block · p=minecraft:crying_obsidian · q=minecraft:deepslate_bricks · r=minecraft:polished_deepslate · s=minecraft:stone_brick_wall · t=minecraft:end_stone_bricks · u=minecraft:deepslate_tiles · v=minecraft:polished_blackstone_bricks · w=minecraft:polished_basalt · x=minecraft:blackstone_wall`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 2 | 94 | 373 | 100 | y-1:55 y0:21 y1:16 y2:2 |
| 3 | 156 | 621 | 137 | y-1:109 y0:24 y1:17 y2:4 y3:2 |
| 4 | 270 | 1077 | 226 | y-1:179 y0:43 y1:32 y2:5 y3:3 y4:1 y5:1 y6:1 y7:3 y8:2 |
| 5 | 438 | 1749 | 377 | y-1:300 y0:86 y1:34 y2:7 y3:3 y4:1 y5:1 y6:1 y7:3 y8:2 |

## gensokyou:kagutsuchi_flame_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 2956
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · B=minecraft:polished_blackstone_bricks · C=gensokyou:ritual_core · D=minecraft:deepslate_bricks · E=minecraft:glowstone · F=minecraft:iron_bars · G=minecraft:purple_stained_glass · H=minecraft:chain · K=minecraft:waxed_copper_block · L=minecraft:soul_lantern · M=minecraft:magenta_stained_glass · O=minecraft:waxed_oxidized_copper · P=#gensokyou:ritual_pedestals · Q=minecraft:coal_block · R=minecraft:lantern · S=minecraft:stone_bricks · T=minecraft:polished_blackstone_brick_slab · V=minecraft:polished_basalt · X=minecraft:crying_obsidian`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 32 | 119 | 18 | y-2:10 y-1:15 y0:6 y1:1 |
| 1 | 92 | 359 | 72 | y-2:10 y-1:51 y0:19 y1:6 y2:3 y3:3 |
| 2 | 209 | 827 | 162 | y-2:10 y-1:73 y0:44 y1:16 y2:13 y3:11 y4:7 y5:25 y6:9 y7:1 |
| 3 | 415 | 1651 | 242 | y-2:10 y-1:124 y0:67 y1:40 y2:32 y3:20 y4:22 y5:33 y6:37 y7:6 y8:11 y9:11 y10:2 |

## gensokyou:resonance_relay

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=true · 优先级 4107
- palette：`2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · 4=#gensokyou:ritual_stones_4_plus · A=minecraft:amethyst_block · C=gensokyou:ritual_core · D=minecraft:purple_stained_glass · G=minecraft:magenta_stained_glass · H=minecraft:iron_bars · L=minecraft:soul_lantern · Q=minecraft:quartz_block · S=minecraft:deepslate_bricks · U=minecraft:purpur_block · V=minecraft:purpur_pillar · W=minecraft:sea_lantern · X=minecraft:waxed_oxidized_copper · g=gensokyou:air`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 2 | 68 | 263 | 29 | y-1:25 y0:5 y1:4 y2:4 y3:4 y4:4 y5:4 y6:4 y7:4 y8:4 y9:6 |
| 3 | 194 | 764 | 41 | y-1:25 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 |
| 4 | 264 | 1041 | 53 | y-1:35 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 y23:4 y24:4 y25:4 y26:34 y27:4 y28:4 y29:6 |
| 5 | 515 | 2039 | 72 | y-1:47 y0:6 y1:5 y2:5 y3:5 y4:5 y5:5 y6:5 y7:5 y8:5 y9:7 y10:5 y11:5 y12:5 y13:5 y14:5 y15:5 y16:5 y17:34 y18:29 y19:4 y20:4 y21:4 y22:6 y23:4 y24:4 y25:4 y26:34 y27:4 y28:4 y29:6 y30:4 y31:4 y32:4 y33:54 y34:54 y35:54 y36:7 y37:15 y38:8 y39:11 y40:3 y41:7 y42:4 y43:6 y44:3 y45:1 |
