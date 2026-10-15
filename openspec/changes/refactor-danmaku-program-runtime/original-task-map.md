## 1. M0 基线与依赖盘点

> 历史任务编号快照，仅供拆分核对，不是实施待办。唯一实施归属见roadmap.md；进度只更新各子change/tasks.md。

- [ ] 1.1 导出sheet三页与现行20卡/底噪的机器可读内容manifest，记录数量、时钟、寿命、伤害、目标模式和移动锁
- [ ] 1.2 登记旧实现缺拍、FAN/CAGE无效参数、表内大玉冲突、环微移及尾珠端点差异，确定content-mapping基线
- [ ] 1.3 盘点碰撞/伤害/擦弹/白名单/owner属性对Projectile实体的依赖，形成DanmakuHitContext适配清单
- [ ] 1.4 用既有profile与延迟工具保存相同弹量的旧后端流量、CPU、frame-time与资源基线
- [ ] 1.5 固定操作注册命名、codec上限、单位和schema版本，建立新后端开关与只读影子模式，确保只有一个判伤者

## 2. M1 程序数据模型与编译

- [ ] 2.1 实现ProgramDefinition、节点ID、常量表、program hash与配置解析快照
- [ ] 2.2 实现Frame/Target/Group/Plan/Selection类型句柄，Card/Cycle/Group/Member时间域
- [ ] 2.3 实现At/Sequence/Parallel/Wait/Repeat/Cycle/Every与Once/Loop/Stream，验证周期偏移子拍和半开区间
- [ ] 2.4 实现通道单写者、显式Compose、依赖拓扑与零时长环检查
- [ ] 2.5 实现类型注册接口、定义有界codec、未知版本检查、节点/段/深度/派生上限
- [ ] 2.6 实现离线数量/生命周期包络与计划清单输出，真实计入Union核心及每节点多射线

## 3. M1 确定性数学与路径

- [ ] 3.1 统一wire量化后输入、StrictMath和无状态索引RNG，建立跨平台轨迹fixture
- [ ] 3.2 实现Hold/Linear/SpeedCurve与显式Continue/Hold/End尾策略，复用既有速度积分数学
- [ ] 3.3 实现Arc及异构Segments，缓存段边界位置和速度，验证任意顺序时间查询
- [ ] 3.4 实现Rotate/Orbit/ScaleCurve/Breathe/ComposeTransform与显式OrientAlongPath
- [ ] 3.5 实现局部图案×父组变换、父层级闭包与成员时间偏移
- [ ] 3.6 实现AimAtPoint/RadialDirection/Detach及选择器，验证部分脱离的位置连续与取消归属
- [ ] 3.7 用独立测试注册新PathType和PatternType，证明无需编辑主循环/通用协议/公共成员结构

## 4. M1 图案与表演操作

- [ ] 4.1 实现Point/Union/Ring/DiscRing/Fan/AngularGrid类型化生成器，验证相位与数量
- [ ] 4.2 实现ParallelGrid及列mask、AxisNodes/SurfaceRays，覆盖蓝3×3/4×3/带门18发/4射线
- [ ] 4.3 迁移Rose/Spiral及Cone/VolumeScatter，独立花心tag与随机平面
- [ ] 4.4 迁移Shell/Cage/Radial/AroundPoint/Lattice及落雨生成规则，修正缺口静默忽略
- [ ] 4.5 实现DamageWindow/VisibilityCycle/Flash/SoundCue/Fade与LaserWindow组合
- [ ] 4.6 实现EmitAlongPath/SplitAt/SpawnChild描述及有界派生预算，固定父子出生端点
- [ ] 4.7 运行复合花阵、跨48拍点亮和多轨共享帧的纯核组合探针，M1通过后才进入数据后端接线
- [ ] 4.8 实现AtSegmentEnd/Stagger/PhaseOffset与谱系SeedDirection，测试删除支路不影响其他随机序列
- [ ] 4.9 实现SphereSurface/SphereDirections/HelixColumn和MirrorTransform，验证空间方向与局部旋转
- [ ] 4.10 实现WaveOffset/BezierPath/TimeMap/RebasePath，明确参数速度和世界速度区别
- [ ] 4.11 实现CreateEmitter/MemberPose/GroupPose与attachment/velocity策略、源失效处理

## 5. M2 服务端运行时与权威决策

- [ ] 5.1 实现每维度DanmakuWorldRuntime、encounter/card/cycle/group/batch/member存储和稳定身份
- [ ] 5.2 实现同tick调度顺序、计划游标、未来派生取消和共享帧引用释放
- [ ] 5.3 实现CaptureTargets/CaptureFrame/SampleTargetPoint，UUID槽失效与近竖直R回退
- [ ] 5.4 实现ResolveSurface、TriggerProximity与通路有限查询，世界读仅在DecisionProvider
- [ ] 5.5 实现PrepareEmission及不可变Plan，实际源点瞄准与预警共享数据
- [ ] 5.6 实现完整batch预算、蓝净距检查和Commit/CancelGroup，查询全战斗范围玩家而非仅5目标
- [ ] 5.7 实现StopEmission/Collapse/EndMembers/EndCard/EndEncounter，含背景、预警、派生和移动锁释放
- [ ] 5.8 实现同卡跨轮retain策略、owner卸载/重启临时状态策略、未加载区域成员终止
- [ ] 5.9 实现ReplaceMembers净配额原子事务、perParent/allSelectedParents、拒绝与触发消费策略
- [ ] 5.10 实现ExternalEntitySource逐发UUID采样、失效处理和Commit结果编码，保持外部实体伤害单一

