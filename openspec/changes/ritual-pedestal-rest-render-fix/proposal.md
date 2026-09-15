# Ritual Pedestal Rest Render Fix — Proposal

## Why

昨天（commit a5dbb3b，9/14）"静置平躺/激活立起悬浮"两态方案落地后，实机出现回归：**未激活祭品台上的物品躺在约高半格处**（无自转，说明过渡进度 eased≈0，锅在静置渲染本身）。嫌疑收敛于两处：① `RitualPedestalRenderer` 对方块类物品的 `BLOCK_HALF_HEIGHT` 校正在"先平移后旋转 90°"的姿态下失配（FIXED 上下文方块几何以角点为枢轴，抬升公式按中心写）；② 物品模型自带 `fixed` display 平移干扰。另外 `ritualActive` 现随 NBT 持久化，服务端异常退出/结构中途拆除可能留下陈旧激活态，属于同类隐患需一并加固。

## What Changes

- 定位并修复祭品台静置物品悬浮半格的渲染问题：以 2D 贴图物品、方块类物品、3D 自定义模型物品三类实机探针区分嫌疑路径，校正静置姿态下的高度/枢轴计算（`REST_Y`/`BLOCK_HALF_HEIGHT`/旋转顺序），保证"静置=平躺贴台面"。
- 激活性态改为**不持久化 + 单一事实源**：`ritualActive` 不再写入 NBT，由核心在启动/停止/结构扫描/加载时向台面广播，杜绝跨重启的陈旧悬浮。
- 不改变既有两态视觉设计（平躺↔立起悬浮过渡、光照采样台面上方一格）。

## Capabilities

### New Capabilities

<!-- 无 -->

### Modified Capabilities

- `ritual-pedestal`: "悬浮渲染"需求补充静置态高度不变量（底缘贴台、MUST NOT 悬浮），并明确激活态标志不持久化、以核心广播为唯一事实源。

## Impact

- **Java**：`client/renderer/RitualPedestalRenderer`（高度/枢轴校正）、`block/entity/RitualPedestalBlockEntity`（去 NBT 持久化字段）、`block/entity/RitualCoreBlockEntity`（加载/重扫时补广播）。
- **存档**：`RitualActive` 标签移除，旧档字段静默忽略，无迁移。
- **不影响**：仪式逻辑、台面物品持有语义、共鸣塔/迦具土特效（另见 `ritual-fx-overhaul`）。
