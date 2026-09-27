## ADDED Requirements

### Requirement: 行为注册与启停门控
`gensokyou:houjouno_teihou_circle` SHALL 绑定一个专用 `RitualBehavior`，并依赖现有 pattern 的 `toggleable:true` 提供启停入口。生产结算 SHALL 只在核心 `enabled=true` 时由 `serverTick` 执行；停机时 MUST NOT 扫描并提交收获、扣费或产物。生产 MUST NOT 使用不受 enabled 门控的通用 passive 配方。

#### Scenario: 注册后可被识别
- **WHEN** 服务器加载并匹配到 `gensokyou:houjouno_teihou_circle`
- **THEN** `RitualBehaviors.get(gensokyou:houjouno_teihou_circle)` 返回专用行为，启停按钮可控制其生产

#### Scenario: 手动停止不生产
- **WHEN** 仪式已成型但 `enabled=false`
- **THEN** 行为不执行收获扫描、灵力支付或产物生成

#### Scenario: 通用 passive 不得绕过启停
- **WHEN** 玩家手动停止丰穰神献礼
- **THEN** 通用 passive 配方通道或任何未受 enabled 门控的路径均不得为该仪式生成产物

### Requirement: 即时结算与固定成功冷却
当核心 `actionCooldown=0` 且仪式已启动时，行为 SHALL 立即尝试一次完整结算。每次成功支付并提交产物后，核心 SHALL 进入默认 1200 tick（60 秒）的冷却；冷却 MUST NOT 随结构等级变化。无有效种子、解析失败或灵力不足时 SHALL 空转且 MUST NOT 设置新冷却。

#### Scenario: 启动后立即首次产出
- **WHEN** 玩家启动结构、冷却为 0、祭品台存在有效种子且灵力充足
- **THEN** 核心下一次服务端 tick 立即完成一次产出，无需再次点击启动

#### Scenario: 成功后冷却一分钟
- **WHEN** 一次结算成功提交产物
- **THEN** 核心 action cooldown 被设为 1200 tick，冷却归零前不再结算

#### Scenario: 无有效种子时空转
- **WHEN** 冷却为 0 但没有有效种子
- **THEN** 不扣费、不产出、不设置冷却，仪式保持 enabled 等待种子

#### Scenario: 欠费时不启动失败冷却
- **WHEN** 存在有效种子但可支付灵力不足
- **THEN** 本次不产出且 cooldown 保持 0，灵力恢复后下一次 tick 可立即重试

### Requirement: 每祭品台独立计费且种子永久保留
行为 SHALL 遍历结构内全部祭品台；每个放有正确种子或农业繁殖材料的祭品台 SHALL 独立形成一个有效收获单元。相同种子放在多个祭品台 MUST 分别计费和采样。有效输入 MUST NOT 在成功、缺料、欠费或适配失败时被消耗、替换或修改。

#### Scenario: 四个小麦种子按四份计算
- **WHEN** 0 阶结构的四个祭品台各放一个 `minecraft:wheat_seeds`
- **THEN** 行为视作四个有效单元，收取四倍单台成本并对每个台位独立采样

#### Scenario: 混合作物分别计算
- **WHEN** 祭品台分别放有小麦种子、胡萝卜与一个无效物品
- **THEN** 小麦和胡萝卜各自形成有效单元，无效物品被忽略且不阻断另外两个台位

#### Scenario: 种子永久保留
- **WHEN** 任意等级成功结算一次或多次
- **THEN** 每个有效祭品台上的原输入物品及其数据组件保持原样

### Requirement: 全有全无三段式灵力支付
单次结算总成本 SHALL 为 `有效祭品台数 × 单台成本`，其中单台成本默认为 `4000 × 4^structureLevel`。支付 SHALL 复用三段式来源：槽内灵力核心 → 核心自身缓存 → 半径内其他成型核心，并 MUST 先验证总额再一次性支付。可支付总额不足时 MUST 丢弃整批采样，不扣任何灵力、不产出、不改冷却，并 SHALL 保持 `enabled=true`。

#### Scenario: 四台零阶总成本
- **WHEN** 0 阶有四个有效祭品台并完成结算
- **THEN** 总成本为 16000 灵力

