## 1. 注册与数据基础

- [x] 1.1 `ModDataComponents` 新增 `RITUAL_BUILDER_SELECTION`（`record BuilderSelection(ResourceLocation patternId, int tier)` + Codec）
- [x] 1.2 `ModItems` 注册 `ritual_builder`（新 `RitualBuilderItem`，进 `gensokyou` 创造标签，暂不配配方）
- [x] 1.3 `GensokyouConfig` 新增 `ritualBuilderConflictOutlineSeconds`（默认 6）
- [x] 1.4 gen-textures 管道新增 `ritual_builder.png` + item 模型 + lang 键

## 2. 搭建核心算法

- [x] 2.1 新建 `RitualBuilderPlacement`（服务端静态）：`byId` 取图案、缺失宽限提示
- [x] 2.2 取最高 level 的 `LevelSlice`，旋转固定 0 遍历 `BlockEntry`
- [x] 2.3 冲突预检：IGNORE 跳过 / 谓词已满足跳过 / 被占不满足记入冲突表；冲突非空则全量中止
- [x] 2.4 标签谓词按所选品阶实例化（`tierOf == tier`），EXACT 用固定 block
- [x] 2.5 规范序尽力放置：`removeItem` 扣 1 + `setBlockAndUpdate`，缺料跳过；创造 `hasInfiniteMaterials` 免耗
- [x] 2.6 放置音效 + "已放置 N/M 格"提示

## 3. 物品交互入口

- [x] 3.1 `RitualBuilderItem.useOn`：潜行 → 服务端 `openMenu`；非潜行且目标 `ritual_core` → 调 `RitualBuilderPlacement`；其余 PASS
- [x] 3.2 `RitualBuilderItem.use`：潜行对空开菜单；非潜行对空 action bar 提示
- [x] 3.3 未选择组件时右键核心 → 提示先选择，不搭建
- [x] 3.4 核对交互链：`doesSneakBypassUse` 默认 false → 潜行右键天然走物品 `useOn`（绕开所有 behavior），非潜行裸核心走 `useItemOn` PASS 让位；无需改 behavior

## 4. 网络包

- [x] 4.1 C2S `RitualSelectPayload(patternId, tier)` + StreamCodec
- [x] 4.2 S2C `RitualConflictPayload(List<BlockPos>)` + StreamCodec
- [x] 4.3 `ModNetworking` 注册两包处理器：C2S 校验图案存在后写回手上组件；S2C 转交客户端冲突渲染器
- [x] 4.4 服务端搭建中止时发送冲突 payload

## 5. 选择菜单

- [x] 5.1 `RitualBuilderMenu`（零槽）+ `ModMenus` 注册
- [x] 5.2 `RitualBuilderScreen`：左列图案滚动列表（`C` 键代表物品为图标）+ 溢出滚动条
- [x] 5.3 品阶 0-5 按钮，`TierPalette` 高亮当前项
- [x] 5.4 材料区：按解析方块聚合"需求 ×N / 持有 ×M"，持有读客户端背包，不足红色
- [x] 5.5 点击图案/品阶 → 客户端乐观更新 + 发 `RitualSelectPayload`（零槽菜单不同步手持 stack，故不能每帧回读组件）
- [x] 5.6 `RegisterMenuScreensEvent` 绑定 Screen

## 6. 冲突红框渲染

- [x] 6.1 客户端 `ClientRitualConflictState`：存 `Map<BlockPos, expireTick>`，新 payload 整体替换
- [x] 6.2 `RenderLevelStageEvent`(AFTER_PARTICLES) 用 `LevelRenderer.renderLineBox` 画红色框
- [x] 6.3 按 `ritualBuilderConflictOutlineSeconds` 到时清除

## 7. 验证

- [x] 7.1 `gradlew compileJava` 通过
- [x] 7.2 `runServer` 启动无 registry 错误
- [x] 7.3 客户端实测：材料齐全一次成型、半路缺料尽力搭、一格被占全停+红框、续搭、创造免耗、选择重进保留；菜单内点仪式/品阶即时刷新、仪式多时滚动条可滚、红框穿透遮挡且时长延长、手持构建器不再误报"结构不完整"
