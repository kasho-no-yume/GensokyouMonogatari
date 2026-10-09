# codex-of-beings Specification

## Purpose
众生典籍物品契约：右键合格 `Mob` 收容并单槽累计同种 mob 数量（无掉落），三类资格排除（非 Mob/boss/黑名单/东方 NPC），驯服动物每书一次防误触警告，满 20 进入不可逆附魔形态，三态 tooltip 与收容反馈；并为「众生余录」仪式提供只读访问器。
## Requirements
### Requirement: 物品与收容数据组件
系统 SHALL 提供正常物品 `gensokyou:codex_of_beings`（众生典籍），不可堆叠，并 SHALL 以数据组件记录收容状态 `{species: ResourceLocation?, count: int}`（单槽，同一本书一次只记一种 mob 类型）。组件 SHALL 持久化并网络同步，缺失时 SHALL 视为空书（species 空、count 0），MUST NOT 导致加载失败。物品 SHALL 提供静态访问器读取 species/count/是否满，供后续仪式直接调用。

#### Scenario: 空书兜底
- **WHEN** 加载一本无收容数据组件的典籍
- **THEN** 视为未收容任何实体（count 0），tooltip 显示空态，可正常使用

#### Scenario: 状态持久化与同步
- **WHEN** 收容一只 mob 后存取存档并登录客户端
- **THEN** species 与 count 保持，客户端 tooltip 与光效正确显示

### Requirement: 收容资格排除
系统 SHALL 仅收容 `Mob` 实例；SHALL 排除 boss、config 黑名单与标签黑名单中的实体类型；SHALL 排除东方 NPC（`TouhouNpcEntity`）。boss 判定 SHALL 基于实体类型标签 `#gensokyou:bosses`（默认含本 mod `flandre`/`fake_flandre`/`cirno`/`big_fairy` 与原版 `wither`/`ender_dragon`/`warden`/`elder_guardian`），MUST NOT 依赖 boss 血条（无公开查询 API）。黑名单 SHALL 支持 config 字符串列表与实体类型标签并存，任一命中即排除。非法黑名单 id SHALL 被跳过而不影响其余。不符合资格的目标 MUST NOT 被收容，也 MUST NOT 产生任何消耗。

#### Scenario: 非 Mob 不可收容
- **WHEN** 对盔甲架、物品实体、船或方块实体右键典籍
- **THEN** 不发生收容，不改变书本状态

#### Scenario: boss 不可收容
- **WHEN** 对 `gensokyou:flandre`、`gensokyou:cirno` 或 `minecraft:wither` 右键典籍
- **THEN** 不发生收容

#### Scenario: 黑名单不可收容
- **WHEN** 对默认黑名单中的 mob（如 `minecraft:bat`）或 config/标签新增的 mob 右键典籍
- **THEN** 不发生收容

#### Scenario: 东方 NPC 不可收容
- **WHEN** 对 `TouhouNpcEntity` 子类（如 `gensokyou:rinnosuke`）右键典籍
- **THEN** 不发生收容

#### Scenario: 一般 mob 可收容
- **WHEN** 对村民、铁傀儡、雪傀儡、牛羊等合格 mob 右键典籍
- **THEN** 收容成立

### Requirement: 右键收容与无掉落移除
右键合格 mob SHALL 将其收容并从世界移除，MUST NOT 产生任何掉落物（无视其装备、命名与是否被驯服）。MUST 仅在服务端执行状态变更与移除，客户端 MUST NOT 独立修改状态。收容 SHALL 只作用于被点击的那一只实体：MUST NOT 连带处理其乘客或坐骑，记录的类型 SHALL 为被点击实体的 `EntityType`。副手持有典籍 SHALL 与主手等效。

#### Scenario: 移除且无掉落
- **WHEN** 收容一只穿着全套盔甲、带命名牌的僵尸
- **THEN** 僵尸消失，不掉落盔甲、命名牌或其他任何物品

#### Scenario: 只收点击目标
- **WHEN** 对一只骑着鸡的僵尸幼体点击其本体右键典籍
- **THEN** 仅僵尸被收容，鸡留在原地

#### Scenario: 副手可用
- **WHEN** 典籍位于副手且主手为空手
- **THEN** 右键合格 mob 同样完成收容

### Requirement: 进度推进与满值上限
收容进度 SHALL 按被收容 mob 的 `EntityType` 记账：空书首次收容 SHALL 置 species 为该类型且 count 为 1；同类型收容 SHALL 使 count 加 1；未满时收容不同类型 SHALL 改写 species 并将 count 重置为 1。count SHALL 硬性封顶 20。

#### Scenario: 同类型累计
- **WHEN** 连续收容 3 只僵尸
- **THEN** 书记录类型为僵尸、count 为 3

#### Scenario: 未满换种清零
- **WHEN** 书记录僵尸 19 只后收容 1 只骷髅
- **THEN** 书类型改写为骷髅、count 变为 1

