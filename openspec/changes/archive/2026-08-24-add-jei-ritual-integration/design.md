# Design: add-jei-ritual-integration

## Context

仪式结构已由 `RitualPattern`（`data/gensokyou/rituals/*.json`）数据驱动，`RitualPatternLoader` 为 `SimpleJsonResourceReloadListener`，客户端资源重载后同样持有全部 pattern。项目为 NeoForge 1.21.1 + ModDevGradle 2.x，`run/mods/` 不参与 dev 运行时加载；build.gradle 已有模板预留的 JEI 依赖注释位。项目构建已有国内镜像（aliyun/bmclapi）网络优化。

## Goals / Non-Goals

**Goals:**

- 开发运行时可见 JEI；本 mod 对 JEI 为可选软依赖，JEI 缺席零影响
- 每个 RitualPattern 在 JEI 中呈现为一个条目：分层物品网格 + 标题 + 说明
- 在核心/祭品台/催化剂物品上按 U 可列出关联仪式
- 数据只读消费，不改动 ritual 包任何现有代码

**Non-Goals:**

- 伪 3D 立体方块渲染（探索结论：小尺寸下收益不足）
- 仪式行为配方展示（分解/聚合输入输出等——留给后续变更扩展同一 category）
- 发布产物内置 JEI 或硬依赖
- dedicated server 下远程客户端的 JEI 图鉴展示（已知限制，见 D7）
- /reload 后会话内的 JEI 即时刷新（保留可选增强位，见 D7）

## Decisions

### D1 依赖接入方式：gradle 声明而非 run/mods

ModDevGradle 不加载 `run/mods/` 目录。采用模板既定方案：

```groovy
compileOnly "mezz.jei:jei-${mc_version}-common-api:${jei_version}"
compileOnly "mezz.jei:jei-${mc_version}-neoforge-api:${jei_version}"
localRuntime "mezz.jei:jei-${mc_version}-neoforge:${jei_version}"
```

仓库加 `https://maven.blamejared.com/`（JEI 官方分发源）。版本取 1.21.1 线最新 release（19.44.0.x）。`localRuntime` 保证不传染发布依赖。
备选：curse maven 拉 jar 文件依赖——被否，API 与完整包分离的官方 maven 形态更干净。

### D2 软依赖安全性：entrypoint 隔离

JEI 插件通过 `neoforge.mods.toml` 的 `[[mods.jeiPlugin]]`… 实际形态为 entrypoint 声明（NeoForge 下 JEI 以 `IModPlugin` + mod entrypoint 发现）。插件类仅被 JEI 存在时实例化，故 `gensokyou.jar` 单独运行时不会触碰任何 JEI 类 → 无 `NoClassDefFoundError` 风险。本体代码零 JEI import。

### D3 展示模型：一个 category + 每 pattern 一个 recipe 包装

```
GensokyouJeiPlugin (IModPlugin)
 ├─ registerCategories → RitualCategory
 └─ registerRecipes    → RitualPatternLoader.all()
                          └─ 每个包装为 RitualRecipeWrapper(pattern)
RitualCategory.draw(recipe, ...) :
   for level in pattern.levels():
       按 slices 字符画逐格取 palette → 代表 ItemStack → drawItem
       锚点格叠加高亮框；tag 格取代表物
U 键入口：registerItemSubtypes/关联挂载 —— 核心/祭品台/催化剂 item → 全部含该 item 的 ritual 条目
```

选 JEI recipe 体系（而非 information tab/tooltips）：免费获得搜索、按 U/R 关联、分页等基础设施。

### D4 tag 代表物与锚点标注

- palette TAG 条目：遍历 `BuiltInRegistries.BLOCK.getTag(tag)` 取首个有对应物品的方块；无成员时画屏障占位并 tooltip 提示 tag 名
- EXACT/AIR 同理映射为方块→物品；AIR 不绘制（留白）
- 锚点（anchorKey）格：绘制后叠半透明白色描边框，tooltip 显示「锚点」
- `_ignore` 格不绘制

### D5 布局：固定视口 + 拖拽平移 + 多层分页（迭代替代初版自适应画布）

