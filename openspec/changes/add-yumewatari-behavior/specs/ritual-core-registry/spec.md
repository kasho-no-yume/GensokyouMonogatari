# ritual-core-registry Delta Spec

## ADDED Requirements

### Requirement: 按图案遍历维度内成型核心
注册表 SHALL 提供"按 patternId 遍历整个 `ServerLevel` 全部登记条目"的查询（不依赖中心点与半径），返回现场校验通过的核心实例列表；校验失败的条目 SHALL 与既有范围查询同语义被惰性剔除。该查询 MUST NOT 要求注册表持久化或跨维度泄漏（仍以 `ServerLevel` 为界）。

#### Scenario: 跳夜结算发现全部候选
- **WHEN** 维度内睡眠跳夜事件触发，结算侧按 `gensokyou:yumewatari_circle` 遍历
- **THEN** 该维度全部成型梦渡核心恰各返回一次，其他图案核心不返回

#### Scenario: 陈旧条目即时剔除
- **WHEN** 遍历遇到坐标处核心已被移除的登记条目
- **THEN** 该条目被剔除且不返回，同次遍历其余条目不受影响

#### Scenario: 未创建注册表时为空
- **WHEN** 查询从未注册过任何核心的维度
- **THEN** 返回空列表且不创建注册表实例
