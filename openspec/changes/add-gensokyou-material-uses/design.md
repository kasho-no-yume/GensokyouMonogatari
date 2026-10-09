## Context

本仓库是 NeoForge 21.1 / MC 1.21.1 的东方 Project 同人 mod「幻想乡」。素材产线已经闭环在生产侧：`gensokyo-materials` spec 定义 22 种素材分五类（矿产/土产/木材/海产/植物），五座资源仪式按类别产出（大山祇=镐、埴山姬=锹、久久能智=斧、绵津见=钓、茅野姬=锄），金山彦命煅炉把粗矿炼成成品金属，造化之仪把成品金属/水晶/符纸装配成弹幕武器与灵力核心。

但消耗侧是断的。已有配方审计结果：**有用途的素材只有 9 种**——精辰砂、灵铁、星银、潮汐晶、神木、灵草、符纸、灵炭、碎符卡星；其余 13 种零出口。

结构性约束（来自现有 spec 与代码，均不可绕过）：

- **素材唯一性红线**（`gensokyo-materials`）：原版可获得之物不得另立为重复的 mod 素材。种子/药水/装备均为原版没有的新物，不冲突。
- **配方构成**：入口层以凡材为主；mod 独有品须含 ≥1 件 mod 料；阶级物品须含对应阶级 mod 材。
- **冶炼仪式独占**：mod 素材不得有任何原版熔炼/烟熏/高炉配方，精炼职责只在金山彦命煅炉。瓷器作为新物品应走煅炉规则而非原版熔炉。
- **五类素材各由同类仪式产出**：本变更把植物类从"仪式直出植物"改为"仪式出种子、植物由农业产出"，仍满足"植物类由茅野姬产出"（只是产出物下沉一个 tier）。
- `RitualBrewRule` 已带 `long_potion` / `strong_potion` 显式兄弟指针、`excludedEffects`、`extendWithoutLong` / `amplifyWithoutStrong`，注释明确这是"给无命名约定的 mod 药水准备的"——mod 药水线的数据载体已经就位。
- 玩家灵力池是 `SpiritPowerData(current, max, temperLevel, regenBuffer, spiritDamage, ...)`，阶级 0 时池恒 0/0；`spirit_core_0..5` 已注册但只有 `spirit_core_0.json` 一条工作台配方。
- `LaevateinTier` 是唯一的 mod 工具 tier 先例（1561 耐久 / 速度 9 / 攻击 7 / 下界合金级 / 附魔值 18）。

## Goals / Non-Goals

**Goals:**
- 给 13 种零出口素材建立消耗出口，让「五仪式产出 → 农业/炼药/装备/工具」形成新闭环。
- 把四种植物从"零收益花草"改造成有种子、有生长、可加速的真作物，并让灵土在这条线里承担明确的加速职能。
- 建立与原版药水彻底隔离的 mod 专属药水线，并把 `sanzu_flask` 正名为该线的独占基液。
- 提供两套可辨识、有特色但不破坏 balance 的装备阶梯（灵铁 / 星银），补上"回复速率"这块当前明显偏低的短板。
- 提供两个面向大型仪式建筑施工的辅助道具。

**Non-Goals:**
- 不做鬼族/抗性类内容（用户明确否决，且声明以后也不做）。
- 不动附魔仪式（用户明确否决）。
- 不做任何形式的丹幕减伤装备（用户要求：弹幕防御不得过高）。
- 不给 mod 素材添加任何原版熔炉/高炉/烟熏配方（既有红线）。
- 鬼石与龙鳞本轮保留不动（仍为零出口，作为后续 BOSS/符卡变更的预留）。
- 不做 spell card 新增、不做降神变身（属阶段 D）。

## Decisions

### D1. 灵土做成「灵土 + 灵土耕地」两块，而不是改 `spirit_soil` 本体

`spirit_soil` 当前是普通 `Block`（`soilProperties()`：强度 0.6、GRAVEL 音效、DIRT 色），是"能走的土色方块"，被仪式 pattern 与建筑当作装饰地基使用。若把它改成 `FarmBlock` 会带来外形变 15/16 格高、可被踩塌、需邻水三重行为变化，破坏既有用法。

**决定**：保留 `spirit_soil` 原状，新增 `spirit_soil_farmland`，由锄头右键 `spirit_soil` 得到（掉落回 `spirit_soil`）。

