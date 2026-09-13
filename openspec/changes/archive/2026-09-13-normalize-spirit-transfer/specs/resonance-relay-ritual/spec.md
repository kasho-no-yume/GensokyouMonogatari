# resonance-relay-ritual Delta

## RENAMED Requirements

- FROM: `### Requirement: 每 tick 平均分配结算`
- TO: `### Requirement: 每结算周期平均分配结算`

## MODIFIED Requirements

### Requirement: 每结算周期平均分配结算
enabled 期间，路由 SHALL 每结算周期（默认 1 秒 = 20 tick）结算一次：对每对（源, 汇）通道，周期预算 = `min(源out速率份额, 汇in速率份额) × 周期/20`，其中端点速率在其"当前仍需传输的对端"间平均分配（源对仍有空位的汇均分、汇对仍有存量的源均分）。该预算 SHALL 仅作**建议值**：实搬量由源/汇端点自身的每结算周期速率账本（见 ritual-power-attributes 的端点账本要求）与源实时存量、汇实时空位共同截断，路由器 MUST NOT 依赖自身计算保证不超发。同一塔内多对通道间的分配 MUST NOT 采用先到先得的顺序独占；跨塔争用同一边端额度时则由端点账本按调用次序先到先得，次序 = 各路由 tick 的执行顺序，与塔的启停历史无关。速率的小数部分 SHALL 以定点进位处理，MUST NOT 因整除截断归零。跨塔之间不做全局带宽仲裁。

#### Scenario: 一源双汇均分
- **WHEN** 1 个 out=200/s 的源连接 2 个均有空位、in 不限量的汇
- **THEN** 两个通道各约 100/s，两汇均匀上涨

#### Scenario: 汇满自动让位
- **WHEN** 上述某一汇被填满
- **THEN** 源的速率份额在下一结算周期全数流向另一汇

#### Scenario: 慢速小流量不被截断
- **WHEN** 通道预算折算每秒不足 20 点（每周期 <1 定点单位）
- **THEN** 进位累加使流量长期仍等于标称速率，无整帧丢失

#### Scenario: 端点账本兜底
- **WHEN** 两座塔各自计算出的建议预算合计超过源端点标称速率
- **THEN** 本周期合计实搬仍被源端账本截断，无超发

## ADDED Requirements

### Requirement: 路由吞吐展示口径
路由界面摘要的吞吐数值 SHALL 由实际搬运量的单调累计差分折算，口径同 ritual-power-attributes 的"实测速率单调计数器差分口径"，按 `gameTime` 精确计算；采样窗不足一个结算周期时 MUST 沿用上次结果，MUST NOT 以短窗空结算误判为 0，也 MUST NOT 使用无界 EMA 造成滞后虚高。候选行 tooltip 展示的端点数值 SHALL 明确为声明速率上限，MUST NOT 与实际吞吐混淆。

#### Scenario: 吞吐读数不滞后虚高
- **WHEN** 源以 1280/s 稳定供灵
- **THEN** 摘要吞吐读数在一个结算周期内即反映实速，不出现数秒级的缓慢爬升，也不超过 1280/s

#### Scenario: 关/开界面不闪零
- **WHEN** 玩家关闭并立即重开共鸣塔界面，两次采样间隔不足一个结算周期
- **THEN** 摘要吞吐沿用上次读数，MUST NOT 闪现 0
