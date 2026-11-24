# 幻想建筑块一览

「幻想建筑块」是本项目一组可逐格染色的中性灰模方块，注册名统一为 `gensokyou:landmark_<形状>`，游戏内中文显示为「幻想建筑块」（英文 `Gensokyo Building Block`）。它们是地标的画布：图纸里每格给一个 `0xRRGGBB`，客户端渲染时按维度、区块段、坐标和预期方块 ID 查色；没给颜色的格子保持灰度。

注册见 `src/main/java/com/bitsson/gensokyou/registry/LandmarkBlocks.java`，染色分派见 `src/main/java/com/bitsson/gensokyou/client/LandmarkBlockColors.java`。这些方块**没有物品形态**，不能从创造/生存模式直接拿到并放置；它们只通过结构模板（`structure` NBT）或 `/setblock` 出现在样件、博丽神社及其外围地标中。布局、配色和预览流程见[制作与预览指南](landmark-building-guide.md)。

## 共同属性

- **不可破坏**：`strength(-1.0F, 3_600_000.0F)` + `noLootTable()`；保护区内手动放置还会被区域规则拦截，动作栏提示「此处不能放置方块」。
- **防活塞**：`PushReaction.BLOCK`，黏性活塞也拉不走。
- **统一染色**：模型所有可见面 `tintindex = 0`；色号 0 表示保持灰度，单份配色资源最多 255 个显式 RGB、255 种预期方块 ID。
- **贴图只有 4 张**：`brick` / `wood` / `tile` / `lamp` 各一张 16×16 灰度图，其余形状复用它们。
- **音效按材质**：石质 `STONE`、木质 `WOOD`、灯 `GLASS`。

## 方块清单

| 注册 ID · 图纸后缀 | 方块类型 | 复用贴图 | 方块状态属性 | 用途与备注 |
|---|---|---|---|---|
| `landmark_brick` · `brick` | `Block` | `landmark_brick` | — | 石质实心。样件地面；神社石垣、灰泥墙、庭院砂地、石灯笼底座 |
| `landmark_wood` · `wood` | `Block` | `landmark_wood` | — | 木质实心。柱、梁、地板、墙面、鸟居笠木与贯 |
| `landmark_tile` · `tile` | `Block` | `landmark_tile` | — | 石质实心。屋顶瓦、石板路、榻榻米、金色装饰 |
| `landmark_tile_slab` · `tile_slab` | `SlabBlock` | `landmark_tile` | `type`, `waterlogged` | 屋檐压顶、矮桌、石灯笼顶盖等半砖 |
| `landmark_tile_stairs` · `tile_stairs` | `StairBlock` | `landmark_tile` | `facing`, `half`, `shape`, `waterlogged` | 屋顶坡面、大阶梯、门廊台阶 |
| `landmark_wood_door` · `wood_door` | `DoorBlock`（OAK） | `landmark_wood` | `facing`, `half`, `hinge`, `open`, `powered` | 上下两半各占一格、分别定义；开合后保留颜色；`noOcclusion()` |
| `landmark_wood_trapdoor` · `wood_trapdoor` | `TrapDoorBlock`（OAK） | `landmark_wood` | `facing`, `half`, `open`, `powered`, `waterlogged` | 神社大门敞开的两扇门叶、赛钱箱盖；`noOcclusion()` |
| `landmark_wood_fence` · `wood_fence` | `FenceBlock` | `landmark_wood` | 四向连接布尔 + `waterlogged` | 回廊栏杆、格栅窗、障子；`noOcclusion()` |
| `landmark_stone_wall` · `stone_wall` | `WallBlock` | `landmark_brick` | `up` + 四向连接 + `waterlogged` | 石灯笼柱、矮墙；`noOcclusion()` |
| `landmark_lamp` · `lamp` | `Block` | `landmark_lamp` | — | 和纸灯笼罩，光照等级 15，音效 `GLASS`；常用来做石灯笼与檐下暖光 |

其中 `brick`、`wood`、`tile`、`tile_slab`、`tile_stairs`、`wood_door` 是**基础 6 种**，模型由 `tools/export_landmark_sample.py` 生成；`wood_trapdoor`、`wood_fence`、`stone_wall`、`lamp` 是**扩展 4 种**，模型由 `tools/export_hakurei_shrine_preview.py` 的 `extra_model_assets()` 生成。两者的方块状态与模型都已签入资源目录，所以在图纸里写任意一种后缀都可以；只是单独重跑样件导出器不会重新生成扩展 4 种的模型。

## 贴图底图

| 贴图 | 灰度图案 | 生成器 | 被哪些方块复用 |
|---|---|---|---|
| `block/landmark_brick` | 砖缝 / 石墙 | `tools/textures/landmark_gray.py` | `brick`、`stone_wall` |
| `block/landmark_wood` | 木纹 | `tools/textures/landmark_gray.py` | `wood`、`wood_door`、`wood_trapdoor`、`wood_fence` |
| `block/landmark_tile` | 瓦垄 | `tools/textures/landmark_gray.py` | `tile`、`tile_slab`、`tile_stairs` |
| `block/landmark_lamp` | 和纸灯罩，中心近白 | `tools/textures/landmark_lamp.py` | `lamp` |

