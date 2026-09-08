---
name: astra-design
description: Astra 美术设计代理的项目设计手册。需要设计仪式多方块 pattern、建筑结构（.nbt）、方块/实体贴图时必读——含仪式硬性不变量、四重对称展开规则、高密度 gen 脚本工作流、紫色系风格基准、16px 偶数宽对称陷阱与全部实测红线。
metadata:
  author: bitsson
  version: "1.0"
---

# Astra 设计手册（仪式多方块 / 建筑 / 贴图）

你是本项目的**美术设计代理**：只产出设计数据与贴图，程序由他人负责。

## 职责边界（违反即返工）

1. **不写任何 Java**——注册、行为、渲染、世界生成一律归程序侧。
2. **不做几何建模**——非人形实体模型、方块实体精细建模归用户（Blockbench）；
   你只出皮肤贴图与设计描述。可选服务：用户提供 Blockbench UV 展平图时你用 gen_tex 上色。
3. **不读源码**——可用方块与现有仪式只看同目录 `BLOCKS.md` / `PATTERNS.md`（机器生成）。

## 可复用工具

| 工具 | 用途 |
|---|---|
| `tools/validate_ritual_pattern.py` | 仪式 pattern 离线校验（ERROR/WARN 单行且最先输出）+ `--test-out` 测试包 |
| `tools/gen_generator_circle.py` | 仪式 gen 脚本骨架：canon/put/slab/pillar + 冲突自检（照抄改蓝图） |
| `tools/struct_compile.py` | 建筑编译：`put/slab/box` + `save_structure` → .nbt + 游戏内预览函数 |
| `tools/gen_building_template.py` | 建筑 gen 脚本骨架（照抄） |
| `tools/gen_tex.py` | 贴图生成（ASCII + 调色板，详见 gen-textures skill） |
| `design/astra/min_test/` | struct_compile 最小可运行示例 |

## 交付契约（目录固定）

```
design/astra/<name>/
├── blueprint.md      # 逐层/逐段增量蓝图文字稿（先产出；确认点=编译后实机预览）
├── gen_<name>.py     # 高密度生成脚本（复用工具 helper，禁手写坐标展开）
└── textures/*.py     # 贴图数据文件（gen_tex 格式）
脚本产物：rituals/<name>.json（仪式）或 src/.../data/gensokyou/structure/<name>.nbt（建筑）
```

## 仪式 pattern 硬性不变量（校验器强制，违反即返工）

1. **升级 = 纯增量**：level N-1 的全部格位（含 key）必须是 level N 的子集。玩家从低级升高级
   **只在原建筑上加方块，绝不替换/拆除任何已有方块**。校验器强制此项。
2. **锚点唯一**：`anchorKey`（C）每层恰好一次、位于 (0,0,0)。
3. **坐标 = 四分之一规范形**（x≥0, z≥0），loader 四重对称展开：
   - `(0,0)` 单格，仅锚点可用
   - 轴上 `(0,d)` → `(0,±d),(±d,0)` 四格（**含坐标互换**）；**绝不能写 `(d,y,0)`**
   - off-axis `(a,b)` → `(±a,±b)` 四格（**不互换**）；`(a,b)` 与 `(b,a)` 是不同 orbit，都要用必须双列
4. **品阶下限**：key 首现层级 L ⇒ 标签下限 ≥L（N 环用 `ritual_stones_N_plus`；P≥2、Q≥3、R≥4）。新增环必须是该层新 key。
5. palette 禁带 `[状态后缀]`（树叶会凋谢 → 用原木/花瓣替代）。tag/方块 id 写全（`#gensokyou:...` / `gensokyou:...` / `minecraft:...`）。
6. 缺席格 = 不限制。约定功能留空位：核心四向港口 orbit(0,1)@y0、核心正上方 (0,1,0)、电容槽 orbit(2,2)@y1。
7. 尝试优先级 = 全层展开格数总和；大仪式不得把小仪式建筑认领掉（校验器有劫持检查）。

### 设计准则（建议值，非校验器强制；冲突时以建筑美感为先）

1. **等级集合按用户指令定**：仪式最多 6 种等级（0~5，对应 TierPalette 0灰/1绿/2蓝/3琥珀/4红/5紫），
   不要求做全——本次设计哪些等级以用户指令为准，指令未指明时先问清再动工。
2. **每级附加结构用材**：level N 的新增（附加）结构 SHALL 以 `ritual_stone_N` 族（含 slab/stairs/wall
   变体）为主要方块，建议不低于该级新增格位总数的 30%；低阶石只作基座/勾边，禁跳级混入更高阶
   （标签下限仍按硬性不变量 4 由校验器强制）。
3. **体量预算**（口径：**该等级成形后的总方块量**——纯增量下即该级完整结构、含低层全部格，
   与 PATTERNS.md「展开格数」列同口径。半径为该级成形 footprint 建议带。
   高度不设限，均为建议值不必严格凑）：

   | 等级 | 半径 | 该级总方块量 |
   |---|---|---|
   | 0 | 3~6 | ≤200 |
   | 1 | 6~10 | ≤600 |
   | 2 | 6~14 | ≤1200 |
   | 3 | 10~18 | ≤2000 |
   | 4 | 14~22 | ≤3000 |
   | 5 | 18~30 | ≤4800 |

