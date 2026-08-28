# Tasks: ritual-recipe-system

## 1. 配方数据层

- [x] 1.1 RitualRecipe 记录 + RitualRecipeLoader（新监听目录 ritual_recipes）：schema 解析、result/effect 至少其一、passive 必有 result 等加载校验
- [x] 1.2 歧义拒载：同 pattern+mode 归一化原料表重复时后者拒绝并日志报因；pattern 引用使用期警告一次
- [x] 1.3 按 pattern 分组的查询 API（含 minTier 过滤）

## 2. 无序匹配内核

- [x] 2.1 台面多重集采集：成型结构内全部祭品台持有栈聚合
- [x] 2.2 严格等值比对 + 「精确优先于标签」贪心分配账本，全有全无应用（逐台 setHeld 剩余量）

## 3. 启动型执行接线

- [x] 3.1 core.start 流程扩展：等级过滤 → 配方解析 → onStart → 扣减账本 → activeRecipeId 持久化；无配方仪式走原路径
- [x] 3.2 RitualBehavior.onRecipeExecuted 回调（effect 解释权在行为）；stop/失效清除激活配方

## 4. 持续型执行与加工环迁移

- [x] 4.1 核心 tick 被动匹配循环（不受 enabled 门控、周期控频）+ result 写回规范序首台位（容量不足整单放弃）
- [x] 4.2 ProcessingBehavior 迁移为配方驱动；SpiritProcessingRecipes 退役；补齐覆盖旧转换表的示例配方 JSON

## 5. UI 与 JEI

- [x] 5.1 InfoPayload 增加 recipes 段；界面渲染可用配方清单（✓/✗ + 缺项摘要）与当前激活配方；启动失败原因含"无匹配配方"
- [x] 5.2 JEI ritual_recipe category（输入 ingredients / 输出 result 或效果文本 + minTier 标注）与插件同步；移除 requirement 卡片路径

## 6. 收尾

- [x] 6.1 语言条目（配方名/缺项摘要/失败原因）zh_cn 与 en_us
- [x] 6.2 docs/new-ritual-checklist.md 增补配方编写节（格式/严格等值语义/minTier/歧义规则），标注 requirements 过渡策略
- [x] 6.3 gradlew build 通过 + 游戏内验证：加工环配方驱动转换回归；召唤类摆料→UI 启动→扣料→回调链路；歧义 JSON 拒载告警；JEI 配方卡与 U 键反查
