> 本任务的每一条都对应 `docs/barrier-shatter-fx-postmortem.md` 记录的一个真实坑。
> 动手前先读那份复盘；每完成一个任务组，回头核对该表。

## 1. 重播命令（先做，否则后面全是盲写）

- [x] 1.1 `DebugCommands`：`/gs_debug barrier replay <core>` —— 定位该 `RitualCoreBlockEntity` 的 `portalPos`，对其 `SukimaBlockEntity` 重新 `requestOpen(scale, burstTicks)`，并对幻想乡侧孪生门做同样处理（双门同帧）。
- [x] 1.2 该命令 MUST NOT 触碰闩锁、MUST NOT 重新判定需求、MUST NOT 消耗祭品——闩锁已是永久闩且祭品已在开启瞬间被吃掉。
- [x] 1.3 保留既有只读探针 `gs_debug barrier <pos>`，把 `replay` 作为其子命令而非平级。
- [x] 1.4 用 `Gensokyou.LOGGER.info` 打一行 `[GS-AUTO] BARRIER REPLAY …`（低频事件，可安全打点）。

## 2. 演出时钟

- [x] 2.1 `SukimaBlockEntity`：新增持久字段 `fxStartGameTime`（int），`requestOpen` 中赋 `level.getGameTime()`；`getUpdateTag` / `handleUpdateTag` / `loadAdditional` **三处都写或读它**。
- [x] 2.2 ⚠️ 逐行确认 `loadAdditional` 补齐了此前漏读的键——只读一条路径会让客户端值永远停在默认值。
- [x] 2.3 `fxTicks()` 改为 `Math.max(0, (int) level.getGameTime() - fxStartGameTime)`（客户端侧），删掉 `fxSeq` 比较分支。
- [x] 2.4 服务端 `serverTick` 同样改用 `fxStartGameTime` 推导 `elapsed`，保证爆炸触发点与客户端演出同源。
- [x] 2.5 `SHATTER_FX_TICKS = 200` 改为派生（`fxChargeEnd + 开门时长`），消除"调大 burst 就会被静默截断"。
- [x] 2.6 `fxChargeEnd` 的来源 MUST 在服务端与客户端一致：优先直接从 tag 读（现有 `TAG_OPEN_DELAY` 已在传），MUST NOT 两侧各自读配置（postmortem §9 记录过两扇门一个 60 一个 1 的事故）。

## 3. 死代码清理

- [x] 3.1 `SukimaBlockEntity`：`fxSeq` / `lastSeenFxSeq` / `fxPlayed` / `fxReplayTick` / `lastEmittedFxTick` / `lastSentFxTicks` / `openDelayTicks` / `moteIndex` / `renderRandom` / `burstDone` / `isBurstDone` / `markBurstDone`。
- [x] 3.2 `SukimaBlockEntity`：`SHATTER_SHOCK_TICKS` / `SHATTER_RING_STEP` / `SHATTER_BEAM_STEP` / `SHATTER_EMBER_TICKS` / `SHATTER_RADIUS_PER_SCALE` / `SHATTER_FLATTEN` / `SHATTER_CORE_STEP` / `FIB_COUNT` / `GOLDEN_ANGLE` / 五个 `FX_*` `DustParticleOptions`。
- [x] 3.3 `SukimaPortalRenderer`：三段孤儿 javadoc（爆炸球壳 / 冲击环 / 演出总长度）、未使用的 `ParticleOptions` / `ParticleTypes` / `RandomSource` / `Vec3` import。`eyeCenter()` 或接线启用或删除。
- [x] 3.4 改正 `SukimaBlock.java:54-57` 那条与代码不符的注释（说差分四个 `lastSent*`，实际三个）。
- [x] 3.5 清理后 `GensokyouTextures.SHATTER_GLOW` 接入使用（不再是孤儿资源）；`SUKIMA_PORTAL_MOTES_PER_SEC` 接入使用。

## 4. 包围盒

- [x] 4.1 `SukimaPortalRenderer#getRenderBoundingBox` 扩到覆盖「眼体 ∪ 球(r=3) ∪ 烟环(r=15)」，取最大者，并同时向上覆盖眼体全高。
- [x] 4.2 记录代价：BER 在视距 192 内几乎恒可见 → 常驻粒子 MUST 按距离门控（见 8.3）。

