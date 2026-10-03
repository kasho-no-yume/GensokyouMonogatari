## Context

灵符是全仓库**唯一**一个「按目标转向」的弹种。它的转向逻辑是每 tick 纯本地计算的：

```java
// TalismanDanmaku#tickHoming
Entity target = this.getTarget();
if (target == null || !target.isAlive()) return;
Vec3 aimPoint = target.position().add(0, target.getBbHeight() * 0.5, 0);
Vec3 toTarget = aimPoint.subtract(this.position());
double angle = Math.acos(clamp(currentDir.dot(targetDir), -1, 1));
if (angle > lossAngle) { this.targetLost = true; ... }   // ← 双端各自判定
```

这里有**两个**变量参与判定，而它们的双端一致性没有保障：

| 变量 | 服务端读到的 | 客户端读到的 |
|---|---|---|
| `target.position()` | 服务端权威位置 | 客户端插值后的位置（滞后若干 tick） |
| `this.position()` | 服务端权威位置 | 客户端自己的模拟位置 |
| 判定结果 `targetLost` | 服务端置位 | **客户端独立重算并置位** |

三个输入里有两个在双端天然不同，而结论被双端**各自**写入一个**既不同步也不入盘**的字段。

### 为什么「客户端多追一会儿」不是可接受的代价

原实现的意图是好的：`targetLost` 的注释写着「用于消除数据包延迟窗口内客户端多追的情况」
—— 它是一个**客户端预测**，目的是让客户端不必等 `DATA_TARGET_ID` 归零的往返就能停下。

预测本身没错，错在两处：

1. **预测不可证伪。** 服务端说「没丢」、客户端说「丢了」时，没有任何机制能发现。
   位置校准比的是位置差，而「多转了 2°」在位置上的表现要累积到肉眼可见才超容差。
2. **预测不可撤销。** 服务端一旦清空 `DATA_TARGET_ID`，客户端会在下一个网络往返后
   停止转向 —— 但它**已经多转的那几帧不会回退**。所以这个预测实际上只在
   「客户端比服务端更早得出同样结论」时才有收益，在两端结论不同时是纯粹的误差源。

正确做法是反过来：**让服务端持有唯一判定权，客户端在结论到达前继续按旧状态表现。**
多转的那 1~3 帧是网络延迟的固有代价，与校准通道测出的「相位亏欠精确等于延迟」是同一件事
（`DanmakuSyncProbeTest#delayShowsUpAsAConstantPhaseDeficit`）—— 客户端**应该**领先，
那是预测架构的固有性质，不是缺陷。

### 为什么 network id 不够

`DATA_TARGET_ID` 存的是 `Entity#getId()`。这个值在 Minecraft 里是**进程内递增计数**，
会绕回、会被复用；`DanmakuMotionState.identityMatches` 的类注释已经写明这条：

> 实体 id 会复用（Minecraft 的 entity id 是递增计数，会绕回），所以跨追踪周期比对
> MUST 用 UUID。

灵符的目标引用是**跨生命周期**的：一枚灵符可以活 1200 tick（默认寿命），而这期间目标
玩家完全可能死亡并让 id 被别的实体占用。当前 `getTarget()` 只检查 `isAlive()`，
所以它会安静地转而追一个新实体 —— 无报错、无日志、无任何可观测症状。

改用 `EntityDataSerializers.OPTIONAL_UUID` 后：`null` 表达「无目标」，
真实 UUID 表达「追这个特定的实体」，被复用的 id 永远匹配不上。

### 为什么 `targetLost` 必须入存档

`danmaku-pipeline-capacity` 的准入判据把「纳入存档」和「同步下发」并列成**两个**
必须条件。本变更暴露的事实是：现有代码里**没有任何一项**状态满足这条判据的后半句。

具体到灵符，读档后的状态是：

```
              存档前              读档后
────────────────────────────────────────────
targetLost    true（已丢失）       false（归零，字段没写盘）
DATA_TARGET_ID 417               0（不存盘，字段回默认值）
⇒ 服务端自己认为「这枚弹没有目标」
⇒ 转向逻辑走 `target == null` 分支
⇒ 弹道在存档边界后发生一次无来由的偏转
```

这不是「退化到旧行为」，是**服务端状态与它自己的历史不符**。修法是把它写进
`addAdditionalSaveData` / `readAdditionalSaveData`，键名沿用既有的具名键风格
（`"Sensitivity"` 已在用）。

## Goals / Non-Goals

**Goals:**

