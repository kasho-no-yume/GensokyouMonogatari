> 每条任务都挂一条**可离线断言**的验收。灵符的转向数学（`rotateTowards` /
> Rodrigues）本变更**一个字都不改**，所以既有行为基线 MUST 保持不变。

## 1. 目标丢失状态服务端权威化

- [x] 1.1 新增 `DATA_TARGET_LOST`（`BOOLEAN`，默认 `false`）；删除普通字段 `targetLost`。
      **验收**：`targetLost` 字段在 `TalismanDanmaku` 中不再作为独立状态存在。 ✔ 已删除
- [x] 1.2 `tickHoming` 的丢失判定收进 `!isClientSide` 分支；客户端分支整体删除。
      **验收**：客户端代码路径中不存在对 `DATA_TARGET_LOST` 的**写**调用（可用源码扫描断言）。 ✔
      `TalismanDanmakuSourceTest#clientNeverWritesTargetLost`
- [x] 1.3 `tickHoming` 开头的短路改读 `DATA_TARGET_LOST`（双端同构）。
      **验收**：`entityData.get(DATA_TARGET_LOST)` 为真时转向逻辑直接返回。 ✔
- [x] 1.4 保留服务端在丢失时 `setTarget(null)` 的既有行为。
      **验收**：目标 UUID 在丢失后为空。 ✔（字段名由任务 2.1 的 `DATA_TARGET_ID`
      更名为 `DATA_TARGET_UUID` —— 字段类型已非 int，沿用旧名会误导）

## 2. 目标身份改用 UUID

- [x] 2.1 目标同步字段由 `INT` 改为 `OPTIONAL_UUID`（原版已有序列化器，不新增编解码）。
      **验收**：`EntityDataSerializers.OPTIONAL_UUID` 被使用，仓库内无自写 UUID 编解码。 ✔
- [x] 2.2 客户端建立 `UUID → Entity` 索引；插入点为实体加载/生成，清理点为实体移除。
      **验收**：索引在实体移除路径上被清理（切世界后旧 UUID 不得指向已销毁对象）。 ✔
      `client/ClientEntityUuidIndex`：`EntityJoinLevelEvent` 插入、
      `EntityLeaveLevelEvent` 清理（`Map#remove(key, value)` 比对实例）、
      `ClientPlayerNetworkEvent.LoggingOut` 清空。
      **与初稿的差异**：初稿写「接 `AbstractDanmakuProjectile#remove()`」，**实测不成立**
      —— `remove()` 是弹幕自身的钩子，而索引要收的是**所有**实体（目标是玩家）。
      事件是唯一全覆盖入口；已核实客户端全部实体都经 `ClientLevel#addEntity` 触发 join。
- [x] 2.3 `getTarget()` 改为按 UUID 查询；查不到返回 `null`。
      **验收**：查不到时**不**回退到任何按网络 id 的查找。 ✔ 解析收敛到
      `DanmakuTargetRef.resolve`，源码断言禁止 `getEntity(int)`
- [x] 2.4 `setTarget(Entity)` / `setTarget(null)` 的语义随之更新；`null` 映射为 `Optional.empty()`。
      **验收**：重复设置同一目标不产生额外同步（`SynchedEntityData` 按值比较）。 ✔
      `Optional` 是值比较，`Optional#equals` 保证重复设置不发包
- [x] 2.5 **正向断言**：两个不同实体先后占用同一网络 id，断言目标解析结果不因 id 复用而改变。
      **验收**：该场景在变更前会失败。 ✔ `DanmakuTargetRefTest#idReuseDoesNotStealTheTarget`
      **前置条件也被断言**：测试里的 id 分配取最小空闲（与 `Entity#getFreeEntityId()` 一致），
      否则「复用」不会真的发生、测试会静退化成平凡情形。
      **旧行为的对照**同测内断言：按 id 查找仍命中 `target-B` —— 即变更前的结果。

## 3. 存档往返

- [x] 3.1 `addAdditionalSaveData` 写 `TargetLost`（boolean）与 `TargetUUID`（可缺省）。
      **验收**：写盘键名与既有具名键风格一致。 ✔
- [x] 3.2 `readAdditionalSaveData` 逐键判存在读取；缺键退化为默认值。
      **验收**：旧存档（无这些键）读档不抛异常。 ✔ `Tag#getBoolean` 对缺键返回 `false`、
      `Tag#hasUUID` 为 `false` ⇒ 退化为「未丢失 + 无目标」
