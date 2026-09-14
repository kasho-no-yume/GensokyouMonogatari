## MODIFIED Requirements

### Requirement: 清单与燃烧行下沉
祭品核对清单 SHALL 由基类默认 `uiInfo` 实现产出为 InfoLine；可用配方清单 SHALL NOT 出现在任何仪式 GUI（配方目录的唯一展示面是 JEI）。加具土命之焰的燃烧批次状态（燃料图标+进度条+剩余秒/停机提示）SHALL 由该仪式 behavior 覆写产出。Screen 侧 MUST NOT 保留按 patternId 特判的渲染分支。

#### Scenario: 加具土命燃烧行
- **WHEN** 加具土命之焰燃烧批次进行中
- **THEN** 信息区出现燃料图标、燃烧进度条与剩余秒数，其渲染数据来自该 behavior 而非 Screen 特判

#### Scenario: 通用仪式默认信息不含配方清单
- **WHEN** 打开未覆写 `uiInfo` 的仪式界面
- **THEN** 信息区按基类默认实现显示祭品清单与（若有）当前激活配方标记，但不罗列可用配方

#### Scenario: 源初造化界面零裸键
- **WHEN** 打开未摆任何原料的源初造化核心界面
- **THEN** 信息区无配方 ✓✗ 行、无未翻译裸键；仅空闲态（无行）或聚灵/合成状态行
