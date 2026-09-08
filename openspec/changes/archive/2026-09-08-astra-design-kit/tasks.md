## 1. 知识目录（design-catalog）

- [x] 1.1 写 `tools/gen_catalog.py`：扫 `assets/gensokyou/blockstates/`、`data/gensokyou/tags/block/`、`data/gensokyou/rituals/`（不解析 Java），产出 `BLOCKS.md`（方块 id/品阶/变体/标签/调色键速查/原版常用建材）与 `PATTERNS.md`（每仪式：id/层数/每层格数/footprint/palette 键/特殊留空位）
- [x] 1.2 运行并核对：BLOCKS.md 能查到 `ritual_stone_3` 完整条目，PATTERNS.md 七仪式均为摘要级；输出落盘不刷屏
- [x] 1.3 确定两个生成物落位（`.opencode/skills/astra-design/` 生成区或 `design/` 下），写入 `--out` 参数并在 .gitignore 处理预演目录

## 2. 建筑编译管道（structure-template-pipeline）

- [x] 2.1 定 NBT 写入方案：优先 `pip install nbtlib`；确认 1.21.1 结构模板 DataVersion（游戏内实测为准）
- [x] 2.2 写 `tools/struct_compile.py`：put/slab/pillar helper（坐标字典 + 冲突自检，单行报错）+ `save_structure(name, cells)`
- [x] 2.3 实现 .nbt 输出：palette 去重 + 索引编码，写入 `src/main/resources/data/gensokyou/structure/<name>.nbt`
- [x] 2.4 实现测试包输出：向 `run/world/datapacks/gs_ritual_test` 生成 `data/gensokyou/function/building/<name>.mcfunction`（setblock 全量 + forceload + 锚点传送），加载 tag 接线
- [x] 2.5 最小样例实测：2×2 石头 gen 脚本 → 编译 → `/place template` 与 `/function gs:building/xxx` 双路径验证（**由用户实机跑**；09-08 修复 nbtlib 双重包裹根因+数据包同步进存档后实机通过）
- [x] 2.6 写建筑 gen 脚本模板骨架 `tools/gen_building_template.py`（注释含用法，Astra 照抄）

## 3. 校验器改造

- [x] 3.1 `validate_ritual_pattern.py` 错误输出改单行格式 `ERROR <定位> <原因>`，消除多行堆栈；stdout 保持摘要级
- [x] 3.2 移除 `--render` 与 `KEY_COLORS`，同步更新 `tools/_run_ritual_test.ps1` 等调用方说明
- [x] 3.3 对现有 7 仪式全量重跑校验确认零回归（不产出任何 PNG）

## 4. Astra 知识包（astra-artist-agent）

- [x] 4.1 写 `.opencode/agents/astra-artist.md`：纯美术角色声明、gpt6 provider 占位（用户侧配置）、三条边界（不写 Java / 不做非人形几何建模 / 不读源码）、交付契约指针、工具调用清单（gen_catalog 产物 / struct_compile / validate / gen_tex）
- [x] 4.2 写 `.opencode/skills/astra-design/SKILL.md`：五项硬性不变量 + 对称展开规则（自 ritual-design skill 剥离）+ 风格指南（材料递进/东方元素/贴图紫色系基准——见 design.md 决策 6）+ 交付契约（design/astra/<name>/ 结构、先蓝图后脚本）+ Blockbench 分工声明（非人形实体/BE 精细建模归用户；可选服务：Blockbench UV 展平图上色）
- [x] 4.3 生成建筑 gen 脚本与仪式 gen 脚本的双模板用法示例（各 ~30 行）纳入 SKILL.md
- [x] 4.4 核算知识包 token 预算（agent 定义 + SKILL.md + BLOCKS.md + PATTERNS.md ≈ 7k），超预算则精简

## 5. 交付目录约定与端到端演练

- [x] 5.1 建 `design/astra/` 目录与 README（契约说明 + 空模板目录结构），.gitignore 处理中间产物（`tools/textures/_preview/` 已在 .gitignore；design/astra 产物全部入库）
- [x] 5.2 端到端演练：用 Astra 试设计一个小建筑 → 蓝图确认 → gen 脚本 → 编译 → `/function` 实测 → 程序侧收编归位，全流程走通（09-08 演练 haiden 紫瓦拜殿 863 格双路径实机通过；子代理模型改为继承主会话，无需配 gpt6 provider）
- [x] 5.3 演练复盘：把新踩的坑回写 SKILL.md（陷阱清单），确认知识包无需扩容（09-08 回写 4 条：文字蓝图不作确认关口/楼梯 facing 高侧反向/墙块显式连接/旗帜 rotation 罗盘序；Astra 零源码阅读下 9/9 引用方块真实存在，知识包判定够用）
