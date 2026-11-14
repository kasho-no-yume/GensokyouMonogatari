## Context

`add-audio-cue-system` 已归档，`audio` 包提供了唯一出口（`Audio`）、8 条 cue、限流、持续音与降级。
现状是：

- 生产代码仍有两处直放，登记在 `src/test/resources/audio-playback-allowlist.txt`：
  `client/BossPhaseTransition`（切牌音）与 `client/ProgramDanmakuRenderer`（程序 cue）。
- 武器开火在服务端 `WeaponFiring`，客户端只看到弹实体；`fromWeapon` 是服务端字段，不同步。
- 大妖精 / 山女已全部运行程序后端，符卡音效应做在 `Presentation.SoundCue` 上，不需要服务端广播。
- `Presentation.SoundCue` 已支持 `MEMBER_AGE` / `GROUP` / `CYCLE` / `CARD` 四个时钟域，
  但 `ProgramSoundLedger` 固定按 `group/batch/dedupeKey` 去重，导致“卡级一次”的 cue
  会按 batch 重复播放。
- 山女同波螺旋会按目标槽各建一个组（最多 5 组；热病每条臂再 ×3，最多 15 组），
  同刻多组不能靠限流“碰巧”压成一声。

本次是纯表现层接线：不改判定、伤害、同步语义与存档结构；唯一的结构改动是
`AbstractDanmakuProjectile` 新增一个客户端可读的同步 cue 字节。

## Goals / Non-Goals

**Goals:**

1. 玩家每次武器扳机播放一条正确的弹种 cue；散弹/扇射一次扳机一声。
2. 激光炮有起音 + 循环 + 收尾；激光枪只播一次性开火音。
3. BOSS 切牌播放 `spellcard.switch`；两处直放迁移完成，豁免清单清空。
4. 程序 `Presentation.SoundCue` 经 `AudioCues.byId` 解析、经 `Audio` 播放，带位置。
5. `SoundCue` 的去重粒度跟随时钟域：`MEMBER_AGE` 每批、`GROUP` 每组、`CYCLE` 每轮、`CARD` 每卡。
6. 大妖精花海：每朵花一声 `boss.burst`，整轮爆裂一声 `warning.shift`。
7. 山女：会留尾珠的蛛丝母弹每批一声 `boss.burst`；螺旋每波一声 `boss.burst`。

**Non-Goals:**

- 不给蓝 / 露米娅 / 其他 BOSS 的符卡新增音效；蓝卡 5 既有原版 cue 暂时静默。
- BOSS 激光音效（逐激光或按组）本次不做；后续单独评估，优先考虑“按组一个全局音 + 并发上限”。
- 不做玩家符卡施放音、BGM、动态混音总线。
- 不迁移仪式 / 物品 / 方块的存量服务端 `playSound`。
- 不把程序音效求值从渲染帧迁到 client tick（音频设计中的 follow-up，本次不做）。
- 不改武器数值、弹种注册、`FirePattern` 形状。

## Decisions

### D1. 程序 cue 走 `AudioCues.byId` + `Audio.playAt`

`ProgramRenderView.Sound` 增加 `position`（取 batch source frame 的 origin，程序 `Vec3`），
客户端渲染器先经 `ProgramSoundLedger` 去重，再把事件交给 `ProgramSoundPlayer`；
后者把 `sound` 字符串交给 `AudioCues.byId(String)` 解析：

- 命中：`Audio.playAt(cue, 转换后的 MC Vec3)`；是否定位由 cue 的 `positional()` 决定。
- 未命中：跳过并**每个 id 只记一次**诊断，不抛异常。

把解析/播放拆到 `ProgramSoundPlayer` 的原因：它不引用任何 Minecraft 客户端类，
可以用测试侧 `RecordingAudioSink` 直接断言“哪条 cue、哪个位置”，不必启动游戏。

**为什么不是继续直放原版事件**：唯一出口要求与豁免棘轮都要求迁移；cue 注册表还统一了
音量、音高抖动、限流与缺资源降级，直放会绕过这四项。
**为什么位置取 batch source 而不是当前 BOSS 实体**：投影层没有实体引用；batch source 是
发射时的世界坐标，对一次性 cue 足够准确，且不引入新的网络字段。

### D2. 去重粒度跟随 `Presentation.Clocks`，而不是固定 batch

`ProgramRenderView` 为每个 cue 计算 `scopeKey`：

```
MEMBER_AGE → "b:" + group + "/" + ordinal + ":MEMBER_AGE:" + cueTick
GROUP      → "g:" + group                  + ":GROUP:"      + cueTick
CYCLE      → "c:" + clocks.cycle()         + ":CYCLE:"      + cueTick
CARD       → "k:" + clocks.card()          + ":CARD:"       + cueTick
```

`ProgramSoundLedger.accept(epoch, sound)` 的键改为 `epoch | scopeKey | dedupeKey`。
epoch 变化时清空。

