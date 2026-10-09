## MODIFIED Requirements

### Requirement: 幻想乡素材目录（T1~T2）
系统 SHALL 注册幻想乡独有素材物品，分属矿产 / 土产 / 木材 / 海产 / 植物五类，每类 SHALL 具备中英语言键、物品模型与贴图，且 MUST 与原版物品名称与外形可辨：

- 矿产（原矿，大山祇神之座产出）：`cinnabar`（辰砂，T1）、`spirit_iron_ore`（灵铁矿，T1）、`star_silver_ore`（星银矿，T2）、`oni_stone`（鬼石，T2）
- 矿产（成品金属，金山彦命煅炉炼出）：`spirit_iron`（灵铁）、`star_silver`（星银）
- 矿产（精炼产物，金山彦命煅炉炼出）：`porcelain`（瓷器）
- 土产：`spirit_soil`（灵土，T1）、`porcelain_clay`（瓷土，T1）、`higan_soil`（彼岸土，T1）、`moon_sand`（月砂，T2）
- 木材：`sacred_wood`（神木，T1）、`magic_wood`（魔法木，T1）、`eternal_wood`（常世木，T2）
- 海产：`sanzu_flask`（瓶装三途川水，**即冥水**，T1）、`spirit_fish`（灵鱼，T1）、`mermaid_scale`（人鱼鳞，T1）、`tide_crystal`（潮汐晶，T2）、`dragon_scale`（龙鳞，T2）
- 植物（**由农业产出**）：`spirit_herb`（灵草，T1）、`gentian`（龙胆，T1）、`higanbana`（彼岸花，T1）、`magic_mushroom`（魔法菇，T2）
- 植物种子（**由茅野姬神花亭产出**）：`spirit_herb_seeds`、`gentian_seeds`、`higanbana_seeds`、`magic_mushroom_spores`
- mod potion（mod 专属药水线）：`crude_sanzu_potion`（粗制冥汤，基液）、`reiki_recovery`（回灵汤）、`spiritual_sight`（灵视药水）、`spirit_touch`（灵触药水）、`higanbana_poison`（彼岸花毒）

`sanzu_flask` 是 mod 专属药水线的独占基液，SHALL 直接以物品形态被消耗，MUST NOT 存在"开启为冥水"的交互或配方。上述新增交付物均无原版对应物，MUST NOT 违反素材唯一性红线。

#### Scenario: 注册与本地化齐备
- **WHEN** 完成注册后打开本 mod 创造标签并切换中英文
- **THEN** 全部素材（含新增交付物）可见、图标正常渲染、名称按语言本地化且无裸 id

#### Scenario: 与原版可辨
- **WHEN** 玩家同时持有任一新素材与其最相近的原版物品
- **THEN** 两者名称与图标均可区分，不产生「这是不是原版 X」的歧义

#### Scenario: 冥水无开启产物
- **WHEN** 审查 `sanzu_flask` 的相关行为
- **THEN** 不存在"开启为冥水"的交互或配方，它直接作为 mod 药水基液被消耗

### Requirement: 材料—仪式来源映射
五类素材 SHALL 各由同类的既有资源仪式经 `gensokyou` 条件池产出：矿产→大山祇神之座（镐）、土产→埴山姬神之壤（锹）、木材→久久能智神庭（斧）、海产→绵津见神之藏（钓）、植物→茅野姬神花亭（锄）。素材在池中的分层 SHALL 与其阶级一致（T1 材属低阶带、T2 材属中阶带）。

植物类 SHALL 另有一条**农业产出路径**：茅野姬神花亭负责供给种子，植物本体由对应作物成熟产出。土产类 SHALL 有两条派生路径：瓷土经金山彦命煅炉精炼为瓷器、`spirit_soil` 可被锄为 `spirit_soil_farmland`。

#### Scenario: 按类归位
- **WHEN** 在久久能智神庭上解锁对应材料带并结算
- **THEN** 抽取结果只含木材类素材，不出现矿产 / 海产等其他类

#### Scenario: 植物类双来源
- **WHEN** 玩家需要任意一种植物本体
- **THEN** 唯一途径是种植对应种子并等待成熟；茅野姬神花亭不再直出植物本体

#### Scenario: 瓷土派生
- **WHEN** 玩家持有 `gensokyou:porcelain_clay`
- **THEN** 可在金山彦命煅炉获得瓷器，而原版熔炉、高炉、烟熏炉 SHALL NOT 产出瓷器
