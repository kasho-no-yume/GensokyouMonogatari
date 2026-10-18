# BOSS 符卡程序开发：从可运行模板开始

适用状态：阶段1–5已交付；第六期先提供作者入口，现有BOSS尚未迁移。本文的Java名称是真实API。写新内容先用`danmaku/program/author/`，不要复制旧预览里的codec、handler或网络安装代码。

## 最小可运行例子

下面就是`AuthoringTemplates.volley()`使用的写法。先照抄整个方法到自己的内容类，修改program id，再逐项调整。

```java
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

含义：取最近至多5名有效玩家，固定第0槽作为瞄准对象；从owner局部上方1格锁定发射帧。卡时间0/30/60开始三次预警，每次20tick后出生9颗弹，各自沿扇形方向以.18格/tick飞行100tick；181结束本卡。这里只有一份朝主目标的攻击，**捕获5人不会自动复制5份**。需要逐人点名时显式为槽0..4创建独立group；场地阵列只建一份。

`styled`的数字依次是**碰撞半径、伤害倍率、视觉半径**。三者独立。速度单位是格/tick；每秒20tick；角度均为弧度，度数用`Math.toRadians`。示例数值是开发模板，正式BOSS可调平衡参数从config传入。

## 一张卡只需决定这些事情

| 需求 | 使用入口 |
|---|---|
| 在指定时刻做事 | `card.at(tick, c -> ...)` |
| 有限连发 | `card.every(first, period, count, c -> ...)` |
| 单轮/循环 | `once()` / `loop(ticks, TailPolicy...)` |
| 攻击弹/纯装饰 | `MemberProgram.attack(...)` / `decoration(...)` |
| 整片沿局部+Z推进 | `fixedDirection()` |
| 扇形、球面等逐弹沿图案方向飞 | `templateDirections()` |
| 按稳定随机谱系向外飞 | `randomDirections(channel)` |
| 局部旋转、呼吸、平移组合 | `transform(new Transforms.Compose(List.of(...)))` |
| 路径分段 | `Paths.Segments`，每段从零位移开始，明确尾部策略 |
| 母弹周期发射 | 成员builder的`emit(selection, firstAge, period, count, child)` |
| 母弹替换为子弹 | `replace(selection, trigger, child, onReject)` |
| 花瓣脱离 | `card.release(group, centerSelection, petalSelection, speed)` |
| 再次瞄准玩家 | `card.reaim(group, slot, selection, speed)`，slot=-1随机有效目标 |
| 持续共享移动组 | `aimedMoving(...)` + `fireAttached(...)` |
| 停止未来生成 / 淡出整组 | `stopEmission(group)` / `collapse(group, fade)` |
| 锁BOSS移动 | `lockMovement(group, duration)`；宿主读取`movementLocked()` |
| 结束当前卡 | `endCard()`；不会结束整场战斗或独立背景 |

`fire`的时间是**开始预警**的时间；成员age从实际出生算起。`at/every`在循环程序里是本轮时间，单轮程序里等同卡时间。动作按声明顺序校验句柄：先创建目标、组，再引用它；不要先写晚时刻的生产者，再在早时刻消费。句柄不能从另一张卡拿来复用。

`every`会有限展开，普通少量拍最直观；大量高频发射用成员`emit`或底层`CreateEmitter`，不要展开几千个节点。循环内不要调用`endCard()`，否则第一轮结束整个卡实例；用`collapse`安排本轮收束，下一轮由播放策略启动。

## 三份真实模板

源码：`src/main/java/com/bitsson/gensokyou/danmaku/program/author/AuthoringTemplates.java`。

- `volley()`：有限齐射、固定目标槽、图案方向、RGB颜色。
- `flower()`：花心+花瓣两种样式、呼吸+旋转+推进、age之外的轮内时刻释放。
- `carriers()`：移动母弹周期生成下一代，下一代定时替换为第三代；派生代数和保守数量自动推导。

游戏里使用生存模式，在空旷区域执行：

```text
/gs_program_preview start author_volley
/gs_program_preview start author_flower
/gs_program_preview start author_carriers
/gs_program_preview start author_hidden
/gs_program_preview stats
/gs_program_preview stop
```

三个入口只是开发预览，不会替换现有BOSS符卡。新增自己的预览需在预览内容注册处添加入口，不要改网络、公共范围计算或渲染器。

`author_hidden`是额外的隐藏态检查场景：在指令执行时视线前方生成固定阵地，预警2秒后出现左侧3颗青色静止球、上方金色三维星形模型、右侧红色激光（18格长），同步正常2秒/半透明隐藏2秒，持续20秒。预览出生净距为0以便走近重复测试，不代表正式内容安全默认。生存模式可在隐藏期穿过它们验证不碰撞；正常期有伤害，球命中后会被消耗，可重新执行指令重置。场地与发射轴锁定，不跟随之后转头；用空旷场地、非和平难度，`stop`随时清场。

远程时钟短暂失准时保留最后画面，短容差后冻结运动，收到独立权威时间样本再校正，允许轻微拉回；不会仅因时钟置信度下降整批隐藏。长时间网络中断仍可能停住，正常停止/寿命结束/兴趣移除仍会消失。这是显示恢复策略，不改变服务端碰撞。

## 伤害、方向与预算：必须读

攻击builder默认全寿命伤害；装饰builder全程无害。需要“先悬停无害，后来伤人”时显式设置`presentation(new Presentation.Profile(...))`，伤害窗口为半开区间`[start,end)`。窗口的时钟必须明确，通常选`MEMBER_AGE`。透明淡出不自动关伤害；`collapse`会关伤害并取消后续发射。

普通弹和激光都支持半透明隐藏态：`.hiddenCycle(TimeDomain.MEMBER_AGE, 40, 20, 0)`表示每40tick前20tick正常、后20tick隐藏。隐藏时仍绘制，且完全跳过实体/方块碰撞；之后自动恢复。球弹及真实三维星形模型遵守旧实体档案`hiddenAlpha`（默认96/255），本体RGB保持原色；激光遵守旧实体独立系数0.18，束/端点/法阵的alpha和RGB同时降低，不重复乘暗态系数。它与仅调`Fade`不同：Fade不自动取消碰撞。完全消失请结束成员或显式alpha=0并关闭伤害窗口。任意GeckoLib/外部实体模型并未自动变成程序弹型；已注册的程序模型走同一档案透明度策略。

必须选方向规则。`Patterns.Fan`只给方向，`templateDirections()`才让路径沿各个方向飞。`fixedDirection()`会让所有成员的路径沿同一局部轴运动，适合整片推进的阵列。旋转、呼吸的变换顺序就是列表顺序，通常先呼吸、再旋转、最后平移。

派生需求保守按所有母成员计算，不因选择器只选中几颗而降低上界；这用于安全准入，不等于实际发射数量。builder自动推导需求，`ProgramLimits`独立限制可接受预算，超限应重新评估内容或显式调整合理预算。不能把预算改大作为通用解决方案。运行期还会检查存活、每tick出生、净距和近域密度；一批可被整体拒绝。观察stats和REJECT原因。

R1/R2/R3仍是内容责任：有预警、保留三维躲避解、控制真实展开和派生峰值/稳态密度。编译通过不代表已经通过可读性设计审查，也不代表任意高密度都达到性能目标。

## 颜色、弹型与范围

内置弹型`sphere / star_prism / laser`；颜色支持`blue/gold/red/white/green/purple/cyan/pink/orange/default`及`#RRGGBB`。拼错会在作者构建时失败。laser还必须配置`.laser(length, clipBlocks, hitInterval)`，漏配会报错。注册既有几何的新贴图样式用`ProgramVisuals.register(namespacedId, profile)`，在两端共同setup中、构造内容前注册；新几何仍是扩展工作。当前程序渲染消费profile的贴图、尺寸、几何及星弹摆动，不承诺旧实体profile的全部多层辉光/变色字段已接入。

