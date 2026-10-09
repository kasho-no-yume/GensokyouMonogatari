## MODIFIED Requirements

### Requirement: 示例配方与本地化

系统 SHALL 交付一仪式一文件的配方数据 `data/gensokyou/ritual_recipes/zaohua_circle.json`（顶层 `pattern` + `recipes[]`，每条含 `name`/`mode`/`match:"max"`/`minTier`）：①`zaohua_stone_t1`：4×diamond + 2×ritual_stone_0 + 2×refined_cinnabar → 1×ritual_stone_1（spCost 2,000，`minTier:0`，`Σcount=8` 恰为 0 阶源初造化的祭品台数）；②`zaohua_spellcard_star`：8×broken_spell_card_star → 1×spellcard_star（spCost 8,000，`minTier:0`）。spCost 数值为可调占位（改 JSON 即生效，MUST NOT 硬编码进 Java）。仪式名、按钮文案、状态与失败消息 SHALL 备齐中英语言键。配方目录展示归 JEI，仪式 GUI MUST NOT 罗列可用配方。

`zaohua_stone_t1` 的原料总量 MUST NOT 超过 0 阶源的祭品台数（8 台）；原 `4×ritual_stone_0 + 4×refined_cinnabar` 组合造成的超量 MUST 被消除。

#### Scenario: 0 阶可造 1 阶仪式石

- **WHEN** 0 阶源初造化的 8 个祭品台各放 1 件（4 钻石 + 2 仪式石 0 + 2 精炼辰砂）且灵力足额，触发合成
- **THEN** 爆炸后掉落 1 个仪式石 1

#### Scenario: max 匹配余料不动

- **WHEN** 台面为 4a+3b+5c+1d（a/b/c/d 对应两条示例配方的实际物品混合）时触发
- **THEN** 仅按可命中的最大匹配配方消耗对应件数，其余物品留在原台面
