## Context

本仓库战斗表现层的音效现状，以及本变更的边界：

```
┌──────────────── 本变更交付（基础设施） ─────────────────┐
│  audio/  AudioCue  AudioCues  Audio  AudioSink           │
│          SoundLookup  AudioRateLimiter  BeamEnvelope     │
│          BeamSound                                       │
│  registry/ ModSounds（8 条 SoundEvent）                  │
│  assets/  sounds.json  sounds/*.ogg  NOTICE.txt          │
│  lang/    subtitles.*（zh_cn + en_us）                   │
│  test/    出口扫描 / 三方一致 / 限流 / 包络 / 降级        │
└──────────────────────────────────────────────────────────┘

┌──────────────── 明确留给后续消费变更 ───────────────────┐
│  BossPhaseTransition 接 spellcard.switch                 │
│  ProgramDanmakuRenderer / ProgramSoundLedger 接程序音效   │
│  fromWeapon 同步位、WeaponFiring 服务端出声               │
│  laser.* / shot.* / knife.throw / boss.burst / warning.*  │
│  的挂点与实机调音                                         │
│  仪式·物品存量 playSound 迁移                             │
└──────────────────────────────────────────────────────────┘
```

相关既有约束：

- `boss-phase-transition` 已有 requirement「切阶段音效」：切换符卡 SHALL 播放一次声明音效；
  音效 MUST NOT 影响判定、MUST NOT 抛到服务端；缺自制资源时 SHALL 用登记的占位音效，
  MUST NOT 静默。**本变更只提供满足该要求的出口，不接该调用点。**
- `project.md` §3 的验收文化是「可离线断言」。
- 技术环境：Minecraft 1.21.1 / NeoForge / Java 21。客户端 `SoundEngine` 的音量更新
  是**每 tick 一次（20 Hz）**，不是每帧。

## Goals / Non-Goals

**Goals:**

1. 所有生产出声只有一个入口，混音参数（音量/音高/间隔/类别）集中在注册表。
2. 调用点只表达语义，不表达参数。
3. 可在离线测试中断言「该出声时请求了哪个 cue、参数是什么」。
4. 重复触发不会打爆声道。
5. 资源缺失时降级到登记的占位音效，且永不抛异常。
6. 持续音（激光）支持按住任意时长且接缝无跳变。
7. 专用服务器不加载任何客户端类。

**Non-Goals:**

- 不接消费点；不决定任何 cue 的挂点。
- 不做 BGM / 动态混音总线。
- 不做「好不好听」的自动判定。
- 不引入新的网络包类型。

## Decisions

### D1. 唯一出口 `Audio` 门面 + 可替换 `AudioSink`

`Audio` 只做三件事：查注册表、套参数与限流、交给 `AudioSink`。
`AudioSink` 是唯一接触 `Minecraft` 客户端的接口，生产实现走
`ClientLevel.playLocalSound(...)`，测试实现只记录调用。

- **为什么不是静态工具类直接调客户端**：静态直调无法离线断言，
  且会把客户端类拖进任何调用方的类加载路径（专用服务器启动即崩）。
- **未安装 sink 时**：`Audio` 的所有入口为 no-op 并只记录一次 debug。专用服务器即此状态。
- **副作用**：`audio` 包之外出现直接播放调用即可判定违规（见 D9）。

### D2. 门面不出网

`Audio` 的三种入口（`play` / `playAt` / `playOn`）只作用于**本客户端**，
MUST NOT 发包、MUST NOT 触碰服务端状态、MUST NOT 参与同步。

- **为什么**：「客户端推导事件、不新增网络包」是一个**消费侧**决策；本变更不替它做。
  门面只需保证「就算将来消费侧决定用服务端广播，也只需新增一个 `AudioSink` 实现，
  而不改 cue 注册表」。
- **代价**：本变更交付后游戏仍然没有新声音（唯一的变化是 `BossPhaseTransition` 之外
  不会多出任何声音）。这是预期状态。

### D3. cue 用 enum，数据按 id 反查

`AudioCue` 是 `enum`，每个常量携带：

```
eventId          ResourceLocation   （gensokyou:shot_normal）
baseVolume       float
basePitch        float
pitchJitter      float
minIntervalTicks int               （见 D4）
source           SoundSource        （PLAYER / HOSTILE / UI / ...）
positional       boolean
subtitleKey      String
placeholderId    ResourceLocation   （原版事件，如 minecraft:block.amethyst_block.chime）
informational    boolean            （信息类，不占每 tick 全局额度）
```

- **为什么 enum 而不是散落字符串**：编译期就能发现拼错；IDE 能列出全部 cue；
  注册表遍历可直接用于校验。
- **为什么占位音效存 `ResourceLocation` 而不是 `SoundEvent`**：
  `SoundEvent` / `SoundEvents` 会把 Minecraft 类拖进 enum 的静态初始化，
  纯 JUnit（无 registry bootstrap）就无法加载 `AudioCue`。
  存 id 后由 `SoundLookup` 在实现侧解析，测试侧可模拟「解析失败」。
