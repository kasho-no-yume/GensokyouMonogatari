# 交接文档：astra-design-kit（2026-09-07，09-08 更新）

新会话从这里接手。当前**卡点在任务 2.5 的实机验证**（根因已修复，待用户实机重试），
其余 16/19 任务已完成。继续实现可跑 `/opsx-apply astra-design-kit`；本文件包含卡点排查所需的全部上下文。

## 1. 卡点：单人存档 `/place template gensokyou:min_test` 失败（根因已实锤，待复验）

### 根因（2026-09-08 定案）
- 日志证据（`run/logs/latest.log`，GBK 编码按 cp936 读）：
  - 23:52:24 `[CHAT] 放置模板失败`——**/place 的失败反馈只进聊天栏不进控制台**，"控制台没报错"是正常现象。
  - 23:52:50 `[CHAT] 未知函数gensokyou:building/min_test`——两个单人存档 `datapacks/` 都是空的，gs_ritual_test 只装在服务器 `run/world/datapacks/`。
- 结构加载链（源码 StructureTemplateManager/StructureTemplate 已核对）：
  - 找不到时 `getOrCreate` **静默 new 空模板** → `placeInWorld` 因 `palettes.isEmpty()` 返回 false → 聊天栏"放置模板失败"，零日志；
  - 解析异常才 `LOGGER.error("Couldn't load structure ...")`——日志里没有 → 文件被干净读入但内容是空的。
- 字节级实锤：**nbtlib 2.0.4 `File({"": root}, gzipped=True)` 是双重包裹**——File 本身就是 Compound
  子类，`""` 键成了根的子条目。原版解析后根里没有 palette/blocks/size → 全空 → 上述失败。
  正确写法 `File(root, gzipped=True)`（root_name 默认 `''`），产物头 `0a 0000 | 03 000b DataVersion`，
  与原版 jar 模板布局一致。
- 顺带修复：gen_min_test 的 `purple_banner[facing=north]` 非法属性（站立旗帜只有 rotation 0-15，
  facing 属于 purple_wall_banner）→ `rotation=4`；存档 generated/ 目录用复数 structures/（原事实链仍成立）。

### 已落地的修复（代码已改，产物重新生成并字节级验证 4 路全 OK）
- `tools/struct_compile.py`：`File(root)` 修正（注释记录陷阱）+ 新增 `_sync_test_datapack()`——
  save_structure 时把 gs_ritual_test 整树幂等镜像进已存在存档的 `datapacks/` → `/function` 单人也可用。
- min_test 已重生成：mod 资源 + 测试包 nbt/函数 + 2 存档 generated/ 回退 + 2 存档数据包同步，全部 243B 新格式。

### 用户重试步骤（重进存档最稳：新数据包自动启用 + 结构缓存清空；/reload 亦可）
1. `/place template gensokyou:min_test`
2. `/function gensokyou:building/min_test`（setblock 是绝对坐标，先传到测试区 (104,100,20) 附近看效果）
3. 若仍失败：把聊天栏原文发来；再用结构方块 SAVE 存任意 3x3 与我们的 nbt 做字节级对比。

### 相关实现要点（tools/struct_compile.py）
- DataVersion=3955（1.21.1）；nbtlib 2.0.4（pip 已装）
- `save_structure(name, cells)` 一次产 3 路输出：
  1. `src/main/resources/data/gensokyou/structure/<name>.nbt`（单数，收编产物）
  2. `run/world/datapacks/gs_ritual_test/data/gensokyou/structure/<name>.nbt`（单数，服务器世界数据包）
  3. `run/saves/*/generated/gensokyou/structures/<name>.nbt`（**复数**，单人免数据包回退）
  4. `run/world/datapacks/gs_ritual_test/data/gensokyou/function/building/<name>.mcfunction`（预览函数）
- `_save_copies()` 只写已存在的存档目录

## 2. 本次会话已交付（全部未提交，git 可见）