**为什么**：`CYCLE` 域本来表达“每轮一次”，`CARD` 表达“每卡一次”；固定按 batch 去重
使这两个域名不副实。花海 16 个 batch 共用 `CYCLE 100` 时，scope 相同，自然合并为一声；
山女同波 5 / 15 个螺旋组共用同一 `CYCLE` 波 tick，也自然合并。
**为什么不做独立 card-level cue API**：需要改 `ProgramDefinition`、wire 描述与作者 API；
scope-aware ledger 只改客户端投影与去重，代价小得多，且保留现有 `SoundCue` 形状。
**为什么不靠 `boss.burst` 的 `minInterval=3` 合并**：同刻多组只是碰巧被限流吞掉，
不是语义；改限流参数或加组数就会退化成机关枪。

### D3. 玩家开火：同步 cue 字节，第一颗弹携带

新增 `audio/WeaponShotCue`（NORMAL / FAST / KNIFE / LASER / LASER_BEAM），
`AbstractDanmakuProjectile` 增加 `DATA_SHOT_CUE`（byte，0 = 无），
`WeaponFiring.fire` 按 `FirePattern` 选 cue，并且：

- 非激光：只在 `i == 0` 的那颗弹上设置 cue；
- 激光 / 灵符：单实体，直接设置。

客户端 `EntityJoinLevelEvent`（`Dist.CLIENT`）看到带 cue 的弹就 `Audio.playAt`。
cue 映射：

| FirePattern | cue | 说明 |
|---|---|---|
| SPHERE（单发 / 散弹） | `shot.normal` | 散弹由第一颗携带，一次扳机一声 |
| CONE（麟弹） | `shot.fast` | 2 tick 一发，匹配 `minInterval=2` |
| KNIFE | `knife.throw` | |
| TALISMAN | `shot.normal` | 无专用 cue |
| LASER（< 2s） | `laser.shoot` | 激光枪的 0.5s 脉冲 |
| LASER（≥ 2s） | `laser.beam` | 激光炮的 3s 持续；长、低沉的循环版本 |

**为什么不是 S2C 音效包**：现有 spawn 包已经会到达所有能看到弹的客户端，加一个同步字节
即可；不新增包类型，也符合程序侧“客户端从复制状态推导表现”的既有倾向。
**为什么不是客户端预测**：灵力不足 / 冷却 / 非本地玩家都会误判或漏听。
**为什么第一颗弹携带**：散弹 5 颗同 tick 出生，若每颗都带 cue，全局每 tick 上限会放出
多条重叠音；第一颗携带把“一次扳机一声”变成结构性事实，而不是限流副作用。
**“炮”的判定**：`laserDurationSeconds >= 2.0`（枪 0.5 / 炮 3.0），在
`WeaponShotCue.forKind(DanmakuKind, boolean, double)` 中集中并有纯函数测试；
`WeaponFiring.cueFor(FirePattern)` 只负责把弹种、`isLaser()` 与持续秒数取出来。
若将来新增持续激光，按同一阈值归类。

### D4. 激光持续音由客户端激光实体自己管理

`LaserDanmaku` 增加客户端字段 `BeamHandle clientBeam` 与 `laserShotPlayed`：

- `age < delay`：什么都不做；
- 首次 `age >= delay`：
  - `LASER`（枪）：`Audio.playAt(LASER_SHOOT)`；
  - `LASER_BEAM`（炮）：只 `Audio.startBeam(LASER_BEAM, position())`，
    **不叠加**短促的 `LASER_SHOOT`；
- `age < delay + duration`：每 tick `clientBeam.keepAlive()`；
- 到期或 `remove(RemovalReason)`：`clientBeam.stop()` 并置空。

**为什么炮不叠短促音**：炮是长时间激光，短促的高频「啾」会把长、低沉的嗡鸣盖住；
用户已明确要求炮使用长的低沉版本，枪才用一次性 `laser.shoot`。
**为什么循环音必须给引擎非零音量**：`SoundEngine.play` 在 `getVolume() == 0` 时直接跳过实例
（"Skipped playing sound ..., volume was zero"）。`BeamEnvelope` 的起音从 0 开始，
若 `BeamSound.getVolume()` 原样返回 0，循环音在入队前就被丢弃，表现为「激光炮完全没有声音」。
`BeamEnvelope.playableAmplitude(0)` 因此返回极小非零值（1e-4，约 -80 dB），第一 tick 听不见，
随后仍按 3 tick 包络起音。
**为什么放在实体 tick**：激光的延迟/持续时间已经由同步字段给出，实体生命周期就是
持续音的生命周期；不需要额外的客户端定时器或网络包。看门狗仍是第二道保险。
**为什么起音放在 ACTIVE 而不是开火瞬间**：炮有 1s 延迟，声音应与光束出现同拍；
枪的延迟只有 0.15s，听感上仍是即时。

### D5. 内容落点

**大妖精 · `BigFairyPrograms.flower()`**（共享的 flower member presentation）：

```java
MEMBER_AGE 0    → "gensokyou:boss_burst"   key "flower-throw"
CYCLE 100       → "gensokyou:warn_shift"   key "flower-burst"
```

