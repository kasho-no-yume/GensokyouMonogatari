## Why

`gensokyou:sukima_fragment`（隙间碎片）是幻想乡 T2 材料带的解锁信物——四个工具献祭仪式的高阶带（`gensokyou_high`：月砂、魔法菇、永恒木、星银矿、鬼石、以及河童特产池的潮汐晶/龙鳞）全部以「祭品台上摆放 ≥1 枚隙间碎片」为唯一解锁条件，且该信物不被结算消耗。

但全项目范围内，`sukima_fragment` 只出现在**一处**：`ritual_recipes/zaohua_circle.json` 的 `zaohua_spirit_core_2` 配方中作为**被消耗的原料**。它没有任何生产配方、没有任何战利品表、没有任何世界生成。

后果是一条三重复死锁：

```
sukima_fragment  ──✗ 无来源──▶  T2 材料带  ──✗──▶  ritual_stone_2
                                                       │
                                            ┌──────────┴──────────┐
                                            ▼                     ▼
                                     spirit_core_2        ritual_stone_2+
                                     （t2 灵核）            （三仪式结构门槛）
```

即：2 阶及以上的一切仪式石、2 阶及以上灵力核心、以及全部 T2 幻想乡材料，目前都无法在正常生存流程中取得。

## What Changes（本变更仅为占位记录，暂不实现）

- 记录需求：**隙间碎片 SHALL 有正式来源**。
- 玩家已指定的设计方向：**由低阶（第一梯队）BOSS 战掉落**。
- 具体形式（掉落率、是否需特定工具/符文、是否走独立战利品表或并入既有 BOSS 表、是否需要二级解锁条件）在本占位变更中**尚未决定**，待对应 BOSS 梯队实施时一并落地。

## Capabilities

### New Capabilities

- `sukima-fragment-source`: 隙间碎片的生产来源（本占位变更仅声明需求，不含可执行场景）。

### Modified Capabilities

- `gensokyo-materials`: 将「信物解锁中阶带」条款中「`sukima_fragment` 的来源 SHALL 由后续变更定义」细化为「由低阶 BOSS 战掉落」。

## Impact

- 本次不改动任何数据或代码，是纯需求占位。
- 阻塞 `add-barrier-break-ritual` 的**实机生存验证**（不阻塞其编码）。
- 阻塞 2 阶仪式石与 2 阶灵力核心的可达性，进而阻塞一切 T2 内容。

## Open Questions

1. 「低阶 BOSS」指哪一只？候选为 `FlandreEntity`（`flandre-boss-low-tier` 已有实现）还是尚未立项的残影 BOSS 梯队。
2. 掉落是必掉、按概率，还是需要多次挑战累积？
3. 是否需要在获取途径上叠加「指导书已读」或「八百万神恩」之类的二级前置？
