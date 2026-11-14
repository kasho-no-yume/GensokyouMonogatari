# BOSS 战 BGM：按阶级映射 + 可替换曲目

## Why

东方的弹幕战，BGM 是战斗体验的一半。当前 mod 的 BOSS 战**完全静音**：`add-audio-cue-system`
已交付统一的**一次性音效**出口（`Audio` 门面 + 8 条 cue + 激光持续音），
`add-audio-consumers` 把战斗消费点接上了，但**没有任何音乐通路**——`MUSIC` 声道上只有
原版情境音乐，与 BOSS 战无关。

三个现实约束决定了方案形状：

1. **不能分发原曲，也不能分发他人改编**。可用素材只有 5 首（朋友授权，可随包分发）。
2. **不能为每只 BOSS 备一首**。曲目数与 BOSS 数会长期不等：现存 6 只 BOSS 只用到 1/2/5 三阶。
3. **必须留一条外部替换通道**。否则 5 首会被十几只 BOSS 用烂，而"换曲"变成改代码。

本轮已实测的现状：`TouhouBoss#bossTier()` 已存在（1~5，编译期常量）；
`sounds.json` 已有 8 条音效事件；成品 jar 为 5.0 MB；5 首素材合计 **23.5 分钟 / 56.5 MB MP3**。

## What Changes

- **内置 5 首 BGM**（tier1~5），Vorbis 44.1 kHz 立体声、流式播放、整首重播
  （**不追求无缝循环**，见 design D7）。不装任何外部资源即有声音。
- **新增能力 `boss-bgm`**：`bossTier()` → 曲目的缺省映射，以及在场 BOSS 的选曲与淡变。
- **每只 BOSS 一个声音事件 id**，缺省指向与其阶级相同的音频文件（**零额外体积**）。
  于是资源包可以只替换一只 BOSS，而不动同阶的其它 BOSS。
- **外部替换零代码**：覆盖同名 `.ogg` 即替换整阶；`sounds.json` 条目带 `"replace": true`
  即替换单只。二者都是 Minecraft 原生行为——**mod 不新增加载器、不定义新压缩包格式**。
- 客户端选曲器 + 总监：滞回进入/退出、淡入淡出、换曲 crossfade、抑制原版情境音乐、
  资源包重载后自动恢复、断线清理。
- **`bossTier()` 的语义扩展**：从"只驱动外观"改为"只驱动表现层（血条造型 + BGM 选曲）"，
  仍然 MUST NOT 影响数值。
- 一次性音效管线**不变**：BGM 不进 cue 注册表、不受限流、不打字幕。

## Capabilities

### New Capabilities

- `boss-bgm`：内置曲目与阶级映射、每 BOSS 事件 id、资源包替换契约、选曲与滞回、
  淡变与 crossfade、原版音乐抑制、生命周期与重载恢复、纯客户端边界。

### Modified Capabilities

- `boss-tier`：`bossTier()` 的"只驱动外观"扩为"只驱动表现层"，并明确选曲同样 MUST NOT
  影响数值平衡；补充"阶级用于选曲时不受仪式等级/战斗进程影响"的场景。
- `audio-cue-system`：唯一出口扩展到音乐（`AudioSink` 新增音乐句柄）；
  孤儿条目校验 MUST 覆盖音乐事件；音乐条目 MUST NOT 被要求提供字幕键。

## Impact

**新增代码**

```
com.bitsson.gensokyou.audio.bossbgm/
  BgmTrack.java          曲目描述体（事件 id、基准音量、淡入/淡出 tick）
  BossBgms.java          曲目目录 + 阶级缺省映射 + BOSS 名册（唯一真值）
  BossBgmSelector.java   纯函数：候选 → 曲目（最近优先 + sticky + 滞回）
  BgmFade.java           纯状态机：淡入 / 常驻 / 淡出（复用感知曲线）
com.bitsson.gensokyou.audio/
  AudioSink.java         新增 startMusic / stopMusic（默认 no-op）
  MusicHandle.java       音乐句柄（keepAlive / stop）
com.bitsson.gensokyou.audio.client/
  BgmSound.java          TickableSoundInstance（MUSIC、非定位、循环）
  BossBgmDirector.java   客户端总线：扫描 / 选曲 / 淡变 / 重载恢复
  VanillaMusicSuppressor.java   PlaySoundEvent 处理器
```

**新增资源**

```
assets/gensokyou/sounds/bgm/tier1.ogg … tier5.ogg    5 首，约 22 MB（q4 实测）
assets/gensokyou/sounds.json                          5 条阶事件 + 每 BOSS 一条事件
assets/gensokyou/sounds/bgm/NOTICE.txt                曲目来源、授权范围、加工链
docs/boss-bgm-pack.md                                 资源包替换说明（对外文档）
```

**新增测试**

