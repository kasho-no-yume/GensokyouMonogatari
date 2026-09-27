## MODIFIED Requirements

### Requirement: 弹幕主武器框采用一阶仪式中门槛
系统 SHALL 移除 `danmaku_weapon` 的普通工作台配方，并仅通过源初造化之仪配方 `zaohua_danmaku_weapon_frame` 产出主武器框。该配方 SHALL 使用 `mode:"activation"`、`match:"max"`、`minTier:1`、`spCost:20000`，消耗 `ritual_stone_1×1 + spirit_iron×2 + minecraft:netherite_ingot×1 + gensokyou:ppoint×4`，产出 `gensokyou:danmaku_weapon×1`。配方 SHALL NOT 校验玩家维度访问历史或仪式运行地点。

该配方 MUST NOT 包含任何 T2 及以上材料：`star_silver`（星银）原为本配方的第五项材料，因其属 T2 材料带而须移除。T2 材料带的唯一钥匙是 `sukima_fragment`，若入口级配方以 T2 材料为门，则「获得弹幕武器」将被锁在「击败 BOSS」之后，与 BOSS 需要弹幕武器对抗形成倒挂死锁。

该配方 MUST NOT 以 `spellcard_star` 或 `broken_spell_card_star` 为原料：符卡之星属于符卡一系，若武器本体也卡在 BOSS 掉落之后，则「无武器 → 打不过 BOSS → 无星 → 无武器」同样成环。武器的入口级材料 SHALL 全部可经 0 阶源初造化与金山彦命锻造在击败 BOSS 之前取得。

#### Scenario: 零阶造化不能铸造武器框
- **WHEN** 玩家在 0 阶源初造化之仪摆齐全部武器框原料并触发合成
- **THEN** 配方因 `minTier:1` 不匹配而拒绝执行，不消耗灵力或原料，不产出武器框

#### Scenario: 一阶造化可铸造武器框
- **WHEN** 玩家在 1 阶及以上源初造化之仪摆齐全部武器框原料且灵力足额并触发合成
- **THEN** 系统消耗 20,000 灵力与一份指定原料，完成合成并产出一个弹幕主武器

#### Scenario: 不要求下界历史或现场运行
- **WHEN** 满足配方的一阶源初造化之仪位于主世界、下界或幻想乡
- **THEN** 仪式按相同结构等级、原料和灵力规则执行，不因维度或运行地点拒绝配方

#### Scenario: 不打 BOSS 也能造出武器
- **WHEN** 玩家已建成 1 阶源初造化之仪与金山彦命锻造、取得下界合金，且未击杀任何 BOSS、亦未取得任何 T2 材料
- **THEN** 仍可造出主武器，MUST NOT 需要任何星类材料或 T2 材料

#### Scenario: 入口配方不含高阶材料
- **WHEN** 审查 `zaohua_danmaku_weapon_frame` 的全部原料
- **THEN** 每一种材料均可在未击败任何 BOSS 的前提下取得，MUST NOT 包含 T2 及以上材料
