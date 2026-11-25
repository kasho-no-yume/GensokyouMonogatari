# Specification: 人间之里群系全面改造（Human Village Rework）

## ADDED Requirements

### Requirement: 局部坐标实例染色与程序化道路着色（GLC3）
系统 MUST 支持将幻想建筑块（`gensokyou:landmark_*`）的染色数据按模板局部坐标保存（`GLC3` 格式），并在客户端按建筑实例的 `(origin, rotation, paletteThemeId)` 动态查色；同时 MUST 保持旧的世界坐标 `GLC2` 格式 100% 兼容。

#### Scenario: 同一民居模板在不同位置、不同旋转、不同调色板下正确渲染
- **WHEN** 服务端在幻想乡维度放置两个相同 `templateId` 的民居实例，分别具有不同的世界原点、不同的 `Rotation`（如 `NONE` 与 `CLOCKWISE_90`）以及不同的 `paletteThemeId`
- **THEN** 客户端在渲染两个实例的方块时，SHALL 各自按逆旋转映射回局部坐标查得对应的颜色槽位，并应用各自的调色板 RGB；内存中该模板的 `LocalLandmarkColors` 只加载 1 份

#### Scenario: 旧地标 GLC2 不受影响
- **WHEN** 玩家访问博丽神社、博丽外围三地标或 `landmark_sample_v1` 样件维度
- **THEN** 原有 `GLC2` 世界坐标查色逻辑 SHALL 优先或正常命中，渲染颜色与改造前完全一致

#### Scenario: 道路、广场与水渠护岸零内存程序化着色
- **WHEN** 客户端渲染位于人间之里城区范围内、且不在任何建筑实例包围盒内的 `landmark_tile` / `landmark_brick` / `landmark_wood` / `landmark_lamp` / `landmark_stone_wall` 及对应半砖楼梯
- **THEN** 客户端 SHALL 通过确定性坐标哈希配色函数返回和谐的石板/青砖/木桥/暖灯 RGB，无需为其生成 `.bin` 文件

#### Scenario: 联机与重进世界客户端同步
- **WHEN** 玩家登录或切换进入 `gensokyou:gensokyo` 维度
- **THEN** 服务端 SHALL 向该玩家发送 `HumanVillageLayoutSyncPayload`（包含当前世界生成的人间之里全部建筑实例列表），客户端收到后立即构建区块空间索引并刷新受影响区块的渲染

---

### Requirement: 扩展和风幻想建筑块
系统 MUST 在 `LandmarkBlocks` 中补充和风町家与庭院所需的灰模建筑块（均不可破坏、防活塞、支持 `tintindex = 0`），并在 `ResidentGround.isPaving()` 中放行新增的可行走地面半砖/楼梯。

#### Scenario: 新增建筑块支持染色与防破坏
- **WHEN** 建筑模板中使用新增的木半砖、木楼梯、砖半砖、砖楼梯、和纸障子、暖帘、书架、栅栏门、提灯等 `landmark_*` 方块
- **THEN** 这些方块在生存模式下不可破坏、防活塞，且能被 `GLC2`/`GLC3` 正确染色

#### Scenario: 假 NPC 可在木桥与石阶上正常行走
- **WHEN** 客户端环境居民路线经过 `landmark_wood_slab`、`landmark_wood_stairs`、`landmark_brick_slab` 或 `landmark_brick_stairs`
- **THEN** `ResidentGround.isPaving()` SHALL 返回 `true`，居民不会在桥面或石阶上消失或卡住

---

### Requirement: 微高差台地与贯城浅溪地形
系统 MUST 改造 `GensokyoLayout` 中 `HUMAN_VILLAGE` 区域的地形高度模型，形成核心商业台地、村北高台、贯城浅溪河谷与村西林缘缓坡，并显式填充浅溪水体。

#### Scenario: 分区高程稳定且无悬空/深坑
- **WHEN** 在任意世界种子下采样人间之里地形高度 `surfaceHeight(x, z, noise)`
- **THEN** 中心商业核心台地（`(-200, 100)` 周边）SHALL 稳定平缓在 `Y = 72`（波动 ≤ 1 格）；村北稗田宅台地（约 `(-200, 14)`）SHALL 抬升至 `Y = 75` 并平滑过渡；浅溪中心线处河床 SHALL 下挖至 `Y = 68..69`

#### Scenario: 浅溪水体在高于海平面时仍保持满水且不漫溢
- **WHEN** 浅溪所在区块完成地形装饰（Feature 阶段）
- **THEN** 河道内部 `Y <= 70` 的空气格 SHALL 被填充为 `minecraft:water`（水源），两侧修筑 `Y = 71..72` 石护岸防止水体向低洼处漫流

#### Scenario: 博丽参道自然衔接
- **WHEN** 玩家沿博丽参道向西行至 `X = 40, Z ≈ 100`
- **THEN** 参道终点 SHALL 自然汇入人间之里东侧外围村道，无断崖或建筑阻挡

