## ADDED Requirements

### Requirement: NPC 无敌性
东方 NPC SHALL 对一切伤害来源免疫（怪物、环境、摔落、岩浆、投射物、`/kill`、虚空等），血量永不减少；SHALL 不受重力、不可被拴绳、不可被推动、不会自然消失。hack 级绕过手段不要求根治，但 SHALL 由恢复流程兜底。

#### Scenario: 环境与怪物伤害无效
- **WHEN** NPC 受到岩浆、摔落、怪物攻击等非玩家来源伤害
- **THEN** 不产生任何伤害结算，NPC 状态与位置不变

#### Scenario: 指令与虚空静默拒绝
- **WHEN** NPC 被 `/kill` 或坠入虚空
- **THEN** 伤害被完全拒绝，NPC 不消失、不计数、无演出

### Requirement: 恢复流程（将死/真死）
NPC 受到玩家一击致死（amount ≥ 满血，即「将死」）或被绕过手段真正杀死（「真死」）时 SHALL 不进入原版死亡管线：无死亡动画、无掉落、无经验；SHALL 立即移除自身、在原地生成大量紫色粒子、并在**原坐标**刷新一个属性一致（含自定义名）的复制体。

#### Scenario: 玩家一击致死演出
- **WHEN** 玩家单次攻击伤害达到 NPC 满血
- **THEN** 原地爆发紫色粒子，NPC 立即消失，1 秒内原坐标出现一模一样的复制体，无掉落物

#### Scenario: 非致死攻击无演出
- **WHEN** 玩家攻击伤害低于 NPC 满血
- **THEN** 伤害被拒绝，无粒子、无复制体、不计数

### Requirement: 恶意致死计数与逐出惩罚
仅当 NPC「将死或真死」且能归因到玩家时（直接攻击或弹射物 owner），该玩家的计数 SHALL +1 并跨死亡持久；非致死攻击 SHALL NOT 计数。计数达到配置阈值（默认 3）时，若玩家在 `gensokyou:gensokyo` 维度 SHALL 被传送至主世界远端随机点（x/z ∈ [50000, 150000] 均匀随机，y 可配置，默认 500），计数清零；不足阈值时 SHALL 给予剩余次数警告。

#### Scenario: 三振逐出
- **WHEN** 同一玩家第 3 次恶意致死 NPC 且身处幻想乡维度
- **THEN** 玩家被传送至主世界远端随机点，收到逐出提示，计数清零

#### Scenario: 跨死亡持久
- **WHEN** 计数为 2 的玩家死亡重生后再次恶意致死 NPC
- **THEN** 计数累加至 3 并触发逐出

#### Scenario: 警告提示
- **WHEN** 玩家第 1 或第 2 次恶意致死 NPC
- **THEN** 收到含剩余次数的警告信息

### Requirement: 持久化与无移动 AI
NPC 基类 SHALL 不注册任何移动类 AI Goal（允许视线类 Goal），SHALL 关闭重力，SHALL 标记持久化（`removeWhenFarAway` 为 false），存档重载后状态保持。

#### Scenario: 长期驻留
- **WHEN** NPC 所在区块卸载后重新加载
- **THEN** NPC 仍在原位，无自然消失、无重力位移

### Requirement: 锚点驻留
NPC SHALL 永远驻留于其锚点坐标（默认 = 刷出位置，NBT 持久）：一切速度类位移（水流、岩浆流、爆炸击退、实体碰撞、活塞）与位置类写入（`setPos`/`moveTo`/代码式传送）SHALL 被钉回锚点；仅 `setAnchorPos` 接口 SHALL 能合法移位。

#### Scenario: 水流与击退无效
- **WHEN** NPC 处于水流、爆炸或实体碰撞中
- **THEN** NPC 位置不变（不可被持续推移；不可覆写路径造成的漂移至多 1 tick 内被拉回）

#### Scenario: 接口移位
- **WHEN** 通过 `setAnchorPos` 更新锚点
- **THEN** NPC 落位至新锚点并以其为永久驻留点

### Requirement: 交互入口分发
右键 NPC 时 SHALL 按子类配置打开交易页面或对话页面；交易 SHALL 复用原版 Merchant 交易界面。

#### Scenario: 打开交易
- **WHEN** 右键一个配置为交易型的 NPC
- **THEN** 打开原版风格交易界面，可正常买卖

#### Scenario: 打开对话
- **WHEN** 右键一个配置为对话型的 NPC
- **THEN** 打开对话界面
