# Ritual Presentation Polish — Proposal

## Why

本 change 原本只覆盖祭品台静置悬浮回归（a5dbb3b 引入）。实机续测又暴露三处同族问题：祭品台姿态状态残留、造化飞行位置同步、迦具土燃烧特效布局。四者共性为**纯客户端表现层缺陷**，与仪式结算/配方无关，故合并为一个变更处理。

原始问题：
- **静置悬浮半格**：未激活祭品台物品躺在约高半格处且无自转 ⇒ 过渡进度 eased≈0 ⇒ 锅在静置绘制路径本身，嫌疑 `RitualPedestalRenderer` 的 `BLOCK_HALF_HEIGHT` 枢轴失配（FIXED 方块几何以角点为枢轴）或物品模型自带 `fixed` 平移。
- **陈旧激活态**：`ritualActive` 随 NBT 持久化，服务端异常退出/结构中途拆除会留下陈旧悬浮态。

新增问题：
1. **造化合成后台面物品暴旋一下才停**：FLIGHT 开始时台面被清空 → `render()` 在 `held.isEmpty()` 处提前返回，`ANIM` 进度冻结在激活值 ≈1；再放物品时 `advanceBlend` 以巨大 dt（clamp 4）在数帧内把进度从 1 拉到 0，同时自转角度用 `绝对 gameTime × eased` 计算，`eased` 每帧大变一帧可扫过几十圈。**状态未清理 + 自转公式错误**双重成因。
2. **飞行材料往外闪现一帧（被拉回）**：`ZaohuaFlightItem` 只在客户端 `setPos`，服务端实体位置恒在起点；1.21.1 `ServerEntity.sendChanges()` 每 60 tick 无条件补发一次位置包（`tickCount % 60 == 0`），而 `Entity.lerpTo` 是**直接 `setPos`（无插值）**，客户端物品被瞬移回起点一帧后由本地曲线拉回。
3. **迦具土火柱超出仪式范围且像复制体**：`RitualFxLayout.flamePillars` 把同心环铺到结构半径的 0.42/0.70/0.98 倍再 ×0.88~1.12 抖动（自述 45%~125%），且每座祭品台再堆 `3+阶级` 根相同柱体；目标是「整体像仪式被烈火炙烤」，不是一圈炎柱复制体。

## What Changes

- **祭品台静置姿态修复（原）**：校正静置高度/枢轴，保证三类物品（2D 贴图 / 方块类 / 自定义 3D 模型）底缘贴台、MUST NOT 悬浮。
- **激活性态去持久化（原）**：`ritualActive` 不写 NBT，由核心在启动/停止/结构重扫时广播，为唯一事实源。
- **姿态表现状态清零 + 自转相位化（新）**：渲染器在台面为空时清除该位姿状态；自转角度改为「随激活进度累积的相位」，任何新占位从静置态（进度 0、相位 0）起算，MUST NOT 继承前一件的进度或残余转速。
- **造化飞行位置客户端独占（新）**：`ZaohuaFlightItem` 覆写 `lerpTo` 忽略服务端位置包，飞行轨迹纯本地确定性解算（纯表现，不同步位置）。
- **迦具土燃烧特效重设计（新）**：弃用离散炎柱布局，改为结构水平半径内收束的**贴地火床 + 低伏火舌（约 1~2 格）+ 地面脉动辉光**；火舌密度/尺寸/滚动随阶级增强；非燃烧态零呈现、启停有包络。
- **不变**：既有两态视觉设计（平躺 ↔ 立起悬浮过渡、光照采样台面上方一格）与过渡逻辑（自转相位化除外）。

## Capabilities

### Modified Capabilities

- `ritual-pedestal`：补充静置底缘贴台不变量、激活态不持久化；新增「姿态表现状态随占位清零、自转由相位累积」。
- `zaohua-crafting`：飞行演出位置以客户端为唯一来源，服务端周期位置包 MUST NOT 影响表现。
- `kagutsuchi-flame-ritual`：「火焰粒子表现」由炎柱场改为贴地烈火场（收束于结构水平半径内、无复制柱体）。
- `ritual-runtime-fx`：特效参数配置项与贴图资产清单随火焰场改造更新。

## Impact

- **Java**：
  - `client/renderer/RitualPedestalRenderer`（状态清零 + 自转相位）
  - `block/entity/RitualPedestalBlockEntity`（去 NBT 持久化字段）
  - `block/entity/RitualCoreBlockEntity`（加载/重扫补广播）
  - `entity/ZaohuaFlightItem`（`lerpTo` 空操作）
  - `client/renderer/RitualCoreRenderer.renderFlame`（火毯几何）
  - `ritual/RitualFxLayout`（火焰场布局替换）
  - `config/GensokyouConfig`（`fxFlame*` → `fxFire*`）
- **资产**：新增火焰场贴图（gen_tex 管道）；`flame_column.png` 视复用情况改名/停用。
- **测试**：`RitualFxLayoutTest` 重写（确定性 / 收束在半径内 / 无复制柱体 / 阶级增强）。
- **存档**：`RitualActive` 标签移除，旧档字段静默忽略，无迁移。
- **不影响**：仪式逻辑与结算、台面物品持有语义、造化 / 迦具土的产出与配方、共鸣塔与八方归元特效。
