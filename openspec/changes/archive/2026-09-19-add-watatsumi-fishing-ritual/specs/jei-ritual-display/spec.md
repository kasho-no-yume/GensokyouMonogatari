## MODIFIED Requirements

### Requirement: 数据单源
JEI 配方页签与配方卡 SHALL 由数据文件自动派生：`data/gensokyou/ritual_recipes/*.json` 派生仪式配方卡，`data/gensokyou/ritual_loot/*.json` 派生工具献祭权重卡，`watatsumi_special.json`（海洋特产池）配合原版钓鱼掉落表派生绵津见池卡。新增、删除或修改任一数据文件后无需改动任何 Java 集成代码即随热重载增删卡片（已登记专属页签的仪式进其页签，其余进兜底页签）。绵津见钓鱼池的内容来自原版掉落表、非 mod 数据文件，故其展示 MAY 由客户端静态表呈现，MUST NOT 要求 dedicated server 同步原版掉落表。

#### Scenario: 新增配方文件
- **WHEN** 新增一个 ritual_recipes JSON 并 `/reload`
- **THEN** 该配方卡自动出现在对应页签（未登记专属页签则现于兜底页签），无需代码改动

#### Scenario: 新增献祭权重文件
- **WHEN** 新增一个 ritual_loot JSON 并 `/reload`
- **THEN** 该仪式的献祭权重卡自动出现于其专属页签，无需代码改动

#### Scenario: 修改特产池文件
- **WHEN** 修改 `watatsumi_special.json` 并 `/reload`
- **THEN** 绵津见页签的特产池条目随热重载更新，无需代码改动

## ADDED Requirements

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
