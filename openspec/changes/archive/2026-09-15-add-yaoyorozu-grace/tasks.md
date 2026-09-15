# Tasks: add-yaoyorozu-grace

## 1. 数据模型与数值（superhuman-temper）

- [x] 1.1 config 新增 `grace` 段：0-5 阶级指数属性表（核心四键 ±15%、其余 ±25~30% roll 区间）、飞行费率表 5/2/1/0.5/0%、演出 tick、PAYING 受灵速率；移除线性淬炼项（`maxSPGainPerTemper`、`spiritDamagePerTemper` 等）
- [x] 1.2 `SpiritPowerData`：initial() 改空池 0/0；新增阶级台账 `Map<Integer, TierRoll>`（tier→max/power 增量）与重算方法；Codec 读写 + 旧档迁移（tier=0 池清零；tier>0 按新表重 roll 覆盖）
- [x] 1.3 `AttributeKey` baseFn 改造：灵力三键改由池字段/台账驱动，删除线性 temper 放大项，守"不双写"规约
- [x] 1.4 roll 结算器：进阶全键区间 roll（核心写池+台账、其余写 permanent 贡献 `grace_tier_N`）；洗练整组替换且组间隔离；池字段=Σ台账重算
- [x] 1.5 凡人零灵力 gate：回灵 tick/汲取/道具注灵对 max=0 短路；武器射击、符卡施放、结界引爆服务端校验统一"需进阶"拦截与提示；HUD 灵力条/槽排 0 值隐藏
- [x] 1.6 单元测试：迁移两分支、roll 区间边界、洗练只替换目标组、池重算与台账一致性

## 2. 八百万神恩仪式行为与会话

- [x] 2.1 `RitualBehaviors` 注册 `kami_no_megumi_circle` → 新建 `YaoyorozuGraceBehavior`（onUiAction 决策表、usesCoreSocket 按需要）
- [x] 2.2 新建 `YaoyorozuGraceService` 会话状态机 IDLE→PAYING→PERFORM；`initiatorUUID/kind/recipeTier` 入 BE NBT 持久化；红石启动显式拒绝；非 initiator 点击拒绝
- [x] 2.3 启动校验链：结构 level≥minTier（复用现有过滤）+ 玩家阶级前置（进阶=N-1、洗练≥阶级 N 且该组存在）；不满足零消耗拒启并反馈
- [x] 2.4 PAYING：容量=spCost 高 inrate 逐 tick 三段式收灵；initiator 再点取消并退还缓存
- [x] 2.5 APPLY 先于演出：扣台面料→进阶 apply（temper+1、roll、飞行授予即时生效）或洗练产出预览挂会话
- [x] 2.6 演出（100t）：initiator 核心正上方钉位悬浮、装饰落雷（isEffect）+雷声音效、脚本掉血 3♥/s 回血 8♥/s 保底 1 心（自定义伤害类型：不入弹幕管线、SpiritLeech 按类型排除）、粒子轰炸；`LivingIncomingDamageEvent` 演出期外部伤害免疫；离场>8 格/死亡/掉线清退兜底；重启恢复时 PERFORM 视为已结束
- [x] 2.7 新建 `data/gensokyou/ritual_recipes/kami_no_megumi_circle.json`：10 条配方骨架（effect 型，选材/spCost 待 ritual-design 轮填入；条目数≤对应阶级台位数为硬性校验）
- [x] 2.8 lang 键（启动/拒绝/取消/演出提示）；服务端单元测试：校验链、取消退还、先入账后掉线不丢效果

## 3. 飞行系统（superhuman-flight）

- [x] 3.1 玩家附件 `graceFlightInertia`（boolean，默认 true）注册+持久化+登录同步
- [x] 3.2 mayfly 授予与重申：在线 tick + 登录/重生/换维度钩子（阶级≥1 且非创造/观察者），死亡后可复飞
- [x] 3.3 阶级费率耗灵：flying=true 时按 `费率×max_spirit/20` 每 tick 小数进位扣池；余额不足当期即撤 flying/mayfly 自然坠落+濒竭提示；5 阶零消耗
- [x] 3.4 惯性关闭：`PlayerSetInputEvent` 无移动键输入 tick 水平速度归零；开关变更实时生效
- [x] 3.5 单元测试：费率取整（小数进位不出现 0 消耗）、撤权路径、跨核心读同一开关值

## 4. 符卡槽重构（skill-slots-hud 改造）

- [x] 4.1 `SkillStateData` 重构：`int[5] cooldownUntil` + `learned` 列表 + `equipped[5]`；Codec 读旧 `cd0/1/2` 按旧 SLOT_ORDER 映射迁移
- [x] 4.2 `SLOT_ORDER` 降级为卡注册表顺序并与槽位解绑；配装默认按学习顺序，已学数上限=阶级（`/gs_learn` 豁免）
- [x] 4.3 `SkillSyncPayload` 数组化；施放四连检改按"该槽已配装+已学+冷却+灵力"且槽数≤阶级
- [x] 4.4 HUD 按查看者阶级画 0-5 槽（0 隐藏）；新键位 K/L 注册（可改键），G/H/J 兼容保留
- [x] 4.5 单元测试：旧档三槽迁移映射、凡人按键拦截、上限学习拒绝

## 5. GUI 与同步

- [x] 5.1 新增 `AttributesSyncPayload`：打开八百万神恩核心时点对点推送查看者本人（阶级、池、15 键最终值），关界面停推
- [x] 5.2 `RitualCoreScreen` 自主信息区接属性面板（只读，复用 InfoLine/进度条样式）
- [x] 5.3 惯性切换 `UiAction` 按钮：仅阶级≥1 查看者可见，服务端拒绝为他人切换
- [x] 5.4 洗练预览面板：新 vs 当前逐键对比 + 采纳/保留按钮；作废时机=关闭界面/离线/会话清退；采纳经 1.4 整组替换落库
- [x] 5.5 lang 键补全；面板与按钮的实机布局验收

## 6. 平衡与收尾

- [x] 6.1 现有 spCost（符卡/武器/结界）对照 1 阶池 200 做一轮回归调值（config 改动，记录进变更说明）
- [x] 6.2 实机验证清单（用户执行）：核心上方演出位无遮挡、落雷/粒子表现、掉光即摔手感、惯性开关手感、旧档迁移
- [x] 6.3 文档：project.md §3.2 淬炼=八百万神恩正式实现措辞更新；与降神变身（kamigakari-transformation）命名区分说明；配方选材完成后回填 2.7 数据
- [x] 6.4 `gradlew build` + 全量测试通过；交付验收
