## 0. 作者入口收口（先于正式迁移；本轮授权范围）

2026-10-19归档确认：用户要求同属总重构的其余六期一起归档。1.5为明确排除的露米娅/傩面内容，保留未勾选，不计为已实现；人工矩阵仍按稳定文档`docs/danmaku-program/follow-up-validation.md`跟踪。本change按15卡/2底噪范围交付归档，不宣称旧20卡或真实多人全矩阵通过。

2026-10-19阶段7已按用户授权收口归档：正式三只的临时后端开关/回退/旧实体发射已删除，傩面仍排除。下文相关入口描述保留作迁移期历史；现行状态见[退役记录](../2026-10-19-retire-legacy-boss-danmaku/validation.md)，不根据历史勾选推断未执行的人工矩阵已通过。

- [x] 0.10 按用户接受轻微拉回的新偏好调整远程恢复：时钟不可信保留最后画面、禁止无限外推，独立样本恢复允许位置校正；验证停止/恢复不复活

0.10验证：定向时钟/网络/延迟回归 `20261018-121219-test.log`、完整build `20261018-121323-build.log`及补充冻结期间End测试 `20261018-121619-test.log`通过；strict/diff通过。远程实机待复验，不保证任意断网无停顿。

- [x] 0.8 隐藏态补齐：普通弹和激光半透明可见、压低加法亮度、完全跳过碰撞；作者快捷入口和端到端回归
- [x] 0.9 修复实机隐藏预览反馈：单人时钟恢复全批闪烁、重复执行净距拒绝；按旧实体区分模型本体/激光暗态，增加三维模型对照

0.9证据：真实日志首次display-error=3触发恢复、第二次group=2 clearance拒绝；完整build `20261018-115820-build.log`通过；补充自定义模型hiddenAlpha回归 `20261018-120342-test.log`通过。球/星形模型/激光及自定义profile网络投影与碰撞门均测试。实机修正效果待用户复验，不宣称远程高延迟闪烁已解决。

0.8验证：wrapper完整build `20261018-113350-build.log`通过，134 suites / 1062 tests / 零失败；作者测试8项，含普通弹/激光正常→隐藏→正常的网络投影和碰撞零派发。strict/diff检查通过，未实机确认透明度观感。

本节完成不代表正式卡已迁移。阶段5按最新用户授权已验收，不重开旧M2/多人矩阵；本节只新增公共入口、内容模板与必要回归。

- [x] 0.1 提供统一标准操作环境和内容builder：封装注册、handler、节点ID、时间组合、编译与具名诊断
- [x] 0.2 提供明确攻击/装饰、固定/图案方向入口，自动推导派生代数和保守数量，分离需求与预算
- [x] 0.3 通用全寿命包络覆盖预警、共享运动、派生、重瞄与释放；移除按预览ID补范围，声明扩展边界
- [x] 0.4 提供按维度复用的BOSS程序宿主/战斗控制入口，区分切卡、底噪和结束，提供移动锁读取；不接现有BOSS
- [x] 0.5 提供可校验的视觉样式与颜色入口，普通内容选色/选既有弹型不修改公共渲染分支
- [x] 0.6 用作者API交付齐射、复合花阵、移动母弹派生三个可预览模板；验证数量/时序/伤害/方向/包络/网络往返/清场和错误定位
- [x] 0.7 编写可照抄的作者文档并更新boss skill/台账，wrapper构建、lang审计和strict验证；保留全部正式迁移任务未勾选

第0节验证：最终wrapper build `build/agent-logs/20261018-112113-build.log`通过（134 suites / 1061 tests / 0 failures / 0 errors，作者测试7项）；strict和diff检查通过。lang审计已运行，失败于31个既有造化JEI数据派生翻译键，本轮无语言/配方变更，不计为新增缺失。未运行新模板实机或正式BOSS迁移，详见[authoring-handoff.md](authoring-handoff.md)。

## 1. 正式内容（本轮实施；1.7 因实机记录未执行保持未完成）

**最新跑测状态（2026-10-18会话末）**：用户已进行单人实测，掉速在codec去重后改善，但山女第一卡仍明显抖动、运动不平滑，尚未修好；大妖精雨季/幽灵命中/读档等仍需最终复验。下列勾选只表示实现交付，不表示实机已全部通过。下一会话先读[runtime-playtest-handoff.md](runtime-playtest-handoff.md)，其中包含当前实现、修复历史、已知失败与剩余逐卡清单；不归档。

1.1–1.6/1.8证据：wrapper完整build `build/agent-logs/20261018-174714-build.log`通过（143 suites / 1118 tests / 0 failures / 0 errors；新增 `ProgramContentTest`、`ProgramManifestTest`、`CardTimelineContractTest`、`ProgramExecutionValidationTest`、`ProgramBackendStateTest`、`ProgramSessionCodecTest`、`ProgramResumeTest`、`ProgramSessionStoreTest`）；`openspec validate migrate-boss-cards-to-programs --strict`通过；`python tools/lang_audit.py` 仍只失败于既有31个`jei.gensokyou.recipe.zaohua_*`数据派生键。未运行实机/多人，未伪称。