- [x] 3.3 目标 UUID 不存在时按无目标处理，**MUST NOT** 退化为「已丢失」。
      **验收**：三条互斥状态各自可被读出。 ✔ 由键独立性保证：
      `TargetLost` 与 `TargetUUID` 是两个独立键，写入条件互不依赖
      （源码断言见 `TalismanDanmakuSourceTest#saveKeysArePaired`）

## 4. 文档修正

- [x] 4.1 修正 `tickHoming` javadoc 中的「默认 150°」——配置实际默认值为 `120D`。
      **验收**：javadoc 引用配置键名而非复述数值。 ✔

## 5. 测试与验证

- [x] 5.1 身份往返测试：设置目标 → 读回 → 逐项相等。 ✔
      `DanmakuTargetRefTest#repeatedLookupIsStable` + `#zeroUuidIsNotASentinel`。
      **形式说明**：本仓库没有构造实体与 `Level` 的测试脚手架，故往返在
      **解析层**验证（任意值类型），而非在实体上验证。
- [x] 5.2 `TargetLost` 存档往返测试：置位 → 存档 → 读回 → 仍为置位。 ✔
      同上，以源码级断言承担（`TalismanDanmakuSourceTest#saveKeysArePaired`：
      键名两侧成对、`hasUUID` 与 `isPresent` 写入条件同宽）。
      **已知局限**：这不是真正的 NBT 往返测试，见 5.8。
- [x] 5.3 客户端不置位的源码级断言（见 1.2）。 ✔
- [x] 5.4 转向数学基线不变：既有灵符相关测试**不改期望值**即通过。 ✔
      全量 `build` 通过，未触碰任何既有测试
- [ ] 5.5 实机：读档后弹道**不**在存档边界发生无来由偏转。 ⛔ **阻塞于 5.8**
      **为什么不能靠手工观察**：唯一精确的读法是 `/data get entity`
      （它读的就是存档用的那份 NBT，`TargetLost: 1b` 是确定值），但要先造出
      「已丢失目标」的弹 —— 得等它飞到夹角超阈值，且 `clientTrackingRange(8)`
      要求全程在 8 格内。纯视觉观察无法分辨「多转了 1~3 帧」，
      而那正是本变更刻意接受的代价。
- [ ] 5.6 实机：目标玩家死亡后灵符**不**转向复用同一 id 的新实体。 ⛔ **阻塞于 5.8**
      **为什么不能靠手工构造**：需要服务端把刚空出的那个最小空闲 id **立刻**分给另一个
      实体，同时有灵符在飞行中观察它。这不是「测不准」，是构造不出来。
      该场景已由 `DanmakuTargetRefTest#idReuseDoesNotStealTheTarget` 在解析层覆盖。
- [x] 5.7 完成 NeoForge 编译与客户端实机回归。 ◐ **部分完成**
      ✔ 全量 `build`（编译 + 全部测试）BUILD SUCCESSFUL
      ✔ 客户端实际启动：`ClientEntityUuidIndex` 被 `AutomaticEventSubscriber` 扫描并注册到
      game event bus，全程无 `ClassNotFound` / `NoClassDefFound`
      （即 common 类引用 client 类未触发 dist 问题）
      ⬜ 游戏内行为回归未做 —— 被 5.5 / 5.6 阻塞
- [ ] 5.8 **新增**：为 `TalismanDanmaku` 建立实体级测试脚手架（可构造 `Level` 与实体），
      把 5.1 / 5.2 从「解析层 + 源码级」升级为真正的 NBT 往返，
      并让 5.5 / 5.6 从「不可验证」变成确定性测试。 **本变更唯一的真实缺口**
      **理由**：源码级断言盯不住 `CompoundTag` 的实际行为（键类型不匹配导致
      `getUUID` 抛异常、读档顺序覆盖同步位）；而 5.5 / 5.6 的观察前提无法手工构造。
      **成本**：需要一处测试专用的 `Level` 实现，高于本变更其余部分。
      **建议**：单独立项，不要塞进本变更 —— 它是基础设施而非灵符的行为。

## 与其它变更的关系

- **零前置。** 不等 `danmaku-timeline-sync`、不等 `danmaku-leg-motion`、不等 `danmaku-track-scope`。
  这是本变更可以先行独立归档的原因。
- 前身 `danmaku-event-sync` 已**暂缓**（机制保留，不否决）并移入
  `openspec/changes/archive/2026-10-01-danmaku-event-sync-deferred/`。
  它记录的「阻塞项 A′」在本变更中完整落地；该文档已随之一并移入 archive，
  阻塞项的完整分析由 `danmaku-leg-motion/spellcard-blockers.md` 承接。
