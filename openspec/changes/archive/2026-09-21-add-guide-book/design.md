# Design: add-guide-book（幻想乡物语指导书 · Patchouli 框架）

## Context

- 现状：`gensokyou:guide_book` 右键输出 6 行占位聊天消息；`memory_fragment`（记忆残页）由小妖精必掉；大妖精直掉整书（占位，另有 change 处理）。
- NeoForge 21.1 / MC 1.21.1；JEI 为 compileOnly + localRuntime 软依赖；Geckolib 已是普通 implementation 依赖。
- `jei-ritual-display` 已确立仪式配方"数据单源"原则（`ritual_recipes/*.json` → JEI 卡片，零 Java 增删）；仪式结构为 `ritual-pattern-system` 的 v5 JSON（`levels[].adds = [char,dx,dy,dz]` 四重对称四分之一增量，加载期展开为全量 `RitualPattern.LevelSlice.blocks`；palette 含 EXACT/TAG/AIR/IGNORE 谓词语义）。
- 仪式 pattern/recipe 的 loader 均为**服务端** datapack reload（`ReloadListenerHandler` 的 `AddReloadListenerEvent`，server-only）；多人服务器客户端不持有该数据（JEI 客户端同步 `RitualRecipeLoader.all()` 仅集成服务器同 JVM 有效）。
- Patchouli 1.21.1-93-NEOFORGE（约 632KB）官方提供 NeoForge 构建；从 1.21.1 起仅支持 NeoForge；API 入口 `vazkii.patchouli.api`（api jar 内含 stub 实现，`isStub()` 可判）。

## Goals / Non-Goals

**Goals:**
- 以**外部必需依赖**接入 Patchouli，不内嵌、不再分发其资产
- 书为唯一权威教程：无 JEI 完全自洽；有 JEI 时体验对齐
- 进度解锁 = 维度门槛（下界/末地），解锁前条目完全不出现
- 仪式章节与 JEI 共用同一数据源，新增仪式数据零 Java 入书
- 内容可持续增量追加（`/patchouli reload` 热重载迭代）

**Non-Goals:**
- 大妖精掉落逻辑调整（另行 change）
- 六章全部条目的最终文案（本 change 交付框架 + 样例条目）
- en_us 书文案补齐（主内容放 en_us，正文可中文；`zh_cn` 覆盖按需后续补）
- 玩家自定义笔记、任务清单系统（Patchouli quest 页如需后续启用）

## Decisions

### D1 依赖策略：外部必需依赖 + api 隔离

```gradle
repositories { maven { url = 'https://maven.blamejared.com' } }
dependencies {
    compileOnly "vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE:api"
    runtimeOnly "vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE"
}
```

- 坐标以 maven.blamejared.com 实际发布为准：artifact 是 `Patchouli`（大写），版本 `1.21.1-93-NEOFORGE`；`compileOnly` 用 `:api` classifier 仅暴露公共 API，`runtimeOnly` 完整包供 dev 运行
- `neoforge.mods.toml`：`patchouli` 声明为必需依赖（外部安装满足），`jei` 保持可选不变
- 代码纪律：业务代码只准 import `vazkii.patchouli.api.*`；客户端专属引用放 `client` 包
- 否决 JarJar 内嵌：Patchouli 为 CC-BY-NC-SA 3.0，README 明确提示 JarJar 会连带再分发含原始 Mojmap 的 refmap；外部依赖亦免去与整合包已有 Patchouli 的版本协商。代价：玩家/整合包须自装 Patchouli（required 缺失即加载失败）
- 否决自研 GUI：配方渲染/3D 投影重造轮子成本过高
- dev 环境以 runtimeOnly 完整包运行；发布产物不携带 Patchouli

### D2 书本体：复用 `gensokyou:guide_book`

book.json（路径 `data/gensokyou/patchouli_books/gensokyou_book/book.json`）设 `use_resource_pack: true` + `dont_generate_book: true` + `custom_book_item: "gensokyou:guide_book"`：不生成 patchouli:book 动态物品，模型/贴图/创造栏归属完全自控（需求 6 的重绘直接落在 `guide_book.png`），残页 3×3 合成输出即本物品（原版 shaped 配方即可，无需 `patchouli:shaped_book_recipe`）。

`GuideBookItem.use()` 重写（打开逻辑全部在服务端，客户端不参与）：
- 服务端：按持久化标记判首开 → `PatchouliAPI.get().openBookEntry(player, BOOK_ID, 序言entry, 0)` 或 `openBookGUI(player, BOOK_ID)`（见 D3），由服务端 API 发包驱动客户端打开
- `isStub()` 为防御分支（外部必需依赖下不可达）：回退原占位聊天消息
- 客户端不执行打开逻辑；客户端专属渲染引用放 `client` 包，dedicated server 零加载

