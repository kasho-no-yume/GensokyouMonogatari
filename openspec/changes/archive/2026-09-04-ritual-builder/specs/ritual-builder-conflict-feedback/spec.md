## ADDED Requirements

### Requirement: 冲突坐标 S2C 下发
搭建因冲突中止时，服务端 SHALL 通过 S2C 包下发全部冲突方块坐标列表；无冲突时 SHALL NOT 下发。

#### Scenario: 冲突触发下发
- **WHEN** 搭建因目标位被占中止
- **THEN** 服务端向该玩家发送含全部冲突坐标的 payload

### Requirement: 客户端红色线框渲染
客户端收到冲突 payload SHALL 在对应方块位置绘制红色线框（`LevelRenderer.renderLineBox`），挂 `RenderLevelStageEvent`；线框 SHALL 精确框定单格 AABB。

#### Scenario: 红框定位准确
- **WHEN** 冲突坐标为某具体方块
- **THEN** 红色线框贴合该方块轮廓绘制

### Requirement: 线框限时且可刷新
线框 SHALL 持续 `GensokyouConfig.ritualBuilderConflictOutlineSeconds`（默认 6 秒）后自动消失；收到新冲突 payload SHALL 整体替换旧线框集合（不叠加历史）。

#### Scenario: 到时消失
- **WHEN** 冲突发生后超过配置秒数
- **THEN** 线框不再渲染

#### Scenario: 再次尝试刷新
- **WHEN** 玩家在旧线框未消失时再次触发一次不同位置的冲突
- **THEN** 旧线框清除，仅显示新一批冲突坐标