- **数据驱动消费方**：额外提供 `AudioCues.byId(ResourceLocation)`，供后续把
  `Presentation.SoundCue` 这类数据里的字符串 id 解析成 cue。本变更只提供方法，
  不做接线，也不改 `Presentation.SoundCue` 的 record 形状（避免动 codec 与存档 hash）。

### D4. 限流按 cue 做令牌桶，且每 tick 有全局上限（全部以 tick 计量）

两把闸：

- **每 cue 最小间隔**（tick）：`shot.fast` 2、`boss.burst` 3、其余 0。
- **每客户端 tick 全局上限**：最多 N 个新实例（初值 4），信息类 cue 不计数。

- **为什么统一用 tick**：客户端音量更新本身就是 20 Hz；用毫秒只会在 50 ms 边界上
  引入「抛硬币」式的取整差异，且每 tick 上限天然是 tick 计量。
- **为什么需要**：Minecraft 声道池有限，超量是**静默丢弃**，表现为「打快了突然没声」。
- **被吞掉的触发不补偿、不排队、不抛异常**：音效是表现层。
- **tick 来源**：客户端 tick 处理器调用 `Audio.onClientTick()`；门面在无 tick 驱动时
  退化为「同一 tick 内只处理一批」，避免依赖 `Minecraft` 时钟。

### D5. 持续音用 `TickableSoundInstance`，包络计算拆成纯函数

`BeamSound` 实现 `TickableSoundInstance` + `setLooping(true)`；
`BeamEnvelope` 负责推进三段：起音 → 循环 → 收尾。

关键约束（**由 20 Hz 的更新频率决定，不是审美选择**）：

- 每段 MUST ≥ 150 ms（3 tick）。低于 3 tick 的淡变会变成阶跃，听感是「啪」甚至爆音。
- 曲线 MUST 用感知曲线（振幅取 `p^1.7`）。`getVolume()` 在 MC 里是**振幅**；
  线性拉满再拉零，人耳听到的是前半段几乎没变、末尾骤降。
- 音高斜坡（0.85 → 1.0，约 120 ms）是可选增强，默认开启，可由 cue 参数关闭。
- 生命周期：`Audio.startBeam(...)` 返回句柄；消费方 MUST 显式 `stop()`；
  另加看门狗：连续 10 tick 未收到存续信号即自行停止，防止循环实例永久占用声道。

**为什么把包络拆出来**：`TickableSoundInstance` 是客户端类，纯 JUnit 无法实例化。
包络与看门狗是纯状态机，拆出后 5.2 / 5.5 的验收都能离线跑；
`BeamSound` 只剩「把包络值喂给 `getVolume()/getPitch()`」。

### D6. 加载期双向校验，而不是等运行时崩

启动时遍历 `AudioCues`，断言：

1. `sounds.json` 存在 `eventId` 条目；
2. 该条目引用的每个 `.ogg` 文件真实存在于 classpath；
3. 没有孤儿条目（`sounds.json` 里存在但注册表没有）；
4. `ModSounds` 注册的 id 集合与注册表的事件 id 集合相等。

任一失败：**记录 ERROR 并降级到占位音效**，不抛异常（对齐「MUST NOT 打断」）。
同时提供一条单元测试，使 CI 直接红。

- **为什么不在运行时报错**：实机听不到声音时，没人会去看日志。
- **为什么能离线测**：校验读的是 classpath 资源，不依赖客户端资源包加载。

### D7. 素材响度差异用 per-cue 增益补偿，而不是重新生成素材

8 个文件都峰值归一到 -3 dBFS，但感知响度差异极大：0.12 s 的「啵」与 2.6 s 的符卡音
在相同峰值下听感差 15 dB 以上。短音需要更高增益才「听得见」，连发用的音必须压低。

初值（待消费变更实机微调，本变更只登记）：

| cue | baseVolume | 依据 |
|---|---|---|
| `shot.normal` | 1.0 | 极短，需要满增益 |
| `shot.fast` | 0.55 | 高频复用、连发，必须压低否则糊成一片 |
| `knife.throw` | 0.9 | 单发，中等 |
| `laser.shoot` | 0.95 | 极短且是战斗主反馈 |
| `laser.beam` | 0.75 | 持续音，长时间存在必须让位给一次性音效 |
| `boss.burst` | 0.8 | 高频出现 |
| `warning.shift` | 1.0 | 信息类，必须盖过战斗底噪 |
| `spellcard.switch` | 1.0 | 演出节点，唯一允许「隆重」的音 |

类别与定位初值：

| cue | source | positional | informational |
|---|---|---|---|
| `shot.*` / `knife.throw` / `laser.*` | PLAYER | true | false |
| `boss.burst` | HOSTILE | true | false |
| `warning.shift` | PLAYER | true | **true** |
| `spellcard.switch` | PLAYER | **false** | **true** |

### D8. 素材许可：输出归我方，但保留迁移路径

音效由 **Stable Audio 3 small-sfx** 生成（Stability AI Community License）。

按该协议的定义，**模型的输出不是 Derivative Work**：

