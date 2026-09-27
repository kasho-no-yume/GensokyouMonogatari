## Why

增幅核（武器槽 3）当前只有"首次获得时随机生成"一次性词条，玩家拿到核之后没有任何手段改善它。`RuneGenerator.reroll` 至今是一个空桩，词条池被锁死在 5 条纯武器向 id、条数固定 2/3/4，核一旦 roll 差就是永久损失。这让"洗练"成为弹幕武器 build 的核心缺失环节，也让高阶玩家没有灵力消耗的长期出口。

同时，**八百万神恩（kami_no_megumi_circle）的洗练预览面板实际不可用**：REVIEW 期 initiator 看到的是 33 行信息（≈363px）挤在 68px 视口的滚动信息盒里，采纳/保留两个决策按钮落在第 16~17 行，需要盲滚 ~110px 才能看见，且无任何滚动提示；更严重的是 initiator 一旦关闭核心界面，`YaoyorozuGraceService.onViewerClosed` 会**静默清空待决预览**（零反馈地永久作废），而 initiator 在 PERFORM 中途死亡/离线时预览会卡在只有该玩家能决策的死锁状态。玩家反馈"没见到选择属性面板"即源于此。

## What Changes

### 新增：星移之仪（`gensokyou:seii_circle`）

- 注册 1/3/5 阶仪式行为（pattern JSON 与 `jei.gensokyou.ritual.seii_circle` 语言键已存在，`RitualBehaviors.SEII` 常量与 `register(...)` 尚缺，当前是"能成型、能开界面、零产出"的空壳）
- 会话型洗练流程，与八百万神恩同构：`IDLE → PAYING → PERFORM → REVIEW`，玩家只需决定"全部采纳 / 保留原词条"
- 三轴阶梯（详见 design.md）：
  - **核阶 T1/T2/T3 决定词条条数 1/3/5 与灵力花费**（30,000 / 3,000,000 / 300,000,000）—— 核阶是**收益阶梯**
  - **仪式阶 1/3/5 决定能洗哪些核阶**（高阶可洗低阶，反之不行）、**缓存**（50,000 / 7,200,000 / 1,036,800,000，×12 每阶）与 **inrate**（20,000 / 2,000,000 / 200,000,000 每秒，×10 每阶）—— 仪式阶是**吞吐阶梯**
  - 受灵速率恰为"该仪式阶可洗的最高核阶花费 ÷ 1.5 秒"，使自然配对（1 阶洗 T1 / 3 阶洗 T2 / 5 阶洗 T3）恒为 1.5 秒、错配更快
- 待决预览**永久存续**（无超时）且**绑定发起玩家**：他人看得到预览但无决策权，他人开关界面不影响待决，且他人可正常启动自己的洗练（旧预览按既有语义作废）
- 核走**核心 GUI 的专用目标物品槽**（紧邻灵力核心槽，`RitualBehavior.usesTargetSlot` 声明显隐），**既不占祭品台位也不进 `ritual_recipes` 的 `ingredients`** —— 祭品台一台一件且是催化剂载体（1 阶仅 4 台），而核进 ingredients 会被 `RitualRecipeMatcher.apply` 吃掉导致 abort 不可退。行为侧读目标槽定位 `AmpCoreItem`，以 `effect` 编码 `seii:core_1/2/3` 自行挑选配方，不依赖 `matchMax` 的 `Σcount` 排序
- 词条**暂存**在会话里，只有"全部采纳"才写回核组件；任一环节中止（结构失效、配方丢失、玩家离线）核保持原样
- 祭品配方：**3 条按核阶分级**（`seii:core_1/2/3`，`minTier` 1/3/5），催化剂需求量随核阶递增；`ingredients` 条目数上限为 4/12/20（各核阶所需最低仪式阶的可用台位数，核已移出祭品台）

### 变更：增幅核词条池（`rune-affix-pool`）

- 词条数由 2/3/4 改为 **1/3/5**
- 词条池由 5 条纯武器 id 扩为 **19 键**：15 条玩家属性 + 4 条武器专有
  - **玩家属性（15）**：`spirit_regen_rate` / `spirit_power` / `move_speed_bonus` / `graze_chance` / `danmaku_reduce` / `tenacity` / `crit_chance` / `crit_damage` / `spell_amp` / `spell_cdr` / `buff_extend` / `spirit_leech_rate` / `jump` / `phys_resist` / `melee_damage`
  - **武器专有（4）**：`damage_pct` / `attack_rate_pct` / `spirit_cost_pct` / `range_pct`（新增）
  - **排除**：`max_spirit`（永久池账）、`health_bonus`（永久属性）、`danmaku_resist`（已退役键）
  - **BREAKING**：`crit_chance_pct` / `crit_damage_pct` 从池中退役，由玩家属性侧的 `crit_chance` / `crit_damage` 取代（`PlayerAttributes.rollCrit` 已经是同一加区、同一 cap，两个 id 属于设计重复，且量级差 4~6 倍）
