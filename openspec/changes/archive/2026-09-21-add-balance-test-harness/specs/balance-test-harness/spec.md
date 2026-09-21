## ADDED Requirements

### Requirement: 测试命令集
模组 SHALL 提供命令 `/gs_test`（权限等级 2），包含子命令：
`player <tier:1..5> [infinite]`、`boss <tier:1..5> <min|max>`、`clear`、`reset`。
命令 SHALL 仅作用于执行者本人及其实体；`tier` 越界时拒绝并提示。命令 MUST NOT 影响其他玩家。

#### Scenario: 一键配置玩家
- **WHEN** 2 阶玩家执行 `/gs_test player 4`
- **THEN** 该玩家被配置为 4 阶标准数值，其余玩家不受影响

#### Scenario: 越界拒绝
- **WHEN** 执行 `/gs_test player 9`
- **THEN** 命令拒绝并提示合法取值范围，无任何属性变化

#### Scenario: 生成指定变体BOSS
- **WHEN** 执行 `/gs_test boss 3 max`
- **THEN** 在玩家附近生成 3 阶 max 变体测试 BOSS

### Requirement: 玩家标准数值配置
`/gs_test player <tier>` SHALL 把玩家配置为该阶级的**标准数值**：先清空既有 `grace_tier_N` 贡献与池台账，再按 grace 表 **base 中点**（不做随机 roll）应用 1..tier 阶全部在册成长键；池设为满、`temperLevel` 设为该 tier；标准数值贡献 MUST NOT 与 `grace_tier_N` 或 `command` 来源互相覆盖，SHALL 使用独立 sourceId。配置 SHALL 同步刷新原版属性桥（生命/移速）。标准装备 SHALL 按武器带发放（tier1-2→Lv1/T1，3-4→Lv2/T2，5→Lv3/T3）。

#### Scenario: 标准值无随机偏差
- **WHEN** 连续两次对同一玩家执行 `/gs_test player 3`
- **THEN** 两次得到完全相同的属性值（确定性，无 roll）

#### Scenario: 独立来源不串扰
- **WHEN** 配置标准数值后玩家再执行真实进阶
- **THEN** 测试来源贡献与新阶 `grace_tier_N` 贡献各自独立、互不覆盖

#### Scenario: 池满与阶级
- **WHEN** `/gs_test player 5` 执行完成
- **THEN** 玩家阶级为 5、灵力池为满、灵力强度等于 5 阶标准值

### Requirement: 测试无限灵力态
`/gs_test player <tier> infinite` SHALL 开启"测试无限灵力"：服务端 tick 中持续把该玩家灵力池补至有效上限；
`reset` SHALL 关闭该态。无限灵力 MUST NOT 改变最大灵力等永久属性，也 MUST NOT 对未开启该态的玩家生效。

#### Scenario: 持续补满
- **WHEN** 开启无限灵力的玩家连续发射主武器
- **THEN** 灵力池维持在满值、不发生枯竭

#### Scenario: 关闭即恢复
- **WHEN** 对该玩家执行 `/gs_test reset`
- **THEN** 无限灵力关闭，池按普通规则变化

### Requirement: 测试BOSS数值标定
`/gs_test boss <tier> <min|max>` 生成的测试 BOSS SHALL 以"标准玩家 DPS × 时长"标定生命，
使**同阶标准装备下平均击杀时长 ≥ 2 分钟**：`min` 变体取该阶区间**最低值**（默认 120 秒标准 DPS），
`max` 变体取**最高值**（默认 180 秒标准 DPS）。弹幕单发伤害 SHALL 取该阶 BOSS 预算的
`playerEHP / hits`（min 较弱、max 较强）。HP 与弹伤数值 MUST 由 config `testHarness` 段驱动，MUST NOT 硬编码。
测试 BOSS SHALL 对非弹幕伤害沿用东方怪的 90% 减免。

#### Scenario: 同阶标准装备TTK达标
- **WHEN** 用标准装备的同阶玩家持续输出 min 变体测试 BOSS
- **THEN** 平均击杀时长不少于约 2 分钟（给足换阶段时间）

#### Scenario: 两端变体
- **WHEN** 分别生成同阶的 min 与 max 变体
- **THEN** max 变体的生命与弹伤均高于 min 变体

#### Scenario: 非弹幕减免
- **WHEN** 用普通近战武器攻击测试 BOSS
- **THEN** 伤害按东方怪非弹幕减免（90%）计入，不通过普通武器速杀

### Requirement: 测试BOSS弹幕模式与阶段
测试 BOSS SHALL 每 **3 秒**（可配置）随机选择一种弹幕模式发射，且 SHALL 提供**共 10 种**东方美学模式：
环弹、螺旋、自机狙、扇形、星芒、花弹、迟弹、激光、追踪符、弹幕雨。BOSS SHALL 按生命阈值分阶段切换
可用模式池、发射间隔与弹速（默认三段：P1 100-66%、P2 66-33%、P3 33-0%）。模式参数与阶段划分
MUST 由 config 驱动；模式生成 SHALL 复用既有弹幕实体，MUST NOT 新增弹幕实体类型。

#### Scenario: 每三秒换模式
- **WHEN** 测试 BOSS 进入战斗
- **THEN** 每约 3 秒切换并发射一种（尽可能不与上一次重复的）弹幕模式

#### Scenario: 十种模式齐备
- **WHEN** 战斗中累计统计发射过的模式
- **THEN** 最终可覆盖 10 种模式（跨阶段）

#### Scenario: 阶段切换
- **WHEN** BOSS 生命跌破 66% / 33%
- **THEN** 其可用模式池、发射间隔与弹速按阶段配置提升，给予换阶段感

#### Scenario: 复用既有实体
- **WHEN** 查阅各模式的实现
- **THEN** 全部使用既有球/飞刀/灵符/激光实体，无新增弹幕实体类型

### Requirement: 测试BOSS移动与清理
测试 BOSS 的移动 SHALL 与小妖精一致（飞行、悬停于玩家上方，受天花板限制，不依赖地面寻路）。
`/gs_test clear` SHALL 移除本命令生成的测试 BOSS；测试 BOSS SHALL NOT 参与正式掉落与图鉴收录。

#### Scenario: 移动一致
- **WHEN** 观察测试 BOSS 的移动
- **THEN** 其飞行/悬停行为与小妖精一致

#### Scenario: 一键清理
- **WHEN** 场上有多个测试 BOSS 时执行 `/gs_test clear`
- **THEN** 本命令生成的测试 BOSS 全部被移除，正式生物不受影响

#### Scenario: 不产正式掉落
- **WHEN** 击杀测试 BOSS
- **THEN** 不产生正式掉落，也不进入图鉴
