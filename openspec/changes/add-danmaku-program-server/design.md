## Context

依赖core/operations。依据总设计D2、D5、D6、D9、D11和operations §4/8/10。

## Decisions

组是数据对象，owner UUID、epoch及group/batch/member身份不可复用。目标槽在采样后固定，退出不转派。预警与发射共用计划，动态外部实体逐发服务端采样。

准入以完整batch为单位；Replace按净占用提交结束母弹和生成子代，拒绝策略必填。普通父弹结束只停未来发射，CancelScope按归属清全部后代。按规定同tick顺序执行，碰撞派生请求通过接口最早下一tick处理。

本阶段提供事件输出队列和checkpoint状态结构，network负责线上编码和传输。碰撞回调用测试适配注入，真实碰撞由gameplay接线，不能因此提前声称完整游戏运行。

## Validation / Handoff

测试目标退出、第二拍安全取消、分裂净预算、重复触发防护、跨轮/切卡差异、owner卸载、新epoch和20次回收。世界查询需要集成环境；不得仅靠mock冒充地形验收。
