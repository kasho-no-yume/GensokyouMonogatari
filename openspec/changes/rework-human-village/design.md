# Design: 人间之里群系全面改造（Rework Human Village）

> **阅读对象与分工说明**：本设计文档面向两类执行者：
> - **建筑设计与产出（高阶模型负责）**：第 2 节（新增建筑块）与第 7 节（4 大地标 + 非功能装饰地标 + 1 Base 派生 10 种民居变体的 Python 生成器与实机审视迭代）。
> - **工程系统实现（下游实现模型照图施工）**：第 3、4、5、6、8、9 节。所有公式、字节格式、坐标、算法权重与边界条件均已给出精确定义，请严格按契约实现，无需自行猜测架构。

---

## 1. 总体架构与空间坐标总图

### 1.1 系统分层

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         人间之里 (HUMAN_VILLAGE)                           │
├─────────────────────────────┬──────────────────────────────────────────────┤
│ 1. 世界生成与地形 (Worldgen) │ 2. 确定性+微变村庄求解 (HumanVillagePlanner)  │
│  · GensokyoLayout           │  · 核心区固定种子(保假NPC零穿墙)              │
│    - 中心商街台地 Y=72      │  · 外围区 worldSeed 混合(微变民居/支路/配色)   │
│    - 村北稗田高台 Y=75      │  · 距群系边缘 >= 220 格禁建带                 │
│    - 贯城浅溪槽 Y=68..69    │  · 径向密度衰减 (r<=110 > 110..160 > 160..200)│
│  · LandmarkClearanceFilter  │  · VillageRoadRouter (分层路网+A*接路+架桥)   │
│    - r<=215 禁野生大树      │                                              │
├─────────────────────────────┼──────────────────────────────────────────────┤
│ 3. 懒加载放置与保护 (Server) │ 4. 局部实例染色与同步 (Client + Network)      │
│  · HumanVillageManager      │  · GLC3 局部坐标色表 (每种模板内存仅 1 份)     │
│    - SavedData 持久化方案    │  · HumanVillageLayoutSyncPayload (~2-3 KB)   │
│    - 按区块渐进放置(不卡顿) │  · LandmarkBlockColors 三层查色:              │
│    - 浅溪显式填水至 Y=70    │    (1) 旧 GLC2 -> (2) GLC3 实例 -> (3) 道路   │
│    - 建筑+主路不可破坏防熊  │  · assets/.../village_residents/human_village│
│    - /gs_village 预览命令   │    128 槽位假 NPC 繁华演出                    │
└─────────────────────────────┴──────────────────────────────────────────────┘
```

### 1.2 核心空间平面图（中心锚点 `CX = -200, CZ = 100, BASE_Y = 72`）

坐标系约定：Minecraft 标准坐标系（`+X = 东`, `-X = 西`, `+Z = 南`, `-Z = 北`）。

```
                              N (-Z)
                                │
                    [北村口冠木门 (-200, -56)]
                                │ (南北主干道 X=-200)
               ┌────────────────┴────────────────┐
               │   ★ 稗田阿求居所 (稗田家宅邸)    │  Y=75 高台
               │   原点 (-224, 74, -18), 48×22×38│  石阶南下接主道
               └────────────────┬────────────────┘
                                │ (Z=20..76 核心北商街 + 民居)
                                │
    W (-X)          ┌───────────┴───────────┐                      E (+X)
  魔法之森方向       │                       │                   博丽神社方向
  ═══════════════╤══╡     ★ 龙神像广场      ╞══╤═════════════════════════▶ X=40
  ★ 香霖堂       │  │  原点(-214,71,86)     │  │ [东冠木门+高札场]   (接博丽参道)
  (-348,71,78)   │  │     29×16×29          │  │   (-72, 71, 93)
  24×16×22       │  │                       │  │
  (林缘西道北侧) │  └───┬───────────────┬───┘  │  (东西主干道 Z=100)
                 │      │               │      │
                 │  ★ 铃奈庵            │      │  [核心环巷]
                 │  (-238, 71, 114)     │      │
                 │  20×16×18 (面向东/北) │      │
                 │      │               │      │
                 │      │      ~~~~~~~  │  ~~~~│~~~~~~~~~ 贯城浅溪 (水面Y=70)
                 │      │   ~~~[朱栏桥]~┼~~    │  ★ 鲵吞亭 (临水酒巷)
                 │      │~~~   水巷     │      │  (-154, 70, 146)
                 │   ~~~│       [水车]  │      │  22×16×20 (面西临水巷)
                 │~~~   │               │      │
                 └──────┴───────┬───────┴──────┘
                                │ (南郊出村道)
                              S (+Z)