- 目标丢失判定由服务端唯一持有，客户端只消费。
- 目标身份使用不可复用的 UUID。
- 两项状态都满足 `danmaku-pipeline-capacity` 的**两条**必须条件（同步 + 存档）。
- 消除「客户端静默追上一个无关实体」这条无诊断路径。

**Non-Goals:**

- 不引入事件协议、序号、scope 身份或任何新的同步包。
- 不改变转向算法本身（`rotateTowards` / Rodrigues 旋转一字不动）。
- 不改变灵敏度、丢失阈值、默认颜色、伤害或任何玩法数值。
- 不为灵符之外的弹种做同类改造（激光几何在 `danmaku-leg-motion`，换向同理）。
- 不等 `danmaku-track-scope`：本变更不涉及任何 scope 级寻址。

## Decisions

### 1. `targetLost` 进 `SynchedEntityData`，服务端唯一写入

```java
// 服务端：唯一置位点。整段判定（含夹角计算）都在服务端分支内 ——
// 客户端不求夹角、不置位、不短路，直接走下面的转向分支。
if (!this.level().isClientSide) {
    double lossAngle = Math.toRadians(GensokyouConfig.TALISMAN_TARGET_LOSS_ANGLE_DEG.get());
    double angle = Math.acos(Mth.clamp(currentDir.dot(targetDir), -1.0D, 1.0D));
    if (angle > lossAngle) {
        this.entityData.set(DATA_TARGET_LOST, true);
        this.setTarget(null);          // 既有行为：清 id，让新追踪者不再追踪
        return;
    }
}

// tickHoming 开头（双端同构，读同一个同步位）
if (this.entityData.get(DATA_TARGET_LOST)) {
    return;
}
```

**⚠️ 本节初版曾把 `return` 写在 `if` 外面**，那会让客户端在本地夹角超阈值时
提前退出本 tick —— 也就是「客户端自行判定丢失」。这与
`danmaku-target-state` 的场景「客户端不得抢先停止转向」直接冲突：
spec 要求客户端 MUST 继续按持有目标的规则转向。现已改为整段判定收进服务端分支。

**为什么不是「客户端也保留判定，但加一个服务端校正」**：那会保留一个不可证伪的预测源。
本仓库的判据要求是「MUST NOT 依赖本地目标、方块加载状态或本地 tick 推断服务端临时决策」
—— 客户端自行置位 `targetLost` 正是这句话描述的行为。

**「清除 `DATA_TARGET_UUID`」是否保留**：保留。它服务的是**新追踪者**（重载后、
chunk 重载后拿到这枚弹的玩家），与 `targetLost` 的作用面不同：前者是「这枚弹现在没有
目标」，后者是「这枚弹曾经丢失过目标且不可逆」。两者都不可省。

### 2. 目标身份改用 `OPTIONAL_UUID`

```java
private static final EntityDataAccessor<Optional<UUID>> DATA_TARGET_UUID = ...
```

`OPTIONAL_UUID` 是原版 `EntityDataSerializers` 里已有的序列化器（`UUIDUtil.STREAM_CODEC`
的可空包装），不新增任何编解码代码。

`getTarget()` 的解析随之改为 UUID 查找。经核实：

| 端 | API | 结论 |
|---|---|---|
| 服务端 | `ServerLevel#getEntity(UUID)` | **存在**（1.21.1 `ServerLevel:1326`），按维度查询 |
| 客户端 | `Level` 只有 `getEntity(int)` | **无 UUID 版本**，需自建索引 |

`TouhouBossBarRenderer` 的类注释已记着同一条事实（血条只给 UUID，只能扫
`entitiesForRendering()` 比对）。所以客户端这张表**不是本变更的新负担**，
而是这个仓库里每一条「客户端按 UUID 找实体」需求的既有代价。

实现落点（任务 2.2 的实际答案，与初稿不同）：

- **索引本体** `client/ClientEntityUuidIndex`，`@EventBusSubscriber(value = Dist.CLIENT)`，
  插入点 `EntityJoinLevelEvent`、清理点 `EntityLeaveLevelEvent`、退出时 `LoggingOut` 清空。
- 初稿写的是「接 `AbstractDanmakuProjectile#remove()` 路径」，**实测不成立**：
  `remove()` 是弹幕自身的移除钩子，而索引里需要清的是**所有**实体
  （目标是玩家，不是弹幕）。事件是唯一全覆盖的入口。
- **插入点覆盖性已核实**：客户端所有实体（含本地玩家 `ClientPacketListener:446/1220`、
  远程玩家 `:486/:539`）都走 `ClientLevel#addEntity`，而它发 `EntityJoinLevelEvent`。