#### Scenario: 二阶单台成本
- **WHEN** 2 阶有一个有效祭品台
- **THEN** 单台成本为 64000 灵力

#### Scenario: 灵力不足不改变开关
- **WHEN** 三个来源合计比总成本少 1 灵力
- **THEN** 所有来源均不被扣除、没有产物、冷却不变，核心仍为 enabled

#### Scenario: 支付失败不产生部分批次
- **WHEN** 批次包含多个有效祭品台但单次 `payCost` 失败
- **THEN** 所有临时采样均被丢弃，不按台位顺序进行部分支付或部分产出

### Requirement: 缓存容量与受灵端点
核心缓存容量 SHALL 默认按 `40000 × 12^structureLevel` 分派，行为 SHALL 声明默认 `40000 × 12^structureLevel` 的受灵速率，并 SHALL 将供灵输出速率保持为 0。容量与受灵 MUST 使用独立配置基项和倍率。结构等级 SHALL 取 `RitualMatch.level()`，MUST NOT 取视觉仪式石最高品阶。

#### Scenario: 三个等级数值按结构等级
- **WHEN** 结构分别匹配为 0、1、2 阶
- **THEN** 容量与受灵速率分别为 40000/480000/5760000

#### Scenario: 可作为受灵汇
- **WHEN** 万象共鸣寻找输出目标
- **THEN** 丰穰神献礼因受灵速率大于 0 而进入候选

#### Scenario: 不作为供灵源
- **WHEN** 万象共鸣寻找供灵来源
- **THEN** 丰穰神献礼因供灵速率为 0 而不进入候选

### Requirement: 标准作物自动识别
未命中显式适配器时，系统 SHALL 在输入为 `BlockItem`、其方块继承 `CropBlock`、且方块克隆/种子身份与输入物品一致时自动建立标准作物适配器。标准适配器 SHALL 通过 `getStateForAge(getMaxAge())` 构造成熟状态，并使用成熟状态的空工具 loot。自动识别 MUST NOT 依赖 `c:seeds`、`forge:seeds` 或 `minecraft:crops` 标签。

#### Scenario: 原版标准作物自动支持
- **WHEN** 祭品台分别放置小麦种子、胡萝卜、马铃薯、甜菜根种子或火把花种子
- **THEN** 无需显式数据条目即可按各作物成熟状态生成正常空工具收获

#### Scenario: 标准 mod 作物自动支持
- **WHEN** mod 种子是 `BlockItem`、对应标准 `CropBlock` 且成熟状态由年龄表达
- **THEN** 该作物自动使用与原版标准作物相同的成熟和 loot 流程

#### Scenario: 成熟作物方块不是种子
- **WHEN** 输入是 `minecraft:wheat` 成熟作物方块物品而非 `minecraft:wheat_seeds`
- **THEN** 克隆/种子身份不匹配，系统不把它当作标准种子

#### Scenario: 非农业植物不自动纳入
- **WHEN** 输入是树苗、蘑菇、装饰植物、成熟果实方块或没有生长阶段的幻想乡植物
- **THEN** 系统不把它们自动识别为农业种子，除非另有显式适配器注册

### Requirement: 正常收获为成熟状态空工具 loot
标准作物与状态型特例 SHALL 以成熟 `BlockState` 为收获对象，并使用空工具、无玩家、无方块实体的 loot 上下文。该基线 MUST 保留战利品表定义的数量、概率、附魔与数据组件，但 MUST NOT 应用经验、幸运、精准采集、工具门槛、破坏事件或特殊玩家交互。

#### Scenario: 小麦保留种子随机掉落
- **WHEN** 标准小麦成熟状态完成一次空工具 loot 采样
- **THEN** 输出遵循当前小麦战利品表，可同时包含小麦与独立随机的种子

#### Scenario: 不产生玩家破坏副作用
- **WHEN** 任一成熟状态执行采样
- **THEN** 不发放经验，不触发玩家破坏/工具门槛语义，也不执行真实世界方块移除

### Requirement: 原版农业特例适配
系统 SHALL 为 `minecraft:melon_seeds`、`pumpkin_seeds`、`pitcher_pod`、`nether_wart`、`sweet_berries`、`cocoa_beans`、`sugar_cane` 和 `cactus` 提供显式适配器。状态型特例 SHALL 使用正确成熟状态的空工具 loot；独立物品型特例 SHALL 每次采样直接返回规定完整物品。