```

### 1.3 固定核心地标精确坐标表（世界坐标，不随种子变化）

> **齐平放置硬约定（零跳跃入户！）**：所有建筑模板的局部 `y = 0` 均为**与世界地表齐平的路面/一楼地面层**。因此在世界放置时，必须设 **`originY = groundSurfaceY`（即把模板 `y = 0` 嵌入替换地表顶层方块，而非叠在地面之上！）**，确保门前路面、门槛与一楼室内地板完全齐平，玩家平步走入无需跳跃。

| ID | 名称 | 类型 | 原点 `(X, Y, Z)` | 尺寸 `(W×H×D)` | 朝向 | 门廊接入锚点 `(X, Y, Z)` |
|---|---|---|---|---|---|---|
| `hv_dragon_plaza` | 龙神像中央广场 | 装饰地标 | `(-214, 71, 86)` | `29×16×29` | 四向 | 四面直连 `X=-200` / `Z=100` 主道（`y=1` 广场面齐 `Y=72`） |
| `hv_hieda_residence` | 稗田阿求居所 | 功能地标 | `(-238, 75, -44)` | `76×28×64` | 南 (`+Z`) | `(-200, 75, 20)`（南石阶下接 `Z=26, Y=72`，`y=0` 嵌入 `Y=75` 台面） |
| `hv_suzunaan` | 铃奈庵（大书店） | 功能地标 | `(-248, 72, 114)` | `38×26×34` | 东 (`+X`) | `(-209, 72, 131)`（直连南主街，`y=0` 嵌入 `Y=72` 齐平入户） |
| `hv_geidontei` | 鲵吞亭（大居酒屋） | 功能地标 | `(-148, 72, 138)` | `42×29×36` | 西 (`-X`) | `(-149, 72, 155)`（临水巷东岸，`y=0` 嵌入 `Y=72` 齐平露台） |
| `hv_kourindou` | 香霖堂（古董大商行） | 功能地标 | `(-364, 72, 60)` | `44×25×36` | 南 (`+Z`) | `(-345, 72, 97)`（直连东西主干道西段，`y=0` 嵌入 `Y=72` 齐平双门） |
| `hv_gate_east` | 东村口冠木门+高札场 | 装饰地标 | `(-78, 72, 91)` | `14×12×19` | 东西向 | 跨 `Z=100` 主道，高札场在北侧 `(-70, 72, 93)` |
| `hv_gate_west` | 西村口冠木门 | 装饰地标 | `(-316, 72, 93)` | `10×11×15` | 东西向 | 跨 `Z=100` 主道（过此门向西即达林缘香霖堂） |
| `hv_gate_north` | 北村口冠木门 | 装饰地标 | `(-207, 72, -74)` | `15×11×10` | 南北向 | 跨 `X=-200` 北出村道 |
| `hv_watermill` | 溪畔水车小屋 | 装饰地标 | `(-182, 70, 156)` | `12×13×12` | 北 (`-Z`) | `(-176, 72, 155)`（紧邻溪岸水巷） |

---

## 2. 新增和风幻想建筑块清单（高阶模型设计并产出资源）

现有 `LandmarkBlocks` 有 10 种方块（`brick` / `wood` / `tile` / `tile_slab` / `tile_stairs` / `wood_door` / `wood_trapdoor` / `wood_fence` / `stone_wall` / `lamp`）。为达到最佳和风町家、书坊、居酒屋与名门大宅外观表现，新增 **9 种**可逐格染色方块：

| 注册 ID | 图纸后缀 | Java 方块类 | 材质/贴图 | 方块状态与特性 | 建筑用途 |
|---|---|---|---|---|---|
| `landmark_wood_slab` | `wood_slab` | `SlabBlock` | 复用 `landmark_wood` | `type`, `waterlogged` | 缘侧走廊、木拱桥面、吧台、低檐、长凳 |
| `landmark_wood_stairs` | `wood_stairs` | `StairBlock` | 复用 `landmark_wood` | `facing`, `half`, `shape`, `waterlogged` | 木台阶、木拱桥坡、屋架梁托、神龛顶 |
| `landmark_brick_slab` | `brick_slab` | `SlabBlock` | 复用 `landmark_brick` | `type`, `waterlogged` | 石垣压顶、青石桥面、庭院飞石、水井沿 |
| `landmark_brick_stairs` | `brick_stairs` | `StairBlock` | 复用 `landmark_brick` | `facing`, `half`, `shape`, `waterlogged` | 稗田宅大石阶、水岸石梯、龙神像基座 |
| `landmark_wood_fence_gate` | `wood_fence_gate` | `FenceGateBlock(WoodType.OAK)` | 复用 `landmark_wood` | `facing`, `open`, `in_wall`, `powered` | 庭院柴扉、吧台小门、栏杆出入口 |
| `landmark_shoji` | `shoji` | `IronBarsBlock`（薄板无遮挡） | **新贴图 `block/landmark_shoji`** | 四向连接 + `waterlogged`, `noOcclusion()` | 和纸障子窗、细木格子窗、虫笼窗 |
| `landmark_curtain` | `curtain` | `HorizontalDirectionalBlock`（自定义薄垂帘，无碰撞 `noCollission()`） | **新贴图 `block/landmark_curtain`** | `facing`（4向）, `noOcclusion()` | 铃奈庵/鲵吞亭/町家商铺**暖帘**、市集幡旗、酒旗 |
| `landmark_bookshelf` | `bookshelf` | `Block` | **新贴图 `block/landmark_bookshelf`** | 实心 `SoundType.WOOD` | 铃奈庵满墙书架、稗田家藏书文库、香霖堂杂货架 |
| `landmark_lantern` | `lantern` | `LanternBlock` | **新贴图 `block/landmark_lantern`** | `hanging`, `waterlogged`, `lightLevel(15)` | 檐下悬挂和纸提灯、鲵吞亭红灯笼、街灯 |

**同步修改点（防遗漏）**：
1. `LandmarkBlocks.tintedBlocks()` 与 `LandmarkBlocks.contains()` 必须包含以上全部 9 种新方块。
2. `ResidentGround.isPaving()` 必须新增允许：`"landmark_wood_slab"`, `"landmark_wood_stairs"`, `"landmark_brick_slab"`, `"landmark_brick_stairs"`。
3. 语言文件 `zh_cn.json` / `en_us.json` 为这 9 种方块添加条目（同旧块命名为“幻想建筑块” / `Gensokyo Building Block`）。

---

## 3. 局部坐标染色协议（GLC3）与客户端渲染架构

### 3.1 `GLC3` 二进制格式规范（资源路径：`assets/gensokyou/landmark/<template_id>.local.bin`）

使用 GZIP 压缩的 `DataOutputStream`（大端序）：

```text
Offset / Field              Type                 Description
------------------------------------------------------------------------------
magic                       int (4B)             固定 0x474C4333 ("GLC3")
sizeX, sizeY, sizeZ         unsigned short × 3   模板局部尺寸 (1..512)
paletteSize                 unsigned short       调色板长度 (1..256)，index 0 固定 NO_TINT
for i in 0..paletteSize-1:
  rgb                       int (4B)             0x00RRGGBB (index 0 写 0)
  roleId                    unsigned byte (1B)   材质角色槽位 (见 3.2，0=固定不换色)
expectedBlockCount          unsigned byte (1B)   预期方块种类数 N (0..255)
for i in 1..N:
  blockId                   UTF string           如 "gensokyou:landmark_wood"
entryCount                  int (4B)             非空染色格数量 M
for k in 0..entryCount-1:
  lx, ly, lz                unsigned short × 3   局部坐标 (0<=lx<sizeX, ...)
  colorIndex                unsigned byte (1B)   1..paletteSize-1
  blockIndex                unsigned byte (1B)   1..N
