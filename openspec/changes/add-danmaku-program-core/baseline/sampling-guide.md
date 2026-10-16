# 旧后端性能采样：用户与接手 agent 操作手册

状态：待采样。用户于2026-10-16选择以后抽空测试，保留任务1.3及阶段最终验收阻塞。不得将下述步骤或客户端启动成功记为实测通过。

## 分工与准备

用户负责Minecraft窗口操作、进入测试世界、固定视角/走位；agent负责启动服务、代理、采样器、保存配置与分析。先使用可丢弃测试世界，保留世界seed和场景坐标。不要在正式存档改变弹数上限。

记录：git commit及未提交diff、mod jar SHA-256、JDK、OS、CPU/GPU/内存、显卡驱动、分辨率、视距/模拟距离、FPS上限、VSync、服务器压缩阈值、全部实际配置、玩家数、seed、站位和走位规则。当前旧卡声明在legacy-cards.json，Excel源在sheet-snapshot.json。

每场景暖机30秒，正式采样180秒，独立重复3次。单人先固定站位和视角，后续走位/多人必须单独标场景。记录真实出生/存活分布和预算拒绝，不能只记录配置弹数。

## 启动拓扑

建议独立服务端与客户端，以便线上字节真实经过TCP：

```text
客户端 → 127.0.0.1:25566（代理）→ 127.0.0.1:25565（测试服务端）
```

Gradle调用必须走wrapper。服务端使用隔离的run-test目录，先由agent准备EULA、测试世界和权限：

```powershell
.\tools\gradle_task.ps1 runServerTest -NoWait
.\tools\gradle_task.ps1 runClient -NoWait
```

若客户端已打开，复用该实例，避免重复启动。普通runServer与runClient共用run目录，别同时用它们覆盖日志。

agent先创建证据目录（例`build/danmaku-baseline/flower_r1`），在单独终端启动代理：

```powershell
python tools/net_jitter_proxy.py --listen 25566 --target 25565 --latency 0 --jitter 0 --profile build/danmaku-baseline/flower_r1/proxy.jsonl --profile-window-ms 50
```

用户通过多人游戏连接`127.0.0.1:25566`。单人集成服务器的内存连接不能替代这份线上字节证据。结束后先停止游戏侧采集并断开客户端，留至少一个50ms窗口让代理写出，再Ctrl+C。

## 第一份可操作样本：大妖精花海

以下命令已核对源码，卡序从0开始，花海是1。需要作弊/OP权限。使用normal难度、生存模式；零伤害预览仍经过命中判定，但不会记录真实战斗扣血，用于固定性能负载。

```text
/difficulty normal
/gamemode survival
/danmaku card big_fairy 1 240 0
```

面对固定方向执行预览：旧预览源放在玩家眼前8格，不是真BOSS，也没有底噪。站位和视角都记入记录。启动30秒后开始正式采样：

```text
/gs_boss danmaku reset
/gs_net start flower_r1
/gs_net mark sample_begin
/gs_boss danmaku
```

同时启动已验证的CPU/帧时采样器，采180秒。结束时：

```text
/gs_boss danmaku
/gs_net mark sample_end
/gs_net stop
/danmaku stop
```

保存`/gs_net start`打印的JSONL路径；通常在服务端工作目录的net_profile下，不要假设在客户端run下。保存客户端/服务端日志和聊天输出。旧stop可能只停发，不能假设已有弹立即清空；等待最长寿命并确认live回到基线，再开始下一次。

用flower_r2、flower_r3重复。山女尾珠样本改为：

```text
/danmaku card yamame 0 240 0
```

所有次数采用同一姿态、场景、配置。正式BOSS及底噪样本另外测试，不把预览数据标为BOSS战数据。

## CPU、帧时与资源采集：现有工具的不足

`/gs_boss danmaku`有累计tick耗时和计数，不能从两次累计读数推导p95。F3瞬时FPS也不能作为frame time p95。

接手agent必须先准备并验证逐tick/逐帧样本采集：可使用经核实可用的客户端帧时记录器及JFR/性能分析工具；记录工具版本、进程PID、计时范围与原始CSV/JFR路径。JFR CPU采样可定位热点，但没有逐tick事件时不能冒充danmaku tick p95。缺少细粒度记录器时，应先补测试专用仪表，而不是凭命令平均值填报告。

资源至少记录server活跃弹数、出生/删除/拒绝、堆与GC、客户端可见弹数；每秒样本保留原始值。CPU区分服务器弹幕tick、客户端求值/渲染；同机双端资源竞争需注明。

## 全部场景与缺少的驱动

| 场景 | 现成入口/接手工作 |
|---|---|
| 花海 | 上述big_fairy卡1预览，固定站位 |
| 山女尾珠 | yamame卡0预览；旧缺拍与尾珠端点记录在manifest |
| 48→480同图案 | 先准备低层等量驱动，不能拿不同卡替代；确认出生与存活分布 |
| 800/2000存活 | 测试配置提高cap；agent准备并验证负载驱动，测试后恢复配置 |
| 蓝8五人 | 旧yakumo_ran卡7是旧设计；五名真实连接或验证过的测试客户端，不能单人冒充五人 |
| 旋转激光/动态方块 | 固定射线布局与方块变更时间脚本；独立记录动态裁剪开销 |
| 中途加入突发 | 固定加入时刻与位置，保留50ms线上突发窗口 |

这些驱动不是本轮已交付工具；缺驱动的场景保持pending，接手agent需要补齐。修复内容造成的弹量增量与后端成本分开比较。

## 网络分析

agent复制真实server JSONL到证据目录后执行：

```powershell
python tools/boss_net_profile.py --proxy build/danmaku-baseline/flower_r1/proxy.jsonl --server build/danmaku-baseline/flower_r1/server.jsonl --out build/danmaku-baseline/flower_r1 --label flower_r1 --window-ms 50
```

保留原始JSONL、CSV、PNG与stdout。工具需要Pillow；当前代理dir为up/down，server wall为毫秒、proxy wall为秒，分析脚本负责换算。
注意现有分析器默认取session start/stop或summon/death窗口，sample_begin/end只是marker，**不会自动裁剪它们**；agent须确保session窗口就是正式180秒，或在分析时明确按marker裁剪。多个连接要按玩家/连接分别统计，不能把全服总字节标为每客户端。

## 完成交付

每个场景3份原始采样、环境记录、复现脚本/走位说明和汇总。汇总分别列逻辑字节、线上字节、50ms峰值、server弹幕tick中位数/p95、client frame中位数/p95、真实弹数分布、回收结果。缺项写缺项，绝不补估值。

全部旧基线齐全后更新performance-baseline.md并勾选1.3；最后复验core任务、build和OpenSpec，才勾选2.6并考虑归档。新后端对照采样属于后续gameplay，保持相同负载与环境。
