# audio-cue-system Specification

## Purpose
TBD - created by archiving change add-audio-cue-system. Update Purpose after archive.
## Requirements
### Requirement: 音效播放入口 MUST 唯一，存量调用点 MUST 受豁免清单约束

系统 MUST 提供唯一的生产音效播放入口（`audio` 包）。

在扫描范围——`client/`、`danmaku/`、`entity/`、`item/weapon/`、`network/`——内的生产代码
MUST NOT 在 `audio` 包之外出现以下调用或类型：
`playSound(`、`playLocalSound(`、`getSoundManager(`、`SimpleSoundInstance`、
`BuiltInRegistries.SOUND_EVENT`。

背景音乐 MUST 同样经由 `audio` 包的音乐句柄播放；扫描范围外的生产代码
MUST NOT 直接构造音乐 `SoundInstance`、MUST NOT 直接向声音引擎投递循环实例。

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

#### Scenario: 音乐绕过门面

- **WHEN** `audio` 包之外出现音乐实例的构造或直接投递
- **THEN** 该次校验 MUST 失败

### Requirement: 混音参数 MUST 集中在注册表

每个 cue MUST 在注册表中声明：声音事件 id、基准音量、基准音高、音高抖动范围、
最小重触发间隔（tick）、声源类别、是否定位、字幕键、占位音效 id、是否信息类。

同一 cue 在任何调用点播放时，实际生效的混音参数 MUST 相同。

注册表 MUST 支持按声音事件 id 反查 cue，供数据驱动的消费方使用。

#### Scenario: 同一 cue 多调用点音量一致

- **WHEN** 同一条 cue 从两处不同代码触发
- **THEN** 两次实际使用的音量与音高参数 MUST 相同

#### Scenario: 注册表可枚举且可按 id 反查

- **WHEN** 系统加载注册表
- **THEN** MUST 能枚举出全部 cue 及其完整参数用于一致性校验，
  且对每个 cue 的事件 id MUST 能反查到该 cue，未知 id MUST 返回空

### Requirement: 注册表与资源 MUST 四方一致

系统 MUST 在加载期校验以下四者一致，并 MUST 提供可离线执行的测试覆盖同一校验：

1. 每个 cue 在 `sounds.json` 有对应条目；
2. 该条目引用的每个音频文件在 classpath 上存在；
3. `sounds.json` 中不存在注册表未声明的孤儿条目；
4. mod 注册的声音事件 id 集合与注册表的事件 id 集合相等。

背景音乐事件 MUST 被同一套校验覆盖：

- 孤儿判定 MUST 把**音乐事件**视为已声明，MUST NOT 因音乐条目而报告孤儿；
- 音乐条目 MUST NOT 被要求提供字幕键（原版音乐同样没有字幕）；
- 每个音乐事件 MUST 已注册进声音事件注册表，且其引用的音频文件 MUST 存在；
- 背景音乐的声音事件清单 MUST 与 cue 清单**分开维护**，MUST NOT 合并为同一集合。

任一失败：MUST 记录 ERROR 并降级到占位音效，MUST NOT 抛异常；对应测试 MUST 失败。

#### Scenario: 缺少 sounds.json 条目

- **WHEN** 某 cue 在注册表中存在但 `sounds.json` 无对应条目
- **THEN** 系统 MUST 记录错误并降级到占位音效，且校验测试 MUST 失败

#### Scenario: 缺少音频文件

- **WHEN** `sounds.json` 引用的某个 `.ogg` 文件不存在
- **THEN** 系统 MUST 报告具体缺失的文件名，且校验测试 MUST 失败

#### Scenario: 存在孤儿条目或未注册事件

- **WHEN** `sounds.json` 含有一条注册表与音乐清单都未声明的事件，或某事件未被注册进声音事件注册表
- **THEN** 校验 MUST 报告该条目，且测试 MUST 失败

#### Scenario: 音乐条目不带字幕

- **WHEN** 某条音乐事件没有字幕键
- **THEN** 校验 MUST 通过，MUST NOT 报告缺少字幕

### Requirement: 重复触发 MUST 受限流约束

系统 MUST 对每条 cue 施加最小重触发间隔（以客户端 tick 计量），
并 MUST 对每个客户端 tick 的新增实例数施加上限。

被限流吞掉的触发 MUST NOT 排队补播，MUST NOT 抛异常。

信息类 cue（如弹幕变轨警示、符卡切换）MUST NOT 计入每 tick 新增上限。

#### Scenario: 同一 tick 内重复触发

- **WHEN** 在单个客户端 tick 内连续触发同一高速弹幕音效 100 次
- **THEN** 实际开始播放的次数 MUST 不超过由最小间隔与该 tick 上限推导出的上限

#### Scenario: 跨 tick 未达最小间隔

- **WHEN** 某 cue 的最小间隔为 N tick，在上次出声后不足 N tick 再次被触发
- **THEN** 该次触发 MUST 被吞掉，且 MUST NOT 进入任何补播队列

#### Scenario: 信息类 cue 不被挤掉

