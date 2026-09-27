# add-barrier-break-ritual

> **归档说明（FX 部分已移交）**
>
> 本变更的「结界崩解」演出（任务组 5.15 / 5.17 / 5.19 / 5.26 / 5.29 / 5.34 / 5.35
> 与 `docs/barrier-shatter-fx-postmortem.md`）**未完成即移交**给
> `openspec/changes/barrier-shatter-fx`。失败复盘见该 postmortem，本变更的
> `design.md` D5.7~D5.14 记录了每一轮的具体偏差与踩坑，一并作为交接材料。
>
> 移交时留在本变更内、**未完成**的条目：
>
> | 条目 | 状态 | 去向 |
> |---|---|---|
> | 5.5 跨维度 `setBlock` 改投目标维度 tick 队列 | 未做（性能项，非缺陷） | 留在本变更，作为已知优化债 |
> | 6.10 尺寸标量 2.0 的目标测试 | 未做 | `barrier-shatter-fx` 任务 4.1 一并处理（同一包围盒改动） |
> | 9.4 / 9.4b / 9.5 / 9.6 / 9.7 / 9.8 / 9.9 实机验证 | 未做 | 阻塞于 `spirit_core_2` / `ritual_stone_2` 可达性（见本变更 Open Questions 3），非代码缺陷；实机验收改由 `barrier-shatter-fx` 的 `/gs_debug barrier replay` 承担 |
> | 10.1 / 10.2 依赖变更 | 阻塞 | 依赖 `tune-resonance-relay-radius` / `add-ritual-stone-higher-tier-recipes` / `add-sukima-fragment-source` |
>
> **本变更必须先于 `sukima-eye-open-axis` 与 `barrier-shatter-fx` 归档**——后两者
> MODIFY 的 `sukima-portal-rendering`「隙间门开合动画 / 启动爆发 / 环境粒子」与
> `barrier-break-ritual`「隙间门生命周期」这几条 Requirement 只在本变更的 delta 里
> 存在。本变更归档后它们才进入 `openspec/specs/`，那两条 delta 才有合法的
> MODIFIED 目标（`openspec archive` 对不存在的 MODIFIED 目标会直接抛错，不会静默丢失，
> 但会中断归档）。
>
> 本变更归档时**保留音效、暂时没有画面**的状态是已知缺陷（`playShatterCues` 仍被调用）。
> 移交方须知：若 `barrier-shatter-fx` 最终放弃，MUST 先决定音效去留。

## Why

`barrier_break_circle` 是断头的占位：`toggleable: false` 使 `onStart` 整条扣费链不可达，`getCapacity()` 无该 pattern 分支。本次落成真正的终局仪式——网络总灵力门槛、闩锁式永久开启、夸张传送门观感升级、幻想乡侧孪生之门。

## What Changes

- 供灵唯一经「八方归元 → 万象共鸣 → 结界」三节点链；受灵上限恒等于归元实际输出，无归元则为 0（对路由器不可见）。
- 缓存 5,000,000，每秒自然流失 150,000；充盈且祭品正确即闩锁开启，永久生效。
- 闩锁为独立持久化字段，不复用 `enabled`（结构失配会把 `enabled` 归零）。
- 祭品 4 星银 + 4 潮汐晶，`consume: none`，5 Hz 轮询判定。
- 传送门 2 倍尺寸（默认尺寸不变）、眼睛开合动画、零网络包启动爆发、紫色环境粒子。
- 幻想乡侧孪生之门，偏离合生点落点，双门同规格同动画。

## Capabilities

### Modified Capabilities

- `barrier-break-ritual`（全面重写）、`sukima-portal-rendering`、`ritual-lifecycle`、`ritual-offerings`、`ritual-gui-info-lines`、`ritual-power-attributes`、`guide-book`

## Impact

- 核心 BE 新增闩锁字段与容量分支；核心方块 `onRemove` 追加跨维度传送门清理。
- 隙间方块实体由无数据无 ticker 升为持有动画状态。
- `BarrierBreakBehavior` 重写。
- 删除 `power.barrier.barrierSpCost`。
- 依赖 `tune-resonance-relay-radius`（半径放宽）；实机生存验证依赖两个占位变更（`ritual_stone_2` 配方、`sukima_fragment` 来源）。
