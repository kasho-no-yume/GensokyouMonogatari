# Core API and verification

代码根：`src/main/java/com/bitsson/gensokyou/danmaku/program/`。
当前仅离线描述、编译和调度，不含世界运行时。

## 编写与编译

1. 实现 `OperationType<P>`：稳定id/schema、typed参数decode/encode、OperationContract。
2. 注册到 `OperationRegistry`，调用 `freeze()`。
3. 使用 `ProgramBuilder` 分配稳定词法ID、捕获已解析配置；组合节点为不可变records。
4. 调用 `ProgramCompiler.compile`，失败读取diagnostics，成功获取ProgramDefinition。
5. `ProgramCodec.encode/decode`使用同一有界规范字节格式，解码后重新编译验证。

参数应使用明确单位的typed P；wire字段当前为规范字符串map，不能写Java对象toString。
所有参数、节点顺序、ID、schema、播放/尾部策略进入hash。配置值改变产生不同hash。
新增操作只需实现注册类型，不修改编译器或通用codec。

## 时间

- `TimeSpec.card/cycle/group/member`创建四种类型的绝对时间，`.at(id,child)`构造At。
- `ClockAnchors`提供server tick锚点；缺少group/member锚点报错。
- `OffsetNode`为局部偏移，Repeat/Every每次occurrence平移。
- `Sequence`通过ProgramTime推进游标；At保持绝对时刻，不额外叠加游标。
- Once必须明确TailPolicy，Loop必须明确周期和TailPolicy；Stream明确period/phase。
- Cycle定义本轮时钟和尾部策略，外层Loop/Repeat/Every驱动重复。
- `ProgramSchedule.between`返回[from,until)事件，稳定occurrence和ownershipEnd；同tick按编译拓扑序。
- 所有查询有输出与访问上限，远期查询可跳过历史周期，不逐tick追赶。

## 引用与合成

OperationContract声明输出类型与读取的producer。编译器检查不存在/错类型、循环、重复作用域逃逸、提前使用与已声明寿命过期；无法证明不同时间域先后时拒绝。
同scope同通道重叠写入拒绝；ComposeNode显式指定ADD_OFFSET/MULTIPLY_SCALE/COMPOSE_TRANSFORM及子顺序。实际变换数学属于operations。
周期写入采用保守时间包络，不进行无限LCM展开；需要精细交错时用显式时间段。

## 预算

注册类型声明真实members、所有代数总derivedMembers、generations和从操作时刻到最后子代结束的lifetimeTicks上界。
ProgramBounds存单操作最大批/派生/寿命，不能冒充世界总并发。
ProgramBudgetReport.inspect提供有限窗口计划清单、总出生数、派生上界、同tick峰值与跨轮存活上界；遵守尾部策略。
派生上界按出生时全部占用、直到最后子代结束计算，保守而非精确派生调度；运行期Commit预算属于server。

## 验证

- ProgramCoreTest：偏移子拍、Once、四域At、远期查询、codec、真实46×16展开、跨轮寿命、嵌套Cycle尾部。
- ProgramValidationTest：引用/环/存活期/作用域、拓扑、通道拼接与显式合成、版本/深度/长度攻击。
- LegacyManifestTest：20张旧卡全部声明导出。
- Gradle始终走`tools/gradle_task.ps1`。

## 仍须完成的阶段证据

旧后端性能实测尚无固定场景数据；客户端启动成功不等于测量完成。
完整阶段完成以tasks和实测报告为准，不凭本API文档进入下一change。
