# watatsumi-fishing-ritual Specification

## Purpose
TBD - created by archiving change add-watatsumi-fishing-ritual. Update Purpose after archive.
## Requirements
### Requirement: 行为注册与持续运行
`gensokyou:watatsumi_circle` SHALL 绑定一个独立的 `RitualBehavior` 实现（MUST NOT 继承既有 `ToolSacrificeBehavior`），并 SHALL 采用启停型（依赖 pattern 的 `toggleable`）。行为 SHALL 在启动（enabled）后**持续**监控祭品台：满足「存在合规钓鱼竿 + 灵力足够 + 冷却已过」三项时执行一次结算，否则 SHALL 保持待机（不自动停机、不报错）。结算 MUST NOT 依赖玩家逐次点击。

#### Scenario: 注册后可被识别
- **WHEN** 服务器加载并匹配到 `watatsumi_circle` 的成型结构
- **THEN** `RitualBehaviors.get(gensokyou:watatsumi_circle)` 返回该行为实例，仪式具备持续运行能力

#### Scenario: 启动后自动连续结算
- **WHEN** 仪式已启动、台上有合规钓鱼竿、灵力充足且冷却已过
- **THEN** 行为自动执行一次结算；冷却结束后若条件仍满足则再次自动结算

#### Scenario: 缺竿待机
- **WHEN** 已启动但台上没有任何合规钓鱼竿
- **THEN** 行为不做任何消耗与产出，保持 enabled，等待钓鱼竿被放入

### Requirement: 钓鱼竿识别与消耗
行为 SHALL 按物品标签 `gensokyou:fishing_rods` 判定合规钓鱼竿（该标签 SHALL 至少含 `minecraft:fishing_rod`）。行为 MUST NOT 因附魔（海之眷顾/饵钓/耐久等）或损耗度改变任何产出或消耗语义。每个结算 SHALL 从全部持有合规钓鱼竿的祭品台中**随机选取一个**并消耗恰好 1 件。

#### Scenario: 按标签识别
- **WHEN** 祭品台上是一根带海之眷顾 III 的钓鱼竿
- **THEN** 它被识别为合规钓鱼竿，且本次结算语义与无附魔钓鱼竿完全一致

#### Scenario: 随机消耗一根
- **WHEN** 台上同时存在多根合规钓鱼竿
- **THEN** 本次结算仅随机消耗其中一根（恰好 1 件），其余原地留守供后续结算

### Requirement: 双独立池与数量公式
每次结算的产出 SHALL 由**两个相互独立的抽取池**构成：钓鱼池与海洋特产池。两者 SHALL 各自独立掷骰、独立聚合。总件数 SHALL 为 `BASE_COUNT × MULT^level`（默认 `5 × 4^L`，即 5/20/80）；其中特产池掷数 SHALL 为 0 阶取 0，1 阶及以上取总量的一半，钓鱼池掷数 SHALL 取余下部分（即 5/0、10/10、40/40）。

#### Scenario: 各阶产出拆分
- **WHEN** 0 阶完成一次结算
- **THEN** 产出 5 件，全部来自钓鱼池，特产池掷 0 次

#### Scenario: 一阶双池各半
- **WHEN** 1 阶完成一次结算
- **THEN** 产出 20 件，其中钓鱼池 10 件、海洋特产池 10 件

#### Scenario: 二阶双池各半
- **WHEN** 2 阶完成一次结算
- **THEN** 产出 80 件，其中钓鱼池 40 件、海洋特产池 40 件

### Requirement: 钓鱼池原版掉落保真
钓鱼池 SHALL 直接使用原版掉落表 `minecraft:gameplay/fishing/fish`、`minecraft:gameplay/fishing/junk`、`minecraft:gameplay/fishing/treasure` 掷骰，SHALL 完整保留其数量、随机损耗、附魔与 NBT（如墨囊 ×10、水瓶、30 级附魔书/弓/竿）。类别选取 SHALL 采用原版根表权重 `fish 85 / junk 10 / treasure 5`，其中 `treasure` SHALL 仅在 1 阶及以上进入池；0 阶 MUST NOT 产出宝藏物品，且 MUST 在移除 treasure 后按 fish/junk 相对权重归一化选取。行为 MUST NOT 依赖「开阔水域」等原版根表条件。

#### Scenario: 0 阶无宝藏
- **WHEN** 0 阶完成一次结算
- **THEN** 5 件产物全部来自 fish/junk 子表，不出现命名牌/鞍/附魔书等宝藏物品

#### Scenario: 一阶解锁宝藏
- **WHEN** 1 阶及以上完成一次结算
- **THEN** 类别按 fish 85 / junk 10 / treasure 5 选取，可能产出宝藏物品，且与是否身处水域无关

#### Scenario: 数量与 NBT 保真
- **WHEN** 抽到原版墨囊条目
- **THEN** 产出 10 个墨囊而非 1 个；抽到宝藏附魔书时产出带附魔的书

