# Design: add-yaoyorozu-grace

## Context

- 现状：`temperLevel`（淬炼阶级）已存在于 `SpiritPowerData` 但生产码零调用；属性套件 15 键（base+permanent+temp 分层结算）就绪但无 roll 机制；符卡槽 3 硬编码（`SLOT_ORDER`/`SkillStateData` 具名字段/定长网络包/HUD）；`kami_no_megumi_circle` pattern（1-5 阶）已存在，未注册行为、无配方文件；飞行/mayfly 全仓零先例。
- 参照物：造化仪式（`ZaohuaCraftingBehavior/Service`，`CraftPhase IDLE→PAYING→FLIGHT`）是会话类仪式的成熟模板；`SpiritPowerHelper` 三段式扣费与 `spiritInRatePerSecond` 沿用。
- 约束：仪式 spCost 从核心侧三段式来源收取（非玩家池）；"不双写"规约（`max_spirit`/`spirit_power` 事实来源在池字段）；减免管线仅 danmaku 类型 + 全局 0.9 封顶；pattern 不声明的格位自由（核心上方留空可做演出位，需实机确认未被装饰顶盖）。
- 会话确认的决策（零灵力凡人、temperLevel 复用、抽象槽、仅弹幕减免、洗练当场预览、先入账后演出、掉光即摔、惯性默认开、10 配方逐级进阶可回洗）视为已锁。

## Goals / Non-Goals

**Goals:** 玩家 0-5 超人类阶级的完整闭环——状态模型、指数属性表+roll、仪式执行（含演出与洗练交互）、创造飞行与耗灵、GUI 属性栏与槽位重构；旧档迁移。
**Non-Goals:** 降神变身（phase-d `kamigakari-transformation`）、委托学卡链路、第 4/5 张符卡内容、全伤害减免管线、仪式 pattern 改动、NPC/妖精侧阶级消费。

## Decisions

### D1 阶级状态：复用 `temperLevel`，新命名 `grace` 域
`SpiritPowerData.temperLevel`（int 0-5）即超人类阶级，不新增字段。所有 config 新项、行为、GUI 一律用 "grace/神恩" 前缀命名，避免与结构 tier/minTier 混淆；代码注释标注 temperLevel 语义变更。理由：字段已持久化、已 copyOnDeath、已有读取（regen 基准公式），迁移成本最低。**替代方案**（新字段 superhumanStage）造成双概念并存，否决。

### D2 属性表与 roll 落点
- 数值全部进 config 新 `grace` 段：核心四键（max_spirit / spirit_power / regen / danmaku_reduce）逐阶基准 + roll 区间（核心 ±15%），其余 11 键逐阶基准 + roll（±25~30%）；格式仿武器词条池 `min,max` 对（参考 `RUNE_AFFIX_POOL`）。飞行费率表 5/2/1/0.5/0 (%每秒)。
- **写入分层**（守"不双写"）：`max_spirit`、`spirit_power` roll 后写池字段（`max`、`spirit_damage`）；`spirit_regen_rate`、`danmaku_reduce` 及其余键写属性容器 permanent 层，`sourceId = "grace_tier_N"`（每阶一组）。
- **洗练 N = 整组替换**：重掷该 sourceId 组内全部键（+池字段对应阶的贡献记录），其他阶不动。池字段需要"按阶贡献台账"：`SpiritPowerData` 增 `Map<Integer, TierRoll>`（tier→已 roll 的 max/power 增量），重算池字段 = Σ台账，死亡保留。
- 旧线性 config（`maxSPGainPerTemper`、`spiritDamagePerTemper`、`baseRegenPerSecond` 的 temper 线性项）删除/替换为指数表；`baseMaxSP` 语义改为"表外兜底 0"。

### D3 会话状态机：仿造化、独立 Service
新建 `YaoyorozuGraceService` + `YaoyorozuGraceBehavior`（`RitualBehaviors` 注册 kami pattern），phase：`IDLE → PAYING → PERFORM(100t) → IDLE`，**APPLY 在 PAYING 满额瞬间完成**（扣台面料→roll→写状态），演出纯表现不回滚。要点：
- 会话持久字段新增 `initiatorUUID`、`recipeTier`、`kind(ADVANCE|REFINE)`；`pendingRefine` 仅内存不入 NBT（当场制，见 D5）；造化框架不动，复制改造。
- 启动校验链（全在点击开始一次完成）：initiator 是玩家→无同核心红石入口（`onRedstonePulse` 显式拒绝）→结构 level ≥ 配方 minTier→玩家阶级前置（进阶：=N-1；洗练：≥N 且 tier N 已 roll）→灵力缓存容量=spCost，`inrate` 配置取大值（默认 1000/s）。
- 演出中 initiator 距核心 >8 格、死亡、掉线：直接清会话收尾（免伤与 noGravity 由会话清退兜底），已 apply 的结果保留。服务器重启：会话 NBT 恢复时若处于 PERFORM → 直接按"演出结束"处理。

### D4 演出实现（5s）
- 玩家：服务端每 tick 钉位在核心上方 `(0.5, y+1.5, 0.5)`（setNoGravity + motion 清零 + position 同步），客户端粒子/落雷为表演。
- 脚本伤害/回血：自定义 damage type（不经 danmaku 管线→天然穿透玩家减免；`SpiritLeechHandler` 增加按伤害类型排除，杜绝自伤汲灵永动机），约 3♥/s + 回 8♥/s，保底钳制不死（当前血 < 1 时不再扣）。
- 外部免疫：`LivingIncomingDamageEvent` 中 initiator 在 PERFORM 集合内则 cancel（保留本 mod 自带演出标记，不引 Tag）。
- 落雷：`LightningBolt` `isEffect=true`（无火无伤）随机砸向结构范围 + `ambient_lightning` 音效 + 紫色粒子柱。