EOF
```

> **为何用稀疏列表存储而不是 4096 密集段？**
> 一栋民居（如 `11×11×11`）约 500–900 个非空染色方块，稀疏表解压后加载为密集一维扁平数组 `byte[sizeX * sizeY * sizeZ]`（两个数组：`colorIndices` 与 `blockIndices`，体积仅 ~2.6 KiB！），查询时直接 `idx = (ly * sizeZ + lz) * sizeX + lx`，**O(1) 无锁、零 GC、极低内存**。

### 3.2 运行时调色板角色槽位（`PaletteRole`）与 8 套町家主题

仅普通民居（及部分可换色市集摊位）使用角色槽位换色；4 座特殊地标可将所有颜色 `roleId = 0`（保持专属手调色）。

| `roleId` | 槽位名 | 对应建筑部位 |
|---|---|---|
| `0` | `FIXED` | 固定色（灯笼暖光、草木、特殊细节，不随主题替换） |
| `1` | `ROOF_MAIN` | 屋顶主瓦色 |
| `2` | `ROOF_RIDGE` | 屋脊/檐口浅瓦色 |
| `3` | `WALL_PLASTER` | 灰泥白墙 / 土藏壁 |
| `4` | `WOOD_FRAME` | 柱、梁、板壁主木构 |
| `5` | `WOOD_DARK` | 门框、踢脚、屋檐深木色 |
| `6` | `ACCENT_CURTAIN` | 暖帘、布幔、招牌点缀色 |
| `7` | `FOUNDATION_STONE` | 础石、石垣底座色 |

**8 套运行时调色板主题（`HumanVillagePalette` enum，`paletteThemeId = 0..7`）**：

| ID | 主题名 | ROOF_MAIN | ROOF_RIDGE | WALL_PLASTER | WOOD_FRAME | WOOD_DARK | ACCENT_CURTAIN | FOUNDATION_STONE |
|---|---|---|---|---|---|---|---|---|
| `0` | 经典黛瓦白壁 | `0x4B5E6F` | `0x687D8F` | `0xECE6D9` | `0x8A5D3B` | `0x4E3320` | `0x3A5F82`（靛蓝） | `0x8E8980` |
| `1` | 老铺黑瓦茶木 | `0x363A40` | `0x525861` | `0xE5DCCB` | `0x6F482D` | `0x3C2516` | `0x9E3D2E`（暗朱） | `0x7F7B74` |
| `2` | 清雅青瓦桧木 | `0x5A737A` | `0x79939A` | `0xF2EEE4` | `0xB58A5E` | `0x7A5635` | `0x4B775B`（松绿） | `0x969188` |
| `3` | 匠人赤瓦深杉 | `0x6B433B` | `0x885C52` | `0xE3D8C5` | `0x7C4E32` | `0x442818` | `0xC48A3A`（姜黄） | `0x867F76` |
| `4` | 茶屋朱帘暖木 | `0x485561` | `0x637280` | `0xEFE6D5` | `0x9B6A43` | `0x5B3A22` | `0xC14436`（朱红） | `0x8D877E` |
| `5` | 染坊蓝染灰瓦 | `0x555E66` | `0x737E88` | `0xE8E6DF` | `0x7A5A42` | `0x463224` | `0x2F4E7A`（深靛） | `0x8A8A86` |
| `6` | 商家紫帘白藏 | `0x3F4652` | `0x5C6573` | `0xF4F1EA` | `0x734C32` | `0x3E2719` | `0x6D4875`（江户紫） | `0x84817B` |
| `7` | 郊野茅褐素木 | `0x7D694D` | `0x988363` | `0xDFD3BC` | `0x9C754F` | `0x62462C` | `0x7A6A46`（麻褐） | `0x92897B` |

> `paletteThemeId = -1` 表示“不换色，直接使用 `GLC3` 文件里的默认 RGB”（4 大功能地标与装饰地标固定传 `-1`）。

### 3.3 Minecraft `Rotation` 精确放置与逆旋转公式（防偏移红线！）

当使用 `StructureTemplate.placeInWorld(level, anchor, anchor, new StructurePlaceSettings().setRotation(rot), ...)` 放置尺寸为 `(sx, sy, sz)` 的模板时，Minecraft 原版 `StructureTemplate.transform` 对局部坐标 `(lx, ly, lz)` 的变换为：
- `NONE`：`wx = ax + lx`, `wz = az + lz`
- `CLOCKWISE_90`：`wx = ax - lz`, `wz = az + lx`
- `CLOCKWISE_180`：`wx = ax - lx`, `wz = az - lz`
- `COUNTERCLOCKWISE_90`：`wx = ax + lz`, `wz = az - lx`

**极其重要**：为了避免负偏移导致的包围盒混乱，我们在 `HumanVillageManager` 中统一以**最小世界角 `minOrigin = (minX, minY, minZ)`** 记录 `BuildingInstance`，放置时再推算传给 `StructureTemplate` 的 `pivotAnchor`：

| `Rotation` | 旋转后世界尺寸 `(rw, rd)` | 传给 `placeInWorld` 的 `pivotAnchor` | 世界坐标 `(wx, wy, wz)` → 局部坐标 `(lx, ly, lz)` 逆映射 |
|---|---|---|---|
| `NONE` | `(sx, sz)` | `(minX, minY, minZ)` | `lx = wx - minX`, `lz = wz - minZ` |
| `CLOCKWISE_90` | `(sz, sx)` | `(minX + sz - 1, minY, minZ)` | `lx = wz - minZ`, `lz = (sz - 1) - (wx - minX)` |
| `CLOCKWISE_180` | `(sx, sz)` | `(minX + sx - 1, minY, minZ + sz - 1)` | `lx = (sx - 1) - (wx - minX)`, `lz = (sz - 1) - (wz - minZ)` |
| `COUNTERCLOCKWISE_90` | `(sz, sx)` | `(minX, minY, minZ + sx - 1)` | `lx = (sx - 1) - (wz - minZ)`, `lz = wx - minX` |

所有情况下 `ly = wy - minY`，边界检查为 `0 <= lx < sx && 0 <= ly < sy && 0 <= lz < sz`。
**下游模型必须为这 4 种旋转写单元测试**，验证 `forward(inverse(wx, wz)) == (wx, wz)` 且四角与中心点全部精确对应！

### 3.4 `LandmarkBlockColors` 三层查色管线

在 `LandmarkBlockColors.java` 的 `gensokyou:gensokyo` 分支中按如下优先级查色（任一层返回 `!= NO_TINT` 即返回）：

1. **Layer 1（兼容层）**：现有 `GLC2` 世界坐标数组（博丽神社主模板 + 外围三地标），代码不变。
2. **Layer 2（实例层 `VillageColorIndex`）**：
   - 客户端维护 `Long2ObjectOpenHashMap<List<ClientBuildingInstance>> chunkBuckets`，键为 `ChunkPos.asLong(cx, cz)`，把每个实例注册到其 `[minX>>4 .. maxX>>4] × [minZ>>4 .. maxZ>>4]` 覆盖的所有区块桶中。
   - 查色时取 `bucket = chunkBuckets.get(ChunkPos.asLong(x >> 4, z >> 4))`，遍历（通常 0–2 个元素），AABB 命中后用 3.3 节公式求 `(lx, ly, lz)`，向缓存的 `LocalLandmarkColors` 查 `(colorIndex, blockIndex)`，再经 `HumanVillagePalette` 映射返回 RGB。
3. **Layer 3（道路/广场/水渠零内存兜底层 `HumanVillageProceduralColors`）**：
   - 仅当 `(x, z)` 位于人间之里城区盒 `x ∈ [-420, 50], z ∈ [-120, 320]` 内时触发。
   - 按 `blockKey` 与确定性哈希 `h = mix32(x, y, z)` 返回固定和谐色：
     - `landmark_tile` / `landmark_tile_slab` / `landmark_tile_stairs`：石板路冷暖灰微差（`0x9C978E` / `0x918C83` / `0xA6A198`；位于主干道边缘 `|z-100|==2` 或 `|x+200|==2` 时取深青石边石 `0x6E7378`）
     - `landmark_brick` / `landmark_brick_slab` / `landmark_brick_stairs` / `landmark_stone_wall`：护岸与地基石垣灰褐（`0x847F77` / `0x7A756D` / `0x8F8A81`）
     - `landmark_wood` / `landmark_wood_slab` / `landmark_wood_stairs` / `landmark_wood_fence` / `landmark_wood_fence_gate`：跨溪木桥原木色（`0x8A5D3B` / `0x7A5132`）
     - `landmark_lamp` / `landmark_lantern`：暖黄灯笼光（`0xFFE09E`）

### 3.5 网络同步 `HumanVillageLayoutSyncPayload`

- **数据包结构**：
  ```java
  public record HumanVillageLayoutSyncPayload(List<InstanceEntry> instances) implements CustomPacketPayload {
      public record InstanceEntry(
          ResourceLocation templateId, // e.g. gensokyou:hv_machiya_v03
          int minX, int minY, int minZ,
          int sizeX, int sizeY, int sizeZ, // 模板原始尺寸
          byte rotationOrdinal,            // 0..3 对应 Rotation
          byte paletteThemeId              // -1 或 0..7
      ) {}
  }
  ```
- **发送时机**：
  1. `PlayerEvent.PlayerLoggedInEvent`（玩家在幻想乡维度时）
  2. `PlayerEvent.PlayerChangedDimensionEvent`（切入 `gensokyou:gensokyo` 时）
  3. 当 `HumanVillageManager` 首次为某世界计算完成 `HumanVillagePlan` 时，向当前处于幻想乡维度的所有玩家广播一次。
- **客户端接收处理**：
  - 在 `ClientVillageColors.applySync(payload)` 中重建 `chunkBuckets`（原子替换 volatile 引用，确保区块网格线程无锁安全读取）。
  - 遍历受影响区块段，调用 `Minecraft.getInstance().levelRenderer.setSectionDirty(cx, cy, cz)` 触发重绘（防止玩家先看到区块后收到包而停留在灰度状态）。
  - 退出世界/切出维度时（`ClientPlayerNetworkEvent.LoggingOut`）清空缓存。

---

## 4. 地形与贯城浅溪改造设计

### 4.1 `GensokyoLayout.java` 改造细节

当前 `GensokyoLayout.surfaceHeight()` 对所有非博丽区域统一执行一个半径 `72..176` 的圆形纯平 pad。需要为 `Region.HUMAN_VILLAGE` 替换为专用雕塑方法 `sculptHumanVillage(double height, int x, int z)`（**跳过通用圆形 pad 循环中的 `HUMAN_VILLAGE`**）：

```java
// 常量定义
public static final int VILLAGE_CENTER_X = -200;
public static final int VILLAGE_CENTER_Z = 100;
public static final int VILLAGE_BASE_Y = 72;
public static final int VILLAGE_STREAM_WATER_Y = 70;
public static final int VILLAGE_HIEDA_Y = 75;
```

**精确雕塑公式（按顺序叠加）**：

1. **中心商业台地 + 柔和盆地过渡**：
   - 计算到中心距离 `r = Math.hypot(x - (-200), z - 100)`。
   - 若 `r < 250.0`：
     - 目标微起伏基准 `micro = 0.8 * Math.sin(x / 55.0) * Math.cos(z / 55.0)`（仅在 `r > 120` 后线性生效，中心 `r <= 120` 严格为 `0.0`）。
     - `plateauTarget = 72.0 + micro * smoothstep((r - 120.0) / 80.0)`
     - `blend = 1.0 - smoothstep((r - 145.0) / 105.0)`（`r <= 145` 时 `blend = 1.0`，`r >= 250` 时 `blend = 0.0`）
     - `height = lerp(height, plateauTarget, blend)`

2. **村北稗田家名门高台（`Y = 75`）**：
   - 台地矩形：`minX = -242, maxX = -159, minZ = -48, maxZ = 20`（覆盖扩建后的稗田宅 `(-238..-163, -44..19)` 及外围 4 格石垣肩）。
   - 计算矩形外距 `hd = rectangleDistance(x, z, -242, -48, -159, 20)`。
   - 若 `hd < 14.0`：`height = lerp(height, 75.0, 1.0 - smoothstep(hd / 14.0))`。

3. **贯城浅溪河谷槽（`Y = 68.2`）**：
   - 浅溪东西贯通 `x ∈ [-360, 20]`，中心线函数（**固定不依赖 seed，确保核心水巷、鲵吞亭、水车与桥梁坐标绝对稳定**）：
     ```java
     public static double villageStreamCenterZ(int x) {
         // 在 X=-200(南主街) 处 Z ≈ 158，在 X=-154(鲵吞亭) 处 Z ≈ 148，向东缓折向东南
         double t = (x + 200.0) / 180.0;
         return 156.0 - 16.0 * Math.sin(t * 1.35) + 8.0 * Math.cos(t * 2.2);
     }
     ```
   - 校验关键点：
     - `x = -200`（南主街跨桥处）：`Z = 164.0`
     - `x = -176`（水车小屋处）：`Z ≈ 154.0`
     - `x = -158`（鲵吞亭西侧水巷）：`Z ≈ 149.0`
     - `x = -260`（村西南弯道）：`Z ≈ 166.0`
   - 河谷宽度与下切：
     - 当 `x ∈ [-360, 20]` 时，计算 `sd = Math.abs(z - villageStreamCenterZ(x))`。
     - 河道半宽 `W_WATER = 3.0`（水面总宽 6–7 格），护岸过渡到 `7.0` 格。
     - 若 `sd < 7.0`：
       - `t = smoothstep((sd - 2.6) / 4.4)`（`sd <= 2.6` 为平河床 `68.2`，`2.6..7.0` 爬升回岸边 `height`）
       - 河床中心微深：`bedY = 68.2 + 0.5 * (sd / 2.6)`
       - `height = lerp(bedY, height, t)`
     - 两端淡出：在 `x ∈ [-360, -320]` 和 `x ∈ [-20, 20]` 按 `smoothstep` 淡出回自然地形，避免突然截断。

4. **博丽参道东口自然衔接**：
   - 保持 `GensokyoLayout.HAKUREI_ROAD_END_X = 40` 不变。
   - 在 `x ∈ [0, 40]`、`hakureiRoadDistance` 附近，`sculptHumanVillage` 已经将 `X = 0..40, Z ≈ 100` 平滑落在 `Y ≈ 72`，与博丽参道终点高度 `72.0` 天然对齐，无需修改博丽参道主体代码。

5. **`LandmarkClearanceFilter.java` 更新**：
   - 将 `HUMAN_VILLAGE` 的野生树木净空半径从 `80` 扩大到 `215` 格（即 `dx*dx + dz*dz <= 215L * 215L` 时返回 `false`），避免野生神木大树长穿屋顶和街道。

### 4.2 浅溪显式填水与石护岸（`HumanVillageStreamPlacer`）

由于 `VILLAGE_STREAM_WATER_Y = 70 > SEA_LEVEL (63)`，在 `HumanVillageManager` 逐区块铺装村庄时（**优先于道路和桥放置，或作为区块初始化第一步**）：
1. 对区块内每个 `(x, z)`，若 `x ∈ [-345, 5]` 且 `sd = Math.abs(z - villageStreamCenterZ(x)) <= 4.2`：
   - 若 `sd <= 3.0`（水体区）：
     - `y = 68`：放 `gensokyou:gensokyo_sand` / `minecraft:gravel` 混合河床。
     - `y = 69, 70`：强制放置 `minecraft:water`（水源）。
     - `y = 71..76`：清理为 `minecraft:air`（桥梁随后在 `y = 72..73` 架设）。
   - 若 `3.0 < sd <= 4.2`（石护岸区）：
     - `y = 68..71`：放 `gensokyou:landmark_brick` 挡水护岸，确保 `y = 70` 的水绝不向低处漏水。
     - 在城区内（`x ∈ [-300, -90]`）每隔 10 格在岸顶 `y = 72` 放一盏石灯柱（`landmark_stone_wall` + `landmark_lamp` + `landmark_tile_slab`）。

---

## 5. 村庄排布与密度梯度算法（`HumanVillagePlanner`）

`HumanVillagePlanner.plan(long worldSeed, GensokyoLayout layout)` 是**纯函数**（无世界 I/O，输入 `(worldSeed, layout)`，输出不可变 `HumanVillagePlan`），便于编写高速 JUnit 单元测试！

### 5.1 核心数据结构

```java
public record HumanVillagePlan(
    long worldSeed,
    List<BuildingInstance> buildings,      // 含 4 大地标 + 装饰地标 + 55..75 普通民居
    List<RoadCell> roadCells,              // 光栅化后的全村路网 + 桥梁格
    Set<Long> protectedColumns             // 快速判断保护区 (可由 buildings AABB + 主干道推导)
) {}