- **清理必须比对实例**：`ClientLevel#addEntity` 先发 join、再踢掉同 id 的旧实体
  （紧接着发一次 leave）。若清理只按 UUID 无条件删，这条「后到的 leave」会删掉刚插入的
  新实体。用 `Map#remove(key, value)` 限定「仍指向这个实例时才删」，id 复用自动无害。

**为什么值得为这件事建一张表**：替代方案是继续用 network id 并在 id 复用时靠
`isAlive()` 之类的启发式兜底 —— 但那条路径**没有兜得住的判据**（新实体也是 `isAlive()` 的）。
UUID 查不到就是查不到，返回 `null` 让灵符按无目标飞，语义明确。

**为什么解析收敛到 `DanmakuTargetRef.resolve(...)`**：它让「不得回退到按 id 查找」成为
一个可执行断言而不是一句约定。回退一旦被允许，id 复用导致的静默错追就从这条路径复活。

### 3. 两项状态都入存档

```java
// addAdditionalSaveData
tag.putFloat("Sensitivity", ...);          // 既有
tag.putBoolean("TargetLost", this.entityData.get(DATA_TARGET_LOST));
if (uuid != null) tag.putUUID("TargetUUID", uuid);   // 具名键，照 Entity 的写法

// readAdditionalSaveData —— 逐键判存在，缺键即退化为该字段的默认值
```

**目标 UUID 该不该入盘？** 这是一个真实的取舍，两边都有代价：

|  | 入盘 | 不入盘 |
|---|---|---|
| 读档后的表现 | 继续追同一个玩家 | 退化为直线飞行 |
| 玩家已下线时 | UUID 查不到 ⇒ 查不到就是无目标 | 无目标 |
| 玩家换维度时 | **可能追到另一个维度的同 UUID 实体** | 无目标 |

本变更取**入盘**，理由是它与 `targetLost` 同侧：既然「不可逆地丢失目标」要跨存档边界
保持一致，那么「正在追谁」也应保持一致 —— 否则会出现「`targetLost=false` 但目标查不到」
这个既不是丢失、也没有目标的第三种状态，而它没有任何 spec 覆盖。

**读档时的跨维度风险由查询侧兜住**：客户端 UUID 索引只收录**当前维度**的实体，
服务端侧 `level.getEntity(uuid)` 本身也是按维度查询的。玩家换维度时服务端那份
`getEntity` 返回 `null`，灵符按无目标处理 —— 与「玩家不在场」同构，语义正确。

### 4. 修正 javadoc 与配置的数值不符

`TalismanDanmaku#tickHoming` 的 javadoc 写「默认 150°」，
`GensokyouConfig.TALISMAN_TARGET_LOSS_ANGLE_DEG` 的实际默认值是 `120D`
（`defineInRange("talismanTargetLossAngleDeg", 120D, 90D, 180D)`）。
以配置为准，javadoc 改为引用配置键而不复述数值 —— 复述的那个数已经漂了。

## Risks / Trade-offs

- **客户端在结论到达前会多转 1~3 帧。** 这是把预测权交给服务端后的必然代价，与
  校准通道测出的相位亏欠同源（客户端应该领先）。相比「两端结论不一致且不可发现」，
  这是可测量、可预期、且随网络延迟收敛的量。
- **UUID 索引的内存与生命周期。** 每实体一条 `Map` 项。必须与实体移除同批清理，
  否则切世界后旧 UUID 会指向已销毁实体对象。`AbstractDanmakuProjectile#remove()`
  是现成钩子。
- **`Optional<UUID>` 的空值语义。** `Optional.empty()` = 无目标，
  `Optional.of(uuid)` = 有目标。`SynchedEntityData` 的 diff 机制按 `equals` 比较，
  `Optional` 的 `equals` 是值比较，所以重复设置同一个目标不会发包。✔

## Migration Plan

1. 加 `DATA_TARGET_LOST` 与 `DATA_TARGET_UUID`，删除普通字段 `targetLost`。
2. 客户端侧删除自行置位的分支；`tickHoming` 改读同步位。
3. 目标身份改 UUID；建客户端 UUID 索引并接上 `remove()` 清理。
4. 存档读写扩展。
5. 纯函数测试：身份往返、`targetLost` 往返、客户端不置位的断言。
6. 修正 javadoc 数值。
7. 实机：读档后弹道不发生无来由偏转；目标玩家死亡后灵符不追新实体。

## Open Questions

无。本变更的所有设计问题在 `danmaku-event-sync` 的历史讨论中已经收敛，
结论是「它需要的最小实现就是一个同步位 + 一条存档键，不需要事件协议」。
