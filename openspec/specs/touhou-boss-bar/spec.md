# touhou-boss-bar Specification

## Purpose
东方 BOSS 的咒符条血条：按 BOSS 品阶选取造型，并在血条下方显示当前符卡名（由服务端经自定义包同步符卡下标，客户端本地反查名字）。
## Requirements

### Requirement: 东方 BOSS 使用咒符条血条

模组 SHALL 为其**东方 BOSS**（本模组注册的 BOSS 类实体）改绘血条：取消原版血条，改绘一套**咒符条**造型。咒符条造型 SHALL **按 BOSS 自身品阶选取**，1~5 阶各一套边框造型；条身颜色 SHALL 取该阶的品阶色（见 `tier-color-palette`）。

同一品阶的 BOSS SHALL 共用同一套造型；造型 MUST NOT 逐 BOSS 个体定制。后续新增东方 BOSS SHALL 按其 `bossTier()` 自动套用对应造型，SHALL NOT 需要为其单独实现绘制。

改绘 SHALL 通过**取消原版逐条血条事件并自绘**实现，MUST NOT 依赖对原版血条容器字段的反射或 mixin 注入实现。

作用范围 SHALL **严格限定**于本模组的东方 BOSS。原版血条（凋灵、末影龙、袭击事件 BOSS 等）SHALL 保持原版渲染，MUST NOT 被本能力波及。

掉血平滑 SHALL 保留（受击后血条 SHALL 平滑过渡到新血量而非瞬切）。多根血条同时在场时 SHALL 正确纵向堆叠，行高 SHALL 高于原版以容纳咒符条的边框、撕边与副行，堆叠超出屏幕 1/3 高度时 SHALL 与原版一致地截断。

#### Scenario: 东方 BOSS 出场

- **WHEN** 玩家进入某东方 BOSS 的可追踪范围
- **THEN** 顶部出现咒符条造型血条，原版 182×5 血条不出现

#### Scenario: 掉血平滑

- **WHEN** 东方 BOSS 一次性损失大量生命
- **THEN** 血条血量在短时内平滑滑落到新值，不发生瞬切

#### Scenario: 原版血条不受影响

- **WHEN** 玩家在场且原版凋灵 BOSS 血条同时激活
- **THEN** 凋灵血条保持原版渲染，咒符条只作用于东方 BOSS

#### Scenario: 多根咒符条堆叠

- **WHEN** 场上有两根以上东方 BOSS 血条
- **THEN** 纵向堆叠、互不重叠，堆叠总高超出屏幕 1/3 时最上方的被截断

#### Scenario: 新增 BOSS 自动套用

- **WHEN** 后续新增一个东方 BOSS 实体并给它挂了原版 BOSS 血条
- **THEN** 其血条自动呈现为咒符条，无需为其新增绘制代码

#### Scenario: 造型随阶级切换

- **WHEN** 一只 3 阶 BOSS 与一只 5 阶 BOSS 同时在场
- **THEN** 两者的血条边框造型 SHALL 不同，条身颜色 SHALL 分别为该阶品阶色（金 / 紫）

#### Scenario: 同阶共用造型

- **WHEN** 两只同阶 BOSS 同时在场
- **THEN** 两者血条 SHALL 呈现完全一致的边框造型与条身颜色

### Requirement: 作用范围判别

服务端 SHALL 以**该 BOSS 实体的 UUID** 作为其血条的标识（血条 id == 实体 UUID），使客户端能把一根血条确定地连回它所描述的那只实体。

判别 SHALL 保持**纯客户端**：客户端拿到血条 id 后在已加载实体中比对 UUID，MUST NOT 需要为此新增独立的状态同步通道。BOSS 血条只对能观察到该 BOSS 的玩家呈现，故该实体必然处于已加载状态。

服务端 MUST NOT 使用 `ServerBossEvent` 承载东方 BOSS 的血条——其三参构造传入的是血条**自身随机生成**的 UUID（`Mth.createInsecureUUID()`），与实体无关，于是客户端永远无法把血条连回实体。SHALL 自行发送 `ClientboundBossEventPacket` 系列包并以实体 UUID 作为其 id。

服务端 SHALL 照原版语义只在**值变化时**发包（进度、名字），MUST NOT 无条件每 tick 广播。

系统 MUST NOT 采用以下脆弱的隐式标记作为判别依据：血条名称的前缀约定或与显示名做字符串匹配、把某条 `BossBarColor` / `BossBarOverlay` 组合当作专用标记。这些做法在新增实体或与其它模组共用时会静默误判。

#### Scenario: 判别不依赖名字

- **WHEN** 某东方 BOSS 的显示名被翻译或改写
- **THEN** 仍被正确判别为东方 BOSS 并使用咒符条

#### Scenario: 判别不依赖颜色约定

- **WHEN** 某东方 BOSS 的血条颜色与原版某 BOSS 恰好相同
- **THEN** 两者仍被正确区分，不出现原版 BOSS 被误绘成咒符条

