## MODIFIED Requirements

### Requirement: 示例配方与本地化

系统 SHALL 交付一仪式一文件的配方数据 `data/gensokyou/ritual_recipes/zaohua_circle.json`（顶层 `pattern` + `recipes[]`，每条含 `name`/`mode`/`match:"max"`/`minTier`）：

1. `zaohua_stone_t1`：`1×diamond + 2×ritual_stone_0 + 1×cinnabar + 4×blackstone → 1×ritual_stone_1`
   （spCost 2,000，`minTier:0`，`Σcount=8` 恰为 0 阶源初造化的祭品台数）。
2. `zaohua_stone_t2`：`2×ritual_stone_1 + 4×spirit_iron + 1×tide_crystal + 1×spirit_herb + 1×gentian + 1×higanbana + 6×purpur_block → 4×ritual_stone_2`
   （spCost 20,000，`minTier:1`，`Σcount=16` 恰为 1 阶源初造化的祭品台数）。
3. `zaohua_spellcard_star`：`8×broken_spell_card_star → 1×spellcard_star`
   （spCost 8,000，`minTier:0`）。

spCost 数值为可调占位（改 JSON 即生效，MUST NOT 硬编码进 Java）。仪式名、按钮文案、状态与失败消息 SHALL 备齐中英语言键。配方目录展示归 JEI，仪式 GUI MUST NOT 罗列可用配方。

`zaohua_stone_t1` 的原料总量 MUST NOT 超过 0 阶源初造化的 8 台；`zaohua_stone_t2` 的原料总量 MUST NOT 超过 1 阶源初造化的 16 台。二者 MUST NOT 以精炼辰砂/星银作为 1/2 阶石的门，以避免灵炭/蓝的配方闭环。

#### Scenario: 0 阶可造 1 阶仪式石

- **WHEN** 0 阶源初造化的 8 个祭品台摆入 1 钻石 + 2 仪式石 0 + 1 辰砂 + 4 黑石且灵力足额，触发合成
- **THEN** 消耗全部原料并产出 1 个仪式石 1，MUST NOT 要求精炼辰砂

#### Scenario: 1 阶可造 2 阶仪式石

- **WHEN** 1 阶源初造化的 16 个祭品台摆入 2 仪式石 1 + 4 灵铁 + 1 潮汐晶 + 1 灵草 + 1 龙胆 + 1 彼岸花 + 6 紫珀块且灵力足额，触发合成
- **THEN** 消耗全部原料并产出 4 个仪式石 2

#### Scenario: max 匹配余料不动

- **WHEN** 台面为 4a+3b+5c+1d（a/b/c/d 对应两条示例配方的实际物品混合）时触发
- **THEN** 仅按可命中的最大匹配配方消耗对应件数，其余物品留在原台面
