# Tasks: 人间之里群系全面改造（Rework Human Village）

> **当前阶段：玩家附近的街巷人流补充（Phase 6）。** Phase 1～5 保留此前工程记录，旧半径/路网/高度和“一户一个外围点、48 人预算”已被现行设计与 Phase 6 替代。历史构建/探针通过不代表本轮通过；新版实机验收未完成。

> **分工标记**：
> - **[高阶模型 / 建筑师]**：新增建筑块设计、4 大特殊地标、装饰地标群、1 Base 派生 10 种民居模板的 Python 实现、预览渲染与配合用户实机迭代验收。
> - **[下游模型 / 照图实现]**：严格按 `design.md` 实现染色底层、地形、规划器、道路算法、懒加载放置、网络同步、假 NPC 配置与单元测试。

---

## Phase 1: 新增和风幻想建筑块与染色底座

- [x] 1.1 **[高阶模型]** 在 `LandmarkBlocks.java` 注册 9 种新建筑块（`wood_slab`, `wood_stairs`, `brick_slab`, `brick_stairs`, `wood_fence_gate`, `shoji`, `curtain`, `bookshelf`, `lantern`），同步更新 `tintedBlocks()`、`contains()` 与 `zh_cn.json` / `en_us.json`
- [x] 1.2 **[高阶模型]** 使用 `tools/gen_tex.py` 产出 4 张新灰度贴图（`landmark_shoji`, `landmark_curtain`, `landmark_bookshelf`, `landmark_lantern`）及 9 种新方块的 blockstate/model JSON
- [x] 1.3 **[下游模型]** 扩展 `ResidentGround.isPaving()` 放行 `landmark_wood_slab`, `landmark_wood_stairs`, `landmark_brick_slab`, `landmark_brick_stairs`
- [x] 1.4 **[下游模型]** 实现 `LocalLandmarkColors.java`（`GLC3` 二进制读写）、`HumanVillagePalette.java`（8 套调色板 + 角色替换）与 4 向逆旋转工具（主类已随展场预置，下游模型补全 `LocalLandmarkColorsTest` 单元测试）
- [x] 1.5 **[下游模型]** 实现 `HumanVillageLayoutSyncPayload.java`、`ClientVillageColors.java`（区块空间索引 + `setSectionDirty` 刷新）及 `HumanVillageProceduralColors.java`（道路/护岸兜底着色），接入 `LandmarkBlockColors.java` 三层查色管线

## Phase 2: 建筑设计实现与实机视觉验收（核心美术交付）

- [x] 2.1 **[高阶模型]** 编写共用 Python 建筑框架 `tools/export_human_village_common.py`（支持 19 种 `landmark_*` 方块、`GLC3` 导出、`PaletteRole` 标注、离线俯视/立面/等轴测预览图渲染、blockstate 合法性校验）
- [x] 2.2 **[高阶模型]** 设计并导出 1 套参数化町家 Base 引擎 `tools/export_human_village_houses.py`，派生 `hv_machiya_v01` ~ `hv_machiya_v10` 共 10 种民居模板（`.nbt` + `.local.bin`）
- [x] 2.3 **[高阶模型]** 设计并导出 4 座特殊 NPC 功能地标 `tools/export_human_village_landmarks.py`：香霖堂（`hv_kourindou`）、鲵吞亭（`hv_geidontei`）、铃奈庵（`hv_suzunaan`）、稗田阿求居所（`hv_hieda_residence`）
- [x] 2.4 **[高阶模型]** 设计并导出非功能装饰地标群 `tools/export_human_village_decor.py`：龙神像中央广场、东/西/北冠木门与高札场、水车小屋、4 类市集摊位、古井/地藏小龛/神木休憩坛
- [x] 2.5 **[高阶模型 + 用户实机验收]** 提供 `/gs_village showcase` 独立展场命令与渲染预览图，由用户逐一检查 4 大地标与 10 种民居变体外观，按反馈迭代打磨直至全部确认满意

## Phase 3: 第一版地形、排布与路网（历史实现）

- [x] 3.1 **[下游模型]** 改造 `GensokyoLayout.java` 实现 `sculptHumanVillage()`（中心台地 `Y=72`、村北稗田高台 `Y=75`、贯城浅溪槽 `Y=68.2`、东口自然衔接），更新 `LandmarkClearanceFilter.java`（人间之里野生树净空半径 `215`），编写 `HumanVillageTerrainTest`
- [x] 3.2 **[下游模型]** 实现 `HumanVillagePlanner.java`（`OccupancyGrid`、边缘禁建 `>= 220` 格、固定核心 + 种子微变三圈密度梯度、坡度校验与地基深度计算），编写 `HumanVillagePlannerTest`
- [x] 3.3 **[下游模型]** 实现 `VillageRoadRouter.java`（4 级路网：主干道、核心环巷/水巷、4 邻域正交 A* 门廊接路、跨溪自动架桥与桥头灯柱），编写 `VillageRoadRouterTest`

## Phase 4: 世界放置、保护区、假 NPC 演出与集成验收

