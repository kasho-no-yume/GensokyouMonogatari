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

书 SHALL 提供隐形引导 advancement：`guide/nether_unlock`（进入下界）、`guide/end_unlock`（进入末地）、`guide/gensokyo_unlock`（进入幻想乡维度），均为 `changed_dimension` 触发器、无 display。需要门槛的内容条目 SHALL 以 `"advancement"` + `"secret": true` 声明：解锁前条目在书中**完全不显示**（非灰色锁定态），解锁时弹出 toast。

内容门槛 SHALL 按**内容所属的轴**选择，MUST NOT 一律按结构阶挂：

- **材料带**：T1 材料页（指导书带）MUST NOT 挂额外 advancement；T2 材料页（花精之滴带）SHALL 挂 `guide/nether_unlock`——花精之滴由 T1 阶段的大妖精产出，玩家在进入末地前就可能采到 T2 材。
- **结构阶**：结构/仪式词条 SHALL 按结构阶挂 `guide/nether_unlock`（1 阶）/ `guide/end_unlock`（2 阶）/ `guide/gensokyo_unlock`（3 阶）；2 阶石与 2 阶结构继续挂末地。
- **BOSS**：按 `bossTier()` 挂对应门槛（沿用「BOSS 条目的阶级门槛」）。
- **玩家阶**：神恩相关条目挂对应世界进度或玩家阶，二者取其一并写明。

0 阶内容条目 MUST NOT 挂任何 advancement（无条件可见）。结构阶 4/5 的门槛在阶段 4/5 世界进度落地前 SHALL 以过渡实现承载（`temperLevel` 授予）。门槛判定 SHALL 以世界进度而非玩家超人类阶级（temperLevel）为唯一依据；玩家阶级不得替代世界进度解锁材料/结构页。

#### Scenario: 1 阶结构页按末地前的世界进度

- **WHEN** 玩家从未进入下界时阅读仪式章节
- **THEN** 挂 `guide/nether_unlock` 的 1 阶结构页不出现；对应 0 阶条目正常可见

#### Scenario: 进入下界解锁 1 阶结构

- **WHEN** 玩家进入下界后重开仪式章节
- **THEN** 1 阶结构页出现并收到解锁 toast

#### Scenario: 星银材料页在下界后出现

- **WHEN** 玩家已进入下界、尚未进入末地
- **THEN** `material_star_silver` 词条可见，且正文说明花精之滴是 T2 材料带信物

#### Scenario: 2 阶结构页仍按末地

- **WHEN** 玩家已进入下界但未进入末地
- **THEN** 挂 `guide/end_unlock` 的 2 阶石/2 阶结构/2 阶核心/八云蓝条目不出现

#### Scenario: 3 阶内容按幻想乡解锁

- **WHEN** 玩家进入幻想乡维度后
- **THEN** 挂 `guide/gensokyo_unlock` 的 3 阶内容条目出现

#### Scenario: 0 阶内容无条件可见

- **WHEN** 新建世界、玩家尚未进入下界
- **THEN** 0 阶石/0 阶仪式/材料带总览等无 advancement 条目正常可见

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

### Requirement: BOSS 条目覆盖与交付义务

书 SHALL 为**每一个当前可遭遇的正式 BOSS** 在「敌对生物列表」分类下各提供一条条目。可遭遇指：已接入生成通路、玩家在正常游玩中能实际打到（当前为百鬼夜行召唤，往后可为幻想乡任务或结构刷新）。

调试、探针、未迁移实验体，以及尚未接入生成通路的 BOSS MUST NOT 建条目。

新增 BOSS 的 change SHALL 在同一次交付内补齐其指导书条目与 `zh_cn` 文案，MUST NOT 留待后续会话。

#### Scenario: 现有四只均有条目

- **WHEN** 打开「敌对生物列表」
- **THEN** 大妖精、黑谷山女、露米娅、八云蓝残影各有一条可打开条目

#### Scenario: 调试 BOSS 不建条目

- **WHEN** 审查条目文件
- **THEN** 不存在 `BalanceTestBossEntity`、`BloodBarProbeBoss`、`UnmigratedTouhouBoss` 对应条目

#### Scenario: 新 BOSS 同交付

- **WHEN** 一个 change 新增了可遭遇 BOSS
- **THEN** 该 change 的交付物中包含其指导书条目；缺条目即视为未完成

