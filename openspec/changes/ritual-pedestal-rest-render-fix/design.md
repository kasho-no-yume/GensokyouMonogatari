# Ritual Pedestal Rest Render Fix — Design

## Context

- 两态姿态（静置平躺 / 激活立起悬浮自转）由 a5dbb3b（9/14）落地，实机回归：**未激活台面物品躺在约高半格处，且无自转**。无自转 ⇒ 过渡进度 eased≈0 ⇒ 抬升不来自激活态残留，锅在静置绘制路径本身。
- 静置渲染链：`RitualPedestalRenderer` translate(0.5, REST_Y=1.01(+BlockItem 时 +BLOCK_HALF_HEIGHT=0.275), 0.5) → YP(0) → XP(90°) → scale(0.55) → `ItemRenderer.renderStatic(FIXED)`。`renderStatic` 内部还会叠加物品模型的 `fixed` display 变换（已验证 sources jar + 原版 client jar：`item/generated.json` 的 fixed 为 rotation [0,180,0]、无平移）。
- 嫌疑排序：
  1. **BlockItem 枢轴失配**：方块烘焙模型顶点为角点系 [0,1]³，非中心系；`BLOCK_HALF_HEIGHT` 按"中心枢轴"抬高，叠加 XP(90°) 绕原点旋转后物整体在 Y/Z 均偏置，方块类供品（含灵力核心若为方块模型）呈现"躺在半格高"。
  2. **测试物品自带 fixed 平移**：某些自定义 3D 物品模型 display.fixed 含 translation（8 单位=0.5 格）直接抬升。
  3. REST_Y 基准与台面视觉不符（可能性低：常量未动、a5dbb3b 前同样表现）。
- 独立隐患：`ritualActive` 持久化进 NBT 且仅在 start/stop 广播，服务端异常退出会留下陈旧激活态（本次症状虽非它，但属同族正确性问题，顺带修）。

## Goals / Non-Goals

**Goals:**
- 静置态物品底缘贴台面（2D 贴图、方块类、自定义 3D 模型三类物品一致），修复悬浮半格。
- 激活性态成为纯渲染态：不持久化、核心为单一事实源，杜绝跨重启陈旧悬浮。
- 两态视觉设计与过渡逻辑保持不变。

**Non-Goals:**
- 仪式特效改造（`ritual-fx-overhaul`）。
- 物品模型资产重制——不为了渲染器而改模型；渲染器兼容现状模型。

## Decisions

### D1 探针先行，公式修正按矩阵定案
实现第一步为 3×2 实机探针矩阵（物品类别 × 激活态）+ 临时调试 overlay 打印 eased 值，锁定嫌疑 1/2 归属。两预案已备好：
- 命中嫌疑 1：静置路径改为**旋转后补偿**——对 BlockItem 放弃 `BLOCK_HALF_HEIGHT` 抬升，改为在 XP(90°)+scale 之后 `translate(-0.5, +0.5, 0)`（角点系→中心贴面归一）；2D/3D 物品保持现公式。
- 命中嫌疑 2：渲染前查询模型 `getTransforms().getTransform(FIXED).translation`，渲染器坐标系内**自减该平移**（通用防御，一次性写对，不依赖逐个模型改资产）。
- 两嫌疑可能并存在不同物品上 → 两修复合一实施（先归一 fixed 平移，再统一底缘贴面）。

### D2 `ritualActive` 去持久化
BE 不再读写 `TAG_RITUAL_ACTIVE`；核心在 `setEnabled`、`stop`、**结构重扫/加载重新成型**三处向台位广播当前态。理由：激活态唯一消费者是渲染器，持久化只会带来陈旧态；广播点均为低频（启停/重扫），无包频风险。旧档多余键静默忽略，零迁移。
- 备选：加载时台子反向询问核心——否决：台子不持有核心引用，反向查找引出新耦合。

### D3 客户端 ANIM 缓存语义不变
`advanceBlend` 的 WeakHashMap 与 float 时间戳保持（症状与它无关），仅确认新加载路径下初值取 `isRituallyActive()` 现状（已如此）。

## Risks / Trade-offs

- [探针需要用户实机配合，纯静态无法终局定位] → 探针步骤设计为一次进服 2 分钟可完成；调试 overlay 用临时 commit 不进主干。
- [fixed 平移自减对"故意用 fixed 平移做姿态"的自定义模型误伤] → 本 mod 与原版资产核查无此类用法；防御性写清注释。
- [去持久化后台子在核心加载前渲染为静置态] → 可接受（渲染态语义）；核心重扫在同一 tick 补广播。

## Migration Plan

单 commit 落地；回滚 revert。无存档/配置迁移。

## Open Questions

- 静置态是否需要**保留**极小离隙（1.01 的 0.01 是防 z-fighting）——修复后仍保留 ≥0.01 抬升，不算悬浮。
