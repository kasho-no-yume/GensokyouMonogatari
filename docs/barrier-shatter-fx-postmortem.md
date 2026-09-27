# 结界崩解特效：失败路径复盘（勿照抄）

> **本文 purpose**：记录本模组「结界崩解」特效实现过程中走过的**每一条死路**及其**确切原因**，
> 以免后来者（或未来的自己）看到代码残留时，把同样的错误再走一遍。
>
> **状态**：特效已从 `SukimaPortalRenderer` 移除并归档交接。**本文不描述最终实现，只描述失败。**
> 时间跨度约 2026-09-26 ~ 09-27。相关变更：`openspec/changes/add-barrier-break-ritual`。

---

## 0. 一句话总结

**这个特效从头到尾没有渲染问题。** 十几个小时里我所有的排查都跑偏了，
真正的问题是**驱动方式选错**（用逐 tick 方块实体同步驱动纯客户端演出），
以及**演出状态机在客户端没有自己的时钟**。

如果一开始就用「客户端本地时钟 + 单次状态通知」，
后面 90% 的排查根本不会发生。

---

## 1. 最大的架构错误：用逐 tick 方块实体同步驱动纯客户端演出

### 我做了什么

特效是**纯客户端渲染**，但我让服务端每 tick 同步演出进度：

```java
// serverTick
if (fxTicks < SHATTER_FX_TICKS) {
    fxTicks++;
    changed = true;                 // ← 驱动同步
}

// ticker
if (be instanceof SukimaBlockEntity portal
        && portal.serverTick(...)) {
    portal.syncIfChanged();          // ← 只在状态变化时发包
}
```

结果：**开门一次 = 200 个方块更新包**（`fxTicks` 每 tick 变一次）。

### 为什么这是错的

1. **浪费**：200 个包只为传一个进度计数器。
2. **脆弱到荒谬**：客户端必须**逐个收到 0..200 每一个值**才能播完。**丢一个包就永久卡死。**
3. **差分逻辑与客户端状态会永久脱钩**：`syncIfChanged` 的基准是「服务端上次发了什么」，
   不是「客户端实际收到了什么」。若唯一一次发送发生在客户端方块实体建立之前，
   那个值**永远不会被重发**——这是我在第 9 轮才撞上的坑。

### 正确做法（万象共鸣塔那一类）

```java
// 服务端：状态变化时推一次
public void requestOpen(float newScale, int delayTicks) {
    this.fxSeq++;                        // 序列号自增
    if (level != null && !level.isClientSide) {
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}

// 客户端：看到新序列号就用自己的时钟跑
public int fxTicks() {
    if (lastSeenFxSeq != fxSeq) {
        lastSeenFxSeq = fxSeq;
        fxLocalStart = (int) level.getGameTime();   // 记下本地起点
    }
    return Math.max(0, (int) level.getGameTime() - fxLocalStart);
}
```

**200 个包 → 1 个包。** 丢包无影响（本地时钟自走）。
帧率无关。晚进服的玩家加载区块时看到新序列号，自然从头播一次。

> 这条是我在**最后**才想通的，而且**是用户指出来的**（原话：
> "按理来讲不应该就是个客户端渲染吗？就像万象共鸣塔那样的"）。
> 我当时还在查客户端为什么不收包。

---

## 2. 重复门控吞掉了最后一次同步

```java
// 错误
if (be instanceof SukimaBlockEntity portal
        && portal.serverTick(...)) {     // ← 外层门控
    portal.syncIfChanged();              // ← 内部已经自己 diff 了
}
```

`syncIfChanged()` **内部就有差分判断**（`lastSentScale` / `lastSentOpenTicks` /
`lastSentClosingTicks` / `lastSentFxTicks`）。外面再套一层**没有任何收益**，
只会让 `fxTicks` 从 199 变 200 那一刻（此后 `serverTick` 恒返回 `false`）**发不出去**。

**通用教训**：`A()` 内部已有 `if (无需更新) return;` 时，
外面绝不能再用 `B() 的返回值` 去 gate 它 —— 除非那个返回值代表**另一件事**。

---

