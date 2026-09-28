## 1. 配置基项（COMMON）

- [x] 1.1 在 `GensokyouConfig` 新增灵浴段声明：`REIYOKU_BASE_CAPACITY`（`LongValue`，默认 10000）、`REIYOKU_BASE_IN_RATE`（`LongValue`，默认 1000）、`REIYOKU_TIER_MAX_SPIRIT`（`IntValue` 逐阶表，默认 `[1000,10000,100000,1000000,10000000]`）、`REIYOKU_CHARGE_PERCENT`（`DoubleValue`，默认 0.01）、`REIYOKU_CACHE_PER_SPIRIT`（`IntValue`，默认 10）、`REIYOKU_BATH_RADIUS`（`DoubleValue`，默认 3.0）、`REIYOKU_BATH_HEIGHT`（`IntValue`，默认 2），并以 `define(...)` / `defineInRange(...)` 挂入 builder，逐项写英文注释
- [x] 1.2 在 fx 段新增水面与灵力柱参数：`FX_REIYOKU_WATER_HEIGHT`、`FX_REIYOKU_WATER_COLOR_R/G/B`、`FX_REIYOKU_WATER_SCROLL_SPEED`、`FX_REIYOKU_WATER_BREATH_AMP`、`FX_REIYOKU_WATER_BREATH_PERIOD_TICKS`、`FX_REIYOKU_WATER_LOD_DISTANCE`、`FX_REIYOKU_PILLAR_RADIUS`、`FX_REIYOKU_PILLAR_COLOR_R/G/B`、`FX_REIYOKU_PILLAR_WIDTH`、`FX_REIYOKU_PILLAR_PLANES`、`FX_REIYOKU_PILLAR_SCROLL_SPEED`、`FX_REIYOKU_PILLAR_ALPHA`
- [x] 1.3 确认全链路无硬编码：行为、渲染、GUI 三处 MUST NOT 出现灵浴数值字面量（自检 `grep -n "10000\|120000\|0\.01" src/main/java/.../ReiyokuBehavior.java` 无实质命中）

## 2. 世界无关静态内核

- [x] 2.1 在 `ReiyokuBehavior` 内实现可直接调用的静态函数：`capacity(level)`、`inRate(level)`、`standardMaxSpirit(level)`、`chargePerSecond(level)`、`cachePerSecond(level)`、`cachePerTick(level)`，逐阶值 MUST 与 spec 表逐项吻合（10,000/120,000/1,440,000/17,280,000/207,360,000；5/50/500/5,000/50,000）
- [x] 2.2 实现静态浴区判据 `insideBath(playerPos, corePos)`：欧氏水平距离 + 脚底方块 Y 闭区间 `[coreY, coreY + height]`，MUST NOT 使用 AABB 或 `|Δy| ≤ h`
- [x] 2.3 实现 Bresenham 均分拆分 `splitShare(carry, total, n)`，返回 `[perPlayer, nextCarry]`，聚合后总量精确等于 `total`

## 3. 行为注册与容量分派

- [x] 3.1 `RitualBehaviors` 新增 `REIYOKU` 常量与静态块 `register(REIYOKU, new ReiyokuBehavior())`
- [x] 3.2 `RitualCoreBlockEntity.getCapacity()` 新增灵浴分支，返回 `ReiyokuBehavior.capacity(activeMatch.level())`；**验收用例 MUST 用 2 阶**（1 阶与 `DEFAULT_CORE_CAPACITY` 同值、无法证伪）
- [x] 3.3 `ReiyokuBehavior` 覆写 `refillsCacheFromSocket()` → `true`，并确认全仓库无对该行为的 `tickBatteryAutoFill()` 调用
- [x] 3.4 `ReiyokuBehavior` 覆写 `spiritInRatePerSecond` 返回 `inRate(level)`，`spiritOutRatePerSecond` 保持默认 0；两条端点分道声明

## 4. 充灵主循环

