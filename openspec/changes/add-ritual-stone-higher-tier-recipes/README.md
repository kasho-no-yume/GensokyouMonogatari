# add-ritual-stone-higher-tier-recipes

## Why

`ritual_stone_3/4/5` 全项目零配方；`ritual_stone_2` 依赖被 `sukima_fragment` 死锁的 T2 材料，靠临时占位维持。缺失直接决定一批 `#ritual_stones_N_plus` 仪式能被建到第几阶。

## What Changes（本变更仅为占位记录，暂不实现）

- 记录需求：`ritual_stone_2` SHALL 有稳定的正式配方。
- 记录需求：`ritual_stone_3/4/5` SHALL 各有配方。
- 配方归属待定。

## Capabilities

### New Capabilities

- `ritual-stone-higher-tier-recipes`: 声明需求，本占位变更不含可执行场景。

## Impact

- 纯需求占位，不改动数据或代码。
- 阻塞 `add-barrier-break-ritual` 的实机生存验证，以及 3 阶以上全部仪式结构。