### D3 首次序言：服务端 openBookEntry + 持久化已读标记（DataAttachment）

- 首次右键（服务端）读玩家持久化标记（DataAttachment `guidebook_read`）：未读 → `openBookEntry(player, BOOK_ID, 序言entry, 0)` 并置位已读；已读 → `openBookGUI(player, BOOK_ID)`（落章节列表）
- 选择服务端入口而非客户端判读：玩家持久化数据读写天然双端一致，且避免客户端打开时序竞态
- 否决隐形 advancement 记"已读"：持久化标记更轻，且 advancement 会把"读过序言"混入进度体系造成语义噪音
- 老档玩家（改版前拿过书）视为未读，会再看到一次序言：可接受，不做迁移

### D4 进度解锁：两个隐形维度 advancement + secret 条目

- `data/gensokyou/advancement/guide/nether_unlock.json`：criteria `changed_dimension → the_nether`，无 display（隐形）
- `.../guide/end_unlock.json`：`changed_dimension → the_end`
- 条目 JSON：`"advancement": "gensokyou:guide/nether_unlock", "secret": true` —— 解锁前条目**完全不显示**（`secret` 使其不落"灰色锁"态），解锁瞬间 toast（书级 `show_toasts: true`）
- 门控语义（需求 3）：0 阶内容无 advancement 字段（无条件）；1 阶条目挂 nether_unlock；2 阶及以上挂 end_unlock。若未来需要"完成某仪式"类复合门槛，再加隐形 advancement（criteria 用 `minecraft:impossible` 由代码 grant），机制不变
- 注意：Patchouli config 可被玩家关闭进度锁 —— 接受（调试便利，不破坏常规体验）
- **阶级门槛**：另建 5 个隐形 advancement `guide/tier_1..5`（criteria `minecraft:impossible`，无 display）；`GuideTierProgress` 每 20 tick 按玩家 `temperLevel` 自动授予 ≤N、回收 >N。用于仪式条目内**分阶页**（结构 / 参数材料）的页级门控，实现「以玩家当前实际进度为上限」（Patchouli 对锁住的页完全隐藏）

### D5 书组织：book.json 在 data、内容在 assets、en_us 为基准

```
data/gensokyou/patchouli_books/gensokyou_book/
  book.json                    # use_resource_pack:true、dont_generate_book、custom_book_item、i18n、show_toasts、landing_text、书纹理/配色
assets/gensokyou/patchouli_books/gensokyou_book/
  en_us/categories/term.json        # 术语解释（基准内容，正文可写中文）
  en_us/categories/spellcards.json
  en_us/categories/monsters.json
  en_us/categories/npcs.json
  en_us/categories/rituals.json
  en_us/categories/weapons.json
  en_us/entries/...                 # 序言 + 每章样例条目
  en_us/templates/ritual_page.json
  zh_cn/categories/...              # 可选覆盖（按需逐步补）
  zh_cn/entries/...
  zh_cn/templates/...
```

- 1.20+ **强制** `"use_resource_pack": true`，且 book.json 必须在 `data/<ns>/patchouli_books/<book>/`；categories/entries/templates 才在 `assets/...`
- `en_us/` 是 Patchouli 的**基准目录**（官方："en_us 里的内容永远是被加载的主内容"）：主内容必须放 en_us，中文正文可直接写在 en_us；`zh_cn/` 仅覆盖需要差异化的文件。`i18n: true` 只影响"渲染前查 lang 键"，不改变基准目录
- landing_text 放一句话欢迎语常驻落地页（与"首开序言"互补，避免落地页空洞）

### D6 仪式章节：配方卡自定义页 + 原生 3D multiblock（构建期内联）

