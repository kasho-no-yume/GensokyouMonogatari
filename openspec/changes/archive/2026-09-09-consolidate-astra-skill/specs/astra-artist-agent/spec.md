## MODIFIED Requirements

### Requirement: 设计知识包内容
仓库 SHALL 以 `.opencode/skills/ritual-design/SKILL.md`（仪式 §1~§6、建筑 .nbt §7）作为 Astra 的唯一设计手册，
不再单独维护 astra-design 技能包。手册内容 MUST 包含：仪式多方块硬性不变量（四重对称展开规则、
升级纯增量、锚点唯一、品阶下限、禁方块状态后缀、仪式石族累积 ≤30%）、风格指南（东方元素词库、
体量预算表）、交付物契约与工具使用说明；MUST NOT 包含程序侧内容（Java、behavior 注册、
测试 harness 实现细节）。贴图风格准则 SHALL 归位 `.opencode/skills/gen-textures/SKILL.md`，
MUST NOT 混入仪式/建筑设计手册。知识包总体积 SHALL 控制在约 7k token 以内（agent 定义 + 技能 + 目录）。

#### Scenario: 知识包不含程序侧内容
- **WHEN** 阅读 ritual-design 技能包
- **THEN** 其中不存在 Java 类名、behavior 注册表、测试 harness 实现等程序侧内容

#### Scenario: 单一事实源、无冲突旧条款
- **WHEN** 用户按本 change 移除 astra-design 技能包
- **THEN** `.opencode/skills/` 下不再存在 `astra-design` 目录，且 ritual-design 内不残留"1 基阶级 / 升级=子集 / ritual_stone 族 ≥30% 为主 / 材料随品阶递进"等与之冲突的过时条款

#### Scenario: 硬性不变量齐备
- **WHEN** Astra 收到仪式设计任务并阅读技能包
- **THEN** 四重对称/纯增量/锚点唯一/品阶下限/禁状态后缀/石族 ≤30% 均可从中获知

#### Scenario: 建筑与贴图任务分流到正确小节
- **WHEN** Astra 收到建筑 .nbt 设计任务或贴图绘制任务
- **THEN** 建筑工作流从 ritual-design §7 获知、贴图规范从 gen-textures 技能获知，二者均无需读取已删除的 astra-design