- 替代方案：让 mod 作物直接覆写 `mayPlaceOn` 收录 `spirit_soil`（无耕地、无视觉反馈、无法表达"耕种"动作）——否决，玩家感受不到"我在种田"。

### D2. mod 作物覆写 `mayPlaceOn`，不碰原版 `CropBlock`

原版 `CropBlock` 的基底判定需程序侧确认为 `Blocks.FARMLAND` 硬编码还是 tag。**若为硬编码**，做法是四个 mod 作物（`extends CropBlock`）覆写 `mayPlaceOn` 返回 `farmland || spirit_soil_farmland`。

- 替代方案 A：mixin `CropBlock` 加入 mod 土 tag——否决，风险与维护成本高。
- 替代方案 B：让 `spirit_soil_farmland` 继承 `FarmBlock` 并只被 mod 作物种植（原版作物仍只认 `Blocks.FARMLAND`）——**这是已选方案的实际形态**，`FarmBlock` 自带 moisture 状态机与踏塌反馈，我们只借它的"是耕地"身份。
- 代价：原版作物无法在灵土上加速。可接受——mod 农业线本就该独立。

### D3. 灵土加速用随机刻追加生长值，不用右键催熟

`spirit_soil_farmland` 覆写 `randomTick`：以远低于骨粉的概率给上方 `CropBlock` 追加生长值（等价于额外一次 `growthSystem` 推进）。

- 用户明确否决右键催熟；随机刻让"种植在灵土上自然更快成熟"，无需玩家操作。
- 概率与时序进 `GensokyouConfig`，便于平衡调整。

### D4. 彼岸土作为两个作物的专属基质，并叠一层额外加速

`higanbana_crop` 与 `magic_mushroom_crop` 的 `mayPlaceOn` 额外收录 `higan_soil`（彼岸土本身就是 `soilProperties()` 的土色方块，可直接承托）。`spirit_soil_farmland` 的加速判定读基底：彼岸土给更高加速倍率。

- 这让 T1 土产"彼岸土"与 T2 植物"魔法菇"形成跨仪式依赖（埴山姬出土、茅野姬出孢子），给两座仪式一个交叉理由。

### D5. 四条植物种子化，仪式改出种子

新增 4 种子物品 + 4 `CropBlock`。**`kaya_no_hime_circle.json` 的 `gensokyou_low`/`gensokyou_high` 全部改为种子**，植物本体改由作物成熟掉落。

- 掉落规则：成熟 → 1× 植物（必然）+ 1~2× 种子（时运影响）；未成熟破坏 → 仅 1× 种子。
- 这是**存档不兼容的数据变更**：旧存档里若已有 plant 物品仍可作试剂（配方不变），但仪式不再直出。

### D6. mod 药水线沿用少名渡汤之仪既有机制，炼药台走两步酿造

**关键事实（读 spec 后修正）**：`sunako-brew-ritual` 的既有形态已经是「祭品台放 `sanzu_flask` 为唯一有效原料 + 核心 GUI 试剂槽决定药水种类 + **产物原位替换**台面」。"瓶装三途川水作为 mod 专属基液走少名渡汤仪式"这件事**已经实现了**，不需要新机制。

**决定 A（仪式路径）**：`brew_recipes/sunako_circle.json` 直接增补 4 条 mod 试剂条目（灵草/魔法菇/龙胆/彼岸花 → 4 种 mod potion），并新增"结构内放置 `magic_wood` 才接受 mod 试剂"的构件门槛。产物仍走原位替换，`RitualBrewRule` 的 `long_potion` / `strong_potion` 字段天然承载 mod 药水的兄弟指针。

**决定 B（炼药台路径）**：两步。第一步 `minecraft:awkward_potion + sanzu_flask → crude_sanzu_potion`（粗制冥汤，mod 版 awkward potion）；第二步 `crude_sanzu_potion + mod 植物 → 4 种 mod 药水`。长效档额外耗 `moon_sand`、强效档额外耗 `porcelain`，形成"原版阶梯之上的一层 mod 阶梯"。

