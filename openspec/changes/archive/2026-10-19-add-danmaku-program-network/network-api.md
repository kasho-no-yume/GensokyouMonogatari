# Network implementation handoff (2026-10-16)

代码根：`src/main/java/com/bitsson/gensokyou/danmaku/program/network/`。

## 入口

- `ProgramWire`：Definition/Description/Start/Checkpoint/Decisions/ClockDigest/Resync/End/Release封套，有界解码、规范描述hash、分片、差分与mask补丁。
- `ReplicaState.capture(ProgramSession.Snapshot, wire)`：按批次共享描述，投影组时钟/父闭包/源绑定/存活与伤害mask/采样/未来Commit/Emitter游标/已消费派生触发。`state`只解析，不执行世界操作。
- `ProgramReplica`：每维度共享ProgramClock，缺定义有界暂存、chunk完整提交、重复/旧epoch拒绝、终止tombstone、摘要恢复、有界历史。定义解码需要宿主安装完整冻结OperationRegistry；未知类型/schema显式拒绝。
- `ProgramSyncServer`：每连接发布、定义cache、稀疏差分、兴趣代次、维度时钟/轮转摘要、恢复授权与限流、按消息种类统计逻辑字节。
- `ProgramInterest`：攻击AABB与边距选择、父/成员源闭包、稳定group/batch/index分片、有限快照路径保守包络。复杂组运动和跟随源包络由宿主显式提供。
- `VisualOffset`：纯视觉小误差消去接口，不改变轨迹或判伤。

## NeoForge接线

`ModNetworking`注册`ProgramDataPayload`（S2C）与`ProgramResyncPayload`（C2S）。`ProgramClientNetwork.install(registry, limits)`在接收正式定义前安装冻结类型库；默认空库会拒绝未知定义，不静默跳过。

先显式安装`ProgramServerHost` session，再用`ProgramNetworkHost.install(level, session, limits, margin, clockPeriod, envelopeProvider[, facts])`安装网络宿主。tick尾发布、退出清理、C2S只恢复既有订阅；无正式BOSS自动启用。`facts`允许gameplay注入量化Beam长度等权威事实（用`ReplicaState.beam`），必须与碰撞使用同一结果。

## 验证与交接

`ProgramNetworkTest`覆盖实际runtime→wire→replica运动对照、480成员分片、半份不生效、缺定义、重复/旧epoch/End、重订阅、迟到rebase、时钟失信冻结、两代分裂后加入、消费游标、编译定义兼容性、未来预警/Commit、共享组运动与跟随源、摘要失配、请求授权/限流、surface缺点、畸形mask原子拒绝和终止成员不能复活。

真实专用服probe验证注册启动、现有世界查询与宿主tick，并增加FriendlyByteBuf/StreamCodec实际payload往返；不等于两名客户端实网传输验证。

验证记录：最终全量wrapper build（`build/agent-logs/20261016-195712-build.log`）117 suites / 971 tests / 0 failures / 0 errors / 0 skipped，network新增18项。专用服日志`build/agent-logs/20261016-195446-runProgramProbe.log`含`PROGRAM_NETWORK_PROBE PASS dedicated-payload-codec`、世界查询和宿主探针PASS。OpenSpec strict通过。ProgramSyncServer.publishSpatial与ProgramNetworkHost.spatial已接入空间分片发布，验证切换和重进入保持成员身份。

gameplay须完成：完整操作库装配、真实内容保守包络生产者、真实动态光束长度生产者、碰撞/声音/渲染消费者、实际延迟/多人重追踪矩阵、逻辑字节与压缩后线上字节对照和CPU/帧时验收。空间分片通过ProgramNetworkHost.spatial注入成员保守包络，缺失时保守发整批。当前投影差分需要逐tick遍历状态，其CPU成本尚无性能达标证据。core旧后端性能基线和跨平台fixture仍未完成。
