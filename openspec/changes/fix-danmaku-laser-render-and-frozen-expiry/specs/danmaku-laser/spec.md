## ADDED Requirements

### Requirement: 激光渲染 MUST NOT 被弹幕密度 LOD 降级

激光的延迟期预警线与激活期各效果层 MUST NOT 受弹幕渲染密度 LOD 策略（`DanmakuRenderProbe` 的 dense 单通降级）抑制。球型/灵符的单通 LOD 策略不在本条范围内，维持其既有规定。

- **延迟期**：半透明红色预警线 MUST 在任意屏上密度下渲染。
- **激活期**：外发光层、亮核、端盖与发射端法阵 MUST 在任意屏上密度下渲染。
- 仅显式调试层开关（`/danmaku layers`，默认全开）MAY 关断这些层，用于诊断。

理由：预警线是**玩法信息**（告知开火位置与时机），不属于可用「降级换性能」的装饰；且激光数量为个位数到数十，恢复其多层渲染的开销与球/灵符的高密度场景不在一个量级。归档 change `danmaku-dense-render-lod` 的单通条款原文仅覆盖球/灵符，激光本不在降级范围内。

#### Scenario: 高密度下预警线仍可见

- **WHEN** 屏上弹幕密度达到 LOD 门槛（dense 生效），且场上存在处于延迟期的激光
- **THEN** 该激光的半透明红色预警线 MUST 被渲染
- **AND** MUST NOT 因密度门控而被跳过

#### Scenario: 高密度下激活期各层仍在

- **WHEN** 屏上弹幕密度达到 LOD 门槛，且场上存在处于激活期的激光
- **THEN** 外发光层、亮核、端盖与发射端法阵 MUST 与本体一并渲染
- **AND** 激光 MUST NOT 退化为「仅本体单层」

#### Scenario: 显式调试开关仍可关断

- **WHEN** 操作者通过 `/danmaku layers glow` 或 `core` 显式关闭对应层
- **THEN** 激光的对应层 MAY 被关断
- **AND** 这是诊断行为，与密度门控无关

#### Scenario: 与球/灵符策略互不干扰

- **WHEN** 激光豁免密度 LOD 生效
- **THEN** 球型与灵符的 body 单通 / LOD 贴图选择策略 MUST 保持不变

### Requirement: 激光预警线渲染不受密度门控影响

激光延迟期的红色预警线 MUST 无条件渲染（除显式调试关断外），其可见性 MUST NOT 取决于屏上弹幕数量、dense 状态或任何 LOD 降级标志。本条为上一要求的可验收细化，独立列出以保证回归可测。

#### Scenario: 预警线回归验证

- **WHEN** 在密集弹幕场景中调用 `/danmaku laser`
- **THEN** 延迟期内 MUST 立即看到红色预警线沿发射方向延伸
- **AND** 延迟结束转入激活期时 MUST 看到完整多层光束
