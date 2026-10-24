# BOSS 符卡程序开发：照着写的作者指南

## 0. 现在能做什么，不能做什么

**你现在可以编写、编译、测试和预览新程序。大妖精、山女、八云蓝、露米娅的正式战斗仅使用程序牌组，临时后端开关与旧回退已移除；傩面已删除。最终实机验收边界见[退役记录](../openspec/changes/archive/2026-10-19-retire-legacy-boss-danmaku/validation.md)。新写的卡仍需显式登记才能用于正式战斗。**

- 普通内容作者：使用`danmaku/program/author/`，交付`CardProgram.Compiled`。
- 想试效果：运行下面的预览指令；新增自己的预览需在`ProgramPreviewHost`与`ProgramPreviewCommands`登记入口。
- 想接新BOSS：登记`BossProgramCards`牌组并覆写`programContentId()`；血量/HUD/加载/结束走公共战斗宿主。普通写卡任务不包含实体接线；用户明确要求接入时按当前任务范围实施。
- 不要复制旧预览里的codec、handler、网络安装代码；这些已由公共入口处理。

读法：先照抄第2节，持续型卡接着看第3节；调伤害看第4节；生成失败看第8节。不需要先读OpenSpec设计稿。

### 正式迁移任务先确认范围

上面的“尚未迁移”描述当前接线状态，不是禁止执行迁移。收到正式迁移任务时，先读对应 change 的 `tasks.md` 和本轮用户要求，再读该 change 的 proposal/design/specs；历史交接中的“本轮不实施”只描述当时的授权范围。前置验收以最新明确记录为准，不能拿较早的“禁止迁移”覆盖后续已接受的结论，也不能把已接受的有限验收说成全部实机矩阵通过。

先列出本轮要迁移的卡和明确排除的卡，再核对旧表格的总数。排除项应注明“不适用/跳过及原因”，不要为了凑历史总数迁移被排除的内容。逐卡验收要分别记录数量、时序、伤害窗口、多目标展开、循环尾弹和结束清场；编译通过只证明入口可用，不能证明这些行为等价。正式宿主接线看第10节，属于一次性的维护工作。

**缺少快捷方法不代表允许改变符卡。** 如果旧卡要求地面采样、走廊净空、特殊瞄准或完整轮廓预警，而本指南没有对应调用，先检查标准操作是否已实现，再由维护者补通用作者入口与测试。登记差异有助于审查，但登记本身不代表差异获准；未经授权的平行弹替代收敛弹、空中光柱替代地面光柱等近似，不能标为等价迁移完成。

等价检查要比较实际几何与世界输入，不能只比较总数。固定20格焦距不能代替“朝采样到的玩家位置收敛”；向上找空气不能代替向下找空气，空旷场地相同也不能证明顶棚下相同。测试应包含不同目标距离、高差和有顶棚的地形。总数正确之后，还要核对每批数量、各列位置、逐弹方向、目标快照时刻和失败撤批范围。

## 1. 先分清四种东西

| 东西 | 通俗解释 | Java类型 / 用法 |
|---|---|---|
| 卡程序 | 一张时间表，规定何时做什么 | `CardProgram` |
| 目标集合 | 本次采样到的玩家名单，不是发射器 | `CardProgram.Targets`，由`targets()`返回 |
| 组 | 锁定一张局部坐标纸，管理从这里发出的弹 | `CardProgram.Group`，由`aimed/arena/offset`返回；`fire`需要它 |
| 成员程序 | 一批弹的形状、运动、寿命和行为说明 | `Actions.ChildProgram`，由`MemberProgram...build()`返回 |

```java
var targets = card.targets("players", 64, 5); // 半径64格内，最多5名有效玩家
var gun = card.aimed("main", targets, 0, new Vec3(0, 1, 0)); // 锁定第0槽
card.fire(gun, bullets, 20, 31); // 不是fire(targets, ...)
```

玩家按距离排序，最近者在第0槽。捕获5人**不会自动复制5份攻击**；需要逐人点名就为槽0..4分别建组，场地阵列只建一份。目标缺席时对应槽不发射，不把后来的人偷偷补到该槽。

验收逐人点名时至少用1人和5人各跑一遍实际生成：检查每个槽的归属和数量，不只检查总数或卡说明。公共阵列和个人点名并存时，总数应为“公共部分 + 有效目标数 × 个人部分”。

