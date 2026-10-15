## Context

依赖server的权威事件和自足状态；操作库codec来自core/operations。

## Decisions

采用Definition/Start/Checkpoint/DecisionBatch/ClockDigest/Resync/End。统一维度时钟，revision独立于age。TCP不另造重传协议，epoch/序号用于幂等和订阅完整性。缺定义有界暂存，chunk完整提交。结束tombstone防止旧消息复活。

迟到事件从effectiveTick的解析状态重建，不重放世界副作用。兴趣范围按攻击包络，补齐父变换闭包。动态激光长度和成员删除作为事实编码；本阶段可使用注入数据测试，实际游戏生产者/渲染由gameplay接线。

## Validation / Handoff

协议/双端模拟验证中途进入、失信时钟、缺定义、重复/旧epoch、半份checkpoint、远端组和谱系恢复。真实网络延迟与多视角验收在gameplay，本阶段不可替它勾完成。
