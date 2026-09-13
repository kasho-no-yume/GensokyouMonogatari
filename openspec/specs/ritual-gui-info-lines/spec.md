# ritual-gui-info-lines Specification

## Purpose

仪式框架提供行为自主的描述性信息行（InfoLine）数据通道，由各 RitualBehavior 产出信息行，客户端 Screen 仅按行渲染、无仪式语义特判；行可携带三态控件与悬浮明细，点击复用菜单按钮通道回报服务端。

## Requirements

### Requirement: 行为自主信息行通道
仪式框架 SHALL 提供描述性信息行（InfoLine）数据通道：各 `RitualBehavior` 经钩子产出信息行列表，随 `RitualInfoPayload` 推送，客户端 Screen 仅按行渲染（文本/物品图标/进度条/✓✗ 四类元素，外加可交互行控件，见"信息行可交互扩展"），MUST NOT 感知具体仪式语义。InfoLine SHALL 至少承载：文本（可本地化）、图标物品、颜色、进度值（-1 表示无）、三态校验标记（null=无 / ✓ / ✗）、可选操作 id 与控件类型。

#### Scenario: 新仪式零客户端改动
- **WHEN** 新增一个仪式 behavior 产出自定义信息行（如产出速率、倒计时）
- **THEN** 其 GUI 信息区自动按行为定义渲染，Screen 代码零改动

#### Scenario: 共鸣塔候选行
- **WHEN** 共鸣塔 behavior 产出一批携带三态控件的候选信息行
- **THEN** Screen 通用渲染为可点击行并把点击回报该 behavior，自身不含任何共鸣语义

### Requirement: 清单与燃烧行下沉
祭品核对清单、可用配方清单 SHALL 由基类默认 `uiInfo` 实现产出为 InfoLine；加具土命之焰的燃烧批次状态（燃料图标+进度条+剩余秒/停机提示）SHALL 由该仪式 behavior 覆写产出。Screen 侧 MUST NOT 保留按 patternId 特判的渲染分支。

#### Scenario: 加具土命燃烧行
- **WHEN** 加具土命之焰燃烧批次进行中
- **THEN** 信息区出现燃料图标、燃烧进度条与剩余秒数，其渲染数据来自该 behavior 而非 Screen 特判

#### Scenario: 通用仪式默认信息
- **WHEN** 打开未覆写 `uiInfo` 的仪式界面
- **THEN** 信息区仍按基类默认实现显示祭品清单与配方清单

### Requirement: 信息行可交互扩展
`InfoLine` SHALL 支持可选交互声明：每行可携带一个操作 id、控件类型（当前定义：0=纯展示、1=三态链接控件）与悬浮明细（tipKey + tipArgs，模板以 \n 分行），纯展示行为默认值。携带操作 id 的行 SHALL 由客户端渲染为可点击行，点击经既有菜单按钮通道回报服务端，由行为侧 `onUiAction` 权威处理；服务端 MUST NOT 信任客户端状态，处理入口统一复核约束。携带 tipKey 的行 SHALL 在鼠标悬浮时按模板分行渲染 tooltip（不做按宽自动换行）。新增字段 SHALL 向后兼容：未声明交互的行为产出的行渲染与行为完全不变。

#### Scenario: 零改动兼容
- **WHEN** 既有仪式（加具土命、通用清单）产出无交互字段的信息行
- **THEN** 渲染与交互行为与本扩展之前完全一致

#### Scenario: 行点击回服务端
- **WHEN** 玩家点击一个携带操作 id 的信息行
- **THEN** 服务端行为收到对应 id 的 onUiAction 调用并回推新快照，无需新增网络包类型

#### Scenario: 悬浮明细分行
- **WHEN** 鼠标悬浮于携带多行 tipKey 模板的信息行
- **THEN** tooltip 按模板内的换行逐项成行展示，不因宽度被再次折行

### Requirement: 信息区滚动
行为信息行总高度超出信息区可视范围时，客户端 SHALL 支持滚轮平移浏览全部行；无溢出时滚轮 SHALL 不产生任何位移。面板整体尺寸与固定头/右侧功能栏布局 MUST NOT 因滚动而改变。

#### Scenario: 长列表可完整浏览
- **WHEN** 共鸣塔候选 + 链接行合计超过可视高度
- **THEN** 滚轮平移可浏览到最后一行，顶部固定信息保持原位