### Requirement: BOSS 条目的阶级门槛

条目 SHALL 以 BOSS 自身阶级（`bossTier()`）映射世界进度门槛，并设 `secret: true`：

| 自身阶级 | 门槛 |
|---|---|
| 1 | `gensokyou:guide/nether_unlock` |
| 2 | `gensokyou:guide/end_unlock` |
| 3 | `gensokyou:guide/gensokyo_unlock` |
| 4 | `gensokyou:guide/tier_4` |
| 5 | `gensokyou:guide/tier_5` |

门槛 MUST NOT 由召唤仪式的结构阶、玩家 `temperLevel` 或首次击败决定。

#### Scenario: 未进下界时 T1 条目隐藏

- **WHEN** 玩家从未进入下界
- **THEN** 三条 T1 BOSS 条目在书中完全不出现

#### Scenario: 进入下界后 T1 解锁

- **WHEN** 玩家进入下界并打开「敌对生物列表」
- **THEN** 大妖精、黑谷山女、露米娅条目出现并收到解锁提示

#### Scenario: T2 条目按末地解锁

- **WHEN** 玩家已进下界但未进末地
- **THEN** 八云蓝残影条目不出现；进入末地后出现

### Requirement: BOSS 条目的排序契约

普通敌对生物条目的 `sortnum` SHALL 落在 `0–99`。BOSS 条目 SHALL 落在 `100 × bossTier` 起的区间（T1 为 100–199，T2 为 200–299，依此类推），同阶内按推进顺序递增。

稳定态下（无未读条目时）BOSS 条目 SHALL 全部排在普通条目之后，并按阶级从低到高排列。

未读条目被 Patchouli 置顶是其既有排序行为，MUST NOT 被视为违反本要求。

#### Scenario: 稳定态顺序

- **WHEN** 玩家已读过「敌对生物列表」中全部条目
- **THEN** 普通怪条目在前，其后依次为大妖精、黑谷山女、露米娅、八云蓝残影

#### Scenario: 未读置顶不算违规

- **WHEN** 玩家刚解锁 T1 且尚未打开任一 T1 BOSS 条目
- **THEN** 这三条未读条目临时排在已读的普通怪条目前面，且不判为违反排序契约

### Requirement: BOSS 条目的三页骨架与内容边界

条目 SHALL 只包含三页，依次为：

1. 引言（`patchouli:text`）
2. 外观（`patchouli:entity`，只展示模型，不配文字）
3. 抵达与信物（同一页，见下一条要求）

条目 MUST NOT 包含 BOSS 的生命、伤害、秒数等数值，MUST NOT 列出符卡名或符卡机制。

引言 SHALL 面向玩家、以故事开篇，并 MAY 改编自官方设定集；条目正文 MUST NOT 出现注册 id、文件路径或内部字段名。

#### Scenario: 三页结构

- **WHEN** 打开任一 BOSS 条目
- **THEN** 首页为引言，第二页为可旋转的 3D 模型，第三页同时呈现抵达方式与该 BOSS 的信物

#### Scenario: 外观页无描述文字

- **WHEN** 查看外观页
- **THEN** 页面上只有模型，没有长相描述文字

#### Scenario: 不写数值与符卡

- **WHEN** 审查任一 BOSS 条目的正文
- **THEN** 不含生命/伤害/秒数，也不含符卡名称或机制描述

### Requirement: BOSS 条目的抵达与信物同页

抵达页 SHALL 同时承载「怎么遇到」与「掉什么信物」。

仪式召唤型 BOSS，抵达页 SHALL 渲染其配方卡（含物品图标、灵力消耗与结构阶），并在同一页展示该 BOSS 的主题信物。配方 SHALL 通过 `recipe` 字段按**配方 id** 绑定，MUST NOT 使用 `recipe_index`。

无召唤配方的 BOSS（任务授予、结构遭遇等），抵达页 SHALL 以文字写明遭遇地点与条件，并在同一页展示信物；信物无法与文字同页时 MAY 另起一页展示。

有主题信物的 BOSS MUST 在抵达页展示该信物；无主题信物的 BOSS MAY 省略信物展示。

信物展示 MUST NOT 配描述文字，也 MUST NOT 指引玩家前往镇魂返照之仪的配方。

