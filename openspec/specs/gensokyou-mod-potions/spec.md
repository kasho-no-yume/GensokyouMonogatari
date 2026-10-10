# gensokyou-mod-potions Specification

## Purpose
TBD - created by archiving change add-gensokyou-material-uses. Update Purpose after archive.
## Requirements
### Requirement: 粗制冥汤（炼药台路径的 mod 版 awkward potion）
系统 SHALL 注册 mod potion `gensokyou:crude_sanzu_potion`（粗制冥汤）。它 SHALL 仅能由炼药台以 `minecraft:awkward_potion` + `gensokyou:sanzu_flask` 酿造得出，并 SHALL 作为全部 mod 药水的共同基液。

#### Scenario: 炼药台产出粗制冥汤
- **WHEN** 玩家在炼药台以 `gensokyou:sanzu_flask` 为材料酿造 `minecraft:awkward_potion`
- **THEN** 得到 `gensokyou:crude_sanzu_potion`，`sanzu_flask` 被消耗

#### Scenario: 无冥水不进 mod 线
- **WHEN** 玩家在炼药台尝试用 `minecraft:water_bottle` 或任何不含 `sanzu_flask` 的配方酿造
- **THEN** 无法得到任何 mod 药水，包括粗制冥汤

### Requirement: mod 药水及其档位
系统 SHALL 以 `crude_sanzu_potion` 为基液，经四种 mod 试剂（`gensokyou:spirit_herb`、`gensokyou:magic_mushroom`、`gensokyou:gentian`、`gensokyou:higanbana`）分别炼出四种 mod 药水：回灵汤、灵视药水、灵触药水、彼岸花毒。mod 药水 SHALL NOT 由任何原版试剂在原版 awkward potion 上炼出。

#### Scenario: 四种 mod 药水
- **WHEN** 玩家在炼药台分别以上述四种 mod 试剂酿造 `gensokyou:crude_sanzu_potion`
- **THEN** 依次得到回灵汤、灵视药水、灵触药水、彼岸花毒

#### Scenario: 原版试剂炼不出 mod 药水
- **WHEN** 玩家在炼药台用任意原版材料（如 blaze_powder、sugar）酿造 `minecraft:awkward_potion`
- **THEN** 只产出原版药水，不产出任何 mod 药水

### Requirement: 强效与长效档的素材门槛
mod 药水的长效档 SHALL 需要消耗 `gensokyou:moon_sand`（月砂）作为触媒，强效档 SHALL 需要消耗 `gensokyou:porcelain`（瓷器）作为封装耗材；每一次档位转化 SHALL 消耗恰好 1 个对应素材。缺少对应素材时相应档位 SHALL NOT 可炼制。

档位 SHALL 按效果逐个定义，MUST NOT 强求每个效果三档齐全：回灵汤为瞬发效果、时长无意义，MUST NOT 有长效档（与原版瞬间治疗只有 I/II 一致）；灵视药水与彼岸花毒的品质不承载任何机制差异、MUST NOT 有强效档（等级恒为 I，与原版夜视一致）；灵触药水三档齐全。

#### Scenario: 长效档需月砂
- **WHEN** 玩家拥有长效档的 mod 药水并对其实用红石（原版触媒）但未持有 `gensokyou:moon_sand`
- **THEN** 无法炼成长效档；持有月砂时转化成功并消耗 1 个月砂

#### Scenario: 强效档需瓷器
- **WHEN** 玩家拥有强效档的 mod 药水并对其实用萤石（原版触媒）但未持有 `gensokyou:porcelain`
- **THEN** 无法炼成强效档；持有瓷器时转化成功并消耗 1 个瓷器

#### Scenario: 无强效档的效果不可升 II
- **WHEN** 玩家对灵视药水或彼岸花毒使用瓷器
- **THEN** 不产出任何强效档条目，等级仍为 I

