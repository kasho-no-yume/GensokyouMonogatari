## 1. 规范止血（不依赖代码，可先行）

- [x] 1.1 改写 `openspec/project.md` §4「平衡哲学」为三分法：战力=常量、经济=SERVER 配置、表现=CLIENT 配置，并指向 `docs/config-guidelines.md`
- [x] 1.2 改口 `openspec/project.md` §3.5（「最终数值以配置为准」→「以代码常量为准」）、§5.4（核图标按 `requiredTier` 常量定色）、§5.5（血条宽度移至 CLIENT 设置）、§9（数值基线进常量）
- [x] 1.3 新建 `docs/config-guidelines.md`：三分法判据、决策树、三桶定义与例子、灰区登记表（掉落/装备/药水=常量，仪式产出=只留倍率）、「新增一个可调项」checklist（须用户确认/用户主动提出，不设键数上限）
- [x] 1.4 替换 `.opencode/skills/neoforge-1211-dev/SKILL.md`「数值与调试」中的「所有可调数值进 `GensokyouConfig`（COMMON）」
- [x] 1.5 替换 `.opencode/skills/ritual-code-dev/SKILL.md` 开篇原则、checklist 第 5 步与反模式表中「可调数值一律接 `GensokyouConfig`」
- [x] 1.6 替换 `.opencode/skills/boss-dev-design/SKILL.md` 中「回写 config」类措辞
- [x] 1.7 替换 `.opencode/skills/player-spellcard-design/SKILL.md` 中「config grace 表 / config Base」类措辞
- [x] 1.8 替换 `docs/mob-design-guidelines.md` §3、`docs/weapon-design-guidelines.md` §7、`docs/spell-card-development-guide.md` §8、`docs/new-ritual-checklist.md` 中的 config 话术

## 2. 常量宿主（按子系统迁移战力数值）

- [x] 2.1 建立/扩充常量宿主类：`balance/*Numbers`、`entity/*Numbers`（fairy/flandre/yamame/ran/nomen/bigFairy）、`WeaponNumbers`、`SairEnergyNumbers`，并从 `GensokyouConfig` 抄入默认值（值不变）
- [x] 2.2 迁移 `npc` + `codex`（7 键）→ 常量；删字段与 `define*` 调用
- [x] 2.3 迁移 `boss`（Flandre 与召唤 BOSS 移动/目标，45 键）→ 常量；同时清理死键 `BOSS_SECONDS_T1/T3/T4/T5`
- [x] 2.4 迁移 `fairy`（41 键）→ 常量；保留数据包刷怪权重不动
- [ ] 2.5 迁移 `monsterBudget`（15 键）→ 常量，并把两套 BOSS 血量口径收敛为单一来源（`MonsterStatBudget` 或每 BOSS 常量，二选一并在 design 记录）
- [x] 2.6 迁移 `danmaku`（基伤/弹速/同步容差/实体上限/校准，约 18 键）→ 常量；`danmakuTelegraphEnabled` 改由 CLIENT 的 `telegraphs` 承担
- [x] 2.7 迁移 `playerAttributes`（28 键）+ `grace` 阶级表与飞行费率 → 常量；`gracePerformTicks`/`graceSpiritInRate` 移入 SERVER 配置
- [x] 2.8 迁移 `weapon` 与 6 个核段（49 键，含 `spiritCost`）→ 常量；同步 `danmaku-weapon` 提到的索敌半径常量
- [x] 2.9 迁移 `spellcards`（38 键）+ `skills`（4 键）→ 常量
- [x] 2.10 迁移 `modPotions`（4 键）+ `equipment`（5 键）→ 常量
- [x] 2.11 迁移 `crystal` 之外的无关项确认：仓储上限/黑名单保留为 SERVER 配置（不迁移）
- [x] 2.12 迁移 `testHarness`（13 键）→ dev-only 常量，`/gs_test` 读常量
- [x] 2.13 迁移 `reiyoku` 的标准池表 `REIYOKU_TIER_MAX_SPIRIT` → 常量
- [x] 2.14 逐子系统 `.\tools\gradle_task.ps1 compileJava` 验证，并对照 `SunakoScaling` 保留「显式传参」重载

## 3. 客户端特效收口

- [x] 3.1 把各段 `fx*`（ritualFx/forge/fxReiyoku/fxSunako/fxSummon/sukima/各仪式内 FX，约 150 键）改为客户端常量
- [x] 3.2 新增 `VisualPreferences` 门面，把画质档映射到 FX 密度/层数/绘制距离
- [x] 3.3 改 `client/renderer/RitualCoreRenderer`（153 处）与 `client/renderer/SukimaPortalRenderer`（18 处）读常量 + 门面
- [x] 3.4 迁移 `talismanBar` 中宽度/可见性等改为 CLIENT 设置，颜色/撕边改为常量

