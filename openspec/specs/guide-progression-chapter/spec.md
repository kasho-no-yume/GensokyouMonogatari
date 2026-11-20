# guide-progression-chapter Specification

## Purpose

指导书第七章节「成神之路」：12 步线性进度条目的章节形态（非 secret 的锁 + turnin 三态）、完成条件与 advancement 链、提前完成的事后补标记，以及玩家向文案规范。

## Requirements

### Requirement: 成神之路章节与线性条目

指导书 SHALL 提供第七个顶级章节 `gensokyou:path`（标题「成神之路」），其下 SHALL 恰有 12 个进度条目，按固定推进顺序以 `sortnum` 1–12 排列，顺序即玩家推进路线。条目 SHALL 为：制作仪式构筑杖、制作仪式核心、制作 0 阶灵力核心、搭建迦具土炎祭、获取幻想材料、精炼幻想材料、领受 1 阶神恩、制作千机核心、召唤并击败大妖精、领受 2 阶神恩、召唤并击败八云蓝残影、进入幻想乡。第 N 条（N≥2）的开放 MUST 严格依赖第 N−1 条完成，MUST NOT 跳跃开放。

#### Scenario: 章节置于首位
- **WHEN** 玩家打开书的落地页
- **THEN** 「成神之路」排在所有主题章节之前

#### Scenario: 十二步顺序呈现
- **WHEN** 玩家打开「成神之路」章节
- **THEN** 看到恰好 12 个按推进顺序排列的条目，第一步无开放门槛

#### Scenario: 后续步骤不跳跃开放
- **WHEN** 第 4 步尚未完成
- **THEN** 第 5 步至第 12 步均处于未开放状态，无法通过任何条目跳转提前阅读

### Requirement: 条目的锁—任务—完成三态

「成神之路」的每个条目 SHALL 以 Patchouli 的 `advancement` 字段挂**上一步的完成 advancement**、`turnin` 字段挂**本步的完成 advancement**，且 MUST NOT 设 `secret: true`。条目据此呈现三态：上一步未完成时为可见的锁定态（Locked）；上一步已完成而本步未完成时带 `(?)` 并置顶；本步完成时 `(?)` 消失。条目 SHALL NOT 完全隐藏，玩家 MUST 能看到后续步骤的存在。

#### Scenario: 未完成前一步时显示锁
- **WHEN** 玩家查看一个上一步尚未完成的进度条目
- **THEN** 该条目在章节列表中可见、显示为锁定态，且无法打开其页面

#### Scenario: 当前步骤带问号置顶
- **WHEN** 玩家完成第 N−1 步但尚未完成第 N 步
- **THEN** 第 N 步条目显示 `(?)` 并置顶，作为当前任务

#### Scenario: 完成后问号消失
- **WHEN** 玩家完成第 N 步对应的 advancement
- **THEN** 第 N 步条目的 `(?)` 消失，第 N+1 步按上一条开放

### Requirement: 十二步完成 advancement

系统 SHALL 为每个步骤提供一个隐形 advancement `gensokyou:guide/path/step_01_done` … `step_12_done`，每个 SHALL 无 `display`，并 SHALL 按步骤顺序以父子链串接（step_01_done 为根），使子项 criterion 在父项未完成时亦可被记录、父项完成时已满足的子项自动授予。八步 SHALL 由原版触发器判定，四步 SHALL 由服务端代码在动作发生的那一刻授予。

| 步骤 | 完成条件 | 判定方式 |
|---|---|---|
| 1 制作仪式构筑杖 | 持有 `gensokyou:ritual_builder` | `inventory_changed` |
| 2 制作仪式核心 | 持有 `gensokyou:ritual_core` | 复用 `achievement/ritual_core` 的 `inventory_changed` |
| 3 制作 0 阶灵力核心 | 持有 `gensokyou:spirit_core_0` | `inventory_changed` |
| 4 搭建迦具土炎祭 | 附近存在已成型的 `gensokyou:kagutsuchi_flame_circle` 结构（手工摆放或构建器搭建均可） | 服务端巡检成型核心授予，构建器成功时即时授予 |
| 5 获取幻想材料 | 持有任一幻想乡特有材料 | `inventory_changed`（物品列表或 tag 的 OR） |
| 6 精炼幻想材料 | 持有任一精炼矿物 | `inventory_changed`（物品列表或 tag 的 OR） |
| 7 领受 1 阶神恩 | 神恩仪式成功提升至 1 阶 | 服务端 Java 在神恩成功时按阶授予 |
| 8 制作千机核心 | 持有 `gensokyou:danmaku_weapon` | `inventory_changed` |
| 9 召唤并击败大妖精 | 击杀 `gensokyou:big_fairy` | 复用 `achievement/big_fairy` 的 `player_killed_entity` |
| 10 领受 2 阶神恩 | 神恩仪式成功提升至 2 阶 | 服务端 Java 在神恩成功时按阶授予 |
| 11 召唤并击败八云蓝残影 | 击杀 `gensokyou:yakumo_ran` 对应实体 | `player_killed_entity` |
| 12 进入幻想乡 | 进入 `gensokyou:gensokyo` 维度 | 复用 `guide/gensokyo_unlock` 的 `changed_dimension` |

