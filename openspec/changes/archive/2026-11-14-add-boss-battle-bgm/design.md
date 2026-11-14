## Context

`add-audio-cue-system` 与 `add-audio-consumers` 已归档，`audio` 包的现状是：

```
┌──────────────── 已交付（音效） ────────────────┐
│  Audio 门面 → AudioSink → ClientAudioSink       │
│  AudioCue × 8（一次性：限流 / 音高抖动 / 字幕）  │
│  BeamEnvelope + BeamSound（循环 + 淡变 + 看门狗）│
│  AudioCues.validate()（四方一致 + 孤儿条目）     │
│  AudioBoundaryTest（唯一出口扫描 + 豁免棘轮）    │
└─────────────────────────────────────────────────┘

┌──────────────── 本变更新增（音乐） ────────────┐
│  BossBgms（曲目目录 + 阶级映射 + BOSS 名册）     │
│  BossBgmSelector（纯函数：谁的音乐）             │
│  BgmFade（纯状态机：淡入/淡出）                  │
│  BossBgmDirector（客户端总线）                   │
│  BgmSound（MUSIC / 非定位 / 循环）               │
│  VanillaMusicSuppressor（PlaySoundEvent）        │
│  sounds.json 的音乐事件 + 5 个 .ogg              │
└─────────────────────────────────────────────────┘
```

相关既有约束：

- `boss-tier`：`bossTier()` MUST 为编译期常量，MUST NOT 影响数值，**当前只驱动血条造型**。
  阶级分配表本身是占位（3/4 阶当前没有 BOSS 使用）。
- `audio-cue-system`：唯一出声出口；`AudioCues.validate()` 会把 `sounds.json` 里
  "注册表未声明"的条目判为孤儿并记 ERROR。
- `project.md` §3 的验收文化是「可离线断言」。

本轮直接从 `build/moddev/artifacts/neoforge-21.1.248-sources.jar` 核对了四个关键实现
（`SoundManager` / `SoundEngine` / `MusicManager` / `SoundEventRegistrationSerializer`），
下面每条决策都标注了它依赖的事实。

## Goals / Non-Goals

**Goals:**

1. 开箱有声：内置 5 首，按 `bossTier()` 1~5 选取，不需要任何配置或外部资源。
2. 换曲不改代码：资源包能替换"整阶"或"单只 BOSS"两种粒度。
3. 音乐与战斗状态解耦：切符卡不重开，换 BOSS 才换曲，双 BOSS 不抖。
4. 不与原版音乐叠播，重载/断线/死亡后不留残留实例。
5. 纯客户端：不发包、不参与判定、专用服务器不加载客户端类。
6. 选曲与淡变可在纯 JUnit 下断言。

**Non-Goals:**

- 不做自定义音乐包加载器、不做新压缩包格式。
- 不做按血量/符卡的动态变奏、不做战前曲、不做战败曲。
- 不改 `AudioCue` 的语义去容纳音乐。
- 不做独立音量滑条。
- 不做服务端广播。

## Decisions

### D1. 音乐走客户端推导，不发包

客户端自己扫本地的 `TouhouBoss` 实体（实体本来就同步）、自己决定放哪首。
服务端**完全不知道 BGM 存在**。

**为什么**：原版情境音乐也是每个客户端独立决定的；服务端广播长音实例需要新增
"start/stop 音轨"包，还要处理中途加入、观战、跨维度，收益为零。
**代价**：两名玩家站在两只 BOSS 中间时可以听到不同的曲子（各自选"离自己最近的那只"）。
这与原版音乐的行为一致，接受。

### D2. 后端仍是 `AudioSink`，但音乐是一个独立句柄

在 `AudioSink` 上新增两个入口，默认实现为 no-op：

```java
MusicHandle startMusic(BgmTrack track, int fadeInTicks);   // 循环、非定位、MUSIC
void stopMusic(MusicHandle handle, int fadeOutTicks);      // 幂等
```

`MusicHandle` = `keepAlive()` + `stop()` + `stopped()`，与 `BeamHandle` 同形。

**为什么不复用 `AudioCue`**：cue 描述体是一次性音效的形状——限流间隔、音高抖动、
字幕键、占位降级。音乐这四项全是反向的（不限流、不抖动、无字幕、占位无意义）。
把 5 条音乐塞进 `AudioCue` 会污染注册表，也会让"每 tick 上限"开始统计音乐。
**为什么仍然走 `AudioSink`**：唯一出口的收益（测试可记录、专用服务器 no-op、
边界扫描可断言）与音效完全一样，没有理由另开一条通道。

### D3. 每只 BOSS 一个事件 id，缺省共享同一音频文件

`sounds.json` 里两类条目：

