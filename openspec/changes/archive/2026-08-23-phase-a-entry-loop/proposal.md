# Proposal: 阶段 A — 入门循环

> 需求依据：`openspec/project.md` §3.5/§3.7、§7 阶段 A。
> 本提案为**框架级**：划定本阶段交付边界；design/specs/tasks 在开工前细化。

## Why

项目需要可运行的起点。阶段 A 交付"从野外遇敌到打赢第一个 BOSS"的最小战斗闭环，
验证核心手感（弹幕战斗 + 道具符卡），并为后续所有阶段建立注册与配置骨架。

## What Changes

- **注册骨架与配置框架**：DeferredRegister 体系、统一 ModConfigSpec（boss/danmaku/items 等节）、创造模式标签；清理模板示例代码
- **妖精生态（入口体验）**：
  - 小妖精：主世界野外随机刷新的普通敌对生物，发射简单弹幕
  - 大妖精：小概率刷新的小 BOSS，击败掉落**引导书**
- **弹幕战斗基件**：`gensokyou:danmaku` 数据驱动伤害类型（bypasses_armor 标签）、弹幕投射物实体（NBT 持久化伤害/来源）、弹幕护盾状态效果
- **首批道具符卡**（一次性消耗品，无素质门槛）：2–3 张，含无想封印（六玉环绕，实体化重写）
- **召唤仪式 + 芙兰朵露（低阶形态）**：多方块召唤仪式（材料驱动）；芙兰朵露野外战斗 AI（瞬移/随机弹幕/八向环形弹幕/分身），ServerBossEvent 血条
- **掉落物体系**：ppoint/bpoint 合成材料、円货币、招牌武器拉维坦剑、符卡星/碎符卡星

## Capabilities（本阶段将产出的 spec）

- `mod-registration-skeleton`：注册骨架/配置框架/创造标签
- `fairy-ecology`：妖精刷新规则、引导书获取链
- `danmaku-combat`：伤害类型/投射物/护盾效果
- `item-spellcards`：符卡基类与首批卡（含无想封印）
- `flandre-boss-low-tier`：低阶形态 BOSS 与召唤仪式
- `loot-currency-basics`：材料/货币/武器掉落体系

## Impact

- 全新代码域：`registry/ item/ entity/ effect/ spellcard/ config/ client/`
- 复用旧仓库资产：lightorb/flandre 贴图、item 模型 JSON（路径适配）；数值基线进配置默认值
- 不引入第三方依赖；不涉及幻想乡维度、灵力经济、技能槽系统（属 B/C 阶段）

## Out of Scope（明确不在本阶段）

个人灵力池、发电机/电容仪式、淬炼炼体、技能槽/HUD/键位、商人 NPC、委托任务、幻想乡维度、降神变身
