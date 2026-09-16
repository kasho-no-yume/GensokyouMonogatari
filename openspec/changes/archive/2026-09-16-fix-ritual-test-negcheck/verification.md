# Verification: fix-ritual-test-negcheck（实机全链，2026-09-16）

结论：**全绿** —— 0 `Failed to load function`、0 `FAIL`、0 `NEG_FAIL`、12/12 `NEG_OK`、42 个有信号阶全 `OK`、9 个无信号 L0 `SKIP`、收尾 `ALL_DONE`。

驱动：`tools/_run_ritual_test.ps1`（runServer，日志 `build_out.txt`），起止 19:36–19:41，结束后服务端进程已杀、mods 已复原。

分图案（去重后）：
- bafang_guiyuan_circle: OK L2/L3/L4/L5 · NEG_OK
- haniyasu_circle: SKIP L0 · OK L1/L2 · NEG_OK
- kagutsuchi_flame_circle: SKIP L0 · OK L1/L2/L3 · NEG_OK
- kami_no_megumi_circle: OK L1–L5 · NEG_OK
- kaya_no_hime_circle: SKIP L0 · OK L1/L2 · NEG_OK
- kukunochi_circle: SKIP L0 · OK L1/L2 · NEG_OK
- nichirin_circle: SKIP L0 · OK L1 · NEG_OK
- oyamatsumi_circle: SKIP L0 · OK L1/L2 · NEG_OK
- resonance_relay: OK L2/L3/L4/L5 · NEG_OK
- tsukikage_circle: SKIP L0 · OK L1 · NEG_OK
- yumewatari_circle: SKIP L0 · OK L1/L2 · NEG_OK
- zaohua_circle: SKIP L0 · OK L1–L5 · NEG_OK

注：无 `NEG_SKIP`（12 图案顶阶切片均有品阶信号，与 design Findings ① 一致）。
