# crystal-storage Specification (delta: add-wujinzang-ritual)

## ADDED Requirements

### Requirement: 绑定晶块交互锁定

被仪式绑定（携带 owner 维度与坐标）的无尽藏晶 SHALL 禁止玩家右键开箱，且 SHALL NOT 对任何方向暴露物品 handler capability，使玩家与自动化都必须经仪式核心读写。未绑定 owner 的独立晶块 SHALL 保持既有玩家界面与自动化接口。

#### Scenario: 绑定晶块不可开箱
- **WHEN** 玩家右键一块绑定了 owner 的晶块
- **THEN** 不打开存储界面

#### Scenario: 绑定晶块无 capability
- **WHEN** 任一方向向绑定晶块请求 `IItemHandler`
- **THEN** 返回空（不提供 handler）

#### Scenario: 独立晶块照旧
- **WHEN** 玩家右键或管道接入一块未绑定 owner 的晶块
- **THEN** 既有界面与自动化接口照常工作

### Requirement: 分区组与空闲晶块格式化

晶块的存储模式 SHALL 由持有它的仪式在格式化时决定；系统 SHALL NOT 提供 GUI 或物品途径供玩家手动改模式。零条目（空闲）晶块 MAY 被仪式重新格式化；含任一条目的晶块 MUST NOT 被改变模式。绑定晶块的模式与分组信息 SHALL 可由仪式核心侧读取与持久化。

#### Scenario: 空闲可格式化
- **WHEN** 仪式选中一块零条目晶块并要求其作为类型制使用
- **THEN** 该晶块被格式化为类型制并可写入

#### Scenario: 非空不可改模式
- **WHEN** 仪式试图改变一块已有条目的晶块模式
- **THEN** 该请求被拒绝，条目不受影响

#### Scenario: 无玩家改模式入口
- **WHEN** 任何玩家打开晶块或核心界面
- **THEN** 不存在改存储模式的控件

### Requirement: 孤儿段导出与恢复往返

晶块内容 SHALL 可被导出为携带存储模式与全部条目的数据（含逐件组件与长计数），并可由仪式核心持久化为「孤儿段」。孤儿段 SHALL 可在原晶位重新可用时导入恢复，导入 SHALL 保留模式与条目。

#### Scenario: 导出存孤儿段
- **WHEN** 仪式在降级时撤下一块晶块并保存其内容
- **THEN** 内容以孤儿段形式持久化于核心，模式与条目完整

#### Scenario: 恢复孤儿段
- **WHEN** 原晶位重新出现且导入该孤儿段
- **THEN** 晶块恢复出与撤下前一致的条目、计数、组件与模式
