## Context

两个独立的小交付凑在一个变更里，都是"补数据缺口"：

1. **矿产下界池缺建材**。四个献祭仪式各有一张主题化的 `nether` 池（土产=灵魂沙/土、
   木材=绯红/诡异菌柄、植物=下界疣/根、矿产=netherrack/magma/石英/残骸）。黑石与玄武岩
   是"石头"，只属矿产仪式。`nether` 池是**加法池**（`RitualLootTable.buildPool`：不下沉
   commons 桶），解锁靠台上 3 个凋灵骷髅头、全工具档共用。
2. **两个工具无获取路径**。`gensokyou-utility-tools` 已定义 `landscaping_tool` 与
   `spirit_bomb` 的行为/配置，但 `data/gensokyou/recipe/` 与 `ritual_recipes/` 均无其条目。

`project.md` §5.1/素材规则：mod 素材必须原版不可得；原版物可作为"体积"由仪式产出
（如竹/樱花木）。黑石/玄武岩正是原版方块的体积产出，**不另立 mod 素材**。

## Goals / Non-Goals

**Goals:**
- 让矿产仪式稳定产出黑石与玄武岩两种建材。
- 给两个工具补上符合其定位的生存获取配方（筑基器前期、引爆器造化一阶）。

**Non-Goals:**
- 不改 `nether` 池的机制、不改工具行为/配置/数值。
- 不给黑石/玄武岩造 mod 素材或特殊用途（本次纯建材）。
- 不动其他三个献祭仪式的下界池。

## Decisions

### D1 黑石 / 玄武岩：权重各 4，全 6 档恒定

在 `oyamatsumi_circle.json` 每个工具的 `nether` 列表中追加
`["minecraft:blackstone", 4]` 与 `["minecraft:basalt", 4]`。

- **并列 netherrack（12）**：三者都是"基础下界石料"，黑石/玄武岩各占下界石料约 17%，
  netherrack 仍为主体。取值 4 而非 12，避免把 netherrack 挤压成少数。
- **全 6 档**：与 netherrack/magma 一致（石英 gold↑、残骸 diamond↑ 属额外稀有层，
  不适用）。
- *备选*：gold↑ 起解锁——否决，黑石/玄武岩是基础建材，不该比 netherrack 更晚。
- **稀释评估**：加法池按比例稀释既有下界产物。netherite 档 special 已吃满 commons 桶
  （bucket=0），kite 池≈143；+8 → 151，残骸份额 0.5/143→0.5/151（相对 −5%），可接受。

### D2 灵力筑基器：工作台配方，T0 入口料

`data/gensokyou/recipe/landscaping_tool.json`（`crafting_shaped`）：

```
  I S I      I = minecraft:iron_ingot
  S C S      S = gensokyou:ritual_stone_0
  I S I      C = gensokyou:refined_cinnabar
```

- 难度对标 `danmaku_assembly_bench`（仪式石0+铁+精炼辰砂），属"铁/钻石级、前期可做"。
- mod 料由 `ritual_stone_0` 满足 `gensokyo-materials`「mod 独有品含 ≥1 mod 料」。
- 不含任何幻想乡素材、不含 T2 料、不含 BOSS 掉落——入口层低门槛。

### D3 灵力引爆器：源初造化之仪配方，minTier 1

在 `zaohua_circle.json` 追加：

```
name: zaohua_spirit_bomb
mode: activation, match: max, minTier: 1, spCost: 20000
ingredients:
  ritual_stone_1   ×1
  spirit_iron      ×2
  refined_cinnabar ×2
  minecraft:gunpowder ×4
  minecraft:amethyst_shard ×2
result: gensokyou:spirit_bomb ×1   (Σcount = 11)
```

- spCost 与 `zaohua_danmaku_weapon_frame` 同档（20000），符合"一阶造化"定位。
- **不含 T2 料、不含碎符卡星/BOSS 掉落**：与 `danmaku-weapon-crafting` 的入口层红线一致，
  避免"要打 BOSS 才能造爆破工具"的怪循环。
- Σcount=11 ≤ zaohua tier1 祭品台数 16（`RitualRecipePedestalCapacityTest` 校验）。

### D4 本地化与展示

- 两工具已有 `item.gensokyou.landscaping_tool` / `item.gensokyou.spirit_bomb` 的 lang 键，
  无需新增文案。
- 引爆器配方经 JEI 的造化配方展示自动收录；筑基器走原版工作台配方展示。

## Risks / Trade-offs

- [加法池稀释既有下界产物] → 权重取 4+4（非 12+12），净稀释 <5~10%；不改池机制。
- [筑基器配方与 danmaku_assembly_bench 撞形] → 用 3×3 全对称且含铁锭/精炼辰砂，
  与原 bench 的 `SIS/ICI/SSS` 区分。
- [引爆器被误当成入口关键件] → 它不是入口件（无它也能正常开局），保持 T1 自洽即可。

## Migration Plan

- 纯新增数据条目，无存档/结构变更。回滚：删除对应 JSON 条目即可。

## Open Questions

- 无（数值均落在既有 config 之外、纯数据层）。
