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
- **THEN** JEI 中出现「仪式结环」配方类别

### Requirement: 仪式条目展示
每个已加载的 RitualPattern SHALL 呈现为恰好一个 JEI 条目；结构 SHALL 绘制于固定尺寸的可拖拽视口中，palette 字符映射为对应方块的物品图标；`_ignore` 与 AIR 格不绘制。多 level 的 RitualPattern SHALL 在条目内提供层级切换控件（上一层/下一层按钮与当前进度显示），不得拆分为多条目。

#### Scenario: 单层结构展示
- **WHEN** 查看召唤仪式条目
- **THEN** 展示 5×5 网格：四角与边中为仪式石图标、中心为祭仪核心图标

#### Scenario: 层内翻页
- **WHEN** 某 ritual JSON 含多个 levels 条目且玩家点击条目内翻层按钮
- **THEN** 同一条目内切换至相邻层的结构视图，不产生新的 JEI 条目

### Requirement: tag 与锚点呈现
palette 的标签条目 SHALL 显示该标签下首个有效方块的代表物品（无有效成员时以屏障占位并在 tooltip 标注标签名）；锚点字符格 SHALL 叠加视觉标注并在 tooltip 说明其为锚点。

#### Scenario: 标签代表物
- **WHEN** palette 含 `"S": "#gensokyou:ritual_stones"`
- **THEN** S 格显示仪式石物品图标

#### Scenario: 锚点识别
- **WHEN** 玩家悬停锚点格图标
- **THEN** tooltip 标注该格为仪式锚点

### Requirement: 物品查找入口
玩家在祭仪核心、祭品台或任一仪式催化剂物品上按 U（用途）SHALL 列出关联的仪式条目；核心/祭品台关联全部含该方块的仪式，催化剂按代码内静态映射表关联对应仪式（该映射随新增仪式检查单文档维护）。

#### Scenario: 从核心查仪式
- **WHEN** 在 JEI 中对祭仪核心按 U
- **THEN** 显示所有使用祭仪核心的仪式条目

#### Scenario: 从催化剂查仪式
- **WHEN** 在 JEI 中对召唤催化剂按 U
- **THEN** 显示召唤仪式条目

### Requirement: 数据单源
JEI 仪式条目 SHALL 仅由 `data/gensokyou/rituals/*.json` 及既有注册数据自动派生；新增或删除仪式文件后无需修改任何 Java 集成代码或独立清单，重启客户端后条目随之增删。

#### Scenario: 新增仪式文件
- **WHEN** 新增一个 rituals JSON 并重启客户端
- **THEN** JEI 自动出现该仪式条目，无需代码改动

### Requirement: 可拖拽视口
结构视图视口尺寸固定，超出视口的结构 SHALL 支持通过鼠标拖拽与滚轮平移查看；视图初始 SHALL 居中显示结构；平移范围 MUST 受结构包围盒约束，内容小于视口时保持居中不可拖出。

#### Scenario: 大结构平移查看
- **WHEN** 结构尺寸超过视口且玩家在视图区拖动鼠标
- **THEN** 视图内容随拖动平移，原本被裁剪的部分可被查看

#### Scenario: 小内容锁定居中
- **WHEN** 结构完全小于视口时尝试拖动
- **THEN** 内容保持居中，不产生位移

### Requirement: 视口对齐与溢出提示
多层级条目切换层级后，视图 SHALL 以该层的对齐中心为视觉中心：有仪式核心的层以其核心格中心为准，无核心的层以其包围盒几何中心为准（依赖仪式中心对称约定）；准线 SHALL 标出该层对齐中心的位置。当结构内容超出视口时，存在未显示内容的每个方向 SHALL 在对应边缘显示方向箭头提示。

#### Scenario: 切层锚点对齐
- **WHEN** 玩家在多层仪式条目内切换层级
- **THEN** 新层视图以该层对齐中心居中，准线指示其位置，可与前层直观对照

#### Scenario: 超界方向箭头
- **WHEN** 当前层内容向某方向超出视口
- **THEN** 该方向边缘中点显示箭头；拖动至该方向无隐藏内容后箭头消失

### Requirement: 双端安全
JEI 注册与绘制逻辑 SHALL 仅在客户端路径执行；dedicated server 上不触发任何客户端类加载。

#### Scenario: dedicated server 无异常
- **WHEN** 本 mod 与 JEI 共同运行于 dedicated server
- **THEN** 服务端启动与运行无 JEI 集成相关错误

### Requirement: 仪式配方卡
`ritual_recipes` 中每条配方 SHALL 呈现为一张配方卡：输入位展示 ingredients 各项物品 ×数量，输出位展示 result 物品（无 result 的 effect 配方输出效果名文本）；卡片 SHALL 标注 minTier 与所属仪式。卡片数据 SHALL 仅由数据文件自动派生，增删文件无需改动任何 Java 集成代码。

#### Scenario: 实物产物卡
- **WHEN** 查看一条带 result 的加工配方卡
- **THEN** 输入区显示全部原料与数量，输出区显示产物物品

#### Scenario: 效果型卡
- **WHEN** 查看一条仅含 effect 的召唤配方卡
- **THEN** 输出区显示效果名文本，并标注所属仪式与等级门槛