## MODIFIED Requirements

### Requirement: BOSS 移动为距离带加自由游走
BOSS SHALL NOT 悬空待机。默认移动策略 SHALL 维持与主目标 `[min, max]` 的距离带，默认 `10 ~ 30` 格，并在带内**自由游走**——位移 MUST 覆盖上、下、左、右，MUST NOT 为固定角速度的环绕轨道。

游走目标点 SHALL 周期性重选，重选时 MUST 排除墙体内部、离任一玩家过近、以及离召唤祭坛过近的点；选不出合法点时 MUST 退化为即时横移而非停止移动。

**位移速度 SHALL 由 config `bossMoveSpeed` 单一驱动，且该配置 MUST 真实生效。** 东方 BOSS 使用 `FairyMoveControl`（绕开原版 `FlyingMoveControl`——`FlyingMob.travel` 只积分 `(xxa, yya, zza)` 输入向量、不读速度 attribute），该控制器唯一的速度来源是 `MoveControl.speedModifier`。因此：

- 游走调用的 `speedModifier` 实参 MUST 取自 `bossMoveSpeed`，MUST NOT 为字面量常量
- `bossMoveSpeed` MUST NOT 被解释为格/tick 的绝对速度；其为 `FairyMoveControl.speedModifier` 的乘区值
- `Attributes.MOVEMENT_SPEED` / `FLYING_SPEED` 对本控制器的位移**无影响**，其取值 MUST NOT 被误当作位移速度的依据
- config 注释 MUST 只表述一种读法，MUST NOT 混用"乘区"与"格/tick"

以下参数 SHALL 可被单只 BOSS 覆写：距离带、移速、垂直偏好、游走点重选间隔、是否允许闪现重定位。

#### Scenario: 距离过近时后退
- **WHEN** 主目标进入 BOSS 的距离带下界以内
- **THEN** BOSS 产生远离主目标的位移，直至回到带内

#### Scenario: 距离过远时前进
- **WHEN** 主目标离开 BOSS 的距离带上界以外
- **THEN** BOSS 产生朝向主目标的位移

#### Scenario: 游走而非环绕
- **WHEN** 主目标处于距离带内且 BOSS 连续运行多个游走周期
- **THEN** BOSS 的运动方向不在单一水平面上保持恒定角速度，可观测到垂直方向上的位移

#### Scenario: 选点失败不卡死
- **WHEN** BOSS 找不到合法的游走目标点
- **THEN** BOSS 退化为即时横移，MUST NOT 停止移动或静止悬浮

#### Scenario: 覆写生效
- **WHEN** 某只 BOSS 覆写了距离带与垂直偏好
- **THEN** 其实际行为符合覆写值而非默认值

#### Scenario: 移速配置真实生效
- **WHEN** 在 config 中把 `bossMoveSpeed` 调高一倍后重载
- **THEN** BOSS 的实测位移速率相应变化；MUST NOT 出现"配置已改、位移不变"的情况

#### Scenario: 位移速度不由 attribute 决定
- **WHEN** 检查 `FairyMoveControl` 的速度来源
- **THEN** 只读取 `this.speedModifier`，MUST NOT 读取 `MOVEMENT_SPEED` / `FLYING_SPEED`；`applyStats()` 设置这两个 attribute 不构成位移速度的依据

#### Scenario: 默认移速约 2 格/秒
- **WHEN** 以默认 `bossMoveSpeed = 0.17` 实测 BOSS 的水平位移速率
- **THEN** 约为 2 格/秒（步行玩家 4.3 格/秒的一半、疾跑的 47%），使 18 格/秒的投射物对其前置量需求可承受

#### Scenario: 游走仍然可观测
- **WHEN** BOSS 在距离带内持续游走
- **THEN** 上/下/左/右各方向的位移仍可观测，MUST NOT 因移速降低而退化为静止悬浮

## ADDED Requirements

### Requirement: 东方 BOSS 击退免疫
全部东方 BOSS SHALL 免疫击退：`Attributes.KNOCKBACK_RESISTANCE` SHALL 为 `1.0`。该免疫 MUST 覆盖全部击退来源（近战伤害、爆炸、活塞），而非仅玩家武器。

免疫 MUST 经由 `AbstractTouhouBoss.bossAttributes()` 统一声明，MUST NOT 在各 BOSS 实体类中重复实现；新增东方 BOSS 只要复用该工厂即自动获得免疫。

BOSS 被击退会破坏站桩输出节奏与弹幕走位，并使玩家可用原版武器推着 BOSS 走，故本要求为强制项。

#### Scenario: 原版武器不推动 BOSS
- **WHEN** 玩家用钻石剑击打任意东方 BOSS
- **THEN** BOSS 不产生击退位移

#### Scenario: 爆炸与活塞同样无效
- **WHEN** BOSS 处于 TNT 爆炸或推动中的活塞旁
- **THEN** BOSS 不被推动

#### Scenario: 新增 BOSS 自动继承
- **WHEN** 新增一只复用 `bossAttributes()` 的东方 BOSS
- **THEN** 其 `KNOCKBACK_RESISTANCE` 已是 1.0，无需额外声明
