# 清理历史世界中的测试结构（可选）

**背景**：早期 e2e 测试数据包（`gs_ritual_test` / `gs_autotest` / `gs_yume_autotest`）
会在世界加载时自动执行，在固定坐标的空中建造一排仪式测试结构并 `forceload` 区块。
本仓库已改为**隔离测试世界**（`run-test/`）+ **显式触发**，不再污染世界。
但**已经被写进旧世界存档**的结构不会自动消失，需要手动清理（或直接弃用该测试世界）。

> ⚠️ 先备份存档再操作。以下 `fill ... air` 会清空指定长方体范围内的**一切**方块
> （含该高度带内的合法建筑），仅适用于确认是开发/测试世界的存档。

## 1. 解除 forceload

```
forceload remove 0 -20 728 28
forceload remove 96 96 104 104
forceload remove 136 96 144 104
forceload remove 160 92 176 108
forceload remove 200 24 292 70
```

`forceload query` 可回读当前强制加载区块。

## 2. 拆掉测试结构（按测试带）

测试结构都在空中（y ≈ 95–108），按测试带清空即可：

```
# gs_ritual_test 主列（x 8..730, z -3..11, y 98..103）
fill 0 95 -25 730 110 30 minecraft:air

# gs_autotest 三段
fill 96 92 96 104 108 104 minecraft:air
fill 136 92 96 144 108 104 minecraft:air
fill 160 92 92 176 108 108 minecraft:air

# gs_yume 探针带
fill 200 92 24 292 108 70 minecraft:air

# struct_compile 建筑预览锚点 (104,100,20)
fill 94 95 10 114 115 30 minecraft:air
```

若不确定坐标，可先用较小的 `fill` 逐步试探，或直接用结构方块框选删除。

## 3. 检查残留数据包

确认世界/存档目录下不再有测试包：

- `run/world/datapacks/`（dedicated 共享世界）
- `run/saves/<存档>/datapacks/`（单人存档）
- `run-test/world/datapacks/`（隔离测试世界，允许存在）

旧工具曾通过 `tools/struct_compile.py` 把测试包同步进单人存档；该同步现已**默认关闭**
（仅 `GS_SYNC_TEST_DATAPACK=1` 才开启）。恢复无测试包的存档只需删除上述目录中的
`gs_ritual_test` / `gs_autotest` / `gs_yume_autotest`。

## 4. 验证

清理后进服应满足：

- 聊天栏无 `[GS-TEST]` / `[GS-AUTO]`；
- 天空中原测试坐标处不再有仪式结构；
- `/forceload query` 不再列出测试区块。
