# 附近街巷人流修订记录

> **最终状态（2026-11-26）**：用户确认“我的验证完成，没什么问题了，可以归档了”，当前版本整体验收完成，见 [acceptance.md](acceptance.md)。下文保留实现各阶段当时的检查范围与限制；agent 未执行的自动化和未提供的量化指标不改写为通过。

用户反馈：房屋增多，但村民仍少，希望只围绕玩家附近兴趣点生成，以行人为主，活动数量控制在一百人以内。

## 实现

- 共享活动/绘制上限由 48 提高至 96，距离仍为 96 格。
- 不再排除核心 240 格，也不再预先为每栋远方住宅建立居民槽位；建筑元数据仅供附近兴趣点和屋顶/室内排除使用。
- 72 格范围兴趣点、384 条附近路线缓存、每 tick 48 次地面探查；每 40 本地 tick 或水平移动八格刷新兴趣点。
- 动态候选约八成尝试 8～16 格往返行走，受阻可缩短到至少四格；少量转弯、短暂停顿；半格采样并压缩共线节点。
- 已加载客户端区块及 `MOTION_BLOCKING` 高度图、真实支持面和扫掠碰撞检查，排除房屋内部/屋顶。
- 几何验证通过才占人数名额，失败点短暂退避，从备用点补入；每 tick 最多 12 次补入尝试，复用现有有效 walker，远处回收且不再 tick。
- 渲染沿用共享模型/贴图/批次，没有新增实体、存档或网络同步，不要求重新生成村庄。

## 检查

已维护 `ResidentPopulationTest`、`ResidentStreetCrowdTest`、`VillageResidentAnchorsTest`、`ResidentDataTest` 回归源码，尚未运行。

已完成轻量检查：

- `git diff --check` 无空白错误。
- 10 个改动/新增 Java 文件核对 UTF-8/BOM、包路径、内部导入和调用点，7 个 Markdown 文件核对相对链接。
- 通过已缓存映射 jar 的 `javap` 核对 `ClientLevel.hasChunk` / `Level.getHeight`；核对高度图枚举字节码，确认 `MOTION_BLOCKING` 使用 CLIENT 分类而 `MOTION_BLOCKING_NO_LEAVES` 使用 LIVE_WORLD 分类，采用前者。
- `bash tools/openspec_task.sh validate rework-human-village --strict` 通过。
- 没有修改世界生成、村庄布局/存档、网络包、贴图或公共构建配置；没有新增语言键。

以上不是 Java 编译、JUnit 或帧耗验证，未引用先前修订的构建结果代替本轮结论。

## 后续修订：避免当面刷新

新居民的实际初始 frame 使用完整身体加一格缓冲的视锥检查，视野内候选退避 60 tick，不占用活动名额；首个实际渲染帧还需确认视野外入场，随后可正常走进视野。空人口时仍保存相机画面，旧世界/布局切换清理快照与标记，主动预览绕过限制。规则保守地要求处于视锥外，未实现屏幕内隔墙提前补人或遮挡射线。

已维护 `ResidentSpawnVisibilityTest` 和 `ResidentPopulationTest` 回归源码，覆盖完整身体/边缘缓冲、无相机画面、实际落点、前方候选退避与后备回填、首次渲染前转头、旧身份重新创建及预览例外。

本轮轻量检查通过：`git diff --check`、6 个 Java 文件的编码/包路径/内部导入与调用点核对、5 个 Markdown 文件的相对链接检查，以及 `openspec validate rework-human-village --strict`。通过现有映射 jar 的 `javap` 核对 `Frustum(Frustum)`、`Frustum.isVisible(AABB)` 和 `AABB.inflate(double)` 签名。未修改网络、Worldgen、存档或资源。按本机策略未运行编译/测试或游戏，新增回归仅为源码，实际防当面出现效果和帧耗尚未实机验收。

## 限制

按本机策略未运行编译/测试，未启动游戏或服务端。没有本轮实际活动人数、绘制人数、路线通过率或帧耗证据。96 人是上限，街巷不足、未加载、流体、净空或遮挡仍可能使实际显示人数更少；待机处的 96 名也不等于视锥内同时画出 96 名。
