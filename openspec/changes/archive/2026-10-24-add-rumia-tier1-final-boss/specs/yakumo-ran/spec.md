## MODIFIED Requirements

### Requirement: 默认攻击

八云蓝在正式程序内容中 MAY 不设独立于符卡表的常驻默认攻击轨（狐火）；其狐火由各张符卡的卡内节拍承担，战斗连续性 MUST NOT 依赖独立底噪。

若未来恢复独立默认攻击轨，其频率与参数 SHALL 从 config 读取，MUST NOT 硬编；该轨 MUST NOT 计入符卡轨道数。默认攻击 SHALL 作为低频、可辨识的底噪，与任意符卡叠加运行，且 MUST NOT 覆盖当前符卡的主视觉或否定其安全区。

#### Scenario: 无独立底噪
- **WHEN** 八云蓝处于任一符卡中
- **THEN** 战斗压力由该符卡的轨道承担，MUST NOT 要求存在独立狐火底噪，也不得因此静默无攻击

#### Scenario: 若恢复默认攻击
- **WHEN** 未来重新启用独立狐火底噪
- **THEN** 底噪仍不得夺取当前符卡的主视觉，且不得否定其安全区

#### Scenario: 不占符卡轨道
- **WHEN** 校验任一张符卡的轨道数
- **THEN** 该计数 MUST NOT 包含默认攻击轨