#### Scenario: 西瓜每次产出完整西瓜
- **WHEN** 西瓜种子完成一次采样
- **THEN** 输出一个 `minecraft:melon`，MUST NOT 输出西瓜片

#### Scenario: 南瓜每次产出完整南瓜
- **WHEN** 南瓜种子完成一次采样
- **THEN** 输出一个 `minecraft:pumpkin`

#### Scenario: 甘蔗每次固定一个
- **WHEN** 甘蔗完成一次采样
- **THEN** 无论其自然成熟高度如何，输出恰好一个 `minecraft:sugar_cane`

#### Scenario: 仙人掌每次固定一个
- **WHEN** 仙人掌完成一次采样
- **THEN** 无论其自然成熟高度如何，输出恰好一个 `minecraft:cactus`

#### Scenario: 状态型特例使用成熟 loot
- **WHEN** 瓶子草、下界疣、甜浆果或可可完成采样
- **THEN** 使用其正确成熟方块状态的当前空工具 loot 决定全部输出

### Requirement: 非标准 mod 适配接口
系统 SHALL 提供运行时作物收获适配器注册接口，使 mod 可按物品或资源标识注册 provider，而无需让本模组编译期依赖该 mod。显式 provider MUST 优先于标准自动识别。provider SHALL 只向当前收获批次收集输出，MUST NOT 修改世界、祭品台或其他持久状态；provider 抛出异常时 SHALL 记录诊断并只使对应祭品台退出本批次。

#### Scenario: 非标准作物注册后可用
- **WHEN** 某 mod 为自定义种子注册 provider 后将其放入祭品台
- **THEN** 丰穰神献礼使用该 provider 的每次采样结果结算

#### Scenario: 显式适配覆盖自动识别
- **WHEN** 某物品本可走标准 CropBlock 自动路径但同时注册了显式 provider
- **THEN** 系统优先使用显式 provider，不自动路径与显式路径同时产出

#### Scenario: 未注册作物不误判
- **WHEN** 一个自定义种子既不符合标准 CropBlock 条件也未注册 provider
- **THEN** 该台被标记为无效并留在祭品台，不产生通用猜测收获

#### Scenario: 适配失败按台隔离
- **WHEN** 一个已注册 provider 抛异常而其他祭品台有效
- **THEN** 异常台不计入成本，其他有效台仍可完成本批次

### Requirement: 每阶独立采样
每个有效祭品台在每次结算中 SHALL 独立执行 `1 × 4^structureLevel` 次采样：0 阶 1 次、1 阶 4 次、2 阶 16 次。系统 MUST NOT 先取得一次随机结果再直接乘数量；每次采样 MUST 使用独立随机结果。已成功识别并完成采样的单元即使本次随机结果为空，仍 SHALL 计入该批成本。

#### Scenario: 各阶采样次数
- **WHEN** 一个有效祭品台分别在 0、1、2 阶结算
- **THEN** provider 分别被调用 1、4、16 次

#### Scenario: 二阶满台采样上限
- **WHEN** 2 阶结构的 12 个祭品台全部有效
- **THEN** 单周期共执行 192 次采样

#### Scenario: 独立随机分布
- **WHEN** 1 阶小麦单台执行四次采样且每次可能掉零到三粒种子
- **THEN** 最终种子数按四次独立结果求和，而不是只取 0、4、8 或 12

#### Scenario: 空 loot 仍计有效配方
- **WHEN** 有效 provider 的某次采样返回空结果
- **THEN** 该祭品台仍作为一个有效单元收取单台成本并完成本次冷却

### Requirement: 完整组件聚合、拆栈与空投
产出 SHALL 在提交前按“物品相同且全部数据组件等价”聚合，累计数量 MUST 使用饱和 `long`。每个聚合项 SHALL 按对应物品最大堆叠数拆分，再通过现有 `RitualOutputs` 在核心上方、配置水平半径的圆盘内生成无拾取保护物品实体。系统 MUST NOT 仅按 `Item` 合并而丢失附魔、名称、药水、容器或自定义组件。

