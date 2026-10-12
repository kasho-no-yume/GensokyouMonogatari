# chinkon-hanshou-ritual Delta

## ADDED Requirements

### Requirement: 行为注册与激活型一次性触发

`gensokyou:chinkon_hanshou_circle` SHALL 绑定一个 `RitualBehavior` 实现，并 SHALL 为**激活型一次性**仪式（pattern `toggleable:false`）：核心 GUI MUST NOT 显示原版启停按钮，仅显示本仪式的「镇魂」按钮；通用 `start()` 通道 SHALL 整体让位（服务端硬拒），使无法绕过供品校验直接执行。

一次镇魂 SHALL 在单次点击内完成校验、扣费、吞供品、演出与产出，MUST NOT 依赖周期冷却或会话充能。

#### Scenario: 注册后可被识别

- **WHEN** 服务器加载并匹配到 `chinkon_hanshou_circle` 的成型结构
- **THEN** `RitualBehaviors.get(gensokyou:chinkon_hanshou_circle)` 返回该行为实例

#### Scenario: 无启停按钮

- **WHEN** 打开已成型仪式的核心 GUI
- **THEN** 显示「镇魂」按钮，MUST NOT 显示原版启停按钮

#### Scenario: 通用启停通道让位

- **WHEN** 任何途径尝试对本仪式调用通用 `start()`
- **THEN** 服务端拒绝，`enabled` 恒不置位

#### Scenario: 一次点击完成整个镇魂

- **WHEN** 台上摆有有效的残魂典籍且灵力足额，玩家点击「镇魂」
- **THEN** 本 tick 内完成扣费、吞供品、产出与演出，不需要后续冷却或再次点击

### Requirement: 供品识别（仅残魂典籍）

行为 SHALL 遍历结构内全部祭品台，仅识别 `gensokyou:boss_residual_codex`（寄宿残魂的众生典籍）。普通众生典籍（`gensokyou:codex_of_beings`，含满 20 态）与任何其他物品 MUST NOT 被识别为有效供品，MUST NOT 触发镇魂。

一次镇魂 SHALL 消耗恰好 1 件残魂典籍（从对应祭品台原位移除）。

#### Scenario: 残魂典籍被识别

- **WHEN** 台上是一件寄宿了大妖精残魂的典籍
- **THEN** 该物品计入有效供品

#### Scenario: 普通典籍被忽略

- **WHEN** 台上是一本满 20 的普通众生典籍（记录僵尸）
- **THEN** 不被识别为供品；点击「镇魂」无匹配供品、不扣费、无产出

#### Scenario: 消耗一件

- **WHEN** 一次镇魂成功
- **THEN** 台上的那件残魂典籍被移除，祭品台清空

### Requirement: 阶级门槛（结构层号 ≥ BOSS 品阶）

行为 SHALL 取该残魂典籍所寄宿 BOSS 的自身品阶（`TouhouBoss.bossTier()`），并与结构层号 `RitualMatch.level()` 比较。仅当 `结构层号 >= bossTier` 时 SHALL 放行；低于时 MUST NOT 消耗供品、MUST NOT 扣费、MUST NOT 产出，并 SHALL 在 GUI 与操作反馈中给出明确原因。

门槛 MUST NOT 使用配方 `minTier` 或 `referenceTier()`。

#### Scenario: 低阶结构拒绝高阶 BOSS

- **WHEN** 在 1 阶结构上摆入寄宿了 5 阶 BOSS 残魂的典籍并点击「镇魂」
- **THEN** 拒绝执行，供品与灵力均不受影响，提示"结构阶级不足"

#### Scenario: 同阶放行

- **WHEN** 在 2 阶结构上摆入寄宿了 `bossTier()==2` 的 BOSS 残魂的典籍
- **THEN** 放行

#### Scenario: 高阶结构向下兼容

- **WHEN** 在 5 阶结构上摆入寄宿了 `bossTier()==1` 的 BOSS 残魂的典籍
- **THEN** 放行

### Requirement: 全有全无灵力扣费

单次镇魂的灵力成本 SHALL 为 `chinkonBaseSpCost × chinkonSpCostMult^(BOSS 品阶-1)`——**成本随所镇残魂的 BOSS 品阶放大，MUST NOT 随结构阶级放大**（结构阶级只决定"能处理到几阶的残魂"这一门槛）。默认值进配置。扣费 SHALL 走仪式既有三段式（槽内灵力核心 → 核心缓存 → 半径 3 内其他核心），且 MUST 为**全有全无**：存量不足以覆盖成本时，本 tick MUST NOT 扣费、MUST NOT 消耗供品、MUST NOT 产出，并 SHALL 提示灵力不足。内部扣费 MUST 走普通通道，MUST NOT 经路由账本接口。

#### Scenario: 灵力不足整体不执行

- **WHEN** 台上供品有效但可支配灵力不足单次成本
- **THEN** 不扣费、不吞供品、不产出，提示"灵力不足"

#### Scenario: 足额一次性扣除

- **WHEN** 供品有效且可支配灵力 ≥ 单次成本
- **THEN** 一次性扣去该成本并执行镇魂

#### Scenario: 成本随 BOSS 品阶放大

- **WHEN** 镇魂一只 `bossTier()==3` 的 BOSS 残魂
- **THEN** 单次成本为 `base × mult²`（与结构阶级无关）

