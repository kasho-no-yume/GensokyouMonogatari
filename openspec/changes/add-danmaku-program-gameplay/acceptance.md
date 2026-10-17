# Gameplay 集中实机验收

2026-10-17 20:33代码验证：时钟收包比较统一到已显示partial边界，新增2tick jitter与500ms RTT实际renderTime→renderViews回归通过，未放宽隐藏门槛；普通弹世界方块VoxelShape半径扫掠已接线，墙边/角落纯几何回归通过。完整build含全测试通过20261017-203038-build.log。实机高延迟闪烁尚无修正后的确认，仍不宣称根治；文末“带半径方块扫掠待补”由本更新覆盖，真实世界方块实测仍未新增。

2026-10-17最新用户反馈：上一批dynamic_warning、high_frequency_circle、sphere_split、moving_carriers、参数化moving_tornado，以及本轮petal_release、roaming_tower，均已在单人存档与500ms延迟的独立服务器测试。新增两项表现通过，整体性能符合预期。500ms下渲染/逻辑偏差可以接受，但整批弹幕闪烁仍然比较严重，当前视为未解决限制；用户不要求重复测试闪烁修复。此反馈不等于1/2/5真实玩家矩阵或量化M2通过。

本地日志曾发现display-error误差约2.04–2.88tick触发全场renderable=false，随后恢复。ProgramClock已对accepted-fit的display-error且采样误差≤4tick增加最多1本地tick的冻结旧姿态，保留原≤2tick误差/≤2tick运动holdover；严重积压/同tick后续采样超过4立即撤销，超时仍隐藏。该缓解没有消除500ms下的严重整批闪烁，不能宣称问题解决。自动化覆盖触发、冻结、期限与积压；用户明确无需再次实测此问题。之前曾推测stale触发，实际日志证据是display-error，以此为准。

## 本轮新增实机证据

| 命令 | 预期和通过条件 | 时长 |
|---|---|---|
| `/gs_program_preview start petal_release` | 同原花阵，约6秒时3个完整花瓣径向释放，共27成员；其余两瓣和花心继续原自转/呼吸/平移，不是逐隔弹选取 | 约13秒 |
| `/gs_program_preview start roaming_tower` | 三臂螺旋柱沿Bezier移动并自转/呼吸；出生后每20tick从三个顶层节点各发8弹环，8轮共192子弹，子弹脱离后独立直飞 | 约14秒 |

两项已自然结束并通过，不再要求用户重复。它们仍走正式runtime、网络与渲染。整瓣选择按Rose当前9样本/瓣及Union的稳定索引编码；自动化覆盖27释放/其余不变、wire恢复与全寿命包络。旋塔三个顶节点索引15/31/47，自动化覆盖24批/192子弹及回收。

最新用户证据：花海、尾珠性能/表现通过；split_emit、split_replace、composite_motion视觉通过，随后碰撞伤害修正通过；partial_release、motion_lab、moving_tornado、rotating_laser、dynamic_warning、high_frequency_circle、sphere_split、moving_carriers、petal_release、roaming_tower均已实机通过表现。500ms独立服下仍有严重整批闪烁，作为已知未解决限制记录，不要求重复实测。这些预览无需重复验收。

所有预览均使用正式ProgramSession/runtime/network/碰撞/renderer，静止ArmorStand仅承载owner归属，不使用TrackRunner或逐成员实体。普通难度、生存、开阔地启动；碰撞可提前消解成员，下表为无提前消解的名义数。所有新增观察集中一轮，无需逐改动停工。

## 已通过的上一批（保留命令说明，无需重复）

