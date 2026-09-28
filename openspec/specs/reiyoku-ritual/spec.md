# reiyoku-ritual Specification

## Purpose
TBD - created by archiving change add-reiyoku-ritual. Update Purpose after archive.
## Requirements
### Requirement: 灵浴行为注册与启停门控
模组 SHALL 为 `gensokyou:reiyoku_circle` 注册启停门控的充灵行为，实现 `RitualBehavior` 并置于 `RitualBehaviors` 注册表。充灵逻辑 SHALL 走 `serverTick`（受 `enabled` 门控），与 pattern 已声明的 `toggleable: true` 一致。行为 MUST NOT 覆写 `handlesStartViaUiAction`（非会话型，不绑定启动者），`usesCoreSocket` 保持默认 `true`。行为 SHALL NOT 声明任何祭品要求或配方（pattern 无祭品台、无配方）。

#### Scenario: 结构成型后可充灵
- **WHEN** `reiyoku_circle` 结构成型、核心处于 `enabled` 态，且有合格玩家位于浴区内
- **THEN** 玩家灵力池每 tick 增长

#### Scenario: 停机即停止充灵
- **WHEN** 玩家点击停止使核心 `enabled = false`
- **THEN** 浴区内玩家的灵力池不再因本仪式增长，停机前后的池值保持不变

#### Scenario: 非会话型：任何人可用
- **WHEN** 甲启动仪式后，玩家乙（未参与启动）走进浴区
- **THEN** 乙同样被充灵

#### Scenario: 仪式未启动
- **WHEN** 核心 `enabled = false` 且浴区内有玩家
- **THEN** 无任何抽取与注灵发生

### Requirement: 浴区判定
浴区 SHALL 为以核心方块原点为圆心的柱体：水平方向玩家方块坐标与核心方块坐标的**欧氏**水平距离 MUST NOT 超过配置基项 `REIYOKU_BATH_RADIUS`（默认 3 格）；垂直方向玩家**脚底方块** Y MUST 落在闭区间 `[核心Y, 核心Y + REIYOKU_BATH_HEIGHT]`（默认 2 格）内。判定 MUST 使用玩家脚底方块坐标（`BlockPos` 语义），MUST NOT 使用 AABB 包围盒。垂直判定 MUST NOT 采用 `|Δy| ≤ H` 的双向形式。

#### Scenario: 站立于池中
- **WHEN** 玩家站在核心同层、欧氏水平距离 2.83 格处（仪式石环顶面），脚底 Y 等于核心 Y
- **THEN** 该玩家位于浴区内

#### Scenario: 半径外不充灵
- **WHEN** 玩家欧氏水平距离为 3.5 格且高度满足
- **THEN** 该玩家不在浴区内，不被充灵

#### Scenario: 挖穿台基不被纳入
- **WHEN** 玩家挖穿 `y = 核心Y-1` 台基并站在 `y = 核心Y-2`
- **THEN** 该玩家不在浴区内（`|Δy| ≤ 2` 的双向读法会错误纳入，此处 MUST 拒绝）

#### Scenario: 跳跃仍在区内
- **WHEN** 玩家在池中跳跃，脚底 Y 为 `核心Y + 1`
- **THEN** 该玩家仍在浴区内

#### Scenario: 屋顶之上不被纳入
- **WHEN** 玩家站在 `y = 核心Y + 3`（屋顶层）
- **THEN** 该玩家不在浴区内

### Requirement: 玩家阶级门控
行为 SHALL 以玩家超人类阶级 `SpiritPowerData.temperLevel` 为唯一判据：当 `玩家阶级 > 结构等级（RitualMatch.level()）` 时，MUST NOT 对该玩家注灵，且 MUST NOT 为其消耗任何缓存。`玩家阶级 ≤ 结构等级` 时 SHALL 正常注灵。阶级 0（凡人）玩家因灵力池上限为 0，其注灵 SHALL 为无任何池变化的 no-op（`player-spirit-attributes` 硬约束）。

#### Scenario: 低阶结构拒绝高阶玩家
- **WHEN** 结构等级为 1，玩家阶级为 3，且玩家位于浴区内
- **THEN** 该玩家灵力池不增长，且本 tick 缓存抽取量不因其减少

#### Scenario: 同阶正常充灵
- **WHEN** 结构等级为 3，玩家阶级为 3，玩家位于浴区内
- **THEN** 该玩家被正常注灵

