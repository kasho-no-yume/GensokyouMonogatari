## 1. 程序音效投影与去重

- [x] 1.1 `ProgramRenderView.Sound` 增加 `scopeKey` 与 `position`（程序 `Vec3`，取 batch source frame origin）。
      **验收**：现有编译通过；`ProgramSoundLedgerTest` 按新构造器更新。
- [x] 1.2 在 `ProgramRenderView.capture` 为每条 `Presentation.SoundCue` 计算 scope：
      `MEMBER_AGE` 用 `group/ordinal`，`GROUP` 用 group id，`CYCLE` 用 `clocks.cycle()`，
      `CARD` 用 `clocks.card()`；scope 内再带时钟域与 cue tick。
      **验收**：新增/更新单测覆盖四个域，同一作用域只派发一次，跨轮/跨批分别派发。
- [x] 1.3 `ProgramSoundLedger.accept` 改为按 `epoch + scopeKey + dedupeKey` 去重；
      epoch 变化时清空。
      **验收**：`ProgramSoundLedgerTest` 覆盖 MEMBER、CYCLE、CARD 与 epoch 重置。

## 2. 程序渲染器与切牌音迁移

- [x] 2.1 `client/ProgramDanmakuRenderer` 删除 `SimpleSoundInstance` /
      `BuiltInRegistries.SOUND_EVENT` 直放，改经 `ProgramSoundPlayer`
      （`AudioCues.byId` + `Audio.playAt`）；未知 id 跳过并每个 id 只记一次诊断；
      程序 `Vec3` 转 MC `Vec3`。
      **验收**：`AudioBoundaryTest` 扫描范围内零直放；程序 cue 能请求到 `BOSS_BURST` / `WARNING_SHIFT`。
- [x] 2.2 `client/BossPhaseTransition.playDeclareSound` 改为
      `Audio.play(AudioCue.SPELLCARD_SWITCH)`，删除原版常量与直放 import。
      **验收**：切牌时请求一次 `SPELLCARD_SWITCH`，演出逻辑不变。
- [x] 2.3 清空 `src/test/resources/audio-playback-allowlist.txt`；修正
      `AudioBoundaryTest` 的“清单不应为空”断言，使空清单成为合法终点。
      **验收**：空清单 + 零违规时测试通过；清单内残留条目仍会使测试失败。

## 3. 玩家武器开火音与激光持续音

- [x] 3.1 新增 `audio/WeaponShotCue`（NORMAL / FAST / KNIFE / LASER / LASER_BEAM），
      提供 `forKind(DanmakuKind, boolean, double)` 纯映射；`WeaponFiring.cueFor(FirePattern)`
      负责从弹种/激光标志/持续秒数适配。
      **验收**：单测覆盖单发玉、散弹、麟弹、飞刀、灵符、激光枪、激光炮。
- [x] 3.2 `AbstractDanmakuProjectile` 增加同步字节 `DATA_SHOT_CUE`（0 = 无）与 getter/setter。
      **验收**：客户端 spawn 后能读到 cue；服务端 Boss / 玩家符卡弹保持 0。
- [x] 3.3 `WeaponFiring.fire` 按弹种设置 cue：非激光只在 `i == 0` 的第一颗弹设置；
      激光 / 灵符单实体设置；激光炮按 `laserDurationSeconds >= 2.0` 选 `LASER_BEAM`。
      **验收**：散弹一次扳机只产生一颗带 cue 的弹；灵力不足时不生成弹也不带 cue。
- [x] 3.4 新增客户端 `EntityJoinLevelEvent` 处理器：带 cue 的弹 `Audio.playAt`；
      激光类跳过，由激光实体自己管理。
      **验收**：多人开火各自听到一次；服务端无任何播放调用。
- [x] 3.5 `LaserDanmaku` 增加客户端持续音：激光枪在 ACTIVE 起始播放 `LASER_SHOOT`；
      激光炮只起 `LASER_BEAM`（长、低沉的循环），`keepAlive` 每 tick，到期或 `remove()` 时 `stop`。
      `BeamSound` 必须给引擎非零音量，避免起音 0 被 `SoundEngine` 跳过。
      **验收**：激光炮只有长低沉循环、不叠短促音；激光枪只有一次性开火音；实体移除后无残留 loop。

## 4. 大妖精 / 山女内容 cue

- [x] 4.1 `BigFairyPrograms.flower()` 的共享 presentation 增加
      `MEMBER_AGE 0 → boss_burst` 与 `CYCLE 100 → warn_shift`。
      **验收**：一轮 16 声 `boss_burst` + 1 声 `warn_shift`；花环/雨季 0 声。
- [x] 4.2 `YamamePrograms.spinner()` 增加 `MEMBER_AGE 0 → boss_burst`。
      **验收**：捕获之网 10 声/轮、石窟蜘蛛巢 13 声/轮；追丝扇 / 吐丝 / 尾珠不触发。
- [x] 4.3 `YamamePrograms.miasma()` 的螺旋 presentation 增加 5 条
      `CYCLE 0/60/120/180/240 → boss_burst`。
      **验收**：每轮 5 声，同波 5 个目标槽合并为 1 声。
- [x] 4.4 `YamamePrograms.fever()` 的中心/左/右螺旋 presentation 增加 6 条
      `CYCLE 0/50/.../250 → boss_burst`（同 key）。
      **验收**：每轮 6 声，同波 15 组合并为 1 声。
- [x] 4.5 内容测试：断言花海 16+1、山女 10/13 + 5/6，并断言 chase / spit / mist /
      花环 / 雨季不产生新增符卡音效。
      **验收**：`ProgramContentTest` 或新增测试全绿。
- [x] 4.6 `BigFairyPrograms.wall()` 增加 `MEMBER_AGE 0 → shot_normal`。
      **验收**：默认弹墙每组一声，每 60 tick 一组；不按 25 颗弹重复。
- [x] 4.7 `YamamePrograms.background()` 增加 `MEMBER_AGE 0 → shot_normal`。
      **验收**：默认底噪蛛丝扇每组一声，每 80 tick 一组；不按 3 颗弹重复。

## 5. 预览基线与构建验证

- [x] 5.1 重新导出 `preview/data/big_fairy_2.json`、`yamame_1..4.json`。
      **验收**：`tools/gradle_task.ps1 exportDanmakuPreview` 后 `--check` 通过。
- [x] 5.2 跑 `tools/gradle_task.ps1 test` 与 `tools/gradle_task.ps1 build`。
      **验收**：全部通过；`openspec validate add-audio-consumers --strict` 通过。

## 6. 实机验收（不在自动化范围）

- [x] 6.1 花海：听到 16 次甩花声与 1 次整体警告；花环/雨季安静。
- [x] 6.2 山女：蛛丝每批一声、螺旋每波一声；追丝扇/吐丝/毒雾/底噪安静。
- [x] 6.3 七种弹核各开火一次，确认 cue 正确；散弹一次扳机一声；激光炮有持续音、
      激光枪没有；灵力不足时不响。
- [x] 6.4 切牌一次，确认 `spellcard.switch` 播放且演出不中断。
- [x] 6.5 记录：蓝卡 5 既有原版 cue 静默（本次有意），以及读档后可能按血量重新选卡。