| 命令 | 预期和通过条件 | 时长 |
|---|---|---|
| `/gs_program_preview start dynamic_warning` | 红色预警在2秒内持续平移/转向；激活边界源点与光束轴连续；之后继续父组运动与成员公转/自转；光束遇墙截断。中途放/拆方块检查裁剪 | 约11秒 |
| `/gs_program_preview start high_frequency_circle` | 两个面向相机的蓝/金法阵，起始1秒只显示阵盘；之后80tick每tick双炮口各发3弹（共480）；旧弹不跟着炮口转。两侧绕看阵盘，世界弹道保持；末次发射后阵盘结束，子弹自然结束 | 约10秒 |
| `/gs_program_preview start sphere_split` | 6颗球面蓝弹径向减速，出生35tick后替换为12红弹，再35tick后替换为24金弹；蓝/红在边界消失，末代独立直线，名义累计42 | 约12.6秒 |
| `/gs_program_preview start moving_carriers` | 3蓝色母弹移动，每母年龄20/40/60/80各发8颗金色球面散射，总96子弹；母弹自然结束后已出生子弹保留 | 约10秒 |
| `/gs_program_preview start moving_tornado` | 48成员由正式HelixColumn生成三条螺旋臂，高度5格、下窄上宽；自转/呼吸/整体弧线推进仍独立 | 约12秒 |

旧批次停止/暂停等步骤已撤出本轮用户操作。异常反馈命令、阶段、表现；不要重启覆盖日志，agent读取run/logs/latest.log。

## 自动化与真实网络矩阵

`ProgramLatencyMatrixTest`输出`build/reports/program-latency-matrix.csv`：1/2/5独立副本、RTT0/100/250ms、抖动0/30ms、停顿0.5/2s、20/10TPS，共72组。下行保持TCP顺序；恢复请求经过上行队列。检查恢复后同刻几何、End后空image、无未完成分块。乱序分块、旧订阅/epoch、重复、缺定义、半checkpoint由ProgramNetworkTest另覆盖。此证据不是独立客户端实机。

现有`tools/net_jitter_proxy.py`和`/gs_net start|stop`可计量压缩后连接字节及压缩前包分类，旧交接“未发现网络工具”已纠正。准备真实矩阵：

```text
python tools/program_network_matrix.py prepare
python tools/program_network_matrix.py relay --rtt 250 --jitter 30
```

relay前先运行独立专用服（所有Gradle仍用wrapper），服务端25565，客户端连接localhost:25566；脚本生成实时lag_control.json、50ms wire JSONL和72行pending-real-clients清单。RTT参数映射为双向各RTT/2的注入延迟，真实端到端还含基础连接/排队延迟。proxy已修复为有界队列按读取时刻排定到达，保持TCP顺序，避免按chunk串行叠加延迟；不能声称实际RTT恒定等于注入值。未自动创建或冒充2/5个真实客户端，环境不足时保留pending。真实双视角、离开重入、目标死亡/取消、客户端停顿与低TPS仍需记录证据，不要求用户先自行搭代理。

## M2证据边界

`ProgramScalingGateTest`输出`build/reports/program-scaling-gate.json`，48→480确定性完整模板使用pattern+seed紧凑编码，初始逻辑字节2415→2529，1.047倍，满足≤2倍。空间子集与例外保留展开模板；实际收益不得外推到所有恢复/空间分片。

已有ClientFrameProbe整帧采样（wrapper任务runClientProfile）和baseline_window.py的ledger窗口，可复用；NetProfile新增program_tickcost原始样本。tools/program_ab_report.py读取两目录window.json/server.jsonl/frames.csv/proxy.jsonl自动出p95及连接逻辑/压缩后字节比率。CPU范围和总连接字节明确标注，未验证等负载与三次重复前不自动批准M2。

尚缺：legacy等负载fixture、新旧统一窗口编排、800/2000压力、花海/尾珠/蓝8五人/激光动态方块/加入突发的30秒暖机+180秒采样×3；逻辑流量≥50%与CPU/整帧p95≤110%尚无结论。已有host/receive/projection/geometryAndSubmit是CPU局部窗口，不是整帧/GPU或完整A/B。M2仍未通过，不进入正式BossCards迁移。

following/detached激光轴契约已有server/replica方向与源丢失测试，法阵预算拒绝/20次清场已有自动化；整瓣释放与巡游旋塔顶层持续发射已有自动化和实机通过证据。普通弹带半径方块扫掠、高频声音的完整场景、真实多人、蓝1/4/6完整证据和高延迟闪烁根因仍待补。不要因本批局部通过勾选整项。
