## ADDED Requirements

### Requirement: 一次性年龄闪亮

弹幕视觉状态 SHALL 能声明一个「闪亮年龄」：弹在出生后到达该年龄时统一提亮（放大）一帧，随后恢复常态。该闪亮 MUST NOT 周期性重复，MUST NOT 改变判伤或运动。

未声明闪亮年龄的弹 MUST 保持既有观感。

#### Scenario: 只闪一帧
- **WHEN** 一枚弹声明闪亮年龄 30
- **THEN** 它在年龄 30 附近短暂提亮一次，之后 MUST NOT 再次提亮

#### Scenario: 不影响判伤
- **WHEN** 一枚弹闪亮期间与玩家碰撞
- **THEN** 伤害结算与无闪亮时逐位相同

#### Scenario: 可选提示音只播一次
- **WHEN** 一拍的闪亮声明了提示音，且该拍在多个循环边界重放
- **THEN** 每次进入该拍 SHALL 至多播放一次提示音，MUST NOT 每 tick 重复播放

#### Scenario: 未声明即旧观感
- **WHEN** 一拍不声明闪亮年龄
- **THEN** 其观感与引入本能力前逐位相同
