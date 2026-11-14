# boss-bgm Specification

## Purpose
BOSS 战背景音乐：内置 5 首按阶级映射的曲目、每只 BOSS 独立的可替换事件 id、客户端选曲与滞回、
淡入淡出与 crossfade、原版情境音乐抑制，以及「普通资源包即可换曲、无需改模组」的替换契约。
## Requirements
### Requirement: 内置曲目 MUST 按 BOSS 阶级映射且开箱有声

模组 MUST 随包内置 5 首 BGM（阶级 1~5 各一首），并在无任何外部资源包的默认状态下，
使每只非豁免的东方 BOSS 在进入战斗时播放其阶级对应的曲目。

内置曲目 MUST 随 mod 二进制分发，MUST NOT 依赖下载、配置项或可选资源包。
曲目 MUST NOT 由原版音乐文件替代或改写。

#### Scenario: 开箱即有声

- **WHEN** 玩家在未安装任何资源包的环境中召唤一只 2 阶 BOSS 并接近至 32 格内
- **THEN** 客户端 MUST 在 2 秒内淡入 2 阶曲目

#### Scenario: 所有阶级都有曲目

- **WHEN** 系统加载曲目目录
- **THEN** 阶级 1~5 MUST 各自解析到一条可播放的事件 id，MUST NOT 存在某阶无曲目

### Requirement: 每只 BOSS MUST 拥有独立事件 id，且资源包 MUST 能按两种粒度替换

每只在名册内的 BOSS MUST 拥有独立的声音事件 id，该事件缺省指向与其阶级相同的音频文件。
多个事件 id 指向同一音频文件 MUST NOT 产生额外音频资源。

资源包替换 MUST 支持两种粒度，且 MUST NOT 需要修改 mod 或编写额外代码：

1. **整阶替换**：在资源包内提供与内置文件同路径、同名的事件音频文件，该阶级全部 BOSS 生效；
2. **单只替换**：在资源包的 `sounds.json` 中为该 BOSS 的事件 id 提供带 `"replace": true`
   的条目，仅该 BOSS 生效。

系统 MUST 在对外文档中说明：不带 `"replace": true` 的同名事件条目是**追加**而非替换，
其结果为新旧音频随机二选一。系统 MUST NOT 提供除上述两条之外的替换途径。

#### Scenario: 整阶替换

- **WHEN** 资源包只提供一阶曲目的同名音频文件
- **THEN** 该阶全部 BOSS MUST 播放新曲目，其它阶级 MUST 不受影响

#### Scenario: 单只替换

- **WHEN** 资源包为某一只 BOSS 的事件 id 提供 `"replace": true` 的条目
- **THEN** 该 BOSS MUST 播放新曲目，同阶级其它 BOSS MUST 保持原曲

#### Scenario: 缺少 replace 时的追加语义

- **WHEN** 资源包为某事件 id 提供条目但未声明 `"replace": true`
- **THEN** 该事件 MUST 同时含有新旧音频作为可选项，MUST NOT 表现为确定性替换

### Requirement: 选曲 MUST 由客户端推导，并具备最近优先、sticky 与滞回

BGM 的选择 MUST 完全由客户端依据本地已同步的 BOSS 实体状态推导，
MUST NOT 新增网络包、MUST NOT 依赖服务端下发播放指令。

选曲 SHALL 遵循：

- **过滤**：超出退出距离、已死亡、处于测试或调试豁免名册的 BOSS MUST NOT 成为候选；
- **优先**：以「是否以本玩家为目标」优先，其次按距离升序；
- **sticky**：当前曲目仍由在场 BOSS 提供时，仅当出现显著更优候选（距离优于当前 25% 以上）
  才换曲，否则 MUST 保持；
- **滞回**：进入距离与退出距离 MUST 不同，退出距离 MUST 大于进入距离。

#### Scenario: 单只 BOSS 进入与离开

