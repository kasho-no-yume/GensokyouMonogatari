## 1. 绑定数据模型与注册

- [x] 1.1 新增 `item/BuilderBind.java`：record `(ResourceLocation dimension, BlockPos pos)`，含 `CODEC`（`ResourceLocation.CODEC` + `BlockPos.CODEC`）与 `STREAM_CODEC`（`RegistryFriendlyByteBuf`）
- [x] 1.2 在 `registry/ModDataComponents.java` 注册 `RITUAL_BUILDER_BIND`（persistent + networkSynchronized），与 `RITUAL_BUILDER_SELECTION` 并列
- [x] 1.3 提供读侧工具（`RitualBuilderItem.bind(stack)` / `setBind` / `clearBind`），缺失组件视作未绑定

## 2. 绑定/解绑手势

- [x] 2.1 `block/RitualCoreBlock.java` 潜行分支：玩家主/副手持构建器时返回 `PASS_TO_DEFAULT_BLOCK_INTERACTION`（`useItemOn`）与 `PASS`（`useWithoutItem`），使物品 `useOn` 收到潜行手势（镜像既有 `holdsBuilder` 让位）
- [x] 2.2 `item/RitualBuilderItem.useOn` 潜行分支重写：点击块为仪式核心时——坐标为绑定坐标→解绑；否则成型且 `pattern == Wujinzang`→绑定；否则提示"非成型无尽藏"且不开菜单；非核心→照旧开菜单
- [x] 2.3 判定"成型且为无尽藏"复用 `RitualMatcher.matchAt` + `RitualBehaviors.WUJINZANG`；绑定写入 `stack` 的组件（`context.getItemInHand()`）
- [x] 2.4 新增行为提示 lang 键（绑定成功/解绑/非成型无尽藏），服务端 action bar 下发

## 3. 搭建扣料：背包优先 + 绑定仓储兜底

- [x] 3.1 `ritual/RitualBuilderPlacement.java`：新增可空绑定源入参（新重载），把 `consumeOne` 改为"背包优先 → 兜底"
- [x] 3.2 兜底实现 `BoundSupply.tryConsumeOne(ServerLevel, BuilderBind, Block)`：同维度→`level.getBlockEntity(pos)`→`activeMatch==WUJINZANG`→`isEnabled()`→`WujinzangStorage.extract(level, match, new ItemStack(block), 1)`
- [x] 3.3 校验闸门与 `wujinzang-storage-terminal` 的 `storageAvailable` 语义一致（不得复用终端菜单类）
- [x] 3.4 核实 `wujinzang_circle` 晶位分布是否使核心自身区块被 `WujinzangStorage.forceChunks` 覆盖；若否，在 `ensureCrystals` 把核心区块纳入强制加载集合并于 `onStructureLost`/`releaseForceLoads` 释放（已加 `forceCoreChunk`）
- [x] 3.5 `RitualBuilderItem.doBuild` 传入绑定源；`itemLess`、创造短路、`Result` 语义保持不变

## 4. 材料计数 S2C 同步

- [x] 4.1 新增 S2C payload `network/BoundSupplyCountsPayload`：`pos` + `status` + 相关方块 `Item→count` 列表；客户端处理写入缓存（按 `pos` 索引）
- [x] 4.2 服务端周期推送器 `ritual/BoundSupplySyncServer`：间隔取 `GensokyouConfig`（默认 20t）；对每个 `ServerPlayer` 收集背包内带绑定组件的构建器选择图案 + 当前预览图案，求 `relevant` 方块集合，从绑定仓储聚合中仅取相关子集，变化才下发；不可用时下发状态码
- [x] 4.3 客户端缓存 `client/ClientBoundSupplyState`（参照 `ClientRitualPreviewState` 静态暂存范式），并在登出时清理
- [x] 4.4 在 `network/ModNetworking.java` 注册 payload，`ClientPayloadHandler` 加处理器

## 5. 三处 UI 呈现并入仓储

- [x] 5.1 `item/RitualBuilderItem.appendHoverText`：新增绑定信息行（维度+坐标+状态）；"持有"改为背包 + 缓存仓储的合并单值
- [x] 5.2 `client/screen/RitualBuilderScreen` 材料区持有数改用背包 + 缓存仓储的合并值（经手构建器的绑定）
- [x] 5.3 `client/RitualPreviewMaterialHud`：缺口公式改为 `max(0, 需求 − (背包 + 缓存仓储))`，门控（缺口全 0 整块消失）保持

## 6. 文案与指导书

- [x] 6.1 `assets/gensokyou/lang/zh_cn.json`：绑定 tooltip 行、状态标注、绑定/解绑/非成型提示键
- [x] 6.2 更新 Patchouli `ritual_builder.json` 条目正文（新增 `p2` 页）；同步 `rituals_basics.builder` 中构建器用法描述，补绑定说明

## 7. 配置与调试

- [x] 7.1 `config/GensokyouConfig.java`（COMMON）新增 `BUILDER_BIND_SYNC_INTERVAL_TICKS`（tick）
- [x] 7.2 `ritual/command/DebugCommands.java` 增 `/gs_debug builderbind` 探针：单行输出绑定坐标、闸门状态与仓储聚合条目数/总量

## 8. 验证

- [x] 8.1 `.\tools\gradle_task.ps1 compileJava` 通过（禁裸跑 gradlew）
- [x] 8.2 `.\tools\gradle_task.ps1 runServer -TimeoutSec 150` 加载无 registry/异常（`Done (6.453s)`，无 ERROR）
- [x] 8.3 `python tools/lang_audit.py` 退出码 0
- [x] 8.4 `openspec validate add-builder-wujinzang-binding --strict` 通过
- [ ] 8.5 实机：绑定→搭建（背包不足从仓储扣）→解绑；未启动/异维度/破碎复原三条闸门各验一次（需人工实操）
