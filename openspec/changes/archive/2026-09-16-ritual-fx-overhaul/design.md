# Ritual FX Overhaul — Design

## Context

- 现状：`RitualCoreRenderer`（BER）按 gameTime 节流发射 Dust 粒子做螺旋与光束（`GLOBAL_BEAM_BUDGET=1536` 预算制）；迦具土火柱是**服务端** `sendParticles(FLAME/SMALL_FLAME/LARGE_SMOKE)`（`KagutsuchiFlameBehavior.emitFlameParticles`），网络与 CPU 双税；八方归元无任何运行态视觉。
- 可复用件：`DanmakuRenderTypes.additiveGlow/additiveSolid`（加法混合、按纹理缓存 RenderType）；`LaserDanmakuRenderer` 的多层光束几何（亮核/主体/外发光/端盖/包络，米字多平面）；`ritual_ghost`/`sukima_portal` 自定义 shader 三件套先例（含 `getUniform` 逐批喂 uniform 的实测套路）；`RitualRenderState` 渲染态通道（仅变化推送、稳态零包、区块载入即下发，已被 relay 验证）。
- 硬事实：`buildRenderState()` 目前非 RESONANCE 图案直接返回 null——`isBurning`、八方归元的 enabled/阶级**均未到达客户端**；客户端也拿不到 `activeMatch`（服务端对象）。通道泛化是本设计的第一块基石。
- 约束（neoforge-1211-dev skill 实测）：shader json 的 vertex/fragment 必须带 `gensokyou:` 命名空间；可调数值全进 `GensokyouConfig`；贴图走 `tools/gen_tex.py` 管道。

## Goals / Non-Goals

**Goals:**
- 三个仪式的运行态特效从"粒子承重"升级为"网格/shader 主体 + 粒子点缀"：迦具土密集火柱（阶级缩放）、共鸣塔宽幅紫雾带 + 持续闪电弧、八方归元 fresnel 灵气球（呼吸、阶级缩放）。
- 渲染态通道泛化为多仪式共享，特效全客户端本地绘制，服务端零粒子广播（点缀烟除外）。
- 成本模型从 O(粒子数) 变为每核心 O(常数)，删预算制，"用力过猛"不再威胁性能。

**Non-Goals:**
- 水位联动的灵气球表现（留接口，本次不实现）。
- 祭品台静置渲染 bug（`ritual-pedestal-rest-render-fix` 独立处理）。
- 其他仪式（造华/八百万/月影/渡梦）的特效改造——只动本次点名的三处。
- 弹幕系统自身的任何改动。

## Decisions

### D1 渲染态通道泛化：`kind` 分发 + 字段复用，而非新建 payload

`RitualRenderState` 增加 `kind`（0=none, 1=relay, 2=kagutsuchi, 3=bafang），字段按 kind 复用：

```
字段        relay            kagutsuchi              bafang
enabled     运行态           运行态                   运行态
tier        阶级             阶级                     阶级
minY/maxY   螺旋纵向范围     结构包围盒(火柱顶端参考)  不用(填0)
linkPos     链接目标坐标     祭品台'P'坐标列表         不用
inCount     入向分界         0                        0
period      结算周期         0                        0
movingMask  通道搬运位图     bit0=燃烧中               不用
```

- 服务端：`buildRenderState()` 增加 KAGUTSUICHI/BAFANG 分支（数据源 `activeMatch.level()`、既有规范序 `pedestalPositions()`（BE 类型判定）、`isBurning()`）；**实测 serverTick 尾部已有每 tick 无条件 `syncRenderState()`（结构失效 return 前亦有）**，burning/enabled/tier 翻转同 tick 即被 equals 去重推送，无需新增挂点。燃烧态每秒至多变一次，稳态零包性质保持。
- 客户端：`RitualCoreRenderer.render()` 读 `state.kind()` 分发到四个静态 emitter；`SPAWN_GUARD`（防逐帧重发）机制删除——网格本来就是逐帧几何，无"发射"概念。
- 备选：每仪式独立 payload + 客户端状态持有者——否决：通道现有语义（变化推、首载推、清零推）完全够用，复制三套握手纯浪费；且 `getUpdateTag` 单点下发已被"区块载入即可见"验证。
- 备选：客户端读 `RitualCoreRegistry` 推导——否决：registry 是服务端结构（skill：渲染态 MUST NOT 参与结算、客户端 MUST NOT 反推服务端权威状态）。