### 单位与坐标

- 20 tick约为1秒；速度单位是格/tick，`.18`约为3.6格/秒。
- 角度均为弧度。60度写`Math.toRadians(60)`，别直接填`60`。
- `Vec3.X/Y/Z`分别是局部右/上/前方向，`Vec3.ZERO`是零偏移。
- `aimed`把局部`+Z`对准采样到的玩家；之后玩家移动不会自动重瞄。
- `new Vec3(0, 1, 0)`是从owner局部坐标上移1格，不是世界绝对坐标。

## 2. 最小例子：三次扇形齐射，只播放一次

先复制整个方法到自己的内容类，修改唯一的program id，再改数字。

```java
import com.bitsson.gensokyou.danmaku.program.*;
import com.bitsson.gensokyou.danmaku.program.author.*;
import com.bitsson.gensokyou.danmaku.program.math.*;
import com.bitsson.gensokyou.danmaku.program.ops.*;

public static CardProgram.Compiled myCard() {
    var card = new CardProgram("gensokyou:my_card");
    var targets = card.targets("players", 64, 5);
    var gun = card.aimed("main", targets, 0, new Vec3(0, 1, 0));

    var pattern = MemberProgram.styled(
        new Patterns.Fan(9, Math.toRadians(60), 0),
        "sphere", "#62D6B4", .12, 1, .22, "main");
    var bullets = MemberProgram.attack("main fan", pattern,
        new Paths.Linear(Vec3.Z, .18), 100)
        .templateDirections().build();

    card.every(0, 30, 3, c -> c.fire(gun, bullets, 20, 31));
    card.at(181, CardProgram::endCard);
    return card.once();
}
```

### 逐个解释照抄后最常改的参数

| 调用 | 参数含义 |
|---|---|
| `Fan(9, radians(60), 0)` | 9发；总张角60度；整个扇形的偏转角0弧度。第3项不是间隔或随机种子 |
| `styled(pattern, "sphere", "#62D6B4", .12, 1, .22, "main")` | 图案；弹型；颜色；基准碰撞半径；伤害倍率；视觉半径；成员标签 |

程序弹幕实际碰撞半径为基准值 × `BalanceNumbers.PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE`（当前 `0.5`），普通弹判伤/碰墙及激光判伤统一应用；激光长度和视觉尺寸保持原值。上述 `.12` 对应实际半径 `.06`。网络与存档保留基准值，避免重复缩放；生成净距与运动包络仍保守使用基准半径。
| `attack("main fan", pattern, path, 100)` | 用于报错定位的名字；图案；路径；每颗弹出生后的寿命100tick |
| `Linear(Vec3.Z, .18)` | 路径沿局部前方，速度.18格/tick；方向最终怎样分配由下一项决定 |
| `templateDirections()` | 把这条前向路径分别朝向Fan给出的每发方向，形成散开的扇 |
| `every(0, 30, 3, ...)` | 从时刻0开始，每30tick一次，共3次：0、30、60 |
| `fire(gun, bullets, 20, 31)` | 组；成员程序；预警20tick；确定性随机种子31。31没有特殊含义，可换固定整数 |
| `at(181, endCard)` | 在181tick结束本卡并撤销它的未来调度，不结束整场战斗或独立底噪 |

种子只影响用到随机的图案/方向；框架还会混入实例和拍次等身份，因此同一个31不代表每一拍复制完全一样的随机结果。不要在每帧用系统时间生成种子。

这张卡在0/30/60开始预警，20/50/80出生，每批9颗；最后一批在180tick寿命结束，181tick清卡。示例数字是开发模板，正式BOSS可调平衡参数应从config传入。

迁移旧时间表时，先标明表中 tick 是“开始预警”还是“实际出生”。`at(t, c -> c.fire(..., w, ...))`在`t+w`出生；旧表若要求在60出生、预警20，就在40调用`fire`。不要直接把旧出生拍点复制到`at`后又加一次预警。测试应读取实际runtime的出生/提交事件，不能只断言时间表节点是60。

### 标签不是组名：`"main"`到底做什么

`styled`末尾是可选的`String... tags`，给每个生成成员贴标签，可写多个，也可不写。标签本身不会改变运动、伤害或颜色。

