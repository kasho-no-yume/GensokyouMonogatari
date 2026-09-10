# Tasks: ritual-builder-incremental-upgrade

## 1. 目标切片修正（病根）

- [x] 1.1 `RitualBuilderPlacement`：`topSlice(pattern)` → `sliceFor(pattern, tier)`，精确匹配 `LevelSlice.level() == tier`，无匹配返回 null；更新过时注释（删除"当前图案均只有 level 1"）
- [x] 1.2 `build` / `classify` / `requirements` 三入口改走 `sliceFor`；`build` 对 null 切片返回 `Result.empty()` 并由调用方提示（与图案失效路径区分，新增 `Result` 或返回 boolean 由 3.2 消费）
- [x] 1.3 确认 `RitualPreviewRenderer` 零改动仍正确（仅共用 `classify`）

## 2. 成型核心让位（RitualCoreBlock）

- [x] 2.1 `useItemOn` 非潜行分支：`match` 存在且 `stack` 为 `RitualBuilderItem` → 返回 `PASS_TO_DEFAULT_BLOCK_INTERACTION`；其余行为（空手开 GUI、其他物品、潜行 dispatchUse）不变
- [x] 2.2 复核 `useWithoutItem`（空手）路径未被误伤：空手右键成型核心仍开 GUI
- [x] 2.3 实机回归发现让位链被半路截胡：1.21.1 `ServerPlayerGameMode.useItemOn` 中主手 `PASS_TO_DEFAULT_BLOCK_INTERACTION` 会先补调 `useWithoutItem`（之后才轮到物品 `useOn`），`openOrHint` 成型分支把 GUI 抢回。修复：`useWithoutItem` 非潜行分支同样守卫 `holdsBuilder && match 成型 → PASS`（副手杖场景 hand=OFF_HAND 不触发补调，2.1 守卫已覆盖）

## 3. 杖侧四分支分发与提示

- [x] 3.1 `RitualBuilderItem.handleBuild`：右键前 `RitualMatcher.matchAt(corePos)`；成型时按 未选仪式 → `builder_select_first`、图案不符 → `builder_core_other_ritual`、N ≤ M → `builder_level_insufficient`（带当前阶 M）、目标 level 不存在 → `builder_level_missing` 分发；仅 N > M 落入既有两段式预览/建造状态机
- [x] 3.2 未成型路径行为回归：确认预览态 `matches`（pattern+tier+pos+dimension）无改动仍成立
- [x] 3.3 lang 新增 `msg.gensokyou.builder_core_other_ritual`、`builder_level_insufficient`（带阶数占位参数）、`builder_level_missing`，zh_cn 与 en_us 双文件补齐

## 4. 验证

- [x] 4.1 `.\gradlew.bat compileJava` 通过（客户端/服务端类均编译）
- [x] 4.2 实机核对单（用户执行）：裸核心按 0/1/2/3 逐阶建造，四阶轮廓肉眼可辨且逐级只增不改；0 阶建筑升 1 阶——预览仅显增量白幽灵、确认后旧 stone_0 原位不动；降阶/他仪式/未选三种右键给对应提示且不开 GUI；空手右键成型核心 GUI 正常；升级成形后核心界面阶级升至 N
