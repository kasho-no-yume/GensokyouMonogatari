# Server implementation handoff (2026-10-16)

经用户授权，在旧后端基线待采样期间推进server；core任务状态不变。旧后端流量/CPU/帧时/资源基线与跨平台fixture仍待实跑，不以本阶段测试代替。

代码根：`src/main/java/com/bitsson/gensokyou/danmaku/program/server/`。

## 运行入口

- `DanmakuWorldRuntime`：维度状态、UUID/epoch、组/批次/成员身份、固定阶段、目标资格、生命周期、Plan/Commit、净预算Replace、派生与发射器。
- `ProgramSession`：已编译定义实例、owner/card/background上下文、循环切换、游标和自足内存快照。恢复保留未来定义事件、预警中的Commit和类型化碰撞请求，不重播历史副作用。
- `ProgramExecution`：冻结的OperationType id/schema到权威handler注册表。安装定义时检查缺失handler；按阶段和编译拓扑顺序调度。
- `ServerOperations`：标准有预警发射操作。
- `ServerControlOperations`：可编码的Capture/Group/Fire/Sample/Surface/Emitter/BindSource/Replace/Corridor/Control。所有变化状态保存在runtime内，不藏在handler闭包里。
- `GroupMotion`：组时钟、平移、局部变换、父闭包、解析速度与角速度。`attachToGroup`显式选择共享组运动；成员年龄仍由各批出生决定。
- `ProgramServerHost`：仅推进显式安装的session，level post tick执行，unload清理。不会自动启用任何正式BOSS。

## 世界与事务契约

`DecisionProvider`是世界查询边界；`ServerLevelDecisions`按UUID读取真实实体，检查已加载区块，使用真实方块collision shape高度，不强加载攻击区域。目标UUID有序固定，同距UUID打破平局；离开资格/半径只失效原槽，不重新分配。

Plan保存不可变出生tick、源姿态、种子和完整成员列表。PrepareEmission从actualSource瞄准。完整batch检查世界存活数、每tick出生数、显式quota、近域密度、评估工作额度及碰撞体净距。CPU额度是确定性工作单位，**不是CPU性能达标证据**；默认值由显式构造参数提供，正式config装配归gameplay。

Replace先固定母弹边界状态，以净占用检查，perParent/allSelectedParents明确，成功与失败触发均消费。一个结果revision包含移除母弹及全部子代Plan。事件队列不足在事务前拒绝，不能留下部分提交。普通母弹结束仅取消其未来ChildAction；已生成worldSnapshot孩子继续。Cancel按归属清后代，立即关伤害、撤预警、按fade截止释放。

正常跨轮停止旧轮顶层计划，retain carrier的有限派生继续；切卡清非background组和游标，Encounter结束连背景清理。移动锁截止为类型化命令，可恢复。隐藏/伤害窗口使用显式card/cycle/group/member时钟，半开区间，在碰撞回调前关门。

ExternalEntitySource每发按UUID采样，Pose随该批事件保存，丢失停止未来发射，不删除独立孩子。MemberPose可绑定稳定batch，重复index不再误选其他批。followSource保存炮口曲线与最后权威姿态，丢源按END或detach执行。

## 事件与checkpoint

事件包含epoch、revision、effective tick、组/成员身份、Plan/替换children、目标点、Frame、Surface结果、通路结果、取消fade截止与撤预警标志。network阶段实现有界wire编码、线上传输、tombstone和兴趣恢复。

Session checkpoint保存定义/anchors/cursor和runtime：成员、计划、pending Commit、有限发射器游标、源batch/跟随/炮口/脱离、目标采样/帧/surface、控制锁和待执行AuthorityCommand。恢复不重复发射。任意Runnable只用于本机测试适配：队列非空时明确拒绝checkpoint；可恢复路径使用AuthorityCommand，不把可执行对象当wire数据。

## 验证

`WorldRuntimeTest`包含真实内存准入/Replace执行，覆盖净预算、拒绝消费、第二拍取消、目标退出、3→6→12两代替换、20次回收、跨轮carrier、旋转移动源、偏移瞄准、重瞄rebase、隐藏伤害门、近域密度、事件容量原子性、编译程序多拍共享组和session恢复。

真实专用服：`tools/gradle_task.ps1 runProgramProbe`，使用`build/program-probe-run`独立世界，需该目录的eula.txt与server.properties。探针明确启用系统属性，恢复临时修改的方块，运行后退出。证据日志：`build/agent-logs/20261016-173835-runProgramProbe.log`：

```text
PROGRAM_WORLD_PROBE PASS surface=slab firstAir=verified corridor=blocked+clear
PROGRAM_HOST_PROBE PASS dedicated-level-ticks runtimeTick=2
BUILD SUCCESSFUL
```

这是实际半砖顶面/首空气/阻塞与畅通通路及宿主启动tick验证，不等于多人真实伤害、外部实体运动或性能验收。

本机全量报告：116 suites / 953 tests / 0 failures / 0 errors / 0 skipped，其中WorldRuntimeTest 29项；OpenSpec strict与git diff --check通过。最终wrapper build日志见会话最后一次`build/agent-logs/*-build.log`。

## 后续边界

- `ServerControlOperations`的名字句柄按occurrence向外层解析；安装定义时独立检查生产先于消费、重复作用域不外泄，跨Parallel分支引用拒绝并要求显式Sequence。通用core typed引用contract保持不变。
- 真实BOSS移动锁消费者、碰撞/擦弹/声音/渲染、全部代表卡和实际带宽由gameplay/network接线。
- 未来网络解码需对checkpoint所有映射和引用做完整有界验证；现restore用于可信内存状态，不宣称能安全接收任意外部对象。
- 任意复杂变换/跟随组合的玩法与CPU压力仍须后续代表切片验收；本次世界探针仅覆盖已列场景。