- **为什么必须两步**：`sanzu_flask` 是普通 `Item`，不是 potion container，无法作酿造台水源位（`PotionBrewing` 水源位只认 potion container）。两步法让 `awkward_potion` 老老实实当水源位，同时把"没有冥水就进不了 mod 线"钉死。
- **同时满足用户两句约束**：**"从地狱疣的粗制药水做出来"**（水源位即 `awkward_potion`）+ **"瓶装三途川水是 mod 专属基液"**（没有它永远进不了 mod 线；原版水瓶与原版 awkward potion 都炼不出 mod 药水，无法绕开）。
- 替代方案（否决）：把 `sanzu_flask` 注册为 potion container 当水源位——会让它持有原版药水，破坏"mod 独占"语义，且需重写容器渲染。
- 仪式路径不产 `crude_sanzu_potion`（一步直达成品），是对耐心玩家的捷径，与炼药台慢路径形成节奏差。

### D7. 魔法木作为 mod 药水炼制的构件门槛

用户否决改附魔仪式，但 mod 药水需要一道门槛，否则原版 reagents 一条路径就直接贯通 mod 线。

**决定**：少名渡汤之仪的试剂槽 SHALL 仅在仪式结构内放置 `gensokyou:magic_wood` 原木方块时才接受 mod 试剂（4 种植物）；未放置时只接受原版试剂，行为与现状完全一致。该构件 SHALL NOT 被消耗。

- 实现上需要"结构内方块构件扫描"。先例是 `ToolSacrificeBehavior.countGuideBooks` / `countSukimaFragments`，但它们扫的是祭品台上的**物品**；方块扫描尚无先例。
- **退路**：魔法木原木是方块物品，可以放上祭品台——若方块扫描无现成路径，退化为"祭品台放 `magic_wood`"。这条退路是通的，不构成阻塞。

### D8. 装备两阶梯：特色在耐久与获取，不在防御

以 `LaevateinTier` 为锚点：

| | 耐久 | 速度 | 攻击 | 采集等级 | 附魔值 |
|---|---|---|---|---|---|
| 灵铁 | 512 | 8.0 | +3 | 钻石级 | 10 |
| 星银 | 2048 | 12.0 | +4 | 下界合金级 | 18 |

- 灵铁全套工具：挖"常见方块"零耐久。常见 = 石/土/木/叶/沙/植物的白名单（与原版 `mineable` tag 取交），挖矿石照常掉耐久。这给了它"施工工具"的定位，与星银的"采矿工具"区分。
- 星银全套工具：自带精准采集（不靠附魔）+ 挖 mod 原矿概率额外掉落 1 个原矿方块。
- 灵铁铠：防御≈钻石（20 点）+ 灵力自然回复 +50%。
- 星银铠：防御略高于下界合金 + 灵力自然回复 +50% + 少量 `spirit_damage`（约 +8%）。
- **明确不给任何丹幕/灵力减伤**。星银定位"高攻中防"。
- 替代方案：把回灵做成药水而非盔甲——否决，用户明确要盔甲补回复短板，且药水是爆发、盔甲是续航，两者互补。

### D9. 灵力引爆器 = 实体 + GUI + 自定义爆炸，是本变更最大单点

- 实体：`SpiritBombEntity`，非 `PrimedTnt`，自带 `renderState` 形态。右键地面放置，再右键开配置 GUI。
- GUI 参数：起爆时间 0.1~600s、强度 1.0~12.0、半径 1~24。启动按钮实时显示估算灵力，玩家 `SpiritPowerData.current` 不足则置灰。
- 计费 `cost = BASE × (强度/4)² × (半径/6)`，`BASE` 默认 20000，进 `GensokyouConfig`。
- 爆炸：以 `ExplosionInteraction.BLOCK` 语义或自定义 `Explosion` 子类遍历受影响方块全数 `dropResources`，实现"掉落所有因爆炸炸毁的方块"。
- **硬上限**：强度/半径上限 + 掉落产物实体数上限（建议合并为单实体或设 1000 上限），否则大半径会刷崩服务器。
- 未启动可破坏收回；已启动不可收回。实体自行计时，不依赖玩家在线。
- 美术：沉睡暗淡 → 参数设定时符环自转 → 倒计时外圈符环依次点亮、内芯脉动随时限加快、颜色随强度青→紫→红 → 引爆瞬先内缩再炸开。占位贴图先走 `gen_tex.py`，正式特效后补。

### D11. mod 药水效果的实现取舍

