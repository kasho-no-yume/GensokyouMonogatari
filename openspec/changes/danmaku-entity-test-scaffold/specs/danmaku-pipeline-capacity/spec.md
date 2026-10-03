## ADDED Requirements

### Requirement: 弹幕实体的状态断言 MUST 可离线执行

测试设施 MUST 提供：最小 `Level` 实现、实体构造、tick 推进与轨迹记录、
存档往返、方块布局设置、同步位往返。

凡以构造弹体、推进 tick、读存档或同步位为形式的验收，
SHALL 能在离线测试中执行，MUST NOT 只能靠实机观察。

未实现的方法 MUST 抛异常，MUST NOT 返回假值。返回假值会让断言在错误的通过
路径上变绿，那比没有测试更危险，因为它消除掉了缺测试这个信号。

脚手架自身 MUST 先被证明有效：它 MUST 至少有一条在建立之前会失败的断言，
以证明它真的能构造实体、推进 tick、触发存档读写。

#### Scenario: 构造实体的年龄初值

- **WHEN** 测试构造一枚弹幕实体
- **THEN** 该实体的年龄 MUST 为 0，MUST 可被断言

#### Scenario: 未实现的方法不得返回假值

- **WHEN** 测试触达一个脚手架未实现的 `Level` 方法
- **THEN** 该方法 MUST 抛 `UnsupportedOperationException`

#### Scenario: 存档往返可执行

- **WHEN** 测试推进一枚弹若干 tick 后执行存读往返
- **THEN** 读回的状态 MUST 可被逐键断言

#### Scenario: 脚手架不进生产 classpath

- **WHEN** 生产构建被打包
- **THEN** 测试设施 MUST NOT 出现在产物中

#### Scenario: 脚手架自身先被证明有效

- **WHEN** 脚手架刚建立
- **THEN** 它 MUST 至少有一条在建立之前会失败的断言
