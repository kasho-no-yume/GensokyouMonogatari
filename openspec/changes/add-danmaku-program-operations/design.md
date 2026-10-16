## Context

依赖core代码完成；详细操作全集以[operations](../refactor-danmaku-program-runtime/operations.md)为准，案例见[recipes](../refactor-danmaku-program-runtime/pattern-recipes.md)。future项不属于交付。

## Decisions

本阶段实现纯数学与描述：固定输入和时间直接求状态；统一量化、StrictMath、索引RNG。组平移与局部旋转缩放分开，同通道显式合成。路径边界缓存前缀起态，不能靠从出生累加。

发射器、Replace、Decision和视觉操作先提供类型化描述和mock上下文，不做世界副作用。台账必须写“描述已实现/运行未接入”，不得标为可用游戏能力。新增Path/Pattern注册测试不改核心；网络实际复验在gameplay。

## Validation / Handoff

任意时间/查询顺序一致、边界连续、删除支路不影响RNG、48拍共圆、花阵旋转呼吸加弧线及部分脱离。交付输出状态与描述格式给server/network/gameplay。

## 复审修订（2026-10-16）

子程序必须保存真实ChildAction子图，不能仅靠maxGenerations/descendantBound数字宣称支持派生。动作带触发、选择、周期、下一代、绑定和失败策略；构造时校验子树数量/代数/母寿命，子树结束包络进入OperationContract。阶段2只展开预设准入成功的纯计划验证，实际同revision事务仍归server。

Attached分离groupOrigin、member birth与transformClock；父层级按内到外保存有界变换闭包，各层独立时间原点。成员direction与位置/速度分别求值，镜像视觉策略不影响真实射线方向。分段路径在Path接口位置也必须可编码，采用本地已知Segments与PathSpec适配，不改核心封套。禁止内路径已经END而外路径CONTINUE把成员复活，构造时拒绝冲突并要求作者把END移到外层。

世界输入描述使用AuthorityDescriptions具体记录，DecisionKind仅作分类；不能用一个radius/maxSteps参数袋代替目标槽、采样规则、帧回退、安全失败和返回条件。详见operations-api.md及OperationCompositionTest。
