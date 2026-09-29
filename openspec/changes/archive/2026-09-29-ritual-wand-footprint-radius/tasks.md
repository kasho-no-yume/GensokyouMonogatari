## 1. 几何查询

- [x] 1.1 `RitualPattern.LevelSlice` 新增 `minY()`（最低格位 Y 偏移，≤0，空切片 0）
- [x] 1.2 `RitualPattern.LevelSlice` 新增 `maxChebRadius()`（`max(|x|,|z|)`，正方形半宽）
- [x] 1.3 新增 `RitualPlacementMetricsTest`：6 项，含用真实图案 JSON 核 `zaohua`(minY −7 / r20)、`resonance_relay`(−1 / r8 / maxY 45)、`sair_energy`(0 / r1)，防 loader 四重展开与累加语义脱节

## 2. 界面展示

- [x] 2.1 `RitualBuilderScreen` 左列列表下方新增两行（`PLACE_Y = 164 + 4`），取 `sliceFor(pattern, 最高可建阶)`
- [x] 2.2 最高可建阶 = `visibleTiers(pattern)` 的最大值（已含 `maxTier` 进度夹取）；无有效阶时跳过，不渲染
- [x] 2.3 行1 `建议离地高度 |minY|`、行2 `最大占地半径 ±Chebyshev`（design D4 文案）
- [x] 2.4 两行共用一个 tooltip 命中区，渲染 `placement_tip`（5 个参数：height, height, radius, tier, radius）
- [x] 2.5 补齐 `zh_cn` / `en_us` 三个键：`builder.core_height` / `builder.footprint` / `builder.placement_tip`
- [x] 2.6 数值每帧从 `RitualPatternLoader.all()` 现取、不缓存（数据包热重载自动跟随）
- [x] 2.7 补 `ResourceLocation` import；`render()` 中在 `hoveredMaterial` 之后叠加 `hoveredPlacement` tooltip

## 3. 排版与实机确认

- [x] 3.1 宽度核算：行1 `建议离地高度 7` ≈66px、行2 `最大占地半径 ±20` ≈78px，左列可用 116px
- [x] 3.2 **实机**确认两行位置与不溢出 —— `已由用户实机验证`
- [x] 3.3 **实机**确认落在列表滚轮命中区（y 24~164）之外、不与右列材料区（x≥132）重叠 —— `已由用户实机验证`
- [x] 3.4 **实机**确认 tooltip 换行与可读性 —— `已由用户实机验证`
- [x] 3.5 文案实感 —— `已由用户实机验证`；若日后觉得 `建议离地高度` 措辞不佳，备选 `核心高度 ≥%s` / `离地 %s 格`（design Q3）

## 4. 验证与收尾

- [x] 4.1 `.\tools\gradle_task.ps1 build` 通过（583 项测试，0 失败）
- [x] 4.2 **实机**：选中 `zaohua` 显示 `建议离地高度 7` / `最大占地半径 ±20`；`sair_energy` 显示 `0` / `±1`；切换品阶数字不变 —— `已由用户实机验证`
- [x] 4.3 确认未改动 `ritual-builder-placement` 的任何逻辑（本变更只加 `RitualPattern` 查询与 `RitualBuilderScreen` 渲染）
- [x] 4.4 确认未引入 `InfoLine` 通道、未加网络包、未加 `RitualBehavior` 钩子（全部数据已在客户端）