```java
var petals = MemberProgram.styled(rose, "sphere", "pink", .12, 1, .22, "petals");
var selected = new Selection.Tag("petals"); // 之后只操作贴了petals标签的成员
```

组名管理一批/多批弹的归属；标签挑选组里的成员。组名碰巧也叫`"main"`并不产生关联。

## 3. 持续型卡：必须明确选择循环

`once()`只执行一次；`every(..., count, ...)`也只是有限次数，不会让卡永远循环。

下面的完整方法每120tick重复一轮，每轮在0/20/40tick发三拍，并重新采样目标。

```java
public static CardProgram.Compiled loopingFan() {
    var card = new CardProgram("gensokyou:looping_fan");
    var targets = card.targets("players", 64, 1);
    var gun = card.aimed("main", targets, 0, new Vec3(0, 1, 0));
    var bullets = MemberProgram.attack("loop fan",
        MemberProgram.styled(new Patterns.Fan(7, Math.toRadians(50), 0),
            "sphere", "blue", .12, 1, .22, "main"),
        new Paths.Linear(Vec3.Z, .16), 100)
        .templateDirections().build();
    card.every(0, 20, 3, c -> c.fire(gun, bullets, 20, 31));
    return card.loop(120, TailPolicy.END_AT_BOUNDARY);
}
```

`loop(long ticks, TailPolicy tail)`的第二项是**一个枚举值，不是可变参数**。

| 策略 | 用途与注意事项 |
|---|---|
| `END_AT_BOUNDARY` | 轮结束清掉旧轮成员/计划，下一轮重开。入门优先选它，避免旧弹跨轮积累 |
| `RETAIN_UNTIL_LIFETIME` | 已出生旧弹按自身寿命继续，有限派生及已安排的跨边界预警出生可继续；旧轮不会重新执行时间表。适合有意跨轮留下尾珠/母弹/预警的设计，必须计算跨轮峰值 |

例子中最后一拍在60tick出生，本可活到160；选择`END_AT_BOUNDARY`会在120tick截断旧轮。

循环验收必须放入寿命长于轮长的成员并执行到下一轮，分别检查两种策略的真实存活数。只检查编译结果中的枚举值，无法发现运行时没有落实清场策略的问题。相应回归见`CardTimelineContractTest`。

**循环体不要调用`endCard()`**，否则第一轮就结束整张卡。要在轮尾演出淡出，用`card.at(100, c -> c.collapse(gun, 10))`；真正结束整张循环卡由战斗控制器切卡。

时间表中所有节点必须落在轮长内。`at/every`在循环中使用本轮时间；单轮中等同卡开始后的时间。先创建目标和组，再引用；同名句柄不能重复声明，不能把另一张卡的组拿来用。

## 4. 写攻击前必须确认：方向、伤害、隐藏

### 4.1 选择一个方向规则

```java
.templateDirections() // 扇形/球面逐发向各自方向飞
.fixedDirection()    // 所有路径共用局部轴，适合整片向前推进
.aimAtTarget()       // 按真实采样目标点，让每个出生点分别朝该点收敛
.randomDirections(7) // 按稳定随机谱系选方向；7是随机用途通道
```

四选一，不是连续调用四项。`Fan`只给方向，没选`templateDirections`就不等于沿扇形飞出。`aimAtTarget`使用服务端本拍采样并随批次同步的目标点；不同出生位置分别求方向，不是固定焦距，也不是持续追踪。复合变换按列表顺序执行，常见顺序为呼吸→旋转→平移。

### 4.2 攻击与装饰不要混用

```java
MemberProgram.attack("bullet", pattern, path, 100);     // 默认全寿命有害
MemberProgram.decoration("spark", pattern, path, 100);  // 全程无碰撞
```

要先无害20tick、后来伤人，给攻击程序设置窗口：

```java
.presentation(new Presentation.Profile(
    java.util.List.of(new Presentation.Window(TimeDomain.MEMBER_AGE, 20, 100)),
    null, null, java.util.List.of()))
```

`[20,100)`包含20，不包含100。`MEMBER_AGE`从该弹实际出生算起，不包含预警时间。`CARD`从卡开始算，`CYCLE`从本轮开始算，`GROUP`从组创建算；别用卡时间判断不同出生时间弹的年龄。

