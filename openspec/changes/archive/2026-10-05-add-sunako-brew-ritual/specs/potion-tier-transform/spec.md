## ADDED Requirements

### Requirement: 药水效果读取以注册表为唯一事实源

药水效果的取值 SHALL 以 `Registry<POTION>`（`BuiltInRegistries.POTION`）为唯一事实源：每个 `Potion` 条目的 `getEffects()` 即该药水的权威效果列表。系统 MUST NOT 硬编码"原版药水 → 效果"的对照表。

由此，全部原版药水与模组经 NeoForge 注册的自定义 `Potion` 条目 SHALL 自动进入可产出范围，MUST NOT 需要本 mod 追加代码。

无效果的条目（`water` / `mundane` / `thick` / `awkward`，`getEffects()` 为空）SHALL NOT 作为可产出目标。

系统 SHALL NOT 依赖"效果 → 药水"的反向映射（1.21.1 的 `MobEffect` 不持有对应 `Potion` 引用）。

#### Scenario: 模组自定义药水无需改代码
- **WHEN** 某模组注册了 `Potion` 条目并通过 `brew_recipes` 或酿造台配方产出该药水
- **THEN** 少名仪式能以该条目为基础产出其1/2/3 阶变体

#### Scenario: 废招条目不可产出
- **WHEN** 试剂解析结果指向 `minecraft:mundane` / `minecraft:thick` / `minecraft:awkward` / `minecraft:water`
- **THEN** 该条目被过滤，不进入可产出集合

#### Scenario: 多效果药水逐条变换
- **WHEN** 基础药水含多个效果（如 `minecraft:turtle_master` 的缓慢 + 抗性两条）
- **THEN** 每条效果独立参与阶级变换

### Requirement: 阶级变换规则与 LONG/STRONG 兄弟链

设基础药水的效果列表为 `E`。变换 SHALL 按阶施加：

- **1 阶**：`E` 原样输出，不改任何时长或品质。
- **2 阶**：对`E` 中每条效果 `e`：
  - `e` 的品质 SHALL **无条件 +1**（这是"红石 + 荧石同施"，原版 1.21.1 因长时效与高品质是互斥的独立注册条目而无法产出该组合）；
  - `e` 的时长 SHALL 依次取值：`LONG_` 兄弟含同一效果 → 取其时长；否则 `STRONG_` 兄弟含同一效果 → 取其时长；否则按 `extend_without_long` 决定合成（打开时 × `SUNAKO_LONG_DURATION_MULTIPLIER`）或保持基础时长。
- **3 阶**：先求出 2 阶结果，再令 `e.品质 = max(e.品质 + 1, 原版该效果可达最高品质)`，**时长沿用 2 阶**（3 阶只继续升品质，不再延长）。

`STRONG_` 作为时长回退项而非仅供品质，SHALL 用于保住**瞬发效果**的 1 tick 时长（治疗/伤害）：它们没有 `LONG_` 兄弟，若改用倍率缩放会把 1 撑成 3。

「原版该效果可达最高品质」SHALL 取 `max(基础.品质, LONG_.品质, STRONG_.品质)`。该品质地板的作用是防止本仪式最高阶产物弱于原版强效药水（例：原版 `strong_slowness` 的品质为 3，若无地板则 3 阶缓慢仅为品质 2）。

`LONG_` / `STRONG_` 兄弟的查找 SHALL 支持数据覆盖：当基础药水遵循命名约定时按约定查找；`brew_recipes` 条目 MAY 显式写 `long_potion` / `strong_potion` 指明兄弟（供无命名约定的模组条目使用）。

退化行为 SHALL 为：两个兄弟皆无时，默认保持基础时长；`brew_recipes` 条目可写 `extend_without_long` 打开按 `SUNAKO_LONG_DURATION_MULTIPLIER`（默认 **8/3**）合成。倍率 SHALL 可配置。`amplify_without_strong` SHALL 保留解析以兼容既有 schema，但因 2 阶品质提升已无条件，MUST NOT 依赖它判断行为。

#### Scenario: 力量的三阶阶梯
- **WHEN** 以 `minecraft:strength`（品质 0 / 时长 3600）为基准产出三阶
- **THEN** 1 阶为品质 0 时长 3600；2 阶为品质 1 时长 9600；3 阶为品质 2 时长 9600