#### Scenario: 高阶结构可服务低阶玩家
- **WHEN** 结构等级为 5，玩家阶级为 1，玩家位于浴区内
- **THEN** 该玩家被注灵，且速率取 5 阶标准而非其自身阶级

#### Scenario: 凡人不被注灵
- **WHEN** 阶级 0 玩家（灵力池 0/0）位于浴区内
- **THEN** 其灵力池保持 0/0，无任何变化

### Requirement: 充灵速率按结构等级取值
充灵速率 SHALL 由**结构等级**对应的玩家阶级标准最大灵力值决定，SHALL NOT 由玩家自身阶级决定。标准最大灵力值 SHALL 取配置基项 `REIYOKU_TIER_MAX_SPIRIT` 的逐阶表（默认 `[1000, 10000, 100000, 1000000, 10000000]`，下标 0 对应 1 阶）。充灵速率（灵力/秒）SHALL 等于 `标准最大灵力值(结构等级) × REIYOKU_CHARGE_PERCENT`（默认 0.01）。每 tick 的灵力增量 SHALL 为 `速率 ÷ 20`，MUST NOT 以“每秒一次性发放”实现。充灵速率 MUST NOT 按玩家当前池上限的百分比动态计算。

默认配置下的逐阶取值：

| 结构等级 | 标准池 | 灵力/秒 | 每 tick 灵力 | 每 tick 缓存 |
|---|---|---|---|---|
| 1 | 1,000 | 10 | 0.5 | 5 |
| 2 | 10,000 | 100 | 5 | 50 |
| 3 | 100,000 | 1,000 | 50 | 500 |
| 4 | 1,000,000 | 10,000 | 500 | 5,000 |
| 5 | 10,000,000 | 100,000 | 5,000 | 50,000 |

#### Scenario: 逐 tick 发放而非每秒跳变
- **WHEN** 1 阶结构、单个 1 阶玩家在浴区内连续观测 40 tick
- **THEN** 池值每 tick 增长 0.5，40 tick 累计约 20，SHALL NOT 表现为每 20 tick 一次性 +10

#### Scenario: 从零充满约百秒
- **WHEN** 1 阶玩家从空池在 1 阶浴池中持续受灵
- **THEN** 约 100 秒（2000 tick）后达到满池

#### Scenario: 高阶结构秒满低阶玩家
- **WHEN** 5 阶结构、1 阶玩家从空池进入浴区
- **THEN** 其在极短时间内（远少于 100 秒）达到满池，因速率取 5 阶标准（100,000/秒）

#### Scenario: 速率不随玩家池浮动
- **WHEN** 玩家通过装备词条把有效池上限抬高到标准值的 1.5 倍
- **THEN** 充灵速率保持不变（仍取结构阶标准），仅充满耗时相应变长

### Requirement: 缓存换算与比例扣费
行为 SHALL 按 `REIYOKU_CACHE_PER_SPIRIT`（默认 10）点缓存兑换 1 点玩家灵力。每 tick 的注灵 MUST 走核心的**普通** `extract` 通道并使用其**实际返回值**折算为灵力增量（`增量 = 实取缓存量 ÷ REIYOKU_CACHE_PER_SPIRIT`），即缓存不足时按比例少给，MUST NOT 拒绝或回退为满额发放。扣费 MUST NOT 走 `extractRouted`（否则被自身 `inRate` 账本误截）。缓存耗尽导致本 tick 实取为 0 时，行为 SHALL 停止本 tick 全部注灵，且 MUST NOT 改写仪式 `enabled` 状态。缓存恢复后 SHALL 自动恢复注灵。

#### Scenario: 满额扣费
- **WHEN** 缓存充足且本 tick 份额为 5
- **THEN** 实取 5 点缓存，玩家获得 0.5 点灵力

#### Scenario: 缓存尾部按比例折算
- **WHEN** 本 tick 份额为 5 但缓存仅余 3
- **THEN** 实取 3 点缓存，玩家获得 0.3 点灵力（不浪费尾部，也不拒绝）

#### Scenario: 缓存耗尽即停充且不改开关
- **WHEN** 缓存为 0 且核心 `enabled = true`
- **THEN** 无玩家获得灵力，核心 `enabled` 仍为 `true`

#### Scenario: 缓存恢复后续充
- **WHEN** 缓存耗尽停充后，万象共鸣向该核心注入灵力使缓存回补
- **THEN** 无需改变开关状态，玩家自动恢复受灵

