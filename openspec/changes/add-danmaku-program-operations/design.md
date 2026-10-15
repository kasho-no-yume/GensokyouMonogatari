## Context

依赖core代码完成；详细操作全集以[operations](../refactor-danmaku-program-runtime/operations.md)为准，案例见[recipes](../refactor-danmaku-program-runtime/pattern-recipes.md)。future项不属于交付。

## Decisions

本阶段实现纯数学与描述：固定输入和时间直接求状态；统一量化、StrictMath、索引RNG。组平移与局部旋转缩放分开，同通道显式合成。路径边界缓存前缀起态，不能靠从出生累加。

发射器、Replace、Decision和视觉操作先提供类型化描述和mock上下文，不做世界副作用。台账必须写“描述已实现/运行未接入”，不得标为可用游戏能力。新增Path/Pattern注册测试不改核心；网络实际复验在gameplay。

## Validation / Handoff

任意时间/查询顺序一致、边界连续、删除支路不影响RNG、48拍共圆、花阵旋转呼吸加弧线及部分脱离。交付输出状态与描述格式给server/network/gameplay。
