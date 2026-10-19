## ADDED Requirements

### Requirement: 权威调度与归属
运行时 SHALL 按稳定身份和固定同tick顺序管理组、计划和派生；世界事实仅由服务端采样。固定目标槽不得重排序转派。切卡/owner结束 MUST 清归属，正常跨轮保留须显式声明。

#### Scenario: 目标退出
- **WHEN** 第一目标退出而另一目标成为最近者
- **THEN** 只取消第一槽未来个人拍，第二槽沿原快照运行

### Requirement: 完整准入和原子替换
安全/预算 SHALL 完整批准或拒绝batch。Replace MUST 以净占用提交，原子范围和拒绝策略必须声明；父弹普通结束不得顺带取消已独立子弹。

#### Scenario: 一换二失败
- **WHEN** 净增加1配额不足且策略endParent
- **THEN** 母弹结束、子弹全不生成，触发已消费，不积压重试
