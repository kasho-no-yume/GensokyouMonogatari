# add-gensokyou-material-uses 实机检验清单

> **交用户跑，agent 不启动客户端。** 本变更共 49 项任务，其中 46 项已由代码与单测覆盖，
> 剩 3 项（种植闭环、工具边界、全套走查）必须实机验证。
>
> **测试环境**：新建一个创造模式世界（别用主存档——`kaya_no_hime_circle.json` 改产出是
> **存档不兼容的数据变更**，见 §8）。
>
> **前置**：`.\tools\gradle_task.ps1 build` 全绿（846 tests）。客户端启动后先 `/reload`。
>
> **测试区惯例**：`(104, 100, 20)` 附近平整地面（沿用既有 harness 的坐标）。

---

## 0. 启动期冒烟（5 分钟，先做这个）

启动到标题界面 → 进世界 → 开控制台看 `run/logs/latest.log`（GBK，按 cp936 读）。

| 检查 | 期望 |
|---|---|
| 有无 registry 异常 / `unbound value` | **无**。上一轮修的两个 bug（config 早读、`MobEffectInstance` 早解引用）就以这两类报错形式出现 |
| `Loaded N ritual brew rules` | N ≥ 20（16 原版 + 4 mod 试剂，来自两个数据文件） |
| 有无 `Rejected ritual brew rule` | **无** |
| 有无 `Brew rule ... overrides earlier mapping` | **无**（mod 试剂在独立文件，不该撞原版） |
| `Loaded N ritual recipes` | N 覆盖 zaohua 39 条 |
| 有无 `Rejected ritual recipe` | **无** |
| 创造栏「幻想乡」标签 | 新物品全部可见：4 种子、瓷器、18 件装备、整地工具、灵力引爆器 |

**踩雷即停**：任何一条不过，先别往下跑——后面的用例都建立在这些之上。

---

## 1. 灵土农业线（`spirit-crop-farming`）

### 1.1 灵土耕地

```mcfunction
/give @s gensokyou:spirit_soil 64
```

1. 手持任意锄右键 `spirit_soil` → 应变 `spirit_soil_farmland`（顶面苔色土纹、侧面沟垄）。
2. 破坏耕地 → 掉落回 **`spirit_soil`**（不是泥土，也不是耕地本身）。
3. **负例**：耕地方块上方压一个石头 → 应退化成 `spirit_soil`（`canSurvive` 链路）。
4. **负例**：跳起来落在耕地上（摔落伤害）→ 退化成 `spirit_soil`。
5. **负例**：锄头点方块的**下表面**（挖脚下的土）→ **不**应变耕地。
6. **负例**：耕地上方已有草方块 → **不**能耕（上方非空气）。

### 1.2 种子种植与基质

```mcfunction
/give @s gensokyou:spirit_herb_seeds 16
/give @s gensokyou:gentian_seeds 16
/give @s gensokyou:higanbana_seeds 16
/give @s gensokyou:magic_mushroom_spores 16
/give @s gensokyou:higan_soil 32
/give @s gensokyou:magic_wood 4
```

| 种子 | 灵土耕地 | 原版耕地 | 彼岸土 |
|---|---|---|---|
| `spirit_herb_seeds` | ✅ | ✅ | ❌ 放不下 |
| `gentian_seeds` | ✅ | ✅ | ❌ 放不下 |
| `higanbana_seeds` | ✅ | ✅ | ✅ |
| `magic_mushroom_spores` | ✅ | ✅ | ✅ |

7. 逐格照上表点一遍，把 ❌ 的两格确认「右键无反应、种子还在背包里」。

### 1.3 生长加速（本节核心）

**对照组实验**（关键，别跳过）：

8. 并排放两块地：A = `minecraft:farmland`（补水）、B = `spirit_soil_farmland`（邻水保湿也行，
   不保湿也能长，只是倍率低）。
9. 同一种子同节奏种下，`/time set day` 后站旁边等。
10. **期望**：B 明显比 A 早成熟（不是"感觉快"，是肉眼可见的提前）。若两者速度无差，
    说明 `SpiritCropBlock.randomTick` 的加速没生效 → 查 `spiritSoil.growthChance` 配置。
