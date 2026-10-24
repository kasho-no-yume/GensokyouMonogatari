## ADDED Requirements

### Requirement: 身份与命名

本 mod SHALL 提供露米娅（`gensokyou:rumia` / `RumiaEntity`）作为**T1 最终 BOSS**。

露米娅 SHALL 以 `AbstractTouhouBoss` + `GeoEntity` 实现，并通过正式程序内容 id `rumia` 接入 `BossProgramCards`；MUST NOT 使用 `UnmigratedTouhouBoss` 旧后端。

旧占位 `gensokyou:nomen_mask` / `NomenMaskEntity` / 傩神楽面「無終」/ 5 张旧卡 SHALL 被删除，MUST NOT 以 id alias、隐藏实体或旧语言键保留。

露米娅的中英实体名 SHALL 为「露米娅」/「Rumia」。

#### Scenario: 注册名与类名

- **WHEN** 读取实体注册表与实体类
- **THEN** 取得 `gensokyou:rumia` / `RumiaEntity`，且不存在 `gensokyou:nomen_mask`

#### Scenario: 旧 id 不再解析

- **WHEN** 用 `gensokyou:nomen_mask` 查询实体类型
- **THEN** 查询失败，MUST NOT 回落为露米娅或任何隐藏 alias

#### Scenario: 显示名无裸 id

- **WHEN** 打开创造标签、JEI 或血条并切换中英文
- **THEN** 实体名显示「露米娅」/「Rumia」，MUST NOT 出现裸 id 或「傩神楽面」

### Requirement: 四张官方符卡

露米娅的符卡表 SHALL 由 **4 张**构成，起始血量占比依次为 `1.00 / 0.75 / 0.50 / 0.25`；每张符卡 SHALL 携带名称、血量区间与 1~5 条并发轨道。

四张卡 SHALL 使用下述官方卡名与母题：

| 序 | 符卡 | 官方出处 | 母题 |
|---|---|---|---|
| 1 | 月符「月光射线」/ Moon Sign "Moonlight Ray" | 东方红魔乡 Stage 1，系列第一张符卡 | 光 / 月光 |
| 2 | 夜符「夜鸟」/ Night Sign "Night Bird" | 东方红魔乡 Stage 1 Boss，系列第一张 Boss 符卡 | 夜 / 双翼 |
| 3 | 暗符「境界线」/ Dark Sign "Demarcation" | 东方红魔乡 Stage 1 Hard/Lunatic | 光与暗的界线 |
| 4 | 暗符「月之暗面」/ Dark Sign "Dark Side of the Moon" | 东方红魔乡 Extra | 月背 / 终局暗面 |

`夜符「ミッドナイトバード」` SHALL NOT 进入本表；它是 Night Bird 的 Extra 变体，母题重复。

每张符卡 SHALL 通过程序内容的 R1/R2/R3 可读性契约；主威胁 MUST 朝玩家，MUST NOT 依赖「序·破·結 / 缺段」形式化装置。

游戏内表现由用户后续设计。首版 SHALL 以明确标记 `TODO: user-supplied performance` 的最小占位程序登记；占位程序 MUST NOT 被当作最终内容验收，Deck MUST NOT 为空，正式召唤配方 MUST NOT 在占位程序缺失时上线。

#### Scenario: 四张卡与阈值

- **WHEN** 读取露米娅的正式牌组
- **THEN** 得到 4 张卡，起始占比依次为 1.00 / 0.75 / 0.50 / 0.25

#### Scenario: 官方卡名与语言键

- **WHEN** 读取第 1~4 张卡的名称
- **THEN** 分别为月光射线 / 夜鸟 / 境界线 / 月之暗面，语言键为 `spellcard.gensokyou.rumia.1..4`

#### Scenario: 占位程序可辨识

- **WHEN** 审查首版 `RumiaPrograms`
- **THEN** 4 张卡均为最小占位程序，且代码、manifest 与设计表均明确标记待用户替换

#### Scenario: 无旧五卡