## 6. M2 碰撞、判伤与渲染后端

- [ ] 6.1 抽取球弹扫掠/静止相交适配器，保留类型过滤、最近命中、白名单、方块穿透语义
- [ ] 6.2 接入DanmakuHitContext、owner属性倍率、擦弹/护壁和伤害事件，验证非BOSS旧入口
- [ ] 6.3 实现曲线路径误差受限子段扫掠及参数上限检查
- [ ] 6.4 接入激光判伤节流、激活窗口、动态方块裁剪与量化长度结果
- [ ] 6.5 实现客户端批量球弹/激光渲染、资源复用与可见包络裁剪，闪亮不改物理尺寸
- [ ] 6.6 实现源符印、面板轮廓、未来弧线、无害余辉与音效去重，均消费同一Plan/Path
- [ ] 6.7 实现Billboard独立视觉旋转与世界炮口姿态，验证两摄像机视角弹道相同

## 7. M2 组级同步与恢复

- [ ] 7.1 实现Definition缓存和兼容协商、Start/Checkpoint/DecisionBatch/ClockDigest/Resync/End封套
- [ ] 7.2 实现组级序号、epoch、幂等与tombstone，有界缺定义缓存和chunk原子提交
- [ ] 7.3 实现共享维度时钟、速率/离群/陈旧检测，程序revision独立于age
- [ ] 7.4 实现effectiveTick事件安装、段起态直接重建、迟到新生与有限视觉修正
- [ ] 7.5 实现自足checkpoint：活跃组、成员mask、目标有效性、权威结果、计划与派生游标
- [ ] 7.6 实现攻击包络兴趣管理、远端弹追踪、父变换闭包和空间分片
- [ ] 7.7 实现成员删除mask、动态激光长度、稀疏摘要、按作用域恢复及请求限流
- [ ] 7.8 验证初始化/恢复不产生历史伤害、派生或音效，旧epoch与结束后迟到消息不能复活

## 8. M2 代表性纵向切片与门槛

- [ ] 8.1 迁移蓝1为预览切片，验证多人固定槽、四拍锁轴、偏移源、安全取消
- [ ] 8.2 迁移蓝4为预览切片，验证跨轨帧、真实断口、激光激活前通路检查
- [ ] 8.3 迁移蓝6为预览切片，验证直线/圆弧/直线与整片偏移保持
- [ ] 8.4 迁移花海为预览切片，验证核心/花瓣分工、权威重瞄与跨轮残留
- [ ] 8.5 迁移山女尾珠为预览切片，验证父死亡、预算准入与子代漂移
- [ ] 8.6 完成复合花阵/旋转激光/远端子组实机探针，以及1/2/5人延迟与重追踪矩阵
- [ ] 8.7 运行migration中同等负载A/B门槛，记录报告；未过关不得进入全量内容迁移
- [ ] 8.8 完成球面两代二分裂探针：7N累计/4N末代、净预算失败、母弹提前死亡、中途加入
- [ ] 8.9 完成n个移动母弹各x轮球形散射探针及外部有害实体变体：nxm计数、停止未来发射、速度继承
- [ ] 8.10 完成移动自旋龙卷风与高频旋转法阵探针，验证同tick批量准入、FPS无关和多视角一致
- [ ] 8.11 完成Wave/Stagger/Bezier/Mirror组合小样；更新M2报告后才允许M3迁移

## 9. M3 全量内容迁移

- [ ] 9.1 迁移大妖精三卡与默认5×5弹墙，核对数量/窗口/伤害/移动锁
- [ ] 9.2 迁移山女四卡与默认蛛丝扇，修正0/20/40子拍并记录实际密度差异
- [ ] 9.3 完成蓝1至4正式程序、本地化和生命周期，关闭蓝独立底噪
- [ ] 9.4 完成蓝5至8正式程序、闪亮提示与t220/t230收束，核对单人/五人发数
- [ ] 9.5 迁移露米娅/傩面五卡及现有默认攻击入口，验证持续流/地雷/显式HoldTail
- [ ] 9.6 接入BOSS血量阶段、HUD、移动锁与旧BOSS弹加载迁移标记，保持循环边界选择
- [ ] 9.7 统一/danmaku card及操作探针到新runtime，保留旧非BOSS调试能力
- [ ] 9.8 完成全部20卡与底噪manifest对照、地形/生存/多人实机记录并回写表格冲突

## 10. M4 可观测性、退出与最终验证

- [ ] 10.1 扩展/gs_boss danmaku的组/计划/真实派生/取消/迟到/恢复/CPU阶段指标
- [ ] 10.2 输出definition/instance/decision/clock/checkpoint字节分解与既有线上profile对账
- [ ] 10.3 完成180s×3基准、48→480组缩放、800/2000压力、20次回收和初始化突发报告
- [ ] 10.4 清理BOSS旧TrackRunner/Emitter执行与临时EntityBackend依赖，按实际引用保留非BOSS实体能力
- [ ] 10.5 回写两项在途扩展的superseded交接，不虚假勾选旧任务；更新boss-dev-design与相关文档适用域
- [ ] 10.6 使用tools/gradle_task.ps1 build完成纯核与集成检查，完成lang审计及严格OpenSpec验证
- [ ] 10.7 确认新BOSS无逐弹原版同步、无双重判伤、非BOSS回归通过，附全部验收证据后进入归档流程
- [ ] 10.8 逐项更新技能操作台账状态、真实代码入口、最小例子与验证证据；future项保持未实现，归档后更新设计链接
