## ADDED Requirements

### Requirement: 程序后端的编排替代契约
对于使用DanmakuProgram的BOSS，本条 SHALL 优先替代本capability中关于Beat/行为开关实体载体、独立轨作用域、多拍一次性禁止、帧归属和逐子代部分准入的实现限制；未迁移entity后端继续遵守旧条款。新程序 SHALL 显式声明Once/Loop/Stream，可有多拍一次性编排，可跨lane共享组/帧；所有实际成员及派生 SHALL 计入预算并以完整batch准入。三维可读性、视觉身份、属性伤害、循环边界血量选择仍须保持。新卡切换 MUST 清理旧卡所属攻击，正常同卡跨轮残留由显式tail策略决定。

#### Scenario: 多拍一次性合法
- **WHEN** 程序声明Once并包含t0/t20/t40三拍及明确结束策略
- **THEN** 编译通过，三拍各发一次，不能因未声明循环被旧lint拒绝

#### Scenario: 完整批次预算
- **WHEN** 18发面板只有10个剩余配额
- **THEN** 整批拒绝且发送一致决策，不生成残缺的10发面板

#### Scenario: 跨轨生命周期
- **WHEN** 两轨共用一个逻辑组并切卡
- **THEN** 其成员、后续计划与预警统一结束，下一卡从0开始
