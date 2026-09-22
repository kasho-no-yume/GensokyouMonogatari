# Design: fix-danmaku-render-order-and-talisman-loss

## Context

弹幕渲染管线现状（`redesign-danmaku-visuals` 之后）：

```
AbstractDanmakuRenderer（基类）
├─ 本体层: baseRenderType()
│    ├─ KnifeDanmakuRenderer    → entityCutoutNoCull（写深度）   ← 未受影响
│    ├─ SphereDanmakuRenderer   → DanmakuRenderTypes.translucent  ← 不写深度（COLOR_WRITE）
│    └─ TalismanDanmakuRenderer → DanmakuRenderTypes.translucent  ← 不写深度（COLOR_WRITE）
└─ 发光层/亮核: glowRenderType() → additiveGlow（不写深度）
LaserDanmakuRenderer — 主体/端盖/预警线用 additiveSolid（写深度），外发光与法阵 additiveGlow（不写深度）
```

`LevelRenderer` 绘制顺序：**实体 → 半透明地形(水) → 粒子 → 云**。实体先画，但凡是**不写深度**的层（本体、以及所有 `additiveGlow` 发光层），后画的水/云深度测试都能通过、合成到弹幕之上，即被覆盖。

原版 `RenderType.entityCutoutNoCull` 走 `NO_TRANSPARENCY`（写掩码 `COLOR_DEPTH_WRITE`）；自建 `translucent` 显式设 `COLOR_WRITE`（`DanmakuRenderTypes.java:101`），注释写明"不写深度：重叠弹幕的柔边不会相互凿洞"。这就是顺序回归的根因——修法是恢复写深度，同源的先例是激光的 `additiveSolid`。

灵符追踪现状（`TalismanDanmaku.tickHoming`）：仅当 `target == null || !target.isAlive()` 时停止；否则每 tick 朝目标做角速度受限的转向（`rotateTowards`）。缺少"目标跑丢"的判据。

## Goals / Non-Goals

**Goals:**
- 球弹/札弹本体被水与云正确遮挡（本体写入深度缓冲）
- 札弹本体满亮度，与球弹/激光一致
- 灵符在"目标甩到身后"时永久停止追踪，之后直线飞行
- 判定双端一致、不需要高频网络同步
- 为后续友军/阵营能力预留字段

**Non-Goals:**
- 敌我辨识（视觉区分）——本次不做
- 友军火力（阵营白名单让友军不误伤）——本次不做，仅在弹幕基类预留 faction 字段
- 灵符目标 id 持久化（保持现状：重载后不追踪）
- 刀弹的写深度语义改动（其本体本就写深度，且无发光层）

## Decisions

### D1: 新增写深度的 alpha 混合类型 `translucentDepth`，本体层改用它

`DanmakuRenderTypes` 新增一个与 `translucent` 同源、唯二差异为**写掩码改 `COLOR_DEPTH_WRITE`** 与 **`sortOnUpload(true)`** 的类型。球弹/札弹本体层从 `translucent` 换到它。保持 `RENDERTYPE_ENTITY_TRANSLUCENT_SHADER` + `TRANSLUCENT_TRANSPARENCY` + `NO_CULL`，以保留柔和渐变边缘。

- **为何不用"深度预写 pass"**：顺序正确且无凿洞，但要额外的几何输出与状态切换，复杂度/开销都不划算；本项目已有 `additiveSolid` 的成功先例，改 RenderType 即可。
- **为何不改成 cutout**：cutout 丢弃 <0.1 alpha 像素，会把柔边切成锯齿，违背重设计的观感目标。
- **透明角落不会造成"方形深度洞"**：所用 shader（`rendertype_entity_translucent` / `rendertype_entity_translucent_emissive`）内含 `if (color.a < 0.1) discard;`，贴图 alpha→0 的边缘像素被丢弃、不写深度，只有可见轮廓参与深度竞争。

### D2: `sortOnUpload(true)`

写深度的半透明几何在同一批内必须按距离排序，否则重叠弹幕的前后关系会随插入顺序抖动。`additiveSolid` 已采用该设置，`translucentDepth` 对齐。

### D3: 札弹本体满亮度

`TalismanDanmakuRenderer` 本体当前传 `packedLight`（世界光照），球弹/激光传 `FULL_BRIGHT`。弹幕是自发光体，札弹进阴影变暗与统一柔光目标不符。改为 `FULL_BRIGHT`。

### D4: 灵符"追踪丢失"＝夹角阈值 + 双端本地判定 + 服务端清 id

每 tick 在 `tickHoming` 内计算 `angle(currentDir, toTarget)`。若 `angle > threshold`：停止本 tick 偏转，并标记丢失；此后恒直线飞行（不可逆）。

