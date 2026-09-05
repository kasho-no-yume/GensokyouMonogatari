# Design: ritual-builder-preview

## Context

- 现状交互：非潜行右键核心 = 立即搭建（`RitualBuilderItem.doBuild`）；冲突才发红框。归档 ritual-builder design 的 Non-Goals 明确"幽灵预览留作后续增强"。
- 客户端已具备全部投影数据源：图案经 `RitualPatternLoader` 双端注册（`all()` 客户端可读）；选择存物品 Data Component；世界方块状态客户端本地有。
- 渲染基建在仓：`RitualConflictRenderer` 示范了 `RenderLevelStageEvent` + 自定义 `RenderType` + 无深度测试线框的完整套路。
- API 已验证（sources jar）：`BlockRenderDispatcher.renderSingleBlock(BlockState, PoseStack, MultiBufferSource, int, int, ModelData, RenderType)` 七参重载存在——显式 RenderType 是幽灵渲染的入口。
- 半透明硬约束：BLOCK 顶点格式的顶点色 alpha 恒 255，原版 translucent shader 下不透明贴图渲染结果仍全不透明 → **必须自定义 shader 乘 uniform alpha**（Litematica schematic 幽灵同路线），无现成 vanilla RenderType 可用。
- 前置变更 `ritual-orientation-schema` 提供 `resolveState(pattern, entry, tier) → BlockState`（种类+品阶+朝向）。
- 用户拍板的交互规则：第一击出预览、同核心同选择第二击才建造；菜单改选择**不**刷新预览，仅"持新选择右键核心"才替换预览；被占格以红色幽灵显示，预览期间拆掉占位方块红标须实时消失。

## Goals / Non-Goals

**Goals:**
- 实地所见即所得的结构投影：正确位置、正确朝向、半透明，占地一目了然。
- 两段式确认：误触不建、建前可核；第二击判定服务端权威。
- 冲突在建造**前**就可见（红色幽灵），红框链路保留兜底"预览后、建造前"窗口期的新冲突。
- 材料列表任意行数不溢出。

**Non-Goals:**
- 不做"预览跟随选择实时刷新"（已否决，见上）。
- 不做多人协作预览/他人可见投影——预览是 per-player 客户端视觉。
- 不做逐格排除/微调（"不想建这格"）——仍是整体搭建语义。
- 不改成型核心的行为分发（成型后右键仍开仪式 UI，与本变更无交集）。

## Decisions

### D1 预览态服务端持有，transient attachment
`ServerPlayer` 挂 NeoForge attachment（`ModAttachments` 范式，无 codec 不序列化）：`record RitualPreviewState(ResourceLocation patternId, int tier, BlockPos corePos, ResourceKey<Level> dimension)`。否决纯客户端态：第一击不能让服务端 `useOn` 直接搭，"第几击"必须由服务端判定；否决存档持久：预览是会话级视觉，重登即清天然合理。生命周期：置入 = 第一击；清除 = 第二击建造成功/失败均清；随玩家对象消亡自动清（登出/换维度），无需 tick 扫描——核心被拆后服务端态虽陈旧但无害（客户端渲染门控兜底，见 D4）。dimension 字段专治"同连接内换维度/死亡：服务端态随实体重建清空，客户端静态态残留"的不同步（见风险表）。

### D2 两段式判定（`RitualBuilderItem.useOn`）
非潜行右键核心：读手上选择组件（无 → 现状提示"先选仪式"）；服务端比对 `preview == (clickedPos, selection.patternId, selection.tier)` **全等** → 执行 `doBuild` 并清预览；否则 → 覆写预览态 + S2C 下发 + action bar 提示"再次右键确认建造"。推论：中途换仪式/品阶后右键 = 替换预览（该击不建造），与用户规则一致。成型核心走不到这里（行为分发先截获开 UI），预览天然只发生在未成型核心。

### D3 S2C payload 单包双向
`RitualPreviewPayload(Optional<RitualPreviewState>)`：有值 = 置入/替换，空 = 清除（建造后下发）。手写 StreamCodec（RL string + varint tier + varlong pos），与 `RitualConflictPayload` 同范式。不做 C2S"请求预览"——预览置入就发生在第一击的 `useOn` 服务端路径里，零额外包。

### D4 客户端渲染门控 + 每帧三分类
`ClientRitualPreviewState` 静态持有 payload 内容。渲染条件（每帧）：态存在 ∧ 主/副手是 `ritual_builder` 物品 ∧ `corePos` 处仍是 `ritual_core` ∧ 图案 `byId` 仍在（热重载宽限）∧ 预览态 `dimension` 与玩家当前维度一致。**不校验组件内选择与预览一致**——零槽菜单关闭后手持组件同步时序不可靠（归档 design 已踩"组件不同步"坑），物品类型判定已足够，选择权威在服务端 D2。分类：调 D6 共用 classify，输入客户端 `level`。

