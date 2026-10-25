## 1. B 点

- [x] 1.1 确认 `data/gensokyou/recipe/bpoint.json` 已是 3×3 八 P 环绕 → 1 B（8 P → 1 B）。
- [x] 1.2 `zaohua_circle.json` 的 9 张符卡配方各加 B 点：
  低档 1（`spider_web` / `plague_repay` / `light_reflect`）、
  中档 2（`healing_garden` / `flower_armor` / `demarcation` / `icicle_fall`）、
  高档 2（`fox_servant` / `musou_fuuin`）。
- [x] 1.3 逐条断言 `Σcount ≤ 16`；`musou_fuuin` 加 2 后恰为 16，MUST NOT 加 4。
- [x] 1.4 符卡 JEI/书页原料显示 B 点；B 点不再是无用途物品。

## 2. 灵鱼与海豚恩惠

- [x] 2.1 注册 `gensokyou:dolphins_grace` 药水（效果 `MobEffects.DOLPHINS_GRACE`），
  只做长效档，不注册强效档。
- [x] 2.2 `data/gensokyou/brew_recipes/sunako_mod_potions.json` 增加
  reagent=`gensokyou:spirit_fish` → potion=`gensokyou:dolphins_grace` / 长效。
- [x] 2.3 长效档消耗 `gensokyou:moon_sand`；原版水瓶不产出该药水。
- [x] 2.4 lang（zh/en）、药水颜色/图标、创造栏归类。
- [x] 2.5 单测/实机：灵鱼 + 冥河瓶 → 长效海豚恩惠；无灵鱼/原版水瓶不产出。

## 3. 人鱼鳞 / 龙鳞盔甲

- [x] 3.1 注册 8 件装备：
  `mermaid_scale_{helmet,chestplate,leggings,boots}`、
  `dragon_scale_{helmet,chestplate,leggings,boots}`。
- [x] 3.2 数值：护甲值、韧性、耐久与钻石盔甲逐件相同；不做弹幕/全伤害减免。
- [x] 3.3 人鱼鳞全套：水下呼吸 + **仅水下**夜视；出水立即移除
  （难判断时最多保留 5 秒）。
- [x] 3.4 龙鳞全套：火焰伤害全免（Fire Resistance 语义）+ `KNOCKBACK_RESISTANCE=1.0`。
- [x] 3.5 全套判定：缺任一件立即移除效果；脱下/换件同一 tick 失效。
- [x] 3.6 配方：5/8/7/4 主材 + 对应凡材；不提供纯 mod 材配方。
- [x] 3.7 模型/贴图（走 `tools/gen_tex.py`，禁止逐像素脚本）、lang、创造栏。
- [x] 3.8 单测/实机：全套/缺件；水下/出水；火焰/岩浆；近战/爆炸/弹幕击退。

## 4. 妖精典籍与镇魂

- [x] 4.1 新增 `data/gensokyou/loot_table/entities/fairy.json`：
  1 记忆残页必掉、10% P 点、10% B 点。
- [x] 4.2 确认众生典籍可收录 `gensokyou:fairy`（不被 boss/黑名单/NPC 规则排除）；
  大妖精仍排除。
- [x] 4.3 镇魂返照对满 20 只妖精的典籍按该表掷骰；至少产出 1 记忆残页。
- [x] 4.4 确认实际击杀小妖精不因新表双掉。
- [x] 4.5 单测：满 20 妖精典籍被识别为有效 species；空表回归测试改为非空断言。

## 5. 指导书条目

- [x] 5.1 `items` 章：人鱼鳞盔甲、龙鳞盔甲各一条（4 件、效果、5/8/7/4 配方）。
- [x] 5.2 `fantasy_materials` / `items`：灵鱼 + 海豚恩惠药水条目（长效、月砂）。
- [x] 5.3 `items`：P 点 / B 点条目（8P→1B、符卡 1/2/2）。
- [x] 5.4 `monsters` 或 `codex_of_beings`：妖精可收录、镇魂产纸/P/B。
- [x] 5.5 新增文案只维护 `zh_cn`；无裸 id、无工程占位语；`lang_audit` 不新增缺键。

## 6. 验证

- [x] 6.1 JSON 解析：`bpoint.json`、`sunako_mod_potions.json`、`zaohua_circle.json`、
  `loot_table/entities/fairy.json`。
- [x] 6.2 `openspec validate add-early-material-uses --strict` 通过。
- [x] 6.3 实机：8 P → 1 B；B 点卡配方；灵鱼药水；两套甲全套/缺件；20 妖精镇魂。
- [x] 6.4 平衡观察：龙鳞全免 + 100% 抗击退是否让 BOSS 战站桩；
  过强优先削抗击退。