- 数值来源分两类：玩家属性键 = per-key 采样带% × 对应玩家阶标准属性值；武器专有键 = per-tier 直接百分比表
- `danmaku_reduce` 单独用绝对点数带（`ΔP = -log2(1-r)`，语义是"减伤 %"而非"P 的 %"）
- 新增软保底：核上 `rune_rerolls` 计数器，保留 +1 / 采纳减半，采样区间随之上移（`PITY_CAP=10`）
- DPS 预算表需重写（per-affix 数值表与池成员彻底变了），但**预算本身未被突破** —— 砍掉武器侧 crit 后 T3 核最坏组合约 **+61%**、典型 **+48%**，仍落在既有"≤+80% / 典型 +50~60%"区间内

### 变更：核给的玩家属性生效通路

- 主手武器 `slot3` 的玩家属性词条以独立 `sourceId` 命名空间（`seii_rune_*`）写入 `PlayerAttributes` **现有临时层**，MUST NOT 新增第三层、亦 MUST NOT 扩 `isTransformRewritable` 白名单
- 改造点只有一处：`setTemp` 的白名单校验从"层级别"移到"来源命名空间" —— `if (isTransformSource(sourceId) && !key.isTransformRewritable()) reject`。`transformRewritable` 描述的是**降神变身**的改写域，把它当作"整个临时层可写哪些键"混淆了两个语义
- 依据：临时层语义 =「不入存档 · 由外部瞬态派生 · 整体丢弃即恢复」，装备语义与之完全一致，只是 `sourceId` 不同
- **加区容器内零乘法**：`totalContribution = Σpermanent + Σtemp` 是平的和，`finalFrom = min(base + Σ, cap)`。新增来源只是往同一个和里加，SHALL NOT 产生第二个乘区（用户疑问已核实排除）
- MUST NOT 写入 `ModAttachments` 台账（否则出现"戴上变强、摘下掉属性"的假永久属性，并与 `GraceService.applyRefine` 的整组替换互相覆盖）
- 设计立场：**武器的加成也是给玩家的** —— 核的属性与玩家其它来源的属性进同一个加区、同一个面板、同一套 cap，不开特例
- 新数据组件 `rune_rerolls : int`（不动 `rune_affixes` 结构）

### 变更：弹幕武器（`danmaku-weapon`）

- `range_pct` 词条消费点（3 处，同一"弹道距离"语义）：`laserMaxLength`、`lifetimeSeconds`（弹幕射程 = speed × lifetime）、符卡弹道寿命；统一走 `(1+r)^0.75` 指数衰减
- `talismanPickRange`（索敌半径）**刻意不接入** `range_pct` —— 索敌是"锁定能力"不是"距离"
- **新增** `BulletCoreItem` 完整 tooltip：伤害倍率 / 攻击间隔 / 单发耗灵 / 弹数 / 散布 / 弹速 / 存活时间 / **有效射程** / 有效 DPS 因子（复用 `CoreMath.bulletDpsFactor` / `laserDpsFactor`）

### 修复：八百万神恩洗练预览（`yaoyorozu-grace-ritual`）

- **关闭界面不再静默作废预览**：移除 `onViewerClosed` 中的 `clearGraceSession()`，`GraceSession.save()` 在 REVIEW 态也写盘，待决永久存续
- **决策权保持绑定 initiator**（不转移）。他人看得到预览但无决策按钮，他人开关界面不影响待决，他人可正常启动自己的洗练 —— 因此不存在"启动玩家掉线死锁"，该缺陷自动消解
- **REVIEW 面板在核心界面内可读**（不新开 GUI）：决策按钮置顶到信息行首位（永久可见）；REVIEW 期隐藏玩家属性面板（33 行 → 17 行）；存在视口外可交互行时自动对齐滚动位置

### 变更：仪式 GUI 信息行（`ritual-gui-info-lines`）