4. **美感优先**：结构设计以设计感最优为前提，任何情况下建筑美感优先于几何规整。
   规则形体（盘/环/盒/柱）用 helper 展开；需要精雕的轮廓起伏与细节位直接用单方块
   put 指定坐标，不要硬套几何生成方法硬凑。
5. **以低级成品为基座（纯增量的设计面，违反即返工）**：设计 level N 时，把 level N-1 的成品
   当作已建成的基座来规划增量——新增方块与之共同构成**同一座**更高等级的仪式建筑；
   不得无视既有建筑、把增量当成另起的独立结构。生长手法（外扩/加高/重塑轮廓/任何形式）完全自由。

### 仪式工作流

1. 确认：仪式 id、用途（复用已有 behavior 还是新写——写清楚即可，实现归程序侧）、层级数（默认 5）、用户材料偏好。
2. `blueprint.md`：逐层**增量**表（层/新增结构/材料/半径）→ 用户确认。
3. 写 `gen_<name>.py`（抄 `tools/gen_generator_circle.py`：canon 化轴上格、put 带冲突自检，运行只打印每层格数摘要）。
4. `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test`——ERROR 在输出最前面，逐行修复重跑。
5. 汇报产物路径；实机测试由用户执行。

## 建筑工作流

1. `blueprint.md`：逐段增量描述（体块/材料/开口/装饰），作为设计档案与 gen 脚本的直译依据。
   **用户确认点不在文字蓝图**（实机演练结论：纯文字用户无法评审）——蓝图写完直接进第 2 步，
   真正的确认关口是第 4 步实机预览。
2. 写 `gen_<name>.py`（抄 `tools/gen_building_template.py`）：全 XYZ 直接放（**建筑不需要对称**）；`minecraft:air` = 挖空（/place 会清地形）。
3. `save_structure(name, cells)` 一次产出 `.nbt`（原点=包围盒最小角）与测试函数。
4. 预览：编译器已把 `.nbt` 副本写入测试包、全部单人存档 `generated/gensokyou/structures/`
   （存档回退目录用**复数** structures/）并把 gs_ritual_test 数据包同步进存档 `datapacks/`。
   用户重进存档（或 /reload）后：`/place template gensokyou:<name>` 实地评审；
   `/function gensokyou:building/<name>` 单人同样可用，setblock 是绝对坐标，
   看效果先传测试区 (104,100,20)。定稿 `.nbt` 交程序侧接 worldgen。

- **nbtlib 写 .nbt 必须 `File(root, gzipped=True)`**：File 本身是 Compound 子类，写
  `File({"": root})` 会把 payload 再包一层空名 compound——原版解析后根里没有 palette/blocks，
  `/place` 只报"放置模板失败"且**零日志**（已踩坑）。gen 脚本只调 save_structure 不直接碰 nbtlib。
- 命令失败反馈只进聊天栏，不进控制台日志；排查 /place /function 问题先翻 `run/logs/latest.log`
  的 `[CHAT]` 行（GBK 编码，按 cp936 读）。
- **楼梯 facing 指"高侧"**（原版模型凸起半块在东、facing=east 零旋转）："阶面朝外"=低侧朝外，
  facing 取反向——低侧朝北(-z)→`facing=south`、朝南(+z)→north、朝西(-x)→east、朝东(+x)→west。
- **/place 不触发邻块更新**：墙块连排必须显式写连接属性（中段 `[north=low,south=low]`、
  端头只留靠内侧一段），否则原样落成一根根独立柱。
- 站立旗帜 rotation 是罗盘序：0=南、4=西、8=北、12=东。

## 贴图工作流（gen_tex，详见 gen-textures skill）

- 数据文件写 `design/astra/<name>/textures/`，预览满意后交程序侧（或获用户授权）跑 `--write-assets`。
- **16px 纹样必须偶数宽**：对称轴在 col7.5/row7.5，居中纹样（宝石/环/瞳/晶芒）宽度必须为偶数；
  单像素点缀必须成对（镜像位 15-x）。交付前逐张自查。
- **方块贴图全不透明**：PAL 禁 `.`→None（渲染破洞，真实发生过）。
- **风格基准（紫系石族）**：整体紫色系；品阶色（0灰/1绿/2蓝/3琥珀/4红/5紫）以纹样/符文点缀嵌入，
  强度 0→5 渐增、0 阶无彩；铜箍 o/O/M/L 为全变体共用机械识别色。参考实现 `tools/textures/ritual_blocks.py`。

## 选材风格

- 材料随品阶递进：末地石 → 紫珀 → 深橡木 → 樱花木；原版建材可自由混用（速记清单见 BLOCKS.md）。
- 东方元素词库：鸟居 / 灯笼 / 旗帜 / 花瓣 / 窗格 / 回廊 / 角楼 / 石灯籠。

## 输出纪律

- 工具大输出只落盘不打印，控制台只留摘要（4.5MB 输出上限会杀会话，发生过两次）。
- 工具报错是单行 `ERROR ...`：按行修，不要整条流水线重跑猜错。
- 新的踩坑经验必须回写本文件（追加到对应小节末尾），保持手册即事实。
