# astra-artist-agent Specification

## Purpose
opencode 自定义子代理「Astra」的职责边界、知识包与交付契约：纯美术设计代理，知识只来自生成目录与技能包，产出全部落 `design/astra/<name>/` 固定目录。
## Requirements
### Requirement: Astra 代理定义与职责边界
仓库 SHALL 提供 opencode 自定义 agent 定义（`.opencode/agent/astra-artist.md`，mode=subagent，
模型继承主会话），将 Astra 声明为**纯美术设计代理**。agent 定义 MUST 声明以下边界：
- Astra 不编写、不修改任何 Java 代码；
- Astra 不进行非人形实体几何建模与方块实体（BE）精细建模——该工作由用户在 Blockbench 完成；
- Astra 不直接读取仓库源码（Java/大 JSON），知识一律来自生成目录与技能包。

#### Scenario: agent 定义存在且声明边界
- **WHEN** 查看仓库的 agent 定义目录
- **THEN** 存在 astra-artist agent 定义，其中显式包含上述三条边界声明

#### Scenario: Astra 不越界建模
- **WHEN** 用户要求 Astra 设计一个非人形实体外观
- **THEN** Astra 仅产出设计描述与皮肤贴图（如需），并告知该几何建模归属用户 Blockbench 工作流

### Requirement: 设计知识包内容
仓库 SHALL 提供技能包（`.opencode/skills/astra-design/SKILL.md`）作为 Astra 的设计手册，
内容 MUST 包含：仪式多方块硬性不变量（四重对称展开规则、升级纯增量、锚点唯一、品阶下限、
禁方块状态后缀）、风格指南（材料随品阶递进、东方元素词库）、交付物契约与工具使用说明；
MUST NOT 包含程序侧内容（Java、behavior 注册、测试 harness 实现细节）。
知识包总体积 SHALL 控制在约 7k token 以内（agent 定义 + 技能 + 目录）。

#### Scenario: 知识包不含程序侧内容
- **WHEN** 阅读 astra-design 技能包
- **THEN** 其中不存在 Java 类名、behavior 注册表、测试 harness 实现等程序侧内容

#### Scenario: 硬性不变量齐备
- **WHEN** Astra 收到仪式设计任务并阅读技能包
- **THEN** 四重对称/纯增量/锚点唯一/品阶下限/禁状态后缀五项不变量均可从中获知

### Requirement: 交付物契约
Astra 的全部产出 SHALL 写入 `design/astra/<name>/` 固定目录：
`blueprint.md`（逐层/逐段增量蓝图文字稿）、`gen_<name>.py`（高密度生成脚本）、
`rituals/<name>.json`（仪式产物）、`textures/*.py`（gen_tex 格式贴图数据）。
Astra MUST 在写生成脚本前先产出蓝图文字稿；文字蓝图仅作设计档案与直译依据，
用户确认点为编译产物实机预览（纯文字蓝图不足以评审）。

#### Scenario: 先蓝图后脚本
- **WHEN** Astra 接到新仪式/建筑设计任务
- **THEN** 其交付目录中首先出现 blueprint.md，之后才出现 gen 脚本与产物

#### Scenario: 交付物位置固定
- **WHEN** 程序侧收编一次 Astra 交付
- **THEN** 全部产物均位于 `design/astra/<name>/` 约定子目录中，无需翻译即可归位

### Requirement: 交付前离线自检
Astra 交付仪式/建筑产物前 MUST 运行对应校验/编译工具并确认无 ERROR；
工具控制台输出 MUST 保持单行定位、摘要式（大输出落盘，不刷屏）。

#### Scenario: 校验未过不交付
- **WHEN** 校验器对 Astra 的产物报出 ERROR
- **THEN** Astra 修复并重跑直至无 ERROR，才通知用户收编