- **WHEN** 读取露米娅的符卡表
- **THEN** 不存在「拍 / 連拍 / 乱拍 / 急拍 / 無終」或 `spellcard.gensokyou.nomen_mask.*`

#### Scenario: 通过可读性校验

- **WHEN** 以程序内容契约校验 4 张卡
- **THEN** 不出现 R1/R2/R3 违规，且不存在「缺段」派生约束

### Requirement: GeckoLib 正式模型

露米娅 SHALL 由 GeckoLib（Bedrock 格式 `geo` / `animation`）渲染，使用自有命名空间资源：

- `assets/gensokyou/geo/entity/rumia.geo.json`
- `assets/gensokyou/animations/entity/rumia.animation.json`
- `assets/gensokyou/textures/entity/rumia.png`

其渲染 MUST NOT 走 `RemnantBossRenderer` 的 billboard 或 `SkinMobRenderer` 占位通路。

模型 SHALL 使用 `idle` / `walk` / `cast` 三条动画；`cast` SHALL 在符卡切换时触发一次。模型缩放与 hitbox SHALL 按正式模型实际尺寸校准，MUST NOT 沿用 `nomen_mask` 的占位尺寸。

#### Scenario: 走正式模型

- **WHEN** 露米娅被渲染
- **THEN** 使用 `rumia.*` 的 GeckoLib 模型、动画与贴图，MUST NOT 使用 billboard 残影或 Alex 人形皮

#### Scenario: 切卡触发 cast

- **WHEN** 露米娅切换到新符卡
- **THEN** 播放一次 `animation.rumia.cast`，并保留基类的符光爆发

#### Scenario: 不走残影通路

- **WHEN** 审查渲染注册
- **THEN** `ModEntityTypes.RUMIA` 注册到 `RumiaGeoRenderer`，MUST NOT 注册到 `RemnantBossRenderer`

#### Scenario: hitbox 与模型相称

- **WHEN** 玩家靠近并擦过露米娅
- **THEN** 碰撞与命中按校准后的 hitbox 结算，MUST NOT 出现「视觉上很大但打不到」或「模型穿墙」

### Requirement: T1 最终数值

露米娅的自身品阶 SHALL 为 1（`bossTier = 1`），数值参照阶 SHALL 为 1（`referenceTier = 1`）。

其战斗秒数 SHALL 为 **240**，挨弹数 SHALL 为 **7**；二者 MUST 来自 `BalanceNumbers` 的确定常量，MUST NOT 硬编在实体类里或施加入睡随机。

露米娅 SHALL 是 T1 强度上限：其有效生命与单发基准 SHALL 高于大妖精与黑谷山女，且 SHALL 低于 T2 的八云蓝。

露米娅 MUST NOT 作为 1→2 的进度门；`nightfall_core` SHALL NOT 被新增为任何 T2 配方或 T2 仪式门槛的必需料。

#### Scenario: 数值定位

- **WHEN** 读取露米娅的品阶、参照阶、秒数与挨弹数
- **THEN** 依次为 1 / 1 / 240 / 7

#### Scenario: T1 上限

- **WHEN** 对比大妖精、黑谷山女、露米娅与八云蓝的有效生命/单发基准
- **THEN** 露米娅高于前两只 T1、低于八云蓝

#### Scenario: 不做进度门

- **WHEN** 审查 T2 召唤与 T2 仪式配方
- **THEN** 不存在强制消费 `nightfall_core` 的新门槛；露米娅可被跳过

#### Scenario: 无出生随机

- **WHEN** 先后召唤露米娅两次
- **THEN** 两次有效生命完全相同，不出现 ±25% 出生 roll

### Requirement: L1 召唤配方

露米娅 SHALL 由百鬼夜行仪式的 **L1** 配方召唤，配方名 `hyakki_boss_rumia`，`effect` 为 `hyakki:rumia`。

配方 SHALL 为 `spellcard_star + fairy_nectar + earth_spider_silk + ritual_stone_1`，`Σcount = 4`，`spCost = 600000`，`minTier = 1`，`minPlayerTier = 0`，`mode = activation`，`match = max`。