public record BuildingInstance(
    ResourceLocation templateId,
    BuildingCategory category,             // SPECIAL_LANDMARK / DECOR_LANDMARK / RESIDENTIAL
    int minX, int minY, int minZ,
    int sizeX, int sizeY, int sizeZ,       // 模板未旋转尺寸
    Rotation rotation,
    int paletteThemeId,                    // -1 或 0..7
    int doorWorldX, int doorWorldY, int doorWorldZ, // 门廊接入锚点 (位于建筑包围盒外 1 格)
    boolean hasDoorAnchor
) {
    public int rotSizeX() { return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sizeZ : sizeX; }
    public int rotSizeZ() { return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sizeX : sizeZ; }
}
```

### 5.2 二维占用网格 `OccupancyGrid`

- 覆盖范围：`x ∈ [-420, 40]`（宽 461），`z ∈ [-120, 320]`（深 441）。
- 格子枚举值：
  - `EMPTY (0)`：可建空地
  - `STREAM (1)`：浅溪与护岸（`sd <= 5.0`），普通建筑禁入
  - `MAIN_ROAD (2)`：预留东西/南北主干道与中心环巷走廊，普通建筑禁入
  - `BUILDING (3)`：已放置建筑本体
  - `BUFFER (4)`：建筑周边 1 格通行/防火净空（不与其他建筑 `BUILDING` 重叠）

### 5.3 边缘禁建带校验（硬约束）

```java
static boolean isSafeFromBiomeEdge(GensokyoLayout layout, int cx, int cz) {
    // 1. 距人间之里中心不超过 200 格
    if (Math.hypot(cx - GensokyoLayout.VILLAGE_CENTER_X, cz - GensokyoLayout.VILLAGE_CENTER_Z) > 200.0) {
        return false;
    }
    // 2. 8 方向采样 220 格，确保远离群系边界（兼容任意种子的 regionWarp）
    final int MARGIN = 220;
    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    for (int[] d : dirs) {
        int sx = cx + (int) Math.round(d[0] * (d[0] != 0 && d[1] != 0 ? MARGIN * 0.7071 : MARGIN));
        int sz = cz + (int) Math.round(d[1] * (d[0] != 0 && d[1] != 0 ? MARGIN * 0.7071 : MARGIN));
        if (layout.regionAt(sx, sz) != GensokyoLayout.Region.HUMAN_VILLAGE) {
            return false;
        }
    }
    return layout.regionAt(cx, cz) == GensokyoLayout.Region.HUMAN_VILLAGE;
}
```

### 5.4 五步放置流程（确定性核心 + 种子微变外围）

1. **Step 1：标记水系与主干道预留走廊**
   - 标记浅溪 `sd <= 5.0` 为 `STREAM`。
   - 标记东西主干道（`z ∈ [97, 103], x ∈ [-385, 25]`）、南北主干道（`x ∈ [-203, -197], z ∈ [-65, 255]`）、以及核心环巷（矩形环 `x = -256, -144` 与 `z = 48, 140`，宽 3 格，左右各留 1 格走廊）为 `MAIN_ROAD`。

2. **Step 2：放置全部固定核心地标（不依赖 `worldSeed`）**
   - 按第 1.3 节精确坐标写入 4 座功能地标 + 龙神像广场 + 3 冠木门 + 水车小屋。
   - 在龙神像广场四角外缘和东西主干道两侧预留 **8 个固定市集摊位点**（具体哪个摊位放团子/鱼菜/和伞/古书可随种子或固定分配，其中核心 4 摊固定以配合假 NPC）。
   - 在固定核心商业街沿主干道两侧放置 **12 栋固定锚位核心町家**（模板与位置固定，调色板可随 `worldSeed` 微变），确保假 NPC 演出的核心街道两侧绝对饱满、永不穿墙。

3. **Step 3：径向三圈密度梯度生成普通民居（使用 `SplittableRandom(worldSeed ^ 0x4856_2026_CAFE_L)`）**
   - 按由内向外的顺序在 3 个同心带采样放置（目标总数 **55–75 栋**）：

   | 圈层 | 半径范围 `r` | 目标新增栋数 | 建筑间最小净空 | 偏向民居类型 | 采样方式 |
   |---|---|---|---|---|---|
   | **核心圈** | `30..110` | `20..26` 栋（含 12 栋固定） | `1` 格（紧密町家街墙） | 厨子二阶 / 本二阶 / 商铺暖帘 / 茶寮 / 藏造 | 优先紧贴主干道与核心环巷两侧网格步进采样（步长 2 格），面向道路 |
   | **中密町家圈** | `110..160` | `22..28` 栋 | `3..5` 格（带小巷与院落） | 平屋 / 厨子二阶 / 工坊 / 带院町家 | 泊松盘/抖动网格采样（网格 18×18，随机偏移 ±4 格），朝向最近主轴 |
   | **稀疏外郊圈** | `160..198` | `10..15` 栋 | `12..18` 格（疏朗农舍感） | 平屋小町家 / 带菜畦农舍（偏向主题 2/3/7） | 抖动网格采样（网格 32×32，随机偏移 ±7 格），跳过过陡坡地 |

   - **每栋候选屋的校验条件**：
     1. `isSafeFromBiomeEdge(layout, centerX, centerZ)` 必须为 `true`。
     2. 占地矩形内无 `STREAM`、`MAIN_ROAD`、`BUILDING`；外扩 `minClearance` 格内无 `BUILDING`。
     3. 地形坡度检查：测量占地四角 `surfaceHeight(x, z, 0.0)` 的 `minH` 与 `maxH`，要求 `maxH - minH <= 2.5`；令 `originY = (int) Math.round(avgH)`（模板 `y=0` 嵌入替换地表顶层格，与路面及室内地板完全齐平，玩家平步走入零跳跃！）。若建筑下方局部有 1–2 格低洼，放置时自动向下补填 `landmark_brick` 地基直至实心土石，绝不悬空！
     4. 朝向选择：计算候选点到最近主干道/环巷的方向，选择使大门朝向道路的 `Rotation`。
     5. 调色板选择：从 `0..7` 按圈层权重随机选取（外郊偏向素木/茅褐，核心偏向黛瓦/朱帘/黑瓦老铺）。

4. **Step 4：点缀街角微装饰地标（随种子微调）**
   - 在已放置民居之间的空隙或路边，尝试放置 `3..5` 个**街角古井**（`5×6×5`）、`3..4` 个**地藏小龛**（`3×5×3`）、`2..3` 个**神木休憩坛**（`7×8×7`）。

5. **Step 5：调用 `VillageRoadRouter` 生成连通道路与跨溪桥梁**（见第 6 节）。

---

## 6. 分层村庄道路与桥梁算法（`VillageRoadRouter`）

### 6.1 四级道路规格

| 级别 | 名称 | 宽度 | 铺装材质 | 街灯/附属 |
|---|---|---|---|---|
| `L1_MAIN` | 东西/南北主干道 | 5 格（半宽 2） | 中间 3 格 `landmark_tile`，两侧边石 1 格 `landmark_tile`（深色边石） | 每隔 14 格在路外 1 格设和纸街灯柱 |
| `L2_RING` | 核心环巷 / 临水酒巷 | 3 格（半宽 1） | `landmark_tile` 交错铺装 | 每隔 18 格单侧设灯柱 |
| `L3_BRANCH` | 民居门廊接入支路 | 2 格（主格 + 1 扩宽格） | `landmark_tile` / `landmark_brick` 混铺 | 路口转角偶设矮石灯 |
| `L4_BRIDGE` | 跨溪桥梁 | 同所在道路宽度 | `landmark_wood_slab` / `landmark_wood` 桥面 + 两侧 `landmark_wood_fence` 朱/木栏杆 + 桥头 `landmark_lantern` | 保持桥下 `Y <= 70` 水源完全贯通 |

### 6.2 精确布路算法（伪代码级别）

```text
Input: OccupancyGrid grid, List<BuildingInstance> buildings, GensokyoLayout layout
Output: Map<Long, RoadType> roadMap (key = packXZ(x, z))

