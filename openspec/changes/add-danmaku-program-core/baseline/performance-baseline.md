# Legacy backend performance baseline protocol

## 固定条件

- JDK 21、Minecraft/NeoForge 1.21.1/21.1.248。
- 固定 seed、BOSS、玩家脚本、视距、分辨率和压缩设置。
- 暖机 30 秒，采样 180 秒，重复 3 次，报告中位数和 p95。
- 场景：48→480 同图案、800/2000 存活弹、花海、山女尾珠、蓝 8 五人、动态方块激光。

## 记录入口

- `/gs_boss danmaku`：发射、命中、移除、tick timing、同步和编排计数。
- `DanmakuBudget.timingStats()`：旧实体弹 tick 累计。
- `DanmakuSyncStats.summary()`：旧同步诊断。
- 客户端 profiler/F3：frame time p95 与分配情况。

## 当前状态

本 change 已完成花海代表场景的旧后端实测基线。新后端尚未接入游戏，因此这里的结果只作为后续 runtime/gameplay/migration 阶段的 A/B 参照，不代表新架构已经通过性能门槛。

三次有效重复均使用同一台机器、同一 dirty dev classpath、同一 seed、单人 survival、VSync 关闭、RTX 4060、约 40° 仰视固定站位、无走位、无 50ms 网络抖动。服务端、客户端和代理同机运行，存在资源竞争，后续 A/B 必须保持相同拓扑。

| 重复 | 窗口 | live min/median/p95/max | server tick p95 | client frame p95 | 逻辑流量 | 线上下行 |
|---|---:|---:|---:|---:|---:|---:|
| flower_r2 | 180.0s | 126 / 448 / 662 / 728 | 2.724ms | 18.44ms | 5.82MB | 41.12KB / 5.95MB |
| flower_r3 | 180.0s | 126 / 562 / 847 / 943 | 2.973ms | 18.38ms | 7.03MB | 54.65KB / 7.11MB |
| flower_r4 | 180.0s | 350 / 847 / 1098 / 1123 | 3.039ms | 18.38ms | 7.47MB | 53.07KB / 7.58MB |
| 三次中位数 | 180.0s | 126 / 562 / 847 / 943 | 2.973ms | 18.38ms | 7.03MB | 53.07KB / 7.11MB |

每轮均满足：session 窗口 180s、tickcost 覆盖率 100%、resets=0、live=0 样本为 0，并保存 `server.jsonl`、`proxy.jsonl`、`frames.csv` 和 `window.json`。原 `flower_r1` 窗口含 live=0 样本，作废，不纳入重复统计。

证据目录：`build/danmaku-baseline/flower_r2`、`flower_r3`、`flower_r4`。r4 保存了完整环境记录；r2/r3 与 r4 共用同一未重启的客户端/服务端进程及代码状态。环境采集文件中的 `server.properties` 难度仍显示 easy，但每轮采样前均通过 RCON 确认运行时 difficulty=normal；该差异须在后续报告中保留说明。

## 复现步骤

1. 用 wrapper 启动测试客户端/服务器。
2. 生成指定 BOSS 与玩家脚本。
3. 每 50ms 记录命令输出和 profiler 快照。
4. 分别统计逻辑包字节、压缩后字节、server tick p95、client frame p95、活跃实体数和分配。
5. 保存 seed、程序/旧卡标识、配置和完整日志路径。

## 后续对照

后续新后端必须在同一硬件、seed、站位、视角、客户端设置、玩家脚本和实际弹量分布下重新采样。不能用新后端较少的弹量或不同场景替代 A/B；新后端的服务端 tick p95、客户端 frame p95、逻辑/线上流量和资源回收结果另行记录。