- **灵视**：直接套原版 `glowing` + 队伍色（team color），穿墙可见是 glowing 自带能力，**零自定义渲染成本**。这是本变更性价比最高的一个效果。
- **持续灵力回复**：效果 `applyEffectTick` 内直写 `ModAttachments.get(ServerPlayer)` 的 `regenBuffer`/`current`，禁止在效果类里做客户端专属逻辑。
- **灵触**：走 `neoforge:block_interaction_range` / `neoforge:entity_interaction_range` 属性修饰（1.20.2+ 已有这两个属性），攻击距离延伸另做小 AOE 补丁。
- **彼岸花毒**：见 D12。

### D12. 彼岸花毒：80% 免伤契约，死亡路径绕开图腾

- 全程 80% 减伤（等值抗性提升 IV，**不采用全免**——Resistance V 全免是公认超标杆）。
- 不挡虚空、不挡 `/kill`；其余常规伤害全挡。
- 到期或被牛奶洗掉 → 立即死亡。
- 死亡路径：不走 `hurt()`（否则图腾在 `hurt()` 内触发），改用 `setHealth(0)` + `die(customDamageType)`。程序侧需实测验证；若做不到则退化为 `kill()`（用户已确认可接受）。

### D13. 剩余素材认领

| 素材 | 用途 | 落点 |
|---|---|---|
| 瓷土 | → 瓷器，mod 药水**强效档**必需封装耗材（每瓶 1 个） | 金山彦命煅炉规则 |
| 月砂 | mod 药水**长效档**必需触媒 | 炼药台 mix |
| 常世木 | `spirit_core_3/4/5` 主材 | `zaohua_circle.json` |
| 魔法木 | long/strong 档位构件（D7） | sunako 结构扫描 |
| 彼岸土 | 专属基质 + 额外加速（D4） | `mayPlaceOn` + `randomTick` |

## Risks / Trade-offs

- **[灵土加速依赖 `CropBlock` 基底判定]** → 程序侧第一步就确认原版判定形态；若为硬编码则走 D2 的 `FarmBlock` 路线，不引入 mixin。
- **[大半径全掉落爆炸刷爆 item entity]** → 强度/半径硬上限 + 掉落产物实体数上限，超限时吞掉多余产物并在日志告警。
- **[彼岸花毒"到期即死"被图腾救回]** → `setHealth(0)` + `die()` 路径；实测不过则退化 `kill()`。
- **[种子化是破坏性数据变更]** → 旧存档已有植物物品仍可作试剂（配方不变），只是仪式不再直出；`kaya_no_hime_circle.json` 变更需在 tasks 里显式标注存档兼容检查。
- **[新增约 33 条目，单变更过大]** → tasks 分两期：一期种子化+耕地+药水线（可验证闭环），二期装备工具+妙妙工具。验收门槛按一期先行。
- **[mod 药水效果与灵力池深度耦合]** → 4 个效果统一走 `MobEffect.applyEffectTick` + `ModAttachments.get(ServerPlayer)`，禁止在效果类里做客户端专属逻辑。
- **[魔法木作为方块构件无法放上祭品台]** → 先确认"结构内方块构件扫描"是否有现成路径（`countGuideBooks` 是扫物品的先例，方块扫描尚未有先例）；若无则退化为"祭品台放 `magic_wood` 原木物品"，退路通畅，不构成阻塞。

## Migration Plan

1. 程序侧先确认三项基底事实：`CropBlock.mayPlaceOn` 判定形态、`FarmBlock` 复用可行性、结构内方块构件扫描路径。
2. 一期落地：种子/作物/耕地 → `kaya_no_hime_circle.json` 数据变更 → mod potion 注册与两步酿造 → 4 效果 → 载具认领（瓷器/月砂/常世木）。
3. 编译 + 单机实测：种植闭环、灵土加速倍率、两条药水产出路径、long/strong 档位门槛、彼岸花毒死亡路径与图腾行为。
4. 二期落地：装备两阶梯 + 两个妙妙工具。
5. 全量过 `openspec validate --strict` 与 `gradle_task.ps1 build`。

**回滚**：数据文件（ritual_loot / brew_recipes / zaohua_circle / smelt_recipes）可用 git 单文件回退；注册项回退需同步删除 lang/模型/贴图，按条目反向清理。
