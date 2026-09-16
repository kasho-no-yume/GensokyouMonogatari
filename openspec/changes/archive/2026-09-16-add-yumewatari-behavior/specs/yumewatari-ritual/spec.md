# yumewatari-ritual Delta Spec

## ADDED Requirements

### Requirement: 合规床判定
梦渡之座 SHALL 以成型结构全量格位的 XZ 包围盒为仪式范围，在仪式核心同一 Y 平面上识别合规床：床方块属于 `#minecraft:beds`，且床头与床尾两格均位于包围盒矩形内。床以床头格为键唯一计数；MUST NOT 对床数量设结构外硬上限（可用空间即天然上限）。

#### Scenario: 双色床与 mod 床兼容
- **WHEN** 仪式范围内等高平面放置任意颜色原版床或进入 `#minecraft:beds` 标签的 mod 床
- **THEN** 均计为合规床

#### Scenario: 床跨出包围盒不计
- **WHEN** 一张床的床头在矩形内、床尾在矩形外
- **THEN** 该床不是合规床

#### Scenario: 高度不等不计
- **WHEN** 床位于仪式范围内但 Y 层不等于核心 Y 层
- **THEN** 该床不是合规床

### Requirement: 跳夜结算触发
当所在 `ServerLevel` 因玩家睡眠达标触发跳过夜晚（原版 `SleepFinishedTimeEvent` 派发）时，SHALL 对该维度内每个已成型梦渡之座核心各执行一次结算。结算 SHALL NOT 依赖仪式"启动"状态（成型即生效）；由 `/time` 等命令造成的时间变化 MUST NOT 触发结算。结构当晚失效（未成型/核心不存在）的仪式 MUST NOT 结算。原版在 `doDaylightCycle=false` 时不派发该事件，相应 MUST NOT 结算。

#### Scenario: 雷雨白天睡觉同样结算
- **WHEN** 雷雨天气白天玩家上床并成功触发跳时（原版允许并派发同一事件）
- **THEN** 合规床上睡眠生物照常结算产灵

#### Scenario: 跳夜由仪式外的床触发仍按合规床结算
- **WHEN** 玩家睡在仪式范围外的床触发跳夜，仪式范围内合规床上只有村民在睡
- **THEN** 梦渡核心按"仪式内合规床上睡眠生物数"结算（本例为玩家 0 + 村民数）

#### Scenario: 无人睡眠的时间设置不结算
- **WHEN** 管理员执行 `/time set day`
- **THEN** 任何梦渡核心不产灵

### Requirement: 睡眠生物快照计数
结算 SHALL 在事件派发当 tick 快照统计：占用者为 `isSleeping()` 且其 `getSleepingPos` 落在该合规床头/床尾格位的 `Player` 或 `Villager`。一张床至多计一个生物；名单外实体（猫、狐狸、其他 mod 实体）MUST NOT 计入。

#### Scenario: 玩家与村民同睡合规床
- **WHEN** 跳夜发生瞬间，3 张合规床上分别睡着一名玩家、一名村民、一名玩家
- **THEN** 计数为 3

#### Scenario: 空合规床不计
- **WHEN** 范围内另有 2 张合规床无人使用
- **THEN** 计数不变（空床不产灵）

### Requirement: 一次性产灵公式
每次结算 SHALL 产出 `睡眠生物数 × YUMEWATARI_PRODUCTION_PER_SLEEPER × 4^阶级` 灵力（基值默认 10000；阶级 = 当前匹配层级 0/1/2 → 每生物 10000/40000/160000），为一次性入账，MUST NOT 按 tick 持续产出。配置基项 SHALL 可调。

#### Scenario: 0 阶 4 生物
- **WHEN** 0 阶仪式结算时快照到 4 个睡眠生物
- **THEN** 本次产出 40000 灵力

#### Scenario: 2 阶 3 生物
- **WHEN** 2 阶仪式结算时快照到 3 个睡眠生物
- **THEN** 本次产出 480000 灵力（3 × 160000）