### D5 自定义半透明 shader + 双 RenderType
`RegisterShadersEvent`（mod bus）注册 `shaders/ritual_ghost.json` + `.fsh`：采样方块图集 × uniform `Tint`（vec4：rgb 色调、a 透明度 ≈0.45）。`RenderType.create`：`DefaultVertexFormat.BLOCK` + 图集纹理 + `TRANSLUCENT_TRANSPARENCY` + 深度测试开/深度写入关（`NO_DEPTH_WRITE`）+ 自定义 shader state；`setStateSource` 喂 uniform。两实例：`GHOST`（白 tint）/`GHOST_CONFLICT`（红 tint）。渲染：`RenderLevelStageEvent` `AFTER_TRANSLUCENT_BLOCKS` 阶段，`renderSingleBlock(resolveState(...), pose, buffers, 满亮度, NO_OVERLAY, ModelData.EMPTY, GHOST*)`，两批各画各的 buffer，结束 `endBuffer`。线框（AIR 冲突格）复用冲突渲染器的 `OVERLAY_LINES` 画法。备选：半尺寸悬浮模型（Create 风）——否决，用户明确要"实地看到建成后样子"判占地。备选：`RenderSystem` 强改混合——否决，RenderType 状态机在 draw 时重置混合参数，不可靠。

### D6 三分类纯函数抽取（共用事实源）
`RitualBuilderPlacement.classify(Level, anchorPos, pattern, tier)` → `Classification(List<BlockEntry> pending, List<Conflict> conflicts, List<Conflict> airConflicts)`，`Conflict(BlockPos pos, BlockEntry entry)`（坐标供红框下发，条目供投影解析目标态——实现期微调：纯 BlockPos 会让红幽灵无从 resolveState）；`build` 改为消费它（行为逐位不变），客户端投影直接调用。签名从 `ServerLevel` 放宽到 `Level`（纯读）。AIR 谓词格被占 → `airConflicts`（无线框外的"要放的方块"，画红框不画幽灵）。已满足格不入任何列表 → 不画。`build` 的 `Result.conflicts` SHALL 取 `conflicts ∪ airConflicts` **并集**——现状 AIR 占位格混在 conflicts 里一并红框下发（`RitualBuilderItem` 直接发 `result.conflicts()`），拆分后必须并回，否则红框链路静默丢失 AIR 占位格。

### D7 材料区滚动 = 左列同款机制右列复用
`RitualBuilderScreen` 加 `matScroll`：滚轮命中判定按区域分流（x < RIGHT_X → 图案列表；材料视口内 → 材料列表）；材料视口 = `MAT_Y .. 面板底-4`，可视行数 = 视口高/18 向下取整；溢出画滚动条（把左列 `renderScrollbar` 泛化为 `(x, top, h, total, visible, scroll)` 私有工具）。点击/悬浮命中判定基于"可视行下标 + scroll 偏移"，与渲染循环同一映射，杜绝画得出点不着。

## Risks / Trade-offs

- [shader 编译失败/参数错位 = 黑屏或崩溃] → 先以最小 quad 冒烟（临时渲染一格），`RegisterShadersEvent`/`CoreShaders` 命名与 json 结构按 sources jar 验证后再写全；失败仅影响预览渲染，不波及搭建主链路。注册链路已有在仓先例（`sukima_portal`），新增面仅 BLOCK 顶点格式 shader 本体（克隆 `rendertype_translucent` 三件套 + Tint uniform）。
- [零槽菜单关闭后组件回同步不可靠，门控若查选择会误灭] → D4 明确只查物品类型；服务端 D2 全等比对兜住权威判定。
- [预览后玩家绕场塞方块再第二击] → 第二击走原冲突预检，红框中止（现有链路），预览态清除；所见非最终所建，可接受（再点一次即新预览）。
- [每帧 classify 开销] → 图案 ≤ 数十格 × `getBlockState`（客户端缓存区块），远低于噪声；无逐帧对象分配热点（列表复用可后置优化）。
- [深度写入关 + 半透明：幽灵互相叠加处更亮/色深] → 接受，hologram 观感反而利于"这是虚影"；如需可降 alpha。
- [同连接内换维度/死亡：服务端 attachment 随实体重建清空，客户端静态态残留] → 预览态携带 `dimension`，客户端门控比对维度，异维即熄灭（D1/D4）；同维度内核心卸载 → 门控"核心仍在"判定熄灭，重新加载后自动复现，正确；重连后客户端态自然清空。

## Migration / Rollback

无数据迁移。回滚 = 还原 Java/资产：右键回到"单击即建"，材料区回到溢出（无持久状态残留）。

## Open Questions

（无——两段式规则、facing 依赖、滚动方案均已由用户拍板。）