| 类别 | 内容 |
|---|---|
| 贴图翻新 | `tools/textures/ritual_blocks.py` 重写为紫系石族 v2，37 张 PNG 已 `--write-assets`；镜像对称+全不透明程序化验证通过；修掉基座侧面 34 个透明像素旧 bug |
| 知识目录 | `tools/gen_catalog.py` → `.opencode/skills/astra-design/{BLOCKS,PATTERNS}.md`（贴图均值主色、标签下限、7 仪式摘要） |
| 建筑管道 | `tools/struct_compile.py`（put/slab/box/save_structure）、`tools/gen_building_template.py`（模板骨架）、`design/astra/min_test/`（最小样例） |
| 校验器 | `tools/validate_ritual_pattern.py`：删 `--render`/`KEY_COLORS`，ERROR/WARN 单行前置；顺手修了数组格式 block 不识别的既有 bug；7 pattern 零回归 |
| Astra 知识包 | `.opencode/agent/astra-artist.md`（gpt6 子代理，model 占位 `gpt6/gpt-6-astra` 待用户配 provider）+ `.opencode/skills/astra-design/SKILL.md`；Astra 首轮加载 ~4.5k tok（预算 7k） |
| 契约 | `design/astra/README.md`（交付目录/角色分工/收编流程） |

## 3. 本次会话沉淀的陷阱（已写进 SKILL.md/注释，做贴图/结构时别再踩）

1. **16px 纹样必须偶数宽**：对称轴在 col7.5，居中纹样（环/瞳/晶芒）宽度必须偶数，单像素点缀必须成对（镜像位 15-x）。
2. **方块贴图全不透明**：gen_tex 的 `.`=透明，方块贴图用会渲染破洞（基座侧面真实发生过）。
3. **structures 命名分裂**：数据包内 `structure/`（1.21+单数）vs 存档 generated 回退 `structures/`（复数）。
4. **运行中的游戏不会重扫 mod 内建数据**；世界数据包会被 `/reload` 重扫；存档 generated 回退按需读取。
5. 1.21.1: DataVersion 3955，数据包 pack_format 48，函数目录 `function/`（单数）。
6. `validate_ritual_pattern.py` 必须同时支持 dict 与数组两种 block 格式（loader 两种都认）。
7. **nbtlib File 是 Compound 子类**：`File({"": root})` 会把 payload 再包一层空名 compound
   （原版读出空结构，`/place` 报"放置模板失败"且零日志）。必须 `File(root, gzipped=True)`。
8. 命令失败反馈只在聊天栏不进控制台；排查 /place /function 先看 latest.log 的 `[CHAT]` 行（cp936 读）。
9. 站立旗帜只有 `rotation`(0-15)，`facing` 属于 `*_wall_banner`；用错属性仅 WARN 且静默丢弃。

## 4. 任务状态（09-08 全部完成，待归档）

- [x] **2.5** min_test 实机双路径验证——nbtlib 双重包裹根因修复后通过
- [x] **5.2** 端到端演练——haiden 紫瓦拜殿（863 格）蓝图→gen→编译→实机双路径全通过；
      子代理模型改为继承主会话（用户不需要配 gpt6 provider，开 gpt6 会话跑 skill 或主会话派活皆可）
- [x] **5.3** 演练复盘——4 条新坑已回写 astra-design SKILL.md：文字蓝图不作确认关口（确认点=实机预览）、
      楼梯 facing 指高侧需反向、/place 不触发邻块更新（墙块要显式连接）、旗帜 rotation 罗盘序 0南4西8北12东
- 下一步：`/opsx-archive astra-design-kit`

## 5. 其他背景

- 模板/测试区锚点：(104,100,20)，仪式 harness 锚点 A1=(4,100,4)/A3=(44,100,4)/A5=(64,100,4)/NEG=(84,100,4)，forceload -16,-16 112 32
- ritual-design SKILL.md 已同步去 `--render` 引用；`tools/_preview/` 预览图路线已整体废除（游戏即预览）
- 风格基准（贴图 v2）：紫色系石族 + 品阶纹样渐变（0 无彩→5 最浓），TierPalette 0灰/1绿/2蓝/3琥珀/4红/5紫
- git：一切未提交；`.cocoindex_code/*.db` 的改动与本项目无关，提交时排除
