# Proposal: ritual-builder-preview

## Why

构建杖现状是"右键即建"：玩家无法预先看到结构占地是否合适，冲突只能等搭建中止后看红框。归档的 ritual-builder design 已把"幽灵预览"显式列为留作后续增强。本变更把交互升级为两段式确认（第一击实地投影、第二击确认建造），并顺手修复菜单材料列表溢出屏幕的问题。

依赖前置变更 `ritual-orientation-schema`：幽灵必须渲染"放置完成后的正确样子"，含朝向的格位 → BlockState 解析（`resolveState`）由该变更提供。

## What Changes

- **两段式建造**：非潜行右键核心，第一击进入预览态（服务端记录 per-player 预览态并 S2C 同步，不放置任何方块）；对同一核心、同一选择（图案+品阶）的第二击才执行实际搭建。菜单改选择不刷新已有预览；持新选择再右键核心 = 替换为新预览。
- **实地半透明投影**：自定义 shader（采样方块图集 × tint uniform，alpha≈0.45）+ 1.21.1 `renderSingleBlock` 七参重载，把全部待放置格以正确位置、正确朝向的幽灵方块渲染在真实世界中（深度测试开、深度写入关）。每格三态：待放置 = 白色幽灵；被占冲突 = 红色幽灵；AIR 谓词格被占 = 红色线框；已满足格不画（避免 z-fighting，实体方块已在场）。分类客户端每帧实时重算——预览期间拆掉占位方块，红标当帧消失，无需任何额外网络包。
- **材料列表溢出滚动**：菜单右列材料区与左列图案列表同款滚轮滚动 + 溢出滚动条，材料种类数不设上限。
- 抽取"格位三分类"纯函数（服务端搭建与客户端投影共用），谓词/冲突规则单一事实源。

## Capabilities

### New Capabilities

- `ritual-builder-preview`: 两段式预览确认流（服务端预览态、S2C 同步、替换/清除规则）与实地半透明结构投影渲染（三态分类、自定义 shader、渲染门控）。

### Modified Capabilities

- `ritual-builder-placement`: "右键核心触发搭建"改为"第二击确认才搭建"，首击语义变为进入预览；搭建算法本体（冲突零容忍、尽力放置）不变。
- `ritual-builder-menu`: 新增"材料列表溢出滚动"要求。

## Impact

- **代码**：`RitualBuilderItem.useOn`（两段式分发）、`RitualBuilderPlacement`（classify 抽取 + Level 放宽）、新增服务端预览态（transient attachment）、新增 S2C `RitualPreviewPayload`、新增客户端 `ClientRitualPreviewState` + `RitualPreviewRenderer`、`RitualBuilderScreen`（右列滚动）、客户端 shader 资产（`RegisterShadersEvent` + fsh/json）。
- **不改**：`RitualCoreBlock`/`RitualMatcher`/行为分发（成型核心仍走行为开 UI，预览只发生在未成型核心上）；冲突红框 S2C 链路保留（第二击仍可能因预览后新放置的方块而冲突）。
- **风险面**：自定义 shader 是试错型工作（编译失败 = 渲染异常），需 runClient 目检迭代；零槽菜单关闭后手持组件同步时序存疑 → 渲染门控只校验"手持构建器物品"，不校验组件内选择（服务端第二击比对才是权威）。
