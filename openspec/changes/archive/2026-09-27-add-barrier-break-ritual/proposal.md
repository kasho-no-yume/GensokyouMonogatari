## Why

`barrier_break_circle` 至今是一个断头的占位：pattern 数据已由 `tools/gen_barrier_break.py` 生成，但 `"toggleable": false` 使启停按钮不渲染 → `core.start()` 不可达 → `BarrierBreakBehavior.onStart` 的整条扣费链是死代码；同时 `RitualCoreBlockEntity.getCapacity()` 没有该 pattern 的分支，容量回落到 `DEFAULT_CORE_CAPACITY = 10000`。玩家既建不出这套结构（palette 要求 `#ritual_stones_2_plus`），也拿不到任何行为。

本次把它落成真正的终局仪式：以「网络总灵力门槛」取代一次性扣费——结界核心自身不储核、不接受任何其他供灵途径，全部灵力 MUST 经「八方归元 → 万象共鸣 → 结界」三节点路由链送来，且供灵上限恒等于归元的实际输出；缓存每秒自然流失 150,000，充盈且祭品正确即闩锁开启隙间传送门，永久生效。

同时隙间传送门本体需要一轮完整的观感升级：尺寸放大 2 倍（且不改动默认尺寸）、眼睛开合过渡动画、启动时零网络包的夸张爆炸、紫色环境粒子悬浮，并在幻想乡侧生成同样大小的孪生之门以支持回程。

## What Changes

### 供灵与门槛

- 结界核心 SHALL 暴露灵力核心槽（`usesCoreSocket` 返回 true），槽内灵核以电池→缓存方向按其 `fillRatePerSecond` 补入缓存；SHALL NOT 同时启用反向的缓存→电池（否则构成闭环空转）。
- 结界核心的**路由**受灵速率 SHALL 恒等于附近**已成型八方归元之仪**的聚合输出速率（其被接受托管核的 `fillRatePerSecond` 之和）；不存在合格归元时 SHALL 返回 0。槽核速率 MUST NOT 计入该口径——它不经路由器，计入会虚增汇端预算、挤占其他受灵汇的额度。
- 由此构成「唯一**网络**通路」的结构性保证：万象共鸣的受灵端点筛选要求 `spiritInRatePerSecond > 0`，返回 0 时结界核心对路由器完全不可见。
- 引导书与界面 MUST NOT 点名任何具体品阶的灵力核心作为解法——本仪式是进入幻想乡的**入口**，供灵物必须在此之前可得；断言一个尚不可得的品阶等于给出无效攻略。
- 核心缓存容量 SHALL 为 5,000,000；`getCapacity()` 新增该 pattern 分支。
- 成型期间（`serverPassiveTick`，不受 `enabled` 门控）SHALL 每秒从缓存扣除 150,000；结构失效期间 tick 提前 return，扣费自动冻结。
- 引导书与界面 SHALL 明示「自然流失 150,000/秒」与「建议备总灵力 6,000,000」。

### 五态状态机与闩锁

- 状态 SHALL 为：无供灵网络 / 供灵不足 / 充能中 / 待献祭 / 已开启。供灵不足（归元输出低于流失速率，进度条倒退）SHALL 显式提示，MUST NOT 让玩家误判为 bug。
- 闩锁 SHALL 为核心方块实体上的独立持久化字段，MUST NOT 复用 `enabled`——框架在 `activeMatch` 失效时会把 `enabled` 归零，与「拆普通方块再补回来传送门必须仍是开的」直接冲突。
- `onStructureLost` SHALL 依据 `core.activeMatch()` 分流：`null` = 结构暂时拆毁 → 保留闩锁与传送门；非 null 且 patternId 不同 = 变成别的仪式 → 清闩锁并请求关门。
- 核心方块被破坏 SHALL 触发同一条关门路径，MUST NOT 留下孤儿传送门。

### 祭品

- 祭品规则 SHALL 为**无序计数**：8 个祭品台上**任意** 4 台放 `gensokyou:star_silver`、**任意** 4 台放 `gensokyou:tide_crystal`，台位次序 MUST NOT 影响判定。祭品台本无先后，per-slot 绑定会把「第几号台」漏给玩家。
- 因此本仪式 SHALL **不**声明 pattern `requirements`（该字段只能一条绑一 slot，天然有序），规则由行为侧持有。
- 祭品 SHALL 在传送门开启的瞬间被消耗：每类各扣 4 件（单件不变量，多放份数保留）；闩锁既成后不再校验。
- 界面 SHALL 以**带计数的两行**（如「奉上 3/4」）呈现祭品状态，MUST NOT 呈现为逐台位的匿名 ✓/✗ 清单。
- 祭品正确性 SHALL 以 5 Hz 轮询判定（祭品台放置物品不产生方块更新，核心收不到通知）。