1. 写入 L1_MAIN 骨架:
   - 东西主干道: x in [-380..40], z in [98..102]
     (注意: 在龙神像广场 [-214..-186, 86..114] 内由广场模板自带铺装，不覆盖)
     (在东端 x in [0..40] 宽度从 5 格渐收为 3 格，自然衔接 HakureiApproachRoadFeature)
   - 南北主干道: z in [-65..230], x in [-202..-198]
     (在稗田宅石阶处 z=20..26 顺滑衔接 Y=75->72)

2. 写入 L2_RING 骨架:
   - 北环: z = 48, x in [-256..-144] (宽 3)
   - 西环: x = -256, z in [48..140] (宽 3)
   - 东环: x = -144, z in [48..140] (宽 3)
   - 临水酒巷 (连接南主街、铃奈庵南侧、水车小屋、鲵吞亭门前):
     沿浅溪北岸 (z = round(villageStreamCenterZ(x)) - 5), x in [-256..-144] (宽 3)

3. L3_BRANCH: 门廊锚点 A* 接入主路网:
   - 收集所有 hasDoorAnchor == true 的建筑，按到中心距离 r 从小到大排序（核心屋先连，外围屋自然并入核心支路！）。
   - 对每栋建筑的 doorAnchor = (dx, dz):
     运行 4 邻域正交 A*，目标集合 = 当前 roadMap 中所有格（启发函数 h = 到最近已有路格的曼哈顿距离 * 0.2，或先选最近 3 个路格求最小曼哈顿距离）。
     单步代价 cost(from -> to):
       - 若 grid[to] == BUILDING: 不可通行 (跳过)
       - 若 to 在 roadMap 中: baseCost = 0.15  // 强烈鼓励复用已有支路，自然形成小巷！
       - 若 grid[to] == STREAM: baseCost = 5.5 // 允许跨溪，但尽量少造新桥，优先复用已有桥
       - 否则 (空地/BUFFER): baseCost = 1.0
       - 转弯惩罚: 若移动方向 != 上一步方向，加 +0.6（消除斜向锯齿！）
       - 坡度惩罚: 加 2.5 * Math.abs(surfaceY(to) - surfaceY(from))
       - 搜索上限: 最多展开 2500 节点，保证极快收敛 (<1ms/屋)
     若找到路径 path:
       将 path 上每个非已有主路的格子写入 roadMap (L3_BRANCH)，并在垂直方向扩宽 1 格（仅当该扩宽格不为 BUILDING 且不为 STREAM）使小巷宽 2 格。