- [x] 4.1 `ModAttachments` 新增不触发 `sync` 的写入路径（供逐 tick 使用），并确认既有 `GraceFlight` 调用点行为不变
- [x] 4.2 实现浴区玩家扫描（`ServerLevel.players()` 迭代 + 浴区判据 + 阶级门控 + 未满池过滤），产出合格玩家列表与分母 N
- [x] 4.3 实现每 tick 顺序：`core.tickBatteryToCacheFill()` 先补料 → 计算本 tick 总缓存份额 → Bresenham 均分 → 逐玩家 `core.extract(share)` 并按实取量 `÷ REIYOKU_CACHE_PER_SPIRIT` 折算为灵力、用静默写入路径更新玩家池
- [x] 4.4 实现 `long` 进位累加器（缓存侧），使非整数份额配置下亦不丢量；累加器随结构失效清理（`onStructureLost`）
- [x] 4.5 边界断言：分母为 0、缓存为 0、玩家满池、`temperLevel = 0` 四种情形下本 tick 抽取与池变化均为 0 / no-op
- [x] 4.6 确认扣费调用点为普通 `extract`（非 `extractRouted`），并确认不因逐 tick 写入而使同步包达 20 包/秒/人

## 5. GUI 信息行

- [x] 5.1 覆写 `uiInfo` 产出：运行状态行（充灵速率，compact 可见 + 精确值 tooltip）、缓存存量/上限诊断行、由来诗 lore 行、未启动行、缓存断供行（红字 `0xFFB22222`，措辞参照 `kagutsuchi.stalled`）
- [x] 5.2 覆写**按查看者**的 `uiInfo(viewer)`：仅当查看者本人在浴区内且 `temperLevel > match.level()` 时追加门控提示行；可见行为 ≤ 11 汉字短标签，完整句子（含玩家名/双方阶级）走 `InfoLine.tipped` 的 tooltip
- [x] 5.3 `zh_cn` 新增全部键：状态行/诊断行/未启动/断供/lore_1..5/门控提示可见行/门控提示 tooltip；门控句 MUST 用带占位参数的独立键，MUST NOT 拼接键前缀
- [x] 5.4 `python tools/lang_audit.py` 退出码 0

## 6. 渲染态通道

- [x] 6.1 `RitualRenderState` 新增 `KIND_REIYOKU = 9` 常量及字段语义注释（`enabled` 唯一门控；`tier`=结构等级；`minY`/`maxY`=结构 Y 范围；`linkPos`/`movingMask` 不使用）
- [x] 6.2 `RitualCoreBlockEntity.buildRenderState()` 新增灵浴分支：kind、`enabled`、`match.level()`、`boundsMinY`、`boundsMaxY`，其余字段留空
- [x] 6.3 `RitualCoreRenderer.getRenderBoundingBox()` 为本 kind 单独开分支，水平半径覆盖占地域（最大 ±14 + 余量）、垂直覆盖 `boundsMaxY` 偏移

## 7. 客户端特效

- [x] 7.1 经 `tools/gen_tex.py` 产出水面贴图（现有 fx 贴图无水），接入资源与纹理常量
- [x] 7.2 `RitualFxLayout` 新增世界无关纯函数：按 pattern 该阶切片取 `y = 核心Y-1` 层的**实际格位**并输出水面布局（局部坐标 + 边缘衰减 + 确定性种子），MUST NOT 取包围盒
- [x] 7.3 `RitualCoreRenderer` 新增 `renderReiyoku` 分支：蓝色水面（加法混合网格，uv 滚动 + 呼吸，包络淡入淡出，占 0.8 格高）+ 浅绿色灵力柱（米字面片，半径 3，高取 `boundsMaxY`）
- [x] 7.4 包络槽位：为灵浴在 `advanceEnvelope` 的槽表中分配新槽（现有 0..3 在用、4/5 被时钟占用），MUST NOT 覆盖既有槽
- [x] 7.5 距离 LOD：超过 `FX_REIYOKU_WATER_LOD_DISTANCE` 时降低水面密度 / 跳过灵力柱
- [x] 7.6 验收：停机 → 淡出无特效；运行中 → 有特效且**与缓存有无无关**；实心方块处不生成水面几何（不穿透发光）

## 8. 调试与验证

