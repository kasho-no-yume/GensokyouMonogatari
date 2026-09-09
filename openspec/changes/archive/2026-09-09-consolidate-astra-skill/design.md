## Context

设计知识长期分散在 `ritual-design`（仪式 pattern）与 `astra-design`（仪式+建筑+贴图）两处。二者内容重叠，但 astra-design 停留在旧 schema：1 基阶级、"升级=子集"、"material 随品阶递进"、"每级以 ritual_stone_N 族为主 ≥30%"——最后一条与本次会话确立的"仪式石族累积 ≤30%、必须大量普通方块装饰"硬不变量直接对立，正是此前产出"纯仪式石无美感"结构的根因。当前 rituals/ 与 ritual_recipes/ 已清空待重设计，是合并知识源的合适时点。

本 change 为**回溯立项**：文件变更已落地，proposal/specs/tasks 用于正式记录并纳入 archive 历史。

## Goals / Non-Goals

**Goals:**
- 单一事实源：设计代理只需读 `ritual-design`（仪式）+ `gen-textures`（贴图），不再有第三份互相冲突的手册。
- 零 know-how 损失：astra-design 中仍正确的内容（建筑 .nbt 工作流、实测坑、风格基准、体量预算、留空位约定）全部归位保留。
- 显式丢弃过时/冲突条款，不留"两说"。

**Non-Goals:**
- 不改任何 Java、不动运行时 pattern/配方数据（已单独清空）。
- 不重设计具体仪式结构（属后续设计任务）。
- 不改 openspec 归档历史（`changes/archive/**` 保持原样，作为既成事实记录）。

## Decisions

- **建筑工作流并入 ritual-design §7，而非新建 `astra-building` 技能**：建筑 .nbt 与仪式 pattern 同属"多方块结构设计"，共享对称/交付/校验心智；单独成技能会重新制造多源。代理定义按"仪式读 §1~§6、建筑读 §7"分流即可。
- **贴图知识并入 gen-textures，而非 ritual-design**：16px 偶数宽对称、紫系风格基准是**贴图**关注点，与仪式 pattern 无关；塞进仪式手册是错配。故只把风格基准移到本就存在的 gen-textures 技能。
- **PATTERNS.md/BLOCKS.md 落位随技能迁移到 ritual-design**：目录是 astra-artist 的唯一方块/仪式事实源，依附于其手册所在技能目录最自然。改 `gen_catalog.py` 默认 `--out`（非仅改文档），否则下次重跑会复活 `astra-design/` 目录。
- **冲突条款"≥30% ritual_stone 为主"直接删除、不折中**：它与现行"≤30% 石族"硬约束语义相反，无法共存；正确表达已在 ritual-design §4.8，保留会误导。
- **回溯形式化**：先前为保持文档真实已直接改过两份现行 spec；本 change 用 delta 记录同一变更，archive 时以 delta 文本覆盖同步（幂等）。

## Risks / Trade-offs

- [误删仍被引用的 astra 内容] → 删前全仓 grep `astra-design`，逐条改活引用（工具/代理/文档/spec），归档历史除外；删后再 grep 仅剩历史任务编号溯源。
- [`gen_catalog.py` 默认路径没改到位，重生成复活旧目录] → 已改默认 `--out` 为 ritual-design 并实跑验证；archive 后若有人跑旧命令会重新生成到 astra-design——故同时改的是默认值而非仅文档。
- [回溯 change 与已手改的现行 spec 文本不一致] → specs delta 以当前部署文本为准撰写，archive 时覆盖式合并，收敛到一致。
- [体量预算表(≤200..≤4800)与 §4.8 石族 ≤30% 叠加，可能让设计者困惑两者关系] → 表头注明"口径=累积总格数"，§4.8 注明"石族/总数"，两口径正交，一个限量级一个限配色比例。

## Migration Plan

已完成（本 change 为记录）：
1. 并入 ritual-design §7/§8 + gen-textures 风格基准。
2. 改 `gen_catalog.py` 默认 out → ritual-design，重生成 BLOCKS/PATTERNS。
3. 重写 astra-artist.md 指向。
4. 删除 `.opencode/skills/astra-design/`。
5. 修工具/文档/现行 spec 的 `astra-design` 路径引用。
回滚：`git checkout` 恢复 astra-design 目录与相关引用即可（无数据/代码耦合）。
