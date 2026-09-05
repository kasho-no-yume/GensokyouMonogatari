# Tasks: ritual-builder-preview

前置依赖：`ritual-orientation-schema` 完成（`resolveState` 可用）。

## 1. 三分类共用抽取（服务端先行，行为不变）

- [x] 1.1 `RitualBuilderPlacement.classify(Level, anchor, pattern, tier)` 纯函数落地（pending/conflicts/airConflicts 三列表），`build` 改消费之；签名从 ServerLevel 放宽到 Level；`Result.conflicts` 取 conflicts ∪ airConflicts 并集（保持 AIR 占位格红框现状）
- [x] 1.2 回归：既有搭建/冲突/续搭行为逐位不变（创造模式实测召唤环 + 塞石头冲突）

## 2. 服务端两段式

- [x] 2.1 `ModAttachments` 注册 transient attachment `ritual_preview`（record patternId/tier/corePos/dimension，无 codec）
- [x] 2.2 `RitualBuilderItem.useOn`：预览态全等 → `doBuild`+清态+S2C 清除；否则置态+S2C 下发+action bar"再次右键确认建造"；无选择组件仍走现状提示
- [x] 2.3 S2C `RitualPreviewPayload`（Optional 态含 dimension，手写 StreamCodec，`RitualConflictPayload` 同范式）注册进 `ModNetworking`

## 3. 客户端态与门控

- [x] 3.1 `ClientRitualPreviewState` 静态持有 payload；payload handler 写入
- [x] 3.2 每帧渲染门控：态存在 ∧ 主/副手为 `ritual_builder` 物品 ∧ 核心仍在 ∧ 图案可解析 ∧ 预览 dimension 与当前维度一致（**不**校验组件选择，防零槽菜单组件不同步坑）

## 4. 半透明幽灵渲染

- [x] 4.1 冒烟：`RegisterShadersEvent` + `ritual_ghost.json/.fsh`（图集采样 × Tint uniform）最小一格渲染跑通，确认 shader 编译与混合状态正确（runClient 目检）
- [x] 4.2 `RenderType` 双实例：GHOST（白 tint）/GHOST_CONFLICT（红 tint），BLOCK 顶点格式 + 图集 + TRANSLUCENT + 深度测试开/写入关，`setStateSource` 喂 uniform
- [x] 4.3 `RitualPreviewRenderer`（AFTER_TRANSLUCENT_BLOCKS）：客户端 classify → pending 格 `renderSingleBlock(resolveState(...))` 白幽灵、conflict 格红幽灵、airConflict 格复用 `OVERLAY_LINES` 红框、已满足/锚点不画；两批 buffer 各自 endBuffer
- [x] 4.4 实时消长实测：预览中敲掉占位块 → 当帧转白幽灵；塞入方块 → 当帧转红

## 5. 材料列表滚动

- [x] 5.1 `renderScrollbar` 泛化为 `(x, top, h, total, visible, scroll)` 工具，左列改调用
- [x] 5.2 右列 `matScroll`：视口高 = 面板底-MAT_Y，滚轮按 x 区域分流（左列/材料区），溢出画滚动条
- [x] 5.3 材料行悬浮/命中判定接入 scroll 偏移；临时造一个 >7 种材料的测试图案验证后移除

## 6. 验证与收尾

- [x] 6.1 全链路 runClient：首击预览 → 换仪式再击替换 → 同选择二击建造 → 成型核心右键仍开 UI（行为分发无扰动）
- [x] 6.2 专用服务端 `runServer` 启动无新告警；`gradlew build` 全绿
- [x] 6.3 lang 键补齐（确认建造提示等）；如 shader 注册有实测坑沉淀进 `neoforge-1211-dev` skill