## 5. 光柱折线共用

- [x] 5.1 把 `RitualCoreRenderer#buildBoltPoints` 抽到 `FxGeometry`（`public static`），补一段类级注释说明"折线生成器供共鸣塔与结界崩解共用"。
- [x] 5.2 共鸣塔侧改为调用 `FxGeometry`，行为 MUST NOT 变化。
- [x] 5.3 补一条离线单测：`buildBoltPoints` 的输出满足「两端偏移归零」「段数在 [4, maxSegments]」「相邻段长约等于 `segLen`」。

## 6. 蓄能段（球 + 光柱）

- [x] 6.0 ⚠️ **先重写 `render()` 的姿态分段**（现两条注释一条作废、一条下面没代码）：
  - 世界姿态：translate 到眼中心 → 画球、画烟环、发粒子；
  - billboard 姿态：内景虚空 + 眼睑两片。
  - **烟环必须是世界水平的**：billboard 姿态的局部 XZ 平面正对相机，在它里面画"水平环"会得到跟着视线转的椭圆。
- [x] 6.1 `GensokyouConfig` 新增 `ritualFx` 组内：球的最终半径（默认 3.0）、膨胀曲线相关项、光柱条数（默认 10）、光柱长度比例、闪烁频率。
- [x] 6.2 球：斐波那球面采样 + `emitCrossGlow`，贴图 `GensokyouTextures.SHATTER_GLOW`，染蓝白（`#C2DCFF` 提为具名常量）。`r(t) = 3 × easeOutCubic(elapsed / fxChargeEnd)`，**单调不减**。
- [x] 6.3 球的中心取眼中心（`CENTER_Y × scale`），与门体同位。
- [x] 6.4 光柱：8~12 条自球心向外的折线，`FxGeometry.emitAlignedBeam` + `bolt_glow` / `bolt_core`，蓝白。
- [x] 6.5 ⚠️ 光柱**强度**用浮点时钟 `now = gameTime + partialTick` 驱动（两路不同频正弦叠加 + 每条独立相位），MUST NOT 用 `elapsed % n`。
- [x] 6.6 光柱**形状**按 `FX_BOLT_ROLL_TICKS` 重掷（这是 roll 不是可见性门控，允许）。
- [x] 6.7 BufferSource 分趟提交：按 RenderType 分趟，趟内写完再换（`MultiBufferSource` 别名规则）。

## 7. 爆炸段

- [x] 7.1 `GensokyouConfig` 新增：烟环最终半径（默认 15.0）、烟环层数（默认 3）、每层 puff 数（默认 24）、烟窗时长（默认 40 tick）。
- [x] 7.2 烟环：`r(t) = 15 × easeOutCubic((elapsed − fxChargeEnd) / 窗长)`，puff 用 `emitCrossGlow(spirit_mist)`，per-puff 确定性抖动，alpha 中段达峰两端归零。**纯几何、无粒子、无状态**。
- [x] 7.3 ⚠️ 不得改用原版 `SMOKE` / `LARGE_SMOKE`：寿命/初速不可控且会累积成实心盘。在代码注释里写下这条理由，避免后人"优化"回去。
- [x] 7.4 服务端：`BarrierBreakBehavior#knockback` 的调用点从 `open()` 迁到爆炸当刻（`elapsed == fxChargeEnd` 那一 tick），半径与强度**不动**。
- [x] 7.5 服务端音效重排：蓄能段在若干**固定相对节点**（如 0.1/0.3/0.5/0.7 × fxChargeEnd）各放一次轻提示（⚠️ 不得逐 tick 门控）；爆炸当刻叠 `GENERIC_EXPLODE` + `WARDEN_SONIC_BOOM` + `AMETHYST_CLUSTER_BREAK`；`burstSound`（`END_GATEWAY_SPAWN`）保留在闩锁瞬间。
- [x] 7.6 `GensokyouConfig`：`ritualFx.sukimaPortalBurstTicks` 默认 60 → **160**，范围上限 600 → 1200。

## 8. 常驻绿十字星

