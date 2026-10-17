# 第五期集中验收执行清单

## 当前集中压力入口（本轮重新实现）

实测更新：修复版独立服高档high-r2-fixed与约500ms配置RTT的high-latency-r1均完成180秒采集、峰值2008、约20TPS，用户零延迟确认表现正常，高延迟反馈未察觉闪烁但可能漏看。前者host/frame p95=41.1486/18.2402ms，后者39.9493/18.4703ms，恢复均0、墓碑/边界/批次复活错误均0、停止runtime零/idle。后者日志仍有三次queued-samples短暂隐藏，已补原有±2tick/2local tick有界holdover，待最终短时复验；不称完全无闪烁。当前证据为独立服单客户端，不覆盖单机或真实多人；CPU为程序host而非整服务器tick。原始目录`build/reports/stress-v2/`。

用户已主动撤回此前ProgramStressHost源码，旧stress_mixed-r1只作失败诊断；summary恢复0与stats恢复171/最终173不符，不沿用其通过判断。新入口复用ProgramBenchmarkHost，独立服/客户端必须加载本轮构建。

连接计量代理`127.0.0.1:25567`，开阔高空，生存模式、观察前方约16格。先由agent运行：

```text
python tools/program_stress_run.py --player Dev --count 800 --root build/reports/stress-v2/low-r1
```

30秒暖机+180秒采集，200tick轮：640父弹+160定时派生+8旋转激光，峰值808。父弹呼吸/旋转且启用碰撞；父寿命200、子寿命80。低档通过后count改2000并换新root，高档1840父弹+160派生+8激光，峰值2008。以statuses实际数量和原始network stats恢复计数为准，不能将未解析值默认为0。日志/账本/帧/proxy自动保存，异常有FAILED.txt，停止后检查idle与runtime全零。测试目标及约束见design当前压力fixture决定；单机、延迟/重连与自然结束仍需分别记录。

构建验证：`20261017-234444-build.log`完整build通过（2m30s）；`20261017-234414-test.log`定向runtime测试通过，strict通过。尚无本轮实机压力结论，不勾3.2–3.4。

> **最新有效范围（用户授权）：第1–4项及代表切片已确认，无需重复，tasks2.1–2.6已收口。第6项旧严格A/B与54项不再要求执行。接下来只准备tasks第3节一次高密度复杂压力测试；具体场景/峰值/时长与命令须由agent实现核实后集中交付，不把本文件历史命令当新待办。** 精确50%/110%、三次重复和真实1/2/5人矩阵转后续专项，不宣称其已过。

本清单按“单机 + 独立服”准备，统一执行一次。已有实机通过的预览仍需按清单记录结果，但不需要重复定位旧问题。每次启动前保存上一轮 `run/logs/latest.log`，不要同时运行多个 Gradle/游戏实例。

最新用户反馈：本清单第1–4项均已执行且无问题，不要求重复。该确认仅记录本清单范围，不扩展成真实2/5人或M2通过。

更正：第6项目前是目标采样协议，尚不能交给用户执行。现有代码没有统一legacy/program等负载基准入口，没有program_48/480/800/2000等对应命令，也没有可持续210秒的共同调度器。baseline_window.py仅识别旧[danmaku-preview]日志，当前program预览会自然结束；manifest仅为证据登记，不创建测试场景。须先补齐基准实现和端到端采样编排，再以实际验证过的命令重写第6项。此前声称非验收工具已经闭合不准确。

## 1. 构建与环境

在仓库根目录执行：

```powershell
python tools/program_m2_manifest.py prepare
python -m unittest tools.test_program_m2_manifest tools.test_net_jitter_proxy
.\tools\gradle_task.ps1 build
openspec validate add-danmaku-program-gameplay --strict
```

期望：标准库测试通过，Gradle 输出 `BUILD SUCCESSFUL`，strict validation 通过。

固定并记录：JDK 21、客户端分辨率、视距、资源包、压缩设置、世界 seed、玩家动作、难度、生存模式、服务器 TPS。

## 2. 单人预览总回归

启动独立服和一个客户端，进入开阔区域，保持生存模式。对以下命令逐项执行；每项自然结束后再执行下一项：

```text
/gs_program_preview start blue1
/gs_program_preview start blue4
/gs_program_preview start blue6
/gs_program_preview start flower_sea
/gs_program_preview start tail_pearls
/gs_program_preview start split_emit
/gs_program_preview start split_replace
/gs_program_preview start composite_motion
/gs_program_preview start partial_release
/gs_program_preview start motion_lab
/gs_program_preview start moving_tornado
/gs_program_preview start rotating_laser
/gs_program_preview start dynamic_warning
/gs_program_preview start high_frequency_circle
/gs_program_preview start sphere_split
/gs_program_preview start moving_carriers
/gs_program_preview start petal_release
/gs_program_preview start roaming_tower
```

期望：