### Requirement: 海洋特产池
海洋特产池 SHALL 由数据文件驱动，SHALL 包含海底神殿特产（如海晶碎片/海晶砂粒/海晶灯/暗海晶石/海绵/湿海绵）与珊瑚/海带/海泡菜/海草等原版钓鱼无法获得的海洋特产。该池 SHALL 仅在 1 阶及以上进入产出。池内 SHALL 按各条目权重归一化选取，且各类特产 SHOULD 保持可感知的概率（不得设置到近乎不可见）。

#### Scenario: 一阶解锁特产
- **WHEN** 1 阶及以上完成一次结算
- **THEN** 10（或 40）件特产产物从海洋特产池按权重抽取

#### Scenario: 0 阶无特产
- **WHEN** 0 阶完成一次结算
- **THEN** 不产出任何海洋特产池条目

### Requirement: 等级门控与无维度条件池
宝藏与海洋特产的解锁 SHALL 仅由仪式等级决定（≥1 阶解锁）。行为 MUST NOT 使用下界池/末地池，也 MUST NOT 因祭品台上的凋灵骷髅头或龙首改变产出或消耗。

#### Scenario: 等级即门槛
- **WHEN** 仪式处于 0 阶
- **THEN** 宝藏与海洋特产均不产出，与台上放置任何头颅无关

#### Scenario: 头颅无效果
- **WHEN** 祭品台上放有凋灵骷髅头或龙首
- **THEN** 它们不改变抽取池、不被消耗（亦不构成任何解锁条件）

### Requirement: 二阶埋藏宝藏额外奖励与动态冷却
当仪式为 2 阶时，每次结算 SHALL 以默认 0.1% 的概率触发一次额外奖励：掷**整张**原版 `minecraft:chests/buried_treasure` 掉落表并作为独立一份产出空投（SHALL 含其保底与随机池，如海洋之心等）。触发时该次结算的冷却 SHALL 置为默认 12000 tick（10 分钟）；未触发时 SHALL 置为默认 1200 tick（1 分钟）。额外奖励 MUST NOT 额外消耗灵力，也 MUST NOT 改变主产出件数。

#### Scenario: 未触发走基础冷却
- **WHEN** 2 阶完成一次结算且未触发额外奖励
- **THEN** 本次冷却为 1200 tick（1 分钟）

#### Scenario: 触发走自罚冷却
- **WHEN** 2 阶完成一次结算且触发额外奖励
- **THEN** 除主产出外额外空投一整箱埋藏宝藏物资（含保底海洋之心），且本次冷却为 12000 tick（10 分钟）

#### Scenario: 低阶无额外奖励
- **WHEN** 0 阶或 1 阶完成一次结算
- **THEN** 不进行额外奖励判定

### Requirement: 灵力扣费与待机
每次结算 SHALL 消耗 `SACRIFICE_BASE_SP_COST × SACRIFICE_SP_COST_MULT^level`（默认 `4000 × 4^L`）灵力，来源 SHALL 走仪式既有三段式（槽内灵力核心 → 核心缓存 → 周围储灵兜底）且 MUST 为全有全无。灵力不足时行为 SHALL 保持待机（MUST NOT 消耗钓鱼竿、MUST NOT 自动停机）。启动动作本身 SHALL NOT 扣费。

#### Scenario: 灵力不足不消耗竿
- **WHEN** 三项条件中仅灵力不足
- **THEN** 钓鱼竿不被消耗、无产出，行为保持 enabled 等待灵力恢复

#### Scenario: 扣费随阶级放大
- **WHEN** 2 阶仪式完成一次结算
- **THEN** 扣除 64000 灵力（4000×4^2）

### Requirement: 受灵端点与缓存容量
行为 SHALL 声明大于 0 的受灵速率（可被万象共鸣路由选为受灵汇，复用既有 `SACRIFICE_SPIRIT_IN_RATE`），SHALL NOT 声明供灵速率，并 SHALL 使核心缓存容量按 patternId 与阶级分派（复用 `SACRIFICE_BASE_CAPACITY × 4^level`）。内部扣费 MUST 走普通通道，不得经路由账本接口。

#### Scenario: 可被路由选为受灵汇
- **WHEN** 万象共鸣在范围内寻找接收端
- **THEN** 该仪式因受灵速率 > 0 而进入候选

#### Scenario: 容量随阶
- **WHEN** 仪式升到 2 阶
- **THEN** 核心缓存容量为基值 ×16

### Requirement: 产物空投落点
产出 SHALL 以物品实体形式空投在**核心上方第 1 格、水平半径 3（`RITUAL_OUTPUT_DROP_RADIUS`）的圆盘区域**内：水平落点在该圆盘内均匀随机，高度固定为核心 Y + 1（与被动配方产物及献祭族同口径）。当随机落点所在列在核心上 1 格处被实心方块占用时，SHALL 在圆盘内重掷（至多 8 次）；仍失败时 SHALL 回退核心正上方。落点 MUST 始终位于该"核心上 1 格、半径 3"区域内，MUST NOT 依赖结构上方空间的遮挡高度抬升。主产出与额外奖励 SHALL 采用同一空投规则。产物 MUST NOT 设置拾取前无敌或防护（可被环境销毁）。

