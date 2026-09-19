# shujou-yoroku-ritual Specification

## ADDED Requirements

### Requirement: 行为注册与启停周期运行
`gensokyou:shujou_yoroku_circle` SHALL 绑定一个 `RitualBehavior` 实现（MUST NOT 实现 `SpiritBank`），并 SHALL 采用启停型（依赖 pattern 的 `toggleable`）。行为 SHALL 仅在核心 `enabled` 时运行，按固定周期反复结算，结算之间 SHALL 保持运行态（不停机、不报错）。结算 MUST NOT 依赖玩家逐次点击。

#### Scenario: 注册后可被识别
- **WHEN** 服务器加载并匹配到 `shujou_yoroku_circle` 的成型结构
- **THEN** `RitualBehaviors.get(gensokyou:shujou_yoroku_circle)` 返回该行为实例，仪式具备周期运行能力

#### Scenario: 未启动不结算
- **WHEN** 结构成型但未启动（`enabled=false`）
- **THEN** 行为不执行任何扫描、扣费与产出

#### Scenario: 启动后自动按周期反复结算
- **WHEN** 已启动且条件满足
- **THEN** 行为执行一次结算并进入冷却；冷却结束后若条件仍满足则再次结算

### Requirement: 典籍识别、满态门槛与去重
行为 SHALL 遍历结构内全部祭品台，仅识别 `gensokyou:codex_of_beings` 中**已收容完成**（`count == 20`）的典籍，SHALL 按典籍记录的 `species` 去重（同类型多本视作一个），MUST NOT 消耗典籍。空书、未满（count<20）、无 species、species 无法解析为已注册实体类型者 SHALL 被忽略且不产生消耗。

#### Scenario: 未满典籍被忽略
- **WHEN** 祭品台上是一本记录 19 只僵尸的典籍
- **THEN** 该典籍不计入有效集合，不参与成本与产出

#### Scenario: 满态典籍被识别
- **WHEN** 祭品台上是一本记录满 20 只僵尸的典籍
- **THEN** 僵尸作为有效 species 计入集合

#### Scenario: 重复类型去重
- **WHEN** 台上分别有满 20 的僵尸典籍 3 本与满 20 的骷髅典籍 1 本
- **THEN** 有效 species 数为 2（僵尸、骷髅），而非 4

#### Scenario: 典籍不被消耗
- **WHEN** 完成任意次数结算
- **THEN** 祭品台上的典籍保持原样（数量与数据组件不变）

### Requirement: 固定周期结算
每次成功结算 SHALL 使其后进入默认 1200 tick（1 分钟）的冷却（由 `shujouCycleTicks` 配置）；冷却期间 SHALL 不再结算。周期 MUST NOT 随阶级或条件变化。

#### Scenario: 一分钟一次
- **WHEN** 仪式连续满足条件且冷却已过
- **THEN** 相邻两次结算的间隔为 1200 tick（默认）

### Requirement: 全有全无灵力扣费
单次结算的灵力成本 SHALL 为「去重后 species 数 × `shujouBaseSpCost × shujouSpCostMult^level`」（默认 `N × 10000 × 4^L`）。扣费 SHALL 走仪式既有三段式（槽内灵力核心 → 核心缓存 → 周围储灵兜底），且 MUST 为全有全无：存量不足以覆盖总成本时，本周期 MUST NOT 扣费、MUST NOT 产出任何物品、MUST NOT 消耗典籍，行为 SHALL 保持 enabled 等待灵力恢复。有效 species 数为 0 时 SHALL 空转（不扣费、不产出）。

#### Scenario: 灵力不足整周期不产
- **WHEN** 0 阶台上 4 本不同 species 的满典籍（总成本 40000），核心可支配灵力为 39999
- **THEN** 本周期不扣费、不产出、不停机

#### Scenario: 按去重数计费
- **WHEN** 0 阶台上 3 本同为僵尸的满典籍
- **THEN** 本周期成本为 10000（1 个 species），而非 30000

#### Scenario: 成本随阶放大
- **WHEN** 2 阶台上存在 1 个有效 species
- **THEN** 单次成本为 160000（10000×4²）

#### Scenario: 无有效典籍空转
- **WHEN** 台上没有任何满态可解析典籍
- **THEN** 不扣费、不产出，保持 enabled

