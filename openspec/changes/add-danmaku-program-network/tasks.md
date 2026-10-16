## 1. 协议与恢复

- [x] 1.1 [原7.1、7.2] 实现全部消息、定义缓存/兼容、epoch/幂等/tombstone与有界chunk提交
- [x] 1.2 [原7.3、7.4] 实现共享时钟、陈旧判据、revision分离、effectiveTick重建和视觉偏移状态接口
- [x] 1.3 [原7.5] 实现自足checkpoint含存活mask/决策/派生游标
- [x] 1.4 [原7.6、7.7] 实现包络订阅/父闭包/分片、成员删除/长度事实/摘要/恢复限流
- [x] 1.5 [原7.8] 验证恢复不重放副作用及旧消息不能复活，完成协议测试并更新台账
- [x] 1.6 严格验证本change；把实际延迟/流量/画面复验交接gameplay

1.4已补齐ProgramSyncServer.publishSpatial与ProgramNetworkHost.spatial接线，测试验证空间切换只发选中成员且重进入不丢身份。真实内容的保守包络生产者与画面/延迟复验归gameplay。详见network-api.md。