### D2 共享加法几何工具：抽 `FxGeometry`，不各自造轮子

从 `LaserDanmakuRenderer` 的 emitBeamPlane/emitCap 提炼中性的静态工具（方向对齐米字面片、竖直交叉面片、十字光斑、单位经纬球、`FULL_BRIGHT` 顶点发射），RenderType 直接复用 `DanmakuRenderTypes` 缓存表（按新 fx 纹理键）。火柱/雾带/闪电三者共用。
- **实测修正**：最初实现的"面向摄像机条带"（side=cross(dir,view)）在掠射角整条棱边消失——旧版"紫气薄束"与首测"闪电横流"根因即此。改为激光弹幕已验证的"把 +Z 对齐段方向 + 绕轴多面交叉"，任何视角都有正对分量。
- 备选：每种特效独立 renderer 类各写几何——否决：三特效几何原语高度重合，集中一处好调优（顶点色打包、UV 滚动参数统一）。

### D3 迦具土火柱：网格路线 A + 密集台位分布 + 少量烟点缀

- 发射点 = 核心 + 每座 'P' 台位（渲染态 linkPos），阶级 ≥2 追加环上插值柱：环半径客户端由台位坐标推 `maxDist(struct)`（复刻现服务端 `emitFlameParticles` 的分布逻辑，视觉分布与规格现状一致）。
- 单柱几何：`PIER_PLANES`（默认 3，即 6 向米字半交叉）个竖直平面绕 Y 均布，贴火纹条带（下宽上尖、底亮顶淡），V 向随 gameTime 滚动 + 逐柱相位种子横向抖动；加法混合。柱宽/高/数量随阶级放大。
- 台位"较密集"：每座祭品台不止一柱——台面取 `2 + 阶级` 个确定性伪随机点（seed=台位坐标，散布半径 0.42），保证远看是"一片火坛"而非"一根签子"；实测首版 1+阶级/2 偏稀、柱体偏细矮，已上调宽度每阶 ×1.35、高度 +0.9/阶、环柱半径铺到结构外扩边界的 45%~125%（原仅至 100%，外圈无柱）。
- 点缀层：保留服务端 `LARGE_SMOKE`（现频率，每柱随机选位），FLAME 粒子全删。服务端 FLAME 发射代码（`emitFlameColumn`）整体移除。
- 备选：自定义翻花火焰 sprite 粒子（路线 B）——用户拍板选 A；且 mcmeta flipbook 在 1.21.1 独立实体贴图路径生效性未验证，A 无此风险。

### D4 紫雾带：连续 ribbon，加厚而非加密

沿核心纵轴螺旋扫出中心线（角度/半径/高度参数化），逐段以**交叉双面片**发射（非单面条带，避免掠射消失），带半宽默认 1.6 格（Dust 时代"线宽"的数倍）；双层错位相位（主层实、副层宽而暗）+ 随阶级加层。雾纹贴图软边渐隐，V 滚动 + 正弦扰动半径给出"气"的流动感。
- 备选：更多 Dust 粒子——回到死循环，否决。备选：粒子 ribbon shader——1.21.1 无 GPU 粒子基建，CPU ribbon 已足够便宜。

### D5 闪电弧：持续放电、本地随机、双层条带

- 顶点序列：塔顶→目标按 ~1.2 格分段，每内点施加垂直于轴的双向随机偏移（幅度沿长度正弦包络、两端归零），每 2 tick 整体重掷 → 持续噼啪形变；段数上限 96（超长距加粗偏移而非增段）。
- 每段画 2 层**方向对齐米字面片**：细白核（`CORE` 纹理，近白，2 面）+ 粗晕（`GLOW` 纹理，通道色：入青/出绿，沿用现语义，3 面）；两端落雷点画 `laser_cap` 式十字光斑（三轴交叉，任意角度呈径向光晕）。
- 出现/消失：包络淡入淡出（同激光 3 tick ramp），moving 位图翻起即淡出，MUST NOT 单帧硬切。
- 纯客户端本地 `RandomSource`（不 seed 同步——视觉件，原版闪电同步 seed 是因为它携带伤害，此弧无权威语义）。
- 删除 `GLOBAL_BEAM_BUDGET` 与 Dust 光束代码。
- 备选：原版 `LightningBolt` 实体——一次性、自带伤害语义与刷实体开销，持续放电做不到，否决。备选：bezier 平滑弧——闪电审美要折线不要蛇形，否决。

