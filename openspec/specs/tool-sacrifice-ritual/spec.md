# tool-sacrifice-ritual Specification

## Purpose
TBD - created by archiving change add-tool-sacrifice-rituals. Update Purpose after archive.
## Requirements
### Requirement: 四个献祭仪式的行为注册
四个 pattern `gensokyou:oyamatsumi_circle` / `kukunochi_circle` / `haniyasu_circle` / `kaya_no_hime_circle` SHALL 各绑定一个 `RitualBehavior` 实现；四者 SHALL 共享同一抽象基类（域差异经抽象方法下放）。行为族 SHALL 可通过覆写声明受灵速率与缓存容量，并 SHALL 采用启停型（依赖 pattern 的 `toggleable`）。

#### Scenario: 注册后可被识别
- **WHEN** 服务器加载并匹配到上述任一 pattern 的成型结构
- **THEN** `RitualBehaviors.get(patternId)` 返回对应行为实例，仪式具备持续运行能力

#### Scenario: 启停按钮可见
- **WHEN** 四仪式 pattern 的 `toggleable` 为 true
- **THEN** 核心 UI 出现启停按钮，启动后行为 `serverTick` 被调用

### Requirement: 持续运行与结算节奏
行为 SHALL 在启动（enabled）后**持续**监控祭品台：满足「存在合规工具 + 灵力足够 + 冷却已过」三项时执行一次结算，否则 SHALL 保持待机（不自动停机、不报错）。每次结算后 SHALL 强制等待一个固定冷却期方可再次结算。结算 MUST NOT 依赖玩家逐次点击。

#### Scenario: 启动后自动连续结算
- **WHEN** 仪式已启动、台上有合规工具、灵力充足且冷却已过
- **THEN** 行为自动执行一次结算；冷却结束后若条件仍满足则再次自动结算

#### Scenario: 缺工具待机
- **WHEN** 已启动但台上没有任何合规工具
- **THEN** 行为不做任何消耗与产出，保持 enabled，等待工具被放入

#### Scenario: 冷却限制频率
- **WHEN** 一次结算刚完成
- **THEN** 在冷却期（默认 1200 tick = 60 秒）内不发生第二次结算

### Requirement: 工具识别与消耗
行为 SHALL 按物品标签（镐/斧/铲/锄四类之一）判定合规工具，SHALL 经 `TieredItem#getTier()` 归一工具材质（wood/stone/gold/iron/diamond/netherite）；无法判定材质者 SHALL 归入最低档。行为 MUST NOT 因附魔而改变任何产出或消耗语义。每个结算 SHALL 从全部持有合规工具的祭品台中**随机选取一个**并消耗恰好 1 件。

#### Scenario: 按材质选表
- **WHEN** 祭品台上是一把钻石镐
- **THEN** 本次结算使用 diamond 材质的权重表

#### Scenario: 附魔等同不附魔
- **WHEN** 台上是一把带任意附魔的钻石镐
- **THEN** 其产出表与消耗与不带附魔的钻石镐完全一致

#### Scenario: 随机消耗一把
- **WHEN** 台上同时存在多把合规工具
- **THEN** 本次结算仅随机消耗其中一把（恰好 1 件），其余原地留守供后续结算

### Requirement: 总权重表模型
权重 SHALL 为**相对总权重**而非写死概率。某材质的实际抽取池 SHALL 由「commons 桶 + 该材质 special + 条件池」构成：commons 桶权重 = `max(0, commonsTotal − Σspecial)`（`commonsTotal` 默认 100），桶内按 commons 相对权重分配；条件池（nether / end / gensokyou）为额外加权。每次抽取 MUST 按各条目权重归一化选取 1 件。有材质 Σspecial 达到 `commonsTotal` 时其 commons 桶 SHALL 为空（对应「不出基础块」）。

#### Scenario: 基础块占比
- **WHEN** 木镐 special 为 `coal` 权重 0.5、commonsTotal 为 100
- **THEN** coal 的实际概率约为 0.5%（0.5 / 100），其余分配给 commons 桶

#### Scenario: 桶被清空
- **WHEN** 某材质 special 权重之和达到 100（如下界合金镐）
- **THEN** 该材质不产出任何 commons 桶物品

#### Scenario: 条件池额外加权
- **WHEN** 地狱条件满足
- **THEN** nether 列表条目加入抽取池，与既有条目一起按总权重抽取

#### Scenario: 幻想乡池额外加权
- **WHEN** 摆放了对应信物且其他条件满足
- **THEN** gensokyou 列表条目与既有条目一起按总权重抽取

