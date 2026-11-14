## MODIFIED Requirements

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

## ADDED Requirements

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