11. **彼岸土额外加速**：另起一块 `higan_soil` 种 `higanbana_seeds`，与 B 同步种下 →
    应比 B **再快一倍**左右（`higanSoilGrowthMultiplier`）。

> 想加速验证可临时改配置：`build/config/...` 或游戏内改完 `/reload`。
> `growthChance` 越小越快；设 0 = 关闭加速（应回到原版速度）。

### 1.4 掉落与骨粉

12. 成熟（最后阶段）破坏 → **1 份植物 + 1~2 份种子**。
13. **未成熟**破坏 → **只掉 1 份种子**，不掉植物。
14. 对未成熟作物用骨粉 → 应能推进阶段（`CropBlock` 既有行为）。
15. 时运 III 镐挖成熟作物 → 种子数应 > 2（掉落表挂了 `binomial_with_bonus_count`）。

### 1.5 茅野姬仪式改出种子

16. 搭 `gensokyou:kaya_no_hime_circle`（仪式构建器一键），祭品台放**指导书**（信物，不消耗），
    启动 → `gensokyou_low` 带应出**种子**，不再出 `spirit_herb` / `gentian` / `higanbana`。
17. 换**隙间碎片**信物 → `gensokyou_high` 带应出 `magic_mushroom_spores`。
18. 确认信物**不被消耗**，多轮结算后仍在台上。

---

## 2. mod 药水线（`gensokyou-mod-potions`）

### 2.1 炼药台两步（主线）

```mcfunction
/give @s minecraft:glass_bottle 32
/give @s minecraft:nether_wart 8
/give @s gensokyou:sanzu_flask 16
/give @s gensokyou:spirit_herb 16
/give @s gensokyou:magic_mushroom 16
/give @s gensokyou:gentian 16
/give @s gensokyou:higanbana 16
/give @s gensokyou:porcelain 16
/give @s gensokyou:moon_sand 16
/give @s minecraft:redstone 8
/give @s minecraft:glowstone_dust 8
```

19. **第一步**：酿造台放 3 瓶**地狱疣粗制药水**，材料位放 `sanzu_flask` → 得
    `crude_sanzu_potion`（粗制冥汤）。
20. **第二步**：粗制冥汤 + 四种植物 → 依次得 `reiki_recovery` / `spiritual_sight` /
    `spirit_touch` / `higanbana_poison`。
21. **独占门槛（负例一）**：材料位放 `minecraft:water_bottle` + 任意 mod 植物 → **不该**出任何 mod 药水。
22. **独占门槛（负例二）**：`minecraft:awkward_potion` + **原版**试剂（如 blaze_powder）
    → 只出原版药水，不出 mod 药水。
23. **档位**：任一 mod 药水 + `moon_sand` → 长效档；+ `porcelain` → 强效档。
24. **跨档互通**：长效 + 瓷器 → 强效；强效 + 月砂 → 长效。
25. **负例**：mod 药水 + **原版红石 / 荧石** → **不该**变长/变强（mod 线绕不开月砂和瓷器）。

### 2.2 少名渡汤之仪路径

26. 搭 `gensokyou:sunako_circle`，祭品台放 `sanzu_flask`，GUI 试剂槽空 → 启动应失败并回显缺试剂。
27. 试剂槽放 `spirit_herb`，**结构内没有 magic_wood** → 启动失败，GUI 状态行显示
    「缺少魔法木构件·无法炼制 mod 药水」（`no_magic_wood`）。
28. 在结构内放一块 `gensokyou:magic_wood`（任意位置，祭品台之外）→ 再启动应成功，
    祭品台原位替换成 `reiki_recovery`（**原地替换**，不掉落圆盘）。
29. **原版路径不受影响**：试剂槽放烈焰粉、结构内无 magic_wood → 仍出原版力量药水。
30. 确认 `magic_wood` **不被消耗**，多轮结算后仍在原地。

### 2.3 瓷器只能走煅炉