#### Scenario: 扣费不走路由通道
- **WHEN** 审查本行为的缓存抽取调用点
- **THEN** 使用普通 `extract` 而非 `extractRouted`

### Requirement: 多人均分
同一 tick 内位于浴区的合格玩家 SHALL 共享本 tick 的总速率，每人获得 `总速率 ÷ 合格人数`。合格人数 SHALL 为同时满足以下三条的玩家数：位于浴区内、`玩家阶级 ≤ 结构等级`、且**当前灵力 < 有效上限**。已满池玩家 MUST NOT 计入分母，且 MUST NOT 消耗缓存。分母为 0 时本 tick SHALL 零抽取。均分 MUST 采用进位拆分（累计器口径），使长时间聚合后的总发放量精确等于总速率，MUST NOT 因逐 tick 整除而系统性偏慢。

#### Scenario: 两人平分
- **WHEN** 两名满级未满池的 1 阶玩家同时位于 1 阶浴池内
- **THEN** 各获得 5 灵力/秒（总速率 10/秒的一半），各自充满耗时约 200 秒

#### Scenario: 已满池者不稀释他人
- **WHEN** 玩家甲已满池、玩家乙未满池，两人同时位于 1 阶浴池内
- **THEN** 乙仍获得完整 10 灵力/秒，甲不消耗任何缓存

#### Scenario: 越阶者不稀释他人
- **WHEN** 1 阶浴池内有玩家甲（1 阶、未满）与玩家乙（3 阶、越阶）
- **THEN** 甲获得完整 10 灵力/秒，乙不消耗缓存

#### Scenario: 均分不因整除丢量
- **WHEN** 3 名合格玩家在 1 阶浴池内长时间受灵
- **THEN** 三人累计获得量之和等于同期总速率应发放量（无系统性亏损）

#### Scenario: 浴区无人时不抽取
- **WHEN** 浴区内无玩家，或所有玩家均已满池
- **THEN** 本 tick 缓存抽取量为 0

### Requirement: 能量入口方向与每 tick 顺序
灵浴为纯消费仪式：行为 SHALL 覆写 `refillsCacheFromSocket()` 返回 `true`，MUST NOT 触发缓存向槽内灵力核心的回流（即 MUST NOT 走 `tickBatteryAutoFill` 方向）。每个 tick 内 SHALL **先**由槽内灵力核心补入缓存（`tickBatteryToCacheFill`）、**后**执行面向玩家的扣费，使同一 tick 内净值不出现负一档。

#### Scenario: 槽核可为本仪式供料
- **WHEN** 槽内插入未满的灵力核心、缓存为 0
- **THEN** 每 tick 缓存先由该核心补入，随后被浴池消耗，玩家获得灵力

#### Scenario: 不把缓存倒回槽核
- **WHEN** 槽内插入未满的灵力核心且缓存充足
- **THEN** 缓存不因本行为而减少（无“缓存→电池”方向的搬运）

#### Scenario: 供料先于耗料
- **WHEN** 同一 tick 内槽核补料与玩家扣费同时发生
- **THEN** 先补后扣，本 tick 结束时缓存不低于“仅扣费不补料”的结果

### Requirement: 端点声明
行为 SHALL 覆写 `spiritInRatePerSecond` 返回 `REIYOKU_BASE_IN_RATE`（默认 1000）按 `12^(结构等级-1)` 缩放后的值，使本仪式可作为受灵汇被万象共鸣连接。`spiritOutRatePerSecond` MUST 保持默认 0（纯消费者，不具备供灵源属性）。受灵上限 SHALL 为静态（仅随结构等级变化），MUST NOT 随时刻、缓存余量或玩家在场与否变化。`spiritInRatePerSecond` 与 `spiritOutRatePerSecond` MUST 分道声明，MUST NOT 因数值相同而合并。

默认配置下：缓存上限 `10000 × 12^(L-1)`、受灵上限 `1000 × 12^(L-1)`、每 tick 缓存消耗 `标准池 ÷ 200`，故缓存从空到满（满速受灵）在**每个阶级恒为 10 秒**，满缓存续航为 `100 × 1.2^(L-1)` 秒（100/120/144/173/207）。

#### Scenario: 取得受灵汇资格
- **WHEN** 成型且缓存可注入
- **THEN** 本仪式出现在万象共鸣的受灵汇候选集中

#### Scenario: 不具备供灵源资格
- **WHEN** 列出某通道的输出源候选
- **THEN** 灵浴（out = 0）不出现在其中

