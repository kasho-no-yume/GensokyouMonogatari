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

当前只有测量方法草稿；CPU、流量、帧时和资源数值均为 `pending-measurement`。任务 1.3 尚未完成，必须在本阶段取得旧后端实测，后续阶段再用同场景采集新后端 A/B 数据。

## 复现步骤

1. 用 wrapper 启动测试客户端/服务器。
2. 生成指定 BOSS 与玩家脚本。
3. 每 50ms 记录命令输出和 profiler 快照。
4. 分别统计逻辑包字节、压缩后字节、server tick p95、client frame p95、活跃实体数和分配。
5. 保存 seed、程序/旧卡标识、配置和完整日志路径。