- [x] 8.1 `DebugCommands` 新增 `reiyoku` 子命令，输出机读单行：`level` / `capacity` / `inRate` / `stored` / `chargePerSecond` / `cachePerSecond`
- [x] 8.2 静态内核断言：逐阶调用 2.1 的函数并与 spec 表比对（可用既有单测脚手架；若无单测框架则经 8.1 探针实机断言）
- [x] 8.3 `.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'` 通过
- [x] 8.4 `.\tools\gradle_task.ps1 runServer` 加载期无 registry 错误，`/gs_debug reiyoku` 输出可解析
- [x] 8.5 实机矩阵：1/2/5 阶 × （单玩家 / 双人均分 / 玩家已满 / 玩家越阶 / 凡人）五种情形，逐条比对 spec 场景

## 9. 指导书与收尾

- [x] 9.1 撰写灵浴条目文案（短故事 + 引言，同页，`$(br)` 换行语法，MUST NOT 用 `\n`），`zh_cn` 补 `gensokyou.book.entry.ritual.reiyoku_circle.text`
- [x] 9.2 `RitualTierComponent` 新增灵浴分支：阶级页按 `tier` 从 `GensokyouConfig` 现算显示「缓存上限 / 受灵上限 / 充灵速率」，MUST NOT 写公式、 MUST NOT 加「启停型」等标签
- [x] 9.3 `python tools/gen_ritual_book_entries.py` 重生成条目（该仪式会因已能成型被自动捡起），确认逐阶结构页与 `ritual_tier_page` 门槛映射正确
- [x] 9.4 确认无 `loot_page`、无配方页（无配方无战利品表）
- [x] 9.5 `openspec validate add-reiyoku-ritual --strict` 通过

## 10. 复审修补（实机反馈后追加）

- [x] 10.1 **修渲染态 Y 语义**：`minY`/`maxY` 是**绝对世界 Y**（`structureMinY/MaxY` 同源），BER 局部系原点为核心方块 → 客户端一律先减 coreY。漏这一步会把水面与柱底画到结构上方数十格，表现为"完全没有特效"。已修 `renderReiyoku` / `renderBathPillar` / `getRenderBoundingBox`，并在 `RitualRenderState.KIND_REIYOKU`、BE 分派注释与 spec 中改正错误描述
- [x] 10.2 校验其余高度派生（灵力柱高度取 `maxOffset - minOffset`，差值与原点无关，改动前后一致）
- [x] 10.3 GUI 追加「在浴人数」行：可见行只放人数，tooltip 给出每人速率 + 名单（上限 4 名 + 省略号）；复用主循环同一判据，BE 侧存瞬态人数与名单文本
- [x] 10.4 在浴人数变化时立即补推快照（`sendRitualInfoToViewers`），不等统一 1Hz 心跳
- [x] 10.5 lang 补 `reiyoku.bathing` / `reiyoku.bathing.tip`，`lang_audit` 退出码 0

## 11. 第二轮实机反馈：特效观感返工

- [x] 11.1 **修水面 alpha ×255 缺失**：配置项是 0..1 不透明度，vertex alpha 要 0..255 —— 原实现直接 `(int)(0.55f * env)` 截成 0，整片水面全透明（"只有一小块"）。现统一 `255 * opacity`
- [x] 11.2 **修水面边缘衰减过强**：原按"离池心距离/最大离心距离"归一化，137 格池子的外缘 2/3 按比例变暗，读起来只剩中心一小块。改为 chamfer 两遍距离变换求"离最近非水面格的切比雪夫距离"，**只让最外 1~2 圈渐隐、池内均匀**（新增 `FX_REIYOKU_WATER_RIM_FADE`）
- [x] 11.3 **修距离变换方向**：距离源必须是<b>非水面格</b>（含 bbox 外围补的一圈），格位置 INF。原实现反过来初始化（格位 0）导致每格都读成"贴边"，整池等亮度衰减。已加离线断言（池心格占比 ≥ 80%）
- [x] 11.4 灵力柱降亮：`FX_REIYOKU_PILLAR_ALPHA` 0.5 → 0.16，且内部固定系数 110/150 改为随不透明度缩放
- [x] 11.5 灵力柱可见高度：默认"顶端淡出到 0"收成尖端，而柱在亭内被屋顶截断只露下段 → 可见段进一步缩成"一点点"。新增 `FX_REIYOKU_PILLAR_TOP_ALPHA`（默认 0.55，顶缘留存在感）与 `FX_REIYOKU_PILLAR_HEIGHT_RATIO`（默认 1.0，可 >1 让柱高出屋顶从屋外可见）；`FxGeometry.emitCrossPlanes` 加带顶缘 alpha 的重载（原签名行为不变）
- [x] 11.6 新增 `ReiyokuBathSurfaceTest`（5 例）离线锁死占地格数 137/137/233/433/433、池心不衰减、edge ∈ [0,1]、确定性、缺失阶级返回空布局

