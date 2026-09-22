# guide-book Specification

## Purpose

《幻想乡物语》指导书框架：基于外部依赖的 Patchouli 数据驱动指导书，作为本 mod 玩法的唯一权威游戏内教程载体（JEI 仅为可选加速器）。覆盖书本体与打开行为、首次序言、六大章节骨架、进度解锁、仪式自定义配方页、书页配方与图标。

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: 仪式条目内容规范
仪式列表章 SHALL 采用「第一条为仪式入门（`sortnum` 最小），其后每个**可正常游玩的**已实现仪式各一条目」的形态；**创造/调试类仪式 MUST NOT 建条目**。每个仪式条目 SHALL 以**故事开篇 + 简短引言**作为正文**同一首页**（口吻含蓄、不必点破机制），其后 SHALL 按结构阶依次呈现该阶结构（multiblock）与该阶**具体参数**（按该阶计算，不写公式、不带「会话型／启停型」等标签）；生产/献祭类仪式 SHALL 以**物品图标**呈现其配方/掉落（JEI 风格，附概率）。正文换行 SHALL 使用 `$(br)`/`$(br2)`，MUST NOT 使用 `\n`。**最低结构阶 ≥ 1 的**仪式条目 SHALL 挂该阶**世界进度**门槛（`secret`）；最低阶为 0 的条目常驻可见。其逐阶结构/参数页 SHALL 各挂对应世界进度门槛。条目 SHALL 以 `sortnum` 明确排序。**没有 pattern 数据的占位/未实现仪式 MUST NOT 建条目**（待其 pattern 落地再补）。新增文案 SHALL 仅维护 `zh_cn`，MUST NOT 要求同步 `en_us`。

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

#### Scenario: 换行正确
- **WHEN** 条目的故事与引言同页呈现
- **THEN** 使用 `$(br2)` 分段正确显示，MUST NOT 出现方框/乱码

#### Scenario: 占位仪式不建条目
- **WHEN** 某仪式尚无 pattern 数据（占位/未实现）
- **THEN** 书中不存在其条目

#### Scenario: 创造/调试类不建条目
- **WHEN** 某仪式为创造/调试用途（如赛尔能源）
- **THEN** 书中不存在其条目

#### Scenario: 仅中文
- **WHEN** 校验新增文案的 lang 键
- **THEN** 仅 `zh_cn.json` 有对应键，`en_us.json` 无需新增