### Requirement: 数据驱动的权重表加载
权重表 SHALL 存放于 `data/gensokyou/ritual_loot/*.json`（一仪式一文件）并经服务端数据重载监听器加载。加载器 SHALL 校验物品 id 存在性与权重非负性，非法文件 MUST 被拒载并报因、MUST NOT 影响其他文件。文件内容 SHALL 支持顶层 `commons` 与按工具材质的 `special` / `nether` / `end` 列表，并 SHALL 支持顶层 `gensokyou_low` / `gensokyou_high` 幻想乡信物带列表（不按工具材质），并 SHALL 支持每材质可选覆盖 commons。

#### Scenario: 热重载生效
- **WHEN** 修改某 ritual_loot JSON 后执行重载
- **THEN** 该仪式后续结算使用新的权重表，无需重启

#### Scenario: 非法文件拒载
- **WHEN** 某权重表引用了不存在的物品 id
- **THEN** 该文件被拒载并记录原因，其余仪式的权重表照常可用

#### Scenario: gensokyou 信物带可声明
- **WHEN** 某 ritual_loot 文件在顶层声明 `gensokyou_low` / `gensokyou_high` 列表
- **THEN** 加载成功，条目在对应信物条件满足时参与抽取

### Requirement: 逐件独立加权掷骰与产出聚合
每次结算 SHALL 掷 `BASE_COUNT × 4^level` 次（默认 20×4^L），**每次独立**从抽取池选取 1 件。产出 SHALL 按物品种类聚合后拆为不超过最大堆叠数的若干叠，以控制生成实体数量。

#### Scenario: 产出总数与阶级
- **WHEN** 仪式为 0 阶完成一次结算
- **THEN** 产出物品总件数为 20；1 阶为 80、2 阶为 320

#### Scenario: 同类聚合
- **WHEN** 一次结算掷出 20 件同种物品
- **THEN** 按最大堆叠数聚合为尽量少的物品叠，而非 20 个独立实体

### Requirement: 产物空投落点
产出 SHALL 以物品实体形式空投在**核心上方第 1 格、水平半径 3（`RITUAL_OUTPUT_DROP_RADIUS`）的圆盘区域**内：水平落点在该圆盘内均匀随机，高度固定为核心 Y + 1（与被动配方产物同口径）。当随机落点所在列在核心上 1 格处被实心方块占用时，SHALL 在圆盘内重掷（至多 8 次）；仍失败时 SHALL 回退核心正上方。落点 MUST 始终位于该"核心上 1 格、半径 3"区域内，MUST NOT 依赖结构上方空间的遮挡高度抬升。产物 MUST NOT 设置拾取前无敌或防护（可被环境销毁）。

#### Scenario: 固定核心上一格
- **WHEN** 一次结算产出
- **THEN** 产物在核心上 1 格高度、半径 3 圆盘内生成，不因上方空间高度而抬升

#### Scenario: 落点被结构方块占用
- **WHEN** 随机落点列在核心上 1 格处是实心结构方块
- **THEN** 重掷到圆盘内未被占用的落点；若圆盘内全部被占则回退核心正上方

#### Scenario: 可被环境销毁
- **WHEN** 产物落地后进入岩浆/爆炸等
- **THEN** 产物照常被销毁，无拾取前无敌或防护

### Requirement: 条件头颅不消耗
当全部祭品台上存在的凋灵骷髅头数量达到门槛（默认 3）时 SHALL 解锁地狱池；当存在龙首数量达到门槛（默认 1）时 SHALL 解锁末地池。这些头颅 MUST NOT 被结算消耗。

#### Scenario: 三头解锁地狱
- **WHEN** 台上存在 ≥3 个凋灵骷髅头
- **THEN** 本次及后续结算的抽取池包含该仪式的地狱池条目，且这些骷髅头不被消耗

#### Scenario: 龙首解锁末地
- **WHEN** 台上存在 ≥1 个龙首
- **THEN** 抽取池包含该仪式的末地池条目，且龙首不被消耗

#### Scenario: 低阶台位限制
- **WHEN** 0 阶仪式台上已摆 3 个凋灵骷髅头与 1 把工具（共占满 4 台）
- **THEN** 无法再摆龙首；龙首需升阶获得更多祭品台后方可摆入