#### Scenario: 固定核心上一格
- **WHEN** 一次结算产出
- **THEN** 产物在核心上 1 格高度、半径 3 圆盘内生成，不因上方空间高度而抬升

#### Scenario: 落点被结构方块占用
- **WHEN** 随机落点列在核心上 1 格处是实心结构方块
- **THEN** 重掷到圆盘内未被占用的落点；若圆盘内全部被占则回退核心正上方

#### Scenario: 主产出与额外奖励同规则
- **WHEN** 一次结算同时产出主产出与额外奖励
- **THEN** 两者都在同一"核心上 1 格、半径 3"规则下空投

#### Scenario: 可被环境销毁
- **WHEN** 产物落地后进入岩浆/爆炸等
- **THEN** 产物照常被销毁，无拾取前无敌或防护

### Requirement: 产出光柱特效
行为 SHALL **仅在结算产出那一刻**触发一次巨大光柱特效，运行/充能期间 MUST NOT 触发。光柱 SHALL 经客户端渲染（BER）实现、服务端 MUST NOT 逐 tick 广播粒子包，SHALL 使用专属水蓝色（新增色索引 4），并在短暂持续后消失。

#### Scenario: 仅产出瞬间触发
- **WHEN** 仪式处于已启动但等待条件（缺竿/缺灵力/冷却中）
- **THEN** 不播放光柱

#### Scenario: 水蓝色光柱
- **WHEN** 一次结算成功产出
- **THEN** 核心处升起水蓝色巨大光柱并随后消失，产物从上方落下

### Requirement: GUI 状态行
核心 UI SHALL 显示当前状态（如待机/缺钓鱼竿/缺灵力/冷却中/结算中）、冷却时长（基础值；冷却进行中 SHALL 显示剩余秒数）、祭品台钓鱼竿计数、当前等级、宝藏解锁与特产解锁状态。MUST NOT 显示下界/末地条件行，MUST NOT 溢出信息区宽度（短标签，明细入 tooltip）。

#### Scenario: 状态可见
- **WHEN** 打开已启动绵津见仪式的核心 UI
- **THEN** 信息区显示当前状态行（如「待机：缺少钓鱼竿」）与等级、解锁状态

#### Scenario: 无维度条件行
- **WHEN** 查看绵津见核心 UI
- **THEN** 不出现「下界池/末地池/凋灵骷髅头/龙首」相关信息行

### Requirement: 数据驱动特产池加载
海洋特产池 SHALL 存放于 `data/gensokyou/ritual_special/watatsumi_special.json`（独立 schema，区别于材质驱动的权重表）并经服务端数据重载监听器加载。加载器 SHALL 校验物品 id 存在性与权重有限非负，非法文件 MUST 被拒载并报因、MUST NOT 影响其他文件。修改后执行重载 SHALL 生效，无需重启。

#### Scenario: 热重载生效
- **WHEN** 修改 `watatsumi_special.json` 后执行重载
- **THEN** 后续结算使用新的特产池权重

#### Scenario: 非法文件拒载
- **WHEN** 该文件引用了不存在的物品 id
- **THEN** 该文件被拒载并记录原因，仪式其余部分照常可用

### Requirement: 专属配置旋钮
行为 SHALL 暴露专属配置项（COMMON）：总量基值（默认 5）、每阶数量倍率（默认 4）、基础冷却 tick（默认 1200）、额外奖励冷却 tick（默认 12000）、额外奖励概率（默认 0.001）。灵力消耗、缓存容量、受灵速率与光柱参数 SHALL 复用既有 `SACRIFICE_*` / `FX_PILLAR_*` 共用项；产物落盘半径 SHALL 复用 `RITUAL_OUTPUT_DROP_RADIUS`（默认 3）。

#### Scenario: 数量可调
- **WHEN** 将总量基值改为 10 后重载配置
- **THEN** 0 阶单次总产出变为 10（钓鱼池 10、特产池 0）

#### Scenario: 概率可调
- **WHEN** 将额外奖励概率改为 0
- **THEN** 2 阶永不触发埋藏宝藏额外奖励，冷却恒为 1200 tick

#### Scenario: 落盘半径复用
- **WHEN** 玩家调整 `RITUAL_OUTPUT_DROP_RADIUS`
- **THEN** 绵津见产物落盘圆盘半径随之变化

### Requirement: 调试探针
行为 SHALL 提供世界无关的纯内核（掷数拆分、等级解锁判定、类别权重选择）与调试命令入口，供 `/gs_debug` 打印当前等级、钓鱼竿计数、两池组装结果（含近似概率）与试掷结果，便于回归断言。

#### Scenario: 探针可断言
- **WHEN** 对成型的绵津见执行调试探针
- **THEN** 输出等级、竿计数、钓鱼池与特产池条目及约 %、宝藏/特产解锁状态与一次试掷结果

