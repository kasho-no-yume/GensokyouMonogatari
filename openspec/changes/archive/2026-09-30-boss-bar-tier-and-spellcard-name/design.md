
### D15 — `FlandreEntity` 的两个空洞

**选择**：`TouhouBoss` 的两个新方法都是 `default`，`FlandreEntity` 只给 `bossTier()` 占位值（5 阶），`activeCardIndex()` 走默认 `OptionalInt.empty()` ⇒ 符卡行不显示。

**理由**：`FlandreEntity extends Monster`，不继承 `AbstractTouhouBoss`，没有 `spellCards()` 也没有 `runner`。若把接口方法写成 `abstract`，编译期就把它和其余 5 只绑死在同一套机制上——而四重存在的 HP 分数语义（4 条命）与"血量比 ⇒ 选阶段"根本不成立。

**附带现状记录**：芙兰朵露目前**召不出来**——`SummonBossEffects.REGISTRY` 只注册 4 只（big_fairy / kuzumono / kitsunebi / nomen），她不在任何 spawn tag 或 biome modifier 里，只能 `/summon`。她属于 `boss-dev-design` §7 的「寝宫 BOSS」定位，而寝宫 worldgen 尚未落地。故 5 阶只是占位。

## Risks / Trade-offs

- **[阶级分配是占位，5 套贴图只有 1~2 套在用]** → 占位表在 design D3 显眼标注，`tasks.md` 留"正式美术到位后按真实阶级表重画"的后续项。当前不要为了"把 5 套都用上"而拔高 BOSS 阶级——那会让血条外观对战斗强度说谎。

- **[5 张 `frame` 违反 `tier-color-palette` 的「不得逐阶烘焙」条款]** → 条款本身 MODIFY，例外范围写窄到"仅 HUD 血条边框装饰层"，条身层仍强制单张灰度 + tint。改条款时 MUST NOT 顺带放宽到其他资产（物品/方块仍逐阶烘焙，那是它们的既有形态）。

- **[占位贴图质量差，玩家可能觉得血条变丑了]** → 这是**已知并接受的**过渡状态，不是缺陷。`frame_N` 的 bounding box 固定 182×14 且内部构造与正式资源无关（端头 8px + 透明窗口是规格的一部分），所以替换时 Java 侧零改动。降级路径（D13）保证逐张替换期间不崩。

- **[`selectCard` 纯函数性是隐式契约，破的时候只表现为"符卡名偶尔不同步"]** → 已由 D6 的 payload 移出客户端重算路径。任务清单留一条注释指向备选方案。

- **[`setIncrement` 提到 38 后，4 只以上同场会触发原版 1/3 屏截断]** → 与原版行为一致，既有 spec 已有该场景且未规定"不可截断"。召唤型实际常态是 2 只。接受。

- **[晚进场玩家看不到符卡名（D8 若漏做）]** → `tasks.md` 里单列一条验收项，且症状描述写进注释——「空白符卡位」与「没有卡」在画面上无法区分，是最难自查的一类 bug。

- **[改 `SpellCard.name` 类型会波及 2 个调试命令]** → `StringBuilder.append(Object)` 走 `Component.toString()`，缺键时吐的是原始 key 而非名字，调试输出会变得难读。两处都要改 `.getString()`。已列入 tasks。

- **[`tools/lang_audit.py` 漏改则 28 个新键隐形]** → 列为独立任务项 + 验收项（造一个故意缺的键，确认 audit 报出来）。

## Migration Plan

1. 先加 `bossTier()` / `activeCardIndex()` 到 `TouhouBoss`（`default` 方法，零回归）。
2. 各 BOSS 覆写 `bossTier()`；`FlandreEntity` 给占位值。
3. `SpellCard.name` → `Component`，改 14 处构造 + 2 个调试命令；补 28 个 lang 键；改 `lang_audit.py`。
4. 加 `SpellCardNamePayload` + 注册 + 切卡单播 + `StartTracking` 补发。
5. 渲染层切九宫格 + 5 阶造型映射 + 缺贴图降级。
6. 跑 `tools/lang_audit.py`、`gradlew build`、`openspec validate --strict`。

**回滚**：无持久化数据变更（`bossTier()` 是编译期常数、不落 NBT；payload 是纯展示通道）。逐条回退 Java 改动即可，无迁移脚本。config 的 `TALISMAN_BAR_ROW_HEIGHT` 默认值改动属配置语义变更，**回滚时需一并改回 28**，否则符卡行会与下一根血条重叠。

## Open Questions

- **正式阶级表** —— 寝宫 BOSS 与高阶内容落地后重定（`phase-d-high-tier-content` / 寝宫 worldgen）。届时 `frame_3`/`frame_4` 大概率要重画。
- **正式美术** —— 6 张占位贴图整体替换。规格已冻结（尺寸 / 九宫格切片 / 绘制序 / 染色层），替换时 Java 侧零改动。
- **`SpellCard.name` 的 en_us 译名** —— 14 张卡的英文名本变更不提供正式译名，先用音译或占位。这是内容工作不是技术债。
