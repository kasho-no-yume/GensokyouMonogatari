## Context

- 代码现状：项目已有原版 advancement 机制，例如 `GuideTierProgress` 会每 tick 1 秒检查并 award/revoke `guide/*`。
- 设计表：`sheet/achieve.xlsx` 共 15 条，前置列目前都为空，3 条标紫（`君权神授`、`妖怪退治`、`梦想崩坏`）。
- 现状：成就内容尚未接线，`data/gensokyou/advancement` 只有 guide 相关维度/过渡门槛。

## Goals / Non-Goals

**Goals:**
- 将成就表落地为原版 advancement，保持 vanilla 的触发/进度/持久化语义。
- 里程碑用 `display.frame = "challenge"` 紫色框。
- 仪式类统一在 ritual behavior/session 成功结算处触发。
- 物品、击杀、维度、药水、神恩等走原版 trigger 或既有事件。

**Non-Goals:**
- 不做自定义成就 UI。
- 不定义复杂成就链前置（表列全空）。
- 不修改已有 guide/* 世界进度的语义。

## Decisions

- **用原版 Advancement 而非自定义成就**：用户确认优先原版 advancement，可直接继承父节点、图标、frame、进度与持久化。
- **里程碑用 challenge frame**：原版“见鬼去吧”等就是 `challenge` 框，直接对齐用户预期。
- **仪式类统一埋点**：相对维护性更好，所有 ritual 完成都在 session/behavior 成功结算点触发；普通条件不强行走仪式入口。
- **先不做前置链**：`前置（行号）` 目前为空，成就间暂不建 parent 依赖，除非后续设计表补齐。

## Risks / Trade-offs

- [某些条件原版没有对应 trigger] → 新增一个轻量 custom trigger 或在服务端 API 完成处直接 award。
- [仪式行为太分散] → 找共同的 `RitualBehavior` 完成回调；如果实在没有，就在 `RitualSession` 结束态统一 award。
- [成就重复触发] → advancement 原生幂等；额外用 `isDone` 守卫。