#### Scenario: 仅有 STRONG 兄弟的瞬发药水
- **WHEN** 以 `minecraft:healing`（品质 0 / 时长 1）产出三阶
- **THEN** 2 阶品质 1 时长 1，3 阶品质 2 时长 1，MUST NOT 因倍率缩放把 1 tick 撑成 3

#### Scenario: 两兄弟皆无时只升品质
- **WHEN** 以 `minecraft:luck`（品质 0 / 时长 6000，无 LONG 与 STRONG 兄弟）产出三阶，且未写 `extend_without_long`
- **THEN** 2 阶为品质 1 时长 6000，3 阶为品质 2 时长 6000——品质照涨，时长因无原版依据而保持

#### Scenario: 品质地板防止倒挂
- **WHEN** 以 `minecraft:slowness` 产出三阶
- **THEN** 3 阶品质不低于原版 `minecraft:strong_slowness` 的品质 3

#### Scenario: 多效果药水逐条独立变换
- **WHEN** 以 `minecraft:turtle_master`（缓慢 品质3/400 + 抗性 品质2/400）产出三阶
- **THEN** 两条效果各自按同一规则变换：2 阶为缓 a4 + 抗 a3 / 时长 800，3 阶为缓 a5 + 抗 a4 / 时长 800

### Requirement: 效果黑名单排除

`brew_recipes` SHALL 支持 `excluded_effects` 列表。列入黑名单的 MobEffect 在阶级变换中 SHALL 被**跳过品质提升**，保留其基础品质与时长（即视为"不可升品质"）。黑名单 SHALL 逐 `brew_recipes` 条目生效，MUST NOT 是全局开关。默认黑名单为空（全部启用品质地板）。

#### Scenario: 黑名单效果不升品质
- **WHEN** 某 `brew_recipes` 条目的 `excluded_effects` 含 `minecraft:movement_slowdown`
- **THEN** 该效果在 2/3 阶保持基础品质与基础时长，不适用品质地板

#### Scenario: 黑名单逐条目隔离
- **WHEN** 条目 A 黑名单了某效果，条目 B 未黑名单
- **THEN** 同一效果在条目 A 的产出中不升品质，在条目 B 的产出中正常升品质

### Requirement: 产物 PotionContents 与自定义名构造

产出药水的构造 SHALL 遵循 1.21.1 `PotionContents` 的字段语义：

- **1 阶** SHALL 保留 `potion` holder（`Optional.of(base)`）且 `customEffects` 为空，SHALL NOT 设置 `CUSTOM_NAME`——其效果与原版条目完全一致，物品名直接复用原版 lang。
- **2/3 阶** SHALL 置 `potion` 为空（`Optional.empty()`）并把变换后的效果写入 `customEffects`。MUST NOT 同时保留 holder——`getAllEffects()` 会把两者**拼接**，导致效果翻倍。
- **2/3 阶** SHALL 设置 `DataComponents.CUSTOM_NAME`，取自本 mod 的 lang 键 `item.gensokyou.potion.t{2,3}.<effect_path>`。缺失 `CUSTOM_NAME` 时原版物品名会退化为 `item.minecraft.potion.effect.empty`（英文 "Uncraftable Potion"），故 MUST NOT 漏设。

变换内核 SHALL 为**世界无关纯静态函数**，其输入仅为 `Holder<Potion>` / 阶级 / 倍率参数 / 黑名单，MUST NOT 依赖 `ServerLevel`、方块实体或玩家，以便直接单元测试。

#### Scenario: 一阶复用原版物品名
- **WHEN** 产出 1 阶力量药水
- **THEN** 物品 `POTION_CONTENTS.potion` 指向 `minecraft:strength`，无自定义名，物品显示原版名

#### Scenario: 二阶不出现效果翻倍
- **WHEN** 产出 2 阶力量药水并检查其效果列表
- **THEN** 效果列表恰含 1 条 `DAMAGE_BOOST`（品质 1 / 时长 9600），MUST NOT 出现原版 3600 时长的重复条目

#### Scenario: 三阶物品名正确
- **WHEN** 产出 3 阶力量药水
- **THEN** 物品有 `CUSTOM_NAME`，显示为本 mod 的 3 阶力量药水名，MUST NOT 显示 "Uncraftable Potion"

#### Scenario: 变换内核可脱离世界测试
- **WHEN** 在无游戏环境的单元测试中给定 `Holder<Potion>` 与阶级调用变换内核
- **THEN** 返回确定的 `PotionContents`，MUST NOT 需要 `ServerLevel` 或任何世界对象