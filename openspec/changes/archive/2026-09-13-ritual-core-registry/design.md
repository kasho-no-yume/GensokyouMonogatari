# Design: ritual-core-registry

## Context

全项目对"附近的仪式核心"的发现只有一种手段：`BlockPos.betweenClosed` 逐格 `getBlockEntity`（`SpiritPowerHelper.capacitorsAround`，半径 3）。万象共鸣之仪需要在 XZ 半径最高 80、高度不限的范围内枚举已成型核心，且候选发现同时服务于每 tick 路由与 GUI 打开，体积扫描在该量级不可行。`RitualCoreBlockEntity.serverTick` 已有每 20t 全量重扫（`ageTicks % 20 == 1`），成/败在 BE 内是显式状态翻转——注册表天然有廉价、权威的事件源。

## Goals / Non-Goals

**Goals:**
- 范围查询复杂度从 O(体积) 降到 O(已成型核心数)，结果与实际匹配态以 ≤20t 收敛。
- 零持久化：不进存档、无迁移负担，世界/区块重载后自愈。
- API 同时满足：候选枚举（带 patternId/阶级过滤）、路由 tick 解析核心 BE。

**Non-Goals:**
- 不替换 `SpiritPowerHelper` 半径 3 的既有扫描（无性能问题，改动无收益）。
- 不做跨维度查询、不做核心"即将成型"的预测索引。
- 不索引非核心的结构组件（祭品台等）。

## Decisions

### D1 宿主与生命周期：静态 Map<ServerLevel, RitualCoreRegistry> + 卸载清理

- 注册表按 `ServerLevel` 实例键控，存于 `RitualCoreRegistry` 内部 `ConcurrentHashMap`（Level 弱引用包装可选）；监听 NeoForge `ServerLevelEvent.Unload`（或 `LevelEvent.Unload` 服务端分支）移除对应实例。
- 备选：`AttachmentType`（level data attachment）——更"官方"，但需要注册序列化豁免与事件接线，收益仅是省一个静态 Map；本索引纯运行时、单一生读者可枚举，静态宿主足够且最直读。

### D2 条目形态：`Map<BlockPos, Entry(pos, patternId, level)>`，查询时解析 BE 并惰性剔除

- 不持有 `RitualCoreBlockEntity` 引用：区块卸载会使 BE 失效，持引用会读到死对象。查询 API 以坐标解析 `level.getBlockEntity(pos)`，校验"仍是成型核心且 patternId 一致"后返回实例；校验失败的条目顺手从索引剔除（lazy eviction）。
- 代价：条目最长可陈旧 20t——由 D3 的事件源保证翻转即时反映，20t 内新成型的核心最迟下一扫描周期入册。消费方（共鸣路由）本就按 20t/秒级窗口设计，可接受。

### D3 接入点：全部在 RitualCoreBlockEntity 既有路径上

- **注册/更新**：`serverTick` 重扫分支——`activeMatch != null` 时 `register(pos, patternId, level)`（幂等 put，值未变则等价覆盖）；同坐标仪式变了自然更新。
- **注销（失效）**：重扫后 `activeMatch == null` 且 `previous != null` 的既有分支（当前已在此做 `invalidateCapabilities` + `onStructureLost`）追加 `unregister(pos)`。
- **注销（BE 移除/区块卸载）**：覆写 `setRemoved()`（或 `onRemove`）注销——仅当条目的 patternId 与自己当前 match 一致时才删，防止"拆旧建新"竞态误删新条目。
- **首刻自愈**：新放置/区块重新加载的核心第一 tick 即重扫（`activeMatch == null` 触发条件已保证），自动入册，无需额外钩子。

### D4 查询 API 形状

```
RitualCoreRegistry.get(ServerLevel) -> 实例
register/unregister：D3 内部用，不对外强调用方
List<RitualCoreBlockEntity> formedWithin(ServerLevel, BlockPos center,
        int xzRadius, @Nullable ResourceLocation excludePatternId)
```

- 判据：`|x-cx| ≤ r && |z-cz| ≤ r`（切比雪夫方形，与需求"21×21 以核心为中心、高度不限"对齐），Y 不参与比较。
- `excludePatternId`（可空）：万象塔用它排除其他共鸣塔核心；不传则全量返回。"排除自己坐标"由调用方过滤（避免 API 塞特例参数）。
- 返回条目附带的 patternId/阶级经 `RitualCoreBlockEntity.activeMatch()` 现取现用，索引内字段仅作候选过滤提示。

## Risks / Trade-offs

- [静态 Map 泄漏维度实例] → Unload 事件强制清理；条目本身只存坐标+两个小值，即便事件漏接，量级也可忽略。
- [同坐标快速"拆了又建"竞态：旧 BE 的 setRemoved 晚于新 BE 注册] → 注销带 patternId 一致性守卫（D3），旧 BE 只能删"仍是自己当年那条"的条目。
- [20t 收敛窗口内的假阴性（新塔刚成型即被查）] → 消费方（GUI 打开、路由 tick）都是周期性重读索引，一拍延迟不可感知；MUST NOT 依赖"同 tick 成型同 tick 可查"。
- [索引与真实世界发散（异常路径未注销）] → 查询侧惰性剔除（D2）保证任何一次遍历都会自我修正，发散不可累积。

## Migration Plan

纯新增 + 三处既有路径挂钩，无存档格式变化，无数据迁移；回滚即移除钩子与类。

## Open Questions

- 无（API 消费方的语义——速率、环流、GUI——全部归后继 change `resonance-relay-routing`）。