31. 金山彦命煅炉祭品台放 `porcelain_clay` + `spirit_charcoal` → 出 `porcelain`。
32. **负例**：原版熔炉/高炉/烟熏炉放 `porcelain_clay` → **不出**瓷器。

### 2.4 常世木 → 高阶灵力核心

33. 源初造化之仪按配方炼 `spirit_core_3` / `_4` / `_5`（`minTier` 3/4/5，常世木主材 +
    至少一件别的幻想乡素材）。确认三条都能出，且材料被正常消耗。

### 2.5 四个效果逐条验

```mcfunction
/effect give @s gensokyou:reiki_recovery 60 0
/effect give @s gensokyou:spiritual_sight 60 0
/effect give @s gensokyou:spirit_touch 60 0
/effect give @s gensokyou:higanbana_poison 30 0
```

| # | 效果 | 验什么 | 期望 |
|---|---|---|---|
| 34 | `reiki_recovery` | 空池站着不动 | 灵力条涨得比没有效果时快 |
| 35 | `reiki_recovery` | **池满时** | 不涨（`current >= max` 直接返回），不报错 |
| 36 | `spiritual_sight` | 周围放几只怪 + 一只动物 | 非玩家实体有**绿色**描边、**可穿墙看见**；玩家自己**不**进队伍、无描边 |
| 37 | `spirit_touch` | 隔 5 格右键箱子 / 打 5 格外实体 | 交互距离变长（约 +2 格） |
| 38 | `higanbana_poison` | 让骷髅打自己 | 显示掉血约为原始的 20%（80% 免伤） |
| 39 | `higanbana_poison` | **坠入虚空** | **照样死**，免伤不生效 |
| 40 | `higanbana_poison` | 效果自然结束 | **立即死亡** |
| 41 | `higanbana_poison` | 喝牛奶洗掉 | **立即死亡**（等同自然结束） |
| 42 | `higanbana_poison` | 背包放不死图腾，等效果结束死 | **图腾不触发**（若触发了，回退方案是接受，用户已确认） |
| 43 | `higanbana_poison` | `/kill` | 照常死（`genericKill` 不挡） |

> ⚠️ 38~43 建议**备份存档或用创造模式飞行**慢慢试。42 是本次最可能翻车的一条。

---

## 3. 灵铁 / 星银装备（`gensokyou-equipment-tiers`）

```mcfunction
/give @s gensokyou:spirit_iron 64
/give @s gensokyou:star_silver 64
/give @s gensokyou:spirit_iron_pickaxe 1
/give @s gensokyou:spirit_iron_axe 1
/give @s gensokyou:spirit_iron_shovel 1
/give @s gensokyou:spirit_iron_hoe 1
/give @s gensokyou:spirit_iron_sword 1
/give @s gensokyou:star_silver_pickaxe 1
```

### 3.1 灵铁零耐久

44. 灵铁镐挖 **`minecraft:stone`** 一大片 → 耐久**不掉**。
45. 灵铁斧挖 **`minecraft:oak_log`** → 耐久**不掉**。
46. 灵铁锹挖 `minecraft:dirt` / `minecraft:sand` → 耐久**不掉**。
47. **负例**：灵铁镐挖 `minecraft:iron_ore` → 耐久**照常掉**。
48. **负例**：灵铁镐挖 `gensokyou:spirit_iron_ore` → 耐久**照常掉**。
49. **负例**：给灵铁镐用铁砧改个名（自定义名）→ 再挖石头 → 耐久**开始掉**（命名方块不豁免）。

### 3.2 星银精准采集

50. 星银镐**不附魔**挖 `minecraft:stone` → 掉 `minecraft:stone`（不是圆石）。
51. 星银镐挖 `minecraft:grass_block` → 掉草方块（ SilkTouch 语义生效）。
52. **自动补回**：把星银镐放铁砧/砂轮洗掉 SilkTouch → 拿回手上再挖一次 →
    **仍然精准**（`inventoryTick` 补组件）。
53. **负例**：挖**带 TileEntity 的方块**（箱子 / 熔炉）→ 走原版掉落表，不会复制内容物
    （这条是安全红线，务必确认）。

