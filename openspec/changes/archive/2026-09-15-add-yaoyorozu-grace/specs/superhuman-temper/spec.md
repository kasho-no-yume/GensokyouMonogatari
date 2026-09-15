# superhuman-temper Spec Delta

## ADDED Requirements

### Requirement: 玩家超人类阶级
每名玩家 SHALL 持有超人类阶级状态（0-5，复用 `SpiritPowerData.temperLevel` 字段），随存档持久化、死亡保留、变更时 SHALL 同步至客户端。阶级 MUST NOT 超过 5，且 MUST NOT 通过任何途径回退。阶级提升的唯一途径为八百万神恩仪式的进阶配方成功执行。

#### Scenario: 阶级持久与同步
- **WHEN** 阶级为 2 的玩家退出重进或死亡重生
- **THEN** 阶级仍为 2，HUD/客户端读到值 2

#### Scenario: 上限封顶
- **WHEN** 玩家已为 5 阶
- **THEN** 任何 5 阶以上进阶配方不可对其执行（启动校验拒绝），阶级保持 5

### Requirement: 凡人零灵力状态
阶级 0 的玩家 SHALL 处于"凡人"状态：灵力池上限、当前值、灵力强度均为 0；符卡技能槽数为 0；弹幕减免为 0。凡人 MUST NOT 能通过任何途径持有灵力池数值（回灵 tick、汲取、道具注灵均不生效），HUD 灵力条与技能槽排 SHALL 整体隐藏；所有消耗玩家灵力的系统（弹幕武器、符卡施放、结界引爆）对凡人在校验上 SHALL 一律拒绝且给出"需进阶"提示。

#### Scenario: 新玩家即凡人
- **WHEN** 新玩家首次进入世界
- **THEN** 灵力池 0/0、已学卡 0、槽位 0，HUD 无灵力条与技能槽

#### Scenario: 凡人施放拦截
- **WHEN** 阶级 0 玩家按符卡键或尝试武器射击
- **THEN** 服务端校验拒绝、无任何消耗、提示需完成首次进阶

### Requirement: 指数属性表与随机 roll
每次成功进阶（N-1→N 阶），系统 SHALL 按 config `grace` 段的该阶级数值表为全部属性 roll 随机值：核心四键（最大灵力、灵力强度、回复速率、弹幕减免）roll 区间为基准 ±15%，其余键 ±25~30%；数值 SHALL 指数递增（相邻阶级倍率约 2.5~3.3）。roll 结果写入位置 SHALL 遵循单写规约：最大灵力与灵力强度写灵力池字段并按阶级记入台账，其余键写属性容器 permanent 层 `sourceId = "grace_tier_N"`。

#### Scenario: 进阶 roll 生效
- **WHEN** 玩家完成 1 阶进阶配方
- **THEN** 池上限与灵力强度变为 1 阶级表 roll 值，12 项属性出现 `grace_tier_1` 贡献，最终值=基准+贡献合并并封顶

#### Scenario: 同表两次 roll 不同
- **WHEN** 两名玩家先后完成 1 阶进阶
- **THEN** 其属性 roll 值独立随机，允许落在区间任意位置

### Requirement: 阶级洗练的整组重掷
洗练阶级 N 的配方 SHALL 仅重掷阶级 N 的贡献组（`grace_tier_N` 全部键 + 池台账中阶级 N 份额并重算池字段），阶级 >N 与 <N 的贡献 MUST NOT 变动。

#### Scenario: 低阶洗练不动高阶
- **WHEN** 3 阶玩家洗练 1 阶配方并采纳
- **THEN** 仅 1 阶贡献组变化，2、3 阶贡献保持原值

### Requirement: 旧档迁移
加载旧存档 SHALL 执行一次性迁移：阶级 0 的玩家池 max/current/spirit_damage 清 0；阶级 >0 的玩家按新表重 roll 并覆盖池台账与贡献组；缺失 grace 字段的存档按空台账初始化，MUST NOT 报错。旧版线性淬炼 config 项 SHALL 从配置移除并被 `grace` 段取代。

#### Scenario: 凡人化迁移
- **WHEN** 加载一名 temperLevel=0、池 100/100 的旧档玩家
- **THEN** 其池变为 0/0、灵力强度 0，成为凡人

### Requirement: 数值全部可配置
阶级属性表（含 roll 区间）、飞行费率表、演出时长、PAYING 受灵速率 SHALL 全部位于 config `grace` 段，reload/重启生效；MUST NOT 有硬编码数值散落行为代码。

#### Scenario: 调表生效
- **WHEN** 管理员将 1 阶最大灵力基准从 200 改为 300 并重载配置
- **THEN** 后续新 roll 使用 300 基准（已有玩家不受追溯影响）
