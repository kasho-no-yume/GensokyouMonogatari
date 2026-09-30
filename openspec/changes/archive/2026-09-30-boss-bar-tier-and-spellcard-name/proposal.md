## Why

东方 BOSS 的咒符条血条当前是**一套造型通用于全部东方 BOSS**（`touhou-boss-bar` spec 明文要求），既读不出这只 BOSS 是几阶，也让玩家在战斗中看不到正在打的符卡叫什么——`SpellCard.name()` 至今**没有任何 UI 消费点**（`rework-big-fairy-cards` design §3.15 已记录该缺口）。两者都是"看得见的信息被丢掉了"。

## What Changes

- **BREAKING** — `touhou-boss-bar`：咒符条造型由「全体统一」改为「**按 BOSS 阶级选型**」。5 个阶级各一套边框造型，条身颜色取该阶品阶色。`MUST NOT 按 BOSS 个体定制` 这条约束**保留**（同一阶的 BOSS 共用同一套造型）。
- 新增 `boss-tier` 能力：`TouhouBoss` 获得 `bossTier()`（与既有 `referenceTier()` 配对——后者是"参照玩家阶级"，前者是"BOSS 自身阶级"）。这是**填已有的空位**：`MonsterStatBudget.forTier(band, monsterTier, playerTier)` 早已有 `monsterTier` 参数，只是从未传入真实值。
- 新增符卡名显示：血条**下方**独立一行，右对齐到朱印左侧（`乱焔散 ▪` 落款版式）。经 `CustomPacketPayload` 在切卡时向血条受众单播。
- **BREAKING** — `SpellCard.name` 类型由 `String` 改为 `Component`，14 处构造改走 lang 键 `spellcard.gensokyou.<boss_id>.<序号>`。
- 血条渲染从纯 `fill()` 画法改为九宫格贴图（`frame_1..5` 定色 + `segment` 灰度染色）。**本变更提供的贴图全部是占位，后续整体替换。**
- **BREAKING** — `tier-color-palette`：「程序化滤镜染色」条款加一条**范围写窄的例外**——HUD 血条的**边框装饰层**允许逐阶独立；**条身层**仍 SHALL 为单张灰度底图 + 运行期 tint。

## Capabilities

### New Capabilities

- `boss-tier`: BOSS 自身品阶（`bossTier()`）的来源、取值范围、占位分配表与 5 阶血条造型的选取规则。

### Modified Capabilities

- `touhou-boss-bar`: 两条 requirement 改写——① 取消"造型统一适用于全部东方 BOSS"，改为"按阶级选型"；② 新增"符卡名显示在血条下方"整条 requirement。
- `tier-color-palette`: 「程序化滤镜染色」加 HUD 装饰层例外；「品阶色单一色源」补血条 tint 一处消费点。

## Impact

- **Java（common）**：`TouhouBoss`（加 2 个 `default` 方法）、`AbstractTouhouBoss`（加 `bossTier()` 覆写点）、`SpellCard`（`String`→`Component`）、`BossCards`（14 处构造 + 2 个调试命令改 `.getString()`）。
- **Java（client）**：`TouhouBossBarRenderer` 改为九宫格绘制 + 符卡行；新增 `bossTier→frame` 映射与缺贴图时的 `fill()` 降级。
- **Java（network）**：新增 `SpellCardNamePayload(entityId, cardIndex)`，`network/ModNetworking` 注册；`AbstractTouhouBoss` 在 `syncCard` 切卡时向 `bossBar` 受众单播，`StartTracking` 补发当前卡名。
- **Java（entity）**：`FlandreEntity` 需给 `bossTier()` 占位值（它 `extends Monster` 而非 `AbstractTouhouBoss`，无符卡表 ⇒ 符卡行不显示）。
- **资源**：新增 `textures/gui/boss_bar/{frame_1..5,segment}.png` + `tools/textures/boss_bar.py`；`lang/{zh_cn,en_us}.json` 增 28 个键。
- **工具**：`tools/lang_audit.py` 的 `KEY_RE` 前缀白名单须加入 `spellcard`——**不加则这 28 个新键对审计完全隐形**。
- **配置**：`TALISMAN_BAR_ROW_HEIGHT` 默认值 28 → 38（容纳符卡行）；既有 `TALISMAN_BAR_COLOR_*` 降级为占位贴图缺失时的回退值，保留不删。
