## ADDED Requirements

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