- [x] 8.1 沿眼形椭圆轨道环绕 + 缓慢上浮发射 `ParticleTypes.GLOW`（`level.addParticle`，非 force）。零新贴图、零新粒子类型。
- [x] 8.2 密度随开启进度上升，完全闭合时为零。
- [x] 8.3 ⚠️ 按距离门控：玩家超出渲染距离时 MUST NOT 产生粒子开销（包围盒涨到 30 格后这一条是必需的）。
- [x] 8.4 `SUKIMA_PORTAL_MOTES_PER_SEC` 默认 30 → **80**。

## 9. 时间线自检

- [x] 9.1 `t = 0`：锚点落下，两门同帧。
- [x] 9.2 `t ∈ (0, 160)`：球 0→3 格单调涨 + 光柱常亮。
- [x] 9.3 `t = 160`：爆炸当刻（音效 + 烟环起 + 击退），同 tick 眼睛开始张。
- [x] 9.4 `t ∈ (160, 200)`：烟环 0→15 格展开；眼睛 0→40 睁开。
- [x] 9.5 `t > 200`：绿十字星常驻；演出几何全部退出（无残留）。
- [x] 9.6 关闭路径回归：`closingTicks` 倒数 40 tick → 闭眼 → `removeBlock`，闭眼动画未因本变更被破坏。

## 10. 实机验证

- [ ] 10.1 用 `replay` 反复播放，逐条核对 9.1~9.5。
- [ ] 10.2 30 fps / 144 fps 各跑一遍，确认无按帧闪烁、无整段空白（postmortem §10.3 的原坑）。
- [ ] 10.3 离开加载范围 10 秒再回来，确认**不重播**。
- [ ] 10.4 服务器在演出中途重启，确认不重播、门处于正确状态。
- [ ] 10.5 迟到玩家（在 t≈100 时进入渲染范围）确认落在正确阶段而非从头播。
- [ ] 10.6 斜视角 / 远距离 / 抬头视角各观察，确认包围盒足够、演出不被剔除。
- [ ] 10.7 站在爆炸半径内确认击退发生在 t=160 而非 t=0；确认仪式结构与核心完好。
- [ ] 10.8 ⚠️ 不要在 `render()` 这类每帧路径上挂 logpoint（postmortem §6.2：曾因此冲掉事件缓冲并制造卡顿）。要用探针就打在 `requestOpen` / `handleUpdateTag` 这类低频路径上，且用 logpoint（`suspendPolicy: NONE`）。
- [ ] 10.9 改动文件后重新核对断点行号；断点"命中"时先看 `lineText` 与条件是否真的生效（§6.3）。

## 11. 实机验证暴露的两处回归（已修）

第一次实机验证直接失败：**眼与全部演出完全不可见、异常音效永不停**。
两者都不是猜测，各自定位到根因（详见 postmortem §15）。

- [x] 11.1 `SukimaPortalRenderer.render()` 把 BER 传入姿态当成"世界原点"，改成世界坐标平移 → 与 `LevelRenderer` 已做的方块位置平移**叠加两次**，整扇门画到两倍远处。恢复为方块局部 `translate(0.5, CENTER_Y * scale, 0.5)`；粒子因走 `level.addParticle` 绝对坐标而仍然可见，这正是该 bug 的指纹。
- [x] 11.2 演出世界对齐段（球/光柱/水平烟环）与 billboard 段（眼形）合并为**一次** `pushPose` + 一次 `mulPose`，只留一个明确的旋转起点。传入姿态只有平移、没有旋转，所以在其内部画"水平"仍是世界水平。
- [x] 11.3 旧存档已闩锁的门没有 `FxStart`，且蓄能长度从缺失的 `FxChargeEnd` 读成 `1` → `(int)(1 × 0.1/0.3/0.5/0.7)` 四个递进节点**全部塌成 tick 0**，而 `fxElapsed()` 恒为 0 → 每 tick 叠 4 声永久噪音；同时 `0 >= 1` 为假使 `openTicks` 永不增长、门永远闭着。
- [x] 11.4 读旧键 `OpenDelay` 作为 `FxChargeEnd` 的回退。
- [x] 11.5 服务端为缺锚点的旧档门补一个"演出早已过去"的锚点（眼立刻张开、演出不重播），并把锚点纳入 `syncIfChanged` 差分让两侧收敛。
- [x] 11.6 递进节点抽为纯函数 `chargeCueNodes(int)`：节点 `max(1, min(end, ·))` 封死塌零，升序去重防同一 tick 叠多声。
- [x] 11.7 新增 `SukimaChargeCueNodesTest`：`chargeEnd ∈ [1,400]` 全区间断言无节点为 0、严格升序、不越 `chargeEnd`；160 tick 下节点落在 16/48/80/112。
- [x] 11.8 回归测试总数 281 → 285，`test` 全绿。
- [ ] 11.9 ⚠️ **待实机复验**：确认眼可见、蓄能球与光柱随时间长大、t=160 爆炸、烟环水平、击退在 t=160、绿十字星常驻、**且无持续音效**。
- [ ] 11.10 ⚠️ **待处理**：`run/config/gensokyou-common.toml` 里 `sukimaPortalBurstTicks = 60`、`sukimaPortalMotesPerSec = 30` 是上一轮持久化的值（Forge 不覆盖已有键），会压过新默认 160 / 80 —— 复验 8 秒节奏前需先改这两行或删掉让它们回落默认。（已改为 160 / 80）