### 4.3 隐藏不是消失；淡出也不等于无碰撞

```java
.hiddenCycle(TimeDomain.MEMBER_AGE, 40, 20, 0)
// 周期40tick，前20正常、后20半透明隐藏，起始相位0
```

隐藏期完全跳过实体/方块碰撞，之后自动恢复。球弹和三维模型按各自档案`hiddenAlpha`降低本体alpha、保留RGB；激光使用旧实体0.18系数降低光束/端点/法阵的alpha和RGB。

只设置`Fade`不会自动关闭碰撞。需要无害淡出整组时用`collapse(group, fadeTicks)`；完全消失请结束成员，或同时关闭伤害窗口并将alpha设为0。先配置自定义`presentation`，再调用`hiddenCycle`，避免后者被新的presentation覆盖。

## 5. 子弹再发子弹：先标签选择，再声明触发

从第三个模板`AuthoringTemplates.carriers()`开始会遇到这些名字：

| 名字 | 意思 |
|---|---|
| `Selection.Tag("mothers")` | 选标签为mothers的成员，不是选同名组 |
| `Selection.IndexRange(a,b)` | 按稳定成员编号选`[a,b)`，删除其他弹不会重排编号 |
| `Selection.IndexModulo(2,0)` | 选稳定编号为偶数的成员 |
| `Actions.AtAge(40)` | 母弹出生40tick后触发 |
| `Actions.AtPathSegmentEnd("slow")` | 母弹具名路径段slow结束时触发 |
| `Actions.OnReject.KEEP_PARENT` | 子代准入失败时保留母弹 |
| `Actions.OnReject.END_PARENT` | 子代准入失败时也结束母弹 |

先构造最末一代，再往外包母弹；不要手填派生上界。

```java
// child已是build()后的成员程序；motherBuilder尚未build()
motherBuilder.emit(new Selection.Tag("mothers"), 20, 20, 3, child);
// 每颗选中母弹在age20/40/60各生成一份child，母弹继续存在

motherBuilder.replace(new Selection.Tag("mothers"),
    new Actions.AtAge(40), child, Actions.OnReject.KEEP_PARENT);
// age40用child替换选中的母弹；成功才移除母弹
```

`emit`快捷入口逐母弹准入，失败保留母弹；`replace`快捷入口将本次选中的母弹作为一笔原子替换，失败按onReject处理。母弹提前死亡会取消未来派生；已经生成的快照子弹不跟着消失。切卡或结束战斗则按归属统一清理。

花阵释放使用标签挑选：

```java
card.at(100, c -> c.release(flowerGroup,
    new Selection.Tag("core"), new Selection.Tag("petals"), .16));
```

`core`必须在执行时选中**恰好一个存活花心**。无花心会取消该组，多个花心是错误；不要给一组里每一批都贴core后再期待它自动猜中心。

## 6. 生成失败怎么办：先净距，再预算

### 6.1 净距：别在玩家身上突然生弹

“净距”是出生时弹的碰撞球边缘与玩家之间要求的安全距离。默认2格，在**实际出生/准入时**检查；玩家可能在预警期间走进去，所以整批被拒绝并不一定是程序错误。

```java
var card = new CardProgram("gensokyou:my_card").clearance(3); // 例：要求3格
```

优先移动生成点、重新设计预警/取消逻辑，再讨论降低净距。只有允许贴近生成的测试或经过审查的特殊表演才使用：

```java
card.clearance(0); // author_hidden测试场景这样做；不是正式卡通用修复
```

`0`表示不要求额外安全间隔，**不等于关闭所有准入检查**；如果出生碰撞球已经与玩家参考位置重叠，净距结果仍可能小于0而被拒绝。

在`fire`之前设置，修改只影响之后创建的发射节点。安全检查默认考虑全部有效战斗玩家，不只瞄准目标。`offset`用组局部坐标移动生成点：

```java
var left = card.offset("left", gun, new Vec3(-3, 0, 0));
card.fire(left, bullets, 20, 31);
```

### 6.2 派生上界：框架估算“最多会生成多少”

这是安全计算，不是实测发数。框架保守按全部母成员计算，即使选择器只挑几颗，也可能仍给出较大的上界。

```java
var members = motherBuilder.build();
long upperBound = members.descendantBound(); // 只读诊断，不要手填
```

