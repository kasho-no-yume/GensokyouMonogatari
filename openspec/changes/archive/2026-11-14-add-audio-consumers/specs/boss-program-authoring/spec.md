## ADDED Requirements

### Requirement: 表现音效 cue 的时钟域与去重

`Presentation.SoundCue` SHALL 支持四种时钟域，并按域定义播放基数：

- `MEMBER_AGE`：每个发射批次一次；
- `GROUP`：每组一次；
- `CYCLE`：每轮一次；
- `CARD`：每张卡一次。

渲染投影 MUST 为每条 cue 计算作用域标识；同一作用域内相同 `deduplicationKey`
的 cue MUST 只派发一次，即使该 cue 被多个批次同时求值。
作者 MUST NOT 依赖限流参数来合并同刻的多组音效。

#### Scenario: 十六批共享一条轮级 cue

- **WHEN** 一张卡的 16 个批次共享同一 member program，program 内含
  一条 `CYCLE 100` 的 cue
- **THEN** 第 100 tick 渲染投影只派发一个声音事件

#### Scenario: 批次级 cue 逐批派发

- **WHEN** 同一 member program 含一条 `MEMBER_AGE 0` 的 cue，并被 16 个批次共享
- **THEN** 渲染投影为每个批次各派发一个声音事件

#### Scenario: 同波多组共用一条轮级 cue

- **WHEN** 同一波按目标槽建立 5 个组，且共享 `CYCLE` 波 tick 上的同一条 cue
- **THEN** 该波只派发一个声音事件