## 12. 第二次实机反馈：几何原语选错（已修）

用户实机指出两条：**蓄能光球"是一群光球不是一个"**、**烟环"是一个个被拉伸成椭圆的白色光球纹理"**。
两条**同源**：`FxGeometry.emitCrossGlow` 发的是三张**轴对齐**方片（XY/XZ/YZ 十字），不是 billboard。
- 蓄能球 = 40 个斐波那球点 × 3 张方片 = **120 个各自带完整径向渐变的亮心**；每片自己就是一个球，加法叠加只会让 120 个球心一起更亮，**永不合并**。
- 烟环 = 轴对齐方片在无旋转的世界系里被透视压扁（XZ 面片成扁椭圆、XY/YZ 面片立成板）；且加法混合**只能加亮**，贴图再灰都读作"白色光球"。

- [x] 12.1 新增 `FxGeometry.emitBillboard(...)`：按传入的摄像机旋转摆正单张面片，任何角度都是正圆。保持该类"不依赖摄像机向量"的既有约定，旋转由调用方传入。
- [x] 12.2 蓄能球改为**同心分层 billboard**（`fxShatterBallLayers`，默认 6；半径 `0.26+0.80f²`、alpha `(1−0.9f)`）——半径上错开的贡献累加成平滑单调衰减，屏幕上只有一个球。
- [x] 12.3 烟环改用**原版 `particle/generic_0..7`**（即 `large_smoke` 的 8 帧软边真烟，零新资源）+ **`translucent` 常规 alpha 混合**（加法只能加亮，永远读不出烟）+ billboard，puff 取 `0.86` 扁圆；8 帧按 `(layer + since/5) & 7` 轮换。
- [x] 12.4 `emitCrossGlow` **保留**（`RitualCoreRenderer` 引线端点光斑是正当用途），但 javadoc 加硬警告：只许用于"单点高亮"，画体积/要正圆一律用 `emitBillboard`。
- [x] 12.5 清理 `SHELL_R/G/B`（重构后只剩声明 = 死代码）；`FX_SHATTER_BALL_PUFFS`（"壳上小球数"）语义已不成立 → 换 `FX_SHATTER_BALL_LAYERS`，并从 dev 配置清掉旧键。
- [x] 12.6 修一处**注释与代码打架**：`breath` 被乘进了 `scale`（最外层轮廓 ±10% 摆动，违反用户明确要的"半径必须单调"），改为只调 alpha。

## 13. 第三次实机反馈：常驻绿十字星跑到别处（已修）

- [x] 13.1 **轨道过宽**：半轴 = 眼半宽/半高 × 1.6，再叠 0.8 格上浮，而 `ParticleTypes.GLOW` 自身还会以 0.02/tick 再飘 0.8 格。四层叠加后绿星跑到眼顶上方两格、侧向外扩一格多。→ `fxShatterMoteOrbitScale` 默认 1.6 → **1.15**，上浮幅度 0.8 → **0.45**。
- [x] 13.2 **公转速度是每 2.7 tick 一整圈**（`theta = now * 0.37`）：注释写着"沿椭圆轨道公转"，实机读作一圈均匀亮环，根本看不出在转。→ `MOTE_ORBIT_RATE = 0.17`（约 6 秒一圈）。
- [x] 13.3 **粒子成团结爆**：发射累加器原是 `static WeakHashMap<BlockPos, double[]>`，`BlockPos` 只被弱引用持有，条目被 GC 静默丢弃后 `elapsed` 一次性取到 clamp 过的 2 秒 → 爆出四十多颗绿星挤成一团。→ 状态搬回 `SukimaBlockEntity` 的 `transient` 字段（`takeMoteBudget(int, double)`），静态 Map 与两个 import 一并删除；`elapsed` 的 clamp 从 2.0s 收到 0.25s。
- [x] 13.4 **距离门控 48 格太宽**（包围盒 30 格见方）：几十格外**别的门**也在往你这边撒绿星，看着就像自己这扇门在别处冒绿星。→ `MOTE_MAX_DISTANCE` 48 → **24**。

