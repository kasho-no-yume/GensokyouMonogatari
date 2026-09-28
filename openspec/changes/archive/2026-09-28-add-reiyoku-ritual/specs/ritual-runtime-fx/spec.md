## MODIFIED Requirements

### Requirement: 仪式渲染态共享通道
仪式核心 SHALL 以单一 `RitualRenderState` 通道向客户端同步运行态特效所需最小状态：`kind`（none/relay/kagutsuchi/bafang/sacrifice/wujinzang/kanayamahiko/seii/summon/reiyoku）、`enabled`、`tier`、按 kind 复用的辅助字段（包围盒、位坐标列表、方向位图）。通道 MUST 保持既有不变量：仅状态变化时推送、稳态零持续包、区块首次载入即下发、未 enabled/结构失效下发清零态。kind 语义 MUST 相互独立：任一 kind 的字段解释变更 MUST NOT 影响其他 kind 的读取。服务端结算 MUST NOT 读取本通道数据。

各 kind 的辅助字段解释如下（新增 kind MUST 在此登记，MUST NOT 复用既有 kind 的字段语义）：

- `relay`：`linkPos`=链接目标（in 前 out 后，与 `movingMask` 位序一一对应），`minY`/`maxY`=螺旋纵向范围，`period`=结算周期。
- `kagutsuchi`：`linkPos`=祭品台坐标（规范序，可能为空），`movingMask` bit0=燃烧中，`maxY`=结构水平半径（格）。
- `bafang`：仅 `enabled`+`tier` 有意义。
- `sacrifice`：`minY`=光柱高度（格），`maxY`=剩余刻，`period`=色索引（0..6）。
- `wujinzang`：`minY`/`maxY`=结构 Y 范围（雾带高度），`linkPos`=底座激光锚点（绝对坐标）。
- `kanayamahiko`：`linkPos`=祭品台坐标（规范序），`maxY`=结构水平半径（格），`movingMask` bit0=存在燃烧任务、bit(i+1)=第 i 台位燃烧。
- `seii`：`enabled`=演出中，`minY`=起始 gameTime，`maxY`=演出总时长 tick。
- `summon`：`enabled`=会话进行中，`movingMask`=相位序号，`minY`=爆散起始 gameTime，`maxY`=爆散时长，`period`=降临光柱保持时长。
- `reiyoku`：`enabled` 为**唯一**门控（运行态表现与缓存有无、玩家在场与否无关）；`tier`=结构等级，客户端据此取该阶 pattern 切片；`minY`/`maxY`=结构的**绝对世界 Y** 范围（与 `RitualCoreBlockEntity.structureMinY/MaxY` 同源，**不是**相对核心的偏移——BER 局部系原点在核心方块，客户端 MUST 先减核心 Y 再当偏移用）；`linkPos`/`inCount`/`movingMask` **不使用**（无逐台通道）。

位宽 `long` 上限 `MAX_CHANNELS`（当前 64）条，超限由构建侧截断。

任何新增 kind 若其表现**完全由已同步的静态描述**（仪式 pattern JSON 的已展开方块表）推导而来，MUST NOT 因此新增 payload 字段、渲染态字段或逐 tick 广播；此类判据 SHOULD 由 kind 唯一定位 patternId 后经客户端数据通道读取**同一份** pattern JSON，MUST NOT 在客户端硬编码几何坐标或引入第二套语义（客户端服务端各自独立求解的判据 MUST 逐条等价）。`shouldRenderOffScreen` **不豁免视锥**（NeoForge 走 `frustum.isVisible(renderer.getRenderBoundingBox(be))`），故特效作用域超出默认包围盒的 kind MUST 为 `getRenderBoundingBox()` 单独开分支。

#### Scenario: 多仪式共用零额外包
- **WHEN** 迦具土持续燃烧、共鸣塔持续路由、八方归元持续运行，各状态均无变化
- **THEN** 三种核心的渲染态包仅在各自状态翻转时刻推送，稳态期间网络零渲染态包

#### Scenario: kind 判别绘制
- **WHEN** 客户端 BER 收到某核心的渲染态
- **THEN** 按 kind 分发出对应特效（火柱/雾带闪电/灵气球），MUST NOT 依赖反查服务端结构或注册表

#### Scenario: 新增 kind 不挤占既有语义
- **WHEN** 新增一种渲染态 kind 并复用 `minY`/`maxY` 承载与既有 kind 不同的含义
- **THEN** 既有各 kind 的读取结果完全不变

#### Scenario: 本地推导的占地零新增同步
- **WHEN** 某新增 kind 的表现范围完全由已同步 pattern 切片推导
- **THEN** 不存在为该表现新增的 payload 字段、渲染态字段或逐 tick 广播

#### Scenario: 大作用域不被视锥剔除
- **WHEN** 某 kind 的表现水平范围达 ±14 格、超出默认包围盒半径
- **THEN** 该 kind 的渲染包围盒单独取值，玩家从结构外侧观察时特效不消失