- **WHEN** 玩家接近一只 BOSS 至进入距离内，随后离开至退出距离之外
- **THEN** 音乐 MUST 分别淡入与淡出，且 MUST NOT 在距离恰好等于阈值时反复启停

#### Scenario: 两只同阶 BOSS 走位抖动

- **WHEN** 两只同阶级 BOSS 交替成为最近者
- **THEN** 音乐 MUST NOT 重新开始，也 MUST NOT 切换曲目

#### Scenario: 双 BOSS 异阶

- **WHEN** 一只 1 阶 BOSS 在场时，一只 2 阶 BOSS 进入进入距离
- **THEN** 音乐 MUST crossfade 到 2 阶曲目

#### Scenario: 测试 BOSS 不触发音乐

- **WHEN** 血条探针或数值测试用 BOSS 被召唤到场
- **THEN** 系统 MUST NOT 启动任何 BGM

### Requirement: 换曲键 MUST 是事件 id，而非「当前 BOSS」

音乐 MUST 仅在目标事件 id 变化时重启。同一只 BOSS 的符卡切换、阶段切换、血量变化
MUST NOT 使音乐重头播放。

#### Scenario: 切符卡不重开

- **WHEN** 同一只 BOSS 连续切换 3 张符卡
- **THEN** 曲目 MUST 连续播放，MUST NOT 出现淡出再淡入或从头开始

#### Scenario: 同阶换 BOSS 不重开

- **WHEN** 在场的 1 阶 BOSS 被另一只 1 阶 BOSS 取代
- **THEN** 若两者解析到同一事件 id，音乐 MUST 不中断

### Requirement: 淡变 MUST 平滑、感知、且能进入声音引擎

淡入与淡出各自的时长 MUST 不短于 2 秒，换曲 MUST 使用 crossfade 且时长不短于 2 秒。
振幅 MUST 使用感知曲线（`p^1.7` 一族），MUST NOT 使用线性振幅。

起音首帧交给声音引擎的音量 MUST 大于零，MUST NOT 因引擎的零音量跳过规则而静默。

同时存活的音乐实例 MUST NOT 超过 2 个（crossfade 期间的上限）。

#### Scenario: 淡入不被引擎丢弃

- **WHEN** 一条音乐在淡入第 0 tick 被播放
- **THEN** 交给引擎的音量 MUST 大于 0，随后按不少于 2 秒的曲线上升

#### Scenario: crossfade 期间实例上限

- **WHEN** 一次换曲正在进行
- **THEN** 同时存活的音乐实例 MUST 不超过 2 个

### Requirement: 原版情境音乐 MUST 在 BOSS 战期间被抑制

当 BGM 正在播放时，原版情境音乐事件 MUST NOT 被播放。
抑制 MUST 只作用于原版情境音乐事件集合，MUST NOT 影响唱片机、背景环境音或其它来源。
BGM 结束后抑制 MUST 解除，原版音乐 MUST 能在正常延迟内恢复播放。

#### Scenario: 战斗期间无原版音乐

- **WHEN** BGM 播放期间原版音乐管理器尝试开始一首情境音乐
- **THEN** 该次播放 MUST 被取消，MUST NOT 与 BGM 叠播

#### Scenario: 战斗结束后恢复

- **WHEN** BGM 淡出完成
- **THEN** 原版情境音乐 MUST 能在常规随机延迟内恢复，MUST NOT 被推后到数十分钟量级

#### Scenario: 唱片机不受影响

- **WHEN** 玩家在战斗中播放唱片
- **THEN** 唱片 MUST 正常播放

### Requirement: 音乐生命周期 MUST 可从重载与断线中恢复

系统 MUST 每 tick 检测当前音乐实例是否仍被声音引擎持有；被丢弃时
（包括资源重载、声音引擎重建）MUST 按当前曲目自动重起。

