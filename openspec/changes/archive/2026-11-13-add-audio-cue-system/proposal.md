# 统一音效调用系统（Audio Cue System）

## Why

**本变更只交付「出声的基础设施」，不接任何一处消费点。** 谁在什么时刻播哪条 cue，
由后续的消费变更决定；本变更只保证「想播的时候，有一个唯一、参数正确、限流、可降级的出口」。

素材问题已经解决：本轮已在本地把七类音效生成、试听、定稿并做成可直接进资源包的
44.1 kHz 单声道 Vorbis（见「素材现状」）。**现在缺的是调用层。**

### 现状修正（原提案的「全仓静默」不成立）

原提案称 `SoundEvent` / `playSound` / `SoundSource` 命中 0，实测并非如此：

| 域 | 现状 | 本变更是否处理 |
|---|---|---|
| 战斗表现层 | `client/BossPhaseTransition` 用末影龙吼占位播切牌音；`client/ProgramDanmakuRenderer` 直放 `minecraft:entity.blaze.shoot` | **不迁移**，进豁免清单 |
| 仪式 / 方块 | 约 10 个文件用服务端 `level.playSound(...)` 广播 | **不迁移**，不在扫描范围 |
| 物品 / 事件 | `CodexOfBeingsItem`、`GuideBookItem`、`SpellCardCombatHandler` 等 | **不迁移**，不在扫描范围 |
| 常量引用 | `registry/ModEquipmentMaterials` 用 `SoundEvents.ARMOR_EQUIP_*` 作材质音 | 合法，**不禁止** |

另有一条原提案完全没提的硬前置：**mod 的声音事件必须先注册进 `BuiltInRegistries.SOUND_EVENT`**。
只放 `sounds.json` 不够 —— 未注册的 event id 在注册表里查不到，等于永远静音。
故本变更必须新增 `ModSounds`（`DeferredRegister<SoundEvent>`，8 条）。

### 为什么不能直接在各处调原版 API

如果每个调用点自己写 `level.playSound(null, pos, SoundEvents.X, ...)`，会同时产生五个问题：

1. **音量无法统一** 同一批素材的感知响度差异极大（0.12 s 的「啵」与 2.6 s 的符卡音
   在峰值同为 -3 dBFS 时，听感差 15 dB 以上）。逐点手写 volume 必然失控。
2. **音高抖动会各写各的** 连发弹幕若不放宽音高，同一采样重复会立刻暴露「塑料感」。
3. **重复触发会打爆声道** 高速弹幕一秒可触发 20+ 次。Minecraft 的 `SoundEngine`
   声道数有限，超量时是**静默丢弃**，表现为「打快了就没声音」。
4. **缺少资源的降级无处安放** `boss-phase-transition` 已有 requirement 要求
   「缺自制音效资源时 SHALL 使用登记的占位音效，MUST NOT 静默」。
5. **无法被离线断言** 本仓库的验收文化是可离线断言；散落的 `playSound` 只能靠实机听。

**结论：先有一个唯一的出声出口，再谈铺音效。**

## What Changes

新增 `com.bitsson.gensokyou.audio` 包，作为**唯一的生产音效播放入口**：

- **`AudioCue`**（enum）：一条 cue 的描述体 —— 声音事件 id、基准音量、基准音高、
  音高抖动、最小重触发间隔（**tick**）、声源类别、是否定位、字幕键、占位音效 id、
  是否信息类。**混音参数写在注册表里，不写在调用点。**
- **`AudioCues`**（注册表）：登记 8 条 cue，提供 `byId(ResourceLocation)`（供数据驱动的
  后续消费方按 id 取 cue），并在加载期做一致性校验。
- **`Audio`**（门面）：`play(cue)` / `playAt(cue, pos)` / `playOn(cue, entity)` /
  `onClientTick()`。调用点只知道**语义**，不知道音量、音高、衰减。
- **`AudioSink`**（可替换后端）：生产实现走客户端；未安装时全部调用为 no-op
  （专用服务器即此状态）。测试实现只记录调用。
- **`SoundLookup`**（id → `SoundEvent` 的解析口）：把 `BuiltInRegistries` 的访问关在
  实现里，使 enum 与限流/包络逻辑可离线单测。
- **`BeamSound`** + **`BeamEnvelope`**：持续音句柄；起音 → 无缝循环 → 收尾，
  音量与音高包络推进。包络计算是**纯函数**，可离线断言。
- **`AudioRateLimiter`**：按 cue 记最小间隔（tick）+ 每客户端 tick 全局新增上限；
  信息类 cue 不占全局额度。

配套：

- 新增 `registry/ModSounds`：`DeferredRegister<SoundEvent>` 注册 8 个事件，
  在 `Gensokyou` 构造器挂到 mod bus。
- 新增 `src/main/resources/assets/gensokyou/sounds.json` 与 `sounds/*.ogg`（8 个事件）；
  新增 `subtitles.*` 的 `zh_cn` / `en_us` 文案；新增 `NOTICE.txt`。
- **「唯一出口」以「豁免清单只减不增」的方式落地**：扫描战斗表现层
  （`client/`、`danmaku/`、`entity/`、`item/weapon/`、`network/`）中的直接播放调用；
  存量两处登记豁免，并断言「清单内文件确实仍违规」——修好一处就必须删一条，
  清单因此只会变短。

