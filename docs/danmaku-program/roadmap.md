# 拆分路线图（2026-10-16）

2026-10-19：总设计已从active changes移至`docs/danmaku-program/`，仅作为设计资产保存，父specs不会重复合入。正式范围为19卡（大妖精3/山女4/蓝8/露米娅4）；露米娅 4 张已接入，傩面5卡已删除。阶段7已按最终授权收口归档，7项delta已合入主规范；证据与后续专项边界见[退役报告](../../openspec/changes/archive/2026-10-19-retire-legacy-boss-danmaku/validation.md)。下文是拆分时的历史依赖说明，不代表所有历史实机门槛已经通过。

本目录现在是**总设计与需求索引，不作为整包代码实施入口**。原有design、operations、recipes和specs保留为完整上下文；实现应选择下列子change。父目录中的规范不单独归档合入主spec，以免重复宣称所有能力已经交付。

## 顺序与边界

2026-10-17用户授权更新阶段5验收：已确认代表切片收口，原严格M2量化A/B/三次重复/真实1/2/5人全矩阵转后续专项；剩余阻塞条件改为`add-danmaku-program-gameplay/tasks.md`第3节高密度复杂压力验收及最终收口。下文阶段5“A/B门槛”“M2”应按该最新决定理解，不再强制原50%/110%和54项。不是旧量化门槛通过声明，阶段6仍等阶段5压力验收通过。

| 顺序 | change | 交付 | 依赖 |
|---|---|---|---|
| 1 | [add-danmaku-program-core](../../openspec/changes/archive/2026-10-19-add-danmaku-program-core/proposal.md) | 基线、类型、时间组合、注册接口与离线编译 | 无 |
| 2 | [add-danmaku-program-operations](../../openspec/changes/archive/2026-10-19-add-danmaku-program-operations/proposal.md) | 数学路径、图案、表演与发射器描述，纯函数测试 | 1 |
| 3 | [add-danmaku-program-server](../../openspec/changes/archive/2026-10-19-add-danmaku-program-server/proposal.md) | 服务端组生命周期、世界采样、预算与分裂事务 | 1、2 |
| 4 | [add-danmaku-program-network](../../openspec/changes/archive/2026-10-19-add-danmaku-program-network/proposal.md) | 定义、实例、事件、检查点、兴趣管理和恢复 | 3 |
| 5 | [add-danmaku-program-gameplay](../../openspec/changes/archive/2026-10-19-add-danmaku-program-gameplay/proposal.md) | 碰撞、渲染、预览、诊断、所有代表切片及A/B门槛 | 2、3、4 |
| 6 | [migrate-boss-cards-to-programs](../../openspec/changes/archive/2026-10-19-migrate-boss-cards-to-programs/proposal.md) | 15张正式符卡、2底噪与BOSS控制接线；5卡排除 | 5验收通过 |
| 7 | [retire-legacy-boss-danmaku（已归档）](../../openspec/changes/archive/2026-10-19-retire-legacy-boss-danmaku/proposal.md) | 最终压力回归、删除BOSS旧入口、规格与在途change交接 | 6 |

顺序是实施前置条件，**不是OpenSpec自动执行的依赖系统**。文档齐全不代表前置代码已实现。阶段1可以开始；其余先完成依赖。阶段5较宽，因为必须把服务端、协议与客户端闭环验收后才能迁移内容；不把它拆成几个互相无法验收的开关。

所有子change都有自己的proposal/design/specs/tasks，可以独立验证与归档。子规范使用独立能力名，避免几个在途change同时修改同一Requirement。原来六项旧capability的适用域替代由阶段7统一承接；在此之前新后端只用于显式选择的预览/测试，阶段6通过迁移开关逐BOSS启用。

## 原任务一对一归属

原编号仅用来追踪，不再维护第二份待办。子任务保留`[原X.Y]`标记；标记只出现一次，避免同一工作被两个change计为交付。

| 原任务 | 唯一归属 |
|---|---|
| 1.1–1.5、2.1–2.6 | 1 core |
| 3.1–3.7、4.1–4.11 | 2 operations |
| 5.1–5.10 | 3 server |
| 7.1–7.8 | 4 network |
| 6.1–6.7、8.1–8.11、10.1–10.2 | 5 gameplay |
| 9.1–9.8 | 6 migration |
| 10.3–10.8 | 7 retirement |

原1.5中的影子后端：阶段1只交付选择接口、配置与单一判伤约束，实际游戏接线归阶段5；不会为了初始化接口要求后端提前实现。原3.7在阶段2验证新类型无需改纯执行核心/编码封套，阶段5复验实际网络与游戏集成。原4.7“先于后端”指纯核探针，实机复验在阶段5。原8.7的门槛在阶段5收尾执行，必须等待同阶段全部8.x案例，不能依编号误以为8.8以后可跳过。

## 规范与原设计的对应

- program-runtime的类型/时间/扩展 → core；数学与通道/图案 → operations；生命周期/决策/事务 → server；碰撞/真实表演 → gameplay。
- program-sync → network；真实数据后端无逐弹包与多视角表现由gameplay端到端复验。
- program-migration → gameplay门槛、content迁移和retirement退出三阶段。
- 旧track-composition/behaviour/motion/leg-motion/pipeline-capacity/timeline替代契约 → 已移至retirement/specs，归档时一次性应用，父目录不再保留第二份delta。
- 操作台账每阶段更新：已接入才标implemented，通过对应测试才标verified；仅有描述不能把外部实体发射、分裂事务等标成可用。

## 总目录的退出

不要对本总目录运行整包apply或普通archive。所有子change完成后，阶段7将总设计移动到稳定的`docs/danmaku-program/`并修正技能和子change链接，将总目录标记为已被七项实现替代，按文档整理方式移出active changes；其specs不得再次合入，原实现任务不得虚假勾选。未完成的extend-danmaku-orchestration/shapes也仍在阶段7明确交接，不在拆分时假装完成。
