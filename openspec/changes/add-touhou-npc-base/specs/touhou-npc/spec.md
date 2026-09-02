## ADDED Requirements

### Requirement: NPC 免伤性
东方 NPC SHALL 对玩家以外的一切伤害来源完全免疫（怪物、环境、摔落、岩浆、投射物等），血量永不减少；SHALL 不可被拴绳、不可被推动、不会自然消失。

#### Scenario: 环境与怪物伤害无效
- **WHEN** NPC 受到岩浆、摔落、怪物攻击等非玩家来源伤害
- **THEN** 不产生任何伤害结算，NPC 状态与位置不变

#### Scenario: 非玩家弹射物无效
- **WHEN** 骷髅箭矢等非玩家弹射物命中 NPC
- **THEN** 伤害被完全拒绝

### Requirement: 非正常死亡流程
NPC 受到致死来源（玩家攻击，或虚空、`/kill` 等绕过源）时 SHALL 不进入原版死亡管线：无死亡动画、无掉落、无经验；SHALL 立即移除自身、在原地生成大量紫色粒子、并在旁边刷新一个属性一致（含自定义名）的复制体。

#### Scenario: 玩家攻击致死演出
- **WHEN** 玩家攻击导致 NPC「死亡」
- **THEN** 原地爆发紫色粒子，NPC 立即消失，1 秒内旁边出现一模一样的复制体，无掉落物

#### Scenario: 指令与虚空死亡
- **WHEN** NPC 被 `/kill` 或坠入虚空
- **THEN** 同样触发粒子与复制体流程，但不归因任何玩家

### Requirement: 击杀计数与逐出惩罚
能归因到玩家时（直接攻击或弹射物 owner），该玩家的「非正常杀死 NPC」计数 SHALL +1 并跨死亡持久；计数达到配置阈值（默认 3）时，若玩家在 `gensokyou:gensokyo` 维度 SHALL 被传送回主世界出生点，计数清零；不足阈值时 SHALL 给予剩余次数警告。

#### Scenario: 三振逐出
- **WHEN** 同一玩家第 3 次非正常杀死 NPC 且身处幻想乡维度
- **THEN** 玩家被传送至主世界出生点，收到逐出提示，计数清零

#### Scenario: 跨死亡持久
- **WHEN** 计数为 2 的玩家死亡重生后再次非正常杀死 NPC
- **THEN** 计数累加至 3 并触发逐出

#### Scenario: 警告提示
- **WHEN** 玩家第 1 或第 2 次非正常杀死 NPC
- **THEN** 收到含剩余次数的警告信息

### Requirement: 持久化与无移动 AI
NPC 基类 SHALL 不注册任何移动类 AI Goal（允许视线类 Goal），SHALL 标记持久化（`removeWhenFarAway` 为 false），存档重载后状态保持。

#### Scenario: 长期驻留
- **WHEN** NPC 所在区块卸载后重新加载
- **THEN** NPC 仍在原位，无自然消失

### Requirement: 交互入口分发
右键 NPC 时 SHALL 按子类配置打开交易页面或对话页面；交易 SHALL 复用原版 Merchant 交易界面。

#### Scenario: 打开交易
- **WHEN** 右键一个配置为交易型的 NPC
- **THEN** 打开原版风格交易界面，可正常买卖

#### Scenario: 打开对话
- **WHEN** 右键一个配置为对话型的 NPC
- **THEN** 打开对话界面