#### Scenario: 受灵上限静态
- **WHEN** 在缓存满、缓存空、有无玩家在场等不同状态下读取 `spiritInRatePerSecond`
- **THEN** 始终返回同一值

#### Scenario: 补满时间阶级恒定
- **WHEN** 1 阶与 5 阶核心分别在满速受灵下从空缓存补满
- **THEN** 两者耗时均约 10 秒

### Requirement: 容量分派与配置基项
`RitualCoreBlockEntity.getCapacity()` SHALL 对灵浴的 patternId 返回 `REIYOKU_BASE_CAPACITY`（默认 10000）按 `12^(结构等级-1)` 缩放后的值，MUST NOT 落回 `DEFAULT_CORE_CAPACITY` 兜底分支。容量、受灵上限、标准池表、充灵百分比、换算比、浴区半径与浴区高度 SHALL 全部定义于 `GensokyouConfig`（COMMON），MUST NOT 硬编码字面量于行为代码。容量与受灵上限因超出 `int` 范围 MUST 使用 `LongValue`。

> 注：`DEFAULT_CORE_CAPACITY` 恰好等于 1 阶目标值，故缺失该分支时 1 阶表现正确、2~5 阶静默偏差。验证 MUST 覆盖 2~5 阶。

#### Scenario: 各阶容量正确
- **WHEN** 分别查询 1~5 阶灵浴核心的 `getCapacity()`
- **THEN** 返回 10,000 / 120,000 / 1,440,000 / 17,280,000 / 207,360,000，2 阶起 MUST NOT 等于 10,000

#### Scenario: 配置可调
- **WHEN** 管理员修改 COMMON 配置中的容量或受灵基项并重载
- **THEN** 行为与核心读取到新值，无需改代码

#### Scenario: 浴区参数可配
- **WHEN** 管理员把浴区半径改为 2
- **THEN** 仅欧氏距离 ≤ 2 的玩家被充灵

### Requirement: 玩家池逐 tick 写入，并在充灵期间逐 tick 同步

行为每 tick 写入玩家灵力池时，MUST 使用不触发客户端同步的写入路径，以免 20 包/秒/人的成本落到**所有**灵力写入路径上。玩家池 `current` 值本身 MUST 每 tick 更新（精度等同 `regenBuffer` 口径），MUST NOT 降为 20 tick 一次结算。

**但充灵期间 MUST 主动按固定 cadence 同步**（`REIYOKU_CHARGE_SYNC_TICKS`，默认 1 = 每 tick），离池即停。**只降同步不降写入是自欺**：静默写入只保证服务端逐 tick 涨，客户端要等统一 1Hz 快照心跳才看到新值，玩家亲眼看到的仍是"按秒补"。写入路径与同步节奏 MUST 一起决定。

cadence SHALL 做成配置项以留退路（20 = 退回 1Hz 行为）。客户端 HUD 为整点语义（`Math.round` 后的 int 槽位），故调至低于 2 tick 不会更平滑；亚点平滑需把共享 int 通道改 float，超出本变更范围。

行为与既有逐 tick 写手（如飞行耗灵）并存时，MUST NOT 产生读-改-写覆盖导致的增量丢失——每个写手 MUST 在自身执行时重新读取当前值。

#### Scenario: 充灵期间池值连续可见
- **WHEN** 玩家在 1 阶浴池内连续受灵
- **THEN** 池值读数逐 tick 连续上升，MUST NOT 表现为每秒跳一次

#### Scenario: 同步开销只落在在浴者
- **WHEN** 无玩家位于浴池内
- **THEN** 行为不产生任何池同步包

#### Scenario: 与飞行耗灵并存不丢量
- **WHEN** 玩家既开启 grace 飞行（逐 tick 扣灵）又位于浴池内（逐 tick 加灵）
- **THEN** 两者的增量同时体现于池值，无任一方向的增量被整体覆盖

### Requirement: GUI 状态行与门控提示
行为 SHALL 通过 `uiInfo` 注入：①一行运行状态（充灵速率，紧凑值可见、精确明细进 tooltip）；②缓存存量/上限诊断行；③**在浴人数行**（可见行只放人数，tooltip 给出每人速率与在浴玩家名单）；④由来诗 lore 行；⑤`enabled = false` 时的未启动状态行；⑥缓存为 0 时的断供状态行（红字，措辞与颜色与迦具土断供行同构）。全部信息行 MUST 遵循 `ritual-gui-info-lines` 的宽度约束：纯文本可见行 ≤ 11 汉字，数值 MUST 经 `InfoLine.compact`、精确值 MUST 走 `InfoLine.tipped` 的 tooltip。