> "Derivative Work(s)" … **but do not include the output of any Model.**
> "Ownership of Outputs." … **You own any outputs generated** from the Models …

因此只分发 `.ogg` 文件时：

| 情形 | 注册 | 署名 |
|---|---|---|
| 非商用（本仓库当前定位） | 不需要 | 不需要 |
| 商用，年收入 < $1M | 需要 | 建议加，非硬性 |
| 商用，年收入 ≥ $1M | 需单独授权 | — |

结论：**当前用法无附加义务。** 仍在资源目录放一行 `NOTICE.txt` 说明生成工具与模型，
理由是**可追溯性**（将来换模型或需要证明来源时有据可查），不是合规强制。

若将来定位变为商用且年收入可能突破 1M，迁移路径是换 **Apache-2.0** 的音效模型
（如 `MOSS-SoundEffect-v2.0`），代价是全部 8 条素材需要重新生成并重走调音。
故 `NOTICE.txt` 里 SHOULD 记录生成参数（模型版本、prompt、后处理链）。

### D9. 唯一出口用「豁免清单只减不增」落地，而不是要求 baseline 全绿

扫描范围：`client/`、`danmaku/`、`entity/`、`item/weapon/`、`network/`。

禁止模式：`playSound(`、`playLocalSound(`、`getSoundManager(`、`SimpleSoundInstance`、
`BuiltInRegistries.SOUND_EVENT`。

- **为什么禁止模式要扩到 `getSoundManager(` / `SimpleSoundInstance`**：
  现存两处真正的直放点都不含 `playSound(` —— 只按原提案三个模式写，
  测试会对真实的违规恒绿。
- **为什么不禁止裸 `SoundEvents.`**：`registry/ModEquipmentMaterials` 用它作材质装备音，
  是合法常量引用。禁止它只会逼人写无意义豁免。
- **豁免清单是棘轮**：清单每条形如 `path | reason`。测试同时断言
  ①清单外无违规、②清单内每个文件**确实仍违规**。修好一处而不删条目 → 测试红，
  于是清单只会变短。
- **不迁移存量**：本变更不改那两处；它们随消费变更迁移。

### D10. 测试分层：纯逻辑与 Minecraft 类分离

| 层 | 内容 | 测试方式 |
|---|---|---|
| 纯逻辑 | `AudioRateLimiter`、`BeamEnvelope`、`AudioCues` 参数表、`AudioCue` 枚举 | 纯 JUnit，无 bootstrap |
| 资源 | `sounds.json`、`.ogg`、`ModSounds` id 集合、lang 键 | 读 classpath / 源文件 |
| 门面 | `Audio.play/playAt/playOn` 的请求内容 | `RecordingAudioSink` 断言 |
| 降级 | 目标缺失 / 占位缺失 | `SoundLookup` 假实现返回 empty |
| 客户端 | 真实播放、包络听感 | 不在本变更验收内（实机属消费变更） |

- **为什么**：`SoundEngine` / `SoundManager` / `BuiltInRegistries` 在纯 JUnit 下不可用；
  把边界切在接口上，验收才能既离线又不依赖游戏启动。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| **交付后听不到任何新声音，被误判为「没做」** | tasks 首行与 proposal 明写「本变更不发声」；验收清单不含听感项 |
| **`ModSounds` 与 `sounds.json` 漂移** | D6 第四项校验（两个 id 集合相等）+ CI 测试 |
| **豁免清单变成永久垃圾场** | 棘轮断言：清单内文件必须仍违规，修好即强制删除 |
| **限流吞掉关键音效** | 信息类 cue（`warning.shift` / `spellcard.switch`）不占每 tick 全局额度 |
| **20 Hz 包络阶跃** | 下限 3 tick；若实机仍可闻，退路是两段（快起音 + 慢释放） |
| **循环实例泄漏** | 看门狗 10 tick；`BeamEnvelope` 纯状态机可离线验证自停 |
| **专用服务器加载客户端类** | `AudioSink` 生产实现单独成类、只在客户端初始化；专用服务器无 sink → no-op |

## Follow-ups（不属于本变更）

1. **消费变更（切牌音）**：`BossPhaseTransition` 改调 `Audio.play(AudioCue.SPELLCARD_SWITCH)`，
   同批重写 `boss-phase-transition` 的 MODIFIED delta。
2. **消费变更（程序音效）**：`Presentation.SoundCue` 的 `sound` 字段解释为 cue id，
   经 `AudioCues.byId` 解析；求值时机从渲染帧改到 client tick；
   `ProgramSoundLedger` 保留语义去重。
3. **消费变更（玩家射击）**：`fromWeapon` 是否加同步位、或改服务端出声，届时再定；
   本变更不提供任何倾向。
4. **实机调音**：按 D7 表微调 baseVolume，回写 design。

## Migration Plan

无存量调用点迁移，属于纯新增。

回滚：删除 `audio` 包、`ModSounds`、8 条 cue 资源与扫描/一致性测试即可回到现状；
`Gensokyou` 构造器删一行注册。
