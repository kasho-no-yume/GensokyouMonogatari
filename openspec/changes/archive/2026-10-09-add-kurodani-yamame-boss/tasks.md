## 1. 撤销缺段机制

- [x] 1.1 删除 `TrackLint.hasNoAimedTrack` 与 `TrackLint.allTracksEndless` 两个方法
- [x] 1.2 删除 `BossCardLintTest` 中依赖缺段断言的测试：`kuzumonoHasNoAimedTrack`、
      `nomenMaskTracksAreAllEndless`、`endlessCheckRejectsTerminatedTracks`
- [x] 1.3 更新 `BossCards` 类注释与 `Track` / `SpellCard` / `TrackLint` 注释：移除
      「缺段 / 序·破·結 / 残影」叙事；保留 `Track.terminates` / `.endless()` 作为通用能力
- [x] 1.4 全仓搜残留引用（`hasNoAimedTrack` / `allTracksEndless` / 缺段 / 序·破·結）；
      含各实体类（狐火/傩）与 `.opencode/skills/boss-dev-design/SKILL.md` 一并清理
      （主 spec 留待归档步骤回填）

## 2. 资源与渲染

- [x] 2.1 复核 `kurodani.png`：128×128、49 色、Alpha 正常——完整贴图而非占位色块
- [x] 2.2 复核 `kurodani.geo.json`：8 bones / 205 cubes，量级正常，无需简化/LOD
- [x] 2.3 搬运资源到 `assets/gensokyou/{geo,animations,textures}/entity/yamame.*`
- [x] 2.4 新增 `client/model/YamameGeoModel`
- [x] 2.5 新增 `client/renderer/YamameGeoRenderer`（scale 0.93，对齐 0.8×1.7 碰撞箱）
- [x] 2.6 `client/GensokyouClient` 注册山女渲染器；`RemnantBossRenderer`/`GensokyouTextures`
      移除 kuzumono

## 3. 实体与注册

- [x] 3.1 新增 `entity/YamameEntity extends AbstractTouhouBoss implements GeoEntity`
      （含默认攻击发射器）
- [x] 3.2 删除 `entity/KuzumonoEntity`
- [x] 3.3 `registry/ModEntityTypes`：删除 `KUZUMONO`，新增 `YAMAME`（id `yamame`）
- [x] 3.4 `ritual/behavior/SummonBossEffects`：`hyakki:kuzumono` → `hyakki:yamame`
- [x] 3.5 `config/GensokyouConfig`：`YAMAME_BOSS_SECONDS`（200）/ `YAMAME_BOSS_HITS`（8）+
      `YAMAME_WEB_*` 默认攻击参数
- [x] 3.6 `command/DanmakuPreview` / `DanmakuTestCommands` / `BossDebugCommands` 改用 yamame

## 4. 符卡与调色盘

- [x] 4.1 `BossCards`：新增 `YAMAME_PALETTE`（6 色），移除 `KUZUMONO_PALETTE`
- [x] 4.2 卡 1 罠符「キャプチャーウェブ」：`RADIAL_BURST`+`Formation.reference`+`Motion.burst` ‖
      `FAN`+`Motion.reclaim`（AIMED），循环 160t
- [x] 4.3 卡 2 瘴符「フィルドミアズマ」：`RING`+`Formation.spin`+`speedProfile` ‖
      `RING`+`decelerateAndReturn` 往返，循环 240t
- [x] 4.4 卡 3 蜘蛛「石窟の蜘蛛の巣」：`FALL_FROM_ABOVE` ‖ `SHELL` ‖ `CAGE(gap)`，循环 220t
- [x] 4.5 卡 4 瘴気「原因不明の熱病」：双反向 `RING`（±spin）‖ `SCATTER_FALL` 毒雨，
      循环 240t，`damageScale` 1.3~1.35
- [x] 4.6 `YamameEntity` 默认攻击发射器（蛛丝 `FAN`，config 驱动，不入 BossCards）
- [x] 4.7 `BossCards`：新增 `yamame()`，移除 `kuzumono()`，更新 `all()`

## 5. 数据与本地化

- [x] 5.1 `hyakki_yagyo_circle.json`：`hyakki_boss_yamame` + `effect` = `hyakki:yamame`
- [x] 5.2 `tags/entity_type/bosses.json`：`gensokyou:yamame`
- [x] 5.3 `lang/zh_cn.json` + `lang/en_us.json`：`entity` / `spellcard ×4` / JEI 配方与效果键
- [x] 5.4 `docs/asset-placeholder-list.md`：山女条目由占位改为正式模型

## 6. 测试与验证

- [x] 6.1 `BossCardLintTest`：符卡表/符卡数切到山女（4 张），`assertLintClean` 用 `YAMAME_PALETTE`
- [x] 6.2 遍历 `BossCards.all()` 的测试（`TrackRepeatTimingTest` / `SpellCardThresholdTest`）通过
- [ ] 6.3 回填 `remnant-touhou-bosses` / `boss-summon-effect` 主 spec（归档时由 openspec 执行）
- [x] 6.4 构建：`.\tools\gradle_task.ps1 build` → BUILD SUCCESSFUL（含全部单测）
- [ ] 6.5 `lang_audit` 退出码 —— `openspec validate --strict` 已过；`lang_audit` 仍为 1，
      但缺失的 21 个键**全部属于无关的 `add-gensokyou-material-uses`**（`zaohua_*` 配方），
      本变更的键（yamame / spellcard / recipe / effect）均报 ok。不属本变更范围。
- [ ] 6.6 实机：`/gs_boss spawn yamame` 逐卡验证可读性（尤其卡 3 垂れ糸 vs 卡 4 毒雨是否观感重复），
      实测秒数后回写 `yamameBossSeconds`（需运行游戏，本会话无法执行）
