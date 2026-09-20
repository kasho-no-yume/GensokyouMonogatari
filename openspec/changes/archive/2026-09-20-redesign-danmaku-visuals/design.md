# Design: redesign-danmaku-visuals

## Context

弹幕渲染管线现状：

```
AbstractDanmakuRenderer（基类）
├─ 本体层: entityCutoutNoCull(贴图) × 实体顶点色
└─ 发光层: 放大 1.35×，additiveGlow（加法混合，不写深度）
    ├─ SphereDanmakuRenderer   — billboard quad，16×16 红色像素球贴图
    ├─ TalismanDanmakuRenderer — 水平纸片 quad，16×16 深灰像素贴图
    ├─ KnifeDanmakuRenderer    — 不动
    └─ LaserDanmakuRenderer    — 多层自组装：外发光/主体/亮核/端盖
                                  （emitBeam × 6 平面圆柱 + 面向摄像机端盖）
                                  已有 computeEnvelope（3 tick 收放包络）
DanmakuRenderTypes — additiveGlow / additiveSolid 两个自建 RenderType
```

关键事实：
- 球弹贴图烤死红色（中心 `255,0,0`），非红色弹经顶点色相乘后发黑。
- 本体层 cutout 混合不支持半透明边缘，做不了柔光。
- 激光局部坐标系 `alignToDirection` 后，Z+ 沿光束、局部 XY 平面垂直于光束——法阵天然宿主平面。
- immediate 缓冲别名规则：请求不同 RenderType 会立即冲刷上一批，必须按层顺序整批写入，禁止交叉。

## Goals / Non-Goals

**Goals:**
- 球弹观感对齐东方弹幕：白心 + 色边 + 光晕，任意实体色正确染色
- 札弹颜色正确、风格与球弹统一（高清柔光）
- 激光发射端五芒星法阵演出（ACTIVE 期，包络展开/收起 + 自旋）
- 所有新贴图去色（白/灰），颜色统一交给顶点色

**Non-Goals:**
- 刀弹外观改动
- DELAY 预警指示线的行为/观感改动
- 弹幕行为逻辑（碰撞、判伤、寿命、追踪）任何改动
- shader/bloom 等后期特效

## Decisions

### D1: 球弹三层结构（方案 B），贴图去色

主体贴图为 64×64 灰白径向渐变（中心近白 → 边缘 alpha 平滑衰减到 0），颜色完全由顶点色提供。

```
③ 外发光: additiveGlow，1.35×，实体色（现有，保留）
② 主体:   translucent × 实体顶点色，渐变贴图
① 亮核:   additiveGlow，约 0.5×，恒白（255,255,255）
```

- 拒绝"纯贴图方案"（白心彩边烤进贴图）：换色失真，废掉现有 RGB 随机色系统。
- 亮核层复用激光 `CORE_WHITE_MIX` 的成熟写法；亮核用单独一张小径向渐变贴图（或共用主体贴图——中心白、边缘透明，缩小后自然就是白核，**优先共用一张**，省贴图且观感一致）。
- 本体层从 `entityCutoutNoCull` 换成 translucent 类 RenderType（新增 `DanmakuRenderTypes.translucent`，alpha 混合、noCull、满亮度可选）。渐变边缘 alpha < 1 必须有真混合才能柔。

### D2: 札弹仅换贴图，几何不动

新贴图：高清白色符纸（去色，符纸纹路线条 + 淡渐变），尺寸保持 16×16 网格比例不变以免动 UV/几何常量——分辨率升到 64×64 但长宽比一致，UV 归一化后无需改代码。

### D3: 法阵贴图与绘制方式

256×256，白色线条（透明底），内容：外圈双线圆环 + 内接五边形 + 五芒星 + 五角小圆点缀（可选符文点）。离线用 Python/PIL 程序化绘制：4× 超采样画几何图形 → 缩回 256 出图。**非像素画、非 gen-textures ASCII 工具链**——法阵是数学图形，矢量绘制天然平滑。

### D4: 法阵渲染时机与动画

```
DELAY:  法阵不渲染（预警线不变）
ACTIVE: 开火 → 随 computeEnvelope 粗细包络同步展开（scale = envelope）
        全程绕 Z 轴自旋（tickCount + partialTick 驱动恒速旋转）
        收束 → 包络归零时法阵同步收起
```

- 法阵 quad 画在激光局部坐标系 z≈0 处（发射端），XY 平面天然垂直于光束，无需额外朝向计算。
- 缩放基于激光半径 × 包络，与光束粗细联动（"法阵撑开光束"的观感）。
- 加法混合（复用 `additiveGlow(laser_magic_circle)`），染激光实体色，FULL_BRIGHT。
- 注意缓冲别名：法阵层与外发光同用 additiveGlow(不同贴图)，仍须整层连续写入——在 `renderActiveBeam` 中按"外发光 → 法阵 → 主体/亮核/端盖"顺序取 consumer。

### D5: RenderType 层面的新增收敛在 DanmakuRenderTypes

新增 `translucent(RenderType)`（球弹/札弹本体用）；法阵复用现有 `additiveGlow`。均走已有的 ConcurrentHashMap 缓存模式。

## Risks / Trade-offs

- [加法层叠过多导致亮部过曝] → 亮核/发光 alpha 取低值起步（参考激光现有参数），游戏内调参
- [translucent 本体在弹幕密集重叠时可能发灰] → 主体保持高 alpha（≥235），仅边缘低 alpha；若糊则回退加法混合主体（代码只换 RenderType，可逆）
- [法阵 256×256 在小半径激光下缩太小看不清线条] → 线条宽度按 256 画布相对值设计（外环线宽 ~6px），最小缩放仍可辨
- [共用球弹贴图做亮核在小尺寸下边缘偏灰] → 若亮核观感不足，再单独出一张更陡峭的中心渐变贴图（预留退路）
- [PIL 生成脚本一次性使用] → 脚本放 `tools/`（如 `tools/gen_danmaku_textures.py`）保留可复跑调参

## Migration Plan

纯客户端表现 + 资源替换，无数据迁移。贴图同名替换（sphere/talisman）无需改注册；法阵为新文件。回滚 = revert 提交。

## Open Questions

（无——三个确认点已由用户拍板：方案 B；法阵仅 ACTIVE 期、包络联动、自旋；高清非像素画。）
