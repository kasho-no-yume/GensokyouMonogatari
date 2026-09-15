# jei-ritual-display Specification (delta)

## REMOVED Requirements

### Requirement: 仪式条目展示
**Reason**: 多方块结构展示职能已由仪式构建器（ritual-builder-*）完整承接，JEI 结构页签属重复建设且维护成本高。
**Migration**: 玩家改用仪式构建器查看/搭建结构；结构数据的唯一游戏内展示入口变为构建器界面。

### Requirement: tag 与锚点呈现
**Reason**: 依附于结构页签，随其一并移除。
**Migration**: 无——构建器与编辑器自成体系。

### Requirement: 物品查找入口
**Reason**: U 键"从物品找仪式结构"随结构页签消失；配方卡的材料槽自带 JEI 常规 U/R 查找。
**Migration**: 催化剂静态映射表（RitualCatalysts）一并删除；按材料查仪式改由配方卡承载。

### Requirement: 可拖拽视口
**Reason**: 结构页签移除。
**Migration**: 无。

### Requirement: 视口对齐与溢出提示
**Reason**: 结构页签移除。
**Migration**: 无。

## MODIFIED Requirements

### Requirement: 软依赖接入
本 mod 对 JEI SHALL 为可选依赖：JEI 插件类仅当 JEI 存在于运行时才被加载；JEI 缺席时本 mod 全部功能不受影响且无报错。JEI 依赖声明 MUST 使用 compileOnly API + localRuntime 完整包，不得进入发布产物依赖。

#### Scenario: JEI 缺席时正常启动
- **WHEN** 开发或生产环境未安装 JEI
- **THEN** 本 mod 正常加载，日志无 JEI 相关错误

#### Scenario: JEI 存在时插件激活
- **WHEN** 运行时装有 JEI 且本 mod 启动
- **THEN** JEI 中出现各仪式的配方页签（每仪式一页），不再出现「仪式结环」结构类别

### Requirement: 数据单源
JEI 配方页签与配方卡 SHALL 仅由 `data/gensokyou/ritual_recipes/*.json` 自动派生；新增、删除或修改配方文件后无需改动任何 Java 集成代码即随热重载增删卡片（已登记专属页签的仪式进其页签，其余进兜底页签）。

#### Scenario: 新增配方文件
- **WHEN** 新增一个 ritual_recipes JSON 并 `/reload`
- **THEN** 该配方卡自动出现在对应页签（未登记专属页签则现于兜底页签），无需代码改动

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

## ADDED Requirements

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
