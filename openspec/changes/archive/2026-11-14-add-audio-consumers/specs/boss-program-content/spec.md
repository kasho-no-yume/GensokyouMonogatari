## ADDED Requirements

### Requirement: 大妖精花海音效契约

花之海洋 SHALL 在每朵花甩出时播放一次 `boss.burst`：每轮 16 朵即 16 声。
整轮花海爆裂 SHALL 只播放一次 `warning.shift`，MUST NOT 按花朵或爆散批次重复。
花环、雨季 MUST NOT 新增符卡音效。
默认攻击墙 SHALL 每组播放一次 `shot.normal`（玩家普通弹的「啵」），MUST NOT 按 25 颗弹重复。

#### Scenario: 一轮花海的音效次数

- **WHEN** 花之海洋完成一轮（16 朵花甩出并各自爆散）
- **THEN** 可听到 16 次 `boss.burst` 和 1 次 `warning.shift`

#### Scenario: 非花海卡保持静音

- **WHEN** 大妖精处于花环或雨季符卡
- **THEN** 该卡 MUST NOT 新增 `boss.burst` / `warning.shift` 播放

#### Scenario: 默认攻击墙每组一声

- **WHEN** 默认攻击墙一组生成 25 颗弹
- **THEN** 只播放一次 `shot.normal`

### Requirement: 黑谷山女蛛丝与螺旋音效契约

会留尾珠的蛛丝母弹 SHALL 每批播放一次 `boss.burst`：捕获之网每轮 10 批、
石窟蜘蛛巢每轮 13 批。MUST NOT 按母弹根数或尾珠数重复。

螺旋 SHALL 每波播放一次 `boss.burst`：瘴气充溢每轮 5 波、原因不明之热病每轮 6 波。
同一波按目标槽与左右臂建立的多个组 MUST 合并为一次播放。

追丝扇、石窟吐丝、毒雾 MUST NOT 新增符卡音效。
默认底噪蛛丝扇 SHALL 每组播放一次 `shot.normal`（玩家普通弹的「啵」），
MUST NOT 按扇内 3 颗弹重复。

#### Scenario: 蛛丝一组一次

- **WHEN** 捕获之网的一拍生成 6 根会留尾珠的蛛丝母弹
- **THEN** 只播放一次 `boss.burst`，不按 6 根母弹或后续尾珠重复

#### Scenario: 螺旋一波一次

- **WHEN** 热病的一波为 5 个目标槽各建中心/左/右三条螺旋（共 15 组）
- **THEN** 该波只播放一次 `boss.burst`

#### Scenario: 默认底噪扇每组一声

- **WHEN** 默认底噪蛛丝扇一组生成 3 颗弹
- **THEN** 只播放一次 `shot.normal`

#### Scenario: 非契约发射保持静音

- **WHEN** 山女发射追丝扇、石窟吐丝或毒雾
- **THEN** MUST NOT 播放新增的符卡音效
