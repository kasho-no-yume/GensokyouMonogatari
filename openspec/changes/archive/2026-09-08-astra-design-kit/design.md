# Design: Astra 设计工具链（astra-design-kit）

## Context

接入外部模型 Astra（GPT6）作为纯美术设计代理，经 opencode 运行。现状：

- 仪式设计已有闭环：`gen_generator_circle.py` 模板（canon/put/slab/pillar + 冲突自检）→
  `validate_ritual_pattern.py`（校验 + `--render` 预览 + `--test-out` 测试包）。
- 建筑设计为空白：仓库无任何 structure 表达物（无 .nbt、无 jigsaw 数据）。
- 贴图工具 `gen_tex.py`（ASCII 像素图 + 调色板）已稳定。
- 知识目前散落在 `ritual-design` skill 中，混有程序侧内容（Java、行为注册），Astra 不可见。

约束：Astra 是纯美术——不写 Java、不做非人形几何建模；token 消耗最小化优先；
正确性靠离线校验，美观靠用户游戏内实地查看（**不要预览图**）。

## Goals / Non-Goals

**Goals:**
- Astra 首轮上下文 ≤ ~7k token（agent 定义 + 技能包 + 生成目录）
- 交付物目录契约固定，程序侧可机械接入，无需翻译
- 建筑设计可产出原版结构模板 .nbt 并游戏内预览
- 校验反馈单行可判，减少试错轮次

**Non-Goals:**
- 不做非人形实体/BE 精细建模工作流（用户在 Blockbench 手工完成）
- 不引入 JsonCubeModelLoader 等 Java 渲染运行时（已否决）
- 不做 GeckLib/骨骼动画系统
- 不改任何 Java 与 rituals 数据本身

## Decisions

1. **Astra 形态 = opencode 自定义 agent（gpt6 provider）**
   - 角色声明 + 交付契约放 agent 定义（~500 tok）；细则放 skill 按需加载。
   - 备选"外部对话复制粘贴"已否决：opencode 运行使 Astra 可自跑工具，省去人工中转。

2. **知识包 = 生成目录 + 精简技能，Astra 永不读源码**
   - `tools/gen_catalog.py` 扫 `assets/.../blockstates`、`data/.../tags/block`、`data/.../rituals` 三个数据事实源
     （不扫 Java），产出 `BLOCKS.md` + `PATTERNS.md`。漂移防护：目录只由该脚本再生成。
   - `SKILL.md` 从 `ritual-design` skill 剥离：保留硬性不变量（四重对称/纯增量/锚点唯一/品阶下限/
     禁状态后缀）、风格指南（材料递进、东方元素）、交付契约；删除全部 Java/behavior/测试 harness 程序侧内容。
   - 备选"发明 JSON DSL"已否决：gen 脚本骨架已实测，Python 即是高密度格式，`slab()` 一行 ≈150 格坐标。

3. **建筑存储目标 = 原版结构模板 .nbt；预览 = setblock mcfunction**
   - `tools/struct_compile.py` 提供 helper（put/slab/pillar 返回坐标字典）+ `save_structure(name, cells)`：
     产出 `data/gensokyou/structure/<name>.nbt`（1.21 起目录为单数 `structure`）与测试包 `gs_ritual_test` 内的 setblock mcfunction。
   - 建筑不需要对称（不走 canon 四分之一形），全 XYZ 直接写； rituals 模板保留 canon。
   - NBT 写入依赖：优先 `nbtlib`（pip）；若不可接受则手写最小 NBT writer（structure template 结构简单）。
     **DataVersion 必须对应 1.21.1，实施时以游戏内 `/place template` 实测为准。**
   - 备选"直接接 jigsaw/structure feature 数据"已否决：那是程序侧集成阶段的活，与 Astra 设计解耦。

4. **校验器输出 AI 化 + 删除预览渲染**
   - 错误格式：`ERROR <定位> <一句话原因>`（如 `ERROR L3 (0,1,0): 与 capacitor_circle L1 冲突`），
     禁止多行堆栈。
   - 删除 `--render`/`KEY_COLORS`：预览图全线砍掉（游戏即预览），调色维护归零。
   - 工具 stdout 纪律：大输出落盘不打印（防 4.5MB 输出上限事故重演）。

5. **交付契约（design/astra/<name>/）**

   ```
   design/astra/<name>/
   ├── blueprint.md       # 逐层/逐段增量蓝图文字稿（先产出，用户确认后写脚本）
   ├── gen_<name>.py      # 高密度生成脚本（仪式：产 JSON；建筑：调 save_structure）
   ├── rituals/<name>.json        # 仪式交付物（gen 产物）
   ├── structures/<name>.nbt      # 建筑交付物（编译产物）
   └── textures/<n>.py            # 贴图数据文件（gen_tex 格式）
   ```

6. **风格基准（仪式方块贴图语言）**
   - 统一为**紫色系石族**：整体色系以紫为主，保证视觉身份统一。
   - 品阶辨识：TierPalette 主色（0灰/1绿/2蓝/3琥珀/4红/5紫）以**纹样/符文点缀**形式嵌入，
     强度随品阶 0→5 渐增；0 阶无彩中性。目标是"一眼可辨品阶、又不破坏紫色系整体"。
   - 此简报为 astra-design 技能包风格指南中贴图部分的基准；首轮贴图翻新由 opencode（omen）
     在本变更外按 gen-textures 流程先行执行，成果作为 Astra 后续贴图工作的风格基线。

## Risks / Trade-offs

- [BLOCKS.md/PATTERNS.md 与事实源漂移] → 目录仅 `gen_catalog.py` 产出；注册新方块后程序侧重跑并随手提交。
- [Astra 写的 Python 崩溃/绕过自检] → 模板内 put()/save_structure 自带冲突检查；技能规定"只走 helper，禁止手写坐标展开"。
- [.nbt DataVersion 或字段写错导致游戏拒载] → 实施任务含最小样例实测（2×2 石头模板 `/place template` 验证）后再放开批量使用。
- [Astra 越界尝试建模/写 Java] → agent 定义与 SKILL.md 双处声明边界：非人形实体、BE 精细建模、一切 Java = Blockbench/程序侧职责。
- [删除 --render 影响程序侧排障] → 接受。git 历史可回溯，仪式排障走游戏内观察。

## Migration Plan

纯增量工具与文档，无数据迁移、无运行时影响：
1. 落 tools（gen_catalog → struct_compile → 校验器改造）并各跑一次自检
2. 落知识包（agent + skill + 生成目录），核对 token 预算
3. 以"试设计一个小建筑"端到端演练一次（Astra → 校验 → /function 实测 → 程序侧收编）
4. 回滚 = git revert（无状态残留）

## Open Questions

- gpt6 provider 的具体接入配置（endpoint/key）由用户侧完成，不进本变更。
- 建筑测试坐标是否与仪式 harness 共区（A1..NEG 锚点带），实施时定。
- gen_tex 是否需要为 64x64 实体皮肤加缩放档（现支持任意正方形，预计无需改动）。
