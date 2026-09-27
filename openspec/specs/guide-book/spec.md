# guide-book Specification

## Purpose

《幻想乡物语》指导书框架：基于外部依赖的 Patchouli 数据驱动指导书，作为本 mod 玩法的唯一权威游戏内教程载体（JEI 仅为可选加速器）。覆盖书本体与打开行为、首次序言、六大章节骨架、进度解锁、仪式自定义配方页、书页配方与图标。
## Requirements
### Requirement: Patchouli 外部依赖接入
本 mod SHALL 以**外部必需依赖**接入 Patchouli：在 `neoforge.mods.toml` 将 `patchouli` 声明为 required，且 MUST NOT 通过 JarJar 内嵌分发。全部 Java 调用 SHALL 仅经由 `vazkii.patchouli.api` 公共 API。

#### Scenario: 正常打开
- **WHEN** 玩家（已安装 Patchouli）右键持有指导书
- **THEN** 打开 Patchouli 书 GUI，物品不消耗

#### Scenario: 缺失依赖
- **WHEN** 运行环境未安装 Patchouli
- **THEN** 加载器报告缺失必需依赖并拒绝进入游戏，MUST NOT 静默降级或崩溃

#### Scenario: API 隔离
- **WHEN** 审查业务代码的 Patchouli 引用
- **THEN** 仅存在 `vazkii.patchouli.api` 下的类型；客户端专属引用位于 `client` 包，dedicated server 不加载

### Requirement: 首次打开显示序言
玩家首次使用指导书 SHALL 直接打开序言条目，并通过玩家持久化数据记录"已读"；后续使用 SHALL 落在书的落地页（章节列表）。打开行为 SHALL 由服务端判定并调用服务端 API 驱动，物品 MUST NOT 消耗。

#### Scenario: 首次使用
- **WHEN** 玩家第一次右键指导书
- **THEN** 打开序言条目，"已读"标记被写入玩家持久化数据

#### Scenario: 后续使用
- **WHEN** 已读玩家再次右键指导书
- **THEN** 打开落地页（书名 + 章节列表），不再自动翻到序言

