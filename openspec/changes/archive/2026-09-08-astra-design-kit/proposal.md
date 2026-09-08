# Proposal: Astra 设计工具链（astra-design-kit）

> 需求来源：2026-09 探索会话结论——接入 GPT6 模型"Astra"作为**纯美术设计代理**，通过 opencode 运行。
> 分工铁律：Astra 只产出设计数据与贴图（文本/Python/PNG），**永不写 Java、永不建模几何**。

## Why

Astra 若直接阅读仓库源码理解工作内容，起步成本 ~20-40k token 且会误读（读 Java、读大 JSON、试错无反馈）。
需要一个窄接口：一次性知识包（~6-7k tok）+ 高密度产出格式 + 离线自检工具，让 Astra
以最小 token 完成仪式多方块设计、建筑设计、贴图/皮肤三类工作，交付物由程序侧（opencode + 用户）机械接入。

## What Changes

- **Astra 美术代理**：新增 opencode agent 定义（gpt6 provider）+ 设计技能包（不变量、风格指南、交付契约、职责边界——非人形实体/BE 精细建模明确划归用户 Blockbench，Astra 不做）
- **机器生成知识目录**：`tools/gen_catalog.py` 扫 blockstates/tags/rituals 数据，产出 `BLOCKS.md`（可用方块+标签+调色键）与 `PATTERNS.md`（现有 7 仪式摘要）；Astra 永不读源码
- **建筑设计管道**：`tools/struct_compile.py` 将 gen 脚本产出的方块坐标编译为**原版结构模板 .nbt** + setblock 测试数据包（复用 `gs_ritual_test` harness 模式，游戏内 `/function` 实地预览，**不产出预览图**）
- **仪式校验器改造**：`validate_ritual_pattern.py` 错误输出改为单行可判格式（AI 友好）；删除 `--render` 预览与 `KEY_COLORS` 调色维护
- **staging 目录约定**：`design/astra/<name>/`（blueprint.md + gen 脚本 + 交付物），固定交付物契约

## Capabilities

### New Capabilities
- `astra-artist-agent`：Astra 代理的角色边界、知识包内容、交付物契约与设计工作流
- `design-catalog`：机器生成的方块/仪式知识目录，与数据事实源保持同步、可离线再生
- `structure-template-pipeline`：建筑蓝图（高密度 gen 脚本）→ 原版 .nbt 结构模板 + 游戏内 setblock 预览的编译管道

### Modified Capabilities

（无——校验器/工具属开发工具链，不在既有 spec 需求范围内）

## Impact

- 新增 `tools/gen_catalog.py`、`tools/struct_compile.py`；修改 `tools/validate_ritual_pattern.py`
- 新增 `.opencode/agents/astra-artist.md`、`.opencode/skills/astra-design/SKILL.md`（由 `ritual-design` skill 精简改编，剥离程序侧内容）
- 新增 `design/astra/` 目录约定
- 不触碰任何 Java；`gen_tex.py` 既有流程不变（贴图仍是 Astra 主力产出工具）
