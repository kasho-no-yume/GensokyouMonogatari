# Tasks: ritual-wand-dev-editor

## 1. 合规校验器（纯函数，先行立基）

- [x] 1.1 新增 `ritual/RitualPatternValidator`：移植 python 5 规则（增量相交 / level 重复 / 锚点三条件 / 品阶下限 WARN 含石台特例 / 跨 pattern 劫持），输入合成后 pattern + 全体 pattern 列表
- [x] 1.2 对表测试：JUnit 读 `src/main/resources/data/gensokyou/rituals/*.json` 全体 pattern，断言 Java 校验器 ERROR/WARN 集合与 `validate_ritual_pattern.py` 输出一致（python 结果固化为 fixture）
- [x] 1.3 违规消息格式对齐 `ERROR: <pattern> <格位>: <原因>` 单行惯例（聊天栏逐条回显用）

## 2. diff 捕获管线

- [x] 2.1 diff 捕获落地为**独立纯函数类 `ritual/editor/RitualDiffCapture`**（偏差说明：不放进 `RitualCapture`——捕获核心不触碰 Level/注册表，世界扫描拆到 `RitualEditorPlacement.scanWorkspace`，这是无注册表单测的前提；对称/锚点/朝向校验复用 loader 同款 `expandInto` 纯几何）；入参 = 工作区格图 + 地基切片 cumulative(N-1) + palette 视图；产出 `Patch(addsEntries, paletteAdditions, violations, keptCells)`
- [x] 2.2 key 判定次序实现：既有 palette 谓词复用 → 石/台标签反导（`_N_plus`；0 阶全量石标签、1 阶台 `_2_plus` 特例）→ 新 EXACT key（A..Z 除 C，排除锚点键复用）
- [x] 2.3 序列化器：内存条目 → 规范四分之一 v5 adds 位置式数组（off-axis 绝对值象限、轴上归并 `(0,d)`；**朝向输出为 int 常量**——与现有 `RitualCapture` 骨架一致而非字符串名，loader 双格式通吃），四重展开自检（`expandInto` 复展开 + 地基相交比对）不过即拒产不落盘
- [x] 2.4 单测：地基改动报违规、本阶自由增删、轴上归并、key 复用与标签反导、自检拦截坏补丁（`RitualDiffCaptureTest` 9 例 + `RitualDraftAndMergeTest` 3 例）

## 3. 编辑杖物品与状态

- [x] 3.1 `ModDataComponents` 新增 `RITUAL_EDITOR_STATE`：锚定点+维度、`BuilderSelection` 复用、`Map<level, Workspace(sizeX,sizeZ,height,yOffset)>`（x/z 恒奇数保核心居中），Codec 持久化 + StreamCodec 同步
- [x] 3.2 `RitualWandItem` 重构：非潜行右键核心 = 纯锚定（成型与否皆可，`hasInfiniteMaterials()` 硬闸在前）；右键非核心提示；潜行右键开编辑菜单（事件闸 `RitualWandHandler` 拦截，防成型核心抢回）；A/B 两点框选与 `FIRST_CORNER` 静态表已移除
- [x] 3.3 `RitualCoreBlock.useItemOn`/`useWithoutItem` 两道守卫扩编辑杖分支：成型核心持杖不开 GUI 只让位锚定（生存持杖仅提示，与 `ritual-editor-anchor` 硬闸一致）
- [x] 3.4 动作入口统一惰性校验（`RitualEditorActions.anchorValid`）：锚点仍为 `ritual_core` + 同维度 + 区块已加载，失效即拒并提示
- [x] 3.5 配置 `EDITOR_MAX_DIMENSION`（**默认 48**，偏差说明：现行 kagutsuchi 阶级 3 半径已达 20+，design 的 32 会卡住大仪式工作区；上限 128 可配）钳制工作区轴长

## 4. 力建 + sweep + 三色预览

