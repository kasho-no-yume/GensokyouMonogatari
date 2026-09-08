# 蓝图：haiden — 紫瓦拜殿（小型东方神社拜殿）

> 状态：已实机验收（2026-09-08，/place template 与 /function 双路径通过）。
> 体量：footprint 17×17 · 高 9 层（y0–y8，含地基）· 左右镜像对称（对称面 x=8.5）。

## 0. 总览与坐标约定

- **主题**：小型神社拜殿——凸字形石台基、正面开敞的 3×3 柱网殿身、二阶紫瓦坡顶、悬脊宝珠；庭园配石灯籠一对、玉垣、紫旗与花瓣苔坪。
- **坐标**：x:0–16（东→西），z:0–16（南→北），y:0–8（自下而上）。
- **朝向**：正面 = z 最小侧（z=0 一方为殿正面/入口）；评审时玩家从正面（南面）观看。
- **风格意图**：
  - 品阶色语义严格两档化——**0 阶（灰、无彩）做基座/围栏，5 阶（紫）做屋顶与焦点**，中间品阶（1–4）一概不用，避免绿/蓝/琥珀/红混入紫系画面。
  - 紫系石族：purpur 柱与脊、amethyst 宝珠、紫玻璃窗、ritual_stone_5 屋面。
  - 东方词库落地：石灯籠 / 玉垣 / 旗帜 / 花瓣 / 窗格。
- **刻意不用 `ritual_core`**：防止建筑方块组合被既有仪式 pattern（barrier_break / tempering 等）误认领。

---

## 1. 段A：地基（y0）

- **体块**：整层实心 17×17（x:0–16 × z:0–16），共 289 格。
- **材料**：`minecraft:deepslate`。
- **作用**：/place 清地形后兜底防悬空；深色基座把紫台与苔坪"抬"出地面，与浅色台基形成第一层明暗对比。

---

## 2. 段B：台基与庭园铺装（y1；点缀 y2）

### 2.1 台基（y1，凸字形，全块）

- **材料**：`gensokyou:ritual_stone_0`（0 阶灰紫，无彩基座）。
- **主台**：x:3–13 × z:4–12（11×9），殿身落座于此。
- **前凸台（拜台）**：x:5–11 × z:2–3（7×2），与主台前缘相连，构成"凸"字。

### 2.2 台缘勾边（半砖收边，顶面 y1.5）

将台基外缘下列格替换为 `gensokyou:ritual_stone_slab_0`（bottom）：

- 主台外缘：(3,5)–(3,7)、(3,9)–(3,11)、(13,5)–(13,7)、(13,9)–(13,11)、(4,4)–(7,4)、(9,4)–(12,4)、(4,12)–(7,12)、(9,12)–(12,12)
- 前凸台缘：(5,2)、(6,2)、(10,2)、(11,2)
- **避让（保持全块）**：
  - 9 个柱位格（x∈{3,8,13} × z∈{4,8,12}）→ 全块作天然柱础；
  - 旗座格 (5,3)、(11,3) → 全块（见段F 前旗）。

### 2.3 上台踏步（y1）

- (7,2)、(8,2)、(9,2)：`gensokyou:ritual_stone_stairs_0`，阶面朝正面（facing 朝 z- / 南向台阶）。

### 2.4 庭园铺装（y1，台基之外全域）

- 默认：`minecraft:moss_block` 苔坪，铺满台基外所有 y1 格。
- 步道：x:6–10 × z:0–1 换 `minecraft:end_stone_bricks`（正对踏步的引道石板）。

### 2.5 花瓣点缀（y2，`minecraft:pink_petals` 立于苔坪上）

- 侧院对称四簇：(1,5)、(15,5)、(1,10)、(15,10)
- 步道两翼：(4,0)、(12,0)
- 殿后一簇：(8,14)

---

## 3. 段C：柱网与墙身（y2–y4）

### 3.1 柱网（9 柱）

- 柱位：(x,z) ∈ {3,8,13} × {4,8,12}，每柱 y2–y4 竖置 3 格。
- 材料：`minecraft:purpur_pillar`（淡紫柱，竖纹）。
- 正面（z=4 一排）柱间**全开敞**，无墙。

### 3.2 板壁（`minecraft:dark_oak_planks`）

