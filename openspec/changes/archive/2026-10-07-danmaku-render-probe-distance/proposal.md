# 弹幕渲染剖针 + 渲染距离对齐视据

## Why

**现象**：wall 600 ~ 800 的弹幕墙让用户持续掉帧。第一直觉是「弹越多越卡」，
但用户实测发现「**视野里出现大量弹幕的时段都会卡**，与是否开 `/danmaku wall`
无直接对应；F3+P 饼图有/无 wall 分布一样」。

三次 JFR（两次全程+两次 in-world；分别含菜单/弹幕在身后/弹幕真在屏上）的
事实链：

1. 服务端几乎不动：Server thread 上 `AbstractDanmakuProjectile.tickDanmaku`
   20s 窗口只采到 7 份样本 → 800 枚 server tick 非瓶颈。
2. 程序侧 Render thread 采样主体是 vanilla 片段
   `Uniform.glGetUniformLocation`（`ShaderInstance.apply()` line 265）
   —— 每次 draw、每个 sampler 都查一次 uniform location。
   弹幕只贡献自己的 ~3 个 `MeshData` batch，所以它既是"原版税"，也不是弹幕专属。
3. **第一遍出现、第二/第三遍消退的「弹幕热点」(`VertexConsumer.setNormal`
   Pose 重载 ×423、`Matrix4f.rotate` ×183、`PoseStack$Pose.<init>` ×1500)
   全是菜单/加载期的帧污染**——in-world 老墙场景都在 1/4 以下。
4. `DanmakuBudget`、`danmaku-track-scope` 的位率猜测逐项排除：800 仍未触及
   上限触发 reconcile 的阈值（cap−max(16,cap/16)=1875），且 `tickStats` 读 OK。
5. 第三遍唯一出现的我们代码是 `FxGeometry.vertex`（仪式 FX）——用户**确认不是
   仪式**，是弹幕本身造成的卡顿（留在代码库里以后再探）。

**因此问题分成两座**，本提案分别处理：

### A. 卡顿未定案（弹幕 render 路径探针）

第二、第三遍都显示「弹幕自身渲染 CPU 极低」，但卡顿真实存在 ⇒ CPU 样本看不见的
东西只有两样：**GPU 填率** 与 **原生 GL 驱动时间**。要做剖针分辨，否则优化就是
瞎猜。需要在渲染点插针：每帧渲染弹数、各层渲染用时、getBuffer 调用次数、
push/pop 拷贝，以及按层关断的开关。

### B. 弹幕渲染范围与用户视据断层

`Entity.shouldRenderAtSqrDistance` 用的是
`AABB 三轴平均 × 64 × clamp(renderDistance/8, 1, 2.5) × entityDistanceScaling`
（Entity.java:1692；LevelRenderer.java:856）。默认视据 12 时 sphere 弹约 38 格
即止绘，knife 约 60 格——正是用户观测的「~40 格没弹」，和视距完全脱钩。
`clientTrackingRange(=8 区块=128 格)` 是上游帽子，视据调大也爬不破；
视据调小 GUI 未更新的样本会变灰。

## What Changes

### 1. 剖针（新增调试，不改变生产默认）

- 渲染器在 `render()` 入口按层（本体/外发光/亮核）push
  `gensokyou_danmaku_body|glow|core` 到 Minecraft Profiler，便能在 F3+P 饼图里
  直接看到「弹幕到底占多少」。
- `/danmaku layers <body|glow|core|all>`：按层关断渲染层，用来按 2 倍速掐断
  GPU 填率贡献——「关掉辉光层 30% 帧率回来」之类的半秒级对照，胜过任何 CPU 样本。
- 每帧计数（渲染实心体数/发光占位层数/亮核层数/顶点数）挂在 `/gs_boss danmaku`
  的诊断行里，同行看到「有多少层在画」。

### 2. 渲染距离对齐视据（行为修正）

- 在 `AbstractDanmakuProjectile` 覆写 `shouldRenderAtSqrDistance(double)`：
  用 `Minecraft.getInstance().options.renderDistance() × 16` 作为半径。
- `LaserDanmaku` 的覆写并入为 `max(maxLength + 64, 视据半径)`。
- `clientTrackingRange` 从 8 区块抬到能装下最大视据的统一值（**建议 32 区块=512 格**），
  不再成为上游帽子。保留非弹幕实体（`orbit_yin_yang_orb` 等）现有值。

### 3. 后续优化（依剖针结果再决）

按层/帧计数 + F3+P 的实测结果决定第一刀指哪：
- **若关断亮核/辉光层恢复帧率** → GPU 填率主因 → 做 LOD（小弹砍辉光/亮核层）；
  可选的合批 billboard（单 buffer 上传所有球弹）作为第二候选。
- **若关断外发光后渲染线程 native 时间仍被 GetUniformLocation 吞掉** →
  减少 `MeshData` batch 数（许多 RenderType 一层一批 ⇒ 内靠 1 个染色代理
  RenderType + 顶点色，避免每种发光都一种 RenderType）。
- 微优化：`renderOffset` 的 `Vec3.add` 分配、`PoseStack$Pose` 复制尽量复用。

## 实测（已写入，2026-10-07）

- 三条真实做空在 `/danmaku layers` 下的现实是：仅关 glow 不足、仅关 core 不足，
  glow+core 全关才顺。于是「填率主因 + 单层纹理路径优选」。
- 剖针三层连即 `body=797 glow=0 core=0` 不再进化，
  `renderProbe[body=797 glow=0 core=0 verts=3188 getBuffer=797]` ——已符合实测。
- probe + layer 开关足够判定；不再补另一个 JFR。

## Non-Goals

- 不修无障碍调优以外的同步/年龄逻辑；不动 `clientTrackingRange` 的定位和
  断言规约外的其他实体。
- 不现在决定 LOD 阈值/合批方案——以剖针实测为准。

## 验收

- 剖针：F3+P 里必须能出现 `gensokyou_danmaku_body|glow|core` 切片；
  `/danmaku layers glow` 关断辉光层后效果立刻消失、帧率可观测恢复或不变各纪一次。
- 距离：视据 8/12/16/32 下、弹幕绘制上限依次对齐上一节公式，长激光
  `maxLength+64` 不伸缩失真。
- 三次 JFR 的「现在花在哪」重新读取一次，写入本提案的「实测」段落版本。