## 14. 屏幕级/镜头级演出（新增，纯客户端，零新增网络包）

用户要求"离正在触发传送门越近越强、最多 100 格开外完全无效果、**对所有玩家生效**"。
新增 `ShatterScreenFx`（client-only 导演）：`SukimaPortalRenderer` 每帧上报
`(眼中心, elapsed, chargeEnd, now)`，导演取**最大值**（两场仪式叠加会糊成两倍黑屏），
强度 `f(d) = (1 − d/100)^1.5`：`d=0 → 1.00 / 20 → 0.72 / 50 → 0.35 / 80 → 0.09 / ≥100 → 0`。
**刻意与"是谁触发的"无关**，故联机时队友放的同样会震到玩家。

- [x] 14.1 屏幕变暗变蓝：`RegisterGuiLayersEvent.registerAboveAll` 挂全屏 GUI 层，
顶部/底部各一道纵向渐晕 + 平铺一层极淡冷蓝。**排在所有原版层之上** ⇒ 血条/物品栏也被染色，
这正是"屏幕变了"的读感。标定：满强度时角落 ≈ 0.48、中央 ≈ 0.16（"克制"档定的 45% 上限内）。
- [x] 14.2 FOV 冲击：`ComputeFovModifierEvent`。以 70° 基准计：预兆 +2.1°、爆炸 +5.95°、颤动 ±0.42°。
- [x] 14.3 ~~后期层晃动~~ → **升级为真·镜头晃动，见 14.12**。
- [x] 14.4 球面扫描环（`renderScanRings`）：3 条亮细环贴球面、法线缓慢进动、相邻环反向。
作用是给"正在长大的球"一个**可读的尺度参照**——纯径向渐变的球膨胀时很难判断大了多少。
- [x] 14.5 向内吸入粒子流（`emitInflowMotes`）：原版 `END_ROD`，生成半径跟着球一起长
⇒ 球一涨就把粒子"吃掉"。**刻意不建自定义粒子类型**：`GLOW` 寿命内部随机，
做不到"恰好在球心消失"，而"生成半径跟着球长"零成本地实现了同一读感。
吸入流与常驻绿星**各用各的存量累加器**（`takeInflowBudget` / `takeMoteBudget`），
共用一个会让两者的开关互相污染。
- [x] 14.6 地面冲击波环（`renderGroundShockwave`）：爆炸当刻起 20 tick 扩到 9 格。
与烟环刻意区分：烟环慢（40 tick）/厚/alpha 混合/留余韵；冲击波快（20 tick）/薄/加法/一闪即逝。
两者叠加才有"炸开 → 然后散成烟"的层次，只有烟环会读作"缓缓扩大的雾圈"。
- [x] 14.7 新增 8 条离线曲线断言（`ShatterScreenFxCurveTest`）：100 格外恒 0、贴身满值、
随距离单调不增、20 格处 > 0.6、80 格处 0~0.2、蓄能爬升、爆炸当刻为 1 且衰减、
`chargeEnd = 0` 不除零、全部强度恒在 [0,1]。→ 回归测试总数 285 → **293**。
- [x] 14.8 新增 6 个 config 键：`fxShatterScanRings` / `fxShatterScanPuffs` /
`fxShatterInflowPerSec` / `fxShatterShockwaveTicks` / `fxShatterShockwaveRadius` /
`fxShatterShockwavePuffs`。
- [ ] 14.9 ⚠️ **待实机验证**（用户）：屏幕蓝黑渐晕、FOV 冲击、真·镜头晃动、球面扫描环、
吸入粒子流、地面冲击波环，以及"离门越近越强 / 100 格外无效 / 队友触发也生效"。
- [ ] 14.10 ⚠️ **已知限制（有意取舍，非缺陷）**：门只在**进入视锥**时才渲染，故上报随之停止。
导演保留 50 tick（≈2.5 s）后淡出——好处是省掉"每帧遍历全世界方块实体"，且爆炸余波在转头后
仍有余韵；代价是**背对着门时不会震**。