### D6 灵气球：fresnel shader + 生成网格，参数单点装配

- 新 shader `gensokyou:spirit_orb.{json,vsh,fsh}`：vsh 变换单位球顶点并传 viewDir；fsh `pow(1.0 - dot(N, V), RIM_POW)` 出边缘光 × `Tint`(vec3 绿系) × `Pulse` uniform；半透明混合 + 不写深度。注册进 `shaders/core` 并在 `RegisterShadersEvent` 挂载（克隆 `ritual_ghost.json` 结构）。
- 几何：程序生成单位经纬球（32×16），静态顶点数组缓存，逐帧 PoseStack 缩放。
- 悬浮于核心上方 `0.55 + 0.18 × tier` 高度（阶级既放大又抬升），呼吸 = 缩放 × (1 + amp·sin(time·rate))，amp/rate 进配置。
- 参数单点函数 `orbScale(tier, /* 未来 storedRatio */) → radius`，水位表现日后只改装配点与 shader uniform，不动渲染结构。
- 备选 (a) 正交 billboard 圆盘组：视角穿帮明显，用户已否；备选 (b) 纯网格+加法：无"灵气"边缘感，用户选 (c)。

### D7 贴图与常量

- 新贴图 `textures/fx/`：`flame_column.png`（火柱条带）、`spirit_mist.png`（雾带软噪声）、`bolt_core.png`/`bolt_glow.png`（闪电）、`orb_*`（无——纯 shader 程序纹理）。全部经 `tools/gen_tex.py` ASCII 像素图管道产出（gen-textures skill）。
- 全部缩放/密度/频率/颜色常量进 `GensokyouConfig`（COMMON），命名 `ritualFx*`。

## Risks / Trade-offs

- [加法大面积 overdraw（火柱×N台位×多层）帧率风险] → 每核心层数随阶级封顶；BER 自动继承视锥/距离剔除；台位柱数有配置钳位；实机 5 座满配塔 + 3 座 5 阶火祭联测为验收场景。
- [半透明 RenderType 与原版 translucent 的排序] → 沿用弹幕实测：外发光不写深度、需正确遮挡水面的核层用 `additiveSolid`；灵气球不写深度，接受"身后水覆盖球"与激光外发光同款权衡。
- [渲染态 tag 体积随台位数增长（八方/迦具土结构可达 ~40 台）] → long[] NBT 每坐标 8B，40 台 <400B 且仅变化时推；无逐 tick 包。
- [burning 翻转触发点遗漏（漏清/漏发火柱）] → `beginBurnBatch`/`clearBurnBatch`/`setEnabled`/结构失效四处统一走 `syncRenderState()`，equals 去重兜底；spec 场景"停等即灭/点火即燃无断档"验证。
- [客户端由台位反推环半径与服务端观感漂移] → 分布逻辑写成纯函数（输入=P 坐标列表）并单测，服务端粒子版删除前留存对照截图。
- [shader 命名空间踩坑（裸名 → minecraft: 崩溃）] → json vertex/fragment 一律 `gensokyou:` 前缀（skill 实测项）。

## Migration Plan

Dev 期无存档义务：渲染态字段新增走 NBT 向后兼容（缺 kind → 按 relay 语义读，等价现状）。旧 Dust 路径直接删除。回滚 = revert 单 commit，无数据残留。

## Open Questions

- 火柱纹理风格（偏写实烈焰 or 偏仪式紫纹）——实现期出图后实机定夺（"先看看效果"）。
- 闪电是否加分叉枝（v1 先无分叉，观感不够再加 `BOLT_BRANCH` 开关）。