贴图由 `python tools/gen_tex.py landmark_gray.py landmark_lamp.py --write-assets` 渲染。底色是灰度，最终颜色来自逐格 tint 相乘，所以同一张贴图能染成朱红、黛瓦、鎏金或暖灯光。

## 在图纸里使用

在 `tools/export_landmark_sample.py` 的 `sample_cells()` 或神社导出器的 `Builder` 里，每格写「局部坐标 + 后缀 + 颜色 + 可选方块状态」：

```python
cell(3, 2, 1, "wood", 0xCB6548)                       # 浅红木板
cell(4, 2, 1, "wood", 0x873927)                       # 深红木板
cell(2, 2, 1, "wood", None)                           # None：保留灰度对照
cell(5, 2, 1, "wood_door", 0x704837, facing="north", half="lower",
     hinge="left", open="false", powered="false")      # 门的上/下半分别定义
```

样件里那三格对照格为 `(14,66,5)`、`(15,66,5)`、`(16,66,5)`：灰、浅红、深红，`(15/16,66,5)` 之间跨区块边界，专门用来验证跨段查色。完整样件用色：

| 形状 | 颜色 | 用途 |
|---|---|---|
| `brick` | `0xA79C91` | 地面灰砖 |
| `wood` | `0xCB6548` / `0x873927` | 浅红/深红墙柱；角柱与偶数列用深红 |
| `tile` / `tile_stairs` | `0x566F83` | 屋顶黛瓦 |
| `tile_slab` | `0x718C9A` | 屋脊浅瓦 |
| `wood_door` | `0x704837` | 门板 |

## 配色资源与生效范围

每个模板（结构 NBT）配套一份 GLC2 配色资源（GZIP 压缩的 `.bin`），按世界坐标查色；同一模板换原点要另存一份配色。

| 配色资源 | 结构模板 | 生效维度 / 原点 |
|---|---|---|
| `assets/gensokyou/landmark/landmark_sample_v1.bin` | `data/gensokyou/structure/landmark_sample_v1.nbt` | `gensokyou:landmark_sample_v1`，原点 `(12,64,4)`，尺寸 `12×7×8` |
| `assets/gensokyou/landmark/hakurei_shrine_preview.bin` | `data/gensokyou/structure/hakurei_shrine_preview.nbt` | 同为样件维度，原点 `(128,64,128)`，尺寸 `95×38×71` |
| `assets/gensokyou/landmark/hakurei_shrine_gensokyo.bin` | 复用博丽神社模板 | `gensokyou:gensokyo`，原点 `(3022,83,93)` |
| `assets/gensokyou/landmark/hakurei_front_gateway_gensokyo.bin` | `hakurei_front_gateway` | `gensokyou:gensokyo`，原点 `(2976,78,96)`，尺寸 `46×18×60` |
| `assets/gensokyou/landmark/hakurei_north_guesthouse_gensokyo.bin` | `hakurei_north_guesthouse` | `gensokyou:gensokyo`，原点 `(3040,82,62)`，尺寸 `72×26×31` |
| `assets/gensokyou/landmark/hakurei_east_grounds_gensokyo.bin` | `hakurei_east_grounds` | `gensokyou:gensokyo`，原点 `(3118,88,100)`，尺寸 `48×32×92` |

其他维度不渲染这些颜色，方块显示原始灰度；配色文件缺失或损坏时同理，客户端日志会出现 `Could not load landmark sample colors`。颜色与坐标及预期方块 ID 绑定：用 `/setblock` 换成另一个幻想建筑块 ID 会褪色，换回原 ID 又恢复；门开合等状态变化不影响颜色。

## 重新生成

```powershell
# 缺 Minecraft 开发资源时先执行
.\tools\gradle_task.ps1 createMinecraftArtifacts

# 贴图
python tools/gen_tex.py landmark_gray.py landmark_lamp.py --write-assets

# 基础 6 种模型 + 样件 NBT/配色
python tools/export_landmark_sample.py
python tools/export_landmark_sample.py --check

# 扩展 4 种模型 + 博丽神社 NBT/两套配色
python tools/export_hakurei_shrine_preview.py
python tools/export_hakurei_shrine_preview.py --check

# 博丽神社外围三块地标 NBT/ Gensokyo 配色
python tools/export_hakurei_outer_grounds.py
python tools/export_hakurei_outer_grounds.py --check
```

配色格式与性能说明见[制作与预览指南](landmark-building-guide.md#配色文件与性能)；验收记录见[归档验收记录](../openspec/changes/archive/2026-11-24-prototype-tinted-landmarks/verification.md)。