## 3. 演出只播一次 + 方块实体持久化 = 无法测试

仪式是**闩锁**的（`barrierLatched`），门一旦打开就长期存在；
而方块实体是**持久化**的。演出跑完后 `fxTicks` 饱和不再增长。

于是客户端每次进游戏，拿到的 `fxTicks` 已经是 200 → **整段演出被跳过，一步都不跑**。

我为此写的第一版"补播"是：

```java
// 错误：一帧内画完 90 步
for (int k = replayFrom; k < SHATTER_TOTAL; k++) {
    emitShatterStep(portal, poseStack, bufferSource, glowType, scale, k);
}
```

**结果：补播变成"一帧闪光"。** 用户描述为"刚进世界时好像有东西闪了一帧"——
**那一帧闪的就是特效本身**。当时我完全没意识到这就是答案。

改为逐帧推进后才暴露下一个问题（`fxTicks` 根本没同步过来）。

> **教训**：演出是**一次性**的，而状态是**持久**的 —— 这两者的组合必须在设计阶段就处理，
> 否则连"能不能复现"都做不到。我在第 6 轮才把这两件事拼起来看。

---

## 4. `fxPlayed` 语义搞反，补播自己挡自己

第一版修法把"已播过"标记放在了**正常路径**里：

```java
// 错误
portal.markFxPlayed();   // ← 客户端收到 fxTicks=1 的第一帧就置位
```

而补播分支的守卫是 `if (portal.isFxPlayed()) return;`
—— **标记在开场就被自己污染，补播永远被自己的守卫挡掉。**

`fxPlayed` 应当表示"**已播完**"而非"已开始"。

---

## 5. 闩锁被瞬时结构重扫撤销 → 演出反复重播

```
onStructureLost → setBarrierLatched(false) + removePortals
                → requestClose → removeBlock
闩锁没了       → 供给判定重新开门 → placeAt → requestOpen
requestOpen     → fxTicks = 0    ← 演出归零重播
```

**演出一直在"归零 → 播 4 帧 → 再归零"循环，周期约 14 秒。**
用户看到的"3 帧白光球"就是循环里的一轮。

`serverPassiveTick` 的注释原话是"闩锁后：永久开启"，
但 `onStructureLost` 会因为**瞬时的**结构匹配失败把它撤销。
设计者已经处理了 `activeMatch() == null`（结构暂时拆毁）这一层，
却漏了"**匹配到了别的仪式**"和"**同一仪式但瞬时不匹配**"。

修法：已闩锁就不该被瞬时失配撤销 —— 真正该关门的情况只有核心方块被拆除
（走 `RitualCoreBlock#onRemove`，那条路径本来就在）。

> **教训**："永久"语义和"瞬时判定"混在一起时，**必须显式写出来**。
> 注释说了不算，代码里要有守卫。

---

## 6. 观测手段本身失效，我却把失效当成了观测结果

这一条我犯了两次，都是**工具层面的错误**，比技术错误更耽误时间。

### 6.1 日志挂在会提前 `return` 的分支后面

```java
private void emitShatterStep(..., int t) {
    if (t < chargeEnd) {
        ...
        return;              // ← 蓄能段在这里就返回了
    }
    if (t < shockEnd) {
        ...
        return;              // ← 崩解段也是
    }
    // 我把计数日志放在这里 ↓
    FXLOG.info("[DIAG] drew {} quads", QUADS.get());
}
```

**永远打不出来。** 但我当时把"日志里没有"当成了"代码没执行"，
于是开始怀疑深度遮挡、billboard 朝向等一系列**根本没被观测到的东西**。

> **教训**：日志打不出来时，先怀疑日志的位置。
> 和「过滤器写错时『查不到』不能当否定证据」是同一类错误：
> **观测手段失效 ≠ 被观测对象不存在。**

### 6.2 每帧触发的 logpoint 冲掉了要看的记录，还制造了卡顿

我在 `render()` 入口挂了一个**无条件 logpoint**（每帧求值一个长字符串），
想看 BER 有没有被调用。结果：

- 每秒约 60 条记录，**把 100 条的事件缓冲区瞬间填满**，
  同期挂的 `[SYNC]` 记录**全被挤出去了**；