## 12. 第三轮实机反馈：光柱 → 灵气

- [x] 12.1 确认硬事实：`additiveGlow` **深度测试开启**（`DanmakuRenderTypes` javadoc 原文「深度测试仍开启，因此发光不会穿透墙体」）。浴亭屋顶 `核心Y+3`、屋脊 `+4` 仅留中心 1×1 烟孔 → 半径 3 的硬光柱在屋外必被整片遮住，「让光柱看起来到顶」物理上做不到，除非拆屋顶
- [x] 12.2 采纳用户第二条思路：**保留「到建筑高度上限」，把表现从「光柱」改为「灵气」**。高度改为 `maxOffset - baseY`（水面基准面 → 结构最高点），`FX_REIYOKU_PILLAR_HEIGHT_RATIO` 默认 1.0 即恰好到顶
- [x] 12.3 灵气质感：贴图由硬边 `bolt_glow` 换成径向柔和的 `spirit_mist`；**去掉亮芯双层**改为单层；面片 4→3；底/顶 alpha 0.16/0.55 → **0.5/0.25**（软雾径向衰减故底值需更高，顶值低 = 升腾变薄）；滚动 0.12→**0.03**（慢速上飘读作"升腾"）
- [x] 12.4 下线 `FX_REIYOKU_PILLAR_CORE_RATIO`（去亮芯后成为死配置项 —— 死旋钮比没有旋钮更糟）
- [x] 12.5 同步改写 design.md D12 与 spec 的特效要求，写明「深度测试开启」这条物理约束与「雾而非光柱」的理由，避免后续照旧描述重做成硬光柱

## 13. 第四轮实机反馈：米字面片像八角 + 水铺错地方