### 3.3 追加掉落

54. 星银镐挖 `gensokyou:cinnabar` ×20 次 → 应偶发额外掉 1 个**矿石方块**
    （概率 `starSilverExtraOreChance`，默认 25%）。
55. **负例**：星银镐挖 `minecraft:diamond_ore` → **不**追加掉落。
56. **负例**：星银斧 / 锹挖 mod 矿石 → **不**追加（追加只限镐）。

### 3.4 盔甲

57. 穿 1 件灵铁盔甲站着回灵 → 比不穿快（约 1/4 份加成）。
58. 穿满 4 件 → 满份加成（约 +50%）。两套混穿也吃同一份。
59. **池不膨胀**：穿满后看灵力池 `max` 和 `spirit_damage` 数值 → **不应变化**
    （红线：单写台账不得被旁路写）。
60. 只穿 1 件星银盔甲 → `spirit_damage` **不变**。
61. 穿满 4 件星银 → 打怪/放弹幕后看灵力强度 → **约 +8%**。
62. **减伤红线**：穿满星银挨打 → 除原版护甲值外**没有**额外减伤效果。
63. 装备修复：把耐久打低，用 `spirit_iron` / `star_silver` 在铁砧修 → 正常。

### 3.5 套装加成即时性

64. 脱下 1 件星银盔甲 → 加成**立即消失**（读时乘算，无残留）。

---

## 4. 两个妙妙工具（`gensokyou-utility-tools`）

### 4.1 整地工具

```mcfunction
/give @s gensokyou:landscaping_tool 1
```

65. 在杂石 + 树 + 草 + 泥土的地形上右键 → 白名单方块被清、地面铺成泥土，
    聊天栏报「整地完成：清除 N 处地形，铺平 M 格」。
66. **硬保护逐条验**（在半径内各放一个，然后整地，**都不该动**）：

| 保护对象 | 摆什么 |
|---|---|
| TileEntity | 箱子（**里面放东西**）、熔炉、`ritual_core`、`ritual_pedestal`、`crystal` |
| 基岩 | 基岩 |
| 黑曜石族 | 黑曜石、哭泣的黑曜石 |
| 矿石 | 钻石矿、`gensokyou:star_silver_ore` |
| 传送门框 | 末地传送门框 / 黑曜石 Portal |
| mod 素材 | `gensokyou:cinnabar`、三种木、`higan_soil`、`moon_sand`、任意品阶仪式石 |

67. **泥土获取**：非创造模式下背包有泥土 → 正常铺；**没泥土** → 只清不填（铺平数为 0）。
68. **冷却**：连续快速右键第二次 → 无反应（默认 10s）。
69. **创造模式**：无冷却、不要求背包有泥土。
70. **自定义名方块**：给一个石头改名 → 整地后**应保留**（`isProtected` 虽不查名字，
    但 `clearTerrain` 会调 `destroyBlock(pos, false, player)`，该路径不掉落物品而已 ——
     确认石头还在）。

### 4.2 灵力引爆器

```mcfunction
/give @s gensokyou:spirit_bomb 1
```

71. **放置**：右键地面 → 出现一个 TNT 外形的实体（占位渲染，正式特效待美术补）。
72. **开 GUI**：再右键它 → 弹出配置面板：三个滑块（起爆时间 0.1~600s / 强度 1.0~12 / 半径 1~24）
    + 预估消耗 + 启动按钮。
73. **参数钳制**：把强度拖到 12.0、半径 24 → 松手 → 滑杆应停在服务端回发值（不漂移）。
74. **启动扣费**：调默认（4.0 / 6），点启动 → 灵力扣除约 20000（`baseCost`），
    按钮变「已启动，倒计时中」并置灰。
75. **负例**：把强度/半径调到很大，让估算消耗 > 当前灵力 → 点启动 → **按钮不可用**，
    显示「灵力不足，无法启动」，灵力不扣。
