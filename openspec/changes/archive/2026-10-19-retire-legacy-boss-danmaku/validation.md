# 退役实现与验证记录（2026-10-19）

## 现行范围

用户授权开始阶段7；碰撞尺寸保持前一轮的0.5倍率。正式范围为大妖精3卡/山女4卡/蓝8卡及2底噪，共15卡，露米娅/傩面5卡继续排除。历史“必须20卡”不再扩大范围。迁移tasks中的人工验收历史说明有冲突，本记录不据勾选推断真实多人/生存/地形/读档已经通过。

## 代码退役与保留

- `AbstractTouhouBoss`不再持有/创建`TrackRunner`，没有后端选择或失败回退。正式内容直接运行`BossProgramBackend`；安装错误记录日志并停用本场攻击，不启动另一判伤路径。
- 大妖精/山女/蓝移除旧`spellCards/palette`执行依赖、实体默认发射、冷却、旧移动锁；切卡演出接`onProgramCardChanged`。HUD名称直接来自程序deck。
- 移除未被引用的`BackendSelection`及临时`BalanceNumbers.BOSS_DANMAKU_BACKEND`。
- 兼容迁移期`ProgramBackendState`：旧legacy标记不再阻止正式内容使用程序，并按血量选卡；program标记仍用于卡号恢复，运行态持久化保留。
- 傩面旧执行隔离到`UnmigratedTouhouBoss`。旧`TrackRunner/Emitter/Geometry/BossCards`仍被傩面、通用调试预览、迁移对照测试及非BOSS工具引用，不能整包删除。旧表只是参考/排除内容，正式三只不能再通过`/danmaku card`运行旧实体表，命令提示程序入口。
- 玩家武器、妖精、旧弹幕实体及其碰撞/存档实现未改动；完整构建覆盖已有相关测试，但真实游戏发射回归另列，不冒充已执行。

## 本轮自动化证据

| 验证 | 结果/证据 |
|---|---|
| 退役编译 | wrapper `20261019-230752-compileJava.log`成功 |
| 20次回收 + 共享描述缩放 + 高负载 | wrapper `20261019-230928-test.log`成功；`ProgramRetirementTest`每次2008峰值、结束后下一tick groups/members/plans/emitters/frames/locks/commands全零，checkpoint派生关系/喷口/组运动/待提交为空 |
| 48→480共享描述 | `build/reports/program-scaling-gate.json`：2461→2575逻辑字节，1.0463倍，≤2门槛通过；同一个确定性pattern，独立于800/2000多batch初始化测量 |
| 三次180秒逻辑窗口 | wrapper `20261019-231251-test.log`成功；`ProgramRetirementLoadTest`，30秒逻辑暖机+180秒逻辑采样，线性/混合×800/2000×3，所有峰值与计数通过。原始数据`build/reports/program-retirement-load.csv` |
| 语言审计 | Java字面量/新提示中英键齐全；仍因31个既有造化JEI数据派生键退出1，本轮未新增缺失 |
| strict | `openspec validate retire-legacy-boss-danmaku --strict`通过 |
| 完整构建 | wrapper `20261019-231546-build.log`成功，148 suites / 1135 tests / 0 failures / 0 errors；包含新增负载/回收及既有实体/网络/内容/存档测试 |
| 最终复验 | 内容deck缓存调整后wrapper `20261019-231741-build.log`成功；本change strict再次通过，Markdown本地链接检查无失效链接，diff检查无空白错误 |
| 专用服集成 | wrapper `20261019-231444-runProgramProbe.log`成功，真实启动Done，`PROGRAM_WORLD_PROBE`（半砖/首空气/通路）、`PROGRAM_NETWORK_PROBE`、`PROGRAM_HOST_PROBE`全部PASS并正常退出 |
| 全仓strict | 129通过/8失败；失败为未改动的danmaku-knife/laser/sphere/talisman、ritual-builder-upgrade、ritual-core-interface、weapon-gui-visual及phase-d-high-tier-content，不属于本change；所有7个程序子change通过 |

负载测量为**加速无头运行时**，实际共执行50,400个逻辑tick；碰撞sink只计派发，不执行Minecraft世界查询/伤害。`advance`含路径/派生/生命周期及碰撞派发，不含网络发布、渲染和完整世界碰撞CPU。初始化字节为`ProgramSyncServer`逻辑字节，不是压缩后连接流量，也不是50ms线上突发。不与旧实体CPU直接比较。

| 负载 | 三次advance p95（ms） | 初始化逻辑字节 | 真实峰值 |
|---|---|---|---|
| 线性800 | .5088 / .2773 / .3728 | 47,538 | 800 |
| 线性2000 | .7165 / .5928 / .8552 | 115,897 | 2000 |
| 混合800 | .5255 / .5064 / .5422 | 70,770 | 808 |
| 混合2000 | 1.1022 / 1.0904 / 1.2001 | 189,484 | 2008 |

这些数字用于检查本轮runtime回归，不是用户游戏帧率或完整服务器弹幕tick耗时承诺。减半碰撞半径会改变正式命中表现；未对旧实体A/B测量，因此不能据此宣称架构性能提升。

## 文档与规范交接

总设计已移动到`docs/danmaku-program/`，含历史specs与未勾选任务快照；从active changes移出，不执行archive、不重复合入父specs。各子change与skill设计链接已更新。六项旧capability delta仍由本change唯一持有，未归档前不自行修改主spec重复应用。

两项旧扩展实际状态及需求去向见`docs/danmaku-program/superseded-extensions.md`：orchestration早已归档；shapes原change当前不存在，仅保存历史需求映射，不伪造完成状态。操作台账顶部给现行入口/证据，future能力保持未实现。

## 最终授权与后续专项

任务1.1的真实客户端180s×3同负载矩阵、整帧、世界碰撞CPU、压缩线上流量及50ms突发，本轮尚未执行；已有自动化逻辑窗口不替代这些要求。工具仍为`tools/program_benchmark_run.py`、`program_stress_run.py`及既有relay/帧记录器，需实际已登录客户端/服务器/连接计量环境。历史阶段5放宽M2记录不等于本期完整门槛已测。

真实非BOSS武器/妖精发射、正式三只生存/地形/多人/读档及碰撞尺寸手感不因构建通过而自动验收。

在前一轮明确报告上述边界和3/6任务状态后，用户确认“ok没问题，完成剩下的收尾后就可以归档了”。本期据此按已交付实现、自动化及专用服探针收口；剩余真实客户端/线上计量/人工回归转`docs/danmaku-program/follow-up-validation.md`专项，未记为实测通过。既有31个lang缺失和8项全仓strict失败保持独立记录，不修改无关规范凑绿。归档仅合入本change的7份delta，总设计父specs不重复合入。
