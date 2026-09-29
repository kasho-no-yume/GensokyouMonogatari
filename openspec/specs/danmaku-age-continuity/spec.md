# danmaku-age-continuity Specification

## Purpose
TBD - created by archiving change danmaku-age-continuity. Update Purpose after archive.
## Requirements
### Requirement: 弹体年龄在存档 / 读档后双端连续

弹体的运动学与终止判据 SHALL 由「年龄」而非「本进程内的 tick 计数」驱动。年龄 SHALL 覆盖以下全部判据，且**每一处** MUST 使用同一个年龄取值：存活时长、悬停、速率曲线、越过发射点销毁、编队帧解析位置、分裂时刻、相位隐藏态、溜め待命期。

年龄 MUST NOT 直接取自原版实体的 tick 计数——该计数**既不进存档、也不同步给客户端**（`Entity.addAdditionalSaveData` 不写它；`Entity.recreateFromPacket` 不设它；两端仅由 `Level.tickNonPassenger` 各自自增）。故「按 tick 计数推导」这一既有准入判据的前提不成立。

实现 SHALL 以「年龄基准 + 本地 tick 计数」得出年龄。年龄基准分两侧，**互不共享**：

- **服务端**：由存档恢复的只读权威值。正常发射时为 0；重新获取（NBT 往返）时为存档中的年龄。此后 MUST NOT 被任何客户端数据改写
- **客户端**：该客户端**本次配对**时由服务端下发的年龄。收到后 MUST NOT 再次变更

年龄基准 MUST **每客户端一份**，MUST NOT 使用跨客户端共享的单一同步值。两个客户端配对于不同时刻时，共享值无法同时满足——改一次就弄坏另一个。

客户端年龄基准 MUST 经**每客户端配对包**下发，MUST NOT 走 `SynchedEntityData`。理由：配对 bundle 携带的 entityData 是 `ServerEntity` 构造时的**快照**（`this.trackedDataValues = entity.getEntityData().getNonDefaultValues()`），不是配对时刻的值；在配对时刻 `set` 的字段要等下一次脏更新才到达客户端，滞后可达 `updateInterval` 个 tick。客户端首个 tick 就会以错误年龄调用 `setPos(解析位置)`，产生可见闪跳。

同时 MUST NOT 使用 `ClientboundAddEntityPacket` 的 `data` 字段承载年龄：`Projectile.getAddEntityPacket` 以它下发 owner 实体 id，客户端用 `getEntity(packet.getData())` 反查。

由此，服务端在年龄 `T` 时配对的客户端从 `T` 起逐 tick 递增，两端在**任意年龄**上得到逐位相同的弹位。

**「客户端重新获取实体」是本要求的适用范围，而非仅限「世界读档」。** 客户端实体的年龄归零，触发条件是客户端丢掉该实体、之后又重新拿到。下列路径 MUST 全部落在本要求内：

- 世界存档后重新载入
- 玩家走出 `clientTrackingRange` 后再回到范围内
- 客户端卸载区块而服务端仍在加载（模拟距离小于追踪距离时必然发生）
- 末影箱 / 跨维度 / 传送 / 重生
- 弹所在区块被卸载后重新加载（BOSS 战横跨区块边界时）

服务端在这些路径下的状态是一致的：**服务端那份实体一直未被销毁、一直在 tick**。故年龄连续 MUST NOT 以「是否发生过 NBT 往返」为条件——那在服务端侧根本不是可区分的两种状态。

MUST NOT 采用「重新获取时销毁该弹」作为替代方案：它把「弹在抖」换成「弹没了」，且触发频率从「读档一次」升到「每场战斗数十次」（每次后撤再贴脸）。存续判据 MUST 与丢失原因无关。

#### Scenario: 读档后编队弹不再失步

