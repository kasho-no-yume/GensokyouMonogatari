# danmaku-assembly-bench Specification

## Purpose
TBD - created by archiving change add-danmaku-assembly-bench. Update Purpose after archive.
## Requirements
### Requirement: 装配台方块与交互
模组 SHALL 提供「弹幕方术装配台」方块（含 BlockEntity）。玩家 SHALL 通过右击方块打开装入界面，无论手持何物；潜行不改变该行为，手持物品的 `useOn` MUST NOT 被触发。

#### Scenario: 右击打开界面
- **WHEN** 玩家右击装配台（空手或手持任意物品）
- **THEN** 打开装配台界面，不触发手持物品的使用/放置

#### Scenario: 潜行同样打开
- **WHEN** 玩家潜行右击装配台
- **THEN** 同样打开装配台界面，无特殊差异

### Requirement: 武器输入槽
装配台界面 SHALL 提供一个独立武器输入槽，仅接受弹幕主武器物品。武器 SHALL 存放在装配台的 BlockEntity 中并在世界重载后保持；核 MUST NOT 单独存放在 BlockEntity 中，核只随武器的 `weapon_slots` 数据组件存在。

#### Scenario: 放入武器
- **WHEN** 玩家把主武器放入武器输入槽
- **THEN** 武器存入装配台，三个核槽载入该武器当前的核配置

#### Scenario: 拒绝非武器
- **WHEN** 玩家尝试把非弹幕主武器的物品放入武器输入槽
- **THEN** 放入被拒绝

#### Scenario: 取走武器
- **WHEN** 玩家从武器输入槽取走武器
- **THEN** 三个核槽清空，核随武器数据一并带出，不额外返还或复制

### Requirement: 核槽即时写回与等级闸门
装配台界面的三个核槽 SHALL 为武器 `weapon_slots` 组件的镜像；任一核槽变更 SHALL 即时写回武器（服务端权威），并复用既有等级闸门与连带取核规则：武器等级由槽2等级核决定，取下/降级导致等级回落时，槽1/槽3 中 requiredTier 超标的核 SHALL 自动取出并返还玩家；当前武器等级不足的核槽 SHALL 灰显且不可装入。武器输入槽为空时，核槽 MUST NOT 接受任何核。

#### Scenario: 即时写回
- **WHEN** 玩家向核槽装入一枚弹幕核
- **THEN** 菜单不关闭即写回武器数据，交换出的旧核返回玩家

#### Scenario: 等级回落连带取核
- **WHEN** 玩家把槽2的等级核换成更低等级，而槽1装有超标的弹幕核
- **THEN** 槽1的弹幕核被自动取出返还玩家

#### Scenario: 武器不在位
- **WHEN** 武器输入槽为空时玩家尝试向核槽放入核
- **THEN** 核槽拒绝放入并灰显

### Requirement: 装配台失效与破坏掉落
菜单 SHALL 在装配台被破坏、超出交互距离或缺失时失效关闭。装配台被破坏时，其内武器 SHALL 作为掉落物释放（核随武器数据一并带出）。

#### Scenario: 破坏释放武器
- **WHEN** 玩家破坏一个装有武器的装配台
- **THEN** 武器（含其核）作为物品掉落，不丢失

#### Scenario: 走远关闭
- **WHEN** 玩家在界面打开时离开装配台超过交互距离
- **THEN** 界面关闭，武器与核数据不丢失