#### Scenario: 实体已卸载

- **WHEN** 某根血条的实体在客户端已不可解析
- **THEN** 回退为原版渲染而非崩溃或错绘

#### Scenario: 血条标识即实体标识

- **WHEN** 服务端为某东方 BOSS 创建血条
- **THEN** 该血条的 id SHALL 等于该实体的 UUID，客户端 SHALL 能据此唯一确定对应实体

#### Scenario: 显示名相同的两只 BOSS 不串味

- **WHEN** 场上有两只显示名相同的东方 BOSS
- **THEN** 两者血条 SHALL 各自连回自己的实体，造型与符卡行 MUST NOT 互换

#### Scenario: 进度不变则不发包

- **WHEN** 某东方 BOSS 的生命占比未发生变化
- **THEN** 服务端 MUST NOT 发送血条进度更新包

### Requirement: 符卡名显示在血条下方

系统 SHALL 在咒符条**下方**绘制当前生效的符卡名，作为独立于血条本体的一行。符卡名 SHALL 右对齐至右下角朱印左侧，与朱印共同构成「落款 + 钤印」版式。

符卡名 SHALL NOT 画在血条本体之上或之内，SHALL NOT 使血条本体高度发生变化。

行高推进 SHALL 把符卡行计入 `setIncrement()`，使下一根血条不与其重叠。符卡行不存在时（无可用符卡名）行高 SHALL 回落为血条本体高度。

#### Scenario: 符卡名在条下方

- **WHEN** 某东方 BOSS 处于某张符卡阶段
- **THEN** 其血条下方出现该符卡名，右对齐至朱印左侧，血条本体高度与无符卡行时一致

#### Scenario: 下一根血条不被遮挡

- **WHEN** 场上有两根东方 BOSS 血条且都有符卡名
- **THEN** 第二根血条 SHALL 起始于第一根的符卡行之下，两者 SHALL 不重叠

#### Scenario: 无符卡名时行高回落

- **WHEN** 某血条当前无可显示的符卡名
- **THEN** 符卡行 SHALL 不绘制，该行行高 SHALL 等于血条本体高度

#### Scenario: 符卡名不遮挡血条

- **WHEN** 检查任何时刻的血条绘制
- **THEN** 符卡名 SHALL NOT 覆盖血条的填充区、残影区或边框

### Requirement: 符卡名的同步

符卡名 SHALL 由服务端在符卡切换时通过自定义网络包，向该血条的受众玩家单播。包体 SHALL 只携带符卡在表中的下标，MUST NOT 携带卡名字符串。

客户端 SHALL 在开始观测该 BOSS 时由服务端补发一次当前符卡名下标，SHALL NOT 依赖「玩家恰好在场时发生过一次切卡」。

#### Scenario: 切卡即时更新

- **WHEN** 服务端把某东方 BOSS 切换到新符卡
- **THEN** 能观测到该 BOSS 的玩家 SHALL 在该次切换后看到新符卡名

#### Scenario: 晚进场玩家不看到空白

- **WHEN** 玩家在 BOSS 已处于某符卡阶段之后才开始观测它
- **THEN** 其血条下方 SHALL 立即显示当前符卡名，MUST NOT 保持空白直到下一次切卡

#### Scenario: 不跨实体串味

- **WHEN** 场上有两只东方 BOSS 且各自处于不同符卡
- **THEN** 每只 BOSS 的符卡行 SHALL 独立显示其自身符卡名，MUST NOT 显示另一只的

#### Scenario: 离开观测范围后清理

- **WHEN** 玩家不再观测某东方 BOSS
- **THEN** 客户端 SHALL 丢弃该 BOSS 的符卡下标记录

### Requirement: 符卡名走语言键

符卡名 SHALL 由 lang 键提供，SHALL NOT 为硬编码字符串。`en_us` 语言环境下 SHALL 显示英文名，MUST NOT 显示中文名。

键名 SHALL 形如 `spellcard.gensokyou.<boss_id>.<序号>`，与既有 `entity.gensokyou.*` / `item.gensokyou.*` 同形。

语言键缺失时系统 SHALL 不绘制符卡行，MUST NOT 把原始键名当作名字显示。

#### Scenario: 英文环境显示英文名

- **WHEN** 客户端语言为 `en_us` 且某东方 BOSS 进入符卡阶段
- **THEN** 符卡行 SHALL 显示该卡的英文名

#### Scenario: 缺键不显示

- **WHEN** 某符卡的 lang 键在当前语言文件中缺失
- **THEN** 符卡行 SHALL 不绘制，MUST NOT 显示原始键名

#### Scenario: 语言键纳入审计

- **WHEN** 运行语言键审计
- **THEN** `spellcard.gensokyou.*` 前缀的键 SHALL 被纳入扫描范围，MUST NOT 因前缀白名单未收录而漏检

