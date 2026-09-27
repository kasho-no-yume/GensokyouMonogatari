## MODIFIED Requirements

### Requirement: 祭品要求声明
祭品要求 SHALL 由 pattern 数据显式声明，绑定到具体祭品台格位：单条要求恒为 1 件，多件需求 SHALL 拆为多条要求绑定不同 slot。要求的消耗模式 SHALL 与所在仪式的生命周期语义相容：`periodic` 要求断供时框架会停机（`enabled` 置否），因此**持有闩锁的仪式 MUST NOT 使用 `periodic` 要求**；`on_activate` 要求依赖启动动作，**无需启停的仪式 MUST NOT 使用 `on_activate` 要求**。

`slot` 寻址 SHALL 以匹配结果的规范序（层自下而上、z 自北向南、x 自西向东）为准，SHALL 与 pattern 的对称展开方式无关。

**该机制 SHALL 仅用于「有序」祭品。** 若某仪式的祭品规则本应与台位无关（例如「若干件 A + 若干件 B，摆哪都行」），MUST NOT 用 per-slot 绑定表达——规范序在界面上完全不可见，per-slot 绑定会迫使玩家反推台位序号。此类规则 SHALL 由行为侧以**无序计数**实现，并自行给出带计数的核对行（先例：`BarrierBreakBehavior.Offerings`，两条 `CONTROL_ITEM` 行显示「奉上 3/4」）。

#### Scenario: 单件不变量
- **WHEN** pattern 声明某条祭品要求的数量大于 1
- **THEN** 加载期报错拒载，并提示拆为多条要求绑定不同 slot

#### Scenario: 闩锁仪式不得用周期供给
- **WHEN** 持有闩锁的仪式声明了 `periodic` 祭品要求
- **THEN** 该要求被禁止，理由为断供会触发框架停机并与闩锁语义冲突

#### Scenario: 无需启停的仪式不得用激活消耗
- **WHEN** pattern `toggleable` 为 false 的仪式声明了 `on_activate` 祭品要求
- **THEN** 该要求不可达，界面上的祭品核对项恒为不满足

#### Scenario: 无序祭品不走 per-slot 绑定
- **WHEN** 某仪式的祭品规则与台位无关（若干件 A + 若干件 B，任意摆放）
- **THEN** 该仪式不声明 pattern `requirements`，改由行为侧按计数判定，界面呈现带计数的行

## ADDED Requirements

### Requirement: 轮询式祭品门槛
祭品台承载物品于方块实体内，摆放或取走祭品 SHALL NOT 产生方块更新，因此依赖祭品正确性的**被动**仪式（无启动动作、`toggleable` 为 false）SHALL 以不低于 5 Hz 的频率轮询祭品正确性，MUST NOT 依赖方块更新或邻居通知。

轮询 SHALL 在核心成型存续期间执行，且 SHALL 在其他开启条件（如缓存充盈）已达成的状态下保持，以支持「条件达成后即时开启」。

#### Scenario: 补齐祭品即时生效
- **WHEN** 仪式已处于待开启状态，玩家把缺失祭品放上祭品台
- **THEN** 在不超过 5 Hz 的延迟内检测到条件满足并开启

#### Scenario: 摆放不产生方块更新
- **WHEN** 玩家把物品放上祭品台
- **THEN** 核心所在位置不产生方块更新，仪式仍能在下一次轮询内感知到变化

### Requirement: 闩锁仪式的祭品消耗
持有闩锁的仪式若其祭品规则要求消耗，则 SHALL 在闩锁置位的那一次动作中**一次性**扣除，SHALL NOT 分摊到后续周期。扣除 SHALL 遵循单件不变量（逐台扣 1 件），多放的份数 SHALL 保留。闩锁既成后 MUST NOT 再校验祭品状态。

#### Scenario: 开启即消耗
- **WHEN** 祭品齐备且闩锁置位
- **THEN** 按需求量一次性扣除，祭品台上不再保有对应物品

#### Scenario: 多放份数保留
- **WHEN** 某类祭品放了 6 件而需求为 4 件
- **THEN** 仅扣除 4 件，台面剩余 2 件不被清空

#### Scenario: 闩锁后不再校验
- **WHEN** 仪式闩锁已置位
- **THEN** 系统不再因祭品状态变化而改变任何结果