#### Scenario: 封顶 20
- **WHEN** 已记录同类型 20 只后再次收容该类型
- **THEN** count 仍为 20，不再增加

### Requirement: 满态不可逆附魔形态
当且仅当 count 达到 20 时，典籍 SHALL 呈现附魔光效（`isFoil` 为真）。满态 SHALL 为不可逆的终态：此后右键任何实体（同种、异种）SHALL 完全无功能——MUST NOT 收容、MUST NOT 改写类型、MUST NOT 重置进度。系统 MUST NOT 依赖动态写入光效组件来实现该表现。

#### Scenario: 满态发光
- **WHEN** count 达到 20
- **THEN** 物品显示附魔光效

#### Scenario: 满态右键无反应
- **WHEN** 满态典籍右键任意合格 mob
- **THEN** 该 mob 不被收容、书状态不变、不播放收容音效

#### Scenario: 满态不换种
- **WHEN** 满态典籍（记录僵尸）右键骷髅
- **THEN** 类型与 count 均保持僵尸 20，不重置

### Requirement: 驯服动物防误触警告
对已驯服动物首次右键时，系统 SHALL 仅弹出警告提示、MUST NOT 收容，并将该书的"已警告"标记置位；此后对任意驯服动物右键 SHALL 正常收容。警告粒度 SHALL 为每本书一次。驯服判定 SHALL 同时覆盖 `TamableAnimal#isTame()` 与 `AbstractHorse#isTamed()`。

#### Scenario: 首次警告不收容
- **WHEN** 用一本从未警告过的典籍右键一只已驯服的狼
- **THEN** 弹出警告消息，狼不被收容，书进度不变

#### Scenario: 二次正常收容
- **WHEN** 同一本书再次右键该已驯服的狼（或任一已驯服动物）
- **THEN** 正常收容并按类型记账

#### Scenario: 覆盖马与羊驼
- **WHEN** 首次右键一只已驯服的马
- **THEN** 同样弹出警告且不收容

### Requirement: Tooltip 三态文案
典籍 SHALL 在常规 tooltip 区域显示收容状态：空态显示「未收容实体」；未满显示「<实体类型本地化名>：n/20」；满态显示「记录了 <实体类型本地化名> 的灵魂」并着金色。类型名 SHALL 取自实体类型的本地化描述。MUST NOT 使用耐久条（`isBarVisible`）作为进度显示。

#### Scenario: 空态文案
- **WHEN** 查看一本空典籍的 tooltip
- **THEN** 显示「未收容实体」

#### Scenario: 未满态文案
- **WHEN** 查看记录骷髅 3 只的典籍
- **THEN** 显示「骷髅怪：3/20」（按客户端语言）

#### Scenario: 满态文案
- **WHEN** 查看记录同类型满 20 只的典籍
- **THEN** 显示金色「记录了 <类型名> 的灵魂」

### Requirement: 收容反馈表现
每次成功收容 SHALL 播放粒子效果与末影人瞬移音效（`SoundEvents.ENDERMAN_TELEPORT`）。MUST NOT 发送 actionbar 消息。

#### Scenario: 成功收容有反馈
- **WHEN** 成功收容一只 mob
- **THEN** 目标位置出现粒子并播放末影人瞬移音效

#### Scenario: 失败无反馈
- **WHEN** 右键不合格目标或满态右键
- **THEN** 不播放收容音效

### Requirement: 获取方式

典籍 SHALL 出现在本 mod 创造模式物品栏，并 SHALL 提供正途合成路径：造化之仪配方 `记忆残页 ×8 + 符纸 ×4 + 灵草 ×3 + 书 ×1 → 众生典籍 ×1`（`minTier` 1，`Σcount=16`）。该配方原料总量 MUST NOT 超过 1 阶造化之仪的祭品台数（16 台），故灵草由 4 下调为 3。物品 SHALL 具备物品模型与 16×16 像素贴图。

#### Scenario: 创造栏可取

- **WHEN** 打开本 mod 创造物品栏
- **THEN** 可见并取出众生典籍，图标正常显示

#### Scenario: 造化合成

- **WHEN** 在 1 阶及以上造化之仪台面摆入 8 记忆残页、4 符纸、3 灵草与 1 本书并触发合成
- **THEN** 扣减全部原料与对应灵力，产出 1 本众生典籍

#### Scenario: 阶不足不可造

- **WHEN** 0 阶造化之仪摆入上述原料
- **THEN** 该配方因 `minTier` 不足不命中，不消耗、无产出

### Requirement: 面向未来仪式的读取契约
系统 SHALL 提供世界无关的静态读取入口（收容类型与数量、是否满），供后续「众生余录」仪式在不消耗本书的前提下读取。本条 MUST NOT 依赖仪式行为的实现。

#### Scenario: 仪式可读取
- **WHEN** 后续仪式代码调用该访问器读取一本典籍
- **THEN** 得到其 species 与 count，且书本不被消耗

