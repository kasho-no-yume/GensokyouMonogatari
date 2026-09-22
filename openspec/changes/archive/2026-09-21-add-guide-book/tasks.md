## 1. 构建与依赖接入

- [x] 1.1 build.gradle 增加 blamejared maven 仓库与 Patchouli 外部依赖（compileOnly `vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE:api` + runtimeOnly 完整包；不引入 jarJar）
- [x] 1.2 neoforge.mods.toml 声明 `patchouli` 必需依赖，确认 JEI 可选声明不变
- [x] 1.3 验证依赖解析与产物（compileJava/build 通过；产物 jar 0 个 patchouli 类、无 jarjar metadata）

## 2. 书本体与打开行为

- [x] 2.1 创建 `data/gensokyou/patchouli_books/gensokyou_book/book.json`（`use_resource_pack: true` + `dont_generate_book` + `custom_book_item=gensokyou:guide_book`、`i18n`、`show_toasts`、`landing_text`、书纹理/配色）
- [x] 2.2 重写 GuideBookItem.use()：**仅服务端** `openBookGUI(ServerPlayer,…)` / `openBookEntry(ServerPlayer,…)`；`isStub` 作防御回退占位聊天消息（原 6 行键保留），客户端不执行打开逻辑
- [x] 2.3 服务端首开序言：玩家持久化标记 `guidebook_read`（DataAttachment，入档/死亡保留），未读 → `openBookEntry(序言)` 并置位，已读 → `openBookGUI`

## 3. 进度解锁 advancement

- [x] 3.1 创建隐形 advancement `guide/nether_unlock`（changed_dimension → the_nether，无 display）
- [x] 3.2 创建隐形 advancement `guide/end_unlock`（changed_dimension → the_end）
- [x] 3.3 创建 5 个阶级隐形 advancement `guide/tier_1..5`（criteria `minecraft:impossible`）+ `GuideTierProgress` 按 `temperLevel` 自动授予/回收

## 4. 六章骨架、序言与样例条目

- [x] 4.1 创建六个章节 category JSON（term/spellcards/monsters/npcs/rituals/weapons），置于 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/categories/`（**en_us 为基准目录**）并配 lang 键
- [x] 4.2 创建序言条目（含首开门槛说明、凑书提示彩蛋）与各章至少一个样例条目（置于 `en_us/entries/`，正文可中文）
- [x] 4.3 样例仪式条目挂 advancement+secret 演示 0/1/2 阶门控三态（0 阶无条件、1 阶挂 nether、2 阶挂 end）
- [x] 4.4 lang 键全量核对（zh_cn 无裸 key、缺键回落中文占位）

## 5. 残页增强与配方

- [x] 5.1 memory_fragment 由 registerSimpleItem 改为自定义 Item 子类，覆写 appendHoverText（lang 键「凑齐9张也许可以拼凑出一本书……」）
- [x] 5.2 配方 JSON：残页→纸（shapeless）、9 残页→指导书（3×3 shaped）
- [ ] 5.3 游戏内验证两个配方与 tooltip（待用户运行游戏确认）

## 6. 仪式页：原生 3D multiblock + 配方卡（方案 B：同步 + 本地缓存）

- [x] 6.1 RitualRecipeLoader 保留原始 JSON（与 RitualPatternLoader.rawOf 对齐），供同步下发
- [x] 6.2 新增 S2C 同步 payload：在 `OnDatapackSyncEvent`（登录 + `/reload`）下发 `rituals`/`ritual_recipes` 原始 JSON（GZIP 压缩为 byte[]，单包；避开 STRING_UTF8 上限）
- [x] 6.3 客户端接收后复用现有 parser 重建对象、写入本地缓存（`config/gensokyou/ritual_data.json`），并提供 client 包查询入口（byId）
- [x] 6.4 客户端注册 builtin 页面模板 `ritual_page`（template JSON）与 `IComponentProcessor`（ritual id 入参、惰性取数、未同步/缺失数据占位）
- [x] 6.5 配方卡区渲染：原料×数量网格、产物/本地化效果名、spCost、minTier（复用 RitualRecipe 访问器与 jei-ritual-display 同一 lang 键）
- [x] 6.6 新增构建期生成器 `tools/gen_ritual_multiblock.py`：仪式 v5 pattern（复刻 loader 四重对称）→ **按阶级裁剪**的每阶内联 dense multiblock（层 y 上→下 / 行 z / 字符 x；EXACT→id、TAG→`#tag`、AIR→空格、IGNORE→`_`、锚点→`0`）
- [x] 6.7 条目改为：每阶一个 `patchouli:multiblock` 页 + 每阶一个 `gensokyou:ritual_tier_page`（参数/材料），各挂 `guide/tier_N` 门槛；配方一配方一页（`recipe_index`）；两自定义组件（`RitualPageComponent`/`RitualTierComponent`）+ 通用透传 processor + 模板
- [ ] 6.8 游戏内验证：分阶（进下界/末地或提阶后仅显示 ≤ 当前阶）、3D 结构 + 眼睛投影、配方一页一个不超框、参数/材料正确、`/reload`/断线缓存、dedicated server 无客户端类加载（待用户运行游戏确认）

## 7. 图标重绘

- [x] 7.1 按 gen-textures 流程重绘 guide_book.png（参照 codex_of_beings.png：书册轮廓+底色，加金边/宝石扣/缎带书签装饰）
- [ ] 7.2 游戏内/视觉确认图标呈现与模型引用（待用户确认）

## 8. 验收

- [ ] 8.1 全量回归：首开序言→已读落地页、章节列表六章、无 JEI 环境书内配方自洽（待用户运行游戏）
- [x] 8.2 编译 + 既有测试通过（gradlew build 成功）
- [x] 8.3 发布物不含 Patchouli（jar 0 引用），缺失时由 required 依赖声明报错