- [x] 13.1 **八角成因**：`emitCrossPlanes` 让固定朝向面片相交，面片是硬的、棱是硬的，斜视时各面自成一形 → 读作八棱柱。改为 N 片 **camera-facing billboard**（`FxGeometry.emitBillboard`）沿高度堆叠，每片恒正对相机，任意视角只有软边轮廓
- [x] 13.2 **高度到世界顶**：顶面改取 `be.getLevel().getMaxBuildHeight()`（MUST NOT 写死 320，超高世界会截断），底面仍为水面基准面 `minY偏移 + 1`；`FX_REIYOKU_QI_HEIGHT_RATIO` 默认 1.0 = 恰好到顶
- [x] 13.3 **包围盒同步开到世界顶**：渲染态 `maxY` 仅结构最高点（≤16），只按它收盒子会使柱顶落在盒外、视锥剔除把整个 BER 连同整柱灵气剔掉（现象与「完全不显示」无法区分）。水平改为 `max(16, 水面半径 + 2, 灵气张开半径 + 2)`
- [x] 13.4 **循环上升 + 回绕消跳**：整柱按 `FX_REIYOKU_QI_DRIFT`（默认 0.6 格/tick）持续上飘并在顶端回绕；回绕处两端交叉淡入淡出（`edgeBand`），否则高 alpha 片从顶端跳回柱底读作「闪一下」
- [x] 13.5 **高度分布与张开**：`FX_REIYOKU_QI_SPREAD` 默认 0.7（<1 把采样点往上推 → 低处密高处疏）、`FX_REIYOKU_QI_GROW` 默认 1.2（顶为底的 2.2 倍，读作蒸汽扩散），另加轻微横摆避免读作一根直杆；片数默认 10，底 alpha 由 0.5 下调到 0.32（10 片叠加会累积）
- [x] 13.6 `PILLAR_*` 整组更名为 `QI_*`（语义已从「光柱」变成「灵气雾团」），`FX_REIYOKU_PILLAR_PLANES` 与 `..._SCROLL_SPEED` 随面片方案下线
- [x] 13.7 **水铺错地方**：原实现把 `y = 核心Y-1` 层**全部**已声明格位铺成水（137/137/233/433/433），连外圈走道一起铺 —— 实机反馈「不是摆在走廊上」。改为主池 + 外圈院子两块
- [x] 13.8 **主池**：取 `y = 核心Y` 层**未被 pattern 声明**且欧氏距离 ≤ `REIYOKU_BATH_RADIUS`（默认 3）的格位，各阶恒 16 格。取「未声明」而非「最低层全部格位」是因为后者含核心基座与仪式石环（占 `y = 核心Y` 那一格），水画进去会被埋在方块内不可见
- [x] 13.9 **院子**：最低层（`y = 核心Y-1`）中未被声明、且**被已声明格位包围**的空连通块（4 邻域 flood fill 自图外起）。实测 L1–L3 = 0 块、L4/L5 = 4 块 × 79 格 —— 4 阶起出现是由几何**自然导出**的，未按阶硬编码
- [x] 13.10 **包围判定 MUST 在最低层做**：外圈石砖环只在 `y = 核心Y-1` 实心；上一层的环有缺口，在那里 flood fill 只得到 3 个 8~9 格小口袋，4 个院子全丢
- [x] 13.11 合计 16/16/16/332/332 格，水平半径 3/3/3/11/11；轴向通道未声明但**与图外连通**故天然无水，外圈环已声明故天然无水
- [x] 13.12 重写 `ReiyokuBathSurfaceTest`：格数钉 16/16/16/332/332、走廊无水（判据 MUST 用「网格坐标恰为 0」——院子实测跨到 `z = ±1`，用「近中轴 ≤1 格」会误判院子内角）、外圈环无水、院心不衰减、`bathRadius = 0` 时只剩院子、不存在的阶级返回空布局
- [x] 13.13 同步改写 design.md D11 / D12 与 spec 特效要求（含"包围判定必须在最低层"、"顶面取 client level"、"包围盒开到世界顶"三条硬约束）

## 14. 第五轮实机反馈：指导书挤一页 / 院子高 1 格 / 主池偏心 / 灵气全没