---

### Requirement: 边缘禁建带与径向密度梯度村庄排布
系统 MUST 通过确定性核心 + 种子微变外围的 `HumanVillagePlanner` 排布建筑，严格遵守边缘禁建带与中心高密、外围稀疏的梯度规则。

#### Scenario: 群系边缘无任何房屋
- **WHEN** 对任意世界种子生成人间之里布局
- **THEN** 任意已放置建筑的包围盒中心到最近非 `HUMAN_VILLAGE` 群系格子的距离 SHALL 大于等于 `220` 格，且所有建筑均位于中心 `(-200, 100)` 半径 `200` 格以内

#### Scenario: 越靠近中心房屋越密集
- **WHEN** 统计核心圈（`r <= 110`）、中圈（`110 < r <= 160`）与外郊圈（`160 < r <= 200`）的单位面积建筑覆盖率
- **THEN** 建筑密度 SHALL 严格满足 `核心圈 > 中圈 > 外郊圈`，且外郊圈民居间距显著大于核心商业街

#### Scenario: 大体布局一致且随种子有细微变化
- **WHEN** 使用两个不同世界种子分别运行 `HumanVillagePlanner.plan(seed)`
- **THEN** 4 座特殊 NPC 地标、龙神像广场、主干道与核心表演街区坐标 SHALL 完全一致；外围民居的具体选型、调色板、微调位置与次级支路 SHALL 随种子产生差异

#### Scenario: 无建筑重叠与侵占河道
- **WHEN** 布局求解完成
- **THEN** 任意两栋建筑的水平包围盒（含 1 格净空）SHALL 不相交，且非桥/非水车建筑的占地 SHALL 不侵入浅溪水面

---

### Requirement: 分层道路生成与自动架桥算法
系统 MUST 提供确定性道路求解器 `VillageRoadRouter`，生成覆盖主干道、次级环巷/水巷、门廊支路与跨溪桥梁的连通网络。

#### Scenario: 连接全部特殊地标与绝大多数普通房屋
- **WHEN** 道路算法对 `HumanVillagePlanner` 产出的建筑集合完成布路
- **THEN** 全部 4 座特殊 NPC 地标、龙神像广场及至少 `90%` 以上的普通民居的门廊锚点 SHALL 与主路网处于同一连通分量

#### Scenario: 支路自然复用已有道路且无斜向锯齿
- **WHEN** 为民居门廊锚点寻路接入最近道路
- **THEN** 寻路器 SHALL 采用 4 邻域正交 A*，对已有道路格给予极低代价权重（优先并道形成丁字路口/小巷），对转弯与坡度施加惩罚

#### Scenario: 道路跨溪自动生成桥梁
- **WHEN** 道路格穿过浅溪河道范围
- **THEN** 该段道路 SHALL 抬升为跨溪木桥/石桥（`Y = 72..73`），保留桥下水面贯通，不填堵河道

---

### Requirement: 四大特殊地标、装饰地标与 1 Base ≥ 8 变体普通民居
系统 MUST 包含 4 座风格鲜明的特殊 NPC 地标、不少于 6 类非功能装饰地标，以及由 1 套参数化江户町家 Base 派生的不少于 8 种（目标 10 种）普通民居变体。

#### Scenario: 四座特殊地标各具原著特色与空间辨识度
- **WHEN** 玩家实机参观香霖堂、鲵吞亭、铃奈庵与稗田阿求居所
- **THEN** 四座建筑 SHALL 分别呈现：香霖堂（村西林缘古董杂货铺）、鲵吞亭（村东南临水隐巷居酒屋+红提灯）、铃奈庵（商街借书屋+满墙书架+暖帘）、稗田阿求居所（村北高台名门大宅+长屋门+庭院+藏书文库），且全部位于不可破坏保护区内

#### Scenario: 普通民居由单套 Base 参数化派生且视觉丰富
- **WHEN** 检查普通民居生成器与实机街景
- **THEN** 至少 8 种（目标 10 种）普通民居模板 SHALL 由同一套町家 Base 参数化派生，并在世界放置时叠加运行时调色板与 4 向旋转，避免街区单调重复

---

### Requirement: 假 NPC 繁华村庄演出联动
系统 MUST 更新 `assets/gensokyou/village_residents/human_village.json`，使客户端环境居民精准配合定稿后的广场、商街、地标门前、市集与水巷进行繁华演出。

#### Scenario: 假 NPC 零穿墙、零悬空、高活跃度
- **WHEN** 玩家站在中央龙神像广场及主商业街巡游
- **THEN** `human_village.json` 中的全部居民点位 SHALL 落在固定核心区的 `landmark_*` 铺装上，路线不穿过任何建筑墙体或水面，且核心视野内可稳定呈现热闹的驻足交谈与往来步行人群