### Requirement: 瓷器由煅炉精炼而非原版熔炉
`gensokyou:porcelain` SHALL 只能经金山彦命煅炉的专属精炼规则由 `gensokyou:porcelain_clay` 产出，MUST NOT 由原版熔炉、高炉或烟熏炉炼出。

#### Scenario: 煅炉出瓷器
- **WHEN** 玩家在成型金山彦命煅炉的祭品台摆放 `gensokyou:porcelain_clay` 并满足该专属规则
- **THEN** 产出 `gensokyou:porcelain`

#### Scenario: 原版炉炼不出
- **WHEN** 玩家将 `gensokyou:porcelain_clay` 放入原版熔炉、高炉或烟熏炉
- **THEN** 不产出 `gensokyou:porcelain`

### Requirement: 常世木灵力核心
`gensokyou:spirit_core_3` / `spirit_core_4` / `spirit_core_5` SHALL 可由源初造化之仪炼制，且每条配方 SHALL 以 `gensokyou:eternal_wood` 为主材之一，并 SHALL 含至少一件其它幻想乡素材。

#### Scenario: 三档核心可炼制
- **WHEN** 玩家在源初造化之仪按配方摆放含 `gensokyou:eternal_wood` 的原料并满足 `minTier` 与灵力条件
- **THEN** 产出对应的 `gensokyou:spirit_core_3` / `_4` / `_5`

#### Scenario: 常世木不是唯一 mod 料
- **WHEN** 审查上述三条配方
- **THEN** 每条配方除 `eternal_wood` 外还含至少一件别的幻想乡素材，满足 `gensokyo-materials` 的独有品规则

### Requirement: mod 药水效果
系统 SHALL 注册四个 mod 药水效果：瞬发灵力回复、灵视、灵触、彼岸花毒。四者 SHALL NOT 提供任何形式的伤害抗性提升（彼岸花毒的 80% 免伤例外见下条），且 SHALL NOT 与原版药水效果同名或同形。灵视与彼岸花毒的等级 SHALL 恒为 I（无强效档），彼岸花毒的死亡契约见下条。

#### Scenario: 瞬发灵力回复
- **WHEN** 玩家饮用或被喷溅到回灵汤
- **THEN** 玩家在生效瞬间一次性回复「当前最大灵力 × 配置比例 × (品质+1)」的灵力（I 阶 10%、II 阶 20%），不产生持续回复、不显示持续时间；灵力已满时为 no-op

#### Scenario: 灵视显形
- **WHEN** 玩家持有灵视效果
- **THEN** 服务端视野距离内的实体以绿色发光轮廓渲染且可穿墙看见，效果结束后轮廓消失

#### Scenario: 灵触延伸
- **WHEN** 玩家持有灵触效果
- **THEN** 玩家的方块交互距离与实体交互距离按配置提升，效果结束后恢复

### Requirement: 彼岸花毒契约
彼岸花毒 SHALL 使持有者在效果持续期间受到 80% 伤害减免（等值抗性提升 IV），并 SHALL NOT 减免虚空伤害与 `/kill`。效果自然结束或被牛奶洗掉时，持有者 SHALL 立即死亡，且该次死亡 SHALL NOT 被不死图腾阻挡。

#### Scenario: 80% 免伤
- **WHEN** 持有彼岸花毒的玩家受到一次本应造成 10 点伤害的近战攻击
- **THEN** 实际受到 2 点伤害

#### Scenario: 不挡虚空
- **WHEN** 持有彼岸花毒的玩家坠入虚空
- **THEN** 玩家正常死亡，免伤不生效

#### Scenario: 到期即死
- **WHEN** 彼岸花毒效果自然结束
- **THEN** 持有者立即死亡

#### Scenario: 洗掉即死
- **WHEN** 玩家饮用牛奶解除彼岸花毒
- **THEN** 玩家立即死亡，等同效果自然结束

#### Scenario: 图腾不阻挡
- **WHEN** 彼岸花毒到期触发死亡且玩家持有不死图腾
- **THEN** 图腾不触发，玩家死亡

