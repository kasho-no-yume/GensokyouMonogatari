## Why

玩家符卡目前只有 3 张（无想封印 / 冰符 / 光反占位），且**没有品质体系、数值不随玩家成长变化**——
前期四只 BOSS（大妖精 / 露米娅 / 黑谷山女 / 八云蓝伪）缺少与之主题呼应的掉落与学习内容。
同时玩家符卡长期缺少"功能优先"的设计规范，容易被写成"往某方向放一波弹"的空卡。
本变更补齐**品质 × 灵力强度缩放模型**，并落地第一批 6 张功能向玩家符卡。

## What Changes

- 新增**符卡品质体系**（1~5 品，对应 5 阶神恩标准灵力强度）与**灵力强度缩放模型**：
  - 已学符卡效果 = `Base × S^α_card`（`S` = 玩家灵力强度；**α 逐卡独立**，伤害近线性、恢复/控制亚线性）。
  - 道具符卡效果 = `Base × S_std(品)^α_card`（**固定**，不读玩家属性）。
- **BREAKING**：道具符卡规格新增"品质"字段与固定数值语义（原 `item-spellcards` 未定义品质）。
- 新增 6 张前期 BOSS 主题玩家符卡（同时提供道具形态与已学形态）：
  1. 花符『癒しの花園』（大妖精）· 恢复花圃（不回灵）
  2. 花符『鮮花之鎧』（大妖精）· 只挡弹射物的花瓣护盾（挡下给明确反馈）
  3. 闇符『ディマーケイション』（露米娅）· 群体致盲/脱战结界（含 BOSS；玩家主动攻击即终止）
  4. 網符『蜘蛛の巣』（黑谷山女）· 前方展开立方体网域，域内移速 ×0.2（含 BOSS）
  5. 疫符『病の返し』（黑谷山女）· 免疫新负面 + 对伤害者回敬随机负面
  6. 式神『狐の従者』（八云蓝伪）· 召唤狐火式神自动索敌攻击（限距限伤）
- 复用既有乘区：冷却（`spell_cdr`）、时长（`buff_extend`）、伤害/回血（`spell_amp`，其首个消费者）。

## Capabilities

### New Capabilities
- `player-spellcard-quality`: 符卡品质（1~5 品）定义、与灵力强度挂钩的逐卡缩放指数模型（已学动态 / 道具固定）、亚线性收益与上下限。
- `early-boss-player-spellcards`: 上述 6 张玩家符卡的功能语义、逐档数值、生效反馈与接入契约（含对 BOSS 生效与主动攻击终止等边界）。

### Modified Capabilities
- `item-spellcards`: 道具符卡规格新增"品质决定固定数值（`Base × S_std(品)^α_card`）"条款；新增 6 张卡的道具形态条目。

## Impact

- **代码**：`spirit/SpellCardEffects`（注册表扩展品质/缩放）、新增 `spirit/SpellCardScaling`（纯逻辑）、
  `config/GensokyouConfig`（新 `spellcards` 段：逐卡 `Base`/`α`/固定参数）、
  新增宿主实体（`FlowerGardenEntity` / `DarknessFieldEntity` / `WebFieldEntity` / `FoxServantEntity`）、
  事件钩子（`LivingIncomingDamageEvent` / `LivingHurtEvent` / `MobEffectEvent.Added`）、
  `AbstractTouhouBoss` 增加"被控制（致盲/减速）"接入点、`item/spellcard/*` 道具类与 `ModItems` 注册、客户端渲染（黑暗遮罩、狐火）。
- **资源**：`tools/gen_tex.py` 图标、`lang` 中英双份。
- **spec**：`item-spellcards` 增量；两个新 capability。
- **关联**：与 `docs` 沉淀的 `player-spellcard-design` skill 一致；不改弹幕轨道管线、不改 `skill-slots-hud` 的槽位/灵力校验语义。
