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

## Risks / Trade-offs

- **[正查覆盖铺设的 destroy 掉落噪声]** 正查 `setblock` 覆盖已有方块会掉落旧方块实体（存量行为，每轮跑测试区多一堆石头）→ 本次不扩大范围；如嫌脏，后续可把正查铺设统一加 `replace`（一行正则的事，另案）。
- **[清场后残留空气与缺席格语义]** pattern 的缺席格=自由：neg 场地里顶阶专属格是空气、其余被低阶填满，matcher 可能匹配到低层级（如梦渡 L0 成立）并把 tier 写成低阶值——这正是期望行为，断言只禁"顶阶值"，不受影响。
- **[链式时长]** 每图案多 ~3k 条 setblock，全链总时长增加数十秒量级，驱动脚本轮询窗口（9 min）不变可容纳。

## Migration Plan

纯工具改动：编辑脚本 → `--test-out` 重建测试包（rmtree 全量替换，无残留）→ 实机跑一轮看全绿。回滚=revert 脚本并重生成。
