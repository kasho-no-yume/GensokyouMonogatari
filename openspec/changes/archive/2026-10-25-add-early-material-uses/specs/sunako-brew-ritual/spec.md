## ADDED Requirements

### Requirement: 灵鱼试剂

素盏呜尊酿药之仪 SHALL 支持 `gensokyou:spirit_fish` 作为 mod 试剂，
映射到 `gensokyou:dolphins_grace` 长效档；该条目 SHALL 写入
`data/gensokyou/brew_recipes/sunako_mod_potions.json`，与其他 mod 试剂同文件。

灵鱼试剂 SHALL NOT 改变既有 mod 药水条目的映射；缺失灵鱼时该条目自然不可酿造。
酿造 SHALL 仍以 `gensokyou:sanzu_flask`（或粗制冥汤）为基液，MUST NOT 接受原版水瓶。

#### Scenario: 灵鱼条目加载

- **WHEN** 服务器加载酿造数据
- **THEN** `sunako_mod_potions.json` 中存在 reagent=`gensokyou:spirit_fish`、
  potion=`gensokyou:dolphins_grace` 的条目

#### Scenario: 无灵鱼不可酿

- **WHEN** 祭品台没有灵鱼且玩家尝试酿海豚恩惠
- **THEN** 不产出该药水，不消耗其它试剂

#### Scenario: 原版水瓶无效

- **WHEN** 基液为 `minecraft:water_bottle` 且试剂为灵鱼
- **THEN** 不产出 mod 药水
