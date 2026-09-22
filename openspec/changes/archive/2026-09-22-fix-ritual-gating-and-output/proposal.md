## Why

三处与"状态门控"和"产物落点"相关的体验问题需要收口：

1. 无尽藏终端在**未启动**时仍完整暴露聚合仓储并可任意存取，与"停机即隐藏晶块"的既有语义不一致，也让未启动的托管仓储成为零成本共享仓库。
2. 仪式构建杖无视玩家**世界进度**，可从菜单直接选到并搭建远超当前进度的阶级（如仅进地狱即可选 5 阶），绕过了指导书的进度解锁节奏。
3. 生产类产物（献祭族、绵津见）从核心上方**最高 20 格无遮挡点**落下，落点随环境高度漂移、产物散乱；而被动配方产物已是"核心上一格、半径 3"口径，三处不一致。

## What Changes

- **无尽藏终端（未启动锁仓储）**：核心未启动（`!enabled`）时，右侧聚合仓储网格置灰且不显示任何条目内容；服务端同步空页并拒绝一切取出手势、存入手势、背包 Shift 存入与 JEI 从仓储取料。启动后恢复现状。左侧信息/启停区、原版 3×3 合成格、玩家物品栏不受影响。
- **构建杖进度限阶**：新增玩家"世界进度阶梯"（0-5）读取——进地狱=1、进末地=2、进幻想乡=3、4/5 由 `temperLevel` 过渡承接（与指导书世界进度同源，复用 `guide/nether_unlock` 等 advancement）。菜单据此过滤：
  - 图案**最低可建阶 > 进度** → 整条图案不在列表出现；
  - 品阶按钮只显示**属于图案 `tiers` 且 ≤ 进度**的项；
  - 创造模式（`hasInfiniteMaterials`）视为进度 5，不受限。
- **构建杖服务端权威校验**：选择写回（`RitualSelectPayload`）与两段式搭建执行均加进度闸，防止改包或跨玩家传递物品越界；开菜单时把进度上限随现有握手数据下发客户端供菜单过滤。
- **生产产物落点统一**：献祭族与绵津见改为与被动配方产物同口径——**核心上方第 1 格、水平半径 3 的圆盘区域内随机**；移除随之失效的 `dropHeight()` 与 `SACRIFICE_FALL_MAX_HEIGHT` 配置。

**BREAKING**：移除 `SACRIFICE_FALL_MAX_HEIGHT` 配置键（旧存档配置中的该键将被忽略，无迁移动作）。

## Capabilities

### New Capabilities
<!-- 无新增 capability -->

### Modified Capabilities
- `wujinzang-storage-terminal`: 新增"未启动仓储锁定"要求；存取手势、背包 Shift 存入、JEI 取料在停止态均不可用，网格不显示内容。
- `ritual-builder-menu`: 图案列表与品阶按钮按玩家世界进度过滤（最低阶高于进度时整条隐藏）。
- `ritual-builder-item`: 服务端选择/构建按世界进度校验；开菜单时随现有握手数据下发进度上限。
- `tool-sacrifice-ritual`: 产物空投落点由"核心上方最高无遮挡点"改为"核心上 1 格、半径 3 圆盘"。
- `watatsumi-fishing-ritual`: 产物空投落点同献祭族口径。

## Impact

- 代码：
  - `menu/WujinzangTerminalMenu`、`client/screen/WujinzangTerminalScreen`（未启动锁仓储 + 置灰）
  - `item/RitualBuilderItem`、`menu/RitualBuilderMenu`、`client/screen/RitualBuilderScreen`、`network/ModNetworking`（进度限阶 + 握手携带进度）
  - `ritual/behavior/ToolSacrificeBehavior`、`ritual/behavior/WatatsumiBehavior`（产物落点）
  - `config/GensokyouConfig`（删配置项）
  - 新增服务端世界进度读取工具（读取 advancement；不新增 advancement 数据）
- 数据：无需新增数据文件；复用既有 `data/gensokyou/advancement/guide/*`。
- 兼容：旧配置的 `sacrificeFallMaxHeight` 键失效；无存档迁移义务（开发期）。
- 风险：核心上 1 格可能落在结构方块/被占格内，物品实体可能被挤出或悬浮——需在 design 决定是否加落点兜底。
