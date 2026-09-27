# add-sukima-fragment-source

## Why

`gensokyou:sukima_fragment` 是 T2 幻想乡材料带的唯一解锁信物，但全项目**零生产配方**。它导致一条三重复死锁，连带 2 阶仪式石与 2 阶灵力核心都不可达。

## What Changes（本变更仅为占位记录，暂不实现）

- 记录需求：隙间碎片 SHALL 有正式来源。
- 已指定方向：**由低阶 BOSS 战掉落**。
- 具体形式待定（见 proposal 的 Open Questions）。

## Capabilities

### New Capabilities

- `sukima-fragment-source`: 声明需求，本占位变更不含可执行场景。

### Modified Capabilities

- `gensokyo-materials`: 细化信物来源方向为「低阶 BOSS 战掉落」。

## Impact

- 纯需求占位，不改动数据或代码。
- 阻塞 `add-barrier-break-ritual` 的实机生存验证，以及全部 T2 内容。
