# player-attribute-suite Specification

## Purpose
TBD - created by archiving change player-attribute-suite. Update Purpose after archive.
## Requirements
### Requirement: 属性注册表
模组 SHALL 提供玩家属性注册表（AttributeKey 枚举），每个键声明：显示名、结算域（基准/加区/独立乘区）、是否可被变身改写、对应配置基准项、硬上限（若有）。全部玩家属性 MUST 经此注册表定义；消费方 MUST NOT 读写注册表外的玩家属性键。本期注册表 SHALL 收录 **18 键**：原有 15 键（最大灵力、灵力恢复速率、灵力强度、生命增幅、移动速度、擦弹率、弹幕减免、弹幕抵抗、韧性、暴击率、暴击伤害、符卡增幅、符卡冷却缩减、强效延长、灵力汲取）+ 修灵馈赠 3 键（**跳跃 `jump`、物抗 `phys_resist`、近战 `melee_damage`**，见 `cultivation-gifts`）。新增三键均 MUST NOT 进入降神变身改写白名单。

#### Scenario: 表外属性被拒
- **WHEN** 某代码路径尝试向属性容器写入未注册的属性键
- **THEN** 该写入无效（属性容器拒绝未知键），不产生隐式新属性

#### Scenario: 新增属性仅需注册
- **WHEN** 未来新增一个玩家属性
- **THEN** 只需在注册表增键并接消费点，不改属性容器的持久化结构

#### Scenario: 注册表键数
- **WHEN** 枚举全部键
- **THEN** 恰好 18 个且 id 唯一（含修灵馈赠三键）

### Requirement: 属性容器与持久化

每名玩家 SHALL 持有一个属性容器（独立于灵力池的 `player_attributes` 附件），含**两层**加区贡献（各层内按来源 `sourceId` 分组）：

1. **持久层**（`permanent`）—— 永久性加区贡献，随存档持久化、死亡保留（`copyOnDeath`）
2. **临时层**（`temp`）—— **不入存档**、由外部瞬态状态派生、整层丢弃即恢复的加区贡献。语义为"生命周期由外部瞬态决定"，MUST 同时承载**降神变身改写**与**装备提供**两类来源，二者以 `sourceId` 命名空间区分

**关键约束：加区容器内 MUST NOT 存在乘法。** 最终值 SHALL 为 `min(基准 + Σ全部来源贡献, 硬上限)`。乘区仅存在于消费点（如弹幕伤害乘积、`1 + 暴击伤害加成`、`2^-护壁指数`），MUST NOT 以"新增来源层"的形式引入 —— 新增任何 `sourceId` 或任何层都只是往同一个平的和里加，SHALL NOT 产生第二个乘区。

临时层到期（整层丢弃）SHALL 使玩家属性恢复到持久层 + 基准值。旧存档缺该附件时以空容器加载（全属性 = 配置基准，向后兼容）。

#### Scenario: 死亡保留

- **WHEN** 属性容器含擦弹率**持久层**贡献的玩家死亡重生
- **THEN** 擦弹率贡献仍在（同最大灵力语义）

#### Scenario: 旧档兼容

- **WHEN** 加载无 `player_attributes` 附件的旧存档
- **THEN** 全部属性取配置基准值，不报错

#### Scenario: 临时层到期恢复

- **WHEN** 临时层改写了生命增幅/灵力强度的变身状态结束
- **THEN** 临时层整体丢弃，两属性回到改写前的持久 + 基准值

#### Scenario: 两类来源在同一层共存

- **WHEN** 玩家同时处于降神变身（写 `spirit_power` 变身来源）且主手武器增幅核带 `spirit_power` 词条（写 `seii_rune_*` 装备来源）
- **THEN** 两者作为不同 `sourceId` 并存于临时层，灵力强度最终值含两者之和；清除其中一个来源不影响另一个

#### Scenario: 装备层不入档

- **WHEN** 带着装备来源贡献的玩家保存并退出游戏
- **THEN** 存档中不含临时层；下次登录时该层由当前装备重算

#### Scenario: 无第二乘区

- **WHEN** 同一属性同时存在持久来源、变身来源、装备来源三条贡献
- **THEN** 最终值为三者与基准的**平的和**再封顶，不做任何逐来源相乘

### Requirement: 分层结算公式
玩家属性 SHALL 按基准与持久/临时各来源贡献的平和计算，再应用属性及当前阶有效上下界；同一属性不同来源 MUST NOT 相乘。阶级贡献 SHALL 仍以grace_tier_N分组；最大灵力、灵力强度 SHALL 保持池字段与阶级台账单一事实来源，不在属性持久层双写。装备来源 SHALL 只提供临时贡献，MUST NOT 改写池台账。百分比与护壁指数 SHALL 使用各自单位；护壁 MUST NOT 钳为0～1百分比。实际边界与原始值 SHALL 可查询，临时来源删除 MUST 只撤销其自身贡献。

