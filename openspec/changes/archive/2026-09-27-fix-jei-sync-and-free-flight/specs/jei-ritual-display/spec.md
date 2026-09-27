## ADDED Requirements

### Requirement: JEI 运行时生命周期

JEI 的配方库为可丢弃的会话态：每次进入世界、每次资源重载、每次重连，JEI 均会重建分类与配方管理器。mod SHALL NOT 以跨会话存活的进程级状态推断"内容已推送"；当 JEI 运行时不可用时（`onRuntimeUnavailable`），mod SHALL 丢弃其全部同步基线与运行时引用；当检测到运行时实例发生更换时，mod SHALL 同样丢弃基线并对全部页签执行全量推送。

已推送内容的记录 MUST NOT 早于运行时重建而被清除。

#### Scenario: 二次进入存档内容一致

- **WHEN** 单人存档中玩家进入世界、打开 JEI 确认仪式配方页签有内容，退出到标题画面后再次进入同一存档并打开 JEI
- **THEN** 全部仪式页签（专属页签与兜底页签）的卡片内容与首次进入时一致，无任何页签从侧栏消失

#### Scenario: 重载后内容一致

- **WHEN** 玩家在世界中执行 `/reload`
- **THEN** 全部仪式页签的卡片内容随最新数据文件刷新，不出现空页签或内容缺失

#### Scenario: 断线重连后内容一致

- **WHEN** 玩家退出到标题画面后重新连接同一服务器
- **THEN** 全部仪式页签的卡片内容与断线前一致

#### Scenario: 无内容时页签隐藏

- **WHEN** 某页签在当前数据下无任何卡片
- **THEN** 该页签不显示于侧栏（此为预期行为，区别于"本应有内容却消失"）

## MODIFIED Requirements

### Requirement: 数据单源

JEI 配方页签与配方卡 SHALL 由数据文件自动派生：`data/gensokyou/ritual_recipes/*.json` 派生仪式配方卡，`data/gensokyou/ritual_loot/*.json` 派生工具献祭权重卡，`watatsumi_special.json`（海洋特产池）配合原版钓鱼掉落表派生绵津见池卡。新增、删除或修改任一数据文件后无需改动任何 Java 集成代码即随热重载增删卡片（已登记专属页签的仪式进其页签，其余进兜底页签）。绵津见钓鱼池的内容来自原版掉落表、非 mod 数据文件，故其展示 MAY 由客户端静态表呈现，MUST NOT 要求 dedicated server 同步原版掉落表。

上述五类数据（`ritual_patterns` / `ritual_recipes` / `ritual_smelt_recipes` / `ritual_loot` / `ritual_special`）在客户端 SHALL 统一经服务端下发的数据快照通道获取，MUST NOT 直读仅在逻辑服务端加载的数据加载器静态状态。客户端同步 SHALL 由真实事件驱动（JEI 运行时建立、服务端快照到达），MUST NOT 使用每 tick 轮询检测数据变化。

#### Scenario: 新增配方文件

- **WHEN** 新增一个 ritual_recipes JSON 并 `/reload`
- **THEN** 该配方卡自动出现在对应页签（未登记专属页签则现于兜底页签），无需代码改动

#### Scenario: 新增献祭权重文件

- **WHEN** 新增一个 ritual_loot JSON 并 `/reload`
- **THEN** 该仪式的献祭权重卡自动出现于其专属页签，无需代码改动

#### Scenario: 修改特产池文件

- **WHEN** 修改 `watatsumi_special.json` 并 `/reload`
- **THEN** 绵津见页签的特产池条目随热重载更新，无需代码改动

#### Scenario: 专用服务器客户端页签非空

- **WHEN** 玩家连接到运行本 mod 的 dedicated server 并打开 JEI
- **THEN** 仪式配方页签、四个工具献祭页签、绵津见页签、煅炉页签均有卡片，与单人存档下内容一致，无空白页签

#### Scenario: 快照迟到不致内容错误

- **WHEN** 数据文件被修改并 `/reload`，且服务端快照的重新下发晚于 JEI 运行时的重建
- **THEN** 最终展示的是新数据而非旧数据或空白（二者到达顺序任意）

#### Scenario: 无快照时降级为陈旧而非空白

- **WHEN** JEI 运行时重建后未收到新的服务端快照
- **THEN** 页签展示上一次已知的数据内容，而非空白

### Requirement: 双端安全

JEI 注册与绘制逻辑 SHALL 仅在客户端路径执行；dedicated server 上不触发任何客户端类加载。客户端所需的全部仪式展示数据 SHALL 经服务端下发的快照通道抵达，MUST NOT 依赖仅在逻辑服务端触发的数据加载事件，故 dedicated server 场景下客户端页签内容 MUST NOT 依赖该服务端事件。

#### Scenario: dedicated server 无异常

- **WHEN** 本 mod 与 JEI 共同运行于 dedicated server
- **THEN** 服务端启动与运行无 JEI 集成相关错误

#### Scenario: dedicated server 客户端有内容

- **WHEN** 玩家经 dedicated server 连接并打开 JEI
- **THEN** 全部页签有卡片内容（不因客户端未加载服务端数据加载器而空白）
