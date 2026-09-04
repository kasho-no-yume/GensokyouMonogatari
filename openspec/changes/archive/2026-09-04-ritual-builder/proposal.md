# Proposal: ritual-builder

## Why

多方块仪式目前只能手动逐格摆放（或 `/gs_ritual_capture` 采集后照图手搭），大型环形祭坛摆放繁琐且易错。需要一个"仪式构建器"物品：选定仪式后右键仪式核心，一键消耗背包材料搭成结构，把玩家从重复摆放中解放出来，同时保留材料收集这一玩法成本。

## What Changes

- 新物品 `ritual_builder`（仪式构建器）：潜行右键（对空或对方块）打开仪式选择菜单；右键仪式核心执行一键搭建。
- 新菜单：列出全部已加载仪式图案 + 品阶选择，实时显示所选仪式的所需方块与身上持有数量；选中结果存入物品 Data Component。品阶按钮由**图案声明的 `tiers`** 驱动（非硬编码 0-5），无标签格位的仪式隐藏品阶行。
- **仪式图案 schema 扩展**：`RitualPattern` 新增可选 `tiers` 字段（0-5 整数数组），声明该仪式允许的标签实例化品阶；缺省=全 0-5，既有图案零改动向后兼容。
- 物品 tooltip 动态显示：所选仪式名、品阶、每种材料"需求 ×N / 持有 ×M"（不足红色）。
- 搭建逻辑（服务端）：
  - 目标位已被**非目标方块**占据 → 整体中止（一个方块也不放），冲突方块红色线框提示。
  - 材料不足但无冲突 → 按规范序尽力放置可用材料（不降品阶、不跳序补位）。
  - 已满足谓词的格子（含已摆对的方块）跳过、不消耗。
  - 创造模式不消耗材料。
- 新网络包：C2S 选择确认、S2C 冲突坐标列表；客户端红色线框渲染（限时）。
- 不改动 `RitualCoreBlock`/`RitualMatcher`：构建器走物品 `useOn` 路径（核心对未成型/无行为 PASS 让位，与召唤催化剂同链路）。
- 构建器仅创造模式可得，暂不出合成配方。

## Capabilities

### New Capabilities

- `ritual-builder-item`: 构建器物品本体——注册、选择数据组件、潜行右键开菜单、动态 tooltip（需求 vs 持有）。
- `ritual-builder-menu`: 仪式选择菜单——图案列表、品阶选择、材料计数展示、C2S 选择写回组件。
- `ritual-builder-placement`: 一键搭建算法——冲突预检全量中止、材料不足尽力搭建、规范序放置、创造免耗。
- `ritual-builder-conflict-feedback`: 冲突红色线框——S2C 坐标下发、客户端限时渲染、再次尝试时刷新。

### Modified Capabilities

- `ritual-pattern-system`: 图案 JSON 新增可选 `tiers` 字段（仪式允许的品阶集合，缺省全 0-5）；`RitualPattern` 记录随之扩展。既有匹配/采集语义不变。

（`ritual-core-interface` 的"右键让位手中物品"既有语义不变，本变更纯依赖不修改。）

## Impact

- **Java**：新增 `RitualBuilderItem`、`RitualBuilderMenu`/`Screen`、`RitualBuilderPlacement`（服务端算法）、冲突渲染器；`ModItems`/`ModMenus`/`ModDataComponents`/`ModNetworking` 各加注册项；`RitualPattern` 记录加 `tiers` 字段、`RitualPatternLoader` 解析该字段。
- **资产**：`ritual_builder.png`（gen_tex 工具生成）、block/item 模型、lang 键。
- **依赖既有**：`RitualPatternLoader`（双端可用）、`ModBlocks.tierOf` 品阶方块族、`LevelRenderer.renderLineBox`（1.21.1 public static，已验证）。
- **配置**：冲突线框持续秒数进 `GensokyouConfig`。
