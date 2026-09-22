# Proposal: 幻想乡物语指导书框架（Patchouli）

## Why

mod 已积累仪式、符卡、武器、生物、经济等多个系统，但除一段占位聊天消息外没有任何游戏内文档，玩家在没有 JEI 的情况下无法理解玩法。需要一本正式的指导书《幻想乡物语》作为唯一权威教程载体（JEI 仅为可选加速器），并以此为基础建立后续内容持续入书的内容管线。

选用社区标准库 mod **Patchouli（帕秋莉的魔法书，1.21.1-93-NEOFORGE，约 632KB）** 提供指导书框架：数据驱动条目、advancement 进度锁、自绘配方页、multiblock 3D 投影。经调研其能力完全覆盖本需求，且以**外部必需依赖**方式接入（不 JarJar 内嵌）——Patchouli 是主流整合包常驻 mod，外部依赖可避免再分发其 CC-BY-NC-SA 资产与 refmap 的许可顾虑，也免去与整合包已有 Patchouli 的版本协商。

## What Changes

- **接入 Patchouli 作为外部必需依赖**：`build.gradle` 增加 blamejared maven 仓库与 `compileOnly :api` + `runtimeOnly`（开发运行，坐标 `vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE`）；`neoforge.mods.toml` 声明 `patchouli` 必需依赖（不内嵌，玩家/整合包须自行安装）；全部 Java 侧调用走 `vazkii.patchouli.api` 公共 API
- **《幻想乡物语》书本体**：`book.json` 置于 `data/gensokyou/patchouli_books/gensokyou_book/`（含 `use_resource_pack: true`），章节/条目/模板置于 `assets/gensokyou/patchouli_books/gensokyou_book/<lang>/`；保留现有 `gensokyou:guide_book` 物品作为书本体（`dont_generate_book` + `custom_book_item` 指向，右键打开 Patchouli GUI，替代占位聊天消息）
- **六大章节骨架**：术语解释、符卡列表、敌对生物列表、NPC 列表、仪式列表、武器系统（内容以 `en_us` 为基准目录：中文正文可直写 `en_us`，`zh_cn` 作覆盖）
- **序言机制**：首次打开书自动翻到序言条目（**纯服务端** `openBookEntry(ServerPlayer,…)` + 玩家持久化已读标记 DataAttachment）；已读玩家打开书落在章节列表落地页
- **进度解锁机制**：隐形的引导 advancement 树（进入下界 / 进入末地，`changed_dimension` 触发器）；条目以 `"advancement"` + `"secret": true` 实现解锁前完全不显示，解锁时 toast 提示；0 阶内容无条件可见
- **仪式章节配方卡与结构展示**：配方卡用 1 个 Patchouli 页面模板 + component processor 从 `ritual_recipes` 渲染（字段与 `jei-ritual-display` 对齐）；结构用 Patchouli 原生 `patchouli:multiblock` 页做 3D 投影（可在世界上幽灵照搭），其多重方块由构建期生成器 `tools/gen_ritual_multiblock.py` 从 `rituals` 数据内联生成；配方的运行期取数采用服务端登录/datapack-sync 下发原始 JSON + 本地缓存兜底（见 design D6）
- **记忆残页增强**：`memory_fragment` 增加 tooltip「凑齐9张也许可以拼凑出一本书……」（需为其注册自定义 Item 子类）；新增 2 个原版配方：1×1 无序合成出纸、3×3 平铺 9 张合成《幻想乡物语》
- **书图标重绘**：参照 `codex_of_beings.png` 风格重绘 `guide_book.png`（16×16，更华丽装饰）
- **说明性内容撰写**：本 change 交付框架 + 全部六章骨架与样例条目；逐条目的完整文案按章节增量追加（Patchouli 支持 `/patchouli reload` 热重载，后续按小 change 补内容）

## Capabilities

### New Capabilities

- `guide-book`：指导书框架整体——Patchouli 外部依赖接入、书本体物品与打开行为、首次序言、六大章节骨架、进度解锁 advancement 机制、仪式自定义配方页模板、书页配方与残页 tooltip

### Modified Capabilities

- `fairy-ecology`：「引导书获取与内容」需求变更——原"仅击杀大妖精掉落获得、不合成"放宽：保留大妖精掉落（占位不变），新增 9 张记忆残页 3×3 合成途径；记忆残页增加提示 tooltip；右键行为从聊天消息改为打开指导书 GUI

## Impact

- **构建**：`build.gradle` 增加 Patchouli maven 仓库（blamejared）与 `compileOnly "vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE:api"` + `runtimeOnly "vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE"`；不引入 jarJar；`neoforge.mods.toml` 增加 `patchouli` 必需依赖声明
- **代码**：`GuideBookItem` 重写（**服务端**打开书 + 首次序言标记，玩家持久化已读 DataAttachment）；`ModItems` 为 `memory_fragment` 换自定义 Item 子类（tooltip）；客户端 Patchouli 胶水（注册 multiblock/模板/页面处理器）
- **资源**：`data/gensokyou/patchouli_books/gensokyou_book/book.json`；`assets/gensokyou/patchouli_books/gensokyou_book/en_us/**`（6 章分类、序言与样例条目、自定义模板，`zh_cn/**` 作覆盖）；`data/gensokyou/advancement/guide/` 两个隐形引导 advancement；`data/gensokyou/recipe/` 残页 2 配方；lang 键族；`guide_book.png` 重绘
- **依赖**：Patchouli 为运行时必需 **外部** 依赖（不内嵌；缺失由加载器报错，不静默降级）；JEI 保持可选软依赖不变
- **风险**：整合包/玩家需自装 Patchouli（发布页须注明）；Patchouli config 中玩家可关闭进度锁（调试便利，不影响常规体验）；仪式自定义页面是本 change 唯一的新渲染代码，需客户端专属路径（dedicated server 不加载客户端类）