「在浴人数行」SHALL 复用主循环的**同一份**浴者判据（合格且未满池），MUST NOT 在 GUI 侧另立一套。玩家名等无界文本 MUST 下沉到 tooltip；名单列出的名字数 MUST 有上限并在超出时以省略号收尾（tooltip 按模板定行数、不换行，长名单会拉成一条极宽横条），**总数由可见行给出**。在浴人数发生变化时 SHALL 立即补推一次信息快照，MUST NOT 依赖统一 1Hz 心跳（否则玩家进出会让该行最长滞后 1 秒 visibly 错误）。

阶级门控提示 SHALL 覆写**按查看者**的 `uiInfo(viewer)` 重载，仅在查看者本人位于浴区内且 `查看者阶级 > 结构等级` 时追加一行；可见行 SHALL 为 ≤ 11 汉字的短标签，明细 MUST 下沉到 tooltip。含玩家名等动态内容的句子 MUST 使用带占位参数的独立 lang 键，MUST NOT 拼接键前缀。相关 lang 键 MUST 在 `zh_cn` 中齐备。

**lang 值的占位符个数 MUST 与代码实参个数一一对应，且顺序 MUST 匹配**：本条曾同时坏两处 —— 可见行 lang 带两个 `%s` 而代码传 `new String[0]`，实机直接显示未替换的 `"%s > %s"`；tooltip 把玩家名塞进第一个实参，导致「你的层级」显示成玩家名。占位符契约 MUST 由 `ReiyokuLangContractTest` 钉死（`lang_audit.py` 只验证键是否存在，查不出这类不匹配）。

#### Scenario: 状态行显示
- **WHEN** 玩家打开灵浴核心界面
- **THEN** 显示充灵速率（紧凑值）、缓存存量/上限（tooltip 内为精确值）与由来诗

#### Scenario: 在浴名单与人数
- **WHEN** 两名合格玩家位于 1 阶浴池内，玩家打开其核心界面
- **THEN** 可见行显示在浴 2 人，悬浮时 tooltip 给出每人 5 灵力/秒与两名玩家名

#### Scenario: 无人时亦显示
- **WHEN** 浴区内无合格玩家
- **THEN** 可见行显示在浴 0 人，tooltip 名单为"无人"，每人速率为 0

#### Scenario: 名单超长截断
- **WHEN** 浴区内有 6 名合格玩家
- **THEN** tooltip 名单只列前若干名并以省略号收尾，可见行仍显示真实总数 6

#### Scenario: 进池即时刷新
- **WHEN** 玩家在核心界面打开状态下走进浴池
- **THEN** 在浴人数行在 1 秒内更新为含该玩家，无需关闭重开界面

#### Scenario: 已满池者不计入
- **WHEN** 唯一在浴的玩家灵力已满
- **THEN** 在浴人数显示 0（该玩家按主循环口径本就不计入）

#### Scenario: 断供状态可见
- **WHEN** 仪式运行中但缓存为 0
- **THEN** 信息区出现红色断供状态行，玩家能把“缓存空”与“未启动”区分开

#### Scenario: 门控提示只给当事人
- **WHEN** 玩家甲（阶级 3）打开一台 1 阶灵浴核心界面，自身不在浴区内
- **THEN** 不出现门控提示行

#### Scenario: 门控提示可见行不超框
- **WHEN** 玩家乙（阶级 5）位于 1 阶灵浴的浴区内并打开其核心界面
- **THEN** 出现一行门控提示，其可见文本在 `zh_cn` 下不超过 11 汉字，完整句子（含玩家名、双方阶级）在悬浮 tooltip 内

#### Scenario: lang 审计通过
- **WHEN** 运行 `python tools/lang_audit.py`
- **THEN** 退出码为 0，无缺失键

### Requirement: 运行态特效
灵浴 SHALL 新增专属渲染态 kind，由客户端绘制两段表现，MUST NOT 以服务端 `sendParticles` 作为主体载体：

