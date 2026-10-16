## Context

依赖server的权威事件和自足状态；操作库codec来自core/operations。

## Decisions

采用Definition/Start/Checkpoint/DecisionBatch/ClockDigest/Resync/End。统一维度时钟，revision独立于age。TCP不另造重传协议，epoch/序号用于幂等和订阅完整性。缺定义有界暂存，chunk完整提交。结束tombstone防止旧消息复活。

迟到事件从effectiveTick的解析状态重建，不重放世界副作用。兴趣范围按攻击包络，补齐父变换闭包。动态激光长度和成员删除作为事实编码；本阶段可使用注入数据测试，实际游戏生产者/渲染由gameplay接线。

### 实施细化（2026-10-16）

- 客户端使用独立ReplicaState投影，不restore服务端执行器。ProgramSession的定义游标、待Commit、有限Emitter游标和已消费触发作为数据保存，不在客户端执行。
- 以维度/owner/epoch为战斗作用域，subscription区分兴趣代次，sequence区分投递边界；authority revision允许因过滤跳号，也允许同revision的新增事实。age不参与幂等。
- 服务端在完成tick后比较投影，覆盖旧Event未包含的组运动/锚点/附着变化。Batch共享计划与描述hash，单成员删除只发mask。定义游标和可解析follow源的lastPose变化不产生稳态消息。
- 操作描述沿用白名单codec；Batch逐模板分段编码，避免480成员超过单条record的4096字段上限。线上快照有独立总字节、集合、分片、缓存、历史和请求额度。NetworkLimits默认值是协议资源保护常量；Host构造注入margin/clockPeriod，正式游戏config装配归gameplay。
- 包络由宿主提供保守世界AABB；ProgramInterest提供有限快照路径包络及稳定身份空间分片。复杂父变换/跟随包络必须显式提供，禁止采样点猜测最大范围。ProgramNetworkHost.spatial接成员包络生产者，发布时按空间分片选择模板与mask，切换使用新订阅代次；缺少成员包络时保守保留整批。真实内容的包络生产者交接gameplay。
- 默认每40tick每连接维度发一次共享ClockDigest，按可见作用域轮转摘要。退订Release不形成攻击终止，重订阅切换subscription；End和已终止成员/组保留有界tombstone。
- VisualOffset只提供≤0.25格、≤2tick的表现偏移接口，真实渲染消费者归gameplay。

## Validation / Handoff

协议/双端模拟验证中途进入、失信时钟、缺定义、重复/旧epoch、半份checkpoint、远端组和谱系恢复。真实网络延迟与多视角验收在gameplay，本阶段不可替它勾完成。