- 侧墙：x=3 与 x=13 列——z∈{5,11} 铺满 y2–y4；z∈{6,7,9,10} 只铺 y2（窗下壁裙）。
- 背墙：z=12 行——x∈{4,5,11,12} 铺满 y2–y4；x∈{6,7,9,10} 只铺 y2。
- 层次：y2 为通长深棕壁裙，y3–y4 在窗区让位玻璃（下条）。

### 3.3 窗（`minecraft:purple_stained_glass`，y3–y4 两格高）

- 侧窗：x=3 与 x=13 列，z∈{6,7} 与 z∈{9,10}（每侧两窗，中柱 z=8 分隔）。
- 背窗：z=12 行，x∈{6,7} 与 x∈{9,10}（中柱 x=8 分隔）。
- 正面无墙无窗，保持开敞。

---

## 4. 段D：屋顶（y5–y8，二阶悬山 + 悬脊宝珠）

### 4.1 y5 檐口圈

- 檐坡：`gensokyou:ritual_stone_stairs_5`，阶面一律朝外：
  - 北檐 z=3 × x:3–13（朝 z-）；南檐 z=13 × x:3–13（朝 z+）
  - 西檐 x=2 × z:4–12（朝 x-）；东檐 x=14 × z:4–12（朝 x+）
- 四角 (2,3)、(14,3)、(2,13)、(14,13)：`gensokyou:ritual_stone_slab_5`（bottom）补角。
- 楣梁：z=4 行与 z=12 行 × x:3–13，`minecraft:dark_oak_log`（**水平轴向，axis=x**）。
- 侧柱顶垫块：(3,8)、(13,8) 放 `gensokyou:ritual_stone_5`。
- 檐下内部（x:4–12 × z:5–11）保持空（吊顶空腔由 y6/y7 屋面封顶）。

### 4.2 y6 坡圈

- 坡沿：`gensokyou:ritual_stone_stairs_5` 阶面朝外：
  - z=4 行 × x:4–12（朝 z-）；z=12 行 × x:4–12（朝 z+）
  - x=3 列 × z:5–11（朝 x-）；x=13 列 × z:5–11（朝 x+）
- 四角 (3,4)、(13,4)、(3,12)、(13,12)：`gensokyou:ritual_stone_slab_5`（bottom）。
- 坡肩垫层（`gensokyou:ritual_stone_5` 实心）：z=5 行 × x:4–12；z=11 行 × x:4–12。
- **悬鱼**：`gensokyou:ritual_stone_wall_5` × (2,8)、(14,8)——立在 y5 檐上、顶起 y7 悬脊端，形成檐→悬鱼→脊→宝珠的竖向收束链。

### 4.3 y7 坡面与脊

- 坡沿：z=6 行 × x:4–12 `ritual_stone_stairs_5` 朝 z-；z=10 行 × x:4–12 朝 z+。
- 瓦沟：z=7 行与 z=9 行 × x:4–12，`gensokyou:ritual_stone_slab_5`（bottom，顶面 y7.5，凹于两侧坡沿半格 → 筒瓦/本瓦交替的瓦垄意象）。
- 脊：z=8 行 × x:2–14，`minecraft:purpur_block`（**两端各悬挑 1 格**）。
- 山面（x=3/13 列的 y7）不设块，保持悬山轮廓。

### 4.4 y8 脊端宝珠

- (2,8) 与 (14,8)：`minecraft:amethyst_block`（立于悬脊两端，全建筑最高点）。

**屋顶剖面自检**（沿 z，x=8 处顶面高度）：
z3(y6.0 檐) → z4–5(y7.0 台级) → z6(y8.0 坡沿) → z7(y7.5 沟) → z8(y8.0 脊) → z9(y7.5 沟) → z10(y8.0 坡沿) → z11–12(y7.0 台级) → z13(y6.0 檐)。二阶轮廓 + 中央瓦沟，符合预期。

---

## 5. 段E：内部陈设（y2–y3）

- 供台：y2 × (7,11)、(8,11)、(9,11)——`gensokyou:ritual_pedestal_5` 三连（贴背墙一排，5 阶紫基座呼应主题色）。
- 中央灯：y3 × (8,11)——`minecraft:soul_lantern`（站立式，青焰立于中座上）。
- 花瓣：y2 × (6,5)、(10,5)——`minecraft:pink_petals`（殿内前缘一对）。
- 其余内部净空（柱间净高 3 格，玩家可入内站立）。

