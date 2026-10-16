# Core registration and backend contract

## Canonical identity

- operation id 使用 `namespace:path` 字符串。
- 每个 operation 携带 `schemaVersion`、有界 codec、validator 和 bounds estimator。
- registry 由调用者完成注册后显式freeze；编译前必须冻结，冻结后注册、替换和重复 id 都拒绝。本阶段没有common setup接线。
- program hash 来自 canonical UTF-8 bytes，不使用对象地址、默认 charset 或 map 遍历顺序。

## Codec limits

默认限制由 `ProgramLimits.defaults()` 提供：节点数、层级、字符串、参数、payload 和单批成员数均有硬上限。解码前检查长度，未知 operation/schema 返回结构化错误，不静默跳过。

## Backend selection

`backend.BackendSelection` 是不可变的encounter选择，`ShadowObserver`只接收不可变ShadowSample。core 不创建实体、不判伤、不发包；真正接线由后续 gameplay/server/network change 完成。同一 encounter 的 backend 不能在运行中热切换。

## 真实入口

- `OperationType<P>`：字符串规范字段经已安装decode/encode转换为typed P，encode(decode(fields))必须完全一致；contract声明类型引用、通道、数量/派生/寿命上界。
- `ProgramBuilder.operation`：捕获配置值生成独立参数快照；编译与求值不再访问实时配置。
- `ProgramCodec`：固定schema 1，长度前缀UTF-8、排序参数、显式节点标签；限制在递归和分配前执行，尾随字节、未知tag/schema均拒绝。
- `ProgramDefinition`：只能由同包编译器构造，公开只读访问器，hash基于完整规范二进制数据。
