## ADDED Requirements

### Requirement: bousen 渲染态字段语义

`RitualRenderState` SHALL 新增 kind `bousen`（忘川灯坛），其辅助字段解释如下，MUST NOT 复用既有 kind 的字段语义：

- `enabled` = 仪式启停态
- `tier` = 结构等级（1/2/3）；客户端据此取该阶 pattern 切片本地推导蜡烛坐标
- `movingMask` = **蜡烛点亮位掩码**，bit i = 该阶蜡烛规范序中第 i 根处于点亮态。bit i 在服务端与客户端 MUST 指向同一根蜡烛
- `minY` / `maxY` / `period` / `inCount` = **不使用**
- `linkPos` = **不使用**

蜡烛总数为 16 / 32 / 64，恰为 1 / 2 / 3 个位掩码所需宽度；「全亮」由客户端以 `movingMask == 该阶满掩码` 自行判定，**MUST NOT** 为此另占标志位。满掩码计算 MUST 特判 64：Java 的 `1L << 64` 等于 `1L`（移位数按 64 取模），直接写 `(1L << n) - 1` 在 `n == 64` 时得 `0` 而非 `-1`。

蜡烛坐标 MUST NOT 经 `linkPos` 承载：`MAX_CHANNELS` 为 64，64 根恰好顶满、零余量，且既有 `forgeBurnMask` 类构造已占用 bit0 作标志位，将来增设第 65 根蜡烛会静默截断。坐标一律由客户端读**同一份**已同步的 pattern JSON 推导，沿用 `KIND_REIYOKU` 因 433 格占地超限而确立的既定路径。

#### Scenario: 全亮由客户端自行判定

- **WHEN** 客户端收到 `bousen` 渲染态，`movingMask` 等于该阶满掩码且 `enabled` 为真
- **THEN** 客户端判定为产灵中并绘制坛面铺光，无须服务端额外下发标志位

#### Scenario: 三阶满掩码不为零

- **WHEN** 客户端为 3 阶（64 根蜡烛）计算满掩码
- **THEN** 结果为全 1 的 `long`，MUST NOT 因移位取模而得到 0

#### Scenario: 坐标不进 linkPos

- **WHEN** 审查 `bousen` 渲染态的构造
- **THEN** `linkPos` 为空数组，64 根蜡烛坐标由客户端从已同步的 pattern 切片推导，未顶满 `MAX_CHANNELS`

#### Scenario: 位序跨端一致

- **WHEN** 服务端熄灭第 k 根蜡烛并推送新掩码
- **THEN** 客户端据此变为淡红的蜡烛，与服务端实际改写为熄灭的是同一根

## MODIFIED Requirements

### Requirement: 仪式渲染态共享通道

仪式核心 SHALL 以单一 `RitualRenderState` 通道向客户端同步运行态特效所需最小状态：`kind`（none/relay/kagutsuchi/bafang/sacrifice/wujinzang/kanayamahiko/seii/summon/reiyoku/bousen）、`enabled`、`tier`、按 kind 复用的辅助字段（包围盒、位坐标列表、方向位图）。通道 MUST 保持既有不变量：仅状态变化时推送、稳态零持续包、区块首次载入即下发、未 enabled/结构失效下发清零态。kind 语义 MUST 相互独立：任一 kind 的字段解释变化 MUST NOT 影响其他 kind 的读取。服务端结算 MUST NOT 读取本通道数据。

各 kind 的辅助字段解释如下（新增 kind MUST 在此登记，MUST NOT 复用既有 kind 的字段语义）：

- `relay`：`linkPos`=链接目标（in 前 out 后，与 `movingMask` 位序一一对应），`minY`/`maxY`=螺旋纵向范围，`period`=结算周期
- `kagutsuchi`：`linkPos`=祭品台坐标（规范序，可能为空），`movingMask` bit0=燃烧中，`maxY`=结构水平半径（格）
- `bafang`：仅 `enabled`+`tier` 有意义
- `sacrifice`：`minY`=光柱高度（格），`maxY`=剩余刻，`period`=色索引（0..6）
- `wujinzang`：`minY`/`maxY`=结构 Y 范围（雾带高度），`linkPos`=底座激光锚点（绝对坐标）
- `kanayamahiko`：`linkPos`=祭品台坐标（规范序），`maxY`=结构水平半径（格），`movingMask` bit0=存在燃烧任务、bit(i+1)=第 i 台位燃烧
- `seii`：`enabled`=演出中，`minY`=起始 gameTime，`maxY`=演出总时长（tick）
- `summon`：`enabled`=会话进行中，`movingMask`=相位序号，`minY`=爆散起始 gameTime，`maxY`=爆散时长，`period`=降临光柱保持时长
- `reiyoku`：`enabled` 是**唯一**门控（运行态表现与缓存有无、玩家在场与否无关）；`tier`=结构等级，客户端据此取该阶 pattern 切片；`minY`/`maxY`=结构**绝对世界 Y** 范围（与 `RitualCoreBlockEntity.structureMinY/MaxY` 同源，**不是**相对核心的偏移——BER 局部系原点在核心方块，客户端 MUST 先减核心 Y 再当偏移用）；`linkPos`/`inCount`/`movingMask` **不使用**（无逐台通道）
- `bousen`：`enabled`=仪式启停态；`tier`=结构等级（客户端据此取该阶 pattern 切片推导蜡烛坐标）；`movingMask`=**蜡烛点亮位掩码**（bit i = 规范序第 i 根蜡烛点亮，1 阶占 4 位 / 2 阶 5 位 / 3 阶 6 位）；`minY`/`maxY`/`period`/`inCount`/`linkPos` **不使用**。蜡烛坐标 MUST NOT 进 `linkPos`（`MAX_CHANNELS` 恰为 64，64 根零余量且 bit0 位惯例已被占用），一律由客户端从已同步 pattern 切片推导。「全亮」由客户端以掩码比对满掩码自行判定，MUST NOT 另占标志位；满掩码计算 MUST 特判 64（`1L << 64 == 1L`，直接移位会得 0 而非全 1）

