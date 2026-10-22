# _legacy —— 已被正式美术取代的 ASCII 贴图生成器

这里的文件生成的是**已被手绘 / AI 正式美术取代**的旧占位贴图。

**它们 MUST NOT 留在 `tools/textures/` 根下**：`tools/gen_tex.py` 会渲染该目录下
所有 `*.py` 数据文件，跑一次 `gen_tex.py --write-assets` 就会把正式美术
**覆盖回旧占位图**。

## 两类来源

| 文件 | 生成什么 | 被什么取代 |
|---|---|---|
| `ritual_core_gui.py`、`danmaku_assembly_bench_gui.py` | 旧占位面板（1px 描边 + 近黑底 / 灰底） | 樱符产线的和纸面板（`gui_sakura.py` + `gui_art.py`） |
| `amp_core.py`、`boss_signatures.py`、`gensokyo_materials.py`、`level_cores.py`、`memory_fragment.py`、`porcelain.py`、`ritual_builder.py`、`spirit_core.py`、`spirit_cores.py`、`weapon_cores.py`、`yen.py` | 16×16 的旧物品图标 | 64×64 的正式物品贴图 |

**实测踩过**：跑一次全量 `gen_tex.py --write-assets`，51 张 64×64 的物品贴图被
刷成了 16×16 —— 直接毁掉了整批正式美术。

## 兜底护栏

`tools/gen_tex.py --write-assets` 现在带一条护栏：**目标资源已存在且尺寸与 ASCII
渲染结果不符 → 跳过并报警**，不覆盖。这只是防呆兜底，
**真正的修法仍是把过期数据文件移进本目录**（`gen_tex.py` 只扫根目录，不递归）。

## 正式产线

- 面板：数据 `tools/textures/gui_sakura.py` → 线稿 `tools/gui_draft.py`
  → AI `tools/genpic_client.py` → 收口 `tools/gui_art.py`
  → 校验 `tools/validate_gui_textures.py`
- 规格：`openspec/changes/sakura-gui-art-system/`（capability `gui-art-system`）
