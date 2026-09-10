# 仪式 Pattern 摘要（机器生成：`python tools/gen_catalog.py`，勿手改）

> 坐标为四分之一规范形（x≥0,z≥0）；「展开格数」= 该级四重对称展开后的建筑总格数
> （该级完整总量，纯增量下含低层全部格）。
> 尝试优先级 = 全层级展开格数总和，大者先试（大仪式不得劫持小仪式建筑）。

## gensokyou:kagutsuchi_flame_circle

- 锚点 key：`C`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=false · 优先级 2920
- palette：`0=#gensokyou:ritual_stones · 1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · B=minecraft:polished_blackstone_bricks · C=gensokyou:ritual_core · D=minecraft:deepslate_bricks · E=minecraft:glowstone · F=minecraft:iron_bars · G=minecraft:purple_stained_glass · H=minecraft:chain · K=minecraft:waxed_copper_block · L=minecraft:soul_lantern · M=minecraft:magenta_stained_glass · O=minecraft:waxed_oxidized_copper · P=#gensokyou:ritual_pedestals · Q=minecraft:coal_block · R=minecraft:lantern · S=minecraft:stone_bricks · T=minecraft:polished_blackstone_brick_slab · V=minecraft:polished_basalt · X=minecraft:crying_obsidian`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 0 | 32 | 119 | 18 | y-2:10 y-1:15 y0:6 y1:1 |
| 1 | 92 | 359 | 72 | y-2:10 y-1:51 y0:19 y1:6 y2:3 y3:3 |
| 2 | 209 | 827 | 162 | y-2:10 y-1:73 y0:44 y1:16 y2:13 y3:11 y4:7 y5:25 y6:9 y7:1 |
| 3 | 406 | 1615 | 242 | y-2:10 y-1:115 y0:67 y1:40 y2:32 y3:20 y4:22 y5:33 y6:37 y7:6 y8:11 y9:11 y10:2 |