```json
"bgm/tier1":            { "sounds": [ { "name": "gensokyou:bgm/tier1", "stream": true } ] },
"bgm/boss/big_fairy":   { "sounds": [ { "name": "gensokyou:bgm/tier1", "stream": true } ] },
```

即：**N 个事件 id → 5 个文件**。同一文件被多条事件引用在 `sounds.json` 里是合法的，
**不产生任何额外体积**。

- 阶级映射只是"缺省指向哪个文件"，写在 `BossBgms` 的一张表里。
- 名册外的 `TouhouBoss`（未来的、或其它 mod 的）回落到 `bgm/tier{n}`。
- 选曲的结果是**事件 id**，不是"第几阶"。因此两只同在 T1 的 BOSS 共享同一事件 id 时，
  从大妖精换成山女**不会重开音乐**——这正是想要的。

**为什么不是"只有 5 个事件 id"**：那样资源包最多只能整阶替换；用户明确要求
"让不同 BOSS 加载资源包里的曲目"，即**单只粒度**。多 6 条 JSON 就能买到它。
**为什么不是"5 个文件 + 一张 JSON 映射表"**：那要新增一个客户端 reload listener 与一套
codec，故障面比 6 条静态 JSON 大得多，而收益（外部作者能任意配曲）可以用
`sounds.json` 的 `replace` 达成。

### D4. 资源包替换契约：两条路径，语义不同，必须写清

| 目标 | 做法 | 生效范围 |
|---|---|---|
| 换掉整阶 | 在资源包里放 `assets/gensokyou/sounds/bgm/tierN.ogg` | 该阶全部 BOSS |
| 换掉单只 | 资源包 `assets/gensokyou/sounds.json` 里写 `"bgm/boss/x": { "replace": true, "sounds": [...] }` | 仅该 BOSS |

两条路径的语义来自源码（`SoundManager.Preparations.handleRegistration`）：

```java
if (existing == null || registration.isReplace())   // ← "replace": true 才替换
    registry.put(id, new WeighedSoundEvents(...));
for (sound : registration.getSounds())
    existing.addSound(sound);                        // ← 否则是【追加】
```

也就是说：**不带 `replace: true` 的 `sounds.json` 条目不会替换，而是给原事件再加一个
variant，结果是新旧曲目随机二选一。** 这个语义反直觉且不会报错，所以对外文档
（`docs/boss-bgm-pack.md`）必须把它写成第一句话，并且只教两条路径，绝不教第三种写法。

文件路径覆盖之所以"零 JSON"，是因为 `Sound.SOUND_LISTER.listMatchingResources`
把同名资源按包优先级取栈顶（`soundCache`），原版文件被直接遮蔽。

### D5. 选曲器：最近优先 + sticky + 滞回，全部纯函数

输入是每只候选 BOSS 的不可变快照：

```
record Candidate(UUID id, int tier, double distanceSqr, boolean targetingMe)
```

规则（纯函数，可离线断言）：

1. 过滤：距离 > 退出半径、测试 BOSS、已死亡。
2. 排序键：`targetingMe` 降序 → `distanceSqr` 升序 → 稳定序（UUID）。
3. **sticky**：若当前曲目仍由在场的某只 BOSS 提供，且没有"明确更好"的候选
   （距离优于当前 25% 以内不算更好），MUST 保持当前曲目。
4. 换曲键 = 事件 id。同 id 不换。

**为什么必须 sticky**：两只 T1 BOSS 在你身边交叉走位时，按距离硬选会每 10 tick 切一次歌。
**为什么距离用平方**：避免开方，且比较语义不变。

### D6. 进入/退出用滞回，半径与 `LOCK_RADIUS` 对齐

- 进入：最近候选 ≤ **32** 格。
- 退出：当前提供者 > **48** 格，或已不存在。
- 扫描周期：**10 tick**（不是每 tick）。

**为什么是 32/48**：`AbstractTouhouBoss.LOCK_RADIUS = 64` 已经是这个仓库里
"这场战斗的范围"的唯一权威来源（它同时管瞄准、移动取人与弹幕锁定）。
BGM 的绝对上限取它，再留出滞回区防止边界抖动。
**为什么不新造一个半径常量**：第三个魔数会立刻与锁定半径漂移，实机表现是
"它已经在朝你放弹幕，但没有音乐"。

### D7. 循环形态是"整首重播"；不追求无缝，只裁首部静音

`SoundEngine` 的循环路径（源码核对）：

```java
boolean loopAutomatically = shouldLoopAutomatically(sound);   // isLooping() && delay <= 0
...
soundInstance.getStream(buffers, sound, loopAutomatically)     // → SoundBufferLibrary.getStream(path, true)
    → new LoopingAudioStream(provider, inputStream)            // 读空 → close() → mark/reset → 重新 create()
```

