# Proposal: ritual-wand-dev-editor

## Why

仪式构造杖现状只做"A/B 框选 → JSON 骨架落盘 → 人肉合入重启"，与手搓 pattern 的真实工作流（设计→进游戏看→改→再校验）严重脱节：捕获不带语义（TAG 谓词退化为 EXACT、AIR 格丢失）、无法在世界里快速迭代某个阶级、校验全靠离线 python。定位是**纯开发者工具（仅创造模式可用，永不进生存）**，应升级为"锚定核心 → 力建/sweep 工作区 → diff 捕获 → 两级保存即时生效"的闭环编辑器，消灭手工缝合环节。

## What Changes

- **BREAKING** 构造杖旧交互（左键 A / 右键 B 两点框选捕获）被编辑工作流取代：非潜行右键任意仪式核心（成型与否皆可）= 锚定编辑位置，无其他副作用；潜行右键开菜单（复用构建杖选择器范式：选仪式 + 阶级）。
- 新增**工作区**概念：每阶级独立记忆一份识别参数 `{长, 宽, 高, y偏移}`（x/z 由四重对称恒以核心为心，仅 y 需偏移），存于物品 Data Component。只有工作区内（该阶级 AABB）的方块被视为仪式结构内容——sweep 只铲区内，捕获只读区内，区外世界"不存在"。
- 新增**力建（编辑模式搭建）**：把所选仪式所选阶级的累积切片强制建到锚点核心——不符格直接覆盖，区内非仪式格位清除为空气（AIR/IGNORE 谓词格一并清空，仅豁免锚点核心），不扣材料、不做冲突中止。与构建杖的生存语义（耗料、零容忍、缺席格自由）互不侵犯。执行前三色预览（绿=新放置 / 橙=覆盖 / 品红=清除）+ 二次确认。
- 新增**diff 捕获器**：捕获=对现有 pattern 切片的补丁而非从零重建——谓词仍满足的格原样保留；区内新增格按 §3 品阶规则反导（石/台 → `#..._N_plus` 标签，含 0 阶全量标签与 1 阶祭品台 `_2_plus` 两个特例；其余方块先复用既有 palette key 再分配 EXACT 新 key）；"已声明格被换块/消失"等 v5 不可表达操作当场报违规。
- 新增**两级保存 + 游戏内校验**：阶级保存=存草稿不校验；仪式保存=跑全套合规校验（移植 `validate_ritual_pattern.py` 的 5 条规则：增量相交 / level 重复 / 锚点三条件 / 品阶下限 WARN / 大 pattern 劫持），产出完整 v5 JSON **直接覆写** `run/world/datapacks/` 使 `/reload` 秒生效，开发环境额外回声一份到 `src/main/resources/data/gensokyou/rituals/`（源码树同步，零人肉搬运）。
- 硬性权限闸：编辑杖全部功能仅 `hasInfiniteMaterials()`（创造）可用，生存/冒险模式右键核心不回锚定、不开菜单，直接提示。
- **明确不做（Non-Goal）**：新增仪式向导（名字/阶级范围声明、缺阶空 adds 自动填充）——新仪式仍按现有 skill 工作流手搓骨架 JSON 后进本编辑循环；`/gs_ritual_capture` 命令保留原样。

## Capabilities

### New Capabilities

- `ritual-editor-anchor`: 编辑杖物品本体与状态——创造模式硬闸、右键核心锚定（任意形态、纯锚定无副作用）、按阶级记忆的工作区参数与锚点 Data Component、核心被拆/换维度的失效判定、仪式+阶级选择菜单。
- `ritual-editor-force-build`: 编辑模式强建——覆盖式放置、工作区内非仪式格 sweep 为空气（豁免仅锚点核心）、三色预览与两段确认、与构建杖生存路径的语义分界。
- `ritual-capture-diff`: 工作区 diff 捕获——谓词满足格保留、新增格标签反导（含两个已知特例）、key 复用、v5 不可表达变更的违规报告、对称/朝向校验复用现有归并逻辑。
- `ritual-editor-save-validate`: 阶级/仪式两级保存——仪式保存时全量合规校验（python 校验器 5 规则的 Java 移植）、覆写 world datapacks + dev 源码树双写、/reload 生效提示。

### Modified Capabilities

- `ritual-pattern-system`: "构造仗框选采集"要求整条替换——两点框选骨架捕获移除，构造杖升级为编辑杖（捕获职责移交 `ritual-capture-diff`）；`/gs_ritual_capture` 命令要求不变。
- `ritual-core-interface`: 成型核心非潜行右键的让位规则新增"编辑杖"分支——持编辑杖右键成型核心执行锚定而非打开核心 GUI。

## Impact

- **物品/注册**：`RitualWandItem` 重构为编辑杖（名称/贴图可沿用）；`ModDataComponents` 新增锚定+工作区组件；`RitualBuilderMenu/Screen` 或新菜单扩展编辑入口。
- **服务端逻辑**：`RitualBuilderPlacement` 增加 force 路径（或新增 `RitualEditorPlacement`）；`RitualCapture` 增加 diff 模式；新增 `RitualPatternValidator`（Java 移植）与保存产出（文件 IO，dev 路径探测）。
- **客户端**：`RitualConflictRenderer`/`RitualPreviewRenderer` 的 RenderType 扩展三色；新 C2S/S2C payload（锚定、力建确认、捕获、保存均服务端权威）。
- **不动**：`RitualPattern` v5 格式、`RitualMatcher`、构建杖生存行为、配方系统（自定义 id 不挂配方）。
- **风险**：校验规则与 python 侧双实现需保持同步（design 中定单一事实源策略）；文件覆写仅 dev 环境探测成功才双写。
