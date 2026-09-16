# Tasks: fix-ritual-test-negcheck

## 1. 生成器修改（tools/validate_ritual_pattern.py）

- [x] 1.1 `setup_neg` 布线：先对顶阶 `expanded` 全量格位逐格发 `setblock <x> <y> <z> minecraft:air replace`，再接现有低阶铺设行
- [x] 1.2 `check_neg` 加 `hi_exp>=1` 守卫：0 信号图案改发 `say [GS-TEST] NEG_SKIP:<path> no-tier-signal`（保留 schedule 衔接行）

## 2. 离线自检

- [x] 2.1 重跑 `validate_ritual_pattern.py`（0 ERROR）并 `--test-out` 重建 `run/world/datapacks/gs_ritual_test`
- [x] 2.2 抽查任一 `setup_*_neg.mcfunction`：air 清场行数 == 顶阶切片格数、全部带 `replace`、清场段在铺设段之前；链式 schedule 无缺环

## 3. 实机全链验证

- [x] 3.1 起专用服务端跑完整套件（同 add-yumewatari-behavior 5.3 驱动法），断言：零 `Failed to load function`、每图案有信号阶 `OK`、每图案 `NEG_OK`（无信号图案 `NEG_SKIP`）、收尾 `ALL_DONE`、零 `FAIL/NEG_FAIL`
- [x] 3.2 记录运行日志摘要到本变更目录（留档一行结果即可），完毕后杀掉服务端进程