## 4. 新配置面

- [x] 4.1 新建 `GensokyouServerConfig`（SERVER），登记仪式经济全部键（含 `*_MULT*`；产出基线不入配置），从旧 COMMON 抄默认值
- [x] 4.2 新建 `GensokyouClientConfig`（CLIENT）：`fxQuality`/`fxDrawDistance`/`cameraShake`/`telegraphs`/`bossBarWidth`/技能栏可见/整地默认尺寸/引爆器默认引信/线框时长
- [x] 4.3 `Gensokyou.java` 改为 `registerConfig(SERVER, GensokyouServerConfig.SPEC)` + `registerConfig(CLIENT, GensokyouClientConfig.SPEC)`，移除 COMMON
- [x] 4.4 在 SERVER 配置根加注释：只含非战力数值，战力数值不可配置
- [x] 4.5 实现刷新概率倍率覆盖（`MobSpawnEvent`/`FinalizeSpawn` 概率过滤，`1.0` = 数据包值；>1 视为 1 并在注释说明）
- [x] 4.6 客户端展示改读同步值：`client/book/RitualTierComponent`（75 处）、GUI 信息行、tooltip
- [x] 4.7 把 `ritualBuilderConflictOutlineSeconds` 接到 CLIENT 的线框时长键

## 5. 删除与清理

- [x] 5.1 删除 `GensokyouConfig.java` 及其全部引用（确认编译器无残留引用）
- [x] 5.2 删除本地 `run/config/gensokyou-common.toml`，记录到 CHANGELOG（旧键失效、玩家自定义战力值回落）
- [x] 5.3 核对逐键默认值：迁移后默认行为与迁移前完全一致（战力不偏移）

## 6. Spec 文本同步

- [x] 6.1 核对/归档 `config-surface` 新能力 spec（本变更已提供）
- [x] 6.2 应用 15 份 delta：reiyoku-ritual、bousen-lantern-ritual、superhuman-temper、ritual-runtime-fx、sukima-portal-rendering、omoikane-enchant-ritual、watatsumi-fishing-ritual、seii-reroll-ritual、sunako-brew-ritual、shujou-yoroku-ritual、kagutsuchi-flame-ritual、danmaku-weapon、chinkon-hanshou-ritual、sair-energy-ritual、ritual-builder-conflict-feedback
- [x] 6.3 `openspec validate restructure-config-surface --strict` 通过

## 7. 测试与锁定

- [x] 7.1 新增 `ConfigSurfaceTest`：只注册 SERVER+CLIENT；两配置键集合 ⊆ 白名单；SERVER 不含 `*SpCost`/`*Damage*`/`*MaxHealth*`/`*Seconds*`/`*Hits*`/`bossHp*`/`tierScale`/`*DropChance*` 形态键
- [x] 7.2 白名单文件与 config-guidelines 的「新增须用户确认」规则一致（不设键数上限）
- [x] 7.3 迁移既有单测：`SunakoScalingTest`/`HoujounoTeihouBehaviorTest`/`ResonanceRelayBehaviorTest` 中 `getDefault()` 断言改为常量断言
- [x] 7.4 新增/更新不变量测试：`ConfigSurfaceTest.clientOnlyTypesAreNotUsedServerSide` 断言 CLIENT 配置/画质档不被 `client` 包以外的类引用（等价于「改画质档不改变服务端行为」）
- [x] 7.5 `.\tools\gradle_task.ps1 build` 全绿；手动跑一次 `runClient` 复核引导书数值与 FX 画质档

## 8. 归档说明（未完成项）

- **2.5 BOSS 血量口径收敛：未做，已回退。** `MonsterStatBudget.forTier` 与 `MONSTER_TRASH/ELITE/BOSS_*`、`MONSTER_TIER_SCALE`、`MONSTER_SPAWN_ROLL` 在主代码**零调用**（死代码），但 `openspec/specs/monster-stat-budget` 仍要求杂兵/精英区间预算。收敛需先裁决该能力去留，故不在本变更内删除，避免单方面改掉既有需求。
- **7.4 额外不变量测试：已完成。** `ConfigSurfaceTest.clientOnlyTypesAreNotUsedServerSide` 扫描 `src/main/java`，断言 `GensokyouClientConfig` / `VisualPreferences` 不被 `client` 包以外的类引用。
- **实机**：用户已实机验证（SERVER 配置读取、刷新倍率、画质档、引导书同步）。
