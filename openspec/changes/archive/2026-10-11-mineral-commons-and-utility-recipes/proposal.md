## Why

大山祇神之座（矿产仪式）的下界池是玩家获取下界石料的主要途径，但当前只产出
`netherrack` / `magma_block` / 石英 / 残骸，缺少黑石与玄武岩两种基础建材——玩家想要它们
仍须亲自下界挖掘。与此同时，两个已注册的实用工具——**灵力筑基器**（`landscaping_tool`）
与**灵力引爆器**（`spirit_bomb`）——只有行为与配置，**没有任何生存获取路径**，
创造模式之外无法获得。

## What Changes

- 大山祇神之座的下界产出池新增**原版黑石与玄武岩**：权重各 4，全 6 工具档恒定，
  以**原版方块形态**产出（建材「体积」产出，不另立 mod 素材）。
- 新增**灵力筑基器**工作台配方：T0 入口料（仪式石 0 + 铁 + 精炼辰砂），前期可做。
- 新增**灵力引爆器**源初造化之仪配方：`minTier:1`、`spCost:20000`，与
  `zaohua_danmaku_weapon_frame` 同档；原料不含任何 T2 料或 BOSS 掉落。
- 两者补齐中英语言/配方展示（JEI 归位），不改动任何行为数值与配置。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `gensokyo-materials`: 矿产仪式下界池新增原版黑石 / 玄武岩体积产出。
- `gensokyou-utility-tools`: 灵力筑基器与灵力引爆器的生存获取配方。

## Impact

- **数据**：`data/gensokyou/ritual_loot/oyamatsumi_circle.json`、
  `data/gensokyou/recipe/landscaping_tool.json`（新增）、
  `data/gensokyou/ritual_recipes/zaohua_circle.json`。
- **代码**：无（纯数据；JEI 既有收录逻辑自动命中）。
- **测试**：既有 `RitualRecipePedestalCapacityTest`、配方加载相关测试；可加一条
  「两工具均有生存配方」的资源测试。
- **无关**：不改仪式行为、不改工具配置、不改任何品阶或战斗数值。
