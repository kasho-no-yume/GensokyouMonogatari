# Design: ritual-wand-dev-editor

## Context

- 两根杖现状：`RitualBuilderItem`（构建杖，生存向：耗料、冲突零容忍、两段式预览、阶级=切片号）；`RitualWandItem`（构造杖，A/B 两点框选 → `RitualCapture.capture` 骨架 JSON 落盘 `ritual_captures/`，人肉入库）。本变更把构造杖重构为**编辑杖**：纯开发工具，创造模式硬闸，永不进生存。
- pattern schema v5（loader 与 `validate_ritual_pattern.py` 同规则）：levels 逐级增量 `adds`，规范四分之一条目、加载期四重展开累积；增量与低级累积切片相交即拒载；锚点全文件恰一次、原点、仅最低阶。
- 世界里的 pattern 在 mod jar 内（只读），但**世界数据包同名文件可压过 jar 内建资源**，`/reload` 即重扫——这给了"保存秒生效"的通道。
- 现成可复用件：`RitualCapture` 的对称类归并 + 朝向反推校验；`classify` 三分类纯函数；`RitualConflictRenderer`/`RitualPreviewRenderer` 的 RenderType 套路；`RitualBuilderMenu` 零槽握手 + C2S payload 写组件的菜单范式；python 校验器 5 条规则文档（skill §1/§4）。

## Goals / Non-Goals

**Goals:**
- 闭环：锚定核心 → 选仪式+阶级 → 力建+sweep 出干净工作区 → 手改方块 → diff 捕获存阶级 →（迭代）→ 仪式保存跑校验 → 覆写文件 `/reload` 生效。全程零人肉 JSON 搬运。
- 工作区（每阶级一份 `{长,宽,高,y偏移}`）是捕获与 sweep 的唯一视野边界；x/z 由四重对称恒以核心为心，仅 y 允许偏移。
- diff 捕获对 v5 忠实：永不产出"替换/删除低级格"这类不可表达补丁，违规当场报告。

**Non-Goals:**
- 新增仪式向导（名字/阶级范围声明、缺阶空 adds 自动填充）——本期不做；新仪式先手搓骨架进本循环。
- 不碰 v5 格式、`RitualMatcher`、构建杖生存路径、配方系统。
- 不做运行时动态 pattern 注册/网络同步（保存走文件 + `/reload`，天然双端一致）。

## Decisions

### D1. 交互状态机与存储：一切皆杖上 Data Component

```
IDLE ──右键核心(任意形态)──▶ ANCHORED ──潜行右键──▶ 编辑菜单
   ▲      （仅创造；非创造仅提示不锚定）              │ 选仪式+阶级 → 写 selection
   │ 核心被拆/异维度/被非核心占据 → 操作时惰性校验失效     ├─ 力建+sweep（两段式）
   └─────────────────────────────────────────────  ├─ 捕获→阶级保存
                                                    └─ 仪式保存（校验+覆写）
```

- 组件 `RITUAL_EDITOR_STATE` = `EditorState(anchor: Optional<BlockPos>, dimension, selection: Optional<BuilderSelection 复用>, workspaces: Map<level, Workspace>)`。选放杖上而非服务端 attachment：跟随物品自然丢失/复制，语义"这根杖是当前编辑会话"，实现最省。多杖同锚点、双人同锚点均可接受（dev 工具，最后写入胜）。
- 锚定无副作用：不改核心、不匹配、不开 GUI。`RitualCoreBlock.useItemOn` 仿现有 `RitualBuilderItem` 分支放行 `RitualWandItem`（成型核心也放行，锚定优先于核心 GUI）。
- 服务端在每个 C2S 动作（锚定应用、力建确认、捕获、保存）入口统一 `player.hasInfiniteMaterials()` 硬闸，UI 隐藏不算防御。

### D2. 力建 + sweep：新增 `RitualEditorPlacement`，不改构建杖

- 力建目标 = `sliceFor(pattern, level)` 全量切片：EXACT/TAG 格 `setBlockAndUpdate` 覆盖（TAG 按该阶级品阶实例化，沿用 `resolveState`）；AIR/IGNORE 格放置为空气；锚点格跳过（核心本体）。不扣料、不预检中止。
- sweep 域 = 工作区 AABB（`anchor + (±dx/2, yOff..yOff+h-1, ±dz/2)`），集合 = AABB − 本阶级切片格位 − 锚点格 → 空气。**切片声明但落在工作区外的格位照样力建**（工作区只约束"铲"和"看"，不约束"建"）。
- 默认工作区尺寸 = 本阶级切片 AABB（y 偏移 = minY），作者可调；调整即时持久化到组件。
- 两段式确认：首击（右键锚点核心）服务端计算三色清单 S2C 下发——绿=原格为空气的新放置、橙=原格被他块覆盖、品红=sweep 清除——并缓存于组件；第二击全等确认才执行（仿构建杖 `RitualPreviewState.matches` 的"全等才放行"防呆）。三色渲染在 `RitualGhostRenderTypes` 增加线框/幽灵条目，复用 `RenderLevelStageEvent` 套路。

### D3. diff 捕获 = 相对"低级地基"重导出本阶 adds

