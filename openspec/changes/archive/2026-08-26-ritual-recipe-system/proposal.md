# Proposal: ritual-recipe-system — 仪式配方系统

## Why

仪式框架目前没有"配方"概念：加工环的转换表硬编码在行为里，召唤类"摆什么供品"只能靠 requirements 定位式声明（key+slot 绑定具体台位），无法用数据文件表达"多种可选组合 → 多种结果"。用户保证**供品摆放顺序无关**，这使得配方可以按"台面内容多重集"匹配，与各仪式台位布局彻底解耦——是引入一等配方公民的最佳时机。同时上一轮遗留的多等级需求（高等级配方是低等级超集）由配方的 `minTier` 一并落地。

## What Changes

- **新数据类型 `ritual_recipes/*.json`**：每文件一条配方——`pattern` 挂靠仪式、`minTier` 等级门槛、`mode`（activation 启动型 / passive 持续型）、无序 `ingredients[]`（物品 id 或 #标签 ×数量）、结果双形态（`result` 实物产物 与/或 `effect` 效果 id，至少其一）
- **无序多重集匹配**：框架扫描成型结构内全部祭品台的持有物做严格等值比对（台面内容 = 配方原料，多余物品视为不匹配），贪心分配扣减（精确物品条目优先于标签条目），全有全无
- **歧义校验期拒绝**：同一仪式同模式下原料表完全相同的配方，loader 直接拒载并日志报因；pattern 引用不存在时使用期警告一次（对齐 vanilla 数据包宽限惯例）
- **启动型执行**：UI 启动按钮解析当前等级可用配方 → 匹配成功才可启动 → 扣减原料 → `activeRecipeId` 存入核心 BE → 行为经 `onRecipeExecuted` 接收（effect 字段的解释权完全在行为侧）；无配方仪式保持原启动路径不受影响
- **持续型执行**：成型期间周期性匹配 passive 配方，自动扣料并把 `result` 写回祭品台；加工环行为迁移到配方驱动，删除硬编码查表
- **UI/JEI 呈现**：界面新增"可用配方"清单（✓/✗ 及缺项提示、显示 activeRecipeId）；JEI 以配方卡取代原"祭品要求卡"（输入=ingredients，输出=result 物品或 effect 文本），U 键反查原生生效
- **requirements 过渡策略**：字段保留为结构性常驻条件（周期燃料等），文档标注新仪式一律用 recipes

## Capabilities

### New Capabilities

- `ritual-recipes`: 配方定义格式、位置无关的无序多重集匹配、歧义拒载、双模式执行模型与 minTier 超集语义

### Modified Capabilities

- `ritual-lifecycle`: 启动动作细化——有配方的仪式 SHALL 解析激活配方成功后方可启动，激活配方 id 随 enabled 一起存续/清除
- `ritual-core-interface`: 界面新增"可用配方"清单展示；启动按钮在有配方仪式上受配方匹配约束
- `jei-ritual-display`: 移除"祭品要求配方卡"，改为 rituals_recipes 驱动的仪式配方卡

## Impact

- 新包/类：`RitualRecipe` 记录 + `RitualRecipeLoader`（新监听目录 ritual_recipes）+ 无序匹配器（扩展 RitualOfferings 或并列新类）
- 核心 BE 增加 `activeRecipeId` 持久化字段；tick 增加被动匹配循环（仅成型且 pattern 含 passive 配方时）
- `ProcessingBehavior` 迁移到配方驱动，`SpiritProcessingRecipes` 硬编码表退役；新增示例配方 JSON 若干
- JEI：新增 recipe category，移除 requirement 卡片路径
- 文档：checklist 增补配方编写节；lang 双语条目
