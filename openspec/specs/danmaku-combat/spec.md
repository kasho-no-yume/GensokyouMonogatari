# danmaku-combat Specification

## Purpose
TBD - created by archiving change phase-a-entry-loop. Update Purpose after archive.
## Requirements
### Requirement: 弹幕伤害类型
模组 SHALL 定义数据驱动伤害类型 `gensokyou:danmaku`，经 `minecraft:bypasses_armor` 标签无视护甲；代码侧 SHALL 以 ResourceKey + Holder 构造 DamageSource，MUST NOT 继承 DamageSource。

#### Scenario: 无视护甲
- **WHEN** 全套护甲的生物被弹幕命中
- **THEN** 伤害不因护甲值衰减

#### Scenario: 来源归属
- **WHEN** 弹幕击杀生物
- **THEN** 死亡消息与掉落归属指向弹幕来源实体

### Requirement: 弹幕投射物
模组 SHALL 提供弹幕投射物实体：无重力直线飞行；伤害值持久化到 NBT（owner 由父类持久化）；命中非来源实体造成 NBT 伤害并消失，命中方块消失；命中判定仅服务端结算，双端行为一致。

#### Scenario: 存档重载一致
- **WHEN** 飞行中的弹幕随存档保存后重载
- **THEN** 弹幕存在且伤害值、来源归属不变

#### Scenario: 命中行为
- **WHEN** 弹幕分别命中实体与方块
- **THEN** 实体受伤且弹幕消失 / 方块无伤弹幕消失；客户端不重复结算

### Requirement: 弹幕护盾效果
模组 SHALL 注册状态效果 danmakuProtect：持有者所受 danmaku 伤害乘以 (9-等级)/10，等级 ≥10 时完全免疫；对非 danmaku 伤害无效。系数从配置读取。

#### Scenario: 减伤与免疫
- **WHEN** 持有 III 级护盾的生物受到 100 点弹幕伤害 / 持有 XI 级护盾时受任意弹幕伤害
- **THEN** 前者实际 60 点 / 后者 0 点

#### Scenario: 不影响普伤
- **WHEN** 持有护盾的生物被剑攻击
- **THEN** 受到全额伤害

### Requirement: 玩家受弹属性管线
当且仅当 受害者为玩家 且 伤害类型为 `gensokyou:danmaku` 时，SHALL 按固定顺序结算玩家防御属性：
1. **擦弹率**：服务端随机 roll，命中则该次伤害为 0 并终止管线；
2. **指数减免（灵力护壁）**：`受伤 × 2^(−P) × danmakuProtect 效果系数`，其中 P 为该玩家"弹幕护壁"属性（无量纲指数，从玩家属性套件读取）；`2^(−P)` 恒大于 0，为**指数级**减免；
3. **弹幕抵抗阶段退役**：不再有 flat 抵抗与"90% 全局封顶"；因 `2^(−P)` 永不归零，无需封顶防免疫链。
mu_power 等既有乘区顺序 SHALL 保持不变（先于本管线）。本管线 MUST NOT 改变非玩家实体所受弹幕结算（既有"弹幕护盾效果"场景行为回归不变）。属性最终值 SHALL 从玩家属性套件读取，MUST NOT 另立存储。

#### Scenario: 擦弹免疫
- **WHEN** 擦弹率 30% 的玩家受到一发弹幕
- **THEN** 约 30% 的次数该次伤害为 0，其余次数继续走指数减免

#### Scenario: 指数减免与护盾乘算
- **WHEN** 护壁指数 P=1（×0.5）且持有 III 级 danmakuProtect 的玩家受到 100 点弹幕伤害
- **THEN** 实际伤害为 100 × 0.5 × 0.6 = 30 点

#### Scenario: 越靠近高护壁收益指数增长
- **WHEN** 玩家护壁指数从 P=7 提升到 P=8
- **THEN** 受到的同阶弹幕伤害减半（×1/128 → ×1/256），无需逼近 100% 百分比

#### Scenario: 高面板不免疫
- **WHEN** 5 阶玩家（护壁 ÷388）受到跨阶（×10 及以上）怪物弹幕
- **THEN** 仍受到可观伤害，`2^(−P)` 不产生绝对免疫

#### Scenario: 非玩家不受影响
- **WHEN** 妖精（非玩家）受同一弹幕命中
- **THEN** 仅走既有效果结算，玩家防御属性不参与

### Requirement: 弹幕伤害来源归属修正
玩家/生物发射的弹幕实体结算伤害时，DamageSource 的 directEntity SHALL 为弹幕实体本身、causingEntity 为发射者（原版投射物惯例；旧实现误将受害者作 directEntity）。击杀掉落与死亡消息归属（读 causingEntity）行为 MUST NOT 改变。该归属同时是灵力汲取"仅武器弹生效"判定的唯一依据（汲取 MUST NOT 作用于符卡等其他玩家弹幕来源）。

#### Scenario: 归属不变
- **WHEN** 玩家主武器弹幕击杀僵尸
- **THEN** 死亡消息与掉落仍归属发射者玩家

#### Scenario: 汲取据弹本体区分来源
- **WHEN** 同一玩家的主武器弹幕与冰符卡弹幕分别命中目标
- **THEN** 仅主武器弹（发射时标记）触发灵力汲取，符卡弹不触发

### Requirement: 弹幕破盾
当弹幕（`gensokyou:danmaku` 伤害）命中正在举盾的玩家时，该击 SHALL 照常被盾牌挡下，随后 MUST 禁用该玩家的盾牌一段配置时长（默认 5 秒 / 100 tick），并停止其举盾动作；禁用时长 MUST 从配置读取。非弹幕伤害 MUST NOT 触发此破盾。

#### Scenario: 弹幕破盾
- **WHEN** 玩家举盾格挡一发 danmaku 弹幕
- **THEN** 伤害被挡下，且玩家盾牌进入 5 秒冷却、举盾被打断

#### Scenario: 非弹幕不破盾
- **WHEN** 玩家举盾格挡普通近战或箭矢
- **THEN** 盾牌正常格挡且不被禁用

