## Context

总设计的D1/D2/D10及operations §1–3是本阶段依据：[design](../../../../docs/danmaku-program/design.md)、[operations](../../../../docs/danmaku-program/operations.md)。先读[通俗指南](../../../../docs/danmaku-program/author-guide.md)。

## Decisions

仅实现程序模型、编译、时间组织和扩展接口。Frame/Plan等是有类型的数据引用，不读取世界；后端选择先提供接口，游戏接线由gameplay阶段完成。核对内容和测量旧后端基线，性能脚本尽量复用既有工具。

时间区间[开始,结束)，明确Once/Loop/Stream。周期子拍不能被外层phase=0过滤。冲突通道必须显式组合。编译产物携带稳定节点ID、hash、有界codec和预算描述接口，具体路径与几何由下一change注册。

## Validation / Handoff

### 时间语义补充（2026-10-16 用户确认）

At 始终引用显式时间域的绝对时刻，不随 Sequence 游标或 Repeat occurrence 自动平移。
Offset 是局部调度偏移：随父容器起点和每次重复重新定位。周期60内的0/20/40子拍应使用Offset。
GroupTime/MemberAge 绝对求值须有对应时钟锚点，缺少锚点必须报错。

无世界测试时间、重复偏移、非法依赖/冲突、codec上限与稳定hash；保存内容manifest和基线报告。交付可供operations注册的API，不宣称已有游戏运行时。实际Gradle调用走tools/gradle_task.ps1。
