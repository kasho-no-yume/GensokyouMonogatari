# 八区地形与素材 · 验收数值

本文件固化第三节 3.2 / 3.3 的可机读验收数值。所有检查由
`GensokyoWorldgenProbe`（`-Dgensokyou.worldgenProbe=true`）在专用服务端的全新世界中执行。

## 1. 采样口径

- **采样区块**：每个区域取地标所在区块外 256 格的 3 个区块（偏移 `(256,0)`、`(0,256)`、`(-256,0)`）；
  只有群系等于该区域目标群系的区块才计入。地标净空半径（56 / 80）内不采样。
- **地表**：用 `MOTION_BLOCKING_NO_LEAVES` 高度图取顶面，并向下跳过树干与原版竹子
  （两者属于植被，不是自然地形表面）。
- **地下**：同一列从地表下方 6 格起，每 4 格取一格，直到 `max(-8, 地表-40)`；跳过空气、不计基岩。
- **幻想乡方块判定**：`namespace(block) == gensokyou`。

## 2. task 3.2 · 自然素材数值

| 对象 | 验收阈值（每区块 / 合计） | 种子 987654321 实测 |
|---|---|---|
| 灵土 `spirit_soil` 地表块 | ≥12 / 同群系区块；至少 1 个同群系区块 | 博丽 59 / 3 区块；人间之里 120 / 3 区块 |
| 有根神树 `sacred_wood` 原木列 | ≥1 / 同群系区块 | 博丽 7 / 3 区块；人间之里 7 / 3 区块 |
| 星银矿 `star_silver_ore` 方块 | ≥7 / 区块 | 守矢 8、魔法森林 16、地灵殿 8 |
| 高竹（≥16 格）柱数 | ≥16 / 区块（原探针阈值） | 151 |
| 灵铁 / 辰砂方块 | >0 / 区块（原探针阈值） | 43 / 15 |
| 星冠木有根原木列 | >0（原探针阈值） | 26 / 3 区块 |
| 妖精刷怪表 | 10 个群系均含 `fairy` 且不含 `big_fairy` | 10 / 10 通过 |

种子 `424242` 复测同样全项通过：博丽灵土 70 / 神树 8，人间之里灵土 368 / 神树 5，
星银矿 9 / 14 / 8，竹子 151，星冠木 14，妖精 10 / 10。

### 点缀斑块参数（`tools/gensokyo_ecology_data.py` 生成）

| Feature | 群系 | disk 参数 | 目标 |
|---|---|---|---|
| `spirit_soil_patch` | 博丽神社、人间之里 | radius 2–3，half_height 1，count 4/区块 | 灵土地表点缀 |
| `higan_soil_patch` | 白玉楼、三途川、红魔馆 | radius 2，half_height 1，count 2/区块 | 彼岸土斑块 |
| `moon_sand_patch` | 三途川、白玉楼、竹林浅沟 | radius 1–2，half_height 1，count 2/区块 | 月砂斑块 |
| `porcelain_clay_patch` | 雾之湖、人间之里 | radius 2，half_height 1，count 2/区块 | 瓷土斑块 |
| `oni_stone_veins` | 地灵殿裂谷 | ore size 5，count 3，Y 40…90 | 鬼石断层矿团 |
| `higanbana` | 白玉楼、三途川 | random_patch tries 12，noise 5/46/0.6 | 彼岸花点缀 |

> 实现注：`minecraft:disk` 的 placement Y 是地表上方一格，`half_height` 必须为 1 才能替换地表方块。

## 3. task 3.3 · 占比、过渡与路线

### 3.3.1 方块占比

| 指标 | 阈值 | 种子 987654321 实测 |
|---|---|---|
| 陆地地表幻想乡方块占比 | 每区 ≥90%，最差区块 ≥85% | 八区 97.7%–100%；最差区块 96% |
| 地下主体幻想乡方块占比 | 每区 ≥95%，最差区块 ≥90% | 八区 99.8%–100%；最差区块 99% |
| 有效地表采样列 | ≥128 / 区 | 768 / 区（3 区块 × 256） |

种子 `424242` 复测：地表 97.4%–100%（最差区块 95%），地下 99.8%–100%（最差区块 99%）。
原版云杉、竹子按"植被"排除后，八区地表/地下均以幻想乡方块为绝对主体。

### 3.3.2 边界过渡

- 每区 2 条贯穿样线，步长 32 格、长 1024 格；跳过外海、雾之湖与地灵殿竖井。
- 相邻采样点高差 **≤48 格**（种子 987654321 守矢主脊实测 44，种子 424242 实测最大 36）。

### 3.3.3 内部路线

- 每区从地标向 N/E/S/W 四向射线，步长 4 格、长 256 格；至少一条全程满足：
  连续水隙 ≤3 格、相邻地形高差 ≤20 格（容纳白玉楼 19 格阶地）。
- 八区均至少一条射线通过。

## 4. 探针断言输出

| 前缀 | 含义 |
|---|---|
| `GENSOKYO_NATURAL_MATERIALS_PROBE` | 灵土 + 神树数量 |
| `GENSOKYO_STAR_SILVER_PROBE` | 星银矿数量 |
| `GENSOKYO_FAIRY_SPAWN_PROBE` | 群系刷怪表 |
| `GENSOKYO_PROPORTIONS_PROBE` | 地表/地下占比 |
| `GENSOKYO_TRANSITIONS_PROBE` | 边界高度连续性 |
| `GENSOKYO_ROUTES_PROBE` | 内部路线可达性 |

`GENSOKYO_WORLDGEN_PROBE PASS` 表示全部断言通过。

## 5. 重新生成维度（改配置后复测）

离线脚本 `tools/regenerate_gensokyo.ps1` 只删除幻想乡维度下的 `region/`、`entities/`、`poi/`，
下次启动按当前数据包配置与世界种子重新生成；主世界和其他维度不受影响。

```powershell
.\tools\regenerate_gensokyo.ps1                                      # 干跑，只打印计划
.\tools\regenerate_gensokyo.ps1 -GameDir run -Backup -Confirm        # 执行并备份
.\tools\regenerate_gensokyo.ps1 -GameDir build\gensokyo-worldgen-probe -Seed 424242 -Confirm
```

必须先停服；`session.lock` 存在时拒绝执行（确认为残留锁可用 `-Force`）。`-ResetData` 才会连
`data/`（结构存档）一起删。已发到玩家手里的调试地图可能保留旧底图，需要换一张新地图。
