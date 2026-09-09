## Why

`astra-design` 技能包与 `ritual-design` 长期内容重叠，且已积累一批**过时且相互冲突**的规则：1 基阶级编号（与 v5 的 0 基 `match.level()` 显示矛盾）、"升级=子集"旧语义、"每级附加结构以 ritual_stone_N 族为主 ≥30%"（与现行"仪式石族 ≤30%、必须大量普通方块装饰"硬性不变量直接对立）、"材料随品阶递进 末地石→紫珀→深橡木"（已作废）。两份手册并存导致设计代理读到错误约束、产出无美感的纯仪式石结构。合并为单一事实源可根除冲突。

## What Changes

- **BREAKING** 删除 `.opencode/skills/astra-design/` 整个技能目录；其仪式相关知识已在 `ritual-design` 中，冲突/过时条款**不迁移**。
- 把 astra-design 中**仪式相关且不冲突**的内容并入 `ritual-design/SKILL.md`：id 书写规则、核心功能留空位坐标（四向 `(0,1)`@y0、正上方 `(0,1,0)`、电容槽 `(2,2)`@y1）、东方元素词库、0~5 阶体量预算表。
- 把 astra-design 的**建筑 .nbt 工作流**整体并入 `ritual-design/SKILL.md` 新增 §7（交付目录、struct_compile/gen_building_template 工具链、nbtlib gzipped / /place 不触发邻块 / 楼梯 facing 高侧 / 旗帜罗盘序 / CHAT 日志 cp936 等实测坑），新增 §8 输出纪律。
- 把 astra-design 的**贴图风格基准**并入 `gen-textures/SKILL.md`（紫系石族基准、16px 对称纹样偶数宽红线）——贴图知识归位到贴图手册。
- `astra-artist` 子代理定义改指向 `ritual-design`（仪式读 §1~§6、建筑读 §7）与 `gen-textures`。
- 知识目录 BLOCKS.md / PATTERNS.md 落位移到 `.opencode/skills/ritual-design/`；`gen_catalog.py` 默认 `--out` 随之改，去除 "Astra" 抬头措辞，并重新生成。
- 同步工具脚本与文档内的 `astra-design` 路径引用（`gen_building_template.py`、`design/astra/README.md`、`docs/new-ritual-checklist.md`）；openspec 归档历史记录不动。

## Capabilities

### New Capabilities
<!-- 无新增能力 -->

### Modified Capabilities
- `astra-artist-agent`: "设计知识包内容" 需求——知识包从独立的 `astra-design` 技能改为复用 `ritual-design`（含并入的建筑 §7），Astra 不再有自己的专用手册。
- `design-catalog`: 知识目录 BLOCKS.md / PATTERNS.md 的落位与默认输出路径改为 `.opencode/skills/ritual-design/`（`astra-design` 目录被删后，原落位失效）。

## Impact

- 删除：`.opencode/skills/astra-design/`（SKILL.md、旧 BLOCKS.md/PATTERNS.md）。
- 修改：`.opencode/skills/ritual-design/SKILL.md`（并 §7/§8）、`gen-textures/SKILL.md`、`.opencode/agent/astra-artist.md`、`tools/gen_catalog.py`（默认 out + 措辞）、`tools/gen_building_template.py`、`design/astra/README.md`、`docs/new-ritual-checklist.md`。
- 生成：`.opencode/skills/ritual-design/BLOCKS.md` / `PATTERNS.md`（新落位，随本 change 重新生成）。
- 无 Java / 无运行时数据改动；纯文档、代理定义与生成器默认值。
