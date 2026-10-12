## 1. 代理计量模式（线上字节）

- [x] 1.1 给 `tools/net_jitter_proxy.py` 增加 `--profile <path>`：每连接分配递增 id 并记录对端地址，
      按方向（upstream/downstream）累计字节；默认 50ms 窗口落一行 JSONL（追加 + flush）
- [x] 1.2 明确并保持「零延迟、零改写」：profile 开启且 latency/jitter=0 时不 sleep、不缓存、字节原样转发
- [x] 1.3 保持与既有延迟模式正交：`--profile` 可与任意 latency/jitter 组合；不传时不改变现有行为
- [x] 1.4 记录本机墙上时钟（`time.time()`）与单调时钟（`time.monotonic()`），供后续对齐与漂移核对

## 2. 事件标记流（游戏侧）

- [x] 2.1 新增服务端 marker 落盘（`server/` 下 JSONL，行内含 `wall_ms` + `game_tick` + `type` + 载荷），
      逐行 flush；文件路径可经命令/config 指定
- [x] 2.2 召唤标记：在 `EntityJoinLevelEvent` 过滤 `AbstractTouhouBoss`（复用 `DanmakuBudget` 既有订阅处）
- [x] 2.3 死亡标记：挂 `AbstractTouhouBoss#die` / `dropThemedCard`
- [x] 2.4 符卡切换标记：挂 `AbstractTouhouBoss#onCardChanged`，记录符卡下标/名称
- [x] 2.5 复核：确认这些钩子均在服务端线程执行，无并发写风险；无需加锁

## 3. 服务端包类归因 handler

- [x] 3.1 在玩家出站管线、帧/压缩编码器**之上**插入 `ChannelOutboundHandler`，按玩家（`Connection`）区分；
      插入点插不上时**显式报错**，不静默
- [x] 3.2 按包类统计包数与压缩前字节数，按 tick/窗口聚合；覆盖 `AddEntity` / `SetEntityData` /
      `MoveEntity*` / `RemoveEntities` / 区块 / 交互等主要 clientbound 类
- [x] 3.3 未登记的包类归入「未知」桶并计数；MUST NOT 抛异常或中止
- [x] 3.4 spawn 类包按实体类型细分（供「单弹生成成本」推导）；记录窗口内 spawn 计数
- [x] 3.5 归因记录写 JSONL（含玩家标识 + tick + 时刻），与代理记录同目录
- [x] 3.6 可选：入站（c2s）观测（首版可只做 s2c，若做则复用同一结构）——决定：首版只做 s2c

## 4. 命令入口与接线

- [x] 4.1 新增 `/gs_net`（权限 2）子命令：`start` / `stop` / `mark <label>` / `status`，
      用于显式括界与手动打点（补自动 marker 覆盖不到的场景）
- [x] 4.2 `tools/run_server_jar.ps1` 增加 `-Profile`：以零延迟启动代理计量模式并指定输出路径，
      幂等替换既有代理（复用 `Stop-LagProxy`）
- [x] 4.3 `-Profile` 参数透传到 `--profile`，且不破坏既有 `-LatencyMs/-JitterMs/-LagOff/-SetLag` 行为

## 5. 离线合成与报告

- [x] 5.1 新增 `tools/boss_net_profile.py`：读取代理记录 + 服务端归因记录 + marker，按墙上时钟对齐
- [x] 5.2 产 CSV：逐窗口线上字节（分方向）+ 包类分解 + 命中的符卡下标
- [x] 5.3 产 PNG：用 PIL 渲染以时间为轴的字节曲线（堆叠/阶梯）+ 符卡竖线标记；不引入 matplotlib
- [x] 5.4 产文本摘要：峰值、均值、峰值/均值比、各符卡占比、单弹生成成本（bytes/spawn）
- [x] 5.5 对账自检：打印服务端逻辑字节与代理线上字节的累计差及来源说明；不平即暴露
- [x] 5.6 支持按玩家过滤，并对跨机（时钟偏移大）场景给出不可信标注

## 6. 验证与交付

- [x] 6.1 `.\tools\gradle_task.ps1 build` 通过（编译 + 既有测试不减少）
- [x] 6.2 跑一场 `gs_boss spawn` 的完整 BOSS 战（召唤→死亡），产出 run ledger 与 profile 图
- [x] 6.3 对账：确认两层字节量级自洽、差异有解释；确认 spawn 突发在细窗口可见
- [x] 6.4 记录 `danmaku-track-scope` 门槛所需的数字（生成突发基线与位置包稳态开销），
      回填该变更的 proposal 门槛
- [ ] 6.5 确认观测不扰动：同一场景开/关观测时弹幕行为与同步读数一致
