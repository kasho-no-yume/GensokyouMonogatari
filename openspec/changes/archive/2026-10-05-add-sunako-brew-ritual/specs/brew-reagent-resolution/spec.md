## ADDED Requirements

### Requirement: brew_recipes 数据包为显式声明来源

系统 SHALL 提供 `data/gensokyou/brew_recipes/*.json` 数据包类型声明「炼药试剂 → 基础药水」的映射，由专属加载器在数据包加载/`/reload` 时读取。

每个文件 SHALL 声明其归属 `pattern`，`entries` 数组每条至少包含：

- `reagent`：试剂物品 id
- `potion`：基础药水在 `Registry/POTION` 中的 id
- 可选 `long_potion` / `strong_potion`：显式指定 LONG / STRONG 兄弟（供无命名约定的模组药水使用）
- 可选 `excluded_effects`：该条目生效的效果黑名单

显式声明 SHALL **优先于**任何自动推导。同一 `reagent` 被多条entries 命中时 SHALL 以**文件中出现顺序靠后者覆盖**前者（便于整合包追加覆盖），且日志 SHALL 记录被覆盖项。

`brew_recipes` SHALL NOT 复用 `ritual_recipes` 的 `ingredients` / `spCost` / `effect` 结构——后者语义为"祭品台凑料替换核心"，与"试剂决定药水种类"无对应关系。

#### Scenario: 数据包声明一条映射
- **WHEN** `brew_recipes/sunako_circle.json` 声明 `minecraft:blaze_powder → minecraft:strength`
- **THEN** 少名仪式接受烈焰粉作为力量药水的试剂

#### Scenario: 后者覆盖前者
- **WHEN** 同一文件内 `minecraft:blaze_powder` 出现两次且后一条指向不同药水
- **THEN** 生效的是后一条，且加载日志记录被覆盖的条目 id

#### Scenario: 可选兄弟字段生效
- **WHEN** 条目为某模组药水显式写了 `long_potion`
- **THEN** 2 阶时长取该 `long_potion` 中对应效果的时长，MUST NOT 走命名约定查找

### Requirement: 向酿造台反查作为回落

未被 `brew_recipes` 显式声明的试剂，系统 SHALL **回落**为向 `PotionBrewing` 反查：以`Registry<POTION` 全量条目为探针，对每个探针调用 `hasMix(试剂, 该探针的药水栈)`。

反查结果 SHALL 施加三条过滤：

1. `mix` 结果与输入相同（无变化）→ 丢弃。
2. 结果物品不是 `minecraft:potion` → 丢弃（滤除容器类转化，如枪粉→喷溅药水、龙息→滞留药水）。
3. 结果 potion 为 `minecraft:water` / `mundane` / `thick` / `awkward` → 丢弃（滤除 1.21 中"任意试剂 + 水 = 平凡"的废招）。

反查 SHALL **MUST NOT** 使用单一固定探针药水：1.21.1 的 `PotionBrewing.addStartMix` 把每个试剂同时注册为「水 + 试剂 = 平凡」与「苦艾 + 试剂 = 产物」两条边，故只有苦艾药水能命中原版试剂；遍历全注册表是唯一能同时覆盖原版 `addStartMix` 与模组任意 `from → to` 边的做法。

候选试剂集 SHALL 为：原版 `addVanillaMixes` 中出现的全部 `addStartMix` 试剂，∪ `brew_recipes` 显式声明的全部试剂。

#### Scenario: 原版试剂经反查命中
- **WHEN** 未声明烈焰粉且以全注册表反查
- **THEN** 烈焰粉经苦艾药水探针命中 `minecraft:strength`，被纳入可映射试剂

#### Scenario: 平凡药水探针查不到
- **WHEN** 仅以 `minecraft:mundane` 为探针反查烈焰粉
- **THEN** 无任何命中（`addStartMix` 的原料边是水与苦艾，平凡不在其中）

#### Scenario: 容器类转化被滤除
- **WHEN** 反查遇到枪粉（其边通向 `splash_potion`）
- **THEN** 该结果因产物物品不是 `minecraft:potion` 被丢弃，MUST NOT 产出喷溅药水

#### Scenario: 废招结果被滤除
- **WHEN** 反查得到 `minecraft:mundane`
- **THEN** 该结果被丢弃，MUST NOT 作为可产出药水

### Requirement: 解析结果缓存与现查语义

试剂 → 基础药水的反查索引 SHALL 在启动/数据包加载时构建一次并缓存，运行时 SHALL **MUST NOT** 重复执行全注册表扫描。

映射 SHALL 为**每次启动现查**：批次执行时按当前试剂即时解析，MUST NOT 在试剂放入槽位时锁定结果。`/reload` 改写映射后，先前放入的试剂 SHALL 立即按新映射解释；若新映射下该试剂无法解析，批次 SHALL 失败并明示"该试剂无法炼制"。

SHALL NOT 以 `Holder<Potion>` 强引用的形式跨`/reload` 持久化解析结果（重载后可能指向已删除的注册条目）。

#### Scenario: 首次访问建索引
- **WHEN** 服务器启动后首次执行炼药
- **THEN** 构建一次反查索引并缓存

#### Scenario: reload 后映射立即生效
- **WHEN** 整合包`/reload` 修改了某试剂的映射
- **THEN** 玩家此前放在槽里的该试剂按新映射解释，MUST NOT 仍按旧映射产出

#### Scenario: reload 后试剂失效
- **WHEN** `/reload` 后某试剂在新映射中无任何对应基础药水
- **THEN** 批次失败并明示"该试剂无法炼制"，MUST NOT 静默产出错误药水