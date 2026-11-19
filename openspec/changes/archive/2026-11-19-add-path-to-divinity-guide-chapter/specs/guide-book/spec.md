## MODIFIED Requirements

### Requirement: 六大章节骨架
书 SHALL 提供六个主题顶级章节：术语解释、符卡列表、敌对生物列表、NPC 列表、仪式列表、武器系统，并额外提供第七个进度章节「成神之路」（`gensokyou:path`，见 `guide-progression-chapter`）。`book.json` SHALL 位于 `data/gensokyou/patchouli_books/gensokyou_book/` 并设置 `use_resource_pack: true`；章节与条目 SHALL 位于 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/`（`en_us` 为 Patchouli 基准目录，中文正文可直写其中），`zh_cn/` SHALL 作为可选覆盖。中英文环境 MUST NOT 出现裸 id。本 change SHALL 交付各章至少一个样例条目与序言条目。

#### Scenario: 章节列表
- **WHEN** 打开落地页
- **THEN** 章节宫格呈现六个主题章节与「成神之路」，标题经 lang 键本地化

#### Scenario: 样例条目可读
- **WHEN** 打开任一章
- **THEN** 至少存在一个可打开的样例条目，正文无裸 key

### Requirement: 进度解锁机制

书 SHALL 提供隐形引导 advancement：`guide/nether_unlock`（进入下界）、`guide/end_unlock`（进入末地）、`guide/gensokyo_unlock`（进入幻想乡维度），均为 `changed_dimension` 触发器、无 display。除「成神之路」进度章节外，需要门槛的内容条目 SHALL 以 `"advancement"` + `"secret": true` 声明：解锁前条目在书中**完全不显示**（非灰色锁定态），解锁时弹出 toast。「成神之路」的进度条目 MUST NOT 使用此规则，SHALL 以非 secret 的锁 + `turnin` 呈现（见 `guide-progression-chapter`）。

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

#### Scenario: 进度条目不走隐藏门槛
- **WHEN** 玩家在前序步骤未完成时查看「成神之路」章节
- **THEN** 后续条目显示为可见的锁态而非完全隐藏，且不出现空白章节