- 信息区内容超出视口且存在视口外可交互行时，客户端自动滚动对齐到该行（通用行为，非神恩专属）
- 内容超出视口时在信息盒右缘渲染滚动条滑块

## Capabilities

### New Capabilities
- `seii-reroll-ritual`: 星移之仪的仪式行为、三轴阶梯门槛、祭品配方、会话状态机、词条暂存与全量替换决策、主手生效的核属性注入

### Modified Capabilities
- `rune-affix-pool`: 词条池重构为 19 键（15 玩家属性 + 4 武器专有）、条数 1/3/5、两类数值来源、软保底计数器、退役 `crit_chance_pct`/`crit_damage_pct`、重写 DPS 预算表
- `danmaku-weapon`: `range_pct` 词条的三处弹道距离消费点与指数衰减、索敌半径排除、弹幕核完整 tooltip
- `yaoyorozu-grace-ritual`: 洗练预览的持久化、决策权转移、REVIEW 面板可读性
- `ritual-gui-info-lines`: 视口外可交互行的自动滚动对齐、滚动条渲染
- `player-attribute-suite`: 明确加区**来源命名空间**语义（临时层同时承载变身与装备两类来源，白名单只约束变身来源）、加区容器零乘法的硬约束

## Impact

**新增文件**
- `ritual/behavior/SeiiBehavior.java` / `SeiiService.java` / `SeiiSession.java`（对照 `YaoyorozuGraceBehavior`/`Service` 与 `RitualCoreBlockEntity.GraceSession` 的既有范式，第三份会话态持有者）
- `item/weapon/SeiiNumbers.java`（world-independent 纯静态：阶梯换算、采样带、软保底区间平移、19 键 roll —— 可单测）
- `data/gensokyou/ritual_recipes/seii_circle.json`

**改动文件**
- `ritual/RitualBehaviors.java`（`SEII` 常量 + `register`）
- `block/entity/RitualCoreBlockEntity.java`（`getCapacity()` 加星移分支；`GraceSession.save()` REVIEW 写盘）
- `item/weapon/RuneGenerator.java`（19 键 roll、软保底、退役 crit_*_pct）
- `item/weapon/RuneSummary.java`（5 字段定长 record → 注册表驱动）
- `item/weapon/WeaponFiring.java`（`range_pct` 三消费点、`rollCrit` 两参数退化）
- `item/weapon/BulletCoreItem.java`（tooltip）
- `registry/ModDataComponents.java`（`rune_rerolls`）
- `spirit/attr/PlayerAttributes.java`（`setTemp` 白名单改为来源命名空间判定 + 按前缀清除；`PlayerAttributesData` **零改动**）
- `ritual/behavior/YaoyorozuGraceService.java` / `YaoyorozuGraceBehavior.java` / `spirit/grace/GraceEvents.java`（三处修复）
- `client/screen/RitualCoreScreen.java`（自动滚动对齐 + 滚动条）
- `client/renderer/RitualCoreRenderer.java` / `RitualFxLayout.java`（分阶 FX 编排）
- `config/GensokyouConfig.java`（`seii` 声明块 + 19 键 per-tier 表 + 阶梯基项）
- `assets/gensokyou/lang/zh_cn.json` / `en_us.json`、`patchouli_books/.../ritual_seii_circle.json`
- `ritual/command/DebugCommands.java`（`/gs_debug seii` 机读单行探针）

**规格影响**
- `openspec/specs/rune-affix-pool/spec.md` 的数值表与池成员章节重写（`crit_*_pct` 退役是 BREAKING；DPS 预算数值本身未被突破）
- `data/gensokyou/rituals/seii_circle.json` 已在仓库中（未注册），本 change 不改动 pattern，只做程序侧接线

**已知遗留（不在本 change 范围）**
- 权重分布待实机调（预算区间已满足，分布未验证）
- `health_bonus` 的 `healthBonusCap=200` 已被神恩 5 阶标准值 810 溢出（与本 change 无关的既有隐患，因 `health_bonus` 已排除出池而不被放大）
- `danmaku_resist` 是 `AttributeKey` 里的死键（本次只在池构建时排除，不动枚举）
- 弹幕合成台未提供"装配后武器的有效属性"总览 tooltip
- 300M 的单次花费对 5 阶玩家的灵力收入是否可达，需上线后观察（不足时调 inrate 阶梯而非花费阶梯）
- 分阶演出的美术强度需玩家实机评审（agent 不启动服务器）