76. **沉睡可收回**：放一个不启动，打掉它 → 掉回 `spirit_bomb` 物品。
77. **已启动不可收回**：启动后打它 → **不掉落物品**，倒计时继续。
78. **离线计时**：启动一个长引信（如 60s），退出重进 → 继续倒计时并起爆。
79. **全掉落爆炸**：起爆后看向四周 → 被毁方块**以物品形态掉落**（不是消失）。
80. **产物上限**：调强度 12 / 半径 24 在一堆石头边起爆 → 服务端**不卡死**；
     `run/logs/latest.log` 应见 `Spirit bomb at ... produced N item entities; culled M over the cap of ...`
     （若 N ≤ cap 则无该日志，正常）。
81. 爆炸后 GUI 应显示「引爆器已不在世界上」。

---

## 5. 回归抽查（别只测新的）

82. 源初造化之仪**原有配方**仍能跑（弹幕武器、符卡星、灵力核心 0~2）——39 条里只增不改。
83. 少名渡汤**原版试剂**全表仍可用（16 条，烈焰粉/幻翼膜/糖/兔子脚……）。
84. 弹幕武器 / 符卡 / 灵力核心的创造栏与 JEI 显示正常。
85. 既有 harness：`powershell -ExecutionPolicy Bypass -File tools\_run_ritual_test.ps1`
    → `function gs_test:run_all` 全绿（确认没踩坏既有仪式）。
86. 单测：`.\tools\gradle_task.ps1 build` → 846 tests, 0 failed。

---

## 6. 日志期望（`run/logs/latest.log`）

- 无 registry 异常、无 `unbound value`、无 `Cannot get config value`
- `Loaded N ritual brew rules` N ≥ 20；无 `Rejected`
- `Loaded N ritual recipes`；无 `Rejected`
- 有 `Spirit bomb ... culled ... over the cap` 时属预期（仅在大爆炸时）
- 无 mod 相关的 `ERROR` / 堆栈

---

## 7. 存疑与已知取舍（跑的时候心里有数）

| 项 | 状态 |
|---|---|
| 灵力引爆器的**视觉** | 目前是 TNT 方块模型占位。设计规格（符环自转 / 内芯脉动随时限加快 / 颜色随强度青→紫→红 / 引爆先内缩再炸开）需要 Blockbench 或图像生成，**超出 16x16 ASCII 表达力**，未做 |
| 爆破裂缝 | 半径是"爆炸威力之外补一段二次伤害"实现的（1.21.1 的 `explode(power)` 无独立半径参数）。观感上可能与"半径"滑杆的直觉略有偏差 |
| `spirit_touch` 的属性加成 | 原版 `addAttributeModifier` 构造期固化，`spiritTouchRangeBonus` 配置**不影响实际加成**，只影响提示文案 |
| 彼岸花毒死法 | 走 `setHealth(0) + die(customDamageType)`。若图腾仍触发，接受退化（用户已确认） |

---

## 8. 存档兼容性（**重要**）

- `kaya_no_hime_circle.json` 的 `gensokyou_low` / `gensokyou_high` 产出从**植物本体**改成**种子**。
- **旧存档里已有的** `spirit_herb` / `gentian` / `higanbana` / `magic_mushroom` **仍可作药水试剂**
  （配方引用的是物品本身，没变），不会报废。
- 但旧存档的**自动化农场**若是靠"仪式直出植物"供货的，现在会断供 —— 需要改种种子。
  **请在主存档升级前确认这一点。**

---

## 9. 三项 openSpec 任务的对应关系

| 任务 | 覆盖章节 |
|---|---|
| 2.9 单机实测：种子种植/成熟/掉落闭环、灵土加速可感知、彼岸土基质生效、旧存档已有植物物品仍可作试剂 | §1 + §8 |
| 5.x 编译 + 单机实测：整地工具各类保护逐条验证；引爆器参数/扣费/上限/掉落/离线计时逐条验证 | §4 |
| 6.5 与用户实机走查（种植闭环、药水双路径、装备特技边界、引爆器爆炸与掉落在 1.21 规则下的一致性） | §1 + §2 + §3 + §4 |

全部打勾后跑 `openspec archive add-gensokyou-material-uses` 收尾。
