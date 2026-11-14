# boss-program-authoring Specification

## Purpose
TBD - created by archiving change migrate-boss-cards-to-programs. Update Purpose after archive.
## Requirements
### Requirement: 远程时钟恢复保持画面
已初始化远程显示时钟 SHALL 在不可信期间保留最后可求值姿态，最多按既有短容差推进后冻结，不无限外推或仅因时钟恢复整批隐藏。后续独立权威样本 SHALL 允许重锚校正；停止及移除事实 SHALL 继续清理副本。

#### Scenario: 恢复样本间隔一秒
- **WHEN** 时钟发生误差并需等待下一次心跳
- **THEN** 已存在弹幕保持可显示的冻结姿态，下次独立样本恢复运动而无时钟门控空白，End仍移除全部成员

### Requirement: 半透明隐藏态
普通弹和激光 SHALL 在隐藏阶段保持低透明度可见并降低亮度，同时完全跳过实体和方块碰撞检测；隐藏不得解释为彻底消失。作者 SHALL 能声明正常/隐藏周期。

#### Scenario: 隐藏后恢复
- **WHEN** 普通弹或激光依次进入正常、隐藏、正常阶段
- **THEN** 隐藏期间本体及激光附属层变暗透明、不派发碰撞，恢复阶段重新按伤害窗口派发碰撞，轨迹和寿命连续

### Requirement: 内容作者入口
框架 SHALL 提供统一标准操作环境和具名内容builder，封装节点ID、codec、handler和编译装配；作者 SHALL 显式选择攻击或装饰以及运动方向，框架 SHALL 自动推导保守派生需求并按独立预算拒绝超限。

#### Scenario: 无伤害和方向歧义
- **WHEN** 作者分别构造攻击弹、装饰弹和沿图案方向运动的扇形弹
- **THEN** 攻击窗口、无害窗口和逐弹方向符合声明，未知样式及超预算产生可定位错误

### Requirement: 通用包络与公共宿主
标准程序的兴趣包络 SHALL 覆盖预警、解析运动和标准重瞄/释放，不按预览名称特判。共享宿主 SHALL 支持同维度多战斗，切卡保留底噪并只清理当前战斗的卡，结束不影响其他战斗。现有BOSS在显式迁移前 SHALL 继续使用旧后端。

#### Scenario: 两场战斗独立结束
- **WHEN** 两个owner共用维度session，其中一个切卡或结束
- **THEN** 另一个owner的实例和成员保持，结束者的计划/成员/锁被回收

### Requirement: 可复用作者模板
框架 SHALL 提供齐射、复合花阵和移动母弹派生三个可编译、可预览模板及与真实API一致的作者指南，标准颜色和已注册弹型的选择 SHALL 不要求修改公共渲染分支。

#### Scenario: 模板内容闭环
- **WHEN** 使用相同作者API编译并执行三个模板
- **THEN** 数量、时序、伤害、方向、网络往返、兴趣包络及结束清场有自动化证据，且不迁移现有符卡

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

