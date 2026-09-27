# jei-ritual-display Specification

## Purpose
在 JEI 中展示各仪式的多方块搭建方式：每个仪式一个条目，绘制可拖拽的分层结构视图，并提供按物品查找仪式的入口。本能力域为纯客户端展示层，只读消费 `ritual-pattern-system` 的数据，与 JEI 为可选软依赖。
## Requirements
### Requirement: 软依赖接入
本 mod 对 JEI SHALL 为可选依赖：JEI 插件类仅当 JEI 存在于运行时才被加载；JEI 缺席时本 mod 全部功能不受影响且无报错。JEI 依赖声明 MUST 使用 compileOnly API + localRuntime 完整包，不得进入发布产物依赖。

#### Scenario: JEI 缺席时正常启动
- **WHEN** 开发或生产环境未安装 JEI
- **THEN** 本 mod 正常加载，日志无 JEI 相关错误

#### Scenario: JEI 存在时插件激活
- **WHEN** 运行时装有 JEI 且本 mod 启动
- **THEN** JEI 中出现各仪式的配方页签（每仪式一页），不再出现「仪式结环」结构类别

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

### Requirement: 仪式配方卡
`ritual_recipes` 中每条配方 SHALL 呈现为其所属仪式页签下的一张配方卡：输入区按原料逐项展示物品 ×数量，原料超过单行容量时 SHALL 换行展示，MUST NOT 截断；输出区展示 result 物品，无 result 的 effect 配方以本地化效果名文本呈现；卡片 SHALL 标注 spCost（灵力消耗）、minTier（结构阶级，本地化呈现），minPlayerTier > 0 时 SHALL 一并标注玩家阶级前置。卡面各元素 MUST NOT 相互重叠。卡片数据 SHALL 仅由数据文件自动派生。配方名与效果名 SHALL 经语言键渲染（配方名 `jei.<ns>.recipe.<path>`，效果名 `jei.<ns>.effect.<path>`），中英文环境下 MUST NOT 出现裸 id 或回退英文路径。

#### Scenario: 实物产物卡
- **WHEN** 查看一条带 result 的加工配方卡
- **THEN** 输入区网格化显示全部原料与数量（超限换行），输出区显示产物，下方标注灵力消耗与阶级门槛

#### Scenario: 效果型卡
- **WHEN** 查看一条仅含 effect 的神恩配方卡
- **THEN** 输出区显示本地化效果名（如"1 阶进阶"），并标注结构阶级与玩家阶级前置

#### Scenario: 版面不重叠
- **WHEN** 查看任意页签下原料最多的一张卡
- **THEN** 标题、原料网格、输出、费用行互不重叠，均在卡边框内

### Requirement: 每仪式独立配方页签
拥有配方的每个仪式 pattern SHALL 呈现于其专属 JEI 页签：页签标题为该仪式的本地化名称（复用 `jei.gensokyou.ritual.<path>` 键族）；已登记专属页签的仪式之配方卡 MUST NOT 落入兜底页签，也不再存在把全部仪式混在一起的"仪式配方"总页签。未在代码登记专属页签的 pattern 之配方 SHALL 归入唯一兜底页签（无内容时该兜底页签 MUST 隐藏不占侧栏），以保证"新增配方文件零 Java 亦可查看"。为既有配方仪式增设专属页签 SHALL 仅需在代码页签清单增加一行登记。

#### Scenario: 双仪式双页签
- **WHEN** 当前数据含源初造化与八百万神恩两个已登记配方文件
- **THEN** JEI 侧边栏出现"源初造化之仪""八百万神恩之仪"两个独立页签，各自只含本仪式配方

#### Scenario: 页签标题本地化
- **WHEN** 客户端语言为 zh_cn / en_us
- **THEN** 页签标题分别显示中文仪式名 / 英文仪式名，无裸 key

#### Scenario: 未登记配方落兜底且可隐藏
- **WHEN** 某 pattern 有配方但未登记专属页签
- **THEN** 其配方卡出现在兜底页签；当兜底页签无任何配方卡时该页签不显示于侧栏

### Requirement: 工具献祭权重卡
四个工具献祭仪式（`oyamatsumi_circle` / `kukunochi_circle` / `haniyasu_circle` / `kaya_no_hime_circle`）SHALL 各自拥有一个专属 JEI 页签，页签标题复用 `jei.gensokyou.ritual.<path>` 键族；页签内 SHALL 按工具材质（wood/stone/gold/iron/diamond/netherite）呈现为 **6 条可翻阅的配方卡**（不拆成按材质独立页签）。每条卡 SHALL 呈现：该材质的输入工具类别代表物、该材质的全部基础产物条目、以及该仪式灵力消耗与结构阶级（取该仪式最低阶）。产物条目 SHALL 以悬浮提示给出**权重折算的近似概率**（`约 X%`，X = 该条目权重 ÷ 当前抽取池总权重 × 100）；受隐藏条件解锁的产物（地狱池/末地池）SHALL 在卡面或悬浮提示中标注其解锁条件（如「≥3 凋灵骷髅头」/「≥1 龙首」）。卡面各元素 MUST NOT 相互重叠；中英文环境下 MUST NOT 出现裸 id 或回退英文路径。

#### Scenario: 按材质翻阅
- **WHEN** 查看大山祇神之座页签
- **THEN** 可依次翻阅 6 张卡（木/石/金/铁/钻石/下界合金），每张仅列该材质的产物

#### Scenario: 权重近似概率悬浮
- **WHEN** 悬浮查看某产物条目
- **THEN** 显示其权重折算的近似概率（如「约 0.5%」），而非裸权重数值

#### Scenario: 隐藏条件标注
- **WHEN** 查看大山祇神之座的钻石镐卡
- **THEN** 卡面/悬浮标注地狱池（≥3 凋灵骷髅头）与末地池（≥1 龙首）的解锁条件及其专属产物

### Requirement: 绵津见献祭权重卡
`watatsumi_circle` SHALL 拥有一个专属 JEI 页签，页签标题复用 `jei.gensokyou.ritual.<path>` 键族；页签内 SHALL 按**仪式等级**呈现为 **3 条可翻阅的配方卡**（0/1/2 阶，不拆成按等级独立页签）。每条卡 SHALL 呈现：该等级的钓鱼竿输入代表物、钓鱼池与海洋特产池的**各自掷数**、以及两池全部产物条目。0 阶卡 MUST NOT 列出宝藏与海洋特产产物；1 阶及以上卡 SHALL 列出宝藏（标注原版 5% 类别权重）与海洋特产条目。产物条目 SHALL 以悬浮提示给出**权重折算的近似概率**（`约 X%`）；钓鱼池三类子表（fish/junk/treasure）SHALL 以原版权重静态呈现并标注来源为原版掉落表。卡面各元素 MUST NOT 相互重叠；中英文环境下 MUST NOT 出现裸 id 或回退英文路径。

#### Scenario: 按等级翻阅
- **WHEN** 查看绵津见神之藏页签
- **THEN** 可依次翻阅 3 张卡（0/1/2 阶），每张仅列该等级可达的产物与掷数

#### Scenario: 0 阶不含宝藏与特产
- **WHEN** 查看 0 阶卡
- **THEN** 产物仅含钓鱼池的 fish/junk 条目，不出现宝藏或海洋特产

#### Scenario: 高阶列出双池与概率
- **WHEN** 查看 1/2 阶卡并悬浮某特产条目
- **THEN** 卡面显示钓鱼池与特产池各自掷数，悬浮显示该条目的近似概率（如「约 12%」）

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

