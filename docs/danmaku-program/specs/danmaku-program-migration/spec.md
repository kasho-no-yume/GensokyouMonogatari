## ADDED Requirements

### Requirement: 全量内容映射和差异登记
迁移 SHALL 覆盖 content-mapping 中的正式卡与默认攻击；当前正式范围为 19 张（大妖精3/山女4/蓝8/露米娅4），旧傩面 5 卡已删除。前三只以 sheet 意图为准；旧蓝由 8 张残魂替换。已知缺拍、无效相位/缺口与表内冲突 SHALL 记录选择依据及新旧结果，不能静默固化 bug 或宣称逐位兼容。

#### Scenario: 山女偏移子拍
- **WHEN** 迁移Every60内0/20/40三拍
- **THEN** 三拍全部运行，差异报告指出旧外层过滤缺拍，而不是删去新拍维持旧弹量

### Requirement: 代表性切片先于全量迁移
实现 SHALL 先验证蓝1/4/6、花海、尾珠和复合花阵探针，再迁移全部卡。新图案/路径独立注册测试与任意时间重建测试必须通过。

#### Scenario: 组合需要修改核心
- **WHEN** 复合花阵只能通过增加形状专用主循环分支才能运行
- **THEN** M2不得视为完成，须修正组合边界后再扩大内容迁移

### Requirement: 非BOSS隔离与单一判伤后端
迁移 SHALL 保持玩家武器/技能及普通妖精的实体路径；一个encounter只能有一个判伤后端。回退 SHALL 先终止战斗再重开，不热接管半程状态。

#### Scenario: 双后端影子比对
- **WHEN** 同时运行新旧求值以比对
- **THEN** 只有选定后端可造成伤害或生成权威派生

### Requirement: 量化验收与旧路线退出
实现 SHALL 达到migration文档的正确性、带宽、CPU、帧时和回收门槛，提供可复现实测报告；不得用降低弹量/寿命/碰撞替代同等负载比较。最终新BOSS路径不得残留逐弹网络实体或临时旧执行适配；两项在途change必须记录需求转移和被替代任务，不虚假勾完成。

#### Scenario: 同等负载未过关
- **WHEN** 流量降低但服务端p95超过基线110%
- **THEN** 完成门槛未满足，保留迁移回退并分析瓶颈，不宣称重构完成

#### Scenario: 归属回收
- **WHEN** 连续20次创建并结束encounter
- **THEN** 每次结束后下一tick组、成员、FX、计划与预算归零，无持续资源增长

### Requirement: 作者文档与技能台账保持可执行状态准确
系统 SHALL 维护技能入口、通俗指南和单一操作台账；每项按planned/implemented/verified/legacy-only标记，implemented必须附代码入口，verified必须附验证证据。用户四例 SHALL 成为M2强制验收；说明必须明确单位、时间域、原子范围、速度继承及失败处理。

#### Scenario: 尚未实现操作
- **WHEN** AI从技能查找CreateEmitter或ReplaceMembers
- **THEN** 能直接看到它们仍为planned及设计入口，不能误认为已有可调用Java方法
