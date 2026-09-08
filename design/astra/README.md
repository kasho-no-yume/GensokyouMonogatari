# design/astra — Astra 美术设计交付区

Astra（gpt6，opencode 子代理 `.opencode/agent/astra-artist.md`）的全部产出按任务落在
`design/astra/<name>/`，交付契约见 `.opencode/skills/astra-design/SKILL.md`。

## 目录结构

```
design/astra/<name>/
├── blueprint.md      # 蓝图文字稿（先产出，用户确认后才写脚本）
├── gen_<name>.py     # 高密度生成脚本
├── rituals/<name>.json     # 仪式交付物（gen 产物，程序侧收编到 data/gensokyou/rituals/）
├── structures/<name>.nbt   # 建筑交付物（save_structure 已直接写 src/.../structure/，此处留存收据）
└── textures/*.py     # 贴图数据文件（gen_tex 格式）
```

## 角色分工

| 工作 | 归属 |
|---|---|
| 仪式 pattern 设计、建筑设计、贴图/人形皮肤 | Astra（本目录产出） |
| 非人形实体 / BE 精细建模 | 用户（Blockbench）；UV 展平图可交 Astra 上色 |
| 新方块注册、behavior/渲染 Java、worldgen 接线 | 程序侧（opencode + 用户） |

## 收编流程（程序侧）

1. 审查 `blueprint.md` 与 gen 脚本 → 仪式 JSON 落 `data/gensokyou/rituals/`（或 .nbt 已就位）。
2. 新方块需求 → 注册 Java + gen_tex `--write-assets` → 重跑 `python tools/gen_catalog.py` 更新知识目录。
3. 重跑 `python tools/validate_ritual_pattern.py`（零 ERROR）→ 用户实机测试。