结论：**Minecraft 只会"整文件播完从头再来"**，没有 LOOPSTART/LOOPEND，
不能跳过前奏只循环主段。因此本能力对素材只提两条**可实现**的要求：

- **首部静音 MUST 裁掉**：否则进入战斗后要等几秒才有声（实测 t5 开头近 3 秒是静音）。
- **响度 MUST 归一**：曲目之间感知响度差控制在 2 LU 量级，避免换阶时忽大忽小。
- **曲末的自然收尾（含淡出）保留**：本能力**不追求无缝循环**。曲末渐弱是作品的一部分，
  为接缝而剪辑音乐结构属于越权改作，不做。
- 代价：接缝处会出现一次短暂回落（t5 最明显，末尾 4 秒渐弱后从满音量重来）。
  这是**已知且已接受**的取舍，不作为缺陷、不进验收项。

**为什么不改用两段式循环**：`SoundEngine` 的 manual loop 只在 `getDelay() > 0` 时生效
（`requiresManualLooping`），且它只是"延迟后重播"，并不能表达 intro/loop 结构。

### D8. 淡变复用 `BeamEnvelope` 的几何与下限，但状态机独立

音乐需要的三段与激光同形：起音 → 常驻 → 收尾。直接复用的三条：

| 复用项 | 来源 | 为什么必须 |
|---|---|---|
| 下次振幅 `p^1.7` 感知曲线 | `BeamEnvelope` | 线性振幅在 20 Hz 更新下听感是"前半段几乎没变、末尾骤降" |
| 起音非零下限 `1e-4` | `BeamEnvelope.playableAmplitude(0)` | `SoundEngine.play` 对 `volume == 0` 直接跳过，淡入永远不开始 |
| 看门狗自停 | `BeamEnvelope` | 防止循环实例永久占声道 |

独立的理由：音乐的段长按秒计（淡入 2 s / 淡出 2.5 s），而 `BeamEnvelope` 的常量被
激光调过（3 tick 起音、12 tick 收尾）；直接改它的常量会动到已归档的验收。
因此新增 `BgmFade`（纯类，同形不同参），并从 `BeamEnvelope` 借一个静态的曲线函数，
避免出现两份 `Math.pow(p, 1.7)`。

**crossfade**：换曲时同时持两个实例——旧的走淡出、新的走淡入。
`Library.Pool.STREAMING` 是很小的固定池（`CountingChannelPool`，满了直接返回 null 并记
`Maximum sound pool size reached`），所以**最多同时 2 个音乐实例**，不许再叠。

### D9. 原版音乐用 `PlaySoundEvent` 抑制，不写 mixin

原版 `MusicManager.tick()` 完全不认识我们的实例，会在 `nextSongDelay` 到点后
直接开一首情境音乐。两种掐法：

| 做法 | 代价 |
|---|---|
| 每 tick `getMusicManager().stopPlaying()` | 每次 `nextSongDelay += 100`，战斗期间一路推高，战后最长 20 分钟没有原版音乐 |
| **`PlaySoundEvent` 里 `setSound(null)`** | 无副作用，精确到实例 |

采用后者。判定条件收窄为"`SoundSource.MUSIC` **且** 事件 id 属于原版情境音乐集合
（`minecraft:music.game` / `.creative` / `.menu` / `.end` / `.dragon` / `.under_water` /
`.nether.*`）"，因此唱片机、其它 mod 的音乐不受牵连。

只有音乐播放期间才拦截；`BossBgmDirector` 结束时放行。
`Minecraft.getMusicManager()` 与 `MusicManager.stopPlaying()` 都是 public（已核对），
但本变更不依赖它们，只在开始时调用一次 `stopPlaying()` 立刻掐掉正在响的那首。

### D10. 生命周期用"存活探测 + 显式停止"双保险

| 事件 | 处理 |
|---|---|
| 客户端 tick（每 tick） | 推进 `BgmFade`；每 10 tick 重扫候选 |
| `BgmSound.tick()` | 包络推进、停止时 `stop()` |
| `SoundManager.isActive(instance)` 为 false | 视为被引擎丢弃（含 `soundEngine.reload()`），按当前曲目重起 |
| 断线 / 退出世界 | `Audio.reset()` 里停掉全部音乐实例 |
| BOSS 死亡 / 超距 / 换维度 | 淡出（2.5 s）后释放 |
| 玩家死亡 | 不中断：BOSS 还在，曲子继续（与原版一致） |

**为什么要存活探测**：`SoundManager.apply()` 末尾会 `soundEngine.reload()`，
换资源包（`F3+T`）或任何 reload 都会把正在播的实例扔掉，而 director 毫不知情。
存活探测顺手把"换包后自动用新文件恢复"这件事也解决了。