- 预警先出现，危险成员只在批准后出生；成员不以 Minecraft 实体形式出现。
- 普通弹轨迹连续、不会因视角改变而改变世界位置；激光方向沿炮口/模板轴，墙体裁剪正确。
- 分裂、派生、释放和移动母弹按各自命名场景的数量与时序发生；父弹结束不误删已出生子代。
- 碰撞和伤害只发生在服务端，生存玩家能观察到命中；停止、自然结束或 owner 失效后资源归零。
- `/gs_program_preview stats` 的 runtime members/groups、pending transfers 和结束后的日志应归零；不出现异常、恢复风暴或持续整批空帧。

其中 `blue1/blue4/blue6` 要重点记录跨轨、目标槽、出生净距、激光断口和蓝6转弯；`flower_sea/tail_pearls` 重点记录重瞄、跨轮和尾珠漂浮期；其余按命令名检查派生或运动语义。

## 3. 暂停、重启、晚加入和重追踪

使用 `flower_sea` 或 `tail_pearls`：

1. 预览运行中暂停客户端约 5 秒，恢复后观察弹幕是否继续并保持时间连续。
2. 停止预览，再立即启动同一预览两次。
3. 预览运行中让客户端离开兴趣范围，再返回。
4. 在预览运行一段时间后重新连接客户端，观察晚加入 checkpoint。
5. 让预览自然结束，再确认旧画面不会复活。

期望：暂停不推进客户端显示时钟；重启从新 birth 开始；离开/返回只恢复当前权威状态；晚加入不会显示负年龄或旧成员；End 后不再渲染、不再请求恢复。

## 4. 延迟与低 TPS

先生成矩阵记录：

```powershell
python tools/program_network_matrix.py prepare
```

使用独立服 25565，relay 监听 25566，客户端连接 25566。至少执行以下三组：

```powershell
python tools/program_network_matrix.py relay --rtt 100 --jitter 0
python tools/program_network_matrix.py relay --rtt 250 --jitter 30
python tools/program_network_matrix.py relay --rtt 250 --jitter 30
```

第二组执行期间暂停客户端约 2 秒；第三组在服务端使用低 TPS 条件运行。每组运行 `flower_sea`、`tail_pearls`、`dynamic_warning` 和 `high_frequency_circle`。

期望：允许短时渲染/逻辑偏差，但不能出现持续整批闪烁、永久幽灵成员、恢复请求无限增长或 End 后残留。记录 `program-clock-transition`、`program-client-timing`、`program-preview STOP` 和 proxy JSONL。若闪烁仍严重，记录为失败限制，不通过时钟稳定性验收。

## 5. 多视角与外部源边界

单机环境无法完成真实 2/5 玩家矩阵，因此只执行可完成部分：同一客户端改变视角绕行 `high_frequency_circle`、`rotating_laser` 和 `motion_lab`；确认法阵面向相机而世界炮口/弹道不变。

期望：相机朝向只影响 billboard，不能改变弹道、激光轴、碰撞或另一视角应看到的世界位置。真实双客户端、外部实体源和 5 人目标槽保留为未完成验收项。

## 6. M2 数据采集

### 当前新增跑测：先执行48弹一对基准（两次，各约3分32秒）

**该首对已完成，无需按本节重复执行。** 新位置预检通过后，两侧全窗口均48颗；报告`build/reports/bench-openair/compare-48-r1.json`。frame p95旧18.6066ms/新18.849501ms；连接总wire旧1368110/新892748 bytes。CPU旧.1903ms/新1.1749ms，统计范围不同，不能用于CPU门槛。M2仍未完成。下一次用户跑测应等待共同CPU范围/弹幕专属字节补齐后的新步骤，不直接扩跑三轮或54项。旧失败/缺弹记录仅为历史。

最新首轮分析：legacy窗口180.044s、program180.048s均采集成功，但legacy有36个换批tick计到96弹（其余48），program全程46弹；因此comparisonReady=false，不能用于等负载/M2结论，不要求扩跑其他档。新修正在启动前检查全寿命球体走廊是否被实体/方块阻挡，旧cohort按统一100tick边界显式回收；脚本暖机/报告检查实际live数量。该修正尚需更新服务端后重新跑一对，旧数据保留作诊断。不要把metadata相等误认为实际负载相等。

当前会话协助环境：原25566高延迟代理保留；agent另启本次零延迟计量代理在25567，客户端应连接`127.0.0.1:25567`。下列通用25566命令仅在没有已有代理占用时使用。本次已由agent启动的服务无需重复启动。

首次实测记录：legacy48 r1的180.044秒窗口已采集；postprocess同名变量覆盖已修复并由agent恢复证据。program48 r1在暖机中因Pre事件向已执行边界排程导致独立服崩溃（crash-2026-10-17_21.49.33-server.txt），不是有效性能结果。现宿主Pre改排到gameTime+1并补边界测试；重试仅program，使用新root保留失败目录，勿覆盖或把Gradle运行服务器的BUILD SUCCESSFUL当实机通过。

2026-10-17新增`gs_program_benchmark`共同低层负载和`program_benchmark_run.py`。四档48/480/800/2000可用；先跑下面一对确认真实客户端/ledger/代理收集闭环，再扩三轮全档。第1–4项已通过，无需重跑。这里只新增低层基准，花海/尾珠/蓝8/加入突发等价fixture仍未实现，不要求执行旧54项。