“代数”是最多几层子代；“峰值”是某一刻同时存在/出生多少；“累计量”是整场一共生成多少。这三个数不能互换。

### 6.3 预算：需求算出来后，另有上限检查

`ProgramLimits`限制节点、树深、单批成员、派生数量、字符串与定义大小。普通作者先沿用默认值；确需调整时显式声明：

```java
card.limits(ProgramLimits.defaults()); // 演示入口；默认payload额度与标准作者环境不完全相同
```

这**不是提高运行期预算**的入口。实际运行还检查全局存活数、每tick出生数、计算工作量、净距与近域密度。“近域密度”就是玩家附近已有/拟新增弹是否过密；拆成多拍也不能无限堆积长寿命弹。

超限先减少单拍数、增加间隔、缩短寿命、减少派生，再找宿主维护者审查预算。不要把每个上限调大当作解决方法。

R1/R2/R3仍要人工审查：威胁有预警或在视野内；玩家有三维躲避解；控制展开、派生峰值和稳态密度。编译通过不等于好躲或性能达标。

## 7. 作者入口速查

| 需求 | 入口 |
|---|---|
| 采样目标名单 | `targets(name, radius, maximum)` → `Targets` |
| 朝一个固定目标槽建组 | `aimed(name, targets, slot, sourceOffset)` → `Group` |
| 使用owner自身坐标轴、不逐人复制 | `arena(name, targets)` → `Group`；不是自动绑定寝宫锚点 |
| 在已有组旁偏移建组 | `offset(name, sourceGroup, localOffset)` → `Group` |
| 创建持续移动组 | `aimedMoving(...)`；持续跟随它发射用`fireAttached(...)` |
| 开始预警后发弹 | `fire(group, members, warningTicks, seed)` |
| 有限连发 / 指定时刻 | `every(first, period, count, action)` / `at(tick, action)` |
| 播放一次 / 重复整轮 | `once()` / `loop(ticks, TailPolicy.END_AT_BOUNDARY)` |
| 攻击 / 装饰 | `MemberProgram.attack(...)` / `decoration(...)` |
| 图案方向 / 统一轴方向 | `templateDirections()` / `fixedDirection()` |
| 各出生点朝真实目标收敛 | `aimAtTarget()`；不是持续追踪 |
| 球弹穿方块 | `piercing()`；仍按攻击窗口判玩家碰撞 |
| 激活前检查局部走廊 | `corridor(group, from, until, halfWidth, maxQueries, cancelOnFailure)` |
| 显式锚点解析世界面后发射 | `surfaceFire(...)` |
| 逐有效目标解析脚下等世界面后发射 | `surfaceFireAtTargets(...)` |
| 半透明无碰撞周期 | `hiddenCycle(clock, period, activeTicks, phase)` |
| 母弹发射 / 替换 | 成员builder的`emit(...)` / `replace(...)` |
| 花瓣径向脱离 / 重新瞄准 | `release(...)` / `reaim(group, slot, selection, speed)`；重瞄slot=-1随机有效目标 |
| 停止未来生成 / 无害淡出 | `stopEmission(group)` / `collapse(group, fadeTicks)` |
| 锁移动 / 结束本卡 | `lockMovement(group, duration)` / `endCard()` |
| 出生净距 / 编译上限 | `clearance(blocks)` / `limits(programLimits)` |

`every`会有限展开，适合少量明确拍。高频大量发射使用成员`emit`或让框架维护者接`CreateEmitter`，不要展开几千个节点。路径分段用`Paths.Segments`，每段从零位移开始；旋转/呼吸/平移用`Transforms.Compose`。

`arena`使用采样时owner的姿态帧；在BOSS宿主中其前向来自实体朝向，不会因为传入了目标集合就自动瞄准最近玩家。“公共一份”说明复制数量，“朝谁”说明坐标帧，两者须分别确认。需要朝玩家的公共攻击，可以只创建一次指向指定槽的`aimed`组。

