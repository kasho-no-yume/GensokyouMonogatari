## MODIFIED Requirements

### Requirement: 线框限时且可刷新
线框 SHALL 持续 CLIENT 配置项「建造器冲突线框时长」（默认 6 秒）后自动消失；收到新冲突 payload SHALL 整体替换旧线框集合（不叠加历史）。该时长 SHALL 只影响本玩家的显示，MUST NOT 影响服务端判定或冲突检测的语义。

#### Scenario: 到时消失
- **WHEN** 冲突发生后超过设置秒数
- **THEN** 线框不再渲染

#### Scenario: 再次尝试刷新
- **WHEN** 玩家在旧线框未消失时再次触发一次不同位置的冲突
- **THEN** 旧线框清除，仅显示新一批冲突坐标

#### Scenario: 只改本地显示
- **WHEN** 玩家把线框时长改为 30 秒
- **THEN** 其本地线框停留更久，其他玩家与服务端的冲突判定不受影响
