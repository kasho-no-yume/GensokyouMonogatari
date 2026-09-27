# zaohua-crafting Specification

## Purpose
TBD - created by archiving change implement-zaohua-crafting. Update Purpose after archive.
## Requirements
### Requirement: 合成触发接口与触发源
源初造化之仪（`gensokyou:zaohua_circle`）SHALL 提供唯一会话入口 `trigger(source, player?)`，所有触发源经此启动一次合成；触发源类型 SHALL 以可扩展枚举声明（本期含 GUI 按钮、红石脉冲），后续新增触发方式 MUST NOT 改动执行路径。GUI 侧 SHALL 以行为注入的自定义操作按钮「开始合成」呈现（仪式 pattern 保持 `toggleable:false`，通用启停按钮不出现）。红石侧 SHALL 由核心方块接收邻居信号变化并做**上升沿**判定：仅"未充能→充能"跳变触发一次，常亮信号 MUST NOT 重复触发；该脉冲链路 MUST NOT 影响其他仪式（仅覆写脉冲钩子的仪式响应）。面向玩家的失败提示仅在存在触发玩家时回显。

#### Scenario: GUI 按钮触发
- **WHEN** 玩家在仪式核心 GUI 点击「开始合成」且台面满足某配方
- **THEN** 合成会话启动，按钮回显状态消息

#### Scenario: 红石上升沿触发一次
- **WHEN** 核心邻居信号从 0 变为 >0（如 1 tick 脉冲）
- **THEN** 恰好触发一次合成会话；信号持续保持 >0 不再触发

#### Scenario: 合成中重复触发无响应
- **WHEN** 会话处于飞行阶段时再次以任意来源触发
- **THEN** 无任何效果：不开启第二次合成、不追加消耗；GUI 按钮呈现禁用/置灰态

#### Scenario: 聚灵中再触发取消会话
- **WHEN** 会话处于聚灵阶段时再次触发
- **THEN** 会话取消并回到空闲；此前已抽入的灵力不退还，台面原料未扣保持原样

### Requirement: 先聚灵后合成的执行序列
合成会话启动 SHALL 按如下顺序执行：①以最大匹配锁定本次配方（会话期内 pin 该配方，不随台面变化改选），会话容量即锁定为配方 `spCost`（**不启动时容量为 0、不缓存灵力；启动后缓存上限恰等于配方需求**）；②进入聚灵阶段：逐 tick 从 `core-socket-powering` 的三段式来源抽取灵力直至累计 = spCost，**不要求触发瞬间足额**（供能主干万象共鸣按 tick 注入、随时间累积，正是此等待语义的用途）；③足额后才扣减台面原料并进入飞行阶段。聚灵期间每 tick SHALL 重验台面仍满足锁定配方子集，一旦不满足即中止执行：已抽灵力**不退还**、未扣原料不动。

#### Scenario: 触发瞬间灵力不足仍启动等待
- **WHEN** 触发时槽核+自身储+周围兜底瞬时合计 < spCost（如灵力靠万象共鸣逐 tick 注入）
- **THEN** 会话仍进入聚灵态，逐 tick 累积已注入灵力，MUST NOT 因瞬间不足而拒绝启动

#### Scenario: 未启动不缓存灵力
- **WHEN** 会话处于空闲（未触发）
- **THEN** 核心灵力缓存上限为 0，路由选不中该汇、万象共鸣注不进来；缓存存量保持 0

#### Scenario: 聚灵跨 tick 累积足额才扣料
- **WHEN** 来源瞬时余额小于 spCost，会话在聚灵态逐笔抽取
- **THEN** 累计足额当 tick 扣减台面原料并进入飞行

#### Scenario: 聚灵途中台面被破坏配方
- **WHEN** 聚灵期间玩家取走/更换台面物品致锁定配方不再子集命中
- **THEN** 会话中止，已抽取的灵力不退，台面剩余物品不动，核心回到空闲态（容量回落 0）

### Requirement: 飞行汇聚合成演出
飞行阶段 SHALL 呈现约 5 秒（时长 config 可调，缺省 100 tick）的演出：有效原料逐台自祭品台起飞，绕核心旋转、轨迹逐步向内收敛且持续升高，最终汇聚于核心上方汇聚点；每件飞行物 MUST 带粒子拖尾；汇聚瞬间 SHALL 播放烟花爆炸粒子效果，产物实体自汇聚点受重力落至地面。飞行期间结构包围盒内 SHALL 持续有大量紫色粒子升空（生成率 config 可调）。演出以服务端权威状态驱动、客户端确定性轨迹解算（两端同曲线函数）。飞行物的位置 SHALL 为**纯客户端表现**：其世界坐标 MUST 仅由客户端确定性曲线解算决定，服务端周期性的实体位置同步 MUST NOT 改变飞行物的呈现位置（客户端 SHALL 忽略该类位置包，重载/传送路径亦然）；服务端实体位置仅作异常掉落解算参考，MUST NOT 由服务端逐 tick 强制同步位置。粒子表现 MUST NOT 采用服务端逐 tick 全结构广播。

