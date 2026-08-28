## ADDED Requirements

### Requirement: 个人灵力池
每名玩家 SHALL 拥有灵力数据（当前值/上限/淬炼等级），随存档持久化；死亡时当前值清零而上限与等级保留；自然回复速率受配置与等级影响。

#### Scenario: 死亡规则
- **WHEN** 灵力 80/100 的玩家死亡并重生
- **THEN** 当前值 0，上限仍 100，可继续自然回复

#### Scenario: 回复
- **WHEN** 玩家静置（回复速率 2/s）
- **THEN** 当前灵力以约 2/秒增长至上限封顶

### Requirement: 灵力 HUD
客户端 SHALL 在 HUD 显示当前/上限灵力条；数值经服务端同步。

#### Scenario: 实时显示
- **WHEN** 玩家消耗或获得灵力
- **THEN** HUD 条与数字在 1 秒内反映新值

### Requirement: MuPower 效果
SHALL 注册 MuPower 状态效果：持有者所受 danmaku 伤害乘以 (分子+等级)/分母（默认 (3+amp)/2）；仅影响 danmaku 来源。

#### Scenario: 增伤生效
- **WHEN** 持有 MuPower I 的生物受 100 点弹幕伤害
- **THEN** 实际受到 200 点

#### Scenario: 与护盾叠加
- **WHEN** 同时持有 MuPower I 与 danmakuProtect III 受 100 点弹幕伤害
- **THEN** 按两倍率先后叠乘结算