- 用户反馈"开仪式有较严重的卡顿" —— **那是我造成的**。

> **教训**：探针频率必须匹配事件的**真实**频率。
> 无条件 logpoint 适合放在低频事件上（如 `handleUpdateTag`），
> 放在每帧路径上就是自伤 + 自毁证据。

### 6.3 断点条件求值失败 = 无条件暂停

```java
// 我在第 390 行设条件 t != prev，但 390 行实际是：
int t = portal.fxTicks();      // prev 还没声明！
int prev = portal.lastEmittedFxTick();   // ← 391 行
```

IntelliJ 在条件求值失败时的默认行为是**无条件暂停**。
于是它每帧都停，看起来像"命中了"，而且游戏冻住。
而且**改文件后行号会漂移**，旧断点会静默失效或落在错误位置。

> **教训**：改过文件后必须重新核对断点行号；
> 断点"命中"时先看 `lineText` 和条件是否真的生效。

---

## 7. 我猜错的那些（都不是原因）

按浪费时间的顺序：

| # | 我的猜测 | 实际 | 排除方式 |
|---|---|---|---|
| 1 | `entityInside` 会把特效**传送走** | 特效是即时几何顶点，**不是实体**，没有 AABB，传送逻辑碰不到 | 静态推理即排除 |
| 2 | `.noOcclusion()` 未设 → 方块**深度遮挡** | 确实没设，但半径 6.4 格的环远在 1×1×1 方块**外面**，遮挡盖不住 | 几何矛盾 |
| 3 | billboard **局部 Z 方向反了** | 三深度探针 `{0.4, 0, -0.4}` **全在方块内**（半宽 0.5），探针从设计之初就废 | 探针设计错误 |
| 4 | 纹理 **UV 错** / **没加载** | UV `0,0→1,1` 正确；`SimpleTexture@34533` `id=58` **确实加载了**；PNG 中心像素 `(255,255,255,255)`，源文件与构建产物 MD5 一致 | 调试器读数 + 离线验图 |
| 5 | RenderType / 顶点格式 / `depth_test` | 全部正确，且与**已确认可见**的眼睑**同一个 RenderType** | 调试器读 `CompositeState` |
| 6 | quad 没被提交 | `QUADS` 从 1 涨到 **6194**，崩解段 127 quad/帧 | 计数器读数 |
| 7 | 演出没播 | `t` 确实在推进，`played=false` | logpoint 读数 |

**第 4、5、6 条花的时间最长** —— 因为它们看起来最像"渲染 bug"，
而实际上渲染代码一行都没错。

> **教训**：当一个假设可以用**一行读数**证伪时，先去读那个数，别先改代码。
> 我在没有 debugger 的前提下靠日志猜了五六轮；
> 有了 `idea_xdebug_*` 之后**一次就拿到全部真相**。

---

## 8. 运行时代码改动的两个坑

### 8.1 改局部变量 → 每帧被重新赋值

```java
// 想把半径放大 10 倍做测试
xdebug_set_variable(scale, 20.0F);   // ← 生效了一帧！
```

`scale` 是 `emitShatterFx` 的**参数**，下一帧就由调用方重新赋值。

> **改运行时状态必须改持久字段**（方块实体字段 / 静态字段），改局部变量无效。
> 正确做法：`xdebug_set_variable` 打在 `portal.scale` 这种 BE 字段上。

### 8.2 探测未运行的东西

我把 `scale` 改成 20.0（64 格大环）后问用户"看到了吗"，得到"什么都没有"。
但**此时运行的是旧 class**（我改的是源码没重编），且 `t` 早就跑完不再前进。
**我观测的是一个不会动的目标。**

---

## 9. 服务端与客户端的 `fxChargeEnd` 不一致

`requestOpen` 写 `fxChargeEnd = Math.max(1, delayTicks)`，
而 `delayTicks` 来自两处不同配置 → 两扇门一个 60 一个 1。
1 意味着"没有蓄能段，开门直接崩解"。

