# Tasks: ritual-core-registry

## 1. 注册表本体

- [x] 1.1 新增 `ritual/RitualCoreRegistry`：静态 `Map<ServerLevel, 实例>` 宿主（`get(ServerLevel)`），条目 `Entry(pos, patternId, level)`，`register/unregister` 内部方法；监听 `LevelEvent.Unload` 服务端分支清理实例
- [x] 1.2 实现 `formedWithin(ServerLevel, BlockPos center, int xzRadius, @Nullable ResourceLocation excludePatternId)`：XZ 切比雪夫过滤、Y 不限；逐条现场解析 BE + 校验成型与条目一致性，失败即剔除

## 2. 核心 BE 接入

- [x] 2.1 `RitualCoreBlockEntity.serverTick` 重扫分支：成型时 `register`（幂等覆盖），`activeMatch` 由有到无的既有失效分支追加 `unregister`
- [x] 2.2 覆写 `setRemoved`（或等效移除钩子）：注销带 patternId 归属守卫（仅删"仍是自己那条"）

## 3. 验证

- [x] 3.1 单元测试：查询几何（方形半径、Y 无关、排除参数）与惰性剔除逻辑（可脱离世界的最小 fake 条目层，或仿 `RitualPatternValidatorParityTest` 的既有测试基建风格）
- [ ] 2 处竞态场景（拆旧建新、区块重载自愈）进游戏随 `resonance-relay-routing` 的实机验证一并覆盖
