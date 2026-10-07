# 新增仪式检查单

新增 / 删除 / 修改一个仪式时，按此清单逐项核对。

> **格式与设计语义的唯一规范来源是 `.opencode/skills/ritual-design/SKILL.md`（schema v5）**，
> 本文不重复定义格式，只列工程与运营侧事项。
>
> 占位仪式与其 4 条配方（iron/gold/coal 分解、紫水晶聚合，原挂 `processing_circle`）
> 已于 2026-09-09 全部删除，当前 rituals/ 与 ritual_recipes/ 为空；
> 新设计一律直接按 SKILL 规范（0 基阶级）从零起。

JEI 配方卡由 ritual_recipes JSON 自动派生，无需改动任何集成代码；结构查看唯一入口是**仪式构建器**
（JEI 结构页签已于 grace-ux-and-jei-tabs 删除）。

## 1. 结构定义（必做）

- 文件：`src/main/resources/data/gensokyou/rituals/<名称>_circle.json`
- 格式 v5：`palette` + `levels[].adds[]`，条目为**位置数组** `["key",x,y,z(,朝向)?]`，
  **每级只写新增**格位；v3 对象条目与 v4 `blocks` 快照出现即拒载
  （旧文件用 `python tools/validate_ritual_pattern.py --convert-v4` 迁移）
- 四重对称展开、锚点唯一、palette 谓词（方块 id / `#标签` / `air` / `_ignore`）、
  朝向、品阶下限等规则一律见 SKILL §1/§3，此处不重复
- 违规由 loader **拒载**并日志报因（展开冲突 / 锚点异常 / 增量相交 / level 重复）
- 生效方式：游戏内匹配随 `/reload` 即时生效；单人模式下 JEI 同会话同步

## 2. 配方（ritual_recipes）

- 目录：`src/main/resources/data/gensokyou/ritual_recipes/<仪式名>.json`，**一仪式一文件**：顶层 `pattern` + `recipes[]`
  （每条配方含可选 `name`（缺省用文件名派生稳定 id）、下略字段）。兼容旧式"一文件一配方"（无 `recipes` 字段即回退）
- 字段（每条配方）：`mode`（activation 启动型 / passive 持续型，passive 必须给 `result`）、
  `minTier`（最低仪式阶级 = 结构层级；默认 1，可取 0 表示 0 阶可用）、`match`（`exact` 缺省严格等值 / `max` 子集最大匹配）、
  `ingredients[]`（`{item 或 #标签, count}`，无序）、`result`（实物产物）与 `effect`（效果 id，解释权在行为）至少其一；可选 `spCost`
- 匹配语义：`exact` **严格等值**（全部祭品台持有物构成多重集与原料表恰好相等，多余即不匹配）；
  `max` **子集命中 + 取消耗总量最大者**（多余原料留台不动，倍数只造一份）；摆放顺序与台位无关
- 歧义规则：同 pattern + mode + 归一化原料集合相同的两条配方，后者拒载；签名互为真包含时输出 WARN（不拒载）
- 配方目录展示归 JEI，**仪式 GUI 不罗列可用配方**
- **配方页签**：JEI 一仪式一页签；要独立页签需在 `GensokyouJeiPlugin.DEDICATED_TABS` 加一行
  （不加也无需任何代码即可查看——配方自动落"仪式配方（其他）"兜底页签）
- 语言键：界面 `gui.gensokyou.ritual.*`、提示 `msg.gensokyou.*`、配方名 `jei.gensokyou.recipe.<name>`、
  效果名 `jei.<effect命名空间>.effect.<path>`——全部须双语落 lang，跑 `python tools/lang_audit.py` 验零缺失
- **供品/产物一律走配方文件**。pattern 内的 `requirements` 字段 loader 仍兼容，
  但现行仪式均未使用，**新仪式默认不要写**（SKILL §1）

## 3. 行为与周边（代码侧，按需）

- 仪式行为由程序侧按 pattern id 注册（`RitualBehaviors`）；设计者只写需求说明，
  不读代码。空手右键核心是否直连交互、启动前置、成型瞬间扩展点等期望行为，
  在需求说明中写清输入/输出即可
- 语言键、配方页签等 JEI 相关事项见 §2；合成配方、战利品表、创造标签页等按需补齐

## 4. 游戏内采集工具

- `/gs_ritual_capture <名称> <半径> <高度>`（权限≥2）：以玩家脚下为中心捕获，
  骨架输出即 **v5 增量格式**（level 号现硬编码 1，落库前改为 0 基编号），可直接作起点