- [x] 4.1 **[下游模型]** 实现 `HumanVillageManager.java`、`HumanVillageSavedData.java` 与 `HumanVillageStreamPlacer.java`（按区块懒加载放置、浅溪显式填水至 `Y=70` 与石护岸、悬空地基自动补填）
- [x] 4.2 **[下游模型]** 实现 `HumanVillageEvents.java`（`ChunkEvent.Load` 异步入队、玩家登录/切维度发送 `HumanVillageLayoutSyncPayload`、保护区禁放拦截、`/gs_village visit|stats` 命令）
- [x] 4.3 **[下游模型]** 编写完整 116+ 槽位 `assets/gensokyou/village_residents/human_village.json`（覆盖龙神广场、东西主街与市集、铃奈庵、鲵吞亭水巷、稗田宅门前、高札场与香霖堂西道），并编写 `HumanVillageResidentsAlignmentTest`
- [ ] 4.4 **[共同验收，转入新版验收]** 第一版未完成客户端验收；当前以 Phase 5 的新世界验收为准，不因历史构建记录勾选完成

## 第一版实现与验证记录（历史，不代表本轮通过）

- 已完成工程任务 1.3–1.5、3.1–3.3、4.1–4.3。坐标冲突的用户确认修订见 `design.md` 开头，建筑 NBT/贴图保持原美术交付。
- 全量 `build` 已完成：1448 测试、213 测试类，失败/错误/跳过均为 0，打包守卫通过。耗时 13 分 10 秒；前台 wrapper 工具先超时，但分离运行的 Gradle 完成，`build/agent-logs/20261125-235755-build.log.exit` 为 `0`。摘要在 `build/agent-logs/human-village-full-suite.json`。
- 原生隔离服务端已验证：区块裁剪不额外加载邻块、重复放置幂等、完整计划及进度存档往返、河床水源与桥下贯通、保护区放置拦截、116 槽位共 7829 个真实方块路线采样点可走。最后的圈层计数和东口楼梯修订另做针对性回归及新隔离世界复验。
- `lang_audit.py` 的 Java 字面量键检查通过；全仓审计仍报告 HEAD 已存在的 32 个中文数据键缺项及 4 个英文独有书籍键。本次新增的 5 个村庄消息键中英文齐全，未扩大差集。
- **4.4 保持未勾选，等待客户端共同验收**：使用 `/gs_village visit dragon` 巡游，依次查看 `hieda`、`suzunaan`、`geidontei`、`kourindou`、`east_gate`，检查染色、街景、露台/门槛、北绕行道、桥栏与东口过渡；检查切维度/重连后的染色刷新及居民观感/性能。微地形应在新生成区块验收，旧区块只补建村庄，不会整体重新生成自然地形。

## Phase 5: 问题记录 2～4 与用户补充的修订

- [x] 5.1 新增完整彩色夯实村土 landmark、顶/侧贴图和无 tint 模型，接入 Worldgen 表层、居民支持面与整地保护
- [x] 5.2 用实际群系采样边界、归一化密度和朝向共享空地的错列住宅组团替代固定半径及三圈栋数，采用稀疏占用网格
- [x] 5.3 移除逐户 A* 支路，将核心公共留白改为土面，入口局部铺石，保留必要桥梁；统一 v2 地表与地板高度
- [x] 5.4 加入 v1/v2 布局持久化、版本同步及区块建筑索引，保留旧世界布局/高度/进度，不自动重建
- [x] 5.5 从房屋描述派生附近外围环境居民，并按布局版本适配核心高度；共用 512 候选、48 活动、96 格预算
- [x] 5.6 维护范围/覆盖/密度、留白可达、版本往返、核心路线及外围门口旋转等回归源码；同步 proposal、design、spec 和使用文档
- [ ] 5.7 运行并确认新版 Java 编译/JUnit/原生探针；仅在后续明确要求时启动，不引用第一版通过记录
- [ ] 5.8 新世界实机确认边缘到核心的连续聚落、内区紧凑度、自然街巷、土/石/门槛齐平、桥溪、四大地标及外围居民；记录帧耗、重连与低视距表现

本轮轻量检查与限制见 [verification-v2.md](verification-v2.md)。未执行的检查保持未勾选，不将工程已写入等同于视觉目标已验收。

## Phase 6: 玩家附近的街巷人流

- [x] 6.1 取消核心 240 格排除和全村预建居民槽位，按附近门前/街巷/广场/编排路线生成去重兴趣点
- [x] 6.2 增量验证当地公共地面，动态候选以较长行走路线为主、短暂停留；控制每 tick 48 次探查及 384 条附近路线缓存
- [x] 6.3 共享活动上限改为 96 人，坏点不占名额并退避回填，每 tick 最多 12 次补入尝试；复用现有 walker、模型、贴图与渲染批次
- [x] 6.4 维护人流密度/半格验证/屋顶排除/无效点回填/数量预算/状态复用/远处回收的回归源码，同步设计、规格和使用文档
- [x] 6.5 完成轻量静态与 OpenSpec 检查，并记录结果
- [ ] 6.6 后续按明确请求运行编译/测试；实机对比核心、普通街巷、外围的活动/绘制人数、移动连续性和帧耗，不将 96 人预算当作性能已通过

本轮详细记录见 [verification-crowd.md](verification-crowd.md)。此次居民调整不要求新建世界，不修改村庄布局或存档。