1. 客户端和独立服必须使用本次相同构建。使用隔离独立服`run-test/`与客户端`run/`，不要同时启动普通runServer与runClient共用run目录。先在终端启动独立服：

```powershell
.\tools\gradle_task.ps1 runServerTest -NoWait
```

等待`run-test/logs/latest.log`出现`Done (`后，再启动带整帧采样的客户端：

```powershell
.\tools\gradle_task.ps1 runClientProfile -NoWait
```

若已有旧进程，先正常退出再启动本次构建；不要重复拉起占用同一存档的实例。

2. 在独立终端启动零延迟计量代理，保持运行：

```powershell
python tools/net_jitter_proxy.py --listen 25566 --target 25565 --latency 0 --jitter 0 --profile build/reports/bench-wire.jsonl
```

3. 客户端连接`127.0.0.1:25566`。选择开阔、已加载的场地，玩家站定，镜头水平，前方约16格及上方20格无障碍。使用生存模式，无其他预览/弹幕。两种模式保持同一位置和镜头。球弹平面从前方16格以0.08格/tick向玩家方向移动，100tick寿命仅移动8格，站定时不会撞玩家；碰撞仍启用。高档阵列向上展开，不要在低顶房间测。

4. 复制`tools/program_benchmark_environment.example.json`为`build/reports/bench-environment.json`，填写真实硬件/分辨率/帧率上限/VSync、视距、世界位置，确认压缩阈值与server.properties一致。脚本拒绝必填字段中的REPLACE占位。

5. 在仓库根目录依次运行（把YOUR_NAME改为在线玩家名）：

```powershell
python tools/program_benchmark_run.py --player YOUR_NAME --mode legacy --count 48 --repeat 1 --proxy-log build/reports/bench-wire.jsonl --environment build/reports/bench-environment.json --root build/reports/bench-first
python tools/program_benchmark_run.py --player YOUR_NAME --mode program --count 48 --repeat 1 --proxy-log build/reports/bench-wire.jsonl --environment build/reports/bench-environment.json --root build/reports/bench-first
```

脚本使用现有run-test RCON配置（默认本机25575），自动启动300秒上限的负载，暖机30秒、采样180秒、复制ledger/帧日志/代理日志、保存metadata/window/summary并停止负载。期间不移动、不切菜单、不最小化、不启动其他测试。失败保留FAILED.txt；不会覆盖已有证据目录。重试换一个新的--root。

6. 两次都显示`collected:`后生成对照报告：

```powershell
python tools/program_ab_report.py build/reports/bench-first/program_48-r1-legacy build/reports/bench-first/program_48-r1-program --output build/reports/bench-first/compare-48-r1.json
```

期望：两种模式均每5秒一批48颗，队形/颜色/速度/寿命一致，无其他运行负载；结束后清场。报告`dataReady=true`且`equivalentMetadata=true`，CPU/frame样本非零。CPU统计范围仍不同，连接字节不是弹幕专属逻辑字节，`m2Passed=false`为预期，本次只确认新基准采集链可用。首次真实跑测后应由agent检查异常/负载一致性再扩全量，不先让用户执行54项。

7. 把两次是否成功、画面差异和`build/reports/bench-first/`报告结果告知agent；日志留在原处供agent分析。需要扩展时，以同样命令修改count为480/800/2000、repeat为1/2/3即可，但首次无需先跑完这些。

**暂不可执行：以下为待实现基准工具的要求，不是当前可运行操作。无需用户手工拼装文件或反复重启短预览来凑采样时长。**

每个场景分别运行 legacy 和 program，使用相同 seed、动作、硬件和采样配置。每种模式重复 3 次：30 秒暖机后采样 180 秒。证据目录必须包含：

```text
metadata.json
window.json
server.jsonl
frames.csv
proxy.jsonl
```

场景由 `build/reports/program-m2/manifest.csv` 给出，共 9 个场景、3 次重复、两种模式共 54 项。完成后用：

```powershell
python tools/program_m2_manifest.py validate
python tools/program_ab_report.py <legacy-dir> <program-dir> --output <report.json>
```

期望：manifest 全部 ready，legacy/program 元数据一致，报告能给出 CPU p95、整帧 p95、逻辑字节和线上字节。M2 通过条件为：逻辑弹幕字节至少下降 50%，server danmaku CPU p95 与 client 整帧 p95 不超过 legacy 的 110%，48→480初始逻辑字节不超过 2 倍，End 后资源归零，恢复有界。工具不会自动批准 M2，需要依据 54 项证据人工确认。

## 7. 结果回收

最终提供：

- 每个预览的通过/失败和简短现象记录；
- 延迟组的 RTT、抖动、暂停/低 TPS、闪烁结论；
- `program_m2_manifest.py validate` 输出；
- 三次重复的 legacy/program 报告；
- 关键日志路径和异常摘要。

只有这些结果齐全后，才更新 `tasks.md` 的 1.5、2.3–2.7；真实多人不足时不勾选对应完整任务。
