## Why

BOSS 战这条链路上叠着三个问题，而且互相咬合：

1. **崩服**：程序运行时把「组」当成无寿命容器，`collect()` 只回收 `!emitting` 的组；而循环型符卡每个循环边界都会重新声明组、每拍 `surfaceFire` 都会新建一个一次性组，这些组 `emitting` 恒为 `true` 且 `retain` 令循环边界也不取消它们。组表单调增长，撞上 512 上限后 `createGroup` 抛 `group limits/slots`，服务端 tick 直接崩掉整个战斗。实测一局大妖精打到第 3 张符卡约 3 分钟后必崩。
2. **切牌不跟手**：符卡切换被「当前符卡的循环边界」挂起，血量跨过阈值后最多要等一整个循环（大妖精雨卡 = 10 秒）才换牌。原作是达阈值即切、立刻播宣言。
3. **激光判定太小**：程序激光的判定半径被普通弹的平衡倍率（0.5）一并砍半，比迁移前的旧实体激光窄四倍，判定窗口与视觉粗细对不上。

三件事都落在同一条链路上（符卡生命周期 → 运行时组管理 → 客户端表现）：修回收要动组的退休语义，切牌要动同一批组的收束时机，演出要挂在切卡事件上。分三次改会互相踩，故合并为一个 change。

## What Changes

- **BREAKING** 符卡切换时机（**程序后端**）：血量跨过下一张符卡的起始阈值后**即刻切换**，MUST NOT 挂起到当前符卡的循环边界。符卡的循环长度退回为「节拍重放周期」，不再是切牌闸门。跨过多张阈值时按「起始门槛已达成的最后一张」直接落到最终应处的符卡。旧符卡的弹幕在切换瞬间按 30 tick 的 fade 淡出，MUST NOT 瞬间消失。旧实体后端一行不动——它服务的 BOSS 符卡内容将随该后端整体退役，届时一并删除。
- 程序运行时新增**组退休**语义：组的「能否继续发射」「程序是否还会引用它」「已出生成员」三件事分离。退休只影响回收，MUST NOT 清成员、MUST NOT 取消已 prepare 未出生的批次、MUST NOT 改动组的发射许可。循环边界把上一轮的组标记退休，成员按各自寿命自然消亡后组被回收。
- 一次性发射组（`surfaceFire`）在批次出生时封口，出生后不再发射，成员消亡后即被回收。
- **BREAKING** 激光判定半径不再套用普通弹的 0.5 倍率：激光按声明的碰撞半径判定，普通弹维持现行倍率。该倍率只作用于弹射物型弹幕。
- 新增**切阶段演出**：切换瞬间在屏幕中央偏上展开符卡宣言（符卡名 + 旋转法阵 + 朱印 + 起始白闪）、旧弹幕 30 tick 淡出、BOSS 触发宣言姿态、播放声明音效（`minecraft:entity.ender_dragon.growl` 占位）。演出为纯表现层，MUST NOT 改变任何判定、伤害或符卡内容时序，且 MUST NOT 遮挡弹幕。
- 补**长跑收敛**回归测试：连续运行多个符卡循环后组数必须收敛到上界，而非随循环数线性增长。

## Capabilities

### New Capabilities
- `boss-phase-transition`: 符卡切换的即时触发语义与切阶段演出（屏幕符卡宣言、旧弹幕淡出、BOSS 宣言姿态、宣言音效）。

### Modified Capabilities
- `danmaku-program-server`: 组的生命周期与回收——新增「退休」语义、「每批成员各自寿命」的显式要求、跨循环边界未出生批次不得被丢弃、以及长跑组数收敛。
- `danmaku-program-gameplay`: 碰撞半径倍率按碰撞形态分流——激光不适用普通弹的 0.5 倍率。
- `danmaku-track-composition`: 移除「符卡切换延后到循环边界」，新增「符卡达血量阈值即刻切换」接管程序后端。
- `kurodani-yamame`: 「阶段随血量切换不叠加」同步改为即刻切换（不再有挂起）。
- `remnant-touhou-bosses`: 「BOSS 符卡即阶段」补达阈值即切与宣言演出的场景。

## Impact

- 运行时：`danmaku/program/server/DanmakuWorldRuntime.java`（`Group` 状态、`collect()`、`endCycle()`、`endCard()` 的 fade）、`danmaku/program/server/ServerControlOperations.java`（`SurfaceFire` 分支的 `commitAtBirth`）。
- 切换时机：`danmaku/program/gameplay/BossProgramBattle.java`。
- 判定：`danmaku/program/gameplay/ProgramCollisionAdapter.java`；倍率常量注释：`balance/BalanceNumbers.java`。
- 演出：新增客户端切阶段渲染层；沿用现有 `SpellCardNamePayload`（按「符卡下标发生变化」区分宣言与状态补发），MUST NOT 新增携带卡名字符串的包；音效复用原版 `minecraft:entity.ender_dragon.growl`。
- 内容：本 change MUST NOT 改变任何符卡的节拍、伤害、寿命与收束时序；激光的**声明半径**是否补回迁移前数值，作为独立数值决策记录在 design 中。
- 规格：上列五条既有规格的对应条款按 delta 改写；`danmaku-track-composition` 中「循环边界血量选择仍须保持」的程序后端替代条款同步失效。
