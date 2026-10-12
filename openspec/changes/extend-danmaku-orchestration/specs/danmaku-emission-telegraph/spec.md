## ADDED Requirements

### Requirement: 发射源预警符印

一拍 SHALL 能声明一个领示提前量（`lead` tick）。声明后，系统 SHALL 在该拍真弹发射前的第 `lead` tick，于该拍的发射原点生成一枚**无伤害、无碰撞**的预警符印；到点后真弹 SHALL 从**同一原点**生成。

符印 MUST NOT 计入弹幕实体数预算，MUST NOT 参与命中判定或伤害结算。

未声明领示提前量的拍 MUST 保持既有行为：不生成符印，真弹照常发射。

#### Scenario: 符印先于真弹出现
- **WHEN** 某拍声明 `lead = 20` 且其发射时刻为本轮第 44 tick
- **THEN** 符印 SHALL 在本轮第 24 tick 出现，真弹在第 44 tick 生成，二者原点一致

#### Scenario: 符印不计入弹数预算
- **WHEN** 场上弹幕实体数已达上限且某拍生成符印
- **THEN** 符印 SHALL 照常生成，MUST NOT 因弹数上限被抑制，也不得占一个实体名额

#### Scenario: 符印不判伤
- **WHEN** 玩家与符印重叠
- **THEN** MUST NOT 结算任何伤害，符印也不会因此消失

#### Scenario: 未声明领示即旧行为
- **WHEN** 某拍不声明领示提前量
- **THEN** 不生成任何符印，行为与引入本能力前逐位相同

### Requirement: 符印与真弹共用同一次快照

若某拍属于一个锁轴组并声明了领示提前量，符印 SHALL 按该组的锁轴规则在**领示时刻**捕获并保存快照，真弹 SHALL 复用**同一份**快照，MUST NOT 在真弹时刻重新采样。

#### Scenario: 玩家在领示期移动后符印与真弹仍重合
- **WHEN** 符印出现后玩家移动了若干格，领示期结束时真弹生成
- **THEN** 真弹的发射原点与朝向 MUST 与符印一致，MUST NOT 跟随玩家偏移

### Requirement: 符印可被安全取消而淡出

当该组因安全判据（见 capability `danmaku-emission-safety`）被取消时，其符印 SHALL 进入淡出并消失，MUST NOT 继续保留或转而生成真弹。

#### Scenario: 取消组时符印淡出
- **WHEN** 某组在真弹发射时刻被判为不满足最小安全距离
- **THEN** 不生成任何真弹，该组符印淡出消失
