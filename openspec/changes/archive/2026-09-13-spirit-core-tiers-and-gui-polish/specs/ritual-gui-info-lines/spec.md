# ritual-gui-info-lines Delta Spec

## ADDED Requirements

### Requirement: 行为自主信息行通道
仪式框架 SHALL 提供描述性信息行（InfoLine）数据通道：各 `RitualBehavior` 经钩子产出信息行列表，随 `RitualInfoPayload` 推送，客户端 Screen 仅按行渲染（文本/物品图标/进度条/✓✗ 四类元素），MUST NOT 感知具体仪式语义。InfoLine SHALL 至少承载：文本（可本地化）、图标物品、颜色、进度值（-1 表示无）、三态校验标记（null=无 / ✓ / ✗）。

#### Scenario: 新仪式零客户端改动
- **WHEN** 新增一个仪式 behavior 产出自定义信息行（如产出速率、倒计时）
- **THEN** 其 GUI 信息区自动按行为定义渲染，Screen 代码零改动

### Requirement: 清单与燃烧行下沉
祭品核对清单、可用配方清单 SHALL 由基类默认 `uiInfo` 实现产出为 InfoLine；加具土命之焰的燃烧批次状态（燃料图标+进度条+剩余秒/停机提示）SHALL 由该仪式 behavior 覆写产出。Screen 侧 MUST NOT 保留按 patternId 特判的渲染分支。

#### Scenario: 加具土命燃烧行
- **WHEN** 加具土命之焰燃烧批次进行中
- **THEN** 信息区出现燃料图标、燃烧进度条与剩余秒数，其渲染数据来自该 behavior 而非 Screen 特判

#### Scenario: 通用仪式默认信息
- **WHEN** 打开未覆写 `uiInfo` 的仪式界面
- **THEN** 信息区仍按基类默认实现显示祭品清单与配方清单
