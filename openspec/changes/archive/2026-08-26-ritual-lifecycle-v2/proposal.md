# Proposal: ritual-lifecycle-v2 — 仪式生命周期框架

## Why

阶段 B 建立的仪式框架只覆盖"结构匹配"单层：方向约定未成文（靠 8 变换暴力兜底）、没有统一的启动语义、祭品台与仪式脱节（仅加工环散装读取）、结构定义缺乏游戏内采集手段的物品化流程。阶段 D 的降神祭坛等内容将大量复用仪式体系且各仪式后续仍会大改重做，因此本变更**只交付框架与基类能力**，把生命周期、祭品要求、界面、图鉴、构造工具的地基打牢。

## What Changes

- **结构定义硬约束**：切片沿过锚点 X/Z 双轴镜像对称 + 轴上格子四方位成套出现 + 全仪式唯一核心（anchorKey 恰现一次）；loader 加载时校验拒载违规文件；`/gs_ritual_capture` 同步校验；匹配器删除镜像变换（8 变换 → 4 旋转）
- **声明式祭品要求**：rituals JSON 新增 `requirements` 段（key + slot 寻址单个祭品台、none/on_activate/periodic 三种消耗语义），匹配器保持纯结构判定，门槛检查独立成层
- **统一生命周期**：成型（结构匹配）→ 启动（门槛校验 + 消耗扣减）→ 运行（enabled 门控所有 behavior tick）→ 停止。**事件型与设施型同轨**：一律经"右键核心打开 UI → 点击启动按钮"启动仪式；重扫失效即停机；事件结束后结构保持原样可复用；现有结界引爆等杂散开关字段收编为统一 `enabled` 态
- **成型替换扩展点**：仪式成型确立时调用结构替换函数，当前版本为空实现占位——为将来"替换为大型 tileblock"的外观机制预留挂载点，本变更不引入新方块与渲染
- **核心右键 UI**：空手右键已成型核心必开界面——仪式信息、祭品要求核对清单（✓/✗）、可开关仪式的启动/停止按钮
- **JEI 配方卡**：由 requirements 自动派生"逐台输入 → 效果输出"的通用配方展示；催化剂查找入口保持现状（其退役随召唤翻新后续变更处理）
- **仪式构造仗**：新物品，左/右键先后框定两个角点确定 AABB，将区内全部方格捕获为仪式多方块结构 JSON 格式（含对称校验回显），方便入库复用
- **占位仪式最小修补**：仅为使现有 JSON 通过新校验、代码不报错而做最小修改（如 generator_circle 补对称）；任何具体仪式的玩法翻新均不在本变更内

## Capabilities

### New Capabilities

- `ritual-offerings`: 祭品要求的声明格式、key+slot 寻址、门槛校验与消耗扣减语义
- `ritual-lifecycle`: 成型/启动/运行/停止状态机、enabled 门控、统一结构存续判定、成型替换扩展点（当前空实现）
- `ritual-core-interface`: 仪式核心右键界面——信息面板、祭品清单、启停操作及其网络交互

### Modified Capabilities

- `ritual-pattern-system`: 新增对称性/单核心加载期校验要求；识别朝向由 8 变换收敛为 4 旋转；新增构造仗框选采集要求
- `jei-ritual-display`: 新增由 requirements 派生的通用配方卡展示

## Impact

- `com.bitsson.gensokyou.ritual` 包重构：RitualPattern schema v2（requirements/toggleable 字段）、RitualMatcher 删镜像 + 全局锚点、RitualPatternLoader 加载校验
- 全 mod 首个 Menu/Screen 与对应网络 payload（开界面、启停指令）；RitualBehavior 接口新增成型替换扩展点回调
- RitualCoreBlockEntity 杂散状态收编（barrierActivated/portalPos 等）；六个现有 behavior 迁移到新生命周期门控
- 新增构造仗物品（注册/交互/与采集管线复用）
- 文档：docs/new-ritual-checklist.md 按新约定重写
- 协调：phase-b-spirit-economy 已完成待归档，发电机 JSON 最小修正需与其归档顺序协调；召唤祭品化翻新、催化剂终局等具体仪式改动明确移交给后续内容变更
