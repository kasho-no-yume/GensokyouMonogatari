> **本变更只交付基础设施：它自己不发声，也不接任何消费点。**
> 顺序是「先证明校验能失败 → 建骨架与资源 → 限流/降级/持续音 → 专用服务器与全量验证」。
> 任务里的「调用点」一律指**测试内的模拟调用**，不是任何游戏代码。

## 0. 先证明校验能失败

- [x] 0.1 写「唯一出口 + 豁免清单」的静态扫描测试：
      扫描 `client/`、`danmaku/`、`entity/`、`item/weapon/`、`network/`，
      禁止 `playSound(` / `playLocalSound(` / `getSoundManager(` / `SimpleSoundInstance` /
      `BuiltInRegistries.SOUND_EVENT`；豁免清单初值 = `BossPhaseTransition`、
      `ProgramDanmakuRenderer` 两条（各带理由）。
      **先让它在清单外的一段临时违规代码上变红**。
      **验收**：删掉临时违规 → 绿；恢复 → 红；把清单内某文件的直放改掉却留着条目 → 红。
- [x] 0.2 写「注册表 ↔ sounds.json ↔ ModSounds ↔ .ogg」四方一致性测试，
      并先对一个虚构的缺条目 cue 变红。
      **验收**：先看到红，再看到绿。

## 1. 骨架（无素材也能跑）

- [x] 1.1 新增 `com.bitsson.gensokyou.audio`：`AudioCue`（enum）、`AudioCues`（注册表）、
      `Audio`（门面）、`AudioSink`（接口）、`SoundLookup`（id → SoundEvent 解析口）。
      `AudioCue` 只存 `ResourceLocation`，MUST NOT 引用 `SoundEvents` / `SoundEvent` 常量。
      **验收**：`AudioCue` 可在纯 JUnit 下加载（无 registry bootstrap）。
- [x] 1.2 `AudioCues` 登记 8 条 cue 的全部参数（D3 字段 + D7 表），
      并提供 `byId(ResourceLocation)`。
      **验收**：`byId` 对 8 个 id 均返回对应常量；未知 id 返回 empty。
- [x] 1.3 写 `RecordingAudioSink`（test 侧）：只记录 cue、位置/实体、实际音量/音高/类别。
      **验收**：此时游戏仍静默，但测试能断言「该 cue 被请求了、参数是 X」。
- [x] 1.4 生产 `AudioSink` 实现：单独成类、客户端初始化时安装；
      MUST NOT 在专用服务器加载客户端类；未安装时所有入口为 no-op。
      **验收**：专用服务器启动 MUST NOT 因音频包崩溃、MUST NOT 出现客户端类加载。
- [x] 1.5 全链路 try/catch：任何音频异常 MUST NOT 外溢到调用方。

## 2. 素材与注册

