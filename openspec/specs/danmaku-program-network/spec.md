# danmaku-program-network Specification

## Purpose
TBD - created by archiving change add-danmaku-program-network. Update Purpose after archive.
## Requirements
### Requirement: 共享网络状态与恢复
协议 SHALL 按hash缓存定义并传实例、稀疏权威事实及维度时钟，不依赖逐弹实体配对。Checkpoint必须自足、有界，包含成员mask、输入与派生游标。恢复 MUST 不执行历史伤害/派生/音效，旧epoch和重复消息不得复活已结束组。

#### Scenario: 分裂后加入
- **WHEN** 客户端在第二代分裂完成后订阅
- **THEN** 恢复末代当前状态，不重新分裂

### Requirement: 时间和兴趣范围
客户端 SHALL 以共享服务器时钟求值，迟到决策按effectiveTick重建，失信冻结并恢复。订阅 SHALL 使用攻击包络及父变换闭包，而非只看owner。

#### Scenario: 远离owner的弹
- **WHEN** 玩家进入远端弹群范围而owner不可见
- **THEN** 仍获得该组及求值依赖，轨迹不脱钩

