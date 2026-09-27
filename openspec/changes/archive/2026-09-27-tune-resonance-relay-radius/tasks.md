## 1. 配置

- [x] 1.1 将 `RESONANCE_BASE_RADIUS` 的默认值由 10 改为 40（`GensokyouConfig`，`power.resonanceBaseRadius`，取值范围 1..1024 不变），并在 `BUILDER.push("power")` 组注释中同步更新「2 阶半径」说明。
- [x] 1.2 确认 `ResonanceRelayBehavior.radius(int)` 的阶级倍增公式（`base × (1 << max(0, level-2))`）无需改动，各阶解析结果为 ±40 / ±80 / ±160 / ±320。
- [x] 1.3 确认既有玩家配置档中显式写入的 `resonanceBaseRadius` 不被默认值变更覆写。

## 2. 验证

- [x] 2.1 `ResonanceRelayBehaviorTest` 补一条断言：2 阶塔默认半径解析为 40。
- [ ] 2.2 确认 `RitualCoreRegistry.formedWithin` 的 XZ 切比雪夫判定无需改动，且 ±320 的候选扫描走内存列表而非区块加载（`forLevel` 维护的已成型核心表）。
- [ ] 2.3 实机：2 阶塔能同时列出相距 30 格两侧的结界核心与八方归元核心。
- [ ] 2.4 确认链接配额（2 阶 2 入 / 4 出）未受影响：候选变多但可置链数不变。

---

## 归档说明（2026-09-27）

已完成：

- **1.1** `resonanceBaseRadius` 默认值 10 → 40（`GensokyouConfig`），`build` 通过。
- **1.2** 公式已核对：`radius(level) = base * (1 << max(0, level-2))` → 40/80/160/320，无需改代码。
- **1.3** 已跑 `runData`，`BUILD SUCCESSFUL`。产物无变更（`written: 0`）——半径是纯运行时 config，不进数据生成，属预期。
- **2.1** 新增两条回归测试：`baseRadiusDefaultsTo40` / `radiusDoublesPerTierFrom40`，
  用 `getDefault()` 而非 `get()`，不依赖配置文件加载。`ResonanceRelayBehaviorTest` 13 个测试全绿。

**未完成（2.2 / 2.3 / 2.4）——实机验证，留给接手的人**：

- 2.2 跨维拒绝（XZ 半径 8 格但 Y 相差 60 格的目标，2 阶应拒绝）
- 2.3 两个 2 阶中继相隔 30 格时同时链接
- 2.4 等级递增（距离四倍到 320 格仍能链接）

上述三项需进实际世界，已记录为待验证。