#### Scenario: 同属性来源平加
- **WHEN** 基准0、持久10、变身6、装备4，且边界允许20
- **THEN** 原始和与生效值均为20，不逐来源连乘

#### Scenario: 装备不写台账
- **WHEN** 装卸带灵力强度词条的武器
- **THEN** 池台账保持不变，最终值通过临时来源增减

#### Scenario: 护壁使用指数边界
- **WHEN** 高阶护壁值超过1但处于该阶允许范围
- **THEN** 原值作为指数保留，不被百分比工具钳到1

### Requirement: 阶级 roll 贡献源规约
超人类进阶写入属性容器的贡献 SHALL 统一使用命名空间 `grace_tier_N`（N=1..5），每阶级一组；洗练重掷 SHALL 整组替换对应 sourceId 且 MUST NOT 触碰其他组。`sourceId` 为 `command` 的调试写入与 `grace_tier_N` 组 MUST NOT 互相覆盖。最大灵力与灵力强度按单写规约走池字段+阶级台账，属性容器内 MUST NOT 存在其持久副本。

#### Scenario: 组间隔离
- **WHEN** 洗练整组替换 grace_tier_2
- **THEN** grace_tier_1、grace_tier_3 与 command 来源贡献全部保持原值

### Requirement: 原版属性桥
生命增幅与移动速度 SHALL 经原版实体属性（`minecraft:max_health` / `minecraft:movement_speed`）的固定 id modifier 实现，语义为在原版基础上追加（不改基础上限常量），且每次属性变更后 SHALL 整体重算该 modifier 值（幂等，非累加）。生命增幅提高时 SHALL NOT 自动回血，降低时 SHALL 由原版规则钳制当前生命。

#### Scenario: 幂等重算
- **WHEN** 生命增幅贡献从 +10 变为 +20
- **THEN** max_health 的该 modifier 值为 20（不是在上次基础上再 +10）

#### Scenario: 增幅降低钳血
- **WHEN** 玩家当前生命高于降低后的生命增幅上限
- **THEN** 当前生命被钳制到新上限，不崩溃

### Requirement: 效果时长缩放消费
强效延长与韧性 SHALL 在 MobEffect 施加点统一折算：玩家所获正面效果时长 ×(1+强效延长)，负面效果时长 ×(1−韧性)，各自封顶于配置上限。强效延长 SHALL 惠及降神变身的持续时间（本期仅落地属性折算入口，变身本体由后续能力消费）。韧性 MUST NOT 免除效果、仅缩短时长。

#### Scenario: 增益延长
- **WHEN** 拥有 +25% 强效延长的玩家获得一个基础 20 秒的正面效果
- **THEN** 实际时长 25 秒

#### Scenario: 减控不免疫
- **WHEN** 拥有 30% 韧性的玩家被施加减速
- **THEN** 减速仍生效但持续时间 ×0.7，非完全免除

### Requirement: 属性查询调试命令
模组 SHALL 提供玩家侧命令 dump 当前玩家全部 15 键属性的最终值与来源分解（基准/加区/乘区），用于占位期代替属性面板 GUI。

#### Scenario: 打印全表
- **WHEN** 玩家执行属性查询命令
- **THEN** 列出全部属性键的最终值（含被封顶后的生效值）

### Requirement: 变身可改写白名单

属性注册表每键的可改写标记 SHALL 构成**降神变身的改写域白名单**：来源命名空间属于变身（`sourceId` 不带装备前缀）的写入 MUST NOT 改写标记为不可改写的键。**该白名单 MUST NOT 约束其他来源命名空间** —— 装备来源（`seii_rune_*` 等）写入临时层时不受 `isTransformRewritable` 限制，因为"变身可改写"与"装备提供"是两个独立语义，把白名单当作层级别的写权限会混淆二者。

落地形式：临时层写入入口 SHALL 先判定 `sourceId` 是否属于变身命名空间，再决定是否施加白名单校验。

#### Scenario: 越界改写被拒

- **WHEN** 变身来源尝试改写一个标记为不可改写的属性键
- **THEN** 该键改写被忽略，其余白名单内键正常生效

#### Scenario: 装备来源不受白名单约束

- **WHEN** 装备来源（`seii_rune_*`）尝试写入一个 `isTransformRewritable` 为 false 的键（如 `jump`、`melee_damage`、`tenacity`）
- **THEN** 写入成功

#### Scenario: 变身的改写域未被扩大

- **WHEN** 改造后的写入入口收到一个变身来源的写入请求
- **THEN** 白名单校验照旧生效，降神变身的可改写键集合与改造前完全一致

