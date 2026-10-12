## Why

BOSS 专属掉落物（碎符卡星、主题信物）是符卡合成与召唤体系的关键料，却只能靠**反复击杀同一 BOSS** 获取；玩家不愿二次讨伐，而材料消耗量很大，形成「要么重复劳动、要么卡进度」的死结。

`boss-signature-materials` 在落地时就预留了这一后续缺口：

> 主题信物的**通用出口**（回收仪式换高价值物、经济/交易系统）为后续变更。

本变更即兑现该预留：提供一条**以大量灵力换取 BOSS 产物**的仪式路径，使玩家在击败 BOSS 一次、取得其信物后，无需重复讨伐即可持续获得其专属产物。

## What Changes

- 新增仪式「**镇魂返照之仪**」（`gensokyou:chinkon_hanshou_circle`，1~5 阶，**激活型** 一次性）：消耗一件「寄宿残魂的众生典籍」+ 大量灵力，产出 `0~1` 碎符卡星 与 `1~2` 件与该 BOSS 对应的主题信物。
- 阶级门槛语义：**结构阶级 SHALL ≥ 该 BOSS 自身品阶（`bossTier()`）**；低阶结构 MUST NOT 产出高阶 BOSS 产物（高阶结构可向下兼容低阶 BOSS）。
- 新增物品「**寄宿残魂的众生典籍**」：由**空的**众生典籍 + 一件主题信物合成，编码该 BOSS 的身份与品阶；该物品 MUST NOT 被「众生余录」识别，只能作为镇魂返照之仪的供品。
- **BREAKING**：众生典籍的满态由「不可逆终态」改为**可清空**——新增合成把任意众生典籍（含满 20）重置为空书，作为残魂典籍合成的原料来源。
- 主题信物获得首条「回收出口」：镇魂返照之仪是信物可被消费为其他产物的第一条系统路径。
- 结构 pattern 已先行落盘并通过离线校验（本变更对其进行实现接线与实机验证）。

## Capabilities

### New Capabilities

- `chinkon-hanshou-ritual`：镇魂返照之仪的多方块行为契约——激活型一次性供品触发、阶级门槛（结构阶 ≥ BOSS 品阶）、消耗残魂典籍、随机产出碎符卡星 + 主题信物、灵力成本/容量、产出光柱、GUI 状态行、专属配置旋钮、调试探针。
- `boss-residual-codex`：寄宿残魂的众生典籍物品契约——空典籍 + 主题信物 → 残魂典籍（编码 BOSS 身份/品阶）、被众生余录排除、tooltip 表现。

### Modified Capabilities

- `codex-of-beings`：满态「不可逆终态」改为可被新增合成清空；新增「清空典籍」配方的行为要求。
- `boss-signature-materials`：主题信物的「通用出口」由**声明**改为**落地**（新增回收仪式引用），移除「本变更不实现回收」的排他场景。

## Impact

- **数据/资源**：新 pattern `data/gensokyou/rituals/chinkon_hanshou_circle.json`（已落盘）；新物品的注册/贴图/模型；「残魂合成」与「清空典籍」两条配方；lang（物品、JEI 仪式名、GUI 信息行）；Patchouli 指导书条目；JEI 展示。
- **代码**：`RitualBehaviors` 注册 + 新 `ChinkonHanshouBehavior`；`ModItems` 新物品；`CodexOfBeingsItem` 清空/读取接线；两条配方（数据驱动或序列化）；`GensokyouConfig` 新基项；`DebugCommands` 探针。
- **依赖**：仪式框架（`RitualBehavior` / `ritual-recipes` 的 activation 语义）、`#gensokyou:boss_signature` 标签、`bossTier()`、`broken_spell_card_star` / `spellcard_star`、`CodexOfBeingsItem` 读写契约。
- **风险**：信物与碎符卡星将变为可由灵力再生的资源，需防止出现「比打 BOSS 更划算」的无限循环（碎星产出时以 `0~1` 压低、信物回吐不高于投入的期望值）；当前 2~5 阶无对应 BOSS（带信物者全为 T1），高阶为预付结构件。