核心模型：**捕获第 N 阶时，`cumulative(N-1)` 是不可动地基**（N 为最低阶时地基=仅锚点格）。新 adds 完全重导出，允许作者随意增删**本阶自己**的格子，低级足迹零风险。工作区内逐规范对称类判定：

```
世界(工作区) ∩ 对称归并（复用 RitualCapture）
  ├ 格位 ∈ cumulative(N-1)：谓词满足 → 跳过（不进 adds，原样保留）
  │                        不满足（换块/被挖/AIR位被占）→ 违规报告（低级格被改动）
  ├ 格位 ∉ cumulative(N-1) 且世界非空气 → 写入本阶 adds：
  │     ① 命中既有 palette key 谓词（EXACT 同块 / 石·台属其标签）→ 复用该 key
  │     ② 石/台且无既有 key → 反导标签 #ritual_stones_N_plus / #ritual_pedestals_N_plus
  │        特例：N=0 石用 #gensokyou:ritual_stones 全量标签；台无 _1_plus，1 阶新增台用 _2_plus
  │     ③ 其余 → 新 EXACT key（沿用 A..Z 除 C 分配）
  └ 旧本阶 adds 格在世界中消失 → 自然落选（= 作者删掉了它，合法，无需报告）
```

- 朝向反推与对称类冲突判定直接扩展现有 `RitualCapture.capture`（它已是"区域→骨架+violations"纯函数），新增 diff 入参（地基切片 + 既有 palette），产出 `CapturePatch(addsEntries, violations, keptCells)`。`/gs_ritual_capture` 走旧全量骨架路径不变。
- 阶级保存：捕获产物替换内存中本阶 adds 草稿（存世界级 saved data `RitualDraftStorage`，按 patternId+level 键），不动 jar 文件、不跑校验——允许仪式处于"施工中"残缺态。
- 序列化注意：adds 输出**规范四分之一位置式数组**（off-axis 绝对值象限、轴上归并北位 `(0,d)`，禁止 `(d,0)` 条目），朝向常量沿用现有字符串名格式，四重展开交 loader 复验。

### D4. 校验：Java 移植 + 与 python 校验器对表测试

- `RitualPatternValidator`（纯函数，输入完整 pattern 结构+全体 pattern 列表）移植 python 5 规则：①增量与低级累积相交；②level 号重复；③锚点三条件；④品阶下限（key 首现层 L → 标签下限 ≥ L，WARN；含台/石特例）；⑤跨 pattern 劫持（A 任一层为 B 某层的子集 → 报 B 被劫持）。
- 双实现漂移对策：JUnit 夹具对 `src/main/resources/data/gensokyou/rituals/*.json` 全体 pattern 跑 Java 校验器，断言 ERROR/WARN 集合与 `validate_ritual_pattern.py` 当前输出一致（python 输出解析为 fixture）。规则变更以 python 侧为设计权威、Java 侧同步改，测试兜底。
- 校验时机：仅"仪式保存"。失败 → 逐条聊天栏回显 + 不落盘；通过 → 覆写。loader 自身拒载逻辑零改动（游戏内校验是提前拦截，不是替代）。

### D5. 保存产出：world datapack 覆写 + dev 源码树回声

- 目标一（必写）：`<level.dat 所在世界>/datapacks/gs_dev/data/gensokyou/rituals/<path>.json`（附 `pack.mcmeta`，描述 "ritual editor live pack"）。同名 RL 压过 jar 内建资源；完成后提示 `/reload`。同 id 二次保存直接覆写自己上次的产物（编辑的始终是 gs_dev 副本）。
- 目标二（机会性）：GAMEDIR 向上探测 `src/main/resources/data/gensokyou/rituals`（dev 运行环境特征），存在则同步覆写源码树，实现"游戏内调好 = 仓库已更新"；不存在则跳过并在回执注明。
- 仪式保存 = 从（内存 pattern + 草稿覆盖层）整体重序列化 v5 JSON；阶级保存不写文件。
- 回滚：删除世界 `datapacks/gs_dev` 即回到 jar 内建版本；源码树回声有 git 兜底。

## Risks / Trade-offs

- [三色预览与力建间世界被改（dev 单机概率极低）] → 第二击全等匹配失败即重算重显，绝不用陈旧清单执行。
- [sweep 是毁灭性操作，工作区误配大 = 铲平一片世界] → 仅创造可用 + 预览品红计数与确认流 + 工作区尺寸上限（沿用 `WAND_MAX_DIMENSION` 配置，默认收紧到 32）。
- [Python/Java 校验器规则漂移] → D4 对表测试；新仪式入库前 python 仍是最终闸（skill §5 流程不变）。
- [同 id datapack 覆盖后，作者又手改 jar 源文件造成两份真相] → gs_dev 回执打印"当前生效来源"；回声写入源码树使两者通常同步。
- [diff 捕获要求规范四分之一序列化无 bug，否则 loader 拒载白费一次保存] → 保存前服务端用 `expandInto` 重展开自检（内存级，零成本），不过即报内部错误不落盘。
- [重序列化丢失 JSON 注释/字段顺序等格式细节] → pattern JSON 无注释惯例；字段顺序固定按 skill 模板，diff 输出可读性够审。

## Open Questions

（无——GUI 细节按构建杖菜单范式自由发挥，不构成阻塞。）