### Requirement: 六大章节骨架
书 SHALL 提供六个顶级章节：术语解释、符卡列表、敌对生物列表、NPC 列表、仪式列表、武器系统。`book.json` SHALL 位于 `data/gensokyou/patchouli_books/gensokyou_book/` 并设置 `use_resource_pack: true`；章节与条目 SHALL 位于 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/`（`en_us` 为 Patchouli 基准目录，中文正文可直写其中），`zh_cn/` SHALL 作为可选覆盖。中英文环境 MUST NOT 出现裸 id。本 change SHALL 交付各章至少一个样例条目与序言条目。

#### Scenario: 章节列表
- **WHEN** 打开落地页
- **THEN** 章节宫格呈现六个章节，标题经 lang 键本地化

#### Scenario: 样例条目可读
- **WHEN** 打开任一章
- **THEN** 至少存在一个可打开的样例条目，正文无裸 key

### Requirement: 进度解锁机制
书 SHALL 提供隐形引导 advancement：`guide/nether_unlock`（进入下界）、`guide/end_unlock`（进入末地）、`guide/gensokyo_unlock`（进入幻想乡维度），均为 `changed_dimension` 触发器、无 display。需要门槛的内容条目 SHALL 以 `"advancement"` + `"secret": true` 声明：解锁前条目在书中**完全不显示**（非灰色锁定态），解锁时弹出 toast。内容 SHALL 以**世界进度**而非玩家超人类阶级（temperLevel）为门槛：0 阶内容条目 MUST NOT 挂任何 advancement（无条件可见）；结构阶 1/2/3 的内容分别以 `guide/nether_unlock` / `guide/end_unlock` / `guide/gensokyo_unlock` 为门槛；结构阶 4/5 的门槛在阶段 4/5 世界进度落地前 SHALL 以过渡实现承载（`temperLevel` 授予）。

#### Scenario: 1 阶内容的门槛
- **WHEN** 玩家从未进入下界时阅读仪式章节
- **THEN** 挂 nether_unlock 的 1 阶内容条目不出现；对应 0 阶条目正常可见

#### Scenario: 进入下界解锁
- **WHEN** 玩家进入下界后重开仪式章节
- **THEN** 1 阶内容条目出现并收到解锁 toast

#### Scenario: 2 阶内容按末地解锁
- **WHEN** 玩家已进末地但从未玩过本 mod（temperLevel 仍为 0）
- **THEN** 挂 end_unlock 的 2 阶内容条目出现，与 temperLevel 无关

#### Scenario: 2 阶内容的门槛
- **WHEN** 玩家已进下界但未进末地
- **THEN** 挂 end_unlock 的 2 阶内容条目在书中完全不出现

#### Scenario: 3 阶内容按幻想乡解锁
- **WHEN** 玩家进入幻想乡维度后
- **THEN** 挂 gensokyo_unlock 的 3 阶内容条目出现

### Requirement: 仪式章节配方卡与结构展示
仪式列表章节 SHALL 以自定义页面模板渲染配方卡（输入物品×数量、产物或本地化效果名、spCost、minTier），展示字段与语言键 SHALL 与 `jei-ritual-display` 共用同一数据模型（`RitualRecipe`）。仪式结构 SHALL 以 Patchouli 原生 `patchouli:multiblock` 页呈现 3D 投影（支持在世界上幽灵投影照搭），其多重方块 SHALL 由构建期生成器从 `rituals` v5 数据内联生成（谓词映射 EXACT→方块 id、TAG→`#tag`、AIR→空格、IGNORE→`_`、锚点→`0`），MUST NOT 依赖运行期 `multiblock_id` 注册。结构与「本阶参数」SHALL **按阶级分页**并各挂对应阶级门槛（见「进度解锁机制」），显示以玩家当前世界进度为上限（锁住的页完全隐藏）；分阶页 MUST NOT 渲染「搭建材料」段；页内文本 SHALL 自动换行以适应页宽（`Font.split`），MUST NOT 因超宽裁切。配方 SHALL 一配方一页，但**满足以下任一条件的仪式 MUST NOT 补配方页**：配方数 > 12；作者显式指认为开放式配方或极多配方。客户端 SHALL 在玩家登录与 datapack 重载时从服务端同步配方数据并写入本地缓存；数据未到达时配方卡 SHALL 呈现占位，离线时读缓存。Patchouli 页列表为静态，无法运行时增页，故长文本 SHALL 在生成期拆分为多张 `patchouli:text` 页。

#### Scenario: 仪式条目渲染
- **WHEN** 打开某个已配置仪式数据与配方文件的条目
- **THEN** 页面呈现 3D 多重方块结构投影（可点眼睛投射）与分阶参数页（原料与数量、产物/效果名、spCost、结构阶级），且不含「搭建材料」段

#### Scenario: 不超框
- **WHEN** 分阶参数或配方卡文本超过页宽
- **THEN** 文本自动换行，不裁切、不溢出页面

#### Scenario: 极多配方不补页
- **WHEN** 某仪式配方数大于 12，或作者显式指认为开放式/极多配方
- **THEN** 该条目不含任何配方页

#### Scenario: 多人服务器取数
- **WHEN** 玩家在专用服务器上打开仪式条目
- **THEN** 收到服务端同步后配方卡正常渲染；断线重连读本地缓存仍可渲染

#### Scenario: 新增仪式需重生成结构
- **WHEN** 新增或修改一个仪式 pattern 后重跑生成器（`tools/gen_ritual_multiblock.py`）
- **THEN** 对应条目的 3D 结构反映新数据，无需改动 Java 渲染代码

### Requirement: 残页 tooltip 与书页配方
记忆残页 SHALL 增加 tooltip「凑齐9张也许可以拼凑出一本书……」（经 lang 键）。系统 SHALL 提供两个原版配方：1 张残页无序合成 1 张纸；9 张残页 3×3 有序合成 1 本《幻想乡物语》。

#### Scenario: 残页提示
- **WHEN** 玩家悬停记忆残页
- **THEN** tooltip 显示凑书提示文案

