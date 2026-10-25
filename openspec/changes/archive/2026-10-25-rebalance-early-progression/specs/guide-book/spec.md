## MODIFIED Requirements

### Requirement: 进度解锁机制

书 SHALL 提供隐形引导 advancement：`guide/nether_unlock`（进入下界）、`guide/end_unlock`（进入末地）、`guide/gensokyo_unlock`（进入幻想乡维度），均为 `changed_dimension` 触发器、无 display。需要门槛的内容条目 SHALL 以 `"advancement"` + `"secret": true` 声明：解锁前条目在书中**完全不显示**（非灰色锁定态），解锁时弹出 toast。

内容门槛 SHALL 按**内容所属的轴**选择，MUST NOT 一律按结构阶挂：

- **材料带**：T1 材料页（指导书带）MUST NOT 挂额外 advancement；T2 材料页（花精之滴带）SHALL 挂 `guide/nether_unlock`——花精之滴由 T1 阶段的大妖精产出，玩家在进入末地前就可能采到 T2 材。
- **结构阶**：结构/仪式词条 SHALL 按结构阶挂 `guide/nether_unlock`（1 阶）/ `guide/end_unlock`（2 阶）/ `guide/gensokyo_unlock`（3 阶）；2 阶石与 2 阶结构继续挂末地。
- **BOSS**：按 `bossTier()` 挂对应门槛（沿用「BOSS 条目的阶级门槛」）。
- **玩家阶**：神恩相关条目挂对应世界进度或玩家阶，二者取其一并写明。

0 阶内容条目 MUST NOT 挂任何 advancement（无条件可见）。结构阶 4/5 的门槛在阶段 4/5 世界进度落地前 SHALL 以过渡实现承载（`temperLevel` 授予）。门槛判定 SHALL 以世界进度而非玩家超人类阶级（temperLevel）为唯一依据；玩家阶级不得替代世界进度解锁材料/结构页。

#### Scenario: 1 阶结构页按末地前的世界进度

- **WHEN** 玩家从未进入下界时阅读仪式章节
- **THEN** 挂 `guide/nether_unlock` 的 1 阶结构页不出现；对应 0 阶条目正常可见

#### Scenario: 进入下界解锁 1 阶结构

- **WHEN** 玩家进入下界后重开仪式章节
- **THEN** 1 阶结构页出现并收到解锁 toast

#### Scenario: 星银材料页在下界后出现

- **WHEN** 玩家已进入下界、尚未进入末地
- **THEN** `material_star_silver` 词条可见，且正文说明花精之滴是 T2 材料带信物

#### Scenario: 2 阶结构页仍按末地

- **WHEN** 玩家已进入下界但未进入末地
- **THEN** 挂 `guide/end_unlock` 的 2 阶石/2 阶结构/2 阶核心/八云蓝条目不出现

#### Scenario: 3 阶内容按幻想乡解锁

- **WHEN** 玩家进入幻想乡维度后
- **THEN** 挂 `guide/gensokyo_unlock` 的 3 阶内容条目出现

#### Scenario: 0 阶内容无条件可见

- **WHEN** 新建世界、玩家尚未进入下界
- **THEN** 0 阶石/0 阶仪式/材料带总览等无 advancement 条目正常可见

## ADDED Requirements

### Requirement: 仪式入门展示仪式石升阶配方

「仪式列表」章节的第一条「仪式入门」SHALL 在原有正文/构建器页之后增加两页
`gensokyou:ritual_page`，分别按**完整配方 id** 绑定：

- `gensokyou:zaohua_stone_t1`（0 阶石 → 1 阶石）
- `gensokyou:zaohua_stone_t2`（1 阶石 → 2 阶石）

两页 MUST NOT 依赖全局配方排序（`recipe_index`）或 JEI；MUST 在配方数据未到达时呈现占位，
不得渲染空白页或裸 id。0 阶/1 阶石页 SHALL 常驻可见；2 阶石页 SHALL 挂 `guide/end_unlock`
（其配方需要末地紫珀块）。页面 SHALL 复用 `ritual_page` 的配方卡渲染，显示原料×数量、
产物×数量、spCost 与结构阶级。

#### Scenario: 0 阶/1 阶石页常驻

- **WHEN** 新玩家第一次打开「仪式入门」
- **THEN** 能看到 0 阶石→1 阶石与 1 阶石→2 阶石的配方页（2 阶页在进末地前隐藏）

#### Scenario: 2 阶石页按末地解锁

- **WHEN** 玩家进入末地后打开「仪式入门」
- **THEN** 2 阶石配方页出现，显示 2 仪式石 1 + 4 灵铁 + 潮汐晶 + 三植物 + 6 紫珀块 → 4 仪式石 2

#### Scenario: 配方按 id 绑定

- **WHEN** `zaohua_circle.json` 的配方顺序发生变化
- **THEN** 两页仍分别显示 `zaohua_stone_t1` / `zaohua_stone_t2`，不显示其它配方

#### Scenario: 数据未到达时占位

- **WHEN** 客户端刚登录、服务端配方数据尚未同步
- **THEN** 两页显示占位而非空白或裸 id；数据到达后在同一会话内刷新