### Requirement: 原版死亡战利品表产出
对每个有效 species，行为 SHALL 取其 `EntityType` 的默认战利品表（`getDefaultLootTable`）并以 `ENTITY` 参数集掷骰一次，产出 SHALL 与该实体原版死亡掉落一致（数量/概率/耐久/附魔/NBT 保真）。行为 SHALL 为每次掷骰提供一个该类型的默认状态临时实体作为 `THIS_ENTITY`（MUST NOT 加入世界、MUST NOT 参与 tick），并以核心中心为 `ORIGIN`。

#### Scenario: 各 species 各产一份
- **WHEN** 有效 species 为僵尸与骷髅
- **THEN** 本周期分别掷僵尸表与骷髅表各一次，两份产出均进入池

#### Scenario: 数量与概率保真
- **WHEN** 对苦力怕掷表且命中火药条目
- **THEN** 按原版 `set_count`(0..2) 与 `enchanted_count_increase` 得到对应数量，而非固定 1

### Requirement: 玩家击杀上下文与抢夺等级
掷骰 SHALL 提供玩家击杀上下文：`LAST_DAMAGE_PLAYER` 与 `ATTACKING_ENTITY`/`DIRECT_ATTACKING_ENTITY` SHALL 为一个专用的 `FakePlayer` 凭证（MUST NOT 加入世界、MUST NOT 执行任何攻击/击杀），`DAMAGE_SOURCE` SHALL 为玩家攻击伤害源。0 阶时该凭证 SHALL 空手（抢夺 0）；1 阶及以上时该凭证主手 SHALL 携带抢夺等级 `shujouLootingLevel`（默认 3）的武器。由此，`killed_by_player` 限定掉落（如僵尸铁锭/胡萝卜/土豆）SHALL 正常产出，抢夺相关数量/概率加成 SHALL 按该等级生效。

#### Scenario: 0 阶视为无抢夺玩家击杀
- **WHEN** 0 阶掷僵尸表
- **THEN** 存在玩家击杀上下文（铁锭/胡萝卜/土豆可能掉落），且不应用抢夺数量加成

#### Scenario: 一阶及以上应用抢夺 3
- **WHEN** 1 阶或 2 阶掷僵尸表
- **THEN** 抢夺数量加成与 `random_chance_with_enchanted_bonus` 按抢夺 3 生效

#### Scenario: 凭证不入世界
- **WHEN** 任意次数结算完成
- **THEN** 该凭证从未作为实体出现在世界中，也不产生任何击杀、经验或成就副作用

### Requirement: 二阶产物翻倍
2 阶结算时，本次全部产物 SHALL 在抢夺 3 结果之上乘以 `shujouL2OutputMult`（默认 4）；0/1 阶 MUST NOT 翻倍。

#### Scenario: 二阶翻四倍
- **WHEN** 2 阶掷某 species 得到 3 个腐肉
- **THEN** 该 species 最终计入 12 个腐肉

#### Scenario: 低阶不翻倍
- **WHEN** 1 阶完成一次结算
- **THEN** 产物按抢夺 3 结果原样计入，不乘倍率

### Requirement: 特殊击杀掉落排除语义
产出 SHALL 仅来自实体默认死亡战利品表与其玩家击杀上下文，MUST NOT 包含代码驱动的特殊击杀掉落与装备掉落（如充能苦力怕致死的头颅、生物随身装备/命名牌），MUST NOT 包含仅在特定非玩家击杀者下才掉落的表内条目（如骷髅击杀苦力怕产生的唱片）。行为 MUST NOT 依赖任何显式的物品排除名单或标签。

#### Scenario: 不掉苦力怕头
- **WHEN** 对苦力怕掷表
- **THEN** 不产出苦力怕头（该掉落为代码驱动，仅充能苦力怕致死时产生）

#### Scenario: 不掉唱片
- **WHEN** 对苦力怕掷表
- **THEN** 不产出唱片（该条目要求击杀者为骷髅，伪造凭证为玩家）

#### Scenario: 不掉装备
- **WHEN** 对穿着盔甲的僵尸类型掷表
- **THEN** 不产出盔甲等装备掉落