- [x] 14.1 **指导书正文拆两页**：`.text` → `.p1` / `.p2`（Patchouli 页列表是静态的，无法运行期增页，故生成期就拆；先例 `seii_circle`）。p1 = 起源 + 供灵，p2 = 泉有等第 + 收束。重跑 `gen_ritual_book_entries.py`，条目由 11 页变 12 页
- [x] 14.2 **主池偏心**（"主核心室里的水看起来不对称了"）：半径原按格心算成 `hypot(x + 0.5, z + 0.5)`，于是 `|−3|` 记作 2.5、`|+3|` 记作 3.5 —— r = 3 时多出的 8 格**全落在 −x/−z 一个象限**。改为按**格坐标距** `hypot(x, z)` 算，得对称的 12 格（3×3 空气袋 + 四轴 4 格）
- [x] 14.3 该错**只在整数半径下暴露**（r = 3.5 恰好对称），故新增 `poolIsFourFoldSymmetric` 做 90°/180°/270° 旋转不变性断言，回归 MUST 用默认 3.0
- [x] 14.4 **院子高 1 格**（"外围 4 池的高度高了 1 格"）：院子处最低层未声明、**无地板**，地面在更低一层，与主池同高会读作"悬空平板架在齐腰的池子旁"。院子底面下调一格（`minY` vs `minY+1`）
- [x] 14.5 `WaterCell` 因此 MUST 携带 `y`；渲染端 `emitBathWater` 改为「逐格底面 + 统一抬升 `yLift`」，**删掉 `baseY` 参数** —— `cell.y()` 已是完整局部偏移，再叠加 `minOffset` 会重复计数把水面顶到结构上方去
- [x] 14.6 **灵气全没**（"灵气效果完全没了"）根因一：顶面取世界建筑上限后核心在 y≈64 时柱高约 **256 格**，而片数是**固定 10** 片 + 高度指数 **0.7** —— 最靠下的一片落在 y≈33（亭顶才 +4），其余 9 片相隔 30 余格挂在天上，亭内空无一物。改为**按固定垂直间距推导片数**（`count = 柱高 ÷ FX_REIYOKU_QI_SPACING`，默认 4 格 → 约 64 片，另设 `FX_REIYOKU_QI_MAX_SPRITES` 封顶成本）
- [x] 14.7 根因二：**指数方向搞反**。`y = 底 + 柱高 × u^k` 中 `k > 1` 才是"低处密、高处疏"；`k < 1` 把采样点往上推。`FX_REIYOKU_QI_SPREAD` 默认 0.7 → **1.0（均匀）**
- [x] 14.8 根因三：**`camRot` 从没在 `render()` 里设过** —— 它只在 `renderOrb` / `renderSummon` 内部赋值，于是本 kind 拿到上一帧残留值或单位四元数，billboard 恒朝 +Z，斜看时同样读作"消失"，且现象随 BER 渲染顺序变化。提到 `render()` 分发之前统一设，两处内部赋值删除（单一数据源）
- [x] 14.9 片数由 10 涨到约 64，叠加量大幅上升 → `FX_REIYOKU_QI_ALPHA` 默认 0.32 → **0.15**；间距固定即密度固定，总观感浓度与柱高无关，单片 alpha 无需再随片数补偿
- [x] 14.10 顺手修文档页：guide-book 生成器本来就支持 `.pN` 拆页（`intro_text_keys`），是灵浴只写了 `.text` 才挤在一页
- [x] 14.11 更新 `ReiyokuBathSurfaceTest`：格数钉 12/12/12/328/328，新增 `poolIsFourFoldSymmetric` 与 `courtyardsSitOneBlockLowerThanThePool`
- [x] 14.12 同步改写 design.md D11 / D12 与 spec（新增「主池四向对称」「两块底面差一格」「片数按柱高推导」「指数方向」「相机朝向每帧统一设置」五条约束）

## 15. 第六轮实机反馈：灵气一股一股像烟囱 / 横截面积要等于充灵范围

- [x] 15.1 **「一股一股」成因一：间距远大于雾团尺寸**。半宽 1（2 格高）配 4 格**绝对**间距 —— 横向 2 格、垂直隔 4 格，相邻片根本不相接。绝对间距与雾团尺寸脱钩，改宽改高都不跟随
- [x] 15.2 **成因二：柱身本身就是根细管**。绝对半径 1.0 = 2 格宽，塞在 6 格净空的浴亭里 = 烟囱
- [x] 15.3 **间距改为雾团高度的比例**（`FX_REIYOKU_QI_SPACING_RATIO`，默认 0.25）→ 每处约 4 片重叠；雾团变大则间距同比变大，光滑度与尺寸解耦。删掉绝对间距 `FX_REIYOKU_QI_SPACING`
- [x] 15.4 **横截半径绑到充灵半径**：`FX_REIYOKU_QI_RADIUS`（绝对 1.0）→ `FX_REIYOKU_QI_WIDTH_RATIO`（`REIYOKU_BATH_RADIUS × 1.0` = 3.0）。两个独立旋钮迟早漂移，漂移后柱身比浴区窄又变回烟囱
- [x] 15.5 **竖向拉伸解耦**（`FX_REIYOKU_QI_TALL`，默认 1.8，只拉高不拉宽）：垂直重叠显著变密而横截面仍严格等于充灵半径。否则要更光滑只能加宽，那会破坏用户要求的横截面积 —— 唯一能同时满足两条需求的关键
- [x] 15.6 **摆动改为沿高度连续**：相位原按片序号取相（`i * 1.7`），片数上百时相邻片反向摆动 = 杂乱喷溅；改按 `u`（0..1 高度比例）取相，整柱同相位移动，读作一股上升的气
- [x] 15.7 片数 64 → **95**；`FX_REIYOKU_QI_MAX_SPRITES` 96 → **192**，且默认参数刻意不触发封顶（封顶会拉大实际间距、重叠变少、又变回"一股一股"）
- [x] 15.8 半径放大 3 倍、竖向拉长 1.8 倍 → 累积量大幅上升，`FX_REIYOKU_GROW` 1.2 → 0.8、`FX_REIYOKU_QI_ALPHA` 0.15 → **0.14**（仍须很低：观感由重叠累积而非单片可见）
- [x] 15.9 渲染包围盒的灵气余量改用派生半径（`REIYOKU_BATH_RADIUS × WIDTH_RATIO × (1 + GROW)`）
- [x] 15.10 同步改写 design.md D12 对照表（加第五轮列）与 spec（新增「横截面积 SHALL 等于充灵范围」「相邻雾团 MUST 显著重叠」「间距 SHALL 是比例」「摆动 SHALL 沿高度连续」四条约束 + 2 个 Scenario）