地形发射的完整参数和可运行用法见`BigFairyPrograms.rain()`及`ProgramContentTest`。`surfaceFire`的anchors在组局部坐标中；`steps/queries/maxPoints`约束搜索与数量，`warningTicks`仍会推迟实际出生。三种采样不能互换：`FIRST_AIR_BELOW`向下找空气，`FIRST_AIR_ABOVE`向上找空气，`DOWNWARD_SOLID_TOP`向下找固体顶面。走廊坐标也在组局部帧中，失败撤销范围由传入group决定。路径中的`Vec3.Y`是局部轴，经组帧映射后才成为世界方向；不要直接称为世界竖直。

## 8. 预览与诊断：看到什么，下一步做什么

真实模板源码：`src/main/java/com/bitsson/gensokyou/danmaku/program/author/AuthoringTemplates.java`。

```text
/gs_program_preview start author_volley
/gs_program_preview start author_flower
/gs_program_preview start author_carriers
/gs_program_preview start author_hidden
/gs_program_preview start formal_big_fairy
/gs_program_preview start formal_yamame
/gs_program_preview start formal_yakumo_ran
/gs_program_preview stats
/gs_program_preview stop
```

`volley`看齐射；`flower`看复合变换与花瓣释放；`carriers`看两层派生。指令示例不是把你的`myCard()`自动注册进游戏，自己的预览要按第0节登记。

`formal_*`目前预览对应正式牌组的**第一张卡**，不自动轮播全部卡。正式战斗通过config `bossDanmakuBackend=program`选择程序后端，默认仍为`legacy`。完整实机检查步骤见迁移change的`migration-manifest.md` §8，不能用首卡预览代替全牌组验收。

`author_hidden`在当前视线前方生成固定阵地：左侧3颗青球、上方金色三维星形模型、右侧18格红激光。先预警40tick（2秒），成员寿命400tick（20秒），441tick结束本卡：**从启动到结束约22.05秒，其中弹幕展示20秒**。三类同步正常2秒/隐藏2秒。测试净距为0，可在隐藏期穿过；正常期有伤害，球命中会消耗，重执行可重置。用生存模式、非和平难度和空旷场地，随时stop。

### 命令输出与日志示意

下面是删减后的格式示意，不是每次必须达到的数值：

```text
program-preview: tick=... runtime=RuntimeStats[groups=..., members=..., plans=...]
gameplay=Snapshot[collisionSamples=..., hits=..., damageRejected=...]
events={COMMIT=..., REJECT=...} network={...recoveryRequests=...}
```

日志`run/logs/latest.log`会记录具体拒绝原因，例如：

```text
[program-preview] decision kind=REJECT tick=... group=2 reason=clearance
```

| 症状 / 字段 | 先检查 | 再怎么处理 |
|---|---|---|
| 启动后没弹，`plans>0` | 是否还在预警时间 | 等到warningTicks后，不要立即判渲染失败 |
| `REJECT`且`reason=clearance` | 实际出生位置是否靠近玩家 | 调生成位置/预警设计；测试可显式clearance(0)，正式卡不可无脑照搬 |
| `reason=budget`或`replace-budget` | 批次、总存活、每tick出生额度 | 减单拍/寿命/派生；编译limits不解决运行预算 |
| `reason=near-density` | 玩家附近长期堆积 | 减寿命、分散生成点、减少并发，不只是把同一批拆拍 |
| `reason=evaluation-budget` | 路径/派生计算工作过多 | 简化组合或分时，交维护者分析，不先增加弹量 |
| `reason=unloaded` | 攻击是否跨到未加载区块 | 收紧区域或取消这批，不能强加载整片攻击场地 |
| 有弹但`collisionSamples=0` | 是否装饰、隐藏或窗口未开始 | 核对attack/presentation/hiddenCycle和所选时钟 |
| `collisionSamples>0`但`hits=0` | 是否真实相交、球是否先撞墙 | 查碰撞半径与路径，生存模式实测，不只放大视觉 |
| `hits>0`且`damageRejected>0` | 创造/和平/免疫/伤害管线 | 查目标与伤害拒绝，不用加弹数补偿 |
| 只打一次不再打 | 是否用了once或只有有限every | 持续型改成第3节loop，并检查未调用endCard |
| 循环越来越密 | 是否RETAIN且寿命/派生跨轮 | 计算跨轮峰值；不需要尾弹就选END_AT_BOUNDARY |
| 停止后仍有内容 | stats是否idle、是否另一owner/旧实体 | 先分清归属再报问题，不删整个世界弹幕 |
| 所有弹停一下再校正 | 远程时钟恢复 | 看`program-clock`日志；恢复保留姿态，不因时钟不可信整批隐藏 |

