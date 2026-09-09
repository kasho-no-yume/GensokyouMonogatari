## MODIFIED Requirements

### Requirement: 目录由数据事实源机器生成
仓库 SHALL 提供 `tools/gen_catalog.py`，仅扫描数据事实源——
`assets/gensokyou/blockstates/`、`data/gensokyou/tags/block/`、`data/gensokyou/rituals/`——
（不解析 Java），生成 `BLOCKS.md` 与 `PATTERNS.md` 两个知识目录文件。
默认输出落位 SHALL 为 `.opencode/skills/ritual-design/`（`--out` 可覆盖），
与 astra-artist-agent 知识包同目录；astra-design 目录已移除，工具与目录文件 MUST NOT 再引用它。

#### Scenario: 生成目录
- **WHEN** 运行 `python tools/gen_catalog.py`
- **THEN** BLOCKS.md 与 PATTERNS.md 产出到 `.opencode/skills/ritual-design/`，且全程未读取任何 .java 文件

#### Scenario: 默认落位不复活旧目录
- **WHEN** 有人不带 `--out` 直接重跑 `gen_catalog.py`
- **THEN** 目录生成到 ritual-design，不会再在 `.opencode/skills/` 下重建 `astra-design`
