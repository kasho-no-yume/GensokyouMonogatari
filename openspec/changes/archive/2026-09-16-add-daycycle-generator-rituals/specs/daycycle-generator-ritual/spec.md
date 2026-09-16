# daycycle-generator-ritual Delta Spec

## ADDED Requirements

### Requirement: 昼夜产灵时间线
日轮天台与月影水镜 SHALL 按所在 `ServerLevel` 的当刻时刻（`floorMod(dayTime, 24000)`，fixed_time 维度取其固定值）以线性三角时间线产灵。日轮（`gensokyou:nichirin_circle`）在日出（0）为 0、线性升至正午（6000）达峰值、线性降至日落（12000）回 0，其余时段为 0；月影（`gensokyou:tsukikage_circle`）时段与日轮相反：日落（12000）0、午夜（18000）峰值、日出（24000/0）0。二者在任一时段 MUST NOT 同时非零，且日升/日落边界处均为 0。

#### Scenario: 日轮四个特征时刻
- **WHEN** 日轮核心所处维度 dayTime 分别为 0 / 6000 / 12000 / 18000
- **THEN** 其产灵速率为 0 / 峰值 / 0 / 0

#### Scenario: 月影四个特征时刻
- **WHEN** 月影核心所处维度 dayTime 分别为 12000 / 18000 / 24000 / 6000
- **THEN** 其产灵速率为 0 / 峰值 / 0 / 0

#### Scenario: 线性插值
- **WHEN** 日轮在 dayTime 3000（日出到正午的中点）
- **THEN** 其未取整产灵速率为峰值的二分之一

### Requirement: 逐秒取整发放
产灵 SHALL 以每秒一次（`ageTicks % 20 == 0`）结算，速率取"峰值 × 时间线比例"后按四舍五入（半值向上，`Math.round`）取整为整数值发放；MUST NOT 以小数累积（定点进位）方式发放。取整后的整数即该秒实际产出。

#### Scenario: 半值向上取整
- **WHEN** 日轮 0 阶（峰值 5）在未取整速率恰为 2.5 的时点结算
- **THEN** 该秒产灵 3

#### Scenario: 日出日落当刻不产灵
- **WHEN** 在 dayTime 0 或 12000 结算日轮
- **THEN** 该秒产灵 0

### Requirement: 峰值与缓存的阶级缩放
0 阶峰值产灵速率 SHALL 为 5/秒、缓存上限 SHALL 为 10000；每升一阶 SHALL 各自乘以 4（峰值 `base × 4^阶级`、缓存 `base × 4^阶级`）。数值 MUST 由配置基项驱动而非硬编码。缓存上限 SHALL 并入核心容量按图案分派（`getCapacity()`）。仪式可达到的阶级由 pattern 的 `tiers` 与结构层级决定（当前 `[0,1]`，即 0 阶 5/s+10000、1 阶 20/s+40000）。

#### Scenario: 0 阶与 1 阶
- **WHEN** 日轮分别为 0 阶与 1 阶
- **THEN** 峰值产灵为 5/s 与 20/s，缓存上限为 10000 与 40000

### Requirement: 启停门控
两仪式 SHALL 为可启停仪式（pattern `toggleable:true`）：成型后可经核心界面启动/停止，**产灵逻辑** SHALL 以 `enabled` 为前置门控——停止态 SHALL NOT 产灵。缓存向灵力核心的**回流** SHALL NOT 受启停门控（成型即每秒进行，见分流需求）。结构失效 SHALL 自动停机并触发失效清理。

#### Scenario: 停机不产电
- **WHEN** 日轮处于停止态且 dayTime 处于白天峰值区间
- **THEN** 不产灵，结构保持有效

#### Scenario: 停机仍回流
- **WHEN** 停止态的日轮缓存中存有灵力、槽内装有未满的核心
- **THEN** 缓存仍按核心注灵速率回流入核（回流不受启停门控）

#### Scenario: 拆石停机
- **WHEN** 运行中的日轮被拆掉一块组成方块
- **THEN** 重扫判定失效，自动停机并可重建后再次启动

