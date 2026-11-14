## 1. 素材加工（5 首 → Vorbis）

- [x] 1.1 新建 `assets/gensokyou/sounds/bgm/`，把 5 首按 **ASCII 文件名** 落位：
      `tier1.ogg` … `tier5.ogg`（t1→tier1 … t5→tier5）。
      **MUST NOT** 保留日文原名（Windows 与 git 的路径编码会带来无谓故障）。
      **验收**：目录下恰好 5 个 `.ogg`，文件名全小写 ASCII。
- [x] 1.2 **裁剪首尾静音**（不做音乐结构编辑）：首尾低于 −50 dBFS 的段剪掉。
      ```bash
      ffmpeg -i "T5.mp3" -af "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.1,\
      areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.1,areverse" \
        -ar 44100 -ac 2 -c:a libvorbis -q:a 4 tier5.ogg
      ```
      **验收**：`tier5` 开头不再有 3 秒空白、结尾不再渐弱到无；其余四首首尾静音 < 0.15 s。
- [x] 1.3 **响度归一**到 −16 LUFS ±1，真峰 ≤ −1.5 dBTP（用实测增益，不用动态压缩）。
      先测：`ffmpeg -i X.ogg -af loudnorm=print_format=json -f null -`，再按差值应用 `volume=<±dB>dB`。
      **验收**：五首 `ebur128` 积分响度落在 −17…−15 LUFS；`volumedetect` 的 `max_volume` ≤ −1.5 dB。
- [x] 1.4 循环处理：**不追求无缝循环**（用户决定）。首部静音已裁，曲末自然收尾原样保留；
      tier5 尾部约 4 秒淡出带来的接缝回落登记为已知取舍。
      **验收**：判定与回落说明写入 `NOTICE.txt` 第 3 节；不作为缺陷、不触发校验失败。
- [x] 1.5 `assets/gensokyou/sounds/bgm/NOTICE.txt`：每首的源文件名、作者/授权范围
      （随 mod 二进制分发 / 转码 / 剪辑 / 署名串）、加工链（ffmpeg 参数与响度实测值）。
      **验收**：五首逐条可追溯；授权范围四条齐全。
- [x] 1.6 体积复核：成品 5 个文件合计 ≤ 24 MB。
      **验收**：实测合计体积记录在 `NOTICE.txt`；超出则改 `-q:a 3` 并重测。

## 2. 目录、注册与 sounds.json

- [x] 2.1 `audio/bossbgm/BgmTrack`：曲目描述体（事件 id、音频路径、基准音量）。
      5 个常量 `TIER1..TIER5`，事件 id 为 `gensokyou:bgm/tierN`。
      **验收**：纯 JUnit 可加载（不引用 Minecraft 类）。
- [x] 2.2 `audio/bossbgm/BossBgms`：**唯一真值表**——阶级 → 曲目缺省映射、
      BOSS 实体 → 事件 id 名册、测试 BOSS 豁免表（附理由）。
      名册外的 `TouhouBoss` 回落 `bgm/tier{n}`。
      **验收**：`byEntity(id)`、`forTier(n)`、`excluded(id)` 三个查询可枚举且确定。
- [x] 2.3 `SoundIds` 新增 `MUSIC_NAMES` 清单（与既有 `NAMES` 分开）；
      `ModSounds` 把两者都注册进同一个 `SOUNDS`，`ALL` 为并集，另暴露 `SFX` / `MUSIC` 子视图。
      **验收**：`SoundIds.NAMES` 与 cue 事件 id 的等值断言（`AudioCatalogTest`）仍然通过。
- [x] 2.4 `sounds.json` 追加 5 条阶事件 + 每只名册 BOSS 一条事件；
      每条 `sounds` 为单元素、带 `"stream": true`，且指向对应阶的音频路径（**不复制文件**）。
      **MUST NOT** 写 `subtitle`（原版音乐也没有）。
      **验收**：除 5 个 `.ogg` 外无新增音频文件；N 条事件指向 5 个文件。
- [x] 2.5 `AudioCues.validate()` 扩展：`sounds.json` 的孤儿校验 MUST 把音乐事件视为已声明；
      音乐条目 MUST NOT 被要求提供 `subtitle`；音乐事件仍 MUST 有 `.ogg` 且已注册。
      **验收**：`AudioCatalogTest` 与加载期 `AudioBootstrap` 的 ERROR 列表为空。

## 3. 纯逻辑与测试

- [x] 3.1 `audio/bossbgm/BossBgmSelector`：输入候选快照（uuid / tier / 距离平方 / 是否锁定我），
      输出曲目（或空）。实现最近优先 + sticky 100 tick + 距离优于 25% 才换。
      **验收**：单测覆盖"单只""两只异阶""两只同阶抖动不切歌""锁定我优先于更近的没锁我"。
- [x] 3.2 `audio/bossbgm/BgmFade`：淡入 / 常驻 / 淡出三段，感知曲线 `p^1.7`，
      起音下限非零，停止幂等。
      **验收**：单测覆盖淡变时长下限、曲线单调、首帧振幅 > 0、重复 stop 不抛异常。