- [x] 4.1 新增 `ritual/editor/RitualEditorPlacement.plan(...)` 纯计算：切片 → 放置(含覆盖)/清空(AIR/IGNORE)/豁免(锚点) + 工作区 sweep 清单三分类；默认工作区 = 阶级切片 AABB（y 偏移 = minY，`defaultWorkspace`）
- [x] 4.2 两段式确认：第一击计算三分类存 transient 附件 `RITUAL_EDITOR_PREVIEW` + S2C 预览 payload（有草稿时附合成 pattern JSON，客户端 parseForEdit 本地重算）；第二击全等才执行（不扣料、无冲突中止），`apply` 先清后建
- [x] 4.3 新增 `RitualEditorPreviewRenderer`：绿/橙幽灵复用 `RitualGhostRenderTypes`（Tint uniform 分批），品红复用 `RitualConflictRenderer.OVERLAY_LINES`；持杖/同维度/锚点核心在场三条件门控
- [x] 4.4 回执：`msg.gensokyou.editor_built` "放置 ×覆盖 ×清除"计数

## 5. 草稿覆盖层与保存产出

- [x] 5.1 `RitualDraftStorage`（世界级 saved data `gensokyou_ritual_drafts`）：按 patternId+level 存 adds 草稿；`RitualEditorActions.composed/composedRaw` = 原 pattern ⊕ 草稿合成入口；仪式保存成功清该 pattern 全部草稿
- [x] 5.2 捕获→阶级保存链路：C2S 捕获请求 → diff 捕获 → 存草稿（不校验不落盘），回显违规/条目数；自检"内部冲突"时拒存、旧草稿不动
- [x] 5.3 仪式保存链路：合成 → 校验器对全体 pattern 语料跑（目标合成替换自身原文）→ ERROR 即拒不落盘；通过 → 覆写 `<world>/datapacks/gs_dev/data/gensokyou/rituals/<path>.json`（自动建目录与 `pack.mcmeta`）+ 回显"/reload 生效"
- [x] 5.4 dev 源码树回声：GAMEDIR 向上探测 `src/main/resources/data/gensokyou/rituals`（`RitualEditorFileOps.detectDevSourceRituals`），存在则同步覆写，不存在回执注明跳过
- [x] 5.5 单测：pack.mcmeta 生成、同 id 覆写自身、dev 探测命中/缺席、生产环境跳过回声（`RitualEditorFileOpsTest` 3 例；合成序覆盖由 `RitualDraftAndMergeTest` 承担）

## 6. 编辑菜单 GUI

- [x] 6.1 新增 `RitualEditorMenu`（零槽握手，仿 `RitualBuilderMenu`）+ `ModMenus.RITUAL_EDITOR` 注册 + 编辑杖 `stillValid` 挂接（杖不离手）
- [x] 6.2 `RitualEditorScreen`：仪式列表（复用构建杖滚动列表范式）+ 阶级按钮（图案 levels 驱动）+ 工作区 4 轴 ± 控件（尺寸步进 2 保持奇数、y 偏移步进 1）+ 捕获存阶/仪式保存/清锚三动作按钮；**力建不入按钮**——按 spec 走"右键已锚核心两段式"，界面底部提示行说明（偏差说明）
- [x] 6.3 C2S payloads：`EditorCommandPayload`（select/set_workspace/capture/save/clear_anchor 五动作）+ S2C `EditorPreviewPayload`；服务端 `handleEditorCommand` 逐入口重复创造闸 + 持杖校验
- [x] 6.4 编辑杖 tooltip：锚点坐标+维度（AQUA）、所选仪式+阶级（YELLOW）、本阶级工作区参数（GRAY）

## 7. 回归与实机

- [x] 7.1 回归（静态）：构建杖路径零改动（builder 分支先于/独立于 wand 分支，`RitualPatternLoader.apply` 仅追加 raw 留存不改排序/解析，命令未动，ModNetworking 既有注册不动）；全量编译 + 30 测试通过
- [ ] 7.2 实机闭环（**用户跑 dev 客户端**）：锚定内置仪式核心 → 力建 0 阶 → 改块 → 捕获存阶 → 仪式保存 → /reload → 重开编辑确认变更生效；覆盖测试含"他仪式原地换代"与"切 0 阶不伤 3 阶外环"
- [ ] 7.3 安全回归（**用户跑 dev 客户端**）：生存模式全动作被拒；锚点核心被拆后动作被拒；工作区拉满 48³ 的预览计数正确
- [x] 7.4 文档回写：ritual-design skill §5/§6 工作流补"编辑杖闭环"段落（捕获/保存即新事实源，python 校验降为对表兜底）
