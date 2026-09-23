## MODIFIED Requirements

### Requirement: 总权重表模型
权重 SHALL 为**相对总权重**而非写死概率。某材质的实际抽取池 SHALL 由「commons 桶 + 该材质 special + 条件池」构成：commons 桶权重 = `max(0, commonsTotal − Σspecial)`（`commonsTotal` 默认 100），桶内按 commons 相对权重分配；条件池（nether / end / gensokyou）为额外加权。每次抽取 MUST 按各条目权重归一化选取 1 件。有材质 Σspecial 达到 `commonsTotal` 时其 commons 桶 SHALL 为空（对应「不出基础块」）。

#### Scenario: 基础块占比
- **WHEN** 木镐 special 为 `coal` 权重 0.5、commonsTotal 为 100
- **THEN** coal 的实际概率约为 0.5%（0.5 / 100），其余分配给 commons 桶

#### Scenario: 桶被清空
- **WHEN** 某材质 special 权重之和达到 100（如下界合金镐）
- **THEN** 该材质不产出任何 commons 桶物品

#### Scenario: 条件池额外加权
- **WHEN** 地狱条件满足
- **THEN** nether 列表条目加入抽取池，与既有条目一起按总权重抽取

#### Scenario: 幻想乡池额外加权
- **WHEN** 摆放了对应信物且其他条件满足
- **THEN** gensokyou 列表条目与既有条目一起按总权重抽取

### Requirement: 数据驱动的权重表加载
权重表 SHALL 存放于 `data/gensokyou/ritual_loot/*.json`（一仪式一文件）并经服务端数据重载监听器加载。加载器 SHALL 校验物品 id 存在性与权重非负性，非法文件 MUST 被拒载并报因、MUST NOT 影响其他文件。文件内容 SHALL 支持顶层 `commons` 与按工具材质的 `special` / `nether` / `end` 列表，并 SHALL 支持顶层 `gensokyou_low` / `gensokyou_high` 幻想乡信物带列表（不按工具材质），并 SHALL 支持每材质可选覆盖 commons。

#### Scenario: 热重载生效
- **WHEN** 修改某 ritual_loot JSON 后执行重载
- **THEN** 该仪式后续结算使用新的权重表，无需重启

#### Scenario: 非法文件拒载
- **WHEN** 某权重表引用了不存在的物品 id
- **THEN** 该文件被拒载并记录原因，其余仪式的权重表照常可用

#### Scenario: gensokyou 信物带可声明
- **WHEN** 某 ritual_loot 文件在顶层声明 `gensokyou_low` / `gensokyou_high` 列表
- **THEN** 加载成功，条目在对应信物条件满足时参与抽取

## ADDED Requirements

### Requirement: 幻想乡信物条件池
工具献祭仪式 SHALL 支持幻想乡信物带（第四个条件池），由祭品台上摆放的**信物**解锁：摆放 **指导书** 解锁低阶带（`gensokyou_low`，T1 材）、摆放 **隙间碎片**（`gensokyou:sukima_fragment`）解锁中阶带（`gensokyou_high`，T2 材）；两枚信物同时在场时两带同时解锁。每枚信物 SHALL 至少各 1 件即可解锁对应带，且 **MUST NOT 被结算消耗**。信物条件 SHALL 与头颅条件（nether / end）相互独立，任一满足即解锁其对应池。信物判定 MUST 仅在服务端进行，MUST NOT 依赖客户端状态。海产线（绵津见钓鱼仪式）SHALL 以其特产池的 `gensokyou_low` / `gensokyou_high` 条目承载同类信物带。

#### Scenario: 指导书解锁低阶带
- **WHEN** 祭品台上摆有 ≥1 本指导书且其余条件满足
- **THEN** 本次结算抽取池包含该仪式 `gensokyou_low` 条目，指导书不被消耗

#### Scenario: 隙间碎片解锁中阶带
- **WHEN** 祭品台上摆有 ≥1 个隙间碎片
- **THEN** 抽取池包含该仪式 `gensokyou_high` 条目，隙间碎片不被消耗

#### Scenario: 无信物不解锁
- **WHEN** 祭品台上既无指导书也无隙间碎片
- **THEN** 信物带全部条目不参与抽取，结算结果与原行为完全一致

#### Scenario: 与头颅条件互不干扰
- **WHEN** 台上仅有信物而无骷髅头 / 龙首
- **THEN** nether / end 池不解锁，但信物带照常解锁