位宽 `long` 上限 `MAX_CHANNELS`（当前 64）条，超限由构建侧截断。

任何新增 kind 若其表现**完全由已同步的静态描述**（仪式 pattern JSON 的已展开方块表）推导而来，MUST NOT 因此新增 payload 字段、渲染态字段或 tick 广播；此类判断 SHOULD 由 kind 唯一定位 patternId 后经客户端数据通道读取**同一份** pattern JSON，MUST NOT 在客户端硬编码几何坐标或引入第二套语义（客户端与服务端各自独立求解的判断 MUST 逐条等价）。`shouldRenderOffScreen` **不豁免视锥**（NeoForge 走 `frustum.isVisible(renderer.getRenderBoundingBox(be))`），故特效作用域超出默认包围盒的 kind MUST 在 `getRenderBoundingBox()` 单独开分支。

#### Scenario: 多仪式共用零额外包

- **WHEN** 迦具土持续燃烧、共鸣塔持续路由、八方归元持续运行，各状态均无变化
- **THEN** 三种核心的渲染态包仅在各自状态翻转时刻推送，稳态期间网络零渲染态包

#### Scenario: kind 判别绘制

- **WHEN** 客户端 BER 收到某核心的渲染态
- **THEN** 按 kind 分发出对应特效（火柱/雾带闪电/灵气球/灯火），MUST NOT 依赖反查服务端结构或注册表

#### Scenario: 新增 kind 不挤占既有语义

- **WHEN** 新增一种渲染态 kind 并复用 `minY`/`maxY` 承载与既有 kind 不同的含义
- **THEN** 既有各 kind 的读取结果完全不变

#### Scenario: 本地推导的占地零新增同步

- **WHEN** 某新 kind 的表现范围完全由已同步 pattern 切片推导
- **THEN** 不存在为该表现新增的 payload 字段、渲染态字段或 tick 广播

#### Scenario: 大作用域不被视锥剔除

- **WHEN** 某 kind 的表现水平范围达 ±14 格、超出默认包围盒半径
- **THEN** 该 kind 的渲染包围盒单独取值，玩家从结构外侧观察时特效不消失

#### Scenario: 逐烛状态只走掩码

- **WHEN** 忘川灯坛的蜡烛明灭发生一次
- **THEN** 渲染态以一个 `long` 掩码的翻转表达，MUST NOT 为 64 根蜡烛各下发一份逐烛状态

### Requirement: 网格优先的特效总则

仪式运行态特效的主体 SHALL 由客户端逐帧网格几何 + 加法混合 RenderType（`DanmakuRenderTypes` 族）构成；原版粒子仅 MAY 作为低频氛围点缀。任何运行态特效 MUST NOT 以服务端 `sendParticles` 作为主体载体，MUST NOT 需要按数量设置的显式预算上限来防性能崩坏（每核心绘制量 SHALL 为阶级有界的常数）。加法混合外壳 MUST NOT 写深度。

**已同步的静态描述**（仪式 pattern JSON 的已展开方块表）与**已同步的方块实体状态**（祭品台持有的物品等）可在客户端**本地推导**出来的运行态表现（例如"哪些祭品台放着合格物品"、"哪些蜡烛处于点亮态"），MUST NOT 因此新增 payload、新 kind 字段或 tick 广播；此类表现的判据 SHALL 与服务端判据保持一致，MUST NOT 为省一次推导而引入第二套语义。

#### Scenario: 主体绘制零网络

- **WHEN** 一名玩家独自在 5 阶仪式附近观察满配特效
- **THEN** 特效全量渲染且该玩家与服务器之间无逐 tick 粒子包

#### Scenario: 本地推导不引入同步

- **WHEN** 审查某运行态表现的实现，确认其数据全部来自已同步的 pattern JSON 与方块实体状态
- **THEN** 不存在为该表现新增的 payload、渲染态字段或 tick 广播

#### Scenario: 跨端判据一致

- **WHEN** 客户端本地判据与服务端判据都被审查
- **THEN** 二者的判定条件逐条对应（同一物品类型谓词、同一阶级比较），差异 MUST NOT 存在
