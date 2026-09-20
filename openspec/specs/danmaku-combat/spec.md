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
2. **百分比减免**：`(1 − 弹幕减免) × danmakuProtect 效果系数` 乘算合并，合并后的总百分比减免 SHALL 封顶于配置上限（默认 90%）；
3. **弹幕抵抗**：再减去固定值，允许减至 0。
mu_power 等既有乘区顺序 SHALL 保持不变（先于本管线）。本管线 MUST NOT 改变非玩家实体所受弹幕结算（既有"弹幕护盾效果"场景行为回归不变）。属性最终值 SHALL 从玩家属性套件读取，MUST NOT 另立存储。

#### Scenario: 擦弹免疫
- **WHEN** 擦弹率 30% 的玩家受到一发弹幕
- **THEN** 约 30% 的次数该次伤害为 0，其余次数继续走减免与抵抗

#### Scenario: 减免与护盾乘算
- **WHEN** 弹幕减免 50% 且持有 III 级 danmakuProtect 的玩家受到 100 点弹幕伤害
- **THEN** 百分比阶段后为 100 × 0.5 × 0.6 = 30 点，再减弹幕抵抗固定值

#### Scenario: 抵抗减至零
- **WHEN** 弹幕抵抗 20 的玩家受到经百分比阶段后剩 15 点的弹幕伤害
- **THEN** 最终伤害为 0（固定值减穿允许，无最低保留伤害）

#### Scenario: 全局封顶防免疫链
- **WHEN** 减免%与高等级护盾合并出的总减免超过配置封顶（默认 90%）
- **THEN** 按 90% 生效，剩余 10% 伤害继续进抵抗阶段

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

