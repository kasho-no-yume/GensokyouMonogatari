# 弹幕操作台账

最后核对：2026-10-16。纯核已有离线实现与测试，游戏程序后端尚未接入。该台账与代码同步更新；设计文件不是实现证据。

2026-10-16：已按[路线图](../../../../openspec/changes/refactor-danmaku-program-runtime/roadmap.md)拆成7个子change。状态仍全部planned；每阶段只更新自己实际接入和验证的能力，不能因原子描述完成就标记整个游戏能力已可用。

## 状态规则

- `planned`：已有设计，无新后端实现。
- `implemented`：必须附真实文件/类型/方法；仍需验收。
- `verified`：必须同时附代码入口、测试或实机证据。
- `legacy-only`：只存在于旧实体路径，不代表新后端支持。

一个条目包含多个操作时，实现进度不同时必须拆行。归档change后更新下面链接，不能让技能指向失效路径。

## 新程序操作索引

除下表单独列出的纯核能力外，其余条目仍为planned。真实入口与离线约束见[core-api.md](../../../../openspec/changes/add-danmaku-program-core/core-api.md)。

| 类别 | 操作 | 状态 | 代码/验证 |
|---|---|---|---|
| 纯核时间 | At/Offset/Sequence/Parallel/Wait/Repeat/Cycle/Every、Once/Loop/Stream | verified（离线） | program/ProgramSchedule、ProgramTime、ClockAnchors；ProgramCoreTest |
| 纯核编译 | hash/codec/注册/引用/通道/拓扑/数量寿命包络 | verified（离线，保守静态分析） | ProgramCompiler/ProgramCodec/ProgramBudgetReport；ProgramValidationTest、ProgramCoreTest |
| 时间扩展 | AtSegmentEnd/Stagger | planned | 未实现 |
| 世界输入 | CaptureTargets/CaptureFrame/SampleTargetPoint/ResolveSurface/TriggerProximity | planned | 未实现 |
| 组与发射 | CreateGroup/ForEachTarget/PrepareEmission/CommitBatch/Emit/Select/SpawnChild | planned | 未实现 |
| 发射源 | CreateEmitter/MemberPose/GroupPose/ExternalEntitySource | planned | 未实现 |
| 分裂 | ReplaceMembers/SplitAt、谱系SeedDirection | planned | 未实现 |
| 基础运动 | Hold/Linear/SpeedCurve/Arc/Segments/Detach/RebasePath | planned | 未实现 |
| 变换 | Rotate/Orbit/ScaleCurve/Breathe/ComposeTransform/MirrorTransform/PhaseOffset | planned | 未实现 |
| 扩展路径 | WaveOffset/BezierPath/TimeMap/OrientAlongPath | planned | 未实现 |
| 图案 | Ring/Fan/AngularGrid/ParallelGrid/Rose/Spiral/散射/球面/螺旋柱/轴节点等 | planned | 未实现 |
| 派生 | EmitAlongPath/SpawnChild | planned | 未实现 |
| 视觉与判伤 | Telegraph/Billboard/Flash/SoundCue/Fade/DamageWindow/VisibilityCycle/LaserWindow | planned | 未实现 |
| 管理 | StopEmission/EndMembers/Collapse/CancelScope/EndCard/EndEncounter/Lifetime/EndOnReturn/LockBossMovement | planned | 未实现 |

快速配方：[两代分裂、移动母弹、龙卷风、旋转法阵](../../../../openspec/changes/refactor-danmaku-program-runtime/pattern-recipes.md)。通俗术语与写作步骤：[author-guide.md](../../../../openspec/changes/refactor-danmaku-program-runtime/author-guide.md)。

真实反弹、连续追踪、力场、传送、倒流和亚tick发射是future方向，**不计为本次交付，也没有可用API**。

## 当前旧后端入口（legacy-only，不保证所有组合可用）

| 能力 | 查阅代码 | 注意 |
|---|---|---|
| 时间与发射 | `danmaku/track/Track.java`、`TrackRunner.java` | 服务端调度，不能当组级双端程序 |
| 几何 | `danmaku/track/Shape.java`、`Geometry.java` | 通用Params有历史复用；核对字段是否实际消费 |
| 曲射/悬停/速率/爆散/回收 | `danmaku/track/Behaviour.java`、`entity/AbstractDanmakuProjectile.java` | 有互斥和顺序覆盖，使用前读实际分支 |
| 编队数学 | `danmaku/motion/FormationFrame.java` | 纯函数部分可复用，不等于新逻辑组已经实现 |
| 段式 | `danmaku/motion/DanmakuLegMotion.java` | 旧段类型与字段限制不属于新程序契约 |
| 激光 | `danmaku/track/Projectile.java`、`entity/LaserDanmaku.java` | 旧LaserMotion与新统一姿态模型不同 |

上表路径均相对`src/main/java/com/bitsson/gensokyou/`。标记legacy-only只表示发现入口，不表示已对所有参数完成验证。