- [x] 2.1 把 8 个 `.ogg` 从 `F:\Comfy-Desktop\sfx-lab\final\` 放入
      `src/main/resources/assets/gensokyou/sounds/`（保持 `sounds.json` 引用的文件名）。
- [x] 2.2 新增 `registry/ModSounds`：`DeferredRegister<SoundEvent>` 注册 8 个事件，
      并在 `Gensokyou` 构造器挂到 mod bus。
      **验收**：`ModSounds` 的 id 集合与 `AudioCues` 的事件 id 集合相等。
- [x] 2.3 写 `sounds.json`，8 个事件，每个事件 MUST 至少 1 个 variant；
      每个事件 MUST 带 `subtitle`。
      **验收**：0.2 的四方一致性测试转绿。
- [x] 2.4 补 `subtitles.*` 的 `zh_cn` 与 `en_us` 文案（8 条）。
      **验收**：两套 lang 键集合一致，且与 `sounds.json` 的 subtitle 引用一致。
- [x] 2.5 加 `NOTICE.txt`：生成模型与版本、prompt、后处理链、来源脚本路径。
      **说明**：当前为非商用，该文件**不是合规强制**，目的是可追溯与将来迁移可复现。
      **验收**：MUST 能从该文件复现任意一条素材的生成参数。
      **落地备注**：6 条已固化到脚本；`laser_beam` 的原始机器嗡鸣、`boss_burst` 的噪声底、
      `warn_shift` 的 b3 参数、`spellcard_switch` 的 mid 参数在源脚本中没有记录，
      NOTICE 已逐条标注「⚠ 缺口」与已知可复现部分（未伪造数值）。
- [x] 2.6 确认 `laser_loop.ogg` 不设 `stream`（47 KB，缓冲播放即可），
      其余 7 条同样不设。

## 3. 限流（tick 计量）

- [x] 3.1 `AudioRateLimiter`：每 cue 最小间隔（tick）+ 每客户端 tick 全局新增上限（初值 4）。
- [x] 3.2 信息类 cue（`warning.shift`、`spellcard.switch`）不计入全局上限。
      **验收**：上限打满时，信息类 cue 仍 MUST 出声。
- [x] 3.3 `Audio.onClientTick()` 作为 tick 来源；无 tick 驱动时退化为「一批只处理一次」。
      **验收**：同一 tick 内触发 100 次 `shot.fast`（`minIntervalTicks=2`），
      实际出声次数 MUST 不超过限流上限；跨 tick 的第二次触发 MUST 被吞且不排队。
- [x] 3.4 被吞掉的触发 MUST NOT 抛异常、MUST NOT 留下待补播队列。

## 4. 降级

- [x] 4.1 每条 cue 登记 `placeholderId`（原版音效 id）。
- [x] 4.2 目标缺失 → 占位；占位也缺 → 静默 + 一次含 cue 名的日志，MUST NOT 抛异常。
      **验收**：用 `SoundLookup` 假实现分别让目标/占位解析失败，断言请求落到占位、
      最终静默且日志含 cue 名。
- [x] 4.3 加载期校验失败（缺条目 / 缺文件 / 孤儿条目 / id 集合不等）
      MUST 记录 ERROR 并降级，MUST NOT 抛异常；同一校验的单元测试 MUST 失败。

## 5. 持续音

- [x] 5.1 `BeamEnvelope`（纯类）：三段推进，每段 ≥ 3 tick，振幅走 `p^1.7`。
- [x] 5.2 `BeamSound` 实现 `TickableSoundInstance` + `setLooping(true)`，
      只负责把 `BeamEnvelope` 的值喂给 `getVolume()/getPitch()`。
- [x] 5.3 音高斜坡 0.85 → 1.0（约 120 ms），可由 cue 参数关闭。
- [x] 5.4 `Audio.startBeam(...)` 返回句柄；句柄 MUST 支持显式 `stop()`；
      重复 `stop()` MUST 幂等。
- [x] 5.5 看门狗：连续 10 tick 无存续信号即自行停止。
      **验收**：纯 JUnit 造一条「永不再喂信号」的包络，MUST 在 10 tick 内进入 TERMINATED
      且 `isStopped()` 为真；长期占用声道的路径 MUST 不存在。
- [x] 5.6 接缝无跳变：`laser_loop.ogg` 循环 60 秒，接缝样本差 MUST 不超过整段
      99.9% 分位。
      **验收**：离线读取 ogg 采样做断言（不依赖游戏启动）。

## 6. 验证

- [x] 6.1 全量测试通过，既有期望值零改动。
- [x] 6.2 专用服务器启动无音频相关异常、无客户端类加载。
- [x] 6.3 `openspec validate add-audio-cue-system --strict` 通过。
- [x] 6.4 复核交付物不含任何消费点改动：
      `git diff --stat` MUST 只在 `audio/`、`registry/ModSounds`、`Gensokyou`（一行注册）、
      `assets/gensokyou/`、`src/test/` 与 openspec 文档内。
      **落地备注**：本变更自身的改动确实只落在上述路径；工作区里另有此前就存在的
      `RumiaPrograms` / `preview/data` / 程序内容测试等改动，不属于本变更，未触碰。
