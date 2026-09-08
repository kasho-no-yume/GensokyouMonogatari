# 方块目录（机器生成：`python tools/gen_catalog.py`，勿手改）

> Astra 选材只看本文与 PATTERNS.md，**禁止读 Java/JSON 源码**。
> id 可省略 `gensokyou:` 前缀书写；主色为该品阶贴图均值（参考用，非精确色）。

## 自定义方块

- `ritual_core`：**仪式锚点**（核心方块，pattern anchorKey 专用）。
  贴图 `ritual_core_0..5` 按 blockstate tier 切换；颜色随品阶（0灰/1绿/2蓝/3琥珀/4红/5紫主色调晶体）。
- `sukima`：隙间传送门（功能方块）。

### ritual_pedestal — 品阶方块族（贴图随品阶变化）

| id | 品阶 | 主色 |
|---|---|---|
| gensokyou:ritual_pedestal_0 | 0(灰) | #6f6869 |
| gensokyou:ritual_pedestal_1 | 1(绿) | #605863 |
| gensokyou:ritual_pedestal_2 | 2(蓝) | #5a5276 |
| gensokyou:ritual_pedestal_3 | 3(琥珀) | #6d525e |
| gensokyou:ritual_pedestal_4 | 4(红) | #6b4360 |
| gensokyou:ritual_pedestal_5 | 5(紫) | #5f3b68 |

### ritual_stone — 品阶方块族（贴图随品阶变化）

| id | 品阶 | 主色 |
|---|---|---|
| gensokyou:ritual_stone_0 | 0(灰) | #605e6b |
| gensokyou:ritual_stone_1 | 1(绿) | #5a4d74 |
| gensokyou:ritual_stone_2 | 2(蓝) | #554985 |
| gensokyou:ritual_stone_3 | 3(琥珀) | #67496f |
| gensokyou:ritual_stone_4 | 4(红) | #67386f |
| gensokyou:ritual_stone_5 | 5(紫) | #5b3179 |

- 品阶变体：`ritual_stone_slab_N` / `ritual_stone_stairs_N` / `ritual_stone_wall_N`（无独立贴图，复用本族贴图；id 形如 `<族>_N`）

## 品阶标签（下限语义）

- `#gensokyou:ritual_stones_N_plus` ⊇ 品阶 ≥N 的仪式石（N=1..5）；`#gensokyou:ritual_stones` = 0..5 全部。
- `#gensokyou:ritual_pedestals_N_plus` ⊇ 品阶 ≥N 的基座（N=2..5）；`#gensokyou:ritual_pedestals` = 全部。
- pattern palette 中 `品阶下限 = key 首现层级`（硬性不变量，详见 SKILL.md）。

## 原版常用建材（可自由使用，选材速记）

- `minecraft:end_stone` · 米黄粗石
- `minecraft:purpur_block` · 淡紫石
- `minecraft:purpur_pillar` · 淡紫柱
- `minecraft:amethyst_block` · 紫水晶块
- `minecraft:end_stone_bricks` · 米黄砖
- `minecraft:deepslate` · 深板岩
- `minecraft:deepslate_bricks` · 深板岩砖
- `minecraft:blackstone` · 黑石
- `minecraft:basalt` · 玄武岩柱状
- `minecraft:polished_basalt` · 玄武岩抛光
- `minecraft:dark_oak_planks` · 深橡木板(深棕)
- `minecraft:dark_oak_log` · 深橡木原木
- `minecraft:cherry_log` · 樱花木(粉白)
- `minecraft:cherry_planks` · 樱花木板
- `minecraft:purple_stained_glass` · 紫染色玻璃
- `minecraft:magenta_stained_glass` · 品红玻璃
- `minecraft:soul_lantern` · 魂灯笼(青焰)
- `minecraft:soul_fire` · 魂火(青)
- `minecraft:purple_banner` · 紫旗帜
- `minecraft:pink_petals` · 粉花瓣(地面)
- `minecraft:crying_obsidian` · 哭泣黑曜石(紫光)
- `minecraft:glowstone` · 萤石
- `minecraft:sea_lantern` · 海晶灯
- `minecraft:quartz_block` · 石英(白)
- `minecraft:oxidized_copper` · 锈铜(青绿)
- `minecraft:waxed_oxidized_copper` · 防锈铜
- `minecraft:moss_block` · 苔藓
- `minecraft:cobweb` · 蛛网
- `minecraft:iron_bars` · 铁栏杆
- `minecraft:chain` · 铁链