`CYCLE 100` 是第一朵花的爆散 tick；后续 15 朵在 104..160 错开爆散，
所以这是“整轮花海一声警告”，不是“同 tick 全体爆散”。花环、雨季不加符卡音效。

**大妖精 · `BigFairyPrograms.wall()`**（默认攻击墙）：

```java
MEMBER_AGE 0 → "gensokyou:shot_normal"  key "wall-throw"
```

默认弹墙每组 25 颗，按组一声「啵」；MUST NOT 按 25 颗弹重复。

**黑谷山女 · `YamamePrograms`**：

- `spinner()`（会 `emit` 尾珠的母弹）presentation：`MEMBER_AGE 0 → boss_burst`。
  捕获之网 10 拍、石窟蜘蛛巢 13 拍，每拍一批一声。
- `miasma()` 的 `spiral` presentation：`CYCLE` 0/60/120/180/240 → 5 条 `boss_burst`。
- `fever()` 的 `centre` / `leftArm` / `rightArm` presentation：`CYCLE` 0/50/.../250 →
  6 条同 key 的 `boss_burst`；任一条臂存活即可触发，scope 合并为每波一声。
- `background()`（默认底噪蛛丝扇）：`MEMBER_AGE 0 → shot_normal`，每组一声。
- 追丝扇（chase）、石窟吐丝（spit）、毒雾（mist）不加符卡音效。

cue 字符串统一用 `Gensokyou.id("boss_burst").toString()` 形式，避免手写命名空间。

### D6. 切牌音与空豁免清单

`BossPhaseTransition.playDeclareSound()` 改为 `Audio.play(AudioCue.SPELLCARD_SWITCH)`，
删除原版常量与直放 import。`ProgramDanmakuRenderer` 删除 `SimpleSoundInstance` /
`BuiltInRegistries.SOUND_EVENT` 直放。

`AudioBoundaryTest` 当前有一句 `assertFalse(allowed.isEmpty(), "豁免清单不应为空……")`。
两处都修好后清单合法为空，必须删除该断言；`readAllowlist()` 已能处理空文件。
`boss-phase-transition` spec 的“尚无自制资源时使用原版占位”同步移除。

### D7. 测试分层

| 层 | 内容 |
|---|---|
| 纯逻辑 | `WeaponShotCue.forKind` 的弹种映射；`ProgramSoundLedger` 的四个 scope |
| 内容投影 | 花海 16 `boss_burst` + 1 `warn_shift`；山女生丝 10/13、螺旋 5/6；默认墙 4、底噪扇 3 声 `shot.normal`；其余 0 |
| 出口边界 | 空豁免清单 + 扫描范围内零直放 |
| 门面 | 用 `RecordingAudioSink` 断言程序 cue 解析到正确 `AudioCue` 与位置（可选） |
| 实机 | 听感、激光炮持续音、多人开火、切换符卡 |

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| 蓝卡 5 的 `minecraft:entity.blaze.shoot` 在迁移后静默 | 用户已确认“先只做点名两只”；后续把该字符串改为 `gensokyou:laser_shoot` 即可恢复 |
| 加 cue 改变 `ProgramDefinition.hash`，读档可能重置卡下标 | 内容变更的正常代价；在验收记录中登记，不伪装成无缝 |
| 花海 16 次爆散在 100..160 错开，一声警告可能被理解为“只警告第一朵” | `CYCLE 100` 对齐第一朵爆散；若实机听感不对，再改为 96 或给整轮一个更强的尾音 |
| “第一颗弹携带 cue”依赖 `WeaponFiring` 的生成顺序 | 映射与设置集中在 `fire()` 一处，加纯函数测试；未来新增弹型必须复用该入口 |
| 激光炮阈值 2.0s 是魔数 | 集中在 `WeaponShotCue.forKind` 并注释枪 0.5 / 炮 3.0 的实际值；新增激光时按同一语义归类 |
| 渲染帧求值仍可能在同一 tick 多次请求 | `ProgramSoundLedger` 先于 `Audio` 去重，限流只做第二道保险；迁到 client tick 留作后续 |
| 空豁免清单断言漏改 | 作为独立任务与验收项；`AudioBoundaryTest` 必须同时覆盖“空清单 + 零违规” |
| `preview/data` 基线漂移 | 重导 `big_fairy_2` / `yamame_1..4`，跑 `--check` |

## Migration Plan

1. 先落程序投影 / ledger / 渲染器迁移，让两处直放消失、豁免清单清空。
2. 再接玩家开火同步字节与激光持续音。
3. 最后加大妖精 / 山女内容 cue，重导 preview baseline。
4. 实机试听：花海 16+1、山女生丝与螺旋、七种弹核、切牌、多人同时开火。
5. 回滚：撤销本 change 即回到“基础设施已就位但无声”；无存档格式变更，
   唯一的同步字节不持久化，回滚不影响旧存档。

## Open Questions

无。下列细节按本设计执行，实机若需调整再作为后续微调：

- 花海警告在 `CYCLE 100`（第一朵爆散）而不是 96。
- 灵符使用 `shot.normal`。
- 激光炮阈值 2.0s；激光枪不起持续音。
