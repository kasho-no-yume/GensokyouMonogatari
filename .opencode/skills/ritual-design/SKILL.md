---
name: ritual-design
description: 仪式多方块结构（data/gensokyou/rituals/*.json）设计手册。需要新增、修改或重设计任何仪式 pattern、调整仪式层级/外形/材料时必读——含纯文字无效果图设计流程、坐标展开规则、"升级=纯增量不加不改"硬性不变量、离线校验/预览/测试数据包工具链与全部实测陷阱。
metadata:
  author: bitsson
  version: "1.0"
---

# 仪式多方块结构设计（rituals/*.json）

权威参考：展开/拒载逻辑见 `src/main/java/com/bitsson/gensokyou/ritual/RitualPatternLoader.java`，
行为注册见 `ritual/RitualBehaviors.java`，行为实现见 `ritual/behavior/*.java`。

## 可复用工具清单

| 工具 | 用途 |
|---|---|
| `tools/validate_ritual_pattern.py` | 离线校验全部 pattern（展开冲突/累积断链/品阶下限/跨 pattern 劫持）；`--render DIR` 出各层级俯视 PNG；`--test-out DIR` 重新生成服务器测试数据包 |
| `tools/gen_generator_circle.py` | **生成器模板**：canon/put/slab/pillar 骨架 + 冲突自检，新仪式照抄改蓝图即可，禁止手写大 JSON |
| `tools/_run_ritual_test.ps1` | 实机端到端测试启动器（**由用户运行**，agent 不启动服务器） |
| `run/world/datapacks/gs_ritual_test` | `--test-out` 生成的测试包，每次改 pattern 后必须重建 |
| `tools/_preview/ritual_<name>/` | 各层级俯视预览图（新 palette exact 键需在 KEY_COLORS 补色，tag 键自动用品阶色） |
| `.opencode/skills/neoforge-1211-dev` | 写任何 NeoForge 代码（新 behavior 等）前必读 |

## Pattern 格式

```json
{ "id": "gensokyou:xxx_circle", "anchorKey": "C", "toggleable": true,
  "palette": { "C": "gensokyou:ritual_core", "1": "#gensokyou:ritual_stones_1_plus",
               "E": "gensokyou:ritual_stone_wall_2", ... },
  "levels": [ { "level": 1, "blocks": [ {"key":"C","x":0,"y":0,"z":0}, ... ] } ] }
```

- 坐标为**四分之一规范形**（x≥0, z≥0，y 为相对锚点高度），loader 四重对称展开：
  - `(0,0)` 单格，仅锚点可用
  - 轴上 `(0,d)` → 展开为 `(0,±d),(±d,0)` 四格（**含坐标互换**）；**绝不能写 `(d,y,0)`**，同一 orbit 写两种会重复冲突拒载
  - off-axis `(a,b)` → `(±a,±b)` 四格（**不互换**）；`(a,b)` 与 `(b,a)` 是不同 orbit，都需要时**必须双列**
- palette 值：`#gensokyou:标签` 或纯方块 id。**禁止带 `[状态后缀]`**（树叶会凋谢 → 用原木/花瓣替代）。
- 可用自定义方块：`gensokyou:ritual_core`（锚点）、`ritual_stone_0..5`、`ritual_pedestal_*`、`ritual_stone_wall_2/3`（wall_4 存在但基本不用）。
- 标签：`ritual_stones_{1..5}_plus`（stone_N..5）、`ritual_pedestals_{2..5}_plus`、基础标签 `ritual_stones`/`ritual_pedestals`（0-5 全含）。tag 文件读时用 utf-8-sig（有 BOM），写 JSON 用纯 utf-8。

## 硬性设计不变量（违反即返工）

1. **升级 = 纯增量**：level N-1 的全部格位（含 key）必须是 level N 的子集。玩家从低级升高级**只在原建筑上加方块，绝不替换/拆除任何已有方块**。校验器强制此项。
2. **锚点唯一**：`anchorKey`（C）在每层恰好出现一次、位于 (0,0,0)。
3. **缺席格 = 不限制**（任意方块均可）。用留空做功能槽位：核心四向港口 orbit(0,1)@y0、**核心正上方 (0,1,0)**（测试把电容核心叠在这里）、电容槽 orbit(2,2)@y1（电容四石位）。
4. **品阶下限**：key 首现层级 L ⇒ 标签下限 ≥ L（"N"环用 `ritual_stones_N_plus`；P≥2、Q≥3、R≥4）。新增环必须是该层的"新"key。
5. **优先级/劫持**：尝试优先级 = 全层级展开格数总和，大者先试。大仪式不能把小仪式的建筑认领掉（校验器有劫持检查）。
6. **先给文字蓝图再动工**：口头概念≠成品。先产出"逐层级蓝图文字稿"（每层新增什么结构/材料/半径高度范围）让用户确认，再写生成脚本——避免"缩水/需求不对"整轮返工。

## 纯文字设计工作流（无效果图）

1. 确认：仪式 id、用途与行为类型（复用已有 behavior 还是新写）、层级数（默认 5）、用户材料偏好。
2. 设计逐层剪影：逐层**增量**列表（每层只写"新增"什么：新石环半径、柱/塔/门/装饰、材料），装饰材料随品阶递进（末地石→紫珀→深橡木→樱花木等原版块可自由混用）。
3. 蓝图文字稿给用户确认（层级表：层 / 新增结构 / 材料 / 半径）。
4. 写 `tools/gen_<ritual>_circle.py`（抄 gen_generator_circle.py 骨架）：canon(轴上格一律化为 (0,d))、put(lv,key,x,**y**,z 带冲突自检)、slab(环盘)、pillar(柱)。运行只打印每层格数摘要。
5. `python tools/validate_ritual_pattern.py --render tools/_preview/ritual_<name> --test-out run/world/datapacks/gs_ritual_test`——有 ERROR 就改蓝图重跑。
6. 新 palette exact 键在 `validate_ritual_pattern.py` 的 `KEY_COLORS` 补色后重跑 `--render`。
7. 预览图路径汇报给用户；实机验证交用户跑 `powershell -ExecutionPolicy Bypass -File tools\_run_ritual_test.ps1`。

## 测试数据包约定（发电机 harness）

- `generator_test_functions` 只认 id 以 `generator_circle` 结尾的 pattern；给其他仪式做同款测试需扩展该函数（锚点 A1=(4,100,4)、A3=(44,100,4)、A5=(64,100,4)、NEG=(84,100,4)，forceload -16,-16,112,32）。
- 电容堆叠：cap1/cap2=(4/64,101,4) 叠在发电机核心正上方，capacitor_circle L1 = C + S@(2,0,2)（展开为 ±(2,2) 四石）→ pattern **不得**要求 (0,1,0) 与 orbit(2,2)@y1。
- `MIXED_TIERS={'1':1..'5':5,'P':2,'Q':3,'R':4}`：新 tag key 必须登记，否则测试里该格被跳过。
- 期望聊天栏输出：T1_OK→T2_OK→T3_OK→T5_OK→NEG_OK→ALL_DONE + SP_OK:cap1/cap2；`scoreboard objectives add gs` 重复加载报错无害。

## 实测陷阱

- **4.5MB 工具输出上限会杀会话**（已发生两次）：大 JSON/长 mcfunction 只落盘不打印；控制台只出摘要；不 cat 整个 JSON。
- `put()` 参数顺序 (lv,key,x,y,z)——y 与 z 写反是真实发生过的 bug，全靠冲突自检拦截，所以新格位永远走 put()，别绕过。
- 升级测试链：setup_t2 用 only_additions=True（只 setblock 新增格，验证纯增量升级）；setup_t3/t5/NEG 用 False（异锚点全量搭建）。
- 新增行为代码前读 neoforge-1211-dev skill；行为注册在 `RitualBehaviors.java`（REGISTRY：pattern id → behavior）。
