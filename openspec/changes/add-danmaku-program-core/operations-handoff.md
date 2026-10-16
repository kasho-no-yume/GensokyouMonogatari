# Operations开发交接：带未解除基线门槛的提前开发

用户已选择将实机采样交给另一agent稍后配合完成。当前core不归档，1.3及2.6保持未完成。

可以先推进`add-danmaku-program-operations`的纯数学、图案与离线测试，作为并行开发工作；这不是路线图“阶段1验收完成”的声明。开始前必须读取本文件、core-api.md、implementation-status.md和operations自身artifacts。

## 当前证据

- 全量build通过；2026-10-16读取结果为112 suites / 904 tests / 0 failures / 0 errors，后续新增测试应重新以最终报告为准。
- 20卡完整参数JSON、Excel源快照与底噪/端点差异已保存在baseline。
- 尚无旧后端固定负载网络/CPU/帧时实测；执行手册为baseline/sampling-guide.md。

## 接入入口

- `OperationType<P>` + `OperationRegistry.freeze()`：为Path/Pattern提供typed参数转换、规范字段和bounds。
- `ProgramBuilder.operation`捕获已解析配置；ProgramDefinition由编译器生成。
- `ProgramSchedule`仅输出无副作用事件；不能拿它当世界runtime。
- At是四域绝对时间，Offset才随重复平移；Group/Member需ClockAnchors。
- ProgramBudgetReport是有限窗口离线计划，派生为保守上界；实际生成数量要与Pattern输出独立对照。
- ComposeNode仅定义合成顺序与冲突检查，变换数学尚未实现。

## 开发者必须复核的边界

当前静态引用分析对不同时间域及周期使用保守证明；若操作设计需要更强表达，补有意义的反例测试再调整核心，不绕过验证。
`ProgramNode.duration()`是声明跨度，含绝对At时实际游标应使用ProgramTime；复合嵌套、跨域引用与零时长边界需要进一步探针。
wire参数为规范字符串map，经typed codec解析；不得编码任意回调、实体或默认toString。
ProgramLimits是显式编译输入。世界配置、网络协商、owner绑定、实际判伤和渲染都未接入。

## 本次提前开发的门槛

- 只推进operations离线部分，不据此默认server/network/gameplay已经可进入。
- core采样阻塞必须在gameplay A/B性能验收之前解除；旧后端保留到采样完成。
- operations完成时须联合复核core契约与其测试；不能把当前8/10改成10/10以满足依赖。
- 若发现真实API不能支撑操作，回到core修复并同步测试与文档，不静默降级内容。

## 工作区注意

gradle.properties已有用户改动，保持；所有Gradle走tools/gradle_task.ps1。
三份本地预览源码此前引用缺失后续依赖，被用户授权移出，备份在
`C:/Users/spdimdriver/AppData/Local/Temp/opencode/gensokyou-preview-backup-20261016/`。
不要为了“恢复预览”未经调查直接搬回src；它们不是本阶段可用API的证据。
