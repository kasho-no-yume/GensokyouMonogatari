# Ritual FX Overhaul — Tasks

## 1. 渲染态通道泛化（D1）

- [x] 1.1 `RitualRenderState` 增加 `kind` 字段（byte，0=none/1=relay/2=kagutsuchi/3=bafang）与 `toTag/fromTag/equals/hashCode` 对应；kind 缺省（无 "K" 键）按 relay 读，向后兼容
- [x] 1.2 `RitualCoreBlockEntity.buildRenderState()` 增加 KAGUTSUICHI 分支（tier、包围盒、台位 long[]、burning→mask bit0）与 BAFANG 分支（enabled+tier）
- [x] 1.3 `syncRenderState()` 无需新增挂点：serverTick 尾部每 tick 无条件同步 + equals 去重，状态翻转同 tick 收敛
- [x] 1.4 单测：布局纯函数确定性/阶级柱数/环柱条件、三 kind toTag/fromTag 往返、缺 K 键回落 relay

## 2. 共享基建（D2/D7）

- [x] 2.1 `FxGeometry`：方向对齐米字面片 `emitAlignedBeam`、竖直火柱面片 `emitCrossPlanes`、端点十字光斑 `emitCrossGlow`、单位经纬球 `emitUnitSphere`
- [x] 2.2 gen_tex 管道新增 `textures/fx/`：flame_column / spirit_mist / bolt_core / bolt_glow（加法混合向黑渐变）
- [x] 2.3 `GensokyouConfig` 新增 `ritualFx` 节 26 项（火柱/雾带/闪电/灵气球全部可调）

## 3. 迦具土火柱（D3）

- [x] 3.1 `RitualCoreRenderer` 按 kind 分发；旧 `SPAWN_GUARD`/Dust 路径删除
- [x] 3.2 火柱 emitter：核心 + 台位密集（`base+tier` 点/台）+ 阶级≥2 环柱（半径 45%~125% 结构外扩边界）；宽度每阶 ×1.35、高度 +0.9/阶
- [x] 3.3 启停淡入淡出包络（FX_RAMP_TICKS，禁单帧硬切）
- [x] 3.4 服务端删 `emitFlameColumn`/FLAME/SMALL_FLAME，仅留低频 LARGE_SMOKE 点缀
- [x] 3.5 实机复核：火柱出现且已铺开（布局改为结构半径驱动后不再依赖台位数据）

## 4. 共鸣塔雾带与闪电（D4/D5）

- [x] 4.1 紫雾带：螺旋中心线逐段交叉面片（2 面），带半宽 1.6、双层错位、阶级加层
- [x] 4.2 闪电弧：抖动折线逐段米字面片（晕 3 面 + 芯 2 面，均加法）+ 两端十字光斑 + 每 2t 重掷
- [x] 4.3 删除 Dust 光束与 `GLOBAL_BEAM_BUDGET`；**路线修正**：原"摄像机朝向条带"在掠射角棱边消失（旧版"紫气薄束"/首测"闪电横流"成因），统一改为激光弹幕已验证的"方向对齐 + 米字交叉"几何（`emitAlignedBeam`）
- [x] 4.4 实机复核：持续放电形态/双向配色/停机即灭/雾带厚度随阶级（用户三轮实机确认）

## 5. 八方归元灵气球（D6）

- [x] 5.1 `shaders/core/spirit_orb.{json,vsh,fsh}`：fresnel rim × 雾噪声流动 × 呼吸
- [x] 5.2 `SpiritOrbRenderTypes` + `RegisterShadersEvent`（格式 `POSITION_COLOR_TEX_LIGHTMAP`，Time uniform 每帧写入）
- [x] 5.3 静态经纬球网格 + 单点装配 `阶级→半径/高度`（半径随阶级、悬浮随半径抬升）
- [x] 5.4 停机/未启动零呈现 + 淡出包络
- [x] 5.5 **崩溃修复**：BufferBuilder 要求顶点格式全元素写入——orbVertex 补 `setLight`（UV2），否则 `Missing elements in vertex: UV2` 客户端崩溃
- [x] 5.6 **顺序崩溃修复**：`BufferSource.getBuffer` 请求不同 RenderType 会**立即结算上一批**（`endBatch(lastSharedType)`）；先把 caps/glow/core 三个 consumer 全取出再交叉写 = 向已结算 builder 写顶点 → `Not building!`。改为三遍严格顺序（每遍开始才 getBuffer）