---

## 6. 段F：庭园家具

- **石灯籠 ×2**（(2,1) 与 (14,1)，左右对称，立于苔坪）：
  - y2 `minecraft:basalt`（柱基）→ y3 `minecraft:soul_lantern`（灯室）→ y4 `gensokyou:ritual_stone_slab_5`（笠）→ y5 `minecraft:purpur_block`（顶珠）。
- **玉垣**：y1 × x=0 列 z:2–12 与 x=16 列 z:2–12——`gensokyou:ritual_stone_wall_0` 连排（墙块自动相连成矮栅，围两侧不围后）。
- **前旗**：y2 × (5,3)、(11,3)——`minecraft:purple_banner`（立式，插于旗础全块上，护卫踏步两侧）。

---

## 7. 材料总表

| 方块 id | 来源 | 用途 |
|---|---|---|
| `minecraft:deepslate` | 原版 | y0 地基 |
| `gensokyou:ritual_stone_0` | **gensokyou** | 台基、柱础、旗座 |
| `gensokyou:ritual_stone_slab_0` | **gensokyou** | 台缘勾边 |
| `gensokyou:ritual_stone_stairs_0` | **gensokyou** | 上台踏步 |
| `gensokyou:ritual_stone_wall_0` | **gensokyou** | 玉垣 |
| `minecraft:moss_block` | 原版 | 苔坪 |
| `minecraft:end_stone_bricks` | 原版 | 前庭步道 |
| `minecraft:pink_petals` | 原版 | 花瓣点缀（庭 + 殿内共 9 格） |
| `minecraft:purpur_pillar` | 原版 | 9 柱 |
| `minecraft:dark_oak_planks` | 原版 | 板壁裙 |
| `minecraft:dark_oak_log` | 原版 | 前后楣梁（水平轴） |
| `minecraft:purple_stained_glass` | 原版 | 侧窗/背窗 |
| `gensokyou:ritual_stone_5` | **gensokyou** | 柱顶垫块、y6 坡肩 |
| `gensokyou:ritual_stone_stairs_5` | **gensokyou** | 檐坡、坡沿 |
| `gensokyou:ritual_stone_slab_5` | **gensokyou** | 檐角、瓦沟、灯笠 |
| `gensokyou:ritual_stone_wall_5` | **gensokyou** | 悬鱼 |
| `minecraft:purpur_block` | 原版 | 屋脊、灯顶珠 |
| `minecraft:amethyst_block` | 原版 | 脊端宝珠 |
| `minecraft:basalt` | 原版 | 石灯籠柱基 |
| `minecraft:soul_lantern` | 原版 | 殿内灯、灯籠灯室（站立式） |
| `gensokyou:ritual_pedestal_5` | **gensokyou** | 供台三连 |
| `minecraft:purple_banner` | 原版 | 前旗一对 |

gensokyou 方块共 8 种，且只用 0 阶（灰·素）与 5 阶（紫·焦点）两档。

## 8. 放置状态备注（给 gen 脚本）

- 所有 stairs 为 bottom 半阶，facing 按"阶面朝外/朝正面"标注解析（台阶低侧朝向该方向）。
- `dark_oak_log` 楣梁为水平轴向（axis=x）；柱与灯籠柱为默认竖轴。
- `soul_lantern` 全部站立式（非 hanging）；slab 全部 bottom。
- `pink_petals` 所在格下方均有支撑（苔坪/台面），已满足放置条件。
- 全图不含 `ritual_core`：不会被任何既有仪式 pattern 认领。

## 9. 待用户确认的取舍点

1. **屋顶**：现为"二阶坡 + 瓦沟 + 悬脊 + 宝珠"的简化悬山。想更华丽可改三阶坡，但高度会顶满 y8 预算（需去掉宝珠）。
2. **供台**：现为 ritual_pedestal_5 三连 + 单盏魂灯的极简版；可改为 amethyst_block 御神体意象。
3. **玉垣**：只围两侧（z:2–12），后场留开放苔坪；如需全围可延伸至 z:13–16（不增高度、不加材料）。
4. **花瓣密度**：现 9 格克制留白；花见氛围更浓可再增密苔坪区域。
