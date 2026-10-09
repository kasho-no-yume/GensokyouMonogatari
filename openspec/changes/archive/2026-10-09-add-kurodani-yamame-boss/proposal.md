## Why

黑谷山女（土蜘蛛）的正式模型已在 Blockbench 完成（`F:/blockbench/kurodani/`，Bedrock 1.12
几何 + 1.8 动画），可直接进现有 GeckoLib 管线。它替换掉现存的占位残影「鬼蛛」——后者
没有正式模型，只是同一套 billboard/Skin 占位。

同时，「序·破·結 / 缺段」是**模型缺失时期的人造脚手架**：当时三只残影共用同一套占位外观，
需要一个机制装置把它们区分开。随着真角色模型陆续到位（大妖精、山女），这个装置已失其用途；
它又非东方原作的设定，留着只会让后来者困惑（本仓库已出现「看不懂这个机制」的反馈）。
故本次一并撤销缺段机制。

## What Changes

- **新增黑谷山女 BOSS**（T1，接在大妖精之后）：GeckoLib 真模型；**4 张签名符卡**
  （网 ×2 + 瘴 ×2），全部是可用瞄准轨的普通符卡；继承鬼蛛的 T1 进度位——
  保底掉隙间碎片 + 碎符卡星。
- **BREAKING（内容）删除鬼蛛（`kuzumono`）**：实体类、注册项、残影渲染绑定、召唤
  effect、召唤配方、boss tag、lang、config、掉落全部移除。旧存档中已降临的鬼蛛实体将失效
  （按用户决定：新 id `yamame`，不留 `kuzumono` 别名）。
- **BREAKING（规格）撤销「缺段」机制**：
  - 删除 `remnant-touhou-bosses` 的「残影的身份与缺段约束」requirement；
  - 弃用 `TrackLint.hasNoAimedTrack` / `TrackLint.allTracksEndless` 及其断言与测试；
  - 狐火、傩神楽面改为**普通召唤 BOSS**——`Track.terminates/endless` 作为通用能力保留，
    但不再「强制」傩的所有轨道无限（其现有 `.endless()` 调用可保留为风格，不构成契约）。
- **召唤配方**：新增 `hyakki_boss_yamame`（占位摆法：钥匙沿用 `broken_spell_card_star` +
  3 种素材，Σcount=4 ≤ L1 祭品台数 4）。
- **后续规划（不在本变更内，仅记录）**：狐火将替换为八云蓝残影，傩神楽面将替换为露米娅；
  规格与名单应保持可增量替换。

## Capabilities

### New Capabilities
- `kurodani-yamame`: 黑谷山女 BOSS 的身份、四张签名符卡（几何 × 行为编排）、调色盘、
  默认攻击、数值（T1 秒带/挨弹带）、掉落、GeckoLib 渲染接入。

### Modified Capabilities
- `remnant-touhou-bosses`: 召唤 BOSS 名单中「鬼蛛」改为「黑谷山女」；删除「残影的身份与
  缺段约束」整条 requirement；相应更新符卡数分档、数值表、残影渲染条款中的对象。
- `boss-summon-effect`: 「召唤存活规则」场景中「鬼蛛额外保底掉隙间碎片」改为「黑谷山女额外
  保底掉隙间碎片」。

## Impact

**代码**
- `entity/KuzumonoEntity` → `entity/YamameEntity`（继承 `AbstractTouhouBoss`）
- `registry/ModEntityTypes`：`KUZUMONO` → `YAMAME`（新 id `yamame`）
- `danmaku/track/BossCards`：新增 `yamame()` + `YAMAME_PALETTE`；移除 `kuzumono()` +
  `KUZUMONO_PALETTE`；`all()` 更新
- `danmaku/track/TrackLint`：移除 `hasNoAimedTrack` / `allTracksEndless`
- `client/renderer/RemnantBossRenderer`：移除 kuzumono 绑定（山女不走残影通路）
- 新增 `client/model/YamameGeoModel` + `client/renderer/YamameGeoRenderer`
- `client/GensokyouClient`：注册山女渲染
- `ritual/behavior/SummonBossEffects`：`hyakki:kuzumono` → `hyakki:yamame`
- `config/GensokyouConfig`：`KUZUMONO_BOSS_*` → `YAMAME_BOSS_*`
- `command/DanmakuPreview`：预览分支 `kuzumono` → `yamame`

**资源 / 数据**
- `assets/gensokyou/geo/entity/kurodani.geo.json`、`animations/entity/kurodani.animation.json`、
  `textures/entity/kurodani.png`（自 Blockbench 搬运）
- `assets/gensokyou/lang/{zh_cn,en_us}.json`：`entity.gensokyou.yamame`、`spellcard.gensokyou.yamame.1..4`、
  JEI 配方/效果键
- `data/gensokyou/ritual_recipes/hyakki_yagyo_circle.json`：配方改名 + effect 改 `hyakki:yamame`
- `data/gensokyou/tags/entity_type/bosses.json`：`kuzumono` → `yamame`
- `docs/asset-placeholder-list.md`：山女从占位转正式

**测试**
- `BossCardLintTest`：删 `kuzumonoHasNoAimedTrack`；符卡数/表改用山女；删缺段相关断言
- 相关遍历 `BossCards.all()` 的测试（`TrackRepeatTimingTest` / `SpellCardThresholdTest`）自动覆盖

**风险点**
- `kurodani.png` 仅约 1.5 KB（128×128），相对预览完成度偏小，接入前须确认是完整贴图而非占位色块。
- `kurodani.geo.json` 约 407 KB，须确认 bone/cube 数与渲染开销；必要时评估是否需 LOD 或简化。
- 删除 `kuzumono` 破坏旧存档兼容；这是经用户确认的取舍。