## 16. 归档前复测发现：越阶提示 %s 未替换 + 充灵看着仍按秒

- [x] 16.1 **`tier_denied` 的 `%s` 未替换**：lang 值带两个 `%s`，而可见行传 `new String[0]` —— 实机直接显示 `"泉等第不足（%s > %s）"`。按本仓库惯例（`kanayamahiko.summary` 等可见行都传参）补上实参
- [x] 16.2 **tooltip 实参顺序也错**：原来把玩家名塞进第一个实参，而 tip 键是「你的层级 %s 高于浴所等第 %s」，于是「你的层级」显示成玩家名、「高于浴所等第」显示成结构阶，玩家层级根本没出现。改为 `[temperLevel, match.level]`
- [x] 16.3 注意**两处顺序相反**：可见行是「浴所等第 > 玩家等第」，tooltip 是「玩家等第 > 浴所等第」
- [x] 16.4 **充灵看着仍按秒的根因**：`setQuiet` 只写服务端，客户端要等统一 1Hz 快照心跳才看到新值 —— 服务端每 tick 都在涨、玩家眼里是"按秒补"。而本 mod 的常规回灵本身就是 1Hz 整点跳（`ModAttachments.tickRegen`：`tickCount % 20 == 0` + `regenBuffer` 攒零头），灵浴跟着它一起变成 1Hz
- [x] 16.5 **修正 D6**（原决策「静默写入 + 1Hz 同步」是自欺：只降同步不降写入，正是它想避免的现象）。改为充灵期间按 `REIYOKU_CHARGE_SYNC_TICKS`（默认 1 = 每 tick）补同步，离池即停；开销只落在在浴者 ×cadence（1 阶即一人 20 包/秒、约 7 字节/包）。间隔做成配置项，20 = 退回 1Hz，给在意包数的服主留退路
- [x] 16.6 **查过但不是 bug**：`chargeStep` 生产侧传 `cachePerTickFixed`（缓存点数）而单测也用 `RATIO=10` 折算同一口径，两侧单位一致；且 `cachePerSecond = chargePerSecond × cachePerSpirit` 使二者恒等，spirit 速率仍精确等于 `标准池 × 1%`。不需改动，仅在代码注释里点明
- [x] 16.7 **无法再进一步的原因**：客户端 HUD 是整点语义（`Math.round(data.current())` → `SpiritPowerClientState.current` 为 int，供 0~5 槽位）。1 阶 0.5 点/tick 最快每 2 tick 跳 1 格，调小同步间隔低于 2 无效。要亚点平滑须把共享 int 通道改 float，会波及全 mod 的回灵/飞行/超人类阶显示 —— 超出本变更范围
- [x] 16.8 新增 `ReiyokuLangContractTest`（4 项）钉死占位符契约：两键各 2 个 `%s`、顺序相反、无参键不得含占位符、不得有畸形占位符、键须齐备。`lang_audit.py` 只验证键是否存在，查不出占位符/实参不匹配
- [x] 16.9 同步改写 design.md D6（标注原决策已被推翻并记下教训）与 spec 同步要求 + 门控提示占位符契约
