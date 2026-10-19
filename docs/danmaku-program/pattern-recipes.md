# 东方弹幕扩展与组合配方

**以下为组合设计配方，已有纯核及游戏样例接线；实际可调用入口以[作者指南](../danmaku-program-authoring.md)为准。** 本文前四例来自用户要求，它们不新增正式BOSS，只验证通用操作可用；代码与验证状态见[操作台账](../../.opencode/skills/boss-dev-design/references/danmaku-operations.md)。额外常用能力分成本次基础与后续独立扩展，不能把后续项伪装成现有API。实际离线API见[阶段2交接](../../openspec/changes/archive/2026-10-19-add-danmaku-program-operations/operations-api.md)。

## 1. 球面减速 → 一分二 → 再一分二

按“从球心向外”解释初始方向：`d=normalize(p-center)`。若要向心，显式取负号，API使用Outward/ Inward枚举，不写含糊的“连线反方向”。球面由SphereSurface生成，不能以旧Shell（有自己语义）冒充。

设初代N颗，T=20×t秒、初速v0、子代初速v1、末代匀速v2：

```text
初代：SphereSurface(N,radius)
  每成员沿Outward方向，速度从v0在T tick内减到0
  AtSegmentEnd：ReplaceMembers(每母弹生成2个子弹)

第一子代：出生点=母弹在边界时刻的世界位置
  每颗独立SeedDirection(UniformSphere)
  速度从v1在T tick内减到0
  AtSegmentEnd：ReplaceMembers(每母弹生成2个子弹)

第二子代：出生点=各自母弹的边界位置
  每颗独立SeedDirection(UniformSphere)
  Linear(v2)，到明确寿命结束；不再分裂
```

**分裂可以拆成销毁+生成，但不能让两条指令各自随意执行。** `ReplaceMembers`是一项事务宏：先固定母弹边界状态→检查子代完整配额→同一边界结束母弹并生成全部子代。`SplitAt`调用这个宏。每个母弹及其2个孩子构成一个原子替换单元；整个N母弹是否一起成功可显式选allSelectedParents，不能暗中扩大原子范围。

预算按替换后的净占用判断：替换一个母弹为2颗需要额外1个名额，不能把马上消失的母弹仍计作额外占用。失败策略必须选择`endParent`或`keepParent`；本例选endParent（熄灭并记录拒绝），不允许自动延后积压。母弹在分裂边界不再参与该tick判伤，子弹从age0开始。若母弹上一tick已被撞毁，不分裂。

初代活到T、第一子代活到2T、末代匀速：在无取消时累计生成`N+2N+4N=7N`，末代活跃4N；初代和第一子代在边界被替换。随机键含初代序号、代数、父谱系和孩子序号，删掉一支不改变其余支。用段结束标签触发，不在浮点运算中轮询`speed==0`。零速停留一段时也只触发一次。

## 2. n个移动母弹，各发x轮球形散射

```text
Repeat(n)：创建carrier，方向=SeedDirection，Linear(vCarrier)
  emitterSource = MemberPose(carrier)
  Repeat(count=x, first=T, period=T)：
    在carrier当前世界位置发SphereDirections(m)
    子弹速度=vChild，出生后独立世界直线
```

第一发明确在T，之后2T...xT；若要出生立即发，写first=0。母弹寿命必须覆盖最后一次发射时刻（至少xT+1tick）。总子弹`n×x×m`，不能只预算n个母弹。每一轮球面散射可以有不同seed或相位，但方向不能每帧重新抽。

母弹**可以有碰撞伤害**，只需Carrier也使用球弹CollisionProfile；发射器本身是绑定在它身上的逻辑节点，不需要另一颗承载参数的实体。母弹碰撞消失则未来发射取消，已独立飞出的子弹继续。母弹死亡清理策略与整张卡结束的归属清理是两回事。

如果母体是任意其他Minecraft实体：用`ExternalEntitySource(UUID, sampleAt=eachShot, onLost=stopEmission)`。每次发射服务端采样其位置/朝向/必要速度，结果加入该批Commit。实体自己的碰撞伤害由实体系统负责，不再额外生成一个重叠伤害球。其已经发出的子弹仍由原BOSS卡负责收场。自由运动实体不能零带宽跟随；本次支持逐次发射采样，不承诺每帧精确贴附。

## 3. 自旋且整体移动的竖直龙卷风

`HelixColumn`在局部Y轴排点：高度y、半径r(y)、初相位`theta=turns×2π×y/height + armPhase`。可以多条螺旋臂，参数显式含height、turns、armCount、samplesPerArm、radiusCurve。不要用反复发射环来假装同一批成员自旋。

```text
group storm：
  worldTranslation = Linear或Bezier路径
  localRotation = Rotate(axis=localY, angularSpeed=omega)
  pattern = HelixColumn(...)
```

先绕本组中心Y轴旋转，再加世界平移；不能绕世界原点公转。每颗局部高度保持；可另加ScaleCurve形成上宽下窄的轮廓。这里“组旋转”改变成员位置，而“弹体贴图旋转”只改变画法，两者不能混用。

