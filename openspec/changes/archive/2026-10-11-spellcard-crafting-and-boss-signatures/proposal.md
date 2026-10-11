## Why

玩家符卡目前**只有创造模式可见**：3 张占位卡（无想封印 / 冰符 / 光反）与 6 张前期 BOSS 主题卡
（大妖精 / 露米娅 / 黑谷山女 / 八云蓝伪）都没有任何生存获取路径。同时，`project.md` §3.5 的
BOSS 掉落体系（招牌武器 / 符卡道具 / 材料 / 円）缺少与角色主题绑定的**专属素材**。

本变更给全部玩家符卡补上「**源初造化之仪合成**」这一生存入口，并引入 **BOSS 签名素材**
作为主题卡的合成料，同时充当未来**回收仪式**与**经济/交易系统**的价值锚
（"交易系统能抹平很多劣势"——签名素材只要能换成价值，就不必每件都自带独门机制）。

## What Changes

- 全部 **9 张玩家符卡**新增**源初造化之仪**配方，按强度**分层造价**：
  - 低档（控制/反制：`spider_web`、`plague_repay`）：碎符卡星 ×2 + 对应主题信物 ×1。
  - 中档（恢复/防御：`healing_garden`、`flower_armor`，及 `demarcation`）：符卡星 ×1 + 主题料。
  - 高档（输出/召唤：`fox_servant`、`musou_fuuin`）：符卡星 ×2 + 主题信物 ×1。
  - 占位卡（无 BOSS 归属）走通用配方，不含主题信物。
- BOSS 击杀**低概率直接掉落**自己的成品道具卡（config 概率）。
- 三只已实现主题 BOSS 各有**主题信物**，其中**新增 2 件**：大妖精·花精之滴、黑谷山女·土蜘蛛之丝
  （对应 BOSS 击杀**保底掉落**，数量 config；具**独立贴图**，不做灰度染色偷懒）；
  八云蓝(伪) 复用**既有** `gensokyou:sukima_fragment`（隙间碎片）作主题信物——它已是该 BOSS
  召唤配方的前置，无需新造物品。另**预注册**第四件 `nightfall_core`（宵暗之核，属未实现的露米娅）：
  物品与 BOSS 解耦，本变更只注册物品、不接掉源，待 Rumia 落地接入。
- 主题信物用于对应 BOSS 主题卡的配方；其**通用出口**（回收仪式 / 经济交易系统）为后续变更，
  本变更只预留语义、不实现。

## Capabilities

### New Capabilities

- `boss-signature-materials`: BOSS 主题信物的定义与掉落来源（新增 2 件 + 复用既有隙间碎片）、
  作为主题符卡合成料的用法，以及作为「价值锚 / 未来回收与交易产出」的定位。

### Modified Capabilities

- `item-spellcards`: 玩家符卡（占位 3 卡 + 6 张主题卡）的源初造化获取配方（分层造价）与
  BOSS 低概率直掉。

## Impact

- **数据**：`data/gensokyou/ritual_recipes/zaohua_circle.json`（新增约 9 条符卡配方）。
- **代码**：`registry/ModItems`（新签名素材 `fairy_nectar` / `earth_spider_silk` / `nightfall_core`
  注册）、三只已实现对应 BOSS 的 `dropCustomDeathLoot`（签名素材保底 + 成品卡低概率）、
  `GensokyouConfig`（掉率/数量项）。
- **资源**：3 张签名素材独立贴图（`tools/gen_tex.py`）+ 中英 `lang` 键。
- **测试**：符卡配方的祭品台容量校验、签名素材掉落配置/资源测试。
- **边界**：露米娅未落地 → `demarcation` 本变更走通用配方，但其信物 `nightfall_core`（宵暗之核）
  已注册（无掉源）；Rumia 落地时接入掉源并可选把 `demarcation` 重指到它。
- **非目标**：学卡（已学形态仍**唯一**经委托，`project.md` §3.3；道具卡不可转已学）；
  符卡升品（暂缓，保留意见）；回收仪式与经济交易系统（后续独立变更）。
