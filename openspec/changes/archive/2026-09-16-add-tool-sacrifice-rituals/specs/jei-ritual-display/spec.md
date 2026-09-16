# jei-ritual-display Delta Spec

## MODIFIED Requirements

### Requirement: 数据单源
JEI 配方页签与配方卡 SHALL 由 `data/gensokyou/ritual_recipes/*.json` 与 `data/gensokyou/ritual_loot/*.json` 两类数据文件自动派生：前者派生仪式配方卡，后者派生工具献祭权重卡。新增、删除或修改任一数据文件后无需改动任何 Java 集成代码即随热重载增删卡片（已登记专属页签的仪式进其页签，其余进兜底页签）。

#### Scenario: 新增配方文件
- **WHEN** 新增一个 ritual_recipes JSON 并 `/reload`
- **THEN** 该配方卡自动出现在对应页签（未登记专属页签则现于兜底页签），无需代码改动

#### Scenario: 新增献祭权重文件
- **WHEN** 新增一个 ritual_loot JSON 并 `/reload`
- **THEN** 该仪式的献祭权重卡自动出现于其专属页签，无需代码改动

## ADDED Requirements

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
