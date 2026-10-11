## ADDED Requirements

### Requirement: 链路字节计量模式
系统 SHALL 提供一种**零延迟的链路字节计量模式**：位于服务端与客户端之间的代理按连接、
按方向记录线上字节量，且 MUST NOT 为观测而注入延迟或改写字节。

#### Scenario: 以计量模式启动
- **WHEN** 代理以 `--profile <path>` 启动且延迟/抖动为 0
- **THEN** 每个连接的客户端→服务端与服务端→客户端方向的字节量被记录到 `<path>`，
  且链路字节流不被改写、不附加延迟

#### Scenario: 计量与延迟可分离
- **WHEN** 不启用计量模式
- **THEN** 代理行为与现状完全一致（仅延迟注入）

### Requirement: 细粒度窗口与突发保真
计量模式 SHALL 以足够细的时间窗口（默认 50ms）采样并保留原始记录，使一次生成突发
（同一窗口内大量 spawn）MUST NOT 被均值掩盖。

#### Scenario: 生成突发可见
- **WHEN** 一 tick 内生成大量弹幕
- **THEN** 原始记录中对应窗口的字节量显著高于相邻窗口，峰值可由离线报告还原

### Requirement: 包类与实体归因（服务端观测）
系统 SHALL 在服务端出站管线中、帧/压缩编码器之上观测 `Packet` 对象，按**包类**、按玩家、
按 tick 统计包数与**压缩前**字节数。

#### Scenario: 产出包类分解
- **WHEN** 观测启用且发生弹幕生成、位置同步、实体移除等流量
- **THEN** 报告按 `AddEntity` / `SetEntityData` / `MoveEntity` / `RemoveEntities` / 区块 /
  交互等包类分别给出包数与字节数

#### Scenario: 未知包类不致命
- **WHEN** 出现未登记的包类
- **THEN** 归入「未知」桶并被计数，MUST NOT 抛出异常或中止观测

#### Scenario: 观测点免疫压缩
- **WHEN** 服务端启用了网络压缩
- **THEN** 包类与字节统计仍按压缩前的逻辑包给出（不受压缩影响）

### Requirement: 两种字节口径并列而不混算
报告 SHALL 同时呈现**线上字节**（压缩后，来自代理）与**逻辑包字节**（压缩前，来自服务端），
并显式标注两者口径不同；MUST NOT 假设或断言两者相等。

#### Scenario: 两者并列展示
- **WHEN** 生成 BOSS 战报告
- **THEN** 报告分别列出代理线上字节与服务端逻辑字节，并注明口径与不可比性

### Requirement: 事件标记流
服务端 SHALL 在 BOSS **召唤**、**死亡**、**符卡切换**时写入一行标记记录，含墙上时钟与
服务端 tick。

#### Scenario: 符卡切换打点
- **WHEN** BOSS 血量跨过阈值触发符卡切换
- **THEN** 写入一条含新符卡下标/名称与时刻的标记记录

#### Scenario: 召唤与死亡括界
- **WHEN** BOSS 被召唤、随后被击杀
- **THEN** 分别写入召唤与死亡标记，使一场战斗可在时间轴上被括出

### Requirement: BOSS 战运行账与报告产出
系统 SHALL 提供离线合成工具，将代理记录、服务端归因记录与标记流按时间对齐，产出
**CSV**、**PNG 图**与**文本摘要**，支持按玩家与按符卡时段过滤。

#### Scenario: 生成报告
- **WHEN** 对一次已完成的 BOSS 战运行离线工具
- **THEN** 产出逐窗口字节序列与包类分解的 CSV、一张以时间为轴的 profile 图，
  以及含峰值/均值/各符卡占比的文本摘要

#### Scenario: 图不引入新依赖
- **WHEN** 生成 profile 图
- **THEN** 图像由仓库既有依赖（PIL）渲染，MUST NOT 要求 matplotlib 或外部抓包工具

### Requirement: 对账自检
文本摘要 SHALL 给出服务端逻辑字节与代理线上字节的累计差及其来源说明；差异 MUST 被显式
暴露，MUST NOT 被静默掩盖。

#### Scenario: 差异可见
- **WHEN** 两种口径的累计字节不一致
- **THEN** 摘要打印该差值并说明可能来源（压缩率、帧开销、非本模组流量等）

### Requirement: 观测不扰动被观测对象
观测（代理计量 + 服务端 handler + 标记）MUST NOT 改变弹幕/同步行为、网络包格式、
`updateInterval` 或追踪范围。

#### Scenario: 行为不变
- **WHEN** 在开启观测的服务器上进行一场 BOSS 战
- **THEN** 弹幕运动、同步与命中判定与未开启观测时一致

### Requirement: 不依赖外部抓包工具
生成 profile 的能力 SHALL 完全由链路上的代理与服务端自身提供，MUST NOT 要求
Wireshark、pktmon 或其它外部抓包/分析工具。

#### Scenario: 无外部工具出图
- **WHEN** 环境未安装任何外部抓包工具
- **THEN** 仍能产出一场 BOSS 战的 profile 图与数据