```
audio/bossbgm/BossBgmCatalogTest   曲目 ↔ sounds.json ↔ 注册表 ↔ .ogg 四方一致
audio/bossbgm/BossBgmSelectorTest  最近优先 / sticky / 滞回 / 测试 BOSS 排除
audio/bossbgm/BgmFadeTest          淡变时长下限、感知曲线、非零起音、幂等停止
audio/bossbgm/BossRosterTest       实体源码扫描：每只 TouhouBoss 在名册内或显式豁免（棘轮）
```

**改动**：`AudioCues.validate()` 的孤儿条目校验需要认得音乐事件；
`ModSounds` / `SoundIds` 新增音乐 id 清单（与 cue 清单分开，保持既有等值断言成立）。
`Gensokyou` 构造器**不改**。

**不影响**：判定、伤害、同步、存档、网络包类型、服务端行为。BGM 是纯客户端表现。

**体积**：5.0 MB → 约 27 MB。

## 素材现状（本轮已实测）

| 阶级 | 源文件（`下载/`） | 时长 | MP3 320k | Vorbis q4 | q3 |
|---|---|---|---|---|---|
| 1 | `t1 フォールオブフォール ～ 秋めく滝.mp3` | 3:22 | 8.1 MB | 3.09 MB | 2.69 MB |
| 2 | `t2 緑眼のジェラシー.mp3` | 1:52 | 4.5 MB | 1.74 MB | 1.48 MB |
| 3 | `t3 天空アーミー.mp3` | 4:52 | 11.7 MB | 4.49 MB | 3.89 MB |
| 4 | `t4 六十年目の東方裁判 ～ Fate of Sixty Years.mp3` | 8:39 | 20.8 MB | 8.52 MB | 7.30 MB |
| 5 | `t5 Complete Darkness.mp3` | 4:48 | 11.5 MB | 4.30 MB | 3.70 MB |
| | **合计** | **23:32** | **56.5 MB** | **22.1 MB** | **19.1 MB** |

三条实测出来的加工约束（`ffmpeg volumedetect`）：

- **t5 首部是静音、尾部是淡出**：开头 3 秒平均 −78 dB（近乎静音），结尾 −82 dB。
  首部静音**必须裁掉**（否则进入战斗后要等三秒才有声）；尾部淡出**接受原样保留**——
  本能力不追求无缝循环，曲末的自然收尾是作品的一部分，不做音乐结构剪辑。
- **响度不齐**：整曲平均 −9.6 dB（t2）到 −14.6 dB（t4），差 5 dB；峰值全部顶在 0.0 dBFS。
  需要静态增益归一到同一响度，并留出编码余量。
- **接缝只保证电平不跳**：t1/t4 开头弱于中段（有前奏），而 Minecraft 不能
  "跳过前奏只循环主段"（见 design D7）。因此只做首部静音裁剪与响度归一，
  和声是否接得上不做处理，也不作为验收项。

## 非目标

- **不做自定义音乐包加载器**。资源包已经覆盖该需求；再写一层只会多一套格式与一套故障面。
- **不做按符卡/血量的动态变奏**、不做战前接近曲、不做战败曲。
- **不做服务端广播**。BGM 是每个客户端本地的表现（与原版音乐一致），不新增网络包。
- **不做独立音量滑条**。BGM 走 `SoundSource.MUSIC`，受原版"音乐"滑条控制。
- 不替换、不重编、不分发原版音乐资源。
- 不改血条、不改阶级数值、不改既有 8 条音效。

## 验收

1. **开箱有声**：无任何外部资源包时，每阶 BOSS 都能听到对应曲目，进战斗 2 秒内淡入。
2. **换阶不换曲不重开**：同一只 BOSS 从第 3 张符卡切到第 4 张，音乐 MUST NOT 从头开始。
3. **换 BOSS 才换曲**：T1 大妖精在场时顶入 T2 八云蓝，音乐 crossfade 到 tier2。
4. **滞回不抖**：两只 BOSS 距离在阈值附近反复变化时，MUST NOT 来回切歌。
5. **资源包替换（整阶）**：只放 `assets/gensokyou/sounds/bgm/tier2.ogg` 即可换掉全部 T2 BOSS 的曲子。
6. **资源包替换（单只）**：`sounds.json` + `"replace": true` 可只换一只 BOSS，同阶其它 BOSS 不变。
7. **原版音乐不叠播**：BOSS 战期间 MUST NOT 听到原版情境音乐；战斗结束后原版音乐恢复。
8. **重载不哑**：`F3+T` 换资源包后，正在进行的战斗 MUST 自动用新文件恢复播放。
9. **退出干净**：断线、换维度、BOSS 死亡后 MUST 在淡出时长内静音，MUST NOT 残留循环实例。
10. **测试 BOSS 静音**：血条探针、数值测试 BOSS MUST NOT 触发 BGM。
11. **专用服务器安全**：启动 MUST NOT 加载任何客户端类，MUST NOT 因 BGM 崩溃。
12. **离线可断言**：目录四方一致、选曲纯函数、淡变状态机、名册棘轮全部在 `test` 任务内可断言。