### D11. 测试分层：纯逻辑与 Minecraft 类分离

| 层 | 内容 | 方式 |
|---|---|---|
| 纯逻辑 | 选曲器、`BgmFade`、目录表 | 纯 JUnit |
| 资源 | `sounds.json` ↔ 注册表 ↔ `.ogg` 四方一致 | 读 classpath / 源文件 |
| 名册 | 每只 `TouhouBoss` 在名册内或显式豁免 | 源码扫描 + 豁免表（棘轮） |
| 门面 | `Audio.startMusic` 的请求内容 | `RecordingAudioSink` |
| 客户端 | 实际听感、crossfade、原版音乐抑制 | 实机，不在自动化范围 |

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| **jar 从 5 MB 涨到约 27 MB** | 已实测：q4 = 22.1 MB；q3 可省 3 MB。音乐是产品本体，不拆包 |
| **t4 长 8:39，长度与战斗时长不匹配** | 不剪：曲子是作品，循环点靠"整首重播"承担；只登记为已知现象 |
| **循环接缝有回落（t5 末尾 4 秒渐弱）** | **已接受**：不做无缝循环，不为此剪辑音乐结构；回落在素材记录里写明，不当作缺陷 |
| **资源包作者不知道要 `replace: true`** | 文档第一条 + 只教两条路径；`sounds.json` 追加语义写进 design 与文档 |
| **音乐滑条为 0 的用户听不到** | 预期行为（与原版一致），写进 FAQ；不另立滑条 |
| **`bossTier()` 仍是占位表** | BGM 跟随阶级，阶级变了歌就变；这是占位期的既定代价，不另设映射 |
| **两只 BOSS 时两名玩家听到不同曲子** | 与原版音乐一致的本地性；接受，不广播 |
| **同名事件 id 未来被新 BOSS 复用导致"不重开"** | 这通常是对的（同阶同曲）；若要强制重开，加一只 BOSS 的事件 id 即可 |
| **`AudioCues.validate()` 把音乐条目判成孤儿** | 本变更必须同步扩展孤儿校验，并让音乐条目豁免 subtitle 要求 |

## Migration Plan

新增为主，无存量迁移：

1. 素材加工（裁剪静音 → 响度归一 → Vorbis q4 → 落 `assets/.../bgm/`）。
2. 目录与名册（`BossBgms`）+ `sounds.json` 条目 + 音乐 id 注册。
3. `AudioCues.validate()` 扩展孤儿校验；补充 `SoundIds` 音乐清单。
4. 纯逻辑（`BossBgmSelector` / `BgmFade`）+ 测试。
5. `AudioSink.startMusic/stopMusic` + `BgmSound` + director + 原版音乐抑制。
6. 对外文档 `docs/boss-bgm-pack.md`；实机验收。

回滚：删除 `audio/bossbgm` 与 `audio/client` 的三个新类、`sounds.json` 音乐条目、
5 个 `.ogg`、音乐 id 清单即可回到"战斗静音"；无存档与网络格式变更。

## 实现偏离（落地后回填）

三处与设计稿的字面差异，语义不变，记录以免归档后被误读：

1. **音乐句柄由门面创建，后端只负责播放**：实际签名是
   `AudioSink.startMusic(ResourceLocation eventId, BgmTrack track, MusicHandle handle)`。
   淡变状态放在 `MusicHandle` 里（而不是后端），这样后端为 no-op 时句柄行为依旧确定，
   测试也能直接断言"该淡出了"。`eventId` 与 `track` 分开传，是因为单只 BOSS 的事件 id
   与它使用的阶音频文件不是同一个 id。
2. **director 自注册**：`BossBgmDirector` 与 `VanillaMusicSuppressor` 各自带
   `@EventBusSubscriber(value = Dist.CLIENT)`，没有往 `AudioClientBootstrap` 里塞静态调用。
   与既有 `client/*` 的写法一致（1.21.1 的 `@EventBusSubscriber` 会自动按事件类型分派总线）。
3. **选曲阈值按距离比较**：`BossBgmSelector` 内部存的是**平方**距离，故换曲判据是
   `bestDist² < currentDist² × 0.75²`，对外语义仍是"近 25% 才换"。

## Open Questions

无。以下按本设计执行，实机后作为微调项：

- 进入 32 / 退出 48 / sticky 100 tick / 扫描 10 tick 为初值。
- 淡入 2 s、淡出 2.5 s、crossfade 2 s。
- 全局基准音量 0.85（响度已在素材侧归一，播放侧不再逐曲调）。
- 不追求无缝循环：曲末收尾原样保留，接缝回落为已知取舍（见 D7）。