- **同步策略**：服务端在判定丢失时 `setTarget(null)`（`DATA_TARGET_ID → 0`，SynchedEntityData 自动同步给追踪客户端）；客户端同样本地计算同一条件，避免等待数据包期间的"多追一 tick"。两侧都是纯确定性计算，最多相差 1~2 tick，随后统一直线，残余位置误差由 `lerpTo` 的 1.0 格硬纠正自愈。
- **不可逆**：一旦丢失即清空目标，不会因条件抖动而反复横跳。
- 判定使用与现有 `aimPoint`（目标碰撞箱中心高度）一致的到目标向量，保证双端口径一致。

### D5: 阈值走配置

`GensokyouConfig` 的 `danmaku` 段新增 `talismanTargetLossAngleDeg`，默认 120（初版 150，实测偏难甩，调低），范围 [90, 180]。读取时转弧度比较。

### D6: 预留 faction 字段（无行为）

`AbstractDanmakuProjectile` 预留一个同步整型 `FACTION`（默认 0 = 中性）。本次不写入、不读取、不影响渲染与判伤；仅作为后续能力的挂载点。设计上区分"预留"与"实现"：字段存在但行为为零，后续 change 只需填充服务端赋值与客户端消费。

### D7: 发光层同样写深度（外发光/亮核/法阵统一走 `additiveSolid`）

本体写深度后，外发光（1.35×）比本体大的那圈光晕仍不写深度，依旧会被水/云覆盖——这正是"弹幕有个外发光仍被遮挡"的现象，激光的外发光/法阵同理（其主体已写深度，发光没有）。

因此把**加法发光层统一改用既有的 `additiveSolid`**（加法混合 + `COLOR_DEPTH_WRITE` + `sortOnUpload(true)` + emissive shader）：

- 球弹/札弹：基类 `glowRenderType()` 从 `additiveGlow` 改为 `additiveSolid`（外发光 + 亮核一并生效；亮核与本体同深，无副作用）。
- 激光：`renderActiveBeam` 的外发光、`renderMagicCircle` 的法阵从 `additiveGlow` 改为 `additiveSolid`（端盖本就是 `additiveSolid`）。
- `additiveGlow`（不写深度版本）保留，仍被水晶/仪式等其它渲染器使用，非死代码。

**代价（已接受）**：发光不再被覆盖的代价是它会在水面上"按可见轮廓占位"（把水挡住）。这几何上正确（发光在水前就该挡水），只是软光晕与水面交界处会有硬边界。若要完全避免该硬边界，需把弹幕整批挪到半透明地形之后重绘（`AFTER_TRANSLUCENT_BLOCKS`），属更大改动，本次不做。

## Risks / Trade-offs

- [写深度后，重叠弹幕的透明柔边互相凿洞（前面弹的透明像素挡住后面弹）] → shader 已 discard alpha<0.1，仅可见轮廓写深度；主体 alpha 保持高值（≥235）、`sortOnUpload(true)` 进一步缓解
- [发光写深度后，光晕在水面上按可见轮廓"占位"、与水面交界有硬边界] → 已接受（几何正确的代价）；若观感不可接受，改为弹幕整批延后到半透明地形之后重绘（另立 change）
- [发光/本体都写深度，密集重叠时的深度竞争可能造成轻微穿插] → 各写深度层均 `sortOnUpload(true)`；弹幕体积小，实测可接受
- [双端本地丢失判定在阈值附近相差 1~2 tick] → 单向不可逆 + `lerpTo` 硬纠正，不产生持续发散
- [服务端清 target id 的数据包延迟导致客户端多追踪数 tick] → 客户端本地同时判定，窗口内也停止
- [预留 faction 字段成为死代码] → 明确注释为预留，且后续能力已规划消费方；不参与任何逻辑，零回归风险

## Migration Plan

纯客户端视觉 + 双端同构逻辑，无数据迁移。回滚 = revert 提交。新同步字段对旧存档/已生成实体落到默认值。

## Open Questions

（无）

## Future Work

- **faction 字段的消费**：后续 change 计划引入阵营枚举（玩家/妖怪/中立）与队伍白名单，实现"友军弹幕不误伤"及"敌我视觉辨识"。本 change 仅预留字段。
- **弹幕延后到半透明地形之后重绘**（`AFTER_TRANSLUCENT_BLOCKS`）：可彻底消除"发光在水面占位"的硬边界，需把弹幕从普通实体阶段剥离并手动遍历渲染，属较大改动。
- 激光预警线的写深度语义（当前 `glowRenderType()` = `additiveSolid`）留待后续评估。
