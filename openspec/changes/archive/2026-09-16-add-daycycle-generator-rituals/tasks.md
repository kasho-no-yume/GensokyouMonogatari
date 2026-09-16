# Tasks: add-daycycle-generator-rituals

## 1. 前置确认与配置

- [x] 1.1 开工前与用户确认 OQ1（"核优先"导致插入有头寸核时路由拿不到货，是否接受）与 OQ2（停机后缓存是否仍回流核）；据答复锁定 D4 分流次序（OQ1=接受字面核优先；OQ2=停机也回流，走无门控 serverPassiveTick）
- [x] 1.2 `GensokyouConfig`（COMMON）新增键：`nichirinBaseRatePerSecond`=5.0、`tsukikageBaseRatePerSecond`=5.0、`nichirinBaseCapacity`=10000、`tsukikageBaseCapacity`=10000、`nichirinOutRatePerSecond`=10000、`tsukikageOutRatePerSecond`=10000、`tsukikageMoonPhaseScaling`=false（预留）
- [x] 1.3 复核并注释 `LevelAccessor.dayTime()` / `DimensionType.fixedTime()` 取时口径（已核验，落代码注释防后人踩 `getTimeOfDay` 平滑曲线）

## 2. 共享内核与行为

- [x] 2.1 实现世界无关纯静态内核：`fraction(dayTime, solar)` 三角时间线 + `producedPerSecond(level, tier, baseRate, solar)`（`Math.round`，含 `fixedTime().orElse(dayTime)` 与 `floorMod(...,24000)`），单测/调试可直调（`DayCycleGeneratorBehavior`）
- [x] 2.2 新建 `ritual/behavior/NichirinBehavior.java` 与 `TsukikageBehavior.java`（共用 2.1 内核，`solar=true/false`）：`serverTick` 每秒结算（enabled 门控）执行 D4 分流（核优先不限速 → 溢出入缓存 → 作废）；`serverPassiveTick`（无门控）`tickBatteryAutoFill()` 回流；覆写 `spiritOutRatePerSecond`（config 定值）、`uiInfo`；`onStructureLost` 预置
- [x] 2.3 `RitualBehaviors` 加 `NICHIRIN`/`TSUKIKAGE` 常量并注册

## 3. 框架触点

- [x] 3.1 `RitualCoreBlockEntity.getCapacity()` 分派新增两分支，走 `daycycleCapacity(level, base) = base × 4^L`（仿 `kagutsuchiCapacity`）
- [x] 3.2 复核 `usesCoreSocket()` 保持默认 true（槽=产出首站）、两 pattern 已 `toggleable:true`；确认 `serverTick` 通道与启停按钮联通

## 4. UI 与文案

- [x] 4.1 覆写 `uiInfo`：一行状态（时段键 + 当前产灵 x/s，`InfoLine.compact` + tip 明细）＋固定由来诗 `lore_1..5`；不渲染通用清单/额外数值行
- [x] 4.2 zh_cn/en_us 补 `gui.gensokyou.ritual.daycycle.*` 与 `.nichirin/.tsukikage.lore_*`；`python tools/lang_audit.py` 零缺失（exit 0）

## 5. 调试与验证

- [x] 5.1 `DebugCommands` 增 `/gs_debug nichirin|tsukikage [at <dayTime>] <corePos>`：回显时刻/比例/实际产灵/峰值/缓存/槽核/enabled
- [x] 5.2 `gradlew compileJava` BUILD SUCCESSFUL；`validate_ritual_pattern.py` 全部通过（未改 pattern）
- [x] 5.3 `runServer` 日志断言：`Done (1.395s)!`、无 `Errors in registry`（后续 Forgematica 专用服务端 mixin 崩溃属环境噪声，与本变更无关）
- [x] 5.4 实机验收（用户执行）：白天日轮产灵、夜间停；月影相反；插核先注核、拔核缓存涨；路由可抽取（缓存有货时）；并用 `/gs_debug nichirin|tsukikage [at <dayTime>] <core>` 验证四特征时刻/半值取整/四分流/停机停产

## 6. 文档

- [x] 6.1 新建 `.opencode/skills/ritual-code-dev/SKILL.md`（仪式代码开发速查：三件套、钩子表、灵力四件套与端点、新增仪式 checklist、红线、调试与验证、参考实现地图）
- [x] 6.2 （可选）在 `.opencode/skills/ritual-design/SKILL.md` 加一行"行为代码见 ritual-code-dev"交叉引用
