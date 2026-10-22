# _legacy —— 已被樱符产线取代的 ASCII 面板生成器

这里的两个文件生成的是**旧的占位面板**（1px 描边 + 近黑底 / 灰底），
分别写 `gui/ritual_core` 与 `gui/danmaku_assembly_bench`。

**它们 MUST NOT 留在 `tools/textures/` 根下**：`tools/gen_tex.py` 会渲染该目录下
所有 `*.py` 数据文件，跑一次 `gen_tex.py` 就会把 AI 产线出的正式美术
**覆盖回旧占位图**。故移到本目录（`gen_tex.py` 只扫根目录，不递归）。

正式产线见：
- 数据：`tools/textures/gui_sakura.py`（调色板 / ASCII 零件 / 面板几何）
- 线稿：`tools/gui_draft.py`
- 收口：`tools/gui_art.py`
- 校验：`tools/validate_gui_textures.py`
- 规格：`openspec/changes/sakura-gui-art-system/`