#### Scenario: 完全相同栈合并
- **WHEN** 多次采样产生多个完全相同的物品栈
- **THEN** 系统合并数量并拆成尽量少的最大堆叠

#### Scenario: 不同组件不合并
- **WHEN** 同一物品分别带不同附魔、名称或容器内容
- **THEN** 各组件签名分别聚合和空投，不得互相覆盖

#### Scenario: 大量产物拆栈
- **WHEN** 某组件等价产物的总数超过单栈最大堆叠数
- **THEN** 输出被拆为多个不超过最大堆叠数的物品栈

### Requirement: 手动停止不停止受灵
手动停止 SHALL 只把 `enabled` 置为 false 并停止生产；路由注入与槽内灵力核心到缓存的补料 SHALL 继续。槽内电池补料 MUST 使用灵力核心自身速率，行为 inRate MUST 只约束万象共鸣路由。手动停止期间 action cooldown SHALL 继续递减；玩家重新启动后，冷却为 0 时 SHALL 立即结算，否则等待剩余时间。欠费 SHALL NOT 改变 enabled，因此无需玩家再次启动即可在灵力恢复后结算。

#### Scenario: 停止期间路由仍可充能
- **WHEN** 玩家手动停止仪式后万象共鸣仍向其传输灵力
- **THEN** 核心缓存继续增加，但停止期间没有生产

#### Scenario: 停止期间电池继续补缓存
- **WHEN** 手动停止后槽内放置仍有储灵的灵力核心
- **THEN** 电池按自身速率继续把灵力补入缓存，缓存满后停止增加

#### Scenario: 停止不暂停冷却
- **WHEN** 冷却尚余 30 秒时手动停止并等待 30 秒后重启
- **THEN** 重启时冷却已归零并立即尝试结算

#### Scenario: 欠费恢复不改开关
- **WHEN** enabled 状态下因灵力不足跳过结算，随后三段式来源恢复足额
- **THEN** 行为保持 enabled 并在下一次 tick 自动结算

### Requirement: 结构变化与生命周期
结构升级或降级 MUST NOT 单独修改 enabled；下一次扫描后的结算 SHALL 使用当前 `RitualMatch.level()`、当前仍在结构中的祭品台和当前配置公式。结构失效 SHALL 由统一核心生命周期自动停机并阻止生产。任何缺料、欠费或适配失败 MUST NOT 模拟结构失效。

#### Scenario: 冷却中升级
- **WHEN** 0 阶运行中的仪式在冷却期间补齐 1 阶结构
- **THEN** enabled 保持不变，冷却不被重置，下一次成功结算使用 1 阶成本与采样倍率

#### Scenario: 拆毁结构自动停机
- **WHEN** 运行中的任一组成方块被拆除并在下一次重扫时失效
- **THEN** 核心框架将 enabled 置为 false，后续不再生产

#### Scenario: 欠费不等于结构失效
- **WHEN** 结构仍完整但可支付灵力不足
- **THEN** 不调用结构失效清理，不把 enabled 置为 false

### Requirement: 配置、GUI、调试与指导书口径
所有数值 SHALL 由 COMMON 配置驱动并使用饱和运算。GUI SHALL 显示运行/手动停止、冷却、有效/无效台位、单位/总成本和采样次数；灵力不足时 MUST 显示为运行中待机而非未启动。调试接口 SHALL 输出包含等级、enabled、cooldown、台位统计、成本、采样数、stored/capacity 和适配失败数的单行摘要。指导书 SHALL 显示各阶现算参数、原版支持范围与非标准 mod 适配说明。

#### Scenario: 修改默认公式配置
- **WHEN** 管理员调整容量、受灵、单台成本、采样或冷却配置
- **THEN** 后续结构判定、路由声明、GUI、调试摘要和指导书参数页使用同一新公式

#### Scenario: 欠费状态不伪装成停机
- **WHEN** enabled=true 但总灵力不足
- **THEN** GUI 显示运行中的缺灵待机原因，启停按钮仍提供停止操作

#### Scenario: 调试摘要可机读
- **WHEN** 管理员查询丰穰神献礼调试信息
- **THEN** 单行输出包含本规范要求的状态与统计字段，数值与 GUI 和实际结算口径一致