任何校验 MUST NOT 要求 BOSS 必须具有召唤配方，也 MUST NOT 将缺少配方判为不合规。

#### Scenario: 仪式型抵达页同时显示配方与信物

- **WHEN** 打开大妖精条目的第三页
- **THEN** 同时看到百鬼夜行的召唤配方卡（含灵力消耗与结构阶）与「花精之滴」

#### Scenario: 任务或结构遭遇型

- **WHEN** 未来的 BOSS 只有任务或结构遭遇，没有召唤配方
- **THEN** 抵达页以文字写明遭遇地点与条件并展示信物，审计不因缺配方报错

#### Scenario: 配方按 id 绑定

- **WHEN** 审查仪式型 BOSS 的抵达页
- **THEN** 使用 `recipe` 字段指向配方 id，MUST NOT 使用 `recipe_index`

#### Scenario: 信物不配文字、不指向镇魂

- **WHEN** 审查抵达页
- **THEN** 信物只有图标、没有描述文字，也不含前往镇魂返照之仪的链接

### Requirement: BOSS 条目图标

条目图标 SHALL 使用该 BOSS 的主题信物物品 id，与抵达页展示的信物保持一致。

无主题信物的 BOSS MAY 使用其代表物品作为图标。

MUST NOT 使用模型面部裁切的贴图：16×16 下的脸部辨识度不足，且模型处于占位状态时会连带拖累书目观感。

#### Scenario: 四只均用信物图标

- **WHEN** 打开「敌对生物列表」
- **THEN** 大妖精、黑谷山女、露米娅、八云蓝残影分别显示花精之滴、土蜘蛛之丝、宵暗之核、隙间碎片

#### Scenario: 与抵达页一致

- **WHEN** 对比某 BOSS 条目的列表图标与抵达页信物
- **THEN** 两者是同一件物品

#### Scenario: 无信物时回落

- **WHEN** 某 BOSS 未声明主题信物
- **THEN** 其条目使用该 BOSS 的代表物品作图标，不影响条目其余内容

### Requirement: 残魂典籍词条的一信物一页

「寄宿残魂的众生典籍」词条 SHALL 为每一条真实的合成配方各提供一页。页标题 SHALL 是该配方所用主题信物的名称；正文 SHALL 链回对应 BOSS 条目。

每页 SHALL 挂对应 BOSS 的阶级门槛；高阶级 BOSS 的信物页 MUST NOT 对低阶玩家显示。

MUST NOT 只展示单条配方，或使玩家以为某一信物是唯一可用的。

#### Scenario: 四条配方各一页

- **WHEN** 玩家已进末地并打开残魂典籍词条
- **THEN** 依次看到花精之滴、土蜘蛛之丝、宵暗之核、隙间碎片四页，各显示对应配方

#### Scenario: 标题为信物名

- **WHEN** 查看任一配方页的标题
- **THEN** 显示该页所用信物的名称，而非统一的产物名

#### Scenario: 高阶信物页隐藏

- **WHEN** 玩家未进末地时打开该词条
- **THEN** 隙间碎片那页不出现

#### Scenario: 链回 BOSS 条目

- **WHEN** 查看任一配方页的正文
- **THEN** 含指向该信物所属 BOSS 条目的链接

### Requirement: BOSS 条目的静态守卫

仓库 SHALL 提供静态审计脚本，对**当前可遭遇 BOSS 集合**断言：条目存在、门槛与 `bossTier()` 一致、`sortnum` 落在对应阶级区间、图标资源存在、抵达页合法（配方 id 可解析或存在文字页）且含信物展示。

`bossTier()` SHALL 从实体类读取，MUST NOT 另建平行阶级表。

发现源 SHALL 只包含可遭遇集合；脚本 MUST NOT 要求 BOSS 必须存在召唤配方。

#### Scenario: 漏写条目被发现

- **WHEN** 已接入生成通路的新 BOSS 没有对应条目
- **THEN** 审计失败并指出缺失的 BOSS

#### Scenario: 门槛与阶级不一致被发现

- **WHEN** 某 BOSS 的 `bossTier()` 改为 2，但条目仍挂 `nether_unlock`
- **THEN** 审计失败

#### Scenario: 新遭遇方式不误报