#### Scenario: 原版触发器步骤自动判定
- **WHEN** 玩家合成并持有千机核心
- **THEN** `step_08_done` 完成，对应条目 `(?)` 消失

#### Scenario: 代码授予步骤即时判定
- **WHEN** 玩家手工摆放或使用构建器使迦具土炎祭成型
- **THEN** 服务端在成型被识别时授予 `step_04_done`，与当时前序步骤是否完成无关

#### Scenario: 手工摆放也算完成
- **WHEN** 玩家照着书里的 3D 投影手工摆出迦具土炎祭，未使用构建器
- **THEN** 巡检识别到附近成型的迦具土炎祭并授予 `step_04_done`，不要求必须用构建器

### Requirement: 提前完成的事后补标记

若某步的动作在第 N−1 步完成之前已经发生，系统 SHALL 在该动作发生时即记录该步的 criterion，并在父链解锁的瞬间把该步直接标记为完成，MUST NOT 要求玩家重复执行已完成的动作。条目 `(?)` 的消失 SHALL 以此为准。

#### Scenario: 提前制作千机核心
- **WHEN** 玩家在第 7 步完成前已合成千机核心，`step_08_done` 的 criterion 已被记录；随后第 7 步完成
- **THEN** `step_08_done` 在同一刻完成，第 8 步条目不显示 `(?)`，直接呈现已完成

#### Scenario: 旧存档兜底
- **WHEN** 玩家在加入含本变更的版本之前已完成某步，新 advancement 当时不存在、criterion 未被记录
- **THEN** 登录巡检按当前状态补授该步 advancement，使其在章节中直接呈现已完成

### Requirement: 进度条目的文案

12 条进度条目的玩家可见正文 SHALL 仅维护 `zh_cn`，以故事开篇、口吻含蓄、暗示而非点破机制，MUST NOT 出现注册 id、文件路径、内部字段名或工程占位语。正文中的物品名 SHALL 与游戏内译名一致，包括「弹幕方术装配台」「弹幕核：麟弹」「等级核」「增幅核」「千机核心」。每条正文 SHALL 包含对其所用系统或后续步骤的暗示。

#### Scenario: 只维护中文
- **WHEN** 校验任一条进度条目的正文键
- **THEN** 仅 `zh_cn.json` 需要存在对应键；`en_us` 缺失不视为缺陷

#### Scenario: 物品名与游戏内一致
- **WHEN** 审查第 8 步正文
- **THEN** 使用「等级核」「弹幕核：麟弹」「弹幕方术装配台」等游戏内译名，不使用「武器核」「方术台」等非游戏内简称

#### Scenario: 无实现细节
- **WHEN** 审查任一进度条目正文
- **THEN** 不含 advancement id、配方 id、`.json` 路径或内部字段名

### Requirement: 成神之路专名可跳转

「成神之路」正文与 spotlight 中凡提及指导书已有独立词条的专名、仪式、物品、BOSS 或材料，SHALL 使用 Patchouli 内链指向对应词条；链接目标 MUST 对应实际存在的条目。特别是各步骤提及的仪式、仪式构建器/核心/祭品台、灵力核心、幻想材料与精炼矿物、千机核心及其核、大妖精、八云蓝残影和结界仪式，均 SHALL 可从正文直接打开相关说明。

#### Scenario: 进度正文中的已收录专名可跳转
- **WHEN** 玩家阅读「成神之路」中提及已有独立词条的专名
- **THEN** 该名称呈现为可点击链接，并打开对应词条

#### Scenario: 链接目标有效
- **WHEN** 检查「成神之路」正文中的 Patchouli 内链
- **THEN** 每个链接均指向书中现存条目，不出现无效目标