`ProgramMotionBounds`为标准路径/变换及重瞄/释放预留保守全寿命范围，包含已出生派生和当前重置路径。不会按卡名称特判。扩展Path/Transform必须实现可信的`radiusBound`；新控制操作若改变未来运动，必须扩展通用包络规则或由宿主提供显式保守包络，不能只让本地预览“看起来正常”。外部实体源支持逐发快照，任意外部跟随不属于作者快捷入口保证。

## 接BOSS：由宿主维护者做一次

`BossProgramHost.open(level, ownerUUID, settings)`复用维度公共session并返回`BossProgramController`。使用`background(program, firstTick)`安装一次底噪，`card(program, firstTick)`切卡，`movementLocked()`读取移动锁，战斗结束调用`BossProgramHost.close(level, controller)`。`firstTick`必须在已完成边界之后，通常是`level.getGameTime()+1`。同维度settings必须一致，由配置装配一次。

`card`会清旧卡实例/成员并保留独立背景；结束战斗只影响自身owner；最后一场关闭才移除公共宿主。不能每张卡重复创建runtime或安装网络host。现有BOSS尚未调用此入口：血量/HUD/后端选择/加载策略属于第六期正式迁移，作者模板不得顺手接入。

## 提交前检查

1. 用模板写内容，program id唯一；明确时间、寿命、方向、伤害、预警和结束方式。
2. 验证数量、派生上界、峰值密度、目标槽以及缺口；不要只断言“编译成功”。参考`ProgramAuthoringTest`。
3. 新运动组合检查整寿命包络和网络恢复后的轨迹一致；先自动化，再实机看可读性。
4. `tools/gradle_task.ps1 build`，`python tools/lang_audit.py`，`openspec validate migrate-boss-cards-to-programs --strict`。Gradle必须走wrapper。

不要为一张卡向runtime或网络主循环增加形状分支。缺空间排列扩展Pattern；缺数学路径扩展Path；需要新的实时世界事实再扩展Decision。底层设计语义仍见父change的operations与pattern-recipes，本文是实际作者入口。
