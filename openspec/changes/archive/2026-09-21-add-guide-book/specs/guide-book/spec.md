# guide-book Specification

## Purpose

《幻想乡物语》指导书框架：基于外部依赖的 Patchouli 数据驱动指导书，作为本 mod 玩法的唯一权威游戏内教程载体（JEI 仅为可选加速器）。覆盖书本体与打开行为、首次序言、六大章节骨架、进度解锁、仪式自定义配方页、书页配方与图标。

## ADDED Requirements

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
书 SHALL 提供隐形引导 advancement（进入下界、进入末地，`changed_dimension` 触发器）。需要门槛的内容条目 SHALL 以 `"advancement"` + `"secret": true` 声明：解锁前条目在书中**完全不显示**（非灰色锁定态），解锁时弹出 toast。0 阶内容条目 MUST NOT 挂任何 advancement（无条件可见）。

#### Scenario: 1 阶内容的门槛
- **WHEN** 玩家从未进入下界时阅读仪式章节
- **THEN** 挂 nether_unlock 的 1 阶内容条目不出现；对应 0 阶条目正常可见

#### Scenario: 进入下界解锁
- **WHEN** 玩家进入下界后重开仪式章节
- **THEN** 1 阶内容条目出现并收到解锁 toast

#### Scenario: 2 阶内容的门槛
- **WHEN** 玩家已进下界但未进末地
- **THEN** 挂 end_unlock 的 2 阶内容条目在书中完全不出现

### Requirement: 仪式章节配方卡与结构展示
仪式列表章节 SHALL 以自定义页面模板渲染配方卡（输入物品×数量、产物或本地化效果名、spCost、minTier），展示字段与语言键 SHALL 与 `jei-ritual-display` 共用同一数据模型（`RitualRecipe`）。仪式结构 SHALL 以 Patchouli 原生 `patchouli:multiblock` 页呈现 3D 投影（支持在世界上幽灵投影照搭），其多重方块 SHALL 由构建期生成器从 `rituals` v5 数据内联生成（谓词映射 EXACT→方块 id、TAG→`#tag`、AIR→空格、IGNORE→`_`、锚点→`0`），MUST NOT 依赖运行期 `multiblock_id` 注册。结构与「本阶参数/材料」SHALL **按阶级分页**并各挂 `gensokyou:guide/tier_N` 隐形 advancement 门槛，显示以玩家当前超人类阶级为上限（锁住的页完全隐藏）；配方 SHALL **一配方一页**。客户端 SHALL 在玩家登录与 datapack 重载时从服务端同步配方数据并写入本地缓存；数据未到达时配方卡 SHALL 呈现占位，离线时读缓存。

#### Scenario: 仪式条目渲染
- **WHEN** 打开某个已配置仪式数据与配方文件的条目
- **THEN** 页面呈现 3D 多重方块结构投影（可点眼睛投射）与配方卡（原料与数量、产物/效果名、spCost、结构阶级）

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