### Requirement: 缓存上限与溢出分流
梦渡缓存容量 SHALL 为 `YUMEWATARI_BASE_CAPACITY × 4^阶级`（默认 40000/160000/640000），并入核心容量按图案分派。结算入账 SHALL 先经普通 `receive` 填入缓存（截断到上限），溢出部分在槽内装有灵力核心时 SHALL 不限速直注该核心，核心也满（或无核心）时余量作废。内部产灵 MUST NOT 走路由速率账本（`extractRouted`/`receiveRouted`）。

#### Scenario: 0 阶缓存装得下
- **WHEN** 空缓存的 0 阶仪式结算产出 30000
- **THEN** 缓存入账 30000，无溢出

#### Scenario: 溢出直注灵力核心
- **WHEN** 空缓存 0 阶（上限 40000）结算产出 60000，槽内有剩余容量充足的灵力核心
- **THEN** 缓存 40000、核心 20000，注灵不受核心每秒速率限制

#### Scenario: 无核心时溢出作废
- **WHEN** 同上但槽位为空
- **THEN** 缓存 40000，其余 20000 作废，无报错

#### Scenario: 缓存半满时优先补缓存
- **WHEN** 缓存已有 35000、上限 40000，结算产出 10000，槽内有空核心
- **THEN** 缓存补满至 40000，仅溢出的 5000 进核心

### Requirement: 缓存自发注灵
梦渡之座属产能仪式：只要结构成型（MUST NOT 依赖"启动"状态），其缓存中的灵力 SHALL 每秒按槽内灵力核心的注灵速率上限自发转入该核心（与迦具土同一 carry 进位口径，防整除截断）；无核心、缓存为空或核心已满时该周期为零不报错。此通道与结算时的"溢出直注"并存：直注不受速率限制，自发注灵受速率限制。

#### Scenario: 跳夜次日间缓慢充核心
- **WHEN** 跳夜结算后缓存存有灵力、槽内装有未满的灵力核心，且无路由抽取
- **THEN** 每秒从缓存向核心转移至多"核心注灵速率"的灵力，缓存相应下降

#### Scenario: 被动生效不经启动
- **WHEN** 梦渡仪式从未被启动（无启停按钮，enabled 恒 false）
- **THEN** 自发注灵照常进行

#### Scenario: 核心已满为零副作用
- **WHEN** 缓存有余但槽内核心已满
- **THEN** 该秒转移量为零，缓存与核心均不变化

### Requirement: 供灵端点声明
梦渡之座 SHALL 声明 `spiritOutRatePerSecond = YUMEWATARI_OUT_RATE`（默认 1,000,000/秒，固定值，MUST NOT 随阶级变化），供万象共鸣路由抽取缓存；`spiritInRatePerSecond` SHALL 保持默认 0（不可被选为受灵汇）。

#### Scenario: 路由可选为源
- **WHEN** 万象共鸣寻找供灵源
- **THEN** 成型梦渡核心以 1000000/s 上限出现在候选中

#### Scenario: 不可作为汇
- **WHEN** 万象共鸣寻找受灵汇
- **THEN** 梦渡核心不在候选中

### Requirement: GUI 信息展示
梦渡核心 GUI 信息区 SHALL 仅有一条可见数值行——合规床数（含睡眠中数，床坐标明细置于悬浮 tip，遵守信息行宽度红线）；MUST NOT 展示产灵/容量数值行或通用清单行。其下 SHALL 固定展示五段式仪式由来诗（`gui.gensokyou.ritual.yumewatari.lore_1..5`，zh/en 双语），总可见行数控制在信息区免滚动容纳。非 `toggleable` 被动仪式的 GUI 右上角状态标签 MUST NOT 渲染"运行中/已停止"（无启停概念，误导为未启动），SHALL 显示中性正向的"已成型"。

#### Scenario: 信息栏只有床数与诗
- **WHEN** 玩家打开成型梦渡核心界面
- **THEN** 可见行 = 床数一行 + 五行传说文本，无其他数值行；悬停床数行出坐标 tip

#### Scenario: 被动仪式不显示已停止
- **WHEN** 打开任何非 toggleable 图案的成型核心 GUI
- **THEN** 右上角显示"已成型"（正向色），不出现"已停止"红字