**我曾把 `openDelayTicks()` 的实现误读为读递减的 `openDelay`**，
据此宣称"蓄能段会消失"并"修"了一个不存在的 bug。
实际实现是 `return Math.max(1, fxChargeEnd);` —— **本来就对**。

> **教训**：用**方法名**猜语义而**不读实现**，会"修"出不存在的问题，
> 并且在正确的代码上留下一堆解释性注释。
> 改之前先确认自己读的是不是实现本身。

---

## 10. 视觉层面最终没达标的部分

即便状态机修好、演出能播完，实际观感仍不达预期：

- **位置不对** —— 特效坐标在方块内部（半宽 0.5），`GLOW_DEPTH = 0.05` 远不够推到方块外
- **纯白无蓝** —— 纹理是 `255,255,255`，调色 `0xFFFFFF`
- **以帧率亮灭** —— 所有元素用整数门控（`t % 8`、`since % 12`）。
  在 60 fps 渲染 20 tick/s 时钟下，每元素"画 1 帧空 11 帧"，
  **而且光束有 11/12 的时间根本不存在**
- **没有膨胀感** —— 球心用的是先胀后缩曲线，不是持续膨胀

修到一半（去掉整数门控、加 `0xC2DCFF` 冷白）时用户决定移交，**未完成验证**。

---

## 11. 移交给接手者：当前遗留的孤儿代码

特效已从 `SukimaPortalRenderer`（612 → 342 行）移除，但**方块实体里的整套 FX 机制还活着**：

| 符号 | 状态 | 说明 |
|---|---|---|
| `playShatterCues` | ⚠️ **仍在被调用** | **崩解音效照放，但画面已删除** —— 这是当前唯一的实际缺陷 |
| `fxTicks` / `fxChargeEnd` / `SHATTER_FX_TICKS` | 仍活跃 | 音效时钟 |
| `FX_SHELL` / `FX_BEAM` / `FX_EMBER` | **完全死代码** | 各只出现 1 次（自身声明），粒子时代残留 |
| `fxSeq` / `fxPlayed` / `fxReplayTick` / `lastSentFxTick` / `openDelayTicks` / `moteIndex` / `renderRandom` | **死代码** | 外部引用全为 0（渲染端已删） |
| `burstDone` | 仍被 `SukimaPortalRenderer:128` 引用 | 需确认是否还有意义 |
| `src/main/resources/.../textures/particle/shatter_glow.png` | **孤儿资源** | 无人引用 |

**接手者第一件事建议**：决定音效去留。若保留无源音效请忽略，
若不要则连同上面所有死代码一并清掉。

---

## 12. 值得保留的成果（与特效无关，是好的改动）

| 改动 | 文件 | 价值 |
|---|---|---|
| 修好开门**冻结世界**的死循环 | `BarrierBreakBehavior` | `surfaceProbeOffsets` 抽出，r=0 时步长为 0 导致死循环 |
| 闩锁守卫 | `BarrierBreakBehavior.onStructureLost` | 永久闩锁不被瞬时失配撤销 |
| 重复门控修复 | `SukimaBlock.getTicker` | `syncIfChanged` 无条件调用（见 §2） |
| **fxSeq + 客户端本地时钟** | `SukimaBlockEntity` | 200 包 → 1 包（见 §1） |
| 眼睑 128×256 抗锯齿 | `tools/textures/sukima.py` | 16×32 放大后明显模糊 |
| 眼睑与虚空**共用开合系数** | `SukimaPortalRenderer` | 曾脱钩成"开闭轴歪了" |
| 移除 BER `render()` 里的 `isClientSide` | `SukimaPortalRenderer` | 该守卫让整个方法成为死代码 |

---

## 13. 方法论复盘

1. **有 debugger 就别猜。** `idea_xdebug_*` 一整套 JVM 调试 API 一直在 MCP 里挂着，
   我最初误判"没有调试器"、靠日志猜了五六轮。**值是什么、方向朝哪、对象在不在** —— 都是一次读数的事。
2. **logpoint > 断点。** 不暂停、不卡游戏、一次拿到状态对比。
   代价是必须把探针放在**低频**路径上，且条件里的变量要在作用域内。
