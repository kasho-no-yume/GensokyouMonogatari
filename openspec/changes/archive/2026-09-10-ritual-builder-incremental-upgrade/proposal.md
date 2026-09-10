# Proposal: ritual-builder-incremental-upgrade

## Why

v5 增量 pattern（`levels[].adds`）早已在数据/loader 侧确立"升级=纯增量"约定，但构建器代码停留在"所有图案只有 level 1"的时代：`RitualBuilderPlacement.topSlice()` 无视所选阶级恒取最高阶切片，导致 `kagutsuchi_flame_circle` 0~3 阶建出来轮廓完全相同（品阶 0 只是少放几块窄标签石）；同时成型核心右键被 `RitualCoreBlock` 无条件截获开 GUI，"对同仪式低阶建筑升级建造"的链路完全不存在。两个问题同一病根：阶级（level）概念从未进入构建器。

## What Changes

- **目标切片跟随所选阶级**：`topSlice()` → `sliceFor(pattern, tier)`（取 `level == tier` 的累积切片），搭建 / 预览三分类 / tooltip 与菜单材料表三个入口同时修正；找不到对应 level 的非法选择回发提示不搭建。
- **成型核心升级流（新能力）**：手持构建器右键成型核心不再开 GUI，由杖侧分发——同仪式且所选阶级更高 → 两段式预览只显增量白幽灵、第二击只放增量格（不拆不换旧方块）；未选仪式 / 所选为他仪式 / 所选阶级 ≤ 当前阶 → 对应聊天提示，均不搭建不开 GUI。空手右键成型核心开 GUI 行为不变。
- **BREAKING（对既有 spec 措辞）**：`ritual-builder-placement` "SHALL NOT 改动 RitualCoreBlock" 约束放宽——`RitualCoreBlock.useItemOn`/`openOrHint` 成型分支需识别手持构建器并让位给物品链路。
- **菜单品阶按钮语义升级**：按钮 N 身兼双职 = 目标阶级 N + 标签格实例化品阶 N（渲染来源仍为 `pattern.tiers()`，UI 布局不变）。
- **数据文件零改动**：`kagutsuchi_flame_circle.json` 四级增量是合格的，bug 全部在代码侧。

## Capabilities

### New Capabilities

- `ritual-builder-upgrade`: 成型核心上的阶级升级链路——右键分发（未选/他仪式/等级不足/可升级四分支）、`RitualCoreBlock` 对构建器的让位规则、增量搭建（只放缺失格、已满足格不拆不换）。

### Modified Capabilities

- `ritual-builder-placement`: 搭建/材料/分类的目标切片由"恒最高阶"改为"所选阶级对应切片"；右键触发范围从"未成型核心"扩展到"未成型或可升级的成型核心"。
- `ritual-builder-preview`: 两段式预览确认流的适用对象从"未成型核心"扩展到"成型核心的升级预览"（预览态本就比对 tier，增量幽灵由既有"已满足格不渲染"规则自然成立）。
- `ritual-builder-menu`: "品阶选择"要求补充语义——品阶按钮同时是目标阶级选择，材料区按所选阶级累积口径展示。

## Impact

- `src/main/java/com/bitsson/gensokyou/ritual/RitualBuilderPlacement.java`：`topSlice` → `sliceFor`，`build`/`classify`/`requirements` 三入口。
- `src/main/java/com/bitsson/gensokyou/item/RitualBuilderItem.java`：`handleBuild` 成型分发 + 提示。
- `src/main/java/com/bitsson/gensokyou/block/RitualCoreBlock.java`：`useItemOn`/`openOrHint` 成型时对手持构建器让位。
- `src/main/resources/assets/gensokyou/lang/{zh_cn,en_us}.json`：新增 3 条提示键。
- 客户端 `RitualPreviewRenderer` 共用 `classify`，预期零改动。
- 既有占位图案（仅 level 1、tiers 缺省 0..5）在按钮 0/2..5 下变为"非法选择提示"，属可接受退化（占位图案本就将随正式设计替换）。