- **WHEN** 一批挂编队帧的弹在飞行途中被存档，世界重新载入后玩家进入该区域
- **THEN** 客户端与服务端在同一墙上时刻推进到**同一年龄**，两端弹位逐位相同
- **AND** 位置纠偏 MUST NOT 被触发，MUST NOT 出现「客户端按自己的解析轨迹走、又被拽回」的往复

#### Scenario: 后撤再贴脸后年龄仍连续

- **WHEN** 一枚编队弹正飞向玩家，玩家后撤使其走出 `clientTrackingRange`，随后走回范围内
- **THEN** 客户端重新获得的该弹，其年龄与服务端一致，MUST NOT 从 0 起算
- **AND** 该弹 MUST NOT 被销毁

#### Scenario: 两名客户端配对于不同时刻

- **WHEN** 客户端 A 于服务端年龄 50 时开始跟踪某弹，客户端 B 于年龄 120 时才开始跟踪
- **THEN** A 的年龄为 50 起算、B 的年龄为 120 起算，两端各自与服务端逐 tick 相等
- **AND** B 开始跟踪 MUST NOT 使 A 的年龄发生跳变

#### Scenario: 客户端卸载区块后重新加载

- **WHEN** 服务端仍在加载某区块、而客户端因模拟距离卸载了它，随后客户端重新加载该区块
- **THEN** 客户端重建的弹幕与服务端处于同一年龄，两端弹位逐位相同

#### Scenario: 跨区块边界的 BOSS 战

- **WHEN** 一场 BOSS 战持续到弹所在的区块被卸载后重新加载
- **THEN** 重载后的弹幕 MUST 按其真实年龄继续飞行，MUST NOT 出现位置反复被拽的往复

#### Scenario: 配对包必须在客户端首个 tick 之前到达

- **WHEN** 客户端收到生成包后构造该弹幕实体
- **THEN** 其年龄基准 MUST 在该实体的首次 tick 之前已就位，MUST NOT 出现首个 tick 以错误年龄调用 `setPos(解析位置)`

#### Scenario: 读档后速率曲线弹双端同速

- **WHEN** 一枚带速率曲线的弹在飞行途中被存档并重载
- **THEN** 双端在该年龄上取到相同的速率，MUST NOT 出现「客户端用初速猛冲、服务端已减速到近乎静止」

#### Scenario: 正常发射路径行为不变

- **WHEN** 一枚弹被正常发射（未经存档）
- **THEN** 其年龄与本进程内 tick 计数逐位相等，行为与本变更之前完全一致

#### Scenario: 读档后寿命按真实年龄扣减

- **WHEN** 一枚存活时长被覆写为 200 tick 的弹，在第 150 tick 时被存档并重载
- **THEN** 它在重载后 50 tick 销毁，MUST NOT 重新获得完整的 200 tick 寿命

#### Scenario: 读档后分裂时刻不提前

- **WHEN** 一枚分裂弹在第 30 tick 触发分裂的设定下，于第 10 tick 被存档并重载
- **THEN** 分裂发生在重载后的第 20 tick，MUST NOT 在重载后的第 30 tick 发生

#### Scenario: 读档后相位隐藏不错位

- **WHEN** 一枚带相位隐藏的弹在飞行途中被存档并重载
- **THEN** 双端的显隐相位一致，MUST NOT 出现「客户端与服务端交替闪烁」

#### Scenario: 缺失年龄基准的旧存档

- **WHEN** 读取一个不含年龄基准键的旧存档
- **THEN** 年龄基准取缺省值，读档得到的飞行中弹退化为与本变更之前相同的行为
- **AND** 系统 MUST NOT 因此报错或拒绝加载

### Requirement: 年龄偏移的可判定测量

弹幕管线 SHALL 提供年龄偏移的测量能力，用以判定「双端年龄是否对齐」。该测量是本变更后续一切修法的**准入闸门**。

测量 SHALL 至少覆盖：

