## ADDED Requirements

### Requirement: 前期 BOSS 主题玩家符卡总表
本变更 SHALL 提供 6 张玩家符卡，每张同时具备**道具形态**与**已学形态**，且每次生效
MUST 给玩家可读的反馈（粒子/光影/音效/actionbar 至少其一）：

| 卡 id | 名称 | 归属 | 类别 |
|---|---|---|---|
| `healing_garden` | 花符『癒しの花園』 | 大妖精 | 恢复 |
| `flower_armor` | 花符『鮮花之鎧』 | 大妖精 | 防御 |
| `demarcation` | 闇符『ディマーケイション』 | 露米娅 | 控制 |
| `spider_web` | 網符『蜘蛛の巣』 | 黑谷山女 | 控制 |
| `plague_repay` | 疫符『病の返し』 | 黑谷山女 | 反制 |
| `fox_servant` | 式神『狐の従者』 | 八云蓝（伪） | 召唤/自动攻击 |

#### Scenario: 双形态齐备
- **WHEN** 检查任一上述符卡
- **THEN** 其可经道具（消耗品）与技能槽（已学）两种途径施放，且效果语义一致

### Requirement: 花符『癒しの花園』
施放 SHALL 在施放点绽放花圃场（半径 4 格）。玩家（自己与队友）处于半径内时 SHALL 每秒
回复生命；离开半径或场地到期即停止。该卡 MUST NOT 回复灵力。回复量 SHALL 随灵力强度
按恢复类指数（α≈0.20）缩放。

#### Scenario: 站圈回血
- **WHEN** 玩家在花圃半径内
- **THEN** 每秒获得回复，且不获得灵力

#### Scenario: 离开停止 / 不回灵
- **WHEN** 玩家离开半径，或场地到期
- **THEN** 回复立即停止，全程灵力值无变化

### Requirement: 花符『鮮花之鎧』
施放 SHALL 为玩家附加花瓣护盾。护盾存续期间，玩家受到的**弹射物伤害**（`AbstractDanmakuProjectile`
或原版 `Projectile`）SHALL 被抵消并消耗一枚花瓣；花瓣耗尽或超时护盾消失。护盾 MUST NOT
抵消近战、环境、摔落或虚空伤害。每次抵消 SHALL 触发可读反馈（花瓣破碎粒子 + 音效 +
actionbar 显示剩余花瓣数）。可挡弹数 SHALL 随灵力强度按防御类指数（α≈0.22）缩放。

#### Scenario: 挡下弹幕
- **WHEN** 携带护盾的玩家被弹幕命中
- **THEN** 伤害被抵消、花瓣数减一、播放碎裂反馈并提示剩余花瓣

#### Scenario: 不挡非弹射物
- **WHEN** 携带护盾的玩家受近战或摔落伤害
- **THEN** 伤害照常结算，花瓣数不变

### Requirement: 闇符『ディマーケイション』
施放 SHALL 以玩家为中心展开黑暗结界（半径与时长按品档固定表）。界内敌对生物（**含 BOSS**）
SHALL 失明——清除其当前目标、丢失仇恨与寻路，且存续期内禁止重新锁定；玩家在界内 SHALL
不可被锁定。玩家视野 SHALL 压暗但自身仍能视物，界内敌人 SHALL 以轮廓显现。
**玩家一旦主动造成伤害（`attacker == player`），本卡 SHALL 立即终止。**

#### Scenario: 对 BOSS 生效
- **WHEN** BOSS 处于黑暗结界内
- **THEN** 其目标被清除并在存续期内无法重新锁定玩家

#### Scenario: 主动攻击终止
- **WHEN** 玩家在结界存续期内对任意目标造成伤害
- **THEN** 结界立即结束，敌人恢复正常锁定

### Requirement: 網符『蜘蛛の巣』
施放 SHALL 朝准星前方 6 格射出一枚丝弹，到位后 SHALL 炸开形成立方体网域（边长与时长为
品档固定表）。网域内**所有实体（含 BOSS）**移动速度 SHALL 变为 ×0.2（−80%）；离开网域
或到期即恢复。移速倍率 MUST NOT 归零。

#### Scenario: 域内减速含 BOSS
- **WHEN** BOSS 位于网域内
- **THEN** 其移动速度被压至 ×0.2，离开后恢复

#### Scenario: 到期恢复
- **WHEN** 网域到期
- **THEN** 域内实体移速恢复原值

### Requirement: 疫符『病の返し』
施放 SHALL 使玩家在持续期内（品档固定表）免疫**新获得**的负面效果（`MobEffectEvent.Added`
取消）；MUST NOT 清除玩家已有的负面、MUST NOT 刷新其时长。期间**对玩家造成伤害的实体**
SHALL 获得 `3 − 该实体当前负面效果数` 个随机 1 分钟负面效果（下限 0），候选池含
Poison / Wither / Weakness / Slowness / MiningFatigue / Blindness / Hunger / Nausea / Darkness。

#### Scenario: 免疫新负面
- **WHEN** 持续期内玩家被施加新的负面效果
- **THEN** 该效果被取消，玩家已有负面不受影响

#### Scenario: 回敬伤害者
- **WHEN** 某实体在持续期内对玩家造成伤害，且其当前有 1 个负面效果
- **THEN** 该实体获得 2 个随机 1 分钟负面效果

### Requirement: 式神『狐の従者』
施放 SHALL 召唤 1 只狐火式神环绕玩家，在索敌半径内自动锁定最近敌人并攻击。索敌半径与
持续时间 SHALL 按品档固定表；单发伤害 SHALL 随灵力强度按伤害类指数（α≈0.90）缩放，
并再乘 `(1 + spell_amp)`；攻击间隔 SHALL 固定。被锁定/攻击的实体 SHALL 显示蓝紫色火焰粒子。
索敌半径外的目标 MUST NOT 被锁定。

#### Scenario: 自动索敌攻击
- **WHEN** 索敌半径内存在敌人
- **THEN** 式神锁定最近者并按间隔发射狐火

#### Scenario: 限距
- **WHEN** 最近的敌人超出索敌半径
- **THEN** 式神不锁定、不攻击