3. **"现象只出现一帧"是极强的线索。** 一帧闪现 = "整段被压进一帧"的典型特征，不是不渲染。
   我当时跑去查深度遮挡。
4. **区分"观测不到"和"不存在"。** 至少要有一次"在入口无条件打一枪"来确认函数被调用。
5. **先问清视觉诉求再写代码。** "逐渐膨胀的稍带点蓝色的能量球 + 径向光束"这三个词，
   我在最后才从用户反馈里拼出来，此前一直在猜"够不够亮"。

---

## 14. 工具备忘

本项目用 IntelliJ MCP 做 JVM 调试（`idea_xdebug_*`）：

- `xdebug_start_debugger_session` 带 `configurationName`；客户端启动慢，**MCP 调用会超时但会话其实起来了**，
  用 `get_debugger_status` 复核，别重复启动。
- `xdebug_set_breakpoint` 设 **logpoint**：`isLogMessage: true` + `logExpression` + `suspendPolicy: "NONE"`，
  `isLogStack: true` 还能打调用栈（**静默抓调用链的神器**）。
- `xdebug_control_session` 的 `DRAIN_EVENTS` 拿记录，输出**极长会被截断**，
  用 PowerShell 正则从落盘文件里筛，别整份读。
- 客户端 BE 用 `loadAdditional` 创建（不是一上来就拿 update tag）——
  两条同步路径**只读一条**会导致客户端值永远停在默认值。

> 环境备忘：Minecraft 单人游戏**窗口失焦即停止渲染**，
> 渲染线程上的断点会因此不再命中；服务端逻辑断点不受影响。

---

## 15. 2026-09-27 重写后的两次回归（本文档最新一条）

前面的 §1~§14 记录的是**第一次实现**的失败。重写之后（绝对锚点 + 8 秒时间轴已落地、
离线测试 285 项全绿），第一次实机验证仍然直接失败：

> **眼与全部演出完全不可见；异常音效永不停。**

这次很有教学价值：**两个 bug 都不是猜测出来的，各自有精确的根因和可复算的算术**，
而且**两个都不可能被 281 项离线测试发现**。

### 15.1 BER 传入姿态已经是"方块局部"——改姿态时叠加了两次平移

`SukimaPortalRenderer.render()` 里，为了让光球/光柱/水平烟环保持世界朝向，
我把演出段和 billboard 段拆成两个 `pushPose`，并把平移改成了**世界坐标**：

```java
poseStack.translate(center.x, center.y, center.z);   // ✗ 错
```

但 `LevelRenderer` 在调 `blockEntityRenderDispatcher.render()` **之前**已经平移过了：

```java
// net/minecraft/client/renderer/LevelRenderer#renderLevel
posestack.pushPose();
posestack.translate(blockpos4.getX() - d0, blockpos4.getY() - d1, blockpos4.getZ() - d2);
this.blockEntityRenderDispatcher.render(blockentity1, f, posestack, multibuffersource1);
```

`BlockEntityRenderDispatcher#setupAndRender` 则**原样透传**，一个动作都不做。
所以 BER 拿到的姿态是"**已平移到方块原点**"，偏移 MUST 是方块局部的
（原代码的 `translate(0.5, CENTER_Y * scale, 0.5)` 一直是对的，我把它改坏了）。
改成世界坐标后整扇门画到约两倍远处 → **全在屏幕外**。

> **指纹：粒子在、眼不在。**
> `level.addParticle` 吃的是**绝对世界坐标**、完全不受姿态影响，
> 而眼/球/柱/环全走姿态。所以"只有粒子出现、其他什么都没发生"
> **不是粒子系统的问题，而是姿态被翻译错了**。
> 下次见到这个组合，先怀疑双重平移，别去查深度剔除或 culling。

**顺带的一条**：既然传入姿态**只有平移、没有旋转**，
那么"世界对齐段"和"billboard 段"根本不需要两个 `pushPose`——
一次 `pushPose` + 局部平移画演出 + `mulPose(camera)` 之后画眼，
就只有一个旋转起点。分段越多，越容易在其中一段里用错坐标系。

### 15.2 递进音效节点塌成 tick 0 —— 每 tick 叠 4 声