配方 MUST NOT 使用 `sukima_fragment`（T2 产出）或 `nightfall_core`（露米娅自身掉落）；MUST NOT 与既有召唤配方产生可同时满足的摆法。

#### Scenario: L1 四料匹配

- **WHEN** 玩家在 L1 祭坛摆齐四件原料
- **THEN** 匹配 `hyakki_boss_rumia`，扣料并进入召唤会话

#### Scenario: 容量与 spCost

- **WHEN** 校验配方
- **THEN** `Σcount = 4 ≤ L1 祭坛台数 4`，且 `spCost` 为 10 的倍数

#### Scenario: 钥匙唯一

- **WHEN** 审查与其它百鬼夜行配方的互斥性
- **THEN** 不存在一份摆法同时满足露米娅与其它 BOSS 的配方

#### Scenario: 不用 T2 材料

- **WHEN** 审查配方原料
- **THEN** 不出现 `sukima_fragment` 或 `nightfall_core`

### Requirement: 掉落与信物

露米娅死亡时 SHALL 掉落 6 个碎符卡星、24~48  yen，并 SHALL 保底掉落其主题信物 `gensokyou:nightfall_core`。

露米娅 MUST NOT 额外掉落整颗 `gensokyou:spellcard_star`——该物品是她的召唤钥匙，掉落整颗会退还钥匙。

其主题卡代表 SHALL 为 `gensokyou:demarcation`（低概率直掉，受 config 控制）。经验奖励 SHALL 为 300。

#### Scenario: 碎星与信物

- **WHEN** 玩家击杀露米娅
- **THEN** 掉落 6 个碎符卡星与 config 数量的 `nightfall_core`

#### Scenario: 不退还钥匙

- **WHEN** 审查露米娅的死亡掉落
- **THEN** 不出现整颗 `spellcard_star`

#### Scenario: 主题卡代表

- **WHEN** 审查露米娅的主题卡归属
- **THEN** 为 `gensokyou:demarcation`

### Requirement: 旧占位清理

旧傩面 / 無終内容 SHALL 从 active 代码与 active 文档中删除：

- `NomenMaskEntity`、`ModEntityTypes.NOMEN_MASK`、`BossCards.nomenMask()` / `NOMEN_PALETTE`
- 5 张旧卡及其 lang 键、`nomen_mask` 实体标签、`hyakki:nomen` effect、`boss_residual_codex_nomen.json`
- `RemnantBossRenderer` 与 `/danmaku preview` 的 nomen 分支
- 测试与 manifest 中的 nomen fixture / excluded 计数

`UnmigratedTouhouBoss` SHALL 保留给 `BloodBarProbeBoss` 等调试探针；旧 `BossCards` 的大妖精 / 山女 / 蓝表 SHALL 保留给旧后端对照测试。

`openspec/changes/archive/**` 中的历史记录 SHALL NOT 被删除或重写；active spec / docs / skills 更新为「露米娅 4 卡已接入」。

#### Scenario: active 代码无 nomen

- **WHEN** 在 `src/main` 与 `src/test` 中搜索 `nomen_mask` / `NomenMask` / `NOMEN_PALETTE`
- **THEN** 只允许出现在 archive、历史文档或明确标记的历史对照中，active 注册/内容/测试 MUST NOT 引用

#### Scenario: 标签与典籍

- **WHEN** 审查 `#gensokyou:bosses` 标签与残魂典籍配方
- **THEN** 标签包含 `gensokyou:rumia`、不包含 `gensokyou:nomen_mask`；典籍配方指向 `gensokyou:rumia`

#### Scenario: 测试计数更新

- **WHEN** 运行内容与 manifest 测试
- **THEN** 正式卡为 19（3+4+8+4），legacy 表为 15（3+4+8），excluded 为 0

#### Scenario: archive 保留

- **WHEN** 审查旧 change
- **THEN** `openspec/changes/archive/**` 中的傩面记录仍在，未被本 change 删除或改写