网络中的`recoveryRequests`不是普通时钟恢复次数；时钟不可信不自动请求全量副本。长时间断网仍可能冻结；正常End/Release/寿命结束也会让内容消失，不能把一切消失都叫闪烁。

## 9. 颜色、模型和包络：普通作者知道边界即可

- 内置弹型：`sphere`、`star_prism`、`laser`。
- 颜色：`blue/gold/red/white/green/purple/cyan/pink/orange/default`或`#RRGGBB`；拼错会报错。
- 激光必须同时选`"laser"`样式与`.laser(length, piercesBlocks, hitInterval)`。第二项实际表示**是否穿方块**，`false`会按方块裁剪；别根据旧参数名clipBlocks理解成相反意思。
- 既有几何的新贴图档案可用`ProgramVisuals.register(namespacedId, profile)`在两端setup、构造内容前注册。
- 当前消费档案的贴图、尺寸、几何、星弹摆动和隐藏透明度；不承诺旧实体档案全部多层辉光/变色字段已接入。
- 任意GeckoLib/外部实体模型并未自动变成程序弹型；新增几何仍由框架维护者实现。

“保守包络”就是覆盖弹幕整个运动范围的外接盒，网络据此决定附近玩家是否需要这份弹幕。普通作者用标准操作不必手算，`ProgramMotionBounds`会处理标准运动/重瞄/释放。

扩展Path/Transform时必须提供可信的`radiusBound`（运动最多离原点多远）。新控制操作如果会把弹送出原范围，维护者须扩展通用分析或显式扩大包络，不能只在公共代码里按卡名特判。外部实体源支持逐发快照，任意外部跟随不属于快捷入口保证。

世界采样后创建的新组也有同样要求：要检查采样/创建之前的预警与未来攻击范围如何进入兴趣订阅，不能只验证出生后组的包络。逐点创建组时另测组数峰值、寿命结束后回收和跨轮稳态；预算调整须给出需求与证据，不能用提高上限代替这些检查。

## 10. 接入正式BOSS：宿主维护者负责，不是每卡装配

正式迁移时才接这条链路：

```text
BossProgramHost.open(level, ownerUUID, settings) → controller
controller.background(program, firstTick)      安装一次独立底噪
controller.card(program, firstTick)            清旧卡、保留底噪、切入新卡
controller.movementLocked()                   移动AI读取锁
BossProgramHost.close(level, controller)       结束该场战斗
```

`firstTick`通常取`level.getGameTime()+1`，必须晚于已完成边界。同维度复用一份session且settings一致；最后一场结束才卸载公共宿主。不要每张卡创建runtime或安装网络host。现有接线参考`BossProgramBattle`、`ProgramBackendState`与`AbstractTouhouBoss`；血量/HUD/移动锁/后端NBT已有实现，真实读档续战仍待实机验收。

正式BOSS的活弹、预警、派生和调度由`ProgramSessionStore`保存整个持久化会话，`ProgramSessionCodec`恢复后保留剩余寿命；预览通过`openEphemeral`加入共享运行时，不写入存档。后端/卡号NBT只表示选择，不能替代运行态快照。维护者验收须包含：新建战斗首次保存、重开后的旧弹续飞、预警继续激活、恢复后切卡清旧卡、底噪不重复、预览不恢复、结束后不复活。仅有NBT或codec往返测试不代表这些接线已通过实机。

## 11. 提交前检查

1. program id唯一；明确单轮/循环、方向、寿命、预警、伤害和结束方式。
2. 测数量/时序/派生上界/目标槽/跨轮峰值，不只断言编译成功；参考`ProgramAuthoringTest`。
3. 新组合测试全寿命包络、网络恢复、结束清场；再实机检查可读性。
4. 执行`tools/gradle_task.ps1 build`、`python tools/lang_audit.py`、`openspec validate migrate-boss-cards-to-programs --strict`。所有Gradle调用必须走wrapper。

缺图案找Pattern、缺路径找Path、需要实时世界事实找Decision。不要为一张卡往runtime/网络主循环加形状分支。只有扩展框架时才需要继续读架构设计，不应让普通内容作者靠翻设计稿补齐本指南遗漏的术语。
