## Why

统一音效系统（`add-audio-cue-system`）已经交付了唯一的播放出口、cue 注册表、限流与降级，
但**没有任何消费点**：游戏里的开火、切牌、BOSS 组弹幕仍然无声，两处直放还被登记在豁免清单里。
本次把第一批消费点接上，让基础设施真正可用，同时把豁免清单清空、完成棘轮的终点。

BOSS **符卡内**音效按用户指定的两处例外落地：大妖精只在花海甩花与整体爆裂处出声；
黑谷山女只在“会留尾珠的蛛丝母弹”与“每波螺旋”处出声，且严格一组一次。
两只 BOSS 的**普攻弹幕**另按组播放玩家普通弹的 `shot.normal`（「啵」）。

## What Changes

- **玩家武器开火**：每次扳机播放一条按弹种映射的 cue；散弹/扇射只由第一颗弹携带 cue，
  保证一次扳机一声，而不是每颗弹一声。激光炮使用长、低沉的持续音（`laser.beam` 循环）
  作为开火反馈，不再叠加短促的 `laser.shoot`；激光枪只播一次性开火音。
- **BOSS 切牌**：`BossPhaseTransition` 的宣言音从原版末影龙吼改为 `spellcard.switch`。
- **程序演出音效**：`ProgramRenderView` 的 `Presentation.SoundCue` 改经 `AudioCues.byId`
  解析、经 `Audio.playAt` 播放；声音事件带上位置，未知 id 跳过并留诊断。
- **音效去重粒度**：`ProgramSoundLedger` 的键从固定的 `group/batch` 改为跟随
  `Presentation.SoundCue` 的时钟域（`MEMBER_AGE` 每批、`GROUP` 每组、`CYCLE` 每轮、
  `CARD` 每卡），使“卡级一次”“每波一次”可以按语义表达，而不是靠限流碰巧压住。
- **大妖精 · 花之海洋**：每朵花甩出时 `boss.burst`（16 声/轮）；16 朵花共同爆裂只发一次
  `warning.shift`（1 声/轮）。花环、雨季不加符卡音效。
- **黑谷山女**：会分裂/留尾珠的蛛丝母弹每批一次 `boss.burst`（捕获之网 10 声/轮、
  石窟蜘蛛巢 13 声/轮）；螺旋每波一次 `boss.burst`（瘴气 5 声/轮、热病 6 声/轮）。
  追丝扇、石窟吐丝、毒雾不加符卡音效。
- **普攻弹幕**：大妖精默认弹墙与山女默认底噪蛛丝扇，每组播放一次 `shot.normal`
  （玩家普通弹的「啵」）；不按组内 25 / 3 颗弹重复。
- **豁免清单清空**：修好两处直放后删除 `audio-playback-allowlist.txt` 条目；同时修正
  `AudioBoundaryTest` 里“清单不应为空”的断言，使空清单成为合法终点。
- **明确不做**：蓝卡 5 的现有原版 cue 暂时静默（用户选择“先只做点名两只”）；
  露米娅、其他 BOSS、玩家符卡施放、BGM 均不在本次范围。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `audio-cue-system`：新增消费点契约（开火每扳机一次、切牌、程序 cue 解析与去重）、
  空豁免清单合法化。
- `boss-program-authoring`：`Presentation.SoundCue` 的四个时钟域与去重粒度语义。
- `boss-program-content`：大妖精花海 16+1、山女蛛丝 10/13 与螺旋 5/6、两只 BOSS 普攻每组一次
  `shot.normal` 的可听次数契约。
- `boss-phase-transition`：切阶段音效改用 `spellcard.switch`，删除“无自制资源时用占位”条款。

## Impact

**代码**

- `client/ProgramDanmakuRenderer`、`client/ProgramSoundLedger`、`client/BossPhaseTransition`
- `danmaku/program/network/ProgramRenderView`（`Sound` 增加位置与 scope）
- `danmaku/program/content/BigFairyPrograms`、`YamamePrograms`
- `item/weapon/WeaponFiring`、`item/weapon` 新增 `WeaponShotCue` 映射
- `entity/AbstractDanmakuProjectile`（新增同步 cue 字节）、`entity/LaserDanmaku`（客户端持续音）
- `client/` 新增武器开火音处理器
- `audio-playback-allowlist.txt` 清空；`AudioBoundaryTest` 断言调整

**数据 / 资源**

- `preview/data/big_fairy_2.json`、`yamame_1..4.json` 因 presentation 增加 cue 而更新。
- `ProgramDefinition.hash` 变化，已持久化 encounter 读档时可能按血量重新选卡；属于内容变更代价。

**不影响**

- 判定、伤害、同步判定、存档结构、网络包类型（只新增一个实体同步字节）。
- 专用服务器：不安装客户端 sink，所有调用保持 no-op。