### Requirement: 缓存容量与受灵端点
行为 SHALL 声明固定的大于 0 的受灵速率（默认 1,000,000/s，由 `shujouSpiritInRate` 配置，MUST NOT 随阶级变化），MUST NOT 声明供灵速率。核心缓存容量 SHALL 按 patternId 与阶级分派为 `shujouBaseCapacity × shujouCapacityMult^level`（默认 `40000 × 20^L`，即 40000/800000/16000000）。内部扣费 MUST 走普通通道，MUST NOT 经路由账本接口。

#### Scenario: 可被路由选为受灵汇
- **WHEN** 万象共鸣在范围内寻找接收端
- **THEN** 该仪式因受灵速率 > 0 而进入候选，且不作为供灵源出现

#### Scenario: 容量随阶
- **WHEN** 仪式升到 2 阶
- **THEN** 核心缓存容量为 16,000,000

#### Scenario: 零点阶容量恰为一次满台成本
- **WHEN** 0 阶、4 个有效 species
- **THEN** 总成本 40000 恰等于该阶缓存容量，可被缓存一次性覆盖

### Requirement: 产物聚合与空投
产出 SHALL 聚合同名物品并按最大堆叠拆分（≤64/栈），以物品实体形式空投：水平落点 SHALL 复用仪式产物随机甩落半径，高度 SHALL 取核心上方、上限内、首个遮挡方块之前的最高可穿过位置。产物 MUST NOT 设置拾取前无敌或防护。

#### Scenario: 同名聚合
- **WHEN** 两个 species 均产出腐肉
- **THEN** 腐肉合并计数后再拆栈空投

#### Scenario: 超堆叠拆分
- **WHEN** 2 阶翻倍后某物品总数超过 64
- **THEN** 拆分为多栈（每栈 ≤64）空投

### Requirement: 产出光柱特效
行为 SHALL 仅在结算产出那一刻触发一次巨大光柱特效，运行/充能/待机期间 MUST NOT 触发。光柱 SHALL 经客户端 BER 渲染，服务端 MUST NOT 逐 tick 广播粒子包，SHALL 使用专属色（新增色索引 5，紫色），并在短暂持续后消失。

#### Scenario: 仅产出瞬间触发
- **WHEN** 仪式处于已启动但待机（无有效典籍/缺灵力/冷却中）
- **THEN** 不播放光柱

#### Scenario: 专属紫色光柱
- **WHEN** 一次结算成功产出
- **THEN** 核心处升起紫色巨大光柱并随后消失

### Requirement: GUI 状态行
核心 UI SHALL 显示当前状态（如待机：无有效典籍 / 缺灵力 / 冷却中 / 结算中）、有效 species 数、祭品台数、单次灵力成本、生产周期秒数、当前等级与 2 阶翻倍状态。MUST NOT 溢出信息区宽度（短标签，明细入 tooltip）。

#### Scenario: 状态可见
- **WHEN** 打开已启动仪式的核心 UI
- **THEN** 信息区显示状态行与等级、有效 species 数、成本、周期

#### Scenario: 冷却剩余可见
- **WHEN** 结算后处于冷却中
- **THEN** 显示剩余冷却秒数

### Requirement: 专属配置旋钮
行为 SHALL 暴露专属配置项（COMMON）：缓存容量基值（默认 40000）、容量每阶倍率（默认 20）、灵力成本基值（默认 10000）、成本每阶倍率（默认 4）、生产周期 tick（默认 1200）、受灵速率（默认 1000000）、2 阶产物倍率（默认 4）、抢夺等级（默认 3）。MUST NOT 在行为中硬编码上述字面量。

#### Scenario: 成本可调
- **WHEN** 将灵力成本基值改为 20000 后重载配置
- **THEN** 0 阶单 species 成本变为 20000

#### Scenario: 周期可调
- **WHEN** 将生产周期改为 2400
- **THEN** 相邻结算间隔变为 2 分钟

### Requirement: 世界无关纯内核与调试探针
行为 SHALL 提供世界无关的纯内核（单 species 成本、总成本、容量、2 阶倍率、典籍有效性判定），并 SHALL 提供 `/gs_debug shujou` 探针，打印等级、祭品台数、满态典籍数、去重 species 数、单/总成本、容量、当前状态与一次试掷摘要，供回归断言。

#### Scenario: 探针可断言
- **WHEN** 对成型的众生余录执行调试探针
- **THEN** 输出等级、祭品台/满典籍/去重 species 计数、单 species 成本与总成本、容量、2 阶倍率与试掷结果