断线、退出世界、切换维度时 MUST 停止并释放全部音乐实例，MUST NOT 残留循环实例。
BOSS 死亡或超距时必须经淡出后释放，MUST NOT 硬切。

#### Scenario: 换资源包后恢复

- **WHEN** 玩家在战斗中触发资源重载
- **THEN** 系统 MUST 在 1 秒内以新资源恢复播放当前曲目

#### Scenario: 断线清理

- **WHEN** 玩家断开与世界的连接
- **THEN** 全部音乐实例 MUST 被停止，且客户端音频后端内部集合 MUST 为空

#### Scenario: BOSS 死亡

- **WHEN** 当前曲目对应的 BOSS 死亡
- **THEN** 音乐 MUST 在淡出时长内静音，MUST NOT 立即截断

### Requirement: 音乐 MUST 是纯客户端表现

音乐播放 MUST NOT 参与判定、伤害、同步、存档或任何服务端状态。
专用服务器启动 MUST NOT 加载任何音乐相关的客户端类，MUST NOT 因音频子系统崩溃。
音乐 MUST 使用 `MUSIC` 声源类别，因而受原版「音乐」音量设置控制。

#### Scenario: 专用服务器

- **WHEN** 以专用服务器启动
- **THEN** 音乐客户端类 MUST NOT 被加载，日志 MUST NOT 出现音频相关 ERROR

#### Scenario: 音乐音量归零

- **WHEN** 玩家把「音乐」音量设为 0
- **THEN** BGM MUST 无声，且 MUST NOT 影响音效与判定

### Requirement: 曲目素材 MUST 可整首重播、流式播放且响度归一

每首内置曲目 MUST：

- 为 Ogg Vorbis、44.1 kHz；
- 在 `sounds.json` 中以流式（`"stream": true`）声明；
- **首部不抢静音**：曲首的静音 MUST 裁掉，使进入战斗时立刻有声；
- 响度归一（同类曲目之间感知响度差 ≤ 2 LU 量级），并保留编码余量，MUST NOT 顶到 0 dBFS。

循环形态固定为「整个文件播完从头再来」。本能力**不要求无缝循环**：
曲末的自然收尾（含渐弱淡出）MAY 原样保留，接缝处的一次短暂回落 MUST 被记录为
**已知取舍**，MUST NOT 被当作缺陷，MUST NOT 成为拒绝该曲目的理由。

系统 MUST NOT 在播放层做 loop point 折算、前奏跳过或音乐结构剪辑。

#### Scenario: 曲目含静音前奏

- **WHEN** 某曲目以明显静音开头
- **THEN** 该曲目 MUST 先裁掉首部静音再进包

#### Scenario: 曲末自然收尾

- **WHEN** 某曲目以渐弱淡出结束
- **THEN** 该曲目 MAY 原样进包；由此产生的接缝回落 MUST 记入素材记录，MUST NOT 触发校验失败

#### Scenario: 流式声明缺失

- **WHEN** 某音乐事件未声明 `"stream": true`
- **THEN** 一致性校验 MUST 失败并指出该事件

### Requirement: 曲目目录与 BOSS 名册 MUST 可离线校验且受棘轮约束

系统 MUST 在离线测试中断言：曲目目录、`sounds.json` 条目、声音事件注册、音频文件
四者一致；每只名册内 BOSS 解析到的事件 id MUST 存在。

新出现的 `TouhouBoss` 实现类 MUST 被纳入名册，或被显式列入豁免表并标注理由；
豁免表中的条目若已不再命中 MUST 使校验失败。

#### Scenario: 新增 BOSS 未入册

- **WHEN** 新增一只实现 `TouhouBoss` 的实体类而未加入名册或豁免表
- **THEN** 校验 MUST 失败并指出该类

#### Scenario: 名册指向缺失资源

- **WHEN** 名册中某条事件 id 在 `sounds.json` 或音频文件中不存在
- **THEN** 校验 MUST 失败并指出具体键名与缺失文件