1. **蓝色灵力水面**：自各区域自身底面向上占据 0.8 格高（`REIYOKU_WATER_HEIGHT`，默认 0.8），呈流动感。占地 SHALL 为**主池 + 外圈院子**两块（定义见下），MUST NOT 取包围盒，MUST NOT 铺满外圈走道。
2. **浅绿灵气**：自水面上方升腾、**直到世界建筑上限**的软雾柱，由沿高度堆叠的 **camera-facing billboard** 构成。其**横截面积 SHALL 等于充灵范围**（横截半径 = `REIYOKU_BATH_RADIUS × FX_REIYOKU_QI_WIDTH_RATIO`，默认比例 1.0），且 SHALL 读作**一根连贯的柱**而非一串独立烟团。

**水面占地 SHALL 由同一份 pattern 纯函数推导，MUST NOT 硬编码坐标**：

| 区域 | 定义 | 实测格数 |
|---|---|---|
| **主池** | `y = 核心Y` 层**未被 pattern 声明**、且**格坐标距** ≤ `REIYOKU_BATH_RADIUS`（默认 3）的格位 | 各阶恒 12 |
| **院子** | `y = 核心Y-1`（最低层）中未被声明、且**被已声明格位包围**的空连通块（4 邻域 flood fill 自图外起） | L1–L3 = 0 块；L4/L5 = 4 块 × 79 格 |

约束：

- **包围判定 MUST 在最低层做**。外圈石砖环只在最低层是实心的；上一层的环有缺口，在那里 flood fill 只会得到 3 个 8~9 格的小口袋。院子在 4 阶起才随外圈环出现，MUST 由几何**自然导出**，MUST NOT 按阶硬编码特判。
- **主池 MUST 取"未声明"格位**，MUST NOT 取最低层全部格位：最低层含核心基座与仪式石环（它们占 `y = 核心Y` 那一格），水画进去会被埋在方块内不可见，而只有未声明处才有空气。所得集合恰等于"玩家能站进去泡水"的全部格位。
- **主池 MUST 四向对称**。半径 MUST 按**格坐标距** `hypot(x, z)` 算，MUST NOT 按格心算成 `hypot(x + 0.5, z + 0.5)`：后者把 `|−3|` 记成 2.5、`|+3|` 记成 3.5，r = 3 时多出的格位全落在一个象限，主池读作偏心。此错只在整数半径下暴露，MUST 以默认 3.0 做回归。
- **院子格位在 pattern 中未声明**（不受结构约束、亦无地板），故 MUST NOT 用"pattern 声明了什么"定位；靠"被声明格位包围"反推，边界来源仍是同一份 pattern。
- **两块底面 MUST 相差一格**：主池坐在最低层顶面（`minY + 1`），院子低一格（`minY`）—— 院子处无地板，地面在更低一层。同高会读作"悬空平板架在齐腰的池子旁"，而非下沉一格的水院。逐格底面 SHALL 由布局数据自带，渲染端 MUST NOT 再叠加整体基准 Y。
- **外圈走道与轴向通道 MUST NOT 铺水**。轴向通道未声明但**与图外连通**，包围法天然排除；外圈石砖环已声明，同样天然排除。

两段表现的参数（高度、半径、颜色、雾团间距、上升速率、高度分布指数、张开倍数、不透明度、脉动/呼吸速率）SHALL 全部为 `GensokyouConfig`（COMMON）项，MUST NOT 硬编码。新增水面贴图 SHALL 经 `tools/gen_tex.py` 产出，MUST NOT 使用坐标循环脚本。

水面底面 SHALL 由布局数据自带的逐格 Y 换算得出，客户端 MUST 注意渲染态 `minY` 是**绝对世界 Y**、BER 局部系原点即核心方块原点（MUST NOT 把绝对世界 Y 直接当作局部坐标 —— 会把水面与灵气画到结构上方数十格处，表现为"完全没有特效"）。

**灵气 SHALL 表现为"充满的雾"而非"硬光柱"**：SHALL 使用径向柔和衰减的软贴图（复用 `spirit_mist`）着色，SHALL NOT 叠加高不透明度亮芯层，SHALL NOT 使用硬边渐变光束贴图；顶缘不透明度 SHALL 显著小于底缘（默认 0.25），使灵气升腾时变薄。理由：浴亭有顶（屋顶 `核心Y+3`、屋脊 `核心Y+4`，仅中心留 1×1 烟孔），而加法混合层**深度测试开启**（`ritual-runtime-fx`：不写深度但仍测深度）——硬光柱在屋外必被屋顶整片遮住、只余烟孔一线，读作"只有一点点"；软雾则使亭内读作充满的灵气，而玩家泡在池中时正身处其中。