### 隙间传送门

- 渲染尺寸 SHALL 由方块实体上的标量参数驱动，默认 1.0（保持现状），本仪式写入配置值（默认 2.0）。几何缩放为纯 Java 常量，着色器零改动。
- 眼睛 SHALL 具备开合过渡动画：眼睑描边贴图沿中线切为上下两片，各自独立位移，实现真正的手风琴式开合而非整体拉伸。
- 关门 SHALL 为「请求关闭 → 40 tick 闭眼 → 门自行倒数移除」，两扇门各自自删。
- 启动爆炸 SHALL 由门体自身的动画计时器在客户端驱动，MUST NOT 新增任何网络包；非破坏性（可击退），MUST NOT 摧毁仪式自身结构。
- 门体周围 SHALL 常态悬浮紫色粒子，由 BER 本地发射，沿眼睑椭圆轨道公转并缓慢上浮。
- 幻想乡侧 SHALL 生成同样大小的孪生之门，钉在出生点 `(0, 地表, 0)` 旁并横向偏移，使玩家传送抵达时不被立即弹回。

### 配套

- 删除已失去载体的 `power.barrier.barrierSpCost` 配置项。
- 补齐指导书条目（`gen_ritual_multiblock.py` + `gen_ritual_book_entries.py`）、阶级参数页分支、lang 键，并使 `tools/lang_audit.py` 退出码为 0。
- 新增 `/gs_debug barrier` 探针与导出单行 `debugSummary`。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `barrier-break-ritual`: 全面重写。原「右键一次性扣费激活」「结构失效即消门」两条与新设计直接冲突，改为网络供灵门槛、五态状态机、闩锁语义、双门生命周期与祭品门槛。
- `sukima-portal-rendering`: 眼形窗条款追加尺寸参数化与开合动画；新增门体动画状态、关门自删、环境粒子与孪生门实例三组要求。
- `ritual-power-attributes`: 新增「非产灵仪式的网络受灵上限」——允许 `spiritInRatePerSecond` 由外部成型节点动态解析，但须论证其与「供灵上限须静态」红线的关系（详见 design.md D3）。
- `ritual-lifecycle`: 「图案直接切换的清理与成型回调」追加闩锁类持久化状态的分流要求。
- `ritual-offerings`: 「祭品要求声明」明确 per-slot 绑定**仅适用于有序祭品**；无序祭品 MUST 由行为侧以计数实现（本仪式即首个此类）。另新增「轮询式祭品门槛」与「闩锁仪式的祭品消耗」两条。
- `ritual-gui-info-lines`: 「行为自主信息行通道」追加五态信息行的宽度与语义约定。
- `guide-book`: 「仪式条目内容规范」追加结界破坏仪式的条目与门槛写法（供灵链三节点 + 自然流失 + 备料量）。

## Impact

- **核心方块实体：** 新增闩锁字段与其 save/load 分支；`getCapacity()` 新增 pattern 分支。既有存档缺字段时按「未闩锁」处理，向后兼容。
- **核心方块：** `onRemove` 追加传送门清理（跨维度，含幻想乡侧）。
- **隙间方块实体：** 由无数据无 ticker 升为持有动画状态（开启进度 / 关闭倒计时 / 尺寸标量），新增服务端 ticker 与 `getUpdateTag`/`getUpdatePacket`。
- **渲染：** `SukimaPortalRenderer` 几何参数化；`SukimaPortalQuads` 新增通用 UV 子区间参数（不得引入 `SukimaBlockEntity` 专属依赖）；`sukima.py` 输出上下两张贴图。
- **行为：** `BarrierBreakBehavior` 重写；`RitualBehaviors` 已有注册无需改。
- **数据：** `barrier_break_circle.json` 追加 `requirements`；`ritual_stones_2_plus` 结构要求保持 `tiers: [2]` 不变。
- **依赖（不阻塞实现，但阻塞实机验证）：** `ritual_stone_2` 当前为临时占位配方、`sukima_fragment` 无来源，分别由 `add-ritual-stone-higher-tier-recipes` 与 `add-sukima-fragment-source` 两个占位变更记录。
- **依赖：** `tune-resonance-relay-radius` 放宽候选发现半径至 ±40，2 阶三核夹击布局依赖之。