#### Scenario: 全流程时序
- **WHEN** 一次合成进入飞行阶段
- **THEN** 原料离台→螺旋内收上升→汇聚爆炸→产物落地全程约 5 秒，产物为可拾取物品实体且落点在结构附近地面

#### Scenario: 结构粒子氛围
- **WHEN** 飞行阶段进行中，附近玩家观察结构
- **THEN** 整个结构范围持续升空紫色粒子，直至爆炸收尾

#### Scenario: 无位置回拉闪现
- **WHEN** 一次飞行演出持续超过 60 tick（覆盖原版实体周期位置包）
- **THEN** 飞行物沿确定性曲线连续运动，MUST NOT 出现被拉回祭品台起点再返回的闪现帧

### Requirement: 异常终止防吞件
合成过程任何路径终止 MUST NOT 凭空吞没物品：飞行阶段结构失效（核心被拆/重扫失配）时，处于飞行中的原料 SHALL 以服务端轨迹解算的当前位置就地掉落为物品实体，会话终止且灵力不退还；区块卸载重载后会话（含聚灵进度与飞行物）SHALL 恢复续跑；仪式核心被破坏时槽内灵力核心的既有掉落规则不受影响。

#### Scenario: 飞行中拆台
- **WHEN** 飞行阶段核心方块被破坏
- **THEN** 全部在飞原料在各自当前轨迹位置落地为可拾取物品，产物不再投放，灵力不退还

#### Scenario: 区块往返恢复
- **WHEN** 会话期间核心所在区块卸载后重新加载
- **THEN** 会话阶段与已聚灵进度保持，飞行物回到当前时刻应有的轨迹位置继续演出

### Requirement: 示例配方与本地化
系统 SHALL 交付一仪式一文件的配方数据 `data/gensokyou/ritual_recipes/zaohua_circle.json`（顶层 `pattern` + `recipes[]`，每条含 `name`/`mode`/`match:"max"`/`minTier:0`）：①`zaohua_stone_t1`：4×diamond + 4×ritual_stone_0 + 4×refined_cinnabar → 1×ritual_stone_1（spCost 2,000）；②`zaohua_spellcard_star`：8×broken_spell_card_star → 1×spellcard_star（spCost 8,000）。spCost 数值为可调占位（改 JSON 即生效，MUST NOT 硬编码进 Java）。仪式名、按钮文案、状态与失败消息 SHALL 备齐中英语言键。配方目录展示归 JEI，仪式 GUI MUST NOT 罗列可用配方。

#### Scenario: 0 阶可造 1 阶仪式石
- **WHEN** 0 阶源初造化的 8 个祭品台各放 1 件（4 钻石 + 4 仪式石 0）且灵力足额，触发合成
- **THEN** 爆炸后掉落 1 个仪式石 1

#### Scenario: max 匹配余料不动
- **WHEN** 台面为 4a+3b+5c+1d（a/b/c/d 对应两条示例配方的实际物品混合）时触发
- **THEN** 仅按可命中的最大匹配配方消耗对应件数，其余物品留在原台面

### Requirement: 碎符卡星为 BOSS 专属稀缺物，不提供配方
`broken_spell_card_star` SHALL 为**有意的稀缺材料**：当前 MUST NOT 存在任何工作台配方、源初造化配方、锻造配方或其它可稳定量产的生产路径，其唯一来源为 BOSS 掉落。系统 MUST NOT 为其补任何合成配方，`zaohua_spellcard_star` SHALL 保持 `minTier:0`、8×碎符卡星配方不变。

该设计为有意决策而非遗漏：弹幕方术台与符星铳因此位于 BOSS 之后，属于中期目标而非开局可及。任何变更若要为其增加生产路径，SHALL 先作为独立的稀缺度调整变更讨论，MUST NOT 在补链类变更中顺带加入。

#### Scenario: 碎符卡星不可制造
- **WHEN** 审查 `data/gensokyou/recipe/`、`ritual_recipes/` 与 `ritual_smelt_recipes/` 中以 `broken_spell_card_star` 为产物的条目
- **THEN** 一条都没有；该物品只能从 BOSS 获得

#### Scenario: 符卡星仍可由碎星重铸
- **WHEN** 玩家持有 8 枚碎符卡星
- **THEN** 可在 0 阶源初造化之仪上重铸出 1 枚符卡星（8,000 灵力）

