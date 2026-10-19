## 1. 路径与数学

- [x] 1.1 [原3.1] 统一量化/StrictMath/索引RNG和跨平台fixture（黄金fixture本机通过；多平台实跑待后续，见operations-api）
- [x] 1.2 [原3.2、3.3] 实现Hold/Linear/SpeedCurve/Arc/Segments及显式段尾，验证任意时刻求值
- [x] 1.3 [原3.4、3.5] 实现Rotate/Orbit/Scale/Breathe/Compose/OrientAlongPath与层级时间偏移
- [x] 1.4 [原3.6] 实现AimAtPoint/RadialDirection/Detach/Select，保留取消归属
- [x] 1.5 [原3.7] 独立注册测试Path/Pattern，验证不改纯核心和编码封套；实际网络留给gameplay复验

## 2. 图案与动作

- [x] 2.1 [原4.1、4.2] 实现Point/Union/Ring/Fan/AngularGrid/ParallelGrid/AxisNodes/SurfaceRays
- [x] 2.2 [原4.3、4.4] 实现Rose/Spiral/散射/Shell/Cage/Radial/AroundPoint/Lattice/落雨，修正缺口相位
- [x] 2.3 [原4.5] 实现窗口/显隐/闪亮/声音/淡出/激光窗口描述及纯状态求值
- [x] 2.4 [原4.6] 定义EmitAlongPath/SplitAt/SpawnChild和有界派生描述，世界事务后续实现
- [x] 2.5 [原4.8、4.9] 实现段尾触发/Stagger/Phase/谱系SeedDirection/球面/螺旋柱/Mirror
- [x] 2.6 [原4.10、4.11] 实现Wave/Bezier/TimeMap/Rebase与Emitter/Pose/绑定继承描述
- [x] 2.7 [原4.7] 通过纯核组合探针，更新台账实际状态，wrapper测试/build与严格验证

复审补充证据：OperationCompositionTest覆盖真实48批出生共享组时钟/父闭包、静止方向、子程序实际两代图7N/4N、移动母弹计数、分段路径嵌套编码、结束策略冲突拒绝与具体权威策略描述。世界事务、游戏接线和多平台实跑仍不计入已验收。
