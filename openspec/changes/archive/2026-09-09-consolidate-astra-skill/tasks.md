## 1. 知识并入（保留正确内容）

- [x] 1.1 `ritual-design/SKILL.md` §1 并入 id 书写规则（gensokyou: 可省、minecraft:/#标签必须写全）与核心功能留空位坐标（四向 `(0,1)`@y0、正上方 `(0,1,0)`、电容槽 `(2,2)`@y1）
- [x] 1.2 `ritual-design/SKILL.md` §3 并入东方意象词库；§5.2 并入 0~5 阶体量预算表（口径=累积总格数）
- [x] 1.3 `ritual-design/SKILL.md` 新增 §7 建筑 .nbt 工作流（交付目录 + struct_compile/gen_building_template + nbtlib gzipped / /place 邻块 / 楼梯 facing 高侧 / 旗帜罗盘序 / CHAT 日志 cp936 实测坑）与 §8 输出纪律
- [x] 1.4 `gen-textures/SKILL.md` 并入紫系石族风格基准与 16px 对称纹样偶数宽红线
- [x] 1.5 冲突/过时条款（1 基阶级、升级=子集、ritual_stone 族 ≥30% 为主、材料随品阶递进）**不迁移**，随 astra-design 删除

## 2. 生成器与目录落位

- [x] 2.1 `tools/gen_catalog.py` 默认 `--out` 改 `.opencode/skills/ritual-design/`，去除 "Astra" 抬头措辞（docstring + BLOCKS 头）
- [x] 2.2 运行 `python tools/gen_catalog.py` 在 ritual-design 下重生成 BLOCKS.md / PATTERNS.md（PATTERNS 如实为空，rituals 已清空）

## 3. 代理与引用同步

- [x] 3.1 重写 `.opencode/agent/astra-artist.md`：事实源改指 ritual-design（仪式 §1~§6 / 建筑 §7）+ gen-textures + 同目录 BLOCKS/PATTERNS
- [x] 3.2 修 `tools/gen_building_template.py`、`design/astra/README.md`、`docs/new-ritual-checklist.md` 中的 `astra-design` 路径/过期描述（checklist 标注占位仪式与 4 配方已清空）
- [x] 3.3 两份现行 spec（`astra-artist-agent`、`design-catalog`）文本已在本 change delta 正式化；`changes/archive/**` 历史不动

## 4. 删除与验证

- [x] 4.1 删除 `.opencode/skills/astra-design/` 整个目录
- [x] 4.2 全仓 grep `astra-design`（排除 node_modules 与 archive）确认活引用清零，仅剩历史任务编号溯源
- [x] 4.3 `python tools/validate_ritual_pattern.py` 通过（exit=0）；`python tools/gen_catalog.py` 重跑不再产生 astra-design 目录
- [ ] 4.4 archive 本 change，将 delta 合并进 `openspec/specs/` 后，复核两份现行 spec 与部署文本一致
