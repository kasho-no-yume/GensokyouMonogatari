# 任务框架与测试入口

本轮提供通用任务框架与阿求的静态测试委托，不注册真实主线或可接支线，不包含每日刷新、符卡学习奖励或委托书生存配方。

## 游戏内入口

1. 创造物品栏取得“委托书”，或使用 `/give @s gensokyou:quest_book`。
2. 使用 `/gs_village visit hieda` 前往稗田居所。阿求在主厅书案旁，建筑全部区块放置完成且锚点实体区块加载完成后补齐。
3. 右键阿求，选择“任务”。可接取委托、材料交付、任务奖励、任务对话分别列出，长列表可翻页。
4. 接受后使用委托书，在任务簿选中任务并点击“追踪此任务”。每名玩家只能追踪一项；右侧说明支持滚轮查看。
5. 完成目标后，导航改为领奖者；回报领奖后任务退出当前列表。复制、丢弃委托书不会改变进度。

测试委托：

| 委托 | 操作 | 预期行为 |
|---|---|---|
| 材料持有 | 持有 4 张纸 | 完成即锁存；之后移走纸张仍可领奖，不扣纸 |
| 当面交付 | 给阿求 4 张纸 | 一次交付同时领奖；奖励空间不足时不扣料 |
| 异地交付 | 给博丽神社灵梦 4 张纸 | 灵梦收料，导航切回阿求，阿求发奖 |
| 对话确认 | 阿求 → 任务对话 → 确认问题 | 打开或选择“否”不完成，点击“是”才完成 |
| 接取后击杀 | 本人击败 2 只僵尸 | 接取前不计数；任意地点均可。参考点在主世界，因此在幻想乡只显示目标维度信息 |

所有测试委托奖励均为 1 円，只验证奖励通路。静态提供源每个定义每位玩家只发布一次，完成收据保留在玩家数据中但不显示归档列表。

## 数据与扩展

任务放在 `data/<namespace>/quests/<path>.json`，顶层 `id` 必须与资源路径一致。示例见 `data/gensokyou/quests/test/`。

- `kind` 为 `main`、`side`、`commission`；`scope` 为 `world` 或 `player`。主线只能用 `world`，支线/委托只能用 `player`。
- 主线用唯一的非负 `mainline_order` 排序，所有阶段展平为线性序列。世界 SavedData 保存一个阶段游标，任一玩家的有效事件可推进；主线奖励必须为空。
- 阶段必须有唯一局部 `id`、`title` 翻译键和非空 `objectives`。一个阶段的全部目标完成才推进；委托恰有一个阶段。
- `have_items` 指定 `item/count/description`；`submit_items` 另需 `npc` 收件角色；`kill_entities` 指定 `entity/count/description`；`talk_to_npc` 指定 `npc/action/description/question`，可选 `confirm` 翻译键。
- `navigation` 独立于完成条件：可以使用 `{"npc":"gensokyou:akyuu"}`，或 `{"dimension":"minecraft:overworld","x":0,"y":80,"z":0}`；省略时不画方向标记。每阶段最多一个导航点。
- `provider` 和 `return_npc` 指定发布者、领奖者。当前提供源只发布标为 `test: true` 的委托；材料收件者仍由目标独立决定。
- 材料检查/扣除限定快捷栏与主背包，不包括装备、副手和嵌套容器。上交时合并同次交付中重复物品的总需求，完整校验后才提交。
- `QuestObjectives.register` 注册新目标的 MapCodec、事件处理、校验和可选 NPC 操作。任务日志与生命周期只依赖通用目标契约；`QuestProviders.register` 添加提供源，无需改状态机。

普通死亡、重登和服务器重启保留任务、完成收据及追踪。服务端 `/reload` 保留主线线性游标；个人任务以持久资源代次统一失效，离线玩家下次读取时同样清理。客户端材质重载不会清除任务。

NPC 的角色身份默认使用实体类型 ID，与恢复时更换的 UUID 无关。`!/?` 由服务端按玩家单独生成；实体 NBT 中旧的全局 `QuestMarker` 不再使用。对话请求携带节点修订号，旧按钮或旧关闭请求不能操作新节点。

## 验证入口

JUnit 回归类位于 `quest/QuestProgressTest`、`QuestTurnInTest`、`QuestDefinitionTest`、`QuestProjectionTest` 和 `worldgen/human_village/AkyuuResidenceTest`。`testkit/scenario/QuestGameTests` 覆盖真实玩家 Attachment、对话会话、个人标记与阿求生成/恢复；`NpcGameTests` 维护现有 NPC 驻留和保护回归。

本轮实现时仅运行轻量检查，没有执行 Java 编译、JUnit、GameTest 或游戏内验证。实际执行状态记录在 change 的 `verification.md` 中。
