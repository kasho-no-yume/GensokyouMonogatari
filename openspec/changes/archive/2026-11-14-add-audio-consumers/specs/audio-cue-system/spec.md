## MODIFIED Requirements

### Requirement: 音效播放入口 MUST 唯一，存量调用点 MUST 受豁免清单约束

系统 MUST 提供唯一的生产音效播放入口（`audio` 包）。

在扫描范围——`client/`、`danmaku/`、`entity/`、`item/weapon/`、`network/`——内的生产代码
MUST NOT 在 `audio` 包之外出现以下调用或类型：
`playSound(`、`playLocalSound(`、`getSoundManager(`、`SimpleSoundInstance`、
`BuiltInRegistries.SOUND_EVENT`。

既有调用点 MUST 迁移到 `audio` 包的门面；迁移完成前 MUST 登记在豁免清单中并标注理由。
清单 MUST 是**棘轮**：清单外的违规 MUST 使校验失败；清单内的文件若已不再违规，
保留条目亦 MUST 使校验失败。全部迁移完成后清单 MAY 为空，空清单 MUST 使校验通过。

裸 `SoundEvents.` 常量引用（材质装备音等注册用途）不在禁止之列。

#### Scenario: 清单外直接调用被拦截

- **WHEN** 静态扫描扫描范围内的生产代码，在 `audio` 包与豁免清单之外
  发现禁止模式
- **THEN** 该次校验 MUST 失败并指出违规文件与行号

#### Scenario: 修好一处却不删清单条目

- **WHEN** 豁免清单中的某文件已不含任何禁止模式，但清单条目仍在
- **THEN** 该次校验 MUST 失败

#### Scenario: 全部迁移后清单为空

- **WHEN** 扫描范围内已无任何直接播放调用，豁免清单为空
- **THEN** 校验 MUST 通过，且 MUST NOT 以“清单不应为空”为由失败

#### Scenario: 调用点不携带混音参数

- **WHEN** 任意玩法代码请求播放一次音效
- **THEN** 该调用 MUST 只传入 cue 与位置/实体，MUST NOT 传入音量或音高

## ADDED Requirements

### Requirement: 玩家武器每次扳机 MUST 播放一条弹种 cue

玩家主武器每次成功扳机 SHALL 播放一次与弹种对应的 cue。

散弹、扇射等一次触发多颗弹的形态 MUST 只播放一次，MUST NOT 按弹数重复播放。
激光炮（持续音型）SHALL 使用长、低沉的持续音（`laser.beam` 循环）作为开火反馈，
MUST NOT 叠加短促的一次性激光开火音；激光枪 SHOULD 只播放一次性开火音。
音效 MUST NOT 因灵力不足、冷却拒绝或未实际生成的弹幕而播放。

#### Scenario: 散弹一次扳机一声

- **WHEN** 散弹核一次扳机生成 5 颗弹
- **THEN** 客户端只收到一次 `shot.normal`，而不是 5 次

#### Scenario: 按弹种选择 cue

- **WHEN** 玩家分别用单发玉、麟弹、飞刀、灵符开火
- **THEN** 分别播放 `shot.normal`、`shot.fast`、`knife.throw`、`shot.normal`

#### Scenario: 灵力不足不响

- **WHEN** 玩家灵力不足，扳机被服务端拒绝且未生成任何弹
- **THEN** MUST NOT 播放开火音

#### Scenario: 激光炮只用长低沉持续音

- **WHEN** 玩家用激光炮开火并进入 ACTIVE
- **THEN** MUST 只启动 `laser.beam` 循环音，MUST NOT 同时播放短促的 `laser.shoot`

### Requirement: 持续音 MUST 以非零音量进入声音引擎

持续音实例在首次交给声音引擎时 MUST 提供大于零的音量；起音从 0 开始的包络
MUST 在客户端播放层夹到非零下限，MUST NOT 因 `SoundEngine` 的零音量跳过规则而静默。

#### Scenario: 起音第 0 tick 仍能入队

- **WHEN** 一条持续音在包络起音第 0 tick 被播放
- **THEN** 交给引擎的音量 MUST 大于 0，随后仍按不少于 3 tick 的包络起音

### Requirement: BOSS 普攻弹幕 MUST 按组播放普通弹 cue

大妖精默认攻击墙与山女默认底噪蛛丝扇 SHALL 每组播放一次 `shot.normal`
（玩家普通弹的「啵」）；同组内的多颗弹 MUST NOT 分别播放。
该音效 MUST 与普通程序 cue 一样经 `AudioCues.byId` 与 `Audio` 门面播放。

#### Scenario: 大妖精默认弹墙每组一声

- **WHEN** 默认攻击墙一组生成 25 颗弹
- **THEN** 只播放一次 `shot.normal`

#### Scenario: 山女默认蛛丝扇每组一声

- **WHEN** 默认底噪蛛丝扇一组生成 3 颗弹
- **THEN** 只播放一次 `shot.normal`

### Requirement: 程序演出音效 MUST 经 cue 注册表解析并用 Audio 门面播放

`Presentation.SoundCue` 的 `sound` 字段 MUST 按 cue 事件 id 解释，并经 `AudioCues.byId`
解析后交给 `Audio` 播放；MUST NOT 直接调用客户端声音管理器或原版注册表。

声音事件 MUST 携带发射批次的世界位置；定位与否由 cue 参数决定。
未知 cue id MUST 被跳过并留下可诊断日志，MUST NOT 抛异常或打断渲染。

#### Scenario: 程序 cue 解析为自定义音效

- **WHEN** 程序 presentation 在某一 tick 到达 `gensokyou:boss_burst`
- **THEN** 客户端通过 `AudioCues.byId` 取到 `BOSS_BURST` 并请求定位播放

#### Scenario: 未知 cue id

- **WHEN** 程序 cue 的 `sound` 不是已注册的 cue id
- **THEN** 该次播放被跳过并记录一次诊断，渲染循环继续

### Requirement: 程序演出音效去重粒度 MUST 跟随 SoundCue 时钟域

同一条 `Presentation.SoundCue` 的去重作用域 MUST 由其 `TimeDomain` 决定：

- `MEMBER_AGE`：每个发射批次一次；
- `GROUP`：每组一次；
- `CYCLE`：每轮一次；
- `CARD`：每张卡一次。

同一作用域内相同 `deduplicationKey` 的 cue MUST 只播放一次，即使它被多个批次同时求值。
跨作用域或跨轮的同名 cue MUST 分别播放。

#### Scenario: 卡级 cue 不被批次放大

- **WHEN** 同一 member program 被 16 个批次共享，且其中含一条 `CYCLE` 时钟的 cue
- **THEN** 该轮内该 cue MUST 只播放一次，而不是 16 次

#### Scenario: 批次级 cue 保持逐批

- **WHEN** 同一 member program 被 16 个批次共享，且其中含一条 `MEMBER_AGE 0` 的 cue
- **THEN** 每个批次各播放一次