- 类别画布定尺 170×143（标题带 13 + 视口 130），不再随结构增大；超大结构（≤16×16/层）靠拖拽查看全貌
- `StructureViewWidget` 同时实现 IRecipeWidget + IJeiInputHandler，经 createRecipeExtras 注册、随配方页存活：`handleMouseDragged` 平移、`handleMouseScrolled` 滚轮平移、包围盒 clamp（内容小于视口时锁死居中）；初始居中
- 渲染：逐格槽位底 + renderFakeItem，越界格整体跳过（无部分绘制）；锚点描边
- 分页规则（迭代修正）：每个仪式恒为一条 JEI 条目；levels >1 时条目内底部居中悬浮「◀ 第 i/N 层 ▶」翻层控件（半透明底、可点击热区），经 handleInput 的 mouse-down 声明 / mouse-up 执行语义切换，切层后视图重新居中；不使用 JEI 外部翻页拆分条目
- 查找关联迁移为隐形成分（可见槽位是静态定位，与拖拽冲突）：结构方块全集 → 隐形 INPUT（跨层可按 U 查到仪式），催化剂 → 隐形 CATALYST；物品悬停 tooltip 由 widget 的 getTooltip 提供
- 对齐与提示：逐层取对齐中心——该层有核心用其核心格中心（匹配器对齐原点语义），无核心用该层包围盒几何中心（中心对称约定下与阵眼轴线重合）；切层/初始定位把该点钉在视口中心；每层绘制贯穿视口的淡色十字准线标出该层对齐中心；内容 bbox 超出视口的方向在边缘中点绘制半透明三角箭头；格子跨界经 enableScissor 平滑裁剪（绝对原点取自 pose 矩阵 m30/m31）

### D6 双端安全

所有 JEI 类位于独立包且仅 entrypoint 触达；注册逻辑不引用 `Minecraft`/渲染类之外的客户端专属 API 于 common 路径。dedicated server 上 JEI 本身以 client-side 方式存在，entrypoint 不触发。

### D7 数据流与刷新模型：运行时增量同步

采用轻量方案（否决服务端同步 payload）。实现中发现纯启动快照不可行：loader 是服务端 reload 监听器，JEI 启动（主菜单期）时 PATTERNS 必为空，单人模式也会显示零个仪式——故将原「可选增强」转正：

- `GensokyouJeiPlugin.syncFromLoader()`：以 pattern 实例同一性对比上次同步集，经 `hideRecipes/addRecipes` 增量推送
- 触发源：`onRuntimeAvailable` + 客户端 tick 轮询（`JeiClientSync`，`Dist.CLIENT` 限端；内部 Bridge 嵌套类隔离 JEI 类引用，保证无 JEI 时不提前加载）
- 单人/集成服务端：loader 与客户端共享 JVM 静态表 → `/reload` 增删改仪式后 JEI 条目同会话即时更新
- dedicated server 远程客户端：其 JVM 静态表为空，图鉴显示为空——已知限制，仪式功能不受影响
- 催化剂↔仪式对应关系用代码内静态映射表（`RitualCatalysts`），随「新增仪式检查单」文档维护；催化剂以隐形 CATALYST 成分进入查找关联，结构方块本身是 INPUT 槽位、天然支持 U 键查找

## Risks / Trade-offs

- [BlameJared maven 国内直连慢] → 构建缓存一次后稳定；必要时后续可加镜像，先不动
- [JEI 19.x API 在 1.21.1 后续版本可能微调] → 锁定 `${jei_version}` 具体版本号，升级走显式变更
- [未来仪式行为需要"输入/输出"槽位] → category 设计预留 wrapper 扩展位（wrapper 持有 pattern 引用即可追加字段），本次不做
- [tag 无有效成员或方块无物品形态] → 屏障占位 + tooltip，不崩溃

## Open Questions

- 催化剂物品与仪式的对应关系目前是隐式约定（如召唤催化剂 ↔ summon_circle），是否需要在 ritual JSON 中显式声明 catalyst item id？（建议：本次用代码内静态映射表，显式声明留待行为配方变更）