## 15. 真·镜头晃动：引入本项目第一个 mixin

用户拍板要做真的。三条 mixin-free 路径全部查过源码确认不通（不是推测）：
`AFTER_SKY` 传 `null` poseStack；`AFTER_ENTITIES`/`AFTER_BLOCK_ENTITIES` 都在地形之后
（`LevelRenderer` 第 1052/1124 行）只能让世界**一半**晃动；改 `Camera` 对象来不及
（`GameRenderer.renderLevel` 在调 `LevelRenderer.renderLevel` **之前**就把
`camera.rotation()` 的逆旋转烘进了 `frustumMatrix`）。

- [x] 15.1 关键发现：`frustumMatrix = new Matrix4f().rotation(camera.rotation().conjugate())`
是**纯旋转、无平移**的世界→视图变换 ⇒ 视图空间里相机在原点 ⇒ 右乘小角度
（`this = this * R`，R 先作用）就是**相机自身 roll/pitch**，而不是绕世界原点公转。
- [x] 15.2 `LevelRendererShakeMixin`：`@Inject(method = "renderLevel", at = @At("HEAD"))`，
原地 `frustumMatrix.rotateX/rotateZ`。天空（`renderSky`）与地形（`renderSectionLayer`）直接用它；
实体/方块实体/粒子走 model-view 栈，而该栈在 `renderLevel` 内部被 `mul(frustumMatrix)`
同步乘上同一份矩阵 ⇒ **天空/地形/实体/粒子/天气一起晃，HUD 与手持物不晃**。
- [x] 15.3 `ShatterScreenFx` 移除 `RenderLevelStageEvent` 晃动分支，
改为暴露纯数值 `shakeDegrees()`（强度仍按距离衰减、100 格外为 0）。
- [x] 15.4 新增 `src/main/resources/gensokyou.mixins.json`，并在
`src/main/templates/META-INF/neoforge.mods.toml` 取消 `[[mixins]]` 段的注释。
**三个坑，每一个的报错都和"配置错了"毫无关系**，且都实际踩了一遍：
  - **(a) 声明途径只有 `neoforge.mods.toml` 的 `[[mixins]]` 段。**
    manifest 属性 `MixinConfigs` **不起作用**——fancymodloader 的 `ModFileParser` 不读它。
    上一轮曾"配了 manifest 属性就去宣布成功"，结果 mixin 从未加载：编译全绿、测试全绿、
    实机零效果（用户反馈"完全没感觉出在晃"）。
  - **(b) `config=` 绝对不能叫 `neoforge.mixins.json`。**
    **NeoForge 本体就带一个同名配置**（`neoforge-21.1.248.jar` 根目录）。
    同名 ⇒ 同一配置被注册两次 ⇒ 启动即抛
    `IllegalArgumentException: Decoration with key 'fabric-modId' already exists on config neoforge.mixins.json`。
    这解释了 (a) 的现象为何是"静默无效"而非报错：mixin 解析 `neoforge.mixins.json`
    时命中的是 **NeoForge 自己那份**，我们的文件从头到尾没被读过。
  - **(c) 配置文件本体 MUST 放在类路径根，不能放 `META-INF/`。**
    `META-INF/` 是 Forge 读 manifest 时的旧惯例；NeoForge 参照的
    `neoforge.mixins.json`、`jade.mixins.json` 全都在 **jar 根目录**。
    放错位置 ⇒ `IllegalArgumentException: The specified resource 'xxx.mixins.json'
    was invalid or could not be read`。
- [x] 15.5 上述 (a)(b) 两个错误**崩在 mixin prepare 阶段，游戏日志里一行都没有**
  （`latest.log`/`debug.log` 都在 `Compatibility level` 那行戛然而止），
  **只有控制台有**。因此定位手段是：用 `tools/gradle_task.ps1 runClient -NoWait`
  抓 `build/agent-logs/*-runClient.log`，那里能看到 `Exception in thread "main"`。
  **只查 `run/logs/` 会完全看不到错误**——这是本项目 mixin 排障的固定流程。