- **WHEN** 某 BOSS 只有结构遭遇、没有召唤配方
- **THEN** 审计通过其条目检查

### Requirement: 仪式入门展示仪式石升阶配方

「仪式列表」章节的第一条「仪式入门」SHALL 在原有正文/构建器页之后增加两页
`gensokyou:ritual_page`，分别按**完整配方 id** 绑定：

- `gensokyou:zaohua_stone_t1`（0 阶石 → 1 阶石）
- `gensokyou:zaohua_stone_t2`（1 阶石 → 2 阶石）

两页 MUST NOT 依赖全局配方排序（`recipe_index`）或 JEI；MUST 在配方数据未到达时呈现占位，
不得渲染空白页或裸 id。0 阶/1 阶石页 SHALL 常驻可见；2 阶石页 SHALL 挂 `guide/end_unlock`
（其配方需要末地紫珀块）。页面 SHALL 复用 `ritual_page` 的配方卡渲染，显示原料×数量、
产物×数量、spCost 与结构阶级。

#### Scenario: 0 阶/1 阶石页常驻

- **WHEN** 新玩家第一次打开「仪式入门」
- **THEN** 能看到 0 阶石→1 阶石与 1 阶石→2 阶石的配方页（2 阶页在进末地前隐藏）

#### Scenario: 2 阶石页按末地解锁

- **WHEN** 玩家进入末地后打开「仪式入门」
- **THEN** 2 阶石配方页出现，显示 2 仪式石 1 + 4 灵铁 + 潮汐晶 + 三植物 + 6 紫珀块 → 4 仪式石 2

#### Scenario: 配方按 id 绑定

- **WHEN** `zaohua_circle.json` 的配方顺序发生变化
- **THEN** 两页仍分别显示 `zaohua_stone_t1` / `zaohua_stone_t2`，不显示其它配方

#### Scenario: 数据未到达时占位

- **WHEN** 客户端刚登录、服务端配方数据尚未同步
- **THEN** 两页显示占位而非空白或裸 id；数据到达后在同一会话内刷新

### Requirement: 新物品书内条目

指导书 SHALL 为以下新增内容各提供玩家向条目，且条目正文 MUST NOT 出现注册 id、
文件路径、内部字段名或工程占位语：

- **人鱼鳞盔甲**：`items` 章，展示 4 件、钻石甲数值、全套水下呼吸 + 仅水下夜视、
  5/8/7/4 配方。
- **龙鳞盔甲**：`items` 章，展示 4 件、钻石甲数值、全套火焰伤害全免 + 100% 抗击退、
  5/8/7/4 配方。
- **灵鱼与海豚恩惠**：`fantasy_materials` 章一条灵鱼条目，`items` 章一条药水条目；
  说明素盏呜尊酿造、只做长效档、长效需月砂。
- **P 点 / B 点**：`items` 章一条；说明 P 点用于仪式石/灵力核心，
  B 点由 8 P 点合成、用于符卡配方（低 1 / 中 2 / 高 2）。
- **妖精与众生典籍**：`monsters` 章或 `codex_of_beings` 词条补一段；
  说明小妖精可被典籍收录、镇魂可产出记忆残页（纸）/P/B 点，大妖精仍不可收录。

新增文案 SHALL 仅维护 `zh_cn`；`en_us` 缺失不视为缺陷。条目图标 SHALL 使用对应物品 id，
MUST NOT 使用模型面部裁切或纯文字代替。

#### Scenario: 人鱼鳞/龙鳞条目可打开

- **WHEN** 玩家打开 `items` 章
- **THEN** 能分别打开人鱼鳞盔甲与龙鳞盔甲条目，看到 4 件、效果与配方

#### Scenario: 灵鱼药水条目说明长效

- **WHEN** 玩家打开灵鱼/海豚恩惠条目
- **THEN** 正文写明素盏呜尊酿造、只有长效档、长效消耗月砂

#### Scenario: P/B 点条目说明用途

- **WHEN** 玩家打开 P/B 点条目
- **THEN** 正文写明 8 P → 1 B 与 B 点在符卡配方中的数量档位

#### Scenario: 妖精典籍条目说明产出

- **WHEN** 玩家打开妖精与众生典籍条目
- **THEN** 正文写明小妖精可收录、镇魂产出纸/P/B 点

