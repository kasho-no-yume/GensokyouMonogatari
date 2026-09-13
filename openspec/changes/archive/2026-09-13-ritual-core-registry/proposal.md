# Proposal: ritual-core-registry

## Why

万象共鸣之仪（后继 change）需要在"以核心为中心、半径最高 80 格、高度不限"的范围内枚举其他仪式核心，但现有发现手段只有 `SpiritPowerHelper.capacitorsAround` 式的 `betweenClosed` 逐格扫 BE——半径 3 尚可，半径 80 × 全高 ≈ 千万格/次，路由 tick 与 GUI 候选列表都不可行。需要一个"已成型仪式核心"的运行时索引，把范围查询从体积扫描变成按核心数遍历。

## What Changes

- 新增 per-`ServerLevel` 的仪式核心注册表：核心结构匹配确立时注册（记坐标、patternId、阶级），失效/移除时注销；同坐标仪式变化时随重扫自然更新。
- 注册表为纯运行时结构：不持久化、不进存档。世界加载后各核心首个 tick 即重扫匹配并自注册，索引自然重建。
- 提供查询 API：按维度内坐标 + XZ 切比雪夫半径枚举已成型核心（高度不限），返回核心引用及其 patternId/阶级。
- 不迁移现有 `SpiritPowerHelper` 的小半径扫描（半径 3 无性能问题），注册表只服务大范围查询方。

## Capabilities

### New Capabilities

- `ritual-core-registry`: 已成型仪式核心的运行时索引——注册/注销时机、一致性要求（索引与实际匹配态收敛）、范围查询语义（维度内、XZ 方形半径、高度不限）。

### Modified Capabilities

（无——注册/注销挂在既有重扫与移除路径上，不改变 ritual-lifecycle 的任何既有需求。）

## Impact

- **代码**：新增 `ritual/RitualCoreRegistry`（挂载于 `ServerLevel`）；`RitualCoreBlockEntity` 重扫成/败分支与移除/加载路径接入注册/注销。
- **下游**：`resonance-relay-routing`（后继 change）的候选发现与路由 tick 依赖本索引；JEI/构建器等不受影响。
- **性能**：注册表遍历对象是"已成型核心"（世界内通常几十至几百个），范围查询 O(核心数)；注册/注销频率 ≤ 每核心每 20t 一次的状态翻转。
