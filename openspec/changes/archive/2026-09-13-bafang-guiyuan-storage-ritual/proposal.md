# Proposal: bafang-guiyuan-storage-ritual

## Why

灵力体系至今没有正式的多方块存储设施：旧"电容器之仪式"只剩无 pattern 的行为尸体，成型过的 `bafang_guiyuan_circle`（八方归元之仪，pattern 已定稿：2~5 阶、祭品台 4/8/16/24 台）因无行为代码而一直按电容兜底运行。本变更落地"超级蓄电池组"仪式，并顺带清退占位时代的三具孤儿行为，把散落在扣费链上的"电容"概念改道到正式存储上。

## What Changes

- 新增行为 `bafang_guiyuan`（注册到既有 pattern `gensokyou:bafang_guiyuan_circle`）：**托管模型**——仪式灵力物理住在祭品台的灵力核心物品里，对外表现为 `stored = Σ核心已存 / capacity = Σ核心容量` 的聚合储灵池；核心 BE 的灵力四件套（getStored/getCapacity/receive/extract）开放按图案分派。
- **阶级门槛**：仅 `tier ≤ 仪式阶级` 的灵力核心被识别计入；更高阶核心放上台也不识别（占位不计数），信息栏提示未识别数。零核心可成型可启动，仅信息栏提示。
- **速率（方案 b）**：每核独立按自身输入输出速率限速、台位并行收支（×1000 定点进位，沿用加具土命 `fillCarry` 手法）；仪式对万象共鸣声明 `spiritIn/OutRatePerSecond = Σ 识别核心速率`，天然成为网络双向端点（超级蓄电池）。
- 仪式**不触碰玩家灵力池**：无直存取按钮、无 onUse 直连（明确否决旧电容交互范式）。
- 灵力扣费源改道：`SpiritPowerHelper.drainCapacitorsAround`（仪式启动 spCost 扣费、结界引爆扣费共 3 处调用）从"抽半径 3 内 capacitor 图案核心"泛化为"抽半径 3 内任意成型且有余灵的核心（不含中心自身）"——旧电容删除后扣费链不悬空，八方归元成为正式提现来源。
- **BREAKING** 删除孤儿行为 `GeneratorBehavior` / `CapacitorBehavior` / `TemperingBehavior` 及注册、patternId 常量、配置项（`CAPACITOR_CAPACITY` `CAPACITOR_TRANSFER_RATE` `GENERATOR_SP_PER_SECOND` `GENERATOR_PUSH_INTERVAL_TICKS` `TEMPER_*`）、相关中英文语言键；淬体成长玩法退役（`temperLevel` 附件字段留存兼容旧档，不再有任何增长途径）。
- **BREAKING** 删除死数据 `data/gensokyou/spirit_processing/` 四配方（缺 `pattern` 字段、result 用 `id` 而非 `item`，加载期即被拒收，从未生效）。
- 保留 `BarrierBreakBehavior`（幻想乡传送门，pattern 后补不在本变更范围），其扣费经由上述改道继续工作。
- 既有成型 bafang 建筑按电容兜底积累的 `storedSpiritPower` 余额作废（WIP 阶段，零迁移）。

## Capabilities

### New Capabilities

- `bafang-guiyuan-ritual`: 八方归元之仪——祭品台灵力核心托管聚合（容量/存量/速率）、阶级门槛、共鸣端点声明、信息栏展示与未识别提示。

### Modified Capabilities

- `ritual-core-interface`: 删除"电容存取按钮"场景；固定头灵力数值行对托管型仪式展示 Σ已存/Σ容量。
- `ritual-power-attributes`: 端点速率声明增加"由被托管物品逐项求和"的实例语义；保护性措辞"发电机推电容、配方就近抽电容"改为"配方就近抽储灵"。
- `player-spirit-attributes`: 移除"淬体仪式成功双增长"要求（淬体玩法退役）。
- `barrier-break-ritual`: 引爆扣费来源措辞由"相邻电容"改为"邻近有余灵的核心"。

## Impact

- `ritual/behavior/`：新增 `BafangGuiyuanBehavior`；删除 Generator/Capacitor/Tempering 三类。
- `ritual/RitualBehaviors`：注册表 +3 常量清理、+BAFANG_GUIYUAN。
- `block/entity/RitualCoreBlockEntity`：灵力四件套按图案分派（托管型路由到行为）；`getCapacity` 兜底从 `CAPACITOR_CAPACITY` 改为代码常量。
- `spirit/SpiritPowerHelper`：`capacitorsAround/drainCapacitorsAround/hasCapacitorAround` 泛化重命名与判定改写（BarrierBreak 与两处配方扣费调用点跟随）。
- `config/GensokyouConfig`：删 6 项配置。
- lang zh/en：删 capacitor/generator/tempering 相关键；加 `jei.gensokyou.ritual.bafang_guiyuan_circle`（八方归元之仪）与信息行键。
- 测试：gs_autotest harness 需扩一个储灵场景（见 tasks）；离线 pattern 校验不受影响（不改动 pattern）。