#### Scenario: 残页造纸
- **WHEN** 在合成格放入 1 张残页
- **THEN** 产出 1 张纸

#### Scenario: 残页合书
- **WHEN** 3×3 平铺 9 张残页合成
- **THEN** 产出 1 本《幻想乡物语》指导书

### Requirement: 书图标重绘
指导书物品图标 SHALL 重绘为 16×16 像素贴图：参照众生典籍的书册轮廓与底色调，增加更华丽的装饰（金边、宝石扣、缎带书签等），并保持物品模型引用 `guide_book.png`。

#### Scenario: 图标呈现
- **WHEN** 在物品栏查看指导书
- **THEN** 显示重绘后的华丽书册图标

### Requirement: 仪式条目内容规范
仪式列表章 SHALL 采用「第一条为仪式入门（`sortnum` 最小），其后每个**可正常游玩的**已实现仪式各一条目」的形态；**创造/调试类仪式 MUST NOT 建条目**。每个仪式条目 SHALL 以**故事开篇 + 简短引言**作为正文**同一首页**（口吻含蓄、不必点破机制），其后 SHALL 按结构阶依次呈现该阶结构（multiblock）与该阶**具体参数**（按该阶计算，不写公式、不带「会话型／启停型」等标签）；生产/献祭类仪式 SHALL 以**物品图标**呈现其配方/掉落（JEI 风格，附概率）。正文换行 SHALL 使用 `$(br)`/`$(br2)`，MUST NOT 使用 `\n`。**最低结构阶 ≥ 1 的**仪式条目 SHALL 挂该阶**世界进度**门槛（`secret`）；最低阶为 0 的条目常驻可见。其逐阶结构/参数页 SHALL 各挂对应世界进度门槛。条目 SHALL 以 `sortnum` 明确排序。**没有 pattern 数据的占位/未实现仪式 MUST NOT 建条目**（待其 pattern 落地再补）。新增文案 SHALL 仅维护 `zh_cn`，MUST NOT 要求同步 `en_us`。

依赖外部供能网络的仪器类仪式，其条目正文 SHALL 额外写明：所需的前置节点、供能链拓扑、以及**运行期的自然流失或持续消耗速率**。自然流失速率 MUST NOT 只存在于界面信息行，玩家在阅读条目时即 SHALL 知晓，以便据其规划备料总量。参数页 SHALL 显示该阶的缓存容量、受能上限与消耗速率具体数值。

条目正文 SHALL 说明供能节点与本仪式核心的空间关系要求（若供能靠邻接或半径覆盖实现）。

#### Scenario: 列表形态
- **WHEN** 打开仪式分类
- **THEN** 第一条为「仪式入门」，其后每条为一个仪式条目

#### Scenario: 故事开篇
- **WHEN** 打开某个仪式条目
- **THEN** 首页为故事 + 简短引言，而非直接罗列参数

#### Scenario: 高阶条目带门槛
- **WHEN** 某仪式最低结构阶为 2（如八方归元／万象共鸣）
- **THEN** 该条目录制为 `secret` 且挂 end_unlock 门槛，未进末地前不出现在列表中；最低阶 0 的仪式条目则常驻可见

#### Scenario: 阶级参数就地显示
- **WHEN** 打开某仪式的某阶结构页
- **THEN** 紧随其后的参数页显示该阶的**具体数值**（如产灵/缓存/连接数），且不含公式符号或「会话型／启停型」标签

#### Scenario: 生产仪式产物以图标呈现
- **WHEN** 打开某生产/献祭类仪式
- **THEN** 其配方/掉落以**物品图标网格**呈现，悬停显示「约 X%」；4 个工具仪式按工具材质各一页（共 6 页），绵津见按等级各一页（共 3 页），而非纯文字描述

#### Scenario: 仪器类条目写明前置与流失
- **WHEN** 打开结界破坏仪式的条目
- **THEN** 正文写明须先建成八方归元与万象共鸣、须托管四颗 2 阶灵力核心、缓存每秒自然流失 150,000、建议备料 6,000,000