4. L4_BRIDGE 跨溪升级:
   - 扫描 roadMap 中所有满足 |z - villageStreamCenterZ(x)| <= 3.6 的格子：
     将其标记为 L4_BRIDGE，桥面高度设为 Y = 72（河心 |sd| <= 1.2 处微拱至 Y = 72.5 使用 landmark_wood_slab top/bottom 过渡），两侧边缘加 landmark_wood_fence 栏杆，桥头两侧立灯柱。

5. 连通性校验（供单元测试断言）:
   - 从龙神像广场东口 (-185, 100) 对 roadMap 做 BFS。
   - 断言：4 座特殊地标门廊 100% 连通；全体普通民居门廊 >= 90% 连通。
```

---

## 7. 建筑设计规格总表（由高阶模型在 Python 中实现并实机审视）

所有建筑均采用 Python 导出器（参考 `tools/export_hakurei_outer_grounds.py`），输出 `data/gensokyou/structure/<id>.nbt` 与 `assets/gensokyou/landmark/<id>.local.bin`（**GLC3 格式**），并配套生成俯视/立面/等轴测预览图与游戏内 `/gs_village preview` 展场。

### 7.1 四座特殊 NPC 功能地标

1. **`hv_kourindou`（香霖堂，`24×16×20`，村西林缘 `(-348, 71, 76)`）**
   - **气质**：半西式半和风的复古古董杂货店，坐落于魔法之森边缘，木构偏深褐温润，青灰瓦入母屋/切妻屋顶，门口悬木牌匾与复古和纸提灯，店侧带杂物堆放廊与小篱笆。
   - **室内**：宽敞古董陈列厅（`landmark_bookshelf` 货架阵列 + 柜台 + 外界奇物陈列台 + 暖炉区），预留森近霖之助（`RinnosukeEntity`）站位。

2. **`hv_geidontei`（鲵吞亭，`22×16×20`，东南水巷 `(-154, 70, 146)`）**
   - **气质**：《醉蝶华》风隐秘临水居酒屋，深木格栅外墙、大面积朱红暖帘（`landmark_curtain`）与檐下连排红提灯（`landmark_lantern`），临溪一侧挑出观水木露台（`landmark_wood_slab`），夜间水面映红光。
   - **室内**：L 形居酒屋吧台 + 矮凳 + 酒坛陈列架 + 后厨灶台 + 二楼雅座包厢。

3. **`hv_suzunaan`（铃奈庵，`20×16×18`，商街西南 `(-238, 71, 114)`）**
   - **气质**：人间之里核心书坊/借书屋，经典江户二阶大町家造型，白灰泥墙配靛蓝暖帘，敞开式一楼店面面向街道展示满墙书架，二楼虫笼窗与起居藏书室。
   - **室内**：三面环绕 `landmark_bookshelf` 书架 + 中心阅览矮桌 + 掌柜记账柜台 + 木梯通二楼。

4. **`hv_hieda_residence`（稗田阿求居所 / 稗田家宅邸，`48×22×38`，村北高台 `(-224, 74, -18)`）**
   - **气质**：人间之里最高规格的名门宅邸！高台石垣 + 正南庄严**长屋门** + 白壁黑瓦围墙环抱；入内为回游式**枯山水/池泉庭院**（砂纹、庭石、石灯笼、小拱桥）；北侧居中为**主殿（书院造+宽缘侧）**，东侧独立**白壁土藏文库（《幻想乡缘起》藏书楼）**，西侧为茶室偏殿。

### 7.2 非功能装饰地标群

| ID | 尺寸 | 外观要点 |
|---|---|---|
| `hv_dragon_plaza` | `29×16×29` | 中心三层石基座 + 盘龙石雕像（石墙/半砖/楼梯精细塑形）+ 四方浅水池 + 八角石板广场 + 四角大石灯笼 + 环绕歇脚石凳 |
| `hv_gate_east` | `14×12×19` | 东村口大气冠木门（朱/深木双柱挑檐瓦顶）+ 挂灯 + 北侧木顶**高札场**（村中告示牌） |
| `hv_gate_west` / `hv_gate_north` | `10×11×15` / `15×11×10` | 简朴木瓦冠木门 + 路边石灯 |
| `hv_watermill` | `12×12×12` | 临溪木构磨坊 + 探入溪水的木制水车轮（`landmark_wood_fence` / `wood_slab` 轮辐） |
| `hv_stall_dango` / `fish` / `umbrella` / `books` | `5×5×4` | 4 种江户市集小摊（斜瓦/布篷顶 + 暖帘 + 货架/长凳 + 提灯） |
| `hv_well` / `hv_jizo` / `hv_rest_tree` | `5×6×5` / `3×5×3` / `7×8×7` | 带木屋顶古井、路边地藏小龛、石围栏神木休憩坛 |

### 7.3 普通 NPC 民居：1 套参数化町家 Base → 10 种变体矩阵

在 Python 中实现 **唯一的** `build_machiya_base(spec: MachiyaSpec)` 参数化引擎，通过组合正交参数导出 10 个共享 `PaletteRole` 槽位的 `GLC3` 模板（再由 Java 运行时叠加 8 套调色板 × 4 旋转 = 320 种视觉组合）：

| 模板 ID | 尺寸 `(W×H×D)` | 层数/体量 | 临街门脸 | 屋顶/附属特色 | 适用圈层 |
|---|---|---|---|---|---|
| `hv_machiya_v01` | `9×12×10` | 平屋小町家 | 细木格子民居 | 切妻瓦顶 + 狗矢来矮栅 | 中圈 / 外郊 |
| `hv_machiya_v02` | `11×12×10` | 宽平屋民居 | 格子窗 + 侧柴扉 | 侧边小花坛围院 | 中圈 / 外郊 |
| `hv_machiya_v03` | `9×14×11` | 厨子二阶 | 暖帘商铺（米/茶铺） | 1.5 层虫笼窗 + 挑檐货架 | 核心 / 中圈 |
| `hv_machiya_v04` | `11×14×11` | 厨子二阶 | 暖帘商铺（布/杂货） | 双开暖帘 + 侧窗提灯 | 核心 / 中圈 |
| `hv_machiya_v05` | `11×16×12` | 本二阶大町家 | 敞开茶寮/点心铺 | 完整二层出挑檐 + 檐下红凳 | 核心商街 |
| `hv_machiya_v06` | `13×16×12` | 本二阶大町家 | 宽门面老字号商号 | 双层格栅 + 二楼观街栏杆 | 核心商街 |
| `hv_machiya_v07` | `9×15×11` | 藏造土藏 | 厚白灰泥防火库门 | 高脊黑瓦 + 铁格小窗 | 核心 / 中圈 |
| `hv_machiya_v08` | `11×13×12` | 平屋带工坊 | 宽门板木工/染作坊 | 侧院晾布架/木料棚 | 中圈 |
| `hv_machiya_v09` | `13×15×13` | L 型转角町家 | 双向临街门脸 | 转角挑檐灯笼（街头拐角） | 核心 / 中圈 |
| `hv_machiya_v10` | `11×13×13` | 郊野农舍町家 | 朴素木门 + 前廊 | 侧边灵土菜畦 + 矮木篱笆 | 外郊圈 |

> 所有民居变体统一约定：未旋转（`NONE`）时大门位于**南侧 `+Z` 边中心**，门廊接入锚点为局部坐标 `(W/2, 1, D)`（即南墙外 1 格）。

---

## 8. 假 NPC 繁华演出编排（`assets/gensokyou/village_residents/human_village.json`）

遵循 `docs/client-village-residents.md` 硬限制：总人数 `<= 128`（本方案精准规划 **116 名**，留 12 名余量），每条路线 `<= 16` 点，相邻点 `0.05..64` 格，**100% 落位在固定核心区与主干道的 `landmark_*` 铺装上**，绝不与外围随种子微变的民居冲突。

| 演出舞台 | 坐标区范围（相对 `origin: [-200, 72, 100]`） | 分配人数 | 动静编排设计 |
|---|---|---|---|
| **1. 龙神像中央广场** | `dx ∈ [-14, 14], dz ∈ [-14, 14]` | **28 人** | 8 组两三人对立驻足交谈（环绕龙神像与石凳）+ 10 名沿八角广场顺/逆时针漫步游人 |
| **2. 东西主干道与市集** | `dx ∈ [-105, 118], dz ∈ [-3, 3]` | **30 人** | 4 处固定市集摊位每摊 1 摊贩 + 2 顾客驻足 + 18 名往返东西主街的行人（分段折线，防拥堵） |
| **3. 铃奈庵与书坊街** | `dx ∈ [-36, -10], dz ∈ [14, 34]` | **16 人** | 铃奈庵门前 4 名看书/交谈读者 + 书坊街两侧 12 名逛街行人 |
| **4. 鲵吞亭与临水酒巷** | `dx ∈ [-20, 56], dz ∈ [44, 62]` | **18 人** | 鲵吞亭门前暖帘下 4 名驻足酒客 + 跨溪桥头 4 名凭栏观水居民 + 水巷沿溪漫步 10 人 |
| **5. 稗田家门前与北商街** | `dx ∈ [-12, 12], dz ∈ [-80, -16]` | **14 人** | 稗田宅长屋门石阶下 2 名驻足侍者/访客 + 北主商街 12 名往来居民 |
| **6. 东门高札场 & 香霖堂西道** | 高札场 `dx ≈ +130, dz ≈ -7`；西道 `dx ∈ [-140, -100]` | **10 人** | 高札场前 4 名读布告村民 + 东/西门进出旅人 6 名（含香霖堂门前驻足访客 2 名） |

> **防穿墙校验**：在 JUnit 测试中加载正式 `human_village.json` 并对照固定核心 `HumanVillagePlan`，断言每个居民的所有 `points` 均落在 `roadCells` 或固定广场/地标外部铺装上，不落入建筑实心室内或水面！

---

## 9. 放置生命周期、保护区、预览命令与测试验收

### 9.1 `HumanVillageManager`（按区块懒加载放置，杜绝卡顿）

人间之里覆盖约 `28 × 28` 个区块，**禁止**在首次进入时像小卫星地标那样一次性 `preload` 全部 784 个区块！改为**分块/按需放置**：
1. `HumanVillageSavedData` 持久化：`boolean planned`、`Set<Long> placedChunks`、`Set<Integer> placedBuildingIds`（以及记录 `worldSeed`）。
2. 当 `ChunkEvent.Load` 触发且区块位于人间之里包围盒内时，通过 `server.tell(new TickTask(...))` 异步执行 `ensureChunkPopulated(level, chunkPos)`：
   - 首次调用时运行 `HumanVillagePlanner.plan(level.getSeed(), layout)` 并缓存于服务端内存；
   - 对该区块执行浅溪填水与护岸；
   - 对该区块铺设 `roadCells` 中落在本区块的道路与桥梁格；
   - 对所有包围盒与本区块相交且尚未放置的 `BuildingInstance`：仅当其覆盖的少量区块（通常 1–4 个区块）预加载后，调用 `template.placeInWorld(...)` 并补齐下方 `landmark_brick` 悬空地基，随后记录进 `placedBuildingIds`。
3. 保护区禁放：在 `HakureiPreviewEvents.preventPlacement`（或独立的 `HumanVillageEvents`）中拦截生存模式下在任意已放置建筑包围盒及主干道/广场保护区内的放置行为。

### 9.2 调试与实机验收命令（`/gs_village`）

1. **`/gs_village showcase`**（供用户实机逐栋审视建筑外观！）：
   - 在 `gensokyou:landmark_sample_v1` 独立平坦展场维度中，将 **4 座特殊地标 + 装饰地标群 + 10 种民居变体（带不同调色板）** 整齐排成一条观光展道，并自动传送玩家至入口；同时配合子命令 `/gs_village showcase <name>` 一键直飞指定建筑正门前。
2. **`/gs_village visit <dragon|hieda|suzunaan|geidontei|kourindou|east_gate>`**：
   - 在 `gensokyou:gensokyo` 维度中传送至对应地标门前，自动确保周边区块已放置并同步客户端染色。
3. **`/gs_village stats`**：
   - 打印当前世界人间之里的建筑总数、各圈层栋数、道路格数、连通率与最近群系边界距离。

### 9.3 单元测试矩阵（下游模型必须全部写齐并通过）

| 测试类 | 核心断言 |
|---|---|
| `LocalLandmarkColorsTest` | `GLC3` 读写往返、4 种 `Rotation` 正逆映射完全一致、8 套 `HumanVillagePalette` 角色替换准确、固定槽位 `roleId=0` 不被替换 |
| `HumanVillageTerrainTest` | 多世界种子下中心台地 `Y=72`、稗田高台 `Y=75`、浅溪河床 `Y=68..69`、博丽参道东口衔接无断崖 |
| `HumanVillagePlannerTest` | 多世界种子下：边缘禁建 `>= 220` 格、半径 `<= 200` 格、三圈密度严格递减、建筑零重叠、不侵占河道、固定核心跨种子一致、外围民居跨种子有差异 |
| `VillageRoadRouterTest` | 4 大地标 100% 连通、普通民居 `>= 90%` 连通、跨溪路段自动生成桥梁、不穿透建筑本体 |
| `HumanVillageResidentsAlignmentTest` | `human_village.json` 中全部 116+ 居民点位 100% 落在核心固定铺装上，零穿墙、零落水 |
