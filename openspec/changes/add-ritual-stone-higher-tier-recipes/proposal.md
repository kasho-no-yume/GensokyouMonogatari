## Why

`ritual_stone_0` 有工作台配方（圆石 ×8 + 引导残页）。`ritual_stone_1` 与 `ritual_stone_2` 各有一条源初造化之仪的激活配方。但 `ritual_stone_3` / `4` / `5` **在全项目范围内没有任何配方**——既没有工作台配方，也没有仪式配方。

同时 `ritual_stone_2` 的唯一配方（`zaohua_stone_t2`）依赖 `star_silver` 与 `tide_crystal`，两者均为 T2 幻想乡材料，被 `sukima_fragment` 死锁。这条链当前是靠**临时占位**维持的，不是设计意图。

由于大量仪式的 palette 使用 `#ritual_stones_N_plus` 标签，缺失的高阶仪式石直接决定了这批仪式**能被建到第几阶**：

| 仪式 | palette 最高要求 | 当前可达最高阶 |
|---|---|---|
| `barrier_break_circle` | `ritual_stones_2_plus` | 受 `ritual_stone_2` 死锁 |
| `bafang_guiyuan_circle` | `h: ritual_stones_5_plus` | 受 `ritual_stone_2` 死锁 |
| `resonance_relay` | `4: ritual_stones_4_plus` | 受 `ritual_stone_2` 死锁 |

`bafang_guiyuan_circle` 的祭品台数按阶为 4 / 8 / 16 / 24，其托管核的接受条件是 `coreTier <= ritualLevel`（`BafangGuiyuanBehavior:65`）——**阶位直接决定它能托管多高阶的灵力核**，因此高阶仪式石缺失会连带锁死高阶灵核的托管能力。

## What Changes（本变更仅为占位记录，暂不实现）

- 记录需求：`ritual_stone_2` SHALL 有稳定的正式配方，SHALL NOT 依赖占位。
- 记录需求：`ritual_stone_3` / `4` / `5` SHALL 各有配方。
- 配方归属（源初造化之仪 / 其他仪式 / 工作台）**在本占位变更中尚未决定**。

玩家已确认的边界：`ritual_stone_2` 的设计意图是「进入末地之后即可做出」，当前配方为临时占位，不属于本变更要固化的内容。

## Capabilities

### New Capabilities

- `ritual-stone-higher-tier-recipes`: 高阶仪式石配方（本占位变更仅声明需求，不含可执行场景）。

## Impact

- 本次不改动任何数据或代码，是纯需求占位。
- 阻塞 `add-barrier-break-ritual` 的**实机生存验证**（不阻塞其编码）。
- 阻塞 3 阶及以上一切仪式结构，含 `bafang_guiyuan_circle` 与 `resonance_relay` 的高阶形态。
- 与 `add-sukima-fragment-source` 强耦合：后者不落地则本变更的 2 阶配方无解。

## Open Questions

1. `ritual_stone_2` 的正式配方应归属哪个仪式？源初造化之仪（`zaohua_*` 族）是最自然的候选，但它当前是「万物初开」定位。
2. 3 / 4 / 5 阶的配方是否应改由**幻想乡侧内容**产出（BOSS 掉落 / 寝宫 / 高阶仪式），而非源初造化？
3. 是否需要为每阶设置独立的世界进度门槛（`guide/*_unlock`），还是由配方材料自然构成门槛？
