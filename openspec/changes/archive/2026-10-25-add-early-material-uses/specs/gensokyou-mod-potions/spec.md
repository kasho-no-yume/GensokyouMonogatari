## ADDED Requirements

### Requirement: 灵鱼海豚恩惠药水

系统 SHALL 注册 mod 药水 `gensokyou:dolphins_grace`，其效果为原版
`MobEffects.DOLPHINS_GRACE`（海豚的恩惠，单级、无强效档）。

该药水 SHALL 以 `gensokyou:spirit_fish` 为试剂、`gensokyou:sanzu_flask`
（或以其为基液的粗制冥汤）为基液，在素盏呜尊酿药之仪上酿出。

因为原版海豚恩惠没有强效档，本药水 SHALL **只提供长效档**，MUST NOT 提供强效档，
MUST NOT 单独提供短基础档成品。长效档 SHALL 按既有 mod 药水规则消耗 `gensokyou:moon_sand`。

#### Scenario: 灵鱼酿出长效海豚恩惠

- **WHEN** 玩家在素盏呜尊酿药之仪上以灵鱼为试剂、冥河瓶为基液完成酿造
- **THEN** 获得长效档 `gensokyou:dolphins_grace` 药水

#### Scenario: 无强效档

- **WHEN** 玩家尝试用瓷器对该药水做强效转化
- **THEN** 不产出任何强效档条目，药水保持长效档

#### Scenario: 长效消耗月砂

- **WHEN** 玩家把海豚恩惠转为长效档
- **THEN** 消耗 1 个 `gensokyou:moon_sand`；不足时不转化

#### Scenario: 原版基液不走 mod 线

- **WHEN** 玩家只用原版水瓶与灵鱼酿造
- **THEN** 不产出 `gensokyou:dolphins_grace`