- **年龄偏移量**——弹的解析自变量在本端与在权威端之差，单位 tick；SHALL 给出最小值、中位数与 p95
- **来源分类**——SHALL 区分「本次会话新发射」与「由存档 / 重新获取而重建」两类弹，因为这是本缺陷唯一的判别特征。判据 SHALL 为年龄基准是否非 0，且**两侧判据同构**（服务端读其存档恢复值，客户端读其配对包值），故两侧读数可直接对比

测量 SHALL 随既有弹幕诊断一并输出。该读数 MUST NOT 与「网络滞后」混为一谈：年龄偏移恒为非零即说明双端自变量不一致，与延迟无关。

**「重建」类 MUST NOT 以世界读档为唯一验证途径。** 判定该缺陷存在一条不依赖存读档的路径：让一枚编队弹或带速率曲线的弹飞行，玩家后撤至其走出 `clientTrackingRange` 再返回。诊断 MUST 能在该情形下复现非零年龄偏移——这是多人游玩中最高频的触发方式，验证 MUST NOT 依赖一次存档退出。

#### Scenario: 判据可依数据作出

- **WHEN** 需要判断「双端年龄是否对齐」
- **THEN** 可读到「重建」类弹与「新发射」类弹各自的年龄偏移分布，据此作出判定

#### Scenario: 分布而非单点

- **WHEN** 读取年龄偏移统计
- **THEN** 输出 SHALL 同时包含最小值、中位数与 p95，MUST NOT 只给单一均值

#### Scenario: 正常游戏下新发射类恒为零

- **WHEN** 一局游戏内从未发生读档、且观察的弹始终在追踪范围内
- **THEN** 「新发射」类弹的年龄偏移 SHALL 为零偏附近，据此可把「年龄失步」与「网络延迟」区分开

#### Scenario: 后撤再贴脸即可复现

- **WHEN** 一枚编队弹飞向玩家，玩家后撤至其走出追踪范围再返回，全程不存档
- **THEN** 该弹被归入「重建」类，且其年龄偏移读数显著非零

### Requirement: 编队帧解析求值的 tick 编号

编队帧 SHALL 按「本 tick 的年龄」求值解析位置，MUST NOT 使用任何对本 tick 编号多加或少加一次的换算。

原版 `Entity.tick()` 及其 `baseTick()` **不**自增 `tickCount`；自增发生在 `Level.tickNonPassenger` 调用 `entity.tick()` **之前**（`ServerLevel` 与 `ClientLevel` 皆如此）。故在弹体自身的 tick 体内，`tickCount` **已经是本 tick 的编号**，代码中 MUST NOT 再对其加一。

#### Scenario: 出生当 tick 使用 t=0

- **WHEN** 一枚编队弹在年龄 0 的那一 tick 推进
- **THEN** 其解析位置取自 `framePositionAt(0)`，MUST NOT 取自 `framePositionAt(1)`

#### Scenario: 双端编号一致

- **WHEN** 双端各自推进一枚编队弹
- **THEN** 两端在同一年龄上求值同一个 tick 编号，MUST NOT 因换算差异而分叉

### Requirement: 编队弹推进方向的存档完整性

一枚挂了编队帧的弹，其「沿弹道推进」项的方向 MUST 在存档与读档后保持不变。该方向由发射时记录的方向轴承载。

方向轴 MUST 纳入存档的读与写，其写入条件 MUST NOT 比「是否挂编队帧」更窄。具体地：挂编队帧的弹 MUST 存方向轴，**无论**它是否同时挂了速率曲线。

#### Scenario: 有帧无曲线时方向轴不丢失

- **WHEN** 一枚挂编队帧但未挂速率曲线的弹被存档并重载
- **THEN** 其推进方向与存档前一致，MUST NOT 回落到世界坐标 +Z 方向

#### Scenario: 方向轴缺失时按缺省读入

- **WHEN** 读取一个不含方向轴键的旧存档中「有帧无曲线」的弹
- **THEN** 方向轴取缺省值，读档后的行为与本变更之前相同，MUST NOT 报错

