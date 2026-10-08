## Why

仪式构建器目前**只能从玩家背包扣料**（`RitualBuilderPlacement.consumeOne`）。高阶/大型法阵动辄需要成百上千块仪式石，玩家必须把材料全部背在身上才能一次搭完；而「无尽藏」已经能把海量物品托管进 128 块晶块仓储，两者却互不相通。把一座无尽藏"绑"到构建器上、搭建时自动从仓储补料，可一次性消除搬运负担，也让无尽藏从"存东西"升级为"建设后勤"。

## What Changes

- 构建器新增**绑定数据组件**（维度 + 核心坐标），记录绑定的无尽藏核心；缺失即未绑定。
- **潜行右键手势**：对**已成型的无尽藏核心**潜行右键 = 绑定；对**同一座已绑定核心**再潜行右键 = 解绑（切换）。对其它仪式核心潜行右键 = 提示"非成型无尽藏"；对非核心潜行右键 = 照旧打开选择菜单。
- **搭建扣料**：背包优先，背包不足时从绑定的无尽藏仓储按方块精确取用（真实消耗，非复制）；仓储不足或不可用时该格照旧跳过（保持"尽力搭建"语义）。
- **补料前提**：绑定核心当前须为**同维度、已成型、已启动**的无尽藏；否则仓储不参与扣料。
- **绑定生命周期**：目标未成形 / 未启动 / 异维度时**保留坐标**，恢复后自动可用；不做主动解绑（由玩家手势解绑）。
- **材料计数并入仓储**：构建器 tooltip、选择菜单材料区、预览材料缺口 HUD 的"持有/缺口"口径并入绑定无尽藏的库存；服务端按当前构建器选择与预览图案所需的**方块子集**推送 S2C 快照，避免整发全仓储。
- **指导书同步**：`ritual_builder` 条目与仪式入门条目补充绑定用法说明。

## Capabilities

### New Capabilities
- `ritual-builder-wujinzang-binding`: 构建器绑定无尽藏核心的数据模型、绑定/解绑手势与生命周期、搭建补料口径、以及三处材料计数并入绑定仓储的 S2C 同步契约。

### Modified Capabilities
- `ritual-builder-item`: 潜行右键在成型无尽藏核心上由"打开选择菜单"改为"绑定/解绑切换"；tooltip 增加绑定坐标行；材料 tooltip 的"持有"并入绑定仓储。
- `ritual-builder-placement`: "材料不足尽力搭建"的扣料由"仅背包"改为"背包优先 + 绑定无尽藏兜底"。
- `ritual-builder-menu`: "材料需求与持有实时对比"的持有数并入绑定仓储（开屏快照口径）。
- `ritual-preview-material-hud`: "缺口数量计算"的持有数并入绑定仓储。

## Impact

- **代码**：`ModDataComponents`（新增绑定组件）、`RitualBuilderItem`、`RitualBuilderPlacement`、`RitualCoreBlock`（潜行分支对构建器让位，使物品 `useOn` 能在成型核心上收到手势）、新增 S2C 材料计数 payload 与客户端缓存、`RitualBuilderScreen`、`RitualPreviewMaterialHud`；读取侧复用 `WujinzangStorage` / `WujinzangBehavior`；可能需在无尽藏成型时补齐**核心区块强加载**，保证异地扣料时 BE 可读。
- **配置**：新增同步刷新间隔等可调项进 `GensokyouConfig`（COMMON）。
- **资源/文案**：`assets/gensokyou/lang/zh_cn.json`（tooltip、提示、指导书正文）、Patchouli 条目 `ritual_builder.json`。
- **不改**：`RitualMatcher`、现有 pattern 与既有能力语义；无尽藏终端与"停止态仓储锁定"的既有规则不变（本变更只是新增一个**受同一 `enabled` 闸门约束**的读取方）。