**灵气 MUST 用堆叠 camera-facing billboard，MUST NOT 用相交面片柱**：相交面片是硬的、棱是硬的，相机斜看时各面自成一形，读作八棱柱而非雾气。改用恒正对相机的 billboard 沿高度堆叠后，任意视角都只有一个软边轮廓，天然读作"一柱上升的雾"。

**雾团片数 MUST 按柱高推导，MUST NOT 是固定值**：顶面取世界建筑上限后柱高可达数百格，固定片数会使最靠下的一片远高于亭顶、其余相隔数十格挂在天上，读作"灵气完全没了"。

**相邻雾团 MUST 显著重叠，MUST NOT 露缝**（否则读作"一股一股、像烟囱"）。为此三条 SHALL 同时成立：

- **垂直间距 SHALL 是雾团自身高度的比例**（`FX_REIYOKU_QI_SPACING_RATIO`，默认 0.25），MUST NOT 是绝对格数。绝对间距与雾团尺寸脱钩：改宽或改高都不会跟随。比例化后雾团变大则间距同比变大，光滑度与尺寸解耦。
- **横截半径 SHALL 绑到充灵半径**（`REIYOKU_BATH_RADIUS × FX_REIYOKU_QI_WIDTH_RATIO`，默认 1.0），MUST NOT 单设绝对值 —— 两个独立旋钮迟早漂移，漂移后柱身比浴区窄又变回烟囱管。
- **雾团 SHALL 允许竖向拉伸**（`FX_REIYOKU_QI_TALL`，默认 1.8，只拉高不拉宽）。这是把「垂直叠得够密」与「横截面积固定」解耦的唯一手段：否则要更光滑只能加宽，而那会破坏横截面积的要求。

**摆动 SHALL 沿高度连续**：相位 MUST 取自高度比例，MUST NOT 取自片序号。片数上百时按序号取相会让相邻两片反向摆动，读作杂乱喷溅而非一根连贯的柱。

整柱 SHALL 循环上升（`FX_REIYOKU_QI_DRIFT`），SHALL 在回绕处两端交叉淡入淡出，否则高不透明度片从顶端跳回柱底会读作"闪一下"。**单片不透明度 SHALL 压得很低**：片数上百且相互重叠，观感由**累积**而成，任何一片都不该自己就看得见。

**片数封顶 SHALL NOT 在默认参数下触发**（封顶会拉大实际间距、重叠变少、又变回"一股一股"）；封顶被触发时该取舍 MUST 在配置注释中写明。

**高度分布指数的方向 MUST 明确**：`y = 底 + 柱高 × u^k` 中 `k > 1` 才是"低处密、高处疏"；`k < 1` 把采样点**往上推**。默认值 SHALL 取 1.0（沿柱均匀）。

**灵气顶面 MUST 取自 client level 的 `getMaxBuildHeight()`**，MUST NOT 写死 320（超高于 320 的世界会在柱顶被截断）。相应地，**客户端渲染包围盒 MUST 一并开到世界建筑上限**：渲染态 `maxY` 仅为结构最高点（偏移 ≤ 16），只按它收盒子会使柱顶落在盒外、视锥剔除把整个 BER 连同整柱灵气一起剔掉，现象与"灵气完全不显示"无法区分。

**相机朝向 MUST 每帧统一设置**：camera-facing billboard 依赖相机旋转四元数，该值 SHALL 在 BER 渲染入口按 kind 分发**之前**写入，MUST NOT 只在部分分支（如百鬼/召唤）内部赋值 —— 否则其余 kind 拿到的是上一帧残留值或单位四元数，billboard 恒朝固定方向，斜看时读作"特效没了"，且现象随渲染顺序变化。

#### Scenario: 灵气横截面积等于充灵范围
- **WHEN** 观察运行中的灵浴，从亭外平视
- **THEN** 灵气柱的横向宽度与浴区充灵半径一致（半径 3 格），MUST NOT 读作亭中一根细管

#### Scenario: 灵气是连贯一柱而非一串烟团
- **WHEN** 沿灵气柱高度方向连续观察
- **THEN** 相邻雾团显著重叠、柱身无可见间隙，整柱读作一股上升的气；MUST NOT 出现间隔的独立烟团