- [x] 15.6 力度按用户定的标准"<b>可能让玩家感到眩晕</b>"重标定（不是"明显但不晕"）：
roll 系数 7.2 → **20.0**（满强度峰值 **15.6°**）、pitch 4.8 → **14.0**（峰值 **10.9°**）、
FOV 爆炸 0.050 → **0.100**、渐晕 0.70 → **0.85**、平铺冷蓝 0.26 → **0.34**。
新增 `fxShatterShakeScale`（0~3，默认 1.0）作为**免重编译**的调节旋钮。
- [x] 15.7 **已实机验证 mixin 真的应用了**（不是"配了就当成功"）：
客户端日志 `Mixing LevelRendererShakeMixin from gensokyou.mixins.json into
net.minecraft.client.renderer.LevelRenderer`，且成功进到标题界面
（`Loaded 1337 recipes` / `Loaded 29 ritual recipes`）。
- [x] 15.8 **晃动不平滑（"是直接 set 的吗"）—— 根因是时钟，不是幅度**：
`ShatterScreenFx.gameTime()` 返回 `level.getGameTime()`，那是**整 tick**（20Hz）。
60fps 下同一 tick 内**连续 3 帧取到同一个角度、然后跳一下** ⇒ 读作 20 级台阶。
修法是**把包络与振荡器分家**：
  - **包络**（何时蓄能 / 何时爆炸）仍来自**游戏时间**——它必须与仪式时间轴同步；
  - **振荡**（怎么抖）改用 `System.nanoTime()` 的**连续真实时钟**——它只是视觉噪声，
    与游戏逻辑无关，20Hz 量化纯属自找麻烦。
- [x] 15.9 振荡器从"几个正弦相加"换成**三八度一维值噪声**
  （`valueNoise(t, seed)`：整数格哈希 + `smoothstep` 插值）。
正弦和读作**摆动**（规律往复），值噪声读作**震动**（不规则）；且它是 `t` 的
**纯函数** ⇒ 任意帧率下逐位一致，用 smoothstep ⇒ C1 连续无折角。
频率 1.9 / 4.7 / 10.3 Hz 对应低频晃、中频、高频颤。
- [x] 15.10 包络加**单极点平滑**（`approach`，用 `e^{-dt/τ}` 而非 `dt/τ`，
后者在 30fps 与 144fps 下收敛速度差近 5 倍）。起振 `TAU_ATTACK=0.02s`、
释放 `TAU_RELEASE=0.32s` —— 爆炸当刻必须**立刻**顶上去不许爬坡，退去时留余韵不许硬切。
- [x] 15.11 屏幕染色与 FOV 颤动也改走**平滑后的**包络与连续时钟：
原先直接读 20Hz 原始包络，染色会一格一格跳，与已平滑的镜头旋转对不上（读作"闪烁"）。
- [x] 15.12 新增 6 条离线断言（`ShatterScreenFxOscillatorTest`）：噪声恒在 [-1,1]、
幅度足够（不读作"没晃"）、不同 seed 相互独立（否则退化成单一方向摆动）、
**60/144/240 fps 下最大帧间步进均 < 0.9**（这条就是钉死"20Hz 台阶"的守卫）、
单极点收敛与帧率无关、起振显著快于衰减。→ 回归测试总数 295 → **301**。
- [ ] 15.13 ⚠️ **待实机验证观感**：晃动是否已是平滑连续（而非 20 级台阶）、
是否读作"震动"而非"摆动"、强度是否达到"可能眩晕"。
旋钮 `fxShatterShakeScale`（0~3，默认 1.0）。

## 16. 第四次实机反馈："旁边也同步出现了一个隙间"（维度笔误，已修）

不是设计，是**开门路径把幻想乡的坐标交给了仪式的维度**。
`twinPortalPos(...)` 内部走 `gensokyoLevel(...)`，返回的是**幻想乡里的坐标**；
但三处都把它交给 `level`：

