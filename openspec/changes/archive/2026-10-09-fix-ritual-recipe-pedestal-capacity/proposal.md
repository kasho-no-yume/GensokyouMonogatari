## Why

八百万神恩（`gensokyou:kami_no_megumi_circle`）1~2 阶的 4 条配方，原料总量（`Σcount`）超过对应阶级结构的祭品台数量（1 阶 4 台 / 2 阶 8 台），而祭品台是「一台一件」——超量配方在启动时被匹配器静默判负，玩家**怎么摆都无法触发**，且没有任何报错。同类隐患另有两处：`zaohua_stone_t1`（Σ12 > 0 阶 8 台）与 `zaohua_codex_of_beings`（Σ17 > 1 阶 16 台）。此外 2 阶以上配方材料偏廉价，与阶级不符。

## What Changes

- **重制八百万神恩 1~2 阶 4 条配方**：1 阶改用灵铁 / 钻石 / 下界合金碎等珍稀材料；2 阶改用星银 / 符卡星 / 隙间碎片等幻想乡高级材料。每条 `Σcount` **恰等于**该阶台位数（4 / 8）。
- **补满八百万神恩 3~5 阶 6 条配方**：沿用现有材料类型，仅把数量补到 `Σcount` **恰等于**台位数（12 / 16 / 20），不再留余量。
- **修正 zaohua 两条超量配方**：`zaohua_stone_t1`（Σ12→8）、`zaohua_codex_of_beings`（Σ17→16），使之落在各自 `minTier` 的台位预算内。
- **新增容量不变量**：为仪式配方引入「原料总量 MUST NOT 超过其 `minTier` 对应结构的祭品台数量」的规约与校验，杜绝同类静默失配复发。

## Capabilities

### New Capabilities
<!-- 无新增能力 -->

### Modified Capabilities
- `ritual-recipes`: 新增「配方原料总量 ≤ 祭品台容量」的硬性约束与校验语义。
- `yaoyorozu-grace-ritual`: 10 条配方的材料带与容量口径细化（1~2 阶换材、3~5 阶补满、逐阶恰好填满台位）。
- `zaohua-crafting`: `zaohua_stone_t1` 配方数量下调至台位预算内。
- `codex-of-beings`: 众生典籍配方数量下调至台位预算内。

## Impact

- 数据：`src/main/resources/data/gensokyou/ritual_recipes/kami_no_megumi_circle.json`、`.../zaohua_circle.json`
- 代码：仪式配方的容量校验（离线校验器 / CI 对表测试；不要求改动 loader 拒载语义）
- 规格：上述 4 个 capability 的 spec 增量
- 无存档迁移、无方块/注册变更；已有建成的仪式结构不受影响，仅配方判定变化
