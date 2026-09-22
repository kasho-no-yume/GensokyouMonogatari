# Implementation Tasks: add-balance-test-harness

> 状态：命令、标准数值/无限灵力、测试 BOSS 实体与十模式编排、配置与语言、单测均已落地
> （`gradlew compileJava` 与 `test` 通过）。实机冒烟与 TTK 观测为人工项（见 4.3/4.4）。

## 1. Config 与注册

- [x] 1.1 新增 `testHarness` config 段（min/max 秒数与命中数、模式间隔、三阶段阈值/间隔/弹速、弹速、十模式伤害系数）
- [x] 1.2 注册实体 `balance_test_boss` + 占位渲染（复用妖精 geo/动画/贴图，`TestBossGeoModel`/`TestBossGeoRenderer`，放大以区分）
- [x] 1.3 `docs/asset-placeholder-list.md` 登记（复用妖精资产，无新增贴图）

## 2. 测试命令

- [x] 2.1 `command/BalanceTestCommands` 注册 `/gs_test`（权限 2）：`player` / `boss` / `clear` / `reset`
- [x] 2.2 `player <tier>`：清 `grace_tier_N` → 按 base 中点应用 1..N 阶（非池键 sourceId=`test_standard`；池两键经台账覆盖）→ 阶级/池满/刷新属性桥
- [x] 2.3 `player <tier> infinite`：测试无限灵力（transient attachment + tick 补满），`reset` 关闭
- [x] 2.4 标准装备发放：按武器带发 主武器 + 等级核 + 球核 + 增幅核（`RuneGenerator.ensureGenerated`）
- [x] 2.5 `boss <tier> <min|max>` 标定生成；`clear` 半径 160 移除；`reset` 回凡人
- [x] 2.6 lang 键（zh_cn/en_us）：配置/重置/生成/清理/阶段播报

## 3. 测试 BOSS 实体与 AI

- [x] 3.1 `entity/BalanceTestBossEntity`：继承妖精飞行基座（悬停/飞行同小妖精）、实现 `TouhouMonster` 非弹幕减免
- [x] 3.2 NBT：test tier / variant / 标定 HP 与弹伤；服务端权威
- [x] 3.3 `entity/goal/TestBossPatternGoal`：每 `patternIntervalTicks` 随机选模式（避免连续重复）+ 三阶段（模式池/间隔/弹速）
- [x] 3.4 十种模式（`entity/TestBossPattern`，复用球/飞刀/灵符/激光 + 方向计算）
- [x] 3.5 弹幕走 `gensokyou:danmaku`（实体 hurt 通道）；owner 免疫（白名单空集）

## 4. 验证

- [x] 4.1 单测：`TestBossTuning` 标定（min TTK ≥ 2 分钟、max > min、hits 越少越痛）
- [x] 4.2 单测：十种模式基础几何（`horiz`/`rotY`、COUNT=10）
- [ ] 4.3 实机冒烟：`player` 各阶 → `boss` min/max → 观察 TTK 与阶段/模式覆盖 → `clear`/`reset`
- [ ] 4.4 实机：同阶标准装备对 min 变体 TTK ≥ 2 分钟

## 5. 文档

- [x] 5.1 新增 `docs/balance-test-harness.md`（命令用法、标准数值来源隔离、BOSS 标定、十模式与阶段、建议流程）
- [x] 5.2 `openspec/project.md` 登记测试台为开发设施

## 实现备注（偏差）

- **池两键的单写例外**：非池键走独立 `test_standard` 来源；但最大灵力/灵力强度受单写规约约束，
  只能经阶级台账写入，故命令会**覆盖 1..N 阶台账份额**（`reset` 清空）。这是现有属性架构下的必然取舍。
- **迟弹简化**：模式 7「缓速散华」实现为慢速大弹，未做"出生后逐步加速"（避免逐弹状态同步成本）。
- **测试 BOSS 渲染**：复用妖精 GeckoLib 模型/动画/贴图占位（`TestBossGeoModel`），仅放大以区分；未做专属美术。
- **模式伤害系数**：十模式单发伤害 = 标定弹伤 × config `testPatternDamageFactor`（密弹更低），避免密集图案秒杀。
