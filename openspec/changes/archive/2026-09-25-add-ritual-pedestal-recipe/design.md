## Context

`ritual_pedestal` 是唯一注册的祭品台方块/物品，`_0.._5` 只是同一 blockstate 的染色变体，品阶由仪式核心在结构重扫时写入（见已归档能力 `ritual-pedestal`、`tier-color-palette`）。因此"祭品台配方"只有一条，不存在分阶配方。

当前生存链断点：所有 `rituals/*.json` 图案都含 `#gensokyou:ritual_pedestals` 格位，而祭品台本身无任何获取路径——`data/gensokyou/recipe/` 下没有 `ritual_pedestal.json`，`ritual_recipes/*.json` 也无人产出它。玩家能造出 `ritual_stone_0`（8 圆石 + 1 P 点）与 `ritual_core`（4 仪式石 + 4 P 点 + 钻石），但摆不出第一个承台。

现有 bootstrap 物价（用于定价校准）：

| 物品 | 配方 | P 点成本 |
|---|---|---:|
| `ritual_stone_0` ×8 | 8 圆石 + 1 P 点 | 0.125 /块 |
| `ritual_core` ×1 | 4 仪式石 + 4 P 点 + 钻石 | 4 |
| `spirit_core_0` ×1 | 4 仪式石 + 4 P 点 + 紫水晶块 | 4 |
| `danmaku_assembly_bench` ×1 | 5 仪式石 + 2 铁 + 1 符卡星 | 0.625 |

P 点没有配方，唯一来源是仙人 10% 掉落（`fairyPpointDropChance`）与铃奈崎，因此 P 点是开局最稀缺的资源。

## Goals / Non-Goals

**Goals:**

- 让玩家在**不使用任何仪式**的前提下造出祭品台，打通第一个仪式。
- 定价使 4～8 个祭品台的首个仪式不被 P 点 RNG 卡死。
- 在指导书中给出祭品台的准确工作台配方页。
- 保持祭品台"单一物品 + 自动染色"架构不变。

**Non-Goals:**

- 不引入 `ritual_pedestal_1/2` 等分阶物品或品阶数据组件（已确认不需要）。
- 不改祭品台的存取、单件不变量、渲染姿态、变色与结构匹配行为。
- 不给祭品台加 secret 门槛或世界进度 advancement。
- 不调整仪式石、仪式核心、灵力核心的既有定价。

## Decisions

### 1. 配方为工作台有序合成 `SSS / RPR / SSS`

| 格位 | 物品 | 数量 |
|---|---|---:|
| S（上/下各 3） | `minecraft:smooth_stone` | 6 |
| R（左右中） | `gensokyou:ritual_stone_0` | 2 |
| P（正中） | `gensokyou:ppoint` | 1 |

产出 `ritual_pedestal ×1`。

选择平滑石而非纯仪式石：祭品台是"承台"而非结构石，用平滑石铺出平整台面、仪式石做承力侧柱、P 点做中心聚焦点，玩家在 JEI 里能一眼区分"这是祭台，不是仪式石"；同时 6 平滑石（6 次熔炉）不引入新的稀有材料依赖。

### 2. 单台成本 1 P 点，不给批量产出

首个仪式（源初造化 0 阶）需 8 个祭品台，即 8 P 点 + 16 仪式石 + 48 平滑石。定价取舍：

- 仪式石本身 0.125 P 点/块，祭品台 1 P 点/个属于同一量级，不会让祭品台吞掉 P 点预算。
- 不做"8 仪式石 + 1 P 点 → 2 个"这类批量配方，是因为批量配方会与 `ritual_stone_0` 的 `### / #P# / ###` 视觉语汇撞脸，JEI 里极易与"造 8 个仪式石"混淆。
- 首批 8 个祭品台合计 8 P 点，低于 `ritual_core` 单件的 4 P 点 ×2，属可接受的一次性投入。

### 3. 祭品台不分阶

已在设计阶段确认：品阶是 blockstate 染色而非物品属性，给祭品台做 1/2 阶配方要么产出同一个物品（无机制差异的假升级），要么推翻 `ritual-pedestal` / `tier-color-palette` / `ritual-pattern-system` 三条既有需求。因此本变更只提供一条 bootstrap 配方。

### 4. 指导书复用既有 crafting 配方页管线

词条由 `tools/gen_ritual_book_entries.py --items-only` 生成，`crafting` 来源分支已存在（`patchouli:crafting` + `item_recipe.crafting.p1`）。为避免把"bootstrap 电池"语义套到祭品台上，新增按物品覆盖说明文本键的映射，祭品台使用专属文案 `item_recipe.pedestal.p1`。词条无 `advancement`/`secret`，常驻可见。

## Risks / Trade-offs

- **祭品台仍是最便宜的仪式结构件** → 首个仪式的真实成本由仪式石、仪式核心与灵力核心承担；祭品台便宜是有意为之，否则玩家永远建不出第一个承台。
- **48 平滑石的熔炉开销** → 玩家通常已有熔炉，且平滑石是纯 vanilla 资源，不构成进度锁。
- **P 点仍受仙人 RNG 影响** → 与 `ritual_stone_0`、`ritual_core`、`spirit_core_0` 的既有定价一致；本变更不调整 P 点来源。
- **新增 `item.gensokyou.ritual_pedestal` 语言键** → 祭品台是 BlockItem，此前仅有 `block.*` 键；补 `item.*` 键只为 Patchouli 词条标题稳定显示，文本与 `block.*` 一致。

## Migration Plan

1. 新增 `recipe/ritual_pedestal.json`。
2. 生成器增加祭品台条目与文本覆盖，重建物品词条。
3. 补齐中英文语言键。
4. 验证：首个仪式（源初造化 0 阶）在不接触任何仪式的情况下可摆出 8 个祭品台。

## Open Questions

- 无。1/2 阶祭品台已确认不需要。