- [x] 3.3 `BossBgmCatalogTest`：曲目 ↔ `sounds.json` ↔ `SoundIds.MUSIC_NAMES` ↔ `.ogg` 四方一致；
      每条音乐事件必须 `stream: true`。
      **验收**：任一缺失/写错 → 测试失败并指出具体键名。
- [x] 3.4 `BossRosterTest`：扫描 `src/main/java/.../entity/` 中实现 `TouhouBoss` 的类，
      断言每只都在 `BossBgms` 名册内或在豁免表中；豁免条目若已不再命中 → 失败（棘轮）。
      **验收**：新增一只 BOSS 却不入册时测试红；删除豁免对象却不删条目时测试红。
- [x] 3.5 `RecordingAudioSink` 断言：选曲 → `startMusic` 收到正确 `BgmTrack`；
      换曲时旧句柄被 `stopMusic`。
      **验收**：不需要启动游戏。

## 4. 客户端播放层

- [x] 4.1 `AudioSink` 新增 `startMusic(BgmTrack, int fadeInTicks)` / `stopMusic(handle, int fadeOutTicks)`
      与 `MusicHandle`；默认实现 no-op，`NONE` 同步补齐。
      **验收**：未安装后端时所有调用为 no-op，不抛异常。
- [x] 4.2 `audio/client/BgmSound`：`AbstractTickableSoundInstance`，`SoundSource.MUSIC`、
      `Attenuation.NONE` + `relative = true`、`looping = true`，音量/包络来自 `BgmFade`；
      `getVolume()` MUST 通过非零下限进入引擎。
      **验收**：首帧可入队（不被 `SoundEngine` 的零音量规则吞掉）；停止后不再 tick。
- [x] 4.3 `ClientAudioSink` 实现两个新入口；`reset()` 一并停掉音乐实例与 `BgmSound`。
      **验收**：断线后 `beams` 与音乐集合均为空。
- [x] 4.4 `audio/client/BossBgmDirector`：每 tick 推进淡变、每 10 tick 扫候选；
      进入 32 / 退出 48；换曲事件 id 变化时才 crossfade；同 id 不重开。
      **验收**：切符卡不重开；换 BOSS 才 crossfade；单只跑动不抖。
- [x] 4.5 存活探测：`soundManager.isActive(instance)` 为 false 时按当前曲目重起。
      **验收**：`F3+T` 换资源包后 2 tick 内恢复播放，且用的是新文件。
- [x] 4.6 注册接线：`AudioClientBootstrap` 增加 director 的客户端 tick 与断线清理。
      **验收**：专用服务器不加载 `BossBgmDirector` / `BgmSound`。

## 5. 原版音乐抑制

- [x] 5.1 `VanillaMusicSuppressor`：`PlaySoundEvent` 中，当音乐正在播放且实例为
      `SoundSource.MUSIC`、事件 id 属于原版情境音乐集合时 `setSound(null)`。
      **验收**：BOSS 战期间听不到 `music.game` 等；唱片机与其它来源不受影响。
- [x] 5.2 开始音乐时调用一次 `getMusicManager().stopPlaying()`，结束后放行。
      **验收**：战斗结束 10 秒内原版音乐可正常回来（不被 `nextSongDelay` 推到十几分钟）。

## 6. 对外文档

- [x] 6.1 `docs/boss-bgm-pack.md`：资源包目录结构、两条替换路径（整阶 / 单只）、
      **`"replace": true` 的追加语义陷阱**、采样率/声道/流式要求、常见故障（没声音的三种原因）。
      **验收**：照文档做两个最小示例包，均能在实机生效。
- [x] 6.2 在 `NOTICE.txt` 与 README/引导书的相关位置登记曲目与授权。
      **验收**：游戏内与仓库内都能查到署名。

## 7. 构建与验证

- [x] 7.1 `.\tools\gradle_task.ps1 test` 与 `.\tools\gradle_task.ps1 build`。
      **验收**：全绿；`openspec validate add-boss-battle-bgm --strict` 通过。
- [x] 7.2 体积与启动复核：jar ≤ 28 MB；专用服务器启动日志无音频相关 ERROR。
      **验收**：`build/libs` 实测大小与启动日志各留一条记录。

## 8. 实机验收（不在自动化范围）

- [x] 8.1 依次召唤 T1/T2/T5 BOSS，确认各自曲目、2 秒内淡入、无爆音。
- [x] 8.2 同一场战斗切 3 张符卡，确认音乐不重开。
- [x] 8.3 T1 与 T2 同时在场，走位使二者距离反复变化，确认不来回切歌。
- [x] 8.4 战斗中 `F3+T` 换资源包，确认自动恢复且切到新文件。
- [ ] 8.5 装一个只换 `tier2.ogg` 的最小资源包，确认 T2 全换、其它阶不变。
      **暂不验证**（用户决定：资源包路径收益低，以后单独验）。
- [ ] 8.6 装一个用 `replace: true` 只换单只 BOSS 的资源包，确认同阶其它 BOSS 不变。
      **暂不验证**（同上）。实现与文档已就绪，风险是"文档与实机行为不一致"。
- [x] 8.7 断开/退出世界/死亡，确认无残留循环、原版音乐能恢复。
- [x] 8.8 把音乐滑条调 0 再调回，确认 BGM 随之静音/恢复（预期行为）。