#### Scenario: 同结构处理不同品阶成本不同

- **WHEN** 在同一 5 阶结构上分别镇魂 1 阶与 5 阶的残魂
- **THEN** 两次成本分别为 `base` 与 `base × mult⁴`

### Requirement: 产出（碎符卡星 + 主题信物）

一次成功镇魂 SHALL 产出：

- `0~1` 个 `gensokyou:broken_spell_card_star`（独立掷骰）；
- `1~2` 件该 BOSS 的主题信物（即 `TouhouBoss.signatureItem()` 的返回物品，须属 `#gensokyou:boss_signature`）。

当该 BOSS 未声明主题信物时，镇魂 SHALL 仍产出碎符卡星掷骰结果，MUST NOT 凭空产出信物。上下界 SHALL 由配置提供，MUST NOT 硬编码。

#### Scenario: 保底信物

- **WHEN** 镇魂一只带信物的 BOSS（如山女）
- **THEN** 至少产出 1 件 `gensokyou:earth_spider_silk`

#### Scenario: 碎星可为 0

- **WHEN** 碎星掷骰命中 0
- **THEN** 本次不产出碎符卡星，但信物照常产出

#### Scenario: 无信物 BOSS 不产信物

- **WHEN** 镇魂一只未声明信物的 BOSS
- **THEN** 只产出碎星掷骰结果，不产出任何信物

### Requirement: 产物聚合与空投

产出 SHALL 聚合同名物品并按最大堆叠拆分，以物品实体形式空投：水平落点 SHALL 复用仪式产物随机甩落半径（核心为圆心、水平半径 3 的圆盘内随机位置），高度 SHALL 取核心上方、上限内、首个遮挡方块之前的最高可穿过位置。产物 MUST NOT 设置拾取前无敌或防护。

#### Scenario: 同名聚合

- **WHEN** 信物与碎星恰好同名（不发生）
- **THEN** 仍分别处理；同名情形聚合后拆栈

#### Scenario: 落点在核心圆盘内

- **WHEN** 一次镇魂产出物品
- **THEN** 每个产物实体的水平坐标落在核心水平半径 3 以内

### Requirement: 产出光柱特效

行为 SHALL 仅在产出那一刻触发一次巨大光柱特效，拒绝/不足/未产出时 MUST NOT 触发。光柱 SHALL 经客户端 BER 渲染，服务端 MUST NOT 逐 tick 广播粒子包，SHALL 使用**冷色（青白）**专属色，与「众生余录」的紫色光柱区分，并在短暂持续后消失。

#### Scenario: 仅产出瞬间触发

- **WHEN** 镇魂被拒绝（缺供品/灵力不足/阶级不足）
- **THEN** 不播放光柱

#### Scenario: 冷色专属光柱

- **WHEN** 一次镇魂成功产出
- **THEN** 核心处升起青白色巨大光柱并随后消失，可与众生余录的紫色光柱一眼区分

### Requirement: GUI 状态行

核心 UI SHALL 显示当前状态：无有效供品 / 阶级不足 / 灵力不足 / 就绪；并在就绪时显示供品寄宿的 BOSS 名与其品阶、结构阶级、单次灵力成本。MUST NOT 溢出信息区宽度（短标签，明细入 tooltip）。

#### Scenario: 状态可见

- **WHEN** 打开已成型仪式的核心 UI
- **THEN** 信息区显示供品状态与成本行

#### Scenario: 拒绝原因可见

- **WHEN** 台上供品的 BOSS 品阶高于结构阶级
- **THEN** 信息区显示"阶级不足"，且标明所需阶级

### Requirement: 专属配置旋钮

行为 SHALL 暴露专属配置项（COMMON）：成本基值、成本每阶倍率、碎星产出下界/上界、信物产出下界/上界、受灵汇速率。MUST NOT 在行为中硬编码上述字面量。

#### Scenario: 成本可调

- **WHEN** 将成本基值改为另一值后重载配置
- **THEN** 单次灵力成本随之变化

#### Scenario: 产出可调

- **WHEN** 将信物产出下界改为 2
- **THEN** 每次镇魂至少产出 2 件信物

### Requirement: 世界无关纯内核与调试探针

行为 SHALL 提供世界无关的纯内核（单次成本、容量、阶级门槛判定、产出掷骰），并 SHALL 提供 `/gs_debug chinkon` 探针，打印等级、祭品台数、有效供品种类、寄宿 BOSS 与其品阶、门槛判定、单次成本与当前状态，供回归断言。

#### Scenario: 探针可断言

- **WHEN** 对成型的镇魂返照之仪执行调试探针
- **THEN** 输出等级、供品、BOSS 品阶、门槛判定、成本与状态

### Requirement: 容量与受灵端点

行为 SHALL 声明固定的大于 0 的受灵速率（由配置提供，MUST NOT 随阶级变化），MUST NOT 声明供灵速率；核心缓存容量 SHALL 至少能覆盖最高阶级的单次成本，使电容/路由可为其蓄灵。

#### Scenario: 可被路由选为受灵汇

- **WHEN** 万象共鸣在范围内寻找接收端
- **THEN** 该仪式因受灵速率 > 0 而进入候选，且不作为供灵源出现