### D5 洗练预览：当场制、挂会话
预览（tier N 新 roll vs 现值）写入 BE 会话内存字段；演出结束 5s 后在核心 GUI（initiator 查看时）呈现 InfoLine 双列对比 + "采纳/保留"按钮（`UiAction`，服务端置灰防代点）。作废即清：initiator 关闭 GUI、离线、会话被清、再次执行任何配方。不写玩家存档。理由：用户选"仅当场有效"，省一层持久化与过期策略。

### D6 飞行：授予、费率、摔落、惯性
- 授予/撤销：`PlayerTickEvent.Pre`（在线期）+ 登录/重生/换维度钩子里 `mayfly = tier≥1 || 原版权限`，经 `onUpdateAbilities` 同步客户端。创造/观察者不受影响；死亡后按 tick 重申恢复。
- 耗灵：仅 `abilities.flying` 为真时按 `tier费率%×max_spirit/20` 每 tick 累积扣除（小数进位，复用 `regenBuffer` 模式）；不足支付当期 → `flying=false, mayfly=false`（凡人期外唯一撤权路径）→ 原版重力摔落。
- 惯性 off：实现落点偏离本稿——玩家运动本就客户端权威（位置包上行），服务端 `PlayerSetInputEvent`
  改速度必回弹；改为**客户端** `PlayerTickEvent.Post`（LocalPlayer）在无 WASD 输入的 tick 水平速度归零，
  开关值经扩展后的 `SpiritPowerSyncPayload`（temper+inertia）下发。
  开关持久化于玩家附件 `SpiritPowerData.flightInertia`（boolean，默认 true=原版惯性手感），不挂方块。
  权限通道实现补充：飞行授予走 NeoForge 官方推荐的 `CREATIVE_FLIGHT` 属性 modifier +
  `abilities.mayfly` 双通道（`mayFly()` 判据含属性，裸设 mayfly 在枯竭撤销后摔落豁免不生效）；
  创造/观察者不受开关与耗灵影响。

### D7 符卡槽重构：具名字段→定长数组
`SkillStateData`：`int[] cooldownUntil(5)` + `List<String> learned`（上限=阶级数）+ `String[] equipped(5)`。Codec 读旧档：`cd0/1/2` 按旧 `SLOT_ORDER` 三卡映射入组，equipped=learned 顺序。`SkillSyncPayload` 数组化；HUD 画 `tier` 个槽（0 个时整排隐藏）；键位 G/H/J 保留、新增 K/L（可改键）；`SLOT_ORDER` 降级为"卡注册表顺序"，与槽位解绑。学卡 `/gs_learn` 过渡命令不受 tier 上限（保留调试便利），正式 gate 归委托（非目标）。

### D8 属性面板同步（实现修订）
不新开 `AttributesSyncPayload`：属性面板改为**服务端按查看者组装 InfoLine**——
`RitualInfoPayload.snapshot` 增加 viewer 参数，行为侧新增 `uiInfo/viewer` 与 `uiActions/viewer`
重载读 viewer 本人状态（阶级/池/15 键最终值；`CONTROL_ATTR` 行属性名客户端本地化、值服务端格式化）。
理由：开界面/1Hz 心跳/状态跃迁本就按玩家点对点推送，属性天然服务端权威，零新增网络面。
MUST NOT 向非本人推送他人属性；关闭界面停推由心跳只对开界玩家发快照天然满足。

### D9 旧档迁移与平衡
附件加载时：tier=0 → 池 max/current/spirit_damage 清 0；tier>0（仅调试档）→ 按新表重 roll 覆盖台账（变更日志声明，开发期可接受）。既有 spCost（符卡/武器/结界）按池 200 起跳首版接受"约半数"贬值，`skills`/`weapon` 段数值留待运营调。

## Risks / Trade-offs

- **R1 凡人零灵力 gate 住 Phase C 学卡链路** → 委托系统未实现，实际无回归；在委托接入时必须读玩家阶级。
- **R2 运动学覆写手感**（惯性 off 每 tick 归零可能与客户端预测打架）→ 先服务端权威实现，实机验收不过关再补输入侧改写；范围仅限"无按键时"，战斗走位受影响小。
- **R3 演出钉位与传送/推拉冲突** → 距离>8 即终止演出（见 D3），不做传送拦截。
- **R4 Codec 双变更（池字段+技能档）同版落地** → 迁移各自独立可回退（读旧字段路径保留一版）。
- **R5 台位容量**：配方材料数 ≤ 该阶结构台位数（每阶扩 1 台×对称展开），1 阶进阶材料只能 1-4 种 → 选材设计（Open Questions）时硬约束。
- **R6 5%×200=10/s 耗灵在池未 roll 满时可能 < 1/s 取整** → 按小数进位累积扣，禁取整为 0。

## Migration Plan

单版本落地：数据层（D1/D2/D7/D9 读旧写新）→ 行为层（D3-D6）→ GUI/同步（D5/D8）。回滚=回退 jar；新字段旧版忽略不炸档。无自动回滚脚本（dev 世界直接重置亦可）。

## Open Questions

- 10 条配方选材与 spCost（需 ritual-design 选材轮，受 R5 容量约束）
- 核心上方演出位是否被现有 pattern 装饰顶盖（实机确认，必要时按编辑杖闭环微调 pattern）
- "八百万神恩"与降神变身（kamigakari-transformation）文档措辞区分
- 第 4/5 张符卡内容与其进 `SLOT_ORDER` 卡注册表时点
