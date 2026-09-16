# Design: fix-ritual-test-negcheck

## Context

`emit_test_pack`（tools/validate_ritual_pattern.py）为每个多级图案在专属锚点列生成 `setup_l0..lN → check → setup_neg → check_neg` 串行链。neg 阶段语义：用最低阶品阶解析顶阶全量切片，解析不出的格位被跳过，断言核心 tier ≠ 顶阶预期（matcher 不得虚高）。现状同锚点复用 + 零清场：被跳过的格位仍是正查刚铺的高阶方块，顶阶"事实上完整"，NEG_FAIL 恒成立（全图案复现）。1.21.1 迁移后 schedule 语法错误使函数从未加载，该缺陷被整体掩盖，直至 add-yumewatari-behavior 修活 harness 才暴露。

## Goals / Non-Goals

**Goals:**
- NEG 断言恢复判别力：真·缺格的高阶切片不得匹配顶阶。
- 无品阶信号图案的 neg 断言不误报（hi_exp=0 → NEG_SKIP）。
- 全链一轮跑绿（OK×N / NEG_OK×N / ALL_DONE、零 FAIL）。

**Non-Goals:**
- 不改离线校验规则、不碰 Java 侧。
- 不改正查链语义（逐级铺+4s 断言保持）。
- 不修 `tools/_run_ritual_test.ps1` 的 D:\ 硬路径（用户侧驱动脚本，另议）。

## Decisions

### D1 同锚点清场重建（否决：独立 NEG 锚点列）

`setup_neg` 开头对**顶阶累积切片全部格位**逐格 `setblock <pos> minecraft:air replace`，随后照旧铺低阶解析版。选它而非恢复旧独立锚点：清场让 neg 面对确定性空白场地，同时保留"同列反复重建"路径的覆盖度（顺带回归验证 setblock 覆盖/BE 重建）；独立锚点要扩 x 槽距与 forceload 带、且 neg 废墟留在新列上，布局复杂度白白翻倍。旧 handoff 的 NEG 分离锚点是"没有清场能力"时代的补偿手段，清场一步到位后不再需要。

- `replace` 模式必须写：缺省 destroy 会把拆掉的高阶仪式石整堆掉落成实体，测试区变垃圾场（正查铺设的覆盖行为存量如此，不在本次扩大处理——见 Risks）。
- 核心格一并清空：拆掉正查留下的带 tier 状态核心与 BE（含缓存/电池 NBT），随后 EXACT 重放一个全新核心，断言起点干净。
- 切片规模上限 ~2819 格（造化 L5）：清场+铺设 ≤6k 条 setblock，正查已同量级验证过单 tick 可承受。

### D2 `hi_exp=0` 时 neg 断言降级 NEG_SKIP

`expected_tier(hi, hi)=0` 的图案（顶阶切片无品阶仪式石信号）断言 `unless block [tier=0]` 必然失败（全新核心默认 state tier 就是 0）。与正查 SKIP 对齐：仅 `hi_exp>=1` 时生成 FAIL/OK 两行，否则生成单行 `say [GS-TEST] NEG_SKIP:<path> no-tier-signal`，链条衔接不变。

## Findings（2026-09-16 实施前复核，代码级实证）

- **磁盘产物是旧代，重建为硬前置**：`run/world/datapacks/gs_ritual_test` 现存的 32 个 mcfunction 仍是 `schedule gs_test:x 1s`（缺 `function` 关键字，1.21.1 非法）；而脚本 L583/604/617 已吐合法形态。故未跑 2.1 前起服，整包零加载，3.1 无意义。
- **`hi_exp>=1` 对全部 12 个图案成立**（顶阶累积切片均含 `#gensokyou:ritual_stones_N_plus` 成员），D2 守卫在当前数据集**永不触发**。3.1 实测预期收敛为「12×NEG_OK、零 NEG_SKIP」；spec 的"无信号图案"场景暂无数据覆盖，属纯防御分支。
- **全仓 palette 无任何 EXACT 高阶仪式石**（扫描 12 文件：0 命中）。负查只能产出 ≤low 品阶石，故修复后 `core.tier != hi` 是被结构保证的，不存在"清场后仍漏高阶"的残余路径。
- **`resonance_relay` 是唯一的 `skipped=0` 图案**（顶阶 2039 格中 1979 格本就不分阶，低阶可 100% 重建）。清场后 matcher 会命中顶阶、但 `ritualTier`=物理 maxTier=low(2)≠5 → NEG_OK。其绿来自"tier 属性不被污染"而非"缺格"，机制与其余 11 图不同，勿按同一逻辑解读日志。

## Risks / Trade-offs

- **[正查覆盖铺设无掉落，原风险条已作废]** MC `setblock` 缺省模式即 `replace`（不产掉落），生成行也未写 `destroy`；因此"每轮多一堆石头"与该条"另案统一加 replace"均为误判，不设 follow-up。（清场行仍显式写 `replace`：意图明确且 spec 明文要求，非为防掉落。）
- **[清场后残留空气与缺席格语义]** pattern 的缺席格=自由：neg 场地里顶阶专属格是空气、其余被低阶填满，matcher 可能匹配到低层级（如梦渡 L0 成立）并把 tier 写成低阶值——这正是期望行为，断言只禁"顶阶值"，不受影响。
- **[链式时长]** 每图案多 ~3k 条 setblock，全链总时长增加数十秒量级，驱动脚本轮询窗口（9 min）不变可容纳。

## Migration Plan

纯工具改动：编辑脚本 → `--test-out` 重建测试包（rmtree 全量替换，无残留）→ 实机跑一轮看全绿。回滚=revert 脚本并重生成。
