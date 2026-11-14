## MODIFIED Requirements

### Requirement: 切阶段音效
系统 SHALL 在符卡切换时通过统一音效门面播放一次 `spellcard.switch`。

音效 SHALL NOT 影响判定与伤害；音效播放失败 MUST NOT 打断演出或抛出到服务端。
系统 MUST NOT 继续直放原版占位音效；资源缺失时 MUST 由 cue 注册表的降级机制处理。

#### Scenario: 切换播放音效
- **WHEN** 某东方 BOSS 切换到新符卡
- **THEN** 客户端在该次切换后播放一次 `spellcard.switch`

#### Scenario: 音效不影响判定
- **WHEN** 声明音效播放期间发生弹幕命中
- **THEN** 命中判定与伤害结果与无音效时一致

#### Scenario: 缺失资源仍不中断
- **WHEN** `spellcard.switch` 的自制资源不可用
- **THEN** 系统按 cue 登记的占位音效降级，演出与判定继续，MUST NOT 抛出异常