旧存档里已闩锁的门是用**旧键名**（`OpenDelay` / `FxSeq` / `FxTicks`）存的，
没有 `FxStart` / `FxChargeEnd`。于是：

```
fxStartGameTime = -1                    → fxElapsed() 恒返回 0
fxChargeEnd     = max(1, getInt(...))   → 1
playShatterCues() 每 tick 跑，t = 0：
    (int)(1 × 0.1) = 0   ← 命中
    (int)(1 × 0.3) = 0   ← 命中
    (int)(1 × 0.5) = 0   ← 命中
    (int)(1 × 0.7) = 0   ← 命中
```

**四个递进节点全部塌成 tick 0，而 `t` 恒为 0 → 每 tick 命中 4 次 →
每 tick 叠 4 声紫水晶共鸣，永不停。** 同一个原因还让
`fxElapsed() >= fxChargeEnd()`（`0 >= 1`）恒为假，`openTicks` 永不增长，
那扇旧门永远闭着。

**这条的结构和 §3 完全同型**：*常量源 + 相对阈值 → 条件恒真*。
§3 是 `fxPlayed` 恒真，§15.2 是 `t` 恒等于塌零后的阈值。
**凡是"相对某个可被配置/存档写坏的量"的阈值，都要问一句：
这个量取最小值时阈值会不会塌成 0，会不会和常量源撞上。**

修法（三处，缺一不可）：

1. **读旧键** `OpenDelay` 作为 `FxChargeEnd` 的回退；
2. **服务端给缺锚点的门补一个"演出早已过去"的锚点** ——
   旧档的门**不该**给玩家重播一遍 8 秒演出，正确语义是"眼立刻张开、演出视为已结束"；
3. **节点下限 + 去重**：`chargeCueNodes(int)` 抽成纯函数，
   `max(1, min(end, ·))` 封死塌零，升序去重防短蓄能时多比例落同一 tick 叠声。

### 15.3 这次为什么测试全绿还是全灭

285 项离线测试里**没有一项**能碰到这两个 bug：

- 双重平移是**渲染期坐标**问题，离线测试拿不到 PoseStack；
- 节点塌零是**存档兼容性 + 常量源**的组合，只有真的加载一个旧 NBT 并跑满若干 tick 才会发生。

> **因此新增了一条纯算术离线断言**（`SukimaChargeCueNodesTest`）：
> 把 `chargeCueNodes` 抽成不依赖任何世界状态的纯函数，
> 对 `chargeEnd ∈ [1,400]` 全区间断言**无节点为 0、严格升序、不越 `chargeEnd`**。
> 这类"音效照播、画面照常、日志一行错都没有"的故障**只能靠算术断言挡住**。

### 15.4 复盘：两条该写进 tasks 的纪律

1. **动 `render()` 的姿态之前，先确认传入姿态的坐标系** ——
   读 `LevelRenderer` + `BlockEntityRenderDispatcher` 两行源码（`$14` 里已有源码包位置），
   不要凭"看起来像世界坐标"就下手。
2. **改持久化字段的键名/语义，必须同时写回读路径和迁移** ——
   `readAdditional` 之外的每一条读法都要过一遍，且要有一个"旧档首次 tick 时收敛"的分支。

### 15.5 §11 的孤儿代码现状（重写后已变化）

§11 列出的一堆死代码在本次重写中**已全部清除**：
`playShatterCues` 现在驱动的是**真实存在**的演出（不再是"音效照放、画面已删"），
`FX_SHELL` / `FX_BEAM` / `FX_EMBER` 已被 `FxGeometry` 的折线 + `ParticleTypes.GLOW` 取代，
`fxSeq` / `fxPlayed` / `fxReplayTick` / `lastSentFxTick` / `openDelayTicks` / `burstDone` / `moteIndex`
均已删除。`shatter_glow.png` 仍作为球壳叠加层在用，**不是孤儿**。
唯一的遗留是 `moteIndex` 曾被用于抖动随机化——现在改为
`level.getGameTime() + partialTick` 驱动的浮点哈希，随机性由时间轴本身提供。