#### Scenario: 仪器类条目写明空间关系
- **WHEN** 打开结界破坏仪式的条目
- **THEN** 正文说明供能链三个节点需同时处于同一座共鸣之仪的覆盖半径内

#### Scenario: 仪器类参数页含消耗项
- **WHEN** 打开结界破坏仪式的阶级参数页
- **THEN** 显示该阶的缓存容量、受能上限与每秒消耗速率

### Requirement: 书内文案面向玩家
书内所有玩家可见正文——序言、章节说明、条目首段与引言、物品词条说明页——SHALL 以**玩家视角**撰写：先说明「这是什么、在玩法里做什么用、怎么获得或使用」，口吻贴合幻想乡世界设定与游玩体验。

书内文案 MUST NOT 出现面向开发者或技术人员的内容，包括但不限于：类名、方法名、文件名、注册名与物品/配方 ID；数据包路径、文件路径与目录结构；内部字段与协议名（如 `minTier`、`spCost`、`mode`、`match`、`recipe_index`、`show_recipes`、`crafting_shapeless`）；架构、生成器与数据管线术语；版本、实现状态与变更记录口吻；以及 `TODO`、"待补充"、"暂无来源"、"暂未实现" 等工程占位说明。配方页上的结构等级与灵力费用 SHALL 以玩家可读措辞呈现（如「1 阶仪式」「20,000 灵力」），MUST NOT 直接罗列字段名。

物品词条说明页 SHALL 以该物品的玩法定位与获取途径为主线，MUST NOT 以"此物由某仪式产出、下页列出配方、字段见下"之类的技术说明代替玩家向介绍。

#### Scenario: 物品词条首段是玩家向介绍
- **WHEN** 玩家打开灵力核心或祭品台的物品词条
- **THEN** 首段说明该物品在玩法中的用途与如何获得，MUST NOT 出现配方 ID、字段名、文件路径或"下一页"式的技术指引

#### Scenario: 正文无实现细节
- **WHEN** 审查任一条目的正文文本
- **THEN** 不含类名、包名、`.json` 路径、数据包路径、注册 ID 或内部字段名

#### Scenario: 无工程占位与变更口吻
- **WHEN** 审查任一条目的正文文本
- **THEN** 不含 `TODO`、"待补充"、"暂无来源"、"暂未实现"、"已修复"、"本次更新" 等工程或变更记录措辞

#### Scenario: 数值以玩法措辞呈现
- **WHEN** 配方页需要说明结构等级或灵力费用
- **THEN** 以「1 阶仪式」「20,000 灵力」等玩家可读措辞呈现，MUST NOT 写成 `minTier:1` / `spCost:20000`

### Requirement: 新增书内文案仅维护 zh_cn
本要求覆盖**全书**所有章节与词条，是各章零散同类约定的上位规则。书内新增或改写的玩家可见正文——序言、章节说明、条目首段与引言、物品词条说明页、配方页说明——SHALL 仅在 `zh_cn.json` 中维护。`en_us.json` 缺少对应键 MUST NOT 视为缺陷，也 MUST NOT 作为验收条件；MUST NOT 因追求中英逐键对齐而拒绝对玩家可见的中文文案。已存在的 `en_us` 键 MAY 保留，但 MUST NOT 因此形成对新增文案的同步义务。

书内正文之外的内容——JEI 配方键、tooltip、GUI 文案等——MUST NOT 援引本要求放宽其既有的中英文同步要求。

#### Scenario: 只维护中文即可
- **WHEN** 新增一个物品词条的说明页
- **THEN** 只需 `zh_cn.json` 存在对应键；`en_us.json` 缺失不算缺陷、不阻塞验收

#### Scenario: 不因缺英文而拒稿
- **WHEN** 校验新增书内文案的同步情况
- **THEN** 校验 MUST NOT 要求 `en_us.json` 存在对应键；已有英文键 MAY 保留且不构成新增文案的维护义务

#### Scenario: 中文环境正常显示
- **WHEN** 中文环境的玩家打开任一书内词条
- **THEN** 显示中文正文，MUST NOT 显示裸键或空页

