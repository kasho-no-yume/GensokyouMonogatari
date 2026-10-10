## ADDED Requirements

### Requirement: 瓷土专属精炼规则
金山彦命煅炉 SHALL 提供一条专属规则：`gensokyou:porcelain_clay` → `gensokyou:porcelain`（瓷器）×1，与矿石/灵炭比例规则同级，优先于运行时 `smelting` / `blasting` / `smoking` 通用配方。该规则 SHALL NOT 进入任何原版烹饪配方目录。

#### Scenario: 煅炉出瓷器
- **WHEN** 成型金山彦命煅炉的祭品台摆放 `gensokyou:porcelain_clay` 且灵力足够
- **THEN** 建立一条专属任务并在完成时产出 `gensokyou:porcelain`

#### Scenario: 原版炉炼不出瓷器
- **WHEN** 玩家将 `gensokyou:porcelain_clay` 放入原版熔炉、高炉或烟熏炉
- **THEN** 不产出 `gensokyou:porcelain`，也不产出任何本 mod 的精炼产物

#### Scenario: 专属规则压过通用配方
- **WHEN** `gensokyou:porcelain_clay` 同时匹配某条运行时 `smelting` 配方
- **THEN** 煅炉使用瓷器专属规则，不建立通用单输入任务

### Requirement: 煅炉专属规则清单的扩展边界
`gensokyou:oni_stone` 与未列出物品 SHALL NOT 获得任何新专属煅炉规则；本条的瓷器规则为 `kanayamahiko-smelting` 「Gensokyo special refining rules」需求所列规则之外唯一的增量。

#### Scenario: 鬼石无精炼规则
- **WHEN** 祭品台摆放 `gensokyou:oni_stone`
- **THEN** 不建立任何专属任务，保持闲置（除非它匹配某条通用配方）