## 5b. 实机反馈修正（第二轮）

- [x] 5b.1 **特效消失真因**：NeoForge 对 global BE 的可见性判定是 `frustum.isVisible(renderer.getRenderBoundingBox(be))`（默认=单格）——`shouldRenderOffScreen` 只摆脱区块可见性、**不豁免视锥**。覆盖 `getRenderBoundingBox`：relay ±96 格、其余 ±16 格、纵向 -16..+48；距离放宽到 192
- [x] 5b.2 **配置未生效**：`run/config/gensokyou-common.toml` 保留旧默认值（`fxOrbRadiusPerTier=0.28`、`fxFlameWidth=0.32`…），新默认值根本没用上。已移到 `.stale.bak`
- [x] 5b.3 灵气球写深度（COLOR_DEPTH_WRITE）：BE 阶段先于半透明地形绘制，不写深度会被后画的玻璃/祭品台悬浮物盖住
- [x] 5b.4 默认值上调：灵气球 `0.7 + 1.6×阶级`（5 阶半径 8.7）、火柱宽 0.7、高 `3.0 + 1.3×阶级`、环点 14+2×阶级、台位 3+阶级 点
- [x] 5b.5 诊断日志扩到全 kind（kind/tier/aux/en/mask，每核心每 10s 一条），用于判定"消失时 render 是否被调用"

## 5c. 实机反馈修正（第三轮）

- [x] 5c.1 **火柱全挤中心修复**：旧布局用"祭品台坐标"推半径，台位数据缺失时退化为最小半径 2 格。改为服务端同步**结构水平半径**（`maxY` 字段语义重定义为半径，见 RitualRenderState javadoc），布局改为同心环带（0.42/0.70/0.98×R，层数随阶级 1→2→3）+ 面积均匀填充（sqrt 采样），从内到外铺满台面
- [x] 5c.2 布局测试改为性质断言 + 回归用例：`emptyPedestalsStillSpreads`（台位缺失也必须铺到 0.7R 以外）、`unknownStructureRadiusFallsBackToMinimum`
- [x] 5c.3 **闪电优先级**：亮核从发光层改为"加法 + 写深度"（`additiveSolid`，同激光主体策略），修复更近的弧被玻璃/悬浮物盖住；外晕保持不写深度
- [x] 5c.4 修复拼写污染：常量声明被写成了 `MASK_KAGUTSU**I**CHI_BURNING`（多一个 I，IDE/编译器报"找不到符号"）。已用 IDE 重命名统一为 `MASK_KAGUTSUCHI_BURNING`（7 处引用）

## 6. 回归与收尾

- [x] 6.1 `compileJava/compileTestJava` 零错误；140 项单测全绿
- [x] 6.2 剔除修复：`shouldRenderOffScreen=true` + `getViewDistance=128`（抬头/视角边缘特效消失）
- [x] 6.3 弹幕本体未改动（`DanmakuRenderTypes`/`LaserDanmakuRenderer` 无 diff）
- [x] 6.4 三轮实机（共鸣塔/迦具土/八方归元同世界运行）未报性能问题；未单独构造极限压测场景
- [x] 6.5 归档前已移除 `diagOncePerWindow` + `FX_DIAG` 临时诊断代码

## 7. 归档时的已知遗留（用户接受，不阻塞归档）

- 火柱观感偏"诡异"：柱体为米字交叉面片 + 竖直 V 滚动，正视时可见面片交叠边；接受现状。
- 火柱散布可越出仪式结构边界：外层环带为 0.98×半径并带 ±12% 抖动、面积填充最大约 1.06×半径。后续若需收紧，只需在 `RitualFxLayout.flamePillars` 对 `r` 施加上限钳制。
- 灵气球未接库存水位（原设计即预留接口，见 design D6）；未来接水位只需改 `RitualCoreRenderer.renderOrb` 的单一装配点。