- 结构展示用 Patchouli 原生 `patchouli:multiblock` 页 + **构建期内联定义**（`"multiblock": {pattern, mapping, symmetrical}`）：`tools/gen_ritual_multiblock.py` 读取 `data/gensokyou/rituals/*.json`（v5 四分之一对称增量），复刻 `RitualPatternLoader` 展开规则得到全量方块，**按阶级裁剪出每阶的累计结构**，每阶一个内联 multiblock 页（挂 `guide/tier_N` 门槛）；输出「层 y 上→下 / 行 z 北→南 / 字符 x 西→东」的 dense pattern；palette 谓词映射：EXACT→方块 id、TAG→`#tag`、AIR→空格、IGNORE→`_`（任意方块）、锚点→`0`（映射到核心块）。玩家可点「眼睛」在世界上 3D 幽灵投影照搭
- 每阶另有一页 `gensokyou:ritual_tier_page`（`RitualTierComponent`，运行时从缓存渲染）：该阶的**仪式参数**（minTier ≤ 该阶的配方 spCost/minPlayerTier、供品消耗模式）与**搭建到该阶的累计材料**（按谓词聚合计数），同样挂 `guide/tier_N` 门槛
- 配方卡**一配方一页**（条目预置该仪式配方数等量的 `gensokyou:ritual_page` 页，组件按 `recipe_index` 渲染对应配方），避免自定义组件不自动分页导致的超框
- 否决运行期 `multiblock_id` 注册：`PageMultiblock.build` 在书内容构建时即解析该 id，未注册会抛 `IllegalArgumentException` 破坏整本书；而仪式数据是登录后才同步到客户端的，时机对不上
- 否决自定义页画 2D 分层网格：可读性差，且与原生 3D 投影功能重复
- 自定义模板 `ritual_page` + `IComponentProcessor` 子类（客户端）**仅渲染配方卡**：条目页 JSON 传 ritual id，processor 从客户端缓存 `ritual_recipes` 取数（输入物品×数量、产物/效果名、spCost、minTier；复用 `RitualRecipe` 访问器与 `jei-ritual-display` 同一 lang 键）
- **客户端取数（方案 B：服务端同步 + 本地缓存）**：processor 在客户端运行，多人服务器上客户端没有服务端 datapack 数据。故在 `OnDatapackSyncEvent`（覆盖玩家登录与 `/reload`）由服务端下发 `rituals`/`ritual_recipes` 的**原始 JSON**（GZIP byte[]），客户端复用现有 parser 重建对象并写入本地缓存（`config/gensokyou/ritual_data.json`）；断线/重连读缓存，数据未到达前渲染占位
- 代价：新增/修改仪式 pattern 后需重跑 `tools/gen_ritual_multiblock.py` 重新生成条目（生成器已提供，后续可接成构建任务）；配方卡的运行期数据仍走同步，保持单源
- 双端安全：模板/渲染/同步全部客户端路径，dedicated server 零加载

### D7 残页增强：tooltip + 两配方

- `memory_fragment` tooltip：为其注册自定义 Item 子类覆写 `appendHoverText`（`ModItems.MEMORY_FRAGMENT` 由 `registerSimpleItem` 改为自定义类）加「凑齐9张也许可以拼凑出一本书……」（灰色斜体，lang 键）
- `data/gensokyou/recipe/memory_page_to_paper.json`（shapeless：1 残页 → 1 纸）
- `.../recipe/memory_pages_to_guide_book.json`（shaped 3×3 全残页 → guide_book）

### D8 图标重绘

`guide_book.png` 16×16 重绘：参照 `codex_of_beings.png` 的书册轮廓与底色调，增加金色镶边、中央宝石扣、缎带书签等华丽装饰；走 `gen-textures` 技能的 ASCII 像素图 + 调色板流程。

## Risks / Trade-offs

- [外部依赖缺失即加载失败] → `required` 声明给出明确报错；发布页注明需装 Patchouli 1.21.1-93-NEOFORGE（或兼容版本）
- [Patchouli 类在 dedicated server 被误加载] → 打开走服务端安全方法（`openBookGUI(ServerPlayer,…)`），客户端渲染类隔离在 client 包
- [仪式同步包体积/时序] → 原始 JSON 可能较大，必要时分片或 gzip；未到达前占位、到达后写本地缓存；`OnDatapackSyncEvent` 保证登录与 `/reload` 均可刷新
- [book.json/内容路径与 use_resource_pack 误配导致零内容] → D5 明确 data/assets 分工与 en_us 基准
- [书条目与 lang 键量大导致裸 key] → 模板 processor 与条目一律走 lang 键；zh_cn 缺失键回落中文占位文案，验收时 grep `gensokyou.book.` 全量键核对
- [pattern→multiblock 转换对既有仪式（水/品阶仪式石 tag）的边界] → palette 映射表显式列出全部字符语义，未知字符按 anyMatcher 兜底并打 log
- [玩家 config 关锁 / JEI 关锁联动] → 接受；文档内说明
- [首次序言对老档重放一次] → 接受（见 D3）

## Migration Plan

- 无数据迁移：物品 id 不变，老玩家背包中的 guide_book 自动获得新行为
- 回滚：将 `patchouli` 依赖改回 optional 并恢复占位聊天实现（书资源文件滞留无害）

## Open Questions

- 仪式 multiblock 3D 投影页是否随本 change 一次交付（任务拆分时若工期紧可先交付分层网格图，投影页作收尾任务）
- en_us 书文案补齐时点（建议首个发布候选前）
