# Proposal: fix-ritual-test-negcheck

## Why

`gs_ritual_test` 实机套件复活后（add-yumewatari-behavior 顺带修复 schedule 语法与无阶标签解析），全部 8 个图案的负查稳定报 `NEG_FAIL: low stones wrongly formed top level!`——不是 matcher 真在虚高匹配，而是负查测试自身退化：`setup_neg` 复用正查链的同一锚点列、且不清场，被低阶品阶"解析不出应跳过"的高阶格位恰好还留着正查刚铺好的高阶石头，顶阶结构事实上完整，断言必然失败。该缺陷此前被 schedule 语法失效整体掩盖（函数从未加载成功），旧单图案 harness 曾以独立 NEG 锚点通过（archive 2026-09-08 handoff：NEG=(84,100,4)），多图案逐锚重构时丢了隔离。

## What Changes

- `tools/validate_ritual_pattern.py` 的 `emit_test_pack`：`setup_neg` 在铺低阶版本前，先对顶阶全量切片的**每一格**（含解析失败将被跳过的格位与核心格）发 `setblock ... air` 清场，保证负查面对的是确定性的空白场地而非正查废墟。
- `check_neg` 断言加 `hi_exp>=1` 守卫：顶阶切片无品阶信号（hi_exp=0）时改发 `NEG_SKIP`（与正查 SKIP 语义对齐），避免对默认 tier=0 核心永远 FAIL。
- 重建 `run/world/datapacks/gs_ritual_test` 测试包并实机跑一轮全链，期望：全部图案 `OK:L*`、`NEG_OK`（或 NEG_SKIP）、末尾 `ALL_DONE`，零 `FAIL/NEG_FAIL`。

## Capabilities

### New Capabilities

- `ritual-e2e-test-pack`: 仪式实机端到端测试包（`--test-out` 生成、load 自触发链、逐级铺结构读核心 tier 断言、低阶铺顶阶负查、串行链收束 ALL_DONE）的行为需求——首次为其立规（现状仅散落在脚本 docstring 与 archive 留档）。

### Modified Capabilities

（无——离线校验器规则不变；`ritual-pattern-system` 的加载期校验不受影响。）

## Impact

- 仅工具：`tools/validate_ritual_pattern.py`（`emit_test_pack` 的 neg 布线 + 断言守卫）。
- 产物：`run/world/datapacks/gs_ritual_test` 重建。
- 不触碰：Java 侧 matcher/BE/行为、pattern JSON、`_run_ritual_test.ps1` 驱动（其 D:\ 硬路径问题另议）。
