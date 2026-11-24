## Why

`rework-gensokyo-dimension` 已经把八区三阶（T3）矿物与特色植物完整生成、注册并配上掉落，
但它们**没有任何消费端**：除 `starcrown_log → starcrown_planks` 外，全部矿产与衍生品都只能
堆在背包里。T3 矿与灵炭的冶炼关系、以及各素材的配方用途只停留在
`tier-3-materials-review.md` 的定性描述，没有数值、没有 spec、没有实现。

本提案专门承接这些"新维度的衍生事项"，与维度生成解耦，独立排期。

## What Changes

- 将 8 种 T3 矿（`wind_mica`、`inkglow_stone`、`ringed_jade`、`snowmarrow_crystal`、
  `echo_crystal_ore`、`rose_garnet`、`mistband_crystal`、`nether_black_sand`）接入
  金山彦命煅炉的专属精炼规则，**原矿 ×1 对应灵炭 ×2**（与星银同档），方块原矿享受矿石翻倍。
- 为没有可用形态的 6 种矿新增精炼产物物品；复用已有的 `echo_crystal_shard` 与 `black_sand_iron`。
- 给全部 T3 精炼料与植物素材各配至少一条消费端配方，主料 4:1（碎片/铁粒 8:1），
  每条配方只吃一种 T3 主料，避免一件成品要求跑遍所有群系。
- 明确 T3 定位：区域工艺材料，不新增装备阶级，不进入原版熔炉。

## Capabilities

### New Capabilities

- `tier3-material-refining`：三阶矿的灵炭精炼比例、精炼产物、消费端配方与数值。

### Modified Capabilities

- `kanayamahiko-smelting`：专属精炼规则清单增加 8 条 T3 原矿规则；保持"未列出物品无专属规则"语义。

## Impact

- 数据：`ritual_smelt_recipes/kanayamahiko_circle.json` 增加 8 条规则；新增 crafting recipe。
- Java：`ModItems` 注册 6 个精炼产物；`ModItems` 物品模型/贴图/语言键；`c/tags/{block,item}/ores.json` 增加 T3 矿以获取翻倍。
- 文档/指导书：新增精炼产物词条；`FantasyMaterialsBookTest.MINERALS` 精确集合断言需同步。
- 不改变维度生成与现有 T1/T2 精炼规则。