### Requirement: 灵力扣费与待机
每次结算 SHALL 消耗 `BASE_SP_COST × 4^level`（默认 4000×4^L）灵力，来源 SHALL 走仪式既有三段式（槽内灵力核心 → 核心缓存 → 周围储灵兜底）且 MUST 为全有全无。灵力不足时行为 SHALL 保持待机（MUST NOT 消耗工具、MUST NOT 自动停机）。启动动作本身 SHALL NOT 扣费。

#### Scenario: 灵力不足不消耗工具
- **WHEN** 三项条件中仅灵力不足
- **THEN** 工具不被消耗、无产出，行为保持 enabled 等待灵力恢复

#### Scenario: 扣费随阶级放大
- **WHEN** 2 阶仪式完成一次结算
- **THEN** 扣除 64000 灵力（4000×4^2）

### Requirement: 受灵端点与缓存容量
行为 SHALL 声明大于 0 的受灵速率（可被万象共鸣路由选为受灵汇），SHALL NOT 声明供灵速率，并 SHALL 使核心缓存容量按 patternId 与阶级分派（`基值 × 4^level`）。内部扣费 MUST 走普通通道，不得经路由账本接口。

#### Scenario: 可被路由选为受灵汇
- **WHEN** 万象共鸣在范围内寻找接收端
- **THEN** 该仪式因受灵速率 > 0 而进入候选

#### Scenario: 容量随阶
- **WHEN** 仪式升到 2 阶
- **THEN** 核心缓存容量为基值 ×16

### Requirement: 产出光柱特效
行为 SHALL **仅在结算产出那一刻**触发一次巨大光柱特效，运行/充能期间 MUST NOT 触发。光柱 SHALL 经客户端渲染（BER）实现，服务端 MUST NOT 逐 tick 广播粒子包。光柱 SHALL 在短暂持续后消失，产物伴随其出现并落下。

#### Scenario: 仅产出瞬间触发
- **WHEN** 仪式处于已启动但等待条件（缺工具/缺灵力/冷却中）
- **THEN** 不播放光柱

#### Scenario: 产出播放光柱
- **WHEN** 一次结算成功产出
- **THEN** 核心处升起巨大光柱并随后消失，产物从上方落下

### Requirement: GUI 状态行
核心 UI SHALL 显示该仪式当前状态（如待机/缺工具/缺灵力/冷却中/结算中），SHALL 显示冷却时间（配置的冷却时长；冷却进行中 SHALL 显示剩余秒数），MUST NOT 溢出信息区宽度（短标签，明细入 tooltip）。

#### Scenario: 状态可见
- **WHEN** 打开已启动献祭仪式的核心 UI
- **THEN** 信息区显示当前状态行（如「待机：缺少工具」）

#### Scenario: 冷却时间可见
- **WHEN** 查看核心 UI 信息区
- **THEN** 显示冷却时长（如「冷却 60s」）；若正处冷却中，状态行显示剩余秒数（如「冷却中 37s」）

### Requirement: 幻想乡信物条件池
工具献祭仪式 SHALL 支持幻想乡信物带（第四个条件池），由祭品台上摆放的**信物**解锁：摆放 **指导书** 解锁低阶带（`gensokyou_low`，T1 材）、摆放 **隙间碎片**（`gensokyou:sukima_fragment`）解锁中阶带（`gensokyou_high`，T2 材）；两枚信物同时在场时两带同时解锁。每枚信物 SHALL 至少各 1 件即可解锁对应带，且 **MUST NOT 被结算消耗**。信物条件 SHALL 与头颅条件（nether / end）相互独立，任一满足即解锁其对应池。信物判定 MUST 仅在服务端进行，MUST NOT 依赖客户端状态。海产线（绵津见钓鱼仪式）SHALL 以其特产池的 `gensokyou_low` / `gensokyou_high` 条目承载同类信物带。

#### Scenario: 指导书解锁低阶带
- **WHEN** 祭品台上摆有 ≥1 本指导书且其余条件满足
- **THEN** 本次结算抽取池包含该仪式 `gensokyou_low` 条目，指导书不被消耗

#### Scenario: 隙间碎片解锁中阶带
- **WHEN** 祭品台上摆有 ≥1 个隙间碎片
- **THEN** 抽取池包含该仪式 `gensokyou_high` 条目，隙间碎片不被消耗

#### Scenario: 无信物不解锁
- **WHEN** 祭品台上既无指导书也无隙间碎片
- **THEN** 信物带全部条目不参与抽取，结算结果与原行为完全一致

#### Scenario: 与头颅条件互不干扰
- **WHEN** 台上仅有信物而无骷髅头 / 龙首
- **THEN** nether / end 池不解锁，但信物带照常解锁