- **仪式构造仗**（创造标签页）：左键设角点 A → 右键方块设角点 B 即捕获；
  潜行右键清除选择；区域内须恰有一个仪式核心作锚点
- 两者共用管线：输出骨架至 `logs/latest.log`，对称冲突与锚点异常随聊天回显；
  骨架人工核对后放入 rituals 目录入库

## 5. 已知限制

- dedicated server 远程客户端的 JEI 图鉴为空（数据在服务端 JVM，未做网络同步），
  远程客户端空手右键核心也不会弹出界面（本地无 pattern 数据）

## 6. 多层级仪式

- `levels` 为**逐级增量**（v5），阶级从 0 编起、连续（0 阶 = 最小形态）：每级 `adds` 只写该级新增，加载期四重展开后逐级
  累积为全量切片；把低阶格位复制进高阶增量会被增量相交校验拒载
- **渐进搭建语义**：匹配自高向低逐级尝试，玩家逐级补建即可升级；
  已满足格位在补建时自动跳过（构建器不重复扣料）
- **off-axis 轨道双向必列**：off-axis 展开只镜像符号不换轴，(a,b) 与 (b,a) 是两个格位，
  规范四分之一里两个方向都要显式列出；轴上格子（x=0 或 z=0）加载时自动四方成套（含坐标互换）
- 临时装饰方块可先用原版替代（palette 是数据，后续替换专属方块零代码）

## 7. 品阶下限标签（`*_N_plus`）与构建器联动

- 目录：`data/gensokyou/tags/block/`，`ritual_stones_{1..5}_plus`、`ritual_pedestals_{2..5}_plus`
- 语义：`N_plus` = N 阶至 5 阶全体成员，表达"该格位**必须 N 阶以上**"的下限约束；
  第 N 阶（N≥1）**新增**环的仪式石/祭品台 palette 引用对应 `N_plus` 标签，0 阶环用全阶标签
  （继承自低阶层的格位不受限）
- 祭品台 `_N_plus` 自 2 起：1 阶祭品台暂用全阶 `#gensokyou:ritual_pedestals`
  （1 条 WARN，已知例外，待程序侧补 `ritual_pedestals_1_plus`）；除此之外新 pattern 不得有 WARN
- 无后缀 `ritual_stones` / `ritual_pedestals` = 全品阶 0..5
- **注意**：标签字典键必须带命名空间（`gensokyou:ritual_stones_1_plus`），
  缺命名空间时标签成员加载为空、校验被静默跳过
- 与构建器联动：品阶由构建器 UI 选择，`RitualBuilderPlacement.resolveBlock` 按
  "标签成员中 tierOf==所选品阶"实例化——品阶选 2 时 0..2 阶环全变 2 级石
  （2 ≥ 这些环的全部下限；更高阶环选 2 解析不出，需逐阶选更高品阶）；
  跨阶渐进搭建时逐阶选品阶即可，已满足格位自动跳过不重复扣料（现有逻辑天然支持）

## 8. 落盘前必跑

- `python tools/validate_ritual_pattern.py`：全部 pattern 离线校验
  （锚点 / 展开冲突 / 纯增量 / 品阶下限 / 跨 pattern 劫持）
- `--test-out run-test/world/datapacks/gs_ritual_test`：重建隔离测试世界的测试数据包，每次改 pattern 后必须重建（勿写共享 `run/world`）
- 测试包**不自触发**（不写 `load.json`/`tick.json`）；实机端到端测试由用户运行
  `powershell -ExecutionPolicy Bypass -File tools\_run_ritual_test.ps1`（隔离服务器经 RCON 显式触发
  `function gs_test:run_all`；agent 不启动服务器；现有 harness 只认 generator_circle，新仪式需程序侧扩展 harness）

## 9. 程序侧待办（0 基阶级对齐）

- `RitualRecipeLoader`：放开 `minTier` ≥1 的钳制，允许 0（0 阶仪式的配方依赖此项）
- JEI 配方卡：阶级显示支持 0（`"I".repeat(Math.max(1, minTier))` 会把 0 画成 I）
- 新增方块标签 `ritual_pedestals_1_plus`（1 阶祭品台下限）
- 采集工具 `/gs_ritual_capture` 骨架的 level 号改输出 0（或与上条二选一处理）
- 存量占位仪式替换/删除时一并处理 1 基编号；若重编号，其配方 `minTier` 需同步降 1
  （门槛语义 minTier ≤ 匹配阶级不变）