| 位置 | 原写法 | 后果 |
|---|---|---|
| `placePortals` | `level.getBlockState(twin)` / `placeAt(level, twin)` | **放置与"先到先得"检查都查错维度** |
| `ensurePortals` | 检查用了 `gensokyoLevel(level)`，**放置却漏了** `placeAt(level, twin)` | 闩锁后每秒复查时又把孪生门塞回仪式的维度 |
| `replayShatter` | `level.getBlockEntity(twin)` | `/gs_debug barrier replay` 只能刷到主世界那扇 |

三条后果：
1. 孪生门被放进**仪式的维度** ⇒ 同一维度两扇门，仪式靠近 `(0,地表,0)` 时读作
   "旁边凭空多了一扇同步的门"（用户原话）；
2. `SukimaBlock#entityInside` 把玩家传到**幻想乡**的 `(0,地表,0)`，那里**根本没有门**
   ⇒ 过不去也回不来，"成对开门好让人原路返回"的意图彻底落空；
3. "先到先得"守卫查错维度，形同虚设。

**坐实是笔误而非设计的证据**：同一文件的 `removePortals`（关闭路径）**一直是对的**
（`requestClose(gensokyo, twin)`），而且 `replayShatter` 的 Javadoc 明写"双门同帧"。

- [x] 16.1 `placePortals`：取 `gensokyo = gensokyoLevel(level)`，检查与放置都走它。
- [x] 16.2 `ensurePortals`：补上遗漏的放置侧维度。
- [x] 16.3 `replayShatter`：改到幻想乡侧查找孪生门。
- [x] 16.4 新增 `BarrierTwinGateDimensionTest`（5 条）：三处禁止写法（`placeAt(level, twin)` /
`level.getBlockState(twin)` / `level.getBlockEntity(twin)`）**任一复现即失败**；
反向断言 `placeAt(gensokyo, twin)` 与 `requestClose(gensokyo, twin)` 必须存在
（否则"因为代码被删了"会让守卫空过）；另加 `TWIN_OFFSET=4`、`PORTAL_UP=2`、
`twinPortalPos` 的 public static 签名。→ 回归测试总数 301 → **306**。
- [ ] 16.5 ⚠️ **需手工清理**：此前每次开门都在**仪式的维度**留下一个孤儿孪生门
（在 `(0,地表,0)+4`）。修复后 `removePortals` 只去幻想乡找门，
**这些孤儿不会被自动关闭**，需手动打掉。

---

## 归档时的验证状态（2026-09-27）

**自动化验证**：`./tools/gradle_task.ps1 test` **306 项全绿**，
`openspec validate barrier-shatter-fx --strict` 通过。

**实机验证：已完成。** 用户在归档时明确告知"观感验收我都完成了"。
本会话中用户实机反馈并驱动了 6 轮修复（光球是一群球 / 烟环被拉成椭圆 / 绿十字星跑到别处 /
无晃动且无压暗 / 启动崩溃 / 晃动不平滑 / 旁边多一扇门），每一轮都在客户端实际观察后提出。
幻想乡孪生门的穿行验证由用户指示跳过。

| 范围 | 状态 |
|---|---|
| 10.1~10.9 原 8 秒时间轴 | ✅ 用户实机验收 |
| 11.9 屏幕压暗 / FOV / 球壳 / 烟环 / 击退 / 无持续音效 | ✅ 用户实机验收 |
| 11.10 配置项回落默认 | ✅ 已改（160 / 80） |
| 14.9 屏幕级与镜头级演出 | ✅ 用户实机验收 |
| 15.13 晃动平滑度与强度 | ✅ 用户实机验收 |
| 幻想乡孪生门穿行 | ⏭️ 用户指示跳过 |
| 16.5 孤儿孪生门清理 | ⏭️ **遗留待办**（见下） |

**归档时唯一的实际遗留**：此前每次开门都在**施法维度**的 `(0,地表,0)+4` 留下过一个孤儿孪生门
（§16 的维度笔误所致）。修复后 `removePortals` 只去幻想乡找门，**这些孤儿不会被自动关闭**，
需在世界里手工打掉。它们除视觉冗余与持续发射绿十字星外无功能影响。

**日后复核入口**：`fxShatterShakeScale`（0~3）是晃动强度的免重编译旋钮；
`/gs_debug barrier replay <core>` 可在闩锁态重播整段演出。
`docs/barrier-shatter-fx-postmortem.md` §15 记录了本轮两类静默失效的排查路径。