#### Scenario: 灵气到世界顶
- **WHEN** 运行中的灵浴处于 1 阶结构、核心位于 `核心Y = 64`
- **THEN** 灵气自水面上方（相对 Y 0）持续上升至 `getMaxBuildHeight()`，亭内充满、屋外自亭顶穿出，且升高过程不出现"闪一下"；斜看任意角度均读作软边雾柱而非棱柱

#### Scenario: 低阶无院子水
- **WHEN** 灵浴处于 1 阶（外圈无实心环）
- **THEN** 水面仅覆盖主池 12 格，4 个院子位置无水

#### Scenario: 4 阶起出现院子水
- **WHEN** 灵浴处于 4 阶及以上
- **THEN** 水面覆盖主池 12 格 + 4 个院子各 79 格（共 328 格），外圈走道与轴向通道无水

#### Scenario: 主池对称
- **WHEN** 观察运行中的灵浴主核心室
- **THEN** 水面绕核心轴 90° 旋转后与自身重合，四向等宽，MUST NOT 偏向任一象限

#### Scenario: 院子比主池低一格
- **WHEN** 观察 4 阶及以上运行中的灵浴
- **THEN** 4 个院子的水面比主核心室水面低恰好 1 格，读作下沉一格的水院，MUST NOT 与主池齐平

特效门控 SHALL **仅**取 `enabled`：停机即无特效；运行中即有特效，与缓存有无、玩家在场与否**无关**。本 kind MUST NOT 新增渲染态字段承载占地，客户端 SHALL 由 kind 定位 patternId、经 `ClientRitualData` 读取同一份 pattern JSON 的该阶切片本地推导占地；MUST NOT 新增 payload 或逐 tick 广播。客户端渲染包围盒 MUST 为本 kind 单独取值，水平覆盖 ±11 格的水面与张开后的灵气，垂直覆盖到世界建筑上限。

**已确认接受**：灵气位于浴亭内部，屋外被 `y = 核心Y+3` 的屋顶遮住（深度测试开启），仅中心 1×1 烟孔透出一线。实现 MUST NOT 为此增加开口、加法或额外绘制；屋内观感（充满的灵气 + 脚下的灵水）即为设计意图，玩家泡在池中时正身处其中。

#### Scenario: 运行中有水有灵气
- **WHEN** 灵浴处于 `enabled` 态（无论缓存是否为 0、是否有玩家在场）
- **THEN** 客户端绘制蓝色水面与浅绿色灵气

#### Scenario: 停机无特效
- **WHEN** 灵浴被停止（`enabled = false`）
- **THEN** 水面与灵气经包络淡出后消失

#### Scenario: 水面可见不被方块埋没
- **WHEN** 玩家从结构外侧观察运行中的灵浴
- **THEN** 水面呈现在各区域自身底面之上、可见且高 0.8 格，MUST NOT 被该层实心方块完全遮挡

#### Scenario: 水面不穿透外墙
- **WHEN** 结构外围存在实心方块（外环墙、石台、仪器核心）
- **THEN** 实心方块处不生成水面几何，加法混合 MUST NOT 透过方块发光

#### Scenario: 占地随结构等级增长
- **WHEN** 比较 1 阶与 5 阶运行中的水面范围
- **THEN** 5 阶比 1 阶多出 4 个院子（328 格 vs 12 格），水平范围由 ±3 扩至 ±11

#### Scenario: 主体绘制零粒子包
- **WHEN** 一名玩家独自在 5 阶灵浴附近观察满配特效
- **THEN** 水面与灵气全量渲染且该玩家与服务器之间无逐 tick 粒子包

#### Scenario: 调参不重编译
- **WHEN** 修改水面高度或灵气雾团间距等任一 FX 配置项并重载
- **THEN** 运行态表现随之变化，无需改动代码

### Requirement: 调试探针
调试子命令 SHALL 输出机读单行摘要，至少含 `level`、`capacity`、`inRate`、`stored`、`chargePerSecond`、`cachePerSecond` 与结构命中状态，供外部 harness 解析断言。世界无关的速率/容量/换算内核 SHALL 实现为可直接调用的静态函数（先例：`DayCycleGeneratorBehavior.producedPerSecond`、`BafangGuiyuanBehavior.weightedSplit`）。

#### Scenario: 探针输出
- **WHEN** 在已成型灵浴核心附近执行调试子命令
- **THEN** 输出单行，各字段与核心当前值一致

#### Scenario: 静态内核可直调
- **WHEN** 直接调用静态速率/容量函数并传入结构等级 1~5
- **THEN** 返回值与本 spec 的逐阶表逐项吻合