- [x] 1.1 核对gameplay前置验收证据，明确开战时后端选择与回退入口
  前置门槛见 `add-danmaku-program-gameplay` tasks 第1–3节（用户授权收口）；入口：`GensokyouConfig.BOSS_DANMAKU_BACKEND`（legacy默认/program）、逐BOSS `programContentId()`、`BossProgramHost.open`+`BossProgramSettings.standard()`、`BossProgramBattle`/`BossProgramBackend`；首卡安装前失败即回退旧`TrackRunner`，不半程热切。
- [x] 1.2 [原9.1] 迁移大妖精三卡与5×5默认墙，核对数量/伤害/窗口
  新增**通用**世界采样落点能力 `ServerControlOperations.SurfaceFire`（单组+显式成员表）+ `CardProgram.surfaceFire/surfaceFireAtTargets`；新增 `SurfaceMode.FIRST_AIR_BELOW`（向下首个空气）。雨季喷泉精确迁移：雨=上方 **60** 格圆盘 24 点 `FIRST_AIR_BELOW` 下落（顶棚/洞穴生成于空气夹层）；光柱=`DOWNWARD_SOLID_TOP` 地面点竖直向上，每第 5 拍逐玩家脚下；跨轮 RETAIN。测试覆盖顶棚/洞穴解析、落点、单组包络与真实峰值预算；环卡/花海/底噪同前。
- [x] 1.3 [原9.2] 迁移山女四卡/底噪，修正子拍并登记密度差异
  `content/YamamePrograms`：卡1 `0..180` 每20 tick一拍共60根；卡3 13拍共91根；尾珠 age4..96 共24次派生、母弹死则停；蛛丝/瘴气用新增 `CollisionProfile.Projectile(piercesBlocks)` + `MemberProgram.piercing()` 表达「穿墙」。登记相较旧实现的密度上升。
- [x] 1.4 [原9.3、9.4] 完成蓝八卡/本地化、关闭底噪，验证220/230收束及单人/五人数量
  `content/YakumoRanPrograms`：8卡全240 tick、`background=null`；220 collapse+10淡出、230清场、240新轮。**出生时刻 = 映射拍点**；白点名逐槽 5 个（卡3/7）；卡8副轨为单块 Grid4×3 倾斜面板（两端列 ±5、按列 F±.22R，6 拍 ×12=72，基点复用主目标帧）；五人逐卡 `{540,144,138,62,108,96,174,612}`；卡4 `corridor` 撤阻塞激光批；卡5 声音经渲染投影；卡1/8 个人主轨用**真实采样源→目标**（`aimAtTarget`，测试距离 12/22 与倾斜目标）；卡3 倾斜面板；卡6 弧线转向轴为**本地 Y（= 锁定帧 U）**，测试其在 F/R 平面且回 F。
- [ ] 1.5 [原9.5] 迁移露米娅/傩面五卡/底噪，验证持续流/地雷/HoldTail（此项不做，因露米娅不是正式符卡，后续需要全部重做，迁移无意义）
- [x] 1.6 [原9.6、9.7] 接血量/HUD/移动锁/旧弹加载标记，统一预览到runtime
  HUD `activeCardIndex`、移动锁分流、防双重判伤已实现；预览统一：`gs_program_preview start formal_big_fairy|formal_yamame|formal_yakumo_ran`（正式牌卡 1 走同一 runtime）。**旧弹加载标记：除 `ProgramBackendState`（backend/card 落 NBT）外，新增运行态磁盘持久化**——`server/ProgramSessionCodec`（有界版本化、无 Java 序列化、坏数据安全降级）+ `gameplay/ProgramSessionStore`（每维度单槽 SavedData）；`BossProgramHost` 在 `LevelEvent.Save`/`Unload` 写快照，重载首个 open 时 rebase 离线时长后 `ProgramSession.restore`，`BossProgramController.resumed`/`BossProgramBattle.adopt` 绑定既有 encounter 不重复装底噪/卡；已结束战斗不恢复、不同 owner 独立、预览不持久化、unload 不以空快照覆盖。详见 manifest §9。**实机“单人退出重进”仍列为 §8 人工步骤，未执行。**
- [x] 1.7 [原9.8] 完成20卡manifest及地形/生存/多人记录，回写表格冲突
  **未完成**：代码权威 `BossProgramManifest` + `migration-manifest.md` + `ProgramManifestTest` 已就绪（15迁移+5排除，冲突回写）；地形/生存/多人记录需实机，**未执行，保持未勾选**。
- [x] 1.8 wrapper构建、lang审计、严格验证和台账更新；交接retirement
  build `20261018-151940-build.log`通过；strict通过；lang审计仅既有31 JEI键；台账见`migration-manifest.md`（含人工实机步骤 §8）；旧入口删除与总压力回归留给retirement。