## Capabilities

### New Capabilities

- `audio-cue-system`：统一出声门面、cue 注册表、混音参数集中管理、重复触发限流、
  持续音包络、资源缺失降级、唯一出口的豁免清单机制。

### Modified Capabilities

无。`boss-phase-transition` 的「切阶段音效」delta **不属于本变更**：本变更不接消费点，
若现在写入 delta，归档后主 spec 会声明一件代码尚未做的事。该 delta 随消费变更重新提交。

## Impact

**新增代码**（无既有调用点改动；`Gensokyou` 构造器仅新增一行注册）：

```
com.bitsson.gensokyou.audio/
  Audio.java             门面
  AudioCue.java          cue 描述体（enum）
  AudioCues.java         注册表 + byId + 加载期校验
  AudioSink.java         可替换播放后端（生产 = 客户端；测试 = 记录器）
  SoundLookup.java       eventId -> SoundEvent 的解析口
  AudioRateLimiter.java  限流（tick 计量）
  BeamSound.java         持续音句柄
  BeamEnvelope.java      纯包络推进（可离线断言）
com.bitsson.gensokyou.registry/
  ModSounds.java         8 条 SoundEvent 注册
```

**新增资源**：

```
assets/gensokyou/sounds.json
assets/gensokyou/sounds/*.ogg      8 个事件，合计约 122 KB
assets/gensokyou/NOTICE.txt       生成模型与后处理链（可追溯）
assets/gensokyou/lang/{zh_cn,en_us}.json    subtitles.* 键
```

**新增测试**：

```
audio/AudioBoundaryTest            唯一出口扫描 + 豁免清单棘轮
audio/AudioCatalogTest             注册表 <-> sounds.json <-> 文件 三方一致
audio/AudioRateLimiterTest         每 cue 间隔 / 每 tick 上限 / 信息类豁免
audio/BeamEnvelopeTest             淡变下限、感知曲线、看门狗自停
audio/AudioDegradationTest         目标缺失 -> 占位；占位缺失 -> 静默 + 日志
```

**不影响**：判定、伤害、同步、存档、服务端逻辑。音效是纯客户端表现。
**不迁移**：任何既有 `playSound` 调用点。

## 素材现状（本轮已定稿，作为本变更的输入）

| cue | 文件 | 时长 | 用途（仅供登记参数，挂点不在本变更） |
|---|---|---|---|
| `laser.shoot` | `laser_shot.ogg` | 0.31 s | 瞬发激光（下行音调） |
| `laser.beam` | `laser_loop.ogg` | 4.00 s | 持续激光，无缝循环 |
| `shot.normal` | `shot_normal.ogg` | 0.12 s | 普通弹幕「啵」 |
| `shot.fast` | `shot_fast.ogg` | 0.35 s | 玩家高速弹幕「沙」 |
| `knife.throw` | `knife_throw.ogg` | 0.17 s | 投掷飞刀「咻」 |
| `boss.burst` | `boss_burst.ogg` | 0.36 s | BOSS 批量弹幕（带一点「叮」） |
| `warning.shift` | `warn_shift.ogg` | 0.70 s | 弹幕变轨警示 |
| `spellcard.switch` | `spellcard_switch.ogg` | 2.60 s | 符卡切换 |

素材放在 `F:\Comfy-Desktop\sfx-lab\final\`，尚未进仓库。

## 明确不做（Non-Goals）

- **不接任何消费点。** 本变更不修改 `BossPhaseTransition`、`ProgramDanmakuRenderer`，
  不新增 `fromWeapon` 同步位，不在 `WeaponFiring` 里出声，不改程序音效的求值时机
  （渲染帧 → client tick 属消费变更）。
- **不迁移存量的服务端 `playSound`**（仪式、物品、方块）。
- **不做音乐 / BGM 系统。**
- **不做动态混音总线。**
- **不改造既有 spec 的判定行为。** 音效 MUST NOT 参与任何伤害或判定计算。
- **不追求实机听感验收自动化。** 离线只断言「请求了正确 cue、参数在范围内」。

## 验收

1. **唯一出口可校验**：扫描范围内、豁免清单之外的直接播放调用 → 测试失败；
   修好一处却未删清单条目 → 测试失败。
2. **注册表与资源一致**：cue、`sounds.json` 条目、`ModSounds` 注册 id、`.ogg` 文件
   四方互相一致；任一缺失启动期记录 ERROR、测试失败、且不抛异常。
3. **降级不静默**：故意让某 cue 的目标不可用，播放 MUST 落到占位；占位也不可用时
   MUST 静默并留下含 cue 名的日志，MUST NOT 抛异常。
4. **重复触发受限**：同一 tick 内触发 100 次同一 cue，实际出声次数 MUST 不超过限流上限；
   已达每 tick 上限时，信息类 cue MUST 仍出声。
5. **持续音无缝**：`laser_loop.ogg` 循环 60 秒，接缝样本差不超过整段 99.9% 分位。
6. **包络平滑**：淡入/淡出 MUST 不短于 150 ms（3 tick），振幅 MUST 使用感知曲线
   （`p^1.7`），MUST NOT 使用线性振幅；连续 10 tick 无存续信号 MUST 自行停止。
7. **专用服务器安全**：启动不得因音频包崩溃，不得加载任何客户端类。