若要持续吐出向上旋转的弹柱，用移动Emitter重复发环+每批局部螺旋路径，这是另一份合法配方，不把它与刚性自旋列混为同一实现。

## 4. 持续旋转喷射的法阵billboard

发射器有一个**游戏世界中的逻辑朝向**，贴图则可以永远朝摄像机。不同玩家的摄像机方向不得参与弹道计算。

```text
emitter：固定或沿路径运动的source
  nozzleRotation(t)=theta0+omega*t
  Repeat(count=x, first=0, period=P)：
    在发射时刻采样自身确定性source/nozzle姿态
    从旋转后的炮口发Fan或Ring
    子弹出生后默认不再跟随炮口旋转
  presentation=Billboard(texture, size, visualSpin)
```

visualSpin可显式绑定同一角度函数，使视觉和喷射节奏配合，但billboard绕相机法线旋转不等于世界炮口绕轴旋转。法阵贴图不自动具备伤害；想让阵盘伤人要单独声明碰撞成员。

本次支持整数tick间隔（最快每tick一次）和同tick多炮口，所有同tick批次进入同一DecisionBatch。不要把高频解释成随渲染FPS发射。亚tick发射需要额外定义碰撞时序，列为后续扩展。固定旋转和频率不发逐帧角度包；每批预算批准可压缩为一组批次标记。固定方向不同发射时刻自然形成旋臂。

## 5. 额外常用手法

### 本次提供基础操作并做小型验证

| 表演 | 组合方式 | 关键边界 |
|---|---|---|
| 洗牌弹/变速交错 | Select(indexModulo)+SpeedCurve，奇数慢后快、偶数快后慢 | 选择器稳定，不随已死成员重排 |
| 旋转缺口环 | Ring的GapMask；每轮发射相位累加 | 已发弹不会因为下一轮相位改变而转向 |
| “开花再冻结，再放行” | Rose+ScaleCurve→Hold→Detach径向 | 冻结组旋转须明确角度保持，不是将弹速置零 |
| 正弦波扇与蛇行弹带 | Fan+WaveOffset(amplitude,period,phase) | 在声明局部轴叠加横向偏移，幅度不积累漂移 |
| 延迟接力弹 | Select(index/range)+Stagger(delayStep)+Segments | 每弹时钟有起点，不新增每弹独立事件流 |
| 绸带式弯曲路径 | BezierPath+TimeMap，再定时EmitAlongPath | 首版按参数u推进，不谎称恒定弧长速度 |
| 两色花阵交换位置 | 子组路径+MirrorTransform+共同帧 | 镜像是几何变换，显示颜色与伤害保持独立 |
| 预告扇面后统一改向 | SampleTargetPoint+RebasePath | 重瞄是权威采样一次，之后直飞；不是持续追踪 |
| 相位错开的螺旋臂 | HelixColumn或Emitter+PhaseOffset | index/arm对应独立相位，随机结果稳定 |

### 已设计扩展入口，但本次不实现

| 手法 | 后续所需原子 | 必须额外解决的语义 |
|---|---|---|
| 屏幕边界/场地镜面反弹 | ReflectAtPlane(maxBounces) | 固定数学平面可纯求值；真实方块反弹必须服务端碰撞事件，定义同tick多次接触 |
| 引力场/涡流场 | FieldPath或受控积分轨迹 | 步长、误差、积分起态与重建成本；不能任意累加后宣称纯函数 |
| 连续追尾/激光跟随 | SampledTracking(turnLimit,samplePeriod) | 明确采样频率、延迟、轨迹连续性和带宽，不能双端读玩家当前位置 |
| 弹幕传送门 | TransferFrame(entry,exit) | 位置不连续是有意行为；禁止扫掠横跨传送距离，分别判入口出口 |
| 轨迹回放倒流 | ReplayPath(reverseWindow) | 记录哪些历史权威采样；已经销毁的成员不会自动复活 |
| 子帧级机枪喷射 | SubTickEmit(rate) | 出生分数时刻、部分tick碰撞、预算与事件时间精度 |

这些扩展不需要现在制造空实现。目录明确标注future，未来新增对应codec/evaluator/decision及测试即可；如果新物理语义确需核心调整，应公开说明，不能用“万能原子已支持”掩盖。

## 6. 我建议的复合探针：星种、旋塔与双使莲华

**星种接力**：球面星种减速→二分裂→再二分裂→末代按奇偶速率交错飞出。覆盖ReplaceMembers、RNG谱系、Stagger和派生预算。

**巡游旋塔**：移动龙卷风以Bezier路径绕场；各层自旋相位错开；每隔20tick从最顶层吐一个球形环，环出生后独立直飞。覆盖移动源、局部螺旋、子代脱离和包络跟踪。

**双使莲华**：两个镜像移动法阵用同一时钟旋转喷出花瓣；每第四轮是慢速留影，延迟后沿锁定方向加速。两阵的贴图始终面向观察者，但世界弹道对所有玩家相同。覆盖Mirror、Billboard、发射角度函数和Select轮次。

探针的密度、预警和场地按R1/R2/预算另行设计；这些是表达能力案例，不代表任意参数组合都公平。