- **WHEN** 同一 tick 内已达新增上限，此时请求播放变轨警示
- **THEN** 该警示音 MUST 仍被播放

### Requirement: 资源缺失 MUST 降级而非静默

当目标音效资源不可用时，系统 MUST 播放该 cue 登记的占位音效。
若占位同样不可用，系统 MUST 静默并记录一次可诊断的日志，MUST NOT 抛出异常，
MUST NOT 中断调用方的游戏逻辑。

#### Scenario: 目标资源缺失

- **WHEN** 某 cue 的声音事件在客户端不存在
- **THEN** MUST 播放该 cue 登记的占位音效，且调用方逻辑继续执行

#### Scenario: 占位也不可用

- **WHEN** 目标与占位音效均不可用
- **THEN** MUST NOT 抛出异常，且 MUST 留下一条包含 cue 名称的日志

### Requirement: 持续音 MUST 支持任意时长且接缝无跳变

系统 MUST 提供持续音句柄，使同一素材可播放任意时长，且循环接缝 MUST 无可闻跳变。

淡入与淡出各自的时长 MUST 不短于 150 毫秒（20 Hz 更新下的 3 tick），
并 MUST 使用感知曲线而非线性振幅。

#### Scenario: 长时循环无跳变

- **WHEN** 持续激光音连续循环 60 秒
- **THEN** 任意一次循环接缝处的样本跳变 MUST 不超过整段 99.9% 分位

#### Scenario: 淡变时长下限

- **WHEN** 请求启动或停止一个持续音
- **THEN** 实际包络时长 MUST 不短于 150 毫秒

#### Scenario: 感知曲线

- **WHEN** 持续音处于淡入阶段
- **THEN** 振幅 MUST 按非线性感知曲线推进，MUST NOT 使用线性振幅

### Requirement: 持续音实例 MUST 可终止

每个持续音句柄 MUST 支持显式停止，重复停止 MUST 幂等。

此外系统 MUST 具备看门狗：实例连续若干 tick 未收到「仍在持续」的信号时
MUST 自行停止，MUST NOT 长期占用声道。

消费方在发射者死亡、切换武器、实体移除或客户端卸载时调用停止，
属于消费侧职责，不在本系统内判定。

#### Scenario: 显式停止

- **WHEN** 消费方对已启动的持续音调用停止，随后再次调用停止
- **THEN** 该实例 MUST 停止，且第二次调用 MUST NOT 抛异常

#### Scenario: 信号中断

- **WHEN** 持续音实例连续 10 tick 未收到存续信号
- **THEN** 该实例 MUST 自行停止，且其状态 MUST 表现为已停止

### Requirement: 音效 MUST NOT 影响游戏状态

音效播放 MUST NOT 参与命中判定、伤害计算、同步或存档。
音频子系统的任何失败 MUST NOT 传播到服务端，MUST NOT 打断演出或玩法流程。

#### Scenario: 音效期间判定不变

- **WHEN** 音效播放期间发生弹幕命中
- **THEN** 命中判定与伤害结果 MUST 与无音效时完全一致

#### Scenario: 音频异常不外溢

- **WHEN** 音频子系统在播放过程中内部失败
- **THEN** 调用方的玩法逻辑 MUST 继续执行，且 MUST NOT 向服务端抛出异常

### Requirement: 音频子系统 MUST NOT 依赖客户端类

音频子系统在未安装客户端播放后端时 MUST 表现为无操作。
专用服务器启动 MUST NOT 因音频子系统崩溃，MUST NOT 加载任何客户端类。

#### Scenario: 专用服务器启动

- **WHEN** 以专用服务器启动游戏
- **THEN** 音频子系统 MUST 可被加载与校验，且 MUST NOT 触发客户端类加载

#### Scenario: 无后端时的播放请求

- **WHEN** 在未安装客户端播放后端的环境中请求播放音效
- **THEN** 该请求 MUST 为无操作，MUST NOT 抛异常

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

### Requirement: 背景音乐 MUST 与非一次性音效分离

背景音乐 MUST NOT 进入一次性 cue 注册表。音乐的下列参数与一次性 cue 的对应机制
MUST 互不适用：

- 音乐 MUST NOT 受每 cue 最小重触发间隔与每 tick 新增实例上限约束；
- 音乐 MUST NOT 施加音高抖动；
- 音乐 MUST NOT 使用 cue 的占位音效降级机制（缺曲时应静音并留日志）；
- 音乐 MUST NOT 计入一次性音效的限流统计。

音乐 SHALL 使用独立的曲目目录与独立的音乐句柄，仅共享 `audio` 包的播放后端。

#### Scenario: 音乐不被限流吞掉

- **WHEN** 同一 tick 内已达一次性音效的新增上限
- **THEN** 音乐的开始与切换 MUST 不受影响

#### Scenario: 缺曲时的降级

- **WHEN** 某音乐的音频资源不可用
- **THEN** 系统 MUST 静音并留下含曲目名的诊断日志，MUST NOT 播放占位音效、MUST NOT 抛异常

