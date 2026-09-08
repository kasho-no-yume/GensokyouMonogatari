# 仪式 Pattern 摘要（机器生成：`python tools/gen_catalog.py`，勿手改）

> 坐标为四分之一规范形（x≥0,z≥0）；「展开格数」= 该级四重对称展开后的建筑总格数
> （该级完整总量，纯增量下含低层全部格）。
> 尝试优先级 = 全层级展开格数总和，大者先试（大仪式不得劫持小仪式建筑）。

## gensokyou:barrier_break_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=true · 优先级 17
- palette：`C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · S=#gensokyou:ritual_stones`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 5 | 17 | 8 | y0:5 |

## gensokyou:capacitor_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=false · 优先级 5
- palette：`C=gensokyou:ritual_core · S=#gensokyou:ritual_stones`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 2 | 5 | 8 | y0:2 |

## gensokyou:generator_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=true · 优先级 5897
- palette：`1=#gensokyou:ritual_stones_1_plus · 2=#gensokyou:ritual_stones_2_plus · 3=#gensokyou:ritual_stones_3_plus · 4=#gensokyou:ritual_stones_4_plus · 5=#gensokyou:ritual_stones_5_plus · A=minecraft:amethyst_block · B=minecraft:purple_banner · C=gensokyou:ritual_core · D=minecraft:purple_stained_glass · E=gensokyou:ritual_stone_wall_2 · H=minecraft:dark_oak_fence · J=minecraft:basalt · K=minecraft:dark_oak_planks · L=minecraft:soul_lantern · O=minecraft:dark_oak_log · P=#gensokyou:ritual_pedestals_2_plus · Q=#gensokyou:ritual_pedestals_3_plus · R=#gensokyou:ritual_pedestals_4_plus · T=minecraft:cherry_log · U=minecraft:purpur_block · W=gensokyou:ritual_stone_wall_3 · X=minecraft:end_stone · Y=minecraft:pink_petals`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 120 | 477 | 149 | y0:120 |
| 2 | 284 | 1133 | 149 | y0:120 y1:89 y2:66 y3:4 y4:3 y5:2 |
| 3 | 305 | 1217 | 149 | y0:120 y1:89 y2:68 y3:22 y4:4 y5:2 |
| 4 | 357 | 1425 | 149 | y0:120 y1:89 y2:88 y3:46 y4:8 y5:3 y6:1 y7:2 |
| 5 | 412 | 1645 | 149 | y0:120 y1:92 y2:91 y3:68 y4:21 y5:9 y6:4 y7:4 y8:3 |

## gensokyou:processing_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=false · 优先级 13
- palette：`C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · S=#gensokyou:ritual_stones`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 4 | 13 | 4 | y0:4 |

## gensokyou:relay_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=false · 优先级 5
- palette：`C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 2 | 5 | 1 | y0:2 |

## gensokyou:summon_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=false · 优先级 9
- palette：`C=gensokyou:ritual_core · S=#gensokyou:ritual_stones`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 3 | 9 | 2 | y0:3 |

## gensokyou:tempering_circle

- 锚点 key：`C`（每层唯一，位于 (0,0,0)）· toggleable=false · 优先级 17
- palette：`C=gensokyou:ritual_core · P=#gensokyou:ritual_pedestals · S=#gensokyou:ritual_stones`

| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |
|---|---|---|---|---|
| 1 | 5 | 17 | 8 | y0:5 |