### Requirement: 发电机灵力分流
产灵 SHALL 按"优先注入槽内灵力核心 → 溢出进仪式缓存 → 缓存在运行期按核心注灵速率回流未满核心 → 两者皆满则余量作废（空烧）"分流：

- 槽内装有灵力核心时，产灵 SHALL 不限速直注该核心（同 `refundCached` 先例），仅当核心装不下才溢出入缓存；
- 溢出经普通 `receive` 填入缓存并截断到缓存上限，余量 MUST 作废且不报错、不落物；
- 只要结构成型（MUST NOT 依赖启停状态），每秒 SHALL 将缓存中的灵力按槽内灵力核心的注灵速率上限回流入该核心（与迦具土同一 carry 进位口径，防整除截断），无核心/缓存为空/核心已满时为 0；
- 内部产灵 MUST NOT 走路由速率账本（`extractRouted`/`receiveRouted`）。

#### Scenario: 有核优先注核
- **WHEN** 空缓存、槽内装有剩余容量充足的灵力核心，某秒产灵 20
- **THEN** 20 全部进入核心，缓存不增

#### Scenario: 核满溢出入缓存
- **WHEN** 槽内核心已满、缓存未满，某秒产灵 20（缓存上限尚有余量）
- **THEN** 20 进入缓存

#### Scenario: 无核全部入缓存
- **WHEN** 槽内无灵力核心，某秒产灵 5
- **THEN** 5 进入缓存

#### Scenario: 缓存回流未满核心
- **WHEN** 缓存有存量、槽内装有未满核心
- **THEN** 每秒从缓存向核心转移至多"核心注灵速率"的灵力，缓存相应下降

#### Scenario: 皆满作废
- **WHEN** 槽内核心已满、缓存也达上限
- **THEN** 该秒产出作废，无报错、无落物

### Requirement: 供灵端点声明
两仪式 SHALL 声明固定的 `spiritOutRatePerSecond = 10000/秒`（MUST NOT 随阶级或时刻变化），供万象共鸣路由抽取；`spiritInRatePerSecond` SHALL 保持默认 0（不可被选为受灵汇）。速率 MUST 为静态值——路由按结算周期缓存端点速率并以 `> 0` 判定源资格，动态速率会致源在时段切换时闪断。

#### Scenario: 缓存有货时可选为源
- **WHEN** 万象共鸣寻找供灵源，且某日轮核心缓存存有灵力
- **THEN** 该核心以 10000/s 上限出现在候选中

#### Scenario: 不可作为汇
- **WHEN** 万象共鸣寻找受灵汇
- **THEN** 日轮/月影核心均不在候选中

### Requirement: 维度无关计时
产灵计时 SHALL 仅依赖所在维度的时刻，MUST NOT 判定天空可见性、天气或月相；任意维度（含无天空的下界/末地）均按同一规则产灵。`doDaylightCycle=false` 导致 dayTime 冻结时，产出 SHALL 恒定为该时刻的速率（属预期）。月相缩放字段 SHALL 预留但默认不参与计算。

#### Scenario: 下界照产
- **WHEN** 成型的日轮核心位于下界且 dayTime 处于白天区间
- **THEN** 按同一线性时间线照常产灵

#### Scenario: 时钟冻结恒定产出
- **WHEN** `doDaylightCycle=false` 且 dayTime 停在正午
- **THEN** 日轮持续以峰值速率产灵

### Requirement: GUI 信息展示
仪式核心 GUI 的信息区 SHALL 展示一行短状态（当前实际产灵 x/s 与所处时段），数值 SHALL 紧凑化（`compact`）、明细 SHALL 置于悬浮 tip 以遵守信息行宽度红线；其下 SHALL 固定展示该仪式的由来诗（zh/en 双语）。因 pattern 无 `requirements`，MUST NOT 渲染通用祭品清单或额外的产灵/容量数值行。

#### Scenario: 打开成型日轮界面
- **WHEN** 玩家打开成型日轮核心界面
- **THEN** 信息区显示当前产灵与时段一行、悬停出明细，其下为由来诗，无通用清单行
