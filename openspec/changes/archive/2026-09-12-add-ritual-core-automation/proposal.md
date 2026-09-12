# Proposal: add-ritual-core-automation

## Why

仪式祭品台目前只能靠玩家逐台右键放料，漏斗/管道等人形以外的自动化方式完全无法与仪式交互。需要把仪式核心变成一个对自动化友好的"箱子视图"（容量 = 结构内祭品台数），让投料与取料都经由外力完成，同时确立"一台一件"的祭品台容量不变量，并顺手修复核心 GUI 首帧按钮闪烁问题。

## What Changes

- 仪式核心方块挂接 NeoForge `IItemHandler` capability（本 mod 首个 capability）：槽位与成型结构内祭品台按规范序一一对应，每槽容量 1，作为祭品台的**活代理**（核心自身无背包、无 NBT 复制）；未成型/失效时 0 槽。全方向暴露，不做物品过滤（纯箱子语义）。
- **祭品台单件不变量**：台面持有物上限收紧为 1 个物品。手动右键逐台放料行为不变（天然符合）。
- **BREAKING（行为变更）**：passive 配方产物不再写回祭品台，改为在以核心为圆心、水平半径 3 格的圆盘内随机位置掉落为实体物品（修正现有实现与"产物扔地上"原始设计的偏离）。
- **BREAKING（schema 收紧）**：仪式 JSON `requirements` 的 `count` 字段废弃——每条要求恒为 1 个物品，loader 拒载 `count > 1`。多件需求 = 多条 requirement 绑定不同台位槽。
- bugfix：`RitualCoreScreen` 启停按钮在 `init()` 建完即置 `visible=false`，由 `containerTick` 按 payload 点亮，消除非 toggleable 仪式（如加具土命之焰）打开界面时两个按钮首帧闪现。
- 文档：ritual-design skill §4 硬性设计不变量补录"一台一件"。

明确不做：GUI 投料槽位、核心右键手动投料、核心主动抽取/输出能力、面向自动化 mod 的额外适配层、"收集仪式"（未来独立立项）。

## Capabilities

### New Capabilities
- `ritual-core-automation`: 仪式核心的 IItemHandler 自动化接口——槽位⇄祭品台活代理、单件容量、未成型零槽、漏斗/管道通用读写语义。

### Modified Capabilities
- `ritual-pedestal`: 台面持有从"右键逐个放入"收紧为硬性单件上限（容量 1）。
- `ritual-recipes`: 持续型执行的产物落位从"写回第一个被消耗台位"改为"核心顶面半径 3 圆盘内随机掉落"；删除"容量不足整单放弃"场景。
- `ritual-offerings`: `requirements` 声明移除 `count` 字段语义，单条要求恒 1 件，`count>1` 拒载。
- `ritual-core-interface`: 启停按钮首帧显隐即正确（不依赖第一 tick 的 payload 收敛），补充"不可开关仪式无按钮闪现"场景。

## Impact

- `block/entity/RitualCoreBlockEntity.java`：capability 宿主（活代理 handler）、`tickPassiveRecipes` 产物掉落改写、台面超限栈的容错处理。
- `block/entity/RitualPedestalBlockEntity.java`：`setHeld` 单件容量约束。
- `ritual/RitualPatternLoader.java`：`parseOffering` 拒载 `count>1`；`ritual/RitualPattern.java`：`Offering` 移除 `count`。
- `ritual/RitualOfferings.java`：校验/扣减按单件语义简化。
- `client/screen/RitualCoreScreen.java`：init 按钮初始隐藏。
- 新增 capability 注册入口（mod bus `RegisterCapabilitiesEvent`）。
- 测试与工具链：离线 pattern 校验脚本同步拒绝 `count>1`；新增漏斗投料与产物掉落的验证用例。
- 存档：现有 pattern JSON（仅 kagutsuchi）无 requirements，无迁移负担。
