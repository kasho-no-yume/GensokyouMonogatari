# Design: ritual-recipe-system

## Context

生命周期框架（ritual-lifecycle-v2）落地后，仪式有了统一的"成型→启动→运行"骨架与祭品门槛层（requirements，定位式 key+slot）。但配方概念缺失：加工环转换表硬编码于 `ProcessingBehavior`/`SpiritProcessingRecipes`；召唤类供品组合无法数据化多选。用户保证**任何配方的祭品摆放顺序无要求**——这是按台面内容做无序匹配的契约基础。

## Goals / Non-Goals

**Goals:** 配方独立数据文件；位置无关的无序多重集匹配与全有全无扣减；歧义配置期拒载；activation/passive 双模式执行；minTier 超集语义；UI 清单与 JEI 配方卡。
**Non-Goals:** 通用配方抽象库（不与 vanilla RecipeManager 合流）；标签重叠的完美匹配算法；跨仪式共享原料池；effect 的内置注册表（解释权在行为侧）。

## Decisions

### D1 数据形态与目录

```jsonc
// data/gensokyou/ritual_recipes/<名称>.json —— 一文件一配方
{
  "pattern": "gensokyou:processing_circle",   // 必填
  "mode": "activation",                        // activation | passive，默认 activation
  "minTier": 1,                                // 默认 1
  "ingredients": [                             // ≥1 项；item 支持 #tag
    { "item": "#forge:ingots/iron", "count": 2 },
    { "item": "gensokyou:ppoint", "count": 1 }
  ],
  "result": { "item": "gensokyou:refined_iron", "count": 1 },  // 可选
  "effect": "gensokyou:some_effect"            // 可选字符串 id，行为侧解释
}
```

- 独立目录而非内嵌 ritual JSON：配方数量级大、需独立热载、JEI 直接遍历、避免 pattern 文件膨胀。
- 加载期校验：`ingredients` 非空；`result`/`effect` 至少其一；passive MUST 有 result（否则无处落物）；count ≥1。违规拒载并日志报因。
- `pattern` 引用存在性属跨数据集检查：**使用期警告一次**（对齐 vanilla placed_feature 引用的宽限惯例），不做加载顺序耦合。

### D2 无序多重集匹配：严格等值 + 贪心扣减

```
输入：成型结构内全部祭品台 BE 的持有栈集合 pools
规则：归一化聚合(pools) == 归一化聚合(ingredients) 才可匹配
      —— 台面上存在配方之外的物品即视为不匹配（"台面即配方"，直觉且天然消除多数歧义）
扣减：ingredients 按「精确物品条目优先、标签条目在后」排序后贪心分配到具体栈，
      全部满足才应用账本（逐台 setHeld 剩余量），任一不足整体放弃
```

- 严格等值是刻意选择：多余物品阻断匹配虽显严格，但换来①超集配方天然无歧义（{A} 与 {A,B} 因台面内容不同而互斥）②玩家预期清晰（清单即真相）。
- 贪心次序缓解标签重叠误吞；残余的病态重叠（两个标签圈定同一物品域）记录为已知限制，由作者规避。

### D3 歧义校验期拒绝

同一 `pattern` + 同一 `mode` 下，若两条配方的归一化原料表完全一致 → 后者拒载，日志报明两条 id。标签导致的语义重叠无法静态判定，不在校验范围（文档标注）。pattern 引用缺失仅使用期警告。

### D4 双模式执行模型

- **activation**：`core.start()` 流程扩展为 requirements 检查 → 在该 pattern 可用配方中按 minTier 过滤并找第一条匹配 → 行为 `onStart` → 应用扣减账本 → `activeRecipeId` 写入核心 BE（持久化，stop/失效清除）→ enabled → 行为 `onRecipeExecuted(recipe)` 收尾（effect 解释权在此）。pattern 无任何配方时保持既有路径（占位召唤环不受影响）。
- **passive**：核心 tick 中（成型即可，不受 enabled 门控——对齐加工环现状的常驻语义），按周期匹配 passive 配方，成功即扣料并将 `result` 写回**规范序第一个被消耗的台位**（容量不足则整单放弃）。`ProcessingBehavior` 迁移为纯消费方：删除硬编码表，改读配方库；`SpiritProcessingRecipes` 退役。

### D5 minTier 超集语义

配方匹配前先以 `match.level() >= minTier` 过滤。高等级结构自动解锁更多配方=超集，无需任何继承机制；等级回落（静默降级）时高阶配方自然失配。

### D6 requirements 过渡策略

requirements 字段保留原语义（结构性常驻条件 / periodic 供给），不删不改；文档标注"摆供品触发结果"类需求一律用 recipes 表达。两者可在同一仪式共存（先验常驻条件，再验配方）。

### D7 UI 与 JEI 呈现

- InfoPayload 增加 recipes 段：当前等级可用配方列表（名称语言键 + ✓/✗ + 缺项摘要），上限 4 条；启动按钮在有配方仪式上受匹配约束，失败原因含"无可匹配配方"。
- JEI 新增 `ritual_recipe` category：输入位 = ingredients，输出位 = result 物品或 effect 文本；U 键反查原生生效。原 requirement 卡片类别与对应 spec 要求一并移除（被配方卡取代）。

## Risks / Trade-offs

- [严格等值对玩家偏严] → UI 缺项摘要明确指出"多余的物品"；代价换取确定性
- [贪心在病态标签重叠下可能漏配] → 排序启发式覆盖常见情形；残余场景文档标注作者规避
- [passive 结果落位规则的人为性] → 规范序首台位规则简单确定；实际体感不佳再迭代
- [加工环迁移回归] → 示例配方 JSON 完整覆盖旧转换表后再删旧路径
- [两 loader 时序] → 歧义校验限定在 recipe loader 内部完成，不依赖 pattern loader 就绪

## Migration Plan

新目录纯增量；加工环迁移随示例配方入库同 PR 完成。/reload 即生效；git revert 单变更回滚。

## Open Questions

- 结构层级 inherit 合成糖（上一轮遗留）是否搭车本变更：倾向不搭，保持本变更边界
